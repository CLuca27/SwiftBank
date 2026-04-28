package com.example.swiftbank.api.dto.response.data.success;

import com.google.gson.annotations.SerializedName;

public class CardDetailsData {
    @SerializedName("card")
    private CardData card;

    public CardData getCard() { return card; }
}
