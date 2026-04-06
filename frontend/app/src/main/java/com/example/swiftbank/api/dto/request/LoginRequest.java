package com.example.swiftbank.api.dto.request;

import com.google.gson.annotations.SerializedName;

public class LoginRequest {
    @SerializedName("phone")
    private String phone;

    @SerializedName("email")
    private String email;

    @SerializedName("password")
    private String password;

    @SerializedName("device_id")
    private String deviceId;

    @SerializedName("device_name")
    private String deviceName;

    public LoginRequest(String phone, String email, String password, String deviceId, String deviceName){
        this.phone = phone;
        this.email = email;
        this.password = password;
        this.deviceId = deviceId;
        this.deviceName = deviceName;
    }
}
