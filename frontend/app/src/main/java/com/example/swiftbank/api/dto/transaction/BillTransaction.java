package com.example.swiftbank.api.dto.transaction;

import com.google.gson.annotations.SerializedName;

public class BillTransaction extends Transaction {

    @SerializedName("biller_name")
    private String billerName;

    @SerializedName("biller_category")
    private String billerCategory;

    @SerializedName("client_code")
    private String clientCode;

    @SerializedName("invoice_reference")
    private String invoiceReference;

    public String getBillerName() {
        return billerName;
    }

    public String getBillerCategory() {
        return billerCategory;
    }

    public String getClientCode() {
        return clientCode;
    }

    public String getInvoiceReference() {
        return invoiceReference;
    }
}
