import controllers from "./controllers/index.js";
import express from 'express' 
import middleware from '../middleware/index.js'

const apiRouter = express.Router()
apiRouter.use(middleware.authMiddleware.authenticateToken)


apiRouter.get('/rates', controllers.rates.getRates)
apiRouter.get('/rates/:currency', controllers.rates.getRateByCurrency)

//Pentru testare
apiRouter.post('/rates/refresh', controllers.rates.refreshRates);

// Accounts
apiRouter.get('/accounts', controllers.account.getAccounts);

// Transactions
apiRouter.get('/transactions', controllers.transaction.getTransactions);


export default apiRouter;