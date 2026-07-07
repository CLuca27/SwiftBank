package com.example.swiftbank.api.dto.request;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class AiChatRequest {
    @SerializedName("message")
    private String message;

    @SerializedName("history")
    private List<ChatMessage> history;

    public AiChatRequest(String message, List<ChatMessage> history) {
        this.message = message;
        this.history = history;
    }

    public static class ChatMessage {
        @SerializedName("role")
        private String role;

        @SerializedName("content")
        private String content;

        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
}