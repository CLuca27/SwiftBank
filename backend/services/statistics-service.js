import supabase from '../config/supabase.js';
import merchantService from './merchant-service.js';
import logoService from './logo-service.js';
import ratesService from './rates-service.js';

const BASE_CURRENCY = 'RON';
const ACTIVE_BILLER_STATUS = 'ACTIVE';
const TOP_MERCHANT_LIMIT = 10;

const CATEGORY_LABELS = {
    food: 'M\u00e2ncare',
    shopping: 'Cump\u0103r\u0103turi',
    transport: 'Transport',
    entertainment: 'Divertisment',
    groceries: 'Alimente',
    health: 'S\u0103n\u0103tate',
    utilities: 'Utilit\u0103\u021bi',
    travel: 'C\u0103l\u0103torii',
    services: 'Servicii',
    telecom: 'Telecomunica\u021bii',
    internet: 'Internet',
    tv: 'TV',
    subscriptions: 'Abonamente',
    energy: 'Energie',
    electricity: 'Energie',
    gas: 'Gaze',
    electronics: 'Electronice',
    furniture: 'Mobilier',
    transfers: 'Transferuri',
    exchange: 'Schimb valutar',
    other: 'Altele'
};

const CATEGORY_EXPENSE_PHRASES = {
    food: 'Cheltuielile cu m\u00e2ncarea',
    shopping: 'Cheltuielile pentru cump\u0103r\u0103turi',
    transport: 'Cheltuielile cu transportul',
    entertainment: 'Cheltuielile pentru divertisment',
    groceries: 'Cheltuielile cu alimentele',
    health: 'Cheltuielile pentru s\u0103n\u0103tate',
    utilities: 'Cheltuielile cu utilit\u0103\u021bile',
    travel: 'Cheltuielile pentru c\u0103l\u0103torii',
    services: 'Cheltuielile pentru servicii',
    telecom: 'Cheltuielile cu telecomunica\u021biile',
    internet: 'Cheltuielile cu serviciile de internet',
    tv: 'Cheltuielile cu serviciile de televiziune',
    subscriptions: 'Cheltuielile cu abonamentele',
    energy: 'Cheltuielile cu energia',
    electricity: 'Cheltuielile cu energia',
    gas: 'Cheltuielile cu gazele',
    electronics: 'Cheltuielile pentru electronice',
    furniture: 'Cheltuielile pentru mobilier',
    transfers: 'Cheltuielile cu transferurile',
    exchange: 'Schimburile valutare',
    other: 'Alte cheltuieli'
};

