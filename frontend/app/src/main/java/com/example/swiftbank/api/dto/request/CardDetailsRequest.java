package com.example.swiftbank.api.dto.request;

import com.google.gson.annotations.SerializedName;

public class CardDetailsRequest {
    @SerializedName("pin")
    private String pin;

    public CardDetailsRequest(String pin) {
        this.pin = pin;
    }

    public String getPin() { return pin; }
    public void setPin(String pin) { this.pin = pin; }
}
