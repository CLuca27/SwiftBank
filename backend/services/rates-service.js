import axios from 'axios';
import xml2js from 'xml2js';
import config from '../config/index.js';

const BNR_URL = 'https://www.bnr.ro/nbrfxrates.xml';
const SUPPORTED_CURRENCIES = ['EUR', 'USD'];

async function fetchBNRRates() {
    try {
        const response = await axios.get(BNR_URL, {
            headers: {
                'User-Agent': 'SwiftBank/1.0'
            },
            timeout: 10000
        });

        const parser = new xml2js.Parser({ explicitArray: false });
        const result = await parser.parseStringPromise(response.data);

        // Extrage data și ratele
        const cube = result.DataSet.Body.Cube;
        const rateDate = cube.$.date;
        const rates = Array.isArray(cube.Rate) ? cube.Rate : [cube.Rate];

        // Filtrăm doar EUR și USD
        const extractedRates = [];

        for (const rateObj of rates) {
            const currency = rateObj.$.currency;

            if (SUPPORTED_CURRENCIES.includes(currency)) {
                const multiplier = rateObj.$.multiplier ? parseInt(rateObj.$.multiplier) : 1;
                const rateValue = parseFloat(rateObj._) / multiplier;

                extractedRates.push({
                    from_currency: currency,
                    to_currency: 'RON',
                    rate: rateValue,
                    rate_date: rateDate
                });
            }
        }

        console.log(`[Rates] Fetched ${extractedRates.length} rates for ${rateDate}`);
        return extractedRates;

    } catch (error) {
        console.error('[Rates] Error fetching BNR rates:', error.message);
        throw error;
    }
} 

async function saveRates(rates) {
    const savedRates = [];

    for (const rate of rates) {
        const { data, error } = await config.supabase
            .from('exchange_rates')
            .upsert({
                from_currency: rate.from_currency,
                to_currency: rate.to_currency,
                rate: rate.rate,
                rate_date: rate.rate_date
            }, {
                onConflict: 'from_currency,to_currency,rate_date'
            })
            .select()
            .single();

        if (error) {
            console.error(`[Rates] Error saving ${rate.from_currency}:`, error.message);
        } else {
            console.log(`[Rates] Saved ${rate.from_currency} → ${rate.to_currency}: ${rate.rate}`);
            savedRates.push(data);
        }
    }

    return savedRates;
} 

async function updateRates() {
    console.log('[Rates] Starting update...');

    const rates = await fetchBNRRates();
    const savedRates = await saveRates(rates);

    console.log('[Rates] Update completed!');
    return savedRates;
}


//Obține ratele curente din DB (cele mai recente)
 
async function getCurrentRates() {
    const { data, error } = await config.supabase
        .from('exchange_rates')
        .select('*')
        .in('from_currency', SUPPORTED_CURRENCIES)
        .order('rate_date', { ascending: false })
        .limit(SUPPORTED_CURRENCIES.length);

    if (error) {
        console.error('[Rates] Error fetching current rates:', error.message);
        throw error;
    }

    return data || [];
} 

async function getRate(fromCurrency) {
    const { data, error } = await config.supabase
        .from('exchange_rates')
        .select('*')
        .eq('from_currency', fromCurrency)
        .eq('to_currency', 'RON')
        .order('rate_date', { ascending: false })
        .limit(1)
        .maybeSingle();

    if (error) {
        console.error(`[Rates] Error fetching ${fromCurrency} rate:`, error.message);
        throw error;
    }

    return data;
} 

export default{
    updateRates,
    getCurrentRates,
    getRate
}
 