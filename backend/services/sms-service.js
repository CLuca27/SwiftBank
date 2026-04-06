import config from "../config/index.js"
import dotenv from 'dotenv' 

async function sendOTP_SMS(recipientPhone, verificationCode) {
    try {
        const response = await config.twilio_client.messages.create({
            to: recipientPhone,
            from: "+18316075136",
            body: `Codul tău de verificare este: ${verificationCode}. Acesta este valabil pentru 5 minute.`
        })
        
        console.log('SMS trimis:', response.sid);
        return { success: true, messageId: response.sid};
    } 
    catch(error) {
        console.error('Eroare Twilio:', error);
        return { success: false, error: error.message };
    }
} 

export default {
    sendOTP_SMS 
}