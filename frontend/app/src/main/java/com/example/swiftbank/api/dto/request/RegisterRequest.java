package com.example.swiftbank.api.dto.request;

import com.google.gson.annotations.SerializedName;

public class RegisterRequest {

    @SerializedName("phone")
    private String phone;

    @SerializedName("email")
    private String email;

    @SerializedName("password")
    private String password;

    @SerializedName("first_name")
    private String firstName;

    @SerializedName("last_name")
    private String lastName;

    @SerializedName("cnp")
    private String cnp;

    @SerializedName("address")
    private String address;

    @SerializedName("device_id")
    private String deviceId;

    @SerializedName("device_name")
    private String deviceName;

    public RegisterRequest(String phone, String email, String password,
                           String firstName, String lastName, String cnp,
                           String address, String deviceId, String deviceName) {
        this.phone = phone;
        this.email = email;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
        this.cnp = cnp;
        this.address = address;
        this.deviceId = deviceId;
        this.deviceName = deviceName;
    }
}
