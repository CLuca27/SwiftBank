package com.example.swiftbank.api.dto.response.data.success.transaction;

import com.google.gson.annotations.SerializedName;

public class CardTransaction extends Transaction {

    @SerializedName("merchant_name")
    private String merchantName;

    @SerializedName("location")
    private String location;

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

    public String getMerchantName() {
        return merchantName;
    }

    public String getLocation() {
        return location;
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

    public boolean hasCurrencyConversion() {
        return originalCurrency != null && !originalCurrency.equals(currency);
    }
}
