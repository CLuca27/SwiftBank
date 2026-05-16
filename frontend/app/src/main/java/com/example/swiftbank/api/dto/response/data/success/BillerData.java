package com.example.swiftbank.api.dto.response.data.success;

import com.google.gson.annotations.SerializedName;

public class BillerData {
    @SerializedName("biller_id")
    private int billerId;

    private String name;
    private String category;

    @SerializedName("account_format")
    private String accountFormat;

    @SerializedName("logo_url")
    private String logoUrl;

    private String domain;

    private String status;

    public int getBillerId() {
        return billerId;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public String getAccountFormat() {
        return accountFormat;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public String getDomain() {
        return domain;
    }

    public String getStatus() {
        return status;
    }
}
