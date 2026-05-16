import services from '../../services/index.js';

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function getIdempotencyKey(req) {
    const headerKey = req.headers['idempotency-key'];
    const bodyKey = req.body?.idempotency_key;
    const rawKey = Array.isArray(headerKey) ? headerKey[0] : (headerKey || bodyKey);

    return typeof rawKey === 'string' ? rawKey.trim() : null;
}

function getServerTime() {
    return new Date().toISOString();
}

function refreshResponseServerTime(responseBody) {
    if (!responseBody || typeof responseBody !== 'object' || !responseBody.data) {
        return responseBody;
    }

    return {
        ...responseBody,
        data: {
            ...responseBody.data,
            server_time: getServerTime()
        }
    };
}

function buildStartPaymentResponse(session) {
    return {
        success: true,
        message: session.reused_session
            ? 'Payment already waiting for app approval'
            : 'Payment waiting for app approval',
        data: {
            session_id: session.session_id,
            status: session.status,
            merchant_name: session.merchant_name,
            merchant_location: session.merchant_location,
            amount: session.amount,
            currency: session.currency,
            masked_card: session.masked_card,
            created_at: session.created_at,
            expires_at: session.expires_at,
            server_time: getServerTime(),
            merchant_logo_url: session.merchant_logo_url,
            category_name: session.category_name,
            category_icon: session.category_icon,
            reused_session: Boolean(session.reused_session)
        }
    };
}

function buildPosPaymentResponse(transaction) {
    return {
        success: true,
        message: 'POS payment authorized',
        data: {
            transaction_id: transaction.transaction_id,
            status: transaction.status,
            merchant_name: transaction.merchant_name,
            location: transaction.location,
            amount: transaction.amount,
            currency: transaction.currency,
            original_amount: transaction.original_amount,
            original_currency: transaction.original_currency,
            exchange_rate: transaction.exchange_rate,
            reference: transaction.reference,
            masked_card: transaction.masked_card,
            entry_mode: transaction.entry_mode,
            settlement_date: transaction.settlement_date,
            merchant_logo_url: transaction.merchant_logo_url,
            category_name: transaction.category_name,
            category_icon: transaction.category_icon
        }
    };
}

/**
 * POST /card-payments/start
 * Endpoint PUBLIC - initiat de simulator/POS
 * Valideaza cardul si creeaza o sesiune de plata
 */
async function startPayment(req, res) {
    try {
        const idempotencyKey = getIdempotencyKey(req);
        const {
            card_number,
            expiry,
            cvv,
            merchant_name,
            merchant_location,
            amount,
            currency = 'RON'
        } = req.body;

        // Verifică idempotency key
        if (idempotencyKey && !UUID_REGEX.test(idempotencyKey)) {
            return res.status(400).json({
                success: false,
                status: 'DECLINED',
                error: {
                    code: 'INVALID_IDEMPOTENCY_KEY',
                    message: 'Idempotency-Key must be a valid UUID'
                }
            });
        }

        const normalizedMerchantName = typeof merchant_name === 'string' ? merchant_name.trim() : '';
        const normalizedMerchantLocation = typeof merchant_location === 'string' && merchant_location.trim()
            ? merchant_location.trim()
            : 'Online';
        const normalizedAmount = Number(amount);
        const normalizedCurrency = String(currency || 'RON').trim().toUpperCase();

        // Validari de baza
        if (!card_number || !expiry || !cvv) {
            const errorResponse = {
                success: false,
                status: 'DECLINED',
                error: {
                    code: 'INVALID_CARD_DETAILS',
                    message: 'Card details are required'
                }
            };
            return res.status(400).json(errorResponse);
        }

        if (normalizedMerchantName.length < 2) {
            const errorResponse = {
                success: false,
                status: 'DECLINED',
                error: {
                    code: 'INVALID_MERCHANT',
                    message: 'Merchant name is required'
                }
            };
            return res.status(400).json(errorResponse);
        }

        if (!Number.isFinite(normalizedAmount) || normalizedAmount <= 0) {
            const errorResponse = {
                success: false,
                status: 'DECLINED',
                error: {
                    code: 'INVALID_AMOUNT',
                    message: 'Amount must be greater than 0'
                }
            };
            return res.status(400).json(errorResponse);
        }

        if (!/^[A-Z]{3}$/.test(normalizedCurrency)) {
            return res.status(400).json({
                success: false,
                status: 'DECLINED',
                error: {
                    code: 'INVALID_CURRENCY',
                    message: 'Currency must be a 3-letter code'
                }
            });
        }

        // Valideaza cardul
        const validation = await services.cardPaymentService.validateCard(card_number, expiry, cvv);

        if (!validation.valid) {
            const errorResponse = {
                success: false,
                status: 'DECLINED',
                error: {
                    code: validation.error,
                    message: getErrorMessage(validation.error)
                }
            };
            return res.status(400).json(errorResponse);
        }

        if (idempotencyKey) {
            const existingResponse = await services.cardPaymentService.checkPaymentIdempotency(
                validation.card.user_id,
                idempotencyKey
            );

            if (existingResponse) {
                return res.status(existingResponse.response_status).json(
                    refreshResponseServerTime(existingResponse.response_body)
                );
            }
        }

        // Creeaza sesiunea de plata
        const session = await services.cardPaymentService.createPaymentSession({
            cardId: validation.card.card_id,
            userId: validation.card.user_id,
            maskedCard: validation.card.masked_card,
            merchantName: normalizedMerchantName,
            merchantLocation: normalizedMerchantLocation,
            amount: normalizedAmount,
            currency: normalizedCurrency
        });

        const successResponse = buildStartPaymentResponse(session);
        const responseStatus = session.reused_session ? 200 : 201;

        await services.cardPaymentService.savePaymentIdempotency(
            validation.card.user_id,
            idempotencyKey,
            responseStatus,
            successResponse
        );
        return res.status(responseStatus).json(successResponse);

    } catch (error) {
        console.error('Error starting payment:', error);
        return res.status(500).json({
            success: false,
            status: 'DECLINED',
            error: {
                code: 'PAYMENT_FAILED',
                message: 'Could not process payment'
            }
        });
    }
}

