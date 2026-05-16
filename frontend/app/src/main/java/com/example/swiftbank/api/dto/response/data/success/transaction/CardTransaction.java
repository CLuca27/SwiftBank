package com.example.swiftbank.api.dto.response.data.success.transaction;

import com.google.gson.annotations.SerializedName;

public class CardTransaction extends Transaction {

    @SerializedName("merchant_name")
    private String merchantName;

    @SerializedName("session_id")
    private Integer sessionId;

    @SerializedName("location")
    private String location;

    @SerializedName("masked_card")
    private String maskedCard;

    @SerializedName("card_number_masked")
    private String cardNumberMasked;

    @SerializedName("original_amount")
    private Double originalAmount;

    @SerializedName("original_currency")
    private String originalCurrency;

    @SerializedName("exchange_rate")
    private Double exchangeRate;

    @SerializedName("settlement_date")
    private String settlementDate;

    @SerializedName("expires_at")
    private String expiresAt;

    public String getMerchantName() {
        return merchantName;
    }

    public Integer getSessionId() {
        return sessionId;
    }

    public String getLocation() {
        return location;
    }

    public String getMaskedCard() {
        return maskedCard;
    }

    public String getCardNumberMasked() {
        return cardNumberMasked;
    }

    public Double getOriginalAmount() {
        return originalAmount;
    }

    public String getOriginalCurrency() {
        return originalCurrency;
    }

    public Double getExchangeRate() {
        return exchangeRate;
    }

    public String getSettlementDate() {
        return settlementDate;
    }

    public String getExpiresAt() {
        return expiresAt;
    }

    public boolean isPendingApproval() {
        return "CARD_PENDING_APPROVAL".equals(transactionType) || "PENDING_APPROVAL".equals(status);
    }

    public boolean hasCurrencyConversion() {
        return originalCurrency != null && !originalCurrency.equals(currency);
    }
}
