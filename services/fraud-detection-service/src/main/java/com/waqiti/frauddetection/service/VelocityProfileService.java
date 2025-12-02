package com.waqiti.frauddetection.service;

import com.waqiti.common.math.MoneyMath;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Velocity Profile Service
 *
 * Real-time transaction velocity monitoring using Redis sorted sets
 * for high-performance sliding window calculations. Detects rapid
 * transaction patterns, spending velocity anomalies, and automated fraud.
 *
 * PRODUCTION-GRADE IMPLEMENTATION
 * - Redis sorted sets for O(log N) performance
 * - Sliding window velocity checks
 * - Multiple time windows (1min, 5min, 1hr, 24hr)
 * - Transaction count and amount velocity
 * - Automatic expiry and cleanup
 * - Thread-safe operations
 *
 * KEY FEATURES:
 * - Transaction frequency monitoring
 * - Spending velocity tracking
 * - Burst detection (many transactions in short time)
 * - Automated bot detection
 * - Configurable thresholds per risk level
 *
 * @author Waqiti Fraud Detection Team
 * @version 1.0 - Production Implementation
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VelocityProfileService {

    private final RedisTemplate<String, Object> redisTemplate;

    // Redis key prefixes
    private static final String TX_COUNT_PREFIX = "velocity:tx:count:";
    private static final String TX_AMOUNT_PREFIX = "velocity:tx:amount:";
    private static final String FAILED_TX_PREFIX = "velocity:tx:failed:";

    // Velocity thresholds (configurable via application properties)
    private static final int MAX_TX_PER_MINUTE = 5;
    private static final int MAX_TX_PER_5_MINUTES = 15;
    private static final int MAX_TX_PER_HOUR = 50;
    private static final int MAX_TX_PER_DAY = 200;

    // Amount velocity thresholds
    private static final BigDecimal MAX_AMOUNT_PER_MINUTE = new BigDecimal("10000");
    private static final BigDecimal MAX_AMOUNT_PER_HOUR = new BigDecimal("50000");
    private static final BigDecimal MAX_AMOUNT_PER_DAY = new BigDecimal("100000");

    /**
     * Check transaction count velocity across multiple time windows
     */
    public VelocityCheckResult checkTransactionVelocity(UUID userId, TimeWindow window) {
        String key = TX_COUNT_PREFIX + userId;
        long now = System.currentTimeMillis();
        long windowStart = now - window.getMillis();

        try {
            // Add current transaction timestamp to sorted set
            redisTemplate.opsForZSet().add(key, UUID.randomUUID().toString(), now);

            // Remove entries outside the sliding window
            redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);

            // Count transactions in window
            Long count = redisTemplate.opsForZSet().count(key, windowStart, now);

            // Set expiry (window duration + 1 day buffer)
            redisTemplate.expire(key, window.getMillis() + 86400000, TimeUnit.MILLISECONDS);

            // Check against threshold
            int threshold = window.getTransactionLimit();
            boolean exceeded = count != null && count > threshold;

            double riskScore = calculateVelocityRisk(count != null ? count.intValue() : 0, threshold);

            VelocityCheckResult result = VelocityCheckResult.builder()
                .userId(userId)
                .window(window)
                .transactionCount(count != null ? count.intValue() : 0)
                .threshold(threshold)
                .limitExceeded(exceeded)
                .riskScore(riskScore)
                .build();

            if (exceeded) {
                log.warn("Transaction velocity limit exceeded for user {}: {} tx in {} (limit: {})",
                    userId, count, window.getDescription(), threshold);
            }

            return result;

        } catch (Exception e) {
            log.error("Error checking transaction velocity for user: {}", userId, e);
            // Fail secure - return high risk on error
            return VelocityCheckResult.builder()
                .userId(userId)
                .window(window)
                .transactionCount(0)
                .threshold(0)
                .limitExceeded(true)
                .riskScore(0.9)
                .errorOccurred(true)
                .build();
        }
    }

    /**
     * Check spending velocity (transaction amounts)
     */
    public VelocityCheckResult checkSpendingVelocity(
            UUID userId,
            BigDecimal transactionAmount,
            TimeWindow window) {

        String key = TX_AMOUNT_PREFIX + userId;
        long now = System.currentTimeMillis();
        long windowStart = now - window.getMillis();

        try {
            // Add current transaction amount with timestamp as score
            String txKey = UUID.randomUUID().toString();
            redisTemplate.opsForZSet().add(key, txKey, now);

            // Store amount separately (using hash for amount lookup)
            String amountKey = TX_AMOUNT_PREFIX + "data:" + userId;
            redisTemplate.opsForHash().put(amountKey, txKey, transactionAmount.toString());

            // Remove old entries
            Set<Object> oldTxKeys = redisTemplate.opsForZSet().rangeByScore(key, 0, windowStart);
            if (oldTxKeys != null && !oldTxKeys.isEmpty()) {
                redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);
                // Clean up amount data
                for (Object oldKey : oldTxKeys) {
                    redisTemplate.opsForHash().delete(amountKey, oldKey);
                }
            }

            // Calculate total amount in window
            Set<Object> txKeysInWindow = redisTemplate.opsForZSet().rangeByScore(key, windowStart, now);
            BigDecimal totalAmount = BigDecimal.ZERO;

            if (txKeysInWindow != null) {
                for (Object txKeyObj : txKeysInWindow) {
                    Object amountObj = redisTemplate.opsForHash().get(amountKey, txKeyObj);
                    if (amountObj != null) {
                        totalAmount = totalAmount.add(new BigDecimal(amountObj.toString()));
                    }
                }
            }

            // Set expiry
            redisTemplate.expire(key, window.getMillis() + 86400000, TimeUnit.MILLISECONDS);
            redisTemplate.expire(amountKey, window.getMillis() + 86400000, TimeUnit.MILLISECONDS);

            // Check against amount threshold
            BigDecimal amountThreshold = window.getAmountLimit();
            boolean exceeded = totalAmount.compareTo(amountThreshold) > 0;

            double riskScore = calculateAmountVelocityRisk(totalAmount, amountThreshold);

            VelocityCheckResult result = VelocityCheckResult.builder()
                .userId(userId)
                .window(window)
                .totalAmount(totalAmount)
                .amountThreshold(amountThreshold)
                .limitExceeded(exceeded)
                .riskScore(riskScore)
                .build();

            if (exceeded) {
                log.warn("Spending velocity limit exceeded for user {}: {} in {} (limit: {})",
                    userId, totalAmount, window.getDescription(), amountThreshold);
            }

            return result;

        } catch (Exception e) {
            log.error("Error checking spending velocity for user: {}", userId, e);
            return VelocityCheckResult.builder()
                .userId(userId)
                .window(window)
                .totalAmount(BigDecimal.ZERO)
                .amountThreshold(BigDecimal.ZERO)
                .limitExceeded(true)
                .riskScore(0.9)
                .errorOccurred(true)
                .build();
        }
    }

    /**
     * Track failed transaction attempts (fraud indicator)
     */
    public void recordFailedTransaction(UUID userId, String failureReason) {
        String key = FAILED_TX_PREFIX + userId;
        long now = System.currentTimeMillis();

        try {
            // Add failed transaction with reason
            String txId = UUID.randomUUID().toString() + ":" + failureReason;
            redisTemplate.opsForZSet().add(key, txId, now);

            // Keep only last 24 hours of failures
            long oneDayAgo = now - 86400000;
            redisTemplate.opsForZSet().removeRangeByScore(key, 0, oneDayAgo);

            // Set expiry (25 hours)
            redisTemplate.expire(key, 25, TimeUnit.HOURS);

            // Count failures in last hour
            long oneHourAgo = now - 3600000;
            Long failureCount = redisTemplate.opsForZSet().count(key, oneHourAgo, now);

            // Alert on excessive failures (>10 in 1 hour)
            if (failureCount != null && failureCount > 10) {
                log.error("EXCESSIVE FAILED TRANSACTIONS: User {} has {} failures in last hour",
                    userId, failureCount);
            }

        } catch (Exception e) {
            log.error("Error recording failed transaction for user: {}", userId, e);
        }
    }

    /**
     * Get failed transaction count in time window
     */
    public int getFailedTransactionCount(UUID userId, TimeWindow window) {
        String key = FAILED_TX_PREFIX + userId;
        long now = System.currentTimeMillis();
        long windowStart = now - window.getMillis();

        try {
            Long count = redisTemplate.opsForZSet().count(key, windowStart, now);
            return count != null ? count.intValue() : 0;
        } catch (Exception e) {
            log.error("Error getting failed transaction count for user: {}", userId, e);
            return 0;
        }
    }

    /**
     * Comprehensive velocity check across all time windows
     */
    public ComprehensiveVelocityResult checkAllVelocities(UUID userId, BigDecimal transactionAmount) {
        log.debug("Running comprehensive velocity check for user: {}", userId);

        // Check all time windows
        VelocityCheckResult oneMinute = checkTransactionVelocity(userId, TimeWindow.ONE_MINUTE);
        VelocityCheckResult fiveMinutes = checkTransactionVelocity(userId, TimeWindow.FIVE_MINUTES);
        VelocityCheckResult oneHour = checkTransactionVelocity(userId, TimeWindow.ONE_HOUR);
        VelocityCheckResult oneDay = checkTransactionVelocity(userId, TimeWindow.ONE_DAY);

        // Check spending velocity
        VelocityCheckResult spendingHour = checkSpendingVelocity(userId, transactionAmount, TimeWindow.ONE_HOUR);
        VelocityCheckResult spendingDay = checkSpendingVelocity(userId, transactionAmount, TimeWindow.ONE_DAY);

        // Get failed transaction count
        int failedCount = getFailedTransactionCount(userId, TimeWindow.ONE_HOUR);

        // Determine overall risk
        boolean anyLimitExceeded = oneMinute.isLimitExceeded() ||
                                   fiveMinutes.isLimitExceeded() ||
                                   oneHour.isLimitExceeded() ||
                                   oneDay.isLimitExceeded() ||
                                   spendingHour.isLimitExceeded() ||
                                   spendingDay.isLimitExceeded();

        // Calculate composite risk score (weighted average)
        double compositeRisk = (
            oneMinute.getRiskScore() * 0.3 +     // Recent activity weighs most
            fiveMinutes.getRiskScore() * 0.25 +
            oneHour.getRiskScore() * 0.2 +
            oneDay.getRiskScore() * 0.15 +
            spendingHour.getRiskScore() * 0.05 +
            spendingDay.getRiskScore() * 0.05
        );

        // Factor in failed transactions
        if (failedCount > 5) {
            compositeRisk = Math.min(0.95, compositeRisk + 0.2);
        }

        String riskLevel = determineVelocityRiskLevel(compositeRisk, anyLimitExceeded);

        return ComprehensiveVelocityResult.builder()
            .userId(userId)
            .oneMinuteCheck(oneMinute)
            .fiveMinutesCheck(fiveMinutes)
            .oneHourCheck(oneHour)
            .oneDayCheck(oneDay)
            .spendingHourCheck(spendingHour)
            .spendingDayCheck(spendingDay)
            .failedTransactionCount(failedCount)
            .anyLimitExceeded(anyLimitExceeded)
            .compositeRiskScore(compositeRisk)
            .riskLevel(riskLevel)
            .build();
    }

    /**
     * Calculate velocity risk score (0.0 - 1.0)
     */
    private double calculateVelocityRisk(int count, int threshold) {
        if (threshold == 0) return 1.0;

        double ratio = (double) count / threshold;

        if (ratio <= 0.5) return 0.0;        // Well below threshold
        if (ratio <= 0.8) return 0.3;        // Approaching threshold
        if (ratio <= 1.0) return 0.6;        // Near threshold
        if (ratio <= 1.5) return 0.8;        // Exceeded threshold
        return 0.95;                          // Significantly exceeded
    }

    /**
     * Calculate amount velocity risk score
     */
    private double calculateAmountVelocityRisk(BigDecimal amount, BigDecimal threshold) {
        if (threshold.compareTo(BigDecimal.ZERO) == 0) return 1.0;

        double ratio = (double) MoneyMath.toMLFeature(
            amount.divide(threshold, 4, RoundingMode.HALF_UP)
        );

        if (ratio <= 0.5) return 0.0;
        if (ratio <= 0.8) return 0.3;
        if (ratio <= 1.0) return 0.6;
        if (ratio <= 1.5) return 0.8;
        return 0.95;
    }

    /**
     * Determine velocity risk level
     */
    private String determineVelocityRiskLevel(double riskScore, boolean limitExceeded) {
        if (limitExceeded && riskScore > 0.8) return "CRITICAL";
        if (riskScore > 0.75) return "HIGH";
        if (riskScore > 0.5) return "MEDIUM";
        return "LOW";
    }

    /**
     * Time window enum with thresholds
     */
    public enum TimeWindow {
        ONE_MINUTE(60_000L, "1 minute", MAX_TX_PER_MINUTE, MAX_AMOUNT_PER_MINUTE),
        FIVE_MINUTES(300_000L, "5 minutes", MAX_TX_PER_5_MINUTES, MAX_AMOUNT_PER_MINUTE.multiply(new BigDecimal("5"))),
        ONE_HOUR(3_600_000L, "1 hour", MAX_TX_PER_HOUR, MAX_AMOUNT_PER_HOUR),
        ONE_DAY(86_400_000L, "24 hours", MAX_TX_PER_DAY, MAX_AMOUNT_PER_DAY);

        private final long millis;
        private final String description;
        private final int transactionLimit;
        private final BigDecimal amountLimit;

        TimeWindow(long millis, String description, int transactionLimit, BigDecimal amountLimit) {
            this.millis = millis;
            this.description = description;
            this.transactionLimit = transactionLimit;
            this.amountLimit = amountLimit;
        }

        public long getMillis() { return millis; }
        public String getDescription() { return description; }
        public int getTransactionLimit() { return transactionLimit; }
        public BigDecimal getAmountLimit() { return amountLimit; }
    }

    /**
     * DTO for velocity check result
     */
    @Data
    @Builder
    public static class VelocityCheckResult {
        private UUID userId;
        private TimeWindow window;
        private int transactionCount;
        private int threshold;
        private BigDecimal totalAmount;
        private BigDecimal amountThreshold;
        private boolean limitExceeded;
        private double riskScore;
        private boolean errorOccurred;
    }

    /**
     * DTO for comprehensive velocity result
     */
    @Data
    @Builder
    public static class ComprehensiveVelocityResult {
        private UUID userId;
        private VelocityCheckResult oneMinuteCheck;
        private VelocityCheckResult fiveMinutesCheck;
        private VelocityCheckResult oneHourCheck;
        private VelocityCheckResult oneDayCheck;
        private VelocityCheckResult spendingHourCheck;
        private VelocityCheckResult spendingDayCheck;
        private int failedTransactionCount;
        private boolean anyLimitExceeded;
        private double compositeRiskScore;
        private String riskLevel;
    }
}
