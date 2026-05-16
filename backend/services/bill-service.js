import config from '../config/index.js';
import accountService from './account-service.js';
import notificationService from './notification-service.js';
import logoService from './logo-service.js';
import crypto from 'crypto';

const ACTIVE_BILLER_STATUS = 'ACTIVE';
const IDEMPOTENCY_EXPIRY_HOURS = 24;
const BILL_PAYMENT_ENDPOINT = 'POST /api/bill-payments';

function shouldRetryIdempotencyInsertWithKeyId(error) {
    return error?.code === '23502' && String(error.message || '').includes('key_id');
}

async function insertIdempotencyRecord(record, logContext) {
    let { error } = await config.supabase
        .from('idempotency_keys')
        .insert(record);

    if (shouldRetryIdempotencyInsertWithKeyId(error)) {
        ({ error } = await config.supabase
            .from('idempotency_keys')
            .insert({
                key_id: crypto.randomInt(1, 2147483647),
                ...record
            }));
    }

    if (error) {
        console.error(logContext, error);
    }
}

function isActiveBiller(biller) {
    return biller && biller.status === ACTIVE_BILLER_STATUS;
}

function normalizeClientCode(clientCode) {
    return String(clientCode || '').trim().toUpperCase();
}

function generateBillReference(paymentId = null) {
    const timestamp = Date.now().toString(36).toUpperCase();
    const random = Math.random().toString(36).substring(2, 7).toUpperCase();
    return paymentId ? `BP${paymentId}${random}` : `BP${timestamp}${random}`;
}

function normalizeCategoryName(categoryName) {
    return String(categoryName || '').trim().toLowerCase();
}

function addBillerLogoUrl(biller, fallbackCategory = null) {
    if (!biller) return null;

    const category = biller.categories?.name || fallbackCategory || biller.category || 'other';
    return {
        ...biller,
        category,
        logo_url: logoService.buildLogoUrl(biller.domain)
    };
}

async function getCategoryByName(categoryName) {
    const normalizedName = normalizeCategoryName(categoryName);
    if (!normalizedName) return null;

    const { data, error } = await config.supabase
        .from('categories')
        .select('category_id, name')
        .ilike('name', normalizedName)
        .limit(1)
        .maybeSingle();

    if (error) {
        console.error('Error fetching category:', error);
        throw error;
    }

    return data;
}

async function checkIdempotencyKey(userId, idempotencyKey, endpoint = BILL_PAYMENT_ENDPOINT) {
    if (!userId || !idempotencyKey) return null;

    const { data, error } = await config.supabase
        .from('idempotency_keys')
        .select('*')
        .eq('user_id', userId)
        .eq('idempotency_key', idempotencyKey)
        .eq('endpoint', endpoint)
        .gt('expires_at', new Date().toISOString())
        .order('created_at', { ascending: false })
        .limit(1)
        .maybeSingle();

    if (error) {
        console.error('Error checking bill payment idempotency:', error);
        return null;
    }

    return data;
}

async function saveIdempotencyKey(userId, idempotencyKey, endpoint, responseStatus, responseBody) {
    if (!userId || !idempotencyKey) return;

    const expiresAt = new Date();
    expiresAt.setHours(expiresAt.getHours() + IDEMPOTENCY_EXPIRY_HOURS);

    await insertIdempotencyRecord({
        user_id: userId,
        idempotency_key: idempotencyKey,
        endpoint: endpoint || BILL_PAYMENT_ENDPOINT,
        response_status: responseStatus,
        response_body: responseBody,
        expires_at: expiresAt.toISOString()
    }, 'Error saving bill payment idempotency:');
}

/**
 * Obține lista furnizorilor
 */
async function getBillers(categoryName = null) {
    const category = categoryName ? await getCategoryByName(categoryName) : null;
    if (categoryName && !category) {
        return [];
    }

    let query = config.supabase
        .from('billers')
        .select(`
            *,
            categories (
                category_id,
                name
            )
        `)
        .eq('status', ACTIVE_BILLER_STATUS)
        .order('name');

    if (category) {
        query = query.eq('category_id', category.category_id);
    }

    const { data, error } = await query;

    if (error) {
        console.error('Error fetching billers:', error);
        throw error;
    }

    return (data || []).map(biller => addBillerLogoUrl(biller, category?.name));
}

/**
 * Obține categoriile care au furnizori activi
 */
