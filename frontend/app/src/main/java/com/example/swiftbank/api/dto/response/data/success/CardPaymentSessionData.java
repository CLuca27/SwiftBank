package com.example.swiftbank.api.dto.response.data.success;

import com.google.gson.annotations.SerializedName;

public class CardPaymentSessionData {

    @SerializedName("session_id")
    private int sessionId;

    @SerializedName("status")
    private String status;

    @SerializedName("merchant_name")
    private String merchantName;

    @SerializedName("merchant_location")
    private String merchantLocation;

    @SerializedName("amount")
    private double amount;

    @SerializedName("currency")
    private String currency;

    @SerializedName("masked_card")
    private String maskedCard;

    @SerializedName("created_at")
    private String createdAt;

    @SerializedName("expires_at")
    private String expiresAt;

    @SerializedName("server_time")
    private String serverTime;

    @SerializedName("approved_at")
    private String approvedAt;

    @SerializedName("declined_at")
    private String declinedAt;

    @SerializedName("completed_at")
    private String completedAt;

    @SerializedName("decline_reason")
    private String declineReason;

    @SerializedName("merchant_logo_url")
    private String merchantLogoUrl;

    @SerializedName("category_name")
    private String categoryName;

    @SerializedName("category_icon")
    private String categoryIcon;

    public int getSessionId() {
        return sessionId;
    }

    public String getStatus() {
        return status;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public String getMerchantLocation() {
        return merchantLocation;
    }

    public double getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getMaskedCard() {
        return maskedCard;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getExpiresAt() {
        return expiresAt;
    }

    public String getServerTime() {
        return serverTime;
    }

    public String getApprovedAt() {
        return approvedAt;
    }

    public String getDeclinedAt() {
        return declinedAt;
    }

    public String getCompletedAt() {
        return completedAt;
    }

    public String getDeclineReason() {
        return declineReason;
    }

    public String getMerchantLogoUrl() {
        return merchantLogoUrl;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public String getCategoryIcon() {
        return categoryIcon;
    }
}
