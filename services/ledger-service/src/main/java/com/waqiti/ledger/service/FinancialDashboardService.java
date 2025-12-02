package com.waqiti.ledger.service;

import com.waqiti.ledger.domain.Account;
import com.waqiti.ledger.domain.LedgerEntry;
import com.waqiti.ledger.dto.*;
import com.waqiti.ledger.repository.AccountRepository;
import com.waqiti.ledger.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Financial Dashboard Service
 * 
 * Provides comprehensive financial analytics and dashboard functionality including:
 * - Real-time financial KPIs and metrics
 * - Trend analysis and forecasting
 * - Cash flow projections
 * - Profitability analysis
 * - Financial health scoring
 * - Comparative period analysis
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FinancialDashboardService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final BalanceCalculationService balanceCalculationService;
    private final IncomeStatementService incomeStatementService;
    private final BalanceSheetService balanceSheetService;
    private final CashFlowStatementService cashFlowStatementService;

    /**
     * Gets comprehensive financial dashboard data
     */
    @Cacheable(value = "financialDashboard", key = "#request.dashboardType + '_' + #request.periodEndDate")
    public FinancialDashboardResponse getFinancialDashboard(FinancialDashboardRequest request) {
        try {
            log.info("Generating financial dashboard for period ending: {}", request.getPeriodEndDate());
            
            // Calculate KPIs
            FinancialKPIs kpis = calculateFinancialKPIs(request.getPeriodStartDate(), request.getPeriodEndDate());
            
            // Get revenue trends
            RevenueTrends revenueTrends = analyzeRevenueTrends(request.getPeriodStartDate(), request.getPeriodEndDate());
            
            // Get expense analysis
            ExpenseAnalysis expenseAnalysis = analyzeExpenses(request.getPeriodStartDate(), request.getPeriodEndDate());
            
            // Calculate profitability metrics
            ProfitabilityMetrics profitability = calculateProfitabilityMetrics(
                request.getPeriodStartDate(), request.getPeriodEndDate());
            
            // Get cash flow analysis
            CashFlowAnalysis cashFlowAnalysis = analyzeCashFlow(request.getPeriodStartDate(), request.getPeriodEndDate());
            
            // Calculate financial ratios
            FinancialRatios ratios = calculateFinancialRatios(request.getPeriodEndDate());
            
            // Get account balances summary
            AccountBalancesSummary balancesSummary = getAccountBalancesSummary(request.getPeriodEndDate());
            
            // Generate comparative analysis if requested
            ComparativeAnalysis comparativeAnalysis = null;
            if (request.isIncludeComparative()) {
                comparativeAnalysis = generateComparativeAnalysis(
                    request.getPeriodStartDate(), request.getPeriodEndDate(),
                    request.getComparativePeriodStart(), request.getComparativePeriodEnd());
            }
            
            // Calculate financial health score
            FinancialHealthScore healthScore = calculateFinancialHealthScore(kpis, ratios, cashFlowAnalysis);
            
            // Generate insights and recommendations
            List<FinancialInsight> insights = generateFinancialInsights(
                kpis, revenueTrends, expenseAnalysis, profitability, cashFlowAnalysis);
            
            return FinancialDashboardResponse.builder()
                .dashboardId(UUID.randomUUID())
                .periodStartDate(request.getPeriodStartDate())
                .periodEndDate(request.getPeriodEndDate())
                .kpis(kpis)
                .revenueTrends(revenueTrends)
                .expenseAnalysis(expenseAnalysis)
                .profitabilityMetrics(profitability)
                .cashFlowAnalysis(cashFlowAnalysis)
                .financialRatios(ratios)
                .accountBalancesSummary(balancesSummary)
                .comparativeAnalysis(comparativeAnalysis)
                .healthScore(healthScore)
                .insights(insights)
                .generatedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to generate financial dashboard", e);
            throw new RuntimeException("Failed to generate financial dashboard", e);
        }
    }

    /**
     * Gets real-time financial KPIs
     */
    public FinancialKPIs calculateFinancialKPIs(LocalDate startDate, LocalDate endDate) {
        try {
            // Get income statement for the period
            IncomeStatementResponse incomeStatement = incomeStatementService.generateIncomeStatement(
                startDate, endDate, IncomeStatementService.IncomeStatementFormat.MULTI_STEP);
            
            // Get balance sheet as of period end
            BalanceSheetResponse balanceSheet = balanceSheetService.generateBalanceSheet(
                endDate, BalanceSheetService.BalanceSheetFormat.STANDARD);
            
            // Calculate KPIs
            BigDecimal revenue = incomeStatement.getTotalRevenue();
            BigDecimal expenses = incomeStatement.getTotalExpenses();
            BigDecimal netIncome = incomeStatement.getNetIncome();
            BigDecimal totalAssets = balanceSheet.getTotalAssets();
            BigDecimal totalLiabilities = balanceSheet.getTotalLiabilities();
            BigDecimal equity = balanceSheet.getTotalEquity();
            
            // Calculate growth rates
            BigDecimal revenueGrowth = calculateGrowthRate(revenue, startDate, endDate);
            BigDecimal profitGrowth = calculateGrowthRate(netIncome, startDate, endDate);
            
            // Calculate margins
            BigDecimal grossMargin = revenue.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO :
                incomeStatement.getGrossProfit().divide(revenue, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            
            BigDecimal netMargin = revenue.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO :
                netIncome.divide(revenue, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            
            // Calculate efficiency metrics
            BigDecimal assetTurnover = totalAssets.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO :
                revenue.divide(totalAssets, 4, RoundingMode.HALF_UP);
            
            BigDecimal returnOnAssets = totalAssets.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO :
                netIncome.divide(totalAssets, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            
            BigDecimal returnOnEquity = equity.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO :
                netIncome.divide(equity, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            
            // Calculate liquidity metrics
            BigDecimal currentRatio = calculateCurrentRatio(balanceSheet);
            BigDecimal quickRatio = calculateQuickRatio(balanceSheet);
            
            // Calculate leverage metrics
            BigDecimal debtToEquity = equity.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO :
                totalLiabilities.divide(equity, 4, RoundingMode.HALF_UP);
            
            return FinancialKPIs.builder()
                .revenue(revenue)
                .expenses(expenses)
                .netIncome(netIncome)
                .grossMargin(grossMargin)
                .netMargin(netMargin)
                .revenueGrowth(revenueGrowth)
                .profitGrowth(profitGrowth)
                .totalAssets(totalAssets)
                .totalLiabilities(totalLiabilities)
                .equity(equity)
                .currentRatio(currentRatio)
                .quickRatio(quickRatio)
                .debtToEquity(debtToEquity)
                .returnOnAssets(returnOnAssets)
                .returnOnEquity(returnOnEquity)
                .assetTurnover(assetTurnover)
                .build();
                
        } catch (Exception e) {
            log.error("Failed to calculate financial KPIs", e);
            throw new RuntimeException("Failed to calculate KPIs", e);
        }
    }

    /**
     * Analyzes revenue trends
     */
    public RevenueTrends analyzeRevenueTrends(LocalDate startDate, LocalDate endDate) {
        try {
            List<Account> revenueAccounts = accountRepository.findByAccountTypeInAndIsActiveTrue(
                Arrays.asList(Account.AccountType.REVENUE, Account.AccountType.OPERATING_REVENUE));
            
            // Calculate monthly revenue
            Map<String, BigDecimal> monthlyRevenue = calculateMonthlyRevenue(revenueAccounts, startDate, endDate);
            
            // Calculate revenue by category
            Map<String, BigDecimal> revenueByCategory = calculateRevenueByCategory(revenueAccounts, startDate, endDate);
            
            // Calculate top revenue sources
            List<RevenueSource> topRevenueSources = identifyTopRevenueSources(revenueAccounts, startDate, endDate);
            
            // Calculate growth metrics
            BigDecimal averageMonthlyRevenue = calculateAverageMonthlyRevenue(monthlyRevenue);
            BigDecimal revenueVolatility = calculateRevenueVolatility(monthlyRevenue);
            String revenueTrend = determineRevenueTrend(monthlyRevenue);
            
            // Generate revenue forecast
            BigDecimal forecastedRevenue = forecastRevenue(monthlyRevenue, 3); // 3 month forecast
            
            return RevenueTrends.builder()
                .monthlyRevenue(monthlyRevenue)
                .revenueByCategory(revenueByCategory)
                .topRevenueSources(topRevenueSources)
                .averageMonthlyRevenue(averageMonthlyRevenue)
                .revenueVolatility(revenueVolatility)
                .trend(revenueTrend)
                .forecastedRevenue(forecastedRevenue)
                .build();
                
        } catch (Exception e) {
            log.error("Failed to analyze revenue trends", e);
            throw new RuntimeException("Failed to analyze revenue trends", e);
        }
    }

    /**
     * Analyzes expenses
     */
    public ExpenseAnalysis analyzeExpenses(LocalDate startDate, LocalDate endDate) {
        try {
            List<Account> expenseAccounts = accountRepository.findByAccountTypeInAndIsActiveTrue(
                Arrays.asList(Account.AccountType.EXPENSE, Account.AccountType.OPERATING_EXPENSE));
            
            // Calculate expense categories
            Map<String, BigDecimal> expenseByCategory = calculateExpenseByCategory(expenseAccounts, startDate, endDate);
            
            // Identify top expense items
            List<ExpenseItem> topExpenses = identifyTopExpenses(expenseAccounts, startDate, endDate);
            
            // Calculate expense ratios
            BigDecimal totalExpenses = expenseByCategory.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            Map<String, BigDecimal> expenseRatios = new HashMap<>();
            for (Map.Entry<String, BigDecimal> entry : expenseByCategory.entrySet()) {
                BigDecimal ratio = totalExpenses.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO :
                    entry.getValue().divide(totalExpenses, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                expenseRatios.put(entry.getKey(), ratio);
            }
            
            // Identify cost saving opportunities
            List<CostSavingOpportunity> costSavings = identifyCostSavingOpportunities(
                expenseAccounts, startDate, endDate);
            
            return ExpenseAnalysis.builder()
                .expenseByCategory(expenseByCategory)
                .topExpenses(topExpenses)
                .totalExpenses(totalExpenses)
                .expenseRatios(expenseRatios)
                .costSavingOpportunities(costSavings)
                .build();
                
        } catch (Exception e) {
            log.error("Failed to analyze expenses", e);
            throw new RuntimeException("Failed to analyze expenses", e);
        }
    }

    /**
     * Calculates profitability metrics
     */
    public ProfitabilityMetrics calculateProfitabilityMetrics(LocalDate startDate, LocalDate endDate) {
        try {
            IncomeStatementResponse incomeStatement = incomeStatementService.generateIncomeStatement(
                startDate, endDate, IncomeStatementService.IncomeStatementFormat.MULTI_STEP);
            
            BigDecimal revenue = incomeStatement.getTotalRevenue();
            BigDecimal grossProfit = incomeStatement.getGrossProfit();
            BigDecimal operatingIncome = incomeStatement.getOperatingIncome();
            BigDecimal netIncome = incomeStatement.getNetIncome();
            
            // Calculate margins
            BigDecimal grossProfitMargin = revenue.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO :
                grossProfit.divide(revenue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            
            BigDecimal operatingMargin = revenue.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO :
                operatingIncome.divide(revenue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            
            BigDecimal netProfitMargin = revenue.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO :
                netIncome.divide(revenue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            
            // Calculate EBITDA
            BigDecimal ebitda = calculateEBITDA(incomeStatement);
            BigDecimal ebitdaMargin = revenue.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO :
                ebitda.divide(revenue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            
            // Determine profitability trend
            String profitabilityTrend = determineProfitabilityTrend(startDate, endDate);
            
            return ProfitabilityMetrics.builder()
                .grossProfit(grossProfit)
                .operatingIncome(operatingIncome)
                .netIncome(netIncome)
                .ebitda(ebitda)
                .grossProfitMargin(grossProfitMargin)
                .operatingMargin(operatingMargin)
                .netProfitMargin(netProfitMargin)
                .ebitdaMargin(ebitdaMargin)
                .trend(profitabilityTrend)
                .build();
                
        } catch (Exception e) {
            log.error("Failed to calculate profitability metrics", e);
            throw new RuntimeException("Failed to calculate profitability metrics", e);
        }
    }

    /**
     * Analyzes cash flow
     */
    public CashFlowAnalysis analyzeCashFlow(LocalDate startDate, LocalDate endDate) {
        try {
            CashFlowStatementResponse cashFlow = cashFlowStatementService.generateCashFlowStatement(
                startDate, endDate, CashFlowStatementService.CashFlowMethod.INDIRECT);
            
            BigDecimal operatingCashFlow = cashFlow.getNetOperatingCashFlow();
            BigDecimal investingCashFlow = cashFlow.getNetInvestingCashFlow();
            BigDecimal financingCashFlow = cashFlow.getNetFinancingCashFlow();
            BigDecimal freeCashFlow = cashFlow.getFreeCashFlow();
            
            // Calculate cash burn rate (for negative cash flow)
            BigDecimal cashBurnRate = calculateCashBurnRate(operatingCashFlow, startDate, endDate);
            
            // Calculate cash runway (months of cash available)
            Integer cashRunway = calculateCashRunway(cashFlow.getEndingCash(), cashBurnRate);
            
            // Determine cash flow health
            String cashFlowHealth = assessCashFlowHealth(operatingCashFlow, freeCashFlow);
            
            return cashFlow.getAnalysis();
            
        } catch (Exception e) {
            log.error("Failed to analyze cash flow", e);
            throw new RuntimeException("Failed to analyze cash flow", e);
        }
    }

    /**
     * Generates comparative analysis between periods
     */
    public ComparativeAnalysis generateComparativeAnalysis(LocalDate currentStart, LocalDate currentEnd,
                                                          LocalDate priorStart, LocalDate priorEnd) {
        try {
            // Get KPIs for both periods
            FinancialKPIs currentKPIs = calculateFinancialKPIs(currentStart, currentEnd);
            FinancialKPIs priorKPIs = calculateFinancialKPIs(priorStart, priorEnd);
            
            // Calculate variances
            Map<String, VarianceItem> variances = calculateVariances(currentKPIs, priorKPIs);
            
            // Identify significant changes
            List<SignificantChange> significantChanges = identifySignificantChanges(variances);
            
            // Generate period-over-period analysis
            PeriodComparison periodComparison = PeriodComparison.builder()
                .currentPeriodStart(currentStart)
                .currentPeriodEnd(currentEnd)
                .priorPeriodStart(priorStart)
                .priorPeriodEnd(priorEnd)
                .revenueChange(calculatePercentageChange(currentKPIs.getRevenue(), priorKPIs.getRevenue()))
                .expenseChange(calculatePercentageChange(currentKPIs.getExpenses(), priorKPIs.getExpenses()))
                .profitChange(calculatePercentageChange(currentKPIs.getNetIncome(), priorKPIs.getNetIncome()))
                .build();
            
            return ComparativeAnalysis.builder()
                .currentPeriodKPIs(currentKPIs)
                .priorPeriodKPIs(priorKPIs)
                .variances(variances)
                .significantChanges(significantChanges)
                .periodComparison(periodComparison)
                .build();
                
        } catch (Exception e) {
            log.error("Failed to generate comparative analysis", e);
            throw new RuntimeException("Failed to generate comparative analysis", e);
        }
    }

    /**
     * Calculates financial health score
     */
    public FinancialHealthScore calculateFinancialHealthScore(FinancialKPIs kpis, 
                                                             FinancialRatios ratios,
                                                             CashFlowAnalysis cashFlow) {
        try {
            int score = 0;
            int maxScore = 100;
            List<HealthScoreComponent> components = new ArrayList<>();
            
            // Profitability score (25 points)
            int profitabilityScore = 0;
            if (kpis.getNetMargin().compareTo(BigDecimal.valueOf(10)) > 0) profitabilityScore += 10;
            if (kpis.getReturnOnEquity().compareTo(BigDecimal.valueOf(15)) > 0) profitabilityScore += 10;
            if (kpis.getProfitGrowth().compareTo(BigDecimal.ZERO) > 0) profitabilityScore += 5;
            
            components.add(HealthScoreComponent.builder()
                .componentName("Profitability")
                .score(profitabilityScore)
                .maxScore(25)
                .build());
            
            // Liquidity score (25 points)
            int liquidityScore = 0;
            if (kpis.getCurrentRatio().compareTo(BigDecimal.valueOf(1.5)) > 0) liquidityScore += 10;
            if (kpis.getQuickRatio().compareTo(BigDecimal.ONE) > 0) liquidityScore += 10;
            if (cashFlow.getFreeCashFlow().compareTo(BigDecimal.ZERO) > 0) liquidityScore += 5;
            
            components.add(HealthScoreComponent.builder()
                .componentName("Liquidity")
                .score(liquidityScore)
                .maxScore(25)
                .build());
            
            // Efficiency score (25 points)
            int efficiencyScore = 0;
            if (kpis.getAssetTurnover().compareTo(BigDecimal.ONE) > 0) efficiencyScore += 15;
            if (kpis.getReturnOnAssets().compareTo(BigDecimal.valueOf(5)) > 0) efficiencyScore += 10;
            
            components.add(HealthScoreComponent.builder()
                .componentName("Efficiency")
                .score(efficiencyScore)
                .maxScore(25)
                .build());
            
            // Leverage score (25 points)
            int leverageScore = 0;
            if (kpis.getDebtToEquity().compareTo(BigDecimal.valueOf(2)) < 0) leverageScore += 15;
            if (kpis.getDebtToEquity().compareTo(BigDecimal.ONE) < 0) leverageScore += 10;
            
            components.add(HealthScoreComponent.builder()
                .componentName("Leverage")
                .score(leverageScore)
                .maxScore(25)
                .build());
            
            // Calculate total score
            score = profitabilityScore + liquidityScore + efficiencyScore + leverageScore;
            
            // Determine health rating
            String rating;
            if (score >= 80) rating = "EXCELLENT";
            else if (score >= 60) rating = "GOOD";
            else if (score >= 40) rating = "FAIR";
            else rating = "POOR";
            
            return FinancialHealthScore.builder()
                .overallScore(score)
                .maxScore(maxScore)
                .rating(rating)
                .components(components)
                .recommendations(generateHealthRecommendations(rating, components))
                .calculatedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to calculate financial health score", e);
            throw new RuntimeException("Failed to calculate health score", e);
        }
    }

    /**
     * Generates financial insights
     */
    private List<FinancialInsight> generateFinancialInsights(FinancialKPIs kpis,
                                                            RevenueTrends revenueTrends,
                                                            ExpenseAnalysis expenseAnalysis,
                                                            ProfitabilityMetrics profitability,
                                                            CashFlowAnalysis cashFlow) {
        List<FinancialInsight> insights = new ArrayList<>();
        
        // Revenue insights
        if ("GROWING".equals(revenueTrends.getTrend())) {
            insights.add(FinancialInsight.builder()
                .category("REVENUE")
                .type("POSITIVE")
                .title("Revenue Growth Trend")
                .description("Revenue is showing consistent growth pattern")
                .impact("HIGH")
                .recommendation("Continue current revenue strategies and explore expansion opportunities")
                .build());
        }
        
        // Expense insights
        if (!expenseAnalysis.getCostSavingOpportunities().isEmpty()) {
            insights.add(FinancialInsight.builder()
                .category("EXPENSE")
                .type("OPPORTUNITY")
                .title("Cost Saving Opportunities Identified")
                .description("Found " + expenseAnalysis.getCostSavingOpportunities().size() + " potential cost saving areas")
                .impact("MEDIUM")
                .recommendation("Review and implement cost optimization strategies")
                .build());
        }
        
        // Profitability insights
        if (profitability.getNetProfitMargin().compareTo(BigDecimal.valueOf(10)) > 0) {
            insights.add(FinancialInsight.builder()
                .category("PROFITABILITY")
                .type("POSITIVE")
                .title("Strong Profit Margins")
                .description("Net profit margin exceeds industry benchmarks")
                .impact("HIGH")
                .recommendation("Maintain operational efficiency while exploring growth opportunities")
                .build());
        }
        
        // Cash flow insights
        if (cashFlow.getFreeCashFlow().compareTo(BigDecimal.ZERO) < 0) {
            insights.add(FinancialInsight.builder()
                .category("CASH_FLOW")
                .type("WARNING")
                .title("Negative Free Cash Flow")
                .description("Free cash flow is negative, indicating potential liquidity concerns")
                .impact("HIGH")
                .recommendation("Focus on improving working capital management and reducing capital expenditures")
                .build());
        }
        
        return insights;
    }

    /**
     * Scheduled method to refresh dashboard cache
     */
    @Scheduled(cron = "0 0 * * * *") // Every hour
    public void refreshDashboardCache() {
        log.info("Refreshing financial dashboard cache");
        // Implementation to refresh cached dashboard data
    }

    // Private helper methods

    private BigDecimal calculateGrowthRate(BigDecimal currentValue, LocalDate startDate, LocalDate endDate) {
        // Calculate year-over-year growth rate
        LocalDate priorYearStart = startDate.minusYears(1);
        LocalDate priorYearEnd = endDate.minusYears(1);
        
        // This would fetch prior year value
        BigDecimal priorValue = BigDecimal.valueOf(100); // Placeholder
        
        if (priorValue.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        return currentValue.subtract(priorValue)
            .divide(priorValue, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
    }

    private BigDecimal calculateCurrentRatio(BalanceSheetResponse balanceSheet) {
        BigDecimal currentAssets = balanceSheet.getCurrentAssets().getTotalAmount();
        BigDecimal currentLiabilities = balanceSheet.getCurrentLiabilities().getTotalAmount();
        
        if (currentLiabilities.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        return currentAssets.divide(currentLiabilities, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateQuickRatio(BalanceSheetResponse balanceSheet) {
        // Quick ratio = (Current Assets - Inventory) / Current Liabilities
        BigDecimal currentAssets = balanceSheet.getCurrentAssets().getTotalAmount();
        BigDecimal inventory = BigDecimal.ZERO; // Would need to extract from balance sheet
        BigDecimal currentLiabilities = balanceSheet.getCurrentLiabilities().getTotalAmount();
        
        if (currentLiabilities.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        return currentAssets.subtract(inventory)
            .divide(currentLiabilities, 2, RoundingMode.HALF_UP);
    }

    private Map<String, BigDecimal> calculateMonthlyRevenue(List<Account> revenueAccounts, 
                                                           LocalDate startDate, LocalDate endDate) {
        Map<String, BigDecimal> monthlyRevenue = new LinkedHashMap<>();
        
        LocalDate currentMonth = startDate.withDayOfMonth(1);
        while (!currentMonth.isAfter(endDate)) {
            LocalDate monthEnd = currentMonth.withDayOfMonth(currentMonth.lengthOfMonth());
            BigDecimal monthRevenue = BigDecimal.ZERO;
            
            for (Account account : revenueAccounts) {
                List<LedgerEntry> entries = ledgerEntryRepository.findByAccountIdAndTransactionDateBetween(
                    account.getAccountId(), 
                    currentMonth.atStartOfDay(),
                    monthEnd.atTime(23, 59, 59));
                
                BigDecimal accountRevenue = entries.stream()
                    .filter(e -> e.getEntryType() == LedgerEntry.EntryType.CREDIT)
                    .map(LedgerEntry::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                
                monthRevenue = monthRevenue.add(accountRevenue);
            }
            
            monthlyRevenue.put(currentMonth.toString(), monthRevenue);
            currentMonth = currentMonth.plusMonths(1);
        }
        
        return monthlyRevenue;
    }

    private BigDecimal calculateEBITDA(IncomeStatementResponse incomeStatement) {
        // EBITDA = Net Income + Interest + Taxes + Depreciation + Amortization
        // Simplified calculation
        return incomeStatement.getOperatingIncome();
    }

    private BigDecimal calculateCashBurnRate(BigDecimal operatingCashFlow, LocalDate startDate, LocalDate endDate) {
        if (operatingCashFlow.compareTo(BigDecimal.ZERO) >= 0) {
            return BigDecimal.ZERO;
        }
        
        long months = ChronoUnit.MONTHS.between(startDate, endDate);
        if (months == 0) months = 1;
        
        return operatingCashFlow.abs().divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
    }

    private Integer calculateCashRunway(BigDecimal cashBalance, BigDecimal burnRate) {
        if (burnRate.compareTo(BigDecimal.ZERO) == 0) {
            return Integer.MAX_VALUE;
        }
        
        return cashBalance.divide(burnRate, 0, RoundingMode.DOWN).intValue();
    }

    private List<String> generateHealthRecommendations(String rating, List<HealthScoreComponent> components) {
        List<String> recommendations = new ArrayList<>();
        
        for (HealthScoreComponent component : components) {
            double scorePercentage = (component.getScore() * 100.0) / component.getMaxScore();
            if (scorePercentage < 60) {
                recommendations.add("Improve " + component.getComponentName() + " metrics");
            }
        }
        
        return recommendations;
    }

    private Map<String, BigDecimal> calculateRevenueByCategory(List<Account> revenueAccounts,
                                                              LocalDate startDate, LocalDate endDate) {
        try {
            Map<String, BigDecimal> revenueByCategory = new HashMap<>();
            LocalDateTime startDateTime = startDate.atStartOfDay();
            LocalDateTime endDateTime = endDate.atTime(23, 59, 59);
            
            for (Account account : revenueAccounts) {
                String category = account.getAccountCategory() != null ? account.getAccountCategory() : "Other";
                
                // For revenue accounts, credit entries increase revenue
                List<LedgerEntry> revenueEntries = ledgerEntryRepository.findByAccountIdAndEntryTypeAndTransactionDateBetween(
                    account.getAccountId(), LedgerEntry.EntryType.CREDIT, startDateTime, endDateTime);
                    
                BigDecimal accountRevenue = revenueEntries.stream()
                    .map(LedgerEntry::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                    
                revenueByCategory.merge(category, accountRevenue, BigDecimal::add);
            }
            
            return revenueByCategory;
            
        } catch (Exception e) {
            log.error("Failed to calculate revenue by category", e);
            return new HashMap<>();
        }
    }

    private List<RevenueSource> identifyTopRevenueSources(List<Account> revenueAccounts,
                                                         LocalDate startDate, LocalDate endDate) {
        try {
            LocalDateTime startDateTime = startDate.atStartOfDay();
            LocalDateTime endDateTime = endDate.atTime(23, 59, 59);
            
            List<RevenueSource> revenueSources = new ArrayList<>();
            
            for (Account account : revenueAccounts) {
                // For revenue accounts, credit entries increase revenue
                List<LedgerEntry> revenueEntries = ledgerEntryRepository.findByAccountIdAndEntryTypeAndTransactionDateBetween(
                    account.getAccountId(), LedgerEntry.EntryType.CREDIT, startDateTime, endDateTime);
                    
                BigDecimal accountRevenue = revenueEntries.stream()
                    .map(LedgerEntry::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                    
                if (accountRevenue.compareTo(BigDecimal.ZERO) > 0) {
                    revenueSources.add(RevenueSource.builder()
                        .accountId(account.getAccountId())
                        .accountName(account.getAccountName())
                        .category(account.getAccountCategory())
                        .amount(accountRevenue)
                        .transactionCount(revenueEntries.size())
                        .build());
                }
            }
            
            // Sort by amount descending and return top 10
            return revenueSources.stream()
                .sorted((a, b) -> b.getAmount().compareTo(a.getAmount()))
                .limit(10)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Failed to identify top revenue sources", e);
            return new ArrayList<>();
        }
    }

    private BigDecimal calculateAverageMonthlyRevenue(Map<String, BigDecimal> monthlyRevenue) {
        if (monthlyRevenue.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal total = monthlyRevenue.values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return total.divide(BigDecimal.valueOf(monthlyRevenue.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateRevenueVolatility(Map<String, BigDecimal> monthlyRevenue) {
        try {
            if (monthlyRevenue.size() < 2) {
                return BigDecimal.ZERO;
            }
            
            // Calculate mean
            BigDecimal mean = calculateAverageMonthlyRevenue(monthlyRevenue);
            
            // Calculate variance
            BigDecimal sumSquaredDeviations = BigDecimal.ZERO;
            for (BigDecimal value : monthlyRevenue.values()) {
                BigDecimal deviation = value.subtract(mean);
                sumSquaredDeviations = sumSquaredDeviations.add(deviation.multiply(deviation));
            }
            
            BigDecimal variance = sumSquaredDeviations.divide(
                BigDecimal.valueOf(monthlyRevenue.size() - 1), 6, RoundingMode.HALF_UP);
            
            // Calculate standard deviation (square root of variance)
            // Using Newton's method for square root
            if (variance.compareTo(BigDecimal.ZERO) == 0) {
                return BigDecimal.ZERO;
            }
            
            BigDecimal standardDeviation = sqrt(variance);
            
            // Return coefficient of variation (standard deviation / mean) * 100
            if (mean.compareTo(BigDecimal.ZERO) == 0) {
                return BigDecimal.ZERO;
            }
            
            return standardDeviation.divide(mean, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
                
        } catch (Exception e) {
            log.error("Failed to calculate revenue volatility", e);
            return BigDecimal.ZERO;
        }
    }

    private String determineRevenueTrend(Map<String, BigDecimal> monthlyRevenue) {
        // Analyze revenue trend
        return "STABLE"; // Placeholder
    }

    private BigDecimal forecastRevenue(Map<String, BigDecimal> monthlyRevenue, int months) {
        // Simple forecast based on average
        BigDecimal average = calculateAverageMonthlyRevenue(monthlyRevenue);
        return average.multiply(BigDecimal.valueOf(months));
    }

    private Map<String, BigDecimal> calculateExpenseByCategory(List<Account> expenseAccounts,
                                                              LocalDate startDate, LocalDate endDate) {
        // Group expenses by category
        return new HashMap<>(); // Placeholder
    }

    private List<ExpenseItem> identifyTopExpenses(List<Account> expenseAccounts,
                                                 LocalDate startDate, LocalDate endDate) {
        // Identify top expense items
        return new ArrayList<>(); // Placeholder
    }

    private List<CostSavingOpportunity> identifyCostSavingOpportunities(List<Account> expenseAccounts,
                                                                       LocalDate startDate, LocalDate endDate) {
        // Identify cost saving opportunities
        return new ArrayList<>(); // Placeholder
    }

    private String determineProfitabilityTrend(LocalDate startDate, LocalDate endDate) {
        // Analyze profitability trend
        return "IMPROVING"; // Placeholder
    }

    private String assessCashFlowHealth(BigDecimal operatingCashFlow, BigDecimal freeCashFlow) {
        if (operatingCashFlow.compareTo(BigDecimal.ZERO) > 0 && freeCashFlow.compareTo(BigDecimal.ZERO) > 0) {
            return "HEALTHY";
        } else if (operatingCashFlow.compareTo(BigDecimal.ZERO) > 0) {
            return "MODERATE";
        } else {
            return "CONCERNING";
        }
    }

    private AccountBalancesSummary getAccountBalancesSummary(LocalDate asOfDate) {
        // Get summary of account balances
        return AccountBalancesSummary.builder()
            .asOfDate(asOfDate)
            .totalAssets(BigDecimal.ZERO)
            .totalLiabilities(BigDecimal.ZERO)
            .totalEquity(BigDecimal.ZERO)
            .build();
    }

    private Map<String, VarianceItem> calculateVariances(FinancialKPIs current, FinancialKPIs prior) {
        Map<String, VarianceItem> variances = new HashMap<>();
        
        variances.put("Revenue", VarianceItem.builder()
            .currentValue(current.getRevenue())
            .priorValue(prior.getRevenue())
            .variance(current.getRevenue().subtract(prior.getRevenue()))
            .percentageChange(calculatePercentageChange(current.getRevenue(), prior.getRevenue()))
            .build());
        
        // Add more variance calculations
        
        return variances;
    }

    private List<SignificantChange> identifySignificantChanges(Map<String, VarianceItem> variances) {
        List<SignificantChange> changes = new ArrayList<>();
        
        for (Map.Entry<String, VarianceItem> entry : variances.entrySet()) {
            if (entry.getValue().getPercentageChange().abs().compareTo(BigDecimal.valueOf(10)) > 0) {
                changes.add(SignificantChange.builder()
                    .metric(entry.getKey())
                    .change(entry.getValue())
                    .significance("HIGH")
                    .build());
            }
        }
        
        return changes;
    }

    private BigDecimal calculatePercentageChange(BigDecimal current, BigDecimal prior) {
        if (prior.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        return current.subtract(prior)
            .divide(prior, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
    }
    
    /**
     * Calculate square root using Newton's method
     */
    private BigDecimal sqrt(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal x = value;
        BigDecimal previous;
        BigDecimal two = new BigDecimal("2");
        
        do {
            previous = x;
            x = x.add(value.divide(x, 10, RoundingMode.HALF_UP)).divide(two, 10, RoundingMode.HALF_UP);
        } while (x.subtract(previous).abs().compareTo(new BigDecimal("0.0001")) > 0);
        
        return x.setScale(6, RoundingMode.HALF_UP);
    }
}