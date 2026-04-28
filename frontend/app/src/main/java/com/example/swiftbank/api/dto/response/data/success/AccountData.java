package com.example.swiftbank.api.dto.response.data.success;

import com.google.gson.annotations.SerializedName;

public class AccountData {

    @SerializedName("account_id")
    private int accountId;

    @SerializedName("iban")
    private String iban;

    @SerializedName("account_type")
    private String accountType;

    @SerializedName("currency")
    private String currency;

    @SerializedName("balance")
    private double balance;

    public int getAccountId() {
        return accountId;
    }

    public String getIban() {
        return iban;
    }

    public String getAccountType() {
        return accountType;
    }

    public String getCurrency() {
        return currency;
    }

    public double getBalance() {
        return balance;
    }
}
