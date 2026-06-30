package com.example.swiftbank.activities.login;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
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
import com.example.swiftbank.api.ApiClient;
import com.example.swiftbank.api.dto.request.ResetPinRequest;
import com.example.swiftbank.api.dto.response.ApiErrorResponse;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.error.ErrorParser;
import com.example.swiftbank.utils.SwiftBankDialog;
import com.example.swiftbank.views.ParticlesView;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ResetPinSetActivity extends AppCompatActivity {

    private static final String TAG = "ResetPinSetActivity";
    private static final int PIN_LENGTH = 6;
    private static final int ANIMATION_DURATION = 300;

    private enum PinState {
        CREATE,
        CONFIRM
    }

    // Views
    private ImageView btnBack;
    private TextView tvTitle;
    private TextView tvSubtitle;
    private LinearLayout dotsContainer;
    private View[] dots;
    private TextView tvError;
    private View loadingOverlay;
    private ParticlesView particlesView;

    // Numpad buttons
    private TextView[] numpadButtons;
    private ImageView btnBackspace;

    // State
    private PinState currentState = PinState.CREATE;
    private StringBuilder currentPin = new StringBuilder();
    private String firstPin = "";
    private boolean isAnimating = false;
    private boolean isLoading = false;
    private AnimatorSet bouncingAnimator;

    // Data from Intent
    private String phone;
    private String email;
    private String firstName;
    private String otpCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin);

        getIntentData();
        initViews();
        setupListeners();
        updateUI();
    }

    private void getIntentData() {
        phone = getIntent().getStringExtra("phone");
        email = getIntent().getStringExtra("email");
        firstName = getIntent().getStringExtra("first_name");
        otpCode = getIntent().getStringExtra("otp_code");
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        tvTitle = findViewById(R.id.tvTitle);
        tvSubtitle = findViewById(R.id.tvSubtitle);
        dotsContainer = findViewById(R.id.dotsContainer);
        tvError = findViewById(R.id.tvError);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        particlesView = findViewById(R.id.particlesView);

        // Ascunde elementele de login
        findViewById(R.id.btnBiometric).setVisibility(View.GONE);
        findViewById(R.id.tvForgotPin).setVisibility(View.INVISIBLE);

        // Initializare dots
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
        setupNumpad();
    }

    @Override
    public void onBackPressed() {
        handleBackPress();
    }

    private void handleBackPress() {
        if (isAnimating) return;

        if (currentState == PinState.CONFIRM) {
            // De la confirmare -> inapoi la introducere PIN cu datele pastrate
            resetToCreateStateWithPin();
        } else {
            // De la introducere PIN -> inapoi la OTP
            finish();
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

        if (currentState == PinState.CREATE) {
            firstPin = pin;
            transitionToConfirmState();
        } else {
            if (pin.equals(firstPin)) {
                onPinConfirmed(pin);
            } else {
                onPinMismatch();
            }
        }
    }

    private void transitionToConfirmState() {
        isAnimating = true;

        AnimatorSet fadeOut = new AnimatorSet();
        ObjectAnimator titleOut = ObjectAnimator.ofFloat(tvTitle, "alpha", 1f, 0f);
        ObjectAnimator subtitleOut = ObjectAnimator.ofFloat(tvSubtitle, "alpha", 1f, 0f);
        ObjectAnimator dotsOut = ObjectAnimator.ofFloat(dotsContainer, "alpha", 1f, 0f);
        fadeOut.playTogether(titleOut, subtitleOut, dotsOut);
        fadeOut.setDuration(ANIMATION_DURATION);

        fadeOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                currentState = PinState.CONFIRM;
                currentPin.setLength(0);
                updateUI();
                resetDots();
                updateBackspaceVisibility();

                AnimatorSet fadeIn = new AnimatorSet();
                ObjectAnimator titleIn = ObjectAnimator.ofFloat(tvTitle, "alpha", 0f, 1f);
                ObjectAnimator subtitleIn = ObjectAnimator.ofFloat(tvSubtitle, "alpha", 0f, 1f);
                ObjectAnimator dotsIn = ObjectAnimator.ofFloat(dotsContainer, "alpha", 0f, 1f);
                fadeIn.playTogether(titleIn, subtitleIn, dotsIn);
                fadeIn.setDuration(ANIMATION_DURATION);

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

        AnimatorSet fadeOut = new AnimatorSet();
        ObjectAnimator titleOut = ObjectAnimator.ofFloat(tvTitle, "alpha", 1f, 0f);
        ObjectAnimator subtitleOut = ObjectAnimator.ofFloat(tvSubtitle, "alpha", 1f, 0f);
        ObjectAnimator dotsOut = ObjectAnimator.ofFloat(dotsContainer, "alpha", 1f, 0f);
        fadeOut.playTogether(titleOut, subtitleOut, dotsOut);
        fadeOut.setDuration(ANIMATION_DURATION);

        fadeOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                currentState = PinState.CREATE;
                currentPin.setLength(0);
                firstPin = "";
                updateUI();
                resetDots();
                updateBackspaceVisibility();

                AnimatorSet fadeIn = new AnimatorSet();
                ObjectAnimator titleIn = ObjectAnimator.ofFloat(tvTitle, "alpha", 0f, 1f);
                ObjectAnimator subtitleIn = ObjectAnimator.ofFloat(tvSubtitle, "alpha", 0f, 1f);
                ObjectAnimator dotsIn = ObjectAnimator.ofFloat(dotsContainer, "alpha", 0f, 1f);
                fadeIn.playTogether(titleIn, subtitleIn, dotsIn);
                fadeIn.setDuration(ANIMATION_DURATION);

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
        // Revenire la CREATE cu PIN-ul pastrat (dots pline)
        isAnimating = true;

        AnimatorSet fadeOut = new AnimatorSet();
        ObjectAnimator titleOut = ObjectAnimator.ofFloat(tvTitle, "alpha", 1f, 0f);
        ObjectAnimator subtitleOut = ObjectAnimator.ofFloat(tvSubtitle, "alpha", 1f, 0f);
        ObjectAnimator dotsOut = ObjectAnimator.ofFloat(dotsContainer, "alpha", 1f, 0f);
        fadeOut.playTogether(titleOut, subtitleOut, dotsOut);
        fadeOut.setDuration(ANIMATION_DURATION);

        fadeOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                currentState = PinState.CREATE;
                // Restaureaza PIN-ul din firstPin
                currentPin.setLength(0);
                currentPin.append(firstPin);
                updateUI();
                // Afiseaza dots pline
                for (int i = 0; i < PIN_LENGTH; i++) {
                    dots[i].setBackgroundResource(R.drawable.pin_dot_filled);
                    dots[i].setScaleX(1f);
                    dots[i].setScaleY(1f);
                }
                updateBackspaceVisibility();

                AnimatorSet fadeIn = new AnimatorSet();
                ObjectAnimator titleIn = ObjectAnimator.ofFloat(tvTitle, "alpha", 0f, 1f);
                ObjectAnimator subtitleIn = ObjectAnimator.ofFloat(tvSubtitle, "alpha", 0f, 1f);
                ObjectAnimator dotsIn = ObjectAnimator.ofFloat(dotsContainer, "alpha", 0f, 1f);
                fadeIn.playTogether(titleIn, subtitleIn, dotsIn);
                fadeIn.setDuration(ANIMATION_DURATION);

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

    private void onPinConfirmed(String pin) {
        Log.d(TAG, "PIN confirmed, resetting...");
        showLoading(true);

        ResetPinRequest request;
        if (phone != null && !phone.isEmpty()) {
            request = ResetPinRequest.withPhone(phone, otpCode, pin);
        } else {
            request = ResetPinRequest.withEmail(email, otpCode, pin);
        }

        ApiClient.getAuthService().resetPin(request).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                showLoading(false);

                if (response.isSuccessful()) {
                    handleResetSuccess();
                } else {
                    handleResetError(response);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                showLoading(false);
                Log.e(TAG, "Network error: " + t.getMessage());
                SwiftBankDialog.showNoNetworkDialog(ResetPinSetActivity.this, v -> onPinConfirmed(pin));
            }
        });
    }

    private void handleResetSuccess() {
        Log.d(TAG, "PIN reset successful!");

        animateSuccess(() -> {
            SwiftBankDialog.showSuccessDialog(this,
                    "PIN resetat!",
                    "Acum te poti autentifica cu noul PIN.",
                    v -> {
                        Intent intent = new Intent(this, LoginPinActivity.class);
                        intent.putExtra("phone", phone);
                        intent.putExtra("email", email);
                        intent.putExtra("first_name", firstName);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                        finish();
                    });
        });
    }

    private void handleResetError(Response<ApiResponse<Void>> response) {
        ApiErrorResponse error = ErrorParser.parseError(response);

        if (error != null && error.getError() != null) {
            String code = error.getError().getCode();

            if ("INVALID_OTP".equals(code) || "OTP_EXPIRED".equals(code)) {
                SwiftBankDialog.showErrorDialog(this, "Codul a expirat. Te rugam sa incerci din nou.");
                // Inapoi la login
                Intent intent = new Intent(this, LoginPinActivity.class);
                intent.putExtra("phone", phone);
                intent.putExtra("email", email);
                intent.putExtra("first_name", firstName);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
            } else {
                SwiftBankDialog.showErrorDialog(this, error.getError().getMessage());
                resetToCreateState();
            }
        } else {
            SwiftBankDialog.showErrorDialog(this, "Eroare la resetarea PIN-ului");
            resetToCreateState();
        }
    }

    private void animateSuccess(Runnable onComplete) {
        isAnimating = true;

        AnimatorSet pulseSet = new AnimatorSet();
        for (int i = 0; i < dots.length; i++) {
            View dot = dots[i];

            // Scale up and down
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(dot, "scaleX", 1f, 1.5f, 1f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(dot, "scaleY", 1f, 1.5f, 1f);

            // Bounce up
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

    private void updateUI() {
        if (currentState == PinState.CREATE) {
            tvTitle.setText("Noul PIN");
            tvSubtitle.setText("Alege 6 cifre pentru noul tau PIN");
        } else {
            tvTitle.setText("Confirma PIN-ul");
            tvSubtitle.setText("Reintrodu cele 6 cifre");
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
                    // Repeta animatia
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

        // Reseteaza pozitia dots
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
