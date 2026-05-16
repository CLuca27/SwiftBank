package com.example.swiftbank.api.dto.response.data.success;

import com.google.gson.annotations.SerializedName;

public class BillPaymentResultData {
    @SerializedName("payment_id")
    private int paymentId;

    @SerializedName("biller_name")
    private String billerName;

    @SerializedName("client_code")
    private String clientCode;

    private String reference;

    private double amount;
    private String currency;
    private String status;

    public int getPaymentId() {
        return paymentId;
    }

    public String getBillerName() {
        return billerName;
    }

    public String getClientCode() {
        return clientCode;
    }

    public String getReference() {
        return reference;
    }

    public double getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getStatus() {
        return status;
    }
}
