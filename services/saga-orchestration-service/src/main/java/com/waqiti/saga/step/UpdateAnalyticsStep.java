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
 * Updates analytics and metrics for the transfer
 * 
 * This step records transaction metrics and analytics data.
 */
@Component
public class UpdateAnalyticsStep implements SagaStep {
    
    private static final Logger logger = LoggerFactory.getLogger(UpdateAnalyticsStep.class);
    
    private final AnalyticsServiceClient analyticsServiceClient;
    
    public UpdateAnalyticsStep(AnalyticsServiceClient analyticsServiceClient) {
        this.analyticsServiceClient = analyticsServiceClient;
    }
    
    @Override
    public String getStepName() {
        return "UpdateAnalytics";
    }
    
    @Override
    public boolean isCompensatable() {
        return true;
    }
    
    @Override
    public StepExecutionResult execute(SagaExecution execution) {
        try {
            logger.info("Updating analytics for saga: {}", execution.getSagaId());
            
            // Get transfer details from context
            String fromUserId = (String) execution.getContextValue("fromUserId");
            String toUserId = (String) execution.getContextValue("toUserId");
            BigDecimal amount = (BigDecimal) execution.getContextValue("amount");
            String currency = (String) execution.getContextValue("currency");
            String transactionId = (String) execution.getContextValue("transactionId");
            LocalDateTime debitTimestamp = (LocalDateTime) execution.getContextValue("debitTimestamp");
            LocalDateTime creditTimestamp = (LocalDateTime) execution.getContextValue("creditTimestamp");
            
            // Create analytics event for transaction
            AnalyticsEventRequest analyticsEvent = createTransactionAnalyticsEvent(
                fromUserId, toUserId, amount, currency, transactionId, 
                debitTimestamp, creditTimestamp, execution);
            
            AnalyticsResponse response = analyticsServiceClient.recordEvent(analyticsEvent);
            
            if (!response.isSuccess()) {
                logger.warn("Failed to record analytics for saga: {} - {}", 
                           execution.getSagaId(), response.getErrorMessage());
                // Analytics failure is not critical - continue saga
            }
            
            // Create user activity events
            recordUserActivity(fromUserId, "MONEY_SENT", amount, currency, transactionId);
            recordUserActivity(toUserId, "MONEY_RECEIVED", amount, currency, transactionId);
            
            // Store analytics details in context
            Map<String, Object> stepData = new HashMap<>();
            stepData.put("analyticsEventId", response.getEventId());
            stepData.put("analyticsRecorded", response.isSuccess());
            stepData.put("analyticsTimestamp", LocalDateTime.now());
            
            logger.info("Analytics updated successfully for saga: {}", execution.getSagaId());
            return StepExecutionResult.success(stepData);
            
        } catch (Exception e) {
            logger.error("Failed to update analytics for saga: {}", execution.getSagaId(), e);
            // Analytics errors are not critical - return success but log the error
            Map<String, Object> stepData = new HashMap<>();
            stepData.put("analyticsRecorded", false);
            stepData.put("analyticsError", e.getMessage());
            
            return StepExecutionResult.success(stepData);
        }
    }
    
    private AnalyticsEventRequest createTransactionAnalyticsEvent(String fromUserId, String toUserId,
                                                                BigDecimal amount, String currency,
                                                                String transactionId, 
                                                                LocalDateTime debitTimestamp,
                                                                LocalDateTime creditTimestamp,
                                                                SagaExecution execution) {
        return AnalyticsEventRequest.builder()
            .eventType("P2P_TRANSFER_COMPLETED")
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
                "debitTimestamp", debitTimestamp != null ? debitTimestamp.toString() : "",
                "creditTimestamp", creditTimestamp != null ? creditTimestamp.toString() : "",
                "transactionType", "P2P_TRANSFER",
                "platform", "mobile_app",
                "version", "1.0"
            ))
            .metrics(Map.of(
                "transaction_amount", amount.doubleValue(),
                "processing_time_seconds", calculateProcessingTime(execution),
                "saga_steps_completed", execution.getCurrentStepIndex()
            ))
            .tags(new String[]{"p2p-transfer", "completed", currency.toLowerCase()})
            .build();
    }
    
    private void recordUserActivity(String userId, String activityType, BigDecimal amount, 
                                  String currency, String transactionId) {
        try {
            AnalyticsEventRequest userActivity = AnalyticsEventRequest.builder()
                .eventType("USER_ACTIVITY")
                .userId(userId)
                .timestamp(LocalDateTime.now())
                .properties(Map.of(
                    "activityType", activityType,
                    "transactionId", transactionId,
                    "amount", amount.toString(),
                    "currency", currency
                ))
                .tags(new String[]{"user-activity", activityType.toLowerCase()})
                .build();
            
            analyticsServiceClient.recordEvent(userActivity);
            
        } catch (Exception e) {
            logger.warn("Failed to record user activity for user: {} - {}", userId, e.getMessage());
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