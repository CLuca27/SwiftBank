package com.example.swiftbank.activities.settings;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.biometric.BiometricManager;

import com.example.swiftbank.R;
import com.example.swiftbank.activities.welcome.WelcomeActivity;
import com.example.swiftbank.storage.TokenManager;
import com.example.swiftbank.utils.SwiftBankDialog;

public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "SwiftBankSettings";
    private static final String KEY_BIOMETRIC_ENABLED = "biometric_enabled";
    private static final String KEY_NOTIFICATIONS_ENABLED = "notifications_enabled";
    private static final String KEY_THEME = "theme"; // "dark" or "light"

    private SharedPreferences prefs;

    // Views
    private ImageView btnBack;
    private SwitchCompat switchBiometric;
    private SwitchCompat switchNotifications;
    private LinearLayout settingChangePin;
    private LinearLayout settingEditProfile;
    private LinearLayout settingTheme;
    private LinearLayout settingTerms;
    private TextView tvCurrentTheme;
    private TextView tvAppVersion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        initViews();
        loadSettings();
        setupListeners();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        switchBiometric = findViewById(R.id.switchBiometric);
        switchNotifications = findViewById(R.id.switchNotifications);
        settingChangePin = findViewById(R.id.settingChangePin);
        settingEditProfile = findViewById(R.id.settingEditProfile);
        settingTheme = findViewById(R.id.settingTheme);
        settingTerms = findViewById(R.id.settingTerms);
        tvCurrentTheme = findViewById(R.id.tvCurrentTheme);
        tvAppVersion = findViewById(R.id.tvAppVersion);

        // Set app version
        try {
            String version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            tvAppVersion.setText(version);
        } catch (Exception e) {
            tvAppVersion.setText("1.0.0");
        }
    }

    private void loadSettings() {
        // Load biometric setting
        boolean biometricEnabled = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false);
        switchBiometric.setChecked(biometricEnabled);

        // Check if biometric is available
        BiometricManager biometricManager = BiometricManager.from(this);
        int canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);
        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            switchBiometric.setEnabled(false);
            switchBiometric.setAlpha(0.5f);
        }

        // Load notifications setting
        boolean notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true);
        switchNotifications.setChecked(notificationsEnabled);

        // Load theme setting
        String theme = prefs.getString(KEY_THEME, "dark");
        tvCurrentTheme.setText(theme.equals("dark") ? "Întunecată" : "Luminoasă");
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        // Biometric toggle
        switchBiometric.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // Verify biometric is available before enabling
                BiometricManager biometricManager = BiometricManager.from(this);
                int canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);

                if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
                    prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, true).apply();
                    SwiftBankDialog.showSuccessDialog(this,
                        "Biometrie activată",
                        "Acum te poți autentifica folosind amprenta.",
                        null);
                } else {
                    switchBiometric.setChecked(false);
                    String message = "Dispozitivul nu suportă autentificare biometrică.";
                    if (canAuthenticate == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
                        message = "Nu ai configurat nicio amprentă pe dispozitiv. Configurează din setările telefonului.";
                    }
                    SwiftBankDialog.showErrorDialog(this, message);
                }
            } else {
                prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, false).apply();
            }
        });

        // Notifications toggle
        switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, isChecked).apply();
            // TODO: Sync with server if needed
        });

        // Change PIN
        settingChangePin.setOnClickListener(v -> {
            // TODO: Implement change PIN flow
            SwiftBankDialog.showInfoDialog(this,
                "În curând",
                "Funcționalitatea de schimbare PIN va fi disponibilă în curând.");
        });

        // Edit Profile
        settingEditProfile.setOnClickListener(v -> {
            // TODO: Implement edit profile screen
            SwiftBankDialog.showInfoDialog(this,
                "În curând",
                "Editarea profilului va fi disponibilă în curând.");
        });

        // Theme
        settingTheme.setOnClickListener(v -> {
            showThemeDialog();
        });

        // Terms
        settingTerms.setOnClickListener(v -> {
            // TODO: Show terms and conditions
            SwiftBankDialog.showInfoDialog(this,
                "Termeni și condiții",
                "Prin utilizarea aplicației SwiftBank, ești de acord cu termenii și condițiile noastre de utilizare.");
        });

        // Logout
        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            showLogoutConfirmation();
        });
    }

    private void showThemeDialog() {
        String currentTheme = prefs.getString(KEY_THEME, "dark");

        new SwiftBankDialog(this)
            .setTitle("Alege tema")
            .setMessage("Selectează tema preferată pentru aplicație.")
            .setPrimaryButton(currentTheme.equals("dark") ? "Luminoasă" : "Întunecată", v -> {
                String newTheme = currentTheme.equals("dark") ? "light" : "dark";
                prefs.edit().putString(KEY_THEME, newTheme).apply();
                tvCurrentTheme.setText(newTheme.equals("dark") ? "Întunecată" : "Luminoasă");

                // TODO: Apply theme change
                SwiftBankDialog.showInfoDialog(this,
                    "Temă schimbată",
                    "Tema va fi aplicată la următoarea deschidere a aplicației.");
            })
            .setSecondaryButton("Anulează", null)
            .show();
    }

    private void showLogoutConfirmation() {
        new SwiftBankDialog(this)
            .setIcon(R.drawable.ic_logout)
            .setTitle("Deconectare")
            .setMessage("Ești sigur că vrei să te deconectezi?")
            .setPrimaryButton("Deconectare", v -> {
                performLogout();
            })
            .setSecondaryButton("Anulează", null)
            .show();
    }

    private void performLogout() {
        // Clear tokens
        TokenManager.getInstance(this).clearTokens();

        // Clear biometric preference
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, false).apply();

        // Navigate to welcome screen
        Intent intent = new Intent(this, WelcomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
