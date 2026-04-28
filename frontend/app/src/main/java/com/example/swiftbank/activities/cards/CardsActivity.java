package com.example.swiftbank.activities.cards;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.swiftbank.R;
import com.example.swiftbank.adapters.CardAdapter;
import com.example.swiftbank.api.ApiClient;
import com.example.swiftbank.api.dto.request.CreateCardRequest;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.success.CardData;
import com.example.swiftbank.api.dto.response.data.success.CardDetailsData;
import com.example.swiftbank.api.dto.response.data.success.CardsData;
import com.example.swiftbank.utils.SwiftBankDialog;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CardsActivity extends AppCompatActivity implements CardAdapter.OnCardClickListener {

    private ImageView btnBack;
    private View btnAddCard;
    private RecyclerView rvCards;
    private View cardsContainer;
    private LinearLayout emptyState, loadingState;
    private View btnCreateFirstCard;

    private CardAdapter adapter;
    private List<CardData> currentCards = new java.util.ArrayList<>();

    private final ActivityResultLauncher<Intent> cardDetailsLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    loadCards();
                }
            }
    );

    private final ActivityResultLauncher<Intent> designPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String design = result.getData().getStringExtra(CardDesignActivity.EXTRA_SELECTED_DESIGN);
                    if (design != null) {
                        createCard(design);
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cards);

        initViews();
        setupListeners();
        setupRecyclerView();
        loadCards();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        btnAddCard = findViewById(R.id.btnAddCard);
        rvCards = findViewById(R.id.rvCards);
        cardsContainer = findViewById(R.id.cardsContainer);
        emptyState = findViewById(R.id.emptyState);
        loadingState = findViewById(R.id.loadingState);
        btnCreateFirstCard = findViewById(R.id.btnCreateFirstCard);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnAddCard.setOnClickListener(v -> showCreateCardDialog());
        btnCreateFirstCard.setOnClickListener(v -> showCreateCardDialog());
    }

    private void setupRecyclerView() {
        adapter = new CardAdapter(this);
        rvCards.setLayoutManager(new LinearLayoutManager(this));
        rvCards.setAdapter(adapter);
    }

    private void loadCards() {
        showLoading();

        ApiClient.getCardService().getCards().enqueue(new Callback<ApiResponse<CardsData>>() {
            @Override
            public void onResponse(Call<ApiResponse<CardsData>> call, Response<ApiResponse<CardsData>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    List<CardData> cards = response.body().getData().getCards();
                    if (cards == null || cards.isEmpty()) {
                        showEmpty();
                    } else {
                        showCards(cards);
                    }
                } else {
                    showEmpty();
                    Toast.makeText(CardsActivity.this, "Eroare la încărcarea cardurilor", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<CardsData>> call, Throwable t) {
                showEmpty();
                Toast.makeText(CardsActivity.this, "Eroare de conexiune", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showLoading() {
        loadingState.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.GONE);
        cardsContainer.setVisibility(View.GONE);
        btnAddCard.setVisibility(View.GONE);
    }

    private void showEmpty() {
        loadingState.setVisibility(View.GONE);
        emptyState.setVisibility(View.VISIBLE);
        cardsContainer.setVisibility(View.GONE);
        btnAddCard.setVisibility(View.GONE);
    }

    private void showCards(List<CardData> cards) {
        loadingState.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
        cardsContainer.setVisibility(View.VISIBLE);

        currentCards.clear();
        currentCards.addAll(cards);

        // Hide add button if all 5 designs are used
        if (cards.size() >= 5) {
            btnAddCard.setVisibility(View.GONE);
        } else {
            btnAddCard.setVisibility(View.VISIBLE);
        }

        adapter.setCards(cards);
    }

    private void showCreateCardDialog() {
        // Collect used designs
        ArrayList<String> usedDesigns = new ArrayList<>();
        for (CardData card : currentCards) {
            if (card.getCardDesign() != null) {
                usedDesigns.add(card.getCardDesign());
            }
        }

        Intent intent = new Intent(this, CardDesignActivity.class);
        intent.putStringArrayListExtra(CardDesignActivity.EXTRA_USED_DESIGNS, usedDesigns);
        designPickerLauncher.launch(intent);
    }

    private void createCard(String design) {
        showLoading();

        CreateCardRequest request = new CreateCardRequest(design);
        ApiClient.getCardService().createCard(request).enqueue(new Callback<ApiResponse<CardDetailsData>>() {
            @Override
            public void onResponse(Call<ApiResponse<CardDetailsData>> call, Response<ApiResponse<CardDetailsData>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    CardData card = response.body().getData().getCard();
                    showNewCardSuccess(card);
                    loadCards();
                } else {
                    loadCards();
                    Toast.makeText(CardsActivity.this, "Eroare la crearea cardului", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<CardDetailsData>> call, Throwable t) {
                loadCards();
                Toast.makeText(CardsActivity.this, "Eroare de conexiune", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showNewCardSuccess(CardData card) {
        new SwiftBankDialog(this)
                .setTitle("Card creat cu succes!")
                .setMessage("Cardul tău virtual a fost adăugat !")
                .setPrimaryButton("OK", null)
                .show();
    }

    @Override
    public void onCardClick(CardData card) {
        Intent intent = new Intent(this, CardDetailsActivity.class);
        intent.putExtra(CardDetailsActivity.EXTRA_CARD_ID, card.getCardId());
        intent.putExtra(CardDetailsActivity.EXTRA_CARD_NUMBER_MASKED, card.getCardNumberMasked());
        intent.putExtra(CardDetailsActivity.EXTRA_CARD_HOLDER, card.getCardHolderName());
        intent.putExtra(CardDetailsActivity.EXTRA_CARD_EXPIRY, card.getFormattedExpiry());
        intent.putExtra(CardDetailsActivity.EXTRA_CARD_BRAND, card.getCardBrand());
        intent.putExtra(CardDetailsActivity.EXTRA_CARD_DESIGN, card.getCardDesign());
        intent.putExtra(CardDetailsActivity.EXTRA_CARD_BLOCKED, card.isBlocked());
        cardDetailsLauncher.launch(intent);
    }
}
