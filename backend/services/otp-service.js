import crypto from 'crypto';
import config from '../config/index.js'
import bcrypt from 'bcrypt' 

function generateCode(){
    
    const internalSecret = process.env.OTP_INTERNAL_SECRET || crypto.randomBytes(32);
    
   
    const randomBytes = crypto.randomBytes(32);
    
    
    const timestamp = Date.now().toString() + process.hrtime.bigint().toString();
    
    
    if (!generateCode.counter) generateCode.counter = 0;
    generateCode.counter++;
    const counter = generateCode.counter.toString();
    
    
    const data = Buffer.concat([
        randomBytes,
        Buffer.from(timestamp),
        Buffer.from(counter)
    ]);
    
    
    const hmac = crypto.createHmac('sha512', internalSecret)
        .update(data)
        .digest('hex');
    
    
    const numericValue = BigInt('0x' + hmac.slice(0, 12));
    const otp = (numericValue % 1000000n).toString().padStart(6, '0');
    
    return otp;
} 

async function saveCode({ phone, email, code, purpose }) {
    try {
        
        let invalidateQuery = config.supabase
            .from('otp_codes')
            .update({ used: true })
            .eq('purpose', purpose)
            .eq('used', false);
        
        if (phone) {
            invalidateQuery = invalidateQuery.eq('phone', phone);
        } else if (email) {
            invalidateQuery = invalidateQuery.eq('email', email);
        }
        
    
        const { error: invalidateError } = await invalidateQuery;
        
        if (invalidateError) {
            throw invalidateError;
        }
        
      
        const saltRounds = 10;
        const codeHash = await bcrypt.hash(code, saltRounds);
        
        
        const expiresMinutes = parseInt(process.env.OTP_EXPIRES_MINUTES) || 5;
        const expiresAt = new Date(Date.now() + expiresMinutes * 60 * 1000);
        
       
        const insertQuery = config.supabase
            .from('otp_codes')
            .insert({
                phone: phone || null,
                email: email || null,
                code_hash: codeHash,
                purpose: purpose,
                expires_at: expiresAt.toISOString(),
                used: false,
                attempts: 0
            })
            .select('otp_id')
            .single();
        
        
        const { data, error: insertError } = await insertQuery;
        
        if (insertError) {
            throw insertError;
        }
        
        return { 
            success: true, 
            data: { otp_id: data.otp_id } 
        };
        
    } catch (error) {
        console.error('Eroare saveOTP:', error);
        return { 
            success: false, 
            error: error.message 
        };
    }
}

async function checkCode({ phone, email, code, purpose, markAsUsed = true }) {
    try {
        let findQuery = config.supabase
            .from('otp_codes')
            .select('*')
            .eq('purpose', purpose)
            .eq('used', false);

        if (phone) {
            findQuery = findQuery.eq('phone', phone);
        } else if (email) {
            findQuery = findQuery.eq('email', email);
        }

        const { data: otp, error: findError } = await findQuery
            .order('created_at', { ascending: false })
            .limit(1)
            .maybeSingle();

        // Nu există OTP
        if (findError || !otp) {
            return {
                success: false,
                error: {
                    code: 'OTP_NOT_FOUND',
                    message: 'Codul nu a fost găsit. Solicită un cod nou'
                }
            };
        }

        // Verifică expirare ÎNAINTE de attempts
        const now = new Date();
        const expiresAt = new Date(otp.expires_at + 'Z');

        if (now > expiresAt) {
            return {
                success: false,
                error: {
                    code: 'OTP_EXPIRED',
                    message: 'Codul a expirat. Solicită unul nou.'
                }
            };
        }

        // Max attempts?
        const maxAttempts = parseInt(process.env.OTP_MAX_ATTEMPTS) || 3;
        if (otp.attempts >= maxAttempts) {
            return {
                success: false,
                error: {
                    code: 'OTP_MAX_ATTEMPTS',
                    message: 'Prea multe încercări. Solicită un cod nou.'
                }
            };
        }

        // Verifică codul
        const isValid = await bcrypt.compare(code, otp.code_hash);

        if (!isValid) {
            // Incrementează attempts
            const newAttempts = otp.attempts + 1;

            await config.supabase
                .from('otp_codes')
                .update({ attempts: newAttempts })
                .eq('otp_id', otp.otp_id);

            const attemptsLeft = maxAttempts - newAttempts;

            // Dacă nu mai are încercări
            if (attemptsLeft <= 0) {
                return {
                    success: false,
                    error: {
                        code: 'OTP_MAX_ATTEMPTS',
                        message: 'Prea multe încercări. Solicită un cod nou.'
                    }
                };
            }

            return {
                success: false,
                error: {
                    code: 'OTP_INVALID',
                    message: `Cod incorect. Mai ai ${attemptsLeft} ${attemptsLeft === 1 ? 'încercare' : 'încercări'}.`,
                    attempts_left: attemptsLeft
                }
            };
        }

        // Cod corect - marchează ca folosit doar dacă e cerut
        if (markAsUsed) {
            const { error: usedError } = await config.supabase
                .from('otp_codes')
                .update({ used: true })
                .eq('otp_id', otp.otp_id);

            if (usedError) {
                throw usedError;
            }
        }

        return { success: true };

    } catch (error) {
        console.error('Eroare checkCode:', error);
        return {
            success: false,
            error: {
                code: 'INTERNAL_SERVER_ERROR',
                message: 'Eroare la verificarea codului'
            }
        };
    }
}


export default {
    generateCode, 
    saveCode,
    checkCode
}





