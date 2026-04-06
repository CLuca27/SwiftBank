package com.example.swiftbank.api.dto.response.data.error;

import com.google.gson.annotations.SerializedName;

public class LoginCooldownErrorData extends ErrorData{

    @SerializedName("seconds_left")
    private int secondsLeft;

    @SerializedName("locked_until")
    private String lockedUntil;

    public int getSecondsLeft() {
        return secondsLeft;
    }

    public String getLockedUntil() {
        return lockedUntil;
    }
}
