package com.example.swiftbank.api.services;

import com.example.swiftbank.api.dto.request.AiChatRequest;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.success.AiChatData;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface AiService {
    @POST("/api/ai/chat")
    Call<ApiResponse<AiChatData>> chat(@Body AiChatRequest request);
}