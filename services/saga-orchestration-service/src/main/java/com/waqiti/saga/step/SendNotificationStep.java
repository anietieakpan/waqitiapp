package com.waqiti.saga.step;

import com.waqiti.saga.client.NotificationServiceClient;
import com.waqiti.saga.domain.SagaExecution;
import com.waqiti.saga.dto.NotificationRequest;
import com.waqiti.saga.dto.NotificationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Sends notifications to both users about the transfer
 * 
 * This step notifies both the sender and receiver about the successful transfer.
 */
@Component
public class SendNotificationStep implements SagaStep {
    
    private static final Logger logger = LoggerFactory.getLogger(SendNotificationStep.class);
    
    private final NotificationServiceClient notificationServiceClient;
    
    public SendNotificationStep(NotificationServiceClient notificationServiceClient) {
        this.notificationServiceClient = notificationServiceClient;
    }
    
    @Override
    public String getStepName() {
        return "SendNotification";
    }
    
    @Override
    public boolean isCompensatable() {
        return true;
    }
    
    @Override
    public StepExecutionResult execute(SagaExecution execution) {
        try {
            logger.info("Sending notifications for saga: {}", execution.getSagaId());
            
            // Get transfer details from context
            String fromUserId = (String) execution.getContextValue("fromUserId");
            String toUserId = (String) execution.getContextValue("toUserId");
            BigDecimal amount = (BigDecimal) execution.getContextValue("amount");
            String currency = (String) execution.getContextValue("currency");
            String transactionId = (String) execution.getContextValue("transactionId");
            String creditTransactionId = (String) execution.getContextValue("creditTransactionId");
            String debitTransactionId = (String) execution.getContextValue("debitTransactionId");
            
            Map<String, Object> stepData = new HashMap<>();
            
            // Send notification to sender
            NotificationRequest senderNotification = createSenderNotification(
                fromUserId, toUserId, amount, currency, transactionId, debitTransactionId);
            
            NotificationResponse senderResponse = notificationServiceClient.sendNotification(senderNotification);
            
            if (senderResponse.isSuccess()) {
                stepData.put("senderNotificationId", senderResponse.getNotificationId());
                logger.debug("Sender notification sent for saga: {}", execution.getSagaId());
            } else {
                logger.warn("Failed to send sender notification for saga: {} - {}", 
                           execution.getSagaId(), senderResponse.getErrorMessage());
                // Continue with receiver notification even if sender notification fails
            }
            
            // Send notification to receiver
            NotificationRequest receiverNotification = createReceiverNotification(
                fromUserId, toUserId, amount, currency, transactionId, creditTransactionId);
            
            NotificationResponse receiverResponse = notificationServiceClient.sendNotification(receiverNotification);
            
            if (receiverResponse.isSuccess()) {
                stepData.put("receiverNotificationId", receiverResponse.getNotificationId());
                logger.debug("Receiver notification sent for saga: {}", execution.getSagaId());
            } else {
                logger.warn("Failed to send receiver notification for saga: {} - {}", 
                           execution.getSagaId(), receiverResponse.getErrorMessage());
            }
            
            // Consider step successful if at least one notification was sent
            boolean hasSuccess = senderResponse.isSuccess() || receiverResponse.isSuccess();
            
            if (!hasSuccess) {
                return StepExecutionResult.failure("Failed to send notifications to both users", 
                    "NOTIFICATION_FAILED");
            }
            
            stepData.put("notificationsSent", hasSuccess);
            stepData.put("senderNotificationSuccess", senderResponse.isSuccess());
            stepData.put("receiverNotificationSuccess", receiverResponse.isSuccess());
            
            logger.info("Notifications sent successfully for saga: {}", execution.getSagaId());
            return StepExecutionResult.success(stepData);
            
        } catch (Exception e) {
            logger.error("Failed to send notifications for saga: {}", execution.getSagaId(), e);
            return StepExecutionResult.failure("Notification error: " + e.getMessage(), 
                "NOTIFICATION_ERROR");
        }
    }
    
    private NotificationRequest createSenderNotification(String fromUserId, String toUserId, 
                                                       BigDecimal amount, String currency, 
                                                       String transactionId, String debitTransactionId) {
        return NotificationRequest.builder()
            .userId(fromUserId)
            .type("TRANSFER_SENT")
            .title("Money Sent Successfully")
            .message(String.format("You sent %s %s to user %s", amount, currency, toUserId))
            .priority("NORMAL")
            .channels(new String[]{"PUSH", "EMAIL"})
            .metadata(Map.of(
                "transactionId", transactionId,
                "walletTransactionId", debitTransactionId,
                "amount", amount.toString(),
                "currency", currency,
                "toUserId", toUserId,
                "transactionType", "P2P_TRANSFER_SENT"
            ))
            .scheduledAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusDays(7))
            .build();
    }
    
    private NotificationRequest createReceiverNotification(String fromUserId, String toUserId, 
                                                         BigDecimal amount, String currency, 
                                                         String transactionId, String creditTransactionId) {
        return NotificationRequest.builder()
            .userId(toUserId)
            .type("TRANSFER_RECEIVED")
            .title("Money Received")
            .message(String.format("You received %s %s from user %s", amount, currency, fromUserId))
            .priority("NORMAL")
            .channels(new String[]{"PUSH", "EMAIL"})
            .metadata(Map.of(
                "transactionId", transactionId,
                "walletTransactionId", creditTransactionId,
                "amount", amount.toString(),
                "currency", currency,
                "fromUserId", fromUserId,
                "transactionType", "P2P_TRANSFER_RECEIVED"
            ))
            .scheduledAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusDays(7))
            .build();
    }
}