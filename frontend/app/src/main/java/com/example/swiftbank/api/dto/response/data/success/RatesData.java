package com.example.swiftbank.api.dto.response.data.success;

import java.util.List;

public class RatesData {
    private List<RateItem> rates;

    public List<RateItem> getRates() {
        return rates;
    }

    public static class RateItem {
        private String from_currency;
        private String to_currency;
        private double rate;
        private String rate_date;

        public String getFromCurrency() {
            return from_currency;
        }

        public String getToCurrency() {
            return to_currency;
        }

        public double getRate() {
            return rate;
        }

        public String getRateDate() {
            return rate_date;
        }
    }
}
