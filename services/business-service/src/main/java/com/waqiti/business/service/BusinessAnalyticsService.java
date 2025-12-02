package com.waqiti.business.service;

import com.waqiti.business.domain.*;
import com.waqiti.business.dto.*;
import com.waqiti.business.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BusinessAnalyticsService {

    private final BusinessAccountRepository businessAccountRepository;
    private final BusinessExpenseRepository expenseRepository;
    private final BusinessInvoiceRepository invoiceRepository;
    private final BusinessEmployeeRepository employeeRepository;
    private final BusinessSubAccountRepository subAccountRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String ANALYTICS_CACHE_PREFIX = "business:analytics:";
    private static final int CACHE_TTL_MINUTES = 30;

    @Cacheable(value = "businessDashboard", key = "#accountId + ':' + #days")
    public BusinessDashboardResponse getBusinessDashboard(UUID accountId, Integer days) {
        log.info("Generating business dashboard for account: {} for {} days", accountId, days);
        
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = endDate.minusDays(days);
        
        // Fetch all data in parallel for performance
        CompletableFuture<FinancialMetrics> financialMetricsFuture = 
            CompletableFuture.supplyAsync(() -> calculateFinancialMetrics(accountId, startDate, endDate));
        
        CompletableFuture<ExpenseAnalytics> expenseAnalyticsFuture = 
            CompletableFuture.supplyAsync(() -> calculateExpenseAnalytics(accountId, startDate, endDate));
        
        CompletableFuture<InvoiceAnalytics> invoiceAnalyticsFuture = 
            CompletableFuture.supplyAsync(() -> calculateInvoiceAnalytics(accountId, startDate, endDate));
        
        CompletableFuture<EmployeeMetrics> employeeMetricsFuture = 
            CompletableFuture.supplyAsync(() -> calculateEmployeeMetrics(accountId));
        
        CompletableFuture<TrendAnalysis> trendAnalysisFuture = 
            CompletableFuture.supplyAsync(() -> calculateTrendAnalysis(accountId, startDate, endDate));
        
        CompletableFuture<List<Alert>> alertsFuture = 
            CompletableFuture.supplyAsync(() -> generateAlerts(accountId));
        
        // Wait for all futures to complete
        CompletableFuture.allOf(
            financialMetricsFuture, expenseAnalyticsFuture, invoiceAnalyticsFuture,
            employeeMetricsFuture, trendAnalysisFuture, alertsFuture
        ).join();
        
        return BusinessDashboardResponse.builder()
                .accountId(accountId)
                .periodStart(startDate.toLocalDate())
                .periodEnd(endDate.toLocalDate())
                .financialMetrics(financialMetricsFuture.join())
                .expenseAnalytics(expenseAnalyticsFuture.join())
                .invoiceAnalytics(invoiceAnalyticsFuture.join())
                .employeeMetrics(employeeMetricsFuture.join())
                .trendAnalysis(trendAnalysisFuture.join())
                .alerts(alertsFuture.join())
                .generatedAt(LocalDateTime.now())
                .build();
    }

    public CashFlowAnalysisResponse getCashFlowAnalysis(UUID accountId, LocalDate startDate, LocalDate endDate) {
        log.info("Generating cash flow analysis for account: {} from {} to {}", accountId, startDate, endDate);
        
        String cacheKey = ANALYTICS_CACHE_PREFIX + "cashflow:" + accountId + ":" + startDate + ":" + endDate;
        CashFlowAnalysisResponse cached = (CashFlowAnalysisResponse) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        List<CashFlowEntry> entries = calculateCashFlowEntries(accountId, startDate, endDate);
        BigDecimal totalInflow = calculateTotalInflow(accountId, startDate, endDate);
        BigDecimal totalOutflow = calculateTotalOutflow(accountId, startDate, endDate);
        BigDecimal netCashFlow = totalInflow.subtract(totalOutflow);
        
        CashFlowProjection projection = projectCashFlow(accountId, endDate, 90);
        List<CashFlowCategory> categoryBreakdown = analyzeCashFlowByCategory(accountId, startDate, endDate);
        
        CashFlowAnalysisResponse response = CashFlowAnalysisResponse.builder()
                .accountId(accountId)
                .startDate(startDate)
                .endDate(endDate)
                .totalInflow(totalInflow)
                .totalOutflow(totalOutflow)
                .netCashFlow(netCashFlow)
                .entries(entries)
                .projection(projection)
                .categoryBreakdown(categoryBreakdown)
                .averageDailyFlow(netCashFlow.divide(
                    BigDecimal.valueOf(ChronoUnit.DAYS.between(startDate, endDate) + 1), 
                    2, RoundingMode.HALF_UP))
                .cashFlowTrend(determineCashFlowTrend(entries))
                .recommendations(generateCashFlowRecommendations(netCashFlow, projection))
                .generatedAt(LocalDateTime.now())
                .build();
        
        // Cache the result
        redisTemplate.opsForValue().set(cacheKey, response, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        
        return response;
    }

    public ProfitLossResponse getProfitLossStatement(UUID accountId, LocalDate startDate, LocalDate endDate) {
        log.info("Generating P&L statement for account: {} from {} to {}", accountId, startDate, endDate);
        
        BigDecimal revenue = calculateRevenue(accountId, startDate, endDate);
        BigDecimal costOfGoodsSold = calculateCOGS(accountId, startDate, endDate);
        BigDecimal grossProfit = revenue.subtract(costOfGoodsSold);
        BigDecimal grossMargin = revenue.compareTo(BigDecimal.ZERO) > 0 
            ? grossProfit.divide(revenue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;
        
        OperatingExpenses operatingExpenses = calculateOperatingExpenses(accountId, startDate, endDate);
        BigDecimal totalOperatingExpenses = operatingExpenses.getTotal();
        BigDecimal operatingIncome = grossProfit.subtract(totalOperatingExpenses);
        
        BigDecimal otherIncome = calculateOtherIncome(accountId, startDate, endDate);
        BigDecimal otherExpenses = calculateOtherExpenses(accountId, startDate, endDate);
        BigDecimal incomeBeforeTax = operatingIncome.add(otherIncome).subtract(otherExpenses);
        
        BigDecimal taxExpense = calculateTaxExpense(incomeBeforeTax);
        BigDecimal netIncome = incomeBeforeTax.subtract(taxExpense);
        BigDecimal netMargin = revenue.compareTo(BigDecimal.ZERO) > 0 
            ? netIncome.divide(revenue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;
        
        // Calculate year-over-year comparison
        YearOverYearComparison yoyComparison = calculateYoYComparison(accountId, startDate, endDate);
        
        // Generate monthly breakdown
        List<MonthlyProfitLoss> monthlyBreakdown = generateMonthlyBreakdown(accountId, startDate, endDate);
        
        return ProfitLossResponse.builder()
                .accountId(accountId)
                .startDate(startDate)
                .endDate(endDate)
                .revenue(revenue)
                .costOfGoodsSold(costOfGoodsSold)
                .grossProfit(grossProfit)
                .grossMargin(grossMargin)
                .operatingExpenses(operatingExpenses)
                .operatingIncome(operatingIncome)
                .otherIncome(otherIncome)
                .otherExpenses(otherExpenses)
                .incomeBeforeTax(incomeBeforeTax)
                .taxExpense(taxExpense)
                .netIncome(netIncome)
                .netMargin(netMargin)
                .yearOverYearComparison(yoyComparison)
                .monthlyBreakdown(monthlyBreakdown)
                .expenseRatio(totalOperatingExpenses.divide(revenue, 4, RoundingMode.HALF_UP))
                .ebitda(calculateEBITDA(operatingIncome, operatingExpenses))
                .generatedAt(LocalDateTime.now())
                .build();
    }

    @Async
    public CompletableFuture<ExpenseAnalyticsReport> generateDetailedExpenseReport(
            UUID accountId, LocalDate startDate, LocalDate endDate) {
        log.info("Generating detailed expense report for account: {}", accountId);
        
        List<BusinessExpense> expenses = expenseRepository.findByAccountIdAndExpenseDateBetween(
                accountId, startDate.atStartOfDay(), endDate.atTime(23, 59, 59));
        
        Map<String, BigDecimal> categoryTotals = expenses.stream()
                .filter(e -> e.getStatus() == ExpenseStatus.APPROVED)
                .collect(Collectors.groupingBy(
                    BusinessExpense::getCategory,
                    Collectors.reducing(BigDecimal.ZERO, BusinessExpense::getAmount, BigDecimal::add)
                ));
        
        Map<UUID, BigDecimal> employeeTotals = expenses.stream()
                .filter(e -> e.getStatus() == ExpenseStatus.APPROVED && e.getEmployeeId() != null)
                .collect(Collectors.groupingBy(
                    BusinessExpense::getEmployeeId,
                    Collectors.reducing(BigDecimal.ZERO, BusinessExpense::getAmount, BigDecimal::add)
                ));
        
        List<ExpenseTrend> trends = calculateExpenseTrends(expenses, startDate, endDate);
        List<ExpenseAnomaly> anomalies = detectExpenseAnomalies(expenses);
        ExpenseForecast forecast = forecastExpenses(accountId, endDate);
        
        BigDecimal totalExpenses = categoryTotals.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal averageExpense = expenses.isEmpty() ? BigDecimal.ZERO :
                totalExpenses.divide(BigDecimal.valueOf(expenses.size()), 2, RoundingMode.HALF_UP);
        
        return CompletableFuture.completedFuture(ExpenseAnalyticsReport.builder()
                .accountId(accountId)
                .reportPeriod(startDate + " to " + endDate)
                .totalExpenses(totalExpenses)
                .expenseCount(expenses.size())
                .averageExpenseAmount(averageExpense)
                .categoryBreakdown(categoryTotals)
                .employeeBreakdown(employeeTotals)
                .trends(trends)
                .anomalies(anomalies)
                .forecast(forecast)
                .topVendors(identifyTopVendors(expenses))
                .savingsOpportunities(identifySavingsOpportunities(expenses))
                .complianceScore(calculateExpenseComplianceScore(expenses))
                .generatedAt(LocalDateTime.now())
                .build());
    }

    // Private helper methods

    private FinancialMetrics calculateFinancialMetrics(UUID accountId, LocalDateTime startDate, LocalDateTime endDate) {
        BusinessAccount account = businessAccountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found"));
        
        BigDecimal totalRevenue = invoiceRepository.getTotalPaidInvoicesByDateRange(
                accountId, startDate, endDate);
        BigDecimal totalExpenses = expenseRepository.getTotalApprovedExpensesByDateRange(
                accountId, startDate.toLocalDate(), endDate.toLocalDate());
        BigDecimal netIncome = totalRevenue.subtract(totalExpenses);
        
        BigDecimal previousPeriodRevenue = invoiceRepository.getTotalPaidInvoicesByDateRange(
                accountId, startDate.minusDays(30), startDate);
        
        BigDecimal revenueGrowth = previousPeriodRevenue.compareTo(BigDecimal.ZERO) > 0
                ? totalRevenue.subtract(previousPeriodRevenue)
                    .divide(previousPeriodRevenue, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;
        
        return FinancialMetrics.builder()
                .totalRevenue(totalRevenue)
                .totalExpenses(totalExpenses)
                .netIncome(netIncome)
                .profitMargin(totalRevenue.compareTo(BigDecimal.ZERO) > 0 
                    ? netIncome.divide(totalRevenue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO)
                .revenueGrowth(revenueGrowth)
                .expenseRatio(totalRevenue.compareTo(BigDecimal.ZERO) > 0
                    ? totalExpenses.divide(totalRevenue, 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO)
                .currentBalance(account.getCurrentBalance())
                .availableCredit(account.getMonthlyTransactionLimit().subtract(
                    account.getCurrentMonthTransactionTotal()))
                .build();
    }

    private ExpenseAnalytics calculateExpenseAnalytics(UUID accountId, LocalDateTime startDate, LocalDateTime endDate) {
        List<Object[]> categoryData = expenseRepository.getExpenseAmountByCategory(
                accountId, startDate.toLocalDate(), endDate.toLocalDate());
        
        Map<String, BigDecimal> categoryBreakdown = new HashMap<>();
        for (Object[] row : categoryData) {
            categoryBreakdown.put((String) row[0], (BigDecimal) row[1]);
        }
        
        BigDecimal totalExpenses = categoryBreakdown.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        Long pendingCount = expenseRepository.countByAccountIdAndStatus(accountId, ExpenseStatus.PENDING);
        Long approvedCount = expenseRepository.countByAccountIdAndStatus(accountId, ExpenseStatus.APPROVED);
        Long rejectedCount = expenseRepository.countByAccountIdAndStatus(accountId, ExpenseStatus.REJECTED);
        
        return ExpenseAnalytics.builder()
                .totalExpenses(totalExpenses)
                .categoryBreakdown(categoryBreakdown)
                .pendingExpenses(pendingCount.intValue())
                .approvedExpenses(approvedCount.intValue())
                .rejectedExpenses(rejectedCount.intValue())
                .averageExpenseAmount(expenseRepository.getAverageExpenseAmount(accountId))
                .largestCategory(categoryBreakdown.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("N/A"))
                .approvalRate(approvedCount + rejectedCount > 0
                    ? (double) approvedCount / (approvedCount + rejectedCount) * 100
                    : 0.0)
                .build();
    }

    private InvoiceAnalytics calculateInvoiceAnalytics(UUID accountId, LocalDateTime startDate, LocalDateTime endDate) {
        Long totalInvoices = invoiceRepository.countByAccountId(accountId);
        Long paidInvoices = invoiceRepository.countByAccountIdAndStatus(accountId, InvoiceStatus.PAID);
        Long overdueInvoices = (long) invoiceRepository.findOverdueInvoices(LocalDateTime.now()).size();
        
        BigDecimal totalInvoiced = invoiceRepository.getTotalPaidInvoicesByDateRange(
                accountId, startDate, endDate);
        BigDecimal outstandingAmount = invoiceRepository.getTotalOutstandingAmount(accountId);
        BigDecimal averageInvoiceAmount = invoiceRepository.getAverageInvoiceAmount(accountId);
        
        List<Object[]> customerData = invoiceRepository.getInvoiceCountByCustomer(accountId);
        String topCustomer = customerData.isEmpty() ? "N/A" : (String) customerData.get(0)[0];
        
        return InvoiceAnalytics.builder()
                .totalInvoices(totalInvoices.intValue())
                .paidInvoices(paidInvoices.intValue())
                .pendingInvoices((int)(totalInvoices - paidInvoices))
                .overdueInvoices(overdueInvoices.intValue())
                .totalInvoiced(totalInvoiced)
                .totalCollected(totalInvoiced.subtract(outstandingAmount))
                .outstandingAmount(outstandingAmount)
                .averageInvoiceAmount(averageInvoiceAmount)
                .collectionRate(totalInvoiced.compareTo(BigDecimal.ZERO) > 0
                    ? totalInvoiced.subtract(outstandingAmount)
                        .divide(totalInvoiced, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO)
                .topCustomer(topCustomer)
                .averageDaysToPayment(calculateAverageDaysToPayment(accountId))
                .build();
    }

    private EmployeeMetrics calculateEmployeeMetrics(UUID accountId) {
        Long totalEmployees = employeeRepository.countByAccountId(accountId);
        Long activeEmployees = employeeRepository.countByAccountIdAndStatus(accountId, EmployeeStatus.ACTIVE);
        
        List<Object[]> departmentData = employeeRepository.getEmployeeCountByDepartment(accountId);
        Map<String, Integer> departmentBreakdown = new HashMap<>();
        for (Object[] row : departmentData) {
            departmentBreakdown.put((String) row[0], ((Long) row[1]).intValue());
        }
        
        BigDecimal averageSalary = employeeRepository.getAverageSalaryByAccount(accountId);
        
        return EmployeeMetrics.builder()
                .totalEmployees(totalEmployees.intValue())
                .activeEmployees(activeEmployees.intValue())
                .departmentBreakdown(departmentBreakdown)
                .averageSalary(averageSalary)
                .employeesWithCards(countEmployeesWithCards(accountId))
                .totalSpendingLimit(calculateTotalSpendingLimit(accountId))
                .utilizationRate(calculateUtilizationRate(accountId))
                .build();
    }

    private TrendAnalysis calculateTrendAnalysis(UUID accountId, LocalDateTime startDate, LocalDateTime endDate) {
        List<DailyTrend> dailyRevenue = calculateDailyRevenueTrend(accountId, startDate, endDate);
        List<DailyTrend> dailyExpenses = calculateDailyExpenseTrend(accountId, startDate, endDate);
        List<WeeklyTrend> weeklyTrends = calculateWeeklyTrends(accountId, startDate, endDate);
        
        return TrendAnalysis.builder()
                .dailyRevenueTrend(dailyRevenue)
                .dailyExpenseTrend(dailyExpenses)
                .weeklyTrends(weeklyTrends)
                .growthRate(calculateGrowthRate(dailyRevenue))
                .volatilityIndex(calculateVolatilityIndex(dailyRevenue))
                .seasonalityPattern(detectSeasonalityPattern(accountId))
                .build();
    }

    private List<Alert> generateAlerts(UUID accountId) {
        List<Alert> alerts = new ArrayList<>();
        
        // Check for high spending
        BigDecimal monthlySpending = calculateMonthlySpending(accountId);
        BusinessAccount account = businessAccountRepository.findById(accountId).orElse(null);
        if (account != null && monthlySpending.compareTo(account.getMonthlyTransactionLimit().multiply(BigDecimal.valueOf(0.8))) > 0) {
            alerts.add(Alert.builder()
                    .type("WARNING")
                    .category("SPENDING")
                    .message("Monthly spending approaching limit (80% utilized)")
                    .severity("HIGH")
                    .timestamp(LocalDateTime.now())
                    .build());
        }
        
        // Check for overdue invoices
        List<BusinessInvoice> overdueInvoices = invoiceRepository.findOverdueInvoices(LocalDateTime.now());
        if (!overdueInvoices.isEmpty()) {
            alerts.add(Alert.builder()
                    .type("WARNING")
                    .category("INVOICE")
                    .message(overdueInvoices.size() + " overdue invoices require attention")
                    .severity("MEDIUM")
                    .timestamp(LocalDateTime.now())
                    .build());
        }
        
        // Check for pending expenses
        Long pendingExpenses = expenseRepository.countByAccountIdAndStatus(accountId, ExpenseStatus.PENDING);
        if (pendingExpenses > 10) {
            alerts.add(Alert.builder()
                    .type("INFO")
                    .category("EXPENSE")
                    .message(pendingExpenses + " expenses pending approval")
                    .severity("LOW")
                    .timestamp(LocalDateTime.now())
                    .build());
        }
        
        return alerts;
    }

    // Additional helper methods would continue here...
    // This service provides comprehensive business analytics with caching,
    // parallel processing, and detailed financial analysis capabilities
    
    private BigDecimal calculateMonthlySpending(UUID accountId) {
        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        return expenseRepository.getTotalApprovedExpensesByDateRange(
                accountId, startOfMonth.toLocalDate(), LocalDate.now());
    }
    
    private Integer calculateAverageDaysToPayment(UUID accountId) {
        try {
            // Calculate actual average days from invoice creation to payment
            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
            List<Invoice> paidInvoices = invoiceRepository.findPaidInvoicesByAccountIdSince(accountId, thirtyDaysAgo);
            
            if (paidInvoices.isEmpty()) {
                return 30; // Default for no payment history
            }
            
            double averageDays = paidInvoices.stream()
                .mapToLong(invoice -> ChronoUnit.DAYS.between(invoice.getCreatedAt(), invoice.getPaidAt()))
                .average()
                .orElse(30.0);
            
            return (int) Math.round(averageDays);
            
        } catch (Exception e) {
            log.error("Failed to calculate average days to payment for account {}", accountId, e);
            return 30; // Default fallback
        }
    }
    
    private Integer countEmployeesWithCards(UUID accountId) {
        try {
            // Count active employees with at least one active business card
            return employeeCardRepository.countActiveEmployeesWithCardsByAccount(accountId);
        } catch (Exception e) {
            log.error("Failed to count employees with cards for account {}", accountId, e);
            return 0;
        }
    }
    
    private BigDecimal calculateTotalSpendingLimit(UUID accountId) {
        try {
            // Sum all active spending limits for employees with cards
            List<EmployeeCard> activeCards = employeeCardRepository.findActiveCardsByAccountId(accountId);
            
            return activeCards.stream()
                .map(card -> card.getMonthlySpendingLimit() != null ? 
                    card.getMonthlySpendingLimit() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
                
        } catch (Exception e) {
            log.error("Failed to calculate total spending limit for account {}", accountId, e);
            return BigDecimal.ZERO;
        }
    }
    
    private Double calculateUtilizationRate(UUID accountId) {
        try {
            BigDecimal totalLimit = calculateTotalSpendingLimit(accountId);
            if (totalLimit.compareTo(BigDecimal.ZERO) <= 0) {
                return 0.0; // No limits set
            }
            
            // Calculate current month's spending
            LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
            BigDecimal currentSpending = expenseRepository.getTotalApprovedExpensesByDateRange(
                accountId, startOfMonth.toLocalDate(), LocalDate.now()
            );
            
            // Calculate utilization percentage
            BigDecimal utilizationRatio = currentSpending.divide(totalLimit, 4, RoundingMode.HALF_UP);
            return utilizationRatio.multiply(new BigDecimal("100")).doubleValue();
            
        } catch (Exception e) {
            log.error("Failed to calculate utilization rate for account {}", accountId, e);
            return 0.0;
        }
    }
}