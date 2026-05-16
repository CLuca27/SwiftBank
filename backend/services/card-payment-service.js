import config from '../config/index.js';
import cardService from './card-service.js';
import accountService from './account-service.js';
import ratesService from './rates-service.js';
import notificationService from './notification-service.js';
import merchantService from './merchant-service.js';
import crypto from 'crypto';

const SESSION_EXPIRY_MINUTES = 5;
const IDEMPOTENCY_EXPIRY_HOURS = 24;
const PAYMENT_START_ENDPOINT = 'POST /api/card-payments/start';
const POS_PAYMENT_ENDPOINT = 'POST /api/card-payments/pos/authorize';
const ACTIVE_APPROVAL_STATUSES = ['PENDING_APPROVAL'];
const POS_ENTRY_MODES = ['GOOGLE_WALLET', 'CONTACTLESS', 'CHIP', 'MAGSTRIPE'];

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

/**
 * Verifică dacă un idempotency key există pentru card payments (endpoint public)
 */
async function checkPaymentIdempotency(userId, idempotencyKey, endpoint = PAYMENT_START_ENDPOINT) {
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
        console.error('Error checking payment idempotency:', error);
        return null;
    }

    return data;
}

/**
 * Salvează idempotency key pentru card payments
 */
async function savePaymentIdempotency(userId, idempotencyKey, responseStatus, responseBody, endpoint = PAYMENT_START_ENDPOINT) {
    if (!userId || !idempotencyKey) return;

    const expiresAt = new Date();
    expiresAt.setHours(expiresAt.getHours() + IDEMPOTENCY_EXPIRY_HOURS);

    await insertIdempotencyRecord({
        user_id: userId,
        idempotency_key: idempotencyKey,
        endpoint,
        response_status: responseStatus,
        response_body: responseBody,
        expires_at: expiresAt.toISOString()
    }, 'Error saving payment idempotency:');
}

function normalizeEntryMode(entryMode) {
    const normalized = String(entryMode || 'GOOGLE_WALLET').trim().toUpperCase();
    return POS_ENTRY_MODES.includes(normalized) ? normalized : 'GOOGLE_WALLET';
}

function generateCardReference() {
    const timestamp = Date.now().toString(36).toUpperCase();
    const random = crypto.randomBytes(3).toString('hex').toUpperCase();
    return `CP${timestamp}${random}`;
}

/**
 * Valideaza un card pentru plata
 * @returns {card, user_id} sau null daca cardul e invalid
 */
async function validateCard(cardNumber, expiry, cvv) {
    const normalizedCardNumber = cardNumber.replace(/\D/g, '');

    console.log('[CardPayment] Looking for card:', normalizedCardNumber);

    const { data: card, error } = await config.supabase
        .from('cards')
        .select('card_id, user_id, card_number, expiry_date, cvv_hash, status')
        .eq('card_number', normalizedCardNumber)
        .single();

    console.log('[CardPayment] Query result - card:', card, 'error:', error);

    if (error || !card) {
        return { valid: false, error: 'CARD_NOT_FOUND' };
    }

    if (card.status !== 'active') {
        return { valid: false, error: 'CARD_BLOCKED' };
    }

    // Verificam expiry date (format MM/YY sau MM/YYYY)
    const expiryMatch = expiry.match(/^(0[1-9]|1[0-2])\/?(\d{2}|\d{4})$/);
    if (!expiryMatch) {
        return { valid: false, error: 'INVALID_EXPIRY_FORMAT' };
    }

    const inputMonth = parseInt(expiryMatch[1]);
    const inputYear = expiryMatch[2].length === 2
        ? 2000 + parseInt(expiryMatch[2])
        : parseInt(expiryMatch[2]);

    const cardExpiry = new Date(card.expiry_date);
    const cardMonth = cardExpiry.getMonth() + 1;
    const cardYear = cardExpiry.getFullYear();

    if (inputYear !== cardYear || inputMonth !== cardMonth) {
        return { valid: false, error: 'EXPIRY_MISMATCH' };
    }

    // Verificam CVV
    const decryptedCvv = cardService.decryptCVV(card.cvv_hash);
    if (cvv !== decryptedCvv) {
        return { valid: false, error: 'INVALID_CVV' };
    }

    return {
        valid: true,
        card: {
            card_id: card.card_id,
            user_id: card.user_id,
            masked_card: `**** ${normalizedCardNumber.slice(-4)}`
        }
    };
}

