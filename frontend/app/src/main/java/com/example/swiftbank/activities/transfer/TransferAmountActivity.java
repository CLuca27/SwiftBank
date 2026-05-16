package com.example.swiftbank.activities.transfer;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.util.Base64;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.swiftbank.R;
import com.example.swiftbank.api.ApiClient;
import com.example.swiftbank.api.dto.request.CreateTransferRequest;
import com.example.swiftbank.api.dto.response.ApiErrorResponse;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.success.AccountData;
import com.example.swiftbank.api.dto.response.data.success.AccountsData;
import com.example.swiftbank.api.dto.response.data.success.ExchangeRateData;
import com.example.swiftbank.api.dto.response.data.success.TransferResultData;
import com.example.swiftbank.managers.RatesManager;
import com.example.swiftbank.utils.BiometricHelper;
import com.example.swiftbank.api.dto.response.data.error.ErrorParser;
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

public class TransferAmountActivity extends AppCompatActivity {

    private ImageView btnBack;
    private TextView tvBeneficiaryName, tvBeneficiaryBank, tvBeneficiaryIban;
    private LinearLayout layoutBeneficiaryInitial;
    private TextView tvBeneficiaryInitial;
    private CardView cardBeneficiaryPhoto;
    private ImageView ivBeneficiaryPhoto;
    private LinearLayout accountSkeleton, skeletonAmount, skeletonDescription;
    private LinearLayout selectorFromAccount, cardAmount, cardDescription;
    private ImageView ivFlagFrom, ivFlagAmount;
    private TextView tvFromAccountName, tvFromAccountBalance, tvCurrency;
    private EditText etAmount, etDescription;
    private LinearLayout layoutConversion;
    private TextView tvExchangeRate, tvConvertedAmount, tvError;
    private TextView tvAmountLabel, tvDescriptionLabel;
    private Button btnTransfer;

    private int beneficiaryId;
    private String beneficiaryIban;
    private String beneficiaryName;
    private String beneficiaryBankName;
    private String beneficiaryPhoto;

    private List<AccountData> accounts = new ArrayList<>();
    private AccountData selectedAccount;
    private boolean isTransferring = false;
    private double exchangeRate = 0;
    private String destinationCurrency = null;
    private String idempotencyKey;

