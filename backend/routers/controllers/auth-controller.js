import crypto from 'crypto';
import config from '../../config/index.js';
import services from '../../services/index.js';
import jwt from 'jsonwebtoken'; 


async function check(req, res) {
    try {
        const { field, value } = req.body;

        if (!field || !value) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'MISSING_REQUIRED_FIELDS',
                    message: 'Campurile field si value sunt obligatorii'
                }
            });
        }

        const normalizedField = field.trim().toLowerCase();
        const normalizedValue = typeof value === 'string' ? value.trim() : value;
        
        let existingData = null; 

        switch (normalizedField) { 

            case 'phone': {
                const normalizedPhone = normalizedValue.replace(/\s/g, '');
                const phoneRegex = /^\+40[0-9]{9}$/;

                if (!phoneRegex.test(normalizedPhone)) {
                    return res.status(400).json({
                        success: false,
                        error: {
                            code: 'INVALID_PHONE_FORMAT',
                            message: 'Format telefon invalid'
                        }
                    });
                }

                const { data, error } = await config.supabase
                    .from('users')
                    .select('user_id')
                    .eq('phone', normalizedPhone)
                    .maybeSingle();

                if (error) throw error; 

                existingData = data; 
                break;
                
            }

            case 'email': { 
                const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;  

                if (!emailRegex.test(normalizedValue)) {
                    return res.status(400).json({
                        success: false,
                        error: {
                            code: 'INVALID_EMAIL_FORMAT',
                            message: 'Format email invalid'
                        }
                    });
                }

                const { data, error } = await config.supabase
                    .from('users')
                    .select('user_id')
                    .eq('email', normalizedValue)
                    .maybeSingle();

                if (error) throw error; 
                existingData = data; 
                break;

            }

            case 'cnp': {
                const cnpRegex = /^[1-9]\d{12}$/;

                if (!cnpRegex.test(normalizedValue)) {
                    return res.status(400).json({
                        success: false,
                        error: {
                            code: 'INVALID_CNP_FORMAT',
                            message: 'Format CNP invalid'
                        }
                    });
                }

                const cnpHash = services.authService.hashCrypto(normalizedValue);

                const { data, error } = await config.supabase
                    .from('users')
                    .select('user_id')
                    .eq('cnp_hash', cnpHash)
                    .maybeSingle();

                if (error) throw error;  

                existingData = data;
                break;
            }

            default:
                return res.status(400).json({
                    success: false,
                    error: {
                        code: 'INVALID_FIELD',
                        message: 'Camp invalid. Valorile permise sunt: phone, email, cnp'
                    }
                }); 

        } 

        return res.status(200).json({
            success: true,
            data: {
                exists: !!existingData
            }
        });
       

    } catch (error) {
        console.error('Eroare check:', error);
        return res.status(500).json({
            success: false,
            error: {
                code: 'INTERNAL_SERVER_ERROR',
                message: 'Eroare interna server'
            }
        });
    }
}

async function sendOTP(req, res) {
    try {
        const { phone, email, purpose } = req.body;
        
        if (!phone && !email) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'MISSING_REQUIRED_FIELDS',
                    message: 'Telefon sau email obligatoriu'
                }
            });
        } 

        const validPurposes = ['REGISTER_PHONE', 'REGISTER_EMAIL'];
        if (!purpose || !validPurposes.includes(purpose)) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'INVALID_PURPOSE',
                    message: 'Purpose invalid'
                }
            });
        }

        // Verifică cooldown (30 secunde)
        const identifier = phone || email;
        const identifierColumn = phone ? 'phone' : 'email';
        
        const { data: recentOtp } = await config.supabase
            .from('otp_codes')
            .select('created_at')
            .eq(identifierColumn, identifier)
            .eq('purpose', purpose)
            .order('created_at', { ascending: false })
            .limit(1)
            .maybeSingle();  

        if (recentOtp) {
            const secondsSince = (Date.now() - new Date(recentOtp.created_at + 'Z').getTime()) / 1000;
            console.log(`Secunde de la ultimul OTP: ${secondsSince}`)
            if (secondsSince < 30) {
                const secondsLeft = Math.ceil(30 - secondsSince);
                return res.status(429).json({
                    success: false,
                    error: {
                        code: 'OTP_COOLDOWN',
                        message: `Așteaptă ${secondsLeft} secunde`,
                        seconds_left: secondsLeft
                    }
                });
            }
        }
        
        // Generează și salvează codul
        const code = services.otpServices.generateCode();
        
        await services.otpServices.saveCode({
            phone: phone || null,
            email: email || null,
            code,
            purpose
        });
        
        // Trimite codul
        if (phone) { 
            console.log(`Trimitere cod ${code} către telefonul ${phone}`)
            //await services.smsService.sendOTP_SMS(phone, code); 

        } else { 
            console.log(`Trimitere cod ${code} către emailul ${email}`)
            await services.emailService.sendOTP_Email(email, code);
        }
        
        return res.status(200).json({
            success: true,
            message: phone ? 'Cod trimis pe SMS' : 'Cod trimis pe email'
        });
        
    } catch (error) {
        console.error('Eroare sendOTP:', error);
        return res.status(500).json({
            success: false,
            error: {
                code: 'INTERNAL_SERVER_ERROR',
                message: 'Eroare la trimiterea codului'
            }
        });
    }
}

