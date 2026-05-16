package com.example.swiftbank.api.services;

import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.success.StatisticsData;
import com.example.swiftbank.api.dto.response.data.success.BalanceHistoryData;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface StatisticsService {

    @GET("/api/statistics")
    Call<ApiResponse<StatisticsData>> getStatistics(@Query("period") String period);

    @GET("/api/statistics/balance-history")
    Call<ApiResponse<BalanceHistoryData>> getBalanceHistory(@Query("period") String period);
}
