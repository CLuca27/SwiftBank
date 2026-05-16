import supabase from '../config/supabase.js';
import accountService from './account-service.js';
import merchantService from './merchant-service.js';
import logoService from './logo-service.js';

function parseAmount(value) {
    return parseFloat(value || 0);
}

function normalizeCurrency(currency) {
    return currency ? currency.trim().toUpperCase() : '';
}

function parseDate(value) {
    const date = value ? new Date(value) : null;
    return date && !Number.isNaN(date.getTime()) ? date : new Date(0);
}

function resolveSessionAccount(session, accounts) {
    const sessionCurrency = normalizeCurrency(session.currency);

    return accounts.find(account => normalizeCurrency(account.currency) === sessionCurrency)
        || accounts.find(account => normalizeCurrency(account.currency) === 'RON')
        || null;
}

async function getMerchantTransactionInfo(merchantName) {
    const merchantInfo = await merchantService.getMerchantInfo(merchantName);

    return {
        merchant_logo_url: merchantInfo.merchant_logo_url,
        category_name: merchantInfo.category_name,
        category_icon: merchantInfo.category_icon
    };
}

function isWithinDateRange(createdAt, startDate, endDate) {
    const createdTime = parseDate(createdAt).getTime();

    if (startDate && createdTime < parseDate(startDate).getTime()) {
        return false;
    }

    if (endDate && createdTime > parseDate(endDate).getTime()) {
        return false;
    }

    return true;
}

async function expireOldPendingSessions(userId) {
    const { error } = await supabase
        .from('card_payment_sessions')
        .update({
            status: 'EXPIRED',
            decline_reason: 'Approval timed out'
        })
        .eq('user_id', userId)
        .eq('status', 'PENDING_APPROVAL')
        .lt('expires_at', new Date().toISOString());

    if (error) {
        console.error('Error expiring old card payment sessions:', error);
    }
}

async function getPendingApprovalTransactions(userId, accountId, options) {
    const { transactionType, startDate, endDate } = options;

    if (transactionType && !['CARD', 'CARD_PENDING_APPROVAL'].includes(transactionType)) {
        return [];
    }

    await expireOldPendingSessions(userId);

    const accounts = await accountService.getAccountsByUserId(userId);

    const { data, error } = await supabase
        .from('card_payment_sessions')
        .select('*')
        .eq('user_id', userId)
        .eq('status', 'PENDING_APPROVAL')
        .order('created_at', { ascending: false });

    if (error) {
        console.error('Error fetching pending card payment sessions:', error);
        return [];
    }

    const transactions = await Promise.all((data || [])
        .map(async session => {
            const account = resolveSessionAccount(session, accounts);
            if (!account) return null;

            const merchantInfo = await getMerchantTransactionInfo(session.merchant_name);

            return {
                user_id: userId,
                account_id: account.account_id,
                id: Number(session.session_id),
                session_id: Number(session.session_id),
                transaction_type: 'CARD_PENDING_APPROVAL',
                title: session.merchant_name,
                subtitle: 'Confirmare necesară',
                amount: -parseAmount(session.amount),
                currency: normalizeCurrency(session.currency),
                original_amount: parseAmount(session.amount),
                original_currency: normalizeCurrency(session.currency),
                exchange_rate: null,
                created_at: session.created_at,
                status: session.status,
                merchant_name: session.merchant_name,
                location: session.merchant_location,
                masked_card: session.masked_card,
                card_number_masked: session.masked_card,
                expires_at: session.expires_at,
                ...merchantInfo,
                beneficiary_name: null,
                iban: null,
                bank_name: null,
                reference: null,
                description: null,
                transfer_type: null,
                sender_photo: null
            };
        }));

    return transactions
        .filter(Boolean)
        .filter(transaction => transaction.account_id === accountId)
        .filter(transaction => isWithinDateRange(transaction.created_at, startDate, endDate));
}

