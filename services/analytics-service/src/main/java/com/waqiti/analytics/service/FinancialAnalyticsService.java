package com.waqiti.analytics.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Financial Analytics Service - Provides comprehensive financial analytics and insights
 * 
 * Handles real-time financial analytics for:
 * - Transaction volume and value analytics
 * - Revenue recognition and tracking
 * - Financial KPI calculation and monitoring
 * - Account balance tracking and analysis
 * - Anomaly detection and pattern recognition
 * - Real-time dashboard metrics and updates
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialAnalyticsService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${analytics.enabled:true}")
    private boolean analyticsEnabled;

    @Value("${analytics.anomaly.threshold.amount:100000}")
    private BigDecimal anomalyThresholdAmount;

    @Value("${analytics.anomaly.threshold.frequency:10}")
    private int anomalyThresholdFrequency;

    // Cache for tracking account activity patterns
    private final Map<String, AccountActivityPattern> accountActivityPatterns = new ConcurrentHashMap<>();

    /**
     * Processes ledger event for financial analytics
     */
    public void processLedgerEvent(
            String eventId,
            String eventType,
            String journalEntryId,
            String accountNumber,
            BigDecimal debitAmount,
            BigDecimal creditAmount,
            String currency,
            String description,
            Map<String, String> metadata,
            LocalDateTime timestamp) {

        if (!analyticsEnabled) {
            log.debug("Analytics disabled, skipping ledger event processing");
            return;
        }

        try {
            log.debug("Processing ledger event for analytics: {} - Type: {}", eventId, eventType);

            // Update transaction volume metrics
            updateTransactionVolumeMetrics(eventType, currency, timestamp);

            // Update transaction value metrics
            updateTransactionValueMetrics(debitAmount, creditAmount, currency, timestamp);

            // Update account-specific metrics
            if (accountNumber != null) {
                updateAccountMetrics(accountNumber, debitAmount, creditAmount, currency, timestamp);
            }

            // Update event type specific analytics
            updateEventTypeAnalytics(eventType, debitAmount, creditAmount, currency, metadata, timestamp);

            log.debug("Successfully processed ledger event for analytics: {}", eventId);

        } catch (Exception e) {
            log.error("Failed to process ledger event for analytics: {}", eventId, e);
        }
    }

    /**
     * Updates financial KPIs based on ledger events
     */
    public void updateFinancialKPIs(
            String eventType,
            BigDecimal debitAmount,
            BigDecimal creditAmount,
            String currency) {

        try {
            log.debug("Updating financial KPIs for event type: {}", eventType);

            // Calculate net transaction value
            BigDecimal netValue = (creditAmount != null ? creditAmount : BigDecimal.ZERO)
                .subtract(debitAmount != null ? debitAmount : BigDecimal.ZERO);

            // Update revenue KPIs
            if (eventType.contains("REVENUE") || eventType.contains("INCOME")) {
                updateRevenueKPIs(netValue, currency);
            }

            // Update expense KPIs
            if (eventType.contains("EXPENSE") || eventType.contains("COST")) {
                updateExpenseKPIs(netValue.abs(), currency);
            }

            // Update profitability KPIs
            updateProfitabilityKPIs(eventType, netValue, currency);

            // Update liquidity KPIs
            updateLiquidityKPIs(eventType, debitAmount, creditAmount, currency);

            log.debug("Financial KPIs updated for event type: {}", eventType);

        } catch (Exception e) {
            log.error("Failed to update financial KPIs", e);
        }
    }

    /**
     * Checks for unusual account activity patterns
     */
    public boolean checkUnusualAccountActivity(
            String accountNumber,
            BigDecimal amount,
            String eventType) {

        try {
            // Get or create account activity pattern
            AccountActivityPattern pattern = accountActivityPatterns.computeIfAbsent(
                accountNumber, k -> new AccountActivityPattern(k));

            // Update pattern with new activity
            pattern.recordActivity(amount, eventType, LocalDateTime.now());

            // Check for anomalies
            boolean isUnusual = false;

            // Check for sudden spike in amount
            if (amount != null && pattern.getAverageAmount() != null) {
                BigDecimal threshold = pattern.getAverageAmount().multiply(new BigDecimal("3"));
                if (amount.compareTo(threshold) > 0) {
                    isUnusual = true;
                    log.warn("Unusual amount detected for account: {} - Amount: {} (Avg: {})",
                        accountNumber, amount, pattern.getAverageAmount());
                }
            }

            // Check for unusual frequency
            if (pattern.getRecentActivityCount() > anomalyThresholdFrequency) {
                isUnusual = true;
                log.warn("Unusual activity frequency for account: {} - Count: {}",
                    accountNumber, pattern.getRecentActivityCount());
            }

            return isUnusual;

        } catch (Exception e) {
            log.error("Failed to check unusual account activity", e);
            return false;
        }
    }

    /**
     * Reports detected anomaly
     */
    public void reportAnomaly(
            String eventId,
            String eventType,
            String anomalyReason,
            LocalDateTime timestamp) {

        try {
            log.warn("Reporting anomaly - Event: {} Type: {} Reason: {}", 
                eventId, eventType, anomalyReason);

            // Store anomaly in Redis for monitoring
            String anomalyKey = "analytics:anomaly:" + eventId;
            Map<String, String> anomalyData = Map.of(
                "event_id", eventId,
                "event_type", eventType,
                "reason", anomalyReason,
                "timestamp", timestamp.toString(),
                "reported_at", LocalDateTime.now().toString()
            );

            redisTemplate.opsForHash().putAll(anomalyKey, anomalyData);
            redisTemplate.expire(anomalyKey, Duration.ofDays(30));

            // Update anomaly counter
            String counterKey = "analytics:anomaly:count:" + LocalDateTime.now().toLocalDate();
            redisTemplate.opsForValue().increment(counterKey);
            redisTemplate.expire(counterKey, Duration.ofDays(7));

        } catch (Exception e) {
            log.error("Failed to report anomaly", e);
        }
    }

    /**
     * Updates dashboard metrics with ledger event data
     */
    public void updateDashboardMetrics(
            String eventType,
            BigDecimal debitAmount,
            BigDecimal creditAmount,
            String currency,
            LocalDateTime timestamp) {

        try {
            log.debug("Updating dashboard metrics for event type: {}", eventType);

            // Update real-time transaction count
            String countKey = "dashboard:transactions:count:" + timestamp.toLocalDate();
            redisTemplate.opsForValue().increment(countKey);
            redisTemplate.expire(countKey, Duration.ofDays(1));

            // Update real-time transaction volume
            BigDecimal volume = (debitAmount != null ? debitAmount : BigDecimal.ZERO)
                .add(creditAmount != null ? creditAmount : BigDecimal.ZERO);
            
            if (volume.compareTo(BigDecimal.ZERO) > 0) {
                String volumeKey = "dashboard:transactions:volume:" + currency + ":" + timestamp.toLocalDate();
                redisTemplate.opsForValue().increment(volumeKey, volume.doubleValue());
                redisTemplate.expire(volumeKey, Duration.ofDays(1));
            }

            // Update event type distribution
            String eventTypeKey = "dashboard:events:type:" + eventType + ":" + timestamp.toLocalDate();
            redisTemplate.opsForValue().increment(eventTypeKey);
            redisTemplate.expire(eventTypeKey, Duration.ofDays(1));

            log.debug("Dashboard metrics updated for event type: {}", eventType);

        } catch (Exception e) {
            log.error("Failed to update dashboard metrics", e);
        }
    }

    /**
     * Updates real-time account balance
     */
    public void updateRealTimeAccountBalance(
            String accountNumber,
            BigDecimal debitAmount,
            BigDecimal creditAmount) {

        try {
            log.debug("Updating real-time balance for account: {}", accountNumber);

            // Calculate net change
            BigDecimal netChange = (creditAmount != null ? creditAmount : BigDecimal.ZERO)
                .subtract(debitAmount != null ? debitAmount : BigDecimal.ZERO);

            // Update account balance in Redis
            String balanceKey = "analytics:account:balance:" + accountNumber;
            Double currentBalance = (Double) redisTemplate.opsForValue().get(balanceKey);
            
            BigDecimal newBalance = (currentBalance != null ? 
                BigDecimal.valueOf(currentBalance) : BigDecimal.ZERO).add(netChange);
            
            redisTemplate.opsForValue().set(balanceKey, newBalance.doubleValue());
            redisTemplate.expire(balanceKey, Duration.ofDays(7));

            // Update balance change history
            String historyKey = "analytics:account:balance:history:" + accountNumber + ":" + 
                LocalDateTime.now().toLocalDate();
            redisTemplate.opsForList().rightPush(historyKey, netChange.toString());
            redisTemplate.expire(historyKey, Duration.ofDays(30));

            log.debug("Real-time balance updated for account: {} - New balance: {}", 
                accountNumber, newBalance);

        } catch (Exception e) {
            log.error("Failed to update real-time account balance", e);
        }
    }

    // Private helper methods

    private void updateTransactionVolumeMetrics(String eventType, String currency, LocalDateTime timestamp) {
        try {
            String volumeKey = "analytics:volume:" + eventType + ":" + currency + ":" + 
                timestamp.toLocalDate();
            redisTemplate.opsForValue().increment(volumeKey);
            redisTemplate.expire(volumeKey, Duration.ofDays(90));
        } catch (Exception e) {
            log.error("Failed to update transaction volume metrics", e);
        }
    }

    private void updateTransactionValueMetrics(BigDecimal debitAmount, BigDecimal creditAmount, 
            String currency, LocalDateTime timestamp) {
        try {
            BigDecimal totalValue = (debitAmount != null ? debitAmount : BigDecimal.ZERO)
                .add(creditAmount != null ? creditAmount : BigDecimal.ZERO);
            
            if (totalValue.compareTo(BigDecimal.ZERO) > 0) {
                String valueKey = "analytics:value:" + currency + ":" + timestamp.toLocalDate();
                redisTemplate.opsForValue().increment(valueKey, totalValue.doubleValue());
                redisTemplate.expire(valueKey, Duration.ofDays(90));
            }
        } catch (Exception e) {
            log.error("Failed to update transaction value metrics", e);
        }
    }

    private void updateAccountMetrics(String accountNumber, BigDecimal debitAmount, 
            BigDecimal creditAmount, String currency, LocalDateTime timestamp) {
        try {
            // Update account transaction count
            String countKey = "analytics:account:count:" + accountNumber + ":" + timestamp.toLocalDate();
            redisTemplate.opsForValue().increment(countKey);
            redisTemplate.expire(countKey, Duration.ofDays(30));

            // Update account transaction value
            BigDecimal value = (debitAmount != null ? debitAmount : BigDecimal.ZERO)
                .add(creditAmount != null ? creditAmount : BigDecimal.ZERO);
            
            if (value.compareTo(BigDecimal.ZERO) > 0) {
                String valueKey = "analytics:account:value:" + accountNumber + ":" + currency;
                redisTemplate.opsForValue().increment(valueKey, value.doubleValue());
                redisTemplate.expire(valueKey, Duration.ofDays(30));
            }
        } catch (Exception e) {
            log.error("Failed to update account metrics", e);
        }
    }

    private void updateEventTypeAnalytics(String eventType, BigDecimal debitAmount, 
            BigDecimal creditAmount, String currency, Map<String, String> metadata, 
            LocalDateTime timestamp) {
        try {
            // Store event type specific analytics
            String analyticsKey = "analytics:event:" + eventType + ":" + timestamp.toLocalDate();
            
            Map<String, String> analyticsData = new ConcurrentHashMap<>();
            analyticsData.put("count", "1");
            analyticsData.put("debit_total", debitAmount != null ? debitAmount.toString() : "0");
            analyticsData.put("credit_total", creditAmount != null ? creditAmount.toString() : "0");
            analyticsData.put("currency", currency);
            analyticsData.put("last_updated", LocalDateTime.now().toString());
            
            redisTemplate.opsForHash().putAll(analyticsKey, analyticsData);
            redisTemplate.expire(analyticsKey, Duration.ofDays(30));
        } catch (Exception e) {
            log.error("Failed to update event type analytics", e);
        }
    }

    private void updateRevenueKPIs(BigDecimal revenueAmount, String currency) {
        try {
            String revenueKey = "kpi:revenue:" + currency + ":" + LocalDateTime.now().toLocalDate();
            redisTemplate.opsForValue().increment(revenueKey, revenueAmount.doubleValue());
            redisTemplate.expire(revenueKey, Duration.ofDays(365));
        } catch (Exception e) {
            log.error("Failed to update revenue KPIs", e);
        }
    }

    private void updateExpenseKPIs(BigDecimal expenseAmount, String currency) {
        try {
            String expenseKey = "kpi:expense:" + currency + ":" + LocalDateTime.now().toLocalDate();
            redisTemplate.opsForValue().increment(expenseKey, expenseAmount.doubleValue());
            redisTemplate.expire(expenseKey, Duration.ofDays(365));
        } catch (Exception e) {
            log.error("Failed to update expense KPIs", e);
        }
    }

    private void updateProfitabilityKPIs(String eventType, BigDecimal netValue, String currency) {
        try {
            String profitKey = "kpi:profitability:" + currency + ":" + LocalDateTime.now().toLocalDate();
            
            if (eventType.contains("REVENUE") || eventType.contains("INCOME")) {
                redisTemplate.opsForValue().increment(profitKey, netValue.doubleValue());
            } else if (eventType.contains("EXPENSE") || eventType.contains("COST")) {
                redisTemplate.opsForValue().increment(profitKey, -netValue.doubleValue());
            }
            
            redisTemplate.expire(profitKey, Duration.ofDays(365));
        } catch (Exception e) {
            log.error("Failed to update profitability KPIs", e);
        }
    }

    private void updateLiquidityKPIs(String eventType, BigDecimal debitAmount, 
            BigDecimal creditAmount, String currency) {
        try {
            if (eventType.contains("CASH") || eventType.contains("LIQUIDITY")) {
                String liquidityKey = "kpi:liquidity:" + currency + ":" + LocalDateTime.now().toLocalDate();
                BigDecimal netLiquidity = (creditAmount != null ? creditAmount : BigDecimal.ZERO)
                    .subtract(debitAmount != null ? debitAmount : BigDecimal.ZERO);
                redisTemplate.opsForValue().increment(liquidityKey, netLiquidity.doubleValue());
                redisTemplate.expire(liquidityKey, Duration.ofDays(90));
            }
        } catch (Exception e) {
            log.error("Failed to update liquidity KPIs", e);
        }
    }

    /**
     * Account activity pattern tracking
     */
    private static class AccountActivityPattern {
        private final String accountNumber;
        private BigDecimal totalAmount = BigDecimal.ZERO;
        private int activityCount = 0;
        private LocalDateTime lastActivity;
        private int recentActivityCount = 0;
        private LocalDateTime recentActivityWindow;

        public AccountActivityPattern(String accountNumber) {
            this.accountNumber = accountNumber;
            this.recentActivityWindow = LocalDateTime.now();
        }

        public void recordActivity(BigDecimal amount, String eventType, LocalDateTime timestamp) {
            if (amount != null) {
                totalAmount = totalAmount.add(amount);
            }
            activityCount++;
            lastActivity = timestamp;

            // Track recent activity (within last hour)
            if (timestamp.isAfter(LocalDateTime.now().minusHours(1))) {
                recentActivityCount++;
            } else {
                // Reset if outside window
                recentActivityCount = 1;
                recentActivityWindow = timestamp;
            }
        }

        public BigDecimal getAverageAmount() {
            if (activityCount == 0) return BigDecimal.ZERO;
            return totalAmount.divide(BigDecimal.valueOf(activityCount), 2, RoundingMode.HALF_UP);
        }

        public int getRecentActivityCount() {
            return recentActivityCount;
        }
    }
}