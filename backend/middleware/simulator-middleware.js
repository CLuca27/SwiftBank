const SIMULATOR_API_KEY = process.env.SIMULATOR_API_KEY || 'sim_dev_key_2024';

function validateSimulatorKey(req, res, next) {
    const apiKey = req.headers['x-simulator-key'];

    if (!apiKey) {
        return res.status(401).json({
            success: false,
            message: 'Payment requests must come from the Card Payment Simulator.',
            error: { code: 'SIMULATOR_KEY_MISSING' }
        });
    }

    if (apiKey !== SIMULATOR_API_KEY) {
        return res.status(403).json({
            success: false,
            message: 'Invalid simulator key.',
            error: { code: 'SIMULATOR_KEY_INVALID' }
        });
    }

    next();
}

export default {
    validateSimulatorKey
};
