import controllers from "./controllers/index.js";
import express from 'express'

const ratesRouter = express.Router()

// Public routes - no auth required
ratesRouter.get('/', controllers.rates.getRates)
ratesRouter.get('/convert', controllers.rates.convert)

export default ratesRouter
