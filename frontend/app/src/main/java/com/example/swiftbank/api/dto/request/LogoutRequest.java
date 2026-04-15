package com.example.swiftbank.api.dto.request;

import com.google.gson.annotations.SerializedName;

public class LogoutRequest {

    @SerializedName("refresh_token")
    private String refreshToken;

    @SerializedName("device_id")
    private String deviceId;

    public LogoutRequest(String refreshToken, String deviceId) {
        this.refreshToken = refreshToken;
        this.deviceId = deviceId;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getDeviceId() {
        return deviceId;
    }
}
