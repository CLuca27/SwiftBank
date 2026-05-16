import bcrypt from 'bcrypt';
import jwt from 'jsonwebtoken';
import crypto from 'crypto';

function hashCrypto(data) {
    return crypto.createHash('sha256').update(data).digest('hex');
} 
function cryptoCompare(str1, str2) 
{
    return hashCrypto(str1) === str2;
} 

function extractBirthDateFromCNP(cnp) {
    const sexDigit = parseInt(cnp[0]);
    const year = cnp.substring(1, 3);
    const month = cnp.substring(3, 5);
    const day = cnp.substring(5, 7);
    
    let fullYear;
    if (sexDigit === 1 || sexDigit === 2) {
        fullYear = '19' + year;
    } else if (sexDigit === 5 || sexDigit === 6) {
        fullYear = '20' + year;
    } 
    return `${fullYear}-${month}-${day}`;
} 

async function hashPassword(password) {
    const saltRounds = 12;
    return await bcrypt.hash(password, saltRounds);
}

async function verifyPassword(password, hash) {
    return await bcrypt.compare(password, hash);
}

function generateAccessToken(user, deviceId = null) {
    const payload = {
        user_id: user.user_id,
        email: user.email, 
        first_name: user.first_name,
        last_name: user.last_name,
        status: user.status,
        type: 'access',
        device_id: deviceId
    };
    
    return jwt.sign(payload, process.env.JWT_ACCESS_SECRET, {
        expiresIn: process.env.JWT_EXPIRES_IN || '15m'
    });
}

function generateRefreshToken(user, deviceId = null) {
    const payload = {
        user_id: user.user_id, 
        user_email: user.email,
        user_first_name: user.first_name,
        user_last_name: user.last_name,
        user_status: user.status,
        type: 'refresh',
        device_id: deviceId
    };
    
    return jwt.sign(payload, process.env.JWT_REFRESH_SECRET, {
        expiresIn: process.env.JWT_REFRESH_EXPIRES_IN || '7d'
    }); 

}  

function getLockDuration(lockCount) {
    const durations = [2, 5, 15, 30];
    if (lockCount >= durations.length) {
        return null;
    }
    return durations[lockCount];
}

export default {
    hashCrypto,  
    cryptoCompare,
    extractBirthDateFromCNP,
    hashPassword,
    verifyPassword,
    generateAccessToken,
    generateRefreshToken,
    getLockDuration
}

