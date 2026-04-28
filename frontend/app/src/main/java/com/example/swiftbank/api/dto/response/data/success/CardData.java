package com.example.swiftbank.api.dto.response.data.success;

import com.google.gson.annotations.SerializedName;

public class CardData {
    @SerializedName("card_id")
    private int cardId;

    @SerializedName("card_number")
    private String cardNumber;

    @SerializedName("card_number_masked")
    private String cardNumberMasked;

    @SerializedName("card_number_formatted")
    private String cardNumberFormatted;

    @SerializedName("card_holder_name")
    private String cardHolderName;

    @SerializedName("expiry_date")
    private String expiryDate;

    @SerializedName("cvv")
    private String cvv;

    @SerializedName("card_brand")
    private String cardBrand;

    @SerializedName("card_design")
    private String cardDesign;

    @SerializedName("status")
    private String status;

    @SerializedName("created_at")
    private String createdAt;

    public int getCardId() { return cardId; }
    public String getCardNumber() { return cardNumber; }
    public String getCardNumberMasked() { return cardNumberMasked; }
    public String getCardNumberFormatted() { return cardNumberFormatted; }
    public String getCardHolderName() { return cardHolderName; }
    public String getExpiryDate() { return expiryDate; }
    public String getCvv() { return cvv; }
    public String getCardBrand() { return cardBrand; }
    public String getCardDesign() { return cardDesign; }
    public String getStatus() { return status; }
    public String getCreatedAt() { return createdAt; }

    public boolean isBlocked() {
        return "blocked".equals(status);
    }

    public String getFormattedExpiry() {
        if (expiryDate == null || expiryDate.length() < 7) return "";
        String[] parts = expiryDate.split("-");
        if (parts.length >= 2) {
            return parts[1] + "/" + parts[0].substring(2);
        }
        return expiryDate;
    }
}
