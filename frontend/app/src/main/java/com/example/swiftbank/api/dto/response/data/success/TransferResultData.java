package com.example.swiftbank.api.dto.response.data.success;

import com.google.gson.annotations.SerializedName;

public class TransferResultData {
    @SerializedName("transfer_id")
    private int transferId;

    private String reference;

    @SerializedName("from_account")
    private FromAccount fromAccount;

    @SerializedName("to_iban")
    private String toIban;

    @SerializedName("beneficiary_name")
    private String beneficiaryName;

    @SerializedName("bank_name")
    private String bankName;

    private double amount;
    private String currency;

    @SerializedName("original_amount")
    private Double originalAmount;

    @SerializedName("original_currency")
    private String originalCurrency;

    @SerializedName("exchange_rate")
    private Double exchangeRate;

    @SerializedName("transfer_type")
    private String transferType;

    private String status;

    @SerializedName("created_at")
    private String createdAt;

    public int getTransferId() {
        return transferId;
    }

    public String getReference() {
        return reference;
    }

    public FromAccount getFromAccount() {
        return fromAccount;
    }

    public String getToIban() {
        return toIban;
    }

    public String getBeneficiaryName() {
        return beneficiaryName;
    }

    public String getBankName() {
        return bankName;
    }

    public double getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
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

    public String getTransferType() {
        return transferType;
    }

    public String getStatus() {
        return status;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public static class FromAccount {
        @SerializedName("account_id")
        private int accountId;

        private String iban;
        private String currency;

        @SerializedName("new_balance")
        private double newBalance;

        public int getAccountId() {
            return accountId;
        }

        public String getIban() {
            return iban;
        }

        public String getCurrency() {
            return currency;
        }

        public double getNewBalance() {
            return newBalance;
        }
    }
}
