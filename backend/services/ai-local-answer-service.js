import accountService from './account-service.js';
import transactionService from './transaction-service.js';

const MAX_TRANSACTIONS_PER_ACCOUNT = 80;
const MAX_TRANSFER_PEOPLE = 6;
const MAX_LABEL_LENGTH = 48;

function normalizeIntentText(value) {
    return String(value || '')
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .toLowerCase()
        .replace(/\s+/g, ' ')
        .trim();
}

function normalizeCurrency(currency) {
    return String(currency || '').trim().toUpperCase();
}

function parseAmount(value) {
    const amount = Number.parseFloat(value || 0);
    return Number.isFinite(amount) ? amount : 0;
}

function roundMoney(value) {
    return Math.round(parseAmount(value) * 100) / 100;
}

function formatAmount(value, currency = 'RON') {
    const normalizedCurrency = normalizeCurrency(currency) || 'RON';

    try {
        return new Intl.NumberFormat('ro-RO', {
            style: 'currency',
            currency: normalizedCurrency,
            maximumFractionDigits: 2
        }).format(roundMoney(value));
    } catch {
        return `${roundMoney(value).toFixed(2)} ${normalizedCurrency}`;
    }
}

function sanitizePublicDetail(value) {
    if (value === null || value === undefined || value === '') return null;

    const text = String(value)
        .replace(/[\r\n\t]+/g, ' ')
        .replace(/\b[A-Z]{2}\d{2}[A-Z0-9]{10,30}\b/gi, '')
        .replace(/\b(?:\d[ -]?){12,19}\b/g, '')
        .replace(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/gi, '')
        .replace(/\s+/g, ' ')
        .trim();

    if (!text || /^schimb valutar$/i.test(text)) return null;
    return text.length > MAX_LABEL_LENGTH ? `${text.substring(0, MAX_LABEL_LENGTH - 1)}...` : text;
}

function isInternalTransfer(transaction) {
    return ['SELF_IN', 'SELF_OUT'].includes(transaction?.transaction_type)
        || transaction?.transfer_type === 'SELF';
}

function isTransferPersonQuestion(message) {
    const text = normalizeIntentText(message);
    const mentionsMoneyMovement = /(transfer|tranzact|trimi|primi|bani|plati|platit)/.test(text);
    const asksForPerson = /(nume|persoan|beneficiar|expeditor|destinatar|cine|cui)/.test(text)
        || text.includes('catre cine')
        || text.includes('de la cine');

    return mentionsMoneyMovement && asksForPerson;
}

function getQuestionDirection(message) {
    const text = normalizeIntentText(message);
    const asksIncoming = text.includes('de la cine')
        || /(primit|primesc|incas|expeditor)/.test(text);
    const asksOutgoing = text.includes('catre cine')
        || /(cui|catre|trimis|trimit|beneficiar|destinatar|platit)/.test(text);

    if (asksIncoming && !asksOutgoing) return 'incoming';
    if (asksOutgoing && !asksIncoming) return 'outgoing';
    return 'all';
}

function pickFirstPublicDetail(values) {
    for (const value of values) {
        const detail = sanitizePublicDetail(value);
        if (detail) return detail;
    }

    return null;
}

function getCounterparty(transaction) {
    if (!transaction || isInternalTransfer(transaction)) return null;

    if (transaction.transaction_type === 'TRANSFER_IN') {
        return pickFirstPublicDetail([
            transaction.sender_name,
            transaction.senderName,
            transaction.sender_full_name,
            transaction.senderFullName,
            transaction.from_name,
            transaction.fromName,
            transaction.beneficiary_name,
            transaction.beneficiaryName
        ]);
    }

    return pickFirstPublicDetail([
        transaction.beneficiary_name,
        transaction.beneficiaryName,
        transaction.recipient_name,
        transaction.recipientName,
        transaction.receiver_name,
        transaction.receiverName,
        transaction.to_name,
        transaction.toName
    ]);
}

function getTransferTypeLabel(transaction) {
    switch (transaction?.transfer_type) {
        case 'INTERNAL':
            return 'transfer SwiftBank';
        case 'EXTERNAL':
            return 'transfer extern';
        default:
            return null;
    }
}

