package com.example.swiftbank.activities.transactions;

import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.example.swiftbank.activities.cards.CardPaymentApprovalActivity;
import com.example.swiftbank.api.ApiClient;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.success.AccountData;
import com.example.swiftbank.api.dto.response.data.success.AccountsData;
import com.example.swiftbank.api.dto.response.data.success.ProfileData;
import com.example.swiftbank.api.dto.response.data.success.TransactionsData;
import com.example.swiftbank.api.dto.response.data.success.transaction.BillTransaction;
import com.example.swiftbank.api.dto.response.data.success.transaction.CardTransaction;
import com.example.swiftbank.api.dto.response.data.success.transaction.Transaction;
import com.example.swiftbank.api.dto.response.data.success.transaction.TransferTransaction;
import com.example.swiftbank.managers.RealtimeManager;
import com.example.swiftbank.utils.ExchangeTitleFormatter;
import com.example.swiftbank.utils.RemoteImageLoader;
import com.google.gson.JsonObject;

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
    private static final int REQUEST_CARD_PAYMENT_APPROVAL = 2401;
    private static final String ACTION_REFRESH_DATA = "com.example.swiftbank.REFRESH_DATA";
    private static final long DATA_REFRESH_DEBOUNCE_MS = 350L;

    private ImageView btnBack, ivAccountFlag;
    private EditText etSearch;
    private TextView tvAccountName;
    private RecyclerView rvTransactions;
    private LinearLayout loadingState, emptyState;

    private TransactionsAdapter adapter;
    private List<Object> allTransactionItems = new ArrayList<>();
    private List<Object> filteredTransactionItems = new ArrayList<>();
    private List<Transaction> allTransactions = new ArrayList<>();

    private int accountId = -1;
    private String accountCurrency = "RON";
    private int currentUserId = -1;
    private boolean refreshReceiverRegistered = false;

    // Pagination
    private static final int PAGE_SIZE = 20;
    private int currentOffset = 0;
    private boolean hasMore = true;
    private boolean isLoadingMore = false;
    private RealtimeManager.RealtimeListener realtimeListener;
    private boolean transactionsRequestInFlight = false;
    private boolean queuedTransactionsRefresh = false;
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable transactionsRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshTransactionsNow();
        }
    };
    private Call<ApiResponse<TransactionsData>> transactionsCall;

    private final BroadcastReceiver refreshReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (accountId != -1) {
                requestTransactionsRefresh();
            }
        }
    };

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
        registerRefreshReceiver();
        loadProfileForRealtime();

        if (accountId == -1) {
            loadDefaultAccount();
        } else {
            loadTransactions();
        }
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        etSearch = findViewById(R.id.etSearch);
        ivAccountFlag = findViewById(R.id.ivAccountFlag);
        tvAccountName = findViewById(R.id.tvAccountName);
        rvTransactions = findViewById(R.id.rvTransactions);
        loadingState = findViewById(R.id.loadingState);
        emptyState = findViewById(R.id.emptyState);

        updateAccountDisplay();
    }

    private void updateAccountDisplay() {
        tvAccountName.setText(accountCurrency);
        ivAccountFlag.setImageResource(getFlagResource(accountCurrency));
    }

    private int getFlagResource(String currency) {
        if (currency == null) return R.drawable.flag_ro;
        switch (currency.trim().toUpperCase()) {
            case "EUR": return R.drawable.flag_eur;
            case "USD": return R.drawable.flag_usd;
            case "GBP": return R.drawable.flag_gbp;
            case "RON": return R.drawable.flag_ro;
            default: return R.drawable.flag_ro;
        }
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
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        rvTransactions.setLayoutManager(layoutManager);
        rvTransactions.setAdapter(adapter);

        // Infinite scroll listener
        rvTransactions.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                if (dy > 0 && hasMore && !isLoadingMore && !transactionsRequestInFlight) {
                    int visibleItemCount = layoutManager.getChildCount();
                    int totalItemCount = layoutManager.getItemCount();
                    int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 3) {
                        loadMoreTransactions();
                    }
                }
            }
        });
    }

    private void loadProfileForRealtime() {
        ApiClient.getUserService().getProfile().enqueue(new Callback<ApiResponse<ProfileData>>() {
            @Override
            public void onResponse(Call<ApiResponse<ProfileData>> call, Response<ApiResponse<ProfileData>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    currentUserId = response.body().getData().getUserId();
                    setupRealtimeSubscription();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<ProfileData>> call, Throwable t) {
                // Realtime is optional here; manual/FCM refresh still works.
            }
        });
    }

    private void setupRealtimeSubscription() {
        if (currentUserId == -1 || realtimeListener != null) return;

        RealtimeManager realtime = RealtimeManager.getInstance();
        realtime.connect();

        realtimeListener = new RealtimeManager.RealtimeListener() {
            @Override
            public void onInsert(String table, JsonObject newRecord) {
                refreshTransactionsFromRealtime();
            }

            @Override
            public void onUpdate(String table, JsonObject oldRecord, JsonObject newRecord) {
                refreshTransactionsFromRealtime();
            }

            @Override
            public void onDelete(String table, JsonObject oldRecord) {
                refreshTransactionsFromRealtime();
            }
        };

        String userId = String.valueOf(currentUserId);
        realtime.subscribeToUserChanges("accounts", userId, realtimeListener);
        realtime.subscribeToUserChanges("card_payment_sessions", userId, realtimeListener);
    }

    private void refreshTransactionsFromRealtime() {
        requestTransactionsRefresh();
    }

    private void requestTransactionsRefresh() {
        refreshHandler.removeCallbacks(transactionsRefreshRunnable);
        refreshHandler.postDelayed(transactionsRefreshRunnable, DATA_REFRESH_DEBOUNCE_MS);
    }

    private void refreshTransactionsNow() {
        if (accountId == -1) {
            loadDefaultAccount();
        } else {
            loadTransactions();
        }
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
                        updateAccountDisplay();
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
        if (transactionsRequestInFlight) {
            queuedTransactionsRefresh = true;
            return;
        }

        // Reset pagination
        currentOffset = 0;
        hasMore = true;
        allTransactions.clear();
        allTransactionItems.clear();

        transactionsRequestInFlight = true;
        showLoading();

        transactionsCall = ApiClient.getTransactionService().getTransactions(accountId, PAGE_SIZE, 0);
        transactionsCall
                .enqueue(new Callback<ApiResponse<TransactionsData>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<TransactionsData>> call, Response<ApiResponse<TransactionsData>> response) {
                        if (call.isCanceled()) {
                            finishTransactionsRequest();
                            return;
                        }

                        if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                            TransactionsData data = response.body().getData();
                            List<Transaction> transactions = data.getTransactions();

                            if (data.getPagination() != null) {
                                hasMore = data.getPagination().hasMore();
                            } else {
                                hasMore = transactions.size() >= PAGE_SIZE;
                            }
                            currentOffset = transactions.size();

                            processTransactions(transactions);
                        } else {
                            showEmpty();
                        }
                        finishTransactionsRequest();
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<TransactionsData>> call, Throwable t) {
                        if (call.isCanceled()) {
                            finishTransactionsRequest();
                            return;
                        }
                        showEmpty();
                        Toast.makeText(TransactionsActivity.this, "Eroare de conexiune", Toast.LENGTH_SHORT).show();
                        finishTransactionsRequest();
                    }
                });
    }

    private void loadMoreTransactions() {
        if (isLoadingMore || !hasMore) return;

        isLoadingMore = true;

        // Add loading skeleton
        filteredTransactionItems.add(new LoadingSkeleton());
        adapter.notifyItemInserted(filteredTransactionItems.size() - 1);

        ApiClient.getTransactionService().getTransactions(accountId, PAGE_SIZE, currentOffset)
                .enqueue(new Callback<ApiResponse<TransactionsData>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<TransactionsData>> call, Response<ApiResponse<TransactionsData>> response) {
                        // Remove loading skeleton
                        int skeletonIndex = filteredTransactionItems.size() - 1;
                        if (skeletonIndex >= 0 && filteredTransactionItems.get(skeletonIndex) instanceof LoadingSkeleton) {
                            filteredTransactionItems.remove(skeletonIndex);
                            adapter.notifyItemRemoved(skeletonIndex);
                        }

                        if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                            TransactionsData data = response.body().getData();
                            List<Transaction> newTransactions = data.getTransactions();

                            if (data.getPagination() != null) {
                                hasMore = data.getPagination().hasMore();
                            } else {
                                hasMore = newTransactions.size() >= PAGE_SIZE;
                            }
                            currentOffset += newTransactions.size();

                            appendTransactions(newTransactions);
                        }
                        isLoadingMore = false;
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<TransactionsData>> call, Throwable t) {
                        // Remove loading skeleton
                        int skeletonIndex = filteredTransactionItems.size() - 1;
                        if (skeletonIndex >= 0 && filteredTransactionItems.get(skeletonIndex) instanceof LoadingSkeleton) {
                            filteredTransactionItems.remove(skeletonIndex);
                            adapter.notifyItemRemoved(skeletonIndex);
                        }
                        isLoadingMore = false;
                    }
                });
    }

    private void finishTransactionsRequest() {
        transactionsRequestInFlight = false;
        transactionsCall = null;

        if (queuedTransactionsRefresh) {
            queuedTransactionsRefresh = false;
            requestTransactionsRefresh();
        }
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

    private void appendTransactions(List<Transaction> newTransactions) {
        if (newTransactions.isEmpty()) return;

        allTransactions.addAll(newTransactions);

        int insertPosition = filteredTransactionItems.size();
        String lastDateGroup = null;

        // Find the last date header
        for (int i = filteredTransactionItems.size() - 1; i >= 0; i--) {
            Object item = filteredTransactionItems.get(i);
            if (item instanceof DateHeader) {
                lastDateGroup = ((DateHeader) item).date;
                break;
            }
        }

        Map<String, List<Transaction>> groupedByDate = new HashMap<>();
        Map<String, Double> dailyTotals = new HashMap<>();
        List<String> dateOrder = new ArrayList<>();

        for (Transaction t : newTransactions) {
            String dateGroup = getDateGroup(t.getCreatedAt());

            if (!groupedByDate.containsKey(dateGroup)) {
                groupedByDate.put(dateGroup, new ArrayList<>());
                dailyTotals.put(dateGroup, 0.0);
                dateOrder.add(dateGroup);
            }

            groupedByDate.get(dateGroup).add(t);
            dailyTotals.put(dateGroup, dailyTotals.get(dateGroup) + t.getAmount());
        }

        List<Object> newItems = new ArrayList<>();

        for (String dateGroup : dateOrder) {
            // If same date group as last, merge transactions without adding new header
            if (dateGroup.equals(lastDateGroup)) {
                // Update the existing header's total
                for (int i = filteredTransactionItems.size() - 1; i >= 0; i--) {
                    Object item = filteredTransactionItems.get(i);
                    if (item instanceof DateHeader && ((DateHeader) item).date.equals(dateGroup)) {
                        DateHeader header = (DateHeader) item;
                        header.total += dailyTotals.get(dateGroup);
                        adapter.notifyItemChanged(i);
                        break;
                    }
                }
            } else {
                newItems.add(new DateHeader(dateGroup, dailyTotals.get(dateGroup), accountCurrency));
                allTransactionItems.add(new DateHeader(dateGroup, dailyTotals.get(dateGroup), accountCurrency));
            }

            for (Transaction t : groupedByDate.get(dateGroup)) {
                TransactionItem transactionItem = createTransactionItem(t);
                newItems.add(transactionItem);
                allTransactionItems.add(transactionItem);
            }

            lastDateGroup = dateGroup;
        }

        filteredTransactionItems.addAll(newItems);
        adapter.notifyItemRangeInserted(insertPosition, newItems.size());
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
        int iconColor = getIconColorForTransaction(t);
        String initials = null;
        String senderPhoto = null;
        String merchantLogoUrl = null;
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

        if ("CARD".equals(t.getTransactionType())
                || "CARD_PENDING_APPROVAL".equals(t.getTransactionType())
                || "BILL".equals(t.getTransactionType())) {
            merchantLogoUrl = t.getMerchantLogoUrl();
        }

        return new TransactionItem(
                t,
                t.getTitle(),
                getDisplaySubtitle(t),
                t.getAmount(),
                t.getCurrency() != null ? t.getCurrency() : accountCurrency,
                time,
                iconRes,
                iconColor,
                initials,
                senderPhoto,
                merchantLogoUrl,
                isExchange,
                secondaryAmount,
                secondaryCurrency,
                fromCurrency,
                toCurrency
        );
    }

    private void onTransactionClick(TransactionItem item) {
        if (item.transaction instanceof CardTransaction) {
            CardTransaction cardTransaction = (CardTransaction) item.transaction;
            if (cardTransaction.isPendingApproval()) {
                openCardPaymentApproval(cardTransaction);
                return;
            }
        }

        Intent intent = new Intent(this, TransactionDetailsActivity.class);
        intent.putExtra(TransactionDetailsActivity.EXTRA_TRANSACTION_ID, item.transaction.getId());
        intent.putExtra(TransactionDetailsActivity.EXTRA_TRANSACTION_TYPE, item.transaction.getTransactionType());
        intent.putExtra(TransactionDetailsActivity.EXTRA_ACCOUNT_CURRENCY, accountCurrency);
        startActivity(intent);
    }

    private void openCardPaymentApproval(CardTransaction transaction) {
        Intent intent = new Intent(this, CardPaymentApprovalActivity.class);
        intent.putExtra(CardPaymentApprovalActivity.EXTRA_SESSION_ID,
                transaction.getSessionId() != null ? transaction.getSessionId() : transaction.getId());
        intent.putExtra(CardPaymentApprovalActivity.EXTRA_MERCHANT_NAME,
                transaction.getMerchantName() != null ? transaction.getMerchantName() : transaction.getTitle());
        intent.putExtra(CardPaymentApprovalActivity.EXTRA_MERCHANT_LOCATION, transaction.getLocation());
        intent.putExtra(CardPaymentApprovalActivity.EXTRA_AMOUNT, Math.abs(transaction.getAmount()));
        intent.putExtra(CardPaymentApprovalActivity.EXTRA_CURRENCY,
                transaction.getCurrency() != null ? transaction.getCurrency() : accountCurrency);
        intent.putExtra(CardPaymentApprovalActivity.EXTRA_MASKED_CARD,
                transaction.getMaskedCard() != null ? transaction.getMaskedCard() : transaction.getCardNumberMasked());
        intent.putExtra(CardPaymentApprovalActivity.EXTRA_EXPIRES_AT, transaction.getExpiresAt());
        startActivityForResult(intent, REQUEST_CARD_PAYMENT_APPROVAL);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CARD_PAYMENT_APPROVAL && resultCode == RESULT_OK) {
            requestTransactionsRefresh();
        }
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
                if (t instanceof BillTransaction) {
                    BillTransaction bill = (BillTransaction) t;
                    return getBillIconForCategory(bill.getBillerCategory());
                }
                return getBillIconForCategory(t.getSubtitle());
            case "CARD_PENDING_APPROVAL":
                return getCategoryIconForTransaction(t, R.drawable.ic_card);
            case "CARD":
            default:
                return getCategoryIconForTransaction(t, R.drawable.ic_shopping);
        }
    }

    private int getBillIconForCategory(String category) {
        if (category == null) return R.drawable.ic_receipt;
        switch (category.toLowerCase()) {
            case "utilities":
                return R.drawable.ic_utilities;
            case "telecom":
                return R.drawable.ic_phone;
            case "internet":
                return R.drawable.ic_wifi;
            case "tv":
                return R.drawable.ic_tv;
            case "insurance":
                return R.drawable.ic_shield;
            default:
                return R.drawable.ic_receipt;
        }
    }

    private int getCategoryIconForTransaction(Transaction transaction, int fallback) {
        String iconName = transaction.getCategoryIcon();
        if (iconName == null || iconName.trim().isEmpty()) {
            iconName = transaction.getCategoryName();
        }

        switch (iconName.trim().toLowerCase(Locale.ROOT)) {
            case "food":
            case "ic_category_food":
                return R.drawable.ic_category_food;
            case "shopping":
            case "ic_category_shopping":
                return R.drawable.ic_category_shopping;
            case "transport":
            case "ic_category_transport":
                return R.drawable.ic_category_transport;
            case "entertainment":
            case "ic_category_entertainment":
                return R.drawable.ic_category_entertainment;
            case "groceries":
            case "ic_category_groceries":
                return R.drawable.ic_category_groceries;
            case "health":
            case "ic_category_health":
                return R.drawable.ic_category_health;
            case "utilities":
            case "ic_category_utilities":
                return R.drawable.ic_category_utilities;
            case "telecom":
            case "ic_category_telecom":
                return R.drawable.ic_phone;
            case "internet":
            case "ic_category_internet":
                return R.drawable.ic_wifi;
            case "tv":
            case "ic_category_tv":
                return R.drawable.ic_tv;
            case "insurance":
            case "ic_category_insurance":
                return R.drawable.ic_shield;
            case "travel":
            case "ic_category_travel":
                return R.drawable.ic_category_travel;
            case "services":
            case "ic_category_services":
                return R.drawable.ic_category_services;
            case "subscriptions":
            case "ic_category_subscriptions":
                return R.drawable.ic_category_entertainment;
            case "other":
            case "ic_category_other":
                return R.drawable.ic_category_other;
            default:
                return fallback;
        }
    }

    private int getCategoryColorForTransaction(Transaction transaction, int fallback) {
        String categoryName = transaction.getCategoryName();
        if (categoryName == null || categoryName.trim().isEmpty()) {
            return fallback;
        }

        switch (categoryName.trim().toLowerCase(Locale.ROOT)) {
            case "food":
                return 0xFFF97316;
            case "shopping":
                return 0xFF8B5CF6;
            case "transport":
                return 0xFF3B82F6;
            case "entertainment":
                return 0xFFEC4899;
            case "health":
                return 0xFF10B981;
            case "travel":
                return 0xFF06B6D4;
            case "services":
                return 0xFF6366F1;
            case "subscriptions":
                return 0xFFF59E0B;
            case "utilities":
                return 0xFFF59E0B;
            case "telecom":
                return 0xFF3B82F6;
            case "internet":
                return 0xFF10B981;
            case "tv":
                return 0xFF8B5CF6;
            case "insurance":
                return 0xFFEF4444;
            case "groceries":
                return 0xFF22C55E;
            case "other":
            default:
                return fallback != 0 ? fallback : 0xFF6B7280;
        }
    }

    private String getCategoryDisplayNameForTransaction(Transaction transaction) {
        String categoryName = transaction.getCategoryName();
        if (categoryName == null || categoryName.trim().isEmpty()) {
            return null;
        }

        switch (categoryName.trim().toLowerCase(Locale.ROOT)) {
            case "food":
                return "Mancare si bauturi";
            case "shopping":
                return "Cumparaturi";
            case "transport":
                return "Transport";
            case "entertainment":
                return "Divertisment";
            case "groceries":
                return "Supermarket";
            case "health":
                return "Sanatate";
            case "utilities":
                return "Utilitati";
            case "telecom":
                return "Telecom";
            case "internet":
                return "Internet";
            case "tv":
                return "TV & Cablu";
            case "insurance":
                return "Asigurari";
            case "travel":
                return "Calatorii";
            case "services":
                return "Servicii";
            case "subscriptions":
                return "Abonamente";
            case "other":
                return "Altele";
            default:
                return categoryName.substring(0, 1).toUpperCase(Locale.ROOT) + categoryName.substring(1);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        refreshHandler.removeCallbacks(transactionsRefreshRunnable);

        if (transactionsCall != null) {
            transactionsCall.cancel();
            transactionsCall = null;
        }

        if (refreshReceiverRegistered) {
            unregisterReceiver(refreshReceiver);
            refreshReceiverRegistered = false;
        }

        if (realtimeListener != null && currentUserId != -1) {
            RealtimeManager realtime = RealtimeManager.getInstance();
            String userId = String.valueOf(currentUserId);
            realtime.unsubscribeFromUserChanges("accounts", userId, realtimeListener);
            realtime.unsubscribeFromUserChanges("card_payment_sessions", userId, realtimeListener);
            realtimeListener = null;
        }
    }

    private void registerRefreshReceiver() {
        IntentFilter filter = new IntentFilter(ACTION_REFRESH_DATA);
        ContextCompat.registerReceiver(this, refreshReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        refreshReceiverRegistered = true;
    }

    private int getIconColorForTransaction(Transaction transaction) {
        if (transaction instanceof CardTransaction && ((CardTransaction) transaction).isPendingApproval()) {
            return getCategoryColorForTransaction(transaction, 0xFFFF9500);
        }

        if ("CARD".equals(transaction.getTransactionType())) {
            return getCategoryColorForTransaction(transaction, 0);
        }

        if (transaction instanceof BillTransaction) {
            BillTransaction bill = (BillTransaction) transaction;
            return getBillCategoryColor(bill.getBillerCategory());
        }

        return 0;
    }

    private int getBillCategoryColor(String category) {
        if (category == null) return 0xFF6B7280;
        switch (category.toLowerCase()) {
            case "utilities":
                return 0xFFF59E0B;
            case "telecom":
                return 0xFF3B82F6;
            case "internet":
                return 0xFF10B981;
            case "tv":
                return 0xFF8B5CF6;
            case "insurance":
                return 0xFFEF4444;
            default:
                return 0xFF6B7280;
        }
    }

    private String getDisplaySubtitle(Transaction transaction) {
        if (transaction instanceof CardTransaction && ((CardTransaction) transaction).isPendingApproval()) {
            return "Asteapta confirmarea";
        }

        if (transaction instanceof BillTransaction) {
            BillTransaction bill = (BillTransaction) transaction;
            return getBillCategoryDisplayName(bill.getBillerCategory());
        }

        String subtitle = transaction.getSubtitle();
        if ("CARD".equals(transaction.getTransactionType()) && "PENDING".equals(transaction.getStatus())) {
            return "Suma blocata";
        }

        if ("CARD".equals(transaction.getTransactionType())) {
            String categoryDisplayName = getCategoryDisplayNameForTransaction(transaction);
            if (categoryDisplayName != null) {
                return categoryDisplayName;
            }
        }

        if ("CARD".equals(transaction.getTransactionType()) && (subtitle == null || subtitle.isEmpty())) {
            return "Plată cu cardul";
        }

        return subtitle != null && !subtitle.isEmpty() ? subtitle : formatTime(transaction.getCreatedAt());
    }

    private String getBillCategoryDisplayName(String category) {
        if (category == null || category.isEmpty()) return "Plată factură";

        switch (category.toLowerCase()) {
            case "utilities":
                return "Utilități";
            case "telecom":
                return "Telecom";
            case "internet":
                return "Internet";
            case "tv":
                return "TV & Cablu";
            case "insurance":
                return "Asigurări";
            default:
                return category.substring(0, 1).toUpperCase() + category.substring(1);
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
    static class LoadingSkeleton {}

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
        int iconColor;
        String initials;
        String senderPhoto;
        String merchantLogoUrl;
        boolean isExchange;
        double secondaryAmount;
        String secondaryCurrency;
        String fromCurrency;
        String toCurrency;

        TransactionItem(Transaction transaction, String title, String subtitle, double amount, String currency,
                        String time, int iconRes, int iconColor, String initials, String senderPhoto,
                        String merchantLogoUrl, boolean isExchange, double secondaryAmount, String secondaryCurrency,
                        String fromCurrency, String toCurrency) {
            this.transaction = transaction;
            this.title = title;
            this.subtitle = subtitle;
            this.amount = amount;
            this.currency = currency;
            this.time = time;
            this.iconRes = iconRes;
            this.iconColor = iconColor;
            this.initials = initials;
            this.senderPhoto = senderPhoto;
            this.merchantLogoUrl = merchantLogoUrl;
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
        private static final int TYPE_LOADING = 2;

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
            Object item = items.get(position);
            if (item instanceof DateHeader) return TYPE_HEADER;
            if (item instanceof LoadingSkeleton) return TYPE_LOADING;
            return TYPE_TRANSACTION;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_HEADER) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_transaction_date_header, parent, false);
                return new HeaderViewHolder(view);
            } else if (viewType == TYPE_LOADING) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_transaction_skeleton, parent, false);
                return new LoadingViewHolder(view);
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
            } else if (holder instanceof TransactionViewHolder) {
                ((TransactionViewHolder) holder).bind((TransactionItem) items.get(position));
            }
            // LoadingViewHolder needs no binding
        }

        class LoadingViewHolder extends RecyclerView.ViewHolder {
            LoadingViewHolder(@NonNull View itemView) {
                super(itemView);
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
                tvMerchantName.setText(item.isExchange
                        ? ExchangeTitleFormatter.format(item.title, getResources().getDisplayMetrics().density)
                        : item.title);
                tvCategory.setText(item.subtitle);
                tvTime.setText(item.time);
                tvTime.setVisibility(View.VISIBLE);

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
                    if (!applyMerchantLogoUrl(item)) {
                        applyIconStyle(item);
                        ivCategoryIcon.setImageResource(item.iconRes);
                    }
                }
            }

            private boolean applyMerchantLogoUrl(TransactionItem item) {
                if (item.merchantLogoUrl == null || item.merchantLogoUrl.trim().isEmpty()) {
                    return false;
                }

                if (cardIcon instanceof androidx.cardview.widget.CardView) {
                    ((androidx.cardview.widget.CardView) cardIcon).setCardBackgroundColor(
                            ContextCompat.getColor(TransactionsActivity.this, R.color.white));
                }

                androidx.core.widget.ImageViewCompat.setImageTintList(ivCategoryIcon, null);
                ivCategoryIcon.clearColorFilter();
                setIconImageSize(32);
                ivCategoryIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                return RemoteImageLoader.load(item.merchantLogoUrl, ivCategoryIcon, () -> {
                    applyIconStyle(item);
                    ivCategoryIcon.setImageResource(item.iconRes);
                });
            }

            private void applyIconStyle(TransactionItem item) {
                ivCategoryIcon.setTag(null);
                if (cardIcon instanceof androidx.cardview.widget.CardView) {
                    int backgroundColor = item.iconColor != 0
                            ? item.iconColor
                            : ContextCompat.getColor(TransactionsActivity.this, R.color.white_10);
                    ((androidx.cardview.widget.CardView) cardIcon).setCardBackgroundColor(backgroundColor);
                }

                int iconTint = item.iconColor != 0
                        ? ContextCompat.getColor(TransactionsActivity.this, R.color.white)
                        : ContextCompat.getColor(TransactionsActivity.this, R.color.white_60);
                setIconImageSize(24);
                ivCategoryIcon.setScaleType(ImageView.ScaleType.CENTER);
                androidx.core.widget.ImageViewCompat.setImageTintList(
                        ivCategoryIcon,
                        android.content.res.ColorStateList.valueOf(iconTint));
            }

            private void setIconImageSize(int sizeDp) {
                ViewGroup.LayoutParams params = ivCategoryIcon.getLayoutParams();
                int sizePx = Math.round(sizeDp * getResources().getDisplayMetrics().density);
                if (params.width != sizePx || params.height != sizePx) {
                    params.width = sizePx;
                    params.height = sizePx;
                    ivCategoryIcon.setLayoutParams(params);
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
