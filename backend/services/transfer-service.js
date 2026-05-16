import config from '../config/index.js';
import accountService from './account-service.js';
import ratesService from './rates-service.js';
import notificationService from './notification-service.js';
import crypto from 'crypto';

const SWIFTBANK_BIC = 'SWFT';

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
 * Extrage BIC-ul din IBAN
 * IBAN format: RO + 2 cifre control + 4 litere BIC + 16 cifre cont
 */
function extractBIC(iban) {
    if (!iban || iban.length < 8) return null;
    // Caracterele 5-8 (index 4-7) sunt BIC-ul
    return iban.substring(4, 8).toUpperCase();
}

/**
 * Validare IBAN romanesc
 */
function validateIBAN(iban) {
    if (!iban) return { valid: false, error: 'IBAN este obligatoriu' };

    // Normalizare
    const normalized = iban.replace(/\s/g, '').toUpperCase();

    // Verificare lungime (RO = 24 caractere)
    if (normalized.length !== 24) {
        return { valid: false, error: 'IBAN-ul trebuie să aibă 24 de caractere' };
    }

    // Verificare prefix RO
    if (!normalized.startsWith('RO')) {
        return { valid: false, error: 'IBAN-ul trebuie să înceapă cu RO' };
    }

    // Verificare format (RO + 2 cifre + 4 litere + 16 alfanumerice)
    const regex = /^RO[0-9]{2}[A-Z]{4}[A-Z0-9]{16}$/;
    if (!regex.test(normalized)) {
        return { valid: false, error: 'Format IBAN invalid' };
    }

    // Verificare cifre de control (ISO 7064)
    const rearranged = normalized.substring(4) + normalized.substring(0, 4);
    let numericString = '';
    for (const char of rearranged) {
        if (char >= 'A' && char <= 'Z') {
            numericString += (char.charCodeAt(0) - 55).toString();
        } else {
            numericString += char;
        }
    }

    const remainder = BigInt(numericString) % 97n;
    if (remainder !== 1n) {
        return { valid: false, error: 'IBAN invalid (cifre de control incorecte)' };
    }

    return { valid: true, normalized };
}

/**
 * Caută banca după BIC
 */
async function getBankByBIC(bic) {
    const { data, error } = await config.supabase
        .from('banks')
        .select('bank_id, bic, name')
        .eq('bic', bic)
        .maybeSingle();

    if (error) {
        console.error('Error fetching bank by BIC:', error);
        return null;
    }

    return data;
}

/**
 * Caută cont SwiftBank după IBAN
 * Returnează și datele utilizatorului (pentru beneficiary_name și profile_photo)
 */
async function getSwiftBankAccountByIBAN(iban) {
    const { data, error } = await config.supabase
        .from('accounts')
        .select(`
            account_id,
            iban,
            currency,
            balance,
            user_id,
            users (
                user_id,
                first_name,
                last_name,
                profile_photo
            )
        `)
        .eq('iban', iban)
        .eq('status', 'ACTIVE')
        .maybeSingle();

    if (error) {
        console.error('Error fetching account by IBAN:', error);
        return null;
    }

    return data;
}

/**
 * Caută beneficiar existent pentru un user
 */
async function getBeneficiaryByIBAN(userId, iban) {
    const { data, error } = await config.supabase
        .from('beneficiaries')
        .select('*')
        .eq('user_id', userId)
        .eq('iban', iban)
        .maybeSingle();

    if (error) {
        console.error('Error fetching beneficiary:', error);
        return null;
    }

    return data;
}

/**
 * Salvează un beneficiar nou (dacă nu există deja)
 */
async function saveBeneficiary(userId, iban, name, bankName) {
    // Verifică dacă există deja
    const existing = await getBeneficiaryByIBAN(userId, iban);
    if (existing) {
        return existing;
    }

    const { data, error } = await config.supabase
        .from('beneficiaries')
        .insert({
            user_id: userId,
            iban: iban,
            name: name,
            bank_name: bankName
        })
        .select()
        .single();

    if (error) {
        console.error('Error saving beneficiary:', error);
        // Nu aruncăm eroare, beneficiarul nu e critic
        return null;
    }

    return data;
}

/**
 * Verifică dacă un idempotency key există și returnează răspunsul cached
 */
async function checkIdempotencyKey(userId, idempotencyKey, endpoint) {
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
        console.error('Error checking idempotency key:', error);
        return null;
    }

    return data;
}

/**
 * Salvează idempotency key cu răspunsul
 */
