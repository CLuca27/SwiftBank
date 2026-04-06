import config from "../config/index.js"

async function sendOTP_Email(recipientEmail, verificationCode) {  
    try {
        const info = await config.transporter.sendMail({
            from: 'no-reply@swiftbank.dev',
            to: recipientEmail,
            subject: "Verificare email",
            html: `<p>Codul de verificare este: <strong>${verificationCode}</strong>. Acesta este valabil timp de 5 minute.</p>` 
        });
        console.log('Email sent successfully:', info.messageId);
        return { success: true, messageId: info.messageId };
    } 
    catch(error) {
        console.error('Error sending email:', error);
        return { success: false, error: error.message };
    }
} 

export default {
    sendOTP_Email 
}