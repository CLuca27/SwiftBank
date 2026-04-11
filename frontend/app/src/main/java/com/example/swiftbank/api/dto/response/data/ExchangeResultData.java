package com.example.swiftbank.api.dto.response.data;

public class ExchangeResultData {
    private AccountResult from;
    private AccountResult to;
    private double exchangeRate;

    public AccountResult getFrom() {
        return from;
    }

    public AccountResult getTo() {
        return to;
    }

    public double getExchangeRate() {
        return exchangeRate;
    }

    public static class AccountResult {
        private String account_id;
        private String currency;
        private double amount;
        private double newBalance;

        public String getAccountId() {
            return account_id;
        }

        public String getCurrency() {
            return currency;
        }

        public double getAmount() {
            return amount;
        }

        public double getNewBalance() {
            return newBalance;
        }
    }
}
