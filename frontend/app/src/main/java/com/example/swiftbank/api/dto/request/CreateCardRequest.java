package com.example.swiftbank.api.dto.request;

import com.google.gson.annotations.SerializedName;

public class CreateCardRequest {
    @SerializedName("card_design")
    private String cardDesign;

    public CreateCardRequest(String cardDesign) {
        this.cardDesign = cardDesign;
    }

    public String getCardDesign() { return cardDesign; }
}
