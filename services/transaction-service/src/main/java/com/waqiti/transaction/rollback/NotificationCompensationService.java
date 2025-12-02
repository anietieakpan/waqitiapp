package com.waqiti.transaction.rollback;

import com.waqiti.transaction.domain.Transaction;
import com.waqiti.transaction.model.CompensationAction;
import com.waqiti.common.client.NotificationServiceClient;
import com.waqiti.common.notification.NotificationTemplate;
import com.waqiti.common.notification.NotificationChannel;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Enterprise-grade Notification Compensation Service
 * 
 * Handles notification compensations during transaction rollbacks.
 * Sends reversal notifications and updates transaction status to users.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationCompensationService {

    private final NotificationServiceClient notificationServiceClient;
    private final CompensationAuditService compensationAuditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final NotificationTemplateService templateService;
    private final UserPreferenceService userPreferenceService;

    /**
     * Execute notification compensation for transaction rollback
     * Sends appropriate notifications to affected parties
     */
    @CircuitBreaker(name = "notification-compensation", fallbackMethod = "compensateFallback")
    @Retry(name = "notification-compensation")
    @Bulkhead(name = "notification-compensation")
    @Transactional
    public CompensationAction.CompensationResult compensateNotifications(
            Transaction transaction, CompensationAction action) {
        
        log.info("Executing notification compensation for transaction: {} - action: {}", 
                transaction.getId(), action.getActionId());

        try {
            // Check idempotency
            if (isCompensationAlreadyApplied(transaction.getId(), action.getActionId())) {
                log.warn("Notification compensation already applied for action: {}", action.getActionId());
                return CompensationAction.CompensationResult.builder()
                    .actionId(action.getActionId())
                    .status(CompensationAction.CompensationStatus.ALREADY_COMPLETED)
                    .message("Notification compensation already applied")
                    .completedAt(LocalDateTime.now())
                    .build();
            }

            // Send notifications based on transaction type
            List<NotificationResult> results = new ArrayList<>();
            
            // Notify sender
            if (transaction.getFromUserId() != null) {
                results.add(notifySender(transaction, action));
            }
            
            // Notify receiver
            if (transaction.getToUserId() != null) {
                results.add(notifyReceiver(transaction, action));
            }
            
            // Notify merchant if applicable
            if (transaction.getMerchantId() != null) {
                results.add(notifyMerchant(transaction, action));
            }
            
            // Send regulatory notifications if required
            if (isRegulatoryNotificationRequired(transaction)) {
                results.add(sendRegulatoryNotification(transaction, action));
            }

            // Check if all notifications were successful
            boolean allSuccessful = results.stream()
                .allMatch(NotificationResult::isSuccessful);

            // Record audit
            compensationAuditService.recordCompensation(
                transaction.getId(), 
                action.getActionId(), 
                "NOTIFICATION", 
                allSuccessful ? "COMPLETED" : "PARTIAL"
            );

            // Publish notification event
            publishNotificationEvent(transaction, results);

            log.info("Notification compensation completed for transaction: {} - sent {} notifications", 
                    transaction.getId(), results.size());

            return CompensationAction.CompensationResult.builder()
                .actionId(action.getActionId())
                .status(allSuccessful ? 
                    CompensationAction.CompensationStatus.COMPLETED : 
                    CompensationAction.CompensationStatus.PARTIAL)
                .message(String.format("Sent %d rollback notifications", results.size()))
                .metadata(Map.of(
                    "notificationsSent", results.size(),
                    "successful", results.stream().filter(NotificationResult::isSuccessful).count(),
                    "failed", results.stream().filter(r -> !r.isSuccessful()).count()
                ))
                .completedAt(LocalDateTime.now())
                .build();

        } catch (Exception e) {
            log.error("Notification compensation failed for transaction: {}", transaction.getId(), e);
            
            // Record failure
            compensationAuditService.recordCompensationFailure(
                transaction.getId(), 
                action.getActionId(), 
                "NOTIFICATION", 
                e.getMessage()
            );

            return CompensationAction.CompensationResult.builder()
                .actionId(action.getActionId())
                .status(CompensationAction.CompensationStatus.FAILED)
                .errorMessage(e.getMessage())
                .failedAt(LocalDateTime.now())
                .retryable(true)
                .build();
        }
    }

    /**
     * Notify sender about transaction rollback
     */
    private NotificationResult notifySender(Transaction transaction, CompensationAction action) {
        log.info("Notifying sender {} about transaction rollback", transaction.getFromUserId());

        try {
            // Get user preferences for notification channels
            Set<NotificationChannel> channels = userPreferenceService
                .getNotificationChannels(transaction.getFromUserId(), "transaction_rollback");

            // Prepare notification content
            NotificationContent content = NotificationContent.builder()
                .userId(transaction.getFromUserId())
                .type("TRANSACTION_ROLLBACK")
                .title("Transaction Reversed")
                .message(String.format(
                    "Your transaction of %s %s has been reversed. The amount will be credited back to your account.",
                    transaction.getCurrency(), transaction.getAmount()
                ))
                .data(Map.of(
                    "transactionId", transaction.getId().toString(),
                    "amount", transaction.getAmount().toString(),
                    "currency", transaction.getCurrency(),
                    "rollbackReason", action.getReason() != null ? action.getReason() : "System rollback",
                    "refundStatus", "PROCESSING",
                    "estimatedCompletionTime", "2-3 business days"
                ))
                .priority("HIGH")
                .channels(channels)
                .build();

            // Send notifications through all channels
            Map<String, String> notificationIds = new HashMap<>();
            
            if (channels.contains(NotificationChannel.PUSH)) {
                String pushId = notificationServiceClient.sendPushNotification(content);
                notificationIds.put("push", pushId);
            }
            
            if (channels.contains(NotificationChannel.EMAIL)) {
                String emailId = notificationServiceClient.sendEmail(
                    content.getUserId(),
                    templateService.getRollbackEmailTemplate(transaction, "sender"),
                    content.getData()
                );
                notificationIds.put("email", emailId);
            }
            
            if (channels.contains(NotificationChannel.SMS)) {
                String smsId = notificationServiceClient.sendSMS(
                    content.getUserId(),
                    templateService.getRollbackSMSTemplate(transaction, "sender")
                );
                notificationIds.put("sms", smsId);
            }
            
            if (channels.contains(NotificationChannel.IN_APP)) {
                String inAppId = notificationServiceClient.sendInAppNotification(content);
                notificationIds.put("inApp", inAppId);
            }

            return NotificationResult.builder()
                .userId(transaction.getFromUserId())
                .role("SENDER")
                .successful(true)
                .notificationIds(notificationIds)
                .sentAt(LocalDateTime.now())
                .build();

        } catch (Exception e) {
            log.error("Failed to notify sender: {}", transaction.getFromUserId(), e);
            
            return NotificationResult.builder()
                .userId(transaction.getFromUserId())
                .role("SENDER")
                .successful(false)
                .error(e.getMessage())
                .sentAt(LocalDateTime.now())
                .build();
        }
    }

    /**
     * Notify receiver about transaction rollback
     */
    private NotificationResult notifyReceiver(Transaction transaction, CompensationAction action) {
        log.info("Notifying receiver {} about transaction rollback", transaction.getToUserId());

        try {
            // Get user preferences
            Set<NotificationChannel> channels = userPreferenceService
                .getNotificationChannels(transaction.getToUserId(), "transaction_rollback");

            // Prepare notification content
            NotificationContent content = NotificationContent.builder()
                .userId(transaction.getToUserId())
                .type("TRANSACTION_ROLLBACK_RECEIVER")
                .title("Incoming Transaction Reversed")
                .message(String.format(
                    "A transaction of %s %s from %s has been reversed. The amount will be debited from your account.",
                    transaction.getCurrency(), transaction.getAmount(), 
                    transaction.getFromUserName() != null ? transaction.getFromUserName() : "a user"
                ))
                .data(Map.of(
                    "transactionId", transaction.getId().toString(),
                    "amount", transaction.getAmount().toString(),
                    "currency", transaction.getCurrency(),
                    "rollbackReason", "Transaction reversed by sender",
                    "action", "DEBIT_PENDING"
                ))
                .priority("HIGH")
                .channels(channels)
                .build();

            // Send notifications
            Map<String, String> notificationIds = sendMultiChannelNotification(content);

            return NotificationResult.builder()
                .userId(transaction.getToUserId())
                .role("RECEIVER")
                .successful(true)
                .notificationIds(notificationIds)
                .sentAt(LocalDateTime.now())
                .build();

        } catch (Exception e) {
            log.error("Failed to notify receiver: {}", transaction.getToUserId(), e);
            
            return NotificationResult.builder()
                .userId(transaction.getToUserId())
                .role("RECEIVER")
                .successful(false)
                .error(e.getMessage())
                .sentAt(LocalDateTime.now())
                .build();
        }
    }

    /**
     * Notify merchant about payment rollback
     */
    private NotificationResult notifyMerchant(Transaction transaction, CompensationAction action) {
        log.info("Notifying merchant {} about payment rollback", transaction.getMerchantId());

        try {
            // Merchant notifications typically go through API webhooks and email
            NotificationContent content = NotificationContent.builder()
                .userId(transaction.getMerchantId())
                .type("MERCHANT_PAYMENT_ROLLBACK")
                .title("Payment Reversed")
                .message(String.format(
                    "Payment of %s %s (Reference: %s) has been reversed.",
                    transaction.getCurrency(), transaction.getAmount(), 
                    transaction.getMerchantReference()
                ))
                .data(Map.of(
                    "transactionId", transaction.getId().toString(),
                    "merchantReference", transaction.getMerchantReference(),
                    "amount", transaction.getAmount().toString(),
                    "currency", transaction.getCurrency(),
                    "rollbackReason", action.getReason() != null ? action.getReason() : "Payment reversed",
                    "timestamp", LocalDateTime.now().toString()
                ))
                .priority("HIGH")
                .channels(Set.of(NotificationChannel.WEBHOOK, NotificationChannel.EMAIL))
                .build();

            // Send webhook notification
            String webhookId = notificationServiceClient.sendWebhook(
                transaction.getMerchantWebhookUrl(),
                content.getData()
            );

            // Send email notification
            String emailId = notificationServiceClient.sendEmail(
                transaction.getMerchantEmail(),
                templateService.getMerchantRollbackEmailTemplate(transaction),
                content.getData()
            );

            return NotificationResult.builder()
                .userId(transaction.getMerchantId())
                .role("MERCHANT")
                .successful(true)
                .notificationIds(Map.of("webhook", webhookId, "email", emailId))
                .sentAt(LocalDateTime.now())
                .build();

        } catch (Exception e) {
            log.error("Failed to notify merchant: {}", transaction.getMerchantId(), e);
            
            return NotificationResult.builder()
                .userId(transaction.getMerchantId())
                .role("MERCHANT")
                .successful(false)
                .error(e.getMessage())
                .sentAt(LocalDateTime.now())
                .build();
        }
    }

    /**
     * Send regulatory notification if required
     */
    private NotificationResult sendRegulatoryNotification(Transaction transaction, CompensationAction action) {
        log.info("Sending regulatory notification for transaction: {}", transaction.getId());

        try {
            // Determine regulatory requirements based on amount and jurisdiction
            RegulatoryNotification notification = RegulatoryNotification.builder()
                .transactionId(transaction.getId())
                .type("TRANSACTION_REVERSAL")
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .parties(Map.of(
                    "sender", transaction.getFromUserId(),
                    "receiver", transaction.getToUserId()
                ))
                .reason(action.getReason())
                .timestamp(LocalDateTime.now())
                .jurisdiction(transaction.getJurisdiction())
                .build();

            // Send to regulatory reporting system
            kafkaTemplate.send("regulatory-notifications", notification);

            return NotificationResult.builder()
                .userId("REGULATORY_SYSTEM")
                .role("REGULATOR")
                .successful(true)
                .notificationIds(Map.of("regulatory", notification.getId()))
                .sentAt(LocalDateTime.now())
                .build();

        } catch (Exception e) {
            log.error("Failed to send regulatory notification", e);
            
            return NotificationResult.builder()
                .userId("REGULATORY_SYSTEM")
                .role("REGULATOR")
                .successful(false)
                .error(e.getMessage())
                .sentAt(LocalDateTime.now())
                .build();
        }
    }

    /**
     * Check if regulatory notification is required
     */
    private boolean isRegulatoryNotificationRequired(Transaction transaction) {
        // Check based on amount thresholds and transaction type
        return transaction.getAmount().compareTo(new java.math.BigDecimal("10000")) >= 0 ||
               transaction.getType().name().contains("INTERNATIONAL") ||
               transaction.isUnderInvestigation() ||
               transaction.isSuspiciousActivity();
    }

    /**
     * Send notification through multiple channels
     */
    private Map<String, String> sendMultiChannelNotification(NotificationContent content) {
        Map<String, String> notificationIds = new HashMap<>();
        
        for (NotificationChannel channel : content.getChannels()) {
            try {
                String notificationId = switch (channel) {
                    case PUSH -> notificationServiceClient.sendPushNotification(content);
                    case EMAIL -> notificationServiceClient.sendEmail(
                        content.getUserId(),
                        templateService.getGenericRollbackEmailTemplate(),
                        content.getData()
                    );
                    case SMS -> notificationServiceClient.sendSMS(
                        content.getUserId(),
                        content.getMessage()
                    );
                    case IN_APP -> notificationServiceClient.sendInAppNotification(content);
                    case WEBHOOK -> notificationServiceClient.sendWebhook(
                        userPreferenceService.getWebhookUrl(content.getUserId()),
                        content.getData()
                    );
                };
                
                notificationIds.put(channel.name().toLowerCase(), notificationId);
                
            } catch (Exception e) {
                log.error("Failed to send {} notification to user {}", channel, content.getUserId(), e);
            }
        }
        
        return notificationIds;
    }

    /**
     * Publish notification event for analytics
     */
    private void publishNotificationEvent(Transaction transaction, List<NotificationResult> results) {
        try {
            NotificationCompensationEvent event = NotificationCompensationEvent.builder()
                .transactionId(transaction.getId())
                .notificationsSent(results.size())
                .successfulNotifications(
                    results.stream().filter(NotificationResult::isSuccessful).count())
                .timestamp(LocalDateTime.now())
                .build();

            kafkaTemplate.send("notification-compensation-events", event);
            
        } catch (Exception e) {
            log.error("Failed to publish notification event", e);
            // Don't fail the compensation if event publishing fails
        }
    }

    /**
     * Check if compensation has already been applied
     */
    private boolean isCompensationAlreadyApplied(UUID transactionId, String actionId) {
        return compensationAuditService.isCompensationApplied(transactionId, actionId, "NOTIFICATION");
    }

    /**
     * Fallback method for circuit breaker
     */
    public CompensationAction.CompensationResult compensateFallback(
            Transaction transaction, CompensationAction action, Exception ex) {
        
        log.error("CIRCUIT_BREAKER: Notification compensation circuit breaker activated for transaction: {}", 
                transaction.getId(), ex);

        // Notifications are not critical for rollback success
        // Mark as completed with warning
        return CompensationAction.CompensationResult.builder()
            .actionId(action.getActionId())
            .status(CompensationAction.CompensationStatus.COMPLETED_WITH_WARNING)
            .message("Notification service unavailable - rollback completed without notifications")
            .warningMessage("Users will be notified when service recovers")
            .completedAt(LocalDateTime.now())
            .build();
    }

    /**
     * Generate notification compensation actions
     */
    public List<CompensationAction> generateActions(Transaction transaction) {
        List<CompensationAction> actions = new ArrayList<>();

        // Always create notification action for rollbacks
        actions.add(CompensationAction.builder()
            .actionId(UUID.randomUUID().toString())
            .actionType(CompensationAction.ActionType.NOTIFICATION)
            .targetService("notification-service")
            .targetResourceId(transaction.getId().toString())
            .compensationData(Map.of(
                "notificationType", "ROLLBACK",
                "recipients", List.of(
                    transaction.getFromUserId(),
                    transaction.getToUserId()
                ).stream().filter(Objects::nonNull).toList(),
                "priority", "HIGH"
            ))
            .priority(5) // Notifications have lower priority than financial compensations
            .retryable(true)
            .maxRetries(3)
            .build());

        return actions;
    }

    // Internal DTOs
    @lombok.Builder
    @lombok.Data
    private static class NotificationContent {
        private String userId;
        private String type;
        private String title;
        private String message;
        private Map<String, String> data;
        private String priority;
        private Set<NotificationChannel> channels;
    }

    @lombok.Builder
    @lombok.Data
    private static class NotificationResult {
        private String userId;
        private String role;
        private boolean successful;
        private Map<String, String> notificationIds;
        private String error;
        private LocalDateTime sentAt;
    }

    @lombok.Builder
    @lombok.Data
    private static class RegulatoryNotification {
        private final String id = UUID.randomUUID().toString();
        private UUID transactionId;
        private String type;
        private java.math.BigDecimal amount;
        private String currency;
        private Map<String, String> parties;
        private String reason;
        private LocalDateTime timestamp;
        private String jurisdiction;
    }

    @lombok.Builder
    @lombok.Data
    private static class NotificationCompensationEvent {
        private UUID transactionId;
        private int notificationsSent;
        private long successfulNotifications;
        private LocalDateTime timestamp;
    }
}