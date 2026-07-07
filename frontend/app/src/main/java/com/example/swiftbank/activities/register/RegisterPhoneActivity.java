package com.example.swiftbank.activities.register;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.swiftbank.R;
import com.example.swiftbank.activities.login.BlockedUserActivity;
import com.example.swiftbank.activities.login.LoginPinActivity;
import com.example.swiftbank.api.ApiClient;
import com.example.swiftbank.api.dto.RegistrationData;
import com.example.swiftbank.api.dto.request.IdentifyRequest;
import com.example.swiftbank.api.dto.request.SendOtpRequest;
import com.example.swiftbank.api.dto.response.ApiErrorResponse;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.error.ErrorData;
import com.example.swiftbank.api.dto.response.data.success.IdentifyData;
import com.example.swiftbank.api.dto.response.data.error.ErrorParser;
import com.example.swiftbank.utils.SwiftBankDialog;
import com.example.swiftbank.views.ParticlesView;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterPhoneActivity extends AppCompatActivity {

    private static final String TAG = "RegisterPhoneActivity";
    public static final String EXTRA_REGISTRATION_DATA = "REGISTER_DATA";

    // Views
    private ImageView btnBack;
    private EditText etPhoneNumber;
    private TextView tvError;
    private Button btnContinue;
    private View loadingOverlay;
    private ParticlesView particlesView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_phone);

        initViews();
        setupListeners();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        etPhoneNumber = findViewById(R.id.etPhoneNumber);
        tvError = findViewById(R.id.tvError);
        btnContinue = findViewById(R.id.btnContinue);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        particlesView = findViewById(R.id.particlesView);

        updateContinueButton(false);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        etPhoneNumber.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                hideError();
                updateContinueButton(isValidPhoneNumber(s.toString()));
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnContinue.setOnClickListener(v -> onContinueClicked());
    }

    private boolean isValidPhoneNumber(String phone) {
        String cleaned = phone.replaceAll("[^0-9]", "");
        return cleaned.length() == 9 && cleaned.startsWith("7");
    }

    private String getFullPhoneNumber() {
        String cleaned = etPhoneNumber.getText().toString().replaceAll("[^0-9]", "");
        return "+40" + cleaned;
    }

    private void updateContinueButton(boolean enabled) {
        btnContinue.setEnabled(enabled);
        btnContinue.setAlpha(enabled ? 1f : 0.5f);
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        tvError.setVisibility(View.GONE);
    }

    private void showNetworkError(View.OnClickListener onRetry) {
        SwiftBankDialog.showServerErrorDialog(this, onRetry);
    }

    private void showLoading(boolean show) {
        loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        btnContinue.setEnabled(!show);
    }

    private void onContinueClicked() {
        String phoneNumber = getFullPhoneNumber();
        Log.d(TAG, "Checking phone: " + phoneNumber);

        showLoading(true);
        hideError();

        // Folosim identify() pentru a verifica dacă user-ul există
        IdentifyRequest request = new IdentifyRequest(null, phoneNumber);

        ApiClient.getAuthService().identify(request).enqueue(new Callback<ApiResponse<IdentifyData>>() {
            @Override
            public void onResponse(Call<ApiResponse<IdentifyData>> call, Response<ApiResponse<IdentifyData>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    // User-ul EXISTĂ - verifică status
                    showLoading(false);
                    IdentifyData data = response.body().getData();

                    if (data.isBlocked()) {
                        goToBlockedPage();
                    } else {
                        goToLogin(phoneNumber, data);
                    }
                } else {
                    // Eroare - verifică dacă e USER_NOT_FOUND
                    handleIdentifyError(response, phoneNumber);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<IdentifyData>> call, Throwable t) {
                showLoading(false);
                Log.e(TAG, "Network error: " + t.getMessage());
                showNetworkError(v -> onContinueClicked());
            }
        });
    }

    private void handleIdentifyError(Response<ApiResponse<IdentifyData>> response, String phoneNumber) {
        ApiErrorResponse error = ErrorParser.parseError(response);

        if (error != null && error.getError() != null) {
            ErrorData errorData = error.getError();

            if ("USER_NOT_FOUND".equals(errorData.getCode())) {
                // User-ul NU există - trimite OTP pentru înregistrare
                sendOtp(phoneNumber);
            } else {
                showLoading(false);
                showError(errorData.getMessage());
            }
        } else {
            showLoading(false);
            showError("A apărut o eroare. Încearcă din nou.");
        }
    }

    private void sendOtp(String phoneNumber) {
        SendOtpRequest otpRequest = new SendOtpRequest(phoneNumber, null, "REGISTER_PHONE");

        ApiClient.getAuthService().sendOtp(otpRequest).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                showLoading(false);

                if (response.isSuccessful() && response.body() != null) {
                    goToOtp(phoneNumber);
                } else {
                    handleOtpError(response);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                showLoading(false);
                Log.e(TAG, "Network error: " + t.getMessage());
                showNetworkError(v -> sendOtp(phoneNumber));
            }
        });
    }

    private void handleOtpError(Response<ApiResponse<Void>> response) {
        ApiErrorResponse error = ErrorParser.parseError(response);
        if (error != null && error.getError() != null) {
            String code = error.getError().getCode();

            if ("OTP_COOLDOWN".equals(code)) {
                showError("Așteaptă 30 secunde înainte să retrimiți codul.");
            } else {
                showError(error.getError().getMessage());
            }
        } else {
            showError("Nu am putut trimite codul SMS.");
        }
    }

    private void goToLogin(String phoneNumber, IdentifyData data) {
        Log.d(TAG, "User exists, going to PinLoginActivity");
        Intent intent = new Intent(this, LoginPinActivity.class);
        intent.putExtra("phone", phoneNumber);
        intent.putExtra("first_name", data.getFirstName());
        intent.putExtra("locked_until", data.getLockedUntil());
        startActivity(intent);
        finish();
    }

    private void goToOtp(String phoneNumber) {
        Intent intent = new Intent(this, OtpActivity.class);
        RegistrationData data = new RegistrationData();
        data.setPhoneNumber(phoneNumber);
        intent.putExtra(EXTRA_REGISTRATION_DATA, data);
        intent.putExtra("purpose", "REGISTER_PHONE");
        startActivity(intent);
        Log.d(TAG, "Going to OtpActivity with phone: " + phoneNumber);
    }

    private void goToBlockedPage() {
        Log.d(TAG, "User is blocked, going to BlockedPageActivity");
        Intent intent = new Intent(this, BlockedUserActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (particlesView != null) {
            particlesView.stopAnimation();
        }
    }
}
