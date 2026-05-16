import cron from 'node-cron';
import services from '../services/index.js';

const SCHEDULE = process.env.CRON_CARD_PAYMENT_SETTLEMENT_SCHEDULE || '0 2 * * *';
const TIMEZONE = process.env.CRON_TIMEZONE || 'Europe/Bucharest';

function init() {
    cron.schedule(SCHEDULE, async () => {
        console.log('[CRON] Running card payment settlement...');

        try {
            const result = await services.cardPaymentService.processSettlements();
            console.log('[CRON] Card payment settlement completed!', result);
        } catch (error) {
            console.error('[CRON] Card payment settlement failed:', error.message);
        }
    }, {
        timezone: TIMEZONE
    });

    console.log(`[CRON] Card payment settlement job scheduled (${SCHEDULE}, ${TIMEZONE})`);
}

async function runNow() {
    console.log('[CRON] Running immediate card payment settlement...');

    try {
        const result = await services.cardPaymentService.processSettlements();
        console.log('[CRON] Immediate card payment settlement completed!', result);
        return result;
    } catch (error) {
        console.error('[CRON] Immediate card payment settlement failed:', error.message);
        return null;
    }
}

export default {
    init,
    runNow
};
