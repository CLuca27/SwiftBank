package com.example.swiftbank.api.dto.response.data.success;

import com.google.gson.annotations.SerializedName;

public class PlaceSuggestionData {
    @SerializedName("place_id")
    private String placeId;
    @SerializedName("description")
    private String description;

    public String getPlaceId() {
        return placeId;
    }
    public String getDescription() {
        return description;
    }
}

