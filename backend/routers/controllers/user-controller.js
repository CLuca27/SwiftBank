import config from '../../config/index.js';
import services from '../../services/index.js';

// Default settings structure
const DEFAULT_SETTINGS = {
    security: {
        biometric_enabled: false
    },
    notifications: {
        push_enabled: true,
        email_enabled: true,
        transaction_alerts: true
    },
    display: {
        hide_balance: false,
        language: "ro"
    }
};

async function getProfile(req, res) {
    try {
        const userId = req.user.user_id;

        const { data: user, error } = await config.supabase
            .from('users')
            .select('user_id, email, phone, first_name, last_name, birth_date, address, created_at')
            .eq('user_id', userId)
            .single();

        if (error || !user) {
            return res.status(404).json({
                success: false,
                error: {
                    code: 'USER_NOT_FOUND',
                    message: 'Utilizatorul nu a fost găsit'
                }
            });
        }

        return res.status(200).json({
            success: true,
            data: {
                user_id: user.user_id,
                email: user.email,
                phone: user.phone,
                first_name: user.first_name,
                last_name: user.last_name,
                birth_date: user.birth_date,
                address: user.address,
                created_at: user.created_at
            }
        });

    } catch (error) {
        console.error('Error getting profile:', error);
        return res.status(500).json({
            success: false,
            error: {
                code: 'INTERNAL_SERVER_ERROR',
                message: 'Eroare la obținerea profilului'
            }
        });
    }
}

async function getSettings(req, res) {
    try {
        const userId = req.user.user_id;

        const { data, error } = await config.supabase
            .from('user_preferences')
            .select('settings')
            .eq('user_id', userId)
            .single();

        if (error) {
            // Dacă nu există, creăm cu defaults
            if (error.code === 'PGRST116') {
                const { data: newData, error: insertError } = await config.supabase
                    .from('user_preferences')
                    .insert({ user_id: userId, settings: DEFAULT_SETTINGS })
                    .select('settings')
                    .single();

                if (insertError) {
                    throw insertError;
                }

                return res.status(200).json({
                    success: true,
                    data: { settings: newData.settings }
                });
            }
            throw error;
        }

        // Merge cu defaults pentru câmpuri lipsă
        const settings = deepMerge(DEFAULT_SETTINGS, data.settings || {});

        return res.status(200).json({
            success: true,
            data: { settings }
        });

    } catch (error) {
        console.error('Error getting settings:', error);
        return res.status(500).json({
            success: false,
            error: {
                code: 'INTERNAL_SERVER_ERROR',
                message: 'Eroare la obținerea setărilor'
            }
        });
    }
}

async function updateSettings(req, res) {
    try {
        const userId = req.user.user_id;
        const updates = req.body.settings;

        if (!updates || typeof updates !== 'object') {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'INVALID_REQUEST',
                    message: 'Setările trebuie să fie un obiect valid'
                }
            });
        }

        // Obține setările curente
        const { data: current, error: fetchError } = await config.supabase
            .from('user_preferences')
            .select('settings')
            .eq('user_id', userId)
            .single();

        let currentSettings = DEFAULT_SETTINGS;
        if (!fetchError && current?.settings) {
            currentSettings = current.settings;
        }

        // Deep merge cu noile setări
        const newSettings = deepMerge(currentSettings, updates);

        // Upsert în DB
        const { data, error } = await config.supabase
            .from('user_preferences')
            .upsert({
                user_id: userId,
                settings: newSettings
            }, {
                onConflict: 'user_id'
            })
            .select('settings')
            .single();

        if (error) {
            throw error;
        }

        return res.status(200).json({
            success: true,
            data: { settings: data.settings }
        });

    } catch (error) {
        console.error('Error updating settings:', error);
        return res.status(500).json({
            success: false,
            error: {
                code: 'INTERNAL_SERVER_ERROR',
                message: 'Eroare la actualizarea setărilor'
            }
        });
    }
}

