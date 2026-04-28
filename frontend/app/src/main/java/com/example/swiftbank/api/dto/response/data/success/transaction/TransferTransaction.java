package com.example.swiftbank.api.dto.response.data.success.transaction;

import com.google.gson.annotations.SerializedName;

public class TransferTransaction extends Transaction {

    @SerializedName("beneficiary_name")
    private String beneficiaryName;

    @SerializedName("iban")
    private String iban;

    @SerializedName("bank_name")
    private String bankName;

    @SerializedName("reference")
    private String reference;

    @SerializedName("description")
    private String description;

    @SerializedName("transfer_type")
    private String transferType; // INTERNAL, OWN_BANK, EXTERNAL

    @SerializedName("original_amount")
    private Double originalAmount;

    @SerializedName("original_currency")
    private String originalCurrency;

    @SerializedName("exchange_rate")
    private Double exchangeRate;

    @SerializedName("sender_photo")
    private String senderPhoto;

    public String getBeneficiaryName() {
        return beneficiaryName;
    }

    public String getIban() {
        return iban;
    }

    public String getBankName() {
        return bankName;
    }

    public String getReference() {
        return reference;
    }

    public String getDescription() {
        return description;
    }

    public String getTransferType() {
        return transferType;
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

    public boolean isInternal() {
        return "INTERNAL".equals(transferType);
    }

    public boolean isExternal() {
        return "EXTERNAL".equals(transferType);
    }

    public boolean hasCurrencyConversion() {
        return originalCurrency != null && !originalCurrency.equals(currency);
    }

    public String getSenderPhoto() {
        return senderPhoto;
    }
}
