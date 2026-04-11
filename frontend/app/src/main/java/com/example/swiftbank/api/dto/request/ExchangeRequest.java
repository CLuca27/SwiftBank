package com.example.swiftbank.api.dto.request;

public class ExchangeRequest {
    private String fromAccountId;
    private String toAccountId;
    private double amount;

    public ExchangeRequest(String fromAccountId, String toAccountId, double amount) {
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
    }

    public String getFromAccountId() {
        return fromAccountId;
    }

    public String getToAccountId() {
        return toAccountId;
    }

    public double getAmount() {
        return amount;
    }
}
