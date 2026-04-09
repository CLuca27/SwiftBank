import services from '../../services/index.js';


const SUPPORTED_CURRENCIES = ['EUR', 'USD', 'GBP'];


async function getRates(req, res) {
    try {
        const rates = await services.ratesService.getCurrentRates();


        return res.status(200).json({
            success: true,
            data: {
                rates: rates
            }
        });

    } catch (error) {
        console.error('Error getting rates:', error);
        return res.status(500).json({
            success: false,
            error: {
                code: 'INTERNAL_SERVER_ERROR',
                message: 'Nu am putut obține ratele de schimb'
            }
        });
    }
} 

async function getRateByCurrency(req, res) {
    try {
        const { currency } = req.params;
        const fromCurrency = currency.toUpperCase();

        if (!SUPPORTED_CURRENCIES.includes(fromCurrency)) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'INVALID_CURRENCY',
                    message: 'Valută nesuportată. Folosește EUR sau USD.'
                }
            });
        }

        const rate = await services.ratesService.getRate(fromCurrency);

        if (!rate) {
            return res.status(404).json({
                success: false,
                error: {
                    code: 'RATE_NOT_FOUND',
                    message: 'Nu am găsit rata pentru această valută'
                }
            });
        }

        return res.status(200).json({
            success: true,
            data: {
                from_currency: rate.from_currency,
                to_currency: rate.to_currency,
                rate: parseFloat(rate.rate),
                rate_date: rate.rate_date
            }
        });

    } catch (error) {
        console.error('Error getting rate:', error);
        return res.status(500).json({
            success: false,
            error: {
                code: 'INTERNAL_SERVER_ERROR',
                message: 'Nu am putut obține rata de schimb'
            }
        });
    }
}

async function refreshRates(req, res) {
    try {
        const rates = await services.ratesService.updateRates();

        return res.status(200).json({
            success: true,
            message: 'Ratele au fost actualizate',
            data: { rates }
        });

    } catch (error) {
        console.error('Error refreshing rates:', error);
        return res.status(500).json({
            success: false,
            error: {
                code: 'REFRESH_FAILED',
                message: 'Nu am putut actualiza ratele'
            }
        });
    }
}

async function convert(req, res) {
    try {
        const { from, to } = req.query;

        if (!from || !to) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'MISSING_PARAMS',
                    message: 'Specifică valutele (from și to)'
                }
            });
        }

        const fromCurrency = from.toUpperCase().trim();
        const toCurrency = to.toUpperCase().trim();

        const rateInfo = await services.ratesService.getExchangeRate(fromCurrency, toCurrency);

        return res.status(200).json({
            success: true,
            data: {
                from: rateInfo.fromCurrency,
                to: rateInfo.toCurrency,
                rate: rateInfo.rate,
                inverse: rateInfo.inverse
            }
        });

    } catch (error) {
        console.error('Error converting rate:', error);
        return res.status(500).json({
            success: false,
            error: {
                code: 'INTERNAL_SERVER_ERROR',
                message: 'Nu am putut calcula rata de schimb'
            }
        });
    }
}

export default {
    getRates,
    getRateByCurrency,
    refreshRates,
    convert
};



