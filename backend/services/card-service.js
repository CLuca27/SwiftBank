import config from '../config/index.js';
import crypto from 'crypto';

// Cheia pentru criptare CVV (în producție ar fi în env vars)
const CVV_ENCRYPTION_KEY = process.env.CVV_ENCRYPTION_KEY || 'swiftbank-cvv-secret-key-32ch';
const CVV_IV_LENGTH = 16;

// Criptează CVV
function encryptCVV(cvv) {
    const iv = crypto.randomBytes(CVV_IV_LENGTH);
    const key = crypto.scryptSync(CVV_ENCRYPTION_KEY, 'salt', 32);
    const cipher = crypto.createCipheriv('aes-256-cbc', key, iv);
    let encrypted = cipher.update(cvv, 'utf8', 'hex');
    encrypted += cipher.final('hex');
    return iv.toString('hex') + ':' + encrypted;
}

// Decriptează CVV
function decryptCVV(encryptedCvv) {
    const parts = encryptedCvv.split(':');
    const iv = Buffer.from(parts[0], 'hex');
    const encrypted = parts[1];
    const key = crypto.scryptSync(CVV_ENCRYPTION_KEY, 'salt', 32);
    const decipher = crypto.createDecipheriv('aes-256-cbc', key, iv);
    let decrypted = decipher.update(encrypted, 'hex', 'utf8');
    decrypted += decipher.final('utf8');
    return decrypted;
}

// Generează un număr de card valid (Luhn algorithm)
function generateCardNumber() {
    // Prefix pentru Visa: 4
    let cardNumber = '4';

    // Generăm 14 cifre random
    for (let i = 0; i < 14; i++) {
        cardNumber += Math.floor(Math.random() * 10);
    }

    // Calculăm cifra de control (Luhn)
    const checkDigit = calculateLuhnCheckDigit(cardNumber);
    cardNumber += checkDigit;

    return cardNumber;
}

function calculateLuhnCheckDigit(partialCardNumber) {
    let sum = 0;
    let isEven = true;

    for (let i = partialCardNumber.length - 1; i >= 0; i--) {
        let digit = parseInt(partialCardNumber[i]);

        if (isEven) {
            digit *= 2;
            if (digit > 9) {
                digit -= 9;
            }
        }

        sum += digit;
        isEven = !isEven;
    }

    return (10 - (sum % 10)) % 10;
}

// Generează CVV (3 cifre)
function generateCVV() {
    return String(Math.floor(100 + Math.random() * 900));
}

// Generează data de expirare (3 ani de acum)
function generateExpiryDate() {
    const now = new Date();
    const expiry = new Date(now.getFullYear() + 3, now.getMonth(), 1);
    return expiry.toISOString().split('T')[0];
}


// Obține cardurile utilizatorului
async function getUserCards(userId) {
    const { data, error } = await config.supabase
        .from('cards')
        .select('card_id, card_number, card_holder_name, expiry_date, card_brand, card_design, status, created_at')
        .eq('user_id', userId)
        .order('created_at', { ascending: false });

    if (error) throw error;

    // Mascăm numărul cardului (arătăm doar ultimele 4 cifre)
    return data.map(card => ({
        ...card,
        card_number_masked: '**** **** **** ' + card.card_number.slice(-4),
        card_number: undefined
    }));
}

// Obține detaliile complete ale cardului (după autentificare)
async function getCardDetails(cardId, userId) {
    const { data, error } = await config.supabase
        .from('cards')
        .select('*')
        .eq('card_id', cardId)
        .eq('user_id', userId)
        .single();

    if (error) throw error;

    return data;
}

// Designuri disponibile pentru carduri
const CARD_DESIGNS = ['green', 'blue', 'purple', 'orange', 'black'];

// Creează un card nou
async function createCard(userId, cardDesign = 'green') {
    // Validare design
    if (!CARD_DESIGNS.includes(cardDesign)) {
        cardDesign = 'green';
    }

    // Obținem numele utilizatorului pentru card_holder_name
    const { data: user, error: userError } = await config.supabase
        .from('users')
        .select('first_name, last_name')
        .eq('user_id', userId)
        .single();

    if (userError) throw userError;

    const cardHolderName = `${user.first_name} ${user.last_name}`.toUpperCase();
    const cardNumber = generateCardNumber();
    const cvv = generateCVV();
    const cvvEncrypted = encryptCVV(cvv);
    const expiryDate = generateExpiryDate();

    const { data, error } = await config.supabase
        .from('cards')
        .insert({
            user_id: userId,
            card_number: cardNumber,
            card_holder_name: cardHolderName,
            expiry_date: expiryDate,
            cvv_hash: cvvEncrypted,
            card_brand: 'Visa',
            card_design: cardDesign,
            status: 'active'
        })
        .select()
        .single();

    if (error) throw error;

    // Returnăm datele cardului inclusiv CVV-ul (doar la creare!)
    return {
        card_id: data.card_id,
        card_number: cardNumber,
        card_number_formatted: formatCardNumber(cardNumber),
        card_holder_name: cardHolderName,
        expiry_date: expiryDate,
        cvv: cvv,
        card_brand: data.card_brand,
        card_design: data.card_design,
        status: data.status
    };
}

// Formatează numărul cardului cu spații
function formatCardNumber(cardNumber) {
    return cardNumber.replace(/(.{4})/g, '$1 ').trim();
}

// Blochează/deblochează card
async function toggleCardBlock(cardId, userId) {
    // Verificăm că cardul aparține utilizatorului
    const { data: card, error: fetchError } = await config.supabase
        .from('cards')
        .select('status')
        .eq('card_id', cardId)
        .eq('user_id', userId)
        .single();

    if (fetchError) throw fetchError;
    if (!card) throw new Error('Card not found');

    const newStatus = card.status === 'active' ? 'blocked' : 'active';

    const { data, error } = await config.supabase
        .from('cards')
        .update({ status: newStatus })
        .eq('card_id', cardId)
        .eq('user_id', userId)
        .select()
        .single();

    if (error) throw error;

    return { status: newStatus };
}

// Șterge card
async function deleteCard(cardId, userId) {
    const { error } = await config.supabase
        .from('cards')
        .delete()
        .eq('card_id', cardId)
        .eq('user_id', userId);

    if (error) throw error;

    return true;
}

// Actualizează designul cardului
async function updateCardDesign(cardId, userId, design) {
    if (!CARD_DESIGNS.includes(design)) {
        throw new Error('Invalid card design');
    }

    const { data, error } = await config.supabase
        .from('cards')
        .update({ card_design: design })
        .eq('card_id', cardId)
        .eq('user_id', userId)
        .select()
        .single();

    if (error) throw error;

    return data;
}

export default {
    generateCardNumber,
    generateCVV,
    generateExpiryDate,
    encryptCVV,
    decryptCVV,
    getUserCards,
    getCardDetails,
    createCard,
    toggleCardBlock,
    deleteCard,
    updateCardDesign,
    formatCardNumber,
    CARD_DESIGNS
};
