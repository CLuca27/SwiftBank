package com.example.swiftbank.api.services;

import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.ProfileData;

import retrofit2.Call;
import retrofit2.http.GET;

public interface UserService {
    @GET("api/user/profile")
    Call<ApiResponse<ProfileData>> getProfile();
}
