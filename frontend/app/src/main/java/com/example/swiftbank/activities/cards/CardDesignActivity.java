package com.example.swiftbank.activities.cards;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.swiftbank.R;

import java.util.ArrayList;
import java.util.List;

public class CardDesignActivity extends AppCompatActivity {

    public static final String EXTRA_CURRENT_DESIGN = "current_design";
    public static final String EXTRA_SELECTED_DESIGN = "selected_design";
    public static final String EXTRA_USED_DESIGNS = "used_designs";

    private RecyclerView rvDesigns;
    private Button btnConfirm;
    private CardDesignAdapter adapter;
    private String selectedDesign = null;
    private List<String> usedDesigns = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_card_design);

        // Get used designs
        ArrayList<String> used = getIntent().getStringArrayListExtra(EXTRA_USED_DESIGNS);
        if (used != null) {
            usedDesigns.addAll(used);
        }

        initViews();
        setupRecyclerView();
    }

    private void initViews() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        rvDesigns = findViewById(R.id.rvDesigns);
        btnConfirm = findViewById(R.id.btnConfirm);

        btnConfirm.setOnClickListener(v -> {
            Intent result = new Intent();
            result.putExtra(EXTRA_SELECTED_DESIGN, selectedDesign);
            setResult(RESULT_OK, result);
            finish();
        });
    }

    private void setupRecyclerView() {
        // All available designs
        List<CardDesign> allDesigns = new ArrayList<>();
        allDesigns.add(new CardDesign("green", "Emerald Green", R.drawable.gradient_card_green));
        allDesigns.add(new CardDesign("blue", "Ocean Blue", R.drawable.gradient_card_blue));
        allDesigns.add(new CardDesign("purple", "Royal Purple", R.drawable.gradient_card_purple));
        allDesigns.add(new CardDesign("orange", "Sunset Orange", R.drawable.gradient_card_orange));
        allDesigns.add(new CardDesign("black", "Midnight Black", R.drawable.gradient_card_black));

        // Filter out used designs
        List<CardDesign> availableDesigns = new ArrayList<>();
        for (CardDesign design : allDesigns) {
            if (!usedDesigns.contains(design.id)) {
                availableDesigns.add(design);
            }
        }

        // Select first available design by default
        if (!availableDesigns.isEmpty()) {
            selectedDesign = availableDesigns.get(0).id;
        }

        adapter = new CardDesignAdapter(availableDesigns, selectedDesign, design -> {
            selectedDesign = design.id;
            adapter.setSelectedDesign(design.id);
        });

        rvDesigns.setLayoutManager(new LinearLayoutManager(this));
        rvDesigns.setAdapter(adapter);
    }

    // Data class for card design
    static class CardDesign {
        String id;
        String name;
        int gradientRes;

        CardDesign(String id, String name, int gradientRes) {
            this.id = id;
            this.name = name;
            this.gradientRes = gradientRes;
        }
    }

    // Adapter
    class CardDesignAdapter extends RecyclerView.Adapter<CardDesignAdapter.ViewHolder> {
        private final List<CardDesign> designs;
        private String selectedId;
        private final OnDesignClickListener listener;

        interface OnDesignClickListener {
            void onDesignClick(CardDesign design);
        }

        CardDesignAdapter(List<CardDesign> designs, String selectedId, OnDesignClickListener listener) {
            this.designs = designs;
            this.selectedId = selectedId;
            this.listener = listener;
        }

        void setSelectedDesign(String id) {
            String oldSelected = selectedId;
            selectedId = id;

            for (int i = 0; i < designs.size(); i++) {
                if (designs.get(i).id.equals(oldSelected) || designs.get(i).id.equals(id)) {
                    notifyItemChanged(i);
                }
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_card_design, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(designs.get(position));
        }

        @Override
        public int getItemCount() {
            return designs.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            View cardBackground;
            ImageView ivSelected;
            TextView tvDesignName;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                cardBackground = itemView.findViewById(R.id.cardBackground);
                ivSelected = itemView.findViewById(R.id.ivSelected);
                tvDesignName = itemView.findViewById(R.id.tvDesignName);

                itemView.setOnClickListener(v -> {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION && listener != null) {
                        listener.onDesignClick(designs.get(pos));
                    }
                });
            }

            void bind(CardDesign design) {
                cardBackground.setBackgroundResource(design.gradientRes);
                tvDesignName.setText(design.name);

                boolean isSelected = design.id.equals(selectedId);
                ivSelected.setVisibility(isSelected ? View.VISIBLE : View.GONE);

                // Scale effect for selected card
                float scale = isSelected ? 1.0f : 0.92f;
                itemView.setScaleX(scale);
                itemView.setScaleY(scale);
                itemView.setAlpha(isSelected ? 1.0f : 0.7f);
            }
        }
    }
}
