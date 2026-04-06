package com.example.swiftbank.api.dto.request;

import com.google.gson.annotations.SerializedName;

public class VerifyOtpRequest {
    @SerializedName("phone")
    private String phone;

    @SerializedName("email")
    private String email;

    @SerializedName("code")
    private String code;

    @SerializedName("purpose")
    private String purpose;

    public VerifyOtpRequest(String phone, String email, String code, String purpose) {
        this.phone = phone;
        this.email = email;
        this.code = code;
        this.purpose = purpose;
    }
}
