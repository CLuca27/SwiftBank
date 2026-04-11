package com.example.swiftbank.api.dto.response.data;

import com.google.gson.annotations.SerializedName;

public class NewAccountData {
    @SerializedName("account")
    private AccountData account;

    public AccountData getAccount() {
        return account;
    }
}
