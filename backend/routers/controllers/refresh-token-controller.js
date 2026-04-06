import config from '../../config/index.js'
import jwt from 'jsonwebtoken' 
import services from '../../services/index.js';

async function refresh(req, res) {
    try {
        const { refresh_token, device_id } = req.body;
        
        if (!refresh_token || !device_id) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'MISSING_REQUIRED_FIELDS',
                    message: 'Refresh token și device_id sunt obligatorii'
                }
            });
        }
        
        let decoded;
        try {
            decoded = jwt.verify(refresh_token, process.env.JWT_REFRESH_SECRET);
        } catch (err) { 
            console.log(err);
            return res.status(403).json({
                success: false,
                error: {
                    code: 'INVALID_REFRESH_TOKEN',
                    message: 'Refresh token invalid sau expirat'
                }
            });
        } 

        const { data: user, error: userError } = await config.supabase
            .from('users')
            .select('user_id, email, first_name, last_name, status, locked_until')
            .eq('user_id', decoded.user_id)
            .single();

        if (userError || !user) {
            return res.status(403).json({
                success: false,
                error: {
                    code: 'USER_NOT_FOUND',
                    message: 'User inexistent'
                }
            });  
        } 

        if (user.status === 'BLOCKED') {
            return res.status(403).json({
                success: false,
                error: {
                    code: 'ACCOUNT_BLOCKED',
                    message: 'Cont blocat permanent'
                }
            });
        }

        // SINGLE DEVICE: Un singur token per user
        const { data: storedToken, error: findError } = await config.supabase
            .from('refresh_tokens')
            .select('*')
            .eq('user_id', decoded.user_id)
            .maybeSingle();

        if (findError) 
        {
            console.error('Eroare la cautarea tokenului', findError); 
            return res.status(403).json({
                success: false,
                error: {
                    code: 'TOKEN_SEARCH_ERROR',
                    message: 'Eroare la căutarea tokenului'
                }
            })
        }
        
        // Token nu există în DB (posibil reuse attack sau logout efectuat)
        if (!storedToken) {
            console.log('Token valid JWT dar nu există în DB - posibil reuse attack');

            return res.status(403).json({
                success: false,
                error: {
                    code: 'SESSION_COMPROMISED',
                    message: 'Sesiune compromisă. Reautentificare necesară.'
                }
            });
        }

        // Verifică hash-ul
        const isMatch = await services.authService.cryptoCompare(refresh_token, storedToken.token_hash);
        if (!isMatch) {
            console.log('Reuse detection: hash nu coincide!');
            
            await config.supabase
                .from('refresh_tokens')
                .delete()
                .eq('user_id', decoded.user_id);
            
            return res.status(403).json({
                success: false,
                error: {
                    code: 'SESSION_COMPROMISED',
                    message: 'Sesiune compromisă. Reautentificare necesară.'
                }
            });
        }

        // DEVICE BINDING: Verifică device_id
        if (storedToken.device_id !== device_id) {
            console.log(`Device mismatch! DB: ${storedToken.device_id}, Request: ${device_id}`);
            
            await config.supabase
                .from('refresh_tokens')
                .delete()
                .eq('token_id', storedToken.token_id);
            
            return res.status(403).json({
                success: false,
                error: {
                    code: 'DEVICE_MISMATCH',
                    message: 'Sesiune invalidă pe acest dispozitiv'
                }
            });
        }
        
        // Generează tokeni noi ÎNTÂI
        const newAccessToken = services.authService.generateAccessToken(user);
        const newRefreshToken = services.authService.generateRefreshToken(user);
        const newTokenHash = services.authService.hashCrypto(newRefreshToken);

        // Salvează noul token ÎNTÂI (pentru robustețe)
        const { error: insertError } = await config.supabase
            .from('refresh_tokens')
            .insert({
                user_id: user.user_id,
                token_hash: newTokenHash,
                device_id: device_id,
                device_name: storedToken.device_name
            });

        if (insertError) {
            console.error('Eroare la salvarea noului token:', insertError);
            return res.status(500).json({
                success: false,
                error: {
                    code: 'TOKEN_SAVE_ERROR',
                    message: 'Eroare la reînnoirea sesiunii'
                }
            });
        }

        // Șterge vechiul token DUPĂ ce noul e salvat
        await config.supabase
            .from('refresh_tokens')
            .delete()
            .eq('token_id', storedToken.token_id);
        
        return res.status(200).json({
            success: true,
            data: {
                access_token: newAccessToken,
                refresh_token: newRefreshToken, 
                user: {
                    user_id: user.user_id,
                    first_name: user.first_name, 
                    last_name: user.last_name,
                    email: user.email
                }
            }
        });
        
    } catch (error) {
        console.error('Eroare refresh:', error);
        return res.status(500).json({
            success: false,
            error: {
                code: 'INTERNAL_SERVER_ERROR',
                message: 'Eroare internă server'
            }
        });
    }
}

export default refresh;