const CATEGORY_GUIDANCE = {
    food: 'Urm\u0103re\u0219te mesele sau comenzile dese; aici apar rapid cheltuieli mici care se adun\u0103.',
    shopping: 'Verific\u0103 achizi\u021biile neesen\u021biale \u0219i cump\u0103r\u0103turile repetate din aceea\u0219i perioad\u0103.',
    transport: 'Uit\u0103-te la cursele sau deplas\u0103rile recurente \u0219i vezi dac\u0103 po\u021bi grupa drumurile.',
    entertainment: 'Verific\u0103 pl\u0103\u021bile pentru divertisment \u0219i p\u0103streaz\u0103 doar serviciile pe care le folose\u0219ti des.',
    groceries: 'Compar\u0103 cump\u0103r\u0103turile alimentare recurente \u0219i vezi unde apar diferen\u021be mari de la o lun\u0103 la alta.',
    health: 'Cheltuielile pentru s\u0103n\u0103tate sunt importante; urm\u0103re\u0219te doar dac\u0103 apar pl\u0103\u021bi neobi\u0219nuit de dese.',
    utilities: 'Compar\u0103 facturile recurente \u0219i vezi dac\u0103 exist\u0103 cre\u0219teri clare fa\u021b\u0103 de perioadele trecute.',
    travel: 'Grupeaz\u0103 costurile de c\u0103l\u0103torie ca s\u0103 vezi mai u\u0219or transportul, cazarea \u0219i pl\u0103\u021bile conexe.',
    services: 'Urm\u0103re\u0219te serviciile recurente \u0219i verific\u0103 dac\u0103 toate sunt \u00eenc\u0103 necesare.',
    telecom: 'Verific\u0103 abonamentele de telefonie \u0219i op\u021biunile suplimentare care se pot repeta lunar.',
    internet: 'Compar\u0103 costul abonamentului de internet cu perioadele trecute sau cu alte oferte disponibile.',
    tv: 'Verific\u0103 pachetele TV \u0219i extraop\u021biunile active, mai ales dac\u0103 apar lunar.',
    subscriptions: 'Revizuie\u0219te abonamentele active \u0219i opre\u0219te serviciile pe care nu le mai folose\u0219ti.',
    energy: 'Urm\u0103re\u0219te consumul \u0219i costurile de energie, mai ales dac\u0103 apar cre\u0219teri bru\u0219te.',
    electricity: 'Urm\u0103re\u0219te consumul \u0219i costurile de energie electric\u0103 fa\u021b\u0103 de lunile anterioare.',
    gas: 'Urm\u0103re\u0219te costurile cu gazele \u0219i compar\u0103 lunile cu consum ridicat.',
    electronics: 'Trateaz\u0103 electronicele ca pl\u0103\u021bi ocazionale \u0219i verific\u0103 dac\u0103 apar prea des \u00een perioada selectat\u0103.',
    furniture: 'Cheltuielile pentru mobilier sunt de obicei rare; urm\u0103re\u0219te dac\u0103 impactul lor domin\u0103 perioada.',
    transfers: 'Verific\u0103 transferurile recurente sau c\u0103tre acelea\u0219i persoane \u0219i vezi dac\u0103 toate sunt necesare.',
    exchange: 'Urm\u0103re\u0219te schimburile valutare \u0219i momentele \u00een care le faci, mai ales c\u00e2nd sumele sunt mari.',
    other: 'Verific\u0103 tranzac\u021biile ne\u00eencadrate \u0219i vezi dac\u0103 pot fi asociate unei categorii mai clare.'
};

function normalizeCategoryKey(name) {
    return String(name || 'other')
        .trim()
        .toLowerCase()
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .replace(/\s+/g, '_');
}

function translateCategoryName(name) {
    const key = normalizeCategoryKey(name);
    return CATEGORY_LABELS[key] || String(name || 'Altele').replace(/_/g, ' ');
}

function getCategoryExpensePhrase(name) {
    const key = normalizeCategoryKey(name);
    return CATEGORY_EXPENSE_PHRASES[key] || `Cheltuielile din categoria ${translateCategoryName(name).toLowerCase()}`;
}

function getCategoryGuidance(name) {
    const key = normalizeCategoryKey(name);
    return CATEGORY_GUIDANCE[key] || CATEGORY_GUIDANCE.other;
}

function lowerFirst(value) {
    if (!value) return value;
    return value.charAt(0).toLowerCase() + value.slice(1);
}

function getCategoryIconName(categoryName) {
    const category = normalizeCategoryKey(categoryName);

    if (['utilities', 'utilitati', 'energy', 'electricity', 'electricitate', 'gas', 'gaze'].includes(category)) {
        return 'ic_category_utilities';
    }

    if (['internet', 'telecom', 'telecomunicatii'].includes(category)) {
        return 'ic_category_services';
    }

    if (category === 'tv' || category === 'subscriptions' || category === 'abonamente') {
        return 'ic_category_entertainment';
    }

    return `ic_category_${category || 'other'}`;
}

function parseAmount(value) {
    const amount = parseFloat(value || 0);
    return Number.isFinite(amount) ? amount : 0;
}

function roundMoney(value) {
    return Math.round(parseAmount(value) * 100) / 100;
}

function normalizeCurrency(currency) {
    return String(currency || BASE_CURRENCY).trim().toUpperCase();
}

function parseDate(value) {
    const date = value ? new Date(value) : null;
    return date && !Number.isNaN(date.getTime()) ? date : new Date(0);
}

function padDatePart(value) {
    return String(value).padStart(2, '0');
}

function toIsoDate(date) {
    return `${date.getFullYear()}-${padDatePart(date.getMonth() + 1)}-${padDatePart(date.getDate())}`;
}

function toMonthKey(date) {
    return `${date.getFullYear()}-${padDatePart(date.getMonth() + 1)}`;
}