async function saveIdempotencyKey(userId, idempotencyKey, endpoint, responseStatus, responseBody) {
    // Expiră în 24 de ore
    const expiresAt = new Date();
    expiresAt.setHours(expiresAt.getHours() + 24);

    await insertIdempotencyRecord({
        user_id: userId,
        idempotency_key: idempotencyKey,
        endpoint: endpoint,
        response_status: responseStatus,
        response_body: responseBody,
        expires_at: expiresAt.toISOString()
    }, 'Error saving idempotency key:');
}

/**
 * Generează o referință unică pentru transfer
 */
function generateReference() {
    const timestamp = Date.now().toString(36).toUpperCase();
    const random = Math.random().toString(36).substring(2, 8).toUpperCase();
    return `TR${timestamp}${random}`;
}

/**
 * Obține lista beneficiarilor unui user
 * Include și profile_photo pentru beneficiarii SwiftBank
 */
async function getBeneficiaries(userId) {
    const { data, error } = await config.supabase
        .from('beneficiaries')
        .select('*')
        .eq('user_id', userId)
        .order('created_at', { ascending: false });

    if (error) {
        console.error('Error fetching beneficiaries:', error);
        throw error;
    }

    if (!data || data.length === 0) {
        return [];
    }

    // Adaugă profile_photo pentru beneficiarii SwiftBank
    const enrichedBeneficiaries = await Promise.all(data.map(async (beneficiary) => {
        if (beneficiary.bank_name === 'SwiftBank') {
            const account = await getSwiftBankAccountByIBAN(beneficiary.iban);
            if (account && account.users) {
                return {
                    ...beneficiary,
                    profile_photo: account.users.profile_photo || null
                };
            }
        }
        return { ...beneficiary, profile_photo: null };
    }));

    return enrichedBeneficiaries;
}

/**
 * Șterge un beneficiar
 */
async function deleteBeneficiary(userId, beneficiaryId) {
    const { data, error } = await config.supabase
        .from('beneficiaries')
        .delete()
        .eq('beneficiary_id', beneficiaryId)
        .eq('user_id', userId)
        .select()
        .single();

    if (error) {
        console.error('Error deleting beneficiary:', error);
        throw error;
    }

    return data;
}

/**
 * Execută un transfer
 */
