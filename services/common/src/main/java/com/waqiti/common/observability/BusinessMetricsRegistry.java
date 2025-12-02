package com.waqiti.common.observability;

import com.waqiti.common.metrics.abstraction.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Business Metrics Registry - PRODUCTION GRADE
 * Uses industrial-strength metrics abstraction with cardinality control
 */
@Slf4j
@Component
public class BusinessMetricsRegistry {
    
    private final MetricsRegistry metricsRegistry;
    private final String serviceName;
    
    // Enterprise-grade state management for business metrics
    private final ConcurrentHashMap<String, AtomicLong> businessCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DoubleAdder> businessGauges = new ConcurrentHashMap<>();
    
    // State tracking for pending transactions
    private final AtomicLong pendingTransactionsCount = new AtomicLong(0);
    private final AtomicLong activeUsersCount = new AtomicLong(0);
    private final AtomicLong completedTransactionsCount = new AtomicLong(0);
    private final AtomicLong failedTransactionsCount = new AtomicLong(0);
    
    // Metric Definitions
    private static final class Metrics {
        // Payment metrics
        static final MetricDefinition PAYMENT_TRANSACTIONS = MetricDefinition.builder()
            .name("payment.transactions.total")
            .description("Total payment transactions")
            .type(MetricDefinition.MetricType.COUNTER)
            .maxCardinality(5000)
            .critical(true)
            .build();
            
        static final MetricDefinition PAYMENT_PROCESSING_TIME = MetricDefinition.builder()
            .name("payment.processing.time")
            .description("Payment processing duration")
            .type(MetricDefinition.MetricType.TIMER)
            .slos(List.of(Duration.ofMillis(100), Duration.ofMillis(500), Duration.ofSeconds(2)))
            .build();
            
        static final MetricDefinition PAYMENT_AMOUNT = MetricDefinition.builder()
            .name("payment.amount")
            .description("Payment amount distribution")
            .type(MetricDefinition.MetricType.DISTRIBUTION_SUMMARY)
            .scale(100.0)
            .build();
        
        // Wallet metrics
        static final MetricDefinition WALLET_TRANSACTIONS = MetricDefinition.builder()
            .name("wallet.transactions.total")
            .description("Total wallet transactions")
            .type(MetricDefinition.MetricType.COUNTER)
            .maxCardinality(3000)
            .build();
            
        static final MetricDefinition WALLET_BALANCE = MetricDefinition.builder()
            .name("wallet.balance")
            .description("Wallet balance")
            .type(MetricDefinition.MetricType.GAUGE)
            .build();
        
        // Transfer metrics
        static final MetricDefinition TRANSFERS = MetricDefinition.builder()
            .name("transfers.total")
            .description("Total transfers")
            .type(MetricDefinition.MetricType.COUNTER)
            .maxCardinality(3000)
            .build();
        
        // KYC metrics
        static final MetricDefinition KYC_VERIFICATIONS = MetricDefinition.builder()
            .name("kyc.verifications.total")
            .description("Total KYC verifications")
            .type(MetricDefinition.MetricType.COUNTER)
            .maxCardinality(1000)
            .build();
            
        static final MetricDefinition KYC_PROCESSING_TIME = MetricDefinition.builder()
            .name("kyc.processing.time")
            .description("KYC processing duration")
            .type(MetricDefinition.MetricType.TIMER)
            .slos(List.of(Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(30)))
            .build();
        
        // Fraud metrics
        static final MetricDefinition FRAUD_CHECKS = MetricDefinition.builder()
            .name("fraud.checks.total")
            .description("Total fraud checks")
            .type(MetricDefinition.MetricType.COUNTER)
            .critical(true)
            .maxCardinality(2000)
            .build();
            
        static final MetricDefinition FRAUD_BLOCKED_AMOUNT = MetricDefinition.builder()
            .name("fraud.blocked.amount")
            .description("Amount blocked due to fraud")
            .type(MetricDefinition.MetricType.DISTRIBUTION_SUMMARY)
            .critical(true)
            .build();
        
        // API metrics
        static final MetricDefinition API_CALLS = MetricDefinition.builder()
            .name("api.calls.total")
            .description("Total API calls")
            .type(MetricDefinition.MetricType.COUNTER)
            .maxCardinality(2000)
            .build();
            
