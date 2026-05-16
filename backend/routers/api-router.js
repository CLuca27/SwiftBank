import controllers from "./controllers/index.js";
import express from 'express'
import middleware from '../middleware/index.js'

const apiRouter = express.Router()

const auth = middleware.authMiddleware.authenticateToken;
const simulatorKey = middleware.simulatorMiddleware.validateSimulatorKey;

// ============================================
// CARD PAYMENTS
// ============================================

// Rute protejate (doar pentru simulator)
apiRouter.post('/card-payments/start', simulatorKey, controllers.cardPayment.startPayment);
apiRouter.post('/card-payments/pos/authorize', simulatorKey, controllers.cardPayment.authorizePosPayment);

// Rute autentificate (pentru app) - TREBUIE INAINTE de :sessionId
apiRouter.get('/card-payments/user/pending', auth, controllers.cardPayment.getPendingSessions);
apiRouter.post('/card-payments/settlement', auth, controllers.cardPayment.processSettlements);
apiRouter.post('/card-payments/:sessionId/approve', auth, controllers.cardPayment.approvePayment);
apiRouter.post('/card-payments/:sessionId/decline', auth, controllers.cardPayment.declinePayment);

// Ruta publica cu parametru (la sfarsit)
apiRouter.get('/card-payments/:sessionId', controllers.cardPayment.getSessionStatus);

// ============================================
// RUTE AUTENTIFICATE
// ============================================

// Rates admin
apiRouter.post('/rates/refresh', auth, controllers.rates.refreshRates);

// Accounts
apiRouter.get('/accounts', auth, controllers.account.getAccounts);
apiRouter.post('/accounts/add', auth, controllers.account.addAccount);
apiRouter.post('/accounts/exchange', auth, controllers.account.exchange);

// Transactions
apiRouter.get('/transactions', auth, controllers.transaction.getTransactions);

// User
apiRouter.get('/user/profile', auth, controllers.user.getProfile);
apiRouter.put('/user/profile', auth, controllers.user.updateProfile);
apiRouter.get('/user/settings', auth, controllers.user.getSettings);
apiRouter.put('/user/settings', auth, controllers.user.updateSettings);
apiRouter.post('/user/verify-pin', auth, controllers.user.verifyPin);
apiRouter.put('/user/change-pin', auth, controllers.user.changePin);

// Devices (FCM)
apiRouter.post('/user/devices', auth, controllers.user.registerDevice);
apiRouter.delete('/user/devices/:deviceId', auth, controllers.user.unregisterDevice);

// Transfers
apiRouter.post('/transfers/validate-iban', auth, controllers.transfer.validateIBAN);
apiRouter.post('/transfers', auth, controllers.transfer.createTransfer);

// Beneficiaries
apiRouter.get('/beneficiaries', auth, controllers.transfer.getBeneficiaries);
apiRouter.post('/beneficiaries', auth, controllers.transfer.createBeneficiary);
apiRouter.delete('/beneficiaries/:id', auth, controllers.transfer.deleteBeneficiary);

// Cards
apiRouter.get('/cards', auth, controllers.card.getCards);
apiRouter.post('/cards', auth, controllers.card.createCard);
apiRouter.post('/cards/:cardId/details', auth, controllers.card.getCardDetails);
apiRouter.put('/cards/:cardId/block', auth, controllers.card.toggleBlock);
apiRouter.put('/cards/:cardId/currency', auth, controllers.card.updateCurrency);
apiRouter.delete('/cards/:cardId', auth, controllers.card.deleteCard);

// Billers
apiRouter.get('/billers', auth, controllers.bill.getBillers);
apiRouter.get('/billers/categories', auth, controllers.bill.getBillerCategories);
apiRouter.get('/billers/:id', auth, controllers.bill.getBillerById);

// Saved Billers
apiRouter.get('/saved-billers', auth, controllers.bill.getSavedBillers);
apiRouter.post('/saved-billers', auth, controllers.bill.saveBiller);
apiRouter.delete('/saved-billers/:id', auth, controllers.bill.deleteSavedBiller);

// Bill Payments
apiRouter.post('/bill-payments', auth, controllers.bill.createBillPayment);
apiRouter.get('/bill-payments', auth, controllers.bill.getBillPayments);

// Statistics
apiRouter.get('/statistics', auth, controllers.statistics.getStatistics);
apiRouter.get('/statistics/balance-history', auth, controllers.statistics.getBalanceHistory);


export default apiRouter;
