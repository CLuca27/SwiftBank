import config from '../config/index.js';
import logoService from './logo-service.js';

const DEFAULT_CATEGORY = {
    category_id: null,
    name: 'other'
};

let categoriesCache = null;
let categoriesCacheTime = 0;
const CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes

async function getCategories() {
    const now = Date.now();
    if (categoriesCache && (now - categoriesCacheTime) < CACHE_TTL_MS) {
        return categoriesCache;
    }

    const { data, error } = await config.supabase
        .from('categories')
        .select('*')
        .order('name');

    if (error) {
        console.error('Error fetching categories:', error);
        return [];
    }

    categoriesCache = data || [];
    categoriesCacheTime = now;
    return categoriesCache;
}

async function getCategoryById(categoryId) {
    if (!categoryId) return DEFAULT_CATEGORY;

    const categories = await getCategories();
    return categories.find(c => c.category_id === categoryId) || DEFAULT_CATEGORY;
}

function normalizeText(value) {
    return String(value || '')
        .trim()
        .toLowerCase()
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '');
}

function isMissingDomainColumn(error) {
    const message = String(error?.message || '');
    return error?.code === '42703' || message.includes('domain') || message.includes('merchants.domain');
}

async function fetchActiveMerchants(includeDomain = true) {
    const selectColumns = includeDomain
        ? `
            merchant_id,
            name,
            domain,
            category_id,
            categories (
                category_id,
                name
            )
        `
        : `
            merchant_id,
            name,
            category_id,
            categories (
                category_id,
                name
            )
        `;

    const { data, error } = await config.supabase
        .from('merchants')
        .select(selectColumns)
        .eq('status', 'ACTIVE');

    if (error && includeDomain && isMissingDomainColumn(error)) {
        console.warn('merchants.domain column is missing; merchant logos will use category fallback.');
        return fetchActiveMerchants(false);
    }

    if (error) {
        console.error('Error fetching merchants:', error);
        return [];
    }

    return data || [];
}

async function findMerchant(merchantName) {
    if (!merchantName) return null;

    const normalizedName = normalizeText(merchantName);
    const merchants = await fetchActiveMerchants();

    for (const merchant of merchants) {
        if (!merchant.name) continue;

        const storedName = normalizeText(merchant.name);
        if (normalizedName.includes(storedName) || storedName.includes(normalizedName)) {
            return {
                merchant_id: merchant.merchant_id,
                name: merchant.name,
                domain: merchant.domain || null,
                category: merchant.categories || DEFAULT_CATEGORY
            };
        }
    }

    return null;
}

function getCategoryIcon(categoryName) {
    return `ic_category_${categoryName || 'other'}`;
}

async function getMerchantInfo(merchantName) {
    const merchant = await findMerchant(merchantName);

    if (merchant) {
        const category = merchant.category || DEFAULT_CATEGORY;
        const categoryName = category.name || DEFAULT_CATEGORY.name;
        return {
            merchant_id: merchant.merchant_id,
            merchant_name: merchant.name,
            merchant_logo_url: logoService.buildLogoUrl(merchant.domain),
            category_id: category.category_id || null,
            category_name: categoryName,
            category_icon: getCategoryIcon(categoryName)
        };
    }

    return {
        merchant_id: null,
        merchant_name: merchantName,
        merchant_logo_url: null,
        category_id: null,
        category_name: DEFAULT_CATEGORY.name,
        category_icon: getCategoryIcon(DEFAULT_CATEGORY.name)
    };
}

export default {
    getCategories,
    getCategoryById,
    findMerchant,
    getMerchantInfo,
    DEFAULT_CATEGORY
};