async function validateCardForPos(cardNumber, expiry) {
    const normalizedCardNumber = cardNumber.replace(/\D/g, '');

    const { data: card, error } = await config.supabase
        .from('cards')
        .select('card_id, user_id, card_number, expiry_date, status')
        .eq('card_number', normalizedCardNumber)
        .single();

    if (error || !card) {
        return { valid: false, error: 'CARD_NOT_FOUND' };
    }

    if (card.status !== 'active') {
        return { valid: false, error: 'CARD_BLOCKED' };
    }

    const normalizedExpiry = String(expiry || '').trim();
    if (normalizedExpiry) {
        const expiryMatch = normalizedExpiry.match(/^(0[1-9]|1[0-2])\/?(\d{2}|\d{4})$/);
        if (!expiryMatch) {
            return { valid: false, error: 'INVALID_EXPIRY_FORMAT' };
        }

        const inputMonth = parseInt(expiryMatch[1]);
        const inputYear = expiryMatch[2].length === 2
            ? 2000 + parseInt(expiryMatch[2])
            : parseInt(expiryMatch[2]);

        const cardExpiry = new Date(card.expiry_date);
        const cardMonth = cardExpiry.getMonth() + 1;
        const cardYear = cardExpiry.getFullYear();

        if (inputYear !== cardYear || inputMonth !== cardMonth) {
            return { valid: false, error: 'EXPIRY_MISMATCH' };
        }
    }

    return {
        valid: true,
        card: {
            card_id: card.card_id,
            user_id: card.user_id,
            masked_card: `**** ${normalizedCardNumber.slice(-4)}`
        }
    };
}

async function findActiveApprovalSession(params) {
    const {
        cardId,
        userId,
        merchantName,
        merchantLocation,
        amount,
        currency
    } = params;

    await expireOldSessions(userId);

    const { data, error } = await config.supabase
        .from('card_payment_sessions')
        .select('*')
        .eq('card_id', cardId)
        .eq('user_id', userId)
        .eq('merchant_name', merchantName)
        .eq('merchant_location', merchantLocation || 'Online')
        .eq('amount', amount)
        .eq('currency', currency)
        .in('status', ACTIVE_APPROVAL_STATUSES)
        .order('created_at', { ascending: false })
        .limit(1)
        .maybeSingle();

    if (error) {
        console.error('Error checking active payment session:', error);
        return null;
    }

    return data || null;
}

/**
 * Creeaza o sesiune de plata noua
 */
async function createPaymentSession(params) {
    const {
        cardId,
        userId,
        maskedCard,
        merchantName,
        merchantLocation,
        amount,
        currency
    } = params;

    const existingSession = await findActiveApprovalSession({
        cardId,
        userId,
        merchantName,
        merchantLocation,
        amount,
        currency
    });

    if (existingSession) {
        const merchantInfo = await merchantService.getMerchantInfo(merchantName);
        return {
            ...existingSession,
            ...merchantInfo,
            reused_session: true
        };
    }

    // Get merchant info for categorization
    const merchantInfo = await merchantService.getMerchantInfo(merchantName);

    const now = new Date();
    const expiresAt = new Date(now.getTime() + SESSION_EXPIRY_MINUTES * 60 * 1000);

    const { data, error } = await config.supabase
        .from('card_payment_sessions')
        .insert({
            card_id: cardId,
            user_id: userId,
            merchant_name: merchantName,
            merchant_location: merchantLocation || 'Online',
            amount: amount,
            currency: currency,
            masked_card: maskedCard,
            status: 'PENDING_APPROVAL',
            expires_at: expiresAt.toISOString()
        })
        .select()
        .single();

    if (error) {
        console.error('Error creating payment session:', error);
        throw new Error('SESSION_CREATE_FAILED');
    }

    // Trimite notificare push catre user
    await notificationService.sendPushNotification(
        userId,
        'Aprobare plata',
        `${amount.toFixed(2)} ${currency} la ${merchantName}`,
        {
            type: 'CARD_PAYMENT_APPROVAL',
            session_id: data.session_id.toString(),
            amount: amount.toString(),
            currency: currency,
            merchant_name: merchantName,
            merchant_location: data.merchant_location,
            masked_card: data.masked_card,
            expires_at: data.expires_at,
            category_icon: merchantInfo.category_icon,
            merchant_logo_url: merchantInfo.merchant_logo_url || ''
        }
    );

    return {
        ...data,
        ...merchantInfo
    };
}

/**
 * Obtine o sesiune dupa ID
 */
