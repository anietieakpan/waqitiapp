package com.waqiti.compliance.service;

import com.waqiti.common.audit.ComprehensiveAuditService;
import com.waqiti.common.security.audit.SecurityAuditLogger;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Transaction Velocity Limit Service
 *
 * CRITICAL FRAUD PREVENTION SERVICE
 *
 * Implements comprehensive transaction velocity controls:
 * - Transaction count limits (e.g., max 5 per minute)
 * - Daily/monthly spending limits
 * - High-value transaction blocks
 * - Suspicious pattern detection
 * - Real-time limit enforcement
 *
 * COMPLIANCE:
 * - Bank Secrecy Act (BSA) - Transaction monitoring
 * - USA PATRIOT Act - Enhanced due diligence
 * - FinCEN regulations - Suspicious activity detection
 * - PCI DSS Requirement 8.2.3 - Account lockout
 * - FFIEC guidelines - Transaction limits
 *
 * FRAUD PREVENTION:
 * - Prevents rapid-fire transactions (velocity attacks)
 * - Detects account takeover attempts
 * - Blocks unusual spending patterns
 * - Enforces regulatory transaction limits
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 * @since 2025-10-05
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionVelocityLimitService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ComprehensiveAuditService auditService;
    private final SecurityAuditLogger securityAuditLogger;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Redis key prefixes for different limit types
    private static final String VELOCITY_PREFIX = "velocity:";
    private static final String DAILY_LIMIT_PREFIX = "daily_limit:";
    private static final String MONTHLY_LIMIT_PREFIX = "monthly_limit:";
    private static final String HIGH_VALUE_PREFIX = "high_value:";

    // Kafka topics
    private static final String LIMIT_BREACH_TOPIC = "compliance.limit.breach";
    private static final String SUSPICIOUS_ACTIVITY_TOPIC = "compliance.suspicious.activity";

    // Velocity limits - defaults are for demonstration only
    // IMPORTANT: Configure via environment variables or external config for production
    private static final int DEFAULT_TRANSACTIONS_PER_MINUTE = Integer.parseInt(
        System.getProperty("velocity.limit.per.minute",
            System.getenv().getOrDefault("VELOCITY_LIMIT_PER_MINUTE", "5")));
    private static final int DEFAULT_TRANSACTIONS_PER_HOUR = Integer.parseInt(
        System.getProperty("velocity.limit.per.hour",
            System.getenv().getOrDefault("VELOCITY_LIMIT_PER_HOUR", "50")));
    private static final int DEFAULT_TRANSACTIONS_PER_DAY = Integer.parseInt(
        System.getProperty("velocity.limit.per.day",
            System.getenv().getOrDefault("VELOCITY_LIMIT_PER_DAY", "200")));
    private static final BigDecimal DEFAULT_DAILY_LIMIT = new BigDecimal(
        System.getProperty("velocity.daily.spending.limit",
            System.getenv().getOrDefault("VELOCITY_DAILY_SPENDING_LIMIT", "10000")));
    private static final BigDecimal DEFAULT_MONTHLY_LIMIT = new BigDecimal(
        System.getProperty("velocity.monthly.spending.limit",
            System.getenv().getOrDefault("VELOCITY_MONTHLY_SPENDING_LIMIT", "50000")));
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal(
        System.getProperty("velocity.high.value.threshold",
            System.getenv().getOrDefault("VELOCITY_HIGH_VALUE_THRESHOLD", "5000")));

    /**
     * Check if transaction is allowed based on all velocity limits
     *
     * @param userId User ID
     * @param amount Transaction amount
     * @param currency Currency code
     * @return VelocityCheckResult with decision and details
     */
    public VelocityCheckResult checkTransactionVelocity(String userId, BigDecimal amount, String currency) {

        log.debug("Checking velocity limits for user: {}, amount: {} {}", userId, amount, currency);

        try {
            // 1. Check transaction count velocity (per minute)
            VelocityCheckResult minuteCheck = checkTransactionCountVelocity(userId, Duration.ofMinutes(1), DEFAULT_TRANSACTIONS_PER_MINUTE);
            if (!minuteCheck.isAllowed()) {
                recordLimitBreach(userId, "VELOCITY_MINUTE", minuteCheck);
                return minuteCheck;
            }

            // 2. Check transaction count velocity (per hour)
            VelocityCheckResult hourCheck = checkTransactionCountVelocity(userId, Duration.ofHours(1), DEFAULT_TRANSACTIONS_PER_HOUR);
            if (!hourCheck.isAllowed()) {
                recordLimitBreach(userId, "VELOCITY_HOUR", hourCheck);
                return hourCheck;
            }

            // 3. Check transaction count velocity (per day)
            VelocityCheckResult dayCheck = checkTransactionCountVelocity(userId, Duration.ofDays(1), DEFAULT_TRANSACTIONS_PER_DAY);
            if (!dayCheck.isAllowed()) {
                recordLimitBreach(userId, "VELOCITY_DAY", dayCheck);
                return dayCheck;
            }

            // 4. Check daily spending limit
            VelocityCheckResult dailySpendingCheck = checkDailySpendingLimit(userId, amount, currency);
            if (!dailySpendingCheck.isAllowed()) {
                recordLimitBreach(userId, "DAILY_SPENDING", dailySpendingCheck);
                return dailySpendingCheck;
            }

            // 5. Check monthly spending limit
            VelocityCheckResult monthlySpendingCheck = checkMonthlySpendingLimit(userId, amount, currency);
            if (!monthlySpendingCheck.isAllowed()) {
                recordLimitBreach(userId, "MONTHLY_SPENDING", monthlySpendingCheck);
                return monthlySpendingCheck;
            }

            // 6. Check high-value transaction block
            VelocityCheckResult highValueCheck = checkHighValueTransaction(userId, amount, currency);
            if (!highValueCheck.isAllowed()) {
                recordLimitBreach(userId, "HIGH_VALUE", highValueCheck);
                return highValueCheck;
            }

            // 7. Detect suspicious patterns
            detectSuspiciousPatterns(userId, amount, currency);

            // All checks passed
            incrementMetrics(userId, "allowed");
            return VelocityCheckResult.allowed("All velocity checks passed");

        } catch (Exception e) {
            log.error("Failed to check velocity limits for user: {}", userId, e);
            // Fail-safe: Allow transaction but log error
            incrementMetrics(userId, "error");
            return VelocityCheckResult.allowed("Velocity check error - fail-safe mode");
        }
    }

    /**
     * Record successful transaction to update velocity counters
     */
    public void recordTransaction(String userId, BigDecimal amount, String currency, String transactionId) {

        try {
            String today = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            String month = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));

            // Increment transaction count
            String velocityKey = VELOCITY_PREFIX + userId;
            redisTemplate.opsForList().leftPush(velocityKey, System.currentTimeMillis());
            redisTemplate.expire(velocityKey, 1, TimeUnit.DAYS);

            // Increment daily spending
            String dailyKey = DAILY_LIMIT_PREFIX + userId + ":" + today;
            redisTemplate.opsForValue().increment(dailyKey, amount.doubleValue());
            redisTemplate.expire(dailyKey, 2, TimeUnit.DAYS);

            // Increment monthly spending
            String monthlyKey = MONTHLY_LIMIT_PREFIX + userId + ":" + month;
            redisTemplate.opsForValue().increment(monthlyKey, amount.doubleValue());
            redisTemplate.expire(monthlyKey, 35, TimeUnit.DAYS);

            log.debug("Recorded transaction for user: {}, amount: {} {}, txnId: {}",
                userId, amount, currency, transactionId);

            incrementMetrics(userId, "recorded");

        } catch (Exception e) {
            log.error("Failed to record transaction for user: {}", userId, e);
        }
    }

    /**
     * Check transaction count velocity within time window
     */
    private VelocityCheckResult checkTransactionCountVelocity(String userId, Duration window, int maxCount) {

        try {
            String velocityKey = VELOCITY_PREFIX + userId;
            List<Object> timestamps = redisTemplate.opsForList().range(velocityKey, 0, -1);

            if (timestamps == null || timestamps.isEmpty()) {
                return VelocityCheckResult.allowed("No transaction history");
            }

            // Count transactions within window
            long windowStartMs = System.currentTimeMillis() - window.toMillis();
            long transactionsInWindow = timestamps.stream()
                .filter(ts -> (Long) ts > windowStartMs)
                .count();

            if (transactionsInWindow >= maxCount) {
                String reason = String.format(
                    "Transaction velocity limit exceeded: %d transactions in configured time window (threshold configured externally)",
                    transactionsInWindow
                );

                return VelocityCheckResult.blocked(reason, Map.of(
                    "window", window.toString(),
                    "count", transactionsInWindow,
                    "limit", maxCount,
                    "remainingTime", calculateCooldownPeriod(timestamps, window)
                ));
            }

            return VelocityCheckResult.allowed("Transaction count within limits");

        } catch (Exception e) {
            log.error("Failed to check transaction count velocity for user: {}", userId, e);
            return VelocityCheckResult.allowed("Velocity check error - fail-safe");
        }
    }

    /**
     * Check daily spending limit
     */
    private VelocityCheckResult checkDailySpendingLimit(String userId, BigDecimal amount, String currency) {

        try {
            String today = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            String dailyKey = DAILY_LIMIT_PREFIX + userId + ":" + today;

            Double currentSpending = (Double) redisTemplate.opsForValue().get(dailyKey);
            BigDecimal totalSpending = currentSpending != null
                ? new BigDecimal(currentSpending).add(amount)
                : amount;

            if (totalSpending.compareTo(DEFAULT_DAILY_LIMIT) > 0) {
                String reason = String.format(
                    "Daily spending limit exceeded: %s %s (limit configured externally for regulatory compliance)",
                    totalSpending, currency
                );

                return VelocityCheckResult.blocked(reason, Map.of(
                    "currentSpending", currentSpending != null ? currentSpending : 0.0,
                    "transactionAmount", amount,
                    "totalSpending", totalSpending,
                    "dailyLimit", DEFAULT_DAILY_LIMIT,
                    "remaining", DEFAULT_DAILY_LIMIT.subtract(new BigDecimal(currentSpending != null ? currentSpending : 0.0))
                ));
            }

            return VelocityCheckResult.allowed("Daily spending within limits");

        } catch (Exception e) {
            log.error("Failed to check daily spending limit for user: {}", userId, e);
            return VelocityCheckResult.allowed("Daily limit check error - fail-safe");
        }
    }

    /**
     * Check monthly spending limit
     */
    private VelocityCheckResult checkMonthlySpendingLimit(String userId, BigDecimal amount, String currency) {

        try {
            String month = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            String monthlyKey = MONTHLY_LIMIT_PREFIX + userId + ":" + month;

            Double currentSpending = (Double) redisTemplate.opsForValue().get(monthlyKey);
            BigDecimal totalSpending = currentSpending != null
                ? new BigDecimal(currentSpending).add(amount)
                : amount;

            if (totalSpending.compareTo(DEFAULT_MONTHLY_LIMIT) > 0) {
                String reason = String.format(
                    "Monthly spending limit exceeded: %s %s (limit configured externally for regulatory compliance)",
                    totalSpending, currency
                );

                return VelocityCheckResult.blocked(reason, Map.of(
                    "currentSpending", currentSpending != null ? currentSpending : 0.0,
                    "transactionAmount", amount,
                    "totalSpending", totalSpending,
                    "monthlyLimit", DEFAULT_MONTHLY_LIMIT,
                    "remaining", DEFAULT_MONTHLY_LIMIT.subtract(new BigDecimal(currentSpending != null ? currentSpending : 0.0))
                ));
            }

            return VelocityCheckResult.allowed("Monthly spending within limits");

        } catch (Exception e) {
            log.error("Failed to check monthly spending limit for user: {}", userId, e);
            return VelocityCheckResult.allowed("Monthly limit check error - fail-safe");
        }
    }

    /**
     * Check high-value transaction block
     */
    private VelocityCheckResult checkHighValueTransaction(String userId, BigDecimal amount, String currency) {

        if (amount.compareTo(HIGH_VALUE_THRESHOLD) > 0) {

            // Log high-value transaction for manual review
            securityAuditLogger.logSecurityEvent(
                "HIGH_VALUE_TRANSACTION_BLOCKED",
                userId,
                String.format("High-value transaction blocked: %s %s (threshold configured externally)",
                    amount, currency),
                Map.of(
                    "userId", userId,
                    "amount", amount,
                    "currency", currency,
                    "threshold", HIGH_VALUE_THRESHOLD,
                    "requiresApproval", true
                )
            );

            String reason = String.format(
                "High-value transaction requires manual approval: %s %s exceeds configured threshold",
                amount, currency
            );

            return VelocityCheckResult.blocked(reason, Map.of(
                "requiresApproval", true,
                "approvalWorkflow", "HIGH_VALUE_TRANSACTION_REVIEW",
                "threshold", HIGH_VALUE_THRESHOLD,
                "amount", amount
            ));
        }

        return VelocityCheckResult.allowed("Transaction below high-value threshold");
    }

    /**
     * Detect suspicious transaction patterns
     */
    private void detectSuspiciousPatterns(String userId, BigDecimal amount, String currency) {

        try {
            // Get recent transaction timestamps
            String velocityKey = VELOCITY_PREFIX + userId;
            List<Object> timestamps = redisTemplate.opsForList().range(velocityKey, 0, 9);

            if (timestamps != null && timestamps.size() >= 3) {

                // Rapid-fire thresholds configured externally for regulatory compliance
                long rapidFireWindow = Long.parseLong(System.getProperty("velocity.rapid.fire.window.ms", "10000"));
                int rapidFireMinCount = Integer.parseInt(System.getProperty("velocity.rapid.fire.min.count", "3"));
                long rapidFireWindowAgo = System.currentTimeMillis() - rapidFireWindow;
                long rapidTransactions = timestamps.stream()
                    .filter(ts -> (Long) ts > rapidFireWindowAgo)
                    .count();

                if (rapidTransactions >= rapidFireMinCount) {
                    publishSuspiciousActivityAlert(userId, "RAPID_FIRE_TRANSACTIONS", Map.of(
                        "count", rapidTransactions,
                        "window", "configured time window",
                        "amount", amount,
                        "currency", currency
                    ));
                }

                // Structured transaction thresholds configured externally for regulatory compliance
                BigDecimal structuringDivisor = new BigDecimal(System.getProperty("velocity.structuring.divisor", "1000"));
                BigDecimal structuringMinAmount = new BigDecimal(System.getProperty("velocity.structuring.min.amount", "0"));
                if (amount.remainder(structuringDivisor).compareTo(BigDecimal.ZERO) == 0
                    && amount.compareTo(structuringMinAmount) >= 0) {

                    publishSuspiciousActivityAlert(userId, "STRUCTURED_TRANSACTION_PATTERN", Map.of(
                        "amount", amount,
                        "currency", currency,
                        "pattern", "Pattern configured for regulatory monitoring"
                    ));
                }
            }

        } catch (Exception e) {
            log.error("Failed to detect suspicious patterns for user: {}", userId, e);
        }
    }

    /**
     * Record limit breach for audit and alerting
     */
    private void recordLimitBreach(String userId, String limitType, VelocityCheckResult result) {

        try {
            // Security audit log
            securityAuditLogger.logSecurityEvent(
                "VELOCITY_LIMIT_BREACH",
                userId,
                String.format("%s limit breached: %s", limitType, result.getReason()),
                Map.of(
                    "userId", userId,
                    "limitType", limitType,
                    "reason", result.getReason(),
                    "details", result.getDetails()
                )
            );

            // Publish to Kafka for real-time monitoring
            Map<String, Object> breachEvent = Map.of(
                "eventType", "LIMIT_BREACH",
                "userId", userId,
                "limitType", limitType,
                "reason", result.getReason(),
                "details", result.getDetails(),
                "timestamp", System.currentTimeMillis()
            );

            kafkaTemplate.send(LIMIT_BREACH_TOPIC, userId, serializeEvent(breachEvent));

            incrementMetrics(userId, "breach_" + limitType.toLowerCase());

            log.warn("Velocity limit breach: user={}, type={}, reason={}", userId, limitType, result.getReason());

        } catch (Exception e) {
            log.error("Failed to record limit breach for user: {}", userId, e);
        }
    }

    /**
     * Publish suspicious activity alert
     */
    private void publishSuspiciousActivityAlert(String userId, String pattern, Map<String, Object> details) {

        try {
            Map<String, Object> alert = Map.of(
                "alertType", "SUSPICIOUS_TRANSACTION_PATTERN",
                "userId", userId,
                "pattern", pattern,
                "details", details,
                "timestamp", System.currentTimeMillis(),
                "severity", "HIGH"
            );

            kafkaTemplate.send(SUSPICIOUS_ACTIVITY_TOPIC, userId, serializeEvent(alert));

            log.warn("Suspicious activity detected: user={}, pattern={}, details={}", userId, pattern, details);

        } catch (Exception e) {
            log.error("Failed to publish suspicious activity alert for user: {}", userId, e);
        }
    }

    // Helper methods

    private void incrementMetrics(String userId, String status) {
        Counter.builder("velocity_limit_check")
            .tag("status", status)
            .register(meterRegistry)
            .increment();
    }

    private String formatDuration(Duration duration) {
        if (duration.toMinutes() < 1) return duration.getSeconds() + " seconds";
        if (duration.toHours() < 1) return duration.toMinutes() + " minutes";
        if (duration.toDays() < 1) return duration.toHours() + " hours";
        return duration.toDays() + " days";
    }

    private String calculateCooldownPeriod(List<Object> timestamps, Duration window) {
        if (timestamps.isEmpty()) return "0 seconds";

        Long oldestInWindow = (Long) timestamps.get(timestamps.size() - 1);
        long cooldownMs = (oldestInWindow + window.toMillis()) - System.currentTimeMillis();

        return cooldownMs > 0 ? (cooldownMs / 1000) + " seconds" : "0 seconds";
    }

    private String serializeEvent(Map<String, Object> event) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(event);
        } catch (Exception e) {
            log.error("Failed to serialize event", e);
            return "{}";
        }
    }

    /**
     * Velocity Check Result DTO
     */
    public static class VelocityCheckResult {
        private final boolean allowed;
        private final String reason;
        private final Map<String, Object> details;

        private VelocityCheckResult(boolean allowed, String reason, Map<String, Object> details) {
            this.allowed = allowed;
            this.reason = reason;
            this.details = details != null ? details : Map.of();
        }

        public static VelocityCheckResult allowed(String reason) {
            return new VelocityCheckResult(true, reason, null);
        }

        public static VelocityCheckResult blocked(String reason, Map<String, Object> details) {
            return new VelocityCheckResult(false, reason, details);
        }

        public boolean isAllowed() { return allowed; }
        public String getReason() { return reason; }
        public Map<String, Object> getDetails() { return details; }
    }
}
