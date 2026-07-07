package com.example.swiftbank.activities.login;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.swiftbank.R;
import com.example.swiftbank.api.ApiClient;
import com.example.swiftbank.api.dto.request.ForgotPinRequest;
import com.example.swiftbank.api.dto.request.VerifyOtpRequest;
import com.example.swiftbank.api.dto.response.ApiErrorResponse;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.success.ForgotPinData;
import com.example.swiftbank.api.dto.response.data.error.ErrorParser;
import com.example.swiftbank.utils.SwiftBankDialog;
import com.example.swiftbank.views.ParticlesView;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ResetPinOtpActivity extends AppCompatActivity {

    private static final String TAG = "ResetPinOtpActivity";
    private static final long COOLDOWN_DURATION = 30000;

    // Views
    private ImageView btnBack;
    private TextView tvSubtitle;
    private EditText[] otpInputs;
    private TextView tvError;
    private TextView tvResend;
    private Button btnVerify;
    private View loadingOverlay;
    private ParticlesView particlesView;

    // Data
    private String phone;
    private String email;
    private String firstName;
    private String maskedPhone;
    private CountDownTimer cooldownTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otp);

        getIntentData();
        initViews();
        setupListeners();
        startCooldownTimer(COOLDOWN_DURATION);
    }

    private void getIntentData() {
        phone = getIntent().getStringExtra("phone");
        email = getIntent().getStringExtra("email");
        firstName = getIntent().getStringExtra("first_name");
        maskedPhone = getIntent().getStringExtra("masked_phone");
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        tvSubtitle = findViewById(R.id.tvSubtitle);
        tvError = findViewById(R.id.tvError);
        tvResend = findViewById(R.id.tvResend);
        btnVerify = findViewById(R.id.btnVerify);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        particlesView = findViewById(R.id.particlesView);

        TextView tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText("Resetare PIN");
        tvSubtitle.setText("Am trimis un cod SMS la " + maskedPhone);

        otpInputs = new EditText[]{
                findViewById(R.id.etOtp1),
                findViewById(R.id.etOtp2),
                findViewById(R.id.etOtp3),
                findViewById(R.id.etOtp4),
                findViewById(R.id.etOtp5),
                findViewById(R.id.etOtp6)
        };

        setupOtpInputs();
    }

    private void setupOtpInputs() {
        for (int i = 0; i < otpInputs.length; i++) {
            final int index = i;
            EditText input = otpInputs[i];

            input.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (s.length() == 1 && index < otpInputs.length - 1) {
                        otpInputs[index + 1].requestFocus();
                    }
                    hideError();
                    updateVerifyButton();
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });

            input.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (input.getText().toString().isEmpty() && index > 0) {
                        otpInputs[index - 1].requestFocus();
                        otpInputs[index - 1].setText("");
                    }
                }
                return false;
            });
        }
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnVerify.setOnClickListener(v -> verifyAndProceed());

        tvResend.setOnClickListener(v -> {
            if (tvResend.isEnabled()) {
                resendOtp();
            }
        });
    }

    private void verifyAndProceed() {
        String code = getOtpCode();
        if (code.length() != 6) {
            showError("Introdu codul complet");
            return;
        }

        showLoading(true);

        VerifyOtpRequest request = new VerifyOtpRequest(
                phone,
                email,
                code,
                "RESET_PIN"
        );

        ApiClient.getAuthService().verifyOtp(request).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                showLoading(false);

                if (response.isSuccessful()) {
                    // OTP valid - navigheaza la setarea PIN-ului
                    Intent intent = new Intent(ResetPinOtpActivity.this, ResetPinSetActivity.class);
                    intent.putExtra("phone", phone);
                    intent.putExtra("email", email);
                    intent.putExtra("first_name", firstName);
                    intent.putExtra("otp_code", code);
                    startActivity(intent);
                    finish();
                } else {
                    handleVerifyError(response);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                showLoading(false);
                SwiftBankDialog.showServerErrorDialog(ResetPinOtpActivity.this, v -> verifyAndProceed());
            }
        });
    }

    private void handleVerifyError(Response<ApiResponse<Void>> response) {
        ApiErrorResponse error = ErrorParser.parseError(response);

        if (error != null && error.getError() != null) {
            String errorCode = error.getError().getCode();

            switch (errorCode) {
                case "OTP_INVALID":
                    showError(error.getError().getMessage());
                    break;
                case "OTP_EXPIRED":
                    showError("Codul a expirat. Solicita unul nou.");
                    clearInputs();
                    break;
                case "OTP_MAX_ATTEMPTS":
                    showError("Prea multe incercari. Solicita un cod nou.");
                    clearInputs();
                    break;
                case "OTP_NOT_FOUND":
                    showError("Codul nu a fost gasit. Solicita unul nou.");
                    clearInputs();
                    break;
                default:
                    showError(error.getError().getMessage());
            }
        } else {
            showError("Eroare la verificarea codului");
        }
    }

    private void clearInputs() {
        for (EditText input : otpInputs) {
            input.setText("");
        }
        otpInputs[0].requestFocus();
        updateVerifyButton();
    }

    private void resendOtp() {
        showLoading(true);

        ForgotPinRequest request;
        if (phone != null && !phone.isEmpty()) {
            request = ForgotPinRequest.withPhone(phone);
        } else {
            request = ForgotPinRequest.withEmail(email);
        }

        ApiClient.getAuthService().forgotPin(request).enqueue(new Callback<ApiResponse<ForgotPinData>>() {
            @Override
            public void onResponse(Call<ApiResponse<ForgotPinData>> call, Response<ApiResponse<ForgotPinData>> response) {
                showLoading(false);

                if (response.isSuccessful()) {
                    startCooldownTimer(COOLDOWN_DURATION);
                    showSuccess("Cod retrimis!");
                } else {
                    ApiErrorResponse error = ErrorParser.parseError(response);
                    if (error != null && error.getError() != null) {
                        showError(error.getError().getMessage());
                    }
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<ForgotPinData>> call, Throwable t) {
                showLoading(false);
                SwiftBankDialog.showServerErrorDialog(ResetPinOtpActivity.this, v -> resendOtp());
            }
        });
    }

    private String getOtpCode() {
        StringBuilder code = new StringBuilder();
        for (EditText input : otpInputs) {
            code.append(input.getText().toString());
        }
        return code.toString();
    }

    private void updateVerifyButton() {
        String code = getOtpCode();
        btnVerify.setEnabled(code.length() == 6);
        btnVerify.setAlpha(code.length() == 6 ? 1f : 0.5f);
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setTextColor(getResources().getColor(R.color.error_red, null));
        tvError.setVisibility(View.VISIBLE);
    }

    private void showSuccess(String message) {
        tvError.setText(message);
        tvError.setTextColor(getResources().getColor(R.color.green_accent, null));
        tvError.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        tvError.setVisibility(View.GONE);
    }

    private void showLoading(boolean show) {
        loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void startCooldownTimer(long duration) {
        tvResend.setEnabled(false);
        tvResend.setAlpha(0.5f);

        if (cooldownTimer != null) {
            cooldownTimer.cancel();
        }

        cooldownTimer = new CountDownTimer(duration, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) (millisUntilFinished / 1000);
                tvResend.setText("Retrimite codul (" + seconds + "s)");
            }

            @Override
            public void onFinish() {
                tvResend.setText("Retrimite codul");
                tvResend.setEnabled(true);
                tvResend.setAlpha(1f);
            }
        }.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cooldownTimer != null) {
            cooldownTimer.cancel();
        }
        if (particlesView != null) {
            particlesView.stopAnimation();
        }
    }
}
