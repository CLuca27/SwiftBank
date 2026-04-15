package com.example.swiftbank.api.dto.response.data;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class BeneficiariesData {
    private List<Beneficiary> beneficiaries;

    public List<Beneficiary> getBeneficiaries() {
        return beneficiaries;
    }

    public static class Beneficiary {
        @SerializedName("beneficiary_id")
        private int beneficiaryId;

        private String name;
        private String iban;

        @SerializedName("bank_name")
        private String bankName;

        @SerializedName("created_at")
        private String createdAt;

        public int getBeneficiaryId() {
            return beneficiaryId;
        }

        public String getName() {
            return name;
        }

        public String getIban() {
            return iban;
        }

        public String getBankName() {
            return bankName;
        }

        public String getCreatedAt() {
            return createdAt;
        }
    }
}