async function getSession(sessionId) {
    const { data, error } = await config.supabase
        .from('card_payment_sessions')
        .select('*')
        .eq('session_id', sessionId)
        .single();

    if (error) {
        return null;
    }

    // Verifica daca a expirat
    if (data.status === 'PENDING_APPROVAL' && new Date(data.expires_at) < new Date()) {
        await expireSession(sessionId);
        data.status = 'EXPIRED';
        data.decline_reason = 'Approval timed out';
    }

    // Add merchant info
    const merchantInfo = await merchantService.getMerchantInfo(data.merchant_name);

    return {
        ...data,
        ...merchantInfo
    };
}

/**
 * Obtine sesiunile pending pentru un user
 */
async function getPendingSessions(userId) {
    // Mai intai expiram sesiunile vechi
    await expireOldSessions(userId);

    const { data, error } = await config.supabase
        .from('card_payment_sessions')
        .select('*')
        .eq('user_id', userId)
        .eq('status', 'PENDING_APPROVAL')
        .order('created_at', { ascending: false });

    if (error) {
        console.error('Error getting pending sessions:', error);
        return [];
    }

    return Promise.all((data || []).map(async session => ({
        ...session,
        ...(await merchantService.getMerchantInfo(session.merchant_name))
    })));
}

async function claimPendingSession(sessionId) {
    const claimedAt = new Date().toISOString();
    const { data, error } = await config.supabase
        .from('card_payment_sessions')
        .update({ approved_at: claimedAt })
        .eq('session_id', sessionId)
        .eq('status', 'PENDING_APPROVAL')
        .is('approved_at', null)
        .select()
        .maybeSingle();

    if (error) {
        console.error('Error claiming payment session:', error);
        throw new Error('SESSION_UPDATE_FAILED');
    }

    if (!data) {
        const latest = await getSession(sessionId);
        if (latest && ['APPROVED', 'COMPLETED'].includes(latest.status)) {
            return { session: latest, alreadyHandled: true };
        }
        throw new Error('SESSION_NOT_PENDING');
    }

    return { session: data, alreadyHandled: false };
}

async function releaseProcessingSession(sessionId) {
    await config.supabase
        .from('card_payment_sessions')
        .update({ approved_at: null })
        .eq('session_id', sessionId)
        .eq('status', 'PENDING_APPROVAL');
}

/**
 * Găsește contul pentru plată (stil Revolut simplificat)
 * 1. Cont în valuta plății (dacă există și are fonduri)
 * 2. Cont RON cu conversie (dacă are fonduri)
 * 3. Eroare INSUFFICIENT_FUNDS
 */
async function findBestAccountForPayment(userId, paymentCurrency, paymentAmount) {
    const accounts = await accountService.getAccountsByUserId(userId);

    if (!accounts || accounts.length === 0) {
        return null;
    }

    // 1. Caută cont în valuta plății
    const sameCurrencyAccount = accounts.find(a => a.currency.trim() === paymentCurrency);
    if (sameCurrencyAccount) {
        const available = parseFloat(sameCurrencyAccount.balance) - parseFloat(sameCurrencyAccount.blocked_balance || 0);
        if (available >= paymentAmount) {
            return {
                account: sameCurrencyAccount,
                needsConversion: false,
                exchangeRate: null,
                amountToBlock: paymentAmount
            };
        }
    }

    // 2. Fallback la RON (dacă plata nu e deja în RON)
    if (paymentCurrency !== 'RON') {
        const ronAccount = accounts.find(a => a.currency.trim() === 'RON');
        if (ronAccount) {
            try {
                const rateInfo = await ratesService.getExchangeRate('RON', paymentCurrency);
                // rata RON->EUR = 0.2 (1 RON = 0.2 EUR)
                // Pentru 100 EUR, blocăm: 100 / 0.2 = 500 RON
                const amountToBlock = paymentAmount / rateInfo.rate;

                const available = parseFloat(ronAccount.balance) - parseFloat(ronAccount.blocked_balance || 0);
                if (available >= amountToBlock) {
                    return {
                        account: ronAccount,
                        needsConversion: true,
                        exchangeRate: rateInfo.rate,
                        amountToBlock: amountToBlock
                    };
                }
            } catch (err) {
                console.error(`Error getting rate for RON -> ${paymentCurrency}:`, err);
            }
        }
    }

    // 3. Nu avem fonduri suficiente
    return null;
}

