package com.example.swiftbank.api.dto.response.data.success;

import com.example.swiftbank.api.dto.response.data.success.transaction.Transaction;
import com.google.gson.annotations.SerializedName;

import java.util.List;

public class TransactionsData {

    @SerializedName("transactions")
    private List<Transaction> transactions;

    @SerializedName("pagination")
    private PaginationData pagination;

    public List<Transaction> getTransactions() {
        return transactions;
    }

    public PaginationData getPagination() {
        return pagination;
    }
}
