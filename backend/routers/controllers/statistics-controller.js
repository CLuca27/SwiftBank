import statisticsService from '../../services/statistics-service.js';

async function getStatistics(req, res) {
    try {
        const userId = req.user.user_id;
        const period = req.query.period || 'this_month';

        const statistics = await statisticsService.getStatistics(userId, period);

        res.json({
            success: true,
            data: statistics
        });
    } catch (error) {
        console.error('Error getting statistics:', error);
        res.status(500).json({
            success: false,
            message: 'Eroare la obtinerea statisticilor',
            error: { code: 'STATISTICS_ERROR', details: error.message }
        });
    }
}

async function getBalanceHistory(req, res) {
    try {
        const userId = req.user.user_id;
        const period = req.query.period || 'last_6_months';

        const balanceHistory = await statisticsService.getBalanceHistory(userId, period);

        res.json({
            success: true,
            data: { balanceHistory }
        });
    } catch (error) {
        console.error('Error getting balance history:', error);
        res.status(500).json({
            success: false,
            message: 'Eroare la obtinerea istoricului soldului',
            error: { code: 'BALANCE_HISTORY_ERROR', details: error.message }
        });
    }
}

export default {
    getStatistics,
    getBalanceHistory
};