function getTransactionDate(transaction) {
    const date = transaction?.created_at ? new Date(transaction.created_at) : null;
    if (!date || Number.isNaN(date.getTime())) return null;
    return date.toISOString().substring(0, 10);
}

async function loadTransactionsForAccounts(userId) {
    const accounts = await accountService.getAccountsByUserId(userId);
    const results = await Promise.all(accounts.map(async account => {
        const response = await transactionService.getTransactions(userId, {
            accountId: account.account_id,
            limit: MAX_TRANSACTIONS_PER_ACCOUNT,
            offset: 0
        });

        return response.transactions || [];
    }));

    return results.flat();
}

function buildTransferPeopleRows(transactions, directionFilter) {
    const rowsByKey = new Map();

    for (const transaction of transactions) {
        const type = transaction.transaction_type;
        if (!['TRANSFER_OUT', 'TRANSFER_IN'].includes(type) || isInternalTransfer(transaction)) {
            continue;
        }

        const direction = type === 'TRANSFER_IN' ? 'incoming' : 'outgoing';
        if (directionFilter !== 'all' && directionFilter !== direction) {
            continue;
        }

        const name = getCounterparty(transaction);
        if (!name) continue;

        const currency = normalizeCurrency(transaction.currency) || 'RON';
        const bankName = sanitizePublicDetail(transaction.bank_name || transaction.bankName);
        const transferType = getTransferTypeLabel(transaction);
        const key = [direction, name, currency, bankName || '', transferType || ''].join('|');
        const date = getTransactionDate(transaction);

        if (!rowsByKey.has(key)) {
            rowsByKey.set(key, {
                direction,
                name,
                currency,
                bankName,
                transferType,
                amount: 0,
                count: 0,
                lastDate: date
            });
        }

        const row = rowsByKey.get(key);
        row.amount += Math.abs(parseAmount(transaction.amount));
        row.count += 1;

        if (date && (!row.lastDate || date > row.lastDate)) {
            row.lastDate = date;
        }
    }

    return [...rowsByKey.values()]
        .sort((a, b) => b.amount - a.amount || b.count - a.count)
        .slice(0, MAX_TRANSFER_PEOPLE);
}

function formatTransferPeopleAnswer(rows, directionFilter) {
    if (!rows.length) {
        return 'Nu am gasit in istoricul recent nume clare pentru persoanele din transferuri. Pot afisa doar informatii sigure in chat, fara IBAN, referinte sau alte detalii sensibile.';
    }

    const intro = directionFilter === 'incoming'
        ? 'Am gasit aceste persoane de la care ai primit bani in transferurile recente:'
        : directionFilter === 'outgoing'
            ? 'Am gasit aceste persoane catre care ai trimis bani in transferurile recente:'
            : 'Am gasit aceste persoane in transferurile recente:';

    const lines = rows.map(row => {
        const directionLabel = row.direction === 'incoming' ? 'de la' : 'catre';
        const details = [row.bankName, row.transferType].filter(Boolean).join(', ');
        const detailsText = details ? ` (${details})` : '';
        const dateText = row.lastDate ? `, ultima data ${row.lastDate}` : '';
        const transactionWord = row.count === 1 ? 'tranzactie' : 'tranzactii';

        return `- ${directionLabel} ${row.name}${detailsText}: ${formatAmount(row.amount, row.currency)} in ${row.count} ${transactionWord}${dateText}`;
    });

    return [
        intro,
        '',
        ...lines,
        '',
        'Din motive de siguranta, nu afisez IBAN, referinte, descrieri complete sau alte date sensibile in chat.'
    ].join('\n');
}

async function tryBuildLocalAnswer(userId, message) {
    if (!isTransferPersonQuestion(message)) {
        return null;
    }

    const directionFilter = getQuestionDirection(message);
    const transactions = await loadTransactionsForAccounts(userId);
    const rows = buildTransferPeopleRows(transactions, directionFilter);

    return formatTransferPeopleAnswer(rows, directionFilter);
}

export default {
    tryBuildLocalAnswer
};
