package com.example.swiftbank.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.swiftbank.R;
import com.example.swiftbank.api.dto.response.data.PlaceSuggestionData;

import java.util.ArrayList;
import java.util.List;

public class AddressSuggestionAdapter extends RecyclerView.Adapter<AddressSuggestionAdapter.ViewHolder> {

    private List<PlaceSuggestionData> suggestions = new ArrayList<>();
    private OnSuggestionClickListener listener;

    public interface OnSuggestionClickListener {
        void onSuggestionClick(PlaceSuggestionData suggestion);
    }

    public AddressSuggestionAdapter(OnSuggestionClickListener listener) {
        this.listener = listener;
    }

    public void setSuggestions(List<PlaceSuggestionData> suggestions) {
        this.suggestions = suggestions != null ? suggestions : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void clear() {
        this.suggestions.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_address_suggestion, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PlaceSuggestionData suggestion = suggestions.get(position);
        holder.tvAddress.setText(suggestion.getDescription());

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSuggestionClick(suggestion);
            }
        });
    }

    @Override
    public int getItemCount() {
        return suggestions.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvAddress;

        ViewHolder(View itemView) {
            super(itemView);
            tvAddress = itemView.findViewById(R.id.tvAddress);
        }
    }
}