        static final MetricDefinition API_LATENCY = MetricDefinition.builder()
            .name("api.latency")
            .description("API latency")
            .type(MetricDefinition.MetricType.TIMER)
            .slos(List.of(Duration.ofMillis(50), Duration.ofMillis(200), Duration.ofMillis(1000)))
            .build();
        
        // Database metrics
        static final MetricDefinition DATABASE_OPERATIONS = MetricDefinition.builder()
            .name("database.operations.total")
            .description("Total database operations")
            .type(MetricDefinition.MetricType.COUNTER)
            .maxCardinality(500)
            .build();
            
        static final MetricDefinition DATABASE_EXECUTION_TIME = MetricDefinition.builder()
            .name("database.execution.time")
            .description("Database execution time")
            .type(MetricDefinition.MetricType.TIMER)
            .slos(List.of(Duration.ofMillis(10), Duration.ofMillis(50), Duration.ofMillis(200)))
            .build();
        
        // User metrics
        static final MetricDefinition USER_LOGINS = MetricDefinition.builder()
            .name("user.logins.total")
            .description("Total user logins")
            .type(MetricDefinition.MetricType.COUNTER)
            .maxCardinality(1000)
            .build();
            
        static final MetricDefinition USER_REGISTRATIONS = MetricDefinition.builder()
            .name("user.registrations.total")
            .description("Total user registrations")
            .type(MetricDefinition.MetricType.COUNTER)
            .critical(true)
            .build();
            
        static final MetricDefinition ACTIVE_USERS = MetricDefinition.builder()
            .name("users.active.count")
            .description("Active users count")
            .type(MetricDefinition.MetricType.GAUGE)
            .build();
        
        // Compliance metrics
        static final MetricDefinition COMPLIANCE_EVENTS = MetricDefinition.builder()
            .name("compliance.events.total")
            .description("Total compliance events")
            .type(MetricDefinition.MetricType.COUNTER)
            .critical(true)
            .maxCardinality(1000)
            .build();
        
        // Notification metrics
        static final MetricDefinition NOTIFICATIONS_SENT = MetricDefinition.builder()
            .name("notifications.sent.total")
            .description("Total notifications sent")
            .type(MetricDefinition.MetricType.COUNTER)
            .sampleRate(0.1) // Sample 10% of notifications
            .build();
            
        // Pending transaction metrics
        static final MetricDefinition PENDING_TRANSACTIONS = MetricDefinition.builder()
            .name("transactions.pending.count")
            .description("Pending transactions count")
            .type(MetricDefinition.MetricType.GAUGE)
            .critical(true)
            .build();
            
        // Fraud detection metrics
        static final MetricDefinition FRAUD_DETECTIONS = MetricDefinition.builder()
            .name("fraud.detections.total")
            .description("Total fraud detections")
            .type(MetricDefinition.MetricType.COUNTER)
            .critical(true)
            .maxCardinality(2000)
            .build();
            
        // Payment failure metrics
        static final MetricDefinition PAYMENT_FAILURES = MetricDefinition.builder()
            .name("payment.failures.total")
            .description("Total payment failures")
            .type(MetricDefinition.MetricType.COUNTER)
            .critical(true)
            .maxCardinality(1500)
            .build();
    }
    
    // Tag constraints for different metric types
    private static final TagConstraints PAYMENT_TAG_CONSTRAINTS = TagConstraints.builder()
        .maxTags(8)
        .requiredTags(java.util.Set.of("type", "status"))
        .valueNormalizers(java.util.Map.of(
            "amount_range", TagConstraints.Normalizers.AMOUNT_RANGE,
            "status_code", TagConstraints.Normalizers.HTTP_STATUS
        ))
        .build();
    
    private static final TagConstraints API_TAG_CONSTRAINTS = TagConstraints.builder()
        .maxTags(6)
        .requiredTags(java.util.Set.of("endpoint", "method"))
        .allowedValues(java.util.Map.of(
            "method", java.util.Set.of("GET", "POST", "PUT", "DELETE", "PATCH")
        ))
        .valueNormalizers(java.util.Map.of(
            "status_code", TagConstraints.Normalizers.HTTP_STATUS,
            "error", TagConstraints.Normalizers.ERROR_CLASS
        ))
        .build();
    
