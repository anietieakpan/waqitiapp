package com.waqiti.saga.step;

import com.waqiti.saga.client.NotificationServiceClient;
import com.waqiti.saga.domain.SagaExecution;
import com.waqiti.saga.dto.CancelNotificationRequest;
import com.waqiti.saga.dto.NotificationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Compensation step: Cancels sent notifications
 * 
 * This step attempts to cancel or mark as invalid the notifications
 * that were sent if the saga fails after notifications were sent.
 */
@Component
public class CancelNotificationStep implements SagaStep {
    
    private static final Logger logger = LoggerFactory.getLogger(CancelNotificationStep.class);
    
    private final NotificationServiceClient notificationServiceClient;
    
    public CancelNotificationStep(NotificationServiceClient notificationServiceClient) {
        this.notificationServiceClient = notificationServiceClient;
    }
    
    @Override
    public String getStepName() {
        return "CancelNotification";
    }
    
    @Override
    public StepExecutionResult execute(SagaExecution execution) {
        try {
            logger.info("Cancelling notifications for saga: {}", execution.getSagaId());
            
            // Get notification details from context
            String senderNotificationId = (String) execution.getContextValue("senderNotificationId");
            String receiverNotificationId = (String) execution.getContextValue("receiverNotificationId");
            String fromUserId = (String) execution.getContextValue("fromUserId");
            String toUserId = (String) execution.getContextValue("toUserId");
            
            Map<String, Object> stepData = new HashMap<>();
            boolean anyCancelled = false;
            
            // Cancel sender notification
            if (senderNotificationId != null) {
                try {
                    CancelNotificationRequest cancelRequest = CancelNotificationRequest.builder()
                        .notificationId(senderNotificationId)
                        .userId(fromUserId)
                        .reason("Transaction failed - saga compensation")
                        .sagaId(execution.getSagaId())
                        .build();
                    
                    NotificationResponse response = notificationServiceClient.cancelNotification(cancelRequest);
                    
                    if (response.isSuccess()) {
                        stepData.put("senderNotificationCancelled", true);
                        anyCancelled = true;
                        logger.debug("Sender notification cancelled for saga: {}", execution.getSagaId());
                    } else {
                        stepData.put("senderNotificationCancelled", false);
                        stepData.put("senderCancellationError", response.getErrorMessage());
                        logger.warn("Failed to cancel sender notification for saga: {} - {}", 
                                   execution.getSagaId(), response.getErrorMessage());
                    }
                    
                } catch (Exception e) {
                    stepData.put("senderNotificationCancelled", false);
                    stepData.put("senderCancellationError", e.getMessage());
                    logger.warn("Error cancelling sender notification for saga: {}", execution.getSagaId(), e);
                }
            }
            
            // Cancel receiver notification
            if (receiverNotificationId != null) {
                try {
                    CancelNotificationRequest cancelRequest = CancelNotificationRequest.builder()
                        .notificationId(receiverNotificationId)
                        .userId(toUserId)
                        .reason("Transaction failed - saga compensation")
                        .sagaId(execution.getSagaId())
                        .build();
                    
                    NotificationResponse response = notificationServiceClient.cancelNotification(cancelRequest);
                    
                    if (response.isSuccess()) {
                        stepData.put("receiverNotificationCancelled", true);
                        anyCancelled = true;
                        logger.debug("Receiver notification cancelled for saga: {}", execution.getSagaId());
                    } else {
                        stepData.put("receiverNotificationCancelled", false);
                        stepData.put("receiverCancellationError", response.getErrorMessage());
                        logger.warn("Failed to cancel receiver notification for saga: {} - {}", 
                                   execution.getSagaId(), response.getErrorMessage());
                    }
                    
                } catch (Exception e) {
                    stepData.put("receiverNotificationCancelled", false);
                    stepData.put("receiverCancellationError", e.getMessage());
                    logger.warn("Error cancelling receiver notification for saga: {}", execution.getSagaId(), e);
                }
            }
            
            // Send failure notifications to inform users about the failed transaction
            sendFailureNotifications(fromUserId, toUserId, execution, stepData);
            
            stepData.put("notificationsCancelled", anyCancelled);
            
            logger.info("Notification cancellation completed for saga: {}", execution.getSagaId());
            return StepExecutionResult.success(stepData);
            
        } catch (Exception e) {
            logger.error("Failed to cancel notifications for saga: {}", execution.getSagaId(), e);
            // Notification cancellation is not critical for saga compensation
            Map<String, Object> stepData = new HashMap<>();
            stepData.put("notificationsCancelled", false);
            stepData.put("cancellationError", e.getMessage());
            
            return StepExecutionResult.success(stepData);
        }
    }
    
    private void sendFailureNotifications(String fromUserId, String toUserId, SagaExecution execution, 
                                        Map<String, Object> stepData) {
        try {
            // Send failure notification to sender
            if (fromUserId != null) {
                com.waqiti.saga.dto.NotificationRequest failureNotification = 
                    com.waqiti.saga.dto.NotificationRequest.builder()
                    .userId(fromUserId)
                    .type("TRANSFER_FAILED")
                    .title("Transfer Failed")
                    .message("Your money transfer could not be completed due to a system error. " +
                            "Any charges will be reversed within 24 hours.")
                    .priority("HIGH")
                    .channels(new String[]{"PUSH", "EMAIL"})
                    .metadata(Map.of(
                        "sagaId", execution.getSagaId(),
                        "transactionType", "P2P_TRANSFER_FAILED",
                        "reason", "System error during processing"
                    ))
                    .scheduledAt(java.time.LocalDateTime.now())
                    .expiresAt(java.time.LocalDateTime.now().plusDays(7))
                    .build();
                
                NotificationResponse response = notificationServiceClient.sendNotification(failureNotification);
                stepData.put("failureNotificationSent", response.isSuccess());
            }
            
        } catch (Exception e) {
            logger.warn("Failed to send failure notifications for saga: {}", execution.getSagaId(), e);
            stepData.put("failureNotificationSent", false);
        }
    }
}