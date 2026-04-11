package com.example.swiftbank.api.dto.response.data;

import com.google.gson.annotations.SerializedName;

public class ForgotPinData {

    @SerializedName("masked_phone")
    private String maskedPhone;

    public String getMaskedPhone() {
        return maskedPhone;
    }
}
