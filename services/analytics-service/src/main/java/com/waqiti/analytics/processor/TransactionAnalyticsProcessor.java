package com.waqiti.analytics.processor;

import com.waqiti.analytics.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Processor for transaction analytics
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionAnalyticsProcessor {

    private final Map<String, List<PaymentEvent>> transactionHistory = new ConcurrentHashMap<>();
    private final Map<String, TransactionMetrics> dailyMetrics = new ConcurrentHashMap<>();

    /**
     * Process transaction event
     */
    public void processTransactionEvent(PaymentEvent event) {
        // Store transaction
        storeTransaction(event);
        
        // Update metrics
        updateDailyMetrics(event);
        
        // Analyze patterns
        analyzeTransactionPatterns(event);
    }

    /**
     * Store transaction for analysis
     */
    private void storeTransaction(PaymentEvent event) {
        String dateKey = LocalDate.now().toString();
        transactionHistory.computeIfAbsent(dateKey, k -> new ArrayList<>()).add(event);
    }

    /**
     * Update daily metrics
     */
    private void updateDailyMetrics(PaymentEvent event) {
        String dateKey = LocalDate.now().toString();
        
        dailyMetrics.compute(dateKey, (key, existing) -> {
            if (existing == null) {
                existing = TransactionMetrics.builder()
                        .period(dateKey)
                        .transactionCount(0L)
                        .totalVolume(BigDecimal.ZERO)
                        .build();
            }
            
            existing.setTransactionCount(existing.getTransactionCount() + 1);
            existing.setTotalVolume(existing.getTotalVolume().add(event.getAmount()));
            existing.setAverageAmount(existing.getTotalVolume().divide(
                    BigDecimal.valueOf(existing.getTransactionCount()), 
                    2, RoundingMode.HALF_UP));
            
            return existing;
        });
    }

    /**
     * Analyze transaction patterns
     */
    private void analyzeTransactionPatterns(PaymentEvent event) {
        // Analyze velocity patterns
        analyzeVelocityPatterns(event);
        
        // Analyze amount patterns
        analyzeAmountPatterns(event);
        
        // Analyze time patterns
        analyzeTimePatterns(event);
    }

    /**
     * Analyze velocity patterns
     */
    private void analyzeVelocityPatterns(PaymentEvent event) {
        String customerKey = "customer-" + event.getCustomerId();
        List<PaymentEvent> customerTransactions = getRecentTransactions(customerKey, 24);
        
        if (customerTransactions.size() > 10) {
            log.warn("High velocity detected for customer: {}", event.getCustomerId());
        }
    }

    /**
     * Analyze amount patterns
     */
    private void analyzeAmountPatterns(PaymentEvent event) {
        String customerKey = "customer-" + event.getCustomerId();
        List<PaymentEvent> recentTransactions = getRecentTransactions(customerKey, 168); // 7 days
        
        if (!recentTransactions.isEmpty()) {
            BigDecimal averageAmount = calculateAverageAmount(recentTransactions);
            
            // Check for unusual amounts
            if (event.getAmount().compareTo(averageAmount.multiply(BigDecimal.valueOf(5))) > 0) {
                log.warn("Unusual amount detected for customer: {}", event.getCustomerId());
            }
        }
    }

    /**
     * Analyze time patterns
     */
    private void analyzeTimePatterns(PaymentEvent event) {
        int hour = event.getTimestamp().atZone(ZoneId.systemDefault()).getHour();
        
        // Flag transactions outside normal hours
        if (hour < 6 || hour > 22) {
            log.info("Off-hours transaction detected: {}", event.getTransactionId());
        }
    }

    /**
     * Get recent transactions
     */
    private List<PaymentEvent> getRecentTransactions(String key, int hours) {
        Instant cutoff = Instant.now().minus(hours, ChronoUnit.HOURS);
        
        return transactionHistory.values().stream()
                .flatMap(List::stream)
                .filter(tx -> tx.getTimestamp().isAfter(cutoff))
                .collect(Collectors.toList());
    }

    /**
     * Calculate average amount
     */
    private BigDecimal calculateAverageAmount(List<PaymentEvent> transactions) {
        if (transactions.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal total = transactions.stream()
                .map(PaymentEvent::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return total.divide(BigDecimal.valueOf(transactions.size()), 2, RoundingMode.HALF_UP);
    }

    /**
     * Analyze trending patterns
     */
    public List<TrendingPattern> analyzeTrendingPatterns() {
        List<TrendingPattern> patterns = new ArrayList<>();
        
        // Analyze daily trends
        patterns.addAll(analyzeDailyTrends());
        
        // Analyze hourly trends
        patterns.addAll(analyzeHourlyTrends());
        
        return patterns;
    }

    /**
     * Analyze daily trends
     */
    private List<TrendingPattern> analyzeDailyTrends() {
        return dailyMetrics.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> TrendingPattern.builder()
                        .pattern("DAILY_VOLUME")
                        .period(entry.getKey())
                        .value(entry.getValue().getTotalVolume())
                        .trend(calculateTrend(entry.getValue()))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Analyze hourly trends
     */
    private List<TrendingPattern> analyzeHourlyTrends() {
        // Group transactions by hour
        Map<Integer, Long> hourlyTransactions = transactionHistory.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.groupingBy(
                        tx -> tx.getTimestamp().atZone(ZoneId.systemDefault()).getHour(),
                        Collectors.counting()
                ));
        
        return hourlyTransactions.entrySet().stream()
                .map(entry -> TrendingPattern.builder()
                        .pattern("HOURLY_TRANSACTIONS")
                        .period("HOUR_" + entry.getKey())
                        .value(BigDecimal.valueOf(entry.getValue()))
                        .trend("STABLE")
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Calculate trend
     */
    private String calculateTrend(TransactionMetrics metrics) {
        // Simple trend calculation - could be enhanced with more sophisticated algorithms
        if (metrics.getTransactionCount() > 100) {
            return "INCREASING";
        } else if (metrics.getTransactionCount() < 50) {
            return "DECREASING";
        } else {
            return "STABLE";
        }
    }

    /**
     * Get transaction metrics
     */
    public TransactionMetrics getTransactionMetrics(String period) {
        return dailyMetrics.get(period);
    }

    /**
     * Get all metrics
     */
    public Map<String, TransactionMetrics> getAllMetrics() {
        return new HashMap<>(dailyMetrics);
    }
}