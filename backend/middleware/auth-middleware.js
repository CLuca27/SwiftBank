import jwt from 'jsonwebtoken';

function authenticateToken(req, res, next) {

    const authHeader = req.headers.authorization || req.headers.Authorization;
    
    if (!authHeader?.startsWith('Bearer ')) {
        return res.status(401).json({
            success: false,
            error: 'Token lipsă'
        });
    }
    
    const token = authHeader.split(' ')[1];  

    jwt.verify(
        token,
        process.env.JWT_ACCESS_SECRET,
        (err, decoded) => {
            if (err) {
                console.log(err)
                return res.status(401).json({
                    success: false,
                    error: 'Token invalid sau expirat'
                });
            }
            
            req.user = {
                user_id: decoded.user_id,
                email: decoded.email
            }; 

            next();
        }
    );
} 



export default {
    authenticateToken
}