import 'dotenv/config';
import app from './app.js'

import exchangeRateCron from './cron/exchange-rate.js'
import cardPaymentSettlementCron from './cron/card-payment-settlement.js' 
const PORT = process.env.PORT || 8618;
app.listen(PORT, '0.0.0.0', () => {
    console.log(`Server is running on port ${PORT}....`);
    exchangeRateCron.init();
    cardPaymentSettlementCron.init();
}); 



