package com.example.swiftbank.activities.transactions;

import android.app.DatePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
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
import androidx.core.content.FileProvider;
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
import com.example.swiftbank.utils.SwiftBankDialog;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TransactionsActivity extends AppCompatActivity {

    public static final String EXTRA_ACCOUNT_ID = "account_id";
    public static final String EXTRA_ACCOUNT_CURRENCY = "account_currency";
    public static final String EXTRA_SCREEN_TITLE = "screen_title";
    public static final String EXTRA_FILTER_CATEGORY = "filter_category";
    public static final String EXTRA_FILTER_MERCHANT = "filter_merchant";
    public static final String EXTRA_START_DATE = "start_date";
    public static final String EXTRA_END_DATE = "end_date";
    public static final String EXTRA_ALL_ACCOUNTS = "all_accounts";
    private static final int REQUEST_CARD_PAYMENT_APPROVAL = 2401;
    private static final String ACTION_REFRESH_DATA = "com.example.swiftbank.REFRESH_DATA";
    private static final long DATA_REFRESH_DEBOUNCE_MS = 350L;
    private static final int STATEMENT_PAGE_SIZE = 100;

    private ImageView btnBack, btnStatement, ivAccountFlag;
    private EditText etSearch;
    private TextView tvAccountName, tvTitle, btnStartDate, btnEndDate, btnClearPeriod;
    private RecyclerView rvTransactions;
    private LinearLayout loadingState, emptyState, layoutAccount, dateFilterContainer;

    private TransactionsAdapter adapter;
    private List<Object> allTransactionItems = new ArrayList<>();
    private List<Object> filteredTransactionItems = new ArrayList<>();
    private List<Transaction> allTransactions = new ArrayList<>();
    private List<AccountData> accounts = new ArrayList<>();

    private int accountId = -1;
    private String accountCurrency = "RON";
    private String screenTitle;
    private String fixedCategoryFilter;
    private String fixedMerchantFilter;
    private String filterStartDate;
    private String filterEndDate;
    private int currentUserId = -1;
    private boolean refreshReceiverRegistered = false;
    private boolean allAccountsMode = false;

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
            if (allAccountsMode || accountId != -1) {
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
        screenTitle = getIntent().getStringExtra(EXTRA_SCREEN_TITLE);
        fixedCategoryFilter = getIntent().getStringExtra(EXTRA_FILTER_CATEGORY);
        fixedMerchantFilter = getIntent().getStringExtra(EXTRA_FILTER_MERCHANT);
        filterStartDate = getIntent().getStringExtra(EXTRA_START_DATE);
        filterEndDate = getIntent().getStringExtra(EXTRA_END_DATE);
        allAccountsMode = getIntent().getBooleanExtra(EXTRA_ALL_ACCOUNTS, false);

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
        btnStatement = findViewById(R.id.btnStatement);
        etSearch = findViewById(R.id.etSearch);
        ivAccountFlag = findViewById(R.id.ivAccountFlag);
        tvAccountName = findViewById(R.id.tvAccountName);
        tvTitle = findViewById(R.id.tvTitle);
        layoutAccount = findViewById(R.id.layoutAccount);
        dateFilterContainer = findViewById(R.id.dateFilterContainer);
        btnStartDate = findViewById(R.id.btnStartDate);
        btnEndDate = findViewById(R.id.btnEndDate);
        btnClearPeriod = findViewById(R.id.btnClearPeriod);
        rvTransactions = findViewById(R.id.rvTransactions);
        loadingState = findViewById(R.id.loadingState);
        emptyState = findViewById(R.id.emptyState);

        updateAccountDisplay();
    }

    private void updateAccountDisplay() {
        if (tvTitle != null && screenTitle != null && !screenTitle.trim().isEmpty()) {
            tvTitle.setText(screenTitle);
        }

        if (allAccountsMode) {
            tvAccountName.setText("Toate");
            ivAccountFlag.setImageResource(R.drawable.ic_filter);
        } else {
            tvAccountName.setText(accountCurrency);
            ivAccountFlag.setImageResource(getFlagResource(accountCurrency));
        }

        updateDateFilterDisplay();
        updateSearchHint();
    }

    private void updateSearchHint() {
        if (etSearch == null) return;
        etSearch.setHint(hasFixedCategoryFilter()
                ? "Caut\u0103 nume sau sum\u0103"
                : "Caut\u0103 nume, sum\u0103 sau categorie");
    }

    private boolean hasFixedCategoryFilter() {
        return fixedCategoryFilter != null && !fixedCategoryFilter.trim().isEmpty();
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

        if (btnStatement != null) {
            btnStatement.setOnClickListener(v -> showStatementConfirmDialog());
        }

        if (layoutAccount != null) {
            layoutAccount.setOnClickListener(v -> showAccountPicker());
        }
        if (btnStartDate != null) {
            btnStartDate.setOnClickListener(v -> showDatePicker(true));
        }
        if (btnEndDate != null) {
            btnEndDate.setOnClickListener(v -> showDatePicker(false));
        }
        if (btnClearPeriod != null) {
            btnClearPeriod.setOnClickListener(v -> {
                filterStartDate = null;
                filterEndDate = null;
                updateDateFilterDisplay();
                requestTransactionsRefresh();
            });
        }

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

    private void showDatePicker(boolean startDate) {
        Calendar calendar = Calendar.getInstance();
        String current = startDate ? filterStartDate : filterEndDate;
        Date parsed = parseTransactionDate(current);
        if (current != null && parsed.getTime() > 0) {
            calendar.setTime(parsed);
        }

        DatePickerDialog picker = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    String iso = String.format(Locale.ROOT,
                            startDate ? "%04d-%02d-%02dT00:00:00.000Z" : "%04d-%02d-%02dT23:59:59.999Z",
                            year,
                            month + 1,
                            dayOfMonth);
                    if (startDate) {
                        filterStartDate = iso;
                    } else {
                        filterEndDate = iso;
                    }
                    updateDateFilterDisplay();
                    requestTransactionsRefresh();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        picker.show();
    }

    private void updateDateFilterDisplay() {
        if (btnStartDate != null) {
            btnStartDate.setText(filterStartDate == null ? "De la" : formatDateChip(filterStartDate));
        }
        if (btnEndDate != null) {
            btnEndDate.setText(filterEndDate == null ? "P\u00E2n\u0103 la" : formatDateChip(filterEndDate));
        }
    }

    private String formatDateChip(String isoDate) {
        if (isoDate == null || isoDate.length() < 10) return "";
        String[] parts = isoDate.substring(0, 10).split("-");
        if (parts.length != 3) return isoDate.substring(0, Math.min(10, isoDate.length()));
        return parts[2] + "." + parts[1] + "." + parts[0];
    }

    private void showAccountPicker() {
        if (accounts.isEmpty()) {
            ApiClient.getAccountService().getAccounts().enqueue(new Callback<ApiResponse<AccountsData>>() {
                @Override
                public void onResponse(Call<ApiResponse<AccountsData>> call, Response<ApiResponse<AccountsData>> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                        accounts.clear();
                        accounts.addAll(response.body().getData().getAccounts());
                        showAccountPicker();
                    }
                }

                @Override
                public void onFailure(Call<ApiResponse<AccountsData>> call, Throwable t) {
                    Toast.makeText(TransactionsActivity.this, "Nu am putut \u00EEnc\u0103rca lista de conturi", Toast.LENGTH_SHORT).show();
                }
            });
            return;
        }

        SwiftBankDialog dialog = new SwiftBankDialog(this)
                .hideIcon()
                .setTitle("Alege contul")
                .setPrimaryButton("\u00CEnchide", null)
                .setCancelable(true);

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.setFillViewport(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        LinearLayout optionsContainer = new LinearLayout(this);
        optionsContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(optionsContainer, new android.widget.ScrollView.LayoutParams(
                android.widget.ScrollView.LayoutParams.MATCH_PARENT,
                android.widget.ScrollView.LayoutParams.WRAP_CONTENT
        ));

        optionsContainer.addView(createAccountPickerItem(null, dialog));
        for (AccountData account : accounts) {
            optionsContainer.addView(createAccountPickerItem(account, dialog));
        }

        dialog.setCustomView(scrollView);
        dialog.show();
    }

    private View createAccountPickerItem(AccountData account, SwiftBankDialog dialog) {
        View item = LayoutInflater.from(this).inflate(R.layout.item_account_bottom_sheet, null, false);
        LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        itemParams.setMargins(0, dpToPx(5), 0, dpToPx(5));
        item.setLayoutParams(itemParams);
        ImageView ivFlag = item.findViewById(R.id.ivFlag);
        ImageView ivSelected = item.findViewById(R.id.ivSelected);
        LinearLayout balanceContainer = item.findViewById(R.id.balanceContainer);
        TextView tvAccountName = item.findViewById(R.id.tvAccountName);
        TextView tvAccountSubtitle = item.findViewById(R.id.tvAccountSubtitle);
        TextView tvBalance = item.findViewById(R.id.tvBalance);
        TextView tvCurrency = item.findViewById(R.id.tvCurrency);

        boolean isAllOption = account == null;
        if (isAllOption) {
            ivFlag.setImageResource(R.drawable.ic_filter);
            androidx.core.widget.ImageViewCompat.setImageTintList(
                    ivFlag,
                    android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.green_accent)));
            tvAccountName.setText("Toate conturile");
            tvAccountSubtitle.setText("Include toate valutele");
            tvAccountSubtitle.setVisibility(View.VISIBLE);
            tvBalance.setVisibility(View.GONE);
            tvCurrency.setVisibility(View.GONE);
            balanceContainer.setVisibility(View.VISIBLE);
            item.setSelected(allAccountsMode);
            ivSelected.setVisibility(allAccountsMode ? View.VISIBLE : View.GONE);
        } else {
            tvAccountSubtitle.setText("Sold disponibil: " + formatBalance(account.getBalance()) + " " + getCurrencySymbol(account.getCurrency()));
            tvAccountSubtitle.setVisibility(View.VISIBLE);
            tvBalance.setVisibility(View.GONE);
            tvCurrency.setVisibility(View.GONE);
            balanceContainer.setVisibility(View.VISIBLE);
            androidx.core.widget.ImageViewCompat.setImageTintList(ivFlag, null);
            ivFlag.clearColorFilter();
            ivFlag.setImageResource(getFlagResource(account.getCurrency()));
            tvAccountName.setText("Personal - " + account.getCurrency());

            boolean selected = !allAccountsMode && account.getAccountId() == accountId;
            item.setSelected(selected);
            ivSelected.setVisibility(selected ? View.VISIBLE : View.GONE);
        }

        item.setOnClickListener(v -> {
            if (isAllOption) {
                allAccountsMode = true;
                accountId = -1;
                accountCurrency = "MULTI";
            } else {
                allAccountsMode = false;
                accountId = account.getAccountId();
                accountCurrency = account.getCurrency();
            }

            updateAccountDisplay();
            loadTransactions();
            dialog.dismiss();
        });

        return item;
    }

    private Date parseTransactionDate(String value) {
        if (value == null) return new Date(0);
        try {
            String cleanValue = value;
            if (cleanValue.endsWith("Z")) {
                cleanValue = cleanValue.substring(0, cleanValue.length() - 1);
            }
            int dotIndex = cleanValue.indexOf('.');
            if (dotIndex >= 0) {
                cleanValue = cleanValue.substring(0, dotIndex);
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            Date parsed = sdf.parse(cleanValue);
            return parsed != null ? parsed : new Date(0);
        } catch (Exception ignored) {
            return new Date(0);
        }
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
        if (allAccountsMode) {
            loadTransactions();
        } else if (accountId == -1) {
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
                    accounts.clear();
                    accounts.addAll(response.body().getData().getAccounts());
                    if (!accounts.isEmpty()) {
                        if (allAccountsMode) {
                            accountId = -1;
                            accountCurrency = "MULTI";
                        } else {
                            accountId = accounts.get(0).getAccountId();
                            accountCurrency = accounts.get(0).getCurrency();
                        }
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

    private void loadTransactionsForAllAccounts() {
        if (accounts.isEmpty()) {
            loadDefaultAccount();
            return;
        }

        currentOffset = 0;
        hasMore = false;
        allTransactions.clear();
        allTransactionItems.clear();
        filteredTransactionItems.clear();

        transactionsRequestInFlight = true;
        showLoading();

        List<Transaction> merged = new ArrayList<>();
        final int[] pending = {accounts.size()};

        for (AccountData account : accounts) {
            ApiClient.getTransactionService()
                    .getTransactionsFiltered(account.getAccountId(), 100, 0, filterStartDate, filterEndDate)
                    .enqueue(new Callback<ApiResponse<TransactionsData>>() {
                        @Override
                        public void onResponse(Call<ApiResponse<TransactionsData>> call,
                                               Response<ApiResponse<TransactionsData>> response) {
                            if (response.isSuccessful()
                                    && response.body() != null
                                    && response.body().getData() != null
                                    && response.body().getData().getTransactions() != null) {
                                merged.addAll(response.body().getData().getTransactions());
                            }
                            finishAllAccountsRequest(merged, pending);
                        }

                        @Override
                        public void onFailure(Call<ApiResponse<TransactionsData>> call, Throwable t) {
                            finishAllAccountsRequest(merged, pending);
                        }
                    });
        }
    }

    private void finishAllAccountsRequest(List<Transaction> merged, int[] pending) {
        pending[0]--;
        if (pending[0] > 0) return;

        List<Transaction> visibleMerged = deduplicateOwnAccountTransfers(merged);
        Collections.sort(visibleMerged, (a, b) -> parseTransactionDate(b.getCreatedAt()).compareTo(parseTransactionDate(a.getCreatedAt())));
        currentOffset = visibleMerged.size();
        processTransactions(visibleMerged);
        finishTransactionsRequest();
    }

    private List<Transaction> deduplicateOwnAccountTransfers(List<Transaction> transactions) {
        List<Transaction> regularTransactions = new ArrayList<>();
        Map<String, Transaction> ownTransfersByKey = new LinkedHashMap<>();

        if (transactions == null) {
            return regularTransactions;
        }

        for (Transaction transaction : transactions) {
            if (!isOwnAccountTransfer(transaction)) {
                regularTransactions.add(transaction);
                continue;
            }

            String key = buildOwnAccountTransferKey(transaction);
            Transaction existing = ownTransfersByKey.get(key);
            if (existing == null || shouldPreferOwnAccountTransfer(transaction, existing)) {
                ownTransfersByKey.put(key, transaction);
            }
        }

        regularTransactions.addAll(ownTransfersByKey.values());
        return regularTransactions;
    }

    private boolean isOwnAccountTransfer(Transaction transaction) {
        String type = transaction.getTransactionType();
        return "SELF_IN".equals(type) || "SELF_OUT".equals(type);
    }

    private boolean shouldPreferOwnAccountTransfer(Transaction candidate, Transaction existing) {
        String candidateType = candidate.getTransactionType();
        String existingType = existing.getTransactionType();

        if ("SELF_OUT".equals(candidateType) && !"SELF_OUT".equals(existingType)) {
            return true;
        }
        if ("SELF_OUT".equals(existingType) && !"SELF_OUT".equals(candidateType)) {
            return false;
        }

        return Math.abs(candidate.getAmount()) >= Math.abs(existing.getAmount());
    }

    private String buildOwnAccountTransferKey(Transaction transaction) {
        String reference = transaction.getReference();
        if (reference != null && !reference.trim().isEmpty()) {
            return "reference:" + reference.trim();
        }

        List<String> amountParts = new ArrayList<>();
        amountParts.add(formatAmountKey(Math.abs(transaction.getAmount())) + ":" + safeValue(transaction.getCurrency()));

        if (transaction instanceof TransferTransaction) {
            TransferTransaction transfer = (TransferTransaction) transaction;
            if (transfer.getOriginalAmount() != null) {
                amountParts.add(formatAmountKey(Math.abs(transfer.getOriginalAmount())) + ":" + safeValue(transfer.getOriginalCurrency()));
            }
        }

        Collections.sort(amountParts);
        return "own-transfer:" + normalizeTransactionTimeKey(transaction.getCreatedAt()) + ":" + String.join("|", amountParts);
    }

    private String normalizeTransactionTimeKey(String createdAt) {
        if (createdAt == null) {
            return "";
        }
        return createdAt.length() >= 16 ? createdAt.substring(0, 16) : createdAt;
    }

    private String formatAmountKey(double amount) {
        return String.format(Locale.US, "%.2f", amount);
    }

    private void loadTransactions() {
        if (transactionsRequestInFlight) {
            queuedTransactionsRefresh = true;
            return;
        }

        if (allAccountsMode) {
            loadTransactionsForAllAccounts();
            return;
        }

        currentOffset = 0;
        hasMore = true;
        allTransactions.clear();
        allTransactionItems.clear();

        transactionsRequestInFlight = true;
        showLoading();

        transactionsCall = ApiClient.getTransactionService()
                .getTransactionsFiltered(accountId, PAGE_SIZE, 0, filterStartDate, filterEndDate);
        transactionsCall.enqueue(new Callback<ApiResponse<TransactionsData>>() {
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
                        hasMore = transactions != null && transactions.size() >= PAGE_SIZE;
                    }
                    currentOffset = transactions != null ? transactions.size() : 0;

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

        ApiClient.getTransactionService().getTransactionsFiltered(accountId, PAGE_SIZE, currentOffset, filterStartDate, filterEndDate)
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
        List<Transaction> visibleTransactions = applyFixedFilters(transactions);

        allTransactions.clear();
        allTransactions.addAll(visibleTransactions);

        allTransactionItems.clear();

        Map<String, List<Transaction>> groupedByDate = new HashMap<>();
        Map<String, Double> dailyTotals = new HashMap<>();
        List<String> dateOrder = new ArrayList<>();

        for (Transaction t : visibleTransactions) {
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
            allTransactionItems.add(new DateHeader(dateGroup, dailyTotals.get(dateGroup), getHeaderCurrency()));

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
        newTransactions = applyFixedFilters(newTransactions);
        if (newTransactions.isEmpty()) return;

        allTransactions.addAll(newTransactions);

        int insertPosition = filteredTransactionItems.size();
        String lastDateGroup = null;

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
            if (dateGroup.equals(lastDateGroup)) {
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
                DateHeader header = new DateHeader(dateGroup, dailyTotals.get(dateGroup), getHeaderCurrency());
                newItems.add(header);
                allTransactionItems.add(header);
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

        String currentQuery = etSearch != null ? etSearch.getText().toString() : "";
        if (currentQuery != null && !currentQuery.trim().isEmpty()) {
            filterTransactions(currentQuery);
        }
    }

    private void filterTransactions(String query) {
        filteredTransactionItems.clear();

        String lowerQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (lowerQuery.isEmpty()) {
            filteredTransactionItems.addAll(allTransactionItems);
        } else {
            Map<String, List<TransactionItem>> groupedByDate = new HashMap<>();
            Map<String, Double> dailyTotals = new HashMap<>();
            List<String> dateOrder = new ArrayList<>();

            for (Transaction t : allTransactions) {
                if (!buildSearchText(t).contains(lowerQuery)) {
                    continue;
                }

                String dateGroup = getDateGroup(t.getCreatedAt());

                if (!groupedByDate.containsKey(dateGroup)) {
                    groupedByDate.put(dateGroup, new ArrayList<>());
                    dailyTotals.put(dateGroup, 0.0);
                    dateOrder.add(dateGroup);
                }

                groupedByDate.get(dateGroup).add(createTransactionItem(t));
                dailyTotals.put(dateGroup, dailyTotals.get(dateGroup) + t.getAmount());
            }

            for (String dateGroup : dateOrder) {
                filteredTransactionItems.add(new DateHeader(dateGroup, dailyTotals.get(dateGroup), getHeaderCurrency()));
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

    private List<Transaction> applyFixedFilters(List<Transaction> source) {
        List<Transaction> result = new ArrayList<>();
        if (source == null) return result;

        for (Transaction transaction : source) {
            if (matchesFixedFilters(transaction)) {
                result.add(transaction);
            }
        }

        return result;
    }

    private boolean matchesFixedFilters(Transaction transaction) {
        if (fixedCategoryFilter != null && !fixedCategoryFilter.trim().isEmpty()) {
            String wanted = normalizeFilterValue(fixedCategoryFilter);
            if ("transfers".equals(wanted) || "transferuri".equals(wanted)) {
                String type = transaction.getTransactionType();
                if (type == null || !type.startsWith("TRANSFER") || transaction.getAmount() >= 0) {
                    return false;
                }
            } else if (!normalizeFilterValue(getTransactionCategory(transaction)).equals(wanted)) {
                return false;
            }
        }

        if (fixedMerchantFilter != null && !fixedMerchantFilter.trim().isEmpty()) {
            String wanted = normalizeFilterValue(fixedMerchantFilter);
            if (!normalizeFilterValue(getTransactionMerchant(transaction)).equals(wanted)) {
                return false;
            }
        }

        return true;
    }

    private String buildSearchText(Transaction transaction) {
        StringBuilder searchText = new StringBuilder();
        searchText.append(safeLower(transaction.getTitle())).append(" ")
                .append(safeLower(transaction.getDescription())).append(" ")
                .append(safeLower(transaction.getReference())).append(" ")
                .append(safeLower(transaction.getCurrency())).append(" ")
                .append(safeLower(getTransactionMerchant(transaction))).append(" ")
                .append(formatBalance(Math.abs(transaction.getAmount()))).append(" ")
                .append(Math.abs(transaction.getAmount()));

        if (!hasFixedCategoryFilter()) {
            searchText.append(" ")
                    .append(safeLower(transaction.getSubtitle())).append(" ")
                    .append(safeLower(getTransactionCategory(transaction))).append(" ")
                    .append(safeLower(getCategoryDisplayNameForTransaction(transaction)));
        }

        return searchText.toString().toLowerCase(Locale.ROOT);
    }

    private String getTransactionCategory(Transaction transaction) {
        if (transaction instanceof BillTransaction) {
            return ((BillTransaction) transaction).getBillerCategory();
        }

        if (transaction instanceof TransferTransaction) {
            return "transfers";
        }

        String category = transaction.getCategoryName();
        if (category != null && !category.trim().isEmpty()) {
            return category;
        }

        String categoryIcon = transaction.getCategoryIcon();
        if (categoryIcon != null && !categoryIcon.trim().isEmpty()) {
            String normalizedIcon = categoryIcon.trim().toLowerCase(Locale.ROOT);
            if (normalizedIcon.startsWith("ic_category_")) {
                return normalizedIcon.substring("ic_category_".length());
            }
            return normalizedIcon;
        }

        String type = transaction.getTransactionType();
        if ("CARD".equals(type) || "CARD_PENDING_APPROVAL".equals(type)) {
            return "other";
        }

        String subtitle = transaction.getSubtitle();
        return subtitle != null && !subtitle.trim().isEmpty() ? subtitle : "other";
    }

    private String getTransactionMerchant(Transaction transaction) {
        if (transaction instanceof CardTransaction) {
            String merchant = ((CardTransaction) transaction).getMerchantName();
            return merchant != null && !merchant.isEmpty() ? merchant : transaction.getTitle();
        }
        if (transaction instanceof BillTransaction) {
            return ((BillTransaction) transaction).getBillerName();
        }
        if (transaction instanceof TransferTransaction) {
            String beneficiary = ((TransferTransaction) transaction).getBeneficiaryName();
            return beneficiary != null && !beneficiary.isEmpty() ? beneficiary : transaction.getTitle();
        }

        return transaction.getTitle();
    }

    private String normalizeFilterValue(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String getHeaderCurrency() {
        return allAccountsMode ? "MULTI" : accountCurrency;
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

    private interface StatementTransactionsCallback {
        void onSuccess(List<Transaction> transactions);
        void onError();
    }

    private void showStatementConfirmDialog() {
        new SwiftBankDialog(this)
                .setIcon(R.drawable.ic_document)
                .setTitle("Extras de cont")
                .setMessage("Vei genera un extras PDF pentru " + getStatementAccountSentenceLabel()
                        + ". Documentul respectă perioada, filtrele și căutarea aplicate în acest ecran.")
                .setPrimaryButton("Generează", v -> generateAccountStatement())
                .setSecondaryButton("Anulează", null)
                .setCancelable(true)
                .show();
    }

    private void generateAccountStatement() {
        if (!allAccountsMode && accountId == -1) {
            showStatementInfo("Alege un cont", "Selectează mai întâi contul pentru care vrei să generezi extrasul.");
            return;
        }

        setStatementButtonLoading(true);
        loadTransactionsForStatement(new StatementTransactionsCallback() {
            @Override
            public void onSuccess(List<Transaction> transactions) {
                setStatementButtonLoading(false);

                List<Transaction> statementTransactions = applyStatementFilters(transactions);
                if (statementTransactions.isEmpty()) {
                    showStatementInfo("Nu există tranzacții", "Nu am găsit tranzacții pentru perioada și filtrele selectate.");
                    return;
                }

                Collections.sort(statementTransactions, (a, b) ->
                        parseTransactionDate(b.getCreatedAt()).compareTo(parseTransactionDate(a.getCreatedAt())));

                try {
                    File statementFile = createStatementPdf(statementTransactions);
                    showStatementReadyDialog(statementFile, statementTransactions.size());
                } catch (IOException e) {
                    showStatementInfo("Extras negenerat", "Nu am putut crea fișierul PDF. Te rugăm să încerci din nou.");
                }
            }

            @Override
            public void onError() {
                setStatementButtonLoading(false);
                showStatementInfo("Extras negenerat", "Nu am putut încărca tranzacțiile necesare pentru extras.");
            }
        });
    }

    private void loadTransactionsForStatement(StatementTransactionsCallback callback) {
        if (allAccountsMode) {
            if (accounts.isEmpty()) {
                ApiClient.getAccountService().getAccounts().enqueue(new Callback<ApiResponse<AccountsData>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<AccountsData>> call,
                                           Response<ApiResponse<AccountsData>> response) {
                        if (response.isSuccessful()
                                && response.body() != null
                                && response.body().getData() != null
                                && response.body().getData().getAccounts() != null) {
                            accounts.clear();
                            accounts.addAll(response.body().getData().getAccounts());
                            loadStatementTransactionsForAllAccounts(callback);
                        } else {
                            callback.onError();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<AccountsData>> call, Throwable t) {
                        callback.onError();
                    }
                });
                return;
            }

            loadStatementTransactionsForAllAccounts(callback);
            return;
        }

        loadStatementTransactionsForAccount(accountId, 0, new ArrayList<>(), callback);
    }

    private void loadStatementTransactionsForAllAccounts(StatementTransactionsCallback callback) {
        if (accounts.isEmpty()) {
            callback.onSuccess(new ArrayList<>());
            return;
        }

        List<Transaction> merged = new ArrayList<>();
        final int[] pending = {accounts.size()};
        final boolean[] failed = {false};

        for (AccountData account : accounts) {
            loadStatementTransactionsForAccount(account.getAccountId(), 0, new ArrayList<>(), new StatementTransactionsCallback() {
                @Override
                public void onSuccess(List<Transaction> transactions) {
                    merged.addAll(transactions);
                    finishStatementAccountRequest(callback, merged, pending, failed);
                }

                @Override
                public void onError() {
                    failed[0] = true;
                    finishStatementAccountRequest(callback, merged, pending, failed);
                }
            });
        }
    }

    private void finishStatementAccountRequest(StatementTransactionsCallback callback,
                                               List<Transaction> merged,
                                               int[] pending,
                                               boolean[] failed) {
        pending[0]--;
        if (pending[0] > 0) return;

        if (failed[0]) {
            callback.onError();
        } else {
            callback.onSuccess(deduplicateOwnAccountTransfers(merged));
        }
    }

    private void loadStatementTransactionsForAccount(int targetAccountId,
                                                     int offset,
                                                     List<Transaction> collector,
                                                     StatementTransactionsCallback callback) {
        ApiClient.getTransactionService()
                .getTransactionsFiltered(targetAccountId, STATEMENT_PAGE_SIZE, offset, filterStartDate, filterEndDate)
                .enqueue(new Callback<ApiResponse<TransactionsData>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<TransactionsData>> call,
                                           Response<ApiResponse<TransactionsData>> response) {
                        if (!response.isSuccessful()
                                || response.body() == null
                                || response.body().getData() == null) {
                            callback.onError();
                            return;
                        }

                        TransactionsData data = response.body().getData();
                        List<Transaction> pageTransactions = data.getTransactions();
                        if (pageTransactions == null) {
                            pageTransactions = new ArrayList<>();
                        }

                        collector.addAll(pageTransactions);

                        boolean more;
                        if (data.getPagination() != null) {
                            more = data.getPagination().hasMore();
                        } else {
                            more = pageTransactions.size() >= STATEMENT_PAGE_SIZE;
                        }

                        if (more && !pageTransactions.isEmpty()) {
                            loadStatementTransactionsForAccount(
                                    targetAccountId,
                                    offset + pageTransactions.size(),
                                    collector,
                                    callback
                            );
                        } else {
                            callback.onSuccess(collector);
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<TransactionsData>> call, Throwable t) {
                        callback.onError();
                    }
                });
    }

    private List<Transaction> applyStatementFilters(List<Transaction> source) {
        List<Transaction> fixedFiltered = applyFixedFilters(source);
        String query = etSearch != null ? etSearch.getText().toString().trim().toLowerCase(Locale.ROOT) : "";

        if (query.isEmpty()) {
            return fixedFiltered;
        }

        List<Transaction> result = new ArrayList<>();
        for (Transaction transaction : fixedFiltered) {
            if (buildSearchText(transaction).contains(query)) {
                result.add(transaction);
            }
        }
        return result;
    }

    private File createStatementPdf(List<Transaction> transactions) throws IOException {
        PdfDocument document = new PdfDocument();
        int pageWidth = 595;
        int pageHeight = 842;
        int margin = 38;
        int pageNumber = 1;

        PdfDocument.Page page = document.startPage(
                new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create());
        Canvas canvas = page.getCanvas();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int y = drawStatementHeader(canvas, paint, pageWidth, margin, transactions);
        y = drawStatementTableHeader(canvas, paint, margin, y, pageWidth);

        for (Transaction transaction : transactions) {
            if (y > pageHeight - 64) {
                drawStatementFooter(canvas, paint, pageNumber, pageWidth, pageHeight);
                document.finishPage(page);
                pageNumber++;
                page = document.startPage(
                        new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create());
                canvas = page.getCanvas();
                y = drawStatementContinuationHeader(canvas, paint, pageWidth, margin);
                y = drawStatementTableHeader(canvas, paint, margin, y, pageWidth);
            }

            y = drawStatementRow(canvas, paint, transaction, margin, y, pageWidth);
        }

        drawStatementFooter(canvas, paint, pageNumber, pageWidth, pageHeight);
        document.finishPage(page);

        File directory = new File(getCacheDir(), "statements");
        if (!directory.exists() && !directory.mkdirs()) {
            document.close();
            throw new IOException("Unable to create statements directory");
        }

        String fileName = "SwiftBank_extras_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date()) + ".pdf";
        File file = new File(directory, fileName);
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            document.writeTo(outputStream);
        } finally {
            document.close();
        }

        return file;
    }

    private int drawStatementHeader(Canvas canvas,
                                    Paint paint,
                                    int pageWidth,
                                    int margin,
                                    List<Transaction> transactions) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(0, 55, 28));
        canvas.drawRect(0, 0, pageWidth, 92, paint);

        paint.setColor(Color.WHITE);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(24);
        canvas.drawText("SwiftBank", margin, 40, paint);

        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        paint.setTextSize(11);
        canvas.drawText("Extras generat din aplicația SwiftBank", margin, 62, paint);

        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(19);
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("Extras de cont", pageWidth - margin, 40, paint);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        paint.setTextSize(10);
        canvas.drawText(formatGeneratedAt(), pageWidth - margin, 62, paint);
        paint.setTextAlign(Paint.Align.LEFT);

        int y = 126;
        paint.setColor(Color.rgb(17, 24, 39));
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(18);
        canvas.drawText("Detalii extras", margin, y, paint);

        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        paint.setTextSize(11);
        paint.setColor(Color.rgb(75, 85, 99));
        y += 24;
        canvas.drawText("Cont: " + getStatementAccountLabel(), margin, y, paint);
        y += 18;
        canvas.drawText("Perioadă: " + getStatementPeriodLabel(), margin, y, paint);
        y += 18;
        canvas.drawText(fitText("Filtre: " + getStatementFilterLabel(), paint, pageWidth - 2 * margin), margin, y, paint);

        y += 28;
        drawStatementSummary(canvas, paint, margin, y, pageWidth, transactions);
        return y + 82;
    }

    private int drawStatementContinuationHeader(Canvas canvas, Paint paint, int pageWidth, int margin) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(0, 55, 28));
        canvas.drawRect(0, 0, pageWidth, 58, paint);

        paint.setColor(Color.WHITE);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(17);
        canvas.drawText("SwiftBank · Extras de cont", margin, 36, paint);

        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        paint.setTextSize(10);
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(getStatementPeriodLabel(), pageWidth - margin, 36, paint);
        paint.setTextAlign(Paint.Align.LEFT);
        return 88;
    }

    private void drawStatementSummary(Canvas canvas,
                                      Paint paint,
                                      int margin,
                                      int y,
                                      int pageWidth,
                                      List<Transaction> transactions) {
        Map<String, Double> income = new LinkedHashMap<>();
        Map<String, Double> expenses = new LinkedHashMap<>();
        Map<String, Double> balance = new LinkedHashMap<>();

        for (Transaction transaction : transactions) {
            String currency = transaction.getCurrency() != null ? transaction.getCurrency() : accountCurrency;
            double amount = transaction.getAmount();
            balance.put(currency, balance.getOrDefault(currency, 0.0) + amount);
            if (amount >= 0) {
                income.put(currency, income.getOrDefault(currency, 0.0) + amount);
            } else {
                expenses.put(currency, expenses.getOrDefault(currency, 0.0) + Math.abs(amount));
            }
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(245, 247, 250));
        canvas.drawRoundRect(margin, y - 18, pageWidth - margin, y + 58, 12, 12, paint);

        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(11);
        paint.setColor(Color.rgb(0, 150, 85));
        canvas.drawText("Intrări", margin + 18, y + 5, paint);
        paint.setColor(Color.rgb(239, 68, 68));
        canvas.drawText("Ieșiri", margin + 190, y + 5, paint);
        paint.setColor(Color.rgb(17, 24, 39));
        canvas.drawText("Sold perioadă", margin + 362, y + 5, paint);

        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        paint.setTextSize(10);
        paint.setColor(Color.rgb(55, 65, 81));
        canvas.drawText(fitText(formatCurrencyTotals(income, false), paint, 145), margin + 18, y + 28, paint);
        canvas.drawText(fitText(formatCurrencyTotals(expenses, false), paint, 145), margin + 190, y + 28, paint);
        canvas.drawText(fitText(formatCurrencyTotals(balance, true), paint, 150), margin + 362, y + 28, paint);
    }

    private int drawStatementTableHeader(Canvas canvas, Paint paint, int margin, int y, int pageWidth) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(229, 231, 235));
        canvas.drawRoundRect(margin, y, pageWidth - margin, y + 28, 8, 8, paint);

        paint.setColor(Color.rgb(55, 65, 81));
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(10);
        canvas.drawText("Data", margin + 10, y + 18, paint);
        canvas.drawText("Descriere", margin + 104, y + 18, paint);
        canvas.drawText("Tip", margin + 316, y + 18, paint);
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("Sumă", pageWidth - margin - 10, y + 18, paint);
        paint.setTextAlign(Paint.Align.LEFT);
        return y + 40;
    }

    private int drawStatementRow(Canvas canvas, Paint paint, Transaction transaction, int margin, int y, int pageWidth) {
        int rowHeight = 42;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(243, 244, 246));
        canvas.drawRect(margin, y + rowHeight - 1, pageWidth - margin, y + rowHeight, paint);

        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        paint.setTextSize(9);
        paint.setColor(Color.rgb(75, 85, 99));
        canvas.drawText(formatStatementDate(transaction.getCreatedAt()), margin + 10, y + 17, paint);

        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(10);
        paint.setColor(Color.rgb(17, 24, 39));
        canvas.drawText(fitText(getStatementDescription(transaction), paint, 202), margin + 104, y + 16, paint);

        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        paint.setTextSize(8);
        paint.setColor(Color.rgb(107, 114, 128));
        String secondaryText = formatStatementSecondaryLine(transaction);
        if (!secondaryText.isEmpty()) {
            canvas.drawText(fitText(secondaryText, paint, 202), margin + 104, y + 31, paint);
        }

        paint.setTextSize(9);
        paint.setColor(Color.rgb(55, 65, 81));
        canvas.drawText(fitText(getStatementTypeLabel(transaction), paint, 86), margin + 316, y + 22, paint);

        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(10);
        paint.setColor(transaction.getAmount() >= 0 ? Color.rgb(0, 150, 85) : Color.rgb(17, 24, 39));
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(formatStatementAmount(transaction), pageWidth - margin - 10, y + 22, paint);
        paint.setTextAlign(Paint.Align.LEFT);

        return y + rowHeight;
    }

    private void drawStatementFooter(Canvas canvas, Paint paint, int pageNumber, int pageWidth, int pageHeight) {
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        paint.setTextSize(8);
        paint.setColor(Color.rgb(107, 114, 128));
        canvas.drawText("Document generat automat de SwiftBank.", 38, pageHeight - 28, paint);
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("Pagina " + pageNumber, pageWidth - 38, pageHeight - 28, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void showStatementReadyDialog(File statementFile, int transactionCount) {
        String countText = transactionCount == 1 ? "1 tranzacție" : transactionCount + " tranzacții";
        new SwiftBankDialog(this)
                .setIcon(R.drawable.ic_document)
                .setTitle("Extras generat")
                .setMessage("Extrasul PDF este pregătit. Include " + countText
                        + " și respectă perioada, filtrele și căutarea selectate.")
                .setPrimaryButton("Deschide extrasul", v -> openStatementPdf(statementFile))
                .setSecondaryButton("Închide", null)
                .setCancelable(true)
                .show();
    }

    private void showStatementInfo(String title, String message) {
        new SwiftBankDialog(this)
                .setIcon(R.drawable.ic_document)
                .setTitle(title)
                .setMessage(message)
                .setPrimaryButton("Am înțeles", null)
                .setCancelable(true)
                .show();
    }

    private void openStatementPdf(File file) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        Intent viewIntent = new Intent(Intent.ACTION_VIEW);
        viewIntent.setDataAndType(uri, "application/pdf");
        viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            startActivity(Intent.createChooser(viewIntent, "Deschide extrasul"));
        } catch (ActivityNotFoundException e) {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/pdf");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                startActivity(Intent.createChooser(shareIntent, "Trimite extrasul"));
            } catch (ActivityNotFoundException ignored) {
                showStatementInfo("Nu pot deschide PDF-ul", "Nu există o aplicație disponibilă pentru fișiere PDF.");
            }
        }
    }

    private void setStatementButtonLoading(boolean loading) {
        if (btnStatement == null) return;
        btnStatement.setEnabled(!loading);
        btnStatement.setAlpha(loading ? 0.45f : 1f);
    }

    private String getStatementAccountLabel() {
        if (allAccountsMode) {
            return "Toate conturile";
        }

        for (AccountData account : accounts) {
            if (account.getAccountId() == accountId) {
                return "Personal - " + account.getCurrency();
            }
        }

        return "Cont " + accountCurrency;
    }

    private String getStatementAccountSentenceLabel() {
        if (allAccountsMode) {
            return "toate conturile";
        }

        return "contul " + getStatementAccountLabel();
    }

    private String getStatementPeriodLabel() {
        if (filterStartDate == null && filterEndDate == null) {
            return "Toată perioada disponibilă";
        }
        if (filterStartDate != null && filterEndDate != null) {
            return formatDateChip(filterStartDate) + " - " + formatDateChip(filterEndDate);
        }
        if (filterStartDate != null) {
            return "din " + formatDateChip(filterStartDate);
        }
        return "până la " + formatDateChip(filterEndDate);
    }

    private String getStatementFilterLabel() {
        List<String> filters = new ArrayList<>();
        if (fixedCategoryFilter != null && !fixedCategoryFilter.trim().isEmpty()) {
            filters.add("categorie " + translateStatementCategory(fixedCategoryFilter));
        }
        if (fixedMerchantFilter != null && !fixedMerchantFilter.trim().isEmpty()) {
            filters.add("comerciant " + fixedMerchantFilter.trim());
        }
        if (etSearch != null && !etSearch.getText().toString().trim().isEmpty()) {
            filters.add("căutare \"" + etSearch.getText().toString().trim() + "\"");
        }

        if (filters.isEmpty()) {
            return "fără filtre suplimentare";
        }

        return String.join(", ", filters);
    }

    private String getStatementDescription(Transaction transaction) {
        if (transaction instanceof TransferTransaction) {
            TransferTransaction transfer = (TransferTransaction) transaction;
            String type = transaction.getTransactionType();
            if ("SELF_IN".equals(type) || "SELF_OUT".equals(type)) {
                if (transfer.hasCurrencyConversion()) {
                    String from = transaction.getAmount() > 0 ? transfer.getOriginalCurrency() : transaction.getCurrency();
                    String to = transaction.getAmount() > 0 ? transaction.getCurrency() : transfer.getOriginalCurrency();
                    return "Schimb valutar " + safeValue(from) + " → " + safeValue(to);
                }
                return "Transfer între conturile proprii";
            }

            String beneficiary = transfer.getBeneficiaryName();
            return beneficiary != null && !beneficiary.trim().isEmpty() ? beneficiary : safeValue(transaction.getTitle());
        }

        if (transaction instanceof CardTransaction) {
            String merchant = ((CardTransaction) transaction).getMerchantName();
            return merchant != null && !merchant.trim().isEmpty() ? merchant : safeValue(transaction.getTitle());
        }

        if (transaction instanceof BillTransaction) {
            return ((BillTransaction) transaction).getBillerName();
        }

        return safeValue(transaction.getTitle());
    }

    private String getStatementTypeLabel(Transaction transaction) {
        if (transaction instanceof TransferTransaction) {
            String type = transaction.getTransactionType();
            if ("SELF_IN".equals(type) || "SELF_OUT".equals(type)) {
                return "Schimb valutar";
            }
            return "Transfer";
        }

        if (transaction instanceof BillTransaction) {
            return "Factură";
        }

        if (transaction instanceof CardTransaction) {
            return "Card";
        }

        return translateStatementCategory(getTransactionCategory(transaction));
    }

    private String formatStatementSecondaryLine(Transaction transaction) {
        if (transaction instanceof TransferTransaction) {
            String type = transaction.getTransactionType();
            if ("TRANSFER_IN".equals(type)) return "primit";
            if ("TRANSFER_OUT".equals(type)) return "trimis";
            if ("SELF_IN".equals(type)) return "primit \u00een cont";
            if ("SELF_OUT".equals(type)) return "trimis din cont";
            return transaction.getAmount() >= 0 ? "primit" : "trimis";
        }

        return formatStatementStatus(transaction.getStatus());
    }

    private String formatStatementStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return "";
        }

        switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "COMPLETED":
            case "FINALIZED":
                return "finalizată";
            case "PENDING":
                return "în așteptare";
            case "PENDING_APPROVAL":
                return "așteaptă confirmarea";
            case "FAILED":
            case "REJECTED":
                return "respinsă";
            case "CANCELLED":
                return "anulată";
            default:
                if ("UNKNOWN".equals(status.trim().toUpperCase(Locale.ROOT))
                        || "NULL".equals(status.trim().toUpperCase(Locale.ROOT))
                        || "NECUNOSCUT".equals(status.trim().toUpperCase(Locale.ROOT))) {
                    return "";
                }
                return status.toLowerCase(Locale.ROOT);
        }
    }

    private String formatStatementAmount(Transaction transaction) {
        String currency = transaction.getCurrency() != null ? transaction.getCurrency() : accountCurrency;
        double amount = transaction.getAmount();
        String prefix = amount > 0 ? "+" : "";
        return prefix + formatBalance(amount) + " " + getCurrencySymbol(currency);
    }

    private String formatStatementDate(String createdAt) {
        Date date = parseTransactionDate(createdAt);
        if (date.getTime() == 0) {
            return "-";
        }
        return new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(date);
    }

    private String formatGeneratedAt() {
        return "Generat la " + new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(new Date());
    }

    private String formatCurrencyTotals(Map<String, Double> totals, boolean keepSign) {
        if (totals.isEmpty()) {
            return "0,00";
        }

        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Double> entry : totals.entrySet()) {
            double value = entry.getValue();
            String sign = keepSign && value > 0 ? "+" : "";
            parts.add(sign + formatBalance(value) + " " + getCurrencySymbol(entry.getKey()));
        }
        return String.join(" · ", parts);
    }

    private String translateStatementCategory(String categoryName) {
        String normalized = normalizeFilterValue(categoryName);
        switch (normalized) {
            case "transfers":
            case "transferuri":
                return "Transferuri";
            case "food":
                return "Mâncare și băuturi";
            case "shopping":
                return "Cumpărături";
            case "transport":
                return "Transport";
            case "entertainment":
                return "Divertisment";
            case "groceries":
                return "Alimente";
            case "health":
                return "Sănătate";
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
            case "travel":
                return "Călătorii";
            case "services":
                return "Servicii";
            case "subscriptions":
                return "Abonamente";
            case "furniture":
                return "Mobilier";
            case "electronics":
                return "Electronice";
            case "other":
                return "Altele";
            default:
                return categoryName == null || categoryName.trim().isEmpty()
                        ? "Altele"
                        : categoryName.trim();
        }
    }

    private String fitText(String text, Paint paint, float maxWidth) {
        if (text == null) return "";
        String clean = text.trim().replace("\n", " ");
        if (paint.measureText(clean) <= maxWidth) {
            return clean;
        }

        String suffix = "...";
        while (clean.length() > 0 && paint.measureText(clean + suffix) > maxWidth) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return clean + suffix;
    }

    private String safeValue(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value.trim();
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
                return "Utilit\u0103\u021Bi";
            case "telecom":
                return "Telecom";
            case "internet":
                return "Internet";
            case "tv":
                return "TV & Cablu";
            case "insurance":
                return "Asigur\u0103ri";
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
            return "A\u0219teapt\u0103 confirmarea";
        }

        if (transaction instanceof BillTransaction) {
            BillTransaction bill = (BillTransaction) transaction;
            return getBillCategoryDisplayName(bill.getBillerCategory());
        }

        String subtitle = transaction.getSubtitle();
        if ("CARD".equals(transaction.getTransactionType()) && "PENDING".equals(transaction.getStatus())) {
            return "Suma blocat\u0103";
        }

        if ("CARD".equals(transaction.getTransactionType())) {
            String categoryDisplayName = getCategoryDisplayNameForTransaction(transaction);
            if (categoryDisplayName != null) {
                return categoryDisplayName;
            }
        }

        if ("CARD".equals(transaction.getTransactionType()) && (subtitle == null || subtitle.isEmpty())) {
            return "Plata cu cardul";
        }

        return subtitle != null && !subtitle.isEmpty() ? subtitle : formatTime(transaction.getCreatedAt());
    }

    private String getBillCategoryDisplayName(String category) {
        if (category == null || category.isEmpty()) return "Plat\u0103 factur\u0103";

        switch (category.toLowerCase()) {
            case "utilities":
                return "Utilit\u0103\u021Bi";
            case "telecom":
                return "Telecom";
            case "internet":
                return "Internet";
            case "tv":
                return "TV & Cablu";
            case "insurance":
                return "Asigur\u0103ri";
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

    private int dpToPx(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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
            case "EUR": return "\u20AC";
            case "USD": return "$";
            case "GBP": return "\u00A3";
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

                if ("MULTI".equals(header.currency)) {
                    tvTotal.setText("");
                    return;
                }

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
