package com.example.swiftbank.activities.settings;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.biometric.BiometricManager;
import androidx.core.content.ContextCompat;

import com.example.swiftbank.R;
import com.example.swiftbank.activities.welcome.WelcomeActivity;
import com.example.swiftbank.api.ApiClient;
import com.example.swiftbank.api.dto.request.LogoutRequest;
import com.example.swiftbank.api.dto.request.UpdateSettingsRequest;
import com.example.swiftbank.utils.DeviceDetails;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.success.SettingsData;
import com.example.swiftbank.managers.BiometricCredentialsManager;
import com.example.swiftbank.managers.AuthTokenManager;
import com.example.swiftbank.utils.SwiftBankDialog;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "SwiftBankSettings";
    private static final String KEY_BIOMETRIC_ENABLED = "biometric_enabled";
    private static final String KEY_NOTIFICATIONS_ENABLED = "notifications_enabled";

    private SharedPreferences prefs;
    private boolean isLoadingSettings = false;
    private SettingsData.UserSettings currentSettings;

    // Views
    private ImageView btnBack;
    private SwitchCompat switchBiometric;
    private SwitchCompat switchNotifications;

    // Permission launcher pentru notificări (declarat după switchNotifications)
    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (!isGranted) {
                    // Permisiune refuzată - dezactivează switch-ul
                    isLoadingSettings = true;
                    switchNotifications.setChecked(false);
                    isLoadingSettings = false;

                    SwiftBankDialog.showInfoDialog(this,
                            "Permisiune necesară",
                            "Pentru a primi notificări, trebuie să activezi permisiunea din setările telefonului.");
                }
            });
    private LinearLayout settingChangePin;
    private LinearLayout settingEditProfile;
    private LinearLayout settingTerms;
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

    @Override
    protected void onResume() {
        super.onResume();
        // Sincronizează starea switch-ului cu permisiunea reală (când revine din Settings Android)
        syncNotificationSwitchWithPermission();
    }

    private void syncNotificationSwitchWithPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && switchNotifications != null) {
            boolean hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;

            // Dacă switch-ul e ON dar permisiunea e refuzată, dezactivează-l
            if (switchNotifications.isChecked() && !hasPermission) {
                isLoadingSettings = true;
                switchNotifications.setChecked(false);
                isLoadingSettings = false;

                // Actualizează și pe server
                UpdateSettingsRequest request = new UpdateSettingsRequest()
                        .setNotificationsPush(false);
                updateSettingOnServer(request);
            }
        }
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        switchBiometric = findViewById(R.id.switchBiometric);
        switchNotifications = findViewById(R.id.switchNotifications);
        settingChangePin = findViewById(R.id.settingChangePin);
        settingEditProfile = findViewById(R.id.settingEditProfile);
        settingTerms = findViewById(R.id.settingTerms);
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
        // Check if biometric is available on device
        BiometricManager biometricManager = BiometricManager.from(this);
        int canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);
        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            switchBiometric.setEnabled(false);
            switchBiometric.setAlpha(0.5f);
        }

        // Load from local cache first (pentru UI rapid)
        boolean biometricEnabled = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false);
        switchBiometric.setChecked(biometricEnabled);

        boolean notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true);
        switchNotifications.setChecked(notificationsEnabled);

        // Fetch from server
        fetchSettingsFromServer();
    }

    private void fetchSettingsFromServer() {
        isLoadingSettings = true;

        ApiClient.getUserService().getSettings().enqueue(new Callback<ApiResponse<SettingsData>>() {
            @Override
            public void onResponse(Call<ApiResponse<SettingsData>> call, Response<ApiResponse<SettingsData>> response) {
                isLoadingSettings = false;

                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    currentSettings = response.body().getData().getSettings();
                    applySettingsToUI(currentSettings);
                    cacheSettingsLocally(currentSettings);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<SettingsData>> call, Throwable t) {
                isLoadingSettings = false;
                // Folosim cache-ul local - nu afișăm eroare
            }
        });
    }

    private void applySettingsToUI(SettingsData.UserSettings settings) {
        if (settings == null) return;

        // Security
        if (settings.getSecurity() != null) {
            // Doar dacă dispozitivul suportă biometrie, altfel forțăm OFF
            BiometricManager biometricManager = BiometricManager.from(this);
            boolean biometricAvailable = biometricManager.canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS;

            switchBiometric.setChecked(biometricAvailable && settings.getSecurity().isBiometricEnabled());
        }

        // Notifications
        if (settings.getNotifications() != null) {
            switchNotifications.setChecked(settings.getNotifications().isPushEnabled());
        }

        // Display - theme e local pentru acum
    }

    private void cacheSettingsLocally(SettingsData.UserSettings settings) {
        if (settings == null) return;

        SharedPreferences.Editor editor = prefs.edit();

        if (settings.getSecurity() != null) {
            editor.putBoolean(KEY_BIOMETRIC_ENABLED, settings.getSecurity().isBiometricEnabled());
        }

        if (settings.getNotifications() != null) {
            editor.putBoolean(KEY_NOTIFICATIONS_ENABLED, settings.getNotifications().isPushEnabled());
        }

        editor.apply();
    }

    private void updateSettingOnServer(UpdateSettingsRequest request) {
        ApiClient.getUserService().updateSettings(request).enqueue(new Callback<ApiResponse<SettingsData>>() {
            @Override
            public void onResponse(Call<ApiResponse<SettingsData>> call, Response<ApiResponse<SettingsData>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    currentSettings = response.body().getData().getSettings();
                    cacheSettingsLocally(currentSettings);
                } else {
                    Toast.makeText(SettingsActivity.this, "Eroare la salvarea setărilor", Toast.LENGTH_SHORT).show();
                    // Revert UI - reîncarcă
                    fetchSettingsFromServer();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<SettingsData>> call, Throwable t) {
                Toast.makeText(SettingsActivity.this, "Eroare de conexiune", Toast.LENGTH_SHORT).show();
                fetchSettingsFromServer();
            }
        });
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        // Biometric toggle
        switchBiometric.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isLoadingSettings) return; // Ignoră schimbările în timpul încărcării

            if (isChecked) {
                // Verify biometric is available before enabling
                BiometricManager biometricManager = BiometricManager.from(this);
                int canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);

                if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
                    // Salvează pe server
                    UpdateSettingsRequest request = new UpdateSettingsRequest()
                            .setSecurityBiometric(true);
                    updateSettingOnServer(request);

                    prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, true).apply();
                    SwiftBankDialog.showSuccessDialog(this,
                        "Biometrie activată",
                        "La următoarea autentificare cu PIN, amprenta va fi activată pentru login și confirmări.",
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
                // Salvează pe server
                UpdateSettingsRequest request = new UpdateSettingsRequest()
                        .setSecurityBiometric(false);
                updateSettingOnServer(request);

                prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, false).apply();

                // Șterge credențialele salvate
                BiometricCredentialsManager.getInstance(this).clearCredentials();
            }
        });

        // Notifications toggle
        switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isLoadingSettings) return;

            if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Verifică permisiunea Android pentru notificări
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {

                    // Verifică dacă putem cere permisiunea sau trebuie să deschidem Settings
                    if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                        // Putem cere permisiunea
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                    } else {
                        // Permisiunea a fost refuzată definitiv - deschide Settings
                        isLoadingSettings = true;
                        switchNotifications.setChecked(false);
                        isLoadingSettings = false;

                        new SwiftBankDialog(this)
                                .setIcon(R.drawable.ic_notifications)
                                .setTitle("Permisiune necesară")
                                .setMessage("Pentru a primi notificări, activează permisiunea din setările telefonului.")
                                .setPrimaryButton("Deschide setările", v -> {
                                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                    intent.setData(Uri.fromParts("package", getPackageName(), null));
                                    startActivity(intent);
                                })
                                .setSecondaryButton("Anulează", null)
                                .show();
                        return;
                    }
                }
            }

            UpdateSettingsRequest request = new UpdateSettingsRequest()
                    .setNotificationsPush(isChecked);
            updateSettingOnServer(request);

            prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, isChecked).apply();
        });

        // Change PIN
        settingChangePin.setOnClickListener(v -> {
            startActivity(new Intent(this, ChangePinActivity.class));
        });

        // Edit Profile
        settingEditProfile.setOnClickListener(v -> {
            startActivity(new Intent(this, ProfileActivity.class));
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
        AuthTokenManager authTokenManager = AuthTokenManager.getInstance(this);
        String refreshToken = authTokenManager.getRefreshToken();
        String deviceId = DeviceDetails.getDeviceId(this);

        // Apelează endpoint-ul de logout pe server
        LogoutRequest request = new LogoutRequest(refreshToken, deviceId);
        ApiClient.getAuthService().logout(request).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                // Continuă cu logout local indiferent de răspuns
                completeLogout();
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                // Continuă cu logout local chiar dacă server-ul nu răspunde
                completeLogout();
            }
        });
    }

    private void completeLogout() {
        // Clear tokens local
        AuthTokenManager.getInstance(this).clearTokens();

        // Clear biometric preference and credentials
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, false).apply();
        BiometricCredentialsManager.getInstance(this).clearCredentials();

        // Navigate to welcome screen
        Intent intent = new Intent(this, WelcomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
