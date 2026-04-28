package com.example.swiftbank.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.swiftbank.R;
import com.example.swiftbank.api.dto.response.data.success.CardData;

import java.util.ArrayList;
import java.util.List;

public class CardAdapter extends RecyclerView.Adapter<CardAdapter.CardViewHolder> {

    private List<CardData> cards = new ArrayList<>();
    private OnCardClickListener listener;

    public interface OnCardClickListener {
        void onCardClick(CardData card);
    }

    public CardAdapter(OnCardClickListener listener) {
        this.listener = listener;
    }

    public void setCards(List<CardData> cards) {
        this.cards = cards != null ? cards : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_card_compact, parent, false);
        return new CardViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CardViewHolder holder, int position) {
        CardData card = cards.get(position);
        holder.bind(card);
    }

    @Override
    public int getItemCount() {
        return cards.size();
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

    private String getDesignName(String design) {
        if (design == null) return "Virtual Green";

        switch (design.toLowerCase()) {
            case "blue":
                return "Virtual Blue";
            case "purple":
                return "Virtual Purple";
            case "orange":
                return "Virtual Orange";
            case "black":
                return "Virtual Black";
            case "green":
            default:
                return "Virtual Green";
        }
    }

    class CardViewHolder extends RecyclerView.ViewHolder {
        private View cardPreviewBackground;
        private View blockedIndicator;
        private TextView tvCardName;
        private TextView tvCardInfo;
        private TextView tvStatus;

        public CardViewHolder(@NonNull View itemView) {
            super(itemView);
            cardPreviewBackground = itemView.findViewById(R.id.cardPreviewBackground);
            blockedIndicator = itemView.findViewById(R.id.blockedIndicator);
            tvCardName = itemView.findViewById(R.id.tvCardName);
            tvCardInfo = itemView.findViewById(R.id.tvCardInfo);
            tvStatus = itemView.findViewById(R.id.tvStatus);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onCardClick(cards.get(pos));
                }
            });
        }

        public void bind(CardData card) {
            cardPreviewBackground.setBackgroundResource(getGradientForDesign(card.getCardDesign()));
            // Card brand is now a static VISA logo image
            tvCardName.setText(getDesignName(card.getCardDesign()));

            String lastFour = "••••";
            if (card.getCardNumberMasked() != null && card.getCardNumberMasked().length() >= 4) {
                lastFour = "••" + card.getCardNumberMasked().substring(card.getCardNumberMasked().length() - 4);
            }
            String info = lastFour + ", " + card.getFormattedExpiry();
            tvCardInfo.setText(info);

            boolean blocked = card.isBlocked();
            blockedIndicator.setVisibility(blocked ? View.VISIBLE : View.GONE);
            tvStatus.setVisibility(blocked ? View.VISIBLE : View.GONE);
            tvStatus.setText("Blocat");
        }
    }
}