/**
 * POST /card-payments/pos/authorize
 * Endpoint PUBLIC - simuleaza un terminal POS card-present
 */
async function authorizePosPayment(req, res) {
    try {
        const idempotencyKey = getIdempotencyKey(req);
        const {
            card_number,
            expiry,
            merchant_name,
            merchant_location,
            amount,
            currency = 'RON',
            entry_mode = 'GOOGLE_WALLET',
            pin
        } = req.body;

        if (idempotencyKey && !UUID_REGEX.test(idempotencyKey)) {
            return res.status(400).json({
                success: false,
                status: 'DECLINED',
                error: {
                    code: 'INVALID_IDEMPOTENCY_KEY',
                    message: 'Idempotency-Key must be a valid UUID'
                }
            });
        }

        const normalizedMerchantName = typeof merchant_name === 'string' ? merchant_name.trim() : '';
        const normalizedMerchantLocation = typeof merchant_location === 'string' && merchant_location.trim()
            ? merchant_location.trim()
            : 'POS Terminal';
        const normalizedAmount = Number(amount);
        const normalizedCurrency = String(currency || 'RON').trim().toUpperCase();
        const normalizedEntryMode = String(entry_mode || 'GOOGLE_WALLET').trim().toUpperCase();

        if (!card_number) {
            return res.status(400).json({
                success: false,
                status: 'DECLINED',
                error: {
                    code: 'INVALID_CARD_DETAILS',
                    message: 'Card number is required'
                }
            });
        }

        if (normalizedMerchantName.length < 2) {
            return res.status(400).json({
                success: false,
                status: 'DECLINED',
                error: {
                    code: 'INVALID_MERCHANT',
                    message: 'Merchant name is required'
                }
            });
        }

        if (!Number.isFinite(normalizedAmount) || normalizedAmount <= 0) {
            return res.status(400).json({
                success: false,
                status: 'DECLINED',
                error: {
                    code: 'INVALID_AMOUNT',
                    message: 'Amount must be greater than 0'
                }
            });
        }

        if (!/^[A-Z]{3}$/.test(normalizedCurrency)) {
            return res.status(400).json({
                success: false,
                status: 'DECLINED',
                error: {
                    code: 'INVALID_CURRENCY',
                    message: 'Currency must be a 3-letter code'
                }
            });
        }

        const validation = await services.cardPaymentService.validateCardForPos(card_number, expiry);
        if (!validation.valid) {
            return res.status(400).json({
                success: false,
                status: 'DECLINED',
                error: {
                    code: validation.error,
                    message: getErrorMessage(validation.error)
                }
            });
        }

        if (idempotencyKey) {
            const existingResponse = await services.cardPaymentService.checkPaymentIdempotency(
                validation.card.user_id,
                idempotencyKey,
                services.cardPaymentService.POS_PAYMENT_ENDPOINT
            );

            if (existingResponse) {
                return res.status(existingResponse.response_status).json(existingResponse.response_body);
            }
        }

        const transaction = await services.cardPaymentService.authorizePosPayment({
            cardId: validation.card.card_id,
            userId: validation.card.user_id,
            maskedCard: validation.card.masked_card,
            merchantName: normalizedMerchantName,
            merchantLocation: normalizedMerchantLocation,
            amount: normalizedAmount,
            currency: normalizedCurrency,
            entryMode: normalizedEntryMode,
            pin
        });

        const successResponse = buildPosPaymentResponse(transaction);

        await services.cardPaymentService.savePaymentIdempotency(
            validation.card.user_id,
            idempotencyKey,
            201,
            successResponse,
            services.cardPaymentService.POS_PAYMENT_ENDPOINT
        );

        return res.status(201).json(successResponse);
    } catch (error) {
        console.error('Error authorizing POS payment:', error);

        const errorResponses = {
            'PIN_REQUIRED': { status: 400, code: 'PIN_REQUIRED', message: 'PIN is required for this POS payment' },
            'INVALID_PIN': { status: 400, code: 'INVALID_PIN', message: 'PIN must contain 4 to 6 digits' },
            'INSUFFICIENT_FUNDS': { status: 400, code: 'INSUFFICIENT_FUNDS', message: 'Insufficient funds' },
            'PAYMENT_FAILED': { status: 500, code: 'PAYMENT_FAILED', message: 'Could not authorize POS payment' }
        };

        const errResponse = errorResponses[error.message] || {
            status: 500,
            code: 'PAYMENT_FAILED',
            message: 'Could not authorize POS payment'
        };

        return res.status(errResponse.status).json({
            success: false,
            status: 'DECLINED',
            error: {
                code: errResponse.code,
                message: errResponse.message
            }
        });
    }
}

