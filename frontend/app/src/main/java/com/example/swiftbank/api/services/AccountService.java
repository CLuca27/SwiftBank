package com.example.swiftbank.api.services;

import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.AccountsData;

import retrofit2.Call;
import retrofit2.http.GET;

public interface AccountService {

    @GET("/api/accounts")
    Call<ApiResponse<AccountsData>> getAccounts();
}
