package com.example.swiftbank.api.dto.response.data.success;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class StatisticsData {

    @SerializedName("period")
    private String period;

    @SerializedName("granularity")
    private String granularity;

    @SerializedName("baseCurrency")
    private String baseCurrency;

    @SerializedName("startDate")
    private String startDate;

    @SerializedName("endDate")
    private String endDate;

    @SerializedName("summary")
    private Summary summary;

    @SerializedName("comparison")
    private Comparison comparison;

    @SerializedName("insights")
    private List<Insight> insights;

    @SerializedName("categories")
    private List<CategoryStats> categories;

    @SerializedName("topMerchants")
    private List<MerchantStats> topMerchants;

    @SerializedName("currencyBreakdown")
    private List<CurrencyStats> currencyBreakdown;

    @SerializedName("monthlyTrend")
    private List<MonthlyStats> monthlyTrend;

    public String getPeriod() {
        return period;
    }

    public String getGranularity() {
        return granularity;
    }

    public String getBaseCurrency() {
        return baseCurrency;
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

    public Comparison getComparison() {
        return comparison;
    }

    public List<Insight> getInsights() {
        return insights;
    }

    public List<CategoryStats> getCategories() {
        return categories;
    }

    public List<MerchantStats> getTopMerchants() {
        return topMerchants;
    }

    public List<CurrencyStats> getCurrencyBreakdown() {
        return currencyBreakdown;
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

    public static class Comparison {
        @SerializedName("hasPreviousPeriod")
        private boolean hasPreviousPeriod;

        @SerializedName("previousTotalIncome")
        private double previousTotalIncome;

        @SerializedName("previousTotalExpenses")
        private double previousTotalExpenses;

        @SerializedName("previousBalance")
        private double previousBalance;

        @SerializedName("incomeChange")
        private double incomeChange;

        @SerializedName("incomeChangePercent")
        private Double incomeChangePercent;

        @SerializedName("expensesChange")
        private double expensesChange;

        @SerializedName("expensesChangePercent")
        private Double expensesChangePercent;

        @SerializedName("balanceChange")
        private double balanceChange;

        @SerializedName("transactionCountChange")
        private int transactionCountChange;

        @SerializedName("topCategoryName")
        private String topCategoryName;

        @SerializedName("topCategoryAmount")
        private double topCategoryAmount;

        @SerializedName("topCategoryPreviousAmount")
        private double topCategoryPreviousAmount;

        @SerializedName("topCategoryChange")
        private double topCategoryChange;

        @SerializedName("topCategoryChangePercent")
        private Double topCategoryChangePercent;

        public boolean hasPreviousPeriod() {
            return hasPreviousPeriod;
        }

        public double getPreviousTotalIncome() {
            return previousTotalIncome;
        }

        public double getPreviousTotalExpenses() {
            return previousTotalExpenses;
        }

        public double getPreviousBalance() {
            return previousBalance;
        }

        public double getIncomeChange() {
            return incomeChange;
        }

        public Double getIncomeChangePercent() {
            return incomeChangePercent;
        }

        public double getExpensesChange() {
            return expensesChange;
        }

        public Double getExpensesChangePercent() {
            return expensesChangePercent;
        }

        public double getBalanceChange() {
            return balanceChange;
        }

        public int getTransactionCountChange() {
            return transactionCountChange;
        }

        public String getTopCategoryName() {
            return topCategoryName;
        }

        public double getTopCategoryAmount() {
            return topCategoryAmount;
        }

        public double getTopCategoryPreviousAmount() {
            return topCategoryPreviousAmount;
        }

        public double getTopCategoryChange() {
            return topCategoryChange;
        }

        public Double getTopCategoryChangePercent() {
            return topCategoryChangePercent;
        }
    }

    public static class Insight {
        @SerializedName("type")
        private String type;

        @SerializedName("title")
        private String title;

        @SerializedName("message")
        private String message;

        public String getType() {
            return type;
        }

        public String getTitle() {
            return title;
        }

        public String getMessage() {
            return message;
        }
    }

    public static class TransactionExample {
        @SerializedName("title")
        private String title;

        @SerializedName("amount")
        private double amount;

        @SerializedName("currency")
        private String currency;

        @SerializedName("createdAt")
        private String createdAt;

        public String getTitle() {
            return title;
        }

        public double getAmount() {
            return amount;
        }

        public String getCurrency() {
            return currency;
        }

        public String getCreatedAt() {
            return createdAt;
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

        @SerializedName("examples")
        private List<TransactionExample> examples;

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

        public List<TransactionExample> getExamples() {
            return examples;
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

        @SerializedName("examples")
        private List<TransactionExample> examples;

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

        public List<TransactionExample> getExamples() {
            return examples;
        }
    }

    public static class CurrencyStats {
        @SerializedName("currency")
        private String currency;

        @SerializedName("income")
        private double income;

        @SerializedName("expenses")
        private double expenses;

        @SerializedName("convertedIncome")
        private double convertedIncome;

        @SerializedName("convertedExpenses")
        private double convertedExpenses;

        @SerializedName("count")
        private int count;

        public String getCurrency() {
            return currency;
        }

        public double getIncome() {
            return income;
        }

        public double getExpenses() {
            return expenses;
        }

        public double getConvertedIncome() {
            return convertedIncome;
        }

        public double getConvertedExpenses() {
            return convertedExpenses;
        }

        public int getCount() {
            return count;
        }
    }

    public static class MonthlyStats {
        @SerializedName("month")
        private String month;

        @SerializedName("label")
        private String label;

        @SerializedName("income")
        private double income;

        @SerializedName("expenses")
        private double expenses;

        public String getMonth() {
            return month;
        }

        public String getLabel() {
            return label;
        }

        public double getIncome() {
            return income;
        }

        public double getExpenses() {
            return expenses;
        }
    }
}