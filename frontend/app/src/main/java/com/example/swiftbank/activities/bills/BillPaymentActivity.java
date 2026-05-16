package com.example.swiftbank.activities.bills;

import android.content.Intent;
import android.graphics.Typeface;
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

import androidx.cardview.widget.CardView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.swiftbank.R;
import com.example.swiftbank.api.ApiClient;
import com.example.swiftbank.api.dto.request.CreateBillPaymentRequest;
import com.example.swiftbank.api.dto.response.ApiErrorResponse;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.success.AccountData;
import com.example.swiftbank.api.dto.response.data.success.AccountsData;
import com.example.swiftbank.api.dto.response.data.success.BillPaymentResultData;
import com.example.swiftbank.api.dto.response.data.error.ErrorParser;
import com.example.swiftbank.utils.BiometricHelper;
import com.example.swiftbank.utils.PinConfirmDialog;
import com.example.swiftbank.utils.RemoteImageLoader;
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

public class BillPaymentActivity extends AppCompatActivity {

    private ImageView btnBack;
    private TextView tvBillerName, tvBillerInitial;
    private LinearLayout accountSkeleton, selectorAccount, layoutBillerInitial;
    private CardView cardBillerLogo;
    private ImageView ivBillerLogo;
    private ImageView ivAccountFlag, ivAmountFlag;
    private TextView tvAccountName, tvAccountBalance, tvCurrency, tvError;
    private EditText etClientCode, etAmount;
    private LinearLayout cardClientCode, cardAmount;
    private Button btnPay;

    private int billerId;
    private String billerName;
    private String accountFormat;
    private String billerLogoUrl;
    private String billerCategory;
    private Integer savedBillerId;
    private String prefilledClientCode;

    private List<AccountData> accounts = new ArrayList<>();
    private AccountData selectedAccount;
    private boolean isPaying = false;
    private String idempotencyKey;

