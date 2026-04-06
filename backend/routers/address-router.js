import controllers from "./controllers/index.js";
import express from 'express'

const addressRouter = express.Router()

addressRouter.get('/autocomplete', controllers.address.autocomplete);
addressRouter.get('/place-details', controllers.address.getPlaceDetails);

export default addressRouter;