function endOfToday(now = new Date()) {
    return new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59, 59, 999);
}

function isInternalTransfer(transaction) {
    return ['SELF_IN', 'SELF_OUT'].includes(transaction?.transaction_type)
        || transaction?.transfer_type === 'SELF';
}

function getExpenseCategory(transaction) {
    if (!transaction) return null;

    if (['CARD', 'BILL'].includes(transaction.transaction_type)) {
        return {
            name: transaction.category_name || transaction.biller_category || 'other',
            icon: transaction.category_icon || getCategoryIconName(transaction.category_name || transaction.biller_category || 'other')
        };
    }

    if (transaction.transaction_type === 'TRANSFER_OUT') {
        return {
            name: 'transfers',
            icon: 'ic_category_services'
        };
    }

    return null;
}

function getMerchantName(transaction) {
    if (!transaction || !['CARD', 'BILL'].includes(transaction.transaction_type)) {
        return null;
    }

    return transaction.merchant_name || transaction.biller_name || transaction.title || null;
}

async function getBillerMerchantInfo(billerName) {
    const normalizedName = String(billerName || '').trim();
    if (!normalizedName) {
        return {};
    }

    const { data, error } = await supabase
        .from('billers')
        .select(`
            name,
            domain,
            categories (
                name
            )
        `)
        .eq('status', ACTIVE_BILLER_STATUS)
        .ilike('name', normalizedName)
        .limit(1)
        .maybeSingle();

    if (error) {
        console.error('Error fetching biller logo for statistics:', error);
        return {};
    }

    if (!data) {
        return {};
    }

    const categoryName = data.categories?.name || null;

    return {
        merchant_logo_url: logoService.buildLogoUrl(data.domain),
        category_name: categoryName,
        category_icon: categoryName ? getCategoryIconName(categoryName) : null
    };
}

function getDateRange(period) {
    const now = new Date();
    let startDate;
    let endDate;

    switch (period) {
        case 'this_month':
            startDate = new Date(now.getFullYear(), now.getMonth(), 1);
            endDate = endOfToday(now);
            break;
        case 'last_month':
            startDate = new Date(now.getFullYear(), now.getMonth() - 1, 1);
            endDate = new Date(now.getFullYear(), now.getMonth(), 0, 23, 59, 59);
            break;
        case 'last_3_months':
            startDate = new Date(now.getFullYear(), now.getMonth() - 2, 1);
            endDate = endOfToday(now);
            break;
        case 'this_year':
            startDate = new Date(now.getFullYear(), 0, 1);
            endDate = endOfToday(now);
            break;
        case 'last_6_months':
        default:
            startDate = new Date(now.getFullYear(), now.getMonth() - 5, 1);
            endDate = endOfToday(now);
            break;
    }

    return {
        startDate,
        endDate,
        startIso: startDate.toISOString(),
        endIso: endDate.toISOString()
    };
}

function buildRange(startDate, endDate) {
    return {
        startDate,
        endDate,
        startIso: startDate.toISOString(),
        endIso: endDate.toISOString()
    };
}

function getPreviousDateRange(period, range) {
    const previousMonthRange = (monthCount) => {
        const previousStart = new Date(
            range.startDate.getFullYear(),
            range.startDate.getMonth() - monthCount,
            1
        );
        const previousEnd = new Date(
            range.startDate.getFullYear(),
            range.startDate.getMonth(),
            0,
            23,
            59,
            59
        );

        return buildRange(previousStart, previousEnd);
    };

    switch (period) {
        case 'this_month':
        case 'last_month':
            return previousMonthRange(1);
        case 'last_3_months':
            return previousMonthRange(3);
        case 'last_6_months':
            return previousMonthRange(6);
        case 'this_year': {
            const previousYear = range.startDate.getFullYear() - 1;
            return buildRange(
                new Date(previousYear, 0, 1),
                new Date(previousYear, 11, 31, 23, 59, 59)
            );
        }
        default: {
            const durationMs = range.endDate.getTime() - range.startDate.getTime();
            const previousEnd = new Date(range.startDate.getTime() - 1);
            const previousStart = new Date(previousEnd.getTime() - durationMs);
            return buildRange(previousStart, previousEnd);
        }
    }
}

