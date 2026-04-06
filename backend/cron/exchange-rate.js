import cron from 'node-cron';
import services from '../services/index.js';


const SCHEDULE = process.env.CRON_EXCHANGE_RATE_SCHEDULE;
const TIMEZONE = process.env.CRON_TIMEZONE; 

function init() {
    cron.schedule(SCHEDULE, async () => {
        console.log('[CRON] Running exchange rate update...');
        
        try {
            const rates = await services.ratesService.updateRates();
            console.log('[CRON] Exchange rate update completed!', rates);
        } catch (error) {
            console.error('[CRON] Exchange rate update failed:', error.message);
        }
    }, {
        timezone: TIMEZONE
    });

    console.log(`[CRON] Exchange rate job scheduled (${SCHEDULE}, ${TIMEZONE})`);
} 

//Functie utilizata pentru test....
async function runNow() {
    console.log('[CRON] Running immediate exchange rate update...');
    try {
        const rates = await services.ratesService.updateRates();
        console.log('[CRON] Immediate update completed!', rates);
        return rates;
    } catch (error) {
        console.error('[CRON] Immediate update failed:', error.message);
        return null;
    }
} 

export default {
    init,
    runNow
}; 

