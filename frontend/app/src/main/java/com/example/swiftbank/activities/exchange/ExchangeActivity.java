package com.example.swiftbank.activities.exchange;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
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
import com.example.swiftbank.api.dto.request.ExchangeRequest;
import com.example.swiftbank.api.dto.response.ApiErrorResponse;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.success.AccountData;
import com.example.swiftbank.api.dto.response.data.success.AccountsData;
import com.example.swiftbank.api.dto.response.data.success.ExchangeRateData;
import com.example.swiftbank.api.dto.response.data.success.ExchangeResultData;
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

public class ExchangeActivity extends AppCompatActivity {

    // Views
    private ImageView btnBack;
    private LinearLayout selectorFrom, selectorTo;
    private LinearLayout cardFrom, cardTo;
    private ImageView ivFlagFrom, ivFlagTo;
    private TextView tvCurrencyFrom, tvCurrencyTo;
    private TextView tvBalanceFrom, tvBalanceTo;
    private TextView tvErrorFrom, tvErrorTo;
    private EditText etAmountFrom, etAmountTo;
    private LinearLayout rateContainer;
    private TextView tvExchangeRate;
    private ImageView btnDirection, btnSwapAccounts;
    private Button btnExchange;

    // Skeleton loading
    private LinearLayout skeletonContainer;
    private LinearLayout contentContainer;

    // State
    private List<AccountData> accounts = new ArrayList<>();
    private AccountData fromAccount;
    private AccountData toAccount;
    private double currentRate = 0;
    private boolean isLoading = false;
    private boolean isDirectionDown = true; // true = de sus în jos
    private boolean isEditingTop = true; // care câmp e editat acum
    private boolean isUpdatingProgrammatically = false; // previne loop infinit
    private String idempotencyKey;

