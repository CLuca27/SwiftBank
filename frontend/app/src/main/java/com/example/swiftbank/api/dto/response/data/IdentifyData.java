package com.example.swiftbank.api.dto.response.data;

import com.google.gson.annotations.SerializedName;

public class IdentifyData {

    @SerializedName("first_name")
    private String firstName;
    @SerializedName("status")
    private String status;

    @SerializedName("locked_until")
    private String lockedUntil;

    public String getFirstName() {
        return firstName;
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


}