/**
 * Aproba o sesiune de plata
 * Flow: găsește cont (cu conversie dacă e nevoie) -> blochează suma -> creează card_transaction -> sesiune APPROVED
 */
async function approveSession(sessionId, userId) {
    let session = await getSession(sessionId);

    if (!session) {
        throw new Error('SESSION_NOT_FOUND');
    }

    if (session.user_id !== userId) {
        throw new Error('UNAUTHORIZED');
    }

    if (['APPROVED', 'COMPLETED'].includes(session.status)) {
        return session;
    }

    if (session.status !== 'PENDING_APPROVAL') {
        throw new Error('SESSION_NOT_PENDING');
    }

    // Găsește cont: valuta plății → RON → eroare
    const paymentInfo = await findBestAccountForPayment(userId, session.currency, session.amount);

    if (!paymentInfo) {
        throw new Error('INSUFFICIENT_FUNDS');
    }

    const { account, needsConversion, exchangeRate, amountToBlock } = paymentInfo;

    const claimed = await claimPendingSession(sessionId);
    if (claimed.alreadyHandled) {
        return claimed.session;
    }
    session = claimed.session;

    // Blocheaza suma atomic (în valuta contului sursă)
    try {
        await accountService.blockAmount(account.account_id, amountToBlock);
    } catch (err) {
        await releaseProcessingSession(sessionId);
        if (err.message === 'INSUFFICIENT_FUNDS') {
            throw err;
        }
        console.error('Error blocking amount:', err);
        throw new Error('PAYMENT_FAILED');
    }

    // Calculeaza settlement_date (3 zile de acum)
    const settlementDate = new Date();
    settlementDate.setDate(settlementDate.getDate() + 3);

    // Creaza card_transaction cu session_id pentru legatura directa
    // amount = suma blocată în valuta contului
    // original_amount/original_currency = suma plății în valuta originală
    const { data: transaction, error: txError } = await config.supabase
        .from('card_transactions')
        .insert({
            card_id: session.card_id,
            session_id: session.session_id,
            account_id: account.account_id,
            merchant_name: session.merchant_name,
            location: session.merchant_location,
            amount: amountToBlock,
            currency: account.currency.trim(),
            original_amount: session.amount,
            original_currency: session.currency,
            exchange_rate: exchangeRate,
            reference: generateCardReference(),
            status: 'PENDING',
            settlement_date: settlementDate.toISOString().split('T')[0]
        })
        .select()
        .single();

    if (txError) {
        // Rollback - deblocheaza suma
        try {
            await accountService.unblockAmount(account.account_id, amountToBlock);
        } catch (rollbackErr) {
            console.error('Error unblocking amount:', rollbackErr);
        }
        console.error('Error creating card transaction:', txError);
        await releaseProcessingSession(sessionId);
        throw new Error('PAYMENT_FAILED');
    }

    // Actualizeaza sesiunea
    const now = new Date().toISOString();
    const { data, error } = await config.supabase
        .from('card_payment_sessions')
        .update({
            status: 'APPROVED',
            approved_at: now,
            from_account_id: account.account_id
        })
        .eq('session_id', sessionId)
        .eq('status', 'PENDING_APPROVAL')
        .not('approved_at', 'is', null)
        .select()
        .single();

    if (error) {
        // Rollback - deblocheaza suma si sterge tranzactia
        try {
            await accountService.unblockAmount(account.account_id, amountToBlock);
            await config.supabase
                .from('card_transactions')
                .delete()
                .eq('transaction_id', transaction.transaction_id);
            await releaseProcessingSession(sessionId);
        } catch (rollbackErr) {
            console.error('Error rolling back:', rollbackErr);
        }
        throw new Error('SESSION_UPDATE_FAILED');
    }

    // Trimite notificare de confirmare
    let notificationBody;
    if (needsConversion) {
        notificationBody = `Ai făcut o plată la ${session.merchant_name} în valoare de ${session.amount.toFixed(2)} ${session.currency} (${amountToBlock.toFixed(2)} ${account.currency.trim()})`;
    } else {
        notificationBody = `Ai făcut o plată la ${session.merchant_name} în valoare de ${session.amount.toFixed(2)} ${session.currency}`;
    }

    await notificationService.sendPushNotification(
        userId,
        'Plată efectuată',
        notificationBody,
        {
            type: 'CARD_PAYMENT_APPROVED',
            session_id: sessionId.toString(),
            amount: amountToBlock.toString(),
            currency: account.currency.trim(),
            original_amount: session.amount.toString(),
            original_currency: session.currency,
            merchant_name: session.merchant_name
        }
    );

    return {
        ...data,
        reference: transaction.reference,
        transaction_id: transaction.transaction_id,
        ...(await merchantService.getMerchantInfo(data.merchant_name))
    };
}

