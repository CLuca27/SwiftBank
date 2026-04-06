package com.example.swiftbank.activities.register;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.swiftbank.R;
import com.example.swiftbank.api.ApiClient;
import com.example.swiftbank.api.dto.RegistrationData;
import com.example.swiftbank.api.dto.request.CheckRequest;
import com.example.swiftbank.api.dto.request.SendOtpRequest;
import com.example.swiftbank.api.dto.response.ApiErrorResponse;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.CheckData;
import com.example.swiftbank.utils.ErrorParser;
import com.example.swiftbank.views.ParticlesView;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterEmailActivity extends BackActivity {

    private static final String TAG = "EmailRegisterActivity";

    // Views
    private ImageView btnBack;
    private EditText etEmail;
    private TextView tvError;
    private Button btnContinue;
    private View loadingOverlay;
    private ParticlesView particlesView;

    // Data
    private RegistrationData data;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_register);

        getIntentData();
        initViews();
        setupListeners();
    }

    private void getIntentData() {
        data = getIntent().getParcelableExtra(RegisterPhoneActivity.EXTRA_REGISTRATION_DATA);
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        etEmail = findViewById(R.id.etEmail);
        tvError = findViewById(R.id.tvError);
        btnContinue = findViewById(R.id.btnContinue);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        particlesView = findViewById(R.id.particlesView);

        updateContinueButton(false);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        etEmail.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                hideError();
                updateContinueButton(isValidEmail(s.toString()));
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnContinue.setOnClickListener(v -> onContinueClicked());
    }

    private boolean isValidEmail(String email) {
        return email != null &&
                !email.isEmpty() &&
                Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private String getEmail() {
        return etEmail.getText().toString().trim().toLowerCase();
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

    private void showLoading(boolean show) {
        loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        btnContinue.setEnabled(!show && isValidEmail(getEmail()));
    }

    private void onContinueClicked() {
        String email = getEmail();
        Log.d(TAG, "Verifying if email exists......");
        showLoading(true);

        CheckRequest checkRequest = new CheckRequest("email", email);
        ApiClient.getAuthService().check(checkRequest).enqueue(new Callback<ApiResponse<CheckData>>() {

            @Override
            public void onResponse(Call<ApiResponse<CheckData>> call, Response<ApiResponse<CheckData>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    CheckData checkData = response.body().getData();
                    if (checkData.isExists()) {
                        showLoading(false);
                        showError("Email-ul este deja inregistrat !");
                    } else {
                        Log.d(TAG, "Sending OTP to email: " + email);
                        SendOtpRequest otpRequest = new SendOtpRequest(null, email, "REGISTER_EMAIL");

                        ApiClient.getAuthService().sendOtp(otpRequest).enqueue(new Callback<ApiResponse<Void>>() {
                            @Override
                            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                                showLoading(false);

                                if (response.isSuccessful() && response.body() != null) {
                                    goToOtp(email);
                                } else {
                                    handleError(response);
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
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<CheckData>> call, Throwable t) {
                showLoading(false);
                Log.e(TAG, "Network error: " + t.getMessage());
                showError("Eroare de conexiune");
            }
        });
    }

    private void handleError(Response<ApiResponse<Void>> response) {
        ApiErrorResponse error = ErrorParser.parseError(response);

        if (error != null && error.getError() != null) {
            showError(error.getError().getMessage());
        } else {
            showError("Nu am putut trimite codul.");
        }
    }

    private void goToOtp(String email) {
        data.setEmail(email);

        Intent intent = new Intent(this, OtpActivity.class);
        intent.putExtra(RegisterPhoneActivity.EXTRA_REGISTRATION_DATA, data);
        intent.putExtra("purpose", "REGISTER_EMAIL");
        startActivity(intent);
        Log.d(TAG, "Going to OtpActivity with email: " + email);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (particlesView != null) {
            particlesView.stopAnimation();
        }
    }

}