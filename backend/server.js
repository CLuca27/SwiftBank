import app from './app.js' 
import services from './services/index.js'
import jwt from 'jsonwebtoken' 
import cron from './cron/exchange-rate.js'
const PORT = process.env.PORT || 7000; 
app.listen(PORT, () => {
    console.log(`Server is running on port ${PORT}....`);
    cron.init();
}) 

;





