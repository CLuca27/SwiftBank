package com.example.swiftbank.api.dto.response;

import com.example.swiftbank.api.dto.response.data.error.ErrorData;

public class ApiErrorResponse {
    private boolean success;
    private ErrorData error;

    public ErrorData getError() {
        return error;
    }

    public boolean isSuccess() {
        return success;
    }
}
