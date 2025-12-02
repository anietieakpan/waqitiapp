package com.waqiti.user.kafka;

import com.waqiti.user.event.UserNotificationEvent;
import com.waqiti.user.service.UserService;
import com.waqiti.user.service.NotificationService;
import com.waqiti.user.service.UserNotificationPreferenceService;
import com.waqiti.user.service.CustomerNotificationService;
import com.waqiti.user.service.UserActivityLogService;
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
 * Production-grade Kafka consumer for user notification events
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserNotificationConsumer {

    private final UserService userService;
    private final NotificationService notificationService;
    private final UserNotificationPreferenceService preferenceService;
    private final CustomerNotificationService customerNotificationService;
    private final UserActivityLogService activityLogService;

    @KafkaListener(topics = "user-notifications", groupId = "notification-processor")
    public void processNotification(@Payload UserNotificationEvent event,
                                  @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                  @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                  @Header(KafkaHeaders.OFFSET) long offset,
                                  Acknowledgment acknowledgment) {
        try {
            log.info("Processing notification for user: {} type: {} channel: {} priority: {}", 
                    event.getUserId(), event.getNotificationType(), 
                    event.getChannel(), event.getPriority());
            
            // Validate event
            validateNotificationEvent(event);
            
            // Check user notification preferences
            if (!preferenceService.isNotificationEnabled(
                    event.getUserId(),
                    event.getNotificationType(),
                    event.getChannel())) {
                log.info("Notification disabled by user preference: {} type: {} channel: {}",
                        event.getUserId(), event.getNotificationType(), event.getChannel());
                acknowledgment.acknowledge();
                return;
            }
            
            // Check Do Not Disturb settings
            if (preferenceService.isInDoNotDisturbMode(event.getUserId()) && 
                !"HIGH".equals(event.getPriority()) && 
                !"URGENT".equals(event.getPriority())) {
                scheduleDelayedNotification(event);
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
                default -> log.warn("Unknown notification channel: {}", event.getChannel());
            }
            
            // Track notification delivery
            if (sent) {
                trackNotificationDelivery(event);
            } else {
                handleNotificationFailure(event);
            }
            
            // Log notification activity
            activityLogService.logNotificationSent(
                event.getUserId(),
                event.getNotificationType(),
                event.getChannel(),
                sent,
                event.getCreatedAt()
            );
            
            // Handle action buttons if present
            if (event.getActionButtons() != null && !event.getActionButtons().isEmpty()) {
                notificationService.trackActionButtons(
                    event.getNotificationId(),
                    event.getUserId(),
                    event.getActionButtons()
                );
            }
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
            log.debug("Successfully processed notification for user: {}", event.getUserId());
            
        } catch (Exception e) {
            log.error("Failed to process notification for user {}: {}", 
                    event.getUserId(), e.getMessage(), e);
            throw new RuntimeException("Notification processing failed", e);
        }
    }

    private void validateNotificationEvent(UserNotificationEvent event) {
        if (event.getUserId() == null || event.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required for notification");
        }
        
        if (event.getNotificationType() == null || event.getNotificationType().trim().isEmpty()) {
            throw new IllegalArgumentException("Notification type is required");
        }
        
        if (event.getChannel() == null || event.getChannel().trim().isEmpty()) {
            throw new IllegalArgumentException("Notification channel is required");
        }
        
        if (event.getContent() == null || event.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("Notification content is required");
        }
    }

    private void scheduleDelayedNotification(UserNotificationEvent event) {
        LocalDateTime sendAfter = preferenceService.getDoNotDisturbEndTime(event.getUserId());
        notificationService.scheduleNotification(
            event.getUserId(),
            event.getNotificationType(),
            event.getChannel(),
            event.getSubject(),
            event.getContent(),
            sendAfter
        );
        log.info("Scheduled notification for user {} after DND period ends at {}", 
                event.getUserId(), sendAfter);
    }

    private boolean sendEmailNotification(UserNotificationEvent event) {
        try {
            String emailContent = formatEmailContent(event);
            
            notificationService.sendEmail(
                event.getUserId(),
                event.getRecipientEmail(),
                event.getSubject(),
                emailContent,
                event.getAttachments(),
                event.isHtml()
            );
            
            // Track email open and click rates
            notificationService.trackEmailEngagement(
                event.getNotificationId(),
                event.getUserId(),
                event.getRecipientEmail()
            );
            
            return true;
        } catch (Exception e) {
            log.error("Failed to send email notification: {}", e.getMessage());
            return false;
        }
    }

    private boolean sendSmsNotification(UserNotificationEvent event) {
        try {
            // Check SMS rate limiting
            if (customerNotificationService.isSmsPaceLimitExceeded(event.getUserId())) {
                log.warn("SMS rate limit exceeded for user: {}", event.getUserId());
                return false;
            }
            
            String smsContent = formatSmsContent(event);
            
            customerNotificationService.sendSms(
                event.getUserId(),
                event.getRecipientPhone(),
                smsContent,
                event.getSmsProvider()
            );
            
            return true;
        } catch (Exception e) {
            log.error("Failed to send SMS notification: {}", e.getMessage());
            return false;
        }
    }

    private boolean sendPushNotification(UserNotificationEvent event) {
        try {
            customerNotificationService.sendPushNotification(
                event.getUserId(),
                event.getDeviceTokens(),
                event.getSubject(),
                event.getContent(),
                event.getImageUrl(),
                event.getDeepLink(),
                event.getCustomData()
            );
            
            // Track push notification delivery
            notificationService.trackPushDelivery(
                event.getNotificationId(),
                event.getUserId(),
                event.getDeviceTokens()
            );
            
            return true;
        } catch (Exception e) {
            log.error("Failed to send push notification: {}", e.getMessage());
            return false;
        }
    }

    private boolean sendInAppNotification(UserNotificationEvent event) {
        try {
            userService.createInAppNotification(
                event.getUserId(),
                event.getNotificationType(),
                event.getSubject(),
                event.getContent(),
                event.getPriority(),
                event.getExpiresAt(),
                event.getActionButtons()
            );
            
            // Send real-time update if user is online
            if (userService.isUserOnline(event.getUserId())) {
                customerNotificationService.sendRealtimeNotification(
                    event.getUserId(),
                    event.getNotificationId(),
                    event.getContent()
                );
            }
            
            return true;
        } catch (Exception e) {
            log.error("Failed to send in-app notification: {}", e.getMessage());
            return false;
        }
    }

    private boolean sendWebhookNotification(UserNotificationEvent event) {
        try {
            String webhookUrl = preferenceService.getUserWebhookUrl(
                event.getUserId(),
                event.getNotificationType()
            );
            
            if (webhookUrl == null) {
                log.warn("No webhook URL configured for user: {} type: {}", 
                        event.getUserId(), event.getNotificationType());
                return false;
            }
            
            Map<String, Object> payload = Map.of(
                "notificationId", event.getNotificationId(),
                "userId", event.getUserId(),
                "type", event.getNotificationType(),
                "subject", event.getSubject(),
                "content", event.getContent(),
                "timestamp", event.getCreatedAt(),
                "customData", event.getCustomData()
            );
            
            customerNotificationService.sendWebhook(
                webhookUrl,
                payload,
                event.getWebhookHeaders()
            );
            
            return true;
        } catch (Exception e) {
            log.error("Failed to send webhook notification: {}", e.getMessage());
            return false;
        }
    }

    private String formatEmailContent(UserNotificationEvent event) {
        if (event.getTemplateId() != null) {
            return notificationService.renderEmailTemplate(
                event.getTemplateId(),
                event.getTemplateData()
            );
        }
        return event.getContent();
    }

    private String formatSmsContent(UserNotificationEvent event) {
        String content = event.getContent();
        // Truncate to SMS length limit
        if (content.length() > 160) {
            content = content.substring(0, 157) + "...";
        }
        return content;
    }

    private void trackNotificationDelivery(UserNotificationEvent event) {
        notificationService.trackDelivery(
            event.getNotificationId(),
            event.getUserId(),
            event.getChannel(),
            "DELIVERED",
            LocalDateTime.now()
        );
        
        // Update user notification stats
        preferenceService.updateNotificationStats(
            event.getUserId(),
            event.getNotificationType(),
            event.getChannel(),
            true
        );
    }

    private void handleNotificationFailure(UserNotificationEvent event) {
        // Track failure
        notificationService.trackDelivery(
            event.getNotificationId(),
            event.getUserId(),
            event.getChannel(),
            "FAILED",
            LocalDateTime.now()
        );
        
        // Retry with fallback channel if configured
        String fallbackChannel = preferenceService.getFallbackChannel(
            event.getUserId(),
            event.getChannel()
        );
        
        if (fallbackChannel != null && event.getRetryCount() < 3) {
            event.setChannel(fallbackChannel);
            event.setRetryCount(event.getRetryCount() + 1);
            notificationService.retryNotification(event);
        }
        
        // Update failure stats
        preferenceService.updateNotificationStats(
            event.getUserId(),
            event.getNotificationType(),
            event.getChannel(),
            false
        );
    }
}