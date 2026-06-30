package com.example.swiftbank.activities.dashboard;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.swiftbank.activities.cards.CardPaymentApprovalActivity;
import com.example.swiftbank.api.dto.response.data.success.transaction.BillTransaction;
import com.example.swiftbank.api.dto.response.data.success.transaction.CardTransaction;
import com.example.swiftbank.api.dto.response.data.success.transaction.TransferTransaction;
import com.example.swiftbank.utils.ExchangeTitleFormatter;
import com.example.swiftbank.utils.RemoteImageLoader;
import com.example.swiftbank.utils.SwiftBankDialog;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.swiftbank.R;
import com.example.swiftbank.api.ApiClient;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.request.AddAccountRequest;
import com.example.swiftbank.api.dto.response.ApiErrorResponse;
import com.example.swiftbank.api.dto.response.data.success.AccountData;
import com.example.swiftbank.api.dto.response.data.success.AccountsData;
import com.example.swiftbank.api.dto.response.data.success.NewAccountData;
import com.example.swiftbank.api.dto.response.data.success.ProfileData;
import com.example.swiftbank.api.dto.response.data.success.TransactionsData;
import com.example.swiftbank.api.dto.response.data.success.RatesData;
import com.example.swiftbank.api.dto.response.data.error.ErrorParser;
import com.example.swiftbank.managers.RatesManager;
import com.example.swiftbank.views.ParticlesView;
import com.example.swiftbank.activities.settings.SettingsActivity;
import com.example.swiftbank.activities.settings.ProfileActivity;
import com.example.swiftbank.managers.RealtimeManager;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class DashboardActivity extends AppCompatActivity {

    private static final String TAG = "DashboardActivity";
    private static final String PREFS_NAME = "SwiftBankNotifications";
    private static final String KEY_PERMISSION_ASKED = "notification_permission_asked";
    private static final String ACTION_REFRESH_DATA = "com.example.swiftbank.REFRESH_DATA";
    private static final int REQUEST_CARD_PAYMENT_APPROVAL = 2402;
    private static final long DATA_REFRESH_DEBOUNCE_MS = 350L;
    private static final int TRANSACTIONS_PAGE_SIZE = 10;

    // Permission launcher pentru notificări
    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    // Permisiune acordată - înregistrăm device-ul pentru FCM
                    com.example.swiftbank.managers.FCMTokenManager.getInstance(this).registerDevice();
                }
                // Marcăm că am întrebat (indiferent de răspuns)
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .edit()
                        .putBoolean(KEY_PERMISSION_ASKED, true)
                        .apply();
            });

    // Views
    private View gradientBackground;
    private ParticlesView particlesView;
    private LinearLayout balanceSection;
    private TextView tvAvatarInitials;
    private ImageView ivAvatarPhoto;
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

    // Skeleton views
    private LinearLayout profileSkeleton, profileContent;
    private LinearLayout balanceSkeleton;
    private LinearLayout transactionsSkeleton;

    // Quick Actions
    private LinearLayout btnSend, btnExchange, btnMore, btnStats;
    private ImageView btnSettings;

    // State
    private int selectedAccountIndex = 0;
    private List<Account> accounts = new ArrayList<>();
    private List<Object> transactionItems = new ArrayList<>();
    private String userFirstName = "";
    private String userLastName = "";
    private int currentUserId = -1;
    private boolean refreshReceiverRegistered = false;
    private boolean didInitialResume = false;
    private boolean accountsRequestInFlight = false;
    private boolean queuedAccountsRefresh = false;
    private int displayedTransactionsAccountId = -1;
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable dashboardRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            loadAccounts();
        }
    };
    private Call<ApiResponse<AccountsData>> accountsCall;
    private Call<ApiResponse<TransactionsData>> transactionsCall;

    // Transactions pagination
    private int transactionsOffset = 0;
    private boolean transactionsHasMore = true;
    private boolean isLoadingMoreTransactions = false;
    private List<com.example.swiftbank.api.dto.response.data.success.transaction.Transaction> allApiTransactions = new ArrayList<>();

    private final BroadcastReceiver refreshReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            requestDashboardRefresh();
        }
    };
    private double displayedBalance = 0; // Pentru animația soldului

    // Realtime
    private RealtimeManager.RealtimeListener realtimeListener;

    // Adapters
    private TransactionsAdapter transactionsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        initViews();
        setupListeners();
        setupTransactionsList();
        registerRefreshReceiver();
        loadProfile();
        loadAccounts();
        loadRates();
        checkNotificationPermission();
    }

    private void registerRefreshReceiver() {
        IntentFilter filter = new IntentFilter(ACTION_REFRESH_DATA);
        ContextCompat.registerReceiver(this, refreshReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        refreshReceiverRegistered = true;
    }

    private void checkNotificationPermission() {
        // Doar pentru Android 13+
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }

        // Verifică dacă am întrebat deja
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_PERMISSION_ASKED, false)) {
            return;
        }

        // Verifică dacă permisiunea e deja acordată
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // Arată dialog explicativ, apoi cere permisiunea
        new SwiftBankDialog(this)
                .setIcon(R.drawable.ic_notifications)
                .setTitle("Activează notificările")
                .setMessage("Primește alerte instant când:\n\n• Primești bani în cont\n• Efectuezi un transfer\n• Detectăm activitate suspectă")
                .setPrimaryButton("Activează", v -> {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                })
                .setSecondaryButton("Mai târziu", v -> {
                    // Marcăm că am întrebat
                    prefs.edit().putBoolean(KEY_PERMISSION_ASKED, true).apply();
                })
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (didInitialResume) {
            requestDashboardRefresh();
        } else {
            didInitialResume = true;
        }
        loadRates();

        // Start particles animation
        if (particlesView != null) {
            particlesView.startAnimation();
        }
    }

    private void loadRates() {
        RatesManager ratesManager = RatesManager.getInstance(this);

        // Verifică inteligent dacă trebuie refresh (bazat pe ora BNR 13:00)
        if (ratesManager.needsRefresh()) {
            fetchRatesFromApi();
        }
    }

    private void fetchRatesFromApi() {
        ApiClient.getRatesService().getRates().enqueue(new Callback<ApiResponse<RatesData>>() {
            @Override
            public void onResponse(Call<ApiResponse<RatesData>> call, Response<ApiResponse<RatesData>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    RatesData data = response.body().getData();
                    if (data.getRates() != null && !data.getRates().isEmpty()) {
                        Map<String, Double> ratesMap = new HashMap<>();
                        String rateDate = null;

                        for (RatesData.RateItem rate : data.getRates()) {
                            ratesMap.put(rate.getFromCurrency(), rate.getRate());
                            if (rateDate == null) {
                                rateDate = rate.getRateDate(); // Ia data din primul item
                            }
                        }

                        RatesManager.getInstance(DashboardActivity.this).saveRates(ratesMap, rateDate);
                    }
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<RatesData>> call, Throwable t) {
                // Silent fail - folosește ratele din cache dacă există
            }
        });
    }

    private void initViews() {
        gradientBackground = findViewById(R.id.gradientBackground);
        particlesView = findViewById(R.id.particlesView);
        balanceSection = findViewById(R.id.balanceSection);
        tvAvatarInitials = findViewById(R.id.tvAvatarInitials);
        ivAvatarPhoto = findViewById(R.id.ivAvatarPhoto);
        tvGreeting = findViewById(R.id.tvGreeting);
        tvUserName = findViewById(R.id.tvUserName);
        tvAccountType = findViewById(R.id.tvAccountType);
        tvTotalBalance = findViewById(R.id.tvTotalBalance);
        tvCurrencyLabel = findViewById(R.id.tvCurrencyLabel);
        btnAccounts = findViewById(R.id.btnAccounts);
        dotsIndicator = findViewById(R.id.dotsIndicator);
        rvTransactions = findViewById(R.id.rvTransactions);
        emptyTransactionsState = findViewById(R.id.emptyTransactionsState);

        btnSend = findViewById(R.id.btnSend);
        btnExchange = findViewById(R.id.btnExchange);
        btnMore = findViewById(R.id.btnMore);
        btnStats = findViewById(R.id.btnStats);
        btnSettings = findViewById(R.id.btnSettings);

        // Skeleton views
        profileSkeleton = findViewById(R.id.profileSkeleton);
        profileContent = findViewById(R.id.profileContent);
        balanceSkeleton = findViewById(R.id.balanceSkeleton);
        transactionsSkeleton = findViewById(R.id.transactionsSkeleton);

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

        btnSend.setOnClickListener(v -> {
            startActivity(new Intent(this, com.example.swiftbank.activities.send.SendOptionsActivity.class));
        });

        btnExchange.setOnClickListener(v -> {
            if (accounts.size() < 2) {
                SwiftBankDialog.showInfoDialog(this,
                    "Conturi insuficiente",
                    "Ai nevoie de cel puțin două conturi pentru a muta bani între ele.");
            } else {
                startActivity(new android.content.Intent(this, com.example.swiftbank.activities.exchange.ExchangeActivity.class));
            }
        });

        btnMore.setOnClickListener(v -> {
            startActivity(new Intent(this, com.example.swiftbank.activities.cards.CardsActivity.class));
        });

        btnSettings.setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
        });

        btnStats.setOnClickListener(v -> {
            startActivity(new Intent(this, com.example.swiftbank.activities.statistics.StatisticsActivity.class));
        });

        profileContent.setOnClickListener(v -> {
            startActivity(new Intent(this, ProfileActivity.class));
        });

        findViewById(R.id.btnSeeAllTransactions).setOnClickListener(v -> {
            if (!accounts.isEmpty()) {
                Account account = accounts.get(selectedAccountIndex);
                Intent intent = new Intent(this, com.example.swiftbank.activities.transactions.TransactionsActivity.class);
                intent.putExtra(com.example.swiftbank.activities.transactions.TransactionsActivity.EXTRA_ACCOUNT_ID, account.id);
                intent.putExtra(com.example.swiftbank.activities.transactions.TransactionsActivity.EXTRA_ACCOUNT_CURRENCY, account.currency);
                startActivity(intent);
            }
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

        // Ascunde butonul dacă ai toate cele 4 conturi (RON, EUR, USD, GBP)
        if (accounts.size() >= 4) {
            btnAddAccount.setVisibility(View.GONE);
        } else {
            btnAddAccount.setOnClickListener(v -> {
                bottomSheet.dismiss();
                showAddAccountDialog();
            });
        }

        bottomSheet.show();
    }

    private void showAddAccountDialog() {
        // Verifică ce valute are deja
        List<String> existingCurrencies = new ArrayList<>();
        for (Account acc : accounts) {
            existingCurrencies.add(acc.currency);
        }

        // Valutele disponibile
        List<CurrencyOption> availableCurrencies = new ArrayList<>();

        if (!existingCurrencies.contains("EUR")) {
            availableCurrencies.add(new CurrencyOption("EUR", "Euro", R.drawable.flag_eur));
        }
        if (!existingCurrencies.contains("USD")) {
            availableCurrencies.add(new CurrencyOption("USD", "Dolar american", R.drawable.flag_usd));
        }
        if (!existingCurrencies.contains("GBP")) {
            availableCurrencies.add(new CurrencyOption("GBP", "Liră sterlină", R.drawable.flag_gbp));
        }

        if (availableCurrencies.isEmpty()) {
            showInfoDialog("Toate conturile create", "Ai deja conturi în toate valutele disponibile (RON, EUR, USD, GBP).");
            return;
        }

        // Afișează bottom sheet
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_add_account, null);
        bottomSheet.setContentView(sheetView);

        // Views
        TextView tvSheetTitle = sheetView.findViewById(R.id.tvSheetTitle);
        RecyclerView rvCurrencies = sheetView.findViewById(R.id.rvCurrencies);
        LinearLayout loadingContainer = sheetView.findViewById(R.id.loadingContainer);
        TextView tvLoadingMessage = sheetView.findViewById(R.id.tvLoadingMessage);
        View dot1 = sheetView.findViewById(R.id.dot1);
        View dot2 = sheetView.findViewById(R.id.dot2);
        View dot3 = sheetView.findViewById(R.id.dot3);

        rvCurrencies.setLayoutManager(new LinearLayoutManager(this));

        CurrencyOptionsAdapter adapter = new CurrencyOptionsAdapter(availableCurrencies, currency -> {
            // Arată loading state
            tvSheetTitle.setText("Un moment...");
            tvLoadingMessage.setText("Contul în " + currency.code + " este pe drum...");
            rvCurrencies.setVisibility(View.GONE);
            loadingContainer.setVisibility(View.VISIBLE);
            bottomSheet.setCancelable(false);

            // Pornește animația bouncing dots
            AnimatorSet bounceAnimation = createBouncingDotsAnimation(dot1, dot2, dot3);
            bounceAnimation.start();

            // Creează contul cu durată minimă de loading
            long startTime = System.currentTimeMillis();
            addAccount(currency.code, bottomSheet, bounceAnimation, startTime);
        });
        rvCurrencies.setAdapter(adapter);

        bottomSheet.show();
    }

    private AnimatorSet createBouncingDotsAnimation(View dot1, View dot2, View dot3) {
        int duration = 400;
        float bounceHeight = -20f;

        ObjectAnimator bounce1 = ObjectAnimator.ofFloat(dot1, "translationY", 0f, bounceHeight, 0f);
        bounce1.setDuration(duration);
        bounce1.setInterpolator(new AccelerateDecelerateInterpolator());

        ObjectAnimator bounce2 = ObjectAnimator.ofFloat(dot2, "translationY", 0f, bounceHeight, 0f);
        bounce2.setDuration(duration);
        bounce2.setInterpolator(new AccelerateDecelerateInterpolator());
        bounce2.setStartDelay(150);

        ObjectAnimator bounce3 = ObjectAnimator.ofFloat(dot3, "translationY", 0f, bounceHeight, 0f);
        bounce3.setDuration(duration);
        bounce3.setInterpolator(new AccelerateDecelerateInterpolator());
        bounce3.setStartDelay(300);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(bounce1, bounce2, bounce3);
        animatorSet.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                animatorSet.start();
            }
        });

        return animatorSet;
    }

    // ==================== CURRENCY OPTION ====================

    static class CurrencyOption {
        String code;
        String name;
        int flagRes;

        CurrencyOption(String code, String name, int flagRes) {
            this.code = code;
            this.name = name;
            this.flagRes = flagRes;
        }
    }

    // ==================== CURRENCY OPTIONS ADAPTER ====================

    class CurrencyOptionsAdapter extends RecyclerView.Adapter<CurrencyOptionsAdapter.ViewHolder> {
        private List<CurrencyOption> currencies;
        private OnCurrencyClickListener listener;

        interface OnCurrencyClickListener {
            void onCurrencyClick(CurrencyOption currency);
        }

        CurrencyOptionsAdapter(List<CurrencyOption> currencies, OnCurrencyClickListener listener) {
            this.currencies = currencies;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_currency_option, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(currencies.get(position));
        }

        @Override
        public int getItemCount() {
            return currencies.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivFlag;
            TextView tvCurrencyName, tvCurrencyCode;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                ivFlag = itemView.findViewById(R.id.ivFlag);
                tvCurrencyName = itemView.findViewById(R.id.tvCurrencyName);
                tvCurrencyCode = itemView.findViewById(R.id.tvCurrencyCode);

                itemView.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onCurrencyClick(currencies.get(getAdapterPosition()));
                    }
                });
            }

            void bind(CurrencyOption currency) {
                tvCurrencyName.setText(currency.name);
                tvCurrencyCode.setText(currency.code);
                ivFlag.setImageResource(currency.flagRes);
            }
        }
    }

    private static final long MIN_LOADING_DURATION = 4000; // 4 secunde

    private void addAccount(String currency, BottomSheetDialog bottomSheet, AnimatorSet animation, long startTime) {
        ApiClient.getAccountService().addAccount(new AddAccountRequest(currency))
                .enqueue(new Callback<ApiResponse<NewAccountData>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<NewAccountData>> call, Response<ApiResponse<NewAccountData>> response) {
                        long elapsed = System.currentTimeMillis() - startTime;
                        long delay = Math.max(0, MIN_LOADING_DURATION - elapsed);

                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                            animation.cancel();
                            bottomSheet.dismiss();

                            if (response.isSuccessful() && response.body() != null) {
                                loadAccounts();
                                new SwiftBankDialog(DashboardActivity.this)
                                        .setIcon(R.drawable.ic_check_circle)
                                        .setTitle("Cont creat")
                                        .setMessage("Contul în " + currency + " este gata de utilizare!")
                                        .setPrimaryButton("Perfect", null)
                                        .show();
                            } else {
                                ApiErrorResponse error = ErrorParser.parseError(response);
                                String message = "Nu am putut crea contul";
                                if (error != null && error.getError() != null) {
                                    message = error.getError().getMessage();
                                }
                                showErrorDialog("Eroare", message);
                            }
                        }, delay);
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<NewAccountData>> call, Throwable t) {
                        long elapsed = System.currentTimeMillis() - startTime;
                        long delay = Math.max(0, MIN_LOADING_DURATION - elapsed);

                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                            animation.cancel();
                            bottomSheet.dismiss();
                            showErrorDialog("Eroare de conexiune", "Verifică conexiunea la internet și încearcă din nou.");
                        }, delay);
                    }
                });
    }

    private void showErrorDialog(String title, String message) {
        new SwiftBankDialog(this)
                .setIcon(R.drawable.ic_block)
                .setTitle(title)
                .setMessage(message)
                .setPrimaryButton("OK", null)
                .show();
    }

    private void showInfoDialog(String title, String message) {
        new SwiftBankDialog(this)
                .setIcon(R.drawable.ic_check_circle)
                .setTitle(title)
                .setMessage(message)
                .setPrimaryButton("Am înțeles", null)
                .show();
    }

    private void loadProfile() {
        ApiClient.getUserService().getProfile().enqueue(new Callback<ApiResponse<ProfileData>>() {
            @Override
            public void onResponse(Call<ApiResponse<ProfileData>> call, Response<ApiResponse<ProfileData>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    ProfileData profile = response.body().getData();
                    userFirstName = profile.getFirstName();
                    userLastName = profile.getLastName();
                    currentUserId = profile.getUserId();
                    updateGreeting();

                    // Încarcă poza de profil
                    loadProfilePhoto(profile.getProfilePhoto());

                    // Setup realtime subscription pentru acest user
                    setupRealtimeSubscription();
                }
                showProfileContent();
            }

            @Override
            public void onFailure(Call<ApiResponse<ProfileData>> call, Throwable t) {
                // Fallback - greeting fara nume
                updateGreeting();
                showProfileContent();
            }
        });
    }

    private void loadProfilePhoto(String base64Photo) {
        if (base64Photo == null || base64Photo.isEmpty()) {
            // Fără poză - afișează inițialele
            tvAvatarInitials.setVisibility(View.VISIBLE);
            ivAvatarPhoto.setVisibility(View.GONE);
            return;
        }

        try {
            // Elimină prefixul data:image/...;base64, dacă există
            String base64Data = base64Photo;
            if (base64Photo.contains(",")) {
                base64Data = base64Photo.substring(base64Photo.indexOf(",") + 1);
            }

            byte[] decodedBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT);
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);

            if (bitmap != null) {
                ivAvatarPhoto.setImageBitmap(bitmap);
                ivAvatarPhoto.setVisibility(View.VISIBLE);
                tvAvatarInitials.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            // Eroare la decodare - păstrează inițialele
            tvAvatarInitials.setVisibility(View.VISIBLE);
            ivAvatarPhoto.setVisibility(View.GONE);
        }
    }

    private void setupRealtimeSubscription() {
        if (currentUserId == -1 || realtimeListener != null) return;

        RealtimeManager realtime = RealtimeManager.getInstance();

        // Conectează dacă nu e deja conectat
        realtime.connect();

        // Creează listener pentru schimbări în conturi
        // Când balanța se schimbă, înseamnă că a avut loc o tranzacție
        // Refresh atât conturile cât și lista de tranzacții
        realtimeListener = new RealtimeManager.RealtimeListener() {
            @Override
            public void onInsert(String table, JsonObject newRecord) {
                // Cont nou adăugat - reîncarcă totul
                runOnUiThread(() -> {
                    requestDashboardRefresh();
                });
            }

            @Override
            public void onUpdate(String table, JsonObject oldRecord, JsonObject newRecord) {
                // Balanță modificată = tranzacție nouă
                // Reîncarcă conturile și tranzacțiile
                runOnUiThread(() -> {
                    requestDashboardRefresh();
                    // Refresh tranzacții pentru contul curent
                });
            }

            @Override
            public void onDelete(String table, JsonObject oldRecord) {
                // Cont șters - reîncarcă lista
                runOnUiThread(() -> {
                    requestDashboardRefresh();
                });
            }
        };

        // Abonează la schimbări pe conturile acestui user
        realtime.subscribeToUserChanges("accounts", String.valueOf(currentUserId), realtimeListener);
        realtime.subscribeToUserChanges("card_payment_sessions", String.valueOf(currentUserId), realtimeListener);
    }

    private void showProfileContent() {
        if (profileSkeleton != null && profileContent != null) {
            profileSkeleton.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> {
                    profileSkeleton.setVisibility(View.GONE);
                    profileContent.setVisibility(View.VISIBLE);
                    profileContent.setAlpha(0f);
                    profileContent.animate().alpha(1f).setDuration(200).start();
                }).start();
        }
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

        if (userFirstName != null && !userFirstName.isEmpty()) {
            tvUserName.setText(userFirstName);
            tvAvatarInitials.setText(getInitials(userFirstName, userLastName));
        }
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

    private void requestDashboardRefresh() {
        refreshHandler.removeCallbacks(dashboardRefreshRunnable);
        refreshHandler.postDelayed(dashboardRefreshRunnable, DATA_REFRESH_DEBOUNCE_MS);
    }

    private void finishAccountsRequest() {
        accountsRequestInFlight = false;
        accountsCall = null;

        if (queuedAccountsRefresh) {
            queuedAccountsRefresh = false;
            requestDashboardRefresh();
        }
    }

    private void loadAccounts() {
        if (accountsRequestInFlight) {
            queuedAccountsRefresh = true;
            return;
        }

        accountsRequestInFlight = true;
        accountsCall = ApiClient.getAccountService().getAccounts();
        accountsCall.enqueue(new Callback<ApiResponse<AccountsData>>() {
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
                    showBalanceContent();
                } else {
                    Toast.makeText(DashboardActivity.this, "Eroare la incarcarea conturilor", Toast.LENGTH_SHORT).show();
                    showBalanceContent();
                }
                finishAccountsRequest();
            }

            @Override
            public void onFailure(Call<ApiResponse<AccountsData>> call, Throwable t) {
                if (call.isCanceled()) {
                    finishAccountsRequest();
                    return;
                }
                Toast.makeText(DashboardActivity.this, "Eroare de conexiune", Toast.LENGTH_SHORT).show();
                showBalanceContent();
                finishAccountsRequest();
            }
        });
    }

    private void showBalanceContent() {
        if (balanceSkeleton != null && balanceSection != null) {
            // Dacă conținutul e deja vizibil, nu mai rula animația de fade
            // (pentru a nu întrerupe animația odometrului când revenim de la altă activitate)
            if (balanceSection.getVisibility() == View.VISIBLE && balanceSection.getAlpha() > 0.9f) {
                return;
            }

            balanceSkeleton.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> {
                    balanceSkeleton.setVisibility(View.GONE);
                    balanceSection.setVisibility(View.VISIBLE);
                    balanceSection.setAlpha(0f);
                    balanceSection.animate().alpha(1f).setDuration(200).start();
                }).start();
        }
    }


    private void loadTransactionsForAccount(Account account) {
        if (transactionsCall != null) {
            transactionsCall.cancel();
        }

        // Reset pagination
        transactionsOffset = 0;
        transactionsHasMore = true;
        allApiTransactions.clear();

        final int requestedAccountId = account.id;
        final boolean isAccountTransition = requestedAccountId != displayedTransactionsAccountId;
        if (isAccountTransition || transactionItems.isEmpty()) {
            showTransactionsLoadingState();
        }

        transactionsCall = ApiClient.getTransactionService().getTransactions(account.id, TRANSACTIONS_PAGE_SIZE, 0);
        transactionsCall
                .enqueue(new Callback<ApiResponse<TransactionsData>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<TransactionsData>> call, Response<ApiResponse<TransactionsData>> response) {
                        if (call.isCanceled() || !isCurrentAccount(requestedAccountId)) {
                            clearTransactionsCall(call);
                            return;
                        }

                        if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                            TransactionsData data = response.body().getData();
                            List<com.example.swiftbank.api.dto.response.data.success.transaction.Transaction> apiTransactions =
                                    data.getTransactions();

                            // Update pagination state
                            if (data.getPagination() != null) {
                                transactionsHasMore = data.getPagination().hasMore();
                            } else {
                                transactionsHasMore = apiTransactions.size() >= TRANSACTIONS_PAGE_SIZE;
                            }
                            transactionsOffset = apiTransactions.size();
                            allApiTransactions.addAll(apiTransactions);

                            transactionItems.clear();
                            String lastDateGroup = "";

                            for (com.example.swiftbank.api.dto.response.data.success.transaction.Transaction t : apiTransactions) {
                                String dateGroup = getDateGroup(t.getCreatedAt());

                                if (!dateGroup.equals(lastDateGroup)) {
                                    transactionItems.add(dateGroup);
                                    lastDateGroup = dateGroup;
                                }

                                Transaction transaction = createTransactionItem(t, account.currency);
                                transactionItems.add(transaction);
                            }

                            displayedTransactionsAccountId = requestedAccountId;
                            if (transactionsAdapter != null) {
                                boolean isLoadingVisible = transactionsSkeleton != null
                                        && transactionsSkeleton.getVisibility() == View.VISIBLE;
                                if (isAccountTransition || isLoadingVisible) {
                                    transactionsAdapter.notifyDataSetChanged();
                                } else {
                                    animateTransactionsRefresh();
                                }
                            }
                            updateTransactionsVisibility();
                        } else {
                            showTransactionsErrorState(requestedAccountId, isAccountTransition);
                            updateTransactionsVisibility();
                        }
                        clearTransactionsCall(call);
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<TransactionsData>> call, Throwable t) {
                        if (call.isCanceled()) {
                            clearTransactionsCall(call);
                            return;
                        }
                        Toast.makeText(DashboardActivity.this, "Eroare la incarcarea tranzactiilor", Toast.LENGTH_SHORT).show();
                        showTransactionsErrorState(requestedAccountId, isAccountTransition);
                        updateTransactionsVisibility();
                        clearTransactionsCall(call);
                    }
                });
    }

    private void loadMoreTransactions() {
        if (isLoadingMoreTransactions || !transactionsHasMore || accounts.isEmpty()) return;

        isLoadingMoreTransactions = true;
        final Account account = accounts.get(selectedAccountIndex);

        // Add loading skeleton
        transactionItems.add(new LoadingSkeleton());
        transactionsAdapter.notifyItemInserted(transactionItems.size() - 1);

        ApiClient.getTransactionService().getTransactions(account.id, TRANSACTIONS_PAGE_SIZE, transactionsOffset)
                .enqueue(new Callback<ApiResponse<TransactionsData>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<TransactionsData>> call, Response<ApiResponse<TransactionsData>> response) {
                        // Remove loading skeleton
                        int skeletonIndex = -1;
                        for (int i = transactionItems.size() - 1; i >= 0; i--) {
                            if (transactionItems.get(i) instanceof LoadingSkeleton) {
                                skeletonIndex = i;
                                break;
                            }
                        }
                        if (skeletonIndex >= 0) {
                            transactionItems.remove(skeletonIndex);
                            transactionsAdapter.notifyItemRemoved(skeletonIndex);
                        }

                        if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                            TransactionsData data = response.body().getData();
                            List<com.example.swiftbank.api.dto.response.data.success.transaction.Transaction> newTransactions =
                                    data.getTransactions();

                            if (data.getPagination() != null) {
                                transactionsHasMore = data.getPagination().hasMore();
                            } else {
                                transactionsHasMore = newTransactions.size() >= TRANSACTIONS_PAGE_SIZE;
                            }
                            transactionsOffset += newTransactions.size();
                            allApiTransactions.addAll(newTransactions);

                            // Find the last date group
                            String lastDateGroup = "";
                            for (int i = transactionItems.size() - 1; i >= 0; i--) {
                                Object item = transactionItems.get(i);
                                if (item instanceof String) {
                                    lastDateGroup = (String) item;
                                    break;
                                }
                            }

                            int insertPosition = transactionItems.size();
                            List<Object> newItems = new ArrayList<>();

                            for (com.example.swiftbank.api.dto.response.data.success.transaction.Transaction t : newTransactions) {
                                String dateGroup = getDateGroup(t.getCreatedAt());

                                if (!dateGroup.equals(lastDateGroup)) {
                                    newItems.add(dateGroup);
                                    lastDateGroup = dateGroup;
                                }

                                Transaction transaction = createTransactionItem(t, account.currency);
                                newItems.add(transaction);
                            }

                            transactionItems.addAll(newItems);
                            transactionsAdapter.notifyItemRangeInserted(insertPosition, newItems.size());
                        }
                        isLoadingMoreTransactions = false;
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<TransactionsData>> call, Throwable t) {
                        // Remove loading skeleton
                        int skeletonIndex = -1;
                        for (int i = transactionItems.size() - 1; i >= 0; i--) {
                            if (transactionItems.get(i) instanceof LoadingSkeleton) {
                                skeletonIndex = i;
                                break;
                            }
                        }
                        if (skeletonIndex >= 0) {
                            transactionItems.remove(skeletonIndex);
                            transactionsAdapter.notifyItemRemoved(skeletonIndex);
                        }
                        isLoadingMoreTransactions = false;
                    }
                });
    }

    private boolean isCurrentAccount(int accountId) {
        return !accounts.isEmpty()
                && selectedAccountIndex < accounts.size()
                && accounts.get(selectedAccountIndex).id == accountId;
    }

    private void clearTransactionsCall(Call<ApiResponse<TransactionsData>> call) {
        if (transactionsCall == call) {
            transactionsCall = null;
        }
    }

    private String getDateGroup(String createdAt) {
        if (createdAt == null) return "ALTELE";

        try {
            // Parsează ca UTC
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            java.util.Date date = sdf.parse(createdAt);
            if (date == null) return "ALTELE";

            // Convertește la timezone-ul local pentru comparație
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
                // Afișează în timezone-ul local
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
            // Parsează ca UTC
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            java.util.Date date = sdf.parse(createdAt);
            // Afișează în timezone-ul local
            java.text.SimpleDateFormat outputFormat = new java.text.SimpleDateFormat("HH:mm", Locale.getDefault());
            return outputFormat.format(date);
        } catch (Exception e) {
            return "";
        }
    }

    private int getIconForTransaction(com.example.swiftbank.api.dto.response.data.success.transaction.Transaction t) {
        String type = t.getTransactionType();
        if (type == null) return R.drawable.ic_shopping;

        // Verifică dacă e schimb valutar (transfer cu conversie)
        if (t instanceof TransferTransaction) {
            TransferTransaction transfer =
                (TransferTransaction) t;
            if (transfer.hasCurrencyConversion()) {
                return R.drawable.ic_exchange;
            }
        }

        switch (type) {
            case "SELF_IN":
            case "SELF_OUT":
                return R.drawable.ic_exchange;
            case "TRANSFER":
                return t.getAmount() > 0 ? R.drawable.ic_transfer_in : R.drawable.ic_transfer_out;
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

    private int getCategoryIconForTransaction(com.example.swiftbank.api.dto.response.data.success.transaction.Transaction transaction,
                                              int fallback) {
        String iconName = transaction.getCategoryIcon();
        if (iconName == null || iconName.trim().isEmpty()) {
            iconName = transaction.getCategoryName();
        }
        return getCategoryIconResource(iconName, fallback);
    }

    private int getCategoryIconResource(String iconName, int fallback) {
        if (iconName == null || iconName.trim().isEmpty()) {
            return fallback;
        }

        switch (iconName.trim().toLowerCase(java.util.Locale.ROOT)) {
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

    private int getCategoryColorForTransaction(com.example.swiftbank.api.dto.response.data.success.transaction.Transaction transaction,
                                               int fallback) {
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

    private String getCategoryDisplayNameForTransaction(com.example.swiftbank.api.dto.response.data.success.transaction.Transaction transaction) {
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

    private Transaction createTransactionItem(com.example.swiftbank.api.dto.response.data.success.transaction.Transaction t, String accountCurrency) {
        String currency = t.getCurrency() != null ? t.getCurrency().trim() : accountCurrency;
        String time = formatTime(t.getCreatedAt());
        int iconRes = getIconForTransaction(t);

        // Verifică dacă e transfer
        if (t instanceof TransferTransaction) {
            TransferTransaction transfer =
                (TransferTransaction) t;

            String beneficiary = transfer.getBeneficiaryName();

            // Verifică dacă e transfer între conturile proprii (SELF_IN sau SELF_OUT din VIEW)
            String transactionType = t.getTransactionType();
            boolean isOwnAccountTransfer = "SELF_IN".equals(transactionType) || "SELF_OUT".equals(transactionType);

            // Prioritate 1: Transfer către propriul cont CU conversie valutară
            if (isOwnAccountTransfer && transfer.hasCurrencyConversion() && transfer.getOriginalCurrency() != null) {
                String fromCurr, toCurr;
                double displayAmount = t.getAmount();

                if (t.getAmount() > 0) {
                    // Am primit bani - originalCurrency → currency
                    fromCurr = transfer.getOriginalCurrency().trim();
                    toCurr = currency;
                } else {
                    // Am trimis bani - currency → originalCurrency
                    fromCurr = currency;
                    toCurr = transfer.getOriginalCurrency().trim();
                }

                String title = fromCurr + " → " + toCurr;

                return Transaction.withExchange(
                    t.getId(),
                    t.getTransactionType(),
                    title,
                    "Transfer între conturi",
                    displayAmount,
                    currency,
                    time,
                    R.drawable.ic_exchange,
                    transfer.getOriginalAmount() != null ? transfer.getOriginalAmount() : Math.abs(displayAmount),
                    transfer.getOriginalCurrency() != null ? transfer.getOriginalCurrency().trim() : currency,
                    fromCurr,
                    toCurr
                );
            }

            // Transfer către propriul cont FĂRĂ conversie (aceeași valută) - afișează simplu
            if (isOwnAccountTransfer) {
                return new Transaction(
                    t.getId(),
                    t.getTransactionType(),
                    "Transfer între conturi",
                    t.getAmount() > 0 ? "Primit din alt cont" : "Trimis către alt cont",
                    t.getAmount(),
                    currency,
                    time,
                    R.drawable.ic_exchange
                );
            }

            // Prioritate 2: Transfer către altcineva - afișează inițiale/poză
            if (beneficiary != null && !beneficiary.isEmpty() && !beneficiary.equals("Schimb valutar") && !isOwnAccountTransfer) {
                String initials = getInitials(beneficiary);
                String senderPhoto = transfer.getSenderPhoto();
                return Transaction.withInitials(
                    t.getId(),
                    t.getTransactionType(),
                    t.getTitle(),
                    t.getSubtitle(),
                    t.getAmount(),
                    currency,
                    time,
                    iconRes,
                    initials,
                    senderPhoto
                );
            }

            // Prioritate 2: Schimb valutar (fără beneficiar sau "Schimb valutar")
            if (transfer.hasCurrencyConversion()) {
                // Exchange transaction - suma principală în moneda contului, secundară în cealaltă
                Double origAmount = transfer.getOriginalAmount();
                String origCurrency = transfer.getOriginalCurrency();

                if (origAmount != null && origCurrency != null) {
                    // Verifică care monedă e cea a contului curent
                    boolean transactionInAccountCurrency = currency.equalsIgnoreCase(accountCurrency.trim());

                    double primaryAmount;
                    String primaryCurrency;
                    double secondaryAmount;
                    String secondaryCurrency;
                    String fromCurrency;
                    String toCurrency;

                    if (transactionInAccountCurrency) {
                        // Tranzacția e deja în moneda contului - corect
                        primaryAmount = t.getAmount();
                        primaryCurrency = currency;
                        secondaryAmount = origAmount;
                        secondaryCurrency = origCurrency.trim();
                        fromCurrency = origCurrency.trim();
                        toCurrency = currency;
                    } else {
                        // Tranzacția e în altă monedă - inversăm
                        primaryAmount = -origAmount; // negativ pentru că e suma plătită
                        primaryCurrency = origCurrency.trim();
                        secondaryAmount = Math.abs(t.getAmount());
                        secondaryCurrency = currency;
                        fromCurrency = origCurrency.trim();
                        toCurrency = currency;
                    }

                    return Transaction.withExchange(
                        t.getId(),
                        t.getTransactionType(),
                        t.getTitle(),
                        t.getSubtitle(),
                        primaryAmount,
                        primaryCurrency,
                        time,
                        iconRes,
                        secondaryAmount,
                        secondaryCurrency,
                        fromCurrency,
                        toCurrency
                    );
                }
            }
        }

        // Tranzacție normală (card, bill, etc.)
        if (t instanceof BillTransaction) {
            BillTransaction bill = (BillTransaction) t;
            Transaction billTransaction = new Transaction(
                t.getId(),
                t.getTransactionType(),
                bill.getBillerName(),
                getBillCategoryDisplayName(bill.getBillerCategory()),
                t.getAmount(),
                currency,
                time,
                iconRes
            );
            billTransaction.iconColor = getBillCategoryColor(bill.getBillerCategory());
            billTransaction.merchantLogoUrl = t.getMerchantLogoUrl();
            return billTransaction;
        }

        if (t instanceof CardTransaction) {
            CardTransaction card = (CardTransaction) t;
            if (card.isPendingApproval()) {
                int pendingIconRes = getCategoryIconForTransaction(t, R.drawable.ic_card);
                Transaction pendingCardTransaction = new Transaction(
                    t.getId(),
                    t.getTransactionType(),
                    card.getMerchantName() != null ? card.getMerchantName() : t.getTitle(),
                    "Asteapta confirmarea",
                    t.getAmount(),
                    currency,
                    time,
                    pendingIconRes
                );
                pendingCardTransaction.iconColor = getCategoryColorForTransaction(t, 0xFFFF9500);
                pendingCardTransaction.sessionId = card.getSessionId() != null ? card.getSessionId() : t.getId();
                pendingCardTransaction.merchantName = card.getMerchantName() != null ? card.getMerchantName() : t.getTitle();
                pendingCardTransaction.location = card.getLocation();
                pendingCardTransaction.maskedCard = card.getMaskedCard() != null ? card.getMaskedCard() : card.getCardNumberMasked();
                pendingCardTransaction.expiresAt = card.getExpiresAt();
                pendingCardTransaction.merchantLogoUrl = t.getMerchantLogoUrl();
                return pendingCardTransaction;
            }
        }

        String subtitle = t.getSubtitle();
        if ("CARD".equals(t.getTransactionType()) && "PENDING".equals(t.getStatus())) {
            subtitle = "Suma blocata";
        }
        if ("CARD".equals(t.getTransactionType()) && !"PENDING".equals(t.getStatus())) {
            String categoryDisplayName = getCategoryDisplayNameForTransaction(t);
            if (categoryDisplayName != null) {
                subtitle = categoryDisplayName;
            }
        }
        if ("CARD".equals(t.getTransactionType()) && (subtitle == null || subtitle.isEmpty())) {
            subtitle = "Plată cu cardul";
        }

        Transaction transaction = new Transaction(
            t.getId(),
            t.getTransactionType(),
            t.getTitle(),
            subtitle,
            t.getAmount(),
            currency,
            time,
            iconRes
        );
        if ("CARD".equals(t.getTransactionType()) || "BILL".equals(t.getTransactionType())) {
            transaction.iconColor = getCategoryColorForTransaction(t, 0);
            transaction.merchantLogoUrl = t.getMerchantLogoUrl();
        }
        return transaction;
    }

    private void openCardPaymentApproval(Transaction transaction) {
        Intent intent = new Intent(this, CardPaymentApprovalActivity.class);
        intent.putExtra(CardPaymentApprovalActivity.EXTRA_SESSION_ID,
                transaction.sessionId != null ? transaction.sessionId : transaction.id);
        intent.putExtra(CardPaymentApprovalActivity.EXTRA_MERCHANT_NAME,
                transaction.merchantName != null ? transaction.merchantName : transaction.title);
        intent.putExtra(CardPaymentApprovalActivity.EXTRA_MERCHANT_LOCATION, transaction.location);
        intent.putExtra(CardPaymentApprovalActivity.EXTRA_AMOUNT, Math.abs(transaction.amount));
        intent.putExtra(CardPaymentApprovalActivity.EXTRA_CURRENCY,
                transaction.currency != null ? transaction.currency : getCurrentAccountCurrency());
        intent.putExtra(CardPaymentApprovalActivity.EXTRA_MASKED_CARD, transaction.maskedCard);
        intent.putExtra(CardPaymentApprovalActivity.EXTRA_EXPIRES_AT, transaction.expiresAt);
        startActivityForResult(intent, REQUEST_CARD_PAYMENT_APPROVAL);
    }

    private String getCurrentAccountCurrency() {
        if (!accounts.isEmpty() && selectedAccountIndex < accounts.size()) {
            return accounts.get(selectedAccountIndex).currency;
        }
        return "RON";
    }

    private String getInitials(String name) {
        if (name == null || name.isEmpty()) return "?";

        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2) {
            return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
        } else if (parts.length == 1 && parts[0].length() >= 2) {
            return parts[0].substring(0, 2).toUpperCase();
        } else if (parts.length == 1) {
            return parts[0].substring(0, 1).toUpperCase();
        }
        return "?";
    }

    private void updateTransactionsVisibility() {
        // Ascunde skeleton-ul
        if (transactionsSkeleton != null && transactionsSkeleton.getVisibility() == View.VISIBLE) {
            transactionsSkeleton.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> {
                    transactionsSkeleton.setVisibility(View.GONE);
                    showTransactionsList();
                }).start();
        } else {
            showTransactionsList();
        }
    }

    private void showTransactionsLoadingState() {
        if (transactionsSkeleton == null) return;

        transactionsSkeleton.animate().cancel();
        rvTransactions.animate().cancel();

        transactionsSkeleton.setAlpha(1f);
        transactionsSkeleton.setVisibility(View.VISIBLE);
        rvTransactions.setAlpha(1f);
        rvTransactions.setVisibility(View.GONE);
        emptyTransactionsState.setVisibility(View.GONE);
    }

    private void showTransactionsErrorState(int accountId, boolean clearCurrentItems) {
        if (!isCurrentAccount(accountId)) return;

        displayedTransactionsAccountId = accountId;
        if (clearCurrentItems) {
            transactionItems.clear();
        }
        if (clearCurrentItems && transactionsAdapter != null) {
            transactionsAdapter.notifyDataSetChanged();
        }
    }

    private void showTransactionsList() {
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
        tvAccountType.setText(getAccountTypeName(account.type) + " · " + account.currency);

        // Update balance with animation
        animateBalanceChange(account.balance);

        // Update currency label
        tvCurrencyLabel.setText(getCurrencySymbol(account.currency));

        // Update gradient based on currency
        updateGradientForCurrency(account.currency);
    }

    private void animateBalanceChange(double newBalance) {
        // Dacă valoarea e aceeași, nu anima
        if (Math.abs(newBalance - displayedBalance) < 0.01) {
            return;
        }

        // Dacă diferența e enormă (> 1 milion), setează direct pentru a evita animații prea lungi
        if (Math.abs(newBalance - displayedBalance) > 1000000) {
            displayedBalance = newBalance;
            tvTotalBalance.setText(formatBalance(newBalance));
            return;
        }

        // Animație de la valoarea veche la cea nouă
        ValueAnimator animator = ValueAnimator.ofFloat((float) displayedBalance, (float) newBalance);
        animator.setDuration(600); // 600ms pentru o animație smooth
        animator.setInterpolator(new AccelerateDecelerateInterpolator());

        animator.addUpdateListener(animation -> {
            float animatedValue = (float) animation.getAnimatedValue();
            tvTotalBalance.setText(formatBalance(animatedValue));
        });

        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                displayedBalance = newBalance;
                // Setează valoarea finală exactă pentru a evita erori de rotunjire
                tvTotalBalance.setText(formatBalance(newBalance));
            }
        });

        animator.start();
    }

    private void animateTransactionsRefresh() {
        // Fade out rapid, actualizează, fade in
        rvTransactions.animate()
            .alpha(0.3f)
            .setDuration(150)
            .withEndAction(() -> {
                transactionsAdapter.notifyDataSetChanged();
                rvTransactions.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .start();
            })
            .start();
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

    private String getAccountTypeName(String type) {
        if (type == null) return "Cont";
        switch (type.toUpperCase()) {
            case "CURRENT": return "Curent";
            case "SAVINGS": return "Economii";
            default: return type;
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
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        rvTransactions.setLayoutManager(layoutManager);
        rvTransactions.setAdapter(transactionsAdapter);

        // Infinite scroll listener
        rvTransactions.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                if (dy > 0 && transactionsHasMore && !isLoadingMoreTransactions) {
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CARD_PAYMENT_APPROVAL && resultCode == RESULT_OK) {
            requestDashboardRefresh();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Reset pentru ca animația soldului să ruleze din nou la revenire
        displayedBalance = 0;
        if (particlesView != null) {
            particlesView.stopAnimation();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        refreshHandler.removeCallbacks(dashboardRefreshRunnable);

        if (accountsCall != null) {
            accountsCall.cancel();
            accountsCall = null;
        }

        if (transactionsCall != null) {
            transactionsCall.cancel();
            transactionsCall = null;
        }

        if (refreshReceiverRegistered) {
            unregisterReceiver(refreshReceiver);
            refreshReceiverRegistered = false;
        }

        // Cleanup realtime subscription
        if (realtimeListener != null && currentUserId != -1) {
            RealtimeManager realtime = RealtimeManager.getInstance();
            realtime.unsubscribeFromUserChanges("accounts", String.valueOf(currentUserId), realtimeListener);
            realtime.unsubscribeFromUserChanges("card_payment_sessions", String.valueOf(currentUserId), realtimeListener);
            realtimeListener = null;
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

    static class LoadingSkeleton {}

    static class Transaction {
        int id;
        String transactionType;
        String title;
        String subtitle;
        double amount;
        String currency;
        String time;
        int iconRes;
        int iconColor;
        Integer sessionId;
        String expiresAt;
        String maskedCard;
        String location;
        String merchantName;
        String merchantLogoUrl;

        // Pentru inițiale/poză (transferuri către persoane)
        boolean showInitials;
        String initials;
        String senderPhoto;

        // Pentru exchange (sumă secundară)
        boolean isExchange;
        double secondaryAmount;
        String secondaryCurrency;

        // Pentru exchange cu steaguri
        String fromCurrency;
        String toCurrency;

        Transaction(int id, String transactionType, String title, String subtitle, double amount, String currency, String time, int iconRes) {
            this.id = id;
            this.transactionType = transactionType;
            this.title = title;
            this.subtitle = subtitle;
            this.amount = amount;
            this.currency = currency;
            this.time = time;
            this.iconRes = iconRes;
            this.showInitials = false;
            this.isExchange = false;
        }

        // Constructor extins pentru transferuri cu inițiale și poză
        static Transaction withInitials(int id, String transactionType, String title, String subtitle, double amount, String currency,
                                        String time, int iconRes, String initials, String senderPhoto) {
            Transaction t = new Transaction(id, transactionType, title, subtitle, amount, currency, time, iconRes);
            t.showInitials = true;
            t.initials = initials;
            t.senderPhoto = senderPhoto;
            return t;
        }

        // Constructor extins pentru exchange cu sumă secundară
        static Transaction withExchange(int id, String transactionType, String title, String subtitle, double amount, String currency,
                                        String time, int iconRes, double secondaryAmount,
                                        String secondaryCurrency, String fromCurrency, String toCurrency) {
            Transaction t = new Transaction(id, transactionType, title, subtitle, amount, currency, time, iconRes);
            t.isExchange = true;
            t.secondaryAmount = secondaryAmount;
            t.secondaryCurrency = secondaryCurrency;
            t.fromCurrency = fromCurrency;
            t.toCurrency = toCurrency;
            return t;
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
            TextView tvAccountName, tvBalance, tvCurrency;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                ivFlag = itemView.findViewById(R.id.ivFlag);
                ivSelected = itemView.findViewById(R.id.ivSelected);
                tvAccountName = itemView.findViewById(R.id.tvAccountName);
                tvBalance = itemView.findViewById(R.id.tvBalance);
                tvCurrency = itemView.findViewById(R.id.tvCurrency);

                itemView.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onAccountClick(getAdapterPosition());
                    }
                });
            }

            void bind(Account account, boolean isSelected) {
                tvAccountName.setText(getAccountTypeName(account.type) + " · " + account.currency);
                tvBalance.setText(formatBalance(account.balance));
                tvCurrency.setText(getCurrencySymbol(account.currency));

                itemView.setSelected(isSelected);
                ivSelected.setVisibility(View.GONE);

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
                    case "GBP":
                        ivFlag.setImageResource(R.drawable.flag_gbp);
                        break;
                }
            }
        }
    }

    // Transactions Adapter
    class TransactionsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_HEADER = 0;
        private static final int TYPE_TRANSACTION = 1;
        private static final int TYPE_LOADING = 2;

        private List<Object> items;

        TransactionsAdapter(List<Object> items) {
            this.items = items;
        }

        @Override
        public int getItemViewType(int position) {
            Object item = items.get(position);
            if (item instanceof String) return TYPE_HEADER;
            if (item instanceof LoadingSkeleton) return TYPE_LOADING;
            return TYPE_TRANSACTION;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_HEADER) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_transaction_header, parent, false);
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
                ((HeaderViewHolder) holder).bind((String) items.get(position));
            } else if (holder instanceof TransactionViewHolder) {
                ((TransactionViewHolder) holder).bind((Transaction) items.get(position));
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
                    if (pos != RecyclerView.NO_POSITION && items.get(pos) instanceof Transaction) {
                        Transaction t = (Transaction) items.get(pos);
                        if ("CARD_PENDING_APPROVAL".equals(t.transactionType)) {
                            openCardPaymentApproval(t);
                            return;
                        }

                        Intent intent = new Intent(DashboardActivity.this, com.example.swiftbank.activities.transactions.TransactionDetailsActivity.class);
                        intent.putExtra(com.example.swiftbank.activities.transactions.TransactionDetailsActivity.EXTRA_TRANSACTION_ID, t.id);
                        intent.putExtra(com.example.swiftbank.activities.transactions.TransactionDetailsActivity.EXTRA_TRANSACTION_TYPE, t.transactionType);
                        if (!accounts.isEmpty()) {
                            intent.putExtra(com.example.swiftbank.activities.transactions.TransactionDetailsActivity.EXTRA_ACCOUNT_CURRENCY, accounts.get(selectedAccountIndex).currency);
                        }
                        startActivity(intent);
                    }
                });
            }

            void bind(Transaction transaction) {
                tvMerchantName.setText(transaction.isExchange
                        ? ExchangeTitleFormatter.format(transaction.title, getResources().getDisplayMetrics().density)
                        : transaction.title);
                tvCategory.setText(transaction.subtitle);
                tvTime.setText(transaction.time);

                // Afișează suma principală
                String amountText;
                if (transaction.amount >= 0) {
                    amountText = "+" + formatBalance(transaction.amount) + " " + getCurrencySymbol(transaction.currency);
                    tvAmount.setTextColor(ContextCompat.getColor(DashboardActivity.this, R.color.green_accent));
                } else {
                    amountText = formatBalance(transaction.amount) + " " + getCurrencySymbol(transaction.currency);
                    tvAmount.setTextColor(ContextCompat.getColor(DashboardActivity.this, R.color.white));
                }
                tvAmount.setText(amountText);

                // Afișează suma secundară pentru exchange (semnul opus primarului)
                if (transaction.isExchange && tvSecondaryAmount != null) {
                    String secondaryText;
                    // Dacă primarul e negativ (ai plătit), secundarul e pozitiv (ai primit)
                    if (transaction.amount < 0) {
                        secondaryText = "+" + formatBalance(transaction.secondaryAmount) + " " + getCurrencySymbol(transaction.secondaryCurrency);
                    } else {
                        secondaryText = "-" + formatBalance(transaction.secondaryAmount) + " " + getCurrencySymbol(transaction.secondaryCurrency);
                    }
                    tvSecondaryAmount.setText(secondaryText);
                    tvSecondaryAmount.setVisibility(View.VISIBLE);
                } else if (tvSecondaryAmount != null) {
                    tvSecondaryAmount.setVisibility(View.GONE);
                }

                // Alege tipul de icon: exchange cu steaguri, inițiale, sau iconița normală
                if (transaction.isExchange && exchangeIconContainer != null) {
                    // Exchange - afișează steagurile suprapuse
                    cardIcon.setVisibility(View.GONE);
                    cardInitials.setVisibility(View.GONE);
                    exchangeIconContainer.setVisibility(View.VISIBLE);

                    // Setează steagurile pentru monedele implicate
                    ivFlagFrom.setImageResource(getFlagForCurrency(transaction.fromCurrency));
                    ivFlagTo.setImageResource(getFlagForCurrency(transaction.toCurrency));
                } else if (transaction.showInitials && cardInitials != null && cardIcon != null) {
                    // Transfer către persoană - afișează cercul cu poză sau inițiale
                    cardIcon.setVisibility(View.GONE);
                    cardInitials.setVisibility(View.VISIBLE);
                    if (exchangeIconContainer != null) exchangeIconContainer.setVisibility(View.GONE);

                    // Încearcă să afișeze poza, altfel inițialele
                    if (transaction.senderPhoto != null && !transaction.senderPhoto.isEmpty() && ivSenderPhoto != null) {
                        try {
                            String base64Data = transaction.senderPhoto;
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
                                tvInitials.setText(transaction.initials);
                            }
                        } catch (Exception e) {
                            ivSenderPhoto.setVisibility(View.GONE);
                            tvInitials.setVisibility(View.VISIBLE);
                            tvInitials.setText(transaction.initials);
                        }
                    } else {
                        if (ivSenderPhoto != null) ivSenderPhoto.setVisibility(View.GONE);
                        tvInitials.setVisibility(View.VISIBLE);
                        tvInitials.setText(transaction.initials);
                    }
                } else if (cardIcon != null) {
                    // Iconița normală
                    cardIcon.setVisibility(View.VISIBLE);
                    if (cardInitials != null) cardInitials.setVisibility(View.GONE);
                    if (exchangeIconContainer != null) exchangeIconContainer.setVisibility(View.GONE);
                    try {
                        if (!applyMerchantLogoUrl(transaction)) {
                            applyIconStyle(transaction);
                            ivCategoryIcon.setImageResource(transaction.iconRes);
                        }
                    } catch (Exception e) {
                        ivCategoryIcon.setImageResource(R.drawable.ic_shopping);
                    }
                }
            }

            private boolean applyMerchantLogoUrl(Transaction transaction) {
                if (transaction.merchantLogoUrl == null || transaction.merchantLogoUrl.trim().isEmpty()) {
                    return false;
                }

                if (cardIcon instanceof androidx.cardview.widget.CardView) {
                    ((androidx.cardview.widget.CardView) cardIcon).setCardBackgroundColor(
                            ContextCompat.getColor(DashboardActivity.this, R.color.white));
                }

                androidx.core.widget.ImageViewCompat.setImageTintList(ivCategoryIcon, null);
                ivCategoryIcon.clearColorFilter();
                setIconImageSize(32);
                ivCategoryIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                return RemoteImageLoader.load(transaction.merchantLogoUrl, ivCategoryIcon, () -> {
                    applyIconStyle(transaction);
                    ivCategoryIcon.setImageResource(transaction.iconRes);
                });
            }

            private void applyIconStyle(Transaction transaction) {
                ivCategoryIcon.setTag(null);
                if (cardIcon instanceof androidx.cardview.widget.CardView) {
                    int backgroundColor = transaction.iconColor != 0
                            ? transaction.iconColor
                            : ContextCompat.getColor(DashboardActivity.this, R.color.white_10);
                    ((androidx.cardview.widget.CardView) cardIcon).setCardBackgroundColor(backgroundColor);
                }

                int iconTint = transaction.iconColor != 0
                        ? ContextCompat.getColor(DashboardActivity.this, R.color.white)
                        : ContextCompat.getColor(DashboardActivity.this, R.color.white_60);
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

            private int getFlagForCurrency(String currency) {
                if (currency == null) return R.drawable.flag_ro;
                switch (currency.trim().toUpperCase()) {
                    case "EUR":
                        return R.drawable.flag_eur;
                    case "USD":
                        return R.drawable.flag_usd;
                    case "GBP":
                        return R.drawable.flag_gbp;
                    case "RON":
                    default:
                        return R.drawable.flag_ro;
                }
            }
        }
    }
}
