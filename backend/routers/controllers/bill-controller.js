import services from '../../services/index.js';

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function getIdempotencyKey(req) {
    const headerKey = req.headers['idempotency-key'];
    const bodyKey = req.body?.idempotency_key;
    const rawKey = Array.isArray(headerKey) ? headerKey[0] : (headerKey || bodyKey);

    return typeof rawKey === 'string' ? rawKey.trim() : null;
}

/**
 * GET /api/billers
 * Obține lista furnizorilor (opțional filtrată pe categorie)
 */
async function getBillers(req, res) {
    try {
        const { category } = req.query;
        const billers = await services.billService.getBillers(category || null);

        return res.json({
            success: true,
            data: {
                billers
            }
        });
    } catch (error) {
        console.error('Error getting billers:', error);
        return res.status(500).json({
            success: false,
            error: {
                code: 'INTERNAL_SERVER_ERROR',
                message: 'Eroare la obținerea furnizorilor'
            }
        });
    }
}

/**
 * GET /api/billers/categories
 * Obține categoriile de furnizori
 */
async function getBillerCategories(req, res) {
    try {
        const categories = await services.billService.getBillerCategories();

        return res.json({
            success: true,
            data: {
                categories
            }
        });
    } catch (error) {
        console.error('Error getting biller categories:', error);
        return res.status(500).json({
            success: false,
            error: {
                code: 'INTERNAL_SERVER_ERROR',
                message: 'Eroare la obținerea categoriilor'
            }
        });
    }
}

/**
 * GET /api/billers/:id
 * Obține detaliile unui furnizor
 */
async function getBillerById(req, res) {
    try {
        const { id } = req.params;
        const biller = await services.billService.getBillerById(parseInt(id));

        if (!biller || biller.status !== 'ACTIVE') {
            return res.status(404).json({
                success: false,
                error: {
                    code: 'BILLER_NOT_FOUND',
                    message: 'Furnizorul nu a fost găsit'
                }
            });
        }

        return res.json({
            success: true,
            data: biller
        });
    } catch (error) {
        console.error('Error getting biller:', error);
        return res.status(500).json({
            success: false,
            error: {
                code: 'INTERNAL_SERVER_ERROR',
                message: 'Eroare la obținerea furnizorului'
            }
        });
    }
}

/**
 * GET /api/saved-billers
 * Obține furnizorii salvați ai utilizatorului
 */
async function getSavedBillers(req, res) {
    try {
        const userId = req.user.user_id;
        const savedBillers = await services.billService.getSavedBillers(userId);

        return res.json({
            success: true,
            data: {
                saved_billers: savedBillers
            }
        });
    } catch (error) {
        console.error('Error getting saved billers:', error);
        return res.status(500).json({
            success: false,
            error: {
                code: 'INTERNAL_SERVER_ERROR',
                message: 'Eroare la obținerea furnizorilor salvați'
            }
        });
    }
}

/**
 * POST /api/saved-billers
 * Salvează un furnizor cu codul de client
 */
async function saveBiller(req, res) {
    try {
        const userId = req.user.user_id;
        const { biller_id, client_code, alias } = req.body;
        const clientCode = typeof client_code === 'string' ? client_code.trim() : '';

        if (!biller_id || !clientCode) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'MISSING_FIELDS',
                    message: 'biller_id și client_code sunt obligatorii'
                }
            });
        }

        const savedBiller = await services.billService.saveBiller(
            userId,
            biller_id,
            clientCode,
            alias || null
        );

        return res.status(201).json({
            success: true,
            message: 'Furnizor salvat cu succes',
            data: savedBiller
        });
    } catch (error) {
        console.error('Error saving biller:', error);

        const errorResponses = {
            'BILLER_NOT_FOUND': { status: 404, code: 'BILLER_NOT_FOUND', message: 'Furnizorul nu a fost gÄƒsit' },
            'BILLER_INACTIVE': { status: 400, code: 'BILLER_INACTIVE', message: 'Furnizorul nu este activ' },
            'INVALID_CLIENT_CODE': { status: 400, code: 'INVALID_CLIENT_CODE', message: 'Format cod client invalid' }
        };
        const errorConfig = errorResponses[error.message] || {
            status: 500,
            code: 'INTERNAL_SERVER_ERROR',
            message: 'Eroare la salvarea furnizorului'
        };

        return res.status(errorConfig.status).json({
            success: false,
            error: {
                code: errorConfig.code,
                message: errorConfig.message
            }
        });
    }
}

/**
 * DELETE /api/saved-billers/:id
 * Șterge un furnizor salvat
 */
