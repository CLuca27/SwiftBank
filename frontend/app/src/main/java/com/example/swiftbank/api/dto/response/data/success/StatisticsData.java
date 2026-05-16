package com.example.swiftbank.api.dto.response.data.success;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class StatisticsData {

    @SerializedName("period")
    private String period;

    @SerializedName("startDate")
    private String startDate;

    @SerializedName("endDate")
    private String endDate;

    @SerializedName("summary")
    private Summary summary;

    @SerializedName("categories")
    private List<CategoryStats> categories;

    @SerializedName("topMerchants")
    private List<MerchantStats> topMerchants;

    @SerializedName("monthlyTrend")
    private List<MonthlyStats> monthlyTrend;

    public String getPeriod() {
        return period;
    }

    public String getStartDate() {
        return startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public Summary getSummary() {
        return summary;
    }

    public List<CategoryStats> getCategories() {
        return categories;
    }

    public List<MerchantStats> getTopMerchants() {
        return topMerchants;
    }

    public List<MonthlyStats> getMonthlyTrend() {
        return monthlyTrend;
    }

    public static class Summary {
        @SerializedName("totalIncome")
        private double totalIncome;

        @SerializedName("totalExpenses")
        private double totalExpenses;

        @SerializedName("balance")
        private double balance;

        @SerializedName("transactionCount")
        private int transactionCount;

        public double getTotalIncome() {
            return totalIncome;
        }

        public double getTotalExpenses() {
            return totalExpenses;
        }

        public double getBalance() {
            return balance;
        }

        public int getTransactionCount() {
            return transactionCount;
        }
    }

    public static class CategoryStats {
        @SerializedName("name")
        private String name;

        @SerializedName("icon")
        private String icon;

        @SerializedName("amount")
        private double amount;

        @SerializedName("count")
        private int count;

        @SerializedName("percentage")
        private double percentage;

        public String getName() {
            return name;
        }

        public String getIcon() {
            return icon;
        }

        public double getAmount() {
            return amount;
        }

        public int getCount() {
            return count;
        }

        public double getPercentage() {
            return percentage;
        }
    }

    public static class MerchantStats {
        @SerializedName("name")
        private String name;

        @SerializedName("merchant_logo_url")
        private String merchantLogoUrl;

        @SerializedName("category_name")
        private String categoryName;

        @SerializedName("category_icon")
        private String categoryIcon;

        @SerializedName("amount")
        private double amount;

        @SerializedName("count")
        private int count;

        public String getName() {
            return name;
        }

        public String getMerchantLogoUrl() {
            return merchantLogoUrl;
        }

        public String getCategoryName() {
            return categoryName;
        }

        public String getCategoryIcon() {
            return categoryIcon;
        }

        public double getAmount() {
            return amount;
        }

        public int getCount() {
            return count;
        }
    }

    public static class MonthlyStats {
        @SerializedName("month")
        private String month;

        @SerializedName("income")
        private double income;

        @SerializedName("expenses")
        private double expenses;

        public String getMonth() {
            return month;
        }

        public double getIncome() {
            return income;
        }

        public double getExpenses() {
            return expenses;
        }
    }
}
