package com.example.swiftbank.api.dto.response.data.success;

import com.google.gson.annotations.SerializedName;

public class BeneficiaryData {
    @SerializedName("beneficiary_id")
    private int beneficiaryId;

    private String name;
    private String iban;

    @SerializedName("bank_name")
    private String bankName;

    @SerializedName("profile_photo")
    private String profilePhoto;

    @SerializedName("created_at")
    private String createdAt;

    public int getBeneficiaryId() {
        return beneficiaryId;
    }

    public String getName() {
        return name;
    }

    public String getIban() {
        return iban;
    }

    public String getBankName() {
        return bankName;
    }

    public String getProfilePhoto() {
        return profilePhoto;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