async function registerDevice(req, res) {
    try {
        const userId = req.user.user_id;
        const { fcm_token, device_id, device_name } = req.body;

        if (!fcm_token || !device_id) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'MISSING_DATA',
                    message: 'FCM token și device_id sunt obligatorii'
                }
            });
        }

        await services.notificationService.registerDevice(
            userId,
            device_id,
            device_name || 'Unknown Device',
            fcm_token
        );

        return res.status(200).json({
            success: true,
            message: 'Dispozitiv înregistrat cu succes'
        });

    } catch (error) {
        console.error('Error registering device:', error);
        return res.status(500).json({
            success: false,
            error: {
                code: 'INTERNAL_SERVER_ERROR',
                message: 'Eroare la înregistrarea dispozitivului'
            }
        });
    }
}

async function unregisterDevice(req, res) {
    try {
        const userId = req.user.user_id;
        const { deviceId } = req.params;

        if (!deviceId) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'MISSING_DEVICE_ID',
                    message: 'Device ID este obligatoriu'
                }
            });
        }

        await services.notificationService.unregisterDevice(userId, deviceId);

        return res.status(200).json({
            success: true,
            message: 'Dispozitiv șters cu succes'
        });

    } catch (error) {
        console.error('Error unregistering device:', error);
        return res.status(500).json({
            success: false,
            error: {
                code: 'INTERNAL_SERVER_ERROR',
                message: 'Eroare la ștergerea dispozitivului'
            }
        });
    }
}

async function changePin(req, res) {
    try {
        const userId = req.user.user_id;
        const { current_pin, new_pin } = req.body;

        // Validare input
        if (!current_pin || !new_pin) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'MISSING_DATA',
                    message: 'PIN-ul curent și noul PIN sunt obligatorii'
                }
            });
        }

        // Validare format PIN (6 cifre)
        const pinRegex = /^\d{6}$/;
        if (!pinRegex.test(new_pin)) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'INVALID_PIN_FORMAT',
                    message: 'PIN-ul trebuie să conțină exact 6 cifre'
                }
            });
        }

        // Verifică că PIN-ul nou e diferit de cel curent
        if (current_pin === new_pin) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'SAME_PIN',
                    message: 'Noul PIN trebuie să fie diferit de cel curent'
                }
            });
        }

        // Obține user-ul cu password_hash
        const { data: user, error: fetchError } = await config.supabase
            .from('users')
            .select('user_id, password_hash')
            .eq('user_id', userId)
            .single();

        if (fetchError || !user) {
            return res.status(404).json({
                success: false,
                error: {
                    code: 'USER_NOT_FOUND',
                    message: 'Utilizatorul nu a fost găsit'
                }
            });
        }

        // Verifică PIN-ul curent
        const validPassword = await services.authService.verifyPassword(
            current_pin,
            user.password_hash
        );

        if (!validPassword) {
            return res.status(401).json({
                success: false,
                error: {
                    code: 'INVALID_PIN',
                    message: 'PIN-ul curent este incorect'
                }
            });
        }

        // Hash noul PIN
        const newPasswordHash = await services.authService.hashPassword(new_pin);

        // Actualizează PIN-ul în DB
        const { error: updateError } = await config.supabase
            .from('users')
            .update({ password_hash: newPasswordHash })
            .eq('user_id', userId);

        if (updateError) {
            throw updateError;
        }

        return res.status(200).json({
            success: true,
            message: 'PIN-ul a fost schimbat cu succes'
        });

    } catch (error) {
        console.error('Error changing PIN:', error);
        return res.status(500).json({
            success: false,
            error: {
                code: 'INTERNAL_SERVER_ERROR',
                message: 'Eroare la schimbarea PIN-ului'
            }
        });
    }
}

// Helper pentru deep merge
function deepMerge(target, source) {
    const result = { ...target };

    for (const key of Object.keys(source)) {
        if (source[key] && typeof source[key] === 'object' && !Array.isArray(source[key])) {
            result[key] = deepMerge(result[key] || {}, source[key]);
        } else {
            result[key] = source[key];
        }
    }

    return result;
}

export default {
    getProfile,
    getSettings,
    updateSettings,
    registerDevice,
    unregisterDevice,
    changePin
};
