import services from '../../services/index.js';

async function getTransactions(req, res) {
    try {
        const userId = req.user.user_id;

        // Query params pentru filtrare si paginare
        const {
            account_id,
            limit = 20,
            offset = 0,
            type,       // CARD, TRANSFER, BILL
            start_date,
            end_date
        } = req.query;

        // account_id este obligatoriu
        if (!account_id) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'MISSING_ACCOUNT_ID',
                    message: 'account_id este obligatoriu'
                }
            });
        }

        // Validare limit
        const parsedLimit = Math.min(parseInt(limit) || 20, 100);
        const parsedOffset = parseInt(offset) || 0;

        const result = await services.transactionService.getTransactions(userId, {
            accountId: parseInt(account_id),
            limit: parsedLimit,
            offset: parsedOffset,
            transactionType: type || null,
            startDate: start_date || null,
            endDate: end_date || null
        });

        return res.status(200).json({
            success: true,
            data: {
                transactions: result.transactions,
                pagination: {
                    limit: parsedLimit,
                    offset: parsedOffset,
                    hasMore: result.hasMore
                }
            }
        });

    } catch (error) {
        console.error('Error getting transactions:', error);
        return res.status(500).json({
            success: false,
            error: {
                code: 'INTERNAL_SERVER_ERROR',
                message: 'Nu am putut obtine tranzactiile'
            }
        });
    }
}

export default {
    getTransactions
};
