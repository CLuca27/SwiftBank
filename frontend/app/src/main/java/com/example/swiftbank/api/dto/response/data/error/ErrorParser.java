package com.example.swiftbank.api.dto.response.data.error;

import com.example.swiftbank.api.dto.response.ApiErrorResponse;
import com.example.swiftbank.config.GsonProvider;

import retrofit2.Response;

public class ErrorParser {

    public static ApiErrorResponse parseError(Response<?> response) {
        try {
            if (response.errorBody() == null) return null;
            String errorBody = response.errorBody().string();
            return GsonProvider.getGson().fromJson(errorBody, ApiErrorResponse.class);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}