async function verifyOTP(req, res) {
    try {
        const { phone, email, code, purpose } = req.body;
        
        if ((!phone && !email) || !code || !purpose) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'MISSING_REQUIRED_FIELDS',
                    message: 'Date incomplete'
                }
            });
        }
        
        const result = await services.otpServices.checkCode({
            phone: phone || null,
            email: email || null,
            code,
            purpose
        });
        
        if (!result.success) {
            return res.status(400).json({
                success: false,
                error: result.error
            });
        }
        
        return res.status(200).json({
            success: true,
            message: 'Cod verificat cu succes'
        });
        
    } catch (error) {
        console.error('Eroare verifyOTP:', error);
        return res.status(500).json({
            success: false,
            error: {
                code: 'INTERNAL_SERVER_ERROR',
                message: 'Eroare la verificarea codului'
            }
        });
    }
}

async function register(req, res) {
    try {
        const {
            phone, email, password,
            first_name, last_name, cnp,
            address
        } = req.body;

        const requiredFields = {
            phone: 'Numărul de telefon',
            email: 'Adresa de email',
            password: 'PIN-ul',
            first_name: 'Prenumele',
            last_name: 'Numele',
            cnp: 'CNP-ul',
            address: 'Adresa'
        };

        for (const [field, label] of Object.entries(requiredFields)) {
            if (!req.body[field]) {
                return res.status(400).json({
                    success: false,
                    error: {
                        code: 'MISSING_REQUIRED_FIELDS',
                        message: `Câmpul ${label} este obligatoriu`
                    }
                });
            }
        }

        const cnpRegex = /^[1-9]\d{12}$/;
        if (!cnpRegex.test(cnp)) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'INVALID_CNP_FORMAT',
                    message: 'Format CNP invalid'
                }
            });
        } 

        // PIN de 6 cifre
        const pinRegex = /^\d{6}$/;
        if (!pinRegex.test(password)) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'INVALID_PIN_FORMAT',
                    message: 'PIN-ul trebuie să conțină exact 6 cifre'
                }
            });
        }

        const cnpHash = services.authService.hashCrypto(cnp);

        const { data: existingCNP } = await config.supabase
            .from('users')
            .select('user_id')
            .eq('cnp_hash', cnpHash)
            .maybeSingle();

        if (existingCNP) {
            return res.status(409).json({
                success: false,
                error: {
                    code: 'CNP_ALREADY_REGISTERED',
                    message: 'CNP-ul este deja înregistrat'
                }
            });
        }

        const birthDateFromCNP = services.authService.extractBirthDateFromCNP(cnp);
        const passwordHash = await services.authService.hashPassword(password);

        const { data: newUser, error: createError } = await config.supabase
            .from('users')
            .insert({
                phone,
                email,
                password_hash: passwordHash,
                first_name,
                last_name,
                cnp_hash: cnpHash,
                birth_date: birthDateFromCNP,
                address,
                status: 'ACTIVE',
                failed_attempts: 0
            })
            .select('user_id, email, first_name, last_name')
            .single();

        if (createError) {
            throw createError;
        }

        const accessToken = services.authService.generateAccessToken(newUser);
        const refreshToken = services.authService.generateRefreshToken(newUser);

        const refreshTokenHash = crypto.createHash('sha256').update(refreshToken).digest('hex');
        const expiresAt = new Date();
        expiresAt.setDate(expiresAt.getDate() + 7);

        await config.supabase
            .from('refresh_tokens')
            .insert({
                user_id: newUser.user_id,
                token_hash: refreshTokenHash,
                expires_at: expiresAt.toISOString(),
                used: false,
                revoked: false
            });

        return res.status(201).json({
            success: true,
            message: 'Cont creat cu succes',
            data: {
                access_token: accessToken,
                refresh_token: refreshToken
            }
        });

    } catch (error) {
        console.error('Eroare register:', error);
        return res.status(500).json({
            success: false,
            error: {
                code: 'INTERNAL_SERVER_ERROR',
                message: 'Eroare la crearea contului'
            }
        });
    }
}