async function deleteSavedBiller(req, res) {
    try {
        const userId = req.user.user_id;
        const savedBillerId = parseInt(req.params.id);

        if (!savedBillerId || isNaN(savedBillerId)) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'INVALID_ID',
                    message: 'ID furnizor salvat invalid'
                }
            });
        }

        await services.billService.deleteSavedBiller(userId, savedBillerId);

        return res.json({
            success: true,
            message: 'Furnizor șters cu succes'
        });
    } catch (error) {
        console.error('Error deleting saved biller:', error);

        if (error.code === 'PGRST116') {
            return res.status(404).json({
                success: false,
                error: {
                    code: 'SAVED_BILLER_NOT_FOUND',
                    message: 'Furnizorul salvat nu a fost gÄƒsit'
                }
            });
        }

        return res.status(500).json({
            success: false,
            error: {
                code: 'INTERNAL_SERVER_ERROR',
                message: 'Eroare la ștergerea furnizorului'
            }
        });
    }
}

/**
 * POST /api/bill-payments
 * Creează o plată de factură
 */
async function createBillPayment(req, res) {
    try {
        const userId = req.user.user_id;
        const idempotencyKey = getIdempotencyKey(req);
        const {
            from_account_id,
            biller_id,
            client_code,
            invoice_reference,
            amount,
            saved_biller_id
        } = req.body;

        if (idempotencyKey && !UUID_REGEX.test(idempotencyKey)) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'INVALID_IDEMPOTENCY_KEY',
                    message: 'Idempotency-Key must be a valid UUID'
                }
            });
        }

        if (idempotencyKey) {
            const existingResponse = await services.billService.checkIdempotencyKey(
                userId,
                idempotencyKey,
                services.billService.BILL_PAYMENT_ENDPOINT
            );

            if (existingResponse) {
                return res.status(existingResponse.response_status).json(existingResponse.response_body);
            }
        }

        // Validări
        if (!from_account_id) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'MISSING_ACCOUNT',
                    message: 'Contul sursă este obligatoriu'
                }
            });
        }

        if (!biller_id) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'MISSING_BILLER',
                    message: 'Furnizorul este obligatoriu'
                }
            });
        }

        if (!client_code) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'MISSING_CLIENT_CODE',
                    message: 'Codul de client este obligatoriu'
                }
            });
        }

        const paymentAmount = Number(amount);
        if (!Number.isFinite(paymentAmount) || paymentAmount <= 0) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'INVALID_AMOUNT',
                    message: 'Suma trebuie să fie mai mare decât 0'
                }
            });
        }

        const result = await services.billService.createBillPayment(userId, {
            fromAccountId: from_account_id,
            billerId: biller_id,
            clientCode: client_code,
            invoiceReference: invoice_reference,
            amount: paymentAmount,
            savedBillerId: saved_biller_id
        });

        const response = {
            success: true,
            message: 'Plată efectuată cu succes',
            data: result
        };

        await services.billService.saveIdempotencyKey(
            userId,
            idempotencyKey,
            services.billService.BILL_PAYMENT_ENDPOINT,
            201,
            response
        );

        return res.status(201).json(response);
    } catch (error) {
        console.error('Error creating bill payment:', error);

        const errorResponses = {
            'BILLER_NOT_FOUND': { status: 404, code: 'BILLER_NOT_FOUND', message: 'Furnizorul nu a fost găsit' },
            'BILLER_INACTIVE': { status: 400, code: 'BILLER_INACTIVE', message: 'Furnizorul nu este activ' },
            'INVALID_CLIENT_CODE': { status: 400, code: 'INVALID_CLIENT_CODE', message: 'Format cod client invalid' },
            'ACCOUNT_NOT_FOUND': { status: 404, code: 'ACCOUNT_NOT_FOUND', message: 'Contul nu a fost găsit' },
            'INVALID_AMOUNT': { status: 400, code: 'INVALID_AMOUNT', message: 'Suma trebuie să fie mai mare decât 0' },
            'INSUFFICIENT_FUNDS': { status: 400, code: 'INSUFFICIENT_FUNDS', message: 'Fonduri insuficiente' },
            'PAYMENT_FAILED': { status: 500, code: 'PAYMENT_FAILED', message: 'Plata nu a putut fi efectuată' }
        };
        const errorConfig = errorResponses[error.message] || {
            status: 500,
            code: 'PAYMENT_FAILED',
            message: 'Eroare la efectuarea plății'
        };

        return res.status(errorConfig.status).json({
            success: false,
            error: {
                code: errorConfig.code,
                message: errorConfig.message
            }
        });
    }
}

/**
 * GET /api/bill-payments
 * Obține istoricul plăților
 */
async function getBillPayments(req, res) {
    try {
        const userId = req.user.user_id;
        const { limit = 50, offset = 0 } = req.query;

        const payments = await services.billService.getBillPayments(
            userId,
            parseInt(limit),
            parseInt(offset)
        );

        return res.json({
            success: true,
            data: {
                payments
            }
        });
    } catch (error) {
        console.error('Error getting bill payments:', error);
        return res.status(500).json({
            success: false,
            error: {
                code: 'INTERNAL_SERVER_ERROR',
                message: 'Eroare la obținerea plăților'
            }
        });
    }
}

export default {
    getBillers,
    getBillerCategories,
    getBillerById,
    getSavedBillers,
    saveBiller,
    deleteSavedBiller,
    createBillPayment,
    getBillPayments
};
