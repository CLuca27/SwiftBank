package com.example.swiftbank.api.dto.response.data;

public class ExchangeRateData {
    private String from;
    private String to;
    private double rate;
    private double inverse;

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    public double getRate() {
        return rate;
    }

    public double getInverse() {
        return inverse;
    }
}
