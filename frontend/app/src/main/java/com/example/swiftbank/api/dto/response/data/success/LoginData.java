package com.example.swiftbank.api.dto.response.data.success;

import com.example.swiftbank.api.dto.response.data.error.UserData;
import com.google.gson.annotations.SerializedName;

public class LoginData {

    @SerializedName("access_token")
    private String accessToken;

    @SerializedName("refresh_token")
    private String refreshToken;

    @SerializedName("user")
    private UserData user;

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public UserData getUser() {
        return user;
    }
}

