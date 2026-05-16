import supabase from '../config/supabase.js';
import merchantService from './merchant-service.js';

function parseAmount(value) {
    return parseFloat(value || 0);
}

function isInternalTransfer(transaction) {
    return ['SELF_IN', 'SELF_OUT'].includes(transaction?.transaction_type)
        || transaction?.transfer_type === 'SELF';
}

function getExpenseCategory(transaction) {
    if (!transaction) return null;

    if (['CARD', 'BILL'].includes(transaction.transaction_type)) {
        return {
            name: transaction.category_name || 'other',
            icon: transaction.category_icon || 'ic_category_other'
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

function getDateRange(period) {
    const now = new Date();
    let startDate, endDate;

    switch (period) {
        case 'this_month':
            startDate = new Date(now.getFullYear(), now.getMonth(), 1);
            endDate = new Date(now.getFullYear(), now.getMonth() + 1, 0, 23, 59, 59);
            break;
        case 'last_month':
            startDate = new Date(now.getFullYear(), now.getMonth() - 1, 1);
            endDate = new Date(now.getFullYear(), now.getMonth(), 0, 23, 59, 59);
            break;
        case 'last_3_months':
            startDate = new Date(now.getFullYear(), now.getMonth() - 2, 1);
            endDate = new Date(now.getFullYear(), now.getMonth() + 1, 0, 23, 59, 59);
            break;
        case 'this_year':
            startDate = new Date(now.getFullYear(), 0, 1);
            endDate = new Date(now.getFullYear(), 11, 31, 23, 59, 59);
            break;
        case 'last_6_months':
        default:
            startDate = new Date(now.getFullYear(), now.getMonth() - 5, 1);
            endDate = new Date(now.getFullYear(), now.getMonth() + 1, 0, 23, 59, 59);
            break;
    }

    return {
        startDate: startDate.toISOString(),
        endDate: endDate.toISOString()
    };
}

async function getStatistics(userId, period = 'this_month') {
    const { startDate, endDate } = getDateRange(period);

    // Get all transactions for the period
    const { data: transactions, error } = await supabase
        .from('user_transactions')
        .select('*')
        .eq('user_id', userId)
        .gte('created_at', startDate)
        .lte('created_at', endDate)
        .order('created_at', { ascending: false });

    if (error) {
        throw error;
    }

    // Calculate summary
    let totalIncome = 0;
    let totalExpenses = 0;
    let transactionCount = 0;
    const categoryBreakdown = {};
    const merchantBreakdown = {};
    const monthlyData = {};

    for (const t of transactions || []) {
        if (isInternalTransfer(t)) {
            continue;
        }

        transactionCount += 1;
        const amount = parseAmount(t.amount);

        if (amount > 0) {
            totalIncome += amount;
        } else {
            totalExpenses += Math.abs(amount);

            const expenseCategory = getExpenseCategory(t);
            if (expenseCategory) {
                const categoryName = expenseCategory.name;
                if (!categoryBreakdown[categoryName]) {
                    categoryBreakdown[categoryName] = {
                        name: categoryName,
                        icon: expenseCategory.icon,
                        amount: 0,
                        count: 0
                    };
                }
                categoryBreakdown[categoryName].amount += Math.abs(amount);
                categoryBreakdown[categoryName].count += 1;
            }

            // Merchant breakdown (only for card transactions)
            if (t.transaction_type === 'CARD' && t.title) {
                const merchantName = t.title;
                if (!merchantBreakdown[merchantName]) {
                    merchantBreakdown[merchantName] = {
                        name: merchantName,
                        merchant_logo_url: t.merchant_logo_url || null,
                        category_name: t.category_name || null,
                        category_icon: t.category_icon || null,
                        amount: 0,
                        count: 0
                    };
                }
                if (!merchantBreakdown[merchantName].merchant_logo_url && t.merchant_logo_url) {
                    merchantBreakdown[merchantName].merchant_logo_url = t.merchant_logo_url;
                }
                if (!merchantBreakdown[merchantName].category_name && t.category_name) {
                    merchantBreakdown[merchantName].category_name = t.category_name;
                }
                if (!merchantBreakdown[merchantName].category_icon && t.category_icon) {
                    merchantBreakdown[merchantName].category_icon = t.category_icon;
                }
                merchantBreakdown[merchantName].amount += Math.abs(amount);
                merchantBreakdown[merchantName].count += 1;
            }
        }

        // Monthly data
        const monthKey = t.created_at.substring(0, 7); // YYYY-MM
        if (!monthlyData[monthKey]) {
            monthlyData[monthKey] = {
                month: monthKey,
                income: 0,
                expenses: 0
            };
        }
        if (amount > 0) {
            monthlyData[monthKey].income += amount;
        } else {
            monthlyData[monthKey].expenses += Math.abs(amount);
        }
    }

    // Convert to arrays and sort
    const categories = Object.values(categoryBreakdown)
        .sort((a, b) => b.amount - a.amount);

    const topMerchantRows = Object.values(merchantBreakdown)
        .sort((a, b) => b.amount - a.amount)
        .slice(0, 5);

    const topMerchants = (await Promise.all(topMerchantRows.map(async merchant => {
        if (merchant.merchant_logo_url && merchant.category_name && merchant.category_icon) {
            return merchant;
        }

        const merchantInfo = await merchantService.getMerchantInfo(merchant.name);
        return {
            ...merchant,
            amount: Math.round(merchant.amount * 100) / 100,
            merchant_logo_url: merchant.merchant_logo_url || merchantInfo.merchant_logo_url,
            category_name: merchant.category_name || merchantInfo.category_name,
            category_icon: merchant.category_icon || merchantInfo.category_icon
        };
    }))).map(merchant => ({
        ...merchant,
        amount: Math.round(merchant.amount * 100) / 100
    }));

    const monthlyTrend = Object.values(monthlyData)
        .sort((a, b) => a.month.localeCompare(b.month));

    // Calculate percentages for categories
    const totalCategoryAmount = categories.reduce((sum, c) => sum + c.amount, 0);
    for (const category of categories) {
        category.percentage = totalCategoryAmount > 0
            ? Math.round((category.amount / totalCategoryAmount) * 1000) / 10
            : 0;
        category.amount = Math.round(category.amount * 100) / 100;
    }

    return {
        period,
        startDate,
        endDate,
        summary: {
            totalIncome: Math.round(totalIncome * 100) / 100,
            totalExpenses: Math.round(totalExpenses * 100) / 100,
            balance: Math.round((totalIncome - totalExpenses) * 100) / 100,
            transactionCount
        },
        categories,
        topMerchants,
        monthlyTrend
    };
}

async function getBalanceHistory(userId, period = 'last_6_months') {
    const { startDate } = getDateRange(period);

    // Get account balances over time by looking at transactions
    const { data: accounts, error: accountsError } = await supabase
        .from('accounts')
        .select('account_id, currency, balance')
        .eq('user_id', userId);

    if (accountsError) {
        throw accountsError;
    }

    // For now, return current balances
    // A more sophisticated implementation would track historical balances
    const balanceHistory = [];
    const now = new Date();

    for (let i = 5; i >= 0; i--) {
        const date = new Date(now.getFullYear(), now.getMonth() - i, 1);
        const monthKey = date.toISOString().substring(0, 7);

        // Get total balance across all accounts (simplified)
        const totalBalance = accounts?.reduce((sum, a) => sum + parseAmount(a.balance), 0) || 0;

        balanceHistory.push({
            month: monthKey,
            balance: Math.round(totalBalance * 100) / 100
        });
    }

    return balanceHistory;
}

export default {
    getStatistics,
    getBalanceHistory
};
