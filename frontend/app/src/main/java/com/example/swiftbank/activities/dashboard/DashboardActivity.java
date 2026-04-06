package com.example.swiftbank.activities.dashboard;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
import com.example.swiftbank.api.dto.response.data.AccountData;
import com.example.swiftbank.api.dto.response.data.AccountsData;
import com.example.swiftbank.api.dto.response.data.TransactionsData;
import com.example.swiftbank.storage.TokenManager;
import com.example.swiftbank.views.ParticlesView;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class DashboardActivity extends AppCompatActivity {

    private static final String TAG = "DashboardActivity";

    // Views
    private View gradientBackground;
    private ParticlesView particlesView;
    private LinearLayout balanceSection;
    private TextView tvAvatarInitials;
    private TextView tvGreeting;
    private TextView tvUserName;
    private TextView tvAccountType;
    private TextView tvTotalBalance;
    private TextView tvCurrencyLabel;
    private Button btnAccounts;
    private LinearLayout dotsIndicator;
    private GestureDetector gestureDetector;
    private RecyclerView rvTransactions;
    private LinearLayout emptyTransactionsState;
    private BottomNavigationView bottomNavigation;

    // Quick Actions
    private LinearLayout btnAddMoney, btnSend, btnExchange, btnMore;

    // State
    private int selectedAccountIndex = 0;
    private List<Account> accounts = new ArrayList<>();
    private List<Object> transactionItems = new ArrayList<>();

    // Adapters
    private TransactionsAdapter transactionsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        initViews();
        setupListeners();
        setupTransactionsList();
        updateGreeting();
        loadAccounts();
    }

    private void initViews() {
        gradientBackground = findViewById(R.id.gradientBackground);
        particlesView = findViewById(R.id.particlesView);
        balanceSection = findViewById(R.id.balanceSection);
        tvAvatarInitials = findViewById(R.id.tvAvatarInitials);
        tvGreeting = findViewById(R.id.tvGreeting);
        tvUserName = findViewById(R.id.tvUserName);
        tvAccountType = findViewById(R.id.tvAccountType);
        tvTotalBalance = findViewById(R.id.tvTotalBalance);
        tvCurrencyLabel = findViewById(R.id.tvCurrencyLabel);
        btnAccounts = findViewById(R.id.btnAccounts);
        dotsIndicator = findViewById(R.id.dotsIndicator);
        rvTransactions = findViewById(R.id.rvTransactions);
        emptyTransactionsState = findViewById(R.id.emptyTransactionsState);
        bottomNavigation = findViewById(R.id.bottomNavigation);

        btnAddMoney = findViewById(R.id.btnAddMoney);
        btnSend = findViewById(R.id.btnSend);
        btnExchange = findViewById(R.id.btnExchange);
        btnMore = findViewById(R.id.btnMore);

        setupSwipeGesture();
    }

    private void setupSwipeGesture() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 100;
            private static final int SWIPE_VELOCITY_THRESHOLD = 100;

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;

                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();

                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            // Swipe right - previous account
                            switchToPreviousAccount();
                        } else {
                            // Swipe left - next account
                            switchToNextAccount();
                        }
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }
        });

        balanceSection.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return true;
        });
    }

    private void switchToNextAccount() {
        if (accounts.isEmpty()) return;
        selectedAccountIndex = (selectedAccountIndex + 1) % accounts.size();
        onAccountChanged();
    }

    private void switchToPreviousAccount() {
        if (accounts.isEmpty()) return;
        selectedAccountIndex = (selectedAccountIndex - 1 + accounts.size()) % accounts.size();
        onAccountChanged();
    }

    private void onAccountChanged() {
        updateAccountDisplay();
        updateDotsIndicator();
        loadTransactionsForAccount(accounts.get(selectedAccountIndex));
    }

    private void setupListeners() {
        btnAccounts.setOnClickListener(v -> showAccountsBottomSheet());

        btnAddMoney.setOnClickListener(v -> {
            Toast.makeText(this, "Adaugă bani - Coming soon", Toast.LENGTH_SHORT).show();
        });

        btnSend.setOnClickListener(v -> {
            Toast.makeText(this, "Trimite - Coming soon", Toast.LENGTH_SHORT).show();
        });

        btnExchange.setOnClickListener(v -> {
            Toast.makeText(this, "Schimbă valută - Coming soon", Toast.LENGTH_SHORT).show();
        });

        btnMore.setOnClickListener(v -> {
            Toast.makeText(this, "Mai multe opțiuni - Coming soon", Toast.LENGTH_SHORT).show();
        });

        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                return true;
            } else if (id == R.id.nav_cards) {
                Toast.makeText(this, "Carduri - Coming soon", Toast.LENGTH_SHORT).show();
                return true;
            } else if (id == R.id.nav_payments) {
                Toast.makeText(this, "Plăți - Coming soon", Toast.LENGTH_SHORT).show();
                return true;
            } else if (id == R.id.nav_stats) {
                Toast.makeText(this, "Statistici - Coming soon", Toast.LENGTH_SHORT).show();
                return true;
            } else if (id == R.id.nav_profile) {
                Toast.makeText(this, "Profil - Coming soon", Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        });

        findViewById(R.id.btnSeeAllTransactions).setOnClickListener(v -> {
            Toast.makeText(this, "Vezi toate tranzacțiile - Coming soon", Toast.LENGTH_SHORT).show();
        });
    }

    private void showAccountsBottomSheet() {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_accounts, null);
        bottomSheet.setContentView(sheetView);

        RecyclerView rvAccounts = sheetView.findViewById(R.id.rvAccounts);
        rvAccounts.setLayoutManager(new LinearLayoutManager(this));

        AccountsBottomSheetAdapter adapter = new AccountsBottomSheetAdapter(accounts, selectedAccountIndex, position -> {
            selectedAccountIndex = position;
            updateAccountDisplay();
            updateDotsIndicator();
            loadTransactionsForAccount(accounts.get(position));
            bottomSheet.dismiss();
        });
        rvAccounts.setAdapter(adapter);

        LinearLayout btnAddAccount = sheetView.findViewById(R.id.btnAddAccount);
        btnAddAccount.setOnClickListener(v -> {
            Toast.makeText(this, "Adaugă cont nou - Coming soon", Toast.LENGTH_SHORT).show();
            bottomSheet.dismiss();
        });

        bottomSheet.show();
    }

    private void updateGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting;

        if (hour >= 5 && hour < 12) {
            greeting = "Bună dimineața";
        } else if (hour >= 12 && hour < 18) {
            greeting = "Bună ziua";
        } else {
            greeting = "Bună seara";
        }

        tvGreeting.setText(greeting);

        // TODO: Get user name from TokenManager or API
        String firstName = "Lucas";
        tvUserName.setText(firstName);
        tvAvatarInitials.setText(getInitials(firstName, "Cirimpei"));
    }

    private String getInitials(String firstName, String lastName) {
        String initials = "";
        if (firstName != null && !firstName.isEmpty()) {
            initials += firstName.charAt(0);
        }
        if (lastName != null && !lastName.isEmpty()) {
            initials += lastName.charAt(0);
        }
        return initials.toUpperCase();
    }

    private void loadAccounts() {
        ApiClient.getAccountService().getAccounts().enqueue(new Callback<ApiResponse<AccountsData>>() {
            @Override
            public void onResponse(Call<ApiResponse<AccountsData>> call, Response<ApiResponse<AccountsData>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    List<AccountData> accountsData = response.body().getData().getAccounts();

                    accounts.clear();
                    for (AccountData acc : accountsData) {
                        accounts.add(new Account(
                                acc.getAccountId(),
                                acc.getIban(),
                                acc.getCurrency(),
                                acc.getBalance(),
                                acc.getAccountType()
                        ));
                    }

                    if (!accounts.isEmpty()) {
                        setupDotsIndicator();
                        updateAccountDisplay();
                        loadTransactionsForAccount(accounts.get(selectedAccountIndex));
                    }
                } else {
                    Toast.makeText(DashboardActivity.this, "Eroare la incarcarea conturilor", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<AccountsData>> call, Throwable t) {
                Toast.makeText(DashboardActivity.this, "Eroare de conexiune", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadTransactionsForAccount(Account account) {
        ApiClient.getTransactionService().getTransactions(account.id, 10, 0)
                .enqueue(new Callback<ApiResponse<TransactionsData>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<TransactionsData>> call, Response<ApiResponse<TransactionsData>> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                            List<com.example.swiftbank.api.dto.transaction.Transaction> apiTransactions =
                                    response.body().getData().getTransactions();

                            transactionItems.clear();
                            String lastDateGroup = "";

                            for (com.example.swiftbank.api.dto.transaction.Transaction t : apiTransactions) {
                                String dateGroup = getDateGroup(t.getCreatedAt());

                                if (!dateGroup.equals(lastDateGroup)) {
                                    transactionItems.add(dateGroup);
                                    lastDateGroup = dateGroup;
                                }

                                transactionItems.add(new Transaction(
                                        t.getTitle(),
                                        t.getSubtitle(),
                                        t.getAmount(),
                                        t.getCurrency() != null ? t.getCurrency().trim() : account.currency,
                                        formatTime(t.getCreatedAt()),
                                        getIconForTransaction(t)
                                ));
                            }

                            if (transactionsAdapter != null) {
                                transactionsAdapter.notifyDataSetChanged();
                            }
                            updateTransactionsVisibility();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<TransactionsData>> call, Throwable t) {
                        Toast.makeText(DashboardActivity.this, "Eroare la incarcarea tranzactiilor", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private String getDateGroup(String createdAt) {
        if (createdAt == null) return "ALTELE";

        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            java.util.Date date = sdf.parse(createdAt);
            if (date == null) return "ALTELE";

            Calendar transactionCal = Calendar.getInstance();
            transactionCal.setTime(date);

            Calendar today = Calendar.getInstance();
            Calendar yesterday = Calendar.getInstance();
            yesterday.add(Calendar.DAY_OF_YEAR, -1);

            if (isSameDay(transactionCal, today)) {
                return "AZI";
            } else if (isSameDay(transactionCal, yesterday)) {
                return "IERI";
            } else {
                java.text.SimpleDateFormat outputFormat = new java.text.SimpleDateFormat("d MMMM", new Locale("ro"));
                return outputFormat.format(date).toUpperCase();
            }
        } catch (Exception e) {
            return "ALTELE";
        }
    }

    private boolean isSameDay(Calendar cal1, Calendar cal2) {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

    private String formatTime(String createdAt) {
        if (createdAt == null) return "";
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            java.util.Date date = sdf.parse(createdAt);
            java.text.SimpleDateFormat outputFormat = new java.text.SimpleDateFormat("HH:mm", Locale.getDefault());
            return outputFormat.format(date);
        } catch (Exception e) {
            return "";
        }
    }

    private int getIconForTransaction(com.example.swiftbank.api.dto.transaction.Transaction t) {
        String type = t.getTransactionType();
        if (type == null) return R.drawable.ic_shopping;

        switch (type) {
            case "TRANSFER":
                return t.getAmount() > 0 ? R.drawable.ic_transfer_in : R.drawable.ic_send;
            case "BILL":
                return R.drawable.ic_payments;
            case "CARD":
            default:
                return R.drawable.ic_shopping;
        }
    }

    private void updateTransactionsVisibility() {
        if (transactionItems.isEmpty()) {
            emptyTransactionsState.setVisibility(View.VISIBLE);
            rvTransactions.setVisibility(View.GONE);
        } else {
            emptyTransactionsState.setVisibility(View.GONE);
            rvTransactions.setVisibility(View.VISIBLE);
        }
    }

    private void updateAccountDisplay() {
        if (accounts.isEmpty()) return;

        Account account = accounts.get(selectedAccountIndex);

        // Update account type label
        tvAccountType.setText(account.type + " · " + account.currency);

        // Update balance
        tvTotalBalance.setText(formatBalance(account.balance));

        // Update currency label
        tvCurrencyLabel.setText(getCurrencySymbol(account.currency));

        // Update gradient based on currency
        updateGradientForCurrency(account.currency);
    }

    private void updateGradientForCurrency(String currency) {
        int gradientRes;
        switch (currency) {
            case "EUR":
                gradientRes = R.drawable.gradient_eur;
                break;
            case "USD":
                gradientRes = R.drawable.gradient_usd;
                break;
            case "RON":
            default:
                gradientRes = R.drawable.gradient_ron;
                break;
        }
        gradientBackground.setBackgroundResource(gradientRes);
    }

    private String formatBalance(double balance) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.getDefault());
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        DecimalFormat df = new DecimalFormat("#,##0.00", symbols);
        return df.format(balance);
    }

    private String getCurrencySymbol(String currency) {
        switch (currency) {
            case "EUR": return "€";
            case "USD": return "$";
            case "GBP": return "£";
            default: return "lei";
        }
    }

    private void setupDotsIndicator() {
        dotsIndicator.removeAllViews();
        float density = getResources().getDisplayMetrics().density;

        for (int i = 0; i < accounts.size(); i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    (int) (10 * density),
                    (int) (10 * density)
            );
            params.setMargins(
                    (int) (6 * density), 0,
                    (int) (6 * density), 0
            );
            dot.setLayoutParams(params);
            dot.setBackgroundResource(R.drawable.dot_indicator);
            dot.setSelected(i == selectedAccountIndex);

            final int index = i;
            dot.setOnClickListener(v -> {
                selectedAccountIndex = index;
                updateAccountDisplay();
                updateDotsIndicator();
                loadTransactionsForAccount(accounts.get(index));
            });

            dotsIndicator.addView(dot);
        }
    }

    private void updateDotsIndicator() {
        for (int i = 0; i < dotsIndicator.getChildCount(); i++) {
            dotsIndicator.getChildAt(i).setSelected(i == selectedAccountIndex);
        }
    }

    private void setupTransactionsList() {
        transactionsAdapter = new TransactionsAdapter(transactionItems);
        rvTransactions.setLayoutManager(new LinearLayoutManager(this));
        rvTransactions.setAdapter(transactionsAdapter);
        rvTransactions.setNestedScrollingEnabled(false);

        updateTransactionsVisibility();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (particlesView != null) {
            particlesView.startAnimation();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (particlesView != null) {
            particlesView.stopAnimation();
        }
    }

    // ==================== DATA CLASSES ====================

    static class Account {
        int id;
        String iban;
        String currency;
        double balance;
        String type;

        Account(int id, String iban, String currency, double balance, String type) {
            this.id = id;
            this.iban = iban;
            this.currency = currency;
            this.balance = balance;
            this.type = type;
        }
    }

    static class Transaction {
        String title;
        String subtitle;
        double amount;
        String currency;
        String time;
        int iconRes;

        Transaction(String title, String subtitle, double amount, String currency, String time, int iconRes) {
            this.title = title;
            this.subtitle = subtitle;
            this.amount = amount;
            this.currency = currency;
            this.time = time;
            this.iconRes = iconRes;
        }
    }

    // ==================== ADAPTERS ====================

    // Accounts Bottom Sheet Adapter
    class AccountsBottomSheetAdapter extends RecyclerView.Adapter<AccountsBottomSheetAdapter.ViewHolder> {
        private List<Account> accounts;
        private int selectedIndex;
        private OnAccountClickListener listener;

        interface OnAccountClickListener {
            void onAccountClick(int position);
        }

        AccountsBottomSheetAdapter(List<Account> accounts, int selectedIndex, OnAccountClickListener listener) {
            this.accounts = accounts;
            this.selectedIndex = selectedIndex;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_account_bottom_sheet, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Account account = accounts.get(position);
            holder.bind(account, position == selectedIndex);
        }

        @Override
        public int getItemCount() {
            return accounts.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivFlag, ivSelected;
            TextView tvAccountName, tvIban, tvBalance, tvCurrency;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                ivFlag = itemView.findViewById(R.id.ivFlag);
                ivSelected = itemView.findViewById(R.id.ivSelected);
                tvAccountName = itemView.findViewById(R.id.tvAccountName);
                tvIban = itemView.findViewById(R.id.tvIban);
                tvBalance = itemView.findViewById(R.id.tvBalance);
                tvCurrency = itemView.findViewById(R.id.tvCurrency);

                itemView.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onAccountClick(getAdapterPosition());
                    }
                });
            }

            void bind(Account account, boolean isSelected) {
                tvAccountName.setText(account.type + " · " + account.currency);
                tvIban.setText("•••• " + account.iban.substring(account.iban.length() - 4));
                tvBalance.setText(formatBalance(account.balance));
                tvCurrency.setText(getCurrencySymbol(account.currency));

                ivSelected.setVisibility(isSelected ? View.VISIBLE : View.GONE);

                // Set flag
                switch (account.currency) {
                    case "RON":
                        ivFlag.setImageResource(R.drawable.flag_ro);
                        break;
                    case "EUR":
                        ivFlag.setImageResource(R.drawable.flag_eur);
                        break;
                    case "USD":
                        ivFlag.setImageResource(R.drawable.flag_usd);
                        break;
                }
            }
        }
    }

    // Transactions Adapter
    class TransactionsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_HEADER = 0;
        private static final int TYPE_TRANSACTION = 1;

        private List<Object> items;

        TransactionsAdapter(List<Object> items) {
            this.items = items;
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position) instanceof String ? TYPE_HEADER : TYPE_TRANSACTION;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_HEADER) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_transaction_header, parent, false);
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
                ((HeaderViewHolder) holder).bind((String) items.get(position));
            } else {
                ((TransactionViewHolder) holder).bind((Transaction) items.get(position));
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class HeaderViewHolder extends RecyclerView.ViewHolder {
            TextView tvDateHeader;

            HeaderViewHolder(@NonNull View itemView) {
                super(itemView);
                tvDateHeader = itemView.findViewById(R.id.tvDateHeader);
            }

            void bind(String header) {
                tvDateHeader.setText(header);
            }
        }

        class TransactionViewHolder extends RecyclerView.ViewHolder {
            ImageView ivCategoryIcon;
            TextView tvMerchantName, tvCategory, tvAmount, tvTime;

            TransactionViewHolder(@NonNull View itemView) {
                super(itemView);
                ivCategoryIcon = itemView.findViewById(R.id.ivCategoryIcon);
                tvMerchantName = itemView.findViewById(R.id.tvMerchantName);
                tvCategory = itemView.findViewById(R.id.tvCategory);
                tvAmount = itemView.findViewById(R.id.tvAmount);
                tvTime = itemView.findViewById(R.id.tvTime);
            }

            void bind(Transaction transaction) {
                tvMerchantName.setText(transaction.title);
                tvCategory.setText(transaction.subtitle);
                tvTime.setText(transaction.time);

                String amountText;
                if (transaction.amount >= 0) {
                    amountText = "+" + formatBalance(transaction.amount) + " " + getCurrencySymbol(transaction.currency);
                    tvAmount.setTextColor(ContextCompat.getColor(DashboardActivity.this, R.color.green_accent));
                } else {
                    amountText = formatBalance(transaction.amount) + " " + getCurrencySymbol(transaction.currency);
                    tvAmount.setTextColor(ContextCompat.getColor(DashboardActivity.this, R.color.white));
                }
                tvAmount.setText(amountText);

                try {
                    ivCategoryIcon.setImageResource(transaction.iconRes);
                } catch (Exception e) {
                    ivCategoryIcon.setImageResource(R.drawable.ic_shopping);
                }
            }
        }
    }
}
