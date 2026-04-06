import supabase from '../config/supabase.js';

/**
 * Obtine toate conturile unui user
 * Ordonate: RON primul, apoi restul dupa data crearii
 */
async function getAccountsByUserId(userId) {
    const { data, error } = await supabase
        .from('accounts')
        .select('account_id, iban, account_type, currency, balance, status, created_at')
        .eq('user_id', userId)
        .eq('status', 'ACTIVE')
        .order('created_at', { ascending: true });

    if (error) {
        throw error;
    }

    // RON primul, restul in ordinea crearii
    const sorted = data.sort((a, b) => {
        if (a.currency === 'RON') return -1;
        if (b.currency === 'RON') return 1;
        return new Date(a.created_at) - new Date(b.created_at);
    });

    return sorted;
}

/**
 * Verifica daca un account apartine unui user
 */
async function verifyAccountOwnership(accountId, userId) {
    const { data, error } = await supabase
        .from('accounts')
        .select('account_id')
        .eq('account_id', accountId)
        .eq('user_id', userId)
        .single();

    if (error || !data) {
        return false;
    }

    return true;
}

export default {
    getAccountsByUserId,
    verifyAccountOwnership
};