async function identify(req, res) {
    try {
        const { phone, email } = req.body;
        
        if (!phone && !email) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'MISSING_REQUIRED_FIELDS',
                    message: 'Telefonul sau email-ul este obligatoriu'
                }
            });
        }
        
        let query = config.supabase
            .from('users')
            .select('user_id, first_name, status, locked_until');
        
        if (phone) {
            query = query.eq('phone', phone);
        } else {
            query = query.eq('email', email);
        }
        
        const { data: user, error: findError } = await query.maybeSingle();
        
        if (findError || !user) {
            return res.status(404).json({
                success: false,
                error: {
                    code: 'USER_NOT_FOUND',
                    message: 'Nu există cont cu aceste date'
                }
            });
        }
        
        if (user.status === 'LOCKED' && user.locked_until) {
            const lockedUntil = new Date(user.locked_until + 'Z');
            const now = new Date();
            
            if (now >= lockedUntil) {
                await config.supabase
                    .from('users')
                    .update({ 
                        status: 'ACTIVE',
                        locked_until: null, 
                        failed_attempts: 0 
                    })
                    .eq('user_id', user.user_id);
                
                user.status = 'ACTIVE';
                user.locked_until = null;
            }
        }

        
        return res.status(200).json({
            success: true,
            data: {
                first_name: user.first_name,
                status: user.status,
                locked_until: user.locked_until
            }
        });
        
    } catch (error) {
        console.error('Eroare identify:', error);
        return res.status(500).json({
            success: false,
            error: {
                code: 'INTERNAL_SERVER_ERROR',
                message: 'Eroare internă server'
            }
        });
    }
}