    private DecimalFormat balanceFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bill_payment);

        billerId = getIntent().getIntExtra("biller_id", -1);
        billerName = getIntent().getStringExtra("biller_name");
        accountFormat = getIntent().getStringExtra("account_format");
        billerLogoUrl = getIntent().getStringExtra("biller_logo_url");
        billerCategory = getIntent().getStringExtra("biller_category");
        savedBillerId = getIntent().hasExtra("saved_biller_id") ?
                getIntent().getIntExtra("saved_biller_id", -1) : null;
        prefilledClientCode = getIntent().getStringExtra("client_code");

        if (billerId == -1 || billerName == null) {
            finish();
            return;
        }

        setupFormatters();
        initViews();
        setupBillerInfo();
        setupListeners();
        loadAccounts();
        resetIdempotencyKey();
    }

    private void resetIdempotencyKey() {
        if (!isPaying) {
            idempotencyKey = UUID.randomUUID().toString();
        }
    }

    private void setupFormatters() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.getDefault());
        symbols.setDecimalSeparator(',');
        symbols.setGroupingSeparator('.');
        balanceFormat = new DecimalFormat("#,##0.00", symbols);
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        tvBillerName = findViewById(R.id.tvBillerName);
        tvBillerInitial = findViewById(R.id.tvBillerInitial);
        layoutBillerInitial = findViewById(R.id.layoutBillerInitial);
        cardBillerLogo = findViewById(R.id.cardBillerLogo);
        ivBillerLogo = findViewById(R.id.ivBillerLogo);
        accountSkeleton = findViewById(R.id.accountSkeleton);
        selectorAccount = findViewById(R.id.selectorAccount);
        ivAccountFlag = findViewById(R.id.ivAccountFlag);
        ivAmountFlag = findViewById(R.id.ivAmountFlag);
        tvAccountName = findViewById(R.id.tvAccountName);
        tvAccountBalance = findViewById(R.id.tvAccountBalance);
        tvCurrency = findViewById(R.id.tvCurrency);
        tvError = findViewById(R.id.tvError);
        etClientCode = findViewById(R.id.etClientCode);
        etAmount = findViewById(R.id.etAmount);
        etAmount.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        cardClientCode = findViewById(R.id.cardClientCode);
        cardAmount = findViewById(R.id.cardAmount);
        btnPay = findViewById(R.id.btnPay);
    }

    private void setupBillerInfo() {
        tvBillerName.setText(billerName);

        if (billerLogoUrl != null && !billerLogoUrl.trim().isEmpty()) {
            showBillerLogoContainer();
            ivBillerLogo.clearColorFilter();
            cardBillerLogo.setCardBackgroundColor(ContextCompat.getColor(this, R.color.white));
            RemoteImageLoader.load(billerLogoUrl, ivBillerLogo, this::showBillerCategoryIcon);
        } else {
            showBillerCategoryIcon();
        }

        if (prefilledClientCode != null && !prefilledClientCode.isEmpty()) {
            etClientCode.setText(prefilledClientCode);
        }
    }

    private void showBillerLogoContainer() {
        cardBillerLogo.setVisibility(View.VISIBLE);
        layoutBillerInitial.setVisibility(View.GONE);
    }

    private void showBillerCategoryIcon() {
        ivBillerLogo.setTag(null);
        showBillerLogoContainer();
        cardBillerLogo.setCardBackgroundColor(getCategoryColor(billerCategory));
        ivBillerLogo.setImageResource(getCategoryIcon(billerCategory));
        ivBillerLogo.setColorFilter(ContextCompat.getColor(this, R.color.white));
    }

    private int getCategoryIcon(String key) {
        if (key == null) return R.drawable.ic_receipt;
        switch (key.toLowerCase()) {
            case "utilities": return R.drawable.ic_utilities;
            case "telecom": return R.drawable.ic_phone;
            case "internet": return R.drawable.ic_wifi;
            case "tv": return R.drawable.ic_tv;
            case "insurance": return R.drawable.ic_shield;
            case "subscriptions": return R.drawable.ic_category_entertainment;
            default: return R.drawable.ic_receipt;
        }
    }

    private int getCategoryColor(String key) {
        if (key == null) return 0xFF6B7280;
        switch (key.toLowerCase()) {
            case "utilities": return 0xFFF59E0B;
            case "telecom": return 0xFF3B82F6;
            case "internet": return 0xFF10B981;
            case "tv": return 0xFF8B5CF6;
            case "insurance": return 0xFFEF4444;
            case "subscriptions": return 0xFFF59E0B;
            default: return 0xFF6B7280;
        }
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        selectorAccount.setOnClickListener(v -> showAccountSelector());

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                resetIdempotencyKey();
                validateAndUpdateButton();
            }
        };

        etClientCode.addTextChangedListener(watcher);
        etAmount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                resetIdempotencyKey();
                validateAmount();
                validateAndUpdateButton();
            }
        });

        btnPay.setOnClickListener(v -> initiatePayment());
    }

    private void loadAccounts() {
        ApiClient.getAccountService().getAccounts().enqueue(new Callback<ApiResponse<AccountsData>>() {
            @Override
            public void onResponse(Call<ApiResponse<AccountsData>> call,
                                   Response<ApiResponse<AccountsData>> response) {
                if (isFinishing() || isDestroyed()) return;

                if (response.isSuccessful() && response.body() != null &&
                        response.body().getData() != null) {
                    accounts = response.body().getData().getAccounts();
                    if (!accounts.isEmpty()) {
                        selectAccount(accounts.get(0));
                        showContent();
                        return;
                    }
                }

                SwiftBankDialog.showErrorDialog(BillPaymentActivity.this,
                        "Nu ai conturi disponibile");
            }

            @Override
            public void onFailure(Call<ApiResponse<AccountsData>> call, Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                SwiftBankDialog.showErrorDialog(BillPaymentActivity.this,
                        "Eroare la încărcarea conturilor");
            }
        });
    }

    private void showContent() {
        accountSkeleton.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> {
                    accountSkeleton.setVisibility(View.GONE);
                    selectorAccount.setVisibility(View.VISIBLE);
                    selectorAccount.setAlpha(0f);
                    selectorAccount.animate().alpha(1f).setDuration(200).start();
                }).start();
    }

    private void selectAccount(AccountData account) {
        selectedAccount = account;
        resetIdempotencyKey();
        ivAccountFlag.setImageResource(getFlagResource(account.getCurrency()));
        ivAmountFlag.setImageResource(getFlagResource(account.getCurrency()));
        tvAccountName.setText("Personal " + account.getCurrency());
        tvAccountBalance.setText("Sold: " + balanceFormat.format(account.getBalance()) +
                " " + getCurrencySymbol(account.getCurrency()));
        tvCurrency.setText(account.getCurrency());

        validateAndUpdateButton();
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

    private void validateAndUpdateButton() {
        String clientCode = etClientCode.getText().toString().trim();
        double amount = getAmount();

        boolean canPay = selectedAccount != null
                && !clientCode.isEmpty()
                && amount > 0
                && amount <= selectedAccount.getBalance()
                && !isPaying;

        btnPay.setEnabled(canPay);
        if (canPay) {
            btnPay.setBackgroundResource(R.drawable.bg_button_primary);
            btnPay.setTextColor(getResources().getColor(R.color.white, null));
        } else {
            btnPay.setBackgroundResource(R.drawable.bg_button_disabled);
            btnPay.setTextColor(getResources().getColor(R.color.white_50, null));
        }
    }

    private void initiatePayment() {
        if (isPaying) return;

        String amountStr = balanceFormat.format(getAmount()) + " " + selectedAccount.getCurrency();

        if (BiometricHelper.shouldUseBiometric(this)) {
            BiometricHelper.authenticateForTransfer(this, billerName, amountStr,
                    new BiometricHelper.BiometricCallback() {
                        @Override
                        public void onSuccess() {
                            executePayment();
                        }

                        @Override
                        public void onError(String error) {
                            if (isFinishing() || isDestroyed()) return;
                            SwiftBankDialog.showErrorDialog(BillPaymentActivity.this,
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
                "Confirmă plata",
                String.format("Introdu PIN-ul pentru a plăti %s către %s", amountStr, billerName),
                new PinConfirmDialog.PinCallback() {
                    @Override
                    public void onPinConfirmed() {
                        executePayment();
                    }

                    @Override
                    public void onCancelled() {}
                }
        ).show();
    }

    private void executePayment() {
        if (isPaying || selectedAccount == null) return;

        if (idempotencyKey == null || idempotencyKey.isEmpty()) {
            resetIdempotencyKey();
        }

        isPaying = true;
        btnPay.setText("Se procesează...");
        validateAndUpdateButton();

        CreateBillPaymentRequest request = new CreateBillPaymentRequest(
                selectedAccount.getAccountId(),
                billerId,
                etClientCode.getText().toString().trim(),
                null,
                getAmount(),
                savedBillerId
        );

        ApiClient.getBillService().createBillPayment(idempotencyKey, request)
                .enqueue(new Callback<ApiResponse<BillPaymentResultData>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<BillPaymentResultData>> call,
                                           Response<ApiResponse<BillPaymentResultData>> response) {
                        if (isFinishing() || isDestroyed()) return;

                        isPaying = false;
                        btnPay.setText("Plătește");

                        if (response.isSuccessful() && response.body() != null &&
                                response.body().isSuccess()) {
                            handlePaymentSuccess(response.body().getData());
                        } else {
                            SwiftBankDialog.showErrorDialog(BillPaymentActivity.this,
                                    getPaymentErrorMessage(response));
                            validateAndUpdateButton();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<BillPaymentResultData>> call, Throwable t) {
                        if (isFinishing() || isDestroyed()) return;

                        isPaying = false;
                        btnPay.setText("Plătește");
                        SwiftBankDialog.showErrorDialog(BillPaymentActivity.this,
                                "Eroare de conexiune");
                        validateAndUpdateButton();
                    }
                });
    }

    private String getPaymentErrorMessage(Response<?> response) {
        ApiErrorResponse error = ErrorParser.parseError(response);
        if (error != null && error.getError() != null && error.getError().getMessage() != null) {
            return error.getError().getMessage();
        }

        return "Plata nu a putut fi efectuată";
    }

    private void handlePaymentSuccess(BillPaymentResultData result) {
        String message = String.format("Ai plătit %s %s către %s",
                balanceFormat.format(result.getAmount()),
                result.getCurrency(),
                result.getBillerName());

        if (result.getReference() != null && !result.getReference().isEmpty()) {
            message += "\nReferință: " + result.getReference();
        }

        SwiftBankDialog.showSuccessDialog(this,
                "Plată reușită!",
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
            ImageView ivFlag, ivSelected;
            TextView tvAccountName, tvBalance, tvCurrency;

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
