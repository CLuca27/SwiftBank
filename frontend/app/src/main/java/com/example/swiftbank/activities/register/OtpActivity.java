package com.example.swiftbank.activities.register;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.swiftbank.R;
import com.example.swiftbank.api.ApiClient;
import com.example.swiftbank.api.dto.RegistrationData;
import com.example.swiftbank.api.dto.request.SendOtpRequest;
import com.example.swiftbank.api.dto.request.VerifyOtpRequest;
import com.example.swiftbank.api.dto.response.ApiErrorResponse;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.error.ErrorData;
import com.example.swiftbank.api.dto.response.data.error.OtpCooldownErrorData;
import com.example.swiftbank.utils.ErrorParser;
import com.example.swiftbank.views.ParticlesView;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OtpActivity extends AppCompatActivity {

    private static final String TAG = "OtpActivity";
    private static final long COOLDOWN_DURATION = 30000; // 60 secunde

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
    private RegistrationData data;
    private String phone;
    private String email;
    private String purpose;
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
        data = getIntent().getParcelableExtra(RegisterPhoneActivity.EXTRA_REGISTRATION_DATA);
        purpose = getIntent().getStringExtra("purpose");
        if("REGISTER_PHONE".equals(purpose) && data != null)
            phone = data.getPhoneNumber();
        else if("REGISTER_EMAIL".equals(purpose) && data != null)
            email = data.getEmail();
        else
            Log.e(TAG, "Datele nu exista sau purpose este invalid");
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        tvSubtitle = findViewById(R.id.tvSubtitle);
        tvError = findViewById(R.id.tvError);
        tvResend = findViewById(R.id.tvResend);
        btnVerify = findViewById(R.id.btnVerify);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        particlesView = findViewById(R.id.particlesView);

        // OTP inputs
        otpInputs = new EditText[]{
                findViewById(R.id.etOtp1),
                findViewById(R.id.etOtp2),
                findViewById(R.id.etOtp3),
                findViewById(R.id.etOtp4),
                findViewById(R.id.etOtp5),
                findViewById(R.id.etOtp6)
        };

        // Set subtitle
        if (phone != null) {
            String maskedPhone = maskPhone(phone);
            tvSubtitle.setText("Am trimis un cod SMS la " + maskedPhone);
        } else if (email != null) {
            String maskedEmail = maskEmail(email);
            tvSubtitle.setText("Am trimis un cod la " + maskedEmail);
        }

        updateVerifyButton(false);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        // OTP input listeners
        for (int i = 0; i < otpInputs.length; i++) {
            final int index = i;

            otpInputs[i].addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    hideError();

                    if (s.length() == 1 && index < otpInputs.length - 1) {
                        // Mută focus la următorul
                        otpInputs[index + 1].requestFocus();
                    }

                    updateVerifyButton(isOtpComplete());
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });

            // Handle backspace
            otpInputs[i].setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_DEL &&
                        event.getAction() == KeyEvent.ACTION_DOWN &&
                        otpInputs[index].getText().toString().isEmpty() &&
                        index > 0) {
                    // Mută focus la precedentul și șterge
                    otpInputs[index - 1].requestFocus();
                    otpInputs[index - 1].setText("");
                    return true;
                }
                return false;
            });
        }

        btnVerify.setOnClickListener(v -> verifyOtp());

        // Resend link
        tvResend.setOnClickListener(v -> {
            if (tvResend.isClickable()) {
                resendOtp();
            }
        });
    }

    private boolean isOtpComplete() {
        for (EditText input : otpInputs) {
            if (input.getText().toString().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private String getOtpCode() {
        StringBuilder code = new StringBuilder();
        for (EditText input : otpInputs) {
            code.append(input.getText().toString());
        }
        return code.toString();
    }

    private void clearOtpInputs() {
        for (EditText input : otpInputs) {
            input.setText("");
        }
        otpInputs[0].requestFocus();
    }

    private void setOtpInputsEnabled(boolean enabled) {
        for (EditText input : otpInputs) {
            input.setEnabled(enabled);
        }
    }

    private void updateVerifyButton(boolean enabled) {
        btnVerify.setEnabled(enabled);
        btnVerify.setAlpha(enabled ? 1f : 0.5f);
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
        btnVerify.setEnabled(!show && isOtpComplete());
    }

    // ==================== COOLDOWN TIMER ====================

    private void startCooldownTimer(long milliseconds) {
        tvResend.setClickable(false);
        tvResend.setAlpha(0.5f);

        if (cooldownTimer != null) {
            cooldownTimer.cancel();
        }

        cooldownTimer = new CountDownTimer(milliseconds, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) (millisUntilFinished / 1000);
                tvResend.setText(String.format("Retrimite cod (0:%02d)", seconds));
            }

            @Override
            public void onFinish() {
                tvResend.setText("Retrimite cod");
                tvResend.setClickable(true);
                tvResend.setAlpha(1f);
            }
        }.start();
    }

//     ==================== API CALLS ====================

    private void verifyOtp() {
        String code = getOtpCode();
        Log.d(TAG, "Verifying OTP: " + code);

        showLoading(true);
        hideError();

        VerifyOtpRequest request = new VerifyOtpRequest(phone, email, code, purpose);

        ApiClient.getAuthService().verifyOtp(request).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                showLoading(false);

                if (response.isSuccessful() && response.body() != null) {
                    onVerifySuccess();
                } else {
                    handleVerifyError(response);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                showLoading(false);
                Log.e(TAG, "Network error: " + t.getMessage());
                showError("Eroare de conexiune.");
            }
        });
    }

    private void resendOtp() {
        Log.d(TAG, "Resending OTP");

        showLoading(true);
        hideError();

        SendOtpRequest request = new SendOtpRequest(phone, email, purpose);

        ApiClient.getAuthService().sendOtp(request).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                showLoading(false);

                if (response.isSuccessful() && response.body() != null) {
                    // Restart cooldown și resetează inputs
                    clearOtpInputs();
                    setOtpInputsEnabled(true);
                    startCooldownTimer(COOLDOWN_DURATION);
                    showSuccess("Cod nou trimis!");
                } else {
                    handleResendError(response);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                showLoading(false);
                Log.e(TAG, "Network error: " + t.getMessage());
                showError("Eroare de conexiune.");
            }
        });
    }

    // ==================== ERROR HANDLING ====================

    private void handleVerifyError(Response<ApiResponse<Void>> response) {
        ApiErrorResponse error = ErrorParser.parseError(response);

        if (error == null || error.getError() == null) {
            showError("Eroare la verificarea codului.");
            return;
        }

        ErrorData errorData = error.getError();
        String code = errorData.getCode();

        switch (code) {
            case "OTP_INVALID":
                clearOtpInputs();
                showError(errorData.getMessage());
                break;

            case "OTP_EXPIRED":
            case "OTP_MAX_ATTEMPTS":
            case "OTP_NOT_FOUND":
                showError(errorData.getMessage());
                setOtpInputsEnabled(false);
                updateVerifyButton(false);
                break;

            default:
                showError(errorData.getMessage());
        }
    }

    //Super rar !!!!!! Dar totusi am tratat si aceasta exceptie
    private void handleResendError(Response<ApiResponse<Void>> response) {
        ApiErrorResponse error = ErrorParser.parseError(response);

        if (error == null || error.getError() == null) {
            showError("Nu am putut retrimite codul.");
            return;
        }

        ErrorData errorData = error.getError();

        if (errorData instanceof OtpCooldownErrorData) {
            OtpCooldownErrorData cooldownError = (OtpCooldownErrorData) errorData;
            startCooldownTimer(cooldownError.getSecondsLeft() * 1000L);
        }

        showError(errorData.getMessage());
    }

    // ==================== SUCCESS ====================

    private void onVerifySuccess() {
        Log.d(TAG, "OTP verified successfully");

        if ("REGISTER_PHONE".equals(purpose)) {
            // Următorul pas: EmailRegisterActivity
            data.setPhoneVerified(true);

            Intent intent = new Intent(this, RegisterEmailActivity.class);
            intent.putExtra(RegisterPhoneActivity.EXTRA_REGISTRATION_DATA, data);
            startActivity(intent);
            finish();
        }
        else if ("REGISTER_EMAIL".equals(purpose)) {
            // Următorul pas: RegisterDataActivity
            data.setEmailVerified(true);

            Intent intent = new Intent(this, RegisterDetailsActivity.class);
            intent.putExtra(RegisterPhoneActivity.EXTRA_REGISTRATION_DATA, data);
            startActivity(intent);
            finish();
        }
    }

    private void showSuccess(String message) {
        // Poți implementa un Toast sau Snackbar
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show();
    }

    // ==================== HELPERS ====================

    private String maskPhone(String phone) {
        // +40712345678 -> +40 7XX XXX X78
        if (phone.length() >= 12) {
            return phone.substring(0, 3) + " 7XX XXX " + phone.substring(phone.length() - 3);
        }
        return phone;
    }

    private String maskEmail(String email) {
        // user@example.com -> u***@example.com
        int atIndex = email.indexOf('@');
        if (atIndex > 1) {
            return email.charAt(0) + "***" + email.substring(atIndex);
        }
        return email;
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