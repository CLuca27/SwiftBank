import services from '../../services/index.js';

const ALLOWED_CURRENCIES = ['EUR', 'USD', 'GBP'];

async function getAccounts(req, res) {
    try {
        const userId = req.user.user_id;

        const accounts = await services.accountService.getAccountsByUserId(userId);

        return res.status(200).json({
            success: true,
            data: {
                accounts: accounts.map(acc => ({
                    account_id: acc.account_id,
                    iban: acc.iban,
                    account_type: acc.account_type,
                    currency: acc.currency.trim(),
                    balance: parseFloat(acc.balance)
                }))
            }
        });

    } catch (error) {
        console.error('Error getting accounts:', error);
        return res.status(500).json({
            success: false,
            error: {
                code: 'INTERNAL_SERVER_ERROR',
                message: 'Nu am putut obtine conturile'
            }
        });
    }
}

async function addAccount(req, res) {
    try {
        const userId = req.user.user_id;
        const { currency } = req.body;

        // Validare valuta
        if (!currency) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'MISSING_CURRENCY',
                    message: 'Valuta este obligatorie'
                }
            });
        }

        const normalizedCurrency = currency.toUpperCase().trim();

        if (!ALLOWED_CURRENCIES.includes(normalizedCurrency)) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'INVALID_CURRENCY',
                    message: 'Valută invalidă. Opțiuni: EUR, USD, GBP'
                }
            });
        }

        // Verifică dacă există deja cont în această valută
        const hasAccount = await services.accountService.hasAccountInCurrency(userId, normalizedCurrency);

        if (hasAccount) {
            return res.status(409).json({
                success: false,
                error: {
                    code: 'ACCOUNT_EXISTS',
                    message: `Ai deja un cont în ${normalizedCurrency}`
                }
            });
        }

        // Crează contul
        const newAccount = await services.accountService.createAccount(userId, normalizedCurrency, 'CURRENT');

        return res.status(201).json({
            success: true,
            message: `Cont ${normalizedCurrency} creat cu succes`,
            data: {
                account: {
                    account_id: newAccount.account_id,
                    iban: newAccount.iban,
                    account_type: newAccount.account_type,
                    currency: newAccount.currency.trim(),
                    balance: parseFloat(newAccount.balance)
                }
            }
        });

    } catch (error) {
        console.error('Error adding account:', error);
        return res.status(500).json({
            success: false,
            error: {
                code: 'INTERNAL_SERVER_ERROR',
                message: 'Nu am putut crea contul'
            }
        });
    }
}

async function exchange(req, res) {
    try {
        const userId = req.user.user_id;
        const { fromAccountId, toAccountId, amount } = req.body;

        // Validari
        if (!fromAccountId || !toAccountId) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'MISSING_ACCOUNTS',
                    message: 'Selectează conturile pentru schimb'
                }
            });
        }

        if (!amount || amount <= 0) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'INVALID_AMOUNT',
                    message: 'Suma trebuie să fie mai mare decât 0'
                }
            });
        }

        // Obtine conturile pentru a afla valutele
        const fromAccount = await services.accountService.getAccountById(fromAccountId);
        const toAccount = await services.accountService.getAccountById(toAccountId);

        if (!fromAccount || !toAccount) {
            return res.status(404).json({
                success: false,
                error: {
                    code: 'ACCOUNT_NOT_FOUND',
                    message: 'Unul dintre conturi nu a fost găsit'
                }
            });
        }

        // Obtine rata de schimb
        const fromCurrency = fromAccount.currency.trim();
        const toCurrency = toAccount.currency.trim();
        const rateInfo = await services.ratesService.getExchangeRate(fromCurrency, toCurrency);

        // Executa schimbul
        const result = await services.accountService.exchangeCurrency(
            userId,
            fromAccountId,
            toAccountId,
            parseFloat(amount),
            rateInfo.rate
        );

        return res.status(200).json({
            success: true,
            message: 'Schimb valutar efectuat cu succes',
            data: {
                from: {
                    account_id: result.fromAccount.account_id,
                    currency: result.fromAccount.currency,
                    amount: result.fromAccount.amount,
                    newBalance: result.fromAccount.newBalance
                },
                to: {
                    account_id: result.toAccount.account_id,
                    currency: result.toAccount.currency,
                    amount: result.toAccount.amount,
                    newBalance: result.toAccount.newBalance
                },
                exchangeRate: result.exchangeRate
            }
        });

    } catch (error) {
        console.error('Error exchanging currency:', error);

        if (error.message === 'UNAUTHORIZED_ACCOUNT') {
            return res.status(403).json({
                success: false,
                error: {
                    code: 'UNAUTHORIZED',
                    message: 'Nu ai acces la unul dintre conturi'
                }
            });
        }

        if (error.message === 'INSUFFICIENT_FUNDS') {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'INSUFFICIENT_FUNDS',
                    message: 'Fonduri insuficiente'
                }
            });
        }

        if (error.message === 'SAME_CURRENCY') {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'SAME_CURRENCY',
                    message: 'Nu poți schimba între aceeași valută'
                }
            });
        }

        return res.status(500).json({
            success: false,
            error: {
                code: 'INTERNAL_SERVER_ERROR',
                message: 'Nu am putut efectua schimbul'
            }
        });
    }
}

export default {
    getAccounts,
    addAccount,
    exchange
};
