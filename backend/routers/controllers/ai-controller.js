import services from '../../services/index.js';

const MAX_MESSAGE_LENGTH = 1000;
const MAX_HISTORY_ITEMS = 12;
const MAX_HISTORY_CONTENT_LENGTH = 4000;
const ALLOWED_HISTORY_ROLES = new Set(['user', 'assistant']);

function normalizeText(value) {
    return String(value || '')
        .replace(/[\r\t]+/g, ' ')
        .replace(/\s+/g, ' ')
        .trim();
}

function normalizeHistory(history) {
    if (!Array.isArray(history)) return [];

    return history
        .slice(-MAX_HISTORY_ITEMS)
        .map(message => {
            const role = normalizeText(message?.role);
            const content = normalizeText(message?.content);

            if (!ALLOWED_HISTORY_ROLES.has(role) || !content) {
                return null;
            }

            return {
                role,
                content: content.substring(0, MAX_HISTORY_CONTENT_LENGTH)
            };
        })
        .filter(Boolean);
}

async function chat(req, res) {
    try {
        const userId = req.user.user_id;
        const message = normalizeText(req.body?.message);

        if (!message) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'MISSING_MESSAGE',
                    message: 'Mesajul este obligatoriu'
                }
            });
        }

        if (message.length > MAX_MESSAGE_LENGTH) {
            return res.status(400).json({
                success: false,
                error: {
                    code: 'MESSAGE_TOO_LONG',
                    message: 'Mesajul este prea lung'
                }
            });
        }

        const history = normalizeHistory(req.body?.history);
const financialSummary = await services.aiContextService.buildFinancialSummary(userId, message);
        const answer = await services.aiService.sendChatMessage({
            message,
            history,
            financialSummary
        });

        return res.status(200).json({
            success: true,
            data: {
                answer,
                contextConnected: true
            }
        });
    } catch (error) {
        console.error('Error in Swift AI chat:', error);

        if (error.message === 'SWIFT_AI_UNAVAILABLE') {
            return res.status(502).json({
                success: false,
                error: {
                    code: 'SWIFT_AI_UNAVAILABLE',
                    message: 'Swift AI nu este disponibil momentan'
                }
            });
        }

        return res.status(500).json({
            success: false,
            error: {
                code: 'SWIFT_AI_ERROR',
                message: 'Nu am putut genera r\u0103spunsul Swift AI'
            }
        });
    }
}

export default {
    chat
};