/**
 * GET /card-payments/:sessionId
 * Endpoint PUBLIC - pentru polling status
 */
async function getSessionStatus(req, res) {
    try {
        const { sessionId } = req.params;

        const session = await services.cardPaymentService.getSession(sessionId);

        if (!session) {
            return res.status(404).json({
                success: false,
                error: {
                    code: 'SESSION_NOT_FOUND',
                    message: 'Payment session not found'
                }
            });
        }

        return res.status(200).json({
            success: true,
            data: {
                session_id: session.session_id,
                status: session.status,
                merchant_name: session.merchant_name,
                merchant_location: session.merchant_location,
                amount: session.amount,
                currency: session.currency,
                masked_card: session.masked_card,
                created_at: session.created_at,
                expires_at: session.expires_at,
                server_time: getServerTime(),
                approved_at: session.approved_at,
                declined_at: session.declined_at,
                completed_at: session.completed_at,
                decline_reason: session.decline_reason,
                merchant_logo_url: session.merchant_logo_url,
                category_name: session.category_name,
                category_icon: session.category_icon
            }
        });

    } catch (error) {
        console.error('Error getting session:', error);
        return res.status(500).json({
            success: false,
            error: {
                code: 'INTERNAL_ERROR',
                message: 'Could not get session status'
            }
        });
    }
}

/**
 * GET /card-payments/pending
 * Endpoint AUTENTIFICAT - obtine sesiunile pending ale userului
 */
async function getPendingSessions(req, res) {
    try {
        const userId = req.user.user_id;

        const sessions = await services.cardPaymentService.getPendingSessions(userId);

        return res.status(200).json({
            success: true,
            data: {
                server_time: getServerTime(),
                sessions: sessions.map(s => ({
                    session_id: s.session_id,
                    merchant_name: s.merchant_name,
                    merchant_location: s.merchant_location,
                    amount: s.amount,
                    currency: s.currency,
                    masked_card: s.masked_card,
                    created_at: s.created_at,
                    expires_at: s.expires_at,
                    merchant_logo_url: s.merchant_logo_url,
                    category_name: s.category_name,
                    category_icon: s.category_icon
                }))
            }
        });

    } catch (error) {
        console.error('Error getting pending sessions:', error);
        return res.status(500).json({
            success: false,
            error: {
                code: 'INTERNAL_ERROR',
                message: 'Could not get pending sessions'
            }
        });
    }
}

/**
 * POST /card-payments/:sessionId/approve
 * Endpoint AUTENTIFICAT - aproba o plata
 */
