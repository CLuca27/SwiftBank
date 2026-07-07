package com.example.swiftbank.activities.login;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.swiftbank.R;
import com.example.swiftbank.activities.register.RegisterPhoneActivity;
import com.example.swiftbank.api.ApiClient;
import com.example.swiftbank.api.dto.request.IdentifyRequest;
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

public class IdentifyActivity extends AppCompatActivity {


    private static final String TAG = "LoginIdentifyActivity";
    private static final int ANIMATION_DURATION = 200;

    private enum InputMode {
        PHONE, EMAIL
    }

    // Views
    private ImageView btnBack;
    private TextView tvSubtitle;
    private LinearLayout phoneInputFrame;
    private EditText etPhone;
    private EditText etEmail;
    private TextView tvSwitchMethod;
    private Button btnContinue;
    private Button btnCreateAccount;
    private ParticlesView particlesView;

    // State
    private InputMode currentMode = InputMode.PHONE;
    private boolean isAnimating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_identify);

        initViews();
        setupListeners();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        tvSubtitle = findViewById(R.id.tvSubtitle);
        phoneInputFrame = findViewById(R.id.phoneInputFrame);
        etPhone = findViewById(R.id.etPhone);
        etEmail = findViewById(R.id.etEmail);
        tvSwitchMethod = findViewById(R.id.tvSwitchMethod);
        btnContinue = findViewById(R.id.btnContinue);
        btnCreateAccount = findViewById(R.id.btnCreateAccount);
        particlesView = findViewById(R.id.particlesView);

        updateContinueButton(false);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        etPhone.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (currentMode == InputMode.PHONE) {
                    updateContinueButton(isValidPhone(s.toString()));
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        etEmail.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (currentMode == InputMode.EMAIL) {
                    updateContinueButton(isValidEmail(s.toString()));
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        tvSwitchMethod.setOnClickListener(v -> {
            if (!isAnimating) {
                switchInputMode();
            }
        });

        btnContinue.setOnClickListener(v -> onContinueClicked());

        btnCreateAccount.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterPhoneActivity.class));
        });
    }

    private void switchInputMode() {
        isAnimating = true;

        InputMode newMode = (currentMode == InputMode.PHONE) ? InputMode.EMAIL : InputMode.PHONE;

        // View-uri curente și noi
        View currentInput = (currentMode == InputMode.PHONE) ? phoneInputFrame : etEmail;
        View newInput = (newMode == InputMode.PHONE) ? phoneInputFrame : etEmail;

        String newSubtitle = (newMode == InputMode.PHONE)
                ? "Introdu numărul de telefon asociat contului tău SwiftBank"
                : "Introdu adresa de email asociată contului tău SwiftBank";

        String newSwitchText = (newMode == InputMode.PHONE)
                ? "Folosește adresa de e-mail"
                : "Folosește numărul de telefon";

        // Fade out
        AnimatorSet fadeOut = new AnimatorSet();
        ObjectAnimator inputFadeOut = ObjectAnimator.ofFloat(currentInput, "alpha", 1f, 0f);
        ObjectAnimator subtitleFadeOut = ObjectAnimator.ofFloat(tvSubtitle, "alpha", 1f, 0f);
        ObjectAnimator switchFadeOut = ObjectAnimator.ofFloat(tvSwitchMethod, "alpha", 1f, 0f);
        fadeOut.playTogether(inputFadeOut, subtitleFadeOut, switchFadeOut);
        fadeOut.setDuration(ANIMATION_DURATION);
        fadeOut.setInterpolator(new AccelerateDecelerateInterpolator());

        fadeOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Schimbă conținutul
                currentInput.setVisibility(View.GONE);
                newInput.setVisibility(View.VISIBLE);
                newInput.setAlpha(0f);

                tvSubtitle.setText(newSubtitle);
                tvSwitchMethod.setText(newSwitchText);

                // Fade in
                AnimatorSet fadeIn = new AnimatorSet();
                ObjectAnimator inputFadeIn = ObjectAnimator.ofFloat(newInput, "alpha", 0f, 1f);
                ObjectAnimator subtitleFadeIn = ObjectAnimator.ofFloat(tvSubtitle, "alpha", 0f, 1f);
                ObjectAnimator switchFadeIn = ObjectAnimator.ofFloat(tvSwitchMethod, "alpha", 0f, 1f);
                fadeIn.playTogether(inputFadeIn, subtitleFadeIn, switchFadeIn);
                fadeIn.setDuration(ANIMATION_DURATION);
                fadeIn.setInterpolator(new AccelerateDecelerateInterpolator());

                fadeIn.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        currentMode = newMode;
                        isAnimating = false;

                        // Focus pe noul input
                        if (newMode == InputMode.PHONE) {
                            etPhone.requestFocus();
                            updateContinueButton(isValidPhone(etPhone.getText().toString()));
                        } else {
                            etEmail.requestFocus();
                            updateContinueButton(isValidEmail(etEmail.getText().toString()));
                        }
                    }
                });

                fadeIn.start();
            }
        });

        fadeOut.start();
    }

    private boolean isValidPhone(String phone) {
        String cleaned = phone.replaceAll("[^0-9]", "");
        return cleaned.length() == 9 && cleaned.startsWith("7");
    }

    private boolean isValidEmail(String email) {
        return email != null &&
                !email.isEmpty() &&
                Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private String getPhoneNumber() {
        String digits = etPhone.getText().toString().replaceAll("[^0-9]", "");
        return "+40" + digits;
    }

    private String getEmail() {
        return etEmail.getText().toString().trim().toLowerCase();
    }

    private void updateContinueButton(boolean enabled) {
        btnContinue.setEnabled(enabled);
        if (enabled) {
            btnContinue.setBackgroundResource(R.drawable.bg_button_primary);
            btnContinue.setTextColor(ContextCompat.getColor(this, R.color.green_dark));
        } else {
            btnContinue.setBackgroundResource(R.drawable.bg_button_primary_disabled);
            btnContinue.setTextColor(ContextCompat.getColor(this, R.color.white_60));
        }
    }

    private void showError(String message) {
        SwiftBankDialog.showErrorDialog(this, "Nu am putut continua", message);
    }

    private void showNetworkError(View.OnClickListener onRetry) {
        SwiftBankDialog.showServerErrorDialog(this, onRetry);
    }

    private void showLoading(boolean show) {
        btnContinue.setEnabled(!show);

        if (show) {
            btnContinue.setText("Se verifică...");
        } else {
            btnContinue.setText("Continuă");

            if (currentMode == InputMode.PHONE) {
                updateContinueButton(isValidPhone(etPhone.getText().toString()));
            } else {
                updateContinueButton(isValidEmail(etEmail.getText().toString()));
            }
        }
    }

    private void onContinueClicked() {
        showLoading(true);

        if (currentMode == InputMode.PHONE) {
            checkPhone(getPhoneNumber());
        } else {
            checkEmail(getEmail());
        }
    }

    private void checkPhone(String phone) {
        Log.d(TAG, "Checking phone: " + phone);

        IdentifyRequest request = new IdentifyRequest(null, phone);

        ApiClient.getAuthService().identify(request).enqueue(new Callback<ApiResponse<IdentifyData>>() {
            @Override
            public void onResponse(Call<ApiResponse<IdentifyData>> call, Response<ApiResponse<IdentifyData>> response) {
                showLoading(false);
                if(response.isSuccessful() && response.body() != null){
                    IdentifyData data = response.body().getData();
                    handleSuccess(phone, null, data);
                }
                else
                    handleError(response);
            }

            @Override
            public void onFailure(Call<ApiResponse<IdentifyData>> call, Throwable t) {
                showLoading(false);
                Log.e(TAG, "Network error: " + t.getMessage());
                showNetworkError(v -> checkPhone(phone));
            }
        });
    }

    private void checkEmail(String email) {
        Log.d(TAG, "Checking email: " + email);

        IdentifyRequest request = new IdentifyRequest(email, null);

        ApiClient.getAuthService().identify(request).enqueue(new Callback<ApiResponse<IdentifyData>>() {
            @Override
            public void onResponse(Call<ApiResponse<IdentifyData>> call, Response<ApiResponse<IdentifyData>> response) {
                showLoading(false);
                if(response.isSuccessful() && response.body() != null){
                    IdentifyData data = response.body().getData();
                    handleSuccess(null, email, data);
                }
                else
                    handleError(response);
            }



            @Override
            public void onFailure(Call<ApiResponse<IdentifyData>> call, Throwable t) {
                showLoading(false);
                Log.e(TAG, "Network error: " + t.getMessage());
                showNetworkError(v -> checkEmail(email));
            }
        });
    }

    private void handleSuccess(String phone, String email, IdentifyData data) {
        if(data != null)
        {
            if(data.isBlocked())
                goToBlockedPage();
            else
                goToPassword(phone, email, data);
        }
    }
    private void handleError(Response<ApiResponse<IdentifyData>> response) {
        ApiErrorResponse error = ErrorParser.parseError(response);
        if (error != null && error.getError() != null) {
            ErrorData data = error.getError();
            if ("USER_NOT_FOUND".equals(data.getCode()))
                goToPhoneRegister();
            else
                showError(data.getMessage());
        } else {
            showError("A apărut o eroare");
        }
    }


    private void goToPassword(String phone, String email, IdentifyData data) {
        Intent intent = new Intent(this, LoginPinActivity.class);
        String resolvedPhone = data.getPhone() != null ? data.getPhone() : phone;
        String resolvedEmail = data.getEmail() != null ? data.getEmail() : email;

        if (resolvedPhone != null) {
            intent.putExtra("phone", resolvedPhone);
        }
        if (resolvedEmail != null) {
            intent.putExtra("email", resolvedEmail);
        }
        intent.putExtra("first_name", data.getFirstName());
        intent.putExtra("locked_until", data.getLockedUntil());
        intent.putExtra("biometric_enabled", data.isBiometricEnabled());
        startActivity(intent);
        Log.e(TAG, "Going to password...");
    }

    private void goToPhoneRegister() {
        Intent intent = new Intent(this, RegisterPhoneActivity.class);
        startActivity(intent);
        Log.e(TAG, "Going to phone register activity...");
    }

    private void goToBlockedPage() {
        Intent intent = new Intent(this, BlockedUserActivity.class);
        startActivity(intent);
        Log.e(TAG, "Going to blocked user activity...");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (particlesView != null) {
            particlesView.stopAnimation();
        }
    }

}
