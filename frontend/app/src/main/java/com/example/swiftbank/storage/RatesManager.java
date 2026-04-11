package com.example.swiftbank.storage;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Manager pentru stocarea locală a ratelor de schimb.
 * Ratele sunt salvate în SharedPreferences pentru acces instant.
 */
public class RatesManager {

    private static final String PREFS_NAME = "SwiftBankRates";
    private static final String KEY_RATES = "exchange_rates";
    private static final String KEY_LAST_UPDATE = "last_update";
    private static final String KEY_RATE_DATE = "rate_date";        // Data cursului BNR (yyyy-MM-dd)
    private static final String KEY_LAST_CHECK = "last_check_date"; // Ultima zi când am verificat

    private static RatesManager instance;
    private SharedPreferences prefs;

    // Cache în memorie pentru acces și mai rapid
    private Map<String, Double> ratesCache = new HashMap<>();

    private RatesManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadRatesFromPrefs();
    }

    public static synchronized RatesManager getInstance(Context context) {
        if (instance == null) {
            instance = new RatesManager(context);
        }
        return instance;
    }

    /**
     * Salvează ratele (toate valutele vs RON)
     * @param rates Map cu currency -> rate vs RON (ex: EUR -> 4.97)
     * @param rateDate Data cursului BNR (yyyy-MM-dd) - poate fi null
     */
    public void saveRates(Map<String, Double> rates, String rateDate) {
        try {
            JSONObject json = new JSONObject();
            for (Map.Entry<String, Double> entry : rates.entrySet()) {
                json.put(entry.getKey(), entry.getValue());
            }

            String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    .format(new java.util.Date());

            SharedPreferences.Editor editor = prefs.edit()
                .putString(KEY_RATES, json.toString())
                .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
                .putString(KEY_LAST_CHECK, today);

            if (rateDate != null) {
                editor.putString(KEY_RATE_DATE, rateDate);
            }

            editor.apply();

            // Actualizează cache-ul
            ratesCache.clear();
            ratesCache.putAll(rates);
            ratesCache.put("RON", 1.0); // RON vs RON = 1

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Salvează ratele fără data cursului (pentru compatibilitate)
     */
    public void saveRates(Map<String, Double> rates) {
        saveRates(rates, null);
    }

    /**
     * Obține data cursului BNR salvat
     */
    public String getRateDate() {
        return prefs.getString(KEY_RATE_DATE, null);
    }

    /**
     * Verifică dacă am făcut deja check azi
     */
    public boolean alreadyCheckedToday() {
        String lastCheck = prefs.getString(KEY_LAST_CHECK, null);
        if (lastCheck == null) return false;

        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(new java.util.Date());

        return lastCheck.equals(today);
    }

    /**
     * Încarcă ratele din SharedPreferences în cache
     */
    private void loadRatesFromPrefs() {
        String json = prefs.getString(KEY_RATES, null);
        if (json != null) {
            try {
                JSONObject obj = new JSONObject(json);
                Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    ratesCache.put(key, obj.getDouble(key));
                }
                ratesCache.put("RON", 1.0);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Obține rata de schimb între două valute
     * @return rata sau 0 dacă nu există
     */
    public double getExchangeRate(String fromCurrency, String toCurrency) {
        if (fromCurrency.equals(toCurrency)) {
            return 1.0;
        }

        Double fromRate = ratesCache.get(fromCurrency);
        Double toRate = ratesCache.get(toCurrency);

        if (fromRate == null || toRate == null) {
            return 0;
        }

        // fromCurrency -> RON -> toCurrency
        // EUR -> RON = 4.97, USD -> RON = 4.5
        // EUR -> USD = 4.97 / 4.5 = 1.104
        return fromRate / toRate;
    }

    /**
     * Verifică dacă avem rate salvate
     */
    public boolean hasRates() {
        return !ratesCache.isEmpty() && ratesCache.size() > 1; // mai mult decât doar RON
    }

    /**
     * Obține timestamp-ul ultimei actualizări
     */
    public long getLastUpdate() {
        return prefs.getLong(KEY_LAST_UPDATE, 0);
    }

    /**
     * Verifică dacă ratele sunt mai vechi de X ore
     */
    public boolean isStale(int hours) {
        long lastUpdate = getLastUpdate();
        if (lastUpdate == 0) return true;

        long diff = System.currentTimeMillis() - lastUpdate;
        return diff > (hours * 60 * 60 * 1000L);
    }

    /**
     * Verifică dacă trebuie să actualizăm ratele bazat pe programul BNR:
     * - BNR actualizează ratele Luni-Vineri în jurul orei 13:00
     * - Sâmbătă/Duminică nu sunt actualizări - ratele de Vineri rămân valide
     * - Sărbători legale: verificăm o dată pe zi, dacă rate_date e aceeași, nu mai verificăm
     */
    public boolean needsRefresh() {
        // Dacă nu avem rate deloc, trebuie să le luăm
        if (!hasRates()) return true;

        // Dacă am verificat deja azi și avem rate, nu mai verificăm
        // (acoperă cazul sărbătorilor - am verificat, BNR n-a publicat nimic nou)
        if (alreadyCheckedToday()) return false;

        java.util.Calendar now = java.util.Calendar.getInstance();
        int dayOfWeek = now.get(java.util.Calendar.DAY_OF_WEEK);

        // Weekend - nu verificăm (BNR nu publică)
        if (dayOfWeek == java.util.Calendar.SATURDAY || dayOfWeek == java.util.Calendar.SUNDAY) {
            return false;
        }

        // Zi lucrătoare - verificăm doar după ora 13:00
        int currentHour = now.get(java.util.Calendar.HOUR_OF_DAY);
        if (currentHour < 13) {
            return false;
        }

        // E zi lucrătoare după 13:00 și nu am verificat azi → refresh
        return true;
    }


    /**
     * Șterge toate ratele
     */
    public void clear() {
        prefs.edit().clear().apply();
        ratesCache.clear();
    }
}
