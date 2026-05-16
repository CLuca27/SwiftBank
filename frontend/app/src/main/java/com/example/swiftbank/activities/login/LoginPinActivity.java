package com.example.swiftbank.activities.login;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.example.swiftbank.R;
import com.example.swiftbank.managers.BiometricCredentialsManager;
import com.example.swiftbank.activities.cards.CardPaymentApprovalActivity;
import com.example.swiftbank.activities.dashboard.DashboardActivity;
import com.example.swiftbank.api.ApiClient;
import com.example.swiftbank.api.dto.request.ForgotPinRequest;
import com.example.swiftbank.api.dto.request.IdentifyRequest;
import com.example.swiftbank.api.dto.request.LoginRequest;
import com.example.swiftbank.api.dto.response.data.success.ForgotPinData;
import com.example.swiftbank.api.dto.response.ApiErrorResponse;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.success.IdentifyData;
import com.example.swiftbank.api.dto.response.data.success.LoginData;
import com.example.swiftbank.api.dto.response.data.error.AttemptsErrorData;
import com.example.swiftbank.api.dto.response.data.error.ErrorData;
import com.example.swiftbank.api.dto.response.data.error.LoginCooldownErrorData;
import com.example.swiftbank.managers.AuthTokenManager;
import com.example.swiftbank.utils.DeviceDetails;
import com.example.swiftbank.api.dto.response.data.error.ErrorParser;
import com.example.swiftbank.utils.SwiftBankDialog;
import com.example.swiftbank.views.ParticlesView;

