package com.example.swiftbank.api.services;

import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.TransactionsData;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface TransactionService {

    @GET("/api/transactions")
    Call<ApiResponse<TransactionsData>> getTransactions(
            @Query("account_id") int accountId,
            @Query("limit") int limit,
            @Query("offset") int offset
    );
}
