package com.waqiti.notification.consumer;

import com.waqiti.common.events.PaymentInitiatedEvent;
import com.waqiti.notification.service.EmailNotificationService;
import com.waqiti.notification.service.PushNotificationService;
import com.waqiti.notification.service.SMSNotificationService;
import com.waqiti.notification.service.InAppNotificationService;
import com.waqiti.notification.repository.ProcessedEventRepository;
import com.waqiti.notification.model.ProcessedEvent;
import com.waqiti.notification.model.NotificationPreferences;
import com.waqiti.notification.dto.PaymentNotificationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.List;

/**
 * Consumer for PaymentInitiatedEvent - Critical for user notifications
 * Sends multi-channel notifications for payment initiation
 * ZERO TOLERANCE: Must notify users immediately for financial transparency
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentInitiatedEventConsumer {
    
    private final EmailNotificationService emailService;
    private final PushNotificationService pushService;
    private final SMSNotificationService smsService;
    private final InAppNotificationService inAppService;
    private final ProcessedEventRepository processedEventRepository;
    private final NotificationPreferencesService preferencesService;
    
    @KafkaListener(
        topics = "payment.initiated",
        groupId = "notification-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handlePaymentInitiated(PaymentInitiatedEvent event) {
        log.info("Processing payment notification for event: {}", event.getEventId());
        
        // IDEMPOTENCY CHECK
        if (processedEventRepository.existsByEventId(event.getEventId())) {
            log.info("Notification already sent for event: {}", event.getEventId());
            return;
        }
        
        try {
            // Get user notification preferences
            NotificationPreferences senderPrefs = preferencesService
                .getPreferences(event.getSenderUserId());
            NotificationPreferences receiverPrefs = preferencesService
                .getPreferences(event.getReceiverUserId());
            
            // Prepare notification data
            PaymentNotificationDto senderNotification = createSenderNotification(event);
            PaymentNotificationDto receiverNotification = createReceiverNotification(event);
            
            // Send notifications asynchronously for performance
            List<CompletableFuture<Void>> notificationTasks = List.of(
                // Sender notifications
                sendSenderNotifications(senderNotification, senderPrefs),
                // Receiver notifications  
                sendReceiverNotifications(receiverNotification, receiverPrefs),
                // Compliance notifications for high-value transfers
                sendComplianceNotifications(event)
            );
            
            // Wait for all notifications to complete
            CompletableFuture.allOf(notificationTasks.toArray(new CompletableFuture[0]))
                .get(); // Block to ensure delivery before marking as processed
            
            // Record successful processing
            ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventId(event.getEventId())
                .eventType("PaymentInitiatedEvent")
                .processedAt(Instant.now())
                .notificationsSent(calculateNotificationCount(senderPrefs, receiverPrefs))
                .build();
                
            processedEventRepository.save(processedEvent);
            
            log.info("Successfully sent payment initiation notifications for: {}", 
                event.getPaymentId());
                
        } catch (Exception e) {
            log.error("Failed to send payment notifications for event: {}", 
                event.getEventId(), e);
                
            // Send fallback notification to ensure user awareness
            sendFallbackNotification(event);
            
            throw new RuntimeException("Payment notification failed", e);
        }
    }
    
    private PaymentNotificationDto createSenderNotification(PaymentInitiatedEvent event) {
        return PaymentNotificationDto.builder()
            .userId(event.getSenderUserId())
            .title("Payment Sent")
            .message(String.format(
                "You sent $%.2f to %s. Transaction ID: %s", 
                event.getAmount(), 
                event.getReceiverDisplayName(),
                event.getPaymentId()
            ))
            .type("PAYMENT_SENT")
            .amount(event.getAmount())
            .currency(event.getCurrency())
            .transactionId(event.getPaymentId())
            .timestamp(event.getTimestamp())
            .priority("HIGH")
            .build();
    }
    
    private PaymentNotificationDto createReceiverNotification(PaymentInitiatedEvent event) {
        return PaymentNotificationDto.builder()
            .userId(event.getReceiverUserId())
            .title("Payment Received")
            .message(String.format(
                "You received $%.2f from %s. Transaction ID: %s", 
                event.getAmount(),
                event.getSenderDisplayName(),
                event.getPaymentId()
            ))
            .type("PAYMENT_RECEIVED")
            .amount(event.getAmount())
            .currency(event.getCurrency())
            .transactionId(event.getPaymentId())
            .timestamp(event.getTimestamp())
            .priority("HIGH")
            .build();
    }
    
    private CompletableFuture<Void> sendSenderNotifications(
            PaymentNotificationDto notification, 
            NotificationPreferences preferences) {
        
        return CompletableFuture.runAsync(() -> {
            // Send based on user preferences
            if (preferences.isEmailEnabled()) {
                emailService.sendPaymentEmail(notification);
            }
            
            if (preferences.isPushEnabled()) {
                pushService.sendPushNotification(notification);
            }
            
            if (preferences.isSmsEnabled() && notification.getAmount().compareTo(
                preferences.getSmsThreshold()) >= 0) {
                smsService.sendPaymentSms(notification);
            }
            
            // Always send in-app notification for audit trail
            inAppService.sendInAppNotification(notification);
        });
    }
    
    private CompletableFuture<Void> sendReceiverNotifications(
            PaymentNotificationDto notification, 
            NotificationPreferences preferences) {
        
        return CompletableFuture.runAsync(() -> {
            // Immediate notifications for receivers
            if (preferences.isEmailEnabled()) {
                emailService.sendPaymentEmail(notification);
            }
            
            if (preferences.isPushEnabled()) {
                pushService.sendPushNotification(notification);
            }
            
            // Always notify receivers via in-app
            inAppService.sendInAppNotification(notification);
        });
    }
    
    private CompletableFuture<Void> sendComplianceNotifications(PaymentInitiatedEvent event) {
        return CompletableFuture.runAsync(() -> {
            // High-value transaction alerts to compliance team
            if (event.getAmount().compareTo(new BigDecimal("10000")) >= 0) {
                emailService.sendComplianceAlert(
                    "High-value payment initiated",
                    String.format(
                        "Payment of $%.2f initiated from %s to %s. Transaction ID: %s",
                        event.getAmount(),
                        event.getSenderDisplayName(),
                        event.getReceiverDisplayName(),
                        event.getPaymentId()
                    ),
                    event
                );
            }
        });
    }
    
    private void sendFallbackNotification(PaymentInitiatedEvent event) {
        // Basic email notification as fallback
        try {
            emailService.sendBasicNotification(
                event.getSenderEmail(),
                "Payment Processing",
                String.format(
                    "Your payment of $%.2f is being processed. Transaction ID: %s",
                    event.getAmount(),
                    event.getPaymentId()
                )
            );
        } catch (Exception e) {
            log.error("Even fallback notification failed for event: {}", 
                event.getEventId(), e);
        }
    }
    
    private int calculateNotificationCount(NotificationPreferences senderPrefs, 
                                         NotificationPreferences receiverPrefs) {
        int count = 2; // Always send in-app notifications
        
        if (senderPrefs.isEmailEnabled()) count++;
        if (senderPrefs.isPushEnabled()) count++;
        if (senderPrefs.isSmsEnabled()) count++;
        
        if (receiverPrefs.isEmailEnabled()) count++;
        if (receiverPrefs.isPushEnabled()) count++;
        
        return count;
    }
}