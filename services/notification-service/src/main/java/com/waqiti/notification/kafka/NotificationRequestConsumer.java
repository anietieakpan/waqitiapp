package com.waqiti.notification.kafka;

import com.waqiti.notification.event.NotificationRequestEvent;
import com.waqiti.notification.service.NotificationService;
import com.waqiti.notification.service.EmailService;
import com.waqiti.notification.service.SmsService;
import com.waqiti.notification.service.PushNotificationService;
import com.waqiti.notification.service.NotificationTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Production-grade Kafka consumer for notification requests
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRequestConsumer {

    private final NotificationService notificationService;
    private final EmailService emailService;
    private final SmsService smsService;
    private final PushNotificationService pushService;
    private final NotificationTrackingService trackingService;

    @KafkaListener(topics = "notification-requests", groupId = "notification-processor")
    public void processNotificationRequest(@Payload NotificationRequestEvent event,
                                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                         @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                         @Header(KafkaHeaders.OFFSET) long offset,
                                         Acknowledgment acknowledgment) {
        try {
            log.info("Processing notification request: {} type: {} channel: {} priority: {}", 
                    event.getNotificationId(), event.getNotificationType(), 
                    event.getChannel(), event.getPriority());
            
            // Validate event
            validateNotificationRequest(event);
            
            // Check if notification should be sent
            if (!shouldSendNotification(event)) {
                log.info("Notification {} skipped based on preferences", event.getNotificationId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Process based on channel
            boolean sent = false;
            switch (event.getChannel()) {
                case "EMAIL" -> sent = sendEmailNotification(event);
                case "SMS" -> sent = sendSmsNotification(event);
                case "PUSH" -> sent = sendPushNotification(event);
                case "IN_APP" -> sent = sendInAppNotification(event);
                case "WEBHOOK" -> sent = sendWebhookNotification(event);
                case "MULTI_CHANNEL" -> sent = sendMultiChannelNotification(event);
                default -> log.warn("Unknown notification channel: {}", event.getChannel());
            }
            
            // Track notification
            trackNotification(event, sent);
            
            // Handle delivery status
            if (sent) {
                handleSuccessfulDelivery(event);
            } else {
                handleDeliveryFailure(event);
            }
            
            // Schedule follow-up if needed
            if (event.getFollowUpRequired()) {
                scheduleFollowUp(event);
            }
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
            log.debug("Successfully processed notification request: {}", event.getNotificationId());
            
        } catch (Exception e) {
            log.error("Failed to process notification request {}: {}", 
                    event.getNotificationId(), e.getMessage(), e);
            throw new RuntimeException("Notification processing failed", e);
        }
    }

    private void validateNotificationRequest(NotificationRequestEvent event) {
        if (event.getNotificationId() == null || event.getNotificationId().trim().isEmpty()) {
            throw new IllegalArgumentException("Notification ID is required");
        }
        
        if (event.getChannel() == null || event.getChannel().trim().isEmpty()) {
            throw new IllegalArgumentException("Channel is required");
        }
        
        if (event.getRecipient() == null || event.getRecipient().trim().isEmpty()) {
            throw new IllegalArgumentException("Recipient is required");
        }
    }

    private boolean shouldSendNotification(NotificationRequestEvent event) {
        // Check user preferences
        if (!notificationService.isNotificationEnabled(
                event.getUserId(),
                event.getNotificationType(),
                event.getChannel())) {
            return false;
        }
        
        // Check rate limiting
        if (notificationService.isRateLimited(
                event.getUserId(),
                event.getChannel())) {
            log.warn("Rate limit exceeded for user: {} channel: {}", 
                    event.getUserId(), event.getChannel());
            return false;
        }
        
        // Check quiet hours
        if (notificationService.isInQuietHours(
                event.getUserId(),
                event.getPriority())) {
            return false;
        }
        
        // Check duplicate
        if (notificationService.isDuplicate(
                event.getNotificationId(),
                event.getDeduplicationKey())) {
            log.info("Duplicate notification detected: {}", event.getNotificationId());
            return false;
        }
        
        return true;
    }

    private boolean sendEmailNotification(NotificationRequestEvent event) {
        try {
            // Prepare email content
            String content = prepareEmailContent(event);
            
            // Send email
            boolean sent = emailService.sendEmail(
                event.getRecipient(),
                event.getSubject(),
                content,
                event.getAttachments(),
                event.getCcRecipients(),
                event.getBccRecipients(),
                event.getReplyTo(),
                event.isHtml()
            );
            
            // Track email metrics
            if (sent) {
                trackingService.trackEmailSent(
                    event.getNotificationId(),
                    event.getRecipient(),
                    event.getSubject()
                );
            }
            
            return sent;
        } catch (Exception e) {
            log.error("Failed to send email notification: {}", e.getMessage());
            return false;
        }
    }

    private boolean sendSmsNotification(NotificationRequestEvent event) {
        try {
            // Validate phone number
            if (!smsService.isValidPhoneNumber(event.getRecipient())) {
                log.error("Invalid phone number: {}", event.getRecipient());
                return false;
            }
            
            // Prepare SMS content
            String content = prepareSmsContent(event);
            
            // Send SMS
            boolean sent = smsService.sendSms(
                event.getRecipient(),
                content,
                event.getSmsProvider(),
                event.getSenderId()
            );
            
            // Track SMS metrics
            if (sent) {
                trackingService.trackSmsSent(
                    event.getNotificationId(),
                    event.getRecipient(),
                    content.length()
                );
            }
            
            return sent;
        } catch (Exception e) {
            log.error("Failed to send SMS notification: {}", e.getMessage());
            return false;
        }
    }

    private boolean sendPushNotification(NotificationRequestEvent event) {
        try {
            // Get device tokens
            var deviceTokens = pushService.getDeviceTokens(event.getUserId());
            if (deviceTokens.isEmpty()) {
                log.warn("No device tokens found for user: {}", event.getUserId());
                return false;
            }
            
            // Send push notification
            boolean sent = pushService.sendPushNotification(
                deviceTokens,
                event.getTitle(),
                event.getBody(),
                event.getImageUrl(),
                event.getDeepLink(),
                event.getCustomData(),
                event.getBadgeCount(),
                event.getSound()
            );
            
            // Track push metrics
            if (sent) {
                trackingService.trackPushSent(
                    event.getNotificationId(),
                    event.getUserId(),
                    deviceTokens.size()
                );
            }
            
            return sent;
        } catch (Exception e) {
            log.error("Failed to send push notification: {}", e.getMessage());
            return false;
        }
    }

    private boolean sendInAppNotification(NotificationRequestEvent event) {
        try {
            // Create in-app notification
            String inAppId = notificationService.createInAppNotification(
                event.getUserId(),
                event.getNotificationType(),
                event.getTitle(),
                event.getBody(),
                event.getPriority(),
                event.getExpiresAt(),
                event.getActionButtons(),
                event.getCustomData()
            );
            
            // Send real-time update if user is online
            if (notificationService.isUserOnline(event.getUserId())) {
                notificationService.sendRealtimeUpdate(
                    event.getUserId(),
                    inAppId,
                    event.getBody()
                );
            }
            
            // Track in-app metrics
            trackingService.trackInAppCreated(
                event.getNotificationId(),
                event.getUserId(),
                inAppId
            );
            
            return true;
        } catch (Exception e) {
            log.error("Failed to create in-app notification: {}", e.getMessage());
            return false;
        }
    }

    private boolean sendWebhookNotification(NotificationRequestEvent event) {
        try {
            // Get webhook URL
            String webhookUrl = notificationService.getWebhookUrl(
                event.getUserId(),
                event.getWebhookId()
            );
            
            if (webhookUrl == null) {
                log.warn("No webhook URL configured for user: {} webhook: {}", 
                        event.getUserId(), event.getWebhookId());
                return false;
            }
            
            // Prepare webhook payload
            Map<String, Object> payload = Map.of(
                "notificationId", event.getNotificationId(),
                "type", event.getNotificationType(),
                "title", event.getTitle(),
                "body", event.getBody(),
                "timestamp", LocalDateTime.now(),
                "customData", event.getCustomData()
            );
            
            // Send webhook
            boolean sent = notificationService.sendWebhook(
                webhookUrl,
                payload,
                event.getWebhookHeaders(),
                event.getWebhookMethod()
            );
            
            // Track webhook metrics
            if (sent) {
                trackingService.trackWebhookSent(
                    event.getNotificationId(),
                    webhookUrl
                );
            }
            
            return sent;
        } catch (Exception e) {
            log.error("Failed to send webhook notification: {}", e.getMessage());
            return false;
        }
    }

    private boolean sendMultiChannelNotification(NotificationRequestEvent event) {
        boolean anySuccess = false;
        
        // Send to all specified channels
        for (String channel : event.getChannels()) {
            event.setChannel(channel);
            boolean sent = switch (channel) {
                case "EMAIL" -> sendEmailNotification(event);
                case "SMS" -> sendSmsNotification(event);
                case "PUSH" -> sendPushNotification(event);
                case "IN_APP" -> sendInAppNotification(event);
                default -> false;
            };
            
            if (sent) {
                anySuccess = true;
                if (event.isStopOnFirstSuccess()) {
                    break;
                }
            }
        }
        
        return anySuccess;
    }

    private String prepareEmailContent(NotificationRequestEvent event) {
        if (event.getTemplateId() != null) {
            return notificationService.renderTemplate(
                event.getTemplateId(),
                event.getTemplateData(),
                "EMAIL"
            );
        }
        return event.getBody();
    }

    private String prepareSmsContent(NotificationRequestEvent event) {
        String content = event.getBody();
        
        // Apply SMS-specific formatting
        if (event.getTemplateId() != null) {
            content = notificationService.renderTemplate(
                event.getTemplateId(),
                event.getTemplateData(),
                "SMS"
            );
        }
        
        // Truncate if necessary
        if (content.length() > 160) {
            content = content.substring(0, 157) + "...";
        }
        
        return content;
    }

    private void trackNotification(NotificationRequestEvent event, boolean sent) {
        trackingService.trackNotification(
            event.getNotificationId(),
            event.getUserId(),
            event.getChannel(),
            event.getNotificationType(),
            sent ? "SENT" : "FAILED",
            LocalDateTime.now()
        );
        
        // Update user notification stats
        notificationService.updateUserStats(
            event.getUserId(),
            event.getChannel(),
            sent
        );
        
        // Track channel metrics
        trackingService.updateChannelMetrics(
            event.getChannel(),
            sent,
            event.getPriority()
        );
    }

    private void handleSuccessfulDelivery(NotificationRequestEvent event) {
        // Mark as delivered
        notificationService.markAsDelivered(
            event.getNotificationId(),
            LocalDateTime.now()
        );
        
        // Clear retry attempts
        notificationService.clearRetryAttempts(event.getNotificationId());
        
        // Trigger success callback if configured
        if (event.getSuccessCallbackUrl() != null) {
            notificationService.triggerCallback(
                event.getSuccessCallbackUrl(),
                event.getNotificationId(),
                "SUCCESS"
            );
        }
    }

    private void handleDeliveryFailure(NotificationRequestEvent event) {
        // Increment failure count
        int failureCount = notificationService.incrementFailureCount(
            event.getNotificationId()
        );
        
        // Check if should retry
        if (failureCount < event.getMaxRetries()) {
            notificationService.scheduleRetry(
                event,
                failureCount
            );
        } else {
            // Mark as failed
            notificationService.markAsFailed(
                event.getNotificationId(),
                "MAX_RETRIES_EXCEEDED"
            );
            
            // Send to DLQ
            notificationService.sendToDeadLetterQueue(event);
            
            // Trigger failure callback if configured
            if (event.getFailureCallbackUrl() != null) {
                notificationService.triggerCallback(
                    event.getFailureCallbackUrl(),
                    event.getNotificationId(),
                    "FAILED"
                );
            }
        }
    }

    private void scheduleFollowUp(NotificationRequestEvent event) {
        if (event.getFollowUpDelay() != null) {
            notificationService.scheduleFollowUpNotification(
                event.getUserId(),
                event.getFollowUpType(),
                event.getFollowUpChannel(),
                LocalDateTime.now().plus(event.getFollowUpDelay())
            );
        }
    }
}