package com.waqiti.analytics.engine;

import com.waqiti.analytics.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Enterprise-grade real-time analytics engine
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealTimeAnalyticsEngine {

    private final Map<String, AtomicLong> transactionCounters = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> volumeCounters = new ConcurrentHashMap<>();
    private final Map<String, List<TransactionEvent>> recentTransactions = new ConcurrentHashMap<>();
    
    // Analytics processors
    private final TransactionAnalyticsProcessor transactionProcessor;
    private final UserAnalyticsProcessor userProcessor;
    private final MerchantAnalyticsProcessor merchantProcessor;
    private final FraudAnalyticsProcessor fraudProcessor;
    
    /**
     * Process payment transaction event
     */
    @KafkaListener(topics = "payment-events")
    public void processPaymentEvent(PaymentEvent event) {
        try {
            log.debug("Processing payment event: {}", event.getTransactionId());
            
            // Update real-time counters
            updateTransactionCounters(event);
            updateVolumeCounters(event);
            storeRecentTransaction(event);
            
            // Process through analytics processors
            transactionProcessor.processTransactionEvent(event);
            userProcessor.processUserTransaction(event);
            merchantProcessor.processMerchantTransaction(event);
            
            // Check for fraud patterns
            fraudProcessor.analyzeTransaction(event);
            
        } catch (Exception e) {
            log.error("Error processing payment event", e);
        }
    }

    /**
     * Process user behavior event
     */
    @KafkaListener(topics = "user-behavior-events")
    public void processUserBehaviorEvent(UserBehaviorEvent event) {
        try {
            log.debug("Processing user behavior event: {}", event.getEventType());
            userProcessor.processUserBehavior(event);
        } catch (Exception e) {
            log.error("Error processing user behavior event", e);
        }
    }

    /**
     * Get real-time transaction statistics
     */
    public TransactionStats getRealTimeTransactionStats() {
        long totalTransactions = transactionCounters.values().stream()
                .mapToLong(AtomicLong::get)
                .sum();
        
        BigDecimal totalVolume = volumeCounters.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return TransactionStats.builder()
                .totalTransactions(totalTransactions)
                .totalVolume(totalVolume)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Get transaction metrics for time period
     */
    public TransactionMetrics getTransactionMetrics(String period) {
        // Implementation would aggregate data based on period
        return TransactionMetrics.builder()
                .period(period)
                .transactionCount(getTransactionCount(period))
                .totalVolume(getTotalVolume(period))
                .averageAmount(getAverageAmount(period))
                .build();
    }

    /**
     * Get user analytics
     */
    public UserAnalytics getUserAnalytics(String userId) {
        return userProcessor.getUserAnalytics(userId);
    }

    /**
     * Get merchant analytics
     */
    public MerchantAnalytics getMerchantAnalytics(String merchantId) {
        return merchantProcessor.getMerchantAnalytics(merchantId);
    }

    /**
     * Update transaction counters
     */
    private void updateTransactionCounters(PaymentEvent event) {
        String dateKey = LocalDate.now().toString();
        transactionCounters.computeIfAbsent(dateKey, k -> new AtomicLong(0)).incrementAndGet();
        
        String hourKey = dateKey + "-" + Instant.now().atZone(ZoneId.systemDefault()).getHour();
        transactionCounters.computeIfAbsent(hourKey, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * Update volume counters
     */
    private void updateVolumeCounters(PaymentEvent event) {
        String dateKey = LocalDate.now().toString();
        volumeCounters.merge(dateKey, event.getAmount(), BigDecimal::add);
        
        String hourKey = dateKey + "-" + Instant.now().atZone(ZoneId.systemDefault()).getHour();
        volumeCounters.merge(hourKey, event.getAmount(), BigDecimal::add);
    }

    /**
     * Store recent transaction for pattern analysis
     */
    private void storeRecentTransaction(PaymentEvent event) {
        TransactionEvent transactionEvent = TransactionEvent.builder()
                .transactionId(event.getTransactionId())
                .customerId(event.getCustomerId())
                .merchantId(event.getMerchantId())
                .amount(event.getAmount())
                .currency(event.getCurrency())
                .timestamp(event.getTimestamp())
                .build();
        
        String key = "recent-" + LocalDate.now().toString();
        recentTransactions.computeIfAbsent(key, k -> new ArrayList<>()).add(transactionEvent);
        
        // Keep only last 1000 transactions per day
        List<TransactionEvent> transactions = recentTransactions.get(key);
        if (transactions.size() > 1000) {
            transactions.subList(0, transactions.size() - 1000).clear();
        }
    }

    /**
     * Get transaction count for period
     */
    private long getTransactionCount(String period) {
        return transactionCounters.entrySet().stream()
                .filter(entry -> entry.getKey().contains(period))
                .mapToLong(entry -> entry.getValue().get())
                .sum();
    }

    /**
     * Get total volume for period
     */
    private BigDecimal getTotalVolume(String period) {
        return volumeCounters.entrySet().stream()
                .filter(entry -> entry.getKey().contains(period))
                .map(Map.Entry::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Get average amount for period
     */
    private BigDecimal getAverageAmount(String period) {
        long count = getTransactionCount(period);
        BigDecimal volume = getTotalVolume(period);
        
        if (count == 0) {
            return BigDecimal.ZERO;
        }
        
        return volume.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    /**
     * Get trending patterns
     */
    public List<TrendingPattern> getTrendingPatterns() {
        return transactionProcessor.analyzeTrendingPatterns();
    }

    /**
     * Get anomaly detection results
     */
    public List<AnomalyDetectionResult> getAnomalies() {
        return fraudProcessor.detectAnomalies();
    }

    /**
     * Clear old data
     */
    public void clearOldData(int daysToKeep) {
        LocalDate cutoffDate = LocalDate.now().minusDays(daysToKeep);
        
        transactionCounters.entrySet().removeIf(entry -> {
            try {
                String dateStr = entry.getKey().split("-")[0];
                LocalDate entryDate = LocalDate.parse(dateStr);
                return entryDate.isBefore(cutoffDate);
            } catch (Exception e) {
                return false;
            }
        });
        
        volumeCounters.entrySet().removeIf(entry -> {
            try {
                String dateStr = entry.getKey().split("-")[0];
                LocalDate entryDate = LocalDate.parse(dateStr);
                return entryDate.isBefore(cutoffDate);
            } catch (Exception e) {
                return false;
            }
        });
        
        recentTransactions.entrySet().removeIf(entry -> {
            try {
                String dateStr = entry.getKey().replace("recent-", "");
                LocalDate entryDate = LocalDate.parse(dateStr);
                return entryDate.isBefore(cutoffDate);
            } catch (Exception e) {
                return false;
            }
        });
    }
}