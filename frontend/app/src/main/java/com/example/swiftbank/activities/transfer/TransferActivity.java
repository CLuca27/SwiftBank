package com.example.swiftbank.activities.transfer;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.swiftbank.R;
import com.example.swiftbank.api.ApiClient;
import com.example.swiftbank.api.dto.request.CreateTransferRequest;
import com.example.swiftbank.api.dto.request.ValidateIBANRequest;
import com.example.swiftbank.api.dto.response.ApiErrorResponse;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.AccountData;
import com.example.swiftbank.api.dto.response.data.AccountsData;
import com.example.swiftbank.api.dto.response.data.ExchangeRateData;
import com.example.swiftbank.api.dto.response.data.TransferResultData;
import com.example.swiftbank.api.dto.response.data.ValidateIBANData;
import com.example.swiftbank.storage.RatesManager;
import com.example.swiftbank.utils.BiometricHelper;
import com.example.swiftbank.utils.ErrorParser;
import com.example.swiftbank.utils.PinConfirmDialog;
import com.example.swiftbank.utils.SwiftBankDialog;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TransferActivity extends AppCompatActivity {

    // Views
    private ImageView btnBack;
    private LinearLayout accountSkeleton;
    private LinearLayout selectorFromAccount;
    private ImageView ivFlagFrom, ivFlagAmount;
    private TextView tvFromAccountName, tvFromAccountBalance;
    private EditText etIban, etBeneficiaryName, etAmount, etDescription;
    private LinearLayout layoutBankInfo, layoutConversion;
    private LinearLayout cardIban, cardBeneficiary, cardAmount;
    private TextView tvBankName, tvCurrency, tvError;
    private TextView tvExchangeRate, tvConvertedAmount;
    private android.widget.ProgressBar progressIban;
    private ImageView ivIbanValid;
    private Button btnTransfer;

    // State
    private List<AccountData> accounts = new ArrayList<>();
    private AccountData selectedAccount;
    private ValidateIBANData validatedIBAN;
    private boolean isValidatingIBAN = false;
    private boolean isTransferring = false;
    private double exchangeRate = 0;
    private String idempotencyKey;

    // Debounce pentru validare IBAN
    private Handler ibanHandler = new Handler(Looper.getMainLooper());
    private Runnable ibanRunnable;

    private DecimalFormat balanceFormat;
    private DecimalFormat amountFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transfer);

        setupFormatters();
        initViews();
        setupListeners();
        loadAccounts();

        // Generează idempotency key pentru acest transfer
        idempotencyKey = UUID.randomUUID().toString();
    }

    private void setupFormatters() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.getDefault());
        symbols.setDecimalSeparator(',');
        symbols.setGroupingSeparator('.');

        balanceFormat = new DecimalFormat("#,##0.00", symbols);
        amountFormat = new DecimalFormat("#,##0.##", symbols);
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        accountSkeleton = findViewById(R.id.accountSkeleton);
        selectorFromAccount = findViewById(R.id.selectorFromAccount);
        ivFlagFrom = findViewById(R.id.ivFlagFrom);
        ivFlagAmount = findViewById(R.id.ivFlagAmount);
        tvFromAccountName = findViewById(R.id.tvFromAccountName);
        tvFromAccountBalance = findViewById(R.id.tvFromAccountBalance);
        etIban = findViewById(R.id.etIban);
        etBeneficiaryName = findViewById(R.id.etBeneficiaryName);
        etAmount = findViewById(R.id.etAmount);
        etDescription = findViewById(R.id.etDescription);
        layoutBankInfo = findViewById(R.id.layoutBankInfo);
        layoutConversion = findViewById(R.id.layoutConversion);
        cardIban = findViewById(R.id.cardIban);
        cardBeneficiary = findViewById(R.id.cardBeneficiary);
        cardAmount = findViewById(R.id.cardAmount);
        tvBankName = findViewById(R.id.tvBankName);
        tvCurrency = findViewById(R.id.tvCurrency);
        tvError = findViewById(R.id.tvError);
        tvExchangeRate = findViewById(R.id.tvExchangeRate);
        tvConvertedAmount = findViewById(R.id.tvConvertedAmount);
        progressIban = findViewById(R.id.progressIban);
        ivIbanValid = findViewById(R.id.ivIbanValid);
        btnTransfer = findViewById(R.id.btnTransfer);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        selectorFromAccount.setOnClickListener(v -> showAccountSelector());

        // IBAN input cu debounce
        etIban.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                // Reset validated data
                validatedIBAN = null;
                layoutBankInfo.setVisibility(View.GONE);
                layoutConversion.setVisibility(View.GONE);
                ivIbanValid.setVisibility(View.GONE);
                exchangeRate = 0;

                // Debounce validation
                if (ibanRunnable != null) {
                    ibanHandler.removeCallbacks(ibanRunnable);
                }

                String iban = s.toString().replace(" ", "").toUpperCase();
                if (iban.length() >= 24) {
                    // Show loading indicator
                    progressIban.setVisibility(View.VISIBLE);
                    ibanRunnable = () -> validateIBAN(iban);
                    ibanHandler.postDelayed(ibanRunnable, 500);
                } else {
                    progressIban.setVisibility(View.GONE);
                }

                updateTransferButton();
            }
        });

        // Amount input
        etAmount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                validateAmount();
                updateConversionInfo();
                updateTransferButton();
            }
        });

        // Beneficiary name
        etBeneficiaryName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                updateTransferButton();
            }
        });

        btnTransfer.setOnClickListener(v -> initiateTransfer());
    }

    private void loadAccounts() {
        ApiClient.getAccountService().getAccounts().enqueue(new Callback<ApiResponse<AccountsData>>() {
            @Override
            public void onResponse(Call<ApiResponse<AccountsData>> call, Response<ApiResponse<AccountsData>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    accounts = response.body().getData().getAccounts();
                    if (!accounts.isEmpty()) {
                        selectAccount(accounts.get(0));
                    }
                }
                showAccountContent();
            }

            @Override
            public void onFailure(Call<ApiResponse<AccountsData>> call, Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                showAccountContent();
                SwiftBankDialog.showErrorDialog(TransferActivity.this, "Eroare la încărcarea conturilor");
            }
        });
    }

    private void showAccountContent() {
        if (accountSkeleton != null && selectorFromAccount != null) {
            accountSkeleton.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction(() -> {
                        accountSkeleton.setVisibility(View.GONE);
                        selectorFromAccount.setVisibility(View.VISIBLE);
                        selectorFromAccount.setAlpha(0f);
                        selectorFromAccount.animate().alpha(1f).setDuration(200).start();
                    }).start();
        }
    }

    private void selectAccount(AccountData account) {
        selectedAccount = account;
        ivFlagFrom.setImageResource(getFlagResource(account.getCurrency()));
        ivFlagAmount.setImageResource(getFlagResource(account.getCurrency()));
        tvFromAccountName.setText("Personal " + account.getCurrency());
        tvFromAccountBalance.setText("Sold: " + balanceFormat.format(account.getBalance()) + " " + getCurrencySymbol(account.getCurrency()));
        tvCurrency.setText(account.getCurrency());

        // Re-validează IBAN pentru a actualiza rata de schimb
        if (validatedIBAN != null) {
            loadExchangeRateIfNeeded();
        }

        updateTransferButton();
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
                            // Show checkmark only if not same user
                            if (!validatedIBAN.isSameUser()) {
                                ivIbanValid.setVisibility(View.VISIBLE);
                            }
                            displayIBANInfo();
                            loadExchangeRateIfNeeded();
                        } else {
                            ivIbanValid.setVisibility(View.GONE);
                            ApiErrorResponse error = ErrorParser.parseError(response);
                            String message = error != null && error.getError() != null ?
                                    error.getError().getMessage() : "IBAN invalid";
                            showIBANError(message);
                        }
                        updateTransferButton();
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<ValidateIBANData>> call, Throwable t) {
                        isValidatingIBAN = false;
                        progressIban.setVisibility(View.GONE);
                        ivIbanValid.setVisibility(View.GONE);
                        showIBANError("Eroare la validarea IBAN-ului");
                    }
                });
    }

    private void displayIBANInfo() {
        if (validatedIBAN == null) return;

        // Afișează info bancă
        layoutBankInfo.setVisibility(View.VISIBLE);
        tvBankName.setText(validatedIBAN.getBankName());

        // Completează numele beneficiarului (dacă e SwiftBank sau din beneficiari)
        if (validatedIBAN.getBeneficiaryName() != null && !validatedIBAN.getBeneficiaryName().isEmpty()) {
            etBeneficiaryName.setText(validatedIBAN.getBeneficiaryName());
            // Dacă e SwiftBank, facem câmpul read-only
            if (validatedIBAN.isSwiftBank()) {
                etBeneficiaryName.setEnabled(false);
                etBeneficiaryName.setAlpha(0.7f);
            }
        } else {
            etBeneficiaryName.setEnabled(true);
            etBeneficiaryName.setAlpha(1f);
            etBeneficiaryName.setText("");
        }

        // Verifică dacă e același utilizator
        if (validatedIBAN.isSameUser()) {
            ivIbanValid.setVisibility(View.GONE);
            showIBANError("Nu poți transfera către propriul cont");
        } else {
            hideError();
        }

        // Reset card appearance
        cardIban.setBackgroundResource(R.drawable.bg_card_dark);
    }

    private void showIBANError(String message) {
        cardIban.setBackgroundResource(R.drawable.bg_card_error);
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    private void loadExchangeRateIfNeeded() {
        if (selectedAccount == null || validatedIBAN == null) return;

        String fromCurrency = selectedAccount.getCurrency();
        String toCurrency = validatedIBAN.getAccountCurrency();

        // Dacă nu avem moneda destinatarului (bancă externă), presupunem aceeași monedă
        if (toCurrency == null || toCurrency.isEmpty()) {
            toCurrency = fromCurrency;
        }

        if (fromCurrency.equals(toCurrency)) {
            exchangeRate = 1;
            layoutConversion.setVisibility(View.GONE);
            return;
        }

        // Încarcă rata de schimb
        final String finalToCurrency = toCurrency;

        // Încearcă din cache local
        RatesManager ratesManager = RatesManager.getInstance(this);
        if (ratesManager.hasRates()) {
            double localRate = ratesManager.getExchangeRate(fromCurrency, finalToCurrency);
            if (localRate > 0) {
                exchangeRate = localRate;
                updateConversionInfo();
                return;
            }
        }

        // Fallback la API
        ApiClient.getAccountService().getExchangeRate(fromCurrency, finalToCurrency)
                .enqueue(new Callback<ApiResponse<ExchangeRateData>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<ExchangeRateData>> call, Response<ApiResponse<ExchangeRateData>> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                            exchangeRate = response.body().getData().getRate();
                            updateConversionInfo();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<ExchangeRateData>> call, Throwable t) {
                        // Ignorăm eroarea - utilizatorul poate continua fără conversie
                    }
                });
    }

    private void updateConversionInfo() {
        if (selectedAccount == null || validatedIBAN == null || exchangeRate <= 0) {
            layoutConversion.setVisibility(View.GONE);
            return;
        }

        String fromCurrency = selectedAccount.getCurrency();
        String toCurrency = validatedIBAN.getAccountCurrency();

        if (toCurrency == null || toCurrency.isEmpty() || fromCurrency.equals(toCurrency)) {
            layoutConversion.setVisibility(View.GONE);
            return;
        }

        double amount = getAmount();
        if (amount <= 0) {
            layoutConversion.setVisibility(View.GONE);
            return;
        }

        double convertedAmount = amount * exchangeRate;

        layoutConversion.setVisibility(View.VISIBLE);
        tvExchangeRate.setText(String.format("1 %s = %.4f %s", fromCurrency, exchangeRate, toCurrency));
        tvConvertedAmount.setText(String.format("Destinatarul primește ~%s %s",
                amountFormat.format(convertedAmount), toCurrency));
    }

    private void validateAmount() {
        double amount = getAmount();

        if (selectedAccount != null && amount > selectedAccount.getBalance()) {
            cardAmount.setBackgroundResource(R.drawable.bg_card_error);
            tvError.setText("Fonduri insuficiente");
            tvError.setVisibility(View.VISIBLE);
        } else {
            cardAmount.setBackgroundResource(R.drawable.bg_card_dark);
            if (validatedIBAN == null || !validatedIBAN.isSameUser()) {
                hideError();
            }
        }
    }

    private void hideError() {
        tvError.setVisibility(View.GONE);
    }

    private double getAmount() {
        String amountStr = etAmount.getText().toString()
                .replace(",", ".")
                .replace(" ", "");
        try {
            return Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void updateTransferButton() {
        boolean canTransfer = selectedAccount != null
                && validatedIBAN != null
                && !validatedIBAN.isSameUser()
                && !etBeneficiaryName.getText().toString().trim().isEmpty()
                && getAmount() > 0
                && getAmount() <= selectedAccount.getBalance()
                && !isTransferring;

        btnTransfer.setEnabled(canTransfer);
        if (canTransfer) {
            btnTransfer.setBackgroundResource(R.drawable.bg_button_primary);
            btnTransfer.setTextColor(getResources().getColor(R.color.white, null));
        } else {
            btnTransfer.setBackgroundResource(R.drawable.bg_button_disabled);
            btnTransfer.setTextColor(getResources().getColor(R.color.white_50, null));
        }
    }

    private void initiateTransfer() {
        if (isTransferring) return;

        String beneficiaryName = etBeneficiaryName.getText().toString().trim();
        String amountStr = balanceFormat.format(getAmount()) + " " + selectedAccount.getCurrency();

        // Verifică dacă trebuie să folosim biometrie
        if (BiometricHelper.shouldUseBiometric(this)) {
            BiometricHelper.authenticateForTransfer(this, beneficiaryName, amountStr,
                    new BiometricHelper.BiometricCallback() {
                        @Override
                        public void onSuccess() {
                            executeTransfer();
                        }

                        @Override
                        public void onError(String error) {
                            if (isFinishing() || isDestroyed()) return;
                            SwiftBankDialog.showErrorDialog(TransferActivity.this,
                                    "Autentificare eșuată", error);
                        }

                        @Override
                        public void onCancel() {
                            // TODO: Implementare confirmare cu PIN
                            // Pentru moment, executăm direct transferul
                            showPinConfirmation();
                        }
                    });
        } else {
            // Fără biometrie activată - cere confirmare cu PIN
            showPinConfirmation();
        }
    }

    private void showPinConfirmation() {
        String beneficiaryName = etBeneficiaryName.getText().toString().trim();
        String amountStr = balanceFormat.format(getAmount()) + " " + selectedAccount.getCurrency();

        new PinConfirmDialog(
                this,
                "Confirmă transferul",
                String.format("Introdu PIN-ul pentru a transfera %s către %s", amountStr, beneficiaryName),
                new PinConfirmDialog.PinCallback() {
                    @Override
                    public void onPinConfirmed() {
                        executeTransfer();
                    }

                    @Override
                    public void onCancelled() {
                        // Utilizatorul a anulat - nu facem nimic
                    }
                }
        ).show();
    }

    private void executeTransfer() {
        if (isTransferring || selectedAccount == null || validatedIBAN == null) return;

        isTransferring = true;
        btnTransfer.setText("Se procesează...");
        updateTransferButton();

        CreateTransferRequest request = new CreateTransferRequest(
                selectedAccount.getAccountId(),
                validatedIBAN.getIban(),
                etBeneficiaryName.getText().toString().trim(),
                getAmount(),
                etDescription.getText().toString().trim()
        );

        ApiClient.getTransferService().createTransfer(idempotencyKey, request)
                .enqueue(new Callback<ApiResponse<TransferResultData>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<TransferResultData>> call, Response<ApiResponse<TransferResultData>> response) {
                        if (isFinishing() || isDestroyed()) return;

                        isTransferring = false;
                        btnTransfer.setText("Continuă");

                        if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                            handleTransferSuccess(response.body().getData());
                        } else {
                            ApiErrorResponse error = ErrorParser.parseError(response);
                            String message = error != null && error.getError() != null ?
                                    error.getError().getMessage() : "Transferul nu a putut fi efectuat";
                            SwiftBankDialog.showErrorDialog(TransferActivity.this, message);
                            updateTransferButton();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<TransferResultData>> call, Throwable t) {
                        if (isFinishing() || isDestroyed()) return;

                        isTransferring = false;
                        btnTransfer.setText("Continuă");
                        SwiftBankDialog.showErrorDialog(TransferActivity.this, "Eroare de conexiune");
                        updateTransferButton();
                    }
                });
    }

    private void handleTransferSuccess(TransferResultData result) {
        String message = String.format("Ai transferat %s %s către %s",
                amountFormat.format(result.getAmount()),
                result.getCurrency(),
                result.getBeneficiaryName());

        if (result.getOriginalAmount() != null && result.getOriginalCurrency() != null) {
            message = String.format("Ai transferat %s %s (%s %s) către %s",
                    amountFormat.format(result.getAmount()),
                    result.getCurrency(),
                    amountFormat.format(result.getOriginalAmount()),
                    result.getOriginalCurrency(),
                    result.getBeneficiaryName());
        }

        SwiftBankDialog.showSuccessDialog(this,
                "Transfer reușit!",
                message,
                v -> {
                    // Returnează account_id pentru ca Dashboard să selecteze contul corect
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("selected_account_id", selectedAccount.getAccountId());
                    setResult(RESULT_OK, resultIntent);
                    finish();
                });
    }

    private void showAccountSelector() {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_select_account, null);
        bottomSheet.setContentView(sheetView);

        TextView tvTitle = sheetView.findViewById(R.id.tvSheetTitle);
        tvTitle.setText("Selectează contul");

        RecyclerView rvAccounts = sheetView.findViewById(R.id.rvAccounts);
        rvAccounts.setLayoutManager(new LinearLayoutManager(this));

        AccountSelectorAdapter adapter = new AccountSelectorAdapter(accounts, account -> {
            selectAccount(account);
            bottomSheet.dismiss();
        });
        rvAccounts.setAdapter(adapter);

        bottomSheet.show();
    }

    private int getFlagResource(String currency) {
        switch (currency) {
            case "EUR": return R.drawable.flag_eur;
            case "USD": return R.drawable.flag_usd;
            case "GBP": return R.drawable.flag_gbp;
            case "RON": return R.drawable.flag_ro;
            default: return R.drawable.flag_ro;
        }
    }

    private String getCurrencySymbol(String currency) {
        if (currency == null) return "";
        switch (currency) {
            case "EUR": return "€";
            case "USD": return "$";
            case "GBP": return "£";
            case "RON": return "lei";
            default: return currency;
        }
    }

    // ==================== ACCOUNT SELECTOR ADAPTER ====================

    class AccountSelectorAdapter extends RecyclerView.Adapter<AccountSelectorAdapter.ViewHolder> {
        private List<AccountData> accounts;
        private OnAccountSelectedListener listener;

        interface OnAccountSelectedListener {
            void onAccountSelected(AccountData account);
        }

        AccountSelectorAdapter(List<AccountData> accounts, OnAccountSelectedListener listener) {
            this.accounts = accounts;
            this.listener = listener;
        }

        @Override
        public ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_account_bottom_sheet, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            holder.bind(accounts.get(position));
        }

        @Override
        public int getItemCount() {
            return accounts.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivFlag;
            TextView tvAccountName, tvIban, tvBalance, tvCurrency;
            ImageView ivSelected;

            ViewHolder(View itemView) {
                super(itemView);
                ivFlag = itemView.findViewById(R.id.ivFlag);
                tvAccountName = itemView.findViewById(R.id.tvAccountName);
                tvIban = itemView.findViewById(R.id.tvIban);
                tvBalance = itemView.findViewById(R.id.tvBalance);
                tvCurrency = itemView.findViewById(R.id.tvCurrency);
                ivSelected = itemView.findViewById(R.id.ivSelected);

                itemView.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onAccountSelected(accounts.get(getAdapterPosition()));
                    }
                });
            }

            void bind(AccountData account) {
                ivFlag.setImageResource(getFlagResource(account.getCurrency()));
                tvAccountName.setText("Personal · " + account.getCurrency());
                tvIban.setText("•••• " + account.getIban().substring(account.getIban().length() - 4));
                tvBalance.setText(balanceFormat.format(account.getBalance()));
                tvCurrency.setText(getCurrencySymbol(account.getCurrency()));

                // Arată check dacă e selectat
                boolean isSelected = selectedAccount != null &&
                        selectedAccount.getAccountId() == account.getAccountId();
                ivSelected.setVisibility(isSelected ? View.VISIBLE : View.GONE);
            }
        }
    }
}
