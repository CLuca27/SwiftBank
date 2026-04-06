package com.example.swiftbank.api.services;

import com.example.swiftbank.api.dto.request.CheckRequest;
import com.example.swiftbank.api.dto.request.IdentifyRequest;
import com.example.swiftbank.api.dto.request.LoginRequest;
import com.example.swiftbank.api.dto.request.RegisterRequest;
import com.example.swiftbank.api.dto.request.SendOtpRequest;
import com.example.swiftbank.api.dto.request.RefreshRequest;
import com.example.swiftbank.api.dto.request.VerifyOtpRequest;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.CheckData;
import com.example.swiftbank.api.dto.response.data.LoginData;
import com.example.swiftbank.api.dto.response.data.RefreshData;
import com.example.swiftbank.api.dto.response.data.TokenData;
import com.example.swiftbank.api.dto.response.data.IdentifyData;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface AuthService {

    @POST("/auth/refresh")
    Call<ApiResponse<RefreshData>> refreshToken(@Body RefreshRequest request);
    @POST("auth/check")
    Call<ApiResponse<CheckData>> check(@Body CheckRequest request);
    @POST("auth/identify")
    Call<ApiResponse<IdentifyData>> identify(@Body IdentifyRequest request);
    @POST("auth/login")
    Call<ApiResponse<LoginData>> login(@Body LoginRequest request);
    @POST("auth/send-otp")
    Call<ApiResponse<Void>> sendOtp(@Body SendOtpRequest request);

    @POST("auth/verify-otp")
    Call<ApiResponse<Void>> verifyOtp(@Body VerifyOtpRequest request);

    @POST("auth/register")
    Call<ApiResponse<TokenData>> register(@Body RegisterRequest request);
}

