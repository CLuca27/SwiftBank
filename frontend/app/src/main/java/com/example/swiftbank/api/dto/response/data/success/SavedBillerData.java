package com.example.swiftbank.api.dto.response.data.success;

import com.google.gson.annotations.SerializedName;

public class SavedBillerData {
    @SerializedName("saved_biller_id")
    private int savedBillerId;

    @SerializedName("user_id")
    private int userId;

    @SerializedName("biller_id")
    private int billerId;

    @SerializedName("client_code")
    private String clientCode;

    private String alias;

    @SerializedName("last_used_at")
    private String lastUsedAt;

    @SerializedName("created_at")
    private String createdAt;

    private BillerData billers;

    public int getSavedBillerId() {
        return savedBillerId;
    }

    public int getUserId() {
        return userId;
    }

    public int getBillerId() {
        return billerId;
    }

    public String getClientCode() {
        return clientCode;
    }

    public String getAlias() {
        return alias;
    }

    public String getLastUsedAt() {
        return lastUsedAt;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public BillerData getBiller() {
        return billers;
    }
}