function normalizeGranularity(granularity, period) {
    if (['day', 'week', 'month'].includes(granularity)) {
        return granularity;
    }

    if (period === 'this_year' || period === 'last_6_months') {
        return 'month';
    }

    if (period === 'last_3_months') {
        return 'week';
    }

    return 'day';
}

function formatBucketLabel(date, granularity) {
    const formatter = new Intl.DateTimeFormat('ro-RO', {
        day: granularity === 'month' ? undefined : '2-digit',
        month: 'short'
    });

    if (granularity === 'month') {
        return formatter.format(date).replace('.', '');
    }

    return formatter.format(date).replace('.', '');
}

function buildTrendBuckets(range, granularity) {
    const buckets = [];
    const cursor = new Date(range.startDate);
    cursor.setHours(0, 0, 0, 0);

    while (cursor <= range.endDate) {
        const start = new Date(cursor);
        let end;
        let key;

        if (granularity === 'month') {
            key = toMonthKey(start);
            end = new Date(start.getFullYear(), start.getMonth() + 1, 0, 23, 59, 59);
            cursor.setMonth(cursor.getMonth() + 1, 1);
        } else if (granularity === 'week') {
            key = toIsoDate(start);
            end = new Date(start);
            end.setDate(end.getDate() + 6);
            end.setHours(23, 59, 59, 999);
            cursor.setDate(cursor.getDate() + 7);
        } else {
            key = toIsoDate(start);
            end = new Date(start);
            end.setHours(23, 59, 59, 999);
            cursor.setDate(cursor.getDate() + 1);
        }

        if (end > range.endDate) {
            end = new Date(range.endDate);
        }

        buckets.push({
            key,
            month: key,
            label: formatBucketLabel(start, granularity),
            start,
            end,
            income: 0,
            expenses: 0
        });
    }

    return buckets;
}

function findBucket(buckets, createdAt, granularity) {
    const date = parseDate(createdAt);

    if (granularity === 'month') {
        const key = toMonthKey(date);
        return buckets.find(bucket => bucket.key === key);
    }

    if (granularity === 'day') {
        const key = toIsoDate(date);
        return buckets.find(bucket => bucket.key === key);
    }

    return buckets.find(bucket => date >= bucket.start && date <= bucket.end);
}

async function buildRateMap() {
    const rates = await ratesService.getCurrentRates();
    const rateMap = { [BASE_CURRENCY]: 1 };

    for (const rate of rates || []) {
        rateMap[normalizeCurrency(rate.from_currency)] = parseAmount(rate.rate);
    }

    return rateMap;
}

function convertToBaseCurrency(amount, currency, rateMap) {
    const normalizedCurrency = normalizeCurrency(currency);
    const rate = rateMap[normalizedCurrency] || 1;

    return parseAmount(amount) * rate;
}

function getTransactionLabel(transaction) {
    return transaction?.merchant_name
        || transaction?.biller_name
        || transaction?.title
        || transaction?.beneficiary_name
        || transaction?.subtitle
        || 'Tranzac\u021bie';
}

function addBreakdownValue(target, key, seed, amount, transaction) {
    if (!target[key]) {
        target[key] = {
            ...seed,
            amount: 0,
            count: 0,
            lastDate: null,
            examples: []
        };
    }

    target[key].amount += Math.abs(amount);
    target[key].count += 1;

    const createdAt = transaction?.created_at || null;
    if (createdAt && (!target[key].lastDate || createdAt > target[key].lastDate)) {
        target[key].lastDate = createdAt;
    }

    if (target[key].examples.length < 3) {
        target[key].examples.push({
            title: getTransactionLabel(transaction),
            amount: roundMoney(Math.abs(amount)),
            currency: BASE_CURRENCY,
            createdAt
        });
    }
}

async function fetchTransactions(userId, range) {
    const { data, error } = await supabase
        .from('user_transactions')
        .select('*')
        .eq('user_id', userId)
        .gte('created_at', range.startIso)
        .lte('created_at', range.endIso)
        .order('created_at', { ascending: false });

    if (error) {
        throw error;
    }

    return data || [];
}