    private DecimalFormat balanceFormat;
    private DecimalFormat amountFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exchange);

        setupFormatters();
        initViews();
        setupListeners();
        loadAccounts();
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
        selectorFrom = findViewById(R.id.selectorFrom);
        selectorTo = findViewById(R.id.selectorTo);
        cardFrom = findViewById(R.id.cardFrom);
        cardTo = findViewById(R.id.cardTo);
        ivFlagFrom = findViewById(R.id.ivFlagFrom);
        ivFlagTo = findViewById(R.id.ivFlagTo);
        tvCurrencyFrom = findViewById(R.id.tvCurrencyFrom);
        tvCurrencyTo = findViewById(R.id.tvCurrencyTo);
        tvBalanceFrom = findViewById(R.id.tvBalanceFrom);
        tvBalanceTo = findViewById(R.id.tvBalanceTo);
        tvErrorFrom = findViewById(R.id.tvErrorFrom);
        tvErrorTo = findViewById(R.id.tvErrorTo);
        etAmountFrom = findViewById(R.id.etAmountFrom);
        etAmountTo = findViewById(R.id.etAmountTo);
        rateContainer = findViewById(R.id.rateContainer);
        tvExchangeRate = findViewById(R.id.tvExchangeRate);
        btnDirection = findViewById(R.id.btnDirection);
        btnSwapAccounts = findViewById(R.id.btnSwapAccounts);
        btnExchange = findViewById(R.id.btnExchange);

        // Skeleton loading
        skeletonContainer = findViewById(R.id.skeletonContainer);
        contentContainer = findViewById(R.id.contentContainer);
    }

    private void showContent() {
        // SkeletonView-urile își opresc singure animația când sunt ascunse
        // Fade out skeleton, fade in content
        skeletonContainer.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction(() -> {
                skeletonContainer.setVisibility(View.GONE);
                contentContainer.setVisibility(View.VISIBLE);
                contentContainer.setAlpha(0f);
                contentContainer.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .start();
            })
            .start();
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        selectorFrom.setOnClickListener(v -> showAccountSelector(true));
        selectorTo.setOnClickListener(v -> showAccountSelector(false));

        btnDirection.setOnClickListener(v -> toggleDirection());
        btnSwapAccounts.setOnClickListener(v -> swapAccounts());

        btnExchange.setOnClickListener(v -> initiateExchange());

        // Focus listeners pentru a ști care câmp e activ
        etAmountFrom.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                isEditingTop = true;
                showKeyboard(etAmountFrom);
            }
        });

        etAmountTo.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                isEditingTop = false;
                showKeyboard(etAmountTo);
            }
        });

        // TextWatcher pentru câmpul de sus - formatează cu simbol
        etAmountFrom.addTextChangedListener(new TextWatcher() {
            private String previousText = "";

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                previousText = s.toString();
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (isUpdatingProgrammatically) return;
                isEditingTop = true;

                String current = s.toString();
                String digits = extractDigits(current);

                // Formatează cu simbol doar dacă avem cifre
                if (!digits.isEmpty() && fromAccount != null) {
                    String sign = isDirectionDown ? "" : "+";
                    String symbol = getCurrencySymbol(fromAccount.getCurrency());
                    String formatted = sign + symbol + digits;

                    if (!current.equals(formatted)) {
                        isUpdatingProgrammatically = true;
                        etAmountFrom.setText(formatted);
                        etAmountFrom.setSelection(formatted.length());
                        isUpdatingProgrammatically = false;
                    }
                }

                calculateOtherAmount();
                updateExchangeButton();
            }
        });

        // TextWatcher pentru câmpul de jos - formatează cu simbol
        etAmountTo.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (isUpdatingProgrammatically) return;
                isEditingTop = false;

                String current = s.toString();
                String digits = extractDigits(current);

                // Formatează cu simbol doar dacă avem cifre
                if (!digits.isEmpty() && toAccount != null) {
                    String sign = isDirectionDown ? "+" : "";
                    String symbol = getCurrencySymbol(toAccount.getCurrency());
                    String formatted = sign + symbol + digits;

                    if (!current.equals(formatted)) {
                        isUpdatingProgrammatically = true;
                        etAmountTo.setText(formatted);
                        etAmountTo.setSelection(formatted.length());
                        isUpdatingProgrammatically = false;
                    }
                }

                calculateOtherAmount();
                updateExchangeButton();
            }
        });
    }

    private void toggleDirection() {
        isDirectionDown = !isDirectionDown;

        // Rotește săgeata
        btnDirection.animate()
            .rotation(isDirectionDown ? 0 : 180)
            .setDuration(200)
            .start();

        // Actualizează vizual semnele (+/-)
        updateDirectionVisuals();

        // Recalculează rata și suma (fără swap de conturi!)
        loadExchangeRate();
        updateConvertedAmount();
        updateExchangeButton();
    }

    private void updateDirectionVisuals() {
        if (fromAccount == null || toAccount == null) return;

        String fromSymbol = getCurrencySymbol(fromAccount.getCurrency());
        String toSymbol = getCurrencySymbol(toAccount.getCurrency());

        if (isDirectionDown) {
            // Banii merg de sus în jos: sus trimite, jos primește (+)
            etAmountFrom.setHint(fromSymbol + "0");
            etAmountFrom.setTextColor(getResources().getColor(R.color.white, null));
            etAmountTo.setHint("+" + toSymbol + "0");
            etAmountTo.setTextColor(getResources().getColor(R.color.green_accent, null));
        } else {
            // Banii merg de jos în sus: sus primește (+), jos trimite
            etAmountFrom.setHint("+" + fromSymbol + "0");
            etAmountFrom.setTextColor(getResources().getColor(R.color.green_accent, null));
            etAmountTo.setHint(toSymbol + "0");
            etAmountTo.setTextColor(getResources().getColor(R.color.white, null));
        }
    }

    private double getAmountFromField(EditText field) {
        String input = field.getText().toString()
            .replace(",", ".")
            .replace(" ", "")
            .replace("+", "")
            .replace("-", "");

        // Elimină simbolurile de monedă
        input = input.replace("€", "").replace("$", "").replace("£", "").replace("lei", "").trim();

        if (input.isEmpty()) {
            return 0;
        }

        try {
            return Double.parseDouble(input);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double getInputAmount() {
        return getAmountFromField(etAmountFrom);
    }

    private void calculateOtherAmount() {
        if (currentRate <= 0 || fromAccount == null || toAccount == null) return;

        isUpdatingProgrammatically = true;

        String fromSymbol = getCurrencySymbol(fromAccount.getCurrency());
        String toSymbol = getCurrencySymbol(toAccount.getCurrency());

        // Rata e întotdeauna: sourceAccount → destAccount
        // DOWN: sursa=sus, dest=jos -> rata = sus→jos
        // UP: sursa=jos, dest=sus -> rata = jos→sus

        if (isEditingTop) {
            // Userul editează sus, calculăm jos
            double topAmount = getAmountFromField(etAmountFrom);
            if (topAmount > 0) {
                double bottomAmount;
                if (isDirectionDown) {
                    // DOWN: sus e sursa, jos e destinația
                    // rata = sus→jos, deci jos = sus * rata
                    bottomAmount = topAmount * currentRate;
                } else {
                    // UP: jos e sursa, sus e destinația
                    // rata = jos→sus, userul introduce cât primește sus
                    // deci jos = sus / rata
                    bottomAmount = topAmount / currentRate;
                }
                String sign = isDirectionDown ? "+" : "-";
                etAmountTo.setText(sign + toSymbol + amountFormat.format(bottomAmount));
            } else {
                etAmountTo.setText("");
            }
        } else {
            // Userul editează jos, calculăm sus
            double bottomAmount = getAmountFromField(etAmountTo);
            if (bottomAmount > 0) {
                double topAmount;
                if (isDirectionDown) {
                    // DOWN: sus e sursa, jos e destinația
                    // rata = sus→jos, userul introduce cât primește jos
                    // deci sus = jos / rata
                    topAmount = bottomAmount / currentRate;
                } else {
                    // UP: jos e sursa, sus e destinația
                    // rata = jos→sus, deci sus = jos * rata
                    topAmount = bottomAmount * currentRate;
                }
                String sign = isDirectionDown ? "-" : "+";
                etAmountFrom.setText(sign + fromSymbol + amountFormat.format(topAmount));
            } else {
                etAmountFrom.setText("");
            }
        }

        isUpdatingProgrammatically = false;
    }

    private void updateConvertedAmount() {
        calculateOtherAmount();
    }

    private void updateExchangeButton() {
        // Calculează suma de trimis din contul sursă
        double sendAmount = getSendAmount();

        // Contul sursă bazat pe direcție
        AccountData sourceAccount = isDirectionDown ? fromAccount : toAccount;

        // Verifică dacă depășește soldul
        boolean exceedsBalance = sendAmount > 0
                && sourceAccount != null
                && sendAmount > sourceAccount.getBalance();

        // Actualizează starea vizuală de eroare
        updateErrorState(exceedsBalance);

        boolean canExchange = sendAmount > 0
                && fromAccount != null
                && toAccount != null
                && sourceAccount != null
                && sendAmount <= sourceAccount.getBalance()
                && currentRate > 0
                && !isLoading;

        btnExchange.setEnabled(canExchange);
        if (canExchange) {
            btnExchange.setBackgroundResource(R.drawable.bg_button_primary);
            btnExchange.setTextColor(getResources().getColor(R.color.white, null));
        } else {
            btnExchange.setBackgroundResource(R.drawable.bg_button_disabled);
            btnExchange.setTextColor(getResources().getColor(R.color.white_50, null));
        }
    }

    private void updateErrorState(boolean exceedsBalance) {
        // Determină care card e sursa bazat pe direcție
        boolean isTopSource = isDirectionDown;

        // Resetează ambele carduri la starea normală
        cardFrom.setBackgroundResource(R.drawable.bg_exchange_card);
        cardTo.setBackgroundResource(R.drawable.bg_exchange_card);
        tvBalanceFrom.setTextColor(getResources().getColor(R.color.white_50, null));
        tvBalanceTo.setTextColor(getResources().getColor(R.color.white_50, null));
        tvErrorFrom.setVisibility(View.GONE);
        tvErrorTo.setVisibility(View.GONE);

        if (exceedsBalance) {
            // Arată eroarea pe cardul sursă
            if (isTopSource) {
                cardFrom.setBackgroundResource(R.drawable.bg_exchange_card_error);
                tvBalanceFrom.setTextColor(getResources().getColor(R.color.error_red, null));
                tvErrorFrom.setVisibility(View.VISIBLE);
                etAmountFrom.setTextColor(getResources().getColor(R.color.error_red, null));
            } else {
                cardTo.setBackgroundResource(R.drawable.bg_exchange_card_error);
                tvBalanceTo.setTextColor(getResources().getColor(R.color.error_red, null));
                tvErrorTo.setVisibility(View.VISIBLE);
                etAmountTo.setTextColor(getResources().getColor(R.color.error_red, null));
            }
        } else {
            // Restaurează culorile normale bazat pe direcție
            updateDirectionVisuals();
        }
    }

    private double getSendAmount() {
        // Suma de trimis este întotdeauna din contul sursă
        // Contul sursă: DOWN = sus (fromAccount), UP = jos (toAccount)
        double topAmount = getAmountFromField(etAmountFrom);
        double bottomAmount = getAmountFromField(etAmountTo);

        if (isDirectionDown) {
            // Sursa e sus, deci trimitem topAmount
            return topAmount;
        } else {
            // Sursa e jos, deci trimitem bottomAmount
            return bottomAmount;
        }
    }

    private String extractDigits(String text) {
        // Extrage doar cifrele și punctul/virgula din text
        StringBuilder digits = new StringBuilder();
        boolean hasDecimal = false;
        for (char c : text.toCharArray()) {
            if (Character.isDigit(c)) {
                digits.append(c);
            } else if ((c == '.' || c == ',') && !hasDecimal) {
                digits.append(c);
                hasDecimal = true;
            }
        }
        return digits.toString();
    }

    private String getCurrencySymbol(String currency) {
        if (currency == null) return "";
        switch (currency) {
            case "EUR": return "€";
            case "USD": return "$";
            case "GBP": return "£";
            case "RON": return "lei ";
            default: return currency + " ";
        }
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

    private void loadAccounts() {
        ApiClient.getAccountService().getAccounts().enqueue(new Callback<ApiResponse<AccountsData>>() {
            @Override
            public void onResponse(Call<ApiResponse<AccountsData>> call, Response<ApiResponse<AccountsData>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    accounts = response.body().getData().getAccounts();

                    if (accounts.size() >= 2) {
                        fromAccount = accounts.get(0);
                        toAccount = accounts.get(1);
                        updateAccountDisplays();
                        loadExchangeRate();
                        showContent();
                    } else {
                        // Nu ai suficiente conturi - închide activity-ul
                        new SwiftBankDialog(ExchangeActivity.this)
                            .setIcon(R.drawable.ic_info)
                            .setTitle("Conturi insuficiente")
                            .setMessage("Ai nevoie de cel puțin două conturi pentru a muta bani între ele.")
                            .setPrimaryButton("Am înțeles", v -> finish())
                            .setCancelable(false)
                            .show();
                    }
                } else {
                    if (isFinishing() || isDestroyed()) return;
                    new SwiftBankDialog(ExchangeActivity.this)
                            .setIcon(R.drawable.ic_error)
                            .setTitle("Eroare")
                            .setMessage("Eroare la încărcarea conturilor")
                            .setPrimaryButton("OK", v -> finish())
                            .show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<AccountsData>> call, Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                new SwiftBankDialog(ExchangeActivity.this)
                        .setIcon(R.drawable.ic_error)
                        .setTitle("Eroare")
                        .setMessage("Eroare la încărcarea conturilor")
                        .setPrimaryButton("OK", v -> finish())
                        .show();
            }
        });
    }

    private void updateAccountDisplays() {
        if (fromAccount != null) {
            tvCurrencyFrom.setText(fromAccount.getCurrency());
            ivFlagFrom.setImageResource(getFlagResource(fromAccount.getCurrency()));
            tvBalanceFrom.setText("Sold: " + balanceFormat.format(fromAccount.getBalance()) + " " + getCurrencySymbol(fromAccount.getCurrency()));
            etAmountFrom.setHint("-" + getCurrencySymbol(fromAccount.getCurrency()) + "0");
        }

        if (toAccount != null) {
            tvCurrencyTo.setText(toAccount.getCurrency());
            ivFlagTo.setImageResource(getFlagResource(toAccount.getCurrency()));
            tvBalanceTo.setText("Sold: " + balanceFormat.format(toAccount.getBalance()) + " " + getCurrencySymbol(toAccount.getCurrency()));
        }

        updateDirectionVisuals();
        updateConvertedAmount();
    }

    private void loadExchangeRate() {
        if (fromAccount == null || toAccount == null) return;

        // Determină valutele bazat pe direcție
        AccountData sourceAccount = isDirectionDown ? fromAccount : toAccount;
        AccountData destAccount = isDirectionDown ? toAccount : fromAccount;

        String fromCurrency = sourceAccount.getCurrency();
        String toCurrency = destAccount.getCurrency();

        // Încearcă să ia rata din cache local (instant)
        RatesManager ratesManager = RatesManager.getInstance(this);
        if (ratesManager.hasRates()) {
            double localRate = ratesManager.getExchangeRate(fromCurrency, toCurrency);
            if (localRate > 0) {
                currentRate = localRate;
                displayExchangeRate();
                return;
            }
        }

        // Fallback la API
        ApiClient.getAccountService().getExchangeRate(fromCurrency, toCurrency)
            .enqueue(new Callback<ApiResponse<ExchangeRateData>>() {
                @Override
                public void onResponse(Call<ApiResponse<ExchangeRateData>> call, Response<ApiResponse<ExchangeRateData>> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                        currentRate = response.body().getData().getRate();
                        displayExchangeRate();
                    }
                }

                @Override
                public void onFailure(Call<ApiResponse<ExchangeRateData>> call, Throwable t) {
                    rateContainer.setVisibility(View.GONE);
                }
            });
    }

    private void displayExchangeRate() {
        rateContainer.setVisibility(View.VISIBLE);

        // Afișează rata bazat pe direcție (sursa → destinație)
        AccountData sourceAccount = isDirectionDown ? fromAccount : toAccount;
        AccountData destAccount = isDirectionDown ? toAccount : fromAccount;

        tvExchangeRate.setText(String.format("1 %s = %.4f %s",
            sourceAccount.getCurrency(),
            currentRate,
            destAccount.getCurrency()));
        updateConvertedAmount();
        updateExchangeButton();
    }

    private void ensureIdempotencyKey() {
        if (idempotencyKey == null || idempotencyKey.isEmpty()) {
            idempotencyKey = UUID.randomUUID().toString();
        }
    }

    private void resetIdempotencyKey() {
        idempotencyKey = UUID.randomUUID().toString();
    }

    private void swapAccounts() {
        if (fromAccount == null || toAccount == null) return;

        AccountData temp = fromAccount;
        fromAccount = toAccount;
        toAccount = temp;

        isUpdatingProgrammatically = true;
        etAmountFrom.setText("");
        etAmountTo.setText("");
        isUpdatingProgrammatically = false;
        updateAccountDisplays();
        loadExchangeRate();
    }

    private void showAccountSelector(boolean isFrom) {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View sheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_select_account, null);
        bottomSheet.setContentView(sheetView);

        TextView tvTitle = sheetView.findViewById(R.id.tvSheetTitle);
        tvTitle.setText(isFrom ? "De unde" : "Unde");

        RecyclerView rvAccounts = sheetView.findViewById(R.id.rvAccounts);
        rvAccounts.setLayoutManager(new LinearLayoutManager(this));

        List<AccountData> availableAccounts = new ArrayList<>();
        for (AccountData acc : accounts) {
            if (isFrom && toAccount != null && acc.getAccountId() == toAccount.getAccountId()) {
                continue;
            }
            if (!isFrom && fromAccount != null && acc.getAccountId() == fromAccount.getAccountId()) {
                continue;
            }
            availableAccounts.add(acc);
        }

        AccountSelectorAdapter adapter = new AccountSelectorAdapter(availableAccounts, account -> {
            if (isFrom) {
                fromAccount = account;
            } else {
                toAccount = account;
            }
            bottomSheet.dismiss();
            isUpdatingProgrammatically = true;
            etAmountFrom.setText("");
            etAmountTo.setText("");
            isUpdatingProgrammatically = false;
            updateAccountDisplays();
            loadExchangeRate();
        });
        rvAccounts.setAdapter(adapter);

        bottomSheet.show();
    }

    private void initiateExchange() {
        double sendAmount = getSendAmount();
        if (sendAmount <= 0 || fromAccount == null || toAccount == null) return;

        // Determină contul sursă și destinație bazat pe direcție
        AccountData sourceAccount = isDirectionDown ? fromAccount : toAccount;
        AccountData destAccount = isDirectionDown ? toAccount : fromAccount;

        // Verifică fonduri suficiente
        if (sendAmount > sourceAccount.getBalance()) {
            String symbol = getCurrencySymbol(sourceAccount.getCurrency());
            SwiftBankDialog.showErrorDialog(this,
                "Fonduri insuficiente",
                String.format("Nu ai suficiente fonduri în contul %s.\nSold disponibil: %s%s",
                    sourceAccount.getCurrency(),
                    symbol,
                    balanceFormat.format(sourceAccount.getBalance())));
            return;
        }

        String amountStr = balanceFormat.format(sendAmount) + " " + sourceAccount.getCurrency();
        String destStr = destAccount.getCurrency();

        // Verifică dacă trebuie să folosim biometrie
        if (BiometricHelper.shouldUseBiometric(this)) {
            BiometricHelper.authenticate(this, "Confirmă mutarea",
                    String.format("Mută %s → %s", amountStr, destStr),
                    new BiometricHelper.BiometricCallback() {
                        @Override
                        public void onSuccess() {
                            executeExchange();
                        }

                        @Override
                        public void onError(String error) {
                            if (isFinishing() || isDestroyed()) return;
                            SwiftBankDialog.showErrorDialog(ExchangeActivity.this,
                                    "Autentificare eșuată", error);
                        }

                        @Override
                        public void onCancel() {
                            showPinConfirmation(amountStr, destStr);
                        }
                    });
        } else {
            showPinConfirmation(amountStr, destStr);
        }
    }

    private void showPinConfirmation(String amountStr, String destStr) {
        new PinConfirmDialog(
                this,
                "Confirmă mutarea",
                String.format("Introdu PIN-ul pentru a converti %s în %s", amountStr, destStr),
                new PinConfirmDialog.PinCallback() {
                    @Override
                    public void onPinConfirmed() {
                        executeExchange();
                    }

                    @Override
                    public void onCancelled() {
                        // Utilizatorul a anulat - nu facem nimic
                    }
                }
        ).show();
    }

    private void executeExchange() {
        double sendAmount = getSendAmount();
        if (sendAmount <= 0 || fromAccount == null || toAccount == null) return;

        // Determină contul sursă și destinație bazat pe direcție
        AccountData sourceAccount = isDirectionDown ? fromAccount : toAccount;
        AccountData destAccount = isDirectionDown ? toAccount : fromAccount;
        ensureIdempotencyKey();

        isLoading = true;
        btnExchange.setText("Se procesează...");
        updateExchangeButton();

        ExchangeRequest request = new ExchangeRequest(
            String.valueOf(sourceAccount.getAccountId()),
            String.valueOf(destAccount.getAccountId()),
            sendAmount
        );

        ApiClient.getAccountService().exchange(idempotencyKey, request).enqueue(new Callback<ApiResponse<ExchangeResultData>>() {
            @Override
            public void onResponse(Call<ApiResponse<ExchangeResultData>> call, Response<ApiResponse<ExchangeResultData>> response) {
                isLoading = false;
                btnExchange.setText("Verifică ordinul");

                if (response.isSuccessful() && response.body() != null) {
                    resetIdempotencyKey();
                    ExchangeResultData result = response.body().getData();

                    String message = String.format("%.2f %s → %.2f %s",
                        Math.abs(result.getFrom().getAmount()),
                        result.getFrom().getCurrency(),
                        result.getTo().getAmount(),
                        result.getTo().getCurrency());

                    SwiftBankDialog.showSuccessDialog(ExchangeActivity.this,
                        "Banii au fost mutați!",
                        message,
                        v -> finish());
                } else {
                    ApiErrorResponse error = ErrorParser.parseError(response);
                    String message = "Nu am putut efectua schimbul";
                    if (error != null && error.getError() != null) {
                        message = error.getError().getMessage();
                    }
                    resetIdempotencyKey();
                    SwiftBankDialog.showErrorDialog(ExchangeActivity.this, message);
                    updateExchangeButton();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<ExchangeResultData>> call, Throwable t) {
                isLoading = false;
                btnExchange.setText("Verifică ordinul");
                SwiftBankDialog.showErrorDialog(ExchangeActivity.this, "Eroare de conexiune");
                updateExchangeButton();
            }
        });
    }

    private void showKeyboard(EditText editText) {
        editText.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
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
                itemView.setSelected(false);
                ivSelected.setVisibility(View.GONE);
            }
        }
    }
}
