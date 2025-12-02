package com.waqiti.analytics.processor;

import com.waqiti.analytics.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Processor for user analytics
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserAnalyticsProcessor {

    private final Map<String, UserAnalytics> userAnalyticsCache = new ConcurrentHashMap<>();
    private final Map<String, List<PaymentEvent>> userTransactionHistory = new ConcurrentHashMap<>();
    private final Map<String, List<UserBehaviorEvent>> userBehaviorHistory = new ConcurrentHashMap<>();

    /**
     * Process user transaction event
     */
    public void processUserTransaction(PaymentEvent event) {
        String userId = event.getCustomerId();
        
        // Store transaction history
        userTransactionHistory.computeIfAbsent(userId, k -> new ArrayList<>()).add(event);
        
        // Update user analytics
        updateUserAnalytics(userId, event);
    }

    /**
     * Process user behavior event
     */
    public void processUserBehavior(UserBehaviorEvent event) {
        String userId = event.getUserId();
        
        // Store behavior history
        userBehaviorHistory.computeIfAbsent(userId, k -> new ArrayList<>()).add(event);
        
        // Update user analytics
        updateUserBehaviorAnalytics(userId, event);
    }

    /**
     * Update user analytics from transaction
     */
    private void updateUserAnalytics(String userId, PaymentEvent event) {
        UserAnalytics analytics = userAnalyticsCache.computeIfAbsent(userId, 
                k -> createNewUserAnalytics(k));
        
        // Update transaction metrics
        analytics.setTotalTransactions(analytics.getTotalTransactions() + 1);
        analytics.setTotalSpent(analytics.getTotalSpent().add(event.getAmount()));
        
        // Update average
        analytics.setAverageTransactionAmount(
                analytics.getTotalSpent().divide(
                        BigDecimal.valueOf(analytics.getTotalTransactions()), 
                        2, RoundingMode.HALF_UP));
        
        // Update max amount
        if (event.getAmount().compareTo(analytics.getMaxTransactionAmount()) > 0) {
            analytics.setMaxTransactionAmount(event.getAmount());
        }
        
        // Update first/last transaction
        if (analytics.getFirstTransactionDate() == null) {
            analytics.setFirstTransactionDate(event.getTimestamp());
        }
        analytics.setLastTransactionDate(event.getTimestamp());
        
        // Update preferred payment methods
        updatePreferredPaymentMethods(analytics, event.getPaymentMethod());
        
        // Update merchant preferences
        updateMerchantPreferences(analytics, event.getMerchantId());
        
        // Update last updated
        analytics.setLastUpdated(Instant.now());
    }

    /**
     * Update user behavior analytics
     */
    private void updateUserBehaviorAnalytics(String userId, UserBehaviorEvent event) {
        UserAnalytics analytics = userAnalyticsCache.computeIfAbsent(userId, 
                k -> createNewUserAnalytics(k));
        
        // Update session metrics
        analytics.setTotalSessions(analytics.getTotalSessions() + 1);
        analytics.setLastActiveDate(event.getTimestamp());
        
        // Update page views
        updatePageViews(analytics, event.getPage());
        
        // Update device usage
        updateDeviceUsage(analytics, event.getDeviceId());
        
        analytics.setLastUpdated(Instant.now());
    }

    /**
     * Create new user analytics
     */
    private UserAnalytics createNewUserAnalytics(String userId) {
        return UserAnalytics.builder()
                .userId(userId)
                .totalTransactions(0L)
                .totalSpent(BigDecimal.ZERO)
                .averageTransactionAmount(BigDecimal.ZERO)
                .maxTransactionAmount(BigDecimal.ZERO)
                .totalSessions(0L)
                .preferredPaymentMethods(new HashMap<>())
                .merchantPreferences(new HashMap<>())
                .pageViews(new HashMap<>())
                .deviceUsage(new HashMap<>())
                .createdAt(Instant.now())
                .lastUpdated(Instant.now())
                .build();
    }

    /**
     * Update preferred payment methods
     */
    private void updatePreferredPaymentMethods(UserAnalytics analytics, String paymentMethod) {
        Map<String, Long> methods = analytics.getPreferredPaymentMethods();
        methods.merge(paymentMethod, 1L, Long::sum);
    }

    /**
     * Update merchant preferences
     */
    private void updateMerchantPreferences(UserAnalytics analytics, String merchantId) {
        Map<String, Long> preferences = analytics.getMerchantPreferences();
        preferences.merge(merchantId, 1L, Long::sum);
    }

    /**
     * Update page views
     */
    private void updatePageViews(UserAnalytics analytics, String page) {
        if (page != null) {
            Map<String, Long> views = analytics.getPageViews();
            views.merge(page, 1L, Long::sum);
        }
    }

    /**
     * Update device usage
     */
    private void updateDeviceUsage(UserAnalytics analytics, String deviceId) {
        if (deviceId != null) {
            Map<String, Long> usage = analytics.getDeviceUsage();
            usage.merge(deviceId, 1L, Long::sum);
        }
    }

    /**
     * Get user analytics
     */
    public UserAnalytics getUserAnalytics(String userId) {
        UserAnalytics analytics = userAnalyticsCache.get(userId);
        if (analytics == null) {
            return createNewUserAnalytics(userId);
        }
        
        // Calculate additional metrics
        calculateAdditionalMetrics(analytics, userId);
        
        return analytics;
    }

    /**
     * Calculate additional metrics
     */
    private void calculateAdditionalMetrics(UserAnalytics analytics, String userId) {
        List<PaymentEvent> transactions = userTransactionHistory.get(userId);
        if (transactions != null && !transactions.isEmpty()) {
            // Calculate velocity
            calculateTransactionVelocity(analytics, transactions);
            
            // Calculate spending patterns
            calculateSpendingPatterns(analytics, transactions);
            
            // Calculate loyalty score
            calculateLoyaltyScore(analytics, transactions);
        }
    }

    /**
     * Calculate transaction velocity
     */
    private void calculateTransactionVelocity(UserAnalytics analytics, List<PaymentEvent> transactions) {
        if (transactions.size() < 2) {
            return;
        }
        
        // Calculate daily velocity
        Instant oneDayAgo = Instant.now().minus(1, ChronoUnit.DAYS);
        long dailyTransactions = transactions.stream()
                .filter(tx -> tx.getTimestamp().isAfter(oneDayAgo))
                .count();
        
        analytics.setDailyVelocity(dailyTransactions);
        
        // Calculate weekly velocity
        Instant oneWeekAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        long weeklyTransactions = transactions.stream()
                .filter(tx -> tx.getTimestamp().isAfter(oneWeekAgo))
                .count();
        
        analytics.setWeeklyVelocity(weeklyTransactions);
    }

    /**
     * Calculate spending patterns
     */
    private void calculateSpendingPatterns(UserAnalytics analytics, List<PaymentEvent> transactions) {
        // Group by hour of day
        Map<Integer, BigDecimal> hourlySpending = transactions.stream()
                .collect(Collectors.groupingBy(
                        tx -> tx.getTimestamp().atZone(java.time.ZoneOffset.UTC).getHour(),
                        Collectors.reducing(BigDecimal.ZERO, PaymentEvent::getAmount, BigDecimal::add)
                ));
        
        // Find peak spending hours
        Integer peakHour = hourlySpending.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(12); // Default to noon
        
        analytics.setPeakSpendingHour(peakHour);
    }

    /**
     * Calculate loyalty score
     */
    private void calculateLoyaltyScore(UserAnalytics analytics, List<PaymentEvent> transactions) {
        int score = 0;
        
        // Base score from transaction count
        score += Math.min(analytics.getTotalTransactions().intValue(), 50);
        
        // Bonus for regular activity
        if (analytics.getDailyVelocity() > 0) {
            score += 10;
        }
        
        // Bonus for high value transactions
        if (analytics.getAverageTransactionAmount().compareTo(BigDecimal.valueOf(100)) > 0) {
            score += 15;
        }
        
        // Bonus for merchant loyalty
        if (analytics.getMerchantPreferences().size() <= 3) {
            score += 10; // Loyal to fewer merchants
        }
        
        // Bonus for tenure
        if (analytics.getFirstTransactionDate() != null) {
            long daysActive = ChronoUnit.DAYS.between(
                    analytics.getFirstTransactionDate(), 
                    Instant.now());
            if (daysActive > 30) {
                score += 15;
            }
        }
        
        analytics.setLoyaltyScore(Math.min(score, 100));
    }

    /**
     * Get user segmentation
     */
    public String getUserSegment(String userId) {
        UserAnalytics analytics = getUserAnalytics(userId);
        
        // Simple segmentation logic
        if (analytics.getTotalSpent().compareTo(BigDecimal.valueOf(10000)) > 0) {
            return "VIP";
        } else if (analytics.getTotalSpent().compareTo(BigDecimal.valueOf(1000)) > 0) {
            return "HIGH_VALUE";
        } else if (analytics.getTotalTransactions() > 50) {
            return "FREQUENT";
        } else if (analytics.getTotalTransactions() > 10) {
            return "REGULAR";
        } else {
            return "NEW";
        }
    }

    /**
     * Clear old data
     */
    public void clearOldData(int daysToKeep) {
        Instant cutoffDate = Instant.now().minus(daysToKeep, ChronoUnit.DAYS);
        
        userTransactionHistory.values().forEach(transactions -> 
                transactions.removeIf(tx -> tx.getTimestamp().isBefore(cutoffDate)));
        
        userBehaviorHistory.values().forEach(events -> 
                events.removeIf(event -> event.getTimestamp().isBefore(cutoffDate)));
    }
}