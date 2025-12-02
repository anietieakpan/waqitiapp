package com.waqiti.analytics.processor;

import com.waqiti.analytics.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Enterprise-grade processor for merchant analytics
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MerchantAnalyticsProcessor {

    private final Map<String, MerchantAnalytics> merchantAnalyticsCache = new ConcurrentHashMap<>();
    private final Map<String, List<PaymentEvent>> merchantTransactionHistory = new ConcurrentHashMap<>();
    private final Map<String, Map<String, BigDecimal>> dailyRevenue = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> merchantCustomers = new ConcurrentHashMap<>();

    /**
     * Process merchant transaction
     */
    public void processMerchantTransaction(PaymentEvent event) {
        String merchantId = event.getMerchantId();
        if (merchantId == null) {
            return;
        }
        
        // Store transaction history
        merchantTransactionHistory.computeIfAbsent(merchantId, k -> new ArrayList<>()).add(event);
        
        // Track unique customers
        merchantCustomers.computeIfAbsent(merchantId, k -> new HashSet<>()).add(event.getCustomerId());
        
        // Update daily revenue
        updateDailyRevenue(merchantId, event);
        
        // Update merchant analytics
        updateMerchantAnalytics(merchantId, event);
    }

    /**
     * Update daily revenue tracking
     */
    private void updateDailyRevenue(String merchantId, PaymentEvent event) {
        String dateKey = LocalDate.now().toString();
        Map<String, BigDecimal> merchantDailyRevenue = dailyRevenue.computeIfAbsent(merchantId, k -> new ConcurrentHashMap<>());
        merchantDailyRevenue.merge(dateKey, event.getAmount(), BigDecimal::add);
    }

    /**
     * Update merchant analytics
     */
    private void updateMerchantAnalytics(String merchantId, PaymentEvent event) {
        MerchantAnalytics analytics = merchantAnalyticsCache.computeIfAbsent(merchantId, 
                k -> createNewMerchantAnalytics(k));
        
        // Update transaction metrics
        analytics.setTotalTransactions(analytics.getTotalTransactions() + 1);
        analytics.setTotalRevenue(analytics.getTotalRevenue().add(event.getAmount()));
        
        // Update average transaction amount
        analytics.setAverageTransactionAmount(
                analytics.getTotalRevenue().divide(
                        BigDecimal.valueOf(analytics.getTotalTransactions()), 
                        2, RoundingMode.HALF_UP));
        
        // Update max transaction amount
        if (event.getAmount().compareTo(analytics.getMaxTransactionAmount()) > 0) {
            analytics.setMaxTransactionAmount(event.getAmount());
        }
        
        // Update unique customers count
        Set<String> customers = merchantCustomers.get(merchantId);
        analytics.setUniqueCustomers((long) customers.size());
        
        // Update payment method breakdown
        updatePaymentMethodBreakdown(analytics, event.getPaymentMethod(), event.getAmount());
        
        // Update currency breakdown
        updateCurrencyBreakdown(analytics, event.getCurrency(), event.getAmount());
        
        // Update first/last transaction dates
        if (analytics.getFirstTransactionDate() == null) {
            analytics.setFirstTransactionDate(event.getTimestamp());
        }
        analytics.setLastTransactionDate(event.getTimestamp());
        
        // Update status tracking
        updateTransactionStatusCounts(analytics, event.getStatus());
        
        analytics.setLastUpdated(Instant.now());
    }

    /**
     * Create new merchant analytics
     */
    private MerchantAnalytics createNewMerchantAnalytics(String merchantId) {
        return MerchantAnalytics.builder()
                .merchantId(merchantId)
                .totalTransactions(0L)
                .totalRevenue(BigDecimal.ZERO)
                .averageTransactionAmount(BigDecimal.ZERO)
                .maxTransactionAmount(BigDecimal.ZERO)
                .uniqueCustomers(0L)
                .paymentMethodBreakdown(new HashMap<>())
                .currencyBreakdown(new HashMap<>())
                .statusCounts(new HashMap<>())
                .createdAt(Instant.now())
                .lastUpdated(Instant.now())
                .build();
    }

    /**
     * Update payment method breakdown
     */
    private void updatePaymentMethodBreakdown(MerchantAnalytics analytics, String paymentMethod, BigDecimal amount) {
        Map<String, BigDecimal> breakdown = analytics.getPaymentMethodBreakdown();
        breakdown.merge(paymentMethod, amount, BigDecimal::add);
    }

    /**
     * Update currency breakdown
     */
    private void updateCurrencyBreakdown(MerchantAnalytics analytics, String currency, BigDecimal amount) {
        Map<String, BigDecimal> breakdown = analytics.getCurrencyBreakdown();
        breakdown.merge(currency, amount, BigDecimal::add);
    }

    /**
     * Update transaction status counts
     */
    private void updateTransactionStatusCounts(MerchantAnalytics analytics, String status) {
        Map<String, Long> statusCounts = analytics.getStatusCounts();
        statusCounts.merge(status, 1L, Long::sum);
    }

    /**
     * Get merchant analytics
     */
    public MerchantAnalytics getMerchantAnalytics(String merchantId) {
        MerchantAnalytics analytics = merchantAnalyticsCache.get(merchantId);
        if (analytics == null) {
            return createNewMerchantAnalytics(merchantId);
        }
        
        // Calculate additional metrics
        calculateAdditionalMetrics(analytics, merchantId);
        
        return analytics;
    }

    /**
     * Calculate additional metrics
     */
    private void calculateAdditionalMetrics(MerchantAnalytics analytics, String merchantId) {
        List<PaymentEvent> transactions = merchantTransactionHistory.get(merchantId);
        if (transactions != null && !transactions.isEmpty()) {
            // Calculate success rate
            calculateSuccessRate(analytics);
            
            // Calculate revenue growth
            calculateRevenueGrowth(analytics, merchantId);
            
            // Calculate customer metrics
            calculateCustomerMetrics(analytics, transactions);
            
            // Calculate peak hours
            calculatePeakHours(analytics, transactions);
            
            // Calculate churn analysis
            calculateChurnMetrics(analytics, transactions);
        }
    }

    /**
     * Calculate success rate
     */
    private void calculateSuccessRate(MerchantAnalytics analytics) {
        Map<String, Long> statusCounts = analytics.getStatusCounts();
        long successful = statusCounts.getOrDefault("COMPLETED", 0L) + 
                         statusCounts.getOrDefault("SUCCESS", 0L);
        
        if (analytics.getTotalTransactions() > 0) {
            double successRate = (double) successful / analytics.getTotalTransactions() * 100;
            analytics.setSuccessRate(BigDecimal.valueOf(successRate).setScale(2, RoundingMode.HALF_UP));
        }
    }

    /**
     * Calculate revenue growth
     */
    private void calculateRevenueGrowth(MerchantAnalytics analytics, String merchantId) {
        Map<String, BigDecimal> merchantRevenue = dailyRevenue.get(merchantId);
        if (merchantRevenue == null || merchantRevenue.size() < 2) {
            return;
        }
        
        // Get last 7 days revenue
        List<String> last7Days = generateDateKeys(7);
        BigDecimal currentWeekRevenue = last7Days.stream()
                .map(date -> merchantRevenue.getOrDefault(date, BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Get previous 7 days revenue
        List<String> previous7Days = generateDateKeys(14, 7);
        BigDecimal previousWeekRevenue = previous7Days.stream()
                .map(date -> merchantRevenue.getOrDefault(date, BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Calculate growth percentage
        if (previousWeekRevenue.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal growth = currentWeekRevenue.subtract(previousWeekRevenue)
                    .divide(previousWeekRevenue, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            analytics.setRevenueGrowthRate(growth);
        }
    }

    /**
     * Calculate customer metrics
     */
    private void calculateCustomerMetrics(MerchantAnalytics analytics, List<PaymentEvent> transactions) {
        // Calculate average transactions per customer
        if (analytics.getUniqueCustomers() > 0) {
            BigDecimal avgTransactionsPerCustomer = BigDecimal.valueOf(analytics.getTotalTransactions())
                    .divide(BigDecimal.valueOf(analytics.getUniqueCustomers()), 2, RoundingMode.HALF_UP);
            analytics.setAverageTransactionsPerCustomer(avgTransactionsPerCustomer);
        }
        
        // Calculate customer lifetime value (simplified)
        if (analytics.getUniqueCustomers() > 0) {
            BigDecimal clv = analytics.getTotalRevenue()
                    .divide(BigDecimal.valueOf(analytics.getUniqueCustomers()), 2, RoundingMode.HALF_UP);
            analytics.setCustomerLifetimeValue(clv);
        }
        
        // Calculate repeat customer rate
        calculateRepeatCustomerRate(analytics, transactions);
    }

    /**
     * Calculate repeat customer rate
     */
    private void calculateRepeatCustomerRate(MerchantAnalytics analytics, List<PaymentEvent> transactions) {
        Map<String, Long> customerTransactionCounts = transactions.stream()
                .collect(Collectors.groupingBy(PaymentEvent::getCustomerId, Collectors.counting()));
        
        long repeatCustomers = customerTransactionCounts.values().stream()
                .mapToLong(count -> count > 1 ? 1 : 0)
                .sum();
        
        if (analytics.getUniqueCustomers() > 0) {
            double repeatRate = (double) repeatCustomers / analytics.getUniqueCustomers() * 100;
            analytics.setRepeatCustomerRate(BigDecimal.valueOf(repeatRate).setScale(2, RoundingMode.HALF_UP));
        }
    }

    /**
     * Calculate peak hours
     */
    private void calculatePeakHours(MerchantAnalytics analytics, List<PaymentEvent> transactions) {
        Map<Integer, Long> hourlyTransactions = transactions.stream()
                .collect(Collectors.groupingBy(
                        tx -> tx.getTimestamp().atZone(ZoneId.systemDefault()).getHour(),
                        Collectors.counting()
                ));
        
        Integer peakHour = hourlyTransactions.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(12);
        
        analytics.setPeakHour(peakHour);
    }

    /**
     * Calculate churn metrics
     */
    private void calculateChurnMetrics(MerchantAnalytics analytics, List<PaymentEvent> transactions) {
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        
        // Get customers who transacted in the last 30 days
        Set<String> recentCustomers = transactions.stream()
                .filter(tx -> tx.getTimestamp().isAfter(thirtyDaysAgo))
                .map(PaymentEvent::getCustomerId)
                .collect(Collectors.toSet());
        
        // Get customers who transacted between 30-60 days ago
        Instant sixtyDaysAgo = Instant.now().minus(60, ChronoUnit.DAYS);
        Set<String> previousCustomers = transactions.stream()
                .filter(tx -> tx.getTimestamp().isBefore(thirtyDaysAgo) && tx.getTimestamp().isAfter(sixtyDaysAgo))
                .map(PaymentEvent::getCustomerId)
                .collect(Collectors.toSet());
        
        // Calculate churn rate
        if (!previousCustomers.isEmpty()) {
            long churnedCustomers = previousCustomers.stream()
                    .mapToLong(customer -> recentCustomers.contains(customer) ? 0 : 1)
                    .sum();
            
            double churnRate = (double) churnedCustomers / previousCustomers.size() * 100;
            analytics.setChurnRate(BigDecimal.valueOf(churnRate).setScale(2, RoundingMode.HALF_UP));
        }
    }

    /**
     * Generate date keys for the last N days
     */
    private List<String> generateDateKeys(int days) {
        return generateDateKeys(days, 0);
    }

    /**
     * Generate date keys with offset
     */
    private List<String> generateDateKeys(int days, int offsetDays) {
        List<String> dateKeys = new ArrayList<>();
        LocalDate startDate = LocalDate.now().minusDays(days + offsetDays);
        
        for (int i = 0; i < days; i++) {
            dateKeys.add(startDate.plusDays(i).toString());
        }
        
        return dateKeys;
    }

    /**
     * Get top merchants by revenue
     */
    public List<MerchantRankingResult> getTopMerchantsByRevenue(int limit) {
        return merchantAnalyticsCache.values().stream()
                .sorted((a, b) -> b.getTotalRevenue().compareTo(a.getTotalRevenue()))
                .limit(limit)
                .map(analytics -> MerchantRankingResult.builder()
                        .merchantId(analytics.getMerchantId())
                        .totalRevenue(analytics.getTotalRevenue())
                        .totalTransactions(analytics.getTotalTransactions())
                        .uniqueCustomers(analytics.getUniqueCustomers())
                        .successRate(analytics.getSuccessRate())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Get merchant performance comparison
     */
    public MerchantPerformanceComparison getMerchantPerformanceComparison(String merchantId) {
        MerchantAnalytics merchant = getMerchantAnalytics(merchantId);
        
        // Calculate industry averages
        OptionalDouble avgRevenue = merchantAnalyticsCache.values().stream()
                .mapToDouble(m -> m.getTotalRevenue().doubleValue())
                .average();
        
        OptionalDouble avgTransactions = merchantAnalyticsCache.values().stream()
                .mapToDouble(m -> m.getTotalTransactions().doubleValue())
                .average();
        
        OptionalDouble avgSuccessRate = merchantAnalyticsCache.values().stream()
                .filter(m -> m.getSuccessRate() != null)
                .mapToDouble(m -> m.getSuccessRate().doubleValue())
                .average();
        
        return MerchantPerformanceComparison.builder()
                .merchantId(merchantId)
                .revenuePercentile(calculatePercentile(merchant.getTotalRevenue(), 
                        merchantAnalyticsCache.values().stream()
                                .map(MerchantAnalytics::getTotalRevenue)
                                .collect(Collectors.toList())))
                .transactionPercentile(calculatePercentile(BigDecimal.valueOf(merchant.getTotalTransactions()),
                        merchantAnalyticsCache.values().stream()
                                .map(m -> BigDecimal.valueOf(m.getTotalTransactions()))
                                .collect(Collectors.toList())))
                .industryAverageRevenue(avgRevenue.orElse(0.0))
                .industryAverageTransactions(avgTransactions.orElse(0.0))
                .industryAverageSuccessRate(avgSuccessRate.orElse(0.0))
                .build();
    }

    /**
     * Calculate percentile ranking
     */
    private int calculatePercentile(BigDecimal value, List<BigDecimal> allValues) {
        List<BigDecimal> sorted = allValues.stream()
                .sorted()
                .collect(Collectors.toList());
        
        int rank = 0;
        for (BigDecimal val : sorted) {
            if (val.compareTo(value) <= 0) {
                rank++;
            } else {
                break;
            }
        }
        
        return (int) ((double) rank / sorted.size() * 100);
    }

    /**
     * Clear old data
     */
    public void clearOldData(int daysToKeep) {
        Instant cutoffDate = Instant.now().minus(daysToKeep, ChronoUnit.DAYS);
        
        // Clear old transaction history
        merchantTransactionHistory.values().forEach(transactions -> 
                transactions.removeIf(tx -> tx.getTimestamp().isBefore(cutoffDate)));
        
        // Clear old daily revenue data
        String cutoffDateStr = LocalDate.now().minusDays(daysToKeep).toString();
        dailyRevenue.values().forEach(merchantRevenue -> 
                merchantRevenue.entrySet().removeIf(entry -> entry.getKey().compareTo(cutoffDateStr) < 0));
    }
}