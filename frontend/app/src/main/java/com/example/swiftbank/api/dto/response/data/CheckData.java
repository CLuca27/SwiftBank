package com.example.swiftbank.api.dto.response.data;

import com.google.gson.annotations.SerializedName;

public class CheckData {

    @SerializedName("exists")
    private boolean exists;

    public boolean isExists() {
        return exists;
    }

}
