package com.example.swiftbank.api.dto.request;

import com.google.gson.annotations.SerializedName;

public class ChangePinRequest {

    @SerializedName("current_pin")
    private String currentPin;

    @SerializedName("new_pin")
    private String newPin;

    public ChangePinRequest(String currentPin, String newPin) {
        this.currentPin = currentPin;
        this.newPin = newPin;
    }

    public String getCurrentPin() {
        return currentPin;
    }

    public String getNewPin() {
        return newPin;
    }
}
