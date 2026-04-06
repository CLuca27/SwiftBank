package com.example.swiftbank.api.dto.request;

import com.google.gson.annotations.SerializedName;

public class CheckRequest {
    @SerializedName("field")
    private String field;

    @SerializedName("value")
    private String value;

    public CheckRequest(String field, String value) {
        this.field = field;
        this.value = value;
    }



}