async function executeTransfer(userId, params) {
    const {
        fromAccountId,
        toIBAN,
        beneficiaryName,
        amount,
        description
    } = params;

    // 1. Verifică ownership cont sursă
    const fromAccount = await accountService.getAccountById(fromAccountId);
    if (!fromAccount) {
        throw new Error('SOURCE_ACCOUNT_NOT_FOUND');
    }

    const isOwner = await accountService.verifyAccountOwnership(fromAccountId, userId);
    if (!isOwner) {
        throw new Error('UNAUTHORIZED_ACCOUNT');
    }

    // 2. Validează IBAN destinație
    const ibanValidation = validateIBAN(toIBAN);
    if (!ibanValidation.valid) {
        throw new Error('INVALID_IBAN');
    }
    const normalizedIBAN = ibanValidation.normalized;

    // 3. Verifică să nu fie același cont
    if (fromAccount.iban === normalizedIBAN) {
        throw new Error('SAME_ACCOUNT');
    }

    // 4. Determină tipul transferului și contul destinație
    const bic = extractBIC(normalizedIBAN);
    const isInternal = bic === SWIFTBANK_BIC;

    let toAccountId = null;
    let toAccount = null;
    let finalBeneficiaryName = beneficiaryName;
    let bankName = null;

    if (isInternal) {
        // Transfer intern - căutăm contul destinație
        toAccount = await getSwiftBankAccountByIBAN(normalizedIBAN);
        if (!toAccount) {
            throw new Error('DESTINATION_ACCOUNT_NOT_FOUND');
        }
        toAccountId = toAccount.account_id;

        // Folosim numele real al beneficiarului
        if (toAccount.users) {
            finalBeneficiaryName = `${toAccount.users.first_name} ${toAccount.users.last_name}`;
        }
        bankName = 'SwiftBank';
    } else {
        // Transfer extern - căutăm banca
        const bank = await getBankByBIC(bic);
        bankName = bank ? bank.name : 'Bancă necunoscută';
    }

    // 5. Verifică și calculează conversie valutară dacă e necesar
    const fromCurrency = fromAccount.currency.trim();
    let toCurrency = fromCurrency;
    let exchangeRate = null;
    let originalAmount = null;
    let originalCurrency = null;
    let finalAmount = parseFloat(amount);

    if (isInternal && toAccount) {
        toCurrency = toAccount.currency.trim();

        if (fromCurrency !== toCurrency) {
            // Avem nevoie de conversie
            const rateInfo = await ratesService.getExchangeRate(fromCurrency, toCurrency);
            exchangeRate = rateInfo.rate;
            originalAmount = finalAmount;
            originalCurrency = fromCurrency;
            finalAmount = originalAmount * exchangeRate;
        }
    }

    // 6. Calculează suma de debitat
    const debitAmount = originalAmount || finalAmount;

    // 7. Execută transferul atomic (actualizare balanțe)
    // Debitează contul sursă (suma negativă)
    let fromResult;
    try {
        fromResult = await accountService.adjustBalance(fromAccountId, -debitAmount);
    } catch (err) {
        if (err.message === 'INSUFFICIENT_FUNDS') {
            throw new Error('INSUFFICIENT_FUNDS');
        }
        throw err;
    }

    // Creditează contul destinatar (doar pentru transfer intern)
    if (isInternal && toAccountId) {
        await accountService.adjustBalance(toAccountId, finalAmount);
    }

    // 8. Generează referință și inserează transfer
    const reference = generateReference();
    const now = new Date();

    const transferData = {
        from_account_id: fromAccountId,
        to_iban: normalizedIBAN,
        to_account_id: toAccountId,
        beneficiary_name: finalBeneficiaryName,
        amount: finalAmount,
        currency: toCurrency,
        original_amount: originalAmount,
        original_currency: originalCurrency,
        exchange_rate: exchangeRate,
        reference: reference,
        description: description || null,
        status: 'COMPLETED',
        transfer_type: isInternal ? (toAccount && toAccount.user_id === userId ? 'SELF' : 'INTERNAL') : 'EXTERNAL',
        scheduled_date: now.toISOString().split('T')[0],
        completed_at: now.toISOString()
    };

    const { data: transfer, error: transferError } = await config.supabase
        .from('transfers')
        .insert(transferData)
        .select()
        .single();

    if (transferError) {
        console.error('Error creating transfer:', transferError);
        // Revert balances (atomic rollback)
        await accountService.adjustBalance(fromAccountId, debitAmount);
        if (isInternal && toAccountId) {
            await accountService.adjustBalance(toAccountId, -finalAmount);
        }
        throw new Error('TRANSFER_FAILED');
    }

    // 9. Salvează beneficiarul (dacă nu există)
    await saveBeneficiary(userId, normalizedIBAN, finalBeneficiaryName, bankName);

    // 10. Trimite notificări push
    try {
        // Notifică expeditorul (pentru toate transferurile)
        await notificationService.notifyTransferSent(
            userId,
            finalBeneficiaryName,
            originalAmount || finalAmount,
            originalCurrency || toCurrency
        );

        // Notifică destinatarul (doar pentru transfer intern)
        if (isInternal && toAccount && toAccount.user_id) {
            // Obține numele expeditorului
            const { data: senderUser } = await config.supabase
                .from('users')
                .select('first_name, last_name')
                .eq('user_id', userId)
                .single();

            const senderName = senderUser
                ? `${senderUser.first_name} ${senderUser.last_name}`
                : 'Utilizator SwiftBank';

            await notificationService.notifyTransferReceived(
                toAccount.user_id,
                senderName,
                finalAmount,
                toCurrency
            );
        }
    } catch (notifError) {
        // Nu blocăm transferul dacă notificarea eșuează
        console.error('Error sending transfer notification:', notifError);
    }

    return {
        transfer_id: transfer.transfer_id,
        reference: transfer.reference,
        from_account: {
            account_id: fromAccount.account_id,
            iban: fromAccount.iban,
            currency: fromCurrency,
            new_balance: fromResult.newBalance
        },
        to_iban: normalizedIBAN,
        beneficiary_name: finalBeneficiaryName,
        bank_name: bankName,
        amount: finalAmount,
        currency: toCurrency,
        original_amount: originalAmount,
        original_currency: originalCurrency,
        exchange_rate: exchangeRate,
        transfer_type: isInternal ? (toAccount && toAccount.user_id === userId ? 'SELF' : 'INTERNAL') : 'EXTERNAL',
        status: 'COMPLETED',
        created_at: transfer.created_at
    };
}

export default {
    extractBIC,
    validateIBAN,
    getBankByBIC,
    getSwiftBankAccountByIBAN,
    getBeneficiaryByIBAN,
    saveBeneficiary,
    checkIdempotencyKey,
    saveIdempotencyKey,
    generateReference,
    getBeneficiaries,
    deleteBeneficiary,
    executeTransfer,
    SWIFTBANK_BIC
};
