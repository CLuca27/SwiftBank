package com.example.swiftbank.api.dto.request;

import com.google.gson.annotations.SerializedName;

public class ResetPinRequest {
    private String phone;
    private String email;
    private String code;

    @SerializedName("new_pin")
    private String newPin;

    public ResetPinRequest(String phone, String email, String code, String newPin) {
        this.phone = phone;
        this.email = email;
        this.code = code;
        this.newPin = newPin;
    }

    public static ResetPinRequest withPhone(String phone, String code, String newPin) {
        return new ResetPinRequest(phone, null, code, newPin);
    }

    public static ResetPinRequest withEmail(String email, String code, String newPin) {
        return new ResetPinRequest(null, email, code, newPin);
    }
}
