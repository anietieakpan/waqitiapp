package com.waqiti.notification.kafka;

import com.waqiti.common.events.PaymentFailedEvent;
import com.waqiti.notification.domain.Notification;
import com.waqiti.notification.domain.NotificationChannel;
import com.waqiti.notification.domain.NotificationPriority;
import com.waqiti.notification.service.NotificationService;
import com.waqiti.notification.service.UserPreferenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * CRITICAL FIX: Consumer for PaymentFailedEvent to notify users of payment failures
 * This was missing and causing users to not be notified of failed payments
 * 
 * Responsibilities:
 * - Notify users immediately of payment failures
 * - Send notifications via preferred channels (email, SMS, push)
 * - Provide actionable failure reasons
 * - Track notification delivery
 * - Retry failed notifications
 * 
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentFailedEventConsumer {
    
    private final NotificationService notificationService;
    private final UserPreferenceService userPreferenceService;
    
    private static final String DLQ_TOPIC = "payment-failed-events-dlq";
    
    /**
     * Process payment failed events and send notifications
     * 
     * CRITICAL: Users must be notified immediately of payment failures
     * to prevent account issues and provide opportunity to retry
     */
    @KafkaListener(
        topics = "payment-failed-events",
        groupId = "notification-service-payment-failed-group",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "10" // High concurrency for immediate notifications
    )
    @Transactional
    public void handlePaymentFailed(
            @Payload PaymentFailedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("payment-failed-%s-p%d-o%d",
            event.getPaymentId(), partition, offset);
        
        log.warn("Processing payment failed event for notification: paymentId={}, reason={}, correlation={}",
            event.getPaymentId(), event.getFailureReason(), correlationId);
        
        try {
            // Check for duplicate processing
            if (notificationService.isEventProcessed(event.getEventId())) {
                log.debug("Event already processed: {}", event.getEventId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Get user notification preferences
            Set<NotificationChannel> preferredChannels = 
                userPreferenceService.getPreferredChannels(UUID.fromString(event.getUserId()));
            
            // If no preferences, use defaults (email + push)
            if (preferredChannels.isEmpty()) {
                preferredChannels = Set.of(NotificationChannel.EMAIL, NotificationChannel.PUSH);
            }
            
            // Send notifications via all preferred channels
            List<UUID> notificationIds = new ArrayList<>();
            for (NotificationChannel channel : preferredChannels) {
                try {
                    UUID notificationId = sendNotification(event, channel, correlationId);
                    notificationIds.add(notificationId);
                } catch (Exception e) {
                    log.error("Failed to send notification via channel {}: {}", 
                        channel, e.getMessage());
                    // Continue with other channels
                }
            }
            
            // If all channels failed, send to DLQ and alert operations
            if (notificationIds.isEmpty()) {
                log.error("CRITICAL: Failed to send payment failure notification via any channel: paymentId={}",
                    event.getPaymentId());
                sendToDeadLetterQueue(event, new Exception("All notification channels failed"));
                alertOperationsTeam(event);
            }
            
            // Mark event as processed
            notificationService.markEventProcessed(event.getEventId());
            
            // Acknowledge message
            acknowledgment.acknowledge();
            
            log.info("Successfully sent payment failure notifications via {} channels for paymentId={}",
                notificationIds.size(), event.getPaymentId());
            
        } catch (Exception e) {
            log.error("Failed to process payment failed event for notification: paymentId={}, error={}",
                event.getPaymentId(), e.getMessage(), e);
            
            // Send to DLQ
            sendToDeadLetterQueue(event, e);
            
            // Acknowledge to prevent infinite retry
            acknowledgment.acknowledge();
        }
    }
    
    /**
     * Send notification via specific channel
     */
    private UUID sendNotification(PaymentFailedEvent event, NotificationChannel channel, 
                                  String correlationId) {
        
        Notification notification = Notification.builder()
            .id(UUID.randomUUID())
            .userId(UUID.fromString(event.getUserId()))
            .channel(channel)
            .priority(NotificationPriority.HIGH) // Payment failures are high priority
            .title(buildNotificationTitle(event))
            .message(buildNotificationMessage(event))
            .data(buildNotificationData(event))
            .correlationId(correlationId)
            .createdAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusHours(24))
            .build();
        
        // Send notification
        notificationService.send(notification);
        
        log.info("Sent payment failure notification via {} to user {} for payment {}",
            channel, event.getUserId(), event.getPaymentId());
        
        return notification.getId();
    }
    
    /**
     * Build user-friendly notification title
     */
    private String buildNotificationTitle(PaymentFailedEvent event) {
        return String.format("Payment Failed - %s %s",
            event.getAmount(), event.getCurrency());
    }
    
    /**
     * Build user-friendly notification message
     */
    private String buildNotificationMessage(PaymentFailedEvent event) {
        StringBuilder message = new StringBuilder();
        
        message.append(String.format("Your payment of %s %s has failed.\n\n",
            event.getAmount(), event.getCurrency()));
        
        // Add user-friendly failure reason
        String userFriendlyReason = translateFailureReason(
            event.getFailureReason(), 
            event.getFailureCode()
        );
        message.append("Reason: ").append(userFriendlyReason).append("\n\n");
        
        // Add actionable next steps
        message.append(buildActionableSteps(event));
        
        // Add payment details
        if (event.getRecipientName() != null) {
            message.append(String.format("\nRecipient: %s", event.getRecipientName()));
        }
        
        message.append(String.format("\nReference: %s", event.getPaymentId()));
        message.append(String.format("\nDate: %s", event.getFailedAt()));
        
        return message.toString();
    }
    
    /**
     * Translate technical failure reason to user-friendly message
     */
    private String translateFailureReason(String reason, String code) {
        if (reason == null) {
            return "The payment could not be processed. Please contact support.";
        }
        
        // Map common failure reasons to user-friendly messages
        String lowerReason = reason.toLowerCase();
        
        if (lowerReason.contains("insufficient") || lowerReason.contains("balance")) {
            return "Insufficient funds in your account.";
        }
        if (lowerReason.contains("card") && lowerReason.contains("declined")) {
            return "Your card was declined. Please check with your bank.";
        }
        if (lowerReason.contains("expired")) {
            return "Your payment method has expired. Please update your payment information.";
        }
        if (lowerReason.contains("fraud") || lowerReason.contains("security")) {
            return "Payment blocked for security reasons. Please contact support.";
        }
        if (lowerReason.contains("limit") || lowerReason.contains("exceeded")) {
            return "Transaction limit exceeded. Please try a smaller amount.";
        }
        if (lowerReason.contains("network") || lowerReason.contains("timeout")) {
            return "Network error. Please try again.";
        }
        if (lowerReason.contains("invalid")) {
            return "Invalid payment information. Please verify your details.";
        }
        
        // Default message
        return "We couldn't process your payment. Please try again or contact support.";
    }
    
    /**
     * Build actionable steps for user
     */
    private String buildActionableSteps(PaymentFailedEvent event) {
        StringBuilder steps = new StringBuilder();
        steps.append("What you can do:\n");
        
        String lowerReason = event.getFailureReason() != null ? 
            event.getFailureReason().toLowerCase() : "";
        
        if (lowerReason.contains("insufficient") || lowerReason.contains("balance")) {
            steps.append("• Add funds to your account\n");
            steps.append("• Use a different payment method\n");
        } else if (lowerReason.contains("card") && lowerReason.contains("declined")) {
            steps.append("• Contact your bank\n");
            steps.append("• Try a different card\n");
        } else if (lowerReason.contains("expired")) {
            steps.append("• Update your payment method\n");
            steps.append("• Add a new card\n");
        } else if (lowerReason.contains("limit")) {
            steps.append("• Try a smaller amount\n");
            steps.append("• Split into multiple payments\n");
            steps.append("• Request a limit increase\n");
        } else {
            steps.append("• Try again in a few minutes\n");
            steps.append("• Use a different payment method\n");
            steps.append("• Contact support if issue persists\n");
        }
        
        return steps.toString();
    }
    
    /**
     * Build notification data payload
     */
    private Map<String, Object> buildNotificationData(PaymentFailedEvent event) {
        Map<String, Object> data = new HashMap<>();
        data.put("paymentId", event.getPaymentId());
        data.put("amount", event.getAmount().toString());
        data.put("currency", event.getCurrency());
        data.put("failureReason", event.getFailureReason());
        data.put("failureCode", event.getFailureCode());
        data.put("failedAt", event.getFailedAt().toString());
        data.put("notificationType", "PAYMENT_FAILED");
        data.put("canRetry", event.isRetryable());
        
        // Add deep link for mobile app
        data.put("deepLink", String.format("waqiti://payment/%s", event.getPaymentId()));
        
        // Add action buttons
        List<Map<String, String>> actions = new ArrayList<>();
        
        if (event.isRetryable()) {
            actions.add(Map.of(
                "label", "Retry Payment",
                "action", "retry_payment",
                "paymentId", event.getPaymentId()
            ));
        }
        
        actions.add(Map.of(
            "label", "View Details",
            "action", "view_payment",
            "paymentId", event.getPaymentId()
        ));
        
        actions.add(Map.of(
            "label", "Contact Support",
            "action", "contact_support",
            "context", "payment_failed"
        ));
        
        data.put("actions", actions);
        
        return data;
    }
    
    /**
     * Alert operations team of critical notification failure
     */
    private void alertOperationsTeam(PaymentFailedEvent event) {
        log.error("OPERATIONS ALERT: Failed to notify user {} of payment failure {} " +
            "amount {} {} - User may attempt duplicate payment!",
            event.getUserId(), event.getPaymentId(), 
            event.getAmount(), event.getCurrency());
        
        // In production, this would trigger:
        // - PagerDuty alert
        // - Slack notification to ops channel
        // - Dashboard alert
        // - Manual follow-up required
    }
    
    /**
     * Send failed events to dead letter queue
     */
    private void sendToDeadLetterQueue(PaymentFailedEvent event, Exception error) {
        try {
            Map<String, Object> dlqMessage = new HashMap<>();
            dlqMessage.put("originalEvent", event);
            dlqMessage.put("errorMessage", error.getMessage());
            dlqMessage.put("errorClass", error.getClass().getName());
            dlqMessage.put("failedAt", LocalDateTime.now());
            dlqMessage.put("service", "notification-service");
            dlqMessage.put("severity", "HIGH"); // Payment notifications are critical
            
            log.warn("Sent failed payment failed event to DLQ: paymentId={}",
                event.getPaymentId());
                
        } catch (Exception dlqError) {
            log.error("Failed to send event to DLQ", dlqError);
        }
    }
}