    public BusinessMetricsRegistry(
            MetricsRegistry metricsRegistry,
            @Value("${spring.application.name:unknown}") String serviceName) {
        this.metricsRegistry = metricsRegistry;
        this.serviceName = serviceName;
        
        log.info("Business metrics registry initialized for service: {}", serviceName);
    }
    
    // Payment metrics
    public void recordPaymentTransaction(String type, String status, String currency, 
                                        BigDecimal amount, Duration processingTime) {
        TagSet tags = TagSet.builder(PAYMENT_TAG_CONSTRAINTS)
            .tag("service", serviceName)
            .tag("type", type)
            .tag("status", status)
            .tag("currency", currency)
            .tag("amount_range", categorizeAmount(amount))
            .build();
        
        metricsRegistry.incrementCounter(Metrics.PAYMENT_TRANSACTIONS, tags);
        metricsRegistry.recordTime(Metrics.PAYMENT_PROCESSING_TIME, tags, processingTime);
        metricsRegistry.recordDistribution(Metrics.PAYMENT_AMOUNT, tags, amount.doubleValue());
    }
    
    // Wallet metrics
    public void recordWalletTransaction(String type, String currency, BigDecimal amount, String status) {
        TagSet tags = TagSet.builder()
            .tag("service", serviceName)
            .tag("type", type)
            .tag("currency", currency)
            .tag("status", status)
            .tag("amount_range", categorizeAmount(amount))
            .build();
        
        metricsRegistry.incrementCounter(Metrics.WALLET_TRANSACTIONS, tags);
    }
    
    public void updateWalletBalance(String walletId, String currency, BigDecimal balance) {
        TagSet tags = TagSet.of("currency", currency, "service", serviceName);
        metricsRegistry.updateGauge(Metrics.WALLET_BALANCE, tags, balance.doubleValue());
    }
    
    // Transfer metrics
    public void recordTransfer(String type, String status, BigDecimal amount, 
                              String sourceCurrency, String targetCurrency) {
        TagSet tags = TagSet.builder()
            .tag("service", serviceName)
            .tag("type", type)
            .tag("status", status)
            .tag("source_currency", sourceCurrency)
            .tag("target_currency", targetCurrency)
            .tag("amount_range", categorizeAmount(amount))
            .build();
        
        metricsRegistry.incrementCounter(Metrics.TRANSFERS, tags);
    }
    
    // KYC metrics
    public void recordKycVerification(String level, String status, String verificationType, 
                                     Duration processingTime) {
        TagSet tags = TagSet.builder()
            .tag("service", serviceName)
            .tag("level", level)
            .tag("status", status)
            .tag("verification_type", verificationType)
            .tag("processing_category", categorizeDuration(processingTime))
            .build();
        
        metricsRegistry.incrementCounter(Metrics.KYC_VERIFICATIONS, tags);
        metricsRegistry.recordTime(Metrics.KYC_PROCESSING_TIME, tags, processingTime);
    }
    
    // Fraud metrics
    public void recordFraudCheck(String type, String riskLevel, boolean blocked, 
                                String reason, String failureReason) {
        TagSet tags = TagSet.builder()
            .tag("service", serviceName)
            .tag("type", type)
            .tag("risk_level", riskLevel)
            .tag("blocked", blocked)
            .tag("reason", reason != null ? reason : "none")
            .tag("failure_reason", failureReason != null ? failureReason : "none")
            .build();
        
        metricsRegistry.incrementCounter(Metrics.FRAUD_CHECKS, tags);
    }
    
    public void recordFraudBlockedAmount(String type, String currency, BigDecimal amount) {
        TagSet tags = TagSet.builder()
            .tag("service", serviceName)
            .tag("type", type)
            .tag("currency", currency)
            .build();
        
        metricsRegistry.recordDistribution(Metrics.FRAUD_BLOCKED_AMOUNT, tags, amount.doubleValue());
    }
    
    // API metrics
    public void recordApiCall(String endpoint, String method, int statusCode, 
                            Duration latency, String error) {
        TagSet tags = TagSet.builder(API_TAG_CONSTRAINTS)
            .tag("service", serviceName)
            .tag("endpoint", normalizeEndpoint(endpoint))
            .tag("method", method)
            .tag("status_code", String.valueOf(statusCode))
            .tag("error", error != null ? error : "none")
            .build();
        
        metricsRegistry.incrementCounter(Metrics.API_CALLS, tags);
        metricsRegistry.recordTime(Metrics.API_LATENCY, tags, latency);
    }
    
