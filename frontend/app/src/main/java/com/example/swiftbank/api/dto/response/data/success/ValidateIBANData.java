package com.example.swiftbank.api.dto.response.data.success;

import com.google.gson.annotations.SerializedName;

public class ValidateIBANData {
    private String iban;
    private String bic;

    @SerializedName("bank_name")
    private String bankName;

    @SerializedName("beneficiary_name")
    private String beneficiaryName;

    @SerializedName("account_currency")
    private String accountCurrency;

    @SerializedName("profile_photo")
    private String profilePhoto;

    @SerializedName("is_swift_bank")
    private boolean isSwiftBank;

    @SerializedName("is_same_user")
    private boolean isSameUser;

    public String getIban() {
        return iban;
    }

    public String getBic() {
        return bic;
    }

    public String getBankName() {
        return bankName;
    }

    public String getBeneficiaryName() {
        return beneficiaryName;
    }

    public String getAccountCurrency() {
        return accountCurrency;
    }

    public String getProfilePhoto() {
        return profilePhoto;
    }

    public boolean isSwiftBank() {
        return isSwiftBank;
    }

    public boolean isSameUser() {
        return isSameUser;
    }
}