async function authorizePosPayment(params) {
    const {
        cardId,
        userId,
        maskedCard,
        merchantName,
        merchantLocation,
        amount,
        currency,
        entryMode
    } = params;

    const normalizedEntryMode = normalizeEntryMode(entryMode);

    const paymentInfo = await findBestAccountForPayment(userId, currency, amount);
    if (!paymentInfo) {
        throw new Error('INSUFFICIENT_FUNDS');
    }

    const { account, needsConversion, exchangeRate, amountToBlock } = paymentInfo;

    try {
        await accountService.blockAmount(account.account_id, amountToBlock);
    } catch (err) {
        if (err.message === 'INSUFFICIENT_FUNDS') {
            throw err;
        }
        console.error('Error blocking POS payment amount:', err);
        throw new Error('PAYMENT_FAILED');
    }

    const settlementDate = new Date();
    settlementDate.setDate(settlementDate.getDate() + 3);

    const { data: transaction, error: txError } = await config.supabase
        .from('card_transactions')
        .insert({
            card_id: cardId,
            account_id: account.account_id,
            merchant_name: merchantName,
            location: merchantLocation || 'POS Terminal',
            amount: amountToBlock,
            currency: account.currency.trim(),
            original_amount: amount,
            original_currency: currency,
            exchange_rate: exchangeRate,
            reference: generateCardReference(),
            status: 'PENDING',
            settlement_date: settlementDate.toISOString().split('T')[0]
        })
        .select()
        .single();

    if (txError) {
        try {
            await accountService.unblockAmount(account.account_id, amountToBlock);
        } catch (rollbackErr) {
            console.error('Error rolling back POS blocked amount:', rollbackErr);
        }
        console.error('Error creating POS card transaction:', txError);
        throw new Error('PAYMENT_FAILED');
    }

    const merchantInfo = await merchantService.getMerchantInfo(merchantName);

    await notificationService.sendPushNotification(
        userId,
        'Plată efectuată',
        `Ai făcut o plată la ${merchantName} în valoare de ${amount.toFixed(2)} ${currency}`,
        {
            type: 'CARD_POS_PAYMENT_AUTHORIZED',
            transaction_id: transaction.transaction_id.toString(),
            amount: amountToBlock.toString(),
            currency: account.currency.trim(),
            original_amount: amount.toString(),
            original_currency: currency,
            merchant_name: merchantName
        }
    );

    return {
        ...transaction,
        ...merchantInfo,
        masked_card: maskedCard,
        entry_mode: normalizedEntryMode,
        needs_conversion: needsConversion,
        blocked_amount: amountToBlock,
        blocked_currency: account.currency.trim()
    };
}

/**
 * Refuza o sesiune de plata
 */
async function declineSession(sessionId, userId, reason = 'Declined by user') {
    const session = await getSession(sessionId);

    if (!session) {
        throw new Error('SESSION_NOT_FOUND');
    }

    if (session.user_id !== userId) {
        throw new Error('UNAUTHORIZED');
    }

    if (session.status === 'DECLINED') {
        return session;
    }

    if (session.status !== 'PENDING_APPROVAL') {
        throw new Error('SESSION_NOT_PENDING');
    }

    const { data, error } = await config.supabase
        .from('card_payment_sessions')
        .update({
            status: 'DECLINED',
            declined_at: new Date().toISOString(),
            decline_reason: reason
        })
        .eq('session_id', sessionId)
        .eq('status', 'PENDING_APPROVAL')
        .is('approved_at', null)
        .select()
        .maybeSingle();

    if (error) {
        throw new Error('SESSION_UPDATE_FAILED');
    }

    if (!data) {
        const latest = await getSession(sessionId);
        if (latest && latest.status === 'DECLINED') {
            return latest;
        }
        throw new Error('SESSION_NOT_PENDING');
    }

    // Trimite notificare de refuz
    await notificationService.sendPushNotification(
        userId,
        'Plata refuzata',
        `Ai refuzat plata de ${session.amount.toFixed(2)} ${session.currency} la ${session.merchant_name}`,
        {
            type: 'CARD_PAYMENT_DECLINED',
            session_id: sessionId.toString(),
            amount: session.amount.toString(),
            currency: session.currency,
            merchant_name: session.merchant_name
        }
    );

    return data;
}

