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

    @SerializedName("available_balance")
    private double availableBalance;

    @SerializedName("ledger_balance")
    private double ledgerBalance;

    @SerializedName("blocked_balance")
    private double blockedBalance;

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

    public double getAvailableBalance() {
        return availableBalance;
    }

    public double getLedgerBalance() {
        return ledgerBalance;
    }

    public double getBlockedBalance() {
        return blockedBalance;
    }
}
