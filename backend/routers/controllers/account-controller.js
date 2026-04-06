import services from '../../services/index.js';

async function getAccounts(req, res) {
    try {
        const userId = req.user.userId;

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

export default {
    getAccounts
};
