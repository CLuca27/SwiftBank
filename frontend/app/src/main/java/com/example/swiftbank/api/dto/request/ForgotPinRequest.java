package com.example.swiftbank.api.dto.request;

public class ForgotPinRequest {
    private String phone;
    private String email;

    public ForgotPinRequest(String phone, String email) {
        this.phone = phone;
        this.email = email;
    }

    public static ForgotPinRequest withPhone(String phone) {
        return new ForgotPinRequest(phone, null);
    }

    public static ForgotPinRequest withEmail(String email) {
        return new ForgotPinRequest(null, email);
    }
}
