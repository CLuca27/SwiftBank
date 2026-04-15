package com.example.swiftbank.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.util.concurrent.Executor;

public class BiometricHelper {

    private static final String PREFS_NAME = "SwiftBankSettings";
    private static final String KEY_BIOMETRIC_ENABLED = "biometric_enabled";

    public interface BiometricCallback {
        void onSuccess();
        void onError(String error);
        void onCancel();
    }

    /**
     * Verifică dacă biometria este disponibilă pe dispozitiv
     */
    public static boolean isBiometricAvailable(Context context) {
        BiometricManager biometricManager = BiometricManager.from(context);
        int canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);
        return canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS;
    }

    /**
     * Verifică dacă utilizatorul a activat biometria în setări
     */
    public static boolean isBiometricEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false);
    }

    /**
     * Verifică dacă trebuie să folosim biometrie (disponibilă ȘI activată)
     */
    public static boolean shouldUseBiometric(Context context) {
        return isBiometricAvailable(context) && isBiometricEnabled(context);
    }

    /**
     * Afișează prompt-ul biometric
     */
    public static void authenticate(FragmentActivity activity, String title, String subtitle, BiometricCallback callback) {
        Executor executor = ContextCompat.getMainExecutor(activity);

        BiometricPrompt biometricPrompt = new BiometricPrompt(activity, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                            errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                            callback.onCancel();
                        } else {
                            callback.onError(errString.toString());
                        }
                    }

                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        callback.onSuccess();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        // Nu facem nimic - utilizatorul poate încerca din nou
                    }
                });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText("Folosește PIN")
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    /**
     * Afișează prompt-ul biometric pentru confirmare transfer
     */
    public static void authenticateForTransfer(FragmentActivity activity, String beneficiaryName, String amount, BiometricCallback callback) {
        authenticate(
                activity,
                "Confirmă transferul",
                String.format("Către %s: %s", beneficiaryName, amount),
                callback
        );
    }
}
