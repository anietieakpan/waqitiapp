package com.waqiti.saga.step;

import com.waqiti.saga.client.AnalyticsServiceClient;
import com.waqiti.saga.domain.SagaExecution;
import com.waqiti.saga.dto.AnalyticsEventRequest;
import com.waqiti.saga.dto.AnalyticsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Compensation step: Reverses analytics updates
 * 
 * This step records a compensating analytics event to reverse
 * the metrics that were recorded for the failed transaction.
 */
@Component
public class ReverseAnalyticsStep implements SagaStep {
    
    private static final Logger logger = LoggerFactory.getLogger(ReverseAnalyticsStep.class);
    
    private final AnalyticsServiceClient analyticsServiceClient;
    
    public ReverseAnalyticsStep(AnalyticsServiceClient analyticsServiceClient) {
        this.analyticsServiceClient = analyticsServiceClient;
    }
    
    @Override
    public String getStepName() {
        return "ReverseAnalytics";
    }
    
    @Override
    public StepExecutionResult execute(SagaExecution execution) {
        try {
            logger.info("Reversing analytics for saga: {}", execution.getSagaId());
            
            // Get analytics details from context
            String analyticsEventId = (String) execution.getContextValue("analyticsEventId");
            Boolean analyticsRecorded = (Boolean) execution.getContextValue("analyticsRecorded");
            
            if (analyticsEventId == null || !Boolean.TRUE.equals(analyticsRecorded)) {
                logger.debug("No analytics to reverse for saga: {} - skipping", execution.getSagaId());
                return StepExecutionResult.success();
            }
            
            // Get original transaction details
            String fromUserId = (String) execution.getContextValue("fromUserId");
            String toUserId = (String) execution.getContextValue("toUserId");
            BigDecimal amount = (BigDecimal) execution.getContextValue("amount");
            String currency = (String) execution.getContextValue("currency");
            String transactionId = (String) execution.getContextValue("transactionId");
            
            // Record analytics reversal event
            AnalyticsEventRequest reversalEvent = createAnalyticsReversalEvent(
                fromUserId, toUserId, amount, currency, transactionId, 
                analyticsEventId, execution);
            
            AnalyticsResponse response = analyticsServiceClient.recordEvent(reversalEvent);
            
            // Record user activity reversals
            recordUserActivityReversal(fromUserId, "MONEY_SENT_FAILED", amount, currency, transactionId);
            recordUserActivityReversal(toUserId, "MONEY_RECEIVED_CANCELLED", amount, currency, transactionId);
            
            // Store reversal details in context
            Map<String, Object> stepData = new HashMap<>();
            stepData.put("analyticsReversalEventId", response.getEventId());
            stepData.put("analyticsReversed", response.isSuccess());
            stepData.put("analyticsReversalTimestamp", LocalDateTime.now());
            stepData.put("originalAnalyticsEventId", analyticsEventId);
            
            if (response.isSuccess()) {
                logger.info("Analytics reversed successfully for saga: {}", execution.getSagaId());
            } else {
                logger.warn("Failed to reverse analytics for saga: {} - {}", 
                           execution.getSagaId(), response.getErrorMessage());
                // Analytics reversal failure is not critical
            }
            
            return StepExecutionResult.success(stepData);
            
        } catch (Exception e) {
            logger.error("Failed to reverse analytics for saga: {}", execution.getSagaId(), e);
            // Analytics errors are not critical for saga compensation
            Map<String, Object> stepData = new HashMap<>();
            stepData.put("analyticsReversed", false);
            stepData.put("analyticsReversalError", e.getMessage());
            
            return StepExecutionResult.success(stepData);
        }
    }
    
    private AnalyticsEventRequest createAnalyticsReversalEvent(String fromUserId, String toUserId,
                                                             BigDecimal amount, String currency,
                                                             String transactionId, String originalEventId,
                                                             SagaExecution execution) {
        return AnalyticsEventRequest.builder()
            .eventType("P2P_TRANSFER_FAILED")
            .userId(fromUserId)
            .sessionId(execution.getSagaId())
            .timestamp(LocalDateTime.now())
            .properties(Map.of(
                "transactionId", transactionId,
                "fromUserId", fromUserId,
                "toUserId", toUserId,
                "amount", amount.toString(),
                "currency", currency,
                "sagaId", execution.getSagaId(),
                "originalAnalyticsEventId", originalEventId,
                "transactionType", "P2P_TRANSFER_FAILED",
                "failureReason", execution.getErrorMessage() != null ? execution.getErrorMessage() : "Unknown",
                "failureStep", execution.getCurrentStep(),
                "platform", "mobile_app",
                "version", "1.0"
            ))
            .metrics(Map.of(
                "failed_transaction_amount", amount.doubleValue(),
                "processing_time_before_failure", calculateProcessingTime(execution),
                "saga_steps_completed_before_failure", execution.getCurrentStepIndex(),
                "compensation_metric", 1.0 // To track compensation events
            ))
            .tags(new String[]{"p2p-transfer", "failed", "compensated", currency.toLowerCase()})
            .build();
    }
    
    private void recordUserActivityReversal(String userId, String activityType, BigDecimal amount, 
                                           String currency, String transactionId) {
        try {
            AnalyticsEventRequest userActivity = AnalyticsEventRequest.builder()
                .eventType("USER_ACTIVITY_REVERSAL")
                .userId(userId)
                .timestamp(LocalDateTime.now())
                .properties(Map.of(
                    "activityType", activityType,
                    "transactionId", transactionId,
                    "amount", amount.toString(),
                    "currency", currency,
                    "reversalReason", "Transaction failed - saga compensation"
                ))
                .tags(new String[]{"user-activity", "reversal", activityType.toLowerCase()})
                .build();
            
            analyticsServiceClient.recordEvent(userActivity);
            
        } catch (Exception e) {
            logger.warn("Failed to record user activity reversal for user: {} - {}", userId, e.getMessage());
        }
    }
    
    private double calculateProcessingTime(SagaExecution execution) {
        if (execution.getStartedAt() == null) {
            return 0.0;
        }
        
        LocalDateTime now = LocalDateTime.now();
        return java.time.Duration.between(execution.getStartedAt(), now).toMillis() / 1000.0;
    }
}