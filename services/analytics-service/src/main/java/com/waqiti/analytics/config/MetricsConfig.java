package com.waqiti.analytics.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Metrics Configuration for Analytics Service
 *
 * Configures custom business and technical metrics for monitoring.
 * Metrics are exposed via Prometheus endpoint (/actuator/prometheus).
 *
 * Metric Categories:
 * 1. Business Metrics - Transaction processing, analytics calculations
 * 2. Technical Metrics - Service health, performance, errors
 * 3. DLQ Metrics - Dead letter queue processing
 * 4. ML Metrics - Model predictions, training
 * 5. Integration Metrics - External service calls
 *
 * Alert Thresholds (configured in Prometheus/AlertManager):
 * - DLQ message count > 1000 - CRITICAL
 * - Error rate > 5% - WARNING
 * - Processing lag > 5 minutes - WARNING
 * - Circuit breaker open - WARNING
 *
 * @author Waqiti Platform Team
 * @version 1.0.0-PRODUCTION
 * @since 2025-11-10
 */
@Configuration
@Slf4j
public class MetricsConfig {

    /**
     * Analytics Event Processing Metrics
     */
    @Bean
    public Counter analyticsEventsProcessedCounter(MeterRegistry registry) {
        return Counter.builder("analytics.events.processed")
            .description("Total number of analytics events processed")
            .tag("service", "analytics")
            .register(registry);
    }

    @Bean
    public Counter analyticsEventsFailedCounter(MeterRegistry registry) {
        return Counter.builder("analytics.events.failed")
            .description("Total number of analytics events that failed processing")
            .tag("service", "analytics")
            .register(registry);
    }

    @Bean
    public Timer analyticsEventProcessingTimer(MeterRegistry registry) {
        return Timer.builder("analytics.events.processing.time")
            .description("Time taken to process analytics events")
            .tag("service", "analytics")
            .publishPercentiles(0.5, 0.95, 0.99)
            .minimumExpectedValue(java.time.Duration.ofMillis(10))
            .maximumExpectedValue(java.time.Duration.ofSeconds(30))
            .register(registry);
    }

    /**
     * DLQ Processing Metrics
     */
    @Bean
    public Counter dlqMessagesReceivedCounter(MeterRegistry registry) {
        return Counter.builder("analytics.dlq.messages.received")
            .description("Total DLQ messages received")
            .tag("service", "analytics")
            .register(registry);
    }

    @Bean
    public Counter dlqMessagesRetried Counter(MeterRegistry registry) {
        return Counter.builder("analytics.dlq.messages.retried")
            .description("Total DLQ messages retried")
            .tag("service", "analytics")
            .register(registry);
    }

    @Bean
    public Counter dlqMessagesPermanentFailureCounter(MeterRegistry registry) {
        return Counter.builder("analytics.dlq.messages.permanent_failure")
            .description("Total DLQ messages with permanent failure")
            .tag("service", "analytics")
            .register(registry);
    }

    /**
     * Database Query Metrics
     */
    @Bean
    public Timer databaseQueryTimer(MeterRegistry registry) {
        return Timer.builder("analytics.database.query.time")
            .description("Database query execution time")
            .tag("service", "analytics")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
    }

    @Bean
    public Counter databaseQueryErrorCounter(MeterRegistry registry) {
        return Counter.builder("analytics.database.query.errors")
            .description("Database query errors")
            .tag("service", "analytics")
            .register(registry);
    }

    /**
     * ML Model Metrics
     */
    @Bean
    public Counter mlPredictionsCounter(MeterRegistry registry) {
        return Counter.builder("analytics.ml.predictions")
            .description("Total ML model predictions")
            .tag("service", "analytics")
            .register(registry);
    }

    @Bean
    public Timer mlPredictionTimer(MeterRegistry registry) {
        return Timer.builder("analytics.ml.prediction.time")
            .description("ML model prediction time")
            .tag("service", "analytics")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
    }

    @Bean
    public Counter mlModelTrainingCounter(MeterRegistry registry) {
        return Counter.builder("analytics.ml.training")
            .description("ML model training executions")
            .tag("service", "analytics")
            .register(registry);
    }

    /**
     * External Service Integration Metrics
     */
    @Bean
    public Timer paymentServiceCallTimer(MeterRegistry registry) {
        return Timer.builder("analytics.integration.payment_service.call_time")
            .description("Payment service API call time")
            .tag("service", "analytics")
            .tag("target", "payment-service")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
    }

    @Bean
    public Counter paymentServiceErrorCounter(MeterRegistry registry) {
        return Counter.builder("analytics.integration.payment_service.errors")
            .description("Payment service call errors")
            .tag("service", "analytics")
            .tag("target", "payment-service")
            .register(registry);
    }

    @Bean
    public Timer userServiceCallTimer(MeterRegistry registry) {
        return Timer.builder("analytics.integration.user_service.call_time")
            .description("User service API call time")
            .tag("service", "analytics")
            .tag("target", "user-service")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
    }

    /**
     * Circuit Breaker Metrics (automatically registered by Resilience4j)
     * - resilience4j.circuitbreaker.calls
     * - resilience4j.circuitbreaker.state
     * - resilience4j.circuitbreaker.failure.rate
     */

    /**
     * Cache Metrics
     */
    @Bean
    public Counter cacheHitCounter(MeterRegistry registry) {
        return Counter.builder("analytics.cache.hits")
            .description("Cache hits")
            .tag("service", "analytics")
            .register(registry);
    }

    @Bean
    public Counter cacheMissCounter(MeterRegistry registry) {
        return Counter.builder("analytics.cache.misses")
            .description("Cache misses")
            .tag("service", "analytics")
            .register(registry);
    }

    /**
     * Business Metrics
     */
    @Bean
    public Counter transactionAnalyticsCalculatedCounter(MeterRegistry registry) {
        return Counter.builder("analytics.business.transaction_analytics_calculated")
            .description("Number of transaction analytics calculated")
            .tag("service", "analytics")
            .register(registry);
    }

    @Bean
    public Counter userMetricsUpdatedCounter(MeterRegistry registry) {
        return Counter.builder("analytics.business.user_metrics_updated")
            .description("Number of user metrics updated")
            .tag("service", "analytics")
            .register(registry);
    }

    @Bean
    public Counter fraudDetectionRunCounter(MeterRegistry registry) {
        return Counter.builder("analytics.business.fraud_detections")
            .description("Number of fraud detections executed")
            .tag("service", "analytics")
            .register(registry);
    }

    @Bean
    public Counter reportsGeneratedCounter(MeterRegistry registry) {
        return Counter.builder("analytics.business.reports_generated")
            .description("Number of reports generated")
            .tag("service", "analytics")
            .register(registry);
    }
}
