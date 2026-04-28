package com.example.swiftbank.api.dto.response.data.success;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class AccountsData {

    @SerializedName("accounts")
    private List<AccountData> accounts;

    public List<AccountData> getAccounts() {
        return accounts;
    }
}
