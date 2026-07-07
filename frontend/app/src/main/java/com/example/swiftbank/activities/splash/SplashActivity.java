package com.example.swiftbank.activities.splash;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.splashscreen.SplashScreen;

import com.example.swiftbank.R;
import com.example.swiftbank.activities.login.BlockedUserActivity;
import com.example.swiftbank.activities.login.LoginPinActivity;
import com.example.swiftbank.activities.welcome.WelcomeActivity;
import com.example.swiftbank.api.ApiClient;
import com.example.swiftbank.api.dto.request.RefreshRequest;
import com.example.swiftbank.api.dto.response.ApiErrorResponse;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.success.RefreshData;
import com.example.swiftbank.api.dto.response.data.error.ErrorParser;
import com.example.swiftbank.utils.SwiftBankDialog;
import com.example.swiftbank.managers.AuthTokenManager;
import com.example.swiftbank.views.ParticlesView;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "SplashActivity";
    private static final long MIN_SPLASH_DURATION = 6000;
    private static final long SYSTEM_SPLASH_DURATION = 1000;

    private CardView logoCard;
    private TextView appNameText;
    private View dot1, dot2, dot3;
    private ParticlesView particlesView;
    private Handler handler;

    private long startTime;
    private Intent nextIntent = null;
    private boolean keepSplashScreen = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);

        super.onCreate(savedInstanceState);

        setupSplashScreen(splashScreen);

        setContentView(R.layout.activity_splash);

        logoCard = findViewById(R.id.logoCard);
        appNameText = findViewById(R.id.appNameText);
        dot1 = findViewById(R.id.dot1);
        dot2 = findViewById(R.id.dot2);
        dot3 = findViewById(R.id.dot3);
        particlesView = findViewById(R.id.particlesView);
        handler = new Handler(Looper.getMainLooper());

        startTime = System.currentTimeMillis();

        startAnimations();
        checkNetworkAndAuth();
    }

    private void setupSplashScreen(SplashScreen splashScreen) {
        splashScreen.setKeepOnScreenCondition(() -> keepSplashScreen);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            keepSplashScreen = false;
        }, SYSTEM_SPLASH_DURATION);

        splashScreen.setOnExitAnimationListener(splashScreenView -> {
            splashScreenView.getView()
                    .animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction(splashScreenView::remove)
                    .start();
        });
    }

    private void checkNetworkAndAuth() {
        if (!isNetworkAvailable()) {
            SwiftBankDialog.showNoNetworkDialog(this, v -> {
                startTime = System.currentTimeMillis();
                checkNetworkAndAuth();
            });
            return;
        }
        checkAuthStatus();
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        NetworkCapabilities capabilities = cm.getNetworkCapabilities(cm.getActiveNetwork());
        if (capabilities == null) return false;

        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
    }

    private void checkAuthStatus() {
        AuthTokenManager authTokenManager = AuthTokenManager.getInstance(this);

        if (!authTokenManager.hasRefreshToken()) {
            Log.d(TAG, "No refresh token found");
            navigateWithDelay(WelcomeActivity.class);
            return;
        }

        String refreshToken = authTokenManager.getRefreshToken();
        String deviceId = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ANDROID_ID
        );

        Log.d(TAG, "Attempting refresh with token");
        RefreshRequest request = new RefreshRequest(refreshToken, deviceId);

        ApiClient.getAuthService().refreshToken(request).enqueue(new Callback<ApiResponse<RefreshData>>() {
            @Override
            public void onResponse(Call<ApiResponse<RefreshData>> call, Response<ApiResponse<RefreshData>> response) {
                if (response.isSuccessful() && response.body() != null){
                    handleRefreshSuccess(response.body(), authTokenManager);
                } else {
                    handleRefreshError(response, authTokenManager);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<RefreshData>> call, Throwable t) {
                Log.e(TAG, "Network error: " + t.getMessage());
                SwiftBankDialog.showServerErrorDialog(SplashActivity.this, v -> {
                    startTime = System.currentTimeMillis();
                    checkNetworkAndAuth();
                });
            }
        });
    }

    private void handleRefreshSuccess(ApiResponse<RefreshData> response, AuthTokenManager authTokenManager) {
        RefreshData data = response.getData();

        if (data != null) {
            authTokenManager.saveTokens(data.getAccessToken(), data.getRefreshToken());
            Log.d(TAG, "Refresh successful, tokens saved");

            Intent intent = new Intent(this, LoginPinActivity.class);
            if (data.getUser() != null) {
                intent.putExtra("email", data.getUser().getEmail());
                intent.putExtra("phone", data.getUser().getPhone());
                intent.putExtra("first_name", data.getUser().getFirstName());
                intent.putExtra("locked_until", data.getUser().getLockedUntil());
            }
            navigateToLoginPin(intent);
        } else {
            navigateWithDelay(WelcomeActivity.class);
        }
    }

    private void navigateToLoginPin(Intent intent) {
        long elapsedTime = System.currentTimeMillis() - startTime;
        long remainingTime = MIN_SPLASH_DURATION - elapsedTime;

        if (remainingTime > 0) {
            handler.postDelayed(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    startActivity(intent);
                    finish();
                }
            }, remainingTime);
        } else {
            startActivity(intent);
            finish();
        }
    }

    private void handleRefreshError(Response<ApiResponse<RefreshData>> response, AuthTokenManager authTokenManager) {
        Log.d(TAG, "Refresh failed with code: " + response.code());

        ApiErrorResponse error = ErrorParser.parseError(response);

        if (error != null && error.getError() != null) {
            String errorCode = error.getError().getCode();
            Log.d(TAG, "Error code: " + errorCode);

            if ("ACCOUNT_BLOCKED".equals(errorCode)) {
                Log.d(TAG, "Account is blocked");
                navigateWithDelay(BlockedUserActivity.class);
                return;
            }
        }

        authTokenManager.clearTokens();
        navigateWithDelay(WelcomeActivity.class);
    }

    private void navigateWithDelay(Class<?> activityClass) {
        nextIntent = new Intent(this, activityClass);

        long elapsedTime = System.currentTimeMillis() - startTime;
        long remainingTime = MIN_SPLASH_DURATION - elapsedTime;

        if (remainingTime > 0) {
            handler.postDelayed(this::navigateToNext, remainingTime);
        } else {
            navigateToNext();
        }
    }

    private void navigateToNext() {
        if (nextIntent != null && !isFinishing() && !isDestroyed()) {
            startActivity(nextIntent);
            finish();
        }
    }

    private void startAnimations() {
        logoCard.setAlpha(0f);
        logoCard.setScaleX(0.8f);
        logoCard.setScaleY(0.8f);
        appNameText.setAlpha(0f);
        appNameText.setTranslationY(30f);

        dot1.setAlpha(0f);
        dot2.setAlpha(0f);
        dot3.setAlpha(0f);

        logoCard.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(800)
                .setStartDelay(200)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    appNameText.animate()
                            .alpha(1f)
                            .translationY(0f)
                            .setDuration(500)
                            .setInterpolator(new AccelerateDecelerateInterpolator())
                            .withEndAction(this::startDotsAnimation)
                            .start();
                })
                .start();
    }

    private void startDotsAnimation() {
        ObjectAnimator dot1Fade = ObjectAnimator.ofFloat(dot1, "alpha", 0f, 1f);
        ObjectAnimator dot2Fade = ObjectAnimator.ofFloat(dot2, "alpha", 0f, 1f);
        ObjectAnimator dot3Fade = ObjectAnimator.ofFloat(dot3, "alpha", 0f, 1f);

        dot1Fade.setDuration(350);
        dot2Fade.setDuration(350);
        dot3Fade.setDuration(350);

        dot1Fade.setInterpolator(new AccelerateDecelerateInterpolator());
        dot2Fade.setInterpolator(new AccelerateDecelerateInterpolator());
        dot3Fade.setInterpolator(new AccelerateDecelerateInterpolator());

        dot1Fade.setStartDelay(0);
        dot2Fade.setStartDelay(250);
        dot3Fade.setStartDelay(500);

        AnimatorSet fadeIn = new AnimatorSet();
        fadeIn.playTogether(dot1Fade, dot2Fade, dot3Fade);
        fadeIn.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                startDotsPulseAnimation();
            }
        });
        fadeIn.start();
    }

    private void startDotsPulseAnimation() {
        ObjectAnimator dot1Scale = ObjectAnimator.ofFloat(dot1, "scaleX", 1f, 1.3f, 1f);
        ObjectAnimator dot1ScaleY = ObjectAnimator.ofFloat(dot1, "scaleY", 1f, 1.3f, 1f);
        ObjectAnimator dot2Scale = ObjectAnimator.ofFloat(dot2, "scaleX", 1f, 1.3f, 1f);
        ObjectAnimator dot2ScaleY = ObjectAnimator.ofFloat(dot2, "scaleY", 1f, 1.3f, 1f);
        ObjectAnimator dot3Scale = ObjectAnimator.ofFloat(dot3, "scaleX", 1f, 1.3f, 1f);
        ObjectAnimator dot3ScaleY = ObjectAnimator.ofFloat(dot3, "scaleY", 1f, 1.3f, 1f);

        dot1Scale.setDuration(600);
        dot1ScaleY.setDuration(600);
        dot2Scale.setDuration(600);
        dot2ScaleY.setDuration(600);
        dot3Scale.setDuration(600);
        dot3ScaleY.setDuration(600);

        dot1Scale.setStartDelay(0);
        dot1ScaleY.setStartDelay(0);
        dot2Scale.setStartDelay(200);
        dot2ScaleY.setStartDelay(200);
        dot3Scale.setStartDelay(400);
        dot3ScaleY.setStartDelay(400);

        AnimatorSet pulse = new AnimatorSet();
        pulse.playTogether(dot1Scale, dot1ScaleY, dot2Scale, dot2ScaleY, dot3Scale, dot3ScaleY);
        pulse.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!isFinishing() && !isDestroyed()) {
                    startDotsPulseAnimation();
                }
            }
        });
        pulse.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }
}
