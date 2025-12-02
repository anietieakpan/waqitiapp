package com.waqiti.notification.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditService;
import com.waqiti.notification.service.EmailNotificationService;
import com.waqiti.notification.service.NotificationTemplateService;
import com.waqiti.notification.service.NotificationDeliveryService;
import com.waqiti.notification.service.NotificationPreferenceService;
import com.waqiti.common.exception.NotificationProcessingException;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka Consumer for Email Notification Events
 * Handles email processing, delivery tracking, and notification preferences
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationEventsConsumer {
    
    private final EmailNotificationService emailService;
    private final NotificationTemplateService templateService;
    private final NotificationDeliveryService deliveryService;
    private final NotificationPreferenceService preferenceService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"email-notification-events", "email-send-requested", "email-delivered", "email-bounced"},
        groupId = "notification-service-email-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000)
    )
    @Transactional
    public void handleEmailNotificationEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        UUID notificationId = null;
        UUID customerId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            notificationId = UUID.fromString((String) event.get("notificationId"));
            customerId = UUID.fromString((String) event.get("customerId"));
            eventType = (String) event.get("eventType");
            String emailAddress = (String) event.get("emailAddress");
            String subject = (String) event.get("subject");
            String templateId = (String) event.get("templateId");
            String notificationType = (String) event.get("notificationType"); // SECURITY, TRANSACTION, MARKETING, etc.
            String priority = (String) event.get("priority"); // LOW, MEDIUM, HIGH, URGENT
            LocalDateTime timestamp = LocalDateTime.parse((String) event.get("timestamp"));
            
            // Email content
            String htmlContent = (String) event.get("htmlContent");
            String textContent = (String) event.get("textContent");
            Map<String, Object> templateVariables = (Map<String, Object>) event.get("templateVariables");
            
            // Delivery information
            String deliveryStatus = (String) event.get("deliveryStatus"); // PENDING, SENT, DELIVERED, BOUNCED, FAILED
            String messageId = (String) event.get("messageId");
            String providerName = (String) event.get("providerName"); // SENDGRID, AWS_SES, MAILGUN
            LocalDateTime sentTime = event.containsKey("sentTime") ?
                    LocalDateTime.parse((String) event.get("sentTime")) : null;
            LocalDateTime deliveredTime = event.containsKey("deliveredTime") ?
                    LocalDateTime.parse((String) event.get("deliveredTime")) : null;
            
            // Error information
            String bounceType = (String) event.get("bounceType"); // HARD, SOFT
            String bounceReason = (String) event.get("bounceReason");
            String errorCode = (String) event.get("errorCode");
            String errorMessage = (String) event.get("errorMessage");
            
            // Engagement tracking
            Boolean trackOpens = (Boolean) event.getOrDefault("trackOpens", true);
            Boolean trackClicks = (Boolean) event.getOrDefault("trackClicks", true);
            String unsubscribeLink = (String) event.get("unsubscribeLink");
            
            log.info("Processing email notification event - NotificationId: {}, CustomerId: {}, Type: {}, Email: {}", 
                    notificationId, customerId, eventType, emailAddress);
            
            // Step 1: Validate email notification data
            Map<String, Object> validationResult = emailService.validateEmailData(
                    notificationId, customerId, emailAddress, subject, templateId,
                    notificationType, priority, timestamp);
            
            if ("INVALID".equals(validationResult.get("status"))) {
                emailService.rejectEmail(notificationId, 
                        (String) validationResult.get("reason"), timestamp);
                log.warn("Email notification validation failed: {}", validationResult.get("reason"));
                acknowledgment.acknowledge();
                return;
            }
            
            // Step 2: Check notification preferences
            Map<String, Object> preferences = preferenceService.getEmailPreferences(
                    customerId, notificationType, priority, timestamp);
            
            Boolean canSendEmail = (Boolean) preferences.get("canSendEmail");
            if (!canSendEmail) {
                emailService.suppressEmail(notificationId, "User preferences", timestamp);
                log.info("Email suppressed due to user preferences");
                acknowledgment.acknowledge();
                return;
            }
            
            // Step 3: Rate limiting and frequency controls
            Map<String, Object> rateLimitCheck = emailService.checkRateLimit(
                    customerId, emailAddress, notificationType, timestamp);
            
            if ("RATE_LIMITED".equals(rateLimitCheck.get("status"))) {
                emailService.deferEmail(notificationId, 
                        (LocalDateTime) rateLimitCheck.get("retryAfter"), timestamp);
                log.info("Email deferred due to rate limiting");
                acknowledgment.acknowledge();
                return;
            }
            
            // Step 4: Process based on event type
            switch (eventType) {
                case "EMAIL_SEND_REQUESTED":
                    emailService.processEmailSendRequest(notificationId, customerId, emailAddress,
                            subject, templateId, htmlContent, textContent, templateVariables,
                            notificationType, priority, trackOpens, trackClicks, timestamp);
                    break;
                    
                case "EMAIL_DELIVERED":
                    emailService.processEmailDelivery(notificationId, messageId, providerName,
                            deliveredTime, timestamp);
                    break;
                    
                case "EMAIL_BOUNCED":
                    emailService.processEmailBounce(notificationId, customerId, emailAddress,
                            bounceType, bounceReason, errorCode, timestamp);
                    break;
                    
                case "EMAIL_OPENED":
                    emailService.processEmailOpen(notificationId, customerId,
                            (LocalDateTime) event.get("openedTime"), 
                            (String) event.get("userAgent"), timestamp);
                    break;
                    
                case "EMAIL_CLICKED":
                    emailService.processEmailClick(notificationId, customerId,
                            (String) event.get("clickedUrl"),
                            (LocalDateTime) event.get("clickedTime"), timestamp);
                    break;
                    
                default:
                    emailService.processGenericEmailEvent(notificationId, eventType, event, timestamp);
            }
            
            // Step 5: Template processing and personalization
            if ("EMAIL_SEND_REQUESTED".equals(eventType) && templateId != null) {
                Map<String, String> processedContent = templateService.processEmailTemplate(
                        templateId, templateVariables, customerId, timestamp);
                
                htmlContent = processedContent.get("htmlContent");
                textContent = processedContent.get("textContent");
                subject = processedContent.get("subject");
            }
            
            // Step 6: Content validation and spam checking
            if ("EMAIL_SEND_REQUESTED".equals(eventType)) {
                Map<String, Object> contentValidation = emailService.validateEmailContent(
                        subject, htmlContent, textContent, timestamp);
                
                if ("SPAM_RISK".equals(contentValidation.get("status"))) {
                    emailService.flagForReview(notificationId, contentValidation, timestamp);
                    log.warn("Email flagged for spam risk review");
                }
            }
            
            // Step 7: Delivery provider selection
            if ("EMAIL_SEND_REQUESTED".equals(eventType)) {
                String selectedProvider = deliveryService.selectOptimalProvider(
                        emailAddress, notificationType, priority, timestamp);
                
                emailService.assignDeliveryProvider(notificationId, selectedProvider, timestamp);
            }
            
            // Step 8: Handle bounces and delivery failures
            if ("EMAIL_BOUNCED".equals(eventType)) {
                deliveryService.handleEmailBounce(customerId, emailAddress, bounceType,
                        bounceReason, timestamp);
                
                // Update email reputation
                if ("HARD".equals(bounceType)) {
                    emailService.markEmailInvalid(customerId, emailAddress, timestamp);
                }
            }
            
            // Step 9: Engagement tracking
            if ("EMAIL_OPENED".equals(eventType) || "EMAIL_CLICKED".equals(eventType)) {
                emailService.updateEngagementMetrics(notificationId, customerId,
                        notificationType, eventType, timestamp);
            }
            
            // Step 10: Unsubscribe handling
            if (event.containsKey("unsubscribeRequested") && 
                (Boolean) event.get("unsubscribeRequested")) {
                preferenceService.processUnsubscribe(customerId, emailAddress,
                        notificationType, timestamp);
            }
            
            // Step 11: A/B testing and optimization
            if ("EMAIL_SEND_REQUESTED".equals(eventType)) {
                emailService.recordABTestMetrics(notificationId, templateId,
                        subject, notificationType, timestamp);
            }
            
            // Step 12: Delivery status updates
            deliveryService.updateDeliveryStatus(notificationId, deliveryStatus,
                    sentTime, deliveredTime, errorMessage, timestamp);
            
            // Step 13: Audit logging
            auditService.auditFinancialEvent(
                    "EMAIL_NOTIFICATION_EVENT_PROCESSED",
                    customerId.toString(),
                    String.format("Email notification event processed - Type: %s, Status: %s, Category: %s", 
                            eventType, deliveryStatus, notificationType),
                    Map.of(
                            "notificationId", notificationId.toString(),
                            "customerId", customerId.toString(),
                            "eventType", eventType,
                            "emailAddress", emailAddress.replaceAll("(.{3}).*@", "$1***@"), // Masked for security
                            "subject", subject != null ? subject.substring(0, Math.min(50, subject.length())) : "N/A",
                            "notificationType", notificationType,
                            "priority", priority,
                            "deliveryStatus", deliveryStatus,
                            "templateId", templateId != null ? templateId : "N/A",
                            "providerName", providerName != null ? providerName : "N/A",
                            "bounceType", bounceType != null ? bounceType : "N/A",
                            "trackOpens", trackOpens.toString(),
                            "trackClicks", trackClicks.toString(),
                            "canSendEmail", canSendEmail.toString()
                    )
            );
            
            acknowledgment.acknowledge();
            log.info("Successfully processed email notification event - NotificationId: {}, EventType: {}, Status: {}", 
                    notificationId, eventType, deliveryStatus);
            
        } catch (Exception e) {
            log.error("Email notification event processing failed - NotificationId: {}, CustomerId: {}, Error: {}", 
                    notificationId, customerId, e.getMessage(), e);
            throw new NotificationProcessingException("Email notification event processing failed", e);
        }
    }
}