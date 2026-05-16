package com.example.swiftbank.activities.transfer;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.swiftbank.R;
import com.example.swiftbank.api.ApiClient;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.example.swiftbank.api.dto.response.ApiErrorResponse;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.error.ErrorParser;
import com.example.swiftbank.api.dto.response.data.success.BeneficiariesData;
import com.example.swiftbank.api.dto.response.data.success.BeneficiaryData;
import com.example.swiftbank.utils.SwiftBankDialog;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class BeneficiariesActivity extends AppCompatActivity {

    private ImageView btnBack;
    private EditText etSearch;
    private FloatingActionButton btnAddNew;
    private LinearLayout loadingState, emptyState;
    private RecyclerView rvBeneficiaries;

    private List<BeneficiaryData> allBeneficiaries = new ArrayList<>();
    private List<BeneficiaryData> filteredBeneficiaries = new ArrayList<>();
    private BeneficiaryAdapter adapter;

    private ActivityResultLauncher<Intent> addBeneficiaryLauncher;
    private ActivityResultLauncher<Intent> transferLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_beneficiaries);

        initViews();
        setupListeners();
        setupLaunchers();
        loadBeneficiaries();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        etSearch = findViewById(R.id.etSearch);
        btnAddNew = findViewById(R.id.btnAddNew);
        loadingState = findViewById(R.id.loadingState);
        emptyState = findViewById(R.id.emptyState);
        rvBeneficiaries = findViewById(R.id.rvBeneficiaries);

        rvBeneficiaries.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BeneficiaryAdapter();
        rvBeneficiaries.setAdapter(adapter);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnAddNew.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddBeneficiaryActivity.class);
            addBeneficiaryLauncher.launch(intent);
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                filterBeneficiaries(s.toString());
            }
        });
    }

    private void setupLaunchers() {
        addBeneficiaryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        // Beneficiary added, open transfer screen
                        String iban = result.getData().getStringExtra("iban");
                        String name = result.getData().getStringExtra("name");
                        String bankName = result.getData().getStringExtra("bank_name");
                        int beneficiaryId = result.getData().getIntExtra("beneficiary_id", -1);

                        openTransferScreen(beneficiaryId, iban, name, bankName, null);
                    }
                }
        );

        transferLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        // Transfer completed
                        setResult(RESULT_OK, result.getData());
                        finish();
                    }
                }
        );
    }

    private void loadBeneficiaries() {
        showLoading();

        ApiClient.getTransferService().getBeneficiaries()
                .enqueue(new Callback<ApiResponse<BeneficiariesData>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<BeneficiariesData>> call, Response<ApiResponse<BeneficiariesData>> response) {
                        if (isFinishing() || isDestroyed()) return;

                        if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                            allBeneficiaries = new ArrayList<>(response.body().getData().getBeneficiaries());
                            filteredBeneficiaries = new ArrayList<>(allBeneficiaries);
                            adapter.notifyDataSetChanged();

                            if (allBeneficiaries.isEmpty()) {
                                showEmpty();
                            } else {
                                showContent();
                            }
                        } else {
                            showEmpty();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<BeneficiariesData>> call, Throwable t) {
                        if (isFinishing() || isDestroyed()) return;
                        showEmpty();
                    }
                });
    }

    private void filterBeneficiaries(String query) {
        filteredBeneficiaries.clear();
        String lowerQuery = query.toLowerCase().trim();

        if (lowerQuery.isEmpty()) {
            filteredBeneficiaries.addAll(allBeneficiaries);
        } else {
            for (BeneficiaryData b : allBeneficiaries) {
                if (b.getName().toLowerCase().contains(lowerQuery) ||
                        b.getIban().toLowerCase().contains(lowerQuery) ||
                        (b.getBankName() != null && b.getBankName().toLowerCase().contains(lowerQuery))) {
                    filteredBeneficiaries.add(b);
                }
            }
        }

        adapter.notifyDataSetChanged();

        if (filteredBeneficiaries.isEmpty() && !allBeneficiaries.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            rvBeneficiaries.setVisibility(View.GONE);
        } else if (!filteredBeneficiaries.isEmpty()) {
            emptyState.setVisibility(View.GONE);
            rvBeneficiaries.setVisibility(View.VISIBLE);
        }
    }

    private void openTransferScreen(int beneficiaryId, String iban, String name, String bankName, String profilePhoto) {
        Intent intent = new Intent(this, TransferAmountActivity.class);
        intent.putExtra("beneficiary_id", beneficiaryId);
        intent.putExtra("iban", iban);
        intent.putExtra("name", name);
        intent.putExtra("bank_name", bankName);
        if (profilePhoto != null) {
            intent.putExtra("profile_photo", profilePhoto);
        }
        transferLauncher.launch(intent);
    }

    private void confirmDeleteBeneficiary(BeneficiaryData beneficiary, int position) {
        SwiftBankDialog.showConfirmDialog(
                this,
                "Ștergi beneficiarul?",
                "Beneficiarul " + beneficiary.getName() + " va fi eliminat din lista ta.",
                "Șterge",
                "Renunță",
                () -> deleteBeneficiary(beneficiary, position)
        );
    }

    private void deleteBeneficiary(BeneficiaryData beneficiary, int position) {
        ApiClient.getTransferService().deleteBeneficiary(beneficiary.getBeneficiaryId())
                .enqueue(new Callback<ApiResponse<Void>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<Void>> call,
                                           Response<ApiResponse<Void>> response) {
                        if (isFinishing() || isDestroyed()) return;

                        if (response.isSuccessful()) {
                            removeBeneficiary(beneficiary.getBeneficiaryId(), position);
                            setResult(RESULT_OK);
                        } else {
                            SwiftBankDialog.showErrorDialog(
                                    BeneficiariesActivity.this,
                                    getDeleteErrorMessage(response)
                            );
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                        if (isFinishing() || isDestroyed()) return;
                        SwiftBankDialog.showErrorDialog(
                                BeneficiariesActivity.this,
                                "Nu am putut șterge beneficiarul. Verifică conexiunea și încearcă din nou."
                        );
                    }
                });
    }

    private void removeBeneficiary(int beneficiaryId, int fallbackPosition) {
        removeBeneficiaryFromList(allBeneficiaries, beneficiaryId);

        int removedIndex = findBeneficiaryIndex(filteredBeneficiaries, beneficiaryId);
        if (removedIndex == -1 && fallbackPosition >= 0 && fallbackPosition < filteredBeneficiaries.size()) {
            removedIndex = fallbackPosition;
        }

        if (removedIndex != -1) {
            filteredBeneficiaries.remove(removedIndex);
            adapter.notifyItemRemoved(removedIndex);
        } else {
            adapter.notifyDataSetChanged();
        }

        if (allBeneficiaries.isEmpty()) {
            showEmpty();
        } else if (filteredBeneficiaries.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            rvBeneficiaries.setVisibility(View.GONE);
        } else {
            showContent();
        }
    }

    private void removeBeneficiaryFromList(List<BeneficiaryData> beneficiaries, int beneficiaryId) {
        int index = findBeneficiaryIndex(beneficiaries, beneficiaryId);
        if (index != -1) {
            beneficiaries.remove(index);
        }
    }

    private int findBeneficiaryIndex(List<BeneficiaryData> beneficiaries, int beneficiaryId) {
        for (int i = 0; i < beneficiaries.size(); i++) {
            if (beneficiaries.get(i).getBeneficiaryId() == beneficiaryId) {
                return i;
            }
        }

        return -1;
    }

    private String getDeleteErrorMessage(Response<?> response) {
        ApiErrorResponse errorResponse = ErrorParser.parseError(response);
        if (errorResponse != null && errorResponse.getError() != null &&
                errorResponse.getError().getMessage() != null) {
            return errorResponse.getError().getMessage();
        }

        return "Nu am putut șterge beneficiarul. Încearcă din nou.";
    }

    private void showLoading() {
        loadingState.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.GONE);
        rvBeneficiaries.setVisibility(View.GONE);
    }

    private void showEmpty() {
        loadingState.setVisibility(View.GONE);
        emptyState.setVisibility(View.VISIBLE);
        rvBeneficiaries.setVisibility(View.GONE);
    }

    private void showContent() {
        loadingState.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
        rvBeneficiaries.setVisibility(View.VISIBLE);
    }

    // ==================== ADAPTER ====================

    class BeneficiaryAdapter extends RecyclerView.Adapter<BeneficiaryAdapter.ViewHolder> {

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_beneficiary, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            holder.bind(filteredBeneficiaries.get(position));
        }

        @Override
        public int getItemCount() {
            return filteredBeneficiaries.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvInitial, tvName, tvBank, tvIban;
            CardView cardPhoto;
            ImageView ivPhoto, btnDelete;

            ViewHolder(View itemView) {
                super(itemView);
                tvInitial = itemView.findViewById(R.id.tvInitial);
                tvName = itemView.findViewById(R.id.tvName);
                tvBank = itemView.findViewById(R.id.tvBank);
                tvIban = itemView.findViewById(R.id.tvIban);
                cardPhoto = itemView.findViewById(R.id.cardPhoto);
                ivPhoto = itemView.findViewById(R.id.ivPhoto);
                btnDelete = itemView.findViewById(R.id.btnDelete);

                itemView.setOnClickListener(v -> {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        BeneficiaryData b = filteredBeneficiaries.get(position);
                        openTransferScreen(b.getBeneficiaryId(), b.getIban(), b.getName(), b.getBankName(), b.getProfilePhoto());
                    }
                });

                btnDelete.setOnClickListener(v -> {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        confirmDeleteBeneficiary(filteredBeneficiaries.get(position), position);
                    }
                });
            }

            void bind(BeneficiaryData beneficiary) {
                String name = beneficiary.getName();
                tvName.setText(name);
                tvBank.setText(beneficiary.getBankName() != null ? beneficiary.getBankName() : "");
                tvIban.setText(beneficiary.getIban());

                String base64Photo = beneficiary.getProfilePhoto();
                if (base64Photo != null && !base64Photo.isEmpty()) {
                    Bitmap bitmap = decodeBase64Photo(base64Photo);
                    if (bitmap != null) {
                        tvInitial.setVisibility(View.GONE);
                        cardPhoto.setVisibility(View.VISIBLE);
                        ivPhoto.setImageBitmap(bitmap);
                    } else {
                        showInitial(name);
                    }
                } else {
                    showInitial(name);
                }
            }

            private void showInitial(String name) {
                tvInitial.setVisibility(View.VISIBLE);
                cardPhoto.setVisibility(View.GONE);
                tvInitial.setText(name.substring(0, 1).toUpperCase());
            }

            private Bitmap decodeBase64Photo(String base64Photo) {
                try {
                    String base64Data = base64Photo;
                    if (base64Photo.contains(",")) {
                        base64Data = base64Photo.substring(base64Photo.indexOf(",") + 1);
                    }
                    byte[] decodedBytes = Base64.decode(base64Data, Base64.DEFAULT);
                    return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                } catch (Exception e) {
                    return null;
                }
            }
        }
    }
}
