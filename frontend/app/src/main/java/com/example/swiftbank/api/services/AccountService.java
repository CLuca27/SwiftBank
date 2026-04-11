package com.example.swiftbank.api.services;

import com.example.swiftbank.api.dto.request.AddAccountRequest;
import com.example.swiftbank.api.dto.request.ExchangeRequest;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.AccountsData;
import com.example.swiftbank.api.dto.response.data.ExchangeRateData;
import com.example.swiftbank.api.dto.response.data.ExchangeResultData;
import com.example.swiftbank.api.dto.response.data.NewAccountData;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface AccountService {

    @GET("/api/accounts")
    Call<ApiResponse<AccountsData>> getAccounts();

    @POST("/api/accounts/add")
    Call<ApiResponse<NewAccountData>> addAccount(@Body AddAccountRequest request);

    @POST("/api/accounts/exchange")
    Call<ApiResponse<ExchangeResultData>> exchange(@Body ExchangeRequest request);

    @GET("/rates/convert")
    Call<ApiResponse<ExchangeRateData>> getExchangeRate(@Query("from") String from, @Query("to") String to);
}