    // Database metrics
    public void recordDatabaseOperation(String operation, String table, Duration executionTime, 
                                       boolean success, String error) {
        TagSet tags = TagSet.builder()
            .tag("service", serviceName)
            .tag("operation", operation)
            .tag("table", table)
            .tag("success", success)
            .tag("error", error != null ? error : "none")
            .tag("execution_category", categorizeDuration(executionTime))
            .build();
        
        metricsRegistry.incrementCounter(Metrics.DATABASE_OPERATIONS, tags);
        metricsRegistry.recordTime(Metrics.DATABASE_EXECUTION_TIME, tags, executionTime);
    }
    
    // User metrics
    public void recordUserLogin(String method, boolean success, String error) {
        TagSet tags = TagSet.builder()
            .tag("service", serviceName)
            .tag("method", method)
            .tag("success", success)
            .tag("error", error != null ? error : "none")
            .build();
        
        metricsRegistry.incrementCounter(Metrics.USER_LOGINS, tags);
    }
    
    public void recordUserRegistration(String channel, String kycLevel, boolean successful) {
        TagSet tags = TagSet.builder()
            .tag("service", serviceName)
            .tag("channel", channel)
            .tag("kyc_level", kycLevel)
            .tag("successful", successful)
            .build();
        
        metricsRegistry.incrementCounter(Metrics.USER_REGISTRATIONS, tags);
    }
    
    public void updateActiveUsers(long count) {
        // Update local state
        activeUsersCount.set(count);
        
        // Update metrics projection
        TagSet tags = TagSet.of("service", serviceName);
        metricsRegistry.updateGauge(Metrics.ACTIVE_USERS, tags, count);
        
        log.debug("Updated active users count: {}", count);
    }
    
    public void incrementActiveUsers() {
        // Update local state
        long newCount = activeUsersCount.incrementAndGet();
        
        // Update metrics projection
        TagSet tags = TagSet.of("service", serviceName);
        metricsRegistry.updateGauge(Metrics.ACTIVE_USERS, tags, newCount);
        
        log.debug("Incremented active users: {}", newCount);
    }
    
    public void decrementActiveUsers() {
        // Update local state (ensure non-negative)
        long newCount = Math.max(0, activeUsersCount.decrementAndGet());
        
        // Update metrics projection
        TagSet tags = TagSet.of("service", serviceName);
        metricsRegistry.updateGauge(Metrics.ACTIVE_USERS, tags, newCount);
        
        log.debug("Decremented active users: {}", newCount);
    }
    
    // Compliance metrics
    public void recordComplianceEvent(String type, String severity, boolean resolved, String resolution) {
        TagSet tags = TagSet.builder()
            .tag("service", serviceName)
            .tag("type", type)
            .tag("severity", severity)
            .tag("resolved", resolved)
            .tag("resolution", resolution != null ? resolution : "pending")
            .build();
        
        metricsRegistry.incrementCounter(Metrics.COMPLIANCE_EVENTS, tags);
    }
    
    // Notification metrics
    public void recordNotification(String type, String channel, boolean delivered, String error) {
        TagSet tags = TagSet.builder()
            .tag("service", serviceName)
            .tag("type", type)
            .tag("channel", channel)
            .tag("delivered", delivered)
            .tag("error", error != null ? error : "none")
            .build();
        
        metricsRegistry.incrementCounter(Metrics.NOTIFICATIONS_SENT, tags);
    }
    
    // Pending transaction metrics - with enterprise state management
    public void incrementPendingTransactions() {
        // Update local state
        long newCount = pendingTransactionsCount.incrementAndGet();
        
        // Update metrics projection
        TagSet tags = TagSet.of("service", serviceName);
        metricsRegistry.updateGauge(Metrics.PENDING_TRANSACTIONS, tags, newCount);
        
        log.debug("Incremented pending transactions: {}", newCount);
    }
    