async function calculateStatisticsForRange(userId, range, granularity, rateMap) {
    const transactions = await fetchTransactions(userId, range);

    let totalIncome = 0;
    let totalExpenses = 0;
    let transactionCount = 0;
    const categoryBreakdown = {};
    const merchantBreakdown = {};
    const currencyBreakdown = {};
    const buckets = buildTrendBuckets(range, granularity);

    for (const transaction of transactions) {
        if (isInternalTransfer(transaction)) {
            continue;
        }

        transactionCount += 1;

        const originalAmount = parseAmount(transaction.amount);
        const originalCurrency = normalizeCurrency(transaction.currency);
        const convertedAmount = convertToBaseCurrency(
            originalAmount,
            originalCurrency,
            rateMap
        );

        if (!currencyBreakdown[originalCurrency]) {
            currencyBreakdown[originalCurrency] = {
                currency: originalCurrency,
                income: 0,
                expenses: 0,
                convertedIncome: 0,
                convertedExpenses: 0,
                count: 0
            };
        }
        currencyBreakdown[originalCurrency].count += 1;

        const bucket = findBucket(buckets, transaction.created_at, granularity);

        if (convertedAmount > 0) {
            totalIncome += convertedAmount;
            currencyBreakdown[originalCurrency].income += Math.abs(originalAmount);
            currencyBreakdown[originalCurrency].convertedIncome += convertedAmount;
            if (bucket) bucket.income += convertedAmount;
            continue;
        }

        totalExpenses += Math.abs(convertedAmount);
        currencyBreakdown[originalCurrency].expenses += Math.abs(originalAmount);
        currencyBreakdown[originalCurrency].convertedExpenses += Math.abs(convertedAmount);
        if (bucket) bucket.expenses += Math.abs(convertedAmount);

        const expenseCategory = getExpenseCategory(transaction);
        if (expenseCategory) {
            addBreakdownValue(
                categoryBreakdown,
                expenseCategory.name,
                {
                    name: expenseCategory.name,
                    icon: expenseCategory.icon
                },
                convertedAmount,
                transaction
            );
        }

        const merchantName = getMerchantName(transaction);
        if (merchantName) {
            addBreakdownValue(
                merchantBreakdown,
                merchantName,
                {
                    name: merchantName,
                    merchant_logo_url: transaction.merchant_logo_url || transaction.biller_logo_url || null,
                    category_name: transaction.category_name || transaction.biller_category || null,
                    category_icon: transaction.category_icon || getCategoryIconName(transaction.category_name || transaction.biller_category)
                },
                convertedAmount,
                transaction
            );

            const transactionLogoUrl = transaction.merchant_logo_url || transaction.biller_logo_url || null;
            const transactionCategoryName = transaction.category_name || transaction.biller_category || null;
            const transactionCategoryIcon = transaction.category_icon || getCategoryIconName(transactionCategoryName);

            if (!merchantBreakdown[merchantName].merchant_logo_url && transactionLogoUrl) {
                merchantBreakdown[merchantName].merchant_logo_url = transactionLogoUrl;
            }
            if (!merchantBreakdown[merchantName].category_name && transactionCategoryName) {
                merchantBreakdown[merchantName].category_name = transactionCategoryName;
            }
            if (!merchantBreakdown[merchantName].category_icon && transactionCategoryIcon) {
                merchantBreakdown[merchantName].category_icon = transactionCategoryIcon;
            }
        }
    }

    const categories = Object.values(categoryBreakdown)
        .sort((a, b) => b.amount - a.amount);

    const totalCategoryAmount = categories.reduce((sum, category) => sum + category.amount, 0);
    for (const category of categories) {
        category.percentage = totalCategoryAmount > 0
            ? Math.round((category.amount / totalCategoryAmount) * 1000) / 10
            : 0;
        category.amount = roundMoney(category.amount);
    }

    const topMerchantRows = Object.values(merchantBreakdown)
        .sort((a, b) => b.amount - a.amount)
        .slice(0, TOP_MERCHANT_LIMIT);

    const topMerchants = (await Promise.all(topMerchantRows.map(async merchant => {
        if (merchant.merchant_logo_url && merchant.category_name && merchant.category_icon) {
            return merchant;
        }

        const [merchantInfo, billerInfo] = await Promise.all([
            merchantService.getMerchantInfo(merchant.name),
            getBillerMerchantInfo(merchant.name)
        ]);

        return {
            ...merchant,
            merchant_logo_url: merchant.merchant_logo_url || billerInfo.merchant_logo_url || merchantInfo.merchant_logo_url,
            category_name: merchant.category_name || billerInfo.category_name || merchantInfo.category_name,
            category_icon: merchant.category_icon || billerInfo.category_icon || merchantInfo.category_icon
        };
    }))).map(merchant => ({
        ...merchant,
        amount: roundMoney(merchant.amount)
    }));

    const monthlyTrend = buckets.map(bucket => ({
        month: bucket.month,
        label: bucket.label,
        income: roundMoney(bucket.income),
        expenses: roundMoney(bucket.expenses)
    }));

    const currencies = Object.values(currencyBreakdown)
        .map(currency => ({
            ...currency,
            income: roundMoney(currency.income),
            expenses: roundMoney(currency.expenses),
            convertedIncome: roundMoney(currency.convertedIncome),
            convertedExpenses: roundMoney(currency.convertedExpenses)
        }))
        .sort((a, b) => b.convertedExpenses - a.convertedExpenses);

    return {
        summary: {
            totalIncome: roundMoney(totalIncome),
            totalExpenses: roundMoney(totalExpenses),
            balance: roundMoney(totalIncome - totalExpenses),
            transactionCount
        },
        categories,
        topMerchants,
        currencyBreakdown: currencies,
        monthlyTrend
    };
}

