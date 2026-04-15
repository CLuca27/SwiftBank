package com.example.swiftbank.api.services;

import com.example.swiftbank.api.dto.request.CreateTransferRequest;
import com.example.swiftbank.api.dto.request.ValidateIBANRequest;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.BeneficiariesData;
import com.example.swiftbank.api.dto.response.data.TransferResultData;
import com.example.swiftbank.api.dto.response.data.ValidateIBANData;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;

public interface TransferService {
    @POST("api/transfers/validate-iban")
    Call<ApiResponse<ValidateIBANData>> validateIBAN(@Body ValidateIBANRequest request);

    @POST("api/transfers")
    Call<ApiResponse<TransferResultData>> createTransfer(
            @Header("Idempotency-Key") String idempotencyKey,
            @Body CreateTransferRequest request
    );

    @GET("api/beneficiaries")
    Call<ApiResponse<BeneficiariesData>> getBeneficiaries();
}
