package com.example.swiftbank.activities.statistics;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.example.swiftbank.R;
import com.example.swiftbank.api.ApiClient;
import com.example.swiftbank.api.dto.response.ApiResponse;
import com.example.swiftbank.api.dto.response.data.success.StatisticsData;
import com.example.swiftbank.utils.RemoteImageLoader;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class StatisticsActivity extends AppCompatActivity {

    private ImageView btnBack;
    private TextView btnThisMonth, btnLastMonth, btnLast3Months, btnThisYear;
    private LinearLayout loadingState, contentContainer, emptyState, monthlyTrendCard;
    private LinearLayout categoriesLegend, merchantsList;

    private TextView tvTotalIncome, tvTotalExpenses, tvBalance;
    private PieChart pieChart;
    private BarChart barChart;

    private String currentPeriod = "this_month";
    private TextView selectedPeriodButton;

    private final int[] CHART_COLORS = {
            0xFF00C853, // Green
            0xFF2979FF, // Blue
            0xFFFF6D00, // Orange
            0xFFAA00FF, // Purple
            0xFFFF1744, // Red
            0xFF00BFA5, // Teal
            0xFFFFD600, // Yellow
            0xFFE91E63, // Pink
            0xFF00B8D4, // Cyan
            0xFF6D4C41  // Brown
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);

        initViews();
        setupListeners();
        setupCharts();
        loadStatistics();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);

        btnThisMonth = findViewById(R.id.btnThisMonth);
        btnLastMonth = findViewById(R.id.btnLastMonth);
        btnLast3Months = findViewById(R.id.btnLast3Months);
        btnThisYear = findViewById(R.id.btnThisYear);

        loadingState = findViewById(R.id.loadingState);
        contentContainer = findViewById(R.id.contentContainer);
        emptyState = findViewById(R.id.emptyState);
        monthlyTrendCard = findViewById(R.id.monthlyTrendCard);

        tvTotalIncome = findViewById(R.id.tvTotalIncome);
        tvTotalExpenses = findViewById(R.id.tvTotalExpenses);
        tvBalance = findViewById(R.id.tvBalance);

        pieChart = findViewById(R.id.pieChart);
        barChart = findViewById(R.id.barChart);

        categoriesLegend = findViewById(R.id.categoriesLegend);
        merchantsList = findViewById(R.id.merchantsList);

        selectedPeriodButton = btnThisMonth;
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnThisMonth.setOnClickListener(v -> selectPeriod("this_month", btnThisMonth));
        btnLastMonth.setOnClickListener(v -> selectPeriod("last_month", btnLastMonth));
        btnLast3Months.setOnClickListener(v -> selectPeriod("last_3_months", btnLast3Months));
        btnThisYear.setOnClickListener(v -> selectPeriod("this_year", btnThisYear));
    }

    private void selectPeriod(String period, TextView button) {
        if (period.equals(currentPeriod)) return;

        // Update button styles
        selectedPeriodButton.setBackgroundResource(R.drawable.bg_chip_unselected);
        selectedPeriodButton.setTextColor(ContextCompat.getColor(this, R.color.white_60));

        button.setBackgroundResource(R.drawable.bg_chip_selected);
        button.setTextColor(ContextCompat.getColor(this, R.color.white));

        selectedPeriodButton = button;
        currentPeriod = period;

        loadStatistics();
    }

    private void setupCharts() {
        // Pie Chart setup
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleColor(Color.TRANSPARENT);
        pieChart.setHoleRadius(55f);
        pieChart.setTransparentCircleRadius(60f);
        pieChart.setDrawCenterText(true);
        pieChart.setCenterTextColor(Color.WHITE);
        pieChart.setCenterTextSize(14f);
        pieChart.setDrawEntryLabels(false);
        pieChart.setMinAngleForSlices(4f);
        pieChart.getDescription().setEnabled(false);
        pieChart.getLegend().setEnabled(false);
        pieChart.setRotationEnabled(false);
        pieChart.setHighlightPerTapEnabled(true);

        pieChart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                if (e instanceof PieEntry) {
                    PieEntry pieEntry = (PieEntry) e;
                    PieData data = pieChart.getData();
                    if (data != null) {
                        float total = 0;
                        for (int i = 0; i < data.getDataSet().getEntryCount(); i++) {
                            total += data.getDataSet().getEntryForIndex(i).getValue();
                        }
                        float percentage = (pieEntry.getValue() / total) * 100f;
                        String centerText = pieEntry.getLabel() + "\n" + String.format(Locale.getDefault(), "%.1f%%", percentage);
                        pieChart.setCenterText(centerText);
                    }
                }
            }

            @Override
            public void onNothingSelected() {
                pieChart.setCenterText("");
            }
        });

        // Bar Chart setup
        barChart.getDescription().setEnabled(false);
        barChart.setDrawGridBackground(false);
        barChart.setDrawBarShadow(false);
        barChart.setDrawValueAboveBar(true);
        barChart.setPinchZoom(false);
        barChart.setDoubleTapToZoomEnabled(false);
        barChart.setScaleEnabled(false);
        barChart.getLegend().setEnabled(false);

        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(Color.WHITE);
        xAxis.setTextSize(10f);
        xAxis.setGranularity(1f);

        YAxis leftAxis = barChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(0x20FFFFFF);
        leftAxis.setTextColor(Color.WHITE);
        leftAxis.setTextSize(10f);
        leftAxis.setAxisMinimum(0f);

        barChart.getAxisRight().setEnabled(false);
    }

    private void loadStatistics() {
        showLoading();

        ApiClient.getStatisticsService().getStatistics(currentPeriod)
                .enqueue(new Callback<ApiResponse<StatisticsData>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<StatisticsData>> call,
                                           Response<ApiResponse<StatisticsData>> response) {
                        if (response.isSuccessful() && response.body() != null &&
                                response.body().getData() != null) {
                            displayStatistics(response.body().getData());
                        } else {
                            showEmpty();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<StatisticsData>> call, Throwable t) {
                        Toast.makeText(StatisticsActivity.this,
                                "Eroare la încărcarea statisticilor", Toast.LENGTH_SHORT).show();
                        showEmpty();
                    }
                });
    }

    private void displayStatistics(StatisticsData data) {
        if (data.getSummary().getTransactionCount() == 0) {
            showEmpty();
            return;
        }

        showContent();

        // Summary
        StatisticsData.Summary summary = data.getSummary();
        tvTotalIncome.setText("+" + formatAmount(summary.getTotalIncome()) + " lei");
        tvTotalExpenses.setText("-" + formatAmount(summary.getTotalExpenses()) + " lei");

        double balance = summary.getBalance();
        String balanceText = (balance >= 0 ? "+" : "") + formatAmount(balance) + " lei";
        tvBalance.setText(balanceText);
        tvBalance.setTextColor(ContextCompat.getColor(this,
                balance >= 0 ? R.color.green_accent : R.color.error_red));

        // Pie Chart
        displayPieChart(data.getCategories());

        // Bar Chart
        displayBarChart(data.getMonthlyTrend());

        // Top Merchants
        displayTopMerchants(data.getTopMerchants());
    }

    private void displayPieChart(List<StatisticsData.CategoryStats> categories) {
        if (categories == null || categories.isEmpty()) {
            pieChart.setVisibility(View.GONE);
            return;
        }

        pieChart.setVisibility(View.VISIBLE);
        categoriesLegend.removeAllViews();

        List<PieEntry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        int colorIndex = 0;
        for (StatisticsData.CategoryStats category : categories) {
            if (category.getAmount() > 0) {
                entries.add(new PieEntry((float) category.getAmount(), translateCategory(category.getName())));
                colors.add(CHART_COLORS[colorIndex % CHART_COLORS.length]);

                // Add legend item
                addCategoryLegendItem(category, CHART_COLORS[colorIndex % CHART_COLORS.length]);
                colorIndex++;
            }
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(colors);
        dataSet.setDrawValues(false);
        dataSet.setSliceSpace(2f);
        dataSet.setSelectionShift(8f);
        dataSet.setHighlightEnabled(true);

        PieData pieData = new PieData(dataSet);
        pieChart.setData(pieData);
        pieChart.setCenterText("");
        pieChart.highlightValues(null);
        pieChart.invalidate();
    }

    private void addCategoryLegendItem(StatisticsData.CategoryStats category, int color) {
        View item = LayoutInflater.from(this).inflate(R.layout.item_category_legend, categoriesLegend, false);

        View colorDot = item.findViewById(R.id.colorDot);
        TextView tvName = item.findViewById(R.id.tvCategoryName);
        TextView tvAmount = item.findViewById(R.id.tvCategoryAmount);
        TextView tvPercentage = item.findViewById(R.id.tvCategoryPercentage);

        colorDot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        tvName.setText(translateCategory(category.getName()));
        tvAmount.setText(formatAmount(category.getAmount()) + " lei");
        tvPercentage.setText(formatPercentage(category.getPercentage()));

        categoriesLegend.addView(item);
    }

    private void displayBarChart(List<StatisticsData.MonthlyStats> monthlyTrend) {
        if (monthlyTrend == null || monthlyTrend.size() < 2) {
            monthlyTrendCard.setVisibility(View.GONE);
            return;
        }

        monthlyTrendCard.setVisibility(View.VISIBLE);
        barChart.setVisibility(View.VISIBLE);

        List<BarEntry> expenseEntries = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        for (int i = 0; i < monthlyTrend.size(); i++) {
            StatisticsData.MonthlyStats month = monthlyTrend.get(i);
            expenseEntries.add(new BarEntry(i, (float) month.getExpenses()));
            labels.add(formatMonthLabel(month.getMonth()));
        }

        BarDataSet expenseDataSet = new BarDataSet(expenseEntries, "Cheltuieli");
        expenseDataSet.setColor(CHART_COLORS[0]);
        expenseDataSet.setDrawValues(false);

        BarData barData = new BarData(expenseDataSet);
        barData.setBarWidth(0.6f);

        barChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        barChart.setData(barData);
        barChart.invalidate();
    }

    private void displayTopMerchants(List<StatisticsData.MerchantStats> merchants) {
        merchantsList.removeAllViews();

        if (merchants == null || merchants.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Nu există date");
            empty.setTextColor(ContextCompat.getColor(this, R.color.white_60));
            empty.setTextSize(14);
            merchantsList.addView(empty);
            return;
        }

        for (int i = 0; i < merchants.size(); i++) {
            StatisticsData.MerchantStats merchant = merchants.get(i);
            addMerchantItem(merchant, i + 1);
        }
    }

    private void addMerchantItem(StatisticsData.MerchantStats merchant, int position) {
        View item = LayoutInflater.from(this).inflate(R.layout.item_merchant_stats, merchantsList, false);

        TextView tvPosition = item.findViewById(R.id.tvPosition);
        CardView cardMerchantLogo = item.findViewById(R.id.cardMerchantLogo);
        ImageView ivMerchantLogo = item.findViewById(R.id.ivMerchantLogo);
        TextView tvName = item.findViewById(R.id.tvMerchantName);
        TextView tvAmount = item.findViewById(R.id.tvMerchantAmount);
        TextView tvCount = item.findViewById(R.id.tvTransactionCount);

        tvPosition.setText("#" + position);
        tvName.setText(merchant.getName());
        tvAmount.setText(formatAmount(merchant.getAmount()) + " lei");
        tvCount.setText(formatTransactionCount(merchant.getCount()));
        applyMerchantVisual(merchant, cardMerchantLogo, ivMerchantLogo);
        merchantsList.addView(item);
    }

    private void applyMerchantVisual(StatisticsData.MerchantStats merchant,
                                     CardView logoCard,
                                     ImageView logoView) {
        String logoUrl = merchant.getMerchantLogoUrl();
        if (logoUrl != null && !logoUrl.trim().isEmpty()) {
            logoCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.white));
            ImageViewCompat.setImageTintList(logoView, null);
            logoView.clearColorFilter();
            setImageSize(logoView, 30);
            logoView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

            if (RemoteImageLoader.load(logoUrl, logoView,
                    () -> applyMerchantFallbackIcon(merchant, logoCard, logoView))) {
                return;
            }
        }

        applyMerchantFallbackIcon(merchant, logoCard, logoView);
    }

    private void applyMerchantFallbackIcon(StatisticsData.MerchantStats merchant,
                                           CardView logoCard,
                                           ImageView logoView) {
        logoView.setTag(null);
        logoCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.white_10));
        setImageSize(logoView, 24);
        logoView.setScaleType(ImageView.ScaleType.CENTER);
        ImageViewCompat.setImageTintList(
                logoView,
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.white_60)));
        logoView.setImageResource(getCategoryIconResource(merchant.getCategoryIcon()));
    }

    private void setImageSize(ImageView imageView, int sizeDp) {
        ViewGroup.LayoutParams params = imageView.getLayoutParams();
        int sizePx = Math.round(sizeDp * getResources().getDisplayMetrics().density);
        if (params.width != sizePx || params.height != sizePx) {
            params.width = sizePx;
            params.height = sizePx;
            imageView.setLayoutParams(params);
        }
    }

    private String formatTransactionCount(int count) {
        return count == 1 ? "1 tranzacție" : count + " tranzacții";
    }

    private int getCategoryIconResource(String iconName) {
        if (iconName == null || iconName.isEmpty()) return R.drawable.ic_category_other;

        String category = iconName.trim().toLowerCase(Locale.ROOT);
        if (category.startsWith("ic_category_")) {
            category = category.substring("ic_category_".length());
        }

        switch (category) {
            case "food":
                return R.drawable.ic_category_food;
            case "shopping":
                return R.drawable.ic_category_shopping;
            case "transport":
                return R.drawable.ic_category_transport;
            case "entertainment":
                return R.drawable.ic_category_entertainment;
            case "groceries":
                return R.drawable.ic_category_groceries;
            case "health":
                return R.drawable.ic_category_health;
            case "utilities":
                return R.drawable.ic_category_utilities;
            case "travel":
                return R.drawable.ic_category_travel;
            case "services":
            case "telecom":
            case "internet":
                return R.drawable.ic_category_services;
            case "electronics":
                return R.drawable.ic_category_electronics;
            case "furniture":
                return R.drawable.ic_category_furniture;
            case "tv":
            case "subscriptions":
                return R.drawable.ic_category_entertainment;
            default:
                return R.drawable.ic_category_other;
        }
    }

    private void showLoading() {
        loadingState.setVisibility(View.VISIBLE);
        contentContainer.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
    }

    private void showContent() {
        loadingState.setVisibility(View.GONE);
        contentContainer.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.GONE);
    }

    private void showEmpty() {
        loadingState.setVisibility(View.GONE);
        contentContainer.setVisibility(View.GONE);
        emptyState.setVisibility(View.VISIBLE);
    }

    private String formatAmount(double amount) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.getDefault());
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        DecimalFormat df = new DecimalFormat("#,##0.00", symbols);
        return df.format(amount);
    }

    private String formatMonthLabel(String monthStr) {
        try {
            SimpleDateFormat input = new SimpleDateFormat("yyyy-MM", Locale.getDefault());
            Date date = input.parse(monthStr);
            SimpleDateFormat output = new SimpleDateFormat("MMM", new Locale("ro"));
            return output.format(date);
        } catch (ParseException e) {
            return monthStr;
        }
    }

    private String translateCategory(String name) {
        if (name == null) return "Altele";
        switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "food": return "Mâncare";
            case "shopping": return "Cumpărături";
            case "transport": return "Transport";
            case "entertainment": return "Divertisment";
            case "groceries": return "Alimente";
            case "health": return "Sănătate";
            case "utilities": return "Utilități";
            case "travel": return "Călătorii";
            case "services": return "Servicii";
            case "electronics": return "Electronice";
            case "furniture": return "Mobilier";
            case "transfers": return "Transferuri";
            case "exchange": return "Schimb valutar";
            case "other": return "Altele";
            default: return capitalizeCategory(name);
        }
    }

    private String formatPercentage(double percentage) {
        if (percentage > 0 && percentage < 1) {
            return "<1%";
        }
        if (percentage == Math.floor(percentage)) {
            return String.format(Locale.ROOT, "%.0f%%", percentage);
        }
        return String.format(Locale.ROOT, "%.1f%%", percentage);
    }

    private String capitalizeCategory(String name) {
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return "Altele";

        String[] words = trimmed.replace('_', ' ').split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(word.substring(0, 1).toUpperCase(Locale.ROOT));
            if (word.length() > 1) {
                result.append(word.substring(1).toLowerCase(Locale.ROOT));
            }
        }

        return result.length() > 0 ? result.toString() : "Altele";
    }
}
