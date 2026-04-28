package com.example.swiftbank.api.dto.response.data.success;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class CardsData {
    @SerializedName("cards")
    private List<CardData> cards;

    public List<CardData> getCards() { return cards; }
}
