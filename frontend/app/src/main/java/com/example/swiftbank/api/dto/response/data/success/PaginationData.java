package com.example.swiftbank.api.dto.response.data.success;

import com.google.gson.annotations.SerializedName;

public class PaginationData {

    @SerializedName("limit")
    private int limit;

    @SerializedName("offset")
    private int offset;

    @SerializedName("hasMore")
    private boolean hasMore;

    public int getLimit() {
        return limit;
    }

    public int getOffset() {
        return offset;
    }

    public boolean hasMore() {
        return hasMore;
    }
}