    private DecimalFormat balanceFormat;
    private DecimalFormat amountFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transfer_amount);

        beneficiaryId = getIntent().getIntExtra("beneficiary_id", -1);
        beneficiaryIban = getIntent().getStringExtra("iban");
        beneficiaryName = getIntent().getStringExtra("name");
        beneficiaryBankName = getIntent().getStringExtra("bank_name");
        beneficiaryPhoto = getIntent().getStringExtra("profile_photo");

        if (beneficiaryIban == null || beneficiaryName == null) {
            finish();
            return;
        }

        resetIdempotencyKey();

        setupFormatters();
        initViews();
        setupBeneficiaryInfo();
        setupListeners();
        loadAccounts();
    }

    private void resetIdempotencyKey() {
        if (!isTransferring) {
            idempotencyKey = UUID.randomUUID().toString();
        }
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
        tvBeneficiaryName = findViewById(R.id.tvBeneficiaryName);
        tvBeneficiaryBank = findViewById(R.id.tvBeneficiaryBank);
        tvBeneficiaryIban = findViewById(R.id.tvBeneficiaryIban);
        layoutBeneficiaryInitial = findViewById(R.id.layoutBeneficiaryInitial);
        tvBeneficiaryInitial = findViewById(R.id.tvBeneficiaryInitial);
        cardBeneficiaryPhoto = findViewById(R.id.cardBeneficiaryPhoto);
        ivBeneficiaryPhoto = findViewById(R.id.ivBeneficiaryPhoto);
        accountSkeleton = findViewById(R.id.accountSkeleton);
        skeletonAmount = findViewById(R.id.skeletonAmount);
        skeletonDescription = findViewById(R.id.skeletonDescription);
        selectorFromAccount = findViewById(R.id.selectorFromAccount);
        cardAmount = findViewById(R.id.cardAmount);
        cardDescription = findViewById(R.id.cardDescription);
        ivFlagFrom = findViewById(R.id.ivFlagFrom);
        ivFlagAmount = findViewById(R.id.ivFlagAmount);
        tvFromAccountName = findViewById(R.id.tvFromAccountName);
        tvFromAccountBalance = findViewById(R.id.tvFromAccountBalance);
        tvCurrency = findViewById(R.id.tvCurrency);
        etAmount = findViewById(R.id.etAmount);
        etAmount.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        etDescription = findViewById(R.id.etDescription);
        layoutConversion = findViewById(R.id.layoutConversion);
        tvExchangeRate = findViewById(R.id.tvExchangeRate);
        tvConvertedAmount = findViewById(R.id.tvConvertedAmount);
        tvError = findViewById(R.id.tvError);
        tvAmountLabel = findViewById(R.id.tvAmountLabel);
        tvDescriptionLabel = findViewById(R.id.tvDescriptionLabel);
        btnTransfer = findViewById(R.id.btnTransfer);
    }

    private void setupBeneficiaryInfo() {
        tvBeneficiaryName.setText(beneficiaryName);
        tvBeneficiaryBank.setText(beneficiaryBankName != null ? beneficiaryBankName : "");
        tvBeneficiaryIban.setText(beneficiaryIban);

        if (beneficiaryPhoto != null && !beneficiaryPhoto.isEmpty()) {
            Bitmap bitmap = decodeBase64Photo(beneficiaryPhoto);
            if (bitmap != null) {
                layoutBeneficiaryInitial.setVisibility(View.GONE);
                cardBeneficiaryPhoto.setVisibility(View.VISIBLE);
                ivBeneficiaryPhoto.setImageBitmap(bitmap);
            } else {
                showBeneficiaryInitial();
            }
        } else {
            showBeneficiaryInitial();
        }

        // Check if SwiftBank to get destination currency
        if ("SwiftBank".equals(beneficiaryBankName)) {
            loadDestinationCurrency();
        }
    }

    private void showBeneficiaryInitial() {
        layoutBeneficiaryInitial.setVisibility(View.VISIBLE);
        cardBeneficiaryPhoto.setVisibility(View.GONE);
        if (beneficiaryName != null && !beneficiaryName.isEmpty()) {
            tvBeneficiaryInitial.setText(beneficiaryName.substring(0, 1).toUpperCase());
        }
    }

    private Bitmap decodeBase64Photo(String base64Photo) {
        try {
            String base64Data = base64Photo;
            if (base64Photo.contains(",")) {
                base64Data = base64Photo.substring(base64Photo.indexOf(",") + 1);
            }
            byte[] decodedBytes = Base64.decode(base64Data, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
        } catch (Exception e) {
            return null;
        }
    }

    private void loadDestinationCurrency() {
        ApiClient.getTransferService().validateIBAN(new com.example.swiftbank.api.dto.request.ValidateIBANRequest(beneficiaryIban))
                .enqueue(new Callback<ApiResponse<com.example.swiftbank.api.dto.response.data.success.ValidateIBANData>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<com.example.swiftbank.api.dto.response.data.success.ValidateIBANData>> call,
                                           Response<ApiResponse<com.example.swiftbank.api.dto.response.data.success.ValidateIBANData>> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                            destinationCurrency = response.body().getData().getAccountCurrency();
                            if (selectedAccount != null) {
                                loadExchangeRateIfNeeded();
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<com.example.swiftbank.api.dto.response.data.success.ValidateIBANData>> call, Throwable t) {
                    }
                });
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        selectorFromAccount.setOnClickListener(v -> showAccountSelector());

        etAmount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                resetIdempotencyKey();
                validateAmount();
                updateConversionInfo();
                updateTransferButton();
            }
        });

        btnTransfer.setOnClickListener(v -> initiateTransfer());
    }

    private void loadAccounts() {
        ApiClient.getAccountService().getAccounts().enqueue(new Callback<ApiResponse<AccountsData>>() {
            @Override
            public void onResponse(Call<ApiResponse<AccountsData>> call, Response<ApiResponse<AccountsData>> response) {
                if (isFinishing() || isDestroyed()) return;

                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    accounts = response.body().getData().getAccounts();
                    if (!accounts.isEmpty()) {
                        selectAccount(accounts.get(0));
                        showContent();
                        return;
                    }
                }

                new SwiftBankDialog(TransferAmountActivity.this)
                        .setIcon(R.drawable.ic_error)
                        .setTitle("Eroare")
                        .setMessage("Nu ai conturi disponibile")
                        .setPrimaryButton("OK", v -> finish())
                        .show();
            }

            @Override
            public void onFailure(Call<ApiResponse<AccountsData>> call, Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                new SwiftBankDialog(TransferAmountActivity.this)
                        .setIcon(R.drawable.ic_error)
                        .setTitle("Eroare")
                        .setMessage("Eroare la încărcarea conturilor")
                        .setPrimaryButton("OK", v -> finish())
                        .show();
            }
        });
    }

    private void showContent() {
        hideSkeletonWithAnimation(accountSkeleton, selectorFromAccount);

        // Show labels
        tvAmountLabel.setVisibility(View.VISIBLE);
        tvDescriptionLabel.setVisibility(View.VISIBLE);

        hideSkeletonWithAnimation(skeletonAmount, cardAmount);
        hideSkeletonWithAnimation(skeletonDescription, cardDescription);
    }

    private void hideSkeletonWithAnimation(View skeleton, View content) {
        if (skeleton != null && content != null) {
            skeleton.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction(() -> {
                        skeleton.setVisibility(View.GONE);
                        content.setVisibility(View.VISIBLE);
                        content.setAlpha(0f);
                        content.animate().alpha(1f).setDuration(200).start();
                    }).start();
        }
    }

    private void selectAccount(AccountData account) {
        selectedAccount = account;
        resetIdempotencyKey();
        ivFlagFrom.setImageResource(getFlagResource(account.getCurrency()));
        ivFlagAmount.setImageResource(getFlagResource(account.getCurrency()));
        tvFromAccountName.setText("Personal " + account.getCurrency());
        tvFromAccountBalance.setText("Sold: " + balanceFormat.format(account.getBalance()) + " " + getCurrencySymbol(account.getCurrency()));
        tvCurrency.setText(account.getCurrency());

        loadExchangeRateIfNeeded();
        updateTransferButton();
    }

    private void loadExchangeRateIfNeeded() {
        if (selectedAccount == null) return;

        String fromCurrency = selectedAccount.getCurrency();
        String toCurrency = destinationCurrency;

        if (toCurrency == null || toCurrency.isEmpty() || fromCurrency.equals(toCurrency)) {
            exchangeRate = 1;
            layoutConversion.setVisibility(View.GONE);
            return;
        }

        RatesManager ratesManager = RatesManager.getInstance(this);
        if (ratesManager.hasRates()) {
            double localRate = ratesManager.getExchangeRate(fromCurrency, toCurrency);
            if (localRate > 0) {
                exchangeRate = localRate;
                updateConversionInfo();
                return;
            }
        }

        ApiClient.getAccountService().getExchangeRate(fromCurrency, toCurrency)
                .enqueue(new Callback<ApiResponse<ExchangeRateData>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<ExchangeRateData>> call, Response<ApiResponse<ExchangeRateData>> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                            exchangeRate = response.body().getData().getRate();
                            updateConversionInfo();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<ExchangeRateData>> call, Throwable t) {}
                });
    }

    private void updateConversionInfo() {
        if (selectedAccount == null || destinationCurrency == null || exchangeRate <= 0) {
            layoutConversion.setVisibility(View.GONE);
            return;
        }

        String fromCurrency = selectedAccount.getCurrency();
        if (fromCurrency.equals(destinationCurrency)) {
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
        tvExchangeRate.setText(String.format("1 %s = %.4f %s", fromCurrency, exchangeRate, destinationCurrency));
        tvConvertedAmount.setText(String.format("Destinatarul primește ~%s %s",
                amountFormat.format(convertedAmount), destinationCurrency));
    }

    private void validateAmount() {
        double amount = getAmount();

        if (selectedAccount != null && amount > selectedAccount.getBalance()) {
            cardAmount.setBackgroundResource(R.drawable.bg_card_error);
            tvError.setText("Fonduri insuficiente");
            tvError.setVisibility(View.VISIBLE);
        } else {
            cardAmount.setBackgroundResource(R.drawable.bg_card_dark);
            tvError.setVisibility(View.GONE);
        }
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

        String amountStr = balanceFormat.format(getAmount()) + " " + selectedAccount.getCurrency();

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
                            SwiftBankDialog.showErrorDialog(TransferAmountActivity.this,
                                    "Autentificare eșuată", error);
                        }

                        @Override
                        public void onCancel() {
                            showPinConfirmation();
                        }
                    });
        } else {
            showPinConfirmation();
        }
    }

    private void showPinConfirmation() {
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
                    public void onCancelled() {}
                }
        ).show();
    }

    private void executeTransfer() {
        if (isTransferring || selectedAccount == null) return;

        if (idempotencyKey == null || idempotencyKey.isEmpty()) {
            resetIdempotencyKey();
        }

        isTransferring = true;
        btnTransfer.setText("Se procesează...");
        updateTransferButton();

        CreateTransferRequest request = new CreateTransferRequest(
                selectedAccount.getAccountId(),
                beneficiaryIban,
                beneficiaryName,
                getAmount(),
                etDescription.getText().toString().trim()
        );

        ApiClient.getTransferService().createTransfer(idempotencyKey, request)
                .enqueue(new Callback<ApiResponse<TransferResultData>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<TransferResultData>> call, Response<ApiResponse<TransferResultData>> response) {
                        if (isFinishing() || isDestroyed()) return;

                        isTransferring = false;
                        btnTransfer.setText("Trimite");

                        if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                            handleTransferSuccess(response.body().getData());
                        } else {
                            ApiErrorResponse error = ErrorParser.parseError(response);
                            String message = error != null && error.getError() != null ?
                                    error.getError().getMessage() : "Transferul nu a putut fi efectuat";
                            SwiftBankDialog.showErrorDialog(TransferAmountActivity.this, message);
                            updateTransferButton();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<TransferResultData>> call, Throwable t) {
                        if (isFinishing() || isDestroyed()) return;

                        isTransferring = false;
                        btnTransfer.setText("Trimite");
                        SwiftBankDialog.showErrorDialog(TransferAmountActivity.this, "Eroare de conexiune");
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
            TextView tvAccountName, tvBalance, tvCurrency;
            ImageView ivSelected;

            ViewHolder(View itemView) {
                super(itemView);
                ivFlag = itemView.findViewById(R.id.ivFlag);
                tvAccountName = itemView.findViewById(R.id.tvAccountName);
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
                tvBalance.setText(balanceFormat.format(account.getBalance()));
                tvCurrency.setText(getCurrencySymbol(account.getCurrency()));

                boolean isSelected = selectedAccount != null &&
                        selectedAccount.getAccountId() == account.getAccountId();
                ivSelected.setVisibility(isSelected ? View.VISIBLE : View.GONE);
            }
        }
    }
}
