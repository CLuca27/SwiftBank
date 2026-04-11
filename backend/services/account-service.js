import supabase from '../config/supabase.js';

const BANK_CODE = 'SWFT';

/**
 * Genereaza un IBAN romanesc valid
 * Format: RO + 2 cifre control + SWFT + 16 cifre cont
 */
function generateIBAN() {
    // Genereaza 16 cifre aleatorii pentru numarul de cont
    let accountNumber = '';
    for (let i = 0; i < 16; i++) {
        accountNumber += Math.floor(Math.random() * 10);
    }

    // BBAN = cod banca (4 litere) + numar cont (16 cifre)
    const bban = BANK_CODE + accountNumber;

    // Calculam cifrele de control conform ISO 7064
    // Mutam RO00 la sfarsit si inlocuim literele cu numere (A=10, B=11, etc.)
    const rearranged = bban + 'RO00';
    let numericString = '';
    for (const char of rearranged) {
        if (char >= 'A' && char <= 'Z') {
            numericString += (char.charCodeAt(0) - 55).toString();
        } else {
            numericString += char;
        }
    }

    // Calculam modulo 97
    let remainder = BigInt(numericString) % 97n;
    let checkDigits = (98n - remainder).toString().padStart(2, '0');

    return 'RO' + checkDigits + bban;
}

/**
 * Creeaza un cont nou pentru un user
 */
async function createAccount(userId, currency, accountType = 'CURRENT') {
    const iban = generateIBAN();

    const { data, error } = await supabase
        .from('accounts')
        .insert({
            user_id: userId,
            iban: iban,
            account_type: accountType,
            currency: currency,
            balance: 0,
            status: 'ACTIVE'
        })
        .select()
        .single();

    if (error) {
        throw error;
    }

    return data;
}

/**
 * Verifica daca un user are deja un cont intr-o anumita valuta
 */
async function hasAccountInCurrency(userId, currency) {
    const { data, error } = await supabase
        .from('accounts')
        .select('account_id')
        .eq('user_id', userId)
        .eq('currency', currency)
        .eq('status', 'ACTIVE')
        .maybeSingle();

    if (error) {
        throw error;
    }

    return !!data;
}

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

/**
 * Obtine un cont dupa ID
 */
async function getAccountById(accountId) {
    const { data, error } = await supabase
        .from('accounts')
        .select('*')
        .eq('account_id', accountId)
        .single();

    if (error) {
        throw error;
    }

    return data;
}

/**
 * Actualizeaza balanta unui cont
 */
async function updateBalance(accountId, newBalance) {
    const { data, error } = await supabase
        .from('accounts')
        .update({ balance: newBalance })
        .eq('account_id', accountId)
        .select()
        .single();

    if (error) {
        throw error;
    }

    return data;
}

/**
 * Executa un schimb valutar intre doua conturi ale aceluiasi user
 */
async function exchangeCurrency(userId, fromAccountId, toAccountId, amount, exchangeRate) {
    // Verifica ownership pentru ambele conturi
    const fromOwnership = await verifyAccountOwnership(fromAccountId, userId);
    const toOwnership = await verifyAccountOwnership(toAccountId, userId);

    if (!fromOwnership || !toOwnership) {
        throw new Error('UNAUTHORIZED_ACCOUNT');
    }

    // Obtine conturile
    const fromAccount = await getAccountById(fromAccountId);
    const toAccount = await getAccountById(toAccountId);

    if (!fromAccount || !toAccount) {
        throw new Error('ACCOUNT_NOT_FOUND');
    }

    if (fromAccount.currency === toAccount.currency) {
        throw new Error('SAME_CURRENCY');
    }

    // Verifica balanta
    const currentBalance = parseFloat(fromAccount.balance);
    if (currentBalance < amount) {
        throw new Error('INSUFFICIENT_FUNDS');
    }

    // Calculeaza suma convertita
    const convertedAmount = amount * exchangeRate;

    // Actualizeaza balantele
    const newFromBalance = currentBalance - amount;
    const newToBalance = parseFloat(toAccount.balance) + convertedAmount;

    await updateBalance(fromAccountId, newFromBalance);
    await updateBalance(toAccountId, newToBalance);

    // Genereaza referinta unica pentru tranzactie
    const reference = `EX${Date.now()}${Math.random().toString(36).substring(2, 8).toUpperCase()}`;

    // Insereaza in tabela transfers pentru a aparea in istoric
    const { error: transferError } = await supabase
        .from('transfers')
        .insert({
            from_account_id: fromAccountId,
            to_account_id: toAccountId,
            to_iban: toAccount.iban,
            beneficiary_name: 'Schimb valutar',
            amount: convertedAmount,
            currency: toAccount.currency.trim(),
            original_amount: amount,
            original_currency: fromAccount.currency.trim(),
            exchange_rate: exchangeRate,
            reference: reference,
            description: `Schimb ${fromAccount.currency.trim()} → ${toAccount.currency.trim()}`,
            status: 'COMPLETED',
            transfer_type: 'INTERNAL',
            scheduled_date: new Date().toISOString().split('T')[0],
            completed_at: new Date().toISOString()
        });

    if (transferError) {
        console.error('Error creating transfer record:', transferError);
        // Nu aruncam eroare, schimbul a fost efectuat deja
    }

    return {
        fromAccount: {
            account_id: fromAccount.account_id,
            currency: fromAccount.currency.trim(),
            oldBalance: currentBalance,
            newBalance: newFromBalance,
            amount: -amount
        },
        toAccount: {
            account_id: toAccount.account_id,
            currency: toAccount.currency.trim(),
            oldBalance: parseFloat(toAccount.balance),
            newBalance: newToBalance,
            amount: convertedAmount
        },
        exchangeRate: exchangeRate
    };
}

export default {
    generateIBAN,
    createAccount,
    hasAccountInCurrency,
    getAccountsByUserId,
    verifyAccountOwnership,
    getAccountById,
    updateBalance,
    exchangeCurrency
};
