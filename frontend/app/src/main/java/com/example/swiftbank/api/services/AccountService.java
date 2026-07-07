package com.example.swiftbank.api.services;

import com.example.swiftbank.api.dto.request.AddAccountRequest;
import com.example.swiftbank.api.dto.request.ExchangeRequest;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.success.AccountsData;
import com.example.swiftbank.api.dto.response.data.success.ExchangeRateData;
import com.example.swiftbank.api.dto.response.data.success.ExchangeResultData;
import com.example.swiftbank.api.dto.response.data.success.NewAccountData;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface AccountService {

    @GET("/api/accounts")
    Call<ApiResponse<AccountsData>> getAccounts();

    @POST("/api/accounts/add")
    Call<ApiResponse<NewAccountData>> addAccount(@Body AddAccountRequest request);

    @POST("/api/accounts/exchange")
    Call<ApiResponse<ExchangeResultData>> exchange(
            @Header("Idempotency-Key") String idempotencyKey,
            @Body ExchangeRequest request
    );

    @GET("/rates/convert")
    Call<ApiResponse<ExchangeRateData>> getExchangeRate(@Query("from") String from, @Query("to") String to);
}
