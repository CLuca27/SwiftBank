import axios from 'axios';

const DEFAULT_AI_SERVICE_URL = 'http://127.0.0.1:8000';
const DEFAULT_TIMEOUT_MS = 30000;

function getBaseUrl() {
     return DEFAULT_AI_SERVICE_URL;
}

function getTimeout() {
    return DEFAULT_TIMEOUT_MS;
}

async function sendChatMessage({ message, history = [], financialSummary = null }) {
    const url = `${getBaseUrl()}/chat`;

    try {
        const response = await axios.post(url, {
            message,
            history,
            financial_summary: financialSummary
        }, {
            timeout: getTimeout()
        });

        return response.data?.answer || 'Swift AI nu a returnat un raspuns.';
    } catch (error) {
        const status = error.response?.status;
        const detail = error.response?.data?.detail || error.message;
        console.error('Swift AI service error:', { status, detail });

        const serviceError = new Error('SWIFT_AI_UNAVAILABLE');
        serviceError.cause = detail;
        throw serviceError;
    }
}

export default {
    sendChatMessage
};