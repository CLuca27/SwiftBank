const LOGO_DEV_BASE_URL = 'https://img.logo.dev';
const LOGO_DEV_DEFAULT_SIZE = '120';
const LOGO_DEV_DEFAULT_FORMAT = 'png';
const LOGO_DEV_DEFAULT_FALLBACK = '404';

function normalizeDomain(domain) {
    return String(domain || '')
        .trim()
        .toLowerCase()
        .replace(/^https?:\/\//, '')
        .replace(/^www\./, '')
        .replace(/\/.*$/, '');
}

function getLogoDevToken() {
    return process.env.LOGO_DEV_PUBLISHABLE_KEY || process.env.LOGO_DEV_TOKEN || '';
}

function buildLogoUrl(domain) {
    const normalizedDomain = normalizeDomain(domain);
    const token = getLogoDevToken();

    if (!normalizedDomain || !token) {
        return null;
    }

    const params = new URLSearchParams({
        token,
        size: process.env.LOGO_DEV_SIZE || LOGO_DEV_DEFAULT_SIZE,
        format: process.env.LOGO_DEV_FORMAT || LOGO_DEV_DEFAULT_FORMAT,
        fallback: process.env.LOGO_DEV_FALLBACK || LOGO_DEV_DEFAULT_FALLBACK
    });

    return `${process.env.LOGO_DEV_BASE_URL || LOGO_DEV_BASE_URL}/${encodeURIComponent(normalizedDomain)}?${params.toString()}`;
}

export default {
    buildLogoUrl,
    normalizeDomain
};
