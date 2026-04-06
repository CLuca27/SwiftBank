package com.example.swiftbank.api.dto.response.data.error;

import com.google.gson.annotations.SerializedName;

public class OtpCooldownErrorData extends ErrorData{

    @SerializedName("seconds_left")
    private int secondsLeft;

    public int getSecondsLeft() {
        return secondsLeft;
    }
}
