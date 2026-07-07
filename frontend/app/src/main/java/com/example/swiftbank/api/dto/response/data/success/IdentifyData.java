package com.example.swiftbank.api.dto.response.data.success;

import com.google.gson.annotations.SerializedName;

public class IdentifyData {

    @SerializedName("phone")
    private String phone;

    @SerializedName("email")
    private String email;

    @SerializedName("first_name")
    private String firstName;
    @SerializedName("status")
    private String status;

    @SerializedName("locked_until")
    private String lockedUntil;

    @SerializedName("biometric_enabled")
    private boolean biometricEnabled;

    public String getFirstName() {
        return firstName;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getLockedUntil() {
        return lockedUntil;
    }

    public boolean isLocked(){
        return "LOCKED".equals(status);
    }

    public boolean isBlocked(){
        return "BLOCKED".equals(status);
    }

    public boolean isBiometricEnabled() {
        return biometricEnabled;
    }


}
