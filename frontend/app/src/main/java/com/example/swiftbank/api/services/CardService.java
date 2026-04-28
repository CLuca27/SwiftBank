package com.example.swiftbank.api.services;

import com.example.swiftbank.api.dto.request.CardDetailsRequest;
import com.example.swiftbank.api.dto.request.CreateCardRequest;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.success.CardDetailsData;
import com.example.swiftbank.api.dto.response.data.success.CardsData;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface CardService {

    @GET("/api/cards")
    Call<ApiResponse<CardsData>> getCards();

    @POST("/api/cards")
    Call<ApiResponse<CardDetailsData>> createCard(@Body CreateCardRequest request);

    @POST("/api/cards/{cardId}/details")
    Call<ApiResponse<CardDetailsData>> getCardDetails(
            @Path("cardId") int cardId,
            @Body CardDetailsRequest request
    );

    @PUT("/api/cards/{cardId}/block")
    Call<ApiResponse<Object>> toggleBlock(@Path("cardId") int cardId);

    @DELETE("/api/cards/{cardId}")
    Call<ApiResponse<Object>> deleteCard(@Path("cardId") int cardId);
}
