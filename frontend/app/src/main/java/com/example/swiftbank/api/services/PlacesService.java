package com.example.swiftbank.api.services;

import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.PlaceDetailsData;
import com.example.swiftbank.api.dto.response.data.PlaceSuggestionData;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface PlacesService {
    @GET("address/autocomplete")
    Call<ApiResponse<List<PlaceSuggestionData>>> autocomplete(@Query("input") String input);
    @GET("address/place-details")
    Call<ApiResponse<PlaceDetailsData>> getDetails(@Query("place_id") String placeId);
}
