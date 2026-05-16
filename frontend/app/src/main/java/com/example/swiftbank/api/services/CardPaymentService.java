package com.example.swiftbank.api.services;

import com.example.swiftbank.api.dto.request.DeclineCardPaymentRequest;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.success.CardPaymentSessionData;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface CardPaymentService {

    @GET("/api/card-payments/{sessionId}")
    Call<ApiResponse<CardPaymentSessionData>> getSessionStatus(@Path("sessionId") int sessionId);

    @POST("/api/card-payments/{sessionId}/approve")
    Call<ApiResponse<CardPaymentSessionData>> approvePayment(@Path("sessionId") int sessionId);

    @POST("/api/card-payments/{sessionId}/decline")
    Call<ApiResponse<CardPaymentSessionData>> declinePayment(
            @Path("sessionId") int sessionId,
            @Body DeclineCardPaymentRequest request
    );
}
