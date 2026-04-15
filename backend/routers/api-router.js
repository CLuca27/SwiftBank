import controllers from "./controllers/index.js";
import express from 'express'
import middleware from '../middleware/index.js'

const apiRouter = express.Router()

// All routes require authentication
apiRouter.use(middleware.authMiddleware.authenticateToken)

// Rates admin
apiRouter.post('/rates/refresh', controllers.rates.refreshRates);

// Accounts
apiRouter.get('/accounts', controllers.account.getAccounts);
apiRouter.post('/accounts/add', controllers.account.addAccount);
apiRouter.post('/accounts/exchange', controllers.account.exchange);

// Transactions
apiRouter.get('/transactions', controllers.transaction.getTransactions);

// User
apiRouter.get('/user/profile', controllers.user.getProfile);
apiRouter.get('/user/settings', controllers.user.getSettings);
apiRouter.put('/user/settings', controllers.user.updateSettings);
apiRouter.put('/user/change-pin', controllers.user.changePin);

// Devices (FCM)
apiRouter.post('/user/devices', controllers.user.registerDevice);
apiRouter.delete('/user/devices/:deviceId', controllers.user.unregisterDevice);

// Transfers
apiRouter.post('/transfers/validate-iban', controllers.transfer.validateIBAN);
apiRouter.post('/transfers', controllers.transfer.createTransfer);
apiRouter.get('/beneficiaries', controllers.transfer.getBeneficiaries);


export default apiRouter;