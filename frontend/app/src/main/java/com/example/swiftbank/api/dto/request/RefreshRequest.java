package com.example.swiftbank.api.dto.request;

import com.google.gson.annotations.SerializedName;

public class RefreshRequest {

    @SerializedName("refresh_token")
    private String refreshToken;

    @SerializedName("device_id")
    private String device_id;

    public RefreshRequest(String refreshToken, String device_id) {
        this.refreshToken = refreshToken;
        this.device_id = device_id;
    }


}
