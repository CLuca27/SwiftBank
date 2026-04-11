package com.example.swiftbank.api.dto.request;

import com.google.gson.annotations.SerializedName;

public class AddAccountRequest {
    @SerializedName("currency")
    private String currency;

    public AddAccountRequest(String currency) {
        this.currency = currency;
    }
}
