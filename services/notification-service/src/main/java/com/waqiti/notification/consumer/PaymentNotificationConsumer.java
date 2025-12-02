package com.waqiti.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.notification.service.NotificationService;
import com.waqiti.notification.domain.NotificationTemplate;
import com.waqiti.notification.domain.NotificationChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Critical Kafka consumer for processing payment notifications
 * Handles payment status updates from payment providers (Wise, Stripe, PayPal, etc.)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentNotificationConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final UniversalDLQHandler universalDLQHandler;

    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000L, multiplier = 2.0),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class}
    )
    @KafkaListener(topics = "payment-notifications", groupId = "notification-service-payment-group")
    public void processPaymentNotification(
            @Payload String payload, 
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
            Acknowledgment acknowledgment) {
        
        try {
            log.info("Processing payment notification from topic: {}, partition: {}", topic, partition);
            
            // Parse payment notification event
            PaymentNotificationEvent event = objectMapper.readValue(payload, PaymentNotificationEvent.class);
            
            // Validate event
            validatePaymentEvent(event);
            
            // Process based on payment status
            switch (event.getPaymentStatus()) {
                case "COMPLETED":
                    handlePaymentCompleted(event);
                    break;
                case "FAILED":
                    handlePaymentFailed(event);
                    break;
                case "PENDING":
                    handlePaymentPending(event);
                    break;
                case "REFUNDED":
                    handlePaymentRefunded(event);
                    break;
                case "CANCELLED":
                    handlePaymentCancelled(event);
                    break;
                case "PROCESSING":
                    handlePaymentProcessing(event);
                    break;
                default:
                    log.warn("Unknown payment status: {}", event.getPaymentStatus());
            }
            
            // Log successful processing
            log.info("Successfully processed payment notification for payment: {}, status: {}", 
                event.getPaymentId(), event.getPaymentStatus());
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Error processing payment notification: {}", e.getMessage(), e);

            // Use UniversalDLQHandler for enhanced DLQ routing
            universalDLQHandler.sendToDLQ(
                payload,
                topic,
                partition,
                -1L, // offset not directly available in this listener signature
                e,
                Map.of(
                    "consumerGroup", "notification-service-payment-group",
                    "errorType", e.getClass().getSimpleName()
                )
            );

            // Don't acknowledge - let retry mechanism handle it
            throw new RuntimeException("Failed to process payment notification", e);
        }
    }

    private void validatePaymentEvent(PaymentNotificationEvent event) {
        if (event.getPaymentId() == null || event.getPaymentId().trim().isEmpty()) {
            throw new IllegalArgumentException("Payment ID is required");
        }
        
        if (event.getUserId() == null || event.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required");
        }
        
        if (event.getPaymentStatus() == null || event.getPaymentStatus().trim().isEmpty()) {
            throw new IllegalArgumentException("Payment status is required");
        }
        
        if (event.getProvider() == null || event.getProvider().trim().isEmpty()) {
            throw new IllegalArgumentException("Payment provider is required");
        }
    }

    private void handlePaymentCompleted(PaymentNotificationEvent event) {
        log.info("Processing completed payment notification for payment: {}", event.getPaymentId());
        
        try {
            // Create notification variables
            Map<String, Object> variables = new HashMap<>();
            variables.put("paymentId", event.getPaymentId());
            variables.put("amount", event.getAmount());
            variables.put("currency", event.getCurrency());
            variables.put("recipientName", event.getRecipientName());
            variables.put("provider", event.getProvider());
            variables.put("completedAt", LocalDateTime.now().toString());
            variables.put("transactionReference", event.getTransactionReference());
            
            // Send SMS notification
            notificationService.sendNotification(
                event.getUserId(),
                NotificationTemplate.PAYMENT_COMPLETED,
                NotificationChannel.SMS,
                variables
            );
            
            // Send email notification
            notificationService.sendNotification(
                event.getUserId(),
                NotificationTemplate.PAYMENT_COMPLETED,
                NotificationChannel.EMAIL,
                variables
            );
            
            // Send push notification
            notificationService.sendNotification(
                event.getUserId(),
                NotificationTemplate.PAYMENT_COMPLETED,
                NotificationChannel.PUSH,
                variables
            );
            
            log.info("Payment completion notifications sent for payment: {}", event.getPaymentId());
            
        } catch (Exception e) {
            log.error("Error sending payment completion notifications for payment {}: {}", 
                event.getPaymentId(), e.getMessage(), e);
            throw new RuntimeException("Failed to send payment completion notifications", e);
        }
    }

    private void handlePaymentFailed(PaymentNotificationEvent event) {
        log.info("Processing failed payment notification for payment: {}", event.getPaymentId());
        
        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("paymentId", event.getPaymentId());
            variables.put("amount", event.getAmount());
            variables.put("currency", event.getCurrency());
            variables.put("recipientName", event.getRecipientName());
            variables.put("provider", event.getProvider());
            variables.put("failureReason", event.getFailureReason());
            variables.put("failedAt", LocalDateTime.now().toString());
            variables.put("supportContact", "support@example.com");
            
            // Send immediate SMS alert for failed payments
            notificationService.sendNotification(
                event.getUserId(),
                NotificationTemplate.PAYMENT_FAILED,
                NotificationChannel.SMS,
                variables
            );
            
            // Send detailed email notification
            notificationService.sendNotification(
                event.getUserId(),
                NotificationTemplate.PAYMENT_FAILED,
                NotificationChannel.EMAIL,
                variables
            );
            
            // Send push notification
            notificationService.sendNotification(
                event.getUserId(),
                NotificationTemplate.PAYMENT_FAILED,
                NotificationChannel.PUSH,
                variables
            );
            
            log.info("Payment failure notifications sent for payment: {}", event.getPaymentId());
            
        } catch (Exception e) {
            log.error("Error sending payment failure notifications for payment {}: {}", 
                event.getPaymentId(), e.getMessage(), e);
            throw new RuntimeException("Failed to send payment failure notifications", e);
        }
    }

    private void handlePaymentPending(PaymentNotificationEvent event) {
        log.info("Processing pending payment notification for payment: {}", event.getPaymentId());
        
        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("paymentId", event.getPaymentId());
            variables.put("amount", event.getAmount());
            variables.put("currency", event.getCurrency());
            variables.put("recipientName", event.getRecipientName());
            variables.put("provider", event.getProvider());
            variables.put("estimatedCompletionTime", event.getEstimatedCompletionTime());
            
            // Send SMS notification for pending status
            notificationService.sendNotification(
                event.getUserId(),
                NotificationTemplate.PAYMENT_PENDING,
                NotificationChannel.SMS,
                variables
            );
            
            // Send push notification
            notificationService.sendNotification(
                event.getUserId(),
                NotificationTemplate.PAYMENT_PENDING,
                NotificationChannel.PUSH,
                variables
            );
            
            log.info("Payment pending notifications sent for payment: {}", event.getPaymentId());
            
        } catch (Exception e) {
            log.error("Error sending payment pending notifications for payment {}: {}", 
                event.getPaymentId(), e.getMessage(), e);
            throw new RuntimeException("Failed to send payment pending notifications", e);
        }
    }

    private void handlePaymentRefunded(PaymentNotificationEvent event) {
        log.info("Processing refund notification for payment: {}", event.getPaymentId());
        
        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("paymentId", event.getPaymentId());
            variables.put("originalAmount", event.getAmount());
            variables.put("refundAmount", event.getRefundAmount());
            variables.put("currency", event.getCurrency());
            variables.put("refundReason", event.getRefundReason());
            variables.put("refundedAt", LocalDateTime.now().toString());
            
            // Send refund confirmation notifications
            notificationService.sendNotification(
                event.getUserId(),
                NotificationTemplate.PAYMENT_REFUNDED,
                NotificationChannel.SMS,
                variables
            );
            
            notificationService.sendNotification(
                event.getUserId(),
                NotificationTemplate.PAYMENT_REFUNDED,
                NotificationChannel.EMAIL,
                variables
            );
            
            notificationService.sendNotification(
                event.getUserId(),
                NotificationTemplate.PAYMENT_REFUNDED,
                NotificationChannel.PUSH,
                variables
            );
            
            log.info("Payment refund notifications sent for payment: {}", event.getPaymentId());
            
        } catch (Exception e) {
            log.error("Error sending payment refund notifications for payment {}: {}", 
                event.getPaymentId(), e.getMessage(), e);
            throw new RuntimeException("Failed to send payment refund notifications", e);
        }
    }

    private void handlePaymentCancelled(PaymentNotificationEvent event) {
        log.info("Processing cancelled payment notification for payment: {}", event.getPaymentId());
        
        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("paymentId", event.getPaymentId());
            variables.put("amount", event.getAmount());
            variables.put("currency", event.getCurrency());
            variables.put("recipientName", event.getRecipientName());
            variables.put("cancellationReason", event.getCancellationReason());
            variables.put("cancelledAt", LocalDateTime.now().toString());
            
            // Send cancellation notifications
            notificationService.sendNotification(
                event.getUserId(),
                NotificationTemplate.PAYMENT_CANCELLED,
                NotificationChannel.SMS,
                variables
            );
            
            notificationService.sendNotification(
                event.getUserId(),
                NotificationTemplate.PAYMENT_CANCELLED,
                NotificationChannel.PUSH,
                variables
            );
            
            log.info("Payment cancellation notifications sent for payment: {}", event.getPaymentId());
            
        } catch (Exception e) {
            log.error("Error sending payment cancellation notifications for payment {}: {}", 
                event.getPaymentId(), e.getMessage(), e);
            throw new RuntimeException("Failed to send payment cancellation notifications", e);
        }
    }

    private void handlePaymentProcessing(PaymentNotificationEvent event) {
        log.info("Processing payment processing notification for payment: {}", event.getPaymentId());
        
        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("paymentId", event.getPaymentId());
            variables.put("amount", event.getAmount());
            variables.put("currency", event.getCurrency());
            variables.put("recipientName", event.getRecipientName());
            variables.put("provider", event.getProvider());
            variables.put("estimatedCompletionTime", event.getEstimatedCompletionTime());
            
            // Send processing notification (push only to avoid spam)
            notificationService.sendNotification(
                event.getUserId(),
                NotificationTemplate.PAYMENT_PROCESSING,
                NotificationChannel.PUSH,
                variables
            );
            
            log.info("Payment processing notification sent for payment: {}", event.getPaymentId());
            
        } catch (Exception e) {
            log.error("Error sending payment processing notification for payment {}: {}", 
                event.getPaymentId(), e.getMessage(), e);
            throw new RuntimeException("Failed to send payment processing notification", e);
        }
    }

    // Payment notification event data structure
    public static class PaymentNotificationEvent {
        private String paymentId;
        private String userId;
        private String paymentStatus;
        private String provider;
        private String amount;
        private String currency;
        private String recipientName;
        private String transactionReference;
        private String failureReason;
        private String refundAmount;
        private String refundReason;
        private String cancellationReason;
        private String estimatedCompletionTime;
        private LocalDateTime timestamp;
        
        // Getters and setters
        public String getPaymentId() { return paymentId; }
        public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
        
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public String getPaymentStatus() { return paymentStatus; }
        public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
        
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        
        public String getAmount() { return amount; }
        public void setAmount(String amount) { this.amount = amount; }
        
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        
        public String getRecipientName() { return recipientName; }
        public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
        
        public String getTransactionReference() { return transactionReference; }
        public void setTransactionReference(String transactionReference) { this.transactionReference = transactionReference; }
        
        public String getFailureReason() { return failureReason; }
        public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
        
        public String getRefundAmount() { return refundAmount; }
        public void setRefundAmount(String refundAmount) { this.refundAmount = refundAmount; }
        
        public String getRefundReason() { return refundReason; }
        public void setRefundReason(String refundReason) { this.refundReason = refundReason; }
        
        public String getCancellationReason() { return cancellationReason; }
        public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }
        
        public String getEstimatedCompletionTime() { return estimatedCompletionTime; }
        public void setEstimatedCompletionTime(String estimatedCompletionTime) { this.estimatedCompletionTime = estimatedCompletionTime; }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }
}