async function enrichCardTransactions(transactions) {
    const cardTransactionIds = transactions
        .filter(transaction => transaction.transaction_type === 'CARD')
        .map(transaction => transaction.id);

    if (cardTransactionIds.length === 0) {
        return transactions;
    }

    const { data, error } = await supabase
        .from('card_transactions')
        .select('transaction_id, session_id, status, settlement_date, location, merchant_name, reference')
        .in('transaction_id', cardTransactionIds);

    if (error) {
        console.error('Error enriching card transactions:', error);
        return transactions;
    }

    const enrichedCards = await Promise.all((data || []).map(async card => ({
        ...card,
        ...(await getMerchantTransactionInfo(card.merchant_name))
    })));

    const cardById = new Map(enrichedCards.map(transaction => [transaction.transaction_id, transaction]));

    return transactions.map(transaction => {
        if (transaction.transaction_type !== 'CARD') {
            return transaction;
        }

        const card = cardById.get(transaction.id);
        if (!card) {
            return transaction;
        }

        const status = card.status || transaction.status;
        const categorySubtitle = card.category_name || transaction.subtitle || 'Plata cu cardul';
        return {
            ...transaction,
            status,
            session_id: card.session_id || transaction.session_id,
            settlement_date: card.settlement_date,
            location: card.location || transaction.location,
            merchant_name: transaction.title,
            reference: card.reference || transaction.reference,
            merchant_logo_url: card.merchant_logo_url,
            category_name: card.category_name,
            category_icon: card.category_icon,
            subtitle: status === 'PENDING' ? 'Suma blocata' : categorySubtitle
        };
    });
}

async function enrichBillTransactions(transactions) {
    const billPaymentIds = transactions
        .filter(transaction => transaction.transaction_type === 'BILL')
        .map(transaction => transaction.id);

    if (billPaymentIds.length === 0) {
        return transactions;
    }

    const { data, error } = await supabase
        .from('bill_payments')
        .select(`
            payment_id,
            status,
            client_code,
            invoice_reference,
            billers (
                name,
                domain,
                categories (
                    name
                )
            )
        `)
        .in('payment_id', billPaymentIds);

    if (error) {
        console.error('Error enriching bill transactions:', error);
        return transactions;
    }

    const billById = new Map((data || []).map(payment => [payment.payment_id, payment]));

    return transactions.map(transaction => {
        if (transaction.transaction_type !== 'BILL') {
            return transaction;
        }

        const payment = billById.get(transaction.id);
        if (!payment) {
            return transaction;
        }

        const biller = Array.isArray(payment.billers) ? payment.billers[0] : payment.billers;
        const category = biller?.categories?.name
            || transaction.category_name
            || transaction.subtitle
            || 'other';
        const logoUrl = logoService.buildLogoUrl(biller?.domain);

        return {
            ...transaction,
            title: biller?.name || transaction.title,
            subtitle: category,
            status: payment.status || transaction.status,
            biller_name: biller?.name || transaction.title,
            biller_category: category,
            client_code: payment.client_code || transaction.reference,
            invoice_reference: payment.invoice_reference || transaction.description,
            category_name: category,
            merchant_name: biller?.name || transaction.title,
            merchant_logo_url: logoUrl,
            biller_logo_url: logoUrl
        };
    });
}

/**
 * Obtine tranzactiile unui user din VIEW-ul user_transactions
 * @param {number} userId - ID-ul userului
 * @param {Object} options - Optiuni de filtrare si paginare
 * @returns {Promise<{transactions: Array, hasMore: boolean}>}
 */
async function getTransactions(userId, options = {}) {
    const {
        accountId = null,
        limit = 20,
        offset = 0,
        transactionType = null,  // 'CARD', 'TRANSFER', 'BILL'
        startDate = null,
        endDate = null
    } = options;

    const pageEnd = offset + limit;

    let query = supabase
        .from('user_transactions')
        .select('*')
        .eq('user_id', userId)
        .order('created_at', { ascending: false });

    // Filtrare pe account_id (obligatoriu)
    query = query.eq('account_id', accountId);

    // Filtrare pe tip tranzactie
    if (transactionType) {
        query = query.eq('transaction_type', transactionType);
    }

    // Filtrare pe perioada
    if (startDate) {
        query = query.gte('created_at', startDate);
    }
    if (endDate) {
        query = query.lte('created_at', endDate);
    }

    // Luam de la inceput ca sa putem imbina corect tranzactiile reale cu sesiunile pending.
    query = query.range(0, pageEnd);

    const { data, error } = await query;

    if (error) {
        throw error;
    }

    const cardTransactions = await enrichCardTransactions(data || []);
    const baseTransactions = await enrichBillTransactions(cardTransactions);
    const pendingApprovalTransactions = await getPendingApprovalTransactions(userId, accountId, {
        transactionType,
        startDate,
        endDate
    });

    const mergedTransactions = [...baseTransactions, ...pendingApprovalTransactions]
        .sort((a, b) => parseDate(b.created_at).getTime() - parseDate(a.created_at).getTime());

    const page = mergedTransactions.slice(offset, offset + limit);
    const hasMore = mergedTransactions.length > offset + limit;

    return {
        transactions: page,
        hasMore
    };
}

export default {
    getTransactions
};