function calculatePercentChange(current, previous) {
    const currentValue = Math.abs(parseAmount(current));
    const previousValue = Math.abs(parseAmount(previous));

    if (!currentValue || !previousValue) {
        return null;
    }

    return Math.round(((currentValue - previousValue) / previousValue) * 1000) / 10;
}

function buildComparison(current, previous) {
    const currentTopCategory = current.categories[0] || null;
    const previousTopCategory = currentTopCategory
        ? previous.categories.find(category => category.name === currentTopCategory.name)
        : null;
    const hasPreviousPeriod = previous.summary.transactionCount > 0;
    const hasComparableTopCategory = Boolean(
        hasPreviousPeriod
        && currentTopCategory
        && previousTopCategory
        && currentTopCategory.amount > 0
        && previousTopCategory.amount > 0
    );

    return {
        hasPreviousPeriod,
        previousTotalIncome: previous.summary.totalIncome,
        previousTotalExpenses: previous.summary.totalExpenses,
        previousBalance: previous.summary.balance,
        incomeChange: hasPreviousPeriod ? roundMoney(current.summary.totalIncome - previous.summary.totalIncome) : 0,
        incomeChangePercent: hasPreviousPeriod ? calculatePercentChange(current.summary.totalIncome, previous.summary.totalIncome) : null,
        expensesChange: hasPreviousPeriod ? roundMoney(current.summary.totalExpenses - previous.summary.totalExpenses) : 0,
        expensesChangePercent: hasPreviousPeriod ? calculatePercentChange(current.summary.totalExpenses, previous.summary.totalExpenses) : null,
        balanceChange: hasPreviousPeriod ? roundMoney(current.summary.balance - previous.summary.balance) : 0,
        transactionCountChange: hasPreviousPeriod ? current.summary.transactionCount - previous.summary.transactionCount : 0,
        topCategoryName: currentTopCategory?.name || null,
        topCategoryAmount: currentTopCategory?.amount || 0,
        topCategoryPreviousAmount: previousTopCategory?.amount || 0,
        topCategoryChange: hasComparableTopCategory
            ? roundMoney(currentTopCategory.amount - previousTopCategory.amount)
            : 0,
        topCategoryChangePercent: hasComparableTopCategory
            ? calculatePercentChange(currentTopCategory.amount, previousTopCategory.amount)
            : null
    };
}

function formatPercent(value) {
    if (value === null || value === undefined) {
        return null;
    }

    const abs = Math.abs(value);
    return `${abs % 1 === 0 ? abs.toFixed(0) : abs.toFixed(1)}%`;
}

