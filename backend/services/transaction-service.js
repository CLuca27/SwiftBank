import supabase from '../config/supabase.js';

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

    // Paginare - luam limit + 1 ca sa stim daca mai sunt
    query = query.range(offset, offset + limit);

    const { data, error } = await query;

    if (error) {
        throw error;
    }

    // Verificam daca mai sunt rezultate
    const hasMore = data.length > limit;
    const transactions = hasMore ? data.slice(0, limit) : data;

    return {
        transactions,
        hasMore
    };
}

export default {
    getTransactions
};
