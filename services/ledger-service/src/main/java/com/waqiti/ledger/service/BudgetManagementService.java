package com.waqiti.ledger.service;

import com.waqiti.ledger.domain.Account;
import com.waqiti.ledger.domain.LedgerEntry;
import com.waqiti.ledger.dto.*;
import com.waqiti.ledger.repository.AccountRepository;
import com.waqiti.ledger.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Budget Management Service
 * 
 * Provides comprehensive budget management functionality including:
 * - Budget creation and allocation
 * - Budget vs actual tracking
 * - Variance analysis
 * - Budget forecasting
 * - Spending alerts and notifications
 * - Multi-period budget planning
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BudgetManagementService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final BalanceCalculationService balanceCalculationService;

    /**
     * Creates a new budget
     */
    @Transactional
    public BudgetResponse createBudget(CreateBudgetRequest request) {
        try {
            log.info("Creating budget: {} for period {} to {}", 
                request.getBudgetName(), request.getStartDate(), request.getEndDate());
            
            // Validate budget request
            validateBudgetRequest(request);
            
            // Create budget header
            Budget budget = Budget.builder()
                .budgetId(UUID.randomUUID())
                .budgetName(request.getBudgetName())
                .budgetType(request.getBudgetType())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .status(BudgetStatus.ACTIVE)
                .createdBy(request.getCreatedBy())
                .createdAt(LocalDateTime.now())
                .build();
            
            // Create budget line items
            List<BudgetLineItem> lineItems = createBudgetLineItems(request.getLineItems(), budget.getBudgetId());
            
            // Calculate total budget amount
            BigDecimal totalBudget = lineItems.stream()
                .map(BudgetLineItem::getBudgetAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            budget.setTotalBudgetAmount(totalBudget);
            budget.setLineItems(lineItems);
            
            // Save budget (in a real implementation)
            // budgetRepository.save(budget);
            
            return BudgetResponse.builder()
                .budget(budget)
                .success(true)
                .message("Budget created successfully")
                .build();
                
        } catch (Exception e) {
            log.error("Failed to create budget", e);
            throw new RuntimeException("Failed to create budget", e);
        }
    }

    /**
     * Gets budget vs actual comparison
     */
    public BudgetVsActualResponse getBudgetVsActual(UUID budgetId, LocalDate asOfDate) {
        try {
            log.debug("Getting budget vs actual for budget: {} as of {}", budgetId, asOfDate);
            
            // Get budget details
            Budget budget = getBudget(budgetId);
            
            // Calculate actual spending for each line item
            List<BudgetVsActualLineItem> comparisonItems = new ArrayList<>();
            BigDecimal totalBudget = BigDecimal.ZERO;
            BigDecimal totalActual = BigDecimal.ZERO;
            BigDecimal totalVariance = BigDecimal.ZERO;
            
            for (BudgetLineItem lineItem : budget.getLineItems()) {
                // Get actual spending
                BigDecimal actualAmount = calculateActualSpending(
                    lineItem.getAccountId(), budget.getStartDate(), asOfDate);
                
                // Calculate variance
                BigDecimal variance = lineItem.getBudgetAmount().subtract(actualAmount);
                BigDecimal variancePercentage = lineItem.getBudgetAmount().compareTo(BigDecimal.ZERO) == 0 ? 
                    BigDecimal.ZERO :
                    variance.divide(lineItem.getBudgetAmount(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                
                // Calculate utilization
                BigDecimal utilization = lineItem.getBudgetAmount().compareTo(BigDecimal.ZERO) == 0 ?
                    BigDecimal.ZERO :
                    actualAmount.divide(lineItem.getBudgetAmount(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                
                // Determine status
                String status = determineLineItemStatus(utilization, asOfDate, budget.getEndDate());
                
                BudgetVsActualLineItem comparisonItem = BudgetVsActualLineItem.builder()
                    .lineItemId(lineItem.getLineItemId())
                    .accountId(lineItem.getAccountId())
                    .accountName(lineItem.getAccountName())
                    .category(lineItem.getCategory())
                    .budgetAmount(lineItem.getBudgetAmount())
                    .actualAmount(actualAmount)
                    .variance(variance)
                    .variancePercentage(variancePercentage)
                    .utilization(utilization)
                    .status(status)
                    .build();
                
                comparisonItems.add(comparisonItem);
                
                totalBudget = totalBudget.add(lineItem.getBudgetAmount());
                totalActual = totalActual.add(actualAmount);
                totalVariance = totalVariance.add(variance);
            }
            
            // Calculate overall metrics
            BigDecimal overallUtilization = totalBudget.compareTo(BigDecimal.ZERO) == 0 ?
                BigDecimal.ZERO :
                totalActual.divide(totalBudget, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            
            // Generate insights
            List<BudgetInsight> insights = generateBudgetInsights(comparisonItems, budget, asOfDate);
            
            // Generate recommendations
            List<String> recommendations = generateBudgetRecommendations(comparisonItems, overallUtilization);
            
            return BudgetVsActualResponse.builder()
                .budgetId(budgetId)
                .budgetName(budget.getBudgetName())
                .asOfDate(asOfDate)
                .lineItems(comparisonItems)
                .totalBudget(totalBudget)
                .totalActual(totalActual)
                .totalVariance(totalVariance)
                .overallUtilization(overallUtilization)
                .insights(insights)
                .recommendations(recommendations)
                .generatedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to get budget vs actual", e);
            throw new RuntimeException("Failed to get budget vs actual", e);
        }
    }

    /**
     * Performs budget variance analysis
     */
    public BudgetVarianceAnalysis analyzeBudgetVariance(UUID budgetId, LocalDate startDate, LocalDate endDate) {
        try {
            log.info("Analyzing budget variance for budget: {} from {} to {}", budgetId, startDate, endDate);
            
            Budget budget = getBudget(budgetId);
            
            // Calculate variances by category
            Map<String, CategoryVariance> categoryVariances = calculateCategoryVariances(budget, startDate, endDate);
            
            // Calculate monthly variances
            Map<String, MonthlyVariance> monthlyVariances = calculateMonthlyVariances(budget, startDate, endDate);
            
            // Identify top variances
            List<TopVarianceItem> topPositiveVariances = identifyTopVariances(categoryVariances, true);
            List<TopVarianceItem> topNegativeVariances = identifyTopVariances(categoryVariances, false);
            
            // Calculate variance trends
            VarianceTrend varianceTrend = analyzeVarianceTrend(monthlyVariances);
            
            // Generate variance explanations
            List<VarianceExplanation> explanations = generateVarianceExplanations(
                categoryVariances, topPositiveVariances, topNegativeVariances);
            
            return BudgetVarianceAnalysis.builder()
                .budgetId(budgetId)
                .analysisStartDate(startDate)
                .analysisEndDate(endDate)
                .categoryVariances(categoryVariances)
                .monthlyVariances(monthlyVariances)
                .topPositiveVariances(topPositiveVariances)
                .topNegativeVariances(topNegativeVariances)
                .varianceTrend(varianceTrend)
                .explanations(explanations)
                .generatedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to analyze budget variance", e);
            throw new RuntimeException("Failed to analyze budget variance", e);
        }
    }

    /**
     * Forecasts budget utilization
     */
    public BudgetForecast forecastBudgetUtilization(UUID budgetId, Integer forecastMonths) {
        try {
            log.info("Forecasting budget utilization for budget: {} for {} months", budgetId, forecastMonths);
            
            Budget budget = getBudget(budgetId);
            LocalDate today = LocalDate.now();
            
            // Calculate historical spending patterns
            SpendingPattern spendingPattern = analyzeSpendingPattern(budget, today.minusMonths(6), today);
            
            // Generate forecast for each line item
            List<ForecastedLineItem> forecastedItems = new ArrayList<>();
            
            for (BudgetLineItem lineItem : budget.getLineItems()) {
                BigDecimal averageMonthlySpending = calculateAverageMonthlySpending(
                    lineItem.getAccountId(), today.minusMonths(3), today);
                
                BigDecimal forecastedSpending = averageMonthlySpending.multiply(BigDecimal.valueOf(forecastMonths));
                BigDecimal remainingBudget = lineItem.getBudgetAmount().subtract(
                    calculateActualSpending(lineItem.getAccountId(), budget.getStartDate(), today));
                
                LocalDate projectedDepletion = null;
                if (averageMonthlySpending.compareTo(BigDecimal.ZERO) > 0 && 
                    remainingBudget.compareTo(BigDecimal.ZERO) > 0) {
                    int monthsUntilDepletion = remainingBudget.divide(
                        averageMonthlySpending, 0, RoundingMode.UP).intValue();
                    projectedDepletion = today.plusMonths(monthsUntilDepletion);
                }
                
                ForecastedLineItem forecastedItem = ForecastedLineItem.builder()
                    .lineItemId(lineItem.getLineItemId())
                    .accountName(lineItem.getAccountName())
                    .currentUtilization(calculateCurrentUtilization(lineItem, budget.getStartDate(), today))
                    .forecastedSpending(forecastedSpending)
                    .remainingBudget(remainingBudget)
                    .projectedUtilization(calculateProjectedUtilization(lineItem, forecastedSpending))
                    .projectedDepletionDate(projectedDepletion)
                    .risk(assessBudgetRisk(remainingBudget, averageMonthlySpending, budget.getEndDate()))
                    .build();
                
                forecastedItems.add(forecastedItem);
            }
            
            // Generate forecast summary
            ForecastSummary summary = generateForecastSummary(forecastedItems, budget, forecastMonths);
            
            // Generate alerts
            List<BudgetAlert> alerts = generateBudgetAlerts(forecastedItems, budget);
            
            return BudgetForecast.builder()
                .budgetId(budgetId)
                .forecastStartDate(today)
                .forecastEndDate(today.plusMonths(forecastMonths))
                .forecastedLineItems(forecastedItems)
                .summary(summary)
                .spendingPattern(spendingPattern)
                .alerts(alerts)
                .generatedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to forecast budget utilization", e);
            throw new RuntimeException("Failed to forecast budget", e);
        }
    }

    /**
     * Gets budget performance metrics
     */
    public BudgetPerformanceMetrics getBudgetPerformanceMetrics(UUID budgetId) {
        try {
            log.debug("Getting budget performance metrics for: {}", budgetId);
            
            Budget budget = getBudget(budgetId);
            LocalDate today = LocalDate.now();
            
            // Calculate performance metrics
            BigDecimal utilizationRate = calculateUtilizationRate(budget, today);
            BigDecimal efficiencyScore = calculateEfficiencyScore(budget, today);
            BigDecimal complianceRate = calculateComplianceRate(budget, today);
            
            // Calculate spending velocity
            BigDecimal spendingVelocity = calculateSpendingVelocity(budget, today);
            
            // Determine budget health
            String budgetHealth = assessBudgetHealth(utilizationRate, efficiencyScore, complianceRate);
            
            // Calculate days remaining
            long daysRemaining = ChronoUnit.DAYS.between(today, budget.getEndDate());
            BigDecimal percentTimeElapsed = calculatePercentTimeElapsed(budget.getStartDate(), budget.getEndDate(), today);
            
            // Generate performance trends
            List<PerformanceTrend> trends = generatePerformanceTrends(budget, today);
            
            return BudgetPerformanceMetrics.builder()
                .budgetId(budgetId)
                .utilizationRate(utilizationRate)
                .efficiencyScore(efficiencyScore)
                .complianceRate(complianceRate)
                .spendingVelocity(spendingVelocity)
                .budgetHealth(budgetHealth)
                .daysRemaining((int) daysRemaining)
                .percentTimeElapsed(percentTimeElapsed)
                .trends(trends)
                .calculatedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to get budget performance metrics", e);
            throw new RuntimeException("Failed to get performance metrics", e);
        }
    }

    /**
     * Creates budget alerts based on thresholds
     */
    @Scheduled(cron = "0 0 9 * * *") // Daily at 9 AM
    public void checkBudgetAlerts() {
        try {
            log.info("Checking budget alerts");
            
            List<Budget> activeBudgets = getActiveBudgets();
            LocalDate today = LocalDate.now();
            
            for (Budget budget : activeBudgets) {
                List<BudgetAlert> alerts = new ArrayList<>();
                
                // Check each line item
                for (BudgetLineItem lineItem : budget.getLineItems()) {
                    BigDecimal utilization = calculateCurrentUtilization(lineItem, budget.getStartDate(), today);
                    
                    // Check thresholds
                    if (utilization.compareTo(BigDecimal.valueOf(90)) > 0) {
                        alerts.add(BudgetAlert.builder()
                            .alertType(AlertType.CRITICAL)
                            .category(lineItem.getCategory())
                            .message(String.format("%s has exceeded 90%% utilization", lineItem.getAccountName()))
                            .utilization(utilization)
                            .threshold(BigDecimal.valueOf(90))
                            .build());
                    } else if (utilization.compareTo(BigDecimal.valueOf(75)) > 0) {
                        alerts.add(BudgetAlert.builder()
                            .alertType(AlertType.WARNING)
                            .category(lineItem.getCategory())
                            .message(String.format("%s has exceeded 75%% utilization", lineItem.getAccountName()))
                            .utilization(utilization)
                            .threshold(BigDecimal.valueOf(75))
                            .build());
                    }
                }
                
                // Process alerts
                if (!alerts.isEmpty()) {
                    processAlerts(budget, alerts);
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to check budget alerts", e);
        }
    }

    // Private helper methods

    private void validateBudgetRequest(CreateBudgetRequest request) {
        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new IllegalArgumentException("Start date must be before end date");
        }
        
        if (request.getLineItems() == null || request.getLineItems().isEmpty()) {
            throw new IllegalArgumentException("At least one budget line item is required");
        }
    }

    private List<BudgetLineItem> createBudgetLineItems(List<CreateBudgetLineItemRequest> requests, UUID budgetId) {
        return requests.stream()
            .map(request -> BudgetLineItem.builder()
                .lineItemId(UUID.randomUUID())
                .budgetId(budgetId)
                .accountId(request.getAccountId())
                .accountName(request.getAccountName())
                .category(request.getCategory())
                .budgetAmount(request.getBudgetAmount())
                .alertThreshold(request.getAlertThreshold())
                .notes(request.getNotes())
                .build())
            .collect(Collectors.toList());
    }

    private Budget getBudget(UUID budgetId) {
        // In a real implementation, this would fetch from database
        return Budget.builder()
            .budgetId(budgetId)
            .budgetName("Sample Budget")
            .startDate(LocalDate.now().minusMonths(1))
            .endDate(LocalDate.now().plusMonths(2))
            .lineItems(new ArrayList<>())
            .build();
    }

    private BigDecimal calculateActualSpending(UUID accountId, LocalDate startDate, LocalDate endDate) {
        List<LedgerEntry> entries = ledgerEntryRepository.findByAccountIdAndTransactionDateBetween(
            accountId, startDate.atStartOfDay(), endDate.atTime(23, 59, 59));
        
        return entries.stream()
            .filter(e -> e.getEntryType() == LedgerEntry.EntryType.DEBIT)
            .map(LedgerEntry::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String determineLineItemStatus(BigDecimal utilization, LocalDate asOfDate, LocalDate budgetEndDate) {
        long totalDays = ChronoUnit.DAYS.between(LocalDate.now().minusMonths(1), budgetEndDate);
        long elapsedDays = ChronoUnit.DAYS.between(LocalDate.now().minusMonths(1), asOfDate);
        BigDecimal expectedUtilization = BigDecimal.valueOf(elapsedDays * 100.0 / totalDays);
        
        if (utilization.compareTo(BigDecimal.valueOf(100)) > 0) {
            return "OVER_BUDGET";
        } else if (utilization.compareTo(expectedUtilization.add(BigDecimal.valueOf(10))) > 0) {
            return "AT_RISK";
        } else if (utilization.compareTo(expectedUtilization.subtract(BigDecimal.valueOf(10))) < 0) {
            return "UNDER_UTILIZED";
        } else {
            return "ON_TRACK";
        }
    }

    private List<BudgetInsight> generateBudgetInsights(List<BudgetVsActualLineItem> items, 
                                                      Budget budget, LocalDate asOfDate) {
        List<BudgetInsight> insights = new ArrayList<>();
        
        // Check for over-budget items
        long overBudgetCount = items.stream()
            .filter(item -> item.getActualAmount().compareTo(item.getBudgetAmount()) > 0)
            .count();
        
        if (overBudgetCount > 0) {
            insights.add(BudgetInsight.builder()
                .type("WARNING")
                .category("OVER_BUDGET")
                .message(overBudgetCount + " categories have exceeded their budget")
                .impact("HIGH")
                .build());
        }
        
        // Check for under-utilized items
        long underUtilizedCount = items.stream()
            .filter(item -> item.getUtilization().compareTo(BigDecimal.valueOf(50)) < 0)
            .count();
        
        if (underUtilizedCount > 0) {
            insights.add(BudgetInsight.builder()
                .type("INFO")
                .category("UNDER_UTILIZED")
                .message(underUtilizedCount + " categories are under 50% utilization")
                .impact("LOW")
                .build());
        }
        
        return insights;
    }

    private List<String> generateBudgetRecommendations(List<BudgetVsActualLineItem> items, 
                                                      BigDecimal overallUtilization) {
        List<String> recommendations = new ArrayList<>();
        
        if (overallUtilization.compareTo(BigDecimal.valueOf(90)) > 0) {
            recommendations.add("Consider increasing budget allocation for high-utilization categories");
        }
        
        if (overallUtilization.compareTo(BigDecimal.valueOf(50)) < 0) {
            recommendations.add("Review budget allocations - significant under-utilization detected");
        }
        
        // Add specific recommendations for over-budget items
        items.stream()
            .filter(item -> item.getStatus().equals("OVER_BUDGET"))
            .forEach(item -> recommendations.add(
                String.format("Review spending in %s - exceeded budget by %.2f%%", 
                    item.getAccountName(), item.getVariancePercentage().abs())));
        
        return recommendations;
    }

    private Map<String, CategoryVariance> calculateCategoryVariances(Budget budget, 
                                                                    LocalDate startDate, LocalDate endDate) {
        try {
            Map<String, CategoryVariance> variances = new HashMap<>();
            
            for (BudgetLineItem lineItem : budget.getLineItems()) {
                String category = lineItem.getCategory();
                BigDecimal budgetAmount = lineItem.getBudgetedAmount();
                
                // Get actual spending for this category
                BigDecimal actualAmount = getActualSpendingForCategory(category, startDate, endDate);
                BigDecimal variance = actualAmount.subtract(budgetAmount);
                BigDecimal variancePercentage = budgetAmount.compareTo(BigDecimal.ZERO) != 0 ? 
                    variance.divide(budgetAmount, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")) : BigDecimal.ZERO;
                
                CategoryVariance categoryVariance = CategoryVariance.builder()
                    .category(category)
                    .budgetedAmount(budgetAmount)
                    .actualAmount(actualAmount)
                    .variance(variance)
                    .variancePercentage(variancePercentage)
                    .favorableVariance(variance.compareTo(BigDecimal.ZERO) <= 0) // For expenses, negative variance is favorable
                    .build();
                    
                variances.put(category, categoryVariance);
            }
            
            return variances;
            
        } catch (Exception e) {
            log.error("Failed to calculate category variances", e);
            return new HashMap<>();
        }
    }

    private Map<String, MonthlyVariance> calculateMonthlyVariances(Budget budget, 
                                                                  LocalDate startDate, LocalDate endDate) {
        try {
            Map<String, MonthlyVariance> monthlyVariances = new HashMap<>();
            
            LocalDate currentMonth = startDate.withDayOfMonth(1);
            LocalDate endMonth = endDate.withDayOfMonth(1);
            
            while (!currentMonth.isAfter(endMonth)) {
                String monthKey = currentMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"));
                
                LocalDate monthStart = currentMonth;
                LocalDate monthEnd = currentMonth.withDayOfMonth(currentMonth.lengthOfMonth());
                
                // Calculate total budgeted amount for the month
                BigDecimal monthlyBudget = budget.getLineItems().stream()
                    .map(BudgetLineItem::getBudgetedAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(new BigDecimal("12"), 2, RoundingMode.HALF_UP); // Assume annual budget divided by 12
                
                // Get actual spending for the month
                BigDecimal actualSpending = getTotalActualSpending(monthStart, monthEnd);
                
                BigDecimal variance = actualSpending.subtract(monthlyBudget);
                BigDecimal variancePercentage = monthlyBudget.compareTo(BigDecimal.ZERO) != 0 ? 
                    variance.divide(monthlyBudget, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")) : BigDecimal.ZERO;
                
                MonthlyVariance monthlyVariance = MonthlyVariance.builder()
                    .month(monthKey)
                    .budgetedAmount(monthlyBudget)
                    .actualAmount(actualSpending)
                    .variance(variance)
                    .variancePercentage(variancePercentage)
                    .build();
                    
                monthlyVariances.put(monthKey, monthlyVariance);
                
                currentMonth = currentMonth.plusMonths(1);
            }
            
            return monthlyVariances;
            
        } catch (Exception e) {
            log.error("Failed to calculate monthly variances", e);
            return new HashMap<>();
        }
    }

    private List<TopVarianceItem> identifyTopVariances(Map<String, CategoryVariance> categoryVariances, 
                                                      boolean positive) {
        try {
            return categoryVariances.values().stream()
                .filter(variance -> positive ? 
                    variance.getVariance().compareTo(BigDecimal.ZERO) > 0 : 
                    variance.getVariance().compareTo(BigDecimal.ZERO) < 0)
                .sorted((a, b) -> positive ? 
                    b.getVariance().compareTo(a.getVariance()) : 
                    a.getVariance().compareTo(b.getVariance()))
                .limit(5) // Top 5 variances
                .map(variance -> TopVarianceItem.builder()
                    .category(variance.getCategory())
                    .variance(variance.getVariance())
                    .variancePercentage(variance.getVariancePercentage())
                    .budgetedAmount(variance.getBudgetedAmount())
                    .actualAmount(variance.getActualAmount())
                    .build())
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Failed to identify top variances", e);
            return new ArrayList<>();
        }
    }

    private VarianceTrend analyzeVarianceTrend(Map<String, MonthlyVariance> monthlyVariances) {
        // Analyze variance trends
        return VarianceTrend.builder()
            .trend("IMPROVING")
            .build();
    }

    private List<VarianceExplanation> generateVarianceExplanations(Map<String, CategoryVariance> categoryVariances,
                                                                  List<TopVarianceItem> topPositive,
                                                                  List<TopVarianceItem> topNegative) {
        try {
            List<VarianceExplanation> explanations = new ArrayList<>();
            
            // Explanations for top positive variances (overspending)
            for (TopVarianceItem item : topPositive) {
                String explanation = generateVarianceExplanation(item, true);
                explanations.add(VarianceExplanation.builder()
                    .category(item.getCategory())
                    .varianceType("NEGATIVE") // Overspending is negative for budget management
                    .explanation(explanation)
                    .severity(determineVarianceSeverity(item.getVariancePercentage()))
                    .recommendedAction(generateRecommendedAction(item, true))
                    .build());
            }
            
            // Explanations for top negative variances (underspending)
            for (TopVarianceItem item : topNegative) {
                String explanation = generateVarianceExplanation(item, false);
                explanations.add(VarianceExplanation.builder()
                    .category(item.getCategory())
                    .varianceType("POSITIVE") // Underspending is positive for budget management
                    .explanation(explanation)
                    .severity(determineVarianceSeverity(item.getVariancePercentage().abs()))
                    .recommendedAction(generateRecommendedAction(item, false))
                    .build());
            }
            
            return explanations;
            
        } catch (Exception e) {
            log.error("Failed to generate variance explanations", e);
            return new ArrayList<>();
        }
    }

    private SpendingPattern analyzeSpendingPattern(Budget budget, LocalDate startDate, LocalDate endDate) {
        // Analyze historical spending patterns
        return SpendingPattern.builder()
            .pattern("CONSISTENT")
            .build();
    }

    private BigDecimal calculateAverageMonthlySpending(UUID accountId, LocalDate startDate, LocalDate endDate) {
        long months = ChronoUnit.MONTHS.between(startDate, endDate);
        if (months == 0) months = 1;
        
        BigDecimal totalSpending = calculateActualSpending(accountId, startDate, endDate);
        return totalSpending.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateCurrentUtilization(BudgetLineItem lineItem, LocalDate startDate, LocalDate today) {
        BigDecimal actual = calculateActualSpending(lineItem.getAccountId(), startDate, today);
        
        if (lineItem.getBudgetAmount().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        return actual.divide(lineItem.getBudgetAmount(), 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
    }

    private BigDecimal calculateProjectedUtilization(BudgetLineItem lineItem, BigDecimal forecastedSpending) {
        BigDecimal projected = forecastedSpending.add(lineItem.getBudgetAmount());
        
        if (lineItem.getBudgetAmount().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        return projected.divide(lineItem.getBudgetAmount(), 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
    }

    private String assessBudgetRisk(BigDecimal remainingBudget, BigDecimal averageSpending, LocalDate endDate) {
        if (remainingBudget.compareTo(BigDecimal.ZERO) <= 0) {
            return "EXCEEDED";
        }
        
        long daysRemaining = ChronoUnit.DAYS.between(LocalDate.now(), endDate);
        BigDecimal projectedSpending = averageSpending.multiply(
            BigDecimal.valueOf(daysRemaining).divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP));
        
        if (projectedSpending.compareTo(remainingBudget) > 0) {
            return "HIGH";
        } else if (projectedSpending.compareTo(remainingBudget.multiply(BigDecimal.valueOf(0.8))) > 0) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    private ForecastSummary generateForecastSummary(List<ForecastedLineItem> items, Budget budget, Integer months) {
        // Generate forecast summary
        return ForecastSummary.builder()
            .forecastMonths(months)
            .build();
    }

    private List<BudgetAlert> generateBudgetAlerts(List<ForecastedLineItem> items, Budget budget) {
        List<BudgetAlert> alerts = new ArrayList<>();
        
        for (ForecastedLineItem item : items) {
            if ("HIGH".equals(item.getRisk()) || "EXCEEDED".equals(item.getRisk())) {
                alerts.add(BudgetAlert.builder()
                    .alertType(AlertType.WARNING)
                    .message("High risk of budget overrun for " + item.getAccountName())
                    .build());
            }
        }
        
        return alerts;
    }

    private BigDecimal calculateUtilizationRate(Budget budget, LocalDate asOfDate) {
        BigDecimal totalBudget = budget.getTotalBudgetAmount();
        BigDecimal totalActual = BigDecimal.ZERO;
        
        for (BudgetLineItem item : budget.getLineItems()) {
            totalActual = totalActual.add(calculateActualSpending(
                item.getAccountId(), budget.getStartDate(), asOfDate));
        }
        
        if (totalBudget.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        return totalActual.divide(totalBudget, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
    }

    private BigDecimal calculateEfficiencyScore(Budget budget, LocalDate asOfDate) {
        // Calculate efficiency based on spending patterns
        return BigDecimal.valueOf(85); // Placeholder
    }

    private BigDecimal calculateComplianceRate(Budget budget, LocalDate asOfDate) {
        // Calculate compliance with budget limits
        long compliantItems = budget.getLineItems().stream()
            .filter(item -> {
                BigDecimal actual = calculateActualSpending(item.getAccountId(), budget.getStartDate(), asOfDate);
                return actual.compareTo(item.getBudgetAmount()) <= 0;
            })
            .count();
        
        return BigDecimal.valueOf(compliantItems * 100.0 / budget.getLineItems().size());
    }

    private BigDecimal calculateSpendingVelocity(Budget budget, LocalDate asOfDate) {
        // Calculate rate of spending
        long daysElapsed = ChronoUnit.DAYS.between(budget.getStartDate(), asOfDate);
        if (daysElapsed == 0) daysElapsed = 1;
        
        BigDecimal totalSpending = BigDecimal.ZERO;
        for (BudgetLineItem item : budget.getLineItems()) {
            totalSpending = totalSpending.add(calculateActualSpending(
                item.getAccountId(), budget.getStartDate(), asOfDate));
        }
        
        return totalSpending.divide(BigDecimal.valueOf(daysElapsed), 2, RoundingMode.HALF_UP);
    }

    private String assessBudgetHealth(BigDecimal utilization, BigDecimal efficiency, BigDecimal compliance) {
        BigDecimal healthScore = utilization.add(efficiency).add(compliance).divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
        
        if (healthScore.compareTo(BigDecimal.valueOf(80)) > 0) {
            return "HEALTHY";
        } else if (healthScore.compareTo(BigDecimal.valueOf(60)) > 0) {
            return "MODERATE";
        } else {
            return "NEEDS_ATTENTION";
        }
    }

    private BigDecimal calculatePercentTimeElapsed(LocalDate startDate, LocalDate endDate, LocalDate currentDate) {
        long totalDays = ChronoUnit.DAYS.between(startDate, endDate);
        long elapsedDays = ChronoUnit.DAYS.between(startDate, currentDate);
        
        if (totalDays == 0) {
            return BigDecimal.valueOf(100);
        }
        
        return BigDecimal.valueOf(elapsedDays * 100.0 / totalDays);
    }

    private List<PerformanceTrend> generatePerformanceTrends(Budget budget, LocalDate asOfDate) {
        // Generate performance trend analysis
        return new ArrayList<>(); // Placeholder
    }

    private List<Budget> getActiveBudgets() {
        // Get all active budgets
        return new ArrayList<>(); // Placeholder
    }

    private void processAlerts(Budget budget, List<BudgetAlert> alerts) {
        // Process and send alerts
        log.info("Processing {} alerts for budget: {}", alerts.size(), budget.getBudgetName());
    }

    // Enums
    public enum BudgetStatus {
        DRAFT,
        ACTIVE,
        CLOSED,
        EXPIRED
    }

    public enum BudgetType {
        OPERATING,
        CAPITAL,
        PROJECT,
        DEPARTMENTAL
    }

    public enum AlertType {
        INFO,
        WARNING,
        CRITICAL
    }
    
    // Helper methods for budget variance analysis
    
    private BigDecimal getActualSpendingForCategory(String category, LocalDate startDate, LocalDate endDate) {
        try {
            // Get expense accounts for this category
            List<Account> categoryAccounts = accountRepository.findByAccountCategoryAndAccountTypeIn(
                category, Arrays.asList(Account.AccountType.EXPENSE, Account.AccountType.COST_OF_GOODS_SOLD));
            
            BigDecimal totalSpending = BigDecimal.ZERO;
            LocalDateTime startDateTime = startDate.atStartOfDay();
            LocalDateTime endDateTime = endDate.atTime(23, 59, 59);
            
            for (Account account : categoryAccounts) {
                // For expense accounts, debit entries represent spending
                List<LedgerEntry> expenseEntries = ledgerEntryRepository.findByAccountIdAndEntryTypeAndTransactionDateBetween(
                    account.getAccountId(), LedgerEntry.EntryType.DEBIT, startDateTime, endDateTime);
                    
                BigDecimal accountSpending = expenseEntries.stream()
                    .map(LedgerEntry::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                    
                totalSpending = totalSpending.add(accountSpending);
            }
            
            return totalSpending;
            
        } catch (Exception e) {
            log.error("Failed to get actual spending for category: {}", category, e);
            return BigDecimal.ZERO;
        }
    }
    
    private BigDecimal getTotalActualSpending(LocalDate startDate, LocalDate endDate) {
        try {
            // Get all expense accounts
            List<Account> expenseAccounts = accountRepository.findByAccountTypeIn(
                Arrays.asList(Account.AccountType.EXPENSE, Account.AccountType.COST_OF_GOODS_SOLD));
            
            BigDecimal totalSpending = BigDecimal.ZERO;
            LocalDateTime startDateTime = startDate.atStartOfDay();
            LocalDateTime endDateTime = endDate.atTime(23, 59, 59);
            
            for (Account account : expenseAccounts) {
                List<LedgerEntry> expenseEntries = ledgerEntryRepository.findByAccountIdAndEntryTypeAndTransactionDateBetween(
                    account.getAccountId(), LedgerEntry.EntryType.DEBIT, startDateTime, endDateTime);
                    
                BigDecimal accountSpending = expenseEntries.stream()
                    .map(LedgerEntry::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                    
                totalSpending = totalSpending.add(accountSpending);
            }
            
            return totalSpending;
            
        } catch (Exception e) {
            log.error("Failed to get total actual spending", e);
            return BigDecimal.ZERO;
        }
    }
    
    private String generateVarianceExplanation(TopVarianceItem item, boolean isOverspending) {
        BigDecimal variancePercentage = item.getVariancePercentage().abs();
        
        if (isOverspending) {
            if (variancePercentage.compareTo(new BigDecimal("50")) > 0) {
                return String.format("Significant overspending in %s category by %.1f%%. " +
                    "This represents $%.2f over budget and requires immediate attention.",
                    item.getCategory(), variancePercentage, item.getVariance());
            } else if (variancePercentage.compareTo(new BigDecimal("20")) > 0) {
                return String.format("Moderate overspending in %s category by %.1f%%. " +
                    "Budget exceeded by $%.2f, review spending patterns.",
                    item.getCategory(), variancePercentage, item.getVariance());
            } else {
                return String.format("Minor overspending in %s category by %.1f%%. " +
                    "Budget exceeded by $%.2f, within acceptable variance.",
                    item.getCategory(), variancePercentage, item.getVariance());
            }
        } else {
            if (variancePercentage.compareTo(new BigDecimal("30")) > 0) {
                return String.format("Significant underspending in %s category by %.1f%%. " +
                    "Savings of $%.2f may indicate budget overallocation or delayed expenses.",
                    item.getCategory(), variancePercentage, item.getVariance().abs());
            } else {
                return String.format("Moderate underspending in %s category by %.1f%%. " +
                    "Savings of $%.2f represents good cost control.",
                    item.getCategory(), variancePercentage, item.getVariance().abs());
            }
        }
    }
    
    private String determineVarianceSeverity(BigDecimal variancePercentage) {
        if (variancePercentage.compareTo(new BigDecimal("50")) > 0) {
            return "HIGH";
        } else if (variancePercentage.compareTo(new BigDecimal("20")) > 0) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }
    
    private String generateRecommendedAction(TopVarianceItem item, boolean isOverspending) {
        if (isOverspending) {
            BigDecimal variancePercentage = item.getVariancePercentage();
            if (variancePercentage.compareTo(new BigDecimal("50")) > 0) {
                return "Immediately review all expenses in this category, implement spending freeze, and investigate root causes.";
            } else if (variancePercentage.compareTo(new BigDecimal("20")) > 0) {
                return "Review spending patterns, implement cost controls, and adjust future budget allocations.";
            } else {
                return "Monitor spending trends and consider slight budget adjustment for next period.";
            }
        } else {
            return "Review if underspending represents genuine savings or delayed expenses. Consider budget reallocation.";
        }
    }
}