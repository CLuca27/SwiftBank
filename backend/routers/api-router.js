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


export default apiRouter;