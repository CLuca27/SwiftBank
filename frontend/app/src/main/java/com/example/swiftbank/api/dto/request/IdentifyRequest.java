package com.example.swiftbank.api.dto.request;

import com.google.gson.annotations.SerializedName;

public class IdentifyRequest {
    @SerializedName("email")
    private String email;

    @SerializedName("phone")
    private String phone;

    public IdentifyRequest(String email, String phone) {
        this.email = email;
        this.phone = phone;
    }


}
