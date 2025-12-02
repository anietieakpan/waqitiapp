package com.waqiti.common.monitoring;

import com.waqiti.common.kafka.KafkaDlqConfiguration;
import io.micrometer.core.instrument.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Centralized metrics service for business and technical metrics
 * 
 * Provides comprehensive metrics collection for:
 * - Payment transactions and amounts
 * - User activities and authentication
 * - System performance and health
 * - Business KPIs and revenue tracking
 * - Security events and fraud detection
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MetricsService {

    private final MeterRegistry meterRegistry;

    // Counters for business metrics
    private final Counter paymentTransactions;
    private final Counter paymentFailures;
    private final Counter userRegistrations;
    private final Counter userLogins;
    private final Counter fraudDetections;
    private final Counter authFailures;
    private final Counter suspiciousActivities;
    private final Counter rateLimitExceeded;

    // Gauges for real-time metrics
    private final AtomicLong activeUsers = new AtomicLong(0);
    private final AtomicLong pendingTransactions = new AtomicLong(0);
    private final AtomicLong dailyTransactionVolume = new AtomicLong(0);

    // Timers for performance metrics
    private final Timer paymentProcessingTime;
    private final Timer fraudCheckTime;
    private final Timer databaseQueryTime;
    private final Timer externalApiCallTime;

    // Distribution summaries for amount tracking
    private final DistributionSummary paymentAmounts;
    private final DistributionSummary transactionFees;

    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // Initialize counters
        this.paymentTransactions = Counter.builder("payment.transactions.total")
                .description("Total number of payment transactions")
                .register(meterRegistry);

        this.paymentFailures = Counter.builder("payment.failures.total")
                .description("Total number of failed payments")
                .register(meterRegistry);

        this.userRegistrations = Counter.builder("user.registrations.total")
                .description("Total number of user registrations")
                .register(meterRegistry);

        this.userLogins = Counter.builder("user.logins.total")
                .description("Total number of user logins")
                .register(meterRegistry);

        this.fraudDetections = Counter.builder("fraud.detections.total")
                .description("Total number of fraud detections")
                .register(meterRegistry);

        this.authFailures = Counter.builder("auth.failures.total")
                .description("Total number of authentication failures")
                .register(meterRegistry);

        this.suspiciousActivities = Counter.builder("security.suspicious.activities.total")
                .description("Total number of suspicious activities detected")
                .register(meterRegistry);

        this.rateLimitExceeded = Counter.builder("rate.limit.exceeded.total")
                .description("Total number of rate limit violations")
                .register(meterRegistry);

        // Initialize gauges
        Gauge.builder("users.active.current", activeUsers, AtomicLong::get)
                .description("Current number of active users")
                .register(meterRegistry);

        Gauge.builder("transactions.pending.current", pendingTransactions, AtomicLong::get)
                .description("Current number of pending transactions")
                .register(meterRegistry);

        Gauge.builder("transactions.daily.volume", dailyTransactionVolume, AtomicLong::get)
                .description("Daily transaction volume in dollars")
                .register(meterRegistry);

        // Initialize timers
        this.paymentProcessingTime = Timer.builder("payment.processing.duration")
                .description("Time taken to process payments")
                .register(meterRegistry);

        this.fraudCheckTime = Timer.builder("fraud.check.duration")
                .description("Time taken for fraud checks")
                .register(meterRegistry);

        this.databaseQueryTime = Timer.builder("database.query.duration")
                .description("Database query execution time")
                .register(meterRegistry);

        this.externalApiCallTime = Timer.builder("external.api.call.duration")
                .description("External API call duration")
                .register(meterRegistry);

        // Initialize distribution summaries
        this.paymentAmounts = DistributionSummary.builder("payment.amounts")
                .description("Distribution of payment amounts")
                .baseUnit("USD")
                .register(meterRegistry);

        this.transactionFees = DistributionSummary.builder("transaction.fees")
                .description("Distribution of transaction fees")
                .baseUnit("USD")
                .register(meterRegistry);
    }

    // Payment metrics
    public void recordPaymentTransaction(String status, double amount, String paymentMethod) {
        Counter.builder("payment.transactions.total")
                .tags("status", status, "payment_method", paymentMethod)
                .register(meterRegistry)
                .increment();

        if ("success".equals(status)) {
            paymentAmounts.record(amount);
            updateDailyVolume(amount);
        } else {
            Counter.builder("payment.failures.total")
                    .tags("payment_method", paymentMethod)
                    .register(meterRegistry)
                    .increment();
        }

        log.debug("Recorded payment transaction: status={}, amount={}, method={}", 
                status, amount, paymentMethod);
    }

    public void recordPaymentProcessingTime(Duration duration, String paymentMethod) {
        Timer.builder("payment.processing.duration")
                .tags("payment_method", paymentMethod)
                .register(meterRegistry)
                .record(duration);
    }

    public Timer.Sample startPaymentProcessingTimer() {
        return Timer.start(meterRegistry);
    }

    // User metrics
    public void recordUserRegistration(String registrationMethod, String userType) {
        Counter.builder("users.registrations.total")
                .tags("method", registrationMethod, "user_type", userType)
                .register(meterRegistry)
                .increment();
        log.debug("Recorded user registration: method={}, type={}", registrationMethod, userType);
    }

    public void recordUserLogin(String loginMethod, boolean biometric) {
        Counter.builder("users.logins.total")
                .tags("method", loginMethod, "biometric", String.valueOf(biometric))
                .register(meterRegistry)
                .increment();
        log.debug("Recorded user login: method={}, biometric={}", loginMethod, biometric);
    }

    public void updateActiveUsers(long count) {
        activeUsers.set(count);
    }

    public void incrementActiveUsers() {
        activeUsers.incrementAndGet();
    }

    public void decrementActiveUsers() {
        activeUsers.decrementAndGet();
    }

    // Security metrics
    public void recordFraudDetection(String fraudType, String riskLevel, double amount) {
        Counter.builder("fraud.detections.total")
                .tags("fraud_type", fraudType, "risk_level", riskLevel)
                .register(meterRegistry)
                .increment();
        log.warn("Recorded fraud detection: type={}, risk={}, amount={}", 
                fraudType, riskLevel, amount);
    }

    public void recordAuthFailure(String failureReason, String userAgent) {
        Counter.builder("auth.failures.total")
                .tags("reason", failureReason, "user_agent", sanitizeUserAgent(userAgent))
                .register(meterRegistry)
                .increment();
        log.warn("Recorded auth failure: reason={}", failureReason);
    }

    public void recordSuspiciousActivity(String activityType, String severity) {
        Counter.builder("security.suspicious.activities.total")
                .tags("activity_type", activityType, "severity", severity)
                .register(meterRegistry)
                .increment();
        log.warn("Recorded suspicious activity: type={}, severity={}", activityType, severity);
    }

    public void recordRateLimitExceeded(String endpoint, String clientId) {
        Counter.builder("rate.limit.exceeded.total")
                .tags("endpoint", endpoint, "client_type", clientId != null ? "authenticated" : "anonymous")
                .register(meterRegistry)
                .increment();
        log.warn("Recorded rate limit exceeded: endpoint={}, client={}", endpoint, clientId);
    }

    // Performance metrics
    public void recordFraudCheckTime(Duration duration, String checkType) {
        Timer.builder("fraud.check.duration")
                .tags("check_type", checkType)
                .register(meterRegistry)
                .record(duration);
    }

    public void recordDatabaseQueryTime(Duration duration, String queryType, String table) {
        Timer.builder("database.query.duration")
                .tags("query_type", queryType, "table", table)
                .register(meterRegistry)
                .record(duration);
    }

    public void recordExternalApiCall(Duration duration, String apiProvider, int statusCode) {
        Timer.builder("external.api.call.duration")
                .tags("provider", apiProvider,
                      "status_code", String.valueOf(statusCode),
                      "success", String.valueOf(statusCode >= 200 && statusCode < 300))
                .register(meterRegistry)
                .record(duration);
    }

    // Transaction metrics
    public void updatePendingTransactions(long count) {
        pendingTransactions.set(count);
    }

    public void incrementPendingTransactions() {
        pendingTransactions.incrementAndGet();
    }

    public void decrementPendingTransactions() {
        pendingTransactions.decrementAndGet();
    }

    public void recordTransactionFee(double fee, String feeType) {
        DistributionSummary.builder("transaction.fees")
                .tags("fee_type", feeType)
                .register(meterRegistry)
                .record(fee);
    }

    // Business metrics
    public void recordRevenueMetric(double amount, String revenueType) {
        DistributionSummary.builder("revenue.amounts")
                .description("Revenue tracking by type")
                .baseUnit("USD")
                .tag("revenue_type", revenueType)
                .register(meterRegistry)
                .record(amount);

        updateDailyVolume(amount);
        log.debug("Recorded revenue: amount={}, type={}", amount, revenueType);
    }

    // Custom business metrics
    public void recordCustomBusinessMetric(String metricName, double value, Tags tags) {
        Gauge.builder("business.custom." + metricName, () -> value)
                .description("Custom business metric: " + metricName)
                .tags(tags)
                .register(meterRegistry);
    }

    public void incrementCustomCounter(String counterName, Tags tags) {
        Counter.builder("business.counter." + counterName)
                .description("Custom business counter: " + counterName)
                .tags(tags)
                .register(meterRegistry)
                .increment();
    }

    // Timer utilities
    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordCustomTimer(String timerName, Duration duration, Tags tags) {
        Timer.builder("business.timer." + timerName)
                .description("Custom business timer: " + timerName)
                .tags(tags)
                .register(meterRegistry)
                .record(duration);
    }

    // Health metrics
    public void recordHealthCheck(String component, boolean healthy, Duration responseTime) {
        Counter.builder("health.checks.total")
                .description("Health check results")
                .tag("component", component)
                .tag("status", healthy ? "healthy" : "unhealthy")
                .register(meterRegistry)
                .increment();

        Timer.builder("health.check.duration")
                .description("Health check response time")
                .tag("component", component)
                .register(meterRegistry)
                .record(responseTime);
    }

    // Helper methods
    private void updateDailyVolume(double amount) {
        dailyTransactionVolume.addAndGet((long) amount);
    }

    private String sanitizeUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return "unknown";
        }
        // Extract browser type for metrics (simplified)
        if (userAgent.contains("Chrome")) return "chrome";
        if (userAgent.contains("Firefox")) return "firefox";
        if (userAgent.contains("Safari")) return "safari";
        if (userAgent.contains("Edge")) return "edge";
        return "other";
    }

    // Metric snapshot for health checks
    public MetricsSnapshot getMetricsSnapshot() {
        return MetricsSnapshot.builder()
                .activeUsers(activeUsers.get())
                .pendingTransactions(pendingTransactions.get())
                .dailyVolume(dailyTransactionVolume.get())
                .totalPayments(paymentTransactions.count())
                .totalFraudDetections(fraudDetections.count())
                .totalAuthFailures(authFailures.count())
                .build();
    }

    // Reset daily metrics (called by scheduled job)
    public void resetDailyMetrics() {
        dailyTransactionVolume.set(0);
        log.info("Daily metrics reset completed");
    }
    
    /**
     * Increment error counter by type and category
     */
    public void incrementErrorCounter(String errorType, String errorCode) {
        Counter.builder("errors.total")
                .description("Total error count by type and code")
                .tag("error_type", errorType)
                .tag("error_code", errorCode)
                .register(meterRegistry)
                .increment();
                
        log.debug("Incremented error counter: type={}, code={}", errorType, errorCode);
    }
    
    /**
     * Record DLQ statistics for monitoring
     */
    public void recordDlqStats(KafkaDlqConfiguration.DlqStatistics stats) {
        if (stats == null) {
            return;
        }
        
        // Record total messages in DLQ
        Gauge.builder("dlq.messages.total", stats, KafkaDlqConfiguration.DlqStatistics::getTotalMessagesInDlq)
                .description("Total messages currently in all DLQ topics")
                .register(meterRegistry);
        
        // Record health score
        Gauge.builder("dlq.health.score", stats, KafkaDlqConfiguration.DlqStatistics::getHealthScore)
                .description("Overall DLQ health score (0-100)")
                .register(meterRegistry);
        
        // Record per-topic metrics
        if (stats.getDlqDepths() != null) {
            stats.getDlqDepths().forEach((topic, depth) -> {
                Gauge.builder("dlq.topic.depth", () -> depth)
                        .description("Messages in specific DLQ topic")
                        .tag("dlq_topic", topic)
                        .register(meterRegistry);
            });
        }
        
        // Record processing rates
        if (stats.getProcessingRates() != null) {
            stats.getProcessingRates().forEach((topic, rate) -> {
                Gauge.builder("dlq.processing.rate", () -> rate)
                        .description("DLQ message processing rate")
                        .tag("dlq_topic", topic)
                        .register(meterRegistry);
            });
        }
        
        // Record oldest message age
        if (stats.getOldestMessages() != null) {
            stats.getOldestMessages().forEach((topic, timestamp) -> {
                long ageMinutes = (System.currentTimeMillis() - timestamp) / 60000;
                Gauge.builder("dlq.message.age.minutes", () -> ageMinutes)
                        .description("Age of oldest message in DLQ (minutes)")
                        .tag("dlq_topic", topic)
                        .register(meterRegistry);
            });
        }
        
        log.debug("Recorded DLQ stats: total={}, health={}", 
                stats.getTotalMessagesInDlq(), stats.getHealthScore());
    }

    public void recordProcessingTime(String accountClosureEventsConsumer, long processingTime) {
//        TODO - properly implement with business logic, production-ready code, etc. added by aniix october, 28th 2025
    }

    public void incrementCounter(String accountClosuresProcessed, String type, String closureType) {
//        TODO - properly implement with business logic, production-ready code, etc. added by aniix october, 28th 2025
    }
}