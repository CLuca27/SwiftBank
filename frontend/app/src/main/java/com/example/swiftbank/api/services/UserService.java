package com.example.swiftbank.api.services;

import com.example.swiftbank.api.dto.request.CardDetailsRequest;
import com.example.swiftbank.api.dto.request.ChangePinRequest;
import com.example.swiftbank.api.dto.request.RegisterDeviceRequest;
import com.example.swiftbank.api.dto.request.UpdateProfileRequest;
import com.example.swiftbank.api.dto.request.UpdateSettingsRequest;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.success.ProfileData;
import com.example.swiftbank.api.dto.response.data.success.SettingsData;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface UserService {
    @GET("api/user/profile")
    Call<ApiResponse<ProfileData>> getProfile();

    @PUT("api/user/profile")
    Call<ApiResponse<ProfileData>> updateProfile(@Body UpdateProfileRequest request);

    @GET("api/user/settings")
    Call<ApiResponse<SettingsData>> getSettings();

    @PUT("api/user/settings")
    Call<ApiResponse<SettingsData>> updateSettings(@Body UpdateSettingsRequest request);

    @POST("api/user/devices")
    Call<ApiResponse<Void>> registerDevice(@Body RegisterDeviceRequest request);

    @DELETE("api/user/devices/{deviceId}")
    Call<ApiResponse<Void>> unregisterDevice(@Path("deviceId") String deviceId);

    @POST("api/user/verify-pin")
    Call<ApiResponse<Void>> verifyPin(@Body CardDetailsRequest request);

    @PUT("api/user/change-pin")
    Call<ApiResponse<Void>> changePin(@Body ChangePinRequest request);
}
