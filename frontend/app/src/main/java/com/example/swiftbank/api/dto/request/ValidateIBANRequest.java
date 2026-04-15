package com.example.swiftbank.api.dto.request;

public class ValidateIBANRequest {
    private String iban;

    public ValidateIBANRequest(String iban) {
        this.iban = iban;
    }

    public String getIban() {
        return iban;
    }
}
