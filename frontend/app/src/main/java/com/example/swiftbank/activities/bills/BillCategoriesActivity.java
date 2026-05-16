package com.example.swiftbank.activities.bills;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.swiftbank.R;
import com.example.swiftbank.api.ApiClient;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.success.BillerCategoriesData;
import com.example.swiftbank.api.dto.response.data.success.BillerData;
import com.example.swiftbank.api.dto.response.data.success.BillersData;
import com.example.swiftbank.api.dto.response.data.success.SavedBillersData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class BillCategoriesActivity extends AppCompatActivity {

    private ImageView btnBack;
    private TextView tvSavedSubtitle, tvCategoriesTitle;
    private LinearLayout btnSavedBillers, loadingState;
    private RecyclerView rvCategories;

    private List<CategoryItem> categories = new ArrayList<>();
    private Map<String, Integer> categoryBillerCounts = new HashMap<>();

    private CategoriesAdapter categoriesAdapter;

    private ActivityResultLauncher<Intent> billersLauncher;
    private ActivityResultLauncher<Intent> savedBillersLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bill_categories);

        initViews();
        setupLaunchers();
        setupListeners();
        setupRecyclerViews();
        loadData();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        btnSavedBillers = findViewById(R.id.btnSavedBillers);
        tvSavedSubtitle = findViewById(R.id.tvSavedSubtitle);
        tvCategoriesTitle = findViewById(R.id.tvCategoriesTitle);
        loadingState = findViewById(R.id.loadingState);
        rvCategories = findViewById(R.id.rvCategories);
    }

    private void setupLaunchers() {
        billersLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        setResult(RESULT_OK, result.getData());
                        finish();
                    }
                }
        );

        savedBillersLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null && data.getBooleanExtra("saved_biller_deleted", false)) {
                            loadSavedBillers();
                            return;
                        }

                        setResult(RESULT_OK, result.getData());
                        finish();
                    }
                }
        );
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnSavedBillers.setOnClickListener(v -> openSavedBillers());
    }

    private void setupRecyclerViews() {
        categoriesAdapter = new CategoriesAdapter();
        rvCategories.setLayoutManager(new LinearLayoutManager(this));
        rvCategories.setAdapter(categoriesAdapter);
    }

    private void loadData() {
        loadSavedBillers();
        loadCategories();
    }

    private void loadSavedBillers() {
        ApiClient.getBillService().getSavedBillers()
                .enqueue(new Callback<ApiResponse<SavedBillersData>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<SavedBillersData>> call,
                                           Response<ApiResponse<SavedBillersData>> response) {
                        if (isFinishing() || isDestroyed()) return;

                        if (response.isSuccessful() && response.body() != null &&
                                response.body().getData() != null) {
                            int count = response.body().getData().getSavedBillers() != null ?
                                    response.body().getData().getSavedBillers().size() : 0;
                            tvSavedSubtitle.setText(count + " furnizor" + (count != 1 ? "i" : ""));
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<SavedBillersData>> call, Throwable t) {
                    }
                });
    }

    private void loadCategories() {
        ApiClient.getBillService().getBillerCategories()
                .enqueue(new Callback<ApiResponse<BillerCategoriesData>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<BillerCategoriesData>> call,
                                           Response<ApiResponse<BillerCategoriesData>> response) {
                        if (isFinishing() || isDestroyed()) return;

                        if (response.isSuccessful() && response.body() != null &&
                                response.body().getData() != null) {
                            List<String> categoryNames = response.body().getData().getCategories();
                            if (categoryNames != null) {
                                categories.clear();
                                categoryBillerCounts.clear();
                                for (String name : categoryNames) {
                                    categories.add(new CategoryItem(name, getCategoryDisplayName(name),
                                            getCategoryIcon(name), getCategoryColor(name)));
                                }
                                loadBillerCounts();
                            }
                        }

                        loadingState.setVisibility(View.GONE);
                        rvCategories.setVisibility(View.VISIBLE);
                        categoriesAdapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<BillerCategoriesData>> call, Throwable t) {
                        if (isFinishing() || isDestroyed()) return;
                        loadingState.setVisibility(View.GONE);
                    }
                });
    }

    private void loadBillerCounts() {
        for (CategoryItem category : categories) {
            ApiClient.getBillService().getBillers(category.key)
                    .enqueue(new Callback<ApiResponse<BillersData>>() {
                        @Override
                        public void onResponse(Call<ApiResponse<BillersData>> call,
                                               Response<ApiResponse<BillersData>> response) {
                            if (response.isSuccessful() && response.body() != null &&
                                    response.body().getData() != null &&
                                    response.body().getData().getBillers() != null) {
                                int count = 0;
                                for (BillerData biller : response.body().getData().getBillers()) {
                                    if (isBillerInCategory(biller, category.key)) {
                                        count++;
                                    }
                                }

                                categoryBillerCounts.put(category.key,
                                        count);
                                categoriesAdapter.notifyDataSetChanged();
                            }
                        }

                        @Override
                        public void onFailure(Call<ApiResponse<BillersData>> call, Throwable t) {
                        }
                    });
        }
    }

    private boolean isBillerInCategory(BillerData biller, String categoryKey) {
        return biller != null &&
                normalizeCategoryKey(biller.getCategory()).equals(normalizeCategoryKey(categoryKey));
    }

    private String normalizeCategoryKey(String key) {
        return key == null ? "" : key.trim().toLowerCase();
    }

    private String getCategoryDisplayName(String key) {
        if (key == null || key.isEmpty()) return "Plată factură";
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

    private void openBillers(String category, String displayName) {
        Intent intent = new Intent(this, BillersActivity.class);
        intent.putExtra("category", category);
        intent.putExtra("category_name", displayName);
        billersLauncher.launch(intent);
    }

    private void openSavedBillers() {
        Intent intent = new Intent(this, SavedBillersActivity.class);
        savedBillersLauncher.launch(intent);
    }

    // ==================== DATA CLASSES ====================

    static class CategoryItem {
        String key;
        String displayName;
        int iconRes;
        int color;

        CategoryItem(String key, String displayName, int iconRes, int color) {
            this.key = key;
            this.displayName = displayName;
            this.iconRes = iconRes;
            this.color = color;
        }
    }

    // ==================== ADAPTERS ====================

    class CategoriesAdapter extends RecyclerView.Adapter<CategoriesAdapter.ViewHolder> {

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_bill_category, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            holder.bind(categories.get(position));
        }

        @Override
        public int getItemCount() {
            return categories.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            LinearLayout iconContainer;
            ImageView ivIcon;
            TextView tvName, tvCount;

            ViewHolder(View itemView) {
                super(itemView);
                iconContainer = itemView.findViewById(R.id.iconContainer);
                ivIcon = itemView.findViewById(R.id.ivIcon);
                tvName = itemView.findViewById(R.id.tvName);
                tvCount = itemView.findViewById(R.id.tvCount);

                itemView.setOnClickListener(v -> {
                    CategoryItem item = categories.get(getAdapterPosition());
                    openBillers(item.key, item.displayName);
                });
            }

            void bind(CategoryItem category) {
                tvName.setText(category.displayName);
                ivIcon.setImageResource(category.iconRes);

                Integer count = categoryBillerCounts.get(category.key);
                if (count != null) {
                    tvCount.setText(count + " furnizor" + (count != 1 ? "i" : ""));
                } else {
                    tvCount.setText("");
                }

                iconContainer.getBackground().setTint(category.color);
            }
        }
    }
}
