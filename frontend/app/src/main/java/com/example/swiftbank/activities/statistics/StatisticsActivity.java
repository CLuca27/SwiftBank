package com.example.swiftbank.activities.statistics;

import android.content.Intent;
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
import com.github.mikephil.charting.components.Legend;
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
import com.github.mikephil.charting.formatter.ValueFormatter;
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
    private TextView btnByDay, btnByWeek, btnByMonth;
    private LinearLayout loadingState, contentContainer, emptyState, monthlyTrendCard, currencyCard, categoryCard, merchantsCard, selectedCategoryContainer;
    private LinearLayout categoriesLegend, merchantsList, currencyList;
    private TextView tvBaseCurrency, tvMainInsight, tvSecondaryInsight, tvSelectedCategoryName, tvSelectedCategoryAmount, btnViewCategoryTransactions;
    private TextView tvTotalIncome, tvTotalExpenses, tvBalance, tvIncomeComparison, tvExpensesComparison;
    private TextView tvTrendTitle;
    private PieChart pieChart;
    private BarChart barChart;
    private View selectedCategoryDot, trendLoadingState;

    private String currentPeriod = "this_month";
    private String currentGranularity = "day";
    private String baseCurrency = "RON";
    private TextView selectedPeriodButton;
    private TextView selectedGranularityButton;
    private StatisticsData currentData;
    private final List<StatisticsData.CategoryStats> pieChartCategories = new ArrayList<>();
    private StatisticsData.CategoryStats selectedPieCategory;

    private final int[] CHART_COLORS = {
            0xFF10994C,
            0xFF2196F3,
            0xFFFF9500,
            0xFF9C27B0,
            0xFFEF4444,
            0xFF00BFA5,
            0xFFFFC107,
            0xFFE91E63,
            0xFF00B8D4,
            0xFF6B7280
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
        btnByDay = findViewById(R.id.btnByDay);
        btnByWeek = findViewById(R.id.btnByWeek);
        btnByMonth = findViewById(R.id.btnByMonth);

        loadingState = findViewById(R.id.loadingState);
        contentContainer = findViewById(R.id.contentContainer);
        emptyState = findViewById(R.id.emptyState);
        monthlyTrendCard = findViewById(R.id.monthlyTrendCard);
        currencyCard = findViewById(R.id.currencyCard);
        categoryCard = findViewById(R.id.categoryCard);
        merchantsCard = findViewById(R.id.merchantsCard);
        selectedCategoryContainer = findViewById(R.id.selectedCategoryContainer);

        tvBaseCurrency = findViewById(R.id.tvBaseCurrency);
        tvMainInsight = findViewById(R.id.tvMainInsight);
        tvSecondaryInsight = findViewById(R.id.tvSecondaryInsight);
        tvTotalIncome = findViewById(R.id.tvTotalIncome);
        tvTotalExpenses = findViewById(R.id.tvTotalExpenses);
        tvBalance = findViewById(R.id.tvBalance);
        tvIncomeComparison = findViewById(R.id.tvIncomeComparison);
        tvExpensesComparison = findViewById(R.id.tvExpensesComparison);
        tvTrendTitle = findViewById(R.id.tvTrendTitle);
        tvSelectedCategoryName = findViewById(R.id.tvSelectedCategoryName);
        tvSelectedCategoryAmount = findViewById(R.id.tvSelectedCategoryAmount);
        btnViewCategoryTransactions = findViewById(R.id.btnViewCategoryTransactions);
        selectedCategoryDot = findViewById(R.id.selectedCategoryDot);
        trendLoadingState = findViewById(R.id.trendLoadingState);

        pieChart = findViewById(R.id.pieChart);
        barChart = findViewById(R.id.barChart);
        categoriesLegend = findViewById(R.id.categoriesLegend);
        merchantsList = findViewById(R.id.merchantsList);
        currencyList = findViewById(R.id.currencyList);

        selectedPeriodButton = btnThisMonth;
        selectedGranularityButton = btnByDay;
        updateGranularityOptionsForPeriod();
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnThisMonth.setOnClickListener(v -> selectPeriod("this_month", btnThisMonth));
        btnLastMonth.setOnClickListener(v -> selectPeriod("last_month", btnLastMonth));
        btnLast3Months.setOnClickListener(v -> selectPeriod("last_3_months", btnLast3Months));
        btnThisYear.setOnClickListener(v -> selectPeriod("this_year", btnThisYear));

        btnByDay.setOnClickListener(v -> selectGranularity("day", btnByDay));
        btnByWeek.setOnClickListener(v -> selectGranularity("week", btnByWeek));
        btnByMonth.setOnClickListener(v -> selectGranularity("month", btnByMonth));
        btnViewCategoryTransactions.setOnClickListener(v -> {
            if (selectedPieCategory != null) {
                openCategoryTransactions(selectedPieCategory);
            }
        });
    }

    private void selectPeriod(String period, TextView button) {
        if (period.equals(currentPeriod)) return;
        setChipSelected(selectedPeriodButton, false);
        setChipSelected(button, true);
        selectedPeriodButton = button;
        currentPeriod = period;
        updateGranularityOptionsForPeriod();
        loadStatistics();
    }

    private void selectGranularity(String granularity, TextView button) {
        if (granularity.equals(currentGranularity)) return;
        setChipSelected(selectedGranularityButton, false);
        setChipSelected(button, true);
        selectedGranularityButton = button;
        currentGranularity = granularity;
        loadTrendOnly();
    }

    private void setChipSelected(TextView chip, boolean selected) {
        chip.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        chip.setTextColor(ContextCompat.getColor(this, selected ? R.color.white : R.color.white_60));
    }

    private void updateGranularityOptionsForPeriod() {
        boolean singleMonthPeriod = isSingleMonthPeriod(currentPeriod);
        btnByMonth.setVisibility(singleMonthPeriod ? View.GONE : View.VISIBLE);

        if (singleMonthPeriod && "month".equals(currentGranularity)) {
            setChipSelected(selectedGranularityButton, false);
            currentGranularity = "day";
            selectedGranularityButton = btnByDay;
            setChipSelected(selectedGranularityButton, true);
        }
    }

    private boolean isSingleMonthPeriod(String period) {
        return "this_month".equals(period) || "last_month".equals(period);
    }

    private void setupCharts() {
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleColor(Color.TRANSPARENT);
        pieChart.setHoleRadius(54f);
        pieChart.setTransparentCircleRadius(60f);
        pieChart.setDrawCenterText(true);
        pieChart.setCenterTextColor(Color.WHITE);
        pieChart.setCenterTextSize(13f);
        pieChart.setDrawEntryLabels(false);
        pieChart.setMinAngleForSlices(4f);
        pieChart.getDescription().setEnabled(false);
        pieChart.getLegend().setEnabled(false);
        pieChart.setRotationEnabled(false);
        pieChart.setHighlightPerTapEnabled(true);
        pieChart.setNoDataText("");

        pieChart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                if (!(e instanceof PieEntry)) return;

                int index = Math.round(h.getX());
                if (index >= 0 && index < pieChartCategories.size()) {
                    showSelectedPieCategory(pieChartCategories.get(index));
                }
            }

            @Override
            public void onNothingSelected() {
                clearSelectedPieCategory();
            }
        });

        barChart.getDescription().setEnabled(false);
        barChart.setDrawGridBackground(false);
        barChart.setDrawBarShadow(false);
        barChart.setDrawValueAboveBar(false);
        barChart.setPinchZoom(false);
        barChart.setDoubleTapToZoomEnabled(true);
        barChart.setScaleEnabled(true);
        barChart.setScaleYEnabled(false);
        barChart.setDragEnabled(true);
        barChart.setExtraOffsets(0f, 10f, 8f, 8f);

        Legend legend = barChart.getLegend();
        legend.setTextColor(Color.WHITE);
        legend.setTextSize(11f);
        legend.setForm(Legend.LegendForm.CIRCLE);
        legend.setFormSize(8f);
        legend.setXEntrySpace(12f);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.RIGHT);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);

        barChart.setNoDataText("");

        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(Color.WHITE);
        xAxis.setTextSize(10f);
        xAxis.setGranularity(1f);
        xAxis.setLabelRotationAngle(-18f);

        YAxis leftAxis = barChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(0x20FFFFFF);
        leftAxis.setTextColor(Color.WHITE);
        leftAxis.setTextSize(10f);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setSpaceTop(18f);
        leftAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return formatAxisAmount(value);
            }
        });

        barChart.getAxisRight().setEnabled(false);
    }

    private void loadStatistics() {
        showLoading();

        ApiClient.getStatisticsService().getStatistics(currentPeriod, currentGranularity)
                .enqueue(new Callback<ApiResponse<StatisticsData>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<StatisticsData>> call,
                                           Response<ApiResponse<StatisticsData>> response) {
                        if (response.isSuccessful()
                                && response.body() != null
                                && response.body().getData() != null) {
                            displayStatistics(response.body().getData());
                        } else {
                            showEmpty();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<StatisticsData>> call, Throwable t) {
                        Toast.makeText(StatisticsActivity.this,
                                "Eroare la \u00EEnc\u0103rcarea statisticilor", Toast.LENGTH_SHORT).show();
                        showEmpty();
                    }
                });
    }

    private void loadTrendOnly() {
        if (currentData == null) {
            loadStatistics();
            return;
        }

        showTrendLoading();

        ApiClient.getStatisticsService().getStatistics(currentPeriod, currentGranularity)
                .enqueue(new Callback<ApiResponse<StatisticsData>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<StatisticsData>> call,
                                           Response<ApiResponse<StatisticsData>> response) {
                        hideTrendLoading();
                        if (response.isSuccessful()
                                && response.body() != null
                                && response.body().getData() != null) {
                            StatisticsData data = response.body().getData();
                            currentData = data;
                            baseCurrency = data.getBaseCurrency() != null ? data.getBaseCurrency() : "RON";
                            tvBaseCurrency.setText("Totaluri globale \u00EEn " + baseCurrency);
                            displayBarChart(data.getMonthlyTrend());
                        } else {
                            Toast.makeText(StatisticsActivity.this,
                                    "Nu am putut actualiza graficul", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<StatisticsData>> call, Throwable t) {
                        hideTrendLoading();
                        Toast.makeText(StatisticsActivity.this,
                                "Nu am putut actualiza graficul", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void displayStatistics(StatisticsData data) {
        if (data.getSummary() == null || data.getSummary().getTransactionCount() == 0) {
            showEmpty();
            return;
        }

        currentData = data;
        baseCurrency = data.getBaseCurrency() != null ? data.getBaseCurrency() : "RON";
        tvBaseCurrency.setText("Totaluri globale \u00EEn " + baseCurrency);
        showContent();

        displayInsights(data);
        displaySummary(data);
        displayCurrencyBreakdown(data.getCurrencyBreakdown());
        displayPieChart(data);
        displayBarChart(data.getMonthlyTrend());
        displayTopMerchants(data.getTopMerchants());
    }

    private void displayInsights(StatisticsData data) {
        List<StatisticsData.Insight> insights = data.getInsights();

        if (insights != null && !insights.isEmpty()) {
            tvMainInsight.setText(insights.get(0).getMessage());
            if (insights.size() > 1) {
                tvSecondaryInsight.setVisibility(View.VISIBLE);
                tvSecondaryInsight.setText(insights.get(1).getMessage());
            } else {
                tvSecondaryInsight.setVisibility(View.GONE);
            }
            return;
        }

        tvMainInsight.setText("Ai " + data.getSummary().getTransactionCount() + " tranzac\u021Bii \u00EEn perioada selectat\u0103.");
        tvSecondaryInsight.setVisibility(View.GONE);
    }

    private void displaySummary(StatisticsData data) {
        StatisticsData.Summary summary = data.getSummary();
        StatisticsData.Comparison comparison = data.getComparison();

        tvTotalIncome.setText("+" + formatAmount(summary.getTotalIncome(), baseCurrency));
        tvTotalExpenses.setText("-" + formatAmount(summary.getTotalExpenses(), baseCurrency));

        double balance = summary.getBalance();
        tvBalance.setText((balance >= 0 ? "+" : "") + formatAmount(balance, baseCurrency));
        tvBalance.setTextColor(ContextCompat.getColor(this,
                balance >= 0 ? R.color.green_accent : R.color.error_red));

        if (comparison != null && comparison.hasPreviousPeriod()) {
            tvIncomeComparison.setText(formatComparison(comparison.getIncomeChangePercent(), comparison.getIncomeChange()));
            tvIncomeComparison.setTextColor(ContextCompat.getColor(this,
                    comparison.getIncomeChange() < 0 ? R.color.error_red : R.color.green_accent));

            tvExpensesComparison.setText(formatComparison(comparison.getExpensesChangePercent(), comparison.getExpensesChange()));
            tvExpensesComparison.setTextColor(ContextCompat.getColor(this,
                    comparison.getExpensesChange() > 0 ? R.color.error_red : R.color.green_accent));
        } else {
            tvIncomeComparison.setText("");
            tvExpensesComparison.setText("");
        }
    }

    private void displayCurrencyBreakdown(List<StatisticsData.CurrencyStats> currencies) {
        currencyList.removeAllViews();

        if (currencies == null || currencies.isEmpty()) {
            currencyCard.setVisibility(View.GONE);
            return;
        }

        currencyCard.setVisibility(View.VISIBLE);

        for (StatisticsData.CurrencyStats currency : currencies) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(8), 0, dp(8));

            TextView name = new TextView(this);
            name.setText(currency.getCurrency());
            name.setTextColor(ContextCompat.getColor(this, R.color.white));
            name.setTextSize(14);
            name.setTypeface(null, android.graphics.Typeface.BOLD);

            TextView detail = new TextView(this);
            detail.setText(buildCurrencyDetail(currency));
            detail.setTextColor(ContextCompat.getColor(this, R.color.white_60));
            detail.setTextSize(12);
            detail.setLineSpacing(dp(2), 1f);

            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.addView(name);
            texts.addView(detail);

            row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            currencyList.addView(row);
        }
    }

    private String buildCurrencyDetail(StatisticsData.CurrencyStats currency) {
        String currencyCode = currency.getCurrency();
        String mainAmount = formatAmount(currency.getExpenses(), currencyCode);
        if (currencyCode != null && currencyCode.equalsIgnoreCase(baseCurrency)) {
            return "Cheltuieli \u00EEn " + currencyCode + ": " + mainAmount;
        }

        return "Cheltuieli \u00EEn " + currencyCode + ": " + mainAmount
                + "\nEchivalent \u00EEn " + baseCurrency + ": "
                + formatAmount(currency.getConvertedExpenses(), baseCurrency);
    }

    private void displayPieChart(StatisticsData data) {
        List<StatisticsData.CategoryStats> categories = data.getCategories();
        categoriesLegend.removeAllViews();
        pieChartCategories.clear();
        selectedPieCategory = null;

        if (categories == null || categories.isEmpty()) {
            categoryCard.setVisibility(View.GONE);
            pieChart.setVisibility(View.GONE);
            selectedCategoryContainer.setVisibility(View.GONE);
            return;
        }

        categoryCard.setVisibility(View.VISIBLE);
        pieChart.setVisibility(View.VISIBLE);

        List<PieEntry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        int colorIndex = 0;
        for (StatisticsData.CategoryStats category : categories) {
            if (category.getAmount() <= 0) continue;
            entries.add(new PieEntry((float) category.getAmount(), translateCategory(category.getName())));
            colors.add(CHART_COLORS[colorIndex % CHART_COLORS.length]);
            pieChartCategories.add(category);
            addCategoryLegendItem(category, CHART_COLORS[colorIndex % CHART_COLORS.length]);
            colorIndex++;
        }

        if (entries.isEmpty()) {
            categoryCard.setVisibility(View.GONE);
            pieChart.setVisibility(View.GONE);
            selectedCategoryContainer.setVisibility(View.GONE);
            return;
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(colors);
        dataSet.setDrawValues(false);
        dataSet.setSliceSpace(2f);
        dataSet.setSelectionShift(8f);
        dataSet.setHighlightEnabled(true);

        pieChart.setData(new PieData(dataSet));
        clearSelectedPieCategory();
        pieChart.highlightValues(null);
        pieChart.invalidate();
    }

    private void showSelectedPieCategory(StatisticsData.CategoryStats category) {
        selectedPieCategory = category;
        String categoryName = translateCategory(category.getName());
        String amount = formatAmount(category.getAmount(), baseCurrency);

        pieChart.setCenterText(categoryName + "\n" + amount);
        tvSelectedCategoryName.setText(categoryName);
        tvSelectedCategoryAmount.setText(amount + " \u00EEn perioada selectat\u0103");
        selectedCategoryContainer.setVisibility(View.VISIBLE);

        int index = pieChartCategories.indexOf(category);
        if (index >= 0) {
            selectedCategoryDot.setBackgroundTintList(ColorStateList.valueOf(CHART_COLORS[index % CHART_COLORS.length]));
        }
    }

    private void clearSelectedPieCategory() {
        selectedPieCategory = null;
        pieChart.setCenterText("Total\n" + formatAmount(sumCategoryAmounts(pieChartCategories), baseCurrency));
        if (selectedCategoryContainer != null) {
            selectedCategoryContainer.setVisibility(View.GONE);
        }
    }

    private void addCategoryLegendItem(StatisticsData.CategoryStats category, int color) {
        View item = LayoutInflater.from(this).inflate(R.layout.item_category_legend, categoriesLegend, false);

        View colorDot = item.findViewById(R.id.colorDot);
        TextView tvName = item.findViewById(R.id.tvCategoryName);
        TextView tvCaption = item.findViewById(R.id.tvCategoryCaption);
        TextView tvAmount = item.findViewById(R.id.tvCategoryAmount);
        TextView tvPercentage = item.findViewById(R.id.tvCategoryPercentage);

        colorDot.setBackgroundTintList(ColorStateList.valueOf(color));
        tvName.setText(translateCategory(category.getName()));
        tvCaption.setText(formatTransactionCount(category.getCount()));
        tvAmount.setText(formatAmount(category.getAmount(), baseCurrency));
        tvPercentage.setText(formatPercentage(category.getPercentage()));
        item.setOnClickListener(v -> showSelectedPieCategory(category));

        categoriesLegend.addView(item);
    }

    private void openCategoryTransactions(StatisticsData.CategoryStats category) {
        Intent intent = new Intent(this, StatisticsTransactionsActivity.class);
        intent.putExtra(StatisticsTransactionsActivity.EXTRA_SCREEN_TITLE, buildCategoryTitle(category.getName()));
        intent.putExtra(StatisticsTransactionsActivity.EXTRA_FILTER_CATEGORY, category.getName());
        putStatisticsRange(intent);
        startActivity(intent);
    }

    private void displayBarChart(List<StatisticsData.MonthlyStats> trend) {
        if (trend == null || trend.isEmpty()) {
            monthlyTrendCard.setVisibility(View.GONE);
            barChart.clear();
            return;
        }

        monthlyTrendCard.setVisibility(View.VISIBLE);
        tvTrendTitle.setText("Trend pe " + getGranularityLabel() + " (" + baseCurrency + ")");

        List<BarEntry> incomeEntries = new ArrayList<>();
        List<BarEntry> expenseEntries = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        for (int i = 0; i < trend.size(); i++) {
            StatisticsData.MonthlyStats point = trend.get(i);
            incomeEntries.add(new BarEntry(i, (float) point.getIncome()));
            expenseEntries.add(new BarEntry(i, (float) point.getExpenses()));
            labels.add(point.getLabel() != null ? point.getLabel() : formatMonthLabel(point.getMonth()));
        }

        BarDataSet incomeDataSet = new BarDataSet(incomeEntries, "Venituri");
        incomeDataSet.setColor(ContextCompat.getColor(this, R.color.green_accent));
        incomeDataSet.setDrawValues(false);

        BarDataSet expenseDataSet = new BarDataSet(expenseEntries, "Cheltuieli");
        expenseDataSet.setColor(ContextCompat.getColor(this, R.color.error_red));
        expenseDataSet.setDrawValues(false);

        BarData barData = new BarData(incomeDataSet, expenseDataSet);
        float groupSpace = 0.28f;
        float barSpace = 0.04f;
        float barWidth = 0.32f;
        barData.setBarWidth(barWidth);

        float groupWidth = barData.getGroupWidth(groupSpace, barSpace);
        float visibleWindow = getVisibleTrendWindowSize(trend.size());
        int firstValueIndex = findFirstTrendValueIndex(trend);

        barChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        barChart.getXAxis().setLabelCount(Math.min(labels.size(), 6), false);
        barChart.getXAxis().setAxisMinimum(0f);
        barChart.getXAxis().setAxisMaximum(groupWidth * trend.size());
        barChart.setData(barData);
        barChart.groupBars(0f, groupSpace, barSpace);
        barChart.fitScreen();
        barChart.setVisibleXRangeMaximum(visibleWindow);
        barChart.moveViewToX(firstValueIndex > 0 ? firstValueIndex * groupWidth : 0f);
        barChart.animateY(450);
        barChart.invalidate();
    }

    private float getVisibleTrendWindowSize(int pointCount) {
        float maxWindow;
        switch (currentGranularity) {
            case "day":
                maxWindow = 7f;
                break;
            case "week":
                maxWindow = 6f;
                break;
            case "month":
            default:
                maxWindow = 6f;
                break;
        }

        return Math.max(1f, Math.min((float) pointCount, maxWindow));
    }

    private int findFirstTrendValueIndex(List<StatisticsData.MonthlyStats> trend) {
        for (int i = 0; i < trend.size(); i++) {
            StatisticsData.MonthlyStats point = trend.get(i);
            if (Math.abs(point.getIncome()) > 0.009 || Math.abs(point.getExpenses()) > 0.009) {
                return i;
            }
        }

        return 0;
    }

    private void displayTopMerchants(List<StatisticsData.MerchantStats> merchants) {
        merchantsList.removeAllViews();

        if (merchants == null || merchants.isEmpty()) {
            merchantsCard.setVisibility(View.GONE);
            return;
        }

        merchantsCard.setVisibility(View.VISIBLE);
        for (int i = 0; i < Math.min(merchants.size(), 10); i++) {
            addMerchantItem(merchants.get(i), i + 1);
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
        tvAmount.setText(formatAmount(merchant.getAmount(), baseCurrency));
        tvCount.setText(formatTransactionCount(merchant.getCount()));
        applyMerchantVisual(merchant, cardMerchantLogo, ivMerchantLogo);
        item.setOnClickListener(v -> openMerchantTransactions(merchant));
        merchantsList.addView(item);
    }

    private void openMerchantTransactions(StatisticsData.MerchantStats merchant) {
        Intent intent = new Intent(this, StatisticsTransactionsActivity.class);
        intent.putExtra(StatisticsTransactionsActivity.EXTRA_SCREEN_TITLE, "Cheltuieli la " + merchant.getName());
        intent.putExtra(StatisticsTransactionsActivity.EXTRA_FILTER_MERCHANT, merchant.getName());
        putStatisticsRange(intent);
        startActivity(intent);
    }

    private void putStatisticsRange(Intent intent) {
        if (currentData == null) return;

        intent.putExtra(StatisticsTransactionsActivity.EXTRA_START_DATE, currentData.getStartDate());
        intent.putExtra(StatisticsTransactionsActivity.EXTRA_END_DATE, currentData.getEndDate());
        intent.putExtra(StatisticsTransactionsActivity.EXTRA_ALL_ACCOUNTS, true);
    }

    private String buildCategoryTitle(String categoryName) {
        switch (normalizeCategory(categoryName)) {
            case "transfers": return "Cheltuieli cu transferurile";
            case "tv": return "Cheltuieli cu serviciile TV";
            case "internet": return "Cheltuieli cu internetul";
            case "utilities": return "Cheltuieli cu utilit\u0103\u021Bile";
            case "telecom": return "Cheltuieli cu telecomunica\u021Biile";
            case "energy":
            case "electricity": return "Cheltuieli cu energia";
            case "gas": return "Cheltuieli cu gazele";
            case "groceries": return "Cheltuieli cu alimentele";
            case "food": return "Cheltuieli cu m\u00E2ncarea";
            case "transport": return "Cheltuieli cu transportul";
            case "health": return "Cheltuieli pentru s\u0103n\u0103tate";
            case "shopping": return "Cheltuieli pentru cump\u0103r\u0103turi";
            case "entertainment": return "Cheltuieli pentru divertisment";
            case "travel": return "Cheltuieli pentru c\u0103l\u0103torii";
            case "services": return "Cheltuieli pentru servicii";
            case "subscriptions": return "Cheltuieli cu abonamentele";
            case "electronics": return "Cheltuieli pentru electronice";
            case "furniture": return "Cheltuieli pentru mobilier";
            case "exchange": return "Schimburi valutare";
            case "other": return "Alte cheltuieli";
            default: return "Cheltuieli - " + translateCategory(categoryName);
        }
    }

    private String normalizeCategory(String categoryName) {
        return categoryName == null ? "" : categoryName.trim().toLowerCase(Locale.ROOT);
    }

    private void appendExamples(StringBuilder detail, List<StatisticsData.TransactionExample> examples) {
        if (examples == null || examples.isEmpty()) return;

        detail.append("\nExemple recente: ");
        int count = Math.min(examples.size(), 2);
        for (int i = 0; i < count; i++) {
            StatisticsData.TransactionExample example = examples.get(i);
            if (i > 0) detail.append("; ");
            detail.append(example.getTitle())
                    .append(" ")
                    .append(formatAmount(example.getAmount(), example.getCurrency() != null ? example.getCurrency() : baseCurrency));
        }
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

    private double sumCategoryAmounts(List<StatisticsData.CategoryStats> categories) {
        double total = 0;
        for (StatisticsData.CategoryStats category : categories) {
            total += category.getAmount();
        }
        return total;
    }

    private String formatComparison(Double percent, double amount) {
        if (percent == null || amount == 0) {
            return "";
        }

        String direction = amount > 0 ? "+" : "-";
        double abs = Math.abs(percent);
        String value = abs == Math.floor(abs)
                ? String.format(Locale.ROOT, "%.0f%%", abs)
                : String.format(Locale.ROOT, "%.1f%%", abs);
        return direction + value + " fa\u021B\u0103 de perioada anterioar\u0103";
    }

    private String formatTransactionCount(int count) {
        return count == 1 ? "1 tranzac\u021Bie" : count + " tranzac\u021Bii";
    }

    private String formatAxisAmount(float amount) {
        double value = Math.abs(amount);
        if (value >= 1_000_000) {
            return trimCompactNumber(value / 1_000_000) + "M " + baseCurrency;
        }
        if (value >= 1_000) {
            return trimCompactNumber(value / 1_000) + "k " + baseCurrency;
        }
        return String.format(Locale.ROOT, "%.0f %s", value, baseCurrency);
    }

    private String trimCompactNumber(double value) {
        return value == Math.floor(value)
                ? String.format(Locale.ROOT, "%.0f", value)
                : String.format(Locale.ROOT, "%.1f", value);
    }

    private String formatAmount(double amount, String currency) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.getDefault());
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        DecimalFormat df = new DecimalFormat("#,##0.00", symbols);
        return df.format(Math.abs(amount)) + " " + (currency != null ? currency : baseCurrency);
    }

    private String getGranularityLabel() {
        switch (currentGranularity) {
            case "week":
                return "s\u0103pt\u0103m\u00E2ni";
            case "month":
                return "luni";
            case "day":
            default:
                return "zile";
        }
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
            case "energy":
            case "electricity":
            case "gas":
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

    private String translateCategory(String name) {
        if (name == null) return "Altele";
        switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "food": return "M\u00E2ncare";
            case "shopping": return "Cump\u0103r\u0103turi";
            case "transport": return "Transport";
            case "entertainment": return "Divertisment";
            case "groceries": return "Alimente";
            case "health": return "S\u0103n\u0103tate";
            case "utilities": return "Utilit\u0103\u021Bi";
            case "travel": return "C\u0103l\u0103torii";
            case "services": return "Servicii";
            case "telecom": return "Telecomunica\u021Bii";
            case "internet": return "Internet";
            case "tv": return "TV";
            case "subscriptions": return "Abonamente";
            case "energy": return "Energie";
            case "electricity": return "Energie";
            case "gas": return "Gaze";
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showLoading() {
        hideTrendLoading();
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
        hideTrendLoading();
        loadingState.setVisibility(View.GONE);
        contentContainer.setVisibility(View.GONE);
        emptyState.setVisibility(View.VISIBLE);
    }

    private void showTrendLoading() {
        if (trendLoadingState != null) {
            trendLoadingState.setVisibility(View.VISIBLE);
        }
        if (barChart != null) {
            barChart.animate().alpha(0.28f).setDuration(120).start();
        }
    }

    private void hideTrendLoading() {
        if (trendLoadingState != null) {
            trendLoadingState.setVisibility(View.GONE);
        }
        if (barChart != null) {
            barChart.animate().alpha(1f).setDuration(120).start();
        }
    }
}
