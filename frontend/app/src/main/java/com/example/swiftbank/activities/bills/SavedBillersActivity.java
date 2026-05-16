package com.example.swiftbank.activities.bills;

import android.content.Intent;
import android.content.res.ColorStateList;
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
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.swiftbank.R;
import com.example.swiftbank.api.ApiClient;
import com.example.swiftbank.api.dto.response.ApiErrorResponse;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.error.ErrorParser;
import com.example.swiftbank.api.dto.response.data.success.SavedBillerData;
import com.example.swiftbank.api.dto.response.data.success.SavedBillersData;
import com.example.swiftbank.utils.RemoteImageLoader;
import com.example.swiftbank.utils.SwiftBankDialog;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SavedBillersActivity extends AppCompatActivity {

    private ImageView btnBack;
    private LinearLayout loadingState, emptyState;
    private RecyclerView rvSavedBillers;

    private List<SavedBillerData> savedBillers = new ArrayList<>();
    private SavedBillersAdapter adapter;
    private ActivityResultLauncher<Intent> paymentLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_saved_billers);

        initViews();
        setupLauncher();
        setupListeners();
        setupRecyclerView();
        loadSavedBillers();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        loadingState = findViewById(R.id.loadingState);
        emptyState = findViewById(R.id.emptyState);
        rvSavedBillers = findViewById(R.id.rvSavedBillers);
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
    }

    private void setupRecyclerView() {
        adapter = new SavedBillersAdapter();
        rvSavedBillers.setLayoutManager(new LinearLayoutManager(this));
        rvSavedBillers.setAdapter(adapter);
    }

    private void loadSavedBillers() {
        showLoading();

        ApiClient.getBillService().getSavedBillers()
                .enqueue(new Callback<ApiResponse<SavedBillersData>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<SavedBillersData>> call,
                                           Response<ApiResponse<SavedBillersData>> response) {
                        if (isFinishing() || isDestroyed()) return;

                        if (response.isSuccessful() && response.body() != null &&
                                response.body().getData() != null &&
                                response.body().getData().getSavedBillers() != null) {
                            savedBillers = new ArrayList<>(response.body().getData().getSavedBillers());
                            adapter.notifyDataSetChanged();

                            if (savedBillers.isEmpty()) {
                                showEmpty();
                            } else {
                                showContent();
                            }
                        } else {
                            showEmpty();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<SavedBillersData>> call, Throwable t) {
                        if (isFinishing() || isDestroyed()) return;
                        showEmpty();
                    }
                });
    }

    private void showLoading() {
        loadingState.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.GONE);
        rvSavedBillers.setVisibility(View.GONE);
    }

    private void showEmpty() {
        loadingState.setVisibility(View.GONE);
        emptyState.setVisibility(View.VISIBLE);
        rvSavedBillers.setVisibility(View.GONE);
    }

    private void showContent() {
        loadingState.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
        rvSavedBillers.setVisibility(View.VISIBLE);
    }

    private void openPayment(SavedBillerData savedBiller) {
        Intent intent = new Intent(this, BillPaymentActivity.class);
        intent.putExtra("saved_biller_id", savedBiller.getSavedBillerId());
        intent.putExtra("biller_id", savedBiller.getBillerId());
        intent.putExtra("biller_name", savedBiller.getBiller() != null ?
                savedBiller.getBiller().getName() : "");
        intent.putExtra("client_code", savedBiller.getClientCode());
        intent.putExtra("biller_logo_url", savedBiller.getBiller() != null ?
                savedBiller.getBiller().getLogoUrl() : null);
        intent.putExtra("biller_category", savedBiller.getBiller() != null ?
                savedBiller.getBiller().getCategory() : null);
        paymentLauncher.launch(intent);
    }

    private void confirmDeleteSavedBiller(SavedBillerData savedBiller, int position) {
        String name = savedBiller.getBiller() != null ?
                savedBiller.getBiller().getName() : "furnizorul";

        SwiftBankDialog.showConfirmDialog(
                this,
                "Ștergi furnizorul?",
                "Furnizorul " + name + " va fi eliminat din lista ta salvată.",
                "Șterge",
                "Renunță",
                () -> deleteSavedBiller(savedBiller, position)
        );
    }

    private void deleteSavedBiller(SavedBillerData savedBiller, int position) {
        ApiClient.getBillService().deleteSavedBiller(savedBiller.getSavedBillerId())
                .enqueue(new Callback<ApiResponse<Void>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<Void>> call,
                                           Response<ApiResponse<Void>> response) {
                        if (isFinishing() || isDestroyed()) return;

                        if (response.isSuccessful()) {
                            removeSavedBiller(savedBiller.getSavedBillerId(), position);
                            Intent resultIntent = new Intent();
                            resultIntent.putExtra("saved_biller_deleted", true);
                            setResult(RESULT_OK, resultIntent);
                        } else {
                            SwiftBankDialog.showErrorDialog(
                                    SavedBillersActivity.this,
                                    getDeleteErrorMessage(response)
                            );
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                        if (isFinishing() || isDestroyed()) return;
                        SwiftBankDialog.showErrorDialog(
                                SavedBillersActivity.this,
                                "Nu am putut șterge furnizorul. Verifică conexiunea și încearcă din nou."
                        );
                    }
                });
    }

    private void removeSavedBiller(int savedBillerId, int fallbackPosition) {
        int removeIndex = -1;
        for (int i = 0; i < savedBillers.size(); i++) {
            if (savedBillers.get(i).getSavedBillerId() == savedBillerId) {
                removeIndex = i;
                break;
            }
        }

        if (removeIndex == -1 && fallbackPosition >= 0 && fallbackPosition < savedBillers.size()) {
            removeIndex = fallbackPosition;
        }

        if (removeIndex != -1) {
            savedBillers.remove(removeIndex);
            adapter.notifyItemRemoved(removeIndex);
        } else {
            adapter.notifyDataSetChanged();
        }

        if (savedBillers.isEmpty()) {
            showEmpty();
        } else {
            showContent();
        }
    }

    private String getDeleteErrorMessage(Response<?> response) {
        ApiErrorResponse errorResponse = ErrorParser.parseError(response);
        if (errorResponse != null && errorResponse.getError() != null &&
                errorResponse.getError().getMessage() != null) {
            return errorResponse.getError().getMessage();
        }

        return "Nu am putut șterge furnizorul. Încearcă din nou.";
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

    class SavedBillersAdapter extends RecyclerView.Adapter<SavedBillersAdapter.ViewHolder> {

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_saved_biller, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            holder.bind(savedBillers.get(position));
        }

        @Override
        public int getItemCount() {
            return savedBillers.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            LinearLayout iconContainer;
            ImageView ivIcon, btnDelete;
            TextView tvName, tvClientCode;

            ViewHolder(View itemView) {
                super(itemView);
                iconContainer = itemView.findViewById(R.id.iconContainer);
                ivIcon = itemView.findViewById(R.id.ivIcon);
                btnDelete = itemView.findViewById(R.id.btnDelete);
                tvName = itemView.findViewById(R.id.tvName);
                tvClientCode = itemView.findViewById(R.id.tvClientCode);

                itemView.setOnClickListener(v -> {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        openPayment(savedBillers.get(position));
                    }
                });

                btnDelete.setOnClickListener(v -> {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        confirmDeleteSavedBiller(savedBillers.get(position), position);
                    }
                });
            }

            void bind(SavedBillerData savedBiller) {
                String name = savedBiller.getBiller() != null ?
                        savedBiller.getBiller().getName() : "Furnizor";
                String category = savedBiller.getBiller() != null ?
                        savedBiller.getBiller().getCategory() : null;

                tvName.setText(name);
                tvClientCode.setText("Cod client: " + savedBiller.getClientCode());

                String logoUrl = savedBiller.getBiller() != null ?
                        savedBiller.getBiller().getLogoUrl() : null;
                if (logoUrl != null && !logoUrl.trim().isEmpty()) {
                    showLogo(logoUrl, category);
                } else {
                    showCategoryIcon(category);
                }
            }

            private void showLogo(String logoUrl, String category) {
                iconContainer.getBackground().setTint(ContextCompat.getColor(SavedBillersActivity.this, R.color.white));
                ImageViewCompat.setImageTintList(ivIcon, null);
                ivIcon.clearColorFilter();
                ivIcon.setImageDrawable(null);
                RemoteImageLoader.load(logoUrl, ivIcon, () -> showCategoryIcon(category));
            }

            private void showCategoryIcon(String category) {
                ivIcon.setTag(null);
                ivIcon.setImageResource(getCategoryIcon(category));
                iconContainer.getBackground().setTint(getCategoryColor(category));
                ImageViewCompat.setImageTintList(
                        ivIcon,
                        ColorStateList.valueOf(ContextCompat.getColor(SavedBillersActivity.this, R.color.white)));
            }
        }
    }
}
