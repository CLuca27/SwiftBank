package com.example.swiftbank.api.services;

import com.example.swiftbank.api.dto.request.CreateBeneficiaryRequest;
import com.example.swiftbank.api.dto.request.CreateTransferRequest;
import com.example.swiftbank.api.dto.request.ValidateIBANRequest;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.success.BeneficiariesData;
import com.example.swiftbank.api.dto.response.data.success.BeneficiaryData;
import com.example.swiftbank.api.dto.response.data.success.TransferResultData;
import com.example.swiftbank.api.dto.response.data.success.ValidateIBANData;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;

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

    @POST("api/beneficiaries")
    Call<ApiResponse<BeneficiaryData>> createBeneficiary(@Body CreateBeneficiaryRequest request);

    @DELETE("api/beneficiaries/{id}")
    Call<ApiResponse<Void>> deleteBeneficiary(@Path("id") int beneficiaryId);
}
