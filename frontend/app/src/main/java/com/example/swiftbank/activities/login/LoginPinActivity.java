package com.example.swiftbank.activities.login;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
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

import androidx.appcompat.app.AppCompatActivity;

import com.example.swiftbank.R;
import com.example.swiftbank.activities.dashboard.DashboardActivity;
import com.example.swiftbank.api.ApiClient;
import com.example.swiftbank.api.dto.request.LoginRequest;
import com.example.swiftbank.api.dto.response.ApiErrorResponse;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.LoginData;
import com.example.swiftbank.api.dto.response.data.error.AttemptsErrorData;
import com.example.swiftbank.api.dto.response.data.error.ErrorData;
import com.example.swiftbank.api.dto.response.data.error.LoginCooldownErrorData;
import com.example.swiftbank.storage.TokenManager;
import com.example.swiftbank.utils.DeviceDetails;
import com.example.swiftbank.utils.ErrorParser;
import com.example.swiftbank.views.ParticlesView;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

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
    private CountDownTimer lockTimer;

    // Data from Intent
    private String phone;
    private String email;
    private String firstName;
    private String lockedUntil;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin);

        getIntentData();
        initViews();
        setupNumpad();
        setupListeners();
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

        // Afișează elementele de login
        tvForgotPin.setVisibility(View.VISIBLE);

        // Biometric - ascuns deocamdată (se activează din settings)
        btnBiometric.setVisibility(View.GONE);

        // Inițializează dots
        dots = new View[PIN_LENGTH];
        dots[0] = findViewById(R.id.dot1);
        dots[1] = findViewById(R.id.dot2);
        dots[2] = findViewById(R.id.dot3);
        dots[3] = findViewById(R.id.dot4);
        dots[4] = findViewById(R.id.dot5);
        dots[5] = findViewById(R.id.dot6);

        btnBackspace = findViewById(R.id.btnBackspace);
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
        btnBack.setOnClickListener(v -> finish());

        tvForgotPin.setOnClickListener(v -> {
            // TODO: Implementează recuperare PIN
            Log.d(TAG, "Forgot PIN clicked");
        });

        btnBiometric.setOnClickListener(v -> {
            // TODO: Implementează autentificare biometrică
            Log.d(TAG, "Biometric clicked");
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
        if (lockedUntil != null && !lockedUntil.isEmpty()) {
            int secondsLeft = calculateSecondsLeft(lockedUntil);
            if (secondsLeft > 0) {
                startLockTimer(secondsLeft);
            }
        }
    }

    private int calculateSecondsLeft(String lockedUntilStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

            Date lockedUntilDate = sdf.parse(lockedUntilStr);
            Date now = new Date();

            if (lockedUntilDate != null) {
                long diffMs = lockedUntilDate.getTime() - now.getTime();
                int secondsLeft = (int) (diffMs / 1000);
                return Math.max(0, secondsLeft);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing locked_until: " + e.getMessage());
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

        animateSuccess(() -> {
            // Salvează tokens
            TokenManager.getInstance(this).saveTokens(
                    loginData.getAccessToken(),
                    loginData.getRefreshToken()
            );

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
                    System.out.println(((LoginCooldownErrorData) errorData).getLockedUntil());
                    Instant lockedUntilInstant = Instant.parse(((LoginCooldownErrorData) errorData).getLockedUntil());
                    long remainingMs = lockedUntilInstant.toEpochMilli() - System.currentTimeMillis();
                    int remainingSeconds = (int) Math.max(0, (remainingMs + 999) / 1000);
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

    private void setNumpadEnabled(boolean enabled) {
        for (TextView btn : numpadButtons) {
            btn.setEnabled(enabled);
            btn.setAlpha(enabled ? 1f : 0.5f);
        }
        btnBackspace.setEnabled(enabled);
        btnBackspace.setAlpha(enabled ? 1f : 0.5f);
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
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(dot, "scaleX", 1f, 1.3f, 1f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(dot, "scaleY", 1f, 1.3f, 1f);
            scaleX.setStartDelay(i * 50L);
            scaleY.setStartDelay(i * 50L);
            pulseSet.playTogether(scaleX, scaleY);
        }
        pulseSet.setDuration(300);

        pulseSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    isAnimating = false;
                    onComplete.run();
                }, 200);
            }
        });

        pulseSet.start();
    }

    private void animateDotFill(int index) {
        if (index < 0 || index >= dots.length) return;

        View dot = dots[index];
        dot.setScaleX(0.5f);
        dot.setScaleY(0.5f);

        ObjectAnimator scaleX = ObjectAnimator.ofFloat(dot, "scaleX", 0.5f, 1.2f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(dot, "scaleY", 0.5f, 1.2f, 1f);

        AnimatorSet animSet = new AnimatorSet();
        animSet.playTogether(scaleX, scaleY);
        animSet.setDuration(150);
        animSet.setInterpolator(new OvershootInterpolator());
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
            btnBackspace.setAlpha(1f);
            btnBackspace.setEnabled(true);
        } else {
            btnBackspace.setAlpha(0f);
            btnBackspace.setEnabled(false);
        }
    }

    private void resetPinInput() {
        currentPin.setLength(0);
        resetDots();
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        tvError.setVisibility(View.GONE);
    }

    private void showLoading(boolean show) {
        loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    // ==================== LIFECYCLE ====================

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (lockTimer != null) {
            lockTimer.cancel();
        }
        if (particlesView != null) {
            particlesView.stopAnimation();
        }
    }
}