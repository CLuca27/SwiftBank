package com.example.swiftbank.api.dto.response.data;

import com.google.gson.annotations.SerializedName;

public class TokenData {
    @SerializedName("access_token")
    private String accessToken;

    @SerializedName("refresh_token")
    private String refreshToken;

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }
}
