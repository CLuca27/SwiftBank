package com.example.swiftbank.activities.cards;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.example.swiftbank.R;
import com.example.swiftbank.api.ApiClient;
import com.example.swiftbank.api.dto.request.CardDetailsRequest;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.success.CardDetailsData;
import com.example.swiftbank.utils.BiometricHelper;
import com.example.swiftbank.utils.PinConfirmDialog;
import com.example.swiftbank.utils.SwiftBankDialog;

import java.util.concurrent.Executor;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CardDetailsActivity extends AppCompatActivity {

    public static final String EXTRA_CARD_ID = "card_id";
    public static final String EXTRA_CARD_NUMBER_MASKED = "card_number_masked";
    public static final String EXTRA_CARD_HOLDER = "card_holder";
    public static final String EXTRA_CARD_EXPIRY = "card_expiry";
    public static final String EXTRA_CARD_BRAND = "card_brand";
    public static final String EXTRA_CARD_DESIGN = "card_design";
    public static final String EXTRA_CARD_BLOCKED = "card_blocked";

    private int cardId;
    private String cardNumberMasked;
    private String cardHolder;
    private String cardExpiry;
    private String cardBrand;
    private String cardDesign;
    private boolean isBlocked;

    private String fullCardNumber;
    private String cvv;
    private boolean detailsRevealed = false;

    private View cardBackground;
    private View blockedOverlay;
    private TextView tvCardNumber, tvExpiry, tvBlockedBadge, tvCvv, tvCardNumberMasked;
    private View cardDetailsContainer;
    private LinearLayout btnShowDetails, btnToggleBlock, btnDelete;
    private ImageView ivBlockIcon, btnCopyNumberCard, btnCopyCvvCard;
    private TextView tvBlockText;

    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;
    private boolean biometricAvailable = false;
    private PinConfirmDialog pinDialog;

    private static final long DETAILS_VISIBLE_DURATION = 30000; // 30 secunde
    private CountDownTimer hideDetailsTimer;
    private TextView tvCountdown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_card_details);

        extractIntentData();
        initViews();
        setupBiometric();
        displayCardData();
        setupListeners();
    }

    private void extractIntentData() {
        Intent intent = getIntent();
        cardId = intent.getIntExtra(EXTRA_CARD_ID, -1);
        cardNumberMasked = intent.getStringExtra(EXTRA_CARD_NUMBER_MASKED);
        cardHolder = intent.getStringExtra(EXTRA_CARD_HOLDER);
        cardExpiry = intent.getStringExtra(EXTRA_CARD_EXPIRY);
        cardBrand = intent.getStringExtra(EXTRA_CARD_BRAND);
        cardDesign = intent.getStringExtra(EXTRA_CARD_DESIGN);
        isBlocked = intent.getBooleanExtra(EXTRA_CARD_BLOCKED, false);
    }

    private void initViews() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        cardBackground = findViewById(R.id.cardBackground);
        blockedOverlay = findViewById(R.id.blockedOverlay);
        tvCardNumber = findViewById(R.id.tvCardNumber);
        tvExpiry = findViewById(R.id.tvExpiry);
        tvBlockedBadge = findViewById(R.id.tvBlockedBadge);
        tvCvv = findViewById(R.id.tvCvv);
        tvCardNumberMasked = findViewById(R.id.tvCardNumberMasked);
        cardDetailsContainer = findViewById(R.id.cardDetailsContainer);

        btnShowDetails = findViewById(R.id.btnShowDetails);
        btnToggleBlock = findViewById(R.id.btnToggleBlock);
        btnDelete = findViewById(R.id.btnDelete);

        ivBlockIcon = findViewById(R.id.ivBlockIcon);
        tvBlockText = findViewById(R.id.tvBlockText);

        btnCopyNumberCard = findViewById(R.id.btnCopyNumberCard);
        btnCopyCvvCard = findViewById(R.id.btnCopyCvvCard);
    }

    private void setupBiometric() {
        BiometricManager biometricManager = BiometricManager.from(this);
        biometricAvailable = biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS;

        if (biometricAvailable) {
            Executor executor = ContextCompat.getMainExecutor(this);
            biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                    super.onAuthenticationSucceeded(result);
                    fetchCardDetails(null, true);
                }

                @Override
                public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                    super.onAuthenticationError(errorCode, errString);
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        showPinDialog();
                    }
                }

                @Override
                public void onAuthenticationFailed() {
                    super.onAuthenticationFailed();
                    Toast.makeText(CardDetailsActivity.this, "Autentificare eșuată", Toast.LENGTH_SHORT).show();
                }
            });

            promptInfo = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Verificare identitate")
                    .setSubtitle("Folosește amprenta pentru a vedea datele cardului")
                    .setNegativeButtonText("Folosește PIN")
                    .build();
        }
    }

    private void displayCardData() {
        // Card brand is now a static VISA logo image
        cardBackground.setBackgroundResource(getGradientForDesign(cardDesign));

        // Show masked card number (•••• 7173)
        String lastFour = cardNumberMasked != null && cardNumberMasked.length() >= 4
                ? cardNumberMasked.substring(cardNumberMasked.length() - 4)
                : "••••";
        tvCardNumberMasked.setText("•••• " + lastFour);

        // Details hidden by default
        cardDetailsContainer.setVisibility(View.GONE);
        tvCardNumberMasked.setVisibility(View.VISIBLE);

        updateBlockedState();
    }

    private void updateBlockedState() {
        blockedOverlay.setVisibility(isBlocked ? View.VISIBLE : View.GONE);
        tvBlockedBadge.setVisibility(isBlocked ? View.VISIBLE : View.GONE);

        if (isBlocked) {
            tvBlockText.setText("Deblochează cardul");
            ivBlockIcon.setImageResource(R.drawable.ic_check_circle);
            ivBlockIcon.setColorFilter(ContextCompat.getColor(this, R.color.green_accent));
        } else {
            tvBlockText.setText("Blochează temporar");
            ivBlockIcon.setImageResource(R.drawable.ic_block);
            ivBlockIcon.setColorFilter(ContextCompat.getColor(this, R.color.orange));
        }
    }

    private void setupListeners() {
        btnShowDetails.setOnClickListener(v -> {
            if (detailsRevealed) {
                hideDetails();
            } else {
                requestAuthentication();
            }
        });

        btnToggleBlock.setOnClickListener(v -> toggleCardBlock());

        btnDelete.setOnClickListener(v -> confirmDeleteCard());

        btnCopyNumberCard.setOnClickListener(v -> {
            if (fullCardNumber != null) {
                copyToClipboard("Număr card", fullCardNumber);
            }
        });

        btnCopyCvvCard.setOnClickListener(v -> {
            if (cvv != null) {
                copyToClipboard("CVV", cvv);
            }
        });
    }

    private void requestAuthentication() {
        if (biometricAvailable && BiometricHelper.isBiometricEnabled(this)) {
            biometricPrompt.authenticate(promptInfo);
        } else {
            showPinDialog();
        }
    }

    private void showPinDialog() {
        pinDialog = new PinConfirmDialog(
                this,
                "Verificare identitate",
                "Introdu PIN-ul pentru a vedea datele cardului",
                new PinConfirmDialog.PinInputCallback() {
                    @Override
                    public void onPinEntered(String pin) {
                        fetchCardDetails(pin, false);
                    }

                    @Override
                    public void onCancelled() {
                        pinDialog = null;
                    }
                }
        );
        pinDialog.show();
    }

    private void fetchCardDetails(String pin, boolean fromBiometric) {
        CardDetailsRequest request = new CardDetailsRequest(pin != null ? pin : "biometric");

        ApiClient.getCardService().getCardDetails(cardId, request).enqueue(new Callback<ApiResponse<CardDetailsData>>() {
            @Override
            public void onResponse(Call<ApiResponse<CardDetailsData>> call, Response<ApiResponse<CardDetailsData>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    var card = response.body().getData().getCard();
                    fullCardNumber = card.getCardNumberFormatted();
                    cvv = card.getCvv();

                    if (pinDialog != null) {
                        pinDialog.dismiss();
                        pinDialog = null;
                    }
                    showDetails();
                } else {
                    if (pinDialog != null && !fromBiometric) {
                        pinDialog.showError("PIN incorect");
                    } else {
                        Toast.makeText(CardDetailsActivity.this, "PIN incorect", Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<CardDetailsData>> call, Throwable t) {
                if (pinDialog != null && !fromBiometric) {
                    pinDialog.showError("Eroare de conexiune");
                } else {
                    Toast.makeText(CardDetailsActivity.this, "Eroare de conexiune", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void showDetails() {
        detailsRevealed = true;

        // Populate card details
        tvCardNumber.setText(fullCardNumber);
        tvExpiry.setText(cardExpiry);
        tvCvv.setText(cvv);

        // Show details, hide masked number
        cardDetailsContainer.setVisibility(View.VISIBLE);
        tvCardNumberMasked.setVisibility(View.GONE);

        // Start auto-hide timer
        startHideTimer();
    }

    private void hideDetails() {
        detailsRevealed = false;

        // Cancel timer
        cancelHideTimer();

        // Hide details, show masked number
        cardDetailsContainer.setVisibility(View.GONE);
        tvCardNumberMasked.setVisibility(View.VISIBLE);

        TextView showDetailsText = (TextView) ((LinearLayout) btnShowDetails).getChildAt(1);
        showDetailsText.setText("Afișează datele cardului");
    }

    private void startHideTimer() {
        cancelHideTimer();

        final TextView showDetailsText = (TextView) ((LinearLayout) btnShowDetails).getChildAt(1);

        hideDetailsTimer = new CountDownTimer(DETAILS_VISIBLE_DURATION, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsLeft = (int) (millisUntilFinished / 1000);
                showDetailsText.setText("Se ascunde în " + secondsLeft + "s");
            }

            @Override
            public void onFinish() {
                hideDetails();
            }
        }.start();
    }

    private void cancelHideTimer() {
        if (hideDetailsTimer != null) {
            hideDetailsTimer.cancel();
            hideDetailsTimer = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelHideTimer();
    }

    private void copyToClipboard(String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text.replace(" ", ""));
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "Copiat în clipboard", Toast.LENGTH_SHORT).show();
    }

    private void toggleCardBlock() {
        String message = isBlocked
                ? "Cardul va fi reactivat și vei putea efectua plăți din nou."
                : "Cardul va fi blocat temporar. Poți să îl deblochezi oricând.";

        new SwiftBankDialog(this)
                .setTitle(isBlocked ? "Deblochează card" : "Blochează card")
                .setMessage(message)
                .setPrimaryButton("Confirmă", v -> {
                    ApiClient.getCardService().toggleBlock(cardId).enqueue(new Callback<ApiResponse<Object>>() {
                        @Override
                        public void onResponse(Call<ApiResponse<Object>> call, Response<ApiResponse<Object>> response) {
                            if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                                isBlocked = !isBlocked;
                                updateBlockedState();
                                String msg = isBlocked ? "Card blocat" : "Card deblocat";
                                Toast.makeText(CardDetailsActivity.this, msg, Toast.LENGTH_SHORT).show();
                                setResult(RESULT_OK);
                            } else {
                                Toast.makeText(CardDetailsActivity.this, "Eroare la modificarea cardului", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<ApiResponse<Object>> call, Throwable t) {
                            Toast.makeText(CardDetailsActivity.this, "Eroare de conexiune", Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setSecondaryButton("Anulează", null)
                .show();
    }

    private void confirmDeleteCard() {
        new SwiftBankDialog(this)
                .setTitle("Șterge card")
                .setMessage("Ești sigur că vrei să ștergi acest card? Acțiunea este ireversibilă.")
                .setPrimaryButton("Șterge", v -> deleteCard())
                .setSecondaryButton("Anulează", null)
                .show();
    }

    private void deleteCard() {
        ApiClient.getCardService().deleteCard(cardId).enqueue(new Callback<ApiResponse<Object>>() {
            @Override
            public void onResponse(Call<ApiResponse<Object>> call, Response<ApiResponse<Object>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Toast.makeText(CardDetailsActivity.this, "Card șters", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                } else {
                    Toast.makeText(CardDetailsActivity.this, "Eroare la ștergerea cardului", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Object>> call, Throwable t) {
                Toast.makeText(CardDetailsActivity.this, "Eroare de conexiune", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private int getGradientForDesign(String design) {
        if (design == null) return R.drawable.gradient_card_green;

        switch (design.toLowerCase()) {
            case "blue":
                return R.drawable.gradient_card_blue;
            case "purple":
                return R.drawable.gradient_card_purple;
            case "orange":
                return R.drawable.gradient_card_orange;
            case "black":
                return R.drawable.gradient_card_black;
            case "green":
            default:
                return R.drawable.gradient_card_green;
        }
    }
}
