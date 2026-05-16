package com.example.swiftbank.api.services;

import com.example.swiftbank.api.dto.request.CreateBillPaymentRequest;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.success.BillerCategoriesData;
import com.example.swiftbank.api.dto.response.data.success.BillerData;
import com.example.swiftbank.api.dto.response.data.success.BillersData;
import com.example.swiftbank.api.dto.response.data.success.BillPaymentResultData;
import com.example.swiftbank.api.dto.response.data.success.SavedBillersData;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface BillService {
    @GET("api/billers")
    Call<ApiResponse<BillersData>> getBillers(@Query("category") String category);

    @GET("api/billers/categories")
    Call<ApiResponse<BillerCategoriesData>> getBillerCategories();

    @GET("api/billers/{id}")
    Call<ApiResponse<BillerData>> getBillerById(@Path("id") int billerId);

    @GET("api/saved-billers")
    Call<ApiResponse<SavedBillersData>> getSavedBillers();

    @DELETE("api/saved-billers/{id}")
    Call<ApiResponse<Void>> deleteSavedBiller(@Path("id") int savedBillerId);

    @POST("api/bill-payments")
    Call<ApiResponse<BillPaymentResultData>> createBillPayment(
            @Header("Idempotency-Key") String idempotencyKey,
            @Body CreateBillPaymentRequest request
    );
}
