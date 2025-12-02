package com.waqiti.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Request DTO for sending notifications through the notification service.
 * Supports multi-channel notifications with rich content and personalization.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequest {

    @NotNull(message = "Notification ID is required")
    private UUID notificationId;

    @NotNull(message = "Recipient ID is required") 
    private UUID recipientId;

    @NotBlank(message = "Notification type is required")
    private String notificationType;

    @NotBlank(message = "Subject is required")
    private String subject;

    @NotBlank(message = "Message body is required")
    private String messageBody;

    // Channel configuration
    private List<NotificationChannel> channels;
    private NotificationPriority priority;
    private Boolean requiresDeliveryConfirmation;

    // Scheduling
    private LocalDateTime scheduledTime;
    private String timezone;
    private Boolean isImmediate;

    // Content and personalization
    private Map<String, Object> templateVariables;
    private String templateId;
    private String languageCode;
    private String locale;

    // Rich content
    private List<NotificationAttachment> attachments;
    private Map<String, String> customHeaders;
    private String htmlContent;
    private String plainTextContent;

    // Business context
    private UUID transactionId;
    private String businessCategory;
    private String businessSubcategory;
    
    // Tracking and analytics
    private String campaignId;
    private String referenceId;
    private Map<String, String> trackingTags;

    // Compliance and security
    private Boolean containsSensitiveData;
    private String encryptionLevel;
    private Boolean requiresAuditTrail;
    private List<String> complianceFlags;

    // Retry and delivery
    private Integer maxRetryAttempts;
    private Long retryIntervalSeconds;
    private LocalDateTime expirationTime;

    // User preferences override
    private Boolean overrideUserPreferences;
    private String reasonForOverride;

    /**
     * Notification channels enum
     */
    public enum NotificationChannel {
        EMAIL,
        SMS,
        PUSH,
        IN_APP,
        WEBHOOK,
        VOICE,
        WHATSAPP,
        SLACK,
        TEAMS
    }

    /**
     * Notification priority levels
     */
    public enum NotificationPriority {
        LOW(1),
        NORMAL(2), 
        HIGH(3),
        URGENT(4),
        CRITICAL(5);

        private final int level;

        NotificationPriority(int level) {
            this.level = level;
        }

        public int getLevel() {
            return level;
        }
    }

    /**
     * Notification attachment
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationAttachment {
        private String filename;
        private String contentType;
        private String content; // Base64 encoded
        private Long size;
        private String url; // Alternative to inline content
        private Boolean isInline;
        private String contentId; // For inline images in HTML
    }

    /**
     * Factory method for transaction notifications
     */
    public static NotificationRequest forTransaction(UUID recipientId, UUID transactionId, 
                                                   String type, String subject, String body) {
        return NotificationRequest.builder()
                .notificationId(UUID.randomUUID())
                .recipientId(recipientId)
                .transactionId(transactionId)
                .notificationType(type)
                .subject(subject)
                .messageBody(body)
                .channels(List.of(NotificationChannel.EMAIL, NotificationChannel.PUSH))
                .priority(NotificationPriority.NORMAL)
                .isImmediate(true)
                .requiresDeliveryConfirmation(true)
                .languageCode("en")
                .locale("en_US")
                .maxRetryAttempts(3)
                .retryIntervalSeconds(300L)
                .build();
    }

    /**
     * Factory method for urgent fraud alerts
     */
    public static NotificationRequest forFraudAlert(UUID recipientId, UUID transactionId, 
                                                   String alertMessage) {
        return NotificationRequest.builder()
                .notificationId(UUID.randomUUID())
                .recipientId(recipientId)
                .transactionId(transactionId)
                .notificationType("FRAUD_ALERT")
                .subject("Security Alert: Suspicious Transaction Detected")
                .messageBody(alertMessage)
                .channels(List.of(NotificationChannel.SMS, NotificationChannel.EMAIL, NotificationChannel.PUSH))
                .priority(NotificationPriority.CRITICAL)
                .isImmediate(true)
                .requiresDeliveryConfirmation(true)
                .overrideUserPreferences(true)
                .reasonForOverride("Security alert")
                .containsSensitiveData(true)
                .requiresAuditTrail(true)
                .complianceFlags(List.of("FRAUD_ALERT", "SECURITY_NOTIFICATION"))
                .maxRetryAttempts(5)
                .retryIntervalSeconds(60L)
                .build();
    }

    /**
     * Factory method for compliance notifications
     */
    public static NotificationRequest forComplianceAlert(UUID recipientId, String complianceType, 
                                                        String message) {
        return NotificationRequest.builder()
                .notificationId(UUID.randomUUID())
                .recipientId(recipientId)
                .notificationType("COMPLIANCE_ALERT")
                .subject("Compliance Alert: " + complianceType)
                .messageBody(message)
                .channels(List.of(NotificationChannel.EMAIL))
                .priority(NotificationPriority.HIGH)
                .isImmediate(true)
                .requiresDeliveryConfirmation(true)
                .containsSensitiveData(true)
                .requiresAuditTrail(true)
                .complianceFlags(List.of("COMPLIANCE_ALERT", complianceType))
                .maxRetryAttempts(3)
                .retryIntervalSeconds(300L)
                .build();
    }

    /**
     * Validates if the request has all required fields
     */
    public boolean isValid() {
        return notificationId != null &&
               recipientId != null &&
               notificationType != null && !notificationType.trim().isEmpty() &&
               subject != null && !subject.trim().isEmpty() &&
               messageBody != null && !messageBody.trim().isEmpty() &&
               (channels != null && !channels.isEmpty());
    }

    /**
     * Determines if this is a high-priority notification
     */
    public boolean isHighPriority() {
        return priority != null && priority.getLevel() >= NotificationPriority.HIGH.getLevel();
    }
}