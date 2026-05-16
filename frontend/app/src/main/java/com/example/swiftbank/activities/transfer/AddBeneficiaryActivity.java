package com.example.swiftbank.activities.transfer;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.swiftbank.R;
import com.example.swiftbank.api.ApiClient;
import com.example.swiftbank.api.dto.request.CreateBeneficiaryRequest;
import com.example.swiftbank.api.dto.request.ValidateIBANRequest;
import com.example.swiftbank.api.dto.response.ApiErrorResponse;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.success.BeneficiaryData;
import com.example.swiftbank.api.dto.response.data.success.ValidateIBANData;
import com.example.swiftbank.api.dto.response.data.error.ErrorParser;
import com.example.swiftbank.utils.SwiftBankDialog;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AddBeneficiaryActivity extends AppCompatActivity {

    private ImageView btnBack;
    private EditText etIban, etName;
    private LinearLayout cardIban, cardName, layoutBankInfo;
    private TextView tvBankName, tvError, tvDetectedName, tvNameLabel;
    private ProgressBar progressIban;
    private ImageView ivIbanValid;
    private Button btnContinue;

    private ValidateIBANData validatedIBAN;
    private boolean isValidatingIBAN = false;
    private boolean isSaving = false;

    private Handler ibanHandler = new Handler(Looper.getMainLooper());
    private Runnable ibanRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_beneficiary);

        initViews();
        setupListeners();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        etIban = findViewById(R.id.etIban);
        etName = findViewById(R.id.etName);
        cardIban = findViewById(R.id.cardIban);
        cardName = findViewById(R.id.cardName);
        layoutBankInfo = findViewById(R.id.layoutBankInfo);
        tvBankName = findViewById(R.id.tvBankName);
        tvError = findViewById(R.id.tvError);
        tvDetectedName = findViewById(R.id.tvDetectedName);
        tvNameLabel = findViewById(R.id.tvNameLabel);
        progressIban = findViewById(R.id.progressIban);
        ivIbanValid = findViewById(R.id.ivIbanValid);
        btnContinue = findViewById(R.id.btnContinue);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        etIban.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                validatedIBAN = null;
                layoutBankInfo.setVisibility(View.GONE);
                ivIbanValid.setVisibility(View.GONE);
                tvNameLabel.setVisibility(View.GONE);
                cardName.setVisibility(View.GONE);
                hideError();

                if (ibanRunnable != null) {
                    ibanHandler.removeCallbacks(ibanRunnable);
                }

                String iban = s.toString().replace(" ", "").toUpperCase();
                if (iban.length() >= 24) {
                    progressIban.setVisibility(View.VISIBLE);
                    ibanRunnable = () -> validateIBAN(iban);
                    ibanHandler.postDelayed(ibanRunnable, 500);
                } else {
                    progressIban.setVisibility(View.GONE);
                }

                updateContinueButton();
            }
        });

        etName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                updateContinueButton();
            }
        });

        btnContinue.setOnClickListener(v -> saveBeneficiaryAndContinue());
    }

    private void validateIBAN(String iban) {
        if (isValidatingIBAN) return;
        isValidatingIBAN = true;

        ApiClient.getTransferService().validateIBAN(new ValidateIBANRequest(iban))
                .enqueue(new Callback<ApiResponse<ValidateIBANData>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<ValidateIBANData>> call, Response<ApiResponse<ValidateIBANData>> response) {
                        isValidatingIBAN = false;
                        progressIban.setVisibility(View.GONE);

                        if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                            validatedIBAN = response.body().getData();

                            if (validatedIBAN.isSameUser()) {
                                showError("Nu poți adăuga propriul cont ca beneficiar");
                                return;
                            }

                            ivIbanValid.setVisibility(View.VISIBLE);
                            displayIBANInfo();
                        } else {
                            ApiErrorResponse error = ErrorParser.parseError(response);
                            String message = error != null && error.getError() != null ?
                                    error.getError().getMessage() : "IBAN invalid";
                            showError(message);
                        }
                        updateContinueButton();
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<ValidateIBANData>> call, Throwable t) {
                        isValidatingIBAN = false;
                        progressIban.setVisibility(View.GONE);
                        showError("Eroare la validarea IBAN-ului");
                    }
                });
    }

    private void displayIBANInfo() {
        if (validatedIBAN == null) return;

        layoutBankInfo.setVisibility(View.VISIBLE);
        tvBankName.setText(validatedIBAN.getBankName());
        cardIban.setBackgroundResource(R.drawable.bg_card_dark);

        tvNameLabel.setVisibility(View.VISIBLE);
        cardName.setVisibility(View.VISIBLE);

        if (validatedIBAN.getBeneficiaryName() != null && !validatedIBAN.getBeneficiaryName().isEmpty()) {
            etName.setText(validatedIBAN.getBeneficiaryName());
            tvDetectedName.setVisibility(View.VISIBLE);
            if (validatedIBAN.isSwiftBank()) {
                etName.setEnabled(false);
                etName.setAlpha(0.7f);
            }
        } else {
            etName.setText("");
            etName.setEnabled(true);
            etName.setAlpha(1f);
            tvDetectedName.setVisibility(View.GONE);
        }
    }

    private void showError(String message) {
        cardIban.setBackgroundResource(R.drawable.bg_card_error);
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        cardIban.setBackgroundResource(R.drawable.bg_card_dark);
        tvError.setVisibility(View.GONE);
    }

    private void showBeneficiaryExistsDialog(String name) {
        SwiftBankDialog.showConfirmDialog(
                this,
                "Beneficiar existent",
                "Acest beneficiar există deja în lista ta. Dorești să continui cu un transfer?",
                "Continuă",
                "Anulează",
                () -> {
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("beneficiary_id", -1);
                    resultIntent.putExtra("iban", validatedIBAN.getIban());
                    resultIntent.putExtra("name", name);
                    resultIntent.putExtra("bank_name", validatedIBAN.getBankName());
                    setResult(RESULT_OK, resultIntent);
                    finish();
                }
        );
    }

    private void updateContinueButton() {
        boolean canContinue = validatedIBAN != null
                && !validatedIBAN.isSameUser()
                && !etName.getText().toString().trim().isEmpty()
                && !isSaving;

        btnContinue.setEnabled(canContinue);
        if (canContinue) {
            btnContinue.setBackgroundResource(R.drawable.bg_button_primary);
            btnContinue.setTextColor(getResources().getColor(R.color.white, null));
        } else {
            btnContinue.setBackgroundResource(R.drawable.bg_button_disabled);
            btnContinue.setTextColor(getResources().getColor(R.color.white_50, null));
        }
    }

    private void saveBeneficiaryAndContinue() {
        if (isSaving || validatedIBAN == null) return;

        isSaving = true;
        btnContinue.setText("Se salvează...");
        updateContinueButton();

        String name = etName.getText().toString().trim();
        CreateBeneficiaryRequest request = new CreateBeneficiaryRequest(
                validatedIBAN.getIban(),
                name
        );

        ApiClient.getTransferService().createBeneficiary(request)
                .enqueue(new Callback<ApiResponse<BeneficiaryData>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<BeneficiaryData>> call, Response<ApiResponse<BeneficiaryData>> response) {
                        if (isFinishing() || isDestroyed()) return;

                        isSaving = false;
                        btnContinue.setText("Continuă");

                        if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                            BeneficiaryData beneficiary = response.body().getData();
                            Intent resultIntent = new Intent();
                            resultIntent.putExtra("beneficiary_id", beneficiary.getBeneficiaryId());
                            resultIntent.putExtra("iban", beneficiary.getIban());
                            resultIntent.putExtra("name", beneficiary.getName());
                            resultIntent.putExtra("bank_name", beneficiary.getBankName());
                            setResult(RESULT_OK, resultIntent);
                            finish();
                        } else if (response.code() == 409) {
                            showBeneficiaryExistsDialog(name);
                        } else {
                            ApiErrorResponse error = ErrorParser.parseError(response);
                            String message = error != null && error.getError() != null ?
                                    error.getError().getMessage() : "Eroare la salvarea beneficiarului";
                            SwiftBankDialog.showErrorDialog(AddBeneficiaryActivity.this, message);
                            updateContinueButton();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<BeneficiaryData>> call, Throwable t) {
                        if (isFinishing() || isDestroyed()) return;

                        isSaving = false;
                        btnContinue.setText("Continuă");
                        SwiftBankDialog.showErrorDialog(AddBeneficiaryActivity.this, "Eroare de conexiune");
                        updateContinueButton();
                    }
                });
    }
}
