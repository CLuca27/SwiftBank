import services from '../../services/index.js';

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function getIdempotencyKey(req) {
    const headerKey = req.headers['idempotency-key'];
    const bodyKey = req.body?.idempotency_key;
    const rawKey = Array.isArray(headerKey) ? headerKey[0] : (headerKey || bodyKey);

    return typeof rawKey === 'string' ? rawKey.trim() : null;
}

/**
 * POST /api/transfers/validate-iban
 * Validează IBAN și returnează info despre bancă/beneficiar
 */
async function validateIBAN(req, res) {
    try {
        const userId = req.user.user_id;
        const { iban } = req.body;

        if (!iban) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'MISSING_IBAN',
                    message: 'IBAN-ul este obligatoriu'
                }
            });
        }

        // Validare format
        const validation = services.transferService.validateIBAN(iban);
        if (!validation.valid) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'INVALID_IBAN',
                    message: validation.error
                }
            });
        }

        const normalizedIBAN = validation.normalized;
        const bic = services.transferService.extractBIC(normalizedIBAN);
        const isSwiftBank = bic === services.transferService.SWIFTBANK_BIC;

        let bankName = null;
        let beneficiaryName = null;
        let accountCurrency = null;
        let profilePhoto = null;
        let isSameUser = false;

        if (isSwiftBank) {
            // Căutăm contul în SwiftBank
            const account = await services.transferService.getSwiftBankAccountByIBAN(normalizedIBAN);

            if (account) {
                bankName = 'SwiftBank';
                accountCurrency = account.currency.trim();

                if (account.users) {
                    beneficiaryName = `${account.users.first_name} ${account.users.last_name}`;
                    profilePhoto = account.users.profile_photo || null;
                }

                // Verifică dacă e contul propriu
                if (account.user_id === userId) {
                    isSameUser = true;
                }
            } else {
                return res.status(404).json({
                    success: false,
                    error: {
                        code: 'ACCOUNT_NOT_FOUND',
                        message: 'Contul SwiftBank nu a fost găsit'
                    }
                });
            }
        } else {
            // Căutăm banca după BIC
            const bank = await services.transferService.getBankByBIC(bic);
            bankName = bank ? bank.name : 'Bancă necunoscută';

            // Verificăm dacă avem beneficiarul salvat
            const beneficiary = await services.transferService.getBeneficiaryByIBAN(userId, normalizedIBAN);
            if (beneficiary) {
                beneficiaryName = beneficiary.name;
            }
        }

        return res.status(200).json({
            success: true,
            data: {
                iban: normalizedIBAN,
                bic: bic,
                bank_name: bankName,
                beneficiary_name: beneficiaryName,
                account_currency: accountCurrency,
                profile_photo: profilePhoto,
                is_swift_bank: isSwiftBank,
                is_same_user: isSameUser
            }
        });

    } catch (error) {
        console.error('Error validating IBAN:', error);
        return res.status(500).json({
            success: false,
            error: {
                code: 'INTERNAL_SERVER_ERROR',
                message: 'Eroare la validarea IBAN-ului'
            }
        });
    }
}

/**
 * POST /api/transfers
 * Creează un transfer nou
 */
async function createTransfer(req, res) {
    try {
        const userId = req.user.user_id;
        const idempotencyKey = getIdempotencyKey(req);
        const {
            from_account_id,
            to_iban,
            beneficiary_name,
            amount,
            description
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
            const existingResponse = await services.transferService.checkIdempotencyKey(
                userId,
                idempotencyKey,
                'POST /api/transfers'
            );

            if (existingResponse) {
                return res.status(existingResponse.response_status).json(existingResponse.response_body);
            }
        }

        // Validări de bază
        if (!from_account_id) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'MISSING_FROM_ACCOUNT',
                    message: 'Selectează contul sursă'
                }
            });
        }

        if (!to_iban) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'MISSING_TO_IBAN',
                    message: 'Introdu IBAN-ul destinatarului'
                }
            });
        }

        if (!beneficiary_name) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'MISSING_BENEFICIARY',
                    message: 'Introdu numele beneficiarului'
                }
            });
        }

        if (!amount || parseFloat(amount) <= 0) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'INVALID_AMOUNT',
                    message: 'Suma trebuie să fie mai mare decât 0'
                }
            });
        }

        // Verifică idempotency key (dacă există)
        if (idempotencyKey) {
            const existingResponse = await services.transferService.checkIdempotencyKey(
                userId,
                idempotencyKey,
                'POST /api/transfers'
            );

            if (existingResponse) {
                // Returnează răspunsul cached
                return res.status(existingResponse.response_status).json(existingResponse.response_body);
            }
        }

        // Execută transferul
        const result = await services.transferService.executeTransfer(userId, {
            fromAccountId: from_account_id,
            toIBAN: to_iban,
            beneficiaryName: beneficiary_name,
            amount: parseFloat(amount),
            description: description
        });

        const response = {
            success: true,
            message: 'Transfer efectuat cu succes',
            data: result
        };

        // Salvează idempotency key
        if (idempotencyKey) {
            await services.transferService.saveIdempotencyKey(
                userId,
                idempotencyKey,
                'POST /api/transfers',
                200,
                response
            );
        }

        return res.status(200).json(response);

    } catch (error) {
        console.error('Error creating transfer:', error);

        const errorResponses = {
            'SOURCE_ACCOUNT_NOT_FOUND': { status: 404, code: 'SOURCE_ACCOUNT_NOT_FOUND', message: 'Contul sursă nu a fost găsit' },
            'UNAUTHORIZED_ACCOUNT': { status: 403, code: 'UNAUTHORIZED', message: 'Nu ai acces la acest cont' },
            'INVALID_IBAN': { status: 400, code: 'INVALID_IBAN', message: 'IBAN invalid' },
            'SAME_ACCOUNT': { status: 400, code: 'SAME_ACCOUNT', message: 'Nu poți transfera către același cont' },
            'DESTINATION_ACCOUNT_NOT_FOUND': { status: 404, code: 'DESTINATION_NOT_FOUND', message: 'Contul destinație nu a fost găsit' },
            'INSUFFICIENT_FUNDS': { status: 400, code: 'INSUFFICIENT_FUNDS', message: 'Fonduri insuficiente' },
            'TRANSFER_FAILED': { status: 500, code: 'TRANSFER_FAILED', message: 'Transferul nu a putut fi efectuat' }
        };

        const errorConfig = errorResponses[error.message];
        if (errorConfig) {
            return res.status(errorConfig.status).json({
                success: false,
                error: {
                    code: errorConfig.code,
                    message: errorConfig.message
                }
            });
        }

        return res.status(500).json({
            success: false,
            error: {
                code: 'INTERNAL_SERVER_ERROR',
                message: 'Eroare la efectuarea transferului'
            }
        });
    }
}

