package com.waqiti.payment.client;

import com.waqiti.payment.dto.analytics.RecordPaymentCompletionRequest;
import com.waqiti.payment.dto.analytics.RecordPaymentCompletionResponse;
import com.waqiti.payment.dto.analytics.RecordGroupPaymentAnalyticsRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import jakarta.validation.Valid;

/**
 * Feign Client for Analytics Service Integration
 * 
 * Handles communication with analytics-service for:
 * - Recording payment completions
 * - Updating transaction metrics
 * - Capturing user behavior data
 * - Merchant analytics
 * - Real-time dashboards
 * 
 * RESILIENCE:
 * - Circuit breaker protection
 * - Automatic retries
 * - Fallback to async processing
 * - Non-blocking failures (analytics is non-critical)
 * 
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-09-27
 */
@FeignClient(
    name = "analytics-service",
    path = "/api/v1/analytics",
    fallback = AnalyticsServiceClientFallback.class
)
public interface AnalyticsServiceClient {
    
    /**
     * Record payment completion for analytics
     * 
     * Captures:
     * - Transaction volume metrics
     * - User spending patterns
     * - Merchant performance data
     * - Payment method preferences
     * - Geographic distribution
     * - Time-based trends
     * 
     * @param request Payment completion analytics request
     * @return Analytics recording response
     */
    @PostMapping("/payments/record-completion")
    @CircuitBreaker(name = "analytics-service")
    @Retry(name = "analytics-service")
    RecordPaymentCompletionResponse recordPaymentCompletion(
        @Valid @RequestBody RecordPaymentCompletionRequest request
    );

    /**
     * Record group payment analytics data
     * 
     * Captures comprehensive analytics for group payments including:
     * - Transaction volume and value metrics
     * - User behavior and engagement patterns
     * - Group payment trends and insights
     * - Participant completion rates
     * - Geographic and demographic data
     * - Performance and business metrics
     * 
     * NON-CRITICAL OPERATION:
     * - Analytics failures do not affect core payment processing
     * - Data can be reconstructed from transaction logs
     * - Supports business intelligence and insights
     * - Enables trend analysis and reporting
     * 
     * @param request Group payment analytics recording request
     * @return Void - fire-and-forget analytics recording
     */
    @PostMapping("/group-payments/record-analytics")
    @CircuitBreaker(name = "analytics-service")
    @Retry(name = "analytics-service")
    void recordGroupPaymentAnalytics(
        @Valid @RequestBody RecordGroupPaymentAnalyticsRequest request
    );
    
    /**
     * Record payment routing analytics
     * 
     * Captures routing optimization analytics including:
     * - Cost savings from intelligent routing
     * - Success rate improvements
     * - Processing time optimizations
     * - Gateway performance comparisons
     * 
     * @param request Routing analytics recording request
     */
    @PostMapping("/routing/record-analytics")
    @CircuitBreaker(name = "analytics-service")
    @Retry(name = "analytics-service")
    void recordRoutingAnalytics(
        @Valid @RequestBody RecordRoutingAnalyticsRequest request
    );
    
    /**
     * Record payment dispute analytics
     * 
     * Captures dispute resolution analytics including:
     * - Dispute outcome patterns
     * - Chargeback trends
     * - Root cause analysis data
     * - Resolution time metrics
     * - Customer/merchant dispute patterns
     * 
     * @param request Dispute analytics recording request
     */
    @PostMapping("/disputes/record-analytics")
    @CircuitBreaker(name = "analytics-service")
    @Retry(name = "analytics-service")
    void recordDisputeAnalytics(
        @Valid @RequestBody RecordDisputeAnalyticsRequest request
    );
    
    /**
     * Record payment reversal failure analytics
     * 
     * Captures reversal failure analytics including:
     * - Failure patterns by gateway and error type
     * - Retry success/failure rates
     * - Root cause analysis data
     * - System reliability metrics
     * - Operations efficiency tracking
     * 
     * @param request Reversal failure analytics recording request
     */
    @PostMapping("/reversals/record-failure-analytics")
    @CircuitBreaker(name = "analytics-service")
    @Retry(name = "analytics-service")
    void recordReversalFailureAnalytics(
        @Valid @RequestBody RecordReversalFailureAnalyticsRequest request
    );
    
    /**
     * Record payment refund analytics
     * 
     * Captures refund lifecycle analytics including:
     * - Refund processing times and SLAs
     * - Refund success/failure rates
     * - Refund reason patterns
     * - Gateway refund performance
     * - Customer refund patterns
     * 
     * @param request Refund analytics recording request
     */
    @PostMapping("/refunds/record-analytics")
    @CircuitBreaker(name = "analytics-service")
    @Retry(name = "analytics-service")
    void recordRefundAnalytics(
        @Valid @RequestBody RecordRefundAnalyticsRequest request
    );
    
    /**
     * Record payment reconciliation analytics
     * 
     * Captures reconciliation performance analytics including:
     * - Reconciliation success/failure rates
     * - Discrepancy patterns and trends
     * - Processing time metrics
     * - Gateway reconciliation performance
     * - Financial accuracy indicators
     * 
     * @param request Reconciliation analytics recording request
     */
    @PostMapping("/reconciliations/record-analytics")
    @CircuitBreaker(name = "analytics-service")
    @Retry(name = "analytics-service")
    void recordReconciliationAnalytics(
        @Valid @RequestBody RecordReconciliationAnalyticsRequest request
    );
}