package com.example.swiftbank.activities.cards;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Insets;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowInsets;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.example.swiftbank.R;
import com.example.swiftbank.activities.splash.SplashActivity;
import com.example.swiftbank.api.ApiClient;
import com.example.swiftbank.managers.AuthTokenManager;
import com.example.swiftbank.managers.RealtimeManager;
import com.example.swiftbank.api.dto.request.DeclineCardPaymentRequest;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.success.CardPaymentSessionData;
import com.example.swiftbank.api.dto.response.ApiErrorResponse;
import com.example.swiftbank.api.dto.response.data.error.ErrorParser;
import com.example.swiftbank.utils.BiometricHelper;
import com.example.swiftbank.utils.PinConfirmDialog;
import com.example.swiftbank.utils.RemoteImageLoader;
import com.example.swiftbank.utils.SwiftBankDialog;
import com.google.gson.JsonObject;

import android.content.SharedPreferences;
import android.content.Intent;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CardPaymentApprovalActivity extends AppCompatActivity {

    private static final long AUTO_CLOSE_DELAY_MS = 1200;
    private static final long SESSION_DURATION_MS = 5 * 60 * 1000; // 5 minute
    private static final long SESSION_FALLBACK_REFRESH_MS = 30000;

    public static final String EXTRA_SESSION_ID = "session_id";
    public static final String EXTRA_MERCHANT_NAME = "merchant_name";
    public static final String EXTRA_MERCHANT_LOCATION = "merchant_location";
    public static final String EXTRA_AMOUNT = "amount";
    public static final String EXTRA_CURRENCY = "currency";
    public static final String EXTRA_MASKED_CARD = "masked_card";
    public static final String EXTRA_EXPIRES_AT = "expires_at";

    private ImageView btnBack;
    private View rootContainer, headerContainer;
    private View cardNumberRow;
    private View loadingState;
    private ScrollView contentContainer;
    private LinearLayout buttonContainer;
    private TextView tvMerchantName, tvLocation, tvAmount, tvCountdown, tvStatus, tvTimerLabel;
    private TextView tvCardNumberMasked;
    private Button btnApprove, btnDecline;
    private ProgressBar progressCircle;
    private CardView merchantIconContainer;
    private ImageView ivMerchantLogo, ivCategoryIcon;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final DecimalFormat amountFormat = createAmountFormat();

    private int sessionId;
    private long expiresAtMillis = 0;
    private long serverClockOffsetMs = 0;
    private boolean actionInProgress = false;
    private int lastColorState = 0;
    private RealtimeManager.RealtimeListener realtimeListener;
    private Call<ApiResponse<CardPaymentSessionData>> sessionStatusCall;

    private final Runnable countdownRunnable = new Runnable() {
        @Override
        public void run() {
            updateCountdown();
            handler.postDelayed(this, 500);
        }
    };

    private final Runnable fallbackRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            loadSession(false);
            handler.postDelayed(this, SESSION_FALLBACK_REFRESH_MS);
        }
    };

    private final Runnable autoCloseRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isFinishing() && !isDestroyed()) {
                setResult(RESULT_OK);
                finish();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Verifică autentificarea înainte de a procesa plata
        if (!AuthTokenManager.getInstance(this).hasValidToken()) {
            savePendingPayment();
            redirectToLogin();
            return;
        }

        setContentView(R.layout.activity_card_payment_approval);

        sessionId = getIntent().getIntExtra(EXTRA_SESSION_ID, -1);
        if (sessionId == -1) {
            sessionId = parseInt(getIntent().getStringExtra(EXTRA_SESSION_ID), -1);
        }

        initViews();
        setupListeners();
        hydrateFromIntent();

        if (sessionId == -1) {
            Toast.makeText(this, "Plata invalida", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupRealtimeSubscription();
        loadSession(true);
    }

    private void savePendingPayment() {
        Intent intent = getIntent();
        SharedPreferences prefs = getSharedPreferences("PendingPayment", MODE_PRIVATE);
        prefs.edit()
                .putInt("session_id", intent.getIntExtra(EXTRA_SESSION_ID,
                        parseInt(intent.getStringExtra(EXTRA_SESSION_ID), -1)))
                .putString("merchant_name", intent.getStringExtra(EXTRA_MERCHANT_NAME))
                .putString("merchant_location", intent.getStringExtra(EXTRA_MERCHANT_LOCATION))
                .putString("amount", String.valueOf(intent.getDoubleExtra(EXTRA_AMOUNT, 0)))
                .putString("currency", intent.getStringExtra(EXTRA_CURRENCY))
                .putString("masked_card", intent.getStringExtra(EXTRA_MASKED_CARD))
                .putString("expires_at", intent.getStringExtra(EXTRA_EXPIRES_AT))
                .putLong("saved_at", System.currentTimeMillis())
                .apply();
    }

    private void redirectToLogin() {
        Intent loginIntent = new Intent(this, SplashActivity.class);
        loginIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(loginIntent);
        finish();
    }

    public static Intent getPendingPaymentIntent(android.content.Context context) {
        SharedPreferences prefs = context.getSharedPreferences("PendingPayment", MODE_PRIVATE);
        int sessionId = prefs.getInt("session_id", -1);

        if (sessionId == -1) return null;

        // Verifică dacă plata nu e prea veche (max 5 minute)
        long savedAt = prefs.getLong("saved_at", 0);
        if (System.currentTimeMillis() - savedAt > 5 * 60 * 1000) {
            clearPendingPayment(context);
            return null;
        }

        Intent intent = new Intent(context, CardPaymentApprovalActivity.class);
        intent.putExtra(EXTRA_SESSION_ID, sessionId);
        intent.putExtra(EXTRA_MERCHANT_NAME, prefs.getString("merchant_name", null));
        intent.putExtra(EXTRA_MERCHANT_LOCATION, prefs.getString("merchant_location", null));
        intent.putExtra(EXTRA_AMOUNT, parseDouble(prefs.getString("amount", "0"), 0));
        intent.putExtra(EXTRA_CURRENCY, prefs.getString("currency", null));
        intent.putExtra(EXTRA_MASKED_CARD, prefs.getString("masked_card", null));
        intent.putExtra(EXTRA_EXPIRES_AT, prefs.getString("expires_at", null));

        return intent;
    }

    public static void clearPendingPayment(android.content.Context context) {
        context.getSharedPreferences("PendingPayment", MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return value == null ? fallback : Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTimers();
        cleanupRealtimeSubscription();

        if (sessionStatusCall != null) {
            sessionStatusCall.cancel();
            sessionStatusCall = null;
        }
    }

    private void setupRealtimeSubscription() {
        if (sessionId == -1 || realtimeListener != null) return;

        RealtimeManager realtime = RealtimeManager.getInstance();
        realtime.connect();

        realtimeListener = new RealtimeManager.RealtimeListener() {
            @Override
            public void onInsert(String table, JsonObject newRecord) {
                if (isCurrentSessionRecord(newRecord)) {
                    loadSession(false);
                }
            }

            @Override
            public void onUpdate(String table, JsonObject oldRecord, JsonObject newRecord) {
                if (isCurrentSessionRecord(newRecord)) {
                    loadSession(false);
                }
            }

            @Override
            public void onDelete(String table, JsonObject oldRecord) {
                if (isCurrentSessionRecord(oldRecord)) {
                    setTerminalState("Indisponibila", R.color.error_red, "Confirmarea nu mai este disponibila.", true);
                }
            }
        };

        realtime.subscribeToColumnChanges(
                "card_payment_sessions",
                "session_id",
                String.valueOf(sessionId),
                realtimeListener
        );
    }

    private void cleanupRealtimeSubscription() {
        if (realtimeListener == null || sessionId == -1) return;

        RealtimeManager.getInstance().unsubscribeFromColumnChanges(
                "card_payment_sessions",
                "session_id",
                String.valueOf(sessionId),
                realtimeListener
        );
        realtimeListener = null;
    }

    private boolean isCurrentSessionRecord(JsonObject record) {
        if (record == null) return false;

        try {
            if (record.has("session_id")) {
                return record.get("session_id").getAsInt() == sessionId;
            }
            if (record.has("id")) {
                return record.get("id").getAsInt() == sessionId;
            }
        } catch (Exception ignored) {
            return false;
        }

        return false;
    }

    private void initViews() {
        rootContainer = findViewById(R.id.rootContainer);
        headerContainer = findViewById(R.id.headerContainer);
        cardNumberRow = findViewById(R.id.cardNumberRow);
        btnBack = findViewById(R.id.btnBack);
        loadingState = findViewById(R.id.loadingState);
        contentContainer = findViewById(R.id.contentContainer);
        buttonContainer = findViewById(R.id.buttonContainer);
        tvMerchantName = findViewById(R.id.tvMerchantName);
        tvLocation = findViewById(R.id.tvLocation);
        tvAmount = findViewById(R.id.tvAmount);
        tvCountdown = findViewById(R.id.tvCountdown);
        tvStatus = findViewById(R.id.tvStatus);
        tvTimerLabel = findViewById(R.id.tvTimerLabel);
        tvCardNumberMasked = findViewById(R.id.tvCardNumberMasked);
        btnApprove = findViewById(R.id.btnApprove);
        btnDecline = findViewById(R.id.btnDecline);
        progressCircle = findViewById(R.id.progressCircle);
        merchantIconContainer = findViewById(R.id.merchantIconContainer);
        ivMerchantLogo = findViewById(R.id.ivMerchantLogo);
        ivCategoryIcon = findViewById(R.id.ivCategoryIcon);
        applySystemInsets();
    }

    private void applySystemInsets() {
        rootContainer.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            headerContainer.setPadding(dp(16), bars.top + dp(8), dp(16), dp(10));
            buttonContainer.setPadding(dp(24), dp(12), dp(24), bars.bottom + dp(14));
            return insets;
        });
        rootContainer.requestApplyInsets();
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnApprove.setOnClickListener(v -> requestApprovalAuthentication());
        btnDecline.setOnClickListener(v -> declinePayment());
    }

    private void hydrateFromIntent() {
        String merchantName = getIntent().getStringExtra(EXTRA_MERCHANT_NAME);
        String merchantLocation = getIntent().getStringExtra(EXTRA_MERCHANT_LOCATION);
        String currency = getIntent().getStringExtra(EXTRA_CURRENCY);
        String maskedCard = getIntent().getStringExtra(EXTRA_MASKED_CARD);
        String expiresAt = getIntent().getStringExtra(EXTRA_EXPIRES_AT);
        double amount = getIntent().getDoubleExtra(EXTRA_AMOUNT, 0);
        if (amount <= 0) {
            amount = parseDouble(getIntent().getStringExtra(EXTRA_AMOUNT), 0);
        }

        if (merchantName != null) {
            tvMerchantName.setText(merchantName);
        }
        if (merchantLocation != null) {
            tvLocation.setText(merchantLocation);
        }
        if (amount > 0) {
            tvAmount.setText(formatMoney(amount, currency));
        }
        updateMaskedCard(maskedCard);
        if (expiresAt != null) {
            expiresAtMillis = parseIsoTime(expiresAt);
            updateCountdown();
        }
    }

    private void loadSession(boolean showLoading) {
        if (sessionStatusCall != null) {
            return;
        }

        if (showLoading) {
            loadingState.setVisibility(View.VISIBLE);
            contentContainer.setVisibility(View.GONE);
        }

        sessionStatusCall = ApiClient.getCardPaymentService().getSessionStatus(sessionId);
        sessionStatusCall
                .enqueue(new Callback<ApiResponse<CardPaymentSessionData>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<CardPaymentSessionData>> call,
                                           Response<ApiResponse<CardPaymentSessionData>> response) {
                        sessionStatusCall = null;
                        if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                            renderSession(response.body().getData());
                        } else if (showLoading) {
                            showLoadError();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<CardPaymentSessionData>> call, Throwable t) {
                        sessionStatusCall = null;
                        if (call.isCanceled()) return;
                        if (showLoading) {
                            showLoadError();
                        }
                    }
                });
    }

    private void renderSession(CardPaymentSessionData session) {
        loadingState.setVisibility(View.GONE);
        contentContainer.setVisibility(View.VISIBLE);

        tvMerchantName.setText(displayValue(session.getMerchantName()));
        tvLocation.setText(displayValue(session.getMerchantLocation()));
        tvAmount.setText(formatMoney(session.getAmount(), session.getCurrency()));
        updateMaskedCard(session.getMaskedCard());
        updateMerchantIcon(session);

        syncServerClock(session.getServerTime());
        expiresAtMillis = parseIsoTime(session.getExpiresAt());

        String status = session.getStatus();
        if ("PENDING_APPROVAL".equals(status)) {
            setPendingState();
        } else if ("APPROVED".equals(status)) {
            setTerminalState("Suma blocata", R.color.green_accent, "Plata a fost aprobata.");
        } else if ("COMPLETED".equals(status)) {
            setTerminalState("Plata finalizata", R.color.green_accent, "Tranzactia a fost decontata.");
        } else if ("EXPIRED".equals(status)) {
            setTerminalState("Expirat", R.color.error_red, "Confirmarea nu mai este disponibila.", true);
        } else if ("DECLINED".equals(status)) {
            setTerminalState("Refuzata", R.color.error_red, displayValue(session.getDeclineReason()), true);
        }
    }

    private void setPendingState() {
        handler.removeCallbacks(autoCloseRunnable);
        actionInProgress = false;
        buttonContainer.setVisibility(View.VISIBLE);
        btnApprove.setEnabled(true);
        btnDecline.setEnabled(true);
        tvCountdown.setVisibility(View.VISIBLE);
        tvCountdown.setGravity(Gravity.CENTER);
        tvCountdown.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        tvTimerLabel.setText("Timp ramas pentru confirmare");
        tvTimerLabel.setTextColor(ContextCompat.getColor(this, R.color.white_60));
        tvStatus.setText("Aproba sau refuza plata");
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.white_60));
        lastColorState = 0;
        progressCircle.getProgressDrawable().setColorFilter(
                ContextCompat.getColor(this, R.color.green_accent), PorterDuff.Mode.SRC_IN);
        progressCircle.setProgress(1000);
        startTimers();
        updateCountdown();
    }

    private void setTerminalState(String countdownText, int colorRes, String statusText) {
        setTerminalState(countdownText, colorRes, statusText, false);
    }

    private void setTerminalState(String statusTitle, int colorRes, String statusText, boolean autoClose) {
        stopTimers();
        buttonContainer.setVisibility(View.GONE);

        // Ascunde textul din cerc - cercul ramane gol cu progres/culoare
        tvCountdown.setVisibility(View.GONE);
        tvTimerLabel.setText(statusTitle);
        tvTimerLabel.setTextColor(ContextCompat.getColor(this, colorRes));

        tvStatus.setText(statusText);
        tvStatus.setTextColor(ContextCompat.getColor(this, colorRes));

        int tintColor = ContextCompat.getColor(this, colorRes);
        progressCircle.getProgressDrawable().setColorFilter(tintColor, PorterDuff.Mode.SRC_IN);
        if (colorRes == R.color.green_accent) {
            progressCircle.setProgress(1000);
        } else {
            progressCircle.setProgress(0);
        }

        if (autoClose) {
            handler.removeCallbacks(autoCloseRunnable);
            handler.postDelayed(autoCloseRunnable, AUTO_CLOSE_DELAY_MS);
        }
    }

    private void requestApprovalAuthentication() {
        if (actionInProgress) return;

        if (expiresAtMillis > 0 && expiresAtMillis <= nowMillis()) {
            setButtonsEnabled(false);
            setTerminalState("Expirat", R.color.error_red, "Confirmarea nu mai este disponibila.", true);
            loadSession(false);
            return;
        }

        String merchantName = tvMerchantName.getText().toString();
        String amount = tvAmount.getText().toString();

        if (BiometricHelper.shouldUseBiometric(this)) {
            setButtonsEnabled(false);
            BiometricHelper.authenticate(this,
                    "Confirma plata",
                    String.format("%s la %s", amount, merchantName),
                    new BiometricHelper.BiometricCallback() {
                        @Override
                        public void onSuccess() {
                            setButtonsEnabled(true);
                            approvePayment();
                        }

                        @Override
                        public void onError(String error) {
                            if (isFinishing() || isDestroyed()) return;
                            setButtonsEnabled(true);
                            SwiftBankDialog.showErrorDialog(CardPaymentApprovalActivity.this,
                                    "Autentificare esuata", error);
                        }

                        @Override
                        public void onCancel() {
                            setButtonsEnabled(true);
                            showPinConfirmation();
                        }
                    });
        } else {
            showPinConfirmation();
        }
    }

    private void showPinConfirmation() {
        String merchantName = tvMerchantName.getText().toString();
        String amount = tvAmount.getText().toString();

        new PinConfirmDialog(
                this,
                "Confirma plata",
                String.format("Introdu PIN-ul pentru a aproba %s la %s", amount, merchantName),
                new PinConfirmDialog.PinCallback() {
                    @Override
                    public void onPinConfirmed() {
                        approvePayment();
                    }

                    @Override
                    public void onCancelled() {
                        setButtonsEnabled(true);
                    }
                }
        ).show();
    }

    private void approvePayment() {
        if (actionInProgress) return;

        actionInProgress = true;
        setButtonsEnabled(false);

        ApiClient.getCardPaymentService().approvePayment(sessionId)
                .enqueue(new Callback<ApiResponse<CardPaymentSessionData>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<CardPaymentSessionData>> call,
                                           Response<ApiResponse<CardPaymentSessionData>> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                            setResult(RESULT_OK);
                            showApprovedDialog();
                        } else {
                            actionInProgress = false;
                            setButtonsEnabled(true);
                            showApprovalError(response);
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<CardPaymentSessionData>> call, Throwable t) {
                        actionInProgress = false;
                        setButtonsEnabled(true);
                        SwiftBankDialog.showServerErrorDialog(CardPaymentApprovalActivity.this, v -> approvePayment());
                    }
                });
    }

    private void showApprovalError(Response<ApiResponse<CardPaymentSessionData>> response) {
        ApiErrorResponse errorResponse = ErrorParser.parseError(response);
        String errorCode = null;
        if (errorResponse != null && errorResponse.getError() != null) {
            errorCode = errorResponse.getError().getCode();
        }

        String errorMessage;
        String declineReason = null;
        boolean shouldAutoDecline = false;

        if (errorCode != null) {
            switch (errorCode) {
                case "INSUFFICIENT_FUNDS":
                    errorMessage = "Fonduri insuficiente";
                    declineReason = "Insufficient funds";
                    shouldAutoDecline = true;
                    break;
                case "SESSION_NOT_PENDING":
                    errorMessage = "Aceasta plata nu mai este disponibila";
                    break;
                case "SESSION_NOT_FOUND":
                    errorMessage = "Sesiunea de plata nu a fost gasita";
                    break;
                case "UNAUTHORIZED":
                    errorMessage = "Nu ai permisiunea de a aproba aceasta plata";
                    break;
                default:
                    errorMessage = "Plata nu a putut fi aprobata";
                    break;
            }
        } else {
            errorMessage = "Plata nu a putut fi aprobata";
        }

        if (shouldAutoDecline) {
            autoDeclineAndShowError(errorMessage, declineReason);
        } else {
            showErrorAndClose(errorMessage);
        }
    }

    private void autoDeclineAndShowError(String errorMessage, String declineReason) {
        ApiClient.getCardPaymentService()
                .declinePayment(sessionId, new DeclineCardPaymentRequest(declineReason))
                .enqueue(new Callback<ApiResponse<CardPaymentSessionData>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<CardPaymentSessionData>> call,
                                           Response<ApiResponse<CardPaymentSessionData>> response) {
                        showErrorAndClose(errorMessage);
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<CardPaymentSessionData>> call, Throwable t) {
                        showErrorAndClose(errorMessage);
                    }
                });
    }

    private void showErrorAndClose(String errorMessage) {
        new SwiftBankDialog(this)
                .setIcon(R.drawable.ic_error)
                .setTitle("Eroare")
                .setMessage(errorMessage)
                .setPrimaryButton("OK", v -> {
                    setResult(RESULT_OK);
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    private void declinePayment() {
        if (actionInProgress) return;

        actionInProgress = true;
        setButtonsEnabled(false);

        ApiClient.getCardPaymentService()
                .declinePayment(sessionId, new DeclineCardPaymentRequest("Declined by user"))
                .enqueue(new Callback<ApiResponse<CardPaymentSessionData>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<CardPaymentSessionData>> call,
                                           Response<ApiResponse<CardPaymentSessionData>> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                            setResult(RESULT_OK);
                            finish();
                        } else {
                            actionInProgress = false;
                            setButtonsEnabled(true);
                            SwiftBankDialog.showErrorDialog(CardPaymentApprovalActivity.this,
                                    "Plata nu a putut fi refuzata");
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<CardPaymentSessionData>> call, Throwable t) {
                        actionInProgress = false;
                        setButtonsEnabled(true);
                        SwiftBankDialog.showServerErrorDialog(CardPaymentApprovalActivity.this, v -> declinePayment());
                    }
                });
    }

    private void showApprovedDialog() {
        new SwiftBankDialog(this)
                .setIcon(R.drawable.ic_check_circle)
                .setTitle("Plata aprobata")
                .setMessage("Plata a fost acceptata cu succes.")
                .setPrimaryButton("OK", v -> finish())
                .show();
    }

    private void startTimers() {
        handler.removeCallbacks(countdownRunnable);
        handler.removeCallbacks(fallbackRefreshRunnable);
        handler.post(countdownRunnable);
        handler.postDelayed(fallbackRefreshRunnable, SESSION_FALLBACK_REFRESH_MS);
    }

    private void stopTimers() {
        handler.removeCallbacks(countdownRunnable);
        handler.removeCallbacks(fallbackRefreshRunnable);
        handler.removeCallbacks(autoCloseRunnable);
    }

    private void updateCountdown() {
        if (expiresAtMillis <= 0) {
            tvCountdown.setText("--:--");
            progressCircle.setProgress(0);
            return;
        }

        long remaining = Math.max(0, expiresAtMillis - nowMillis());
        long totalSeconds = remaining / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        tvCountdown.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));

        // Folosim durata totală fixă (5 min) pentru progres corect
        int targetProgress = (int) ((remaining * 1000) / SESSION_DURATION_MS);
        progressCircle.setProgress(Math.max(0, Math.min(1000, targetProgress)));

        int newColorState;
        if (remaining <= 30000) {
            newColorState = 2;
            tvCountdown.setTextColor(ContextCompat.getColor(this, R.color.error_red));
        } else if (remaining <= 60000) {
            newColorState = 1;
            tvCountdown.setTextColor(ContextCompat.getColor(this, R.color.warning_color));
        } else {
            newColorState = 0;
            tvCountdown.setTextColor(ContextCompat.getColor(this, R.color.white));
        }

        if (newColorState != lastColorState) {
            lastColorState = newColorState;
            int tintColor;
            if (newColorState == 2) {
                tintColor = ContextCompat.getColor(this, R.color.error_red);
            } else if (newColorState == 1) {
                tintColor = ContextCompat.getColor(this, R.color.warning_color);
            } else {
                tintColor = ContextCompat.getColor(this, R.color.green_accent);
            }
            progressCircle.getProgressDrawable().setColorFilter(tintColor, PorterDuff.Mode.SRC_IN);
        }

        if (remaining <= 0) {
            setButtonsEnabled(false);
            setTerminalState("Expirat", R.color.error_red, "Confirmarea nu mai este disponibila.", true);
            loadSession(false);
        }
    }

    private void setButtonsEnabled(boolean enabled) {
        btnApprove.setEnabled(enabled);
        btnDecline.setEnabled(enabled);
        btnApprove.setAlpha(enabled ? 1f : 0.55f);
        btnDecline.setAlpha(enabled ? 1f : 0.55f);
    }

    private void updateMerchantIcon(CardPaymentSessionData session) {
        String merchantLogoUrl = session.getMerchantLogoUrl();
        if (merchantLogoUrl != null && !merchantLogoUrl.trim().isEmpty()) {
            ivCategoryIcon.setVisibility(View.GONE);
            ivMerchantLogo.setVisibility(View.VISIBLE);
            merchantIconContainer.setCardBackgroundColor(ContextCompat.getColor(this, R.color.surface_color));
            RemoteImageLoader.load(merchantLogoUrl, ivMerchantLogo, () -> showCategoryIcon(session));
            return;
        }

        showCategoryIcon(session);
    }

    private void showCategoryIcon(CardPaymentSessionData session) {
        ivMerchantLogo.setTag(null);
        ivMerchantLogo.setImageDrawable(null);
        ivMerchantLogo.setVisibility(View.GONE);
        ivCategoryIcon.setVisibility(View.VISIBLE);
        ivCategoryIcon.setImageResource(getCategoryIconResource(firstAvailable(
                session.getCategoryIcon(),
                session.getCategoryName()
        )));
        ivCategoryIcon.setColorFilter(ContextCompat.getColor(this, R.color.white), PorterDuff.Mode.SRC_IN);
        merchantIconContainer.setCardBackgroundColor(getCategoryColor(session.getCategoryName()));
    }

    private String firstAvailable(String primary, String fallback) {
        return primary != null && !primary.trim().isEmpty() ? primary : fallback;
    }

    private int getCategoryColor(String categoryName) {
        if (categoryName == null) return Color.parseColor("#6B7280");

        switch (categoryName.trim().toLowerCase(Locale.ROOT)) {
            case "food":
                return Color.parseColor("#F97316");
            case "shopping":
                return Color.parseColor("#8B5CF6");
            case "transport":
                return Color.parseColor("#3B82F6");
            case "entertainment":
                return Color.parseColor("#EC4899");
            case "groceries":
                return Color.parseColor("#22C55E");
            case "health":
                return Color.parseColor("#EF4444");
            case "utilities":
                return Color.parseColor("#6366F1");
            case "telecom":
                return Color.parseColor("#3B82F6");
            case "internet":
                return Color.parseColor("#10B981");
            case "tv":
                return Color.parseColor("#8B5CF6");
            case "insurance":
                return Color.parseColor("#EF4444");
            case "travel":
                return Color.parseColor("#14B8A6");
            case "services":
                return Color.parseColor("#F59E0B");
            case "subscriptions":
                return Color.parseColor("#F59E0B");
            case "other":
            default:
                return Color.parseColor("#6B7280");
        }
    }

    private int getCategoryIconResource(String iconName) {
        if (iconName == null) return R.drawable.ic_category_other;

        switch (iconName.trim().toLowerCase(Locale.ROOT)) {
            case "food":
            case "ic_category_food":
                return R.drawable.ic_category_food;
            case "shopping":
            case "ic_category_shopping":
                return R.drawable.ic_category_shopping;
            case "transport":
            case "ic_category_transport":
                return R.drawable.ic_category_transport;
            case "entertainment":
            case "ic_category_entertainment":
                return R.drawable.ic_category_entertainment;
            case "groceries":
            case "ic_category_groceries":
                return R.drawable.ic_category_groceries;
            case "health":
            case "ic_category_health":
                return R.drawable.ic_category_health;
            case "utilities":
            case "ic_category_utilities":
                return R.drawable.ic_category_utilities;
            case "telecom":
            case "ic_category_telecom":
                return R.drawable.ic_phone;
            case "internet":
            case "ic_category_internet":
                return R.drawable.ic_wifi;
            case "tv":
            case "ic_category_tv":
                return R.drawable.ic_tv;
            case "insurance":
            case "ic_category_insurance":
                return R.drawable.ic_shield;
            case "travel":
            case "ic_category_travel":
                return R.drawable.ic_category_travel;
            case "services":
            case "ic_category_services":
                return R.drawable.ic_category_services;
            case "subscriptions":
            case "ic_category_subscriptions":
                return R.drawable.ic_category_entertainment;
            case "other":
            case "ic_category_other":
            default:
                return R.drawable.ic_category_other;
        }
    }

    private void showLoadError() {
        Toast.makeText(this, "Nu am putut incarca plata", Toast.LENGTH_SHORT).show();
        finish();
    }

    private String displayValue(String value) {
        return value == null || value.isEmpty() ? "-" : value;
    }

    private void updateMaskedCard(String maskedCard) {
        String formatted = formatMaskedCard(maskedCard);
        if (formatted == null) {
            cardNumberRow.setVisibility(View.GONE);
            return;
        }

        tvCardNumberMasked.setText(formatted);
        cardNumberRow.setVisibility(View.VISIBLE);
    }

    private String formatMaskedCard(String maskedCard) {
        if (maskedCard == null || maskedCard.trim().isEmpty()) {
            return null;
        }

        String digits = maskedCard.replaceAll("\\D", "");
        if (digits.length() >= 4) {
            return "**** **** **** " + digits.substring(digits.length() - 4);
        }

        return maskedCard.trim();
    }

    private String formatMoney(double amount, String currency) {
        return amountFormat.format(amount) + " " + getCurrencySymbol(currency);
    }

    private String getCurrencySymbol(String currency) {
        if (currency == null) return "lei";
        switch (currency.trim()) {
            case "EUR": return "€";
            case "USD": return "$";
            case "GBP": return "£";
            default: return "lei";
        }
    }

    private void syncServerClock(String serverTime) {
        long serverTimeMillis = parseIsoTime(serverTime);
        if (serverTimeMillis > 0) {
            serverClockOffsetMs = serverTimeMillis - System.currentTimeMillis();
        }
    }

    private long nowMillis() {
        return System.currentTimeMillis() + serverClockOffsetMs;
    }

    private long parseIsoTime(String value) {
        if (value == null || value.isEmpty()) return 0;

        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss"
        };

        for (String pattern : patterns) {
            try {
                SimpleDateFormat formatter = new SimpleDateFormat(pattern, Locale.US);
                if (pattern.endsWith("'Z'")) {
                    formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
                }
                Date date = formatter.parse(value);
                if (date != null) {
                    return date.getTime();
                }
            } catch (Exception ignored) {
            }
        }

        return 0;
    }

    private int parseInt(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static DecimalFormat createAmountFormat() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.getDefault());
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        return new DecimalFormat("#,##0.00", symbols);
    }
}
