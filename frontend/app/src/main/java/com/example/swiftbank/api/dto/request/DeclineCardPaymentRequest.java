package com.example.swiftbank.api.dto.request;

import com.google.gson.annotations.SerializedName;

public class DeclineCardPaymentRequest {

    @SerializedName("reason")
    private final String reason;

    public DeclineCardPaymentRequest(String reason) {
        this.reason = reason;
    }
}
