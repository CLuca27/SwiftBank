package com.example.swiftbank.api.services;

import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.RatesData;

import retrofit2.Call;
import retrofit2.http.GET;

public interface RatesService {

    @GET("/rates")
    Call<ApiResponse<RatesData>> getRates();
}