async function getBillerCategories() {
    const { data, error } = await config.supabase
        .from('billers')
        .select(`
            categories!inner (
                name
            )
        `)
        .eq('status', ACTIVE_BILLER_STATUS);

    if (error) {
        console.error('Error fetching biller categories:', error);
        throw error;
    }

    const categories = [...new Set(data.map(b => b.categories?.name).filter(Boolean))];
    return categories.sort();
}

/**
 * Obține un furnizor după ID
 */
async function getBillerById(billerId) {
    const { data, error } = await config.supabase
        .from('billers')
        .select(`
            *,
            categories (
                category_id,
                name
            )
        `)
        .eq('biller_id', billerId)
        .single();

    if (error) {
        console.error('Error fetching biller:', error);
        return null;
    }

    return addBillerLogoUrl(data);
}

/**
 * Obține furnizorii salvați ai unui utilizator
 */
async function getSavedBillers(userId) {
    const { data, error } = await config.supabase
        .from('saved_billers')
        .select(`
            *,
            billers!inner (
                *,
                categories (
                    name
                )
            )
        `)
        .eq('user_id', userId)
        .eq('billers.status', ACTIVE_BILLER_STATUS)
        .order('last_used_at', { ascending: false, nullsFirst: false });

    if (error) {
        console.error('Error fetching saved billers:', error);
        throw error;
    }

    return (data || []).map(saved => ({
        ...saved,
        billers: addBillerLogoUrl(saved.billers)
    }));
}

/**
 * Salvează un furnizor cu codul de client
 */
async function saveBiller(userId, billerId, clientCode, alias = null) {
    const normalizedClientCode = normalizeClientCode(clientCode);
    if (!normalizedClientCode) {
        throw new Error('INVALID_CLIENT_CODE');
    }

    const biller = await getBillerById(billerId);
    if (!biller) {
        throw new Error('BILLER_NOT_FOUND');
    }

    if (!isActiveBiller(biller)) {
        throw new Error('BILLER_INACTIVE');
    }

    const validation = validateClientCode(normalizedClientCode, biller.account_format);
    if (!validation.valid) {
        throw new Error('INVALID_CLIENT_CODE');
    }
    // Verifică dacă există deja
    const { data: existingBillers, error: existingError } = await config.supabase
        .from('saved_billers')
        .select('saved_biller_id, client_code')
        .eq('user_id', userId)
        .eq('biller_id', billerId);

    if (existingError) {
        console.error('Error checking saved biller:', existingError);
        throw existingError;
    }

    const existing = (existingBillers || []).find(savedBiller =>
        normalizeClientCode(savedBiller.client_code) === normalizedClientCode
    );

    if (existing) {
        // Actualizează last_used_at
        const { data, error } = await config.supabase
            .from('saved_billers')
            .update({ client_code: normalizedClientCode, last_used_at: new Date().toISOString() })
            .eq('saved_biller_id', existing.saved_biller_id)
            .select()
            .single();

        if (error) throw error;
        return data;
    }

    // Creează nou
    const { data, error } = await config.supabase
        .from('saved_billers')
        .insert({
            user_id: userId,
            biller_id: billerId,
            client_code: normalizedClientCode,
            alias: alias
        })
        .select()
        .single();

    if (error) {
        console.error('Error saving biller:', error);
        throw error;
    }

    return data;
}

/**
 * Actualizează last_used_at pentru un furnizor salvat
 */
async function updateSavedBillerUsage(userId, savedBillerId) {
    await config.supabase
        .from('saved_billers')
        .update({ last_used_at: new Date().toISOString() })
        .eq('saved_biller_id', savedBillerId)
        .eq('user_id', userId);
}

/**
 * Șterge un furnizor salvat
 */
async function deleteSavedBiller(userId, savedBillerId) {
    const { data, error } = await config.supabase
        .from('saved_billers')
        .delete()
        .eq('saved_biller_id', savedBillerId)
        .eq('user_id', userId)
        .select()
        .single();

    if (error) {
        console.error('Error deleting saved biller:', error);
        throw error;
    }

    return data;
}

/**
 * Validează codul de client conform formatului furnizorului
 */
function validateClientCode(clientCode, accountFormat) {
    if (!clientCode || clientCode.trim() === '') {
        return { valid: false, error: 'Codul de client este obligatoriu' };
    }

    if (!accountFormat) {
        return { valid: true };
    }

    try {
        const regex = new RegExp(accountFormat);
        if (!regex.test(clientCode)) {
            return { valid: false, error: 'Format cod client invalid' };
        }
    } catch (e) {
        // Invalid regex in DB, skip validation
    }

    return { valid: true };
}

