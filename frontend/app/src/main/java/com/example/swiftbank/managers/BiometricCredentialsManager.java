package com.example.swiftbank.managers;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Manager pentru stocarea securizată a credențialelor pentru autentificare biometrică.
 * Folosește EncryptedSharedPreferences pentru a stoca PIN-ul criptat.
 */
public class BiometricCredentialsManager {

    private static final String TAG = "BiometricCredentials";
    private static final String PREFS_NAME = "biometric_credentials";
    private static final String KEY_PIN = "stored_pin";
    private static final String KEY_PHONE = "stored_phone";
    private static final String KEY_EMAIL = "stored_email";

    private static BiometricCredentialsManager instance;
    private SharedPreferences encryptedPrefs;

    private BiometricCredentialsManager(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            encryptedPrefs = EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Error creating encrypted prefs: " + e.getMessage());
            // Fallback to regular prefs (less secure, but won't crash)
            encryptedPrefs = context.getSharedPreferences(PREFS_NAME + "_fallback", Context.MODE_PRIVATE);
        }
    }

    public static synchronized BiometricCredentialsManager getInstance(Context context) {
        if (instance == null) {
            instance = new BiometricCredentialsManager(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * Salvează credențialele pentru autentificare biometrică.
     * Apelat după un login cu PIN reușit când biometria e activată.
     */
    public void saveCredentials(String phone, String email, String pin) {
        encryptedPrefs.edit()
                .putString(KEY_PHONE, phone)
                .putString(KEY_EMAIL, email)
                .putString(KEY_PIN, pin)
                .apply();
        Log.d(TAG, "Credentials saved for biometric login");
    }

    /**
     * Verifică dacă avem credențiale salvate.
     */
    public boolean hasCredentials() {
        String pin = encryptedPrefs.getString(KEY_PIN, null);
        return pin != null && !pin.isEmpty();
    }

    public boolean hasCredentialsFor(String phone, String email) {
        if (!hasCredentials()) {
            return false;
        }

        boolean hasRequestedIdentifier = hasValue(phone) || hasValue(email);
        if (!hasRequestedIdentifier) {
            return true;
        }

        return matches(phone, getPhone()) || matches(email, getEmail());
    }

    private boolean matches(String requestedValue, String storedValue) {
        if (!hasValue(requestedValue) || !hasValue(storedValue)) {
            return false;
        }
        return normalize(requestedValue).equals(normalize(storedValue));
    }

    private boolean hasValue(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String normalize(String value) {
        return value.trim().replace(" ", "").toLowerCase();
    }

    /**
     * Obține PIN-ul salvat (după autentificare biometrică reușită).
     */
    public String getPin() {
        return encryptedPrefs.getString(KEY_PIN, null);
    }

    /**
     * Obține telefonul salvat.
     */
    public String getPhone() {
        return encryptedPrefs.getString(KEY_PHONE, null);
    }

    /**
     * Obține email-ul salvat.
     */
    public String getEmail() {
        return encryptedPrefs.getString(KEY_EMAIL, null);
    }

    /**
     * Șterge credențialele (la logout sau când se dezactivează biometria).
     */
    public void clearCredentials() {
        encryptedPrefs.edit()
                .remove(KEY_PIN)
                .remove(KEY_PHONE)
                .remove(KEY_EMAIL)
                .apply();
        Log.d(TAG, "Credentials cleared");
    }
}