async function approvePayment(req, res) {
    try {
        const userId = req.user.user_id;
        const { sessionId } = req.params;

        const session = await services.cardPaymentService.approveSession(sessionId, userId);

        return res.status(200).json({
            success: true,
            message: 'Payment approved',
            data: {
                session_id: session.session_id,
                status: session.status,
                merchant_name: session.merchant_name,
                amount: session.amount,
                currency: session.currency,
                transaction_id: session.transaction_id,
                reference: session.reference,
                completed_at: session.completed_at,
                server_time: getServerTime(),
                merchant_logo_url: session.merchant_logo_url,
                category_name: session.category_name,
                category_icon: session.category_icon
            }
        });

    } catch (error) {
        console.error('Error approving payment:', error);

        const errorResponses = {
            'SESSION_NOT_FOUND': { status: 404, code: 'SESSION_NOT_FOUND', message: 'Payment session not found' },
            'UNAUTHORIZED': { status: 403, code: 'UNAUTHORIZED', message: 'You are not authorized to approve this payment' },
            'SESSION_NOT_PENDING': { status: 400, code: 'SESSION_NOT_PENDING', message: 'This payment session is no longer pending' },
            'INSUFFICIENT_FUNDS': { status: 400, code: 'INSUFFICIENT_FUNDS', message: 'Insufficient funds' }
        };

        const errResponse = errorResponses[error.message];
        if (errResponse) {
            return res.status(errResponse.status).json({
                success: false,
                error: {
                    code: errResponse.code,
                    message: errResponse.message
                }
            });
        }

        return res.status(500).json({
            success: false,
            error: {
                code: 'PAYMENT_FAILED',
                message: 'Could not approve payment'
            }
        });
    }
}

/**
 * POST /card-payments/:sessionId/decline
 * Endpoint AUTENTIFICAT - refuza o plata
 */
async function declinePayment(req, res) {
    try {
        const userId = req.user.user_id;
        const { sessionId } = req.params;
        const { reason } = req.body;

        const session = await services.cardPaymentService.declineSession(
            sessionId,
            userId,
            reason || 'Declined by user'
        );

        return res.status(200).json({
            success: true,
            message: 'Payment declined',
            data: {
                session_id: session.session_id,
                status: session.status,
                decline_reason: session.decline_reason,
                server_time: getServerTime()
            }
        });

    } catch (error) {
        console.error('Error declining payment:', error);

        if (error.message === 'SESSION_NOT_FOUND') {
            return res.status(404).json({
                success: false,
                error: {
                    code: 'SESSION_NOT_FOUND',
                    message: 'Payment session not found'
                }
            });
        }

        if (error.message === 'UNAUTHORIZED') {
            return res.status(403).json({
                success: false,
                error: {
                    code: 'UNAUTHORIZED',
                    message: 'You are not authorized to decline this payment'
                }
            });
        }

        if (error.message === 'SESSION_NOT_PENDING') {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'SESSION_NOT_PENDING',
                    message: 'This payment session is no longer pending'
                }
            });
        }

        return res.status(500).json({
            success: false,
            error: {
                code: 'DECLINE_FAILED',
                message: 'Could not decline payment'
            }
        });
    }
}

/**
 * POST /card-payments/settlement
 * Endpoint ADMIN - procesează settlement-urile pending
 */
async function processSettlements(req, res) {
    try {
        const result = await services.cardPaymentService.processSettlements();

        return res.status(200).json({
            success: true,
            message: `Settlement completed: ${result.processed} processed, ${result.failed} failed`,
            data: result
        });

    } catch (error) {
        console.error('Error processing settlements:', error);
        return res.status(500).json({
            success: false,
            error: {
                code: 'SETTLEMENT_FAILED',
                message: 'Could not process settlements'
            }
        });
    }
}

function getErrorMessage(errorCode) {
    const messages = {
        'CARD_NOT_FOUND': 'Card not found. Please check your card details.',
        'CARD_BLOCKED': 'This card is blocked.',
        'INVALID_EXPIRY_FORMAT': 'Invalid expiry date format.',
        'EXPIRY_MISMATCH': 'Expiry date does not match.',
        'INVALID_CVV': 'Invalid CVV.'
    };
    return messages[errorCode] || 'Payment declined. Check the card details.';
}

export default {
    startPayment,
    authorizePosPayment,
    getSessionStatus,
    getPendingSessions,
    approvePayment,
    declinePayment,
    processSettlements
};
