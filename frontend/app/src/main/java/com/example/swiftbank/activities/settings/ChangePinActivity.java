package com.example.swiftbank.activities.settings;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.SharedPreferences;
import android.os.Bundle;
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
import com.example.swiftbank.api.ApiClient;
import com.example.swiftbank.api.dto.request.ChangePinRequest;
import com.example.swiftbank.api.dto.response.ApiErrorResponse;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.managers.BiometricCredentialsManager;
import com.example.swiftbank.api.dto.response.data.error.ErrorParser;
import com.example.swiftbank.utils.SwiftBankDialog;
import com.example.swiftbank.views.ParticlesView;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChangePinActivity extends AppCompatActivity {

    private static final String TAG = "ChangePinActivity";
    private static final int PIN_LENGTH = 6;
    private static final int ANIMATION_DURATION = 300;

    private enum PinState {
        VERIFY_CURRENT,  // Verifică PIN-ul curent
        CREATE,          // Introdu PIN nou
        CONFIRM          // Confirmă PIN nou
    }

    // Views
    private ImageView btnBack;
    private TextView tvTitle;
    private TextView tvSubtitle;
    private LinearLayout dotsContainer;
    private View[] dots;
    private ImageView btnBiometric;
    private TextView tvError;
    private ParticlesView particlesView;

    // Numpad buttons
    private TextView[] numpadButtons;
    private ImageView btnBackspace;

    // State
    private PinState currentState = PinState.VERIFY_CURRENT;
    private StringBuilder currentPin = new StringBuilder();
    private String verifiedCurrentPin = "";
    private String newPin = "";
    private boolean isAnimating = false;
    private boolean isLoading = false;
    private AnimatorSet bouncingAnimator;

    // Biometric
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;
    private boolean biometricAvailable = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin);

        initViews();
        setupNumpad();
        setupListeners();
        setupBiometric();
        updateUI();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        tvTitle = findViewById(R.id.tvTitle);
        tvSubtitle = findViewById(R.id.tvSubtitle);
        dotsContainer = findViewById(R.id.dotsContainer);
        tvError = findViewById(R.id.tvError);
        btnBiometric = findViewById(R.id.btnBiometric);
        particlesView = findViewById(R.id.particlesView);

        // Ascunde forgot pin (nu e relevant aici)
        findViewById(R.id.tvForgotPin).setVisibility(View.GONE);

        // Inițializare dots
        dots = new View[PIN_LENGTH];
        dots[0] = findViewById(R.id.dot1);
        dots[1] = findViewById(R.id.dot2);
        dots[2] = findViewById(R.id.dot3);
        dots[3] = findViewById(R.id.dot4);
        dots[4] = findViewById(R.id.dot5);
        dots[5] = findViewById(R.id.dot6);

        // Numpad
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
        btnBack.setOnClickListener(v -> handleBackPress());

        btnBiometric.setOnClickListener(v -> {
            if (biometricAvailable && currentState == PinState.VERIFY_CURRENT) {
                showBiometricPrompt();
            }
        });
    }

    private void setupBiometric() {
        // Verifică dacă biometria e activată și avem credențiale
        SharedPreferences prefs = getSharedPreferences("SwiftBankSettings", MODE_PRIVATE);
        boolean biometricEnabled = prefs.getBoolean("biometric_enabled", false);

        BiometricCredentialsManager credentialsManager = BiometricCredentialsManager.getInstance(this);
        boolean hasCredentials = credentialsManager.hasCredentials();

        BiometricManager biometricManager = BiometricManager.from(this);
        int canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);
        boolean deviceSupports = (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS);

        biometricAvailable = biometricEnabled && hasCredentials && deviceSupports;

        if (biometricAvailable) {
            btnBiometric.setVisibility(View.VISIBLE);

            biometricPrompt = new BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                            super.onAuthenticationError(errorCode, errString);
                            // Utilizatorul poate folosi PIN
                        }

                        @Override
                        public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                            super.onAuthenticationSucceeded(result);
                            handleBiometricSuccess();
                        }

                        @Override
                        public void onAuthenticationFailed() {
                            super.onAuthenticationFailed();
                        }
                    });

            promptInfo = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Verifică identitatea")
                    .setSubtitle("Folosește amprenta pentru a continua")
                    .setNegativeButtonText("Folosește PIN")
                    .build();

            // Arată prompt automat
            new Handler(Looper.getMainLooper()).postDelayed(this::showBiometricPrompt, 300);
        } else {
            btnBiometric.setVisibility(View.GONE);
        }
    }

    private void showBiometricPrompt() {
        if (biometricPrompt != null && promptInfo != null) {
            biometricPrompt.authenticate(promptInfo);
        }
    }

    private void handleBiometricSuccess() {
        // Obține PIN-ul din credențiale
        BiometricCredentialsManager credentialsManager = BiometricCredentialsManager.getInstance(this);
        String storedPin = credentialsManager.getPin();

        if (storedPin != null && !storedPin.isEmpty()) {
            verifiedCurrentPin = storedPin;

            // Animează dots rapid
            for (int i = 0; i < PIN_LENGTH; i++) {
                final int index = i;
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    dots[index].setBackgroundResource(R.drawable.pin_dot_filled);
                }, i * 30L);
            }

            // Treci la pasul următor
            new Handler(Looper.getMainLooper()).postDelayed(this::transitionToCreateState, PIN_LENGTH * 30L + 200);
        }
    }

    @Override
    public void onBackPressed() {
        handleBackPress();
    }

    private void handleBackPress() {
        if (isAnimating) return;

        switch (currentState) {
            case VERIFY_CURRENT:
                finish();
                break;
            case CREATE:
                // Înapoi la verificare
                resetToVerifyState();
                break;
            case CONFIRM:
                // Înapoi la creare cu PIN-ul păstrat
                resetToCreateStateWithPin();
                break;
        }
    }

    private void onDigitPressed(int digit) {
        if (isAnimating || currentPin.length() >= PIN_LENGTH) {
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
        if (isAnimating || currentPin.length() == 0) {
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

        switch (currentState) {
            case VERIFY_CURRENT:
                verifiedCurrentPin = pin;
                transitionToCreateState();
                break;
            case CREATE:
                newPin = pin;
                transitionToConfirmState();
                break;
            case CONFIRM:
                if (pin.equals(newPin)) {
                    onPinConfirmed();
                } else {
                    onPinMismatch();
                }
                break;
        }
    }

    // ==================== STATE TRANSITIONS ====================

    private void transitionToCreateState() {
        isAnimating = true;
        btnBiometric.setVisibility(View.GONE);

        AnimatorSet fadeOut = createFadeOutAnimation();
        fadeOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                currentState = PinState.CREATE;
                currentPin.setLength(0);
                updateUI();
                resetDots();
                updateBackspaceVisibility();

                AnimatorSet fadeIn = createFadeInAnimation();
                fadeIn.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        isAnimating = false;
                    }
                });
                fadeIn.start();
            }
        });
        fadeOut.start();
    }

    private void transitionToConfirmState() {
        isAnimating = true;

        AnimatorSet fadeOut = createFadeOutAnimation();
        fadeOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                currentState = PinState.CONFIRM;
                currentPin.setLength(0);
                updateUI();
                resetDots();
                updateBackspaceVisibility();

                AnimatorSet fadeIn = createFadeInAnimation();
                fadeIn.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        isAnimating = false;
                    }
                });
                fadeIn.start();
            }
        });
        fadeOut.start();
    }

    private void resetToVerifyState() {
        isAnimating = true;

        AnimatorSet fadeOut = createFadeOutAnimation();
        fadeOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                currentState = PinState.VERIFY_CURRENT;
                currentPin.setLength(0);
                verifiedCurrentPin = "";
                newPin = "";
                updateUI();
                resetDots();
                updateBackspaceVisibility();

                if (biometricAvailable) {
                    btnBiometric.setVisibility(View.VISIBLE);
                }

                AnimatorSet fadeIn = createFadeInAnimation();
                fadeIn.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        isAnimating = false;
                    }
                });
                fadeIn.start();
            }
        });
        fadeOut.start();
    }

    private void resetToCreateState() {
        isAnimating = true;

        AnimatorSet fadeOut = createFadeOutAnimation();
        fadeOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                currentState = PinState.CREATE;
                currentPin.setLength(0);
                newPin = "";
                updateUI();
                resetDots();
                updateBackspaceVisibility();

                AnimatorSet fadeIn = createFadeInAnimation();
                fadeIn.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        isAnimating = false;
                    }
                });
                fadeIn.start();
            }
        });
        fadeOut.start();
    }

    private void resetToCreateStateWithPin() {
        isAnimating = true;

        AnimatorSet fadeOut = createFadeOutAnimation();
        fadeOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                currentState = PinState.CREATE;
                currentPin.setLength(0);
                currentPin.append(newPin);
                updateUI();

                // Afișează dots pline
                for (int i = 0; i < PIN_LENGTH; i++) {
                    dots[i].setBackgroundResource(R.drawable.pin_dot_filled);
                    dots[i].setScaleX(1f);
                    dots[i].setScaleY(1f);
                }
                updateBackspaceVisibility();

                AnimatorSet fadeIn = createFadeInAnimation();
                fadeIn.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        isAnimating = false;
                    }
                });
                fadeIn.start();
            }
        });
        fadeOut.start();
    }

    private AnimatorSet createFadeOutAnimation() {
        AnimatorSet fadeOut = new AnimatorSet();
        ObjectAnimator titleOut = ObjectAnimator.ofFloat(tvTitle, "alpha", 1f, 0f);
        ObjectAnimator subtitleOut = ObjectAnimator.ofFloat(tvSubtitle, "alpha", 1f, 0f);
        ObjectAnimator dotsOut = ObjectAnimator.ofFloat(dotsContainer, "alpha", 1f, 0f);
        fadeOut.playTogether(titleOut, subtitleOut, dotsOut);
        fadeOut.setDuration(ANIMATION_DURATION);
        return fadeOut;
    }

    private AnimatorSet createFadeInAnimation() {
        AnimatorSet fadeIn = new AnimatorSet();
        ObjectAnimator titleIn = ObjectAnimator.ofFloat(tvTitle, "alpha", 0f, 1f);
        ObjectAnimator subtitleIn = ObjectAnimator.ofFloat(tvSubtitle, "alpha", 0f, 1f);
        ObjectAnimator dotsIn = ObjectAnimator.ofFloat(dotsContainer, "alpha", 0f, 1f);
        fadeIn.playTogether(titleIn, subtitleIn, dotsIn);
        fadeIn.setDuration(ANIMATION_DURATION);
        return fadeIn;
    }

    // ==================== PIN VALIDATION ====================

    private void onPinMismatch() {
        isAnimating = true;

        for (View dot : dots) {
            dot.setBackgroundResource(R.drawable.pin_dot_error);
        }

        ObjectAnimator shake = ObjectAnimator.ofFloat(dotsContainer, "translationX",
                0, 25, -25, 25, -25, 15, -15, 6, -6, 0);
        shake.setDuration(400);

        shake.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                showError("Codurile PIN nu coincid");

                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    resetToCreateState();
                }, 800);
            }
        });

        shake.start();
    }

    private void onPinConfirmed() {
        // Verifică că PIN-ul nou e diferit de cel curent
        if (newPin.equals(verifiedCurrentPin)) {
            showErrorWithShake("PIN-ul nou trebuie să fie diferit");
            new Handler(Looper.getMainLooper()).postDelayed(this::resetToCreateState, 800);
            return;
        }

        Log.d(TAG, "PIN confirmed, changing...");
        showLoading(true);

        ChangePinRequest request = new ChangePinRequest(verifiedCurrentPin, newPin);

        ApiClient.getUserService().changePin(request).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                showLoading(false);

                if (response.isSuccessful()) {
                    handleChangeSuccess();
                } else {
                    handleChangeError(response);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                showLoading(false);
                Log.e(TAG, "Network error: " + t.getMessage());
                SwiftBankDialog.showNoNetworkDialog(ChangePinActivity.this, v -> onPinConfirmed());
            }
        });
    }

    private void handleChangeSuccess() {
        Log.d(TAG, "PIN change successful!");

        // Actualizează credențialele biometric dacă sunt activate
        SharedPreferences prefs = getSharedPreferences("SwiftBankSettings", MODE_PRIVATE);
        boolean biometricEnabled = prefs.getBoolean("biometric_enabled", false);

        if (biometricEnabled) {
            BiometricCredentialsManager credentialsManager = BiometricCredentialsManager.getInstance(this);
            String phone = credentialsManager.getPhone();
            String email = credentialsManager.getEmail();
            credentialsManager.saveCredentials(phone, email, newPin);
        }

        animateSuccess(() -> {
            SwiftBankDialog.showSuccessDialog(this,
                    "PIN schimbat!",
                    "Noul tău PIN a fost salvat cu succes.",
                    v -> finish());
        });
    }

    private void handleChangeError(Response<ApiResponse<Void>> response) {
        ApiErrorResponse error = ErrorParser.parseError(response);

        if (error != null && error.getError() != null) {
            String code = error.getError().getCode();

            if ("INVALID_PIN".equals(code) || "WRONG_PIN".equals(code)) {
                showErrorWithShake("PIN-ul curent este incorect");
                new Handler(Looper.getMainLooper()).postDelayed(this::resetToVerifyState, 800);
            } else {
                SwiftBankDialog.showErrorDialog(this, error.getError().getMessage());
                resetToCreateState();
            }
        } else {
            SwiftBankDialog.showErrorDialog(this, "Eroare la schimbarea PIN-ului");
            resetToCreateState();
        }
    }

    // ==================== UI ====================

    private void updateUI() {
        switch (currentState) {
            case VERIFY_CURRENT:
                tvTitle.setText("PIN curent");
                tvSubtitle.setText("Introdu PIN-ul actual pentru verificare");
                break;
            case CREATE:
                tvTitle.setText("PIN nou");
                tvSubtitle.setText("Alege 6 cifre pentru noul PIN");
                break;
            case CONFIRM:
                tvTitle.setText("Confirmă PIN-ul");
                tvSubtitle.setText("Reintrodu cele 6 cifre");
                break;
        }
    }

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
                btnBackspace.setEnabled(true);
                btnBackspace.animate()
                        .alpha(1f)
                        .setDuration(150)
                        .start();
            }
        } else {
            if (btnBackspace.getAlpha() == 1f) {
                btnBackspace.animate()
                        .alpha(0f)
                        .setDuration(150)
                        .withEndAction(() -> btnBackspace.setEnabled(false))
                        .start();
            }
        }
    }

    // ==================== ANIMATIONS ====================

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

    private void showErrorWithShake(String message) {
        isAnimating = true;

        for (View dot : dots) {
            dot.setBackgroundResource(R.drawable.pin_dot_error);
        }

        ObjectAnimator shake = ObjectAnimator.ofFloat(dotsContainer, "translationX",
                0, 25, -25, 25, -25, 15, -15, 6, -6, 0);
        shake.setDuration(400);

        shake.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                showError(message);
                isAnimating = false;
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

    // ==================== LOADING ====================

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopBouncingAnimation();
        if (particlesView != null) {
            particlesView.stopAnimation();
        }
    }
}
