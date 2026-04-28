package com.example.swiftbank.activities.transactions;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.swiftbank.R;
import com.example.swiftbank.api.ApiClient;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.success.AccountData;
import com.example.swiftbank.api.dto.response.data.success.AccountsData;
import com.example.swiftbank.api.dto.response.data.success.TransactionsData;
import com.example.swiftbank.api.dto.response.data.success.transaction.CardTransaction;
import com.example.swiftbank.api.dto.response.data.success.transaction.Transaction;
import com.example.swiftbank.api.dto.response.data.success.transaction.TransferTransaction;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TransactionsActivity extends AppCompatActivity {

    public static final String EXTRA_ACCOUNT_ID = "account_id";
    public static final String EXTRA_ACCOUNT_CURRENCY = "account_currency";

    private ImageView btnBack;
    private EditText etSearch;
    private RecyclerView rvTransactions;
    private LinearLayout loadingState, emptyState;

    private TransactionsAdapter adapter;
    private List<Object> allTransactionItems = new ArrayList<>();
    private List<Object> filteredTransactionItems = new ArrayList<>();
    private List<Transaction> allTransactions = new ArrayList<>();

    private int accountId = -1;
    private String accountCurrency = "RON";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transactions);

        accountId = getIntent().getIntExtra(EXTRA_ACCOUNT_ID, -1);
        accountCurrency = getIntent().getStringExtra(EXTRA_ACCOUNT_CURRENCY);
        if (accountCurrency == null) accountCurrency = "RON";

        initViews();
        setupListeners();
        setupRecyclerView();

        if (accountId == -1) {
            loadDefaultAccount();
        } else {
            loadTransactions();
        }
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        etSearch = findViewById(R.id.etSearch);
        rvTransactions = findViewById(R.id.rvTransactions);
        loadingState = findViewById(R.id.loadingState);
        emptyState = findViewById(R.id.emptyState);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterTransactions(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupRecyclerView() {
        adapter = new TransactionsAdapter(filteredTransactionItems, this::onTransactionClick);
        rvTransactions.setLayoutManager(new LinearLayoutManager(this));
        rvTransactions.setAdapter(adapter);
    }

    private void loadDefaultAccount() {
        showLoading();
        ApiClient.getAccountService().getAccounts().enqueue(new Callback<ApiResponse<AccountsData>>() {
            @Override
            public void onResponse(Call<ApiResponse<AccountsData>> call, Response<ApiResponse<AccountsData>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    List<AccountData> accounts = response.body().getData().getAccounts();
                    if (!accounts.isEmpty()) {
                        accountId = accounts.get(0).getAccountId();
                        accountCurrency = accounts.get(0).getCurrency();
                        loadTransactions();
                    } else {
                        showEmpty();
                    }
                } else {
                    showEmpty();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<AccountsData>> call, Throwable t) {
                showEmpty();
                Toast.makeText(TransactionsActivity.this, "Eroare de conexiune", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadTransactions() {
        showLoading();

        ApiClient.getTransactionService().getTransactions(accountId, 100, 0)
                .enqueue(new Callback<ApiResponse<TransactionsData>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<TransactionsData>> call, Response<ApiResponse<TransactionsData>> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                            List<Transaction> transactions = response.body().getData().getTransactions();
                            processTransactions(transactions);
                        } else {
                            showEmpty();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<TransactionsData>> call, Throwable t) {
                        showEmpty();
                        Toast.makeText(TransactionsActivity.this, "Eroare de conexiune", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void processTransactions(List<Transaction> transactions) {
        allTransactions.clear();
        allTransactions.addAll(transactions);

        allTransactionItems.clear();

        Map<String, List<Transaction>> groupedByDate = new HashMap<>();
        Map<String, Double> dailyTotals = new HashMap<>();
        List<String> dateOrder = new ArrayList<>();

        for (Transaction t : transactions) {
            String dateGroup = getDateGroup(t.getCreatedAt());

            if (!groupedByDate.containsKey(dateGroup)) {
                groupedByDate.put(dateGroup, new ArrayList<>());
                dailyTotals.put(dateGroup, 0.0);
                dateOrder.add(dateGroup);
            }

            groupedByDate.get(dateGroup).add(t);
            dailyTotals.put(dateGroup, dailyTotals.get(dateGroup) + t.getAmount());
        }

        for (String dateGroup : dateOrder) {
            allTransactionItems.add(new DateHeader(dateGroup, dailyTotals.get(dateGroup), accountCurrency));

            for (Transaction t : groupedByDate.get(dateGroup)) {
                allTransactionItems.add(createTransactionItem(t));
            }
        }

        filteredTransactionItems.clear();
        filteredTransactionItems.addAll(allTransactionItems);
        adapter.notifyDataSetChanged();

        if (filteredTransactionItems.isEmpty()) {
            showEmpty();
        } else {
            showContent();
        }
    }

    private void filterTransactions(String query) {
        filteredTransactionItems.clear();

        if (query.isEmpty()) {
            filteredTransactionItems.addAll(allTransactionItems);
        } else {
            String lowerQuery = query.toLowerCase();

            Map<String, List<TransactionItem>> groupedByDate = new HashMap<>();
            Map<String, Double> dailyTotals = new HashMap<>();
            List<String> dateOrder = new ArrayList<>();

            for (Transaction t : allTransactions) {
                String title = t.getTitle() != null ? t.getTitle().toLowerCase() : "";
                String subtitle = t.getSubtitle() != null ? t.getSubtitle().toLowerCase() : "";
                String amount = formatBalance(Math.abs(t.getAmount()));

                if (title.contains(lowerQuery) || subtitle.contains(lowerQuery) || amount.contains(query)) {
                    String dateGroup = getDateGroup(t.getCreatedAt());

                    if (!groupedByDate.containsKey(dateGroup)) {
                        groupedByDate.put(dateGroup, new ArrayList<>());
                        dailyTotals.put(dateGroup, 0.0);
                        dateOrder.add(dateGroup);
                    }

                    groupedByDate.get(dateGroup).add(createTransactionItem(t));
                    dailyTotals.put(dateGroup, dailyTotals.get(dateGroup) + t.getAmount());
                }
            }

            for (String dateGroup : dateOrder) {
                filteredTransactionItems.add(new DateHeader(dateGroup, dailyTotals.get(dateGroup), accountCurrency));
                filteredTransactionItems.addAll(groupedByDate.get(dateGroup));
            }
        }

        adapter.notifyDataSetChanged();

        if (filteredTransactionItems.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            rvTransactions.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            rvTransactions.setVisibility(View.VISIBLE);
        }
    }

    private TransactionItem createTransactionItem(Transaction t) {
        String time = formatTime(t.getCreatedAt());
        int iconRes = getIconForTransaction(t);
        String initials = null;
        String senderPhoto = null;
        boolean isExchange = false;
        double secondaryAmount = 0;
        String secondaryCurrency = null;
        String fromCurrency = null;
        String toCurrency = null;

        if (t instanceof TransferTransaction) {
            TransferTransaction transfer = (TransferTransaction) t;

            String transactionType = t.getTransactionType();
            boolean isOwnAccountTransfer = "SELF_IN".equals(transactionType) || "SELF_OUT".equals(transactionType);

            if (isOwnAccountTransfer && transfer.hasCurrencyConversion()) {
                isExchange = true;
                secondaryAmount = transfer.getOriginalAmount() != null ? transfer.getOriginalAmount() : 0;
                secondaryCurrency = transfer.getOriginalCurrency();
                if (t.getAmount() > 0) {
                    fromCurrency = transfer.getOriginalCurrency();
                    toCurrency = t.getCurrency();
                } else {
                    fromCurrency = t.getCurrency();
                    toCurrency = transfer.getOriginalCurrency();
                }
            } else if (!isOwnAccountTransfer && transfer.getBeneficiaryName() != null) {
                initials = getInitials(transfer.getBeneficiaryName());
                senderPhoto = transfer.getSenderPhoto();
            }
        }

        return new TransactionItem(
                t,
                t.getTitle(),
                t.getSubtitle(),
                t.getAmount(),
                t.getCurrency() != null ? t.getCurrency() : accountCurrency,
                time,
                iconRes,
                initials,
                senderPhoto,
                isExchange,
                secondaryAmount,
                secondaryCurrency,
                fromCurrency,
                toCurrency
        );
    }

    private void onTransactionClick(TransactionItem item) {
        Intent intent = new Intent(this, TransactionDetailsActivity.class);
        intent.putExtra(TransactionDetailsActivity.EXTRA_TRANSACTION_ID, item.transaction.getId());
        intent.putExtra(TransactionDetailsActivity.EXTRA_TRANSACTION_TYPE, item.transaction.getTransactionType());
        intent.putExtra(TransactionDetailsActivity.EXTRA_ACCOUNT_CURRENCY, accountCurrency);
        startActivity(intent);
    }

    private void showLoading() {
        loadingState.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.GONE);
        rvTransactions.setVisibility(View.GONE);
    }

    private void showEmpty() {
        loadingState.setVisibility(View.GONE);
        emptyState.setVisibility(View.VISIBLE);
        rvTransactions.setVisibility(View.GONE);
    }

    private void showContent() {
        loadingState.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
        rvTransactions.setVisibility(View.VISIBLE);
    }

    private String getDateGroup(String createdAt) {
        if (createdAt == null) return "Altele";
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            Date date = sdf.parse(createdAt);
            if (date == null) return "Altele";

            Calendar transactionCal = Calendar.getInstance();
            transactionCal.setTime(date);

            Calendar today = Calendar.getInstance();
            Calendar yesterday = Calendar.getInstance();
            yesterday.add(Calendar.DAY_OF_YEAR, -1);

            if (isSameDay(transactionCal, today)) {
                return "Azi";
            } else if (isSameDay(transactionCal, yesterday)) {
                return "Ieri";
            } else {
                SimpleDateFormat outputFormat = new SimpleDateFormat("d MMM", new Locale("ro"));
                return outputFormat.format(date);
            }
        } catch (Exception e) {
            return "Altele";
        }
    }

    private boolean isSameDay(Calendar cal1, Calendar cal2) {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

    private String formatTime(String createdAt) {
        if (createdAt == null) return "";
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            Date date = sdf.parse(createdAt);
            SimpleDateFormat outputFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            return outputFormat.format(date);
        } catch (Exception e) {
            return "";
        }
    }

    private int getIconForTransaction(Transaction t) {
        String type = t.getTransactionType();
        if (type == null) return R.drawable.ic_shopping;

        if (t instanceof TransferTransaction) {
            TransferTransaction transfer = (TransferTransaction) t;
            if (transfer.hasCurrencyConversion()) {
                return R.drawable.ic_exchange;
            }
        }

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

    // Data classes
    static class DateHeader {
        String date;
        double total;
        String currency;

        DateHeader(String date, double total, String currency) {
            this.date = date;
            this.total = total;
            this.currency = currency;
        }
    }

    static class TransactionItem {
        Transaction transaction;
        String title;
        String subtitle;
        double amount;
        String currency;
        String time;
        int iconRes;
        String initials;
        String senderPhoto;
        boolean isExchange;
        double secondaryAmount;
        String secondaryCurrency;
        String fromCurrency;
        String toCurrency;

        TransactionItem(Transaction transaction, String title, String subtitle, double amount, String currency,
                        String time, int iconRes, String initials, String senderPhoto,
                        boolean isExchange, double secondaryAmount, String secondaryCurrency,
                        String fromCurrency, String toCurrency) {
            this.transaction = transaction;
            this.title = title;
            this.subtitle = subtitle;
            this.amount = amount;
            this.currency = currency;
            this.time = time;
            this.iconRes = iconRes;
            this.initials = initials;
            this.senderPhoto = senderPhoto;
            this.isExchange = isExchange;
            this.secondaryAmount = secondaryAmount;
            this.secondaryCurrency = secondaryCurrency;
            this.fromCurrency = fromCurrency;
            this.toCurrency = toCurrency;
        }
    }

    // Adapter
    class TransactionsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_HEADER = 0;
        private static final int TYPE_TRANSACTION = 1;

        private List<Object> items;
        private OnTransactionClickListener listener;

        interface OnTransactionClickListener {
            void onClick(TransactionItem item);
        }

        TransactionsAdapter(List<Object> items, OnTransactionClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position) instanceof DateHeader ? TYPE_HEADER : TYPE_TRANSACTION;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_HEADER) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_transaction_date_header, parent, false);
                return new HeaderViewHolder(view);
            } else {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_transaction, parent, false);
                return new TransactionViewHolder(view);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof HeaderViewHolder) {
                ((HeaderViewHolder) holder).bind((DateHeader) items.get(position));
            } else {
                ((TransactionViewHolder) holder).bind((TransactionItem) items.get(position));
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class HeaderViewHolder extends RecyclerView.ViewHolder {
            TextView tvDate, tvTotal;

            HeaderViewHolder(@NonNull View itemView) {
                super(itemView);
                tvDate = itemView.findViewById(R.id.tvDate);
                tvTotal = itemView.findViewById(R.id.tvTotal);
            }

            void bind(DateHeader header) {
                tvDate.setText(header.date);

                String totalText;
                if (header.total >= 0) {
                    totalText = "+" + formatBalance(header.total) + " " + getCurrencySymbol(header.currency);
                    tvTotal.setTextColor(ContextCompat.getColor(TransactionsActivity.this, R.color.green_accent));
                } else {
                    totalText = formatBalance(header.total) + " " + getCurrencySymbol(header.currency);
                    tvTotal.setTextColor(ContextCompat.getColor(TransactionsActivity.this, R.color.white));
                }
                tvTotal.setText(totalText);
            }
        }

        class TransactionViewHolder extends RecyclerView.ViewHolder {
            ImageView ivCategoryIcon;
            TextView tvMerchantName, tvCategory, tvAmount, tvTime;
            View cardIcon, cardInitials, exchangeIconContainer;
            TextView tvInitials, tvSecondaryAmount;
            ImageView ivFlagFrom, ivFlagTo, ivSenderPhoto;

            TransactionViewHolder(@NonNull View itemView) {
                super(itemView);
                ivCategoryIcon = itemView.findViewById(R.id.ivCategoryIcon);
                tvMerchantName = itemView.findViewById(R.id.tvMerchantName);
                tvCategory = itemView.findViewById(R.id.tvCategory);
                tvAmount = itemView.findViewById(R.id.tvAmount);
                tvTime = itemView.findViewById(R.id.tvTime);
                cardIcon = itemView.findViewById(R.id.cardIcon);
                cardInitials = itemView.findViewById(R.id.cardInitials);
                tvInitials = itemView.findViewById(R.id.tvInitials);
                ivSenderPhoto = itemView.findViewById(R.id.ivSenderPhoto);
                tvSecondaryAmount = itemView.findViewById(R.id.tvSecondaryAmount);
                exchangeIconContainer = itemView.findViewById(R.id.exchangeIconContainer);
                ivFlagFrom = itemView.findViewById(R.id.ivFlagFrom);
                ivFlagTo = itemView.findViewById(R.id.ivFlagTo);

                itemView.setOnClickListener(v -> {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION && items.get(pos) instanceof TransactionItem) {
                        listener.onClick((TransactionItem) items.get(pos));
                    }
                });
            }

            void bind(TransactionItem item) {
                tvMerchantName.setText(item.title);
                tvCategory.setText(item.time);
                tvTime.setVisibility(View.GONE);

                String amountText;
                if (item.amount >= 0) {
                    amountText = "+" + formatBalance(item.amount) + " " + getCurrencySymbol(item.currency);
                    tvAmount.setTextColor(ContextCompat.getColor(TransactionsActivity.this, R.color.green_accent));
                } else {
                    amountText = formatBalance(item.amount) + " " + getCurrencySymbol(item.currency);
                    tvAmount.setTextColor(ContextCompat.getColor(TransactionsActivity.this, R.color.white));
                }
                tvAmount.setText(amountText);

                if (item.isExchange && tvSecondaryAmount != null) {
                    String secondaryText;
                    if (item.amount < 0) {
                        secondaryText = "+" + formatBalance(item.secondaryAmount) + " " + getCurrencySymbol(item.secondaryCurrency);
                    } else {
                        secondaryText = "-" + formatBalance(item.secondaryAmount) + " " + getCurrencySymbol(item.secondaryCurrency);
                    }
                    tvSecondaryAmount.setText(secondaryText);
                    tvSecondaryAmount.setVisibility(View.VISIBLE);
                } else if (tvSecondaryAmount != null) {
                    tvSecondaryAmount.setVisibility(View.GONE);
                }

                if (item.isExchange && exchangeIconContainer != null) {
                    cardIcon.setVisibility(View.GONE);
                    cardInitials.setVisibility(View.GONE);
                    exchangeIconContainer.setVisibility(View.VISIBLE);
                    ivFlagFrom.setImageResource(getFlagForCurrency(item.fromCurrency));
                    ivFlagTo.setImageResource(getFlagForCurrency(item.toCurrency));
                } else if (item.initials != null && cardInitials != null) {
                    cardIcon.setVisibility(View.GONE);
                    cardInitials.setVisibility(View.VISIBLE);
                    if (exchangeIconContainer != null) exchangeIconContainer.setVisibility(View.GONE);

                    if (item.senderPhoto != null && !item.senderPhoto.isEmpty() && ivSenderPhoto != null) {
                        try {
                            String base64Data = item.senderPhoto;
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
                                showInitials(item.initials);
                            }
                        } catch (Exception e) {
                            showInitials(item.initials);
                        }
                    } else {
                        showInitials(item.initials);
                    }
                } else {
                    cardIcon.setVisibility(View.VISIBLE);
                    if (cardInitials != null) cardInitials.setVisibility(View.GONE);
                    if (exchangeIconContainer != null) exchangeIconContainer.setVisibility(View.GONE);
                    ivCategoryIcon.setImageResource(item.iconRes);
                }
            }

            private void showInitials(String initials) {
                if (ivSenderPhoto != null) ivSenderPhoto.setVisibility(View.GONE);
                tvInitials.setVisibility(View.VISIBLE);
                tvInitials.setText(initials);
            }

            private int getFlagForCurrency(String currency) {
                if (currency == null) return R.drawable.flag_ro;
                switch (currency.trim().toUpperCase()) {
                    case "EUR": return R.drawable.flag_eur;
                    case "USD": return R.drawable.flag_usd;
                    case "GBP": return R.drawable.flag_gbp;
                    default: return R.drawable.flag_ro;
                }
            }
        }
    }
}
