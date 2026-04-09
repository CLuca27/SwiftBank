import controllers from './controllers/index.js'
import express from 'express'

const authRouter = express.Router()

//===================== REGISTRATION ====================
authRouter.post('/check', controllers.auth.check);
authRouter.post('/send-otp', controllers.auth.sendOTP);
authRouter.post('/verify-otp', controllers.auth.verifyOTP);
authRouter.post('/register', controllers.auth.register);

//===================== LOGIN ====================
authRouter.post('/identify', controllers.auth.identify)
authRouter.post('/login', controllers.auth.login)

//===================== FORGOT PIN ====================
authRouter.post('/forgot-pin', controllers.auth.forgotPin)
authRouter.post('/reset-pin', controllers.auth.resetPin)

//===================== REFRESH TOKENS ====================
authRouter.post('/refresh', controllers.refreshToken)

//===================== LOGOUT ====================
authRouter.post('/logout', controllers.auth.logout)  

export default authRouter;
