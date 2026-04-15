package com.example.swiftbank.api.dto.request;

import com.google.gson.annotations.SerializedName;

public class CreateTransferRequest {
    @SerializedName("from_account_id")
    private int fromAccountId;

    @SerializedName("to_iban")
    private String toIban;

    @SerializedName("beneficiary_name")
    private String beneficiaryName;

    private double amount;

    private String description;

    public CreateTransferRequest(int fromAccountId, String toIban, String beneficiaryName, double amount, String description) {
        this.fromAccountId = fromAccountId;
        this.toIban = toIban;
        this.beneficiaryName = beneficiaryName;
        this.amount = amount;
        this.description = description;
    }

    public int getFromAccountId() {
        return fromAccountId;
    }

    public String getToIban() {
        return toIban;
    }

    public String getBeneficiaryName() {
        return beneficiaryName;
    }

    public double getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }
}