/**
 * Expira o sesiune
 */
async function expireSession(sessionId) {
    await config.supabase
        .from('card_payment_sessions')
        .update({
            status: 'EXPIRED',
            decline_reason: 'Approval timed out'
        })
        .eq('session_id', sessionId)
        .eq('status', 'PENDING_APPROVAL');
}

/**
 * Expira toate sesiunile vechi ale unui user
 */
async function expireOldSessions(userId) {
    const { error } = await config.supabase
        .from('card_payment_sessions')
        .update({
            status: 'EXPIRED',
            decline_reason: 'Approval timed out'
        })
        .eq('user_id', userId)
        .eq('status', 'PENDING_APPROVAL')
        .lt('expires_at', new Date().toISOString());

    if (error) {
        console.error('Error expiring old sessions:', error);
    }
}

/**
 * Rezolvă account_id și session_id pentru o tranzacție
 * Acum avem legătură directă prin session_id și account_id în card_transactions
 */
function resolveSettlementTarget(transaction) {
    return {
        accountId: transaction.account_id,
        sessionId: transaction.session_id
    };
}

/**
 * Procesează settlement-ul pentru tranzacțiile PENDING cu settlement_date <= azi
 * Ar trebui apelată de un cron job zilnic
 */
async function processSettlements() {
    const today = new Date().toISOString().split('T')[0];

    // Găsește tranzacțiile care trebuie settled (doar cele cu account_id setat)
    const { data: transactions, error } = await config.supabase
        .from('card_transactions')
        .select('*')
        .eq('status', 'PENDING')
        .not('account_id', 'is', null)
        .lte('settlement_date', today);

    if (error) {
        console.error('Error fetching pending settlements:', error);
        return { processed: 0, failed: 0 };
    }

    let processed = 0;
    let failed = 0;

    for (const tx of transactions) {
        try {
            // Deduce efectiv suma din cont (atomic)
            await accountService.settleBlockedAmount(tx.account_id, tx.amount);

            // Marchează tranzacția ca COMPLETED
            await config.supabase
                .from('card_transactions')
                .update({
                    status: 'COMPLETED',
                    completed_at: new Date().toISOString()
                })
                .eq('transaction_id', tx.transaction_id);

            // Actualizează sesiunea asociată prin session_id direct
            if (tx.session_id) {
                await config.supabase
                    .from('card_payment_sessions')
                    .update({
                        status: 'COMPLETED',
                        completed_at: new Date().toISOString()
                    })
                    .eq('session_id', tx.session_id);
            }

            processed++;
            console.log(`[Settlement] Processed transaction ${tx.transaction_id}`);
        } catch (err) {
            failed++;
            console.error(`[Settlement] Failed to process transaction ${tx.transaction_id}:`, err);
        }
    }

    console.log(`[Settlement] Completed: ${processed} processed, ${failed} failed`);
    return { processed, failed };
}

/**
 * Anulează o tranzacție PENDING (refund înainte de settlement)
 */
async function cancelPendingTransaction(transactionId) {
    const { data: tx, error } = await config.supabase
        .from('card_transactions')
        .select('*')
        .eq('transaction_id', transactionId)
        .eq('status', 'PENDING')
        .single();

    if (error || !tx) {
        throw new Error('TRANSACTION_NOT_FOUND');
    }

    if (!tx.account_id) {
        throw new Error('ACCOUNT_NOT_FOUND');
    }

    // Deblochează suma (atomic)
    await accountService.unblockAmount(tx.account_id, tx.amount);

    // Marchează ca CANCELLED
    await config.supabase
        .from('card_transactions')
        .update({
            status: 'CANCELLED',
            completed_at: new Date().toISOString()
        })
        .eq('transaction_id', transactionId);

    // Actualizează și sesiunea dacă există
    if (tx.session_id) {
        await config.supabase
            .from('card_payment_sessions')
            .update({
                status: 'CANCELLED',
                decline_reason: 'Transaction cancelled before settlement'
            })
            .eq('session_id', tx.session_id);
    }

    return { success: true };
}

export default {
    validateCard,
    validateCardForPos,
    createPaymentSession,
    getSession,
    getPendingSessions,
    approveSession,
    declineSession,
    expireSession,
    processSettlements,
    cancelPendingTransaction,
    checkPaymentIdempotency,
    savePaymentIdempotency,
    authorizePosPayment,
    POS_PAYMENT_ENDPOINT
};
