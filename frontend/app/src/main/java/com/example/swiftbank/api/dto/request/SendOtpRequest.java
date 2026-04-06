package com.example.swiftbank.api.dto.request;

import com.google.gson.annotations.SerializedName;

public class SendOtpRequest {
    @SerializedName("phone")
    private String phone;
    @SerializedName("email")
    private String email;
    @SerializedName("purpose")
    private String purpose;

    public SendOtpRequest(String phone, String email, String purpose) {
        this.phone = phone;
        this.purpose = purpose;
        this.email = email;
    }

}
