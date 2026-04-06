package com.example.swiftbank.api.dto.response.data.error;

import com.google.gson.annotations.SerializedName;

public class AttemptsErrorData extends ErrorData{
    @SerializedName("attempts_left")
    private int attemptsLeft;

    public int getAttemptsLeft() {
        return attemptsLeft;
    }
}
