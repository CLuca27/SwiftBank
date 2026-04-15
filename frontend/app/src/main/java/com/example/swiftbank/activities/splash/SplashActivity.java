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
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.splashscreen.SplashScreen;

import com.example.swiftbank.R;
import com.example.swiftbank.activities.login.LoginPinActivity;
import com.example.swiftbank.activities.welcome.WelcomeActivity;
import com.example.swiftbank.api.ApiClient;
import com.example.swiftbank.api.dto.request.RefreshRequest;
import com.example.swiftbank.api.dto.response.ApiErrorResponse;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.RefreshData;
import com.example.swiftbank.utils.ErrorParser;
import com.example.swiftbank.utils.SwiftBankDialog;
import com.example.swiftbank.storage.AuthTokenManager;
import com.example.swiftbank.views.ParticlesView;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "SplashActivity";
    private static final long MIN_SPLASH_DURATION = 6000;
    private static final long SYSTEM_SPLASH_DURATION = 1500; // Durata splash-ului Android 12+

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
        // Install splash screen before calling super.onCreate()
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);

        super.onCreate(savedInstanceState);

        // Configurare splash screen Android 12+
        setupSplashScreen(splashScreen);

        setContentView(R.layout.activity_splash);

        // Initialize views
        logoCard = findViewById(R.id.logoCard);
        appNameText = findViewById(R.id.appNameText);
        dot1 = findViewById(R.id.dot1);
        dot2 = findViewById(R.id.dot2);
        dot3 = findViewById(R.id.dot3);
        particlesView = findViewById(R.id.particlesView);
        handler = new Handler(Looper.getMainLooper());

        startTime = System.currentTimeMillis();

        // Start animations + check network + auth
        startAnimations();
        checkNetworkAndAuth();
    }

    private void setupSplashScreen(SplashScreen splashScreen) {
        // Păstrează splash-ul vizibil până când flag-ul devine false
        splashScreen.setKeepOnScreenCondition(() -> keepSplashScreen);

        // După SYSTEM_SPLASH_DURATION, ascunde splash-ul cu animație
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            keepSplashScreen = false;
        }, SYSTEM_SPLASH_DURATION);

        // Animație de ieșire - fade out simplu și rapid
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
                // Reset timer și retry
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

        // Nu există refresh token → Welcome
        if (!authTokenManager.hasRefreshToken()) {
            Log.d(TAG, "No refresh token found");
            navigateWithDelay(WelcomeActivity.class);
            return;
        }

        // Există refresh token → încearcă refresh
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

            // Navighează la LoginPinActivity cu datele user-ului
            Intent intent = new Intent(this, LoginPinActivity.class);
            if (data.getUser() != null) {
                intent.putExtra("email", data.getUser().getEmail());
                intent.putExtra("first_name", data.getUser().getFirstName());
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
                // TODO: navigheză la BlockedActivity când e gata
                Log.d(TAG, "Account is blocked");
                navigateWithDelay(WelcomeActivity.class);
                return;
            }
        }

        // Orice altă eroare - clear tokens și Welcome
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
        // Logo fade in + scale
        logoCard.setAlpha(0f);
        logoCard.setScaleX(0.8f);
        logoCard.setScaleY(0.8f);

        logoCard.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(800)
                .setStartDelay(200)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        // App name fade in + slide up
        appNameText.setAlpha(0f);
        appNameText.setTranslationY(30f);

        appNameText.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(800)
                .setStartDelay(500)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        // Loading dots - start after 1 second
        handler.postDelayed(this::startDotsAnimation, 1000);
    }

    private void startDotsAnimation() {
        View[] dots = {dot1, dot2, dot3};

        for (View dot : dots) {
            dot.setAlpha(0.3f);
            dot.setScaleX(0.6f);
            dot.setScaleY(0.6f);
        }

        animateDot(dot1, 0);
        animateDot(dot2, 250);
        animateDot(dot3, 500);
    }

    private void animateDot(View dot, long startDelay) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(dot, "scaleX", 0.6f, 1f, 0.6f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(dot, "scaleY", 0.6f, 1f, 0.6f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(dot, "alpha", 0.3f, 1f, 0.3f);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(scaleX, scaleY, alpha);
        animatorSet.setDuration(1200);
        animatorSet.setStartDelay(startDelay);
        animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());

        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!isFinishing() && !isDestroyed()) {
                    animatorSet.setStartDelay(0);
                    animatorSet.start();
                }
            }
        });

        animatorSet.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        if (particlesView != null) {
            particlesView.stopAnimation();
        }
    }
}