/**
 * GET /api/beneficiaries
 * Lista beneficiarilor salvați
 */
async function getBeneficiaries(req, res) {
    try {
        const userId = req.user.user_id;

        const beneficiaries = await services.transferService.getBeneficiaries(userId);

        return res.status(200).json({
            success: true,
            data: {
                beneficiaries: beneficiaries.map(b => ({
                    beneficiary_id: b.beneficiary_id,
                    name: b.name,
                    iban: b.iban,
                    bank_name: b.bank_name,
                    profile_photo: b.profile_photo || null,
                    created_at: b.created_at
                }))
            }
        });

    } catch (error) {
        console.error('Error getting beneficiaries:', error);
        return res.status(500).json({
            success: false,
            error: {
                code: 'INTERNAL_SERVER_ERROR',
                message: 'Eroare la obținerea beneficiarilor'
            }
        });
    }
}

/**
 * POST /api/beneficiaries
 * Adaugă un beneficiar nou
 */
async function createBeneficiary(req, res) {
    try {
        const userId = req.user.user_id;
        const { iban, name } = req.body;

        if (!iban) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'MISSING_IBAN',
                    message: 'IBAN-ul este obligatoriu'
                }
            });
        }

        if (!name) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'MISSING_NAME',
                    message: 'Numele este obligatoriu'
                }
            });
        }

        // Validare IBAN
        const validation = services.transferService.validateIBAN(iban);
        if (!validation.valid) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'INVALID_IBAN',
                    message: validation.error
                }
            });
        }

        const normalizedIBAN = validation.normalized;

        // Verifică dacă beneficiarul există deja
        const existing = await services.transferService.getBeneficiaryByIBAN(userId, normalizedIBAN);
        if (existing) {
            return res.status(409).json({
                success: false,
                error: {
                    code: 'BENEFICIARY_EXISTS',
                    message: 'Acest beneficiar există deja'
                }
            });
        }

        // Determină banca
        const bic = services.transferService.extractBIC(normalizedIBAN);
        let bankName = null;
        let profilePhoto = null;

        if (bic === services.transferService.SWIFTBANK_BIC) {
            bankName = 'SwiftBank';
            const account = await services.transferService.getSwiftBankAccountByIBAN(normalizedIBAN);
            if (account && account.users) {
                profilePhoto = account.users.profile_photo || null;
            }
        } else {
            const bank = await services.transferService.getBankByBIC(bic);
            bankName = bank ? bank.name : 'Bancă necunoscută';
        }

        // Salvează beneficiarul
        const beneficiary = await services.transferService.saveBeneficiary(
            userId,
            normalizedIBAN,
            name,
            bankName
        );

        return res.status(201).json({
            success: true,
            message: 'Beneficiar adăugat cu succes',
            data: {
                beneficiary_id: beneficiary.beneficiary_id,
                name: beneficiary.name,
                iban: beneficiary.iban,
                bank_name: beneficiary.bank_name,
                profile_photo: profilePhoto,
                created_at: beneficiary.created_at
            }
        });

    } catch (error) {
        console.error('Error creating beneficiary:', error);
        return res.status(500).json({
            success: false,
            error: {
                code: 'INTERNAL_SERVER_ERROR',
                message: 'Eroare la adăugarea beneficiarului'
            }
        });
    }
}

/**
 * DELETE /api/beneficiaries/:id
 * Șterge un beneficiar
 */
async function deleteBeneficiary(req, res) {
    try {
        const userId = req.user.user_id;
        const beneficiaryId = parseInt(req.params.id);

        if (!beneficiaryId || isNaN(beneficiaryId)) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'INVALID_ID',
                    message: 'ID beneficiar invalid'
                }
            });
        }

        await services.transferService.deleteBeneficiary(userId, beneficiaryId);

        return res.status(200).json({
            success: true,
            message: 'Beneficiar șters cu succes'
        });

    } catch (error) {
        console.error('Error deleting beneficiary:', error);

        if (error.code === 'PGRST116') {
            return res.status(404).json({
                success: false,
                error: {
                    code: 'NOT_FOUND',
                    message: 'Beneficiarul nu a fost găsit'
                }
            });
        }

        return res.status(500).json({
            success: false,
            error: {
                code: 'INTERNAL_SERVER_ERROR',
                message: 'Eroare la ștergerea beneficiarului'
            }
        });
    }
}

export default {
    validateIBAN,
    createTransfer,
    getBeneficiaries,
    createBeneficiary,
    deleteBeneficiary
}; 
