package com.waqiti.common.metrics;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Comprehensive metrics collection service for all Waqiti financial operations
 * 
 * Provides standardized metrics collection across:
 * - Transaction processing performance and volumes
 * - Account management operations
 * - Payment processing success rates and latencies
 * - Fraud detection effectiveness metrics
 * - Compliance screening performance
 * - System health and resource utilization
 * - Business KPIs and financial metrics
 * - User engagement and behavior analytics
 * - API performance and error rates
 * - Integration service health monitoring
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComprehensiveMetricsService {

    private final MeterRegistry meterRegistry;
    
    // Custom counters and gauges
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();
    private final Map<String, Timer> timers = new ConcurrentHashMap<>();
    private final Map<String, Gauge> gauges = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> atomicValues = new ConcurrentHashMap<>();

    // =====================================================
    // TRANSACTION METRICS
    // =====================================================

    /**
     * Record transaction processing metrics
     */
    public void recordTransactionProcessed(String transactionType, String status, 
                                         BigDecimal amount, String currency, Duration processingTime) {
        try {
            // Transaction count by type and status
            getCounter("transactions.processed", 
                    "type", transactionType, 
                    "status", status, 
                    "currency", currency)
                .increment();
            
            // Transaction processing time
            getTimer("transactions.processing.time", 
                    "type", transactionType)
                .record(processingTime);
            
            // Transaction amount metrics
            recordTransactionAmount(transactionType, currency, amount);
            
            log.debug("Recorded transaction metric: type={}, status={}, amount={} {}, duration={}ms",
                    transactionType, status, amount, currency, processingTime.toMillis());
                    
        } catch (Exception e) {
            log.warn("Failed to record transaction metrics", e);
        }
    }

    /**
     * Record transaction failure metrics
     */
    public void recordTransactionFailure(String transactionType, String failureReason, 
                                       String errorCode, Duration attemptDuration) {
        getCounter("transactions.failures", 
                "type", transactionType,
                "reason", failureReason,
                "error_code", errorCode)
            .increment();
        
        getTimer("transactions.failure.duration", 
                "type", transactionType,
                "reason", failureReason)
            .record(attemptDuration);
    }

    /**
     * Record transaction retry metrics
     */
    public void recordTransactionRetry(String transactionType, int retryAttempt, 
                                     String previousFailureReason) {
        getCounter("transactions.retries",
                "type", transactionType,
                "retry_attempt", String.valueOf(retryAttempt),
                "previous_failure", previousFailureReason)
            .increment();
    }

    // =====================================================
    // PAYMENT PROCESSING METRICS
    // =====================================================

    /**
     * Record payment processing performance
     */
    public void recordPaymentProcessed(String paymentMethod, String status, BigDecimal amount, 
                                     String currency, Duration processingTime, String provider) {
        // Payment success/failure rates by method
        getCounter("payments.processed",
                "method", paymentMethod,
                "status", status,
                "currency", currency,
                "provider", provider)
            .increment();
        
        // Payment processing latency
        getTimer("payments.processing.latency",
                "method", paymentMethod,
                "provider", provider)
            .record(processingTime);
        
        // Payment amount distribution
        recordPaymentAmount(paymentMethod, currency, amount);
    }

    /**
     * Record payment settlement metrics
     */
    public void recordPaymentSettlement(String settlementType, Duration settlementTime, 
                                      BigDecimal settlementAmount, String currency, String status) {
        getCounter("payments.settlements",
                "type", settlementType,
                "status", status,
                "currency", currency)
            .increment();
        
        getTimer("payments.settlement.time",
                "type", settlementType)
            .record(settlementTime);
        
        recordSettlementAmount(currency, settlementAmount);
    }

    // =====================================================
    // ACCOUNT MANAGEMENT METRICS
    // =====================================================

    /**
     * Record account operations
     */
    public void recordAccountOperation(String operationType, String accountType, 
                                     String status, Duration operationTime) {
        getCounter("accounts.operations",
                "operation", operationType,
                "account_type", accountType,
                "status", status)
            .increment();
        
        getTimer("accounts.operation.duration",
                "operation", operationType,
                "account_type", accountType)
            .record(operationTime);
    }

    /**
     * Record account balance updates
     */
    public void recordBalanceUpdate(String accountType, String currency, 
                                  BigDecimal previousBalance, BigDecimal newBalance, String updateReason) {
        BigDecimal balanceChange = newBalance.subtract(previousBalance);
        
        // Record balance change distribution
        recordBalanceChange(accountType, currency, balanceChange);
        
        getCounter("accounts.balance.updates",
                "account_type", accountType,
                "currency", currency,
                "reason", updateReason)
            .increment();
    }

    // =====================================================
    // FRAUD DETECTION METRICS
    // =====================================================

    /**
     * Record fraud detection performance
     */
    public void recordFraudDetection(String detectionType, boolean fraudDetected, 
                                   double riskScore, Duration analysisTime, String riskLevel) {
        getCounter("fraud.detections",
                "type", detectionType,
                "detected", String.valueOf(fraudDetected),
                "risk_level", riskLevel)
            .increment();
        
        getTimer("fraud.analysis.time",
                "type", detectionType)
            .record(analysisTime);
        
        // Record risk score distribution
        recordRiskScore(detectionType, riskScore);
    }

    /**
     * Record fraud investigation outcomes
     */
    public void recordFraudInvestigation(String investigationType, String outcome, 
                                       Duration investigationTime, boolean falsePositive) {
        getCounter("fraud.investigations",
                "type", investigationType,
                "outcome", outcome,
                "false_positive", String.valueOf(falsePositive))
            .increment();
        
        getTimer("fraud.investigation.duration",
                "type", investigationType)
            .record(investigationTime);
    }

    // =====================================================
    // COMPLIANCE METRICS
    // =====================================================

    /**
     * Record AML screening performance
     */
    public void recordAMLScreening(String screeningType, String result, 
                                 Duration screeningTime, int matchCount, String riskLevel) {
        getCounter("compliance.aml.screenings",
                "type", screeningType,
                "result", result,
                "risk_level", riskLevel)
            .increment();
        
        getTimer("compliance.aml.screening.time",
                "type", screeningType)
            .record(screeningTime);
        
        // Record match count distribution
        recordScreeningMatches(screeningType, matchCount);
    }

    /**
     * Record KYC verification metrics
     */
    public void recordKYCVerification(String verificationType, String status, 
                                    Duration verificationTime, String provider, boolean manual) {
        getCounter("compliance.kyc.verifications",
                "type", verificationType,
                "status", status,
                "provider", provider,
                "manual", String.valueOf(manual))
            .increment();
        
        getTimer("compliance.kyc.verification.time",
                "type", verificationType,
                "provider", provider)
            .record(verificationTime);
    }

    // =====================================================
    // SYSTEM PERFORMANCE METRICS
    // =====================================================

    /**
     * Record API endpoint performance
     */
    public void recordAPICall(String endpoint, String method, int responseCode, 
                            Duration responseTime, String userAgent) {
        getCounter("api.requests",
                "endpoint", endpoint,
                "method", method,
                "response_code", String.valueOf(responseCode),
                "user_agent", userAgent)
            .increment();
        
        getTimer("api.response.time",
                "endpoint", endpoint,
                "method", method)
            .record(responseTime);
    }

    /**
     * Record database operation metrics
     */
    public void recordDatabaseOperation(String operationType, String table, 
                                      Duration operationTime, boolean successful, int recordCount) {
        getCounter("database.operations",
                "operation", operationType,
                "table", table,
                "success", String.valueOf(successful))
            .increment();
        
        getTimer("database.operation.time",
                "operation", operationType,
                "table", table)
            .record(operationTime);
        
        if (recordCount > 0) {
            recordDatabaseRecordCount(operationType, table, recordCount);
        }
    }

    /**
     * Record cache performance
     */
    public void recordCacheOperation(String cacheType, String operation, 
                                   boolean hit, Duration operationTime) {
        getCounter("cache.operations",
                "cache", cacheType,
                "operation", operation,
                "hit", String.valueOf(hit))
            .increment();
        
        getTimer("cache.operation.time",
                "cache", cacheType,
                "operation", operation)
            .record(operationTime);
    }

    // =====================================================
    // BUSINESS METRICS
    // =====================================================

    /**
     * Record revenue metrics
     */
    public void recordRevenue(String revenueType, String currency, BigDecimal amount, 
                            String source, LocalDateTime timestamp) {
        recordRevenueAmount(revenueType, currency, amount);
        
        getCounter("business.revenue.events",
                "type", revenueType,
                "currency", currency,
                "source", source)
            .increment();
    }

    /**
     * Record user engagement metrics
     */
    public void recordUserEngagement(String engagementType, String userId, 
                                   String feature, Duration sessionDuration) {
        getCounter("users.engagement",
                "type", engagementType,
                "feature", feature)
            .increment();
        
        if (sessionDuration != null) {
            getTimer("users.session.duration",
                    "feature", feature)
                .record(sessionDuration);
        }
    }

    /**
     * Record customer acquisition metrics
     */
    public void recordCustomerAcquisition(String acquisitionChannel, String customerType, 
                                        String region, BigDecimal acquisitionCost) {
        getCounter("customers.acquired",
                "channel", acquisitionChannel,
                "type", customerType,
                "region", region)
            .increment();
        
        if (acquisitionCost != null) {
            recordAcquisitionCost(acquisitionChannel, customerType, acquisitionCost);
        }
    }

    // =====================================================
    // INTEGRATION SERVICE METRICS
    // =====================================================

    /**
     * Record external service integration performance
     */
    public void recordExternalServiceCall(String serviceName, String operation, 
                                        Duration responseTime, boolean successful, 
                                        int responseCode, String errorType) {
        getCounter("integrations.external.calls",
                "service", serviceName,
                "operation", operation,
                "success", String.valueOf(successful),
                "response_code", String.valueOf(responseCode),
                "error_type", errorType != null ? errorType : "none")
            .increment();
        
        getTimer("integrations.external.response.time",
                "service", serviceName,
                "operation", operation)
            .record(responseTime);
    }

    /**
     * Record message queue metrics
     */
    public void recordMessageQueueOperation(String queueName, String operation, 
                                          boolean successful, Duration processingTime, 
                                          int messageCount, long queueDepth) {
        getCounter("messaging.operations",
                "queue", queueName,
                "operation", operation,
                "success", String.valueOf(successful))
            .increment(messageCount);
        
        getTimer("messaging.processing.time",
                "queue", queueName,
                "operation", operation)
            .record(processingTime);
        
        // Record current queue depth
        recordQueueDepth(queueName, queueDepth);
    }

    // =====================================================
    // UTILITY METHODS
    // =====================================================

    /**
     * Get or create a counter with tags
     */
    private Counter getCounter(String name, String... tags) {
        String key = buildMetricKey(name, tags);
        return counters.computeIfAbsent(key, k -> 
            Counter.builder(name)
                .tags(tags)
                .register(meterRegistry));
    }

    /**
     * Get or create a timer with tags
     */
    private Timer getTimer(String name, String... tags) {
        String key = buildMetricKey(name, tags);
        return timers.computeIfAbsent(key, k ->
            Timer.builder(name)
                .tags(tags)
                .register(meterRegistry));
    }

    /**
     * Record distribution summary for amounts
     */
    private void recordTransactionAmount(String transactionType, String currency, BigDecimal amount) {
        DistributionSummary.builder("transactions.amount")
            .tags("type", transactionType, "currency", currency)
            .register(meterRegistry)
            .record(amount.doubleValue());
    }

    private void recordPaymentAmount(String paymentMethod, String currency, BigDecimal amount) {
        DistributionSummary.builder("payments.amount")
            .tags("method", paymentMethod, "currency", currency)
            .register(meterRegistry)
            .record(amount.doubleValue());
    }

    private void recordSettlementAmount(String currency, BigDecimal amount) {
        DistributionSummary.builder("settlements.amount")
            .tags("currency", currency)
            .register(meterRegistry)
            .record(amount.doubleValue());
    }

    private void recordBalanceChange(String accountType, String currency, BigDecimal change) {
        DistributionSummary.builder("accounts.balance.change")
            .tags("account_type", accountType, "currency", currency)
            .register(meterRegistry)
            .record(change.doubleValue());
    }

    private void recordRiskScore(String detectionType, double riskScore) {
        DistributionSummary.builder("fraud.risk.score")
            .tags("type", detectionType)
            .register(meterRegistry)
            .record(riskScore);
    }

    private void recordScreeningMatches(String screeningType, int matchCount) {
        DistributionSummary.builder("compliance.screening.matches")
            .tags("type", screeningType)
            .register(meterRegistry)
            .record(matchCount);
    }

    private void recordDatabaseRecordCount(String operationType, String table, int recordCount) {
        DistributionSummary.builder("database.record.count")
            .tags("operation", operationType, "table", table)
            .register(meterRegistry)
            .record(recordCount);
    }

    private void recordRevenueAmount(String revenueType, String currency, BigDecimal amount) {
        DistributionSummary.builder("business.revenue.amount")
            .tags("type", revenueType, "currency", currency)
            .register(meterRegistry)
            .record(amount.doubleValue());
    }

    private void recordAcquisitionCost(String channel, String customerType, BigDecimal cost) {
        DistributionSummary.builder("customers.acquisition.cost")
            .tags("channel", channel, "type", customerType)
            .register(meterRegistry)
            .record(cost.doubleValue());
    }

    private void recordQueueDepth(String queueName, long depth) {
        AtomicLong atomicDepth = atomicValues.computeIfAbsent("queue.depth." + queueName, 
            k -> {
                AtomicLong atomic = new AtomicLong(0);
                Gauge.builder("messaging.queue.depth", atomic, AtomicLong::doubleValue)
                    .tags("queue", queueName)
                    .register(meterRegistry);
                return atomic;
            });
        atomicDepth.set(depth);
    }

    /**
     * Build metric key for caching
     */
    private String buildMetricKey(String name, String... tags) {
        StringBuilder key = new StringBuilder(name);
        for (int i = 0; i < tags.length; i += 2) {
            if (i + 1 < tags.length) {
                key.append(":").append(tags[i]).append("=").append(tags[i + 1]);
            }
        }
        return key.toString();
    }

    /**
     * Health check for metrics service
     */
    public boolean isHealthy() {
        try {
            // Test basic metric creation
            getCounter("health.check.test").increment();
            return true;
        } catch (Exception e) {
            log.error("Metrics service health check failed", e);
            return false;
        }
    }

    /**
     * Get current metrics summary
     */
    public MetricsSummary getMetricsSummary() {
        return MetricsSummary.builder()
            .countersRegistered(counters.size())
            .timersRegistered(timers.size())
            .gaugesRegistered(gauges.size())
            .enabled(true)
            .lastUpdated(java.time.Instant.now())
            .build();
    }
}