package com.waqiti.payment.client;

import com.waqiti.payment.dto.analytics.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback implementation for Analytics Service Client
 *
 * Provides graceful degradation when analytics-service is unavailable:
 * - Returns success response without blocking payment flow
 * - Logs the failure for async processing
 * - Analytics can be rebuilt from payment transaction logs
 * - Non-critical failure - doesn't impact user experience
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-09-27
 */
@Component
@Slf4j
public class AnalyticsServiceClientFallback implements AnalyticsServiceClient {

    @Override
    public RecordPaymentCompletionResponse recordPaymentCompletion(RecordPaymentCompletionRequest request) {
        log.warn("Analytics service unavailable - using fallback for payment: {}. " +
                "Analytics will be rebuilt from transaction logs.", request.getPaymentId());

        // Analytics is non-critical - allow payment flow to continue
        // Analytics will be rebuilt through batch ETL jobs
        return RecordPaymentCompletionResponse.builder()
            .paymentId(request.getPaymentId())
            .recorded(false)
            .message("Analytics service temporarily unavailable - will be processed asynchronously")
            .requiresAsyncProcessing(true)
            .build();
    }

    @Override
    public void recordGroupPaymentAnalytics(RecordGroupPaymentAnalyticsRequest request) {
        log.warn("Analytics service unavailable - group payment analytics recording deferred. " +
                "Group payment ID: {}", request != null ? request.getGroupPaymentId() : "unknown");
        // Fire-and-forget - failure is acceptable for analytics
    }

    @Override
    public void recordRoutingAnalytics(RecordRoutingAnalyticsRequest request) {
        log.warn("Analytics service unavailable - routing analytics recording deferred.");
        // Fire-and-forget - failure is acceptable for analytics
    }

    @Override
    public void recordDisputeAnalytics(RecordDisputeAnalyticsRequest request) {
        log.warn("Analytics service unavailable - dispute analytics recording deferred.");
        // Fire-and-forget - failure is acceptable for analytics
    }

    @Override
    public void recordReversalFailureAnalytics(RecordReversalFailureAnalyticsRequest request) {
        log.warn("Analytics service unavailable - reversal failure analytics recording deferred.");
        // Fire-and-forget - failure is acceptable for analytics
    }

    @Override
    public void recordRefundAnalytics(RecordRefundAnalyticsRequest request) {
        log.warn("Analytics service unavailable - refund analytics recording deferred.");
        // Fire-and-forget - failure is acceptable for analytics
    }

    @Override
    public void recordReconciliationAnalytics(RecordReconciliationAnalyticsRequest request) {
        log.warn("Analytics service unavailable - reconciliation analytics recording deferred.");
        // Fire-and-forget - failure is acceptable for analytics
    }
}