/**
 * Creează o plată de factură
 */
async function createBillPayment(userId, params) {
    const {
        fromAccountId,
        billerId,
        clientCode,
        invoiceReference,
        amount,
        savedBillerId
    } = params;
    const normalizedClientCode = normalizeClientCode(clientCode);

    // 1. Verifică furnizorul
    const biller = await getBillerById(billerId);
    if (!biller) {
        throw new Error('BILLER_NOT_FOUND');
    }

    if (!isActiveBiller(biller)) {
        throw new Error('BILLER_INACTIVE');
    }

    // 2. Validează codul de client
    const validation = validateClientCode(normalizedClientCode, biller.account_format);
    if (!validation.valid) {
        throw new Error('INVALID_CLIENT_CODE');
    }

    if (!Number.isFinite(amount) || amount <= 0) {
        throw new Error('INVALID_AMOUNT');
    }

    // 3. Verifică contul sursă și că aparține userului
    const account = await accountService.getAccountByIdForUser(userId, fromAccountId);
    if (!account) {
        throw new Error('ACCOUNT_NOT_FOUND');
    }

    // 4. Verifică sold
    if (parseFloat(account.balance) < amount) {
        throw new Error('INSUFFICIENT_FUNDS');
    }

    // 5. Debitează contul atomic
    let adjustResult;
    try {
        adjustResult = await accountService.adjustBalance(fromAccountId, -amount);
    } catch (err) {
        if (err.message === 'INSUFFICIENT_FUNDS') {
            throw err;
        }
        console.error('Error adjusting balance:', err);
        throw new Error('PAYMENT_FAILED');
    }

    // 6. Creează plata
    const paymentReference = invoiceReference || generateBillReference();

    const { data: payment, error: paymentError } = await config.supabase
        .from('bill_payments')
        .insert({
            from_account_id: fromAccountId,
            biller_id: billerId,
            client_code: normalizedClientCode,
            invoice_reference: paymentReference,
            amount: amount,
            currency: account.currency.trim(),
            status: 'COMPLETED',
            completed_at: new Date().toISOString()
        })
        .select()
        .single();

    if (paymentError) {
        // Rollback - creditează înapoi contul
        try {
            await accountService.adjustBalance(fromAccountId, amount);
        } catch (rollbackErr) {
            console.error('Error rolling back balance:', rollbackErr);
        }

        console.error('Error creating bill payment:', paymentError);
        throw new Error('PAYMENT_FAILED');
    }

    // 7. Actualizează sau salvează furnizorul
    if (savedBillerId) {
        await updateSavedBillerUsage(userId, savedBillerId);
    } else {
        await saveBiller(userId, billerId, normalizedClientCode);
    }

    // 8. Trimite notificare de confirmare
    await notificationService.sendPushNotification(
        userId,
        'Factura platita',
        `${amount.toFixed(2)} ${account.currency.trim()} catre ${biller.name}`,
        {
            type: 'BILL_PAYMENT_COMPLETED',
            payment_id: payment.payment_id.toString(),
            biller_name: biller.name,
            amount: amount.toString(),
            currency: account.currency.trim()
        }
    );

    return {
        payment_id: payment.payment_id,
        biller_name: biller.name,
        client_code: normalizedClientCode,
        reference: payment.invoice_reference,
        amount: amount,
        currency: account.currency.trim(),
        status: 'COMPLETED'
    };
}

/**
 * Obține istoricul plăților de facturi
 */
async function getBillPayments(userId, limit = 50, offset = 0) {
    const accounts = await accountService.getAccounts(userId);
    const accountIds = accounts.map(a => a.account_id);

    if (accountIds.length === 0) {
        return [];
    }

    const { data, error } = await config.supabase
        .from('bill_payments')
        .select(`
            *,
            billers (
                *,
                categories (
                    name
                )
            )
        `)
        .in('from_account_id', accountIds)
        .order('created_at', { ascending: false })
        .range(offset, offset + limit - 1);

    if (error) {
        console.error('Error fetching bill payments:', error);
        throw error;
    }

    return (data || []).map(payment => ({
        ...payment,
        billers: addBillerLogoUrl(payment.billers)
    }));
}

export default {
    getBillers,
    getBillerCategories,
    getBillerById,
    getSavedBillers,
    saveBiller,
    deleteSavedBiller,
    checkIdempotencyKey,
    saveIdempotencyKey,
    validateClientCode,
    createBillPayment,
    getBillPayments,
    BILL_PAYMENT_ENDPOINT
};
