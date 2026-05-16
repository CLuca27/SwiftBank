package com.example.swiftbank.api.dto.request;

public class CreateBeneficiaryRequest {
    private String iban;
    private String name;

    public CreateBeneficiaryRequest(String iban, String name) {
        this.iban = iban;
        this.name = name;
    }

    public String getIban() {
        return iban;
    }

    public String getName() {
        return name;
    }
}
