package com.waqiti.common.metrics.service;

import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Interface for production-grade metrics collection and reporting.
 * Provides standardized methods for business and technical metrics
 * across all microservices in the Waqiti platform.
 */
public interface MetricsService {

    /**
     * Increment counter by 1 with tags
     */
    void incrementCounter(String name, Map<String, String> tags);

    /**
     * Increment counter by specific amount with tags
     */
    void incrementCounter(String name, double amount, Map<String, String> tags);

    /**
     * Record timer with millisecond duration and tags
     */
    void recordTimer(String name, long durationMs, Map<String, String> tags);

    /**
     * Record timer with Duration and tags
     */
    void recordTimer(String name, Duration duration, Map<String, String> tags);

    /**
     * Record distribution summary (amounts, sizes, etc.)
     */
    void recordDistribution(String name, double value, Map<String, String> tags);

    /**
     * Register gauge with value supplier
     */
    void registerGauge(String name, Supplier<Number> valueSupplier, Map<String, String> tags);

    /**
     * Record comprehensive transaction metrics
     */
    void recordTransactionMetrics(TransactionMetrics transaction);

    /**
     * Record KYC verification metrics
     */
    void recordKycMetrics(KycMetrics kyc);

    /**
     * Record fraud detection metrics
     */
    void recordFraudMetrics(FraudMetrics fraud);

    /**
     * Record API endpoint performance metrics
     */
    void recordEndpointMetrics(String endpoint, String method, int statusCode, Duration duration);

    /**
     * Record external service call metrics
     */
    void recordExternalServiceMetrics(ExternalServiceMetrics serviceMetrics);

    /**
     * Record failed operation with reason
     */
    void recordFailedOperation(String operation, String reason);

    /**
     * Record successful operation with timing
     */
    void recordSuccessfulOperation(String operation, Duration duration);

    /**
     * Get current metrics summary
     */
    MetricsSummary getMetricsSummary();
}