function buildInsights(current, comparison) {
    const insights = [];
    const { summary, categories } = current;

    if (!summary.transactionCount) {
        return insights;
    }

    const topCategory = categories[0] || null;
    if (topCategory) {
        insights.push({
            type: 'category',
            title: 'Categoria principal\u0103',
            message: `${getCategoryExpensePhrase(topCategory.name)} reprezint\u0103 ${topCategory.percentage}% din totalul perioadei selectate.`
        });

        const categoryPercentText = formatPercent(comparison?.topCategoryChangePercent);
        if (comparison?.hasPreviousPeriod && comparison.topCategoryChange !== 0 && categoryPercentText) {
            const wentUp = comparison.topCategoryChange > 0;
            insights.push({
                type: wentUp ? 'warning' : 'positive',
                title: 'Evolu\u021bie categorie',
                message: `Fa\u021b\u0103 de perioada anterioar\u0103, ${lowerFirst(getCategoryExpensePhrase(topCategory.name))} ${wentUp ? 'au crescut' : 'au sc\u0103zut'} cu ${categoryPercentText}.`
            });
        } else {
            insights.push({
                type: 'tip',
                title: 'Observa\u021bie',
                message: getCategoryGuidance(topCategory.name)
            });
        }
    }

    if (!topCategory && comparison?.hasPreviousPeriod && comparison.expensesChange !== 0) {
        const wentUp = comparison.expensesChange > 0;
        const percentText = formatPercent(comparison.expensesChangePercent);
        if (percentText) {
            insights.push({
                type: wentUp ? 'warning' : 'positive',
                title: wentUp ? 'Cheltuieli \u00een cre\u0219tere' : 'Cheltuieli mai mici',
                message: `Fa\u021b\u0103 de perioada anterioar\u0103, cheltuielile totale ${wentUp ? 'au crescut' : 'au sc\u0103zut'} cu ${percentText}.`
            });
        }
    }

    if (summary.balance >= 0) {
        insights.push({
            type: 'positive',
            title: 'Balan\u021b\u0103 pozitiv\u0103',
            message: 'Veniturile dep\u0103\u0219esc cheltuielile \u00een perioada selectat\u0103.'
        });
    } else {
        insights.push({
            type: 'warning',
            title: 'Balan\u021b\u0103 negativ\u0103',
            message: 'Cheltuielile dep\u0103\u0219esc veniturile \u00een perioada selectat\u0103.'
        });
    }

    return insights.slice(0, 3);
}

async function getStatistics(userId, period = 'this_month', granularity = null) {
    const normalizedGranularity = normalizeGranularity(granularity, period);
    const currentRange = getDateRange(period);
    const previousRange = getPreviousDateRange(period, currentRange);
    const rateMap = await buildRateMap();

    const [current, previous] = await Promise.all([
        calculateStatisticsForRange(userId, currentRange, normalizedGranularity, rateMap),
        calculateStatisticsForRange(userId, previousRange, normalizedGranularity, rateMap)
    ]);

    const comparison = buildComparison(current, previous);

    return {
        period,
        granularity: normalizedGranularity,
        baseCurrency: BASE_CURRENCY,
        startDate: currentRange.startIso,
        endDate: currentRange.endIso,
        summary: current.summary,
        comparison,
        insights: buildInsights(current, comparison),
        categories: current.categories,
        topMerchants: current.topMerchants,
        currencyBreakdown: current.currencyBreakdown,
        monthlyTrend: current.monthlyTrend
    };
}

async function getBalanceHistory(userId, period = 'last_6_months') {
    const rateMap = await buildRateMap();

    const { data: accounts, error: accountsError } = await supabase
        .from('accounts')
        .select('account_id, currency, balance')
        .eq('user_id', userId);

    if (accountsError) {
        throw accountsError;
    }

    const balanceHistory = [];
    const now = new Date();

    for (let i = 5; i >= 0; i--) {
        const date = new Date(now.getFullYear(), now.getMonth() - i, 1);
        const monthKey = toMonthKey(date);

        const totalBalance = accounts?.reduce((sum, account) => {
            return sum + convertToBaseCurrency(account.balance, account.currency, rateMap);
        }, 0) || 0;

        balanceHistory.push({
            month: monthKey,
            balance: roundMoney(totalBalance)
        });
    }

    return balanceHistory;
}

export default {
    getStatistics,
    getBalanceHistory
};