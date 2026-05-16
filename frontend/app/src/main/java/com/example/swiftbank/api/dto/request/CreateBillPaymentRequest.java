package com.example.swiftbank.api.dto.request;

import com.google.gson.annotations.SerializedName;

public class CreateBillPaymentRequest {
    @SerializedName("from_account_id")
    private int fromAccountId;

    @SerializedName("biller_id")
    private int billerId;

    @SerializedName("client_code")
    private String clientCode;

    @SerializedName("invoice_reference")
    private String invoiceReference;

    private double amount;

    @SerializedName("saved_biller_id")
    private Integer savedBillerId;

    public CreateBillPaymentRequest(int fromAccountId, int billerId, String clientCode,
                                    String invoiceReference, double amount, Integer savedBillerId) {
        this.fromAccountId = fromAccountId;
        this.billerId = billerId;
        this.clientCode = clientCode;
        this.invoiceReference = invoiceReference;
        this.amount = amount;
        this.savedBillerId = savedBillerId;
    }
}