import java.time.Instant;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginPinActivity extends AppCompatActivity {

    private static final String TAG = "LoginPinActivity";
    private static final int PIN_LENGTH = 6;

    // Views
    private ImageView btnBack;
    private TextView tvTitle;
    private TextView tvSubtitle;
    private LinearLayout dotsContainer;
    private View[] dots;
    private ImageView btnBiometric;
    private TextView tvError;
    private TextView tvForgotPin;
    private View loadingOverlay;
    private ParticlesView particlesView;

    // Numpad
    private TextView[] numpadButtons;
    private ImageView btnBackspace;

    // State
    private StringBuilder currentPin = new StringBuilder();
    private boolean isAnimating = false;
    private boolean isLocked = false;
    private boolean isLoading = false;
    private CountDownTimer lockTimer;
    private AnimatorSet bouncingAnimator;

    // Data from Intent
    private String phone;
    private String email;
    private String firstName;
    private String lockedUntil;

    // Biometric
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;
    private boolean biometricEnabled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin);

        getIntentData();
        initViews();
        setupNumpad();
        setupListeners();
        setupBiometric();
        updateUI();
        checkInitialLockState();
    }

    private void getIntentData() {
        phone = getIntent().getStringExtra("phone");
        email = getIntent().getStringExtra("email");
        firstName = getIntent().getStringExtra("first_name");
        lockedUntil = getIntent().getStringExtra("locked_until");
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        tvTitle = findViewById(R.id.tvTitle);
        tvSubtitle = findViewById(R.id.tvSubtitle);
        dotsContainer = findViewById(R.id.dotsContainer);
        tvError = findViewById(R.id.tvError);
        tvForgotPin = findViewById(R.id.tvForgotPin);
        btnBiometric = findViewById(R.id.btnBiometric);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        particlesView = findViewById(R.id.particlesView);

        btnBack.setVisibility(View.INVISIBLE);
        btnBack.setEnabled(false);

        // Afișează elementele de login
        tvForgotPin.setVisibility(View.VISIBLE);

        // Verifică dacă biometria e activată și disponibilă
        checkBiometricAvailability();

        // Inițializează dots
        dots = new View[PIN_LENGTH];
        dots[0] = findViewById(R.id.dot1);
        dots[1] = findViewById(R.id.dot2);
        dots[2] = findViewById(R.id.dot3);
        dots[3] = findViewById(R.id.dot4);
        dots[4] = findViewById(R.id.dot5);
        dots[5] = findViewById(R.id.dot6);

        btnBackspace = findViewById(R.id.btnBackspace);
        btnBackspace.setAlpha(0f);
        btnBackspace.setEnabled(false);
    }

    private void setupNumpad() {
        numpadButtons = new TextView[10];
        numpadButtons[0] = findViewById(R.id.btn0);
        numpadButtons[1] = findViewById(R.id.btn1);
        numpadButtons[2] = findViewById(R.id.btn2);
        numpadButtons[3] = findViewById(R.id.btn3);
        numpadButtons[4] = findViewById(R.id.btn4);
        numpadButtons[5] = findViewById(R.id.btn5);
        numpadButtons[6] = findViewById(R.id.btn6);
        numpadButtons[7] = findViewById(R.id.btn7);
        numpadButtons[8] = findViewById(R.id.btn8);
        numpadButtons[9] = findViewById(R.id.btn9);

        for (int i = 0; i < 10; i++) {
            final int digit = i;
            numpadButtons[i].setOnClickListener(v -> onDigitPressed(digit));
        }

        btnBackspace.setOnClickListener(v -> onBackspacePressed());
    }

    private void setupListeners() {
        tvForgotPin.setOnClickListener(v -> showForgotPinDialog());

        btnBiometric.setOnClickListener(v -> {
            if (biometricEnabled && biometricPrompt != null) {
                showBiometricPrompt();
            }
        });
    }

    private void updateUI() {
        if (firstName != null && !firstName.isEmpty()) {
            tvTitle.setText("Bun venit, " + firstName + "!");
        } else {
            tvTitle.setText("Bun venit!");
        }
        tvSubtitle.setText("Introdu codul PIN");
    }

    private void checkInitialLockState() {
        if (startLockTimerFromTimestamp(lockedUntil)) {
            return;
        }

        refreshLockStateFromBackend();
    }

    private boolean startLockTimerFromTimestamp(String lockedUntilValue) {
        int secondsLeft = calculateSecondsLeft(lockedUntilValue);
        if (secondsLeft <= 0) {
            return false;
        }

        startLockTimer(secondsLeft);
        return true;
    }

    private void refreshLockStateFromBackend() {
        if ((phone == null || phone.isEmpty()) && (email == null || email.isEmpty())) {
            return;
        }

        IdentifyRequest request = new IdentifyRequest(email, phone);
        ApiClient.getAuthService().identify(request).enqueue(new Callback<ApiResponse<IdentifyData>>() {
            @Override
            public void onResponse(Call<ApiResponse<IdentifyData>> call,
                                   Response<ApiResponse<IdentifyData>> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().getData() == null) {
                    return;
                }

                IdentifyData data = response.body().getData();
                if (data.isBlocked()) {
                    goToBlockedPage();
                    return;
                }

                startLockTimerFromTimestamp(data.getLockedUntil());
            }

            @Override
            public void onFailure(Call<ApiResponse<IdentifyData>> call, Throwable t) {
                Log.e(TAG, "Unable to refresh lock state: " + t.getMessage());
            }
        });
    }

    private int calculateSecondsLeft(String lockedUntilStr) {
        long lockedUntilMillis = parseLockedUntilMillis(lockedUntilStr);
        if (lockedUntilMillis <= 0) {
            return 0;
        }

        long diffMs = lockedUntilMillis - System.currentTimeMillis();
        return (int) Math.max(0, (diffMs + 999) / 1000);
    }

    private long parseLockedUntilMillis(String lockedUntilStr) {
        if (lockedUntilStr == null || lockedUntilStr.trim().isEmpty()) {
            return 0;
        }

        String value = lockedUntilStr.trim().replace(' ', 'T');
        try {
            return Instant.parse(value).toEpochMilli();
        } catch (Exception e) {
            try {
                return Instant.parse(value + "Z").toEpochMilli();
            } catch (Exception ignored) {
                Log.e(TAG, "Error parsing locked_until: " + e.getMessage());
            }
        }
        return 0;
    }

    // ==================== INPUT HANDLING ====================

    private void onDigitPressed(int digit) {
        if (isAnimating || isLocked || currentPin.length() >= PIN_LENGTH) {
            return;
        }

        hideError();
        currentPin.append(digit);
        updateDots();
        updateBackspaceVisibility();
        animateDotFill(currentPin.length() - 1);

        if (currentPin.length() == PIN_LENGTH) {
            new Handler(Looper.getMainLooper()).postDelayed(this::onPinComplete, 150);
        }
    }

    private void onBackspacePressed() {
        if (isAnimating || isLocked || currentPin.length() == 0) {
            return;
        }

        int lastIndex = currentPin.length() - 1;
        currentPin.deleteCharAt(lastIndex);
        animateDotEmpty(lastIndex);
        updateBackspaceVisibility();
        hideError();
    }

    private void onPinComplete() {
        String pin = currentPin.toString();
        login(pin);
    }

    // ==================== API CALLS ====================

    private void login(String pin) {
        Log.d(TAG, "Logging in...");
        showLoading(true);

        String deviceId = DeviceDetails.getDeviceId(this);
        String deviceName = DeviceDetails.getDeviceModel();
        Log.e(TAG, "Device_id: " + deviceId);
        Log.e(TAG, "Device_name: " + deviceName);
        LoginRequest request = new LoginRequest(phone, email, pin, deviceId, deviceName);

        ApiClient.getAuthService().login(request).enqueue(new Callback<ApiResponse<LoginData>>() {
            @Override
            public void onResponse(Call<ApiResponse<LoginData>> call, Response<ApiResponse<LoginData>> response) {
                showLoading(false);

                if (response.isSuccessful() && response.body() != null) {
                    handleLoginSuccess(response.body().getData());
                } else {
                    handleLoginError(response);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<LoginData>> call, Throwable t) {
                showLoading(false);
                Log.e(TAG, "Network error: " + t.getMessage());
                showErrorWithShake("Eroare de conexiune");
            }
        });
    }

    private void handleLoginSuccess(LoginData loginData) {
        Log.d(TAG, "Login successful!");

        // Salvează credențialele pentru biometric dacă e activat
        SharedPreferences prefs = getSharedPreferences("SwiftBankSettings", MODE_PRIVATE);
        boolean biometricSettingEnabled = prefs.getBoolean("biometric_enabled", false);
        if (biometricSettingEnabled && currentPin.length() == PIN_LENGTH) {
            BiometricCredentialsManager.getInstance(this)
                    .saveCredentials(phone, email, currentPin.toString());
            Log.d(TAG, "Credentials saved for biometric");
        }

        animateSuccess(() -> {
            // Salvează tokens
            AuthTokenManager.getInstance(this).saveTokens(
                    loginData.getAccessToken(),
                    loginData.getRefreshToken()
            );

            // Înregistrează dispozitivul pentru notificări push
            com.example.swiftbank.managers.FCMTokenManager.getInstance(this).registerDevice();

            // Verifică dacă există plată pending de confirmat
            Intent pendingPayment = CardPaymentApprovalActivity.getPendingPaymentIntent(this);
            if (pendingPayment != null) {
                CardPaymentApprovalActivity.clearPendingPayment(this);
                pendingPayment.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(pendingPayment);
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                finish();
                return;
            }

            // Navighează la Dashboard cu animație
            Intent intent = new Intent(this, DashboardActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            finish();
        });
    }

    private void handleLoginError(Response<ApiResponse<LoginData>> response) {
        ApiErrorResponse error = ErrorParser.parseError(response);

        if (error == null || error.getError() == null) {
            showErrorWithShake("Autentificare eșuată");
            return;
        }

        ErrorData errorData = error.getError();
        String code = errorData.getCode();

        switch (code) {
            case "INVALID_PIN":
                if (errorData instanceof AttemptsErrorData) {
                    int attemptsLeft = ((AttemptsErrorData) errorData).getAttemptsLeft();
                    if (attemptsLeft > 0) {
                        showErrorWithShake("PIN incorect. Încercări rămase: " + attemptsLeft);
                    }
                    else {
                        showErrorWithShake("PIN incorect");
                    }
                } else {
                    showErrorWithShake("PIN incorect");
                }
                break;

            case "ACCOUNT_LOCKED":
                if (errorData instanceof LoginCooldownErrorData) {
                    LoginCooldownErrorData cooldownData = (LoginCooldownErrorData) errorData;
                    int remainingSeconds = calculateSecondsLeft(cooldownData.getLockedUntil());
                    if (remainingSeconds <= 0) {
                        remainingSeconds = cooldownData.getSecondsLeft();
                    }
                    if (remainingSeconds > 0) {
                        startLockTimer(remainingSeconds);
                    }
                    else {
                        showErrorWithShake("Contul este blocat temporar");
                    }

                } else {
                    showErrorWithShake("Contul este blocat temporar");
                }
                break;

            case "ACCOUNT_BLOCKED":
                goToBlockedPage();
                break;

            case "INVALID_CREDENTIALS":
                showErrorWithShake("Date incorecte");
                break;

            case "MISSING_DEVICE_ID":
                showErrorWithShake("Eroare dispozitiv");
                break;

            default:
                showErrorWithShake(errorData.getMessage());
                break;
        }
    }

    // ==================== NAVIGATION ====================

    private void goToBlockedPage() {
        Intent intent = new Intent(this, BlockedUserActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ==================== FORGOT PIN ====================

    private void showForgotPinDialog() {
        new SwiftBankDialog(this)
                .setIcon(R.drawable.ic_block)
                .setTitle("Resetare PIN")
                .setMessage("Vei primi un cod SMS pentru a-ți reseta PIN-ul.")
                .setPrimaryButton("Trimite codul", v -> requestPinReset())
                .setSecondaryButton("Anulează", null)
                .show();
    }

    private void requestPinReset() {
        showLoading(true);

        ForgotPinRequest request;
        if (phone != null && !phone.isEmpty()) {
            request = ForgotPinRequest.withPhone(phone);
        } else if (email != null && !email.isEmpty()) {
            request = ForgotPinRequest.withEmail(email);
        } else {
            showLoading(false);
            SwiftBankDialog.showErrorDialog(this, "Date insuficiente pentru resetare");
            return;
        }

        ApiClient.getAuthService().forgotPin(request).enqueue(new Callback<ApiResponse<ForgotPinData>>() {
            @Override
            public void onResponse(Call<ApiResponse<ForgotPinData>> call, Response<ApiResponse<ForgotPinData>> response) {
                showLoading(false);

                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    ForgotPinData data = response.body().getData();
                    String maskedPhone = data != null ? data.getMaskedPhone() : "telefonul tău";
                    navigateToResetPinOtp(maskedPhone);
                } else {
                    handleForgotPinError(response);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<ForgotPinData>> call, Throwable t) {
                showLoading(false);
                SwiftBankDialog.showNoNetworkDialog(LoginPinActivity.this, v -> requestPinReset());
            }
        });
    }

    private void handleForgotPinError(Response<ApiResponse<ForgotPinData>> response) {
        ApiErrorResponse error = ErrorParser.parseError(response);

        if (error != null && error.getError() != null) {
            String code = error.getError().getCode();

            if ("OTP_COOLDOWN".equals(code)) {
                SwiftBankDialog.showErrorDialog(this, "Așteaptă puțin înainte de a cere un nou cod.");
            } else if ("ACCOUNT_BLOCKED".equals(code)) {
                SwiftBankDialog.showErrorDialog(this, "Contul este blocat. Contactează suportul.");
            } else {
                SwiftBankDialog.showErrorDialog(this, error.getError().getMessage());
            }
        } else {
            SwiftBankDialog.showErrorDialog(this, "Eroare la trimiterea codului");
        }
    }

    private void navigateToResetPinOtp(String maskedPhone) {
        Intent intent = new Intent(this, ResetPinOtpActivity.class);
        intent.putExtra("phone", phone);
        intent.putExtra("email", email);
        intent.putExtra("first_name", firstName);
        intent.putExtra("masked_phone", maskedPhone);
        startActivity(intent);
    }

    // ==================== BIOMETRIC ====================

    private void checkBiometricAvailability() {
        // Verifică setarea din SharedPreferences
        SharedPreferences prefs = getSharedPreferences("SwiftBankSettings", MODE_PRIVATE);
        boolean biometricSettingEnabled = prefs.getBoolean("biometric_enabled", false);

        // Verifică dacă avem credențiale salvate
        BiometricCredentialsManager credentialsManager = BiometricCredentialsManager.getInstance(this);
        boolean hasCredentials = credentialsManager.hasCredentials();

        // Verifică dacă dispozitivul suportă biometrie
        BiometricManager biometricManager = BiometricManager.from(this);
        int canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);
        boolean biometricAvailable = (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS);

        biometricEnabled = biometricSettingEnabled && hasCredentials && biometricAvailable;

        if (biometricEnabled) {
            btnBiometric.setVisibility(View.VISIBLE);
            // Arată automat prompt-ul biometric la deschiderea ecranului
            new Handler(Looper.getMainLooper()).postDelayed(this::showBiometricPrompt, 300);
        } else {
            btnBiometric.setVisibility(View.GONE);
        }

        Log.d(TAG, "Biometric - setting: " + biometricSettingEnabled +
                   ", credentials: " + hasCredentials +
                   ", available: " + biometricAvailable +
                   ", enabled: " + biometricEnabled);
    }

    private void setupBiometric() {
        biometricPrompt = new BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        // Utilizatorul a anulat sau a apărut o eroare - nu facem nimic, poate folosi PIN
                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                            errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            Log.e(TAG, "Biometric error: " + errString);
                        }
                    }

                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        Log.d(TAG, "Biometric authentication succeeded");
                        handleBiometricSuccess();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        Log.d(TAG, "Biometric authentication failed");
                    }
                });

        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Autentificare SwiftBank")
                .setSubtitle("Folosește amprenta pentru a te conecta")
                .setNegativeButtonText("Folosește PIN")
                .build();
    }

    private void showBiometricPrompt() {
        if (biometricPrompt != null && promptInfo != null && !isLocked) {
            biometricPrompt.authenticate(promptInfo);
        }
    }

    private void handleBiometricSuccess() {
        BiometricCredentialsManager credentialsManager = BiometricCredentialsManager.getInstance(this);
        String storedPin = credentialsManager.getPin();

        if (storedPin != null && !storedPin.isEmpty()) {
            // Animează dots ca și cum ar fi fost introduse
            for (int i = 0; i < PIN_LENGTH && i < storedPin.length(); i++) {
                final int index = i;
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (index < dots.length) {
                        dots[index].setBackgroundResource(R.drawable.pin_dot_filled);
                        animateDotFill(index);
                    }
                }, i * 50L);
            }

            // După animație, fă login
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                currentPin.setLength(0);
                currentPin.append(storedPin);
                login(storedPin);
            }, PIN_LENGTH * 50L + 100);
        } else {
            // Credențiale lipsă - forțează login cu PIN
            showError("Te rugăm să te autentifici cu PIN");
            btnBiometric.setVisibility(View.GONE);
            biometricEnabled = false;
        }
    }

    // ==================== LOCK TIMER ====================

    private void startLockTimer(int seconds) {
        isLocked = true;
        setNumpadEnabled(false);
        resetPinInput();

        // Setează dots roșii
        for (View dot : dots) {
            dot.setBackgroundResource(R.drawable.pin_dot_error);
        }

        lockTimer = new CountDownTimer(seconds * 1000L, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsRemaining = (int) (millisUntilFinished / 1000);
                int minutes = secondsRemaining / 60;
                int secs = secondsRemaining % 60;

                String timeText;
                if (minutes > 0) {
                    timeText = String.format(Locale.getDefault(),
                            "Cont blocat. Reîncearcă în %d:%02d", minutes, secs);
                } else {
                    timeText = String.format(Locale.getDefault(),
                            "Cont blocat. Reîncearcă în %d secunde", secs);
                }
                tvError.setText(timeText);
                tvError.setVisibility(View.VISIBLE);
            }

            @Override
            public void onFinish() {
                isLocked = false;
                setNumpadEnabled(true);
                resetPinInput();
                hideError();
            }
        }.start();
    }

    // ==================== ANIMATIONS ====================

    private void showErrorWithShake(String message) {
        isAnimating = true;

        // Setează dots roșii
        for (View dot : dots) {
            dot.setBackgroundResource(R.drawable.pin_dot_error);
        }

        // Shake animation
        ObjectAnimator shake = ObjectAnimator.ofFloat(dotsContainer, "translationX",
                0, 25, -25, 25, -25, 15, -15, 6, -6, 0);
        shake.setDuration(400);

        shake.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                showError(message);

                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    resetPinInput();
                    isAnimating = false;
                }, 500);
            }
        });

        shake.start();
    }

    private void animateSuccess(Runnable onComplete) {
        isAnimating = true;

        AnimatorSet pulseSet = new AnimatorSet();
        for (int i = 0; i < dots.length; i++) {
            View dot = dots[i];

            ObjectAnimator scaleX = ObjectAnimator.ofFloat(dot, "scaleX", 1f, 1.5f, 1f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(dot, "scaleY", 1f, 1.5f, 1f);
            ObjectAnimator bounceUp = ObjectAnimator.ofFloat(dot, "translationY", 0f, -20f, 0f);

            scaleX.setStartDelay(i * 70L);
            scaleY.setStartDelay(i * 70L);
            bounceUp.setStartDelay(i * 70L);

            pulseSet.playTogether(scaleX, scaleY, bounceUp);
        }
        pulseSet.setDuration(400);

        pulseSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    isAnimating = false;
                    onComplete.run();
                }, 300);
            }
        });

        pulseSet.start();
    }

    private void animateDotFill(int index) {
        if (index < 0 || index >= dots.length) return;

        View dot = dots[index];
        dot.setScaleX(0.3f);
        dot.setScaleY(0.3f);

        ObjectAnimator scaleX = ObjectAnimator.ofFloat(dot, "scaleX", 0.3f, 1.3f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(dot, "scaleY", 0.3f, 1.3f, 1f);

        AnimatorSet animSet = new AnimatorSet();
        animSet.playTogether(scaleX, scaleY);
        animSet.setDuration(250);
        animSet.setInterpolator(new OvershootInterpolator(2f));
        animSet.start();
    }

    private void animateDotEmpty(int index) {
        if (index < 0 || index >= dots.length) return;

        View dot = dots[index];
        dot.setBackgroundResource(R.drawable.pin_dot_empty);

        ObjectAnimator scaleX = ObjectAnimator.ofFloat(dot, "scaleX", 1f, 0.8f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(dot, "scaleY", 1f, 0.8f, 1f);

        AnimatorSet animSet = new AnimatorSet();
        animSet.playTogether(scaleX, scaleY);
        animSet.setDuration(100);
        animSet.start();
    }

    // ==================== UI HELPERS ====================

    private void updateDots() {
        for (int i = 0; i < PIN_LENGTH; i++) {
            if (i < currentPin.length()) {
                dots[i].setBackgroundResource(R.drawable.pin_dot_filled);
            } else {
                dots[i].setBackgroundResource(R.drawable.pin_dot_empty);
            }
        }
    }

    private void resetDots() {
        for (View dot : dots) {
            dot.setBackgroundResource(R.drawable.pin_dot_empty);
            dot.setScaleX(1f);
            dot.setScaleY(1f);
        }
    }

    private void updateBackspaceVisibility() {
        if (currentPin.length() > 0) {
            if (btnBackspace.getAlpha() == 0f) {
                // Fade in animation
                btnBackspace.setEnabled(true);
                btnBackspace.animate()
                        .alpha(1f)
                        .setDuration(150)
                        .start();
            }
        } else {
            if (btnBackspace.getAlpha() == 1f) {
                // Fade out animation
                btnBackspace.animate()
                        .alpha(0f)
                        .setDuration(150)
                        .withEndAction(() -> btnBackspace.setEnabled(false))
                        .start();
            }
        }
    }

    private void resetPinInput() {
        currentPin.setLength(0);
        resetDots();
        updateBackspaceVisibility();
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        tvError.setVisibility(View.GONE);
    }

    private void showLoading(boolean show) {
        isLoading = show;
        setNumpadEnabled(!show);

        if (show) {
            startBouncingAnimation();
        } else {
            stopBouncingAnimation();
        }
    }

    private void setNumpadEnabled(boolean enabled) {
        for (TextView btn : numpadButtons) {
            btn.setEnabled(enabled);
            btn.setAlpha(enabled ? 1f : 0.5f);
        }
        btnBackspace.setEnabled(enabled && currentPin.length() > 0);
        btnBackspace.setAlpha(enabled && currentPin.length() > 0 ? 1f : 0f);
    }

    private void startBouncingAnimation() {
        if (bouncingAnimator != null && bouncingAnimator.isRunning()) {
            return;
        }

        bouncingAnimator = new AnimatorSet();
        long duration = 400;
        long delayBetweenDots = 80;

        for (int i = 0; i < dots.length; i++) {
            View dot = dots[i];

            ObjectAnimator bounceUp = ObjectAnimator.ofFloat(dot, "translationY", 0f, -30f);
            bounceUp.setDuration(duration / 2);

            ObjectAnimator bounceDown = ObjectAnimator.ofFloat(dot, "translationY", -30f, 0f);
            bounceDown.setDuration(duration / 2);

            AnimatorSet dotBounce = new AnimatorSet();
            dotBounce.playSequentially(bounceUp, bounceDown);
            dotBounce.setStartDelay(i * delayBetweenDots);

            bouncingAnimator.playTogether(dotBounce);
        }

        bouncingAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (isLoading) {
                    bouncingAnimator.setStartDelay(100);
                    bouncingAnimator.start();
                }
            }
        });

        bouncingAnimator.start();
    }

    private void stopBouncingAnimation() {
        if (bouncingAnimator != null) {
            bouncingAnimator.cancel();
            bouncingAnimator = null;
        }

        for (View dot : dots) {
            dot.setTranslationY(0f);
        }
    }

    // ==================== LIFECYCLE ====================

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopBouncingAnimation();
        if (lockTimer != null) {
            lockTimer.cancel();
        }
        if (particlesView != null) {
            particlesView.stopAnimation();
        }
    }
}
