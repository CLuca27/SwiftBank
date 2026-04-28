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
apiRouter.put('/user/profile', controllers.user.updateProfile);
apiRouter.get('/user/settings', controllers.user.getSettings);
apiRouter.put('/user/settings', controllers.user.updateSettings);
apiRouter.post('/user/verify-pin', controllers.user.verifyPin);
apiRouter.put('/user/change-pin', controllers.user.changePin);

// Devices (FCM)
apiRouter.post('/user/devices', controllers.user.registerDevice);
apiRouter.delete('/user/devices/:deviceId', controllers.user.unregisterDevice);

// Transfers
apiRouter.post('/transfers/validate-iban', controllers.transfer.validateIBAN);
apiRouter.post('/transfers', controllers.transfer.createTransfer);
apiRouter.get('/beneficiaries', controllers.transfer.getBeneficiaries);

// Cards
apiRouter.get('/cards', controllers.card.getCards);
apiRouter.post('/cards', controllers.card.createCard);
apiRouter.post('/cards/:cardId/details', controllers.card.getCardDetails);
apiRouter.put('/cards/:cardId/block', controllers.card.toggleBlock);
apiRouter.put('/cards/:cardId/currency', controllers.card.updateCurrency);
apiRouter.delete('/cards/:cardId', controllers.card.deleteCard);


export default apiRouter;