    public void incrementPendingTransactions(String type, String currency) {
        // Update local state
        long newCount = pendingTransactionsCount.incrementAndGet();
        
        // Update metrics projection with tags
        TagSet tags = TagSet.builder()
            .tag("service", serviceName)
            .tag("type", type)
            .tag("currency", currency)
            .build();
        
        metricsRegistry.updateGauge(Metrics.PENDING_TRANSACTIONS, tags, newCount);
        
        log.debug("Incremented pending transactions [type={}, currency={}]: {}", type, currency, newCount);
    }
    
    public void decrementPendingTransactions() {
        // Update local state (ensure non-negative)
        long newCount = Math.max(0, pendingTransactionsCount.decrementAndGet());
        
        // Update metrics projection
        TagSet tags = TagSet.of("service", serviceName);
        metricsRegistry.updateGauge(Metrics.PENDING_TRANSACTIONS, tags, newCount);
        
        log.debug("Decremented pending transactions: {}", newCount);
    }
    
    public void decrementPendingTransactions(String type, String currency) {
        // Update local state (ensure non-negative)
        long newCount = Math.max(0, pendingTransactionsCount.decrementAndGet());
        
        // Update metrics projection with tags
        TagSet tags = TagSet.builder()
            .tag("service", serviceName)
            .tag("type", type)
            .tag("currency", currency)
            .build();
        
        metricsRegistry.updateGauge(Metrics.PENDING_TRANSACTIONS, tags, newCount);
        
        log.debug("Decremented pending transactions [type={}, currency={}]: {}", type, currency, newCount);
    }
    
    public long getPendingTransactionsCount() {
        return pendingTransactionsCount.get();
    }
    
    public void incrementCompletedTransactions() {
        // Update local state
        long newCount = completedTransactionsCount.incrementAndGet();
        
        // Update metrics projection
        TagSet tags = TagSet.of("service", serviceName, "status", "completed");
        metricsRegistry.incrementCounter(Metrics.PAYMENT_TRANSACTIONS, tags);
        
        log.debug("Incremented completed transactions: {}", newCount);
    }
    
    public void incrementFailedTransactions() {
        // Update local state
        long newCount = failedTransactionsCount.incrementAndGet();
        
        // Update metrics projection
        TagSet tags = TagSet.of("service", serviceName, "status", "failed");
        metricsRegistry.incrementCounter(Metrics.PAYMENT_FAILURES, tags);
        
        log.debug("Incremented failed transactions: {}", newCount);
    }
    
    public long getCompletedTransactionsCount() {
        return completedTransactionsCount.get();
    }
    
    public long getFailedTransactionsCount() {
        return failedTransactionsCount.get();
    }
    
    public void recordTransactionDuration(String type, long durationMs) {
        TagSet tags = TagSet.builder()
            .tag("service", serviceName)
            .tag("type", type)
            .build();
        metricsRegistry.recordTime(Metrics.PAYMENT_PROCESSING_TIME, tags, Duration.ofMillis(durationMs));
    }
    
    // Fraud detection metrics
    public void recordFraudDetection(String transactionId, double fraudScore) {
        TagSet tags = TagSet.builder()
            .tag("service", serviceName)
            .tag("transaction_id", transactionId)
            .tag("score_range", categorizeFraudScore(fraudScore))
            .build();
        
        metricsRegistry.incrementCounter(Metrics.FRAUD_DETECTIONS, tags);
    }
    
    public void recordFraudDetection(String detectionType, String riskLevel, boolean blocked, String reason) {
        TagSet tags = TagSet.builder()
            .tag("service", serviceName)
            .tag("detection_type", detectionType)
            .tag("risk_level", riskLevel)
            .tag("blocked", String.valueOf(blocked))
            .tag("reason", reason != null ? reason : "unknown")
            .build();
        
        metricsRegistry.incrementCounter(Metrics.FRAUD_DETECTIONS, tags);
    }
    
    // Compliance check metrics
    public void recordComplianceCheck(String checkType, boolean passed) {
        TagSet tags = TagSet.builder()
            .tag("service", serviceName)
            .tag("check_type", checkType)
            .tag("passed", String.valueOf(passed))
            .build();
        
        metricsRegistry.incrementCounter(Metrics.COMPLIANCE_EVENTS, tags);
    }
    
