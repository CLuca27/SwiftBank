package com.example.swiftbank.activities.bills;

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

import androidx.cardview.widget.CardView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.swiftbank.R;
import com.example.swiftbank.api.ApiClient;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.success.BillerData;
import com.example.swiftbank.api.dto.response.data.success.BillersData;
import com.example.swiftbank.utils.RemoteImageLoader;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class BillersActivity extends AppCompatActivity {

    private ImageView btnBack;
    private TextView tvTitle;
    private EditText etSearch;
    private LinearLayout loadingState, emptyState;
    private RecyclerView rvBillers;

    private String category;
    private String categoryName;

    private List<BillerData> allBillers = new ArrayList<>();
    private List<BillerData> filteredBillers = new ArrayList<>();
    private BillersAdapter adapter;

    private ActivityResultLauncher<Intent> paymentLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_billers);

        category = getIntent().getStringExtra("category");
        categoryName = getIntent().getStringExtra("category_name");

        if (category == null) {
            finish();
            return;
        }

        initViews();
        setupLauncher();
        setupListeners();
        setupRecyclerView();
        loadBillers();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        tvTitle = findViewById(R.id.tvTitle);
        etSearch = findViewById(R.id.etSearch);
        loadingState = findViewById(R.id.loadingState);
        emptyState = findViewById(R.id.emptyState);
        rvBillers = findViewById(R.id.rvBillers);

        tvTitle.setText(categoryName != null ? categoryName : category);
    }

    private void setupLauncher() {
        paymentLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        setResult(RESULT_OK, result.getData());
                        finish();
                    }
                }
        );
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterBillers(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupRecyclerView() {
        adapter = new BillersAdapter();
        rvBillers.setLayoutManager(new LinearLayoutManager(this));
        rvBillers.setAdapter(adapter);
    }

    private void loadBillers() {
        showLoading();

        ApiClient.getBillService().getBillers(category)
                .enqueue(new Callback<ApiResponse<BillersData>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<BillersData>> call,
                                           Response<ApiResponse<BillersData>> response) {
                        if (isFinishing() || isDestroyed()) return;

                        if (response.isSuccessful() && response.body() != null &&
                                response.body().getData() != null) {
                            List<BillerData> serverBillers = response.body().getData().getBillers();
                            allBillers = filterBillersByCategory(serverBillers);
                            filteredBillers = new ArrayList<>(allBillers);
                            adapter.notifyDataSetChanged();

                            if (allBillers.isEmpty()) {
                                showEmpty();
                            } else {
                                showContent();
                            }
                        } else {
                            showEmpty();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<BillersData>> call, Throwable t) {
                        if (isFinishing() || isDestroyed()) return;
                        showEmpty();
                    }
                });
    }

    private List<BillerData> filterBillersByCategory(List<BillerData> billers) {
        List<BillerData> result = new ArrayList<>();
        if (billers == null) return result;

        String expectedCategory = normalizeCategoryKey(category);
        for (BillerData biller : billers) {
            if (biller != null &&
                    normalizeCategoryKey(biller.getCategory()).equals(expectedCategory)) {
                result.add(biller);
            }
        }

        return result;
    }

    private String normalizeCategoryKey(String key) {
        return key == null ? "" : key.trim().toLowerCase();
    }

    private void filterBillers(String query) {
        filteredBillers.clear();
        String lowerQuery = query.toLowerCase().trim();

        if (lowerQuery.isEmpty()) {
            filteredBillers.addAll(allBillers);
        } else {
            for (BillerData b : allBillers) {
                if (b.getName().toLowerCase().contains(lowerQuery)) {
                    filteredBillers.add(b);
                }
            }
        }

        adapter.notifyDataSetChanged();

        if (filteredBillers.isEmpty() && !allBillers.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            rvBillers.setVisibility(View.GONE);
        } else if (!filteredBillers.isEmpty()) {
            emptyState.setVisibility(View.GONE);
            rvBillers.setVisibility(View.VISIBLE);
        }
    }

    private void showLoading() {
        loadingState.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.GONE);
        rvBillers.setVisibility(View.GONE);
    }

    private void showEmpty() {
        loadingState.setVisibility(View.GONE);
        emptyState.setVisibility(View.VISIBLE);
        rvBillers.setVisibility(View.GONE);
    }

    private void showContent() {
        loadingState.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
        rvBillers.setVisibility(View.VISIBLE);
    }

    private void openPayment(BillerData biller) {
        Intent intent = new Intent(this, BillPaymentActivity.class);
        intent.putExtra("biller_id", biller.getBillerId());
        intent.putExtra("biller_name", biller.getName());
        intent.putExtra("account_format", biller.getAccountFormat());
        intent.putExtra("biller_logo_url", biller.getLogoUrl());
        intent.putExtra("biller_category", biller.getCategory());
        paymentLauncher.launch(intent);
    }

    private String getCategoryDisplayName(String key) {
        if (key == null || key.isEmpty()) return "";
        switch (key.toLowerCase()) {
            case "utilities": return "Utilități";
            case "telecom": return "Telecom";
            case "internet": return "Internet";
            case "tv": return "TV & Cablu";
            case "insurance": return "Asigurări";
            case "subscriptions": return "Abonamente";
            default: return key.substring(0, 1).toUpperCase() + key.substring(1);
        }
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

    // ==================== ADAPTER ====================

    class BillersAdapter extends RecyclerView.Adapter<BillersAdapter.ViewHolder> {

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_biller, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            holder.bind(filteredBillers.get(position));
        }

        @Override
        public int getItemCount() {
            return filteredBillers.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvInitial, tvName, tvCategory;
            CardView cardLogo;
            ImageView ivLogo;

            ViewHolder(View itemView) {
                super(itemView);
                tvInitial = itemView.findViewById(R.id.tvInitial);
                tvName = itemView.findViewById(R.id.tvName);
                tvCategory = itemView.findViewById(R.id.tvCategory);
                cardLogo = itemView.findViewById(R.id.cardLogo);
                ivLogo = itemView.findViewById(R.id.ivLogo);

                itemView.setOnClickListener(v -> {
                    openPayment(filteredBillers.get(getAdapterPosition()));
                });
            }

            void bind(BillerData biller) {
                String name = biller.getName();
                tvName.setText(name);
                tvCategory.setText(getCategoryDisplayName(biller.getCategory()));

                String logoUrl = biller.getLogoUrl();
                if (logoUrl != null && !logoUrl.trim().isEmpty()) {
                    showLogoContainer();
                    ivLogo.clearColorFilter();
                    cardLogo.setCardBackgroundColor(ContextCompat.getColor(BillersActivity.this, R.color.white));
                    RemoteImageLoader.load(logoUrl, ivLogo, () -> showCategoryIcon(biller));
                } else {
                    showCategoryIcon(biller);
                }
            }

            private void showLogoContainer() {
                tvInitial.setVisibility(View.GONE);
                cardLogo.setVisibility(View.VISIBLE);
            }

            private void showCategoryIcon(BillerData biller) {
                ivLogo.setTag(null);
                showLogoContainer();
                cardLogo.setCardBackgroundColor(getCategoryColor(biller.getCategory()));
                ivLogo.setImageResource(getCategoryIcon(biller.getCategory()));
                ivLogo.setColorFilter(ContextCompat.getColor(BillersActivity.this, R.color.white));
            }
        }
    }
}
