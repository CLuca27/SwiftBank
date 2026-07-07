package com.example.swiftbank.api.dto.response.data.success;

import com.google.gson.annotations.SerializedName;

public class AiChatData {
    @SerializedName("answer")
    private String answer;

    @SerializedName("contextConnected")
    private boolean contextConnected;

    public String getAnswer() {
        return answer;
    }

    public boolean isContextConnected() {
        return contextConnected;
    }
}