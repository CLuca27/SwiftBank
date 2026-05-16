import services from '../../services/index.js';
import config from '../../config/index.js';

// GET /cards - Listează cardurile utilizatorului
async function getCards(req, res) {
    try {
        const userId = req.user.user_id;
        const cards = await services.cardService.getUserCards(userId);

        return res.status(200).json({
            success: true,
            data: { cards }
        });
    } catch (error) {
        console.error('Error getting cards:', error);
        return res.status(500).json({
            success: false,
            error: {
                code: 'INTERNAL_SERVER_ERROR',
                message: 'Eroare la obținerea cardurilor'
            }
        });
    }
}

// POST /cards - Creează un card virtual nou
async function createCard(req, res) {
    try {
        const userId = req.user.user_id;
        const { card_design = 'green' } = req.body;

        // Validăm designul
        const validDesigns = ['green', 'blue', 'purple', 'orange', 'black'];
        if (!validDesigns.includes(card_design)) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'INVALID_DESIGN',
                    message: 'Designul selectat nu este valid'
                }
            });
        }

        const card = await services.cardService.createCard(userId, card_design);

        return res.status(201).json({
            success: true,
            data: { card },
            message: 'Card virtual creat cu succes. Salvează CVV-ul, nu îl vei mai putea vedea!'
        });
    } catch (error) {
        console.error('Error creating card:', error);

        if (error.code === 'CARD_LIMIT_REACHED') {
            return res.status(409).json({
                success: false,
                error: {
                    code: 'CARD_LIMIT_REACHED',
                    message: 'Ai deja numarul maxim de carduri virtuale'
                }
            });
        }

        if (error.code === 'CARD_DESIGN_ALREADY_USED') {
            return res.status(409).json({
                success: false,
                error: {
                    code: 'CARD_DESIGN_ALREADY_USED',
                    message: 'Ai deja un card cu acest design'
                }
            });
        }

        return res.status(500).json({
            success: false,
            error: {
                code: 'INTERNAL_SERVER_ERROR',
                message: 'Eroare la crearea cardului'
            }
        });
    }
}

// POST /cards/:cardId/details - Obține detaliile complete (necesită PIN sau biometric)
async function getCardDetails(req, res) {
    try {
        const userId = req.user.user_id;
        const { cardId } = req.params;
        const { pin } = req.body;

        if (!pin) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'PIN_REQUIRED',
                    message: 'PIN-ul este necesar pentru a vedea detaliile cardului'
                }
            });
        }

        // Verificăm dacă e autentificare biometrică
        if (pin === 'biometric') {
            // Verificăm dacă utilizatorul are biometria activată
            const { data: prefs, error: prefsError } = await config.supabase
                .from('user_preferences')
                .select('settings')
                .eq('user_id', userId)
                .single();

            const biometricEnabled = prefs?.settings?.security?.biometric_enabled;
            if (!biometricEnabled) {
                return res.status(401).json({
                    success: false,
                    error: {
                        code: 'BIOMETRIC_NOT_ENABLED',
                        message: 'Autentificarea biometrică nu este activată'
                    }
                });
            }
        } else {
            // Obținem user-ul cu password_hash pentru verificare PIN
            const { data: user, error: userError } = await config.supabase
                .from('users')
                .select('password_hash')
                .eq('user_id', userId)
                .single();

            if (userError || !user) {
                return res.status(401).json({
                    success: false,
                    error: {
                        code: 'USER_NOT_FOUND',
                        message: 'Utilizatorul nu a fost găsit'
                    }
                });
            }

            // Verificăm PIN-ul
            const validPin = await services.authService.verifyPassword(pin, user.password_hash);
            if (!validPin) {
                return res.status(403).json({
                    success: false,
                    error: {
                        code: 'INVALID_PIN',
                        message: 'PIN incorect'
                    }
                });
            }
        }

        const card = await services.cardService.getCardDetails(cardId, userId);

        if (!card) {
            return res.status(404).json({
                success: false,
                error: {
                    code: 'CARD_NOT_FOUND',
                    message: 'Cardul nu a fost găsit'
                }
            });
        }

        // Decriptăm CVV-ul pentru afișare
        const cvv = services.cardService.decryptCVV(card.cvv_hash);

        return res.status(200).json({
            success: true,
            data: {
                card: {
                    card_id: card.card_id,
                    card_number: card.card_number,
                    card_number_formatted: services.cardService.formatCardNumber(card.card_number),
                    card_holder_name: card.card_holder_name,
                    expiry_date: card.expiry_date,
                    cvv: cvv,
                    card_brand: card.card_brand,
                    primary_currency: card.primary_currency,
                    status: card.status
                }
            }
        });
    } catch (error) {
        console.error('Error getting card details:', error);
        return res.status(500).json({
            success: false,
            error: {
                code: 'INTERNAL_SERVER_ERROR',
                message: 'Eroare la obținerea detaliilor cardului'
            }
        });
    }
}

// PUT /cards/:cardId/block - Blochează/deblochează cardul
async function toggleBlock(req, res) {
    try {
        const userId = req.user.user_id;
        const { cardId } = req.params;

        const result = await services.cardService.toggleCardBlock(cardId, userId);

        const message = result.status === 'blocked'
            ? 'Cardul a fost blocat'
            : 'Cardul a fost deblocat';

        return res.status(200).json({
            success: true,
            data: result,
            message
        });
    } catch (error) {
        console.error('Error toggling card block:', error);

        if (error.message === 'Card not found') {
            return res.status(404).json({
                success: false,
                error: {
                    code: 'CARD_NOT_FOUND',
                    message: 'Cardul nu a fost găsit'
                }
            });
        }

        return res.status(500).json({
            success: false,
            error: {
                code: 'INTERNAL_SERVER_ERROR',
                message: 'Eroare la modificarea statusului cardului'
            }
        });
    }
}

// DELETE /cards/:cardId - Șterge cardul
async function deleteCard(req, res) {
    try {
        const userId = req.user.user_id;
        const { cardId } = req.params;

        await services.cardService.deleteCard(cardId, userId);

        return res.status(200).json({
            success: true,
            message: 'Cardul a fost șters cu succes'
        });
    } catch (error) {
        console.error('Error deleting card:', error);
        return res.status(500).json({
            success: false,
            error: {
                code: 'INTERNAL_SERVER_ERROR',
                message: 'Eroare la ștergerea cardului'
            }
        });
    }
}

// PUT /cards/:cardId/currency - Actualizează moneda primară
async function updateCurrency(req, res) {
    try {
        const userId = req.user.user_id;
        const { cardId } = req.params;
        const { currency } = req.body;

        const validCurrencies = ['RON', 'EUR', 'USD', 'GBP'];
        if (!currency || !validCurrencies.includes(currency)) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'INVALID_CURRENCY',
                    message: 'Moneda selectată nu este validă'
                }
            });
        }

        const card = await services.cardService.updatePrimaryCurrency(cardId, userId, currency);

        return res.status(200).json({
            success: true,
            data: { primary_currency: card.primary_currency },
            message: 'Moneda primară a fost actualizată'
        });
    } catch (error) {
        console.error('Error updating card currency:', error);
        return res.status(500).json({
            success: false,
            error: {
                code: 'INTERNAL_SERVER_ERROR',
                message: 'Eroare la actualizarea monedei'
            }
        });
    }
}

export default {
    getCards,
    createCard,
    getCardDetails,
    toggleBlock,
    deleteCard,
    updateCurrency
};