async function login(req, res) {
    try {
        const { phone, email, password, device_id, device_name } = req.body;
        
        if ((!phone && !email) || !password) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'MISSING_REQUIRED_FIELDS',
                    message: 'Telefon/email și PIN-ul sunt obligatorii'
                }
            });
        }

        if (!device_id) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'MISSING_DEVICE_ID',
                    message: 'Device ID este obligatoriu'
                }
            });
        }
        
        let query = config.supabase.from('users').select('*');
        
        if (phone) {
            query = query.eq('phone', phone);
        } else {
            query = query.eq('email', email);
        }
        
        const { data: user, error: findError } = await query.maybeSingle();
        
        if (findError || !user) {
            return res.status(401).json({
                success: false,
                error: {
                    code: 'INVALID_CREDENTIALS',
                    message: 'Date incorecte'
                }
            });
        }
        
        // Verifică dacă contul e blocat temporar
        if (user.status === 'LOCKED') {
            const lockedUntil = new Date(user.locked_until + 'Z');
            const now = new Date();

            if (now >= lockedUntil) { 
                await config.supabase
                    .from('users')
                    .update({
                        status: 'ACTIVE',
                        locked_until: null,
                        failed_attempts: 0
                    })
                    .eq('user_id', user.user_id);

                user.status = 'ACTIVE';
                user.locked_until = null;
                user.failed_attempts = 0;
            } else { 
                const secondsLeft = Math.ceil((lockedUntil - now) / 1000);
                return res.status(403).json({
                    success: false,
                    error: {
                        code: 'ACCOUNT_LOCKED',
                        message: 'Contul este blocat temporar',
                        seconds_left: secondsLeft,
                        locked_until: user.locked_until
                    }
                });
            }
        }

        // Verifică dacă contul e blocat permanent
        if (user.status === 'BLOCKED') {
            return res.status(403).json({
                success: false,
                error: {
                    code: 'ACCOUNT_BLOCKED',
                    message: 'Contul este blocat permanent. Contactează suportul.'
                }
            });
        }

        // Verifică PIN-ul
        const validPassword = await services.authService.verifyPassword(
            password,
            user.password_hash
        );
        
        if (!validPassword) {
            const newFailedAttempts = (user.failed_attempts || 0) + 1;
            const maxAttempts = parseInt(process.env.MAX_LOGIN_ATTEMPTS) || 3;
            const attemptsLeft = maxAttempts - newFailedAttempts;
            let updateData = { failed_attempts: newFailedAttempts };
            
            // Verifică dacă trebuie blocat
            if (newFailedAttempts >= maxAttempts) {
                const currentLockCount = user.lock_count || 0;
                const lockMinutes = services.authService.getLockDuration(currentLockCount);
                
                if (lockMinutes === null) {
                    // Blocare permanentă
                    updateData.status = 'BLOCKED'; 
                    updateData.locked_until = null;
                    updateData.lock_count = currentLockCount + 1;
                    
                    await config.supabase
                        .from('users')
                        .update(updateData)
                        .eq('user_id', user.user_id);

                    return res.status(403).json({
                        success: false,
                        error: {
                            code: 'ACCOUNT_BLOCKED',
                            message: 'Prea multe încercări. Contul a fost blocat permanent. Contactează suportul.'
                        }
                    });
                } else {
                    // Blocare temporară
                    const lockedUntil = new Date();
                    lockedUntil.setMinutes(lockedUntil.getMinutes() + lockMinutes);
                    
                    updateData.status = 'LOCKED';
                    updateData.locked_until = lockedUntil.toISOString();
                    updateData.lock_count = currentLockCount + 1;
                    
                    await config.supabase
                        .from('users')
                        .update(updateData)
                        .eq('user_id', user.user_id);

                    return res.status(403).json({
                        success: false,
                        error: {
                            code: 'ACCOUNT_LOCKED',
                            message: `Prea multe încercări. Contul a fost blocat pentru ${lockMinutes} minute.`,
                            seconds_left: lockMinutes * 60,
                            locked_until: lockedUntil.toISOString()
                        }
                    });
                }
            }
            
            // Nu e încă blocat, doar incrementează
            await config.supabase
                .from('users')
                .update(updateData)
                .eq('user_id', user.user_id);
            
            return res.status(401).json({
                success: false,
                error: {
                    code: 'INVALID_PIN',
                    message: 'PIN incorect',
                    attempts_left: attemptsLeft
                }
            });
        }
        
        // PIN corect - resetează contoarele
        await config.supabase
            .from('users')
            .update({ 
                failed_attempts: 0,
                lock_count: 0,
                locked_until: null
            })
            .eq('user_id', user.user_id);
        
        // Generează tokens
        const accessToken = services.authService.generateAccessToken(user);
        const refreshToken = services.authService.generateRefreshToken(user);
        const refreshTokenHash = await services.authService.hashCrypto(refreshToken);

        // SINGLE DEVICE: Șterge TOATE sesiunile vechi
        await config.supabase
            .from('refresh_tokens')
            .delete()
            .eq('user_id', user.user_id);

        // Salvează noul token CU device_id și device_name
        await config.supabase
            .from('refresh_tokens')
            .insert({
                user_id: user.user_id,
                token_hash: refreshTokenHash,
                device_id: device_id,
                device_name: device_name || 'Unknown Device'
            });
        
        return res.status(200).json({
            success: true,
            message: 'Autentificare reușită',
            data: {
                access_token: accessToken,
                refresh_token: refreshToken,
                user: {
                    user_id: user.user_id,
                    email: user.email,
                    first_name: user.first_name,
                    last_name: user.last_name
                }
            }
        });
        
    } catch (error) {
        console.error('Eroare login:', error);
        return res.status(500).json({
            success: false,
            error: {
                code: 'INTERNAL_SERVER_ERROR',
                message: 'Eroare la autentificare'
            }
        });
    }
}

async function logout(req, res) {
    try {
        const { refresh_token } = req.body;
        
        if (!refresh_token) {  
            console.log("Lipsește token-ul");
            return res.sendStatus(204);
        }
        
        let decoded;
        try {
            decoded = jwt.verify(refresh_token, process.env.JWT_REFRESH_SECRET);
        } catch (err) { 
            console.log(err);
            return res.sendStatus(204);
        }
        
        const { data: storedToken } = await config.supabase
            .from('refresh_tokens')
            .select('*')
            .eq('user_id', decoded.user_id)
            .maybeSingle();
        
        if (!storedToken) { 
            console.log("Nu există token");
            return res.sendStatus(204);
        }
        
        const isMatch = await services.authService.cryptoCompare(refresh_token, storedToken.token_hash);

        if (isMatch) {
            await config.supabase
                .from('refresh_tokens')
                .delete()
                .eq('token_id', storedToken.token_id);
            
            console.log("Token-ul a fost șters!");
        } else {
            console.log("Token-urile nu coincid!");
        }
        
        return res.sendStatus(204);
        
    } catch (error) {
        console.error('Eroare logout:', error);
        return res.sendStatus(204);
    }
}


export default {
    check,
    sendOTP,
    verifyOTP,
    register,
    identify,
    login,
    logout
}