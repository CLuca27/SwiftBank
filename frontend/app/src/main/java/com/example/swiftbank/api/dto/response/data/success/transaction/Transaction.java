package com.example.swiftbank.api.dto.response.data.success.transaction;

import com.google.gson.annotations.SerializedName;

public abstract class Transaction {

    @SerializedName("id")
    protected int id;

    @SerializedName("transaction_type")
    protected String transactionType;

    @SerializedName("account_id")
    protected int accountId;

    @SerializedName("title")
    protected String title;

    @SerializedName("subtitle")
    protected String subtitle;

    @SerializedName("amount")
    protected double amount;

    @SerializedName("currency")
    protected String currency;

    @SerializedName("status")
    protected String status;

    @SerializedName("created_at")
    protected String createdAt;

    @SerializedName("category_icon")
    protected String categoryIcon;

    @SerializedName("category_name")
    protected String categoryName;

    @SerializedName("merchant_logo_url")
    protected String merchantLogoUrl;

    @SerializedName("reference")
    protected String reference;

    @SerializedName("description")
    protected String description;

    public int getId() {
        return id;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public int getAccountId() {
        return accountId;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
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

    public String getCreatedAt() {
        return createdAt;
    }

    public String getCategoryIcon() {
        return categoryIcon;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public String getCategoryDisplayName() {
        return categoryName;
    }

    public String getMerchantLogoUrl() {
        return merchantLogoUrl;
    }

    public String getReference() {
        return reference;
    }

    public String getDescription() {
        return description;
    }

    public boolean isIncoming() {
        return amount > 0;
    }
}
