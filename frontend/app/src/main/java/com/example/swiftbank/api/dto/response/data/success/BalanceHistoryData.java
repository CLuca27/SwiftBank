package com.example.swiftbank.api.dto.response.data.success;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class BalanceHistoryData {

    @SerializedName("balanceHistory")
    private List<BalancePoint> balanceHistory;

    public List<BalancePoint> getBalanceHistory() {
        return balanceHistory;
    }

    public static class BalancePoint {
        @SerializedName("month")
        private String month;

        @SerializedName("balance")
        private double balance;

        public String getMonth() {
            return month;
        }

        public double getBalance() {
            return balance;
        }
    }
}
