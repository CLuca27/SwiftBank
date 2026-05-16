package com.example.swiftbank.api.dto.response.data.success;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class SavedBillersData {
    @SerializedName("saved_billers")
    private List<SavedBillerData> savedBillers;

    public List<SavedBillerData> getSavedBillers() {
        return savedBillers;
    }
}
