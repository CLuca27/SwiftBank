package com.example.swiftbank.api.dto.response.data.success.transaction;

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
        return firstAvailable(billerName, title);
    }

    public String getBillerCategory() {
        return firstAvailable(billerCategory, subtitle);
    }

    public String getClientCode() {
        return firstAvailable(clientCode, reference);
    }

    public String getInvoiceReference() {
        return firstAvailable(invoiceReference, description);
    }

    @Override
    public String getStatus() {
        if (status != null && !status.isEmpty()) {
            return status;
        }

        return "COMPLETED";
    }

    private String firstAvailable(String primary, String fallback) {
        if (primary != null && !primary.isEmpty()) {
            return primary;
        }

        return fallback;
    }
}
