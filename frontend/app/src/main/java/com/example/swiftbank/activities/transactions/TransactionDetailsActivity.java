package com.example.swiftbank.activities.transactions;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.example.swiftbank.R;
import com.example.swiftbank.api.ApiClient;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.success.TransactionsData;
import com.example.swiftbank.api.dto.response.data.success.transaction.BillTransaction;
import com.example.swiftbank.api.dto.response.data.success.transaction.CardTransaction;
import com.example.swiftbank.api.dto.response.data.success.transaction.Transaction;
import com.example.swiftbank.api.dto.response.data.success.transaction.TransferTransaction;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TransactionDetailsActivity extends AppCompatActivity {

    public static final String EXTRA_TRANSACTION_ID = "transaction_id";
    public static final String EXTRA_TRANSACTION_TYPE = "transaction_type";
    public static final String EXTRA_ACCOUNT_CURRENCY = "account_currency";

    private ImageView btnBack, btnMore;
    private TextView tvTitle;

    // Icon section
    private CardView cardIcon, cardInitials;
    private ImageView ivIcon, ivSenderPhoto;
    private TextView tvInitials;

    // Amount section
    private TextView tvAmount, tvStatus, tvDate;

    // Details sections
    private LinearLayout sectionPaymentInfo, sectionTransferInfo, sectionCardInfo, sectionBillInfo;

    // Payment Info
    private TextView tvAccountName, tvAccountCurrency;

    // Transfer Info
    private TextView tvBeneficiaryName, tvIban, tvBankName, tvDescription, tvReference;
    private LinearLayout rowBankName, rowDescription, rowReference;

    // Card Info
    private TextView tvMerchantName, tvLocation, tvCardUsed;
    private LinearLayout rowLocation;

    // Bill Info
    private TextView tvBillerName, tvBillerCategory, tvClientCode, tvInvoiceRef;

    // Amount breakdown
    private LinearLayout sectionAmountBreakdown;
    private TextView tvOriginalAmount, tvFees, tvTotalAmount;
    private LinearLayout rowOriginalAmount, rowFees;

    // Loading
    private LinearLayout loadingState;
    private View contentContainer;

    private int transactionId;
    private String transactionType;
    private String accountCurrency;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_details);

        transactionId = getIntent().getIntExtra(EXTRA_TRANSACTION_ID, -1);
        transactionType = getIntent().getStringExtra(EXTRA_TRANSACTION_TYPE);
        accountCurrency = getIntent().getStringExtra(EXTRA_ACCOUNT_CURRENCY);
        if (accountCurrency == null) accountCurrency = "RON";

        initViews();
        setupListeners();

        if (transactionId != -1) {
            loadTransactionDetails();
        } else {
            Toast.makeText(this, "Tranzacție invalidă", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        btnMore = findViewById(R.id.btnMore);
        tvTitle = findViewById(R.id.tvTitle);

        cardIcon = findViewById(R.id.cardIcon);
        cardInitials = findViewById(R.id.cardInitials);
        ivIcon = findViewById(R.id.ivIcon);
        tvInitials = findViewById(R.id.tvInitials);
        ivSenderPhoto = findViewById(R.id.ivSenderPhoto);

        tvAmount = findViewById(R.id.tvAmount);
        tvStatus = findViewById(R.id.tvStatus);
        tvDate = findViewById(R.id.tvDate);

        sectionPaymentInfo = findViewById(R.id.sectionPaymentInfo);
        sectionTransferInfo = findViewById(R.id.sectionTransferInfo);
        sectionCardInfo = findViewById(R.id.sectionCardInfo);
        sectionBillInfo = findViewById(R.id.sectionBillInfo);

        tvAccountName = findViewById(R.id.tvAccountName);
        tvAccountCurrency = findViewById(R.id.tvAccountCurrency);

        tvBeneficiaryName = findViewById(R.id.tvBeneficiaryName);
        tvIban = findViewById(R.id.tvIban);
        tvBankName = findViewById(R.id.tvBankName);
        tvDescription = findViewById(R.id.tvDescription);
        tvReference = findViewById(R.id.tvReference);
        rowBankName = findViewById(R.id.rowBankName);
        rowDescription = findViewById(R.id.rowDescription);
        rowReference = findViewById(R.id.rowReference);

        tvMerchantName = findViewById(R.id.tvMerchantName);
        tvLocation = findViewById(R.id.tvLocation);
        tvCardUsed = findViewById(R.id.tvCardUsed);
        rowLocation = findViewById(R.id.rowLocation);

        tvBillerName = findViewById(R.id.tvBillerName);
        tvBillerCategory = findViewById(R.id.tvBillerCategory);
        tvClientCode = findViewById(R.id.tvClientCode);
        tvInvoiceRef = findViewById(R.id.tvInvoiceRef);

        sectionAmountBreakdown = findViewById(R.id.sectionAmountBreakdown);
        tvOriginalAmount = findViewById(R.id.tvOriginalAmount);
        tvFees = findViewById(R.id.tvFees);
        tvTotalAmount = findViewById(R.id.tvTotalAmount);
        rowOriginalAmount = findViewById(R.id.rowOriginalAmount);
        rowFees = findViewById(R.id.rowFees);

        loadingState = findViewById(R.id.loadingState);
        contentContainer = findViewById(R.id.contentContainer);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnMore.setOnClickListener(v -> {
            Toast.makeText(this, "Mai multe opțiuni - Coming soon", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadTransactionDetails() {
        showLoading();

        // Since we don't have a direct getTransactionById endpoint,
        // we'll load all transactions and find the one we need
        // In production, you'd want a dedicated endpoint

        ApiClient.getAccountService().getAccounts().enqueue(new Callback<ApiResponse<com.example.swiftbank.api.dto.response.data.success.AccountsData>>() {
            @Override
            public void onResponse(Call<ApiResponse<com.example.swiftbank.api.dto.response.data.success.AccountsData>> call,
                                   Response<ApiResponse<com.example.swiftbank.api.dto.response.data.success.AccountsData>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    List<com.example.swiftbank.api.dto.response.data.success.AccountData> accounts =
                            response.body().getData().getAccounts();

                    for (com.example.swiftbank.api.dto.response.data.success.AccountData account : accounts) {
                        loadTransactionsForAccount(account.getAccountId());
                    }
                } else {
                    showError();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<com.example.swiftbank.api.dto.response.data.success.AccountsData>> call, Throwable t) {
                showError();
            }
        });
    }

    private void loadTransactionsForAccount(int accountId) {
        ApiClient.getTransactionService().getTransactions(accountId, 100, 0)
                .enqueue(new Callback<ApiResponse<TransactionsData>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<TransactionsData>> call,
                                           Response<ApiResponse<TransactionsData>> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                            List<Transaction> transactions = response.body().getData().getTransactions();

                            for (Transaction t : transactions) {
                                if (t.getId() == transactionId) {
                                    displayTransaction(t);
                                    return;
                                }
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<TransactionsData>> call, Throwable t) {
                        // Silent fail - maybe transaction is in another account
                    }
                });
    }

    private void displayTransaction(Transaction transaction) {
        showContent();

        // Title
        tvTitle.setText(transaction.getTitle());

        // Amount
        String amountText;
        if (transaction.getAmount() >= 0) {
            amountText = "+" + formatBalance(transaction.getAmount()) + " " + getCurrencySymbol(transaction.getCurrency());
            tvAmount.setTextColor(ContextCompat.getColor(this, R.color.green_accent));
        } else {
            amountText = formatBalance(transaction.getAmount()) + " " + getCurrencySymbol(transaction.getCurrency());
            tvAmount.setTextColor(ContextCompat.getColor(this, R.color.white));
        }
        tvAmount.setText(amountText);

        // Status
        String status = transaction.getStatus();
        if ("COMPLETED".equals(status)) {
            tvStatus.setText("Finalizată");
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.green_accent));
        } else if ("PENDING".equals(status)) {
            tvStatus.setText("În așteptare");
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.warning_color));
        } else {
            tvStatus.setText(status);
        }

        // Date
        tvDate.setText(formatFullDate(transaction.getCreatedAt()));

        // Payment info (shown for all)
        sectionPaymentInfo.setVisibility(View.VISIBLE);
        tvAccountName.setText("Cont " + accountCurrency);
        tvAccountCurrency.setText(accountCurrency);

        // Icon
        setupIcon(transaction);

        // Type-specific sections
        if (transaction instanceof CardTransaction) {
            displayCardTransaction((CardTransaction) transaction);
        } else if (transaction instanceof TransferTransaction) {
            displayTransferTransaction((TransferTransaction) transaction);
        } else if (transaction instanceof BillTransaction) {
            displayBillTransaction((BillTransaction) transaction);
        }

        // Amount breakdown
        displayAmountBreakdown(transaction);
    }

    private void setupIcon(Transaction transaction) {
        String type = transaction.getTransactionType();

        if (transaction instanceof TransferTransaction) {
            TransferTransaction transfer = (TransferTransaction) transaction;
            String beneficiary = transfer.getBeneficiaryName();

            if (beneficiary != null && !beneficiary.isEmpty() &&
                !"SELF_IN".equals(type) && !"SELF_OUT".equals(type)) {
                // Show initials/photo for person transfers
                cardIcon.setVisibility(View.GONE);
                cardInitials.setVisibility(View.VISIBLE);

                String senderPhoto = transfer.getSenderPhoto();
                if (senderPhoto != null && !senderPhoto.isEmpty()) {
                    try {
                        String base64Data = senderPhoto;
                        if (base64Data.contains(",")) {
                            base64Data = base64Data.substring(base64Data.indexOf(",") + 1);
                        }
                        byte[] decodedBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT);
                        android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                        if (bitmap != null) {
                            ivSenderPhoto.setImageBitmap(bitmap);
                            ivSenderPhoto.setVisibility(View.VISIBLE);
                            tvInitials.setVisibility(View.GONE);
                        } else {
                            ivSenderPhoto.setVisibility(View.GONE);
                            tvInitials.setVisibility(View.VISIBLE);
                            tvInitials.setText(getInitials(beneficiary));
                        }
                    } catch (Exception e) {
                        ivSenderPhoto.setVisibility(View.GONE);
                        tvInitials.setVisibility(View.VISIBLE);
                        tvInitials.setText(getInitials(beneficiary));
                    }
                } else {
                    ivSenderPhoto.setVisibility(View.GONE);
                    tvInitials.setVisibility(View.VISIBLE);
                    tvInitials.setText(getInitials(beneficiary));
                }
                return;
            }
        }

        // Show icon
        cardIcon.setVisibility(View.VISIBLE);
        cardInitials.setVisibility(View.GONE);
        ivIcon.setImageResource(getIconForTransaction(transaction));
    }

    private void displayCardTransaction(CardTransaction transaction) {
        sectionCardInfo.setVisibility(View.VISIBLE);
        sectionTransferInfo.setVisibility(View.GONE);
        sectionBillInfo.setVisibility(View.GONE);

        tvMerchantName.setText(transaction.getMerchantName() != null ?
                transaction.getMerchantName() : transaction.getTitle());

        if (transaction.getLocation() != null && !transaction.getLocation().isEmpty()) {
            rowLocation.setVisibility(View.VISIBLE);
            tvLocation.setText(transaction.getLocation());
        } else {
            rowLocation.setVisibility(View.GONE);
        }

        if (transaction.getCardNumberMasked() != null) {
            tvCardUsed.setText("•••• " + transaction.getCardNumberMasked());
        } else {
            tvCardUsed.setText("Card virtual");
        }
    }

    private void displayTransferTransaction(TransferTransaction transaction) {
        sectionTransferInfo.setVisibility(View.VISIBLE);
        sectionCardInfo.setVisibility(View.GONE);
        sectionBillInfo.setVisibility(View.GONE);

        String type = transaction.getTransactionType();
        boolean isSelfTransfer = "SELF_IN".equals(type) || "SELF_OUT".equals(type);

        if (isSelfTransfer) {
            tvBeneficiaryName.setText("Transfer între conturi proprii");
            tvIban.setText("-");
            rowBankName.setVisibility(View.GONE);
        } else {
            tvBeneficiaryName.setText(transaction.getBeneficiaryName() != null ?
                    transaction.getBeneficiaryName() : "-");
            tvIban.setText(transaction.getIban() != null ?
                    formatIban(transaction.getIban()) : "-");

            if (transaction.getBankName() != null && !transaction.getBankName().isEmpty()) {
                rowBankName.setVisibility(View.VISIBLE);
                tvBankName.setText(transaction.getBankName());
            } else {
                rowBankName.setVisibility(View.GONE);
            }
        }

        // Description (what user wrote)
        if (transaction.getDescription() != null && !transaction.getDescription().isEmpty()) {
            rowDescription.setVisibility(View.VISIBLE);
            tvDescription.setText(transaction.getDescription());
        } else {
            rowDescription.setVisibility(View.GONE);
        }

        // Reference (system reference)
        if (transaction.getReference() != null && !transaction.getReference().isEmpty()) {
            rowReference.setVisibility(View.VISIBLE);
            tvReference.setText(transaction.getReference());
        } else {
            rowReference.setVisibility(View.GONE);
        }
    }

    private void displayBillTransaction(BillTransaction transaction) {
        sectionBillInfo.setVisibility(View.VISIBLE);
        sectionTransferInfo.setVisibility(View.GONE);
        sectionCardInfo.setVisibility(View.GONE);

        tvBillerName.setText(transaction.getBillerName() != null ?
                transaction.getBillerName() : transaction.getTitle());
        tvBillerCategory.setText(transaction.getBillerCategory() != null ?
                transaction.getBillerCategory() : "-");
        tvClientCode.setText(transaction.getClientCode() != null ?
                transaction.getClientCode() : "-");
        tvInvoiceRef.setText(transaction.getInvoiceReference() != null ?
                transaction.getInvoiceReference() : "-");
    }

    private void displayAmountBreakdown(Transaction transaction) {
        sectionAmountBreakdown.setVisibility(View.VISIBLE);

        double totalAmount = Math.abs(transaction.getAmount());
        double fees = 0;

        // Check for currency conversion
        if (transaction instanceof TransferTransaction) {
            TransferTransaction transfer = (TransferTransaction) transaction;
            if (transfer.hasCurrencyConversion() && transfer.getOriginalAmount() != null) {
                rowOriginalAmount.setVisibility(View.VISIBLE);
                tvOriginalAmount.setText(formatBalance(Math.abs(transfer.getOriginalAmount())) +
                        " " + getCurrencySymbol(transfer.getOriginalCurrency()));
            } else {
                rowOriginalAmount.setVisibility(View.GONE);
            }
        } else if (transaction instanceof CardTransaction) {
            CardTransaction card = (CardTransaction) transaction;
            if (card.hasCurrencyConversion() && card.getOriginalAmount() != null) {
                rowOriginalAmount.setVisibility(View.VISIBLE);
                tvOriginalAmount.setText(formatBalance(Math.abs(card.getOriginalAmount())) +
                        " " + getCurrencySymbol(card.getOriginalCurrency()));
            } else {
                rowOriginalAmount.setVisibility(View.GONE);
            }
        } else {
            rowOriginalAmount.setVisibility(View.GONE);
        }

        // Fees (for now always 0)
        rowFees.setVisibility(View.VISIBLE);
        tvFees.setText("Fără comision");

        // Total
        tvTotalAmount.setText(formatBalance(totalAmount) + " " + getCurrencySymbol(transaction.getCurrency()));
    }

    private void showLoading() {
        loadingState.setVisibility(View.VISIBLE);
        contentContainer.setVisibility(View.GONE);
    }

    private void showContent() {
        loadingState.setVisibility(View.GONE);
        contentContainer.setVisibility(View.VISIBLE);
    }

    private void showError() {
        Toast.makeText(this, "Eroare la încărcarea detaliilor", Toast.LENGTH_SHORT).show();
        finish();
    }

    private String formatBalance(double balance) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.getDefault());
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        DecimalFormat df = new DecimalFormat("#,##0.00", symbols);
        return df.format(balance);
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

    private String formatFullDate(String createdAt) {
        if (createdAt == null) return "";
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            Date date = sdf.parse(createdAt);
            SimpleDateFormat outputFormat = new SimpleDateFormat("d MMMM yyyy, HH:mm", new Locale("ro"));
            return outputFormat.format(date);
        } catch (Exception e) {
            return createdAt;
        }
    }

    private String formatIban(String iban) {
        if (iban == null) return "";
        // Format: RO49 AAAA 1B31 0075 9384 0000
        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i < iban.length(); i++) {
            if (i > 0 && i % 4 == 0) {
                formatted.append(" ");
            }
            formatted.append(iban.charAt(i));
        }
        return formatted.toString();
    }

    private String getInitials(String name) {
        if (name == null || name.isEmpty()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2) {
            return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
        } else if (parts.length == 1 && parts[0].length() >= 2) {
            return parts[0].substring(0, 2).toUpperCase();
        }
        return parts[0].substring(0, 1).toUpperCase();
    }

    private int getIconForTransaction(Transaction t) {
        String type = t.getTransactionType();
        if (type == null) return R.drawable.ic_shopping;

        switch (type) {
            case "SELF_IN":
            case "SELF_OUT":
                return R.drawable.ic_exchange;
            case "TRANSFER":
            case "TRANSFER_IN":
                return R.drawable.ic_transfer_in;
            case "TRANSFER_OUT":
                return R.drawable.ic_transfer_out;
            case "BILL":
                return R.drawable.ic_payments;
            case "CARD":
            default:
                return R.drawable.ic_shopping;
        }
    }
}
