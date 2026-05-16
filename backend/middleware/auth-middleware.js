import jwt from 'jsonwebtoken';
import config from '../config/index.js';

function authError(res, code, message) {
    return res.status(401).json({
        success: false,
        error: {
            code,
            message
        }
    });
}

async function authenticateToken(req, res, next) {
    const authHeader = req.headers.authorization || req.headers.Authorization;

    if (!authHeader?.startsWith('Bearer ')) {
        return authError(res, 'MISSING_TOKEN', 'Token lipsa');
    }

    const token = authHeader.split(' ')[1];

    let decoded;
    try {
        decoded = jwt.verify(token, process.env.JWT_ACCESS_SECRET);
    } catch (err) {
        console.log(err);
        return authError(res, 'INVALID_ACCESS_TOKEN', 'Token invalid sau expirat');
    }

    if (decoded.type !== 'access' || !decoded.user_id || !decoded.device_id) {
        return authError(res, 'INVALID_ACCESS_TOKEN', 'Token invalid sau expirat');
    }

    const { data: activeSession, error } = await config.supabase
        .from('refresh_tokens')
        .select('token_id, device_id')
        .eq('user_id', decoded.user_id)
        .eq('device_id', decoded.device_id)
        .order('created_at', { ascending: false })
        .limit(1)
        .maybeSingle();

    if (error) {
        console.error('[Auth] Error validating active session:', error);
        return authError(res, 'SESSION_CHECK_FAILED', 'Nu am putut verifica sesiunea');
    }

    if (!activeSession) {
        return authError(res, 'SESSION_REVOKED', 'Sesiunea nu mai este activa pe acest dispozitiv');
    }

    req.user = {
        user_id: decoded.user_id,
        email: decoded.email,
        device_id: decoded.device_id
    };

    next();
}

export default {
    authenticateToken
};