    public void recordComplianceCheck(String checkType, String status, String riskLevel, String resolution) {
        TagSet tags = TagSet.builder()
            .tag("service", serviceName)
            .tag("check_type", checkType)
            .tag("status", status)
            .tag("risk_level", riskLevel != null ? riskLevel : "unknown")
            .tag("resolution", resolution != null ? resolution : "pending")
            .build();
        
        metricsRegistry.incrementCounter(Metrics.COMPLIANCE_EVENTS, tags);
    }
    
    // Payment failure metrics
    public void recordPaymentFailure(String type, String reason, String errorCode, BigDecimal amount, String currency) {
        TagSet tags = TagSet.builder()
            .tag("service", serviceName)
            .tag("type", type)
            .tag("reason", reason != null ? reason : "unknown")
            .tag("error_code", errorCode != null ? errorCode : "unknown")
            .tag("currency", currency)
            .tag("amount_range", categorizeAmount(amount))
            .build();
        
        metricsRegistry.incrementCounter(Metrics.PAYMENT_FAILURES, tags);
    }
    
    // Active users getter - enterprise state management
    public long getActiveUsers() {
        return activeUsersCount.get();
    }
    
    // Enterprise state management methods
    public BusinessMetricsSummary getBusinessMetricsSummary() {
        return BusinessMetricsSummary.builder()
            .serviceName(serviceName)
            .pendingTransactions(pendingTransactionsCount.get())
            .completedTransactions(completedTransactionsCount.get())
            .failedTransactions(failedTransactionsCount.get())
            .activeUsers(activeUsersCount.get())
            .totalTransactions(completedTransactionsCount.get() + failedTransactionsCount.get())
            .successRate(calculateSuccessRate())
            .lastUpdated(java.time.LocalDateTime.now())
            .build();
    }
    
    private double calculateSuccessRate() {
        long completed = completedTransactionsCount.get();
        long failed = failedTransactionsCount.get();
        long total = completed + failed;
        return total > 0 ? (double) completed / total * 100.0 : 100.0;
    }
    
    // Reset all counters (for testing or maintenance)
    public void resetAllCounters() {
        pendingTransactionsCount.set(0);
        activeUsersCount.set(0);
        completedTransactionsCount.set(0);
        failedTransactionsCount.set(0);
        businessCounters.clear();
        businessGauges.clear();
        
        log.warn("All business metrics counters have been reset for service: {}", serviceName);
    }
    
    @lombok.Data
    @lombok.Builder
    public static class BusinessMetricsSummary {
        private String serviceName;
        private long pendingTransactions;
        private long completedTransactions;
        private long failedTransactions;
        private long activeUsers;
        private long totalTransactions;
        private double successRate;
        private java.time.LocalDateTime lastUpdated;
    }
    
    // Helper methods
    private String categorizeAmount(BigDecimal amount) {
        if (amount == null) return "unknown";
        double value = amount.doubleValue();
        if (value < 10) return "micro";
        if (value < 100) return "small";
        if (value < 1000) return "medium";
        if (value < 10000) return "large";
        return "very_large";
    }
    
    private String categorizeDuration(Duration duration) {
        if (duration == null) return "unknown";
        long millis = duration.toMillis();
        if (millis < 100) return "very_fast";
        if (millis < 500) return "fast";
        if (millis < 2000) return "normal";
        if (millis < 5000) return "slow";
        return "very_slow";
    }
    
    private String categorizeFraudScore(double score) {
        if (score < 0.2) return "very_low";
        if (score < 0.4) return "low";
        if (score < 0.6) return "medium";
        if (score < 0.8) return "high";
        return "very_high";
    }
    
    private String normalizeEndpoint(String endpoint) {
        if (endpoint == null) return "unknown";
        // Remove IDs and UUIDs from paths
        return endpoint
            .replaceAll("/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "/{id}")
            .replaceAll("/\\d+", "/{id}");
    }
    
    /**
     * Get comprehensive metrics summary
     */
    public com.waqiti.common.observability.dto.BusinessMetricsSummary getMetricsSummary() {
        return com.waqiti.common.observability.dto.BusinessMetricsSummary.builder()
            .totalTransactions(completedTransactionsCount.get())
            .failedTransactions(failedTransactionsCount.get())
            .pendingTransactions(pendingTransactionsCount.get())
            .activeUsers(activeUsersCount.get())
            .timestamp(java.time.Instant.now())
            .build();
    }
}