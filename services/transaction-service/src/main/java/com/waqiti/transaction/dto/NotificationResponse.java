package com.waqiti.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for notification service operations.
 * Provides comprehensive status and tracking information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    private UUID notificationId;
    private UUID requestId;
    private LocalDateTime timestamp;
    
    // Status information
    private NotificationStatus status;
    private String message;
    private String errorCode;
    private List<String> errorDetails;
    
    // Delivery information
    private List<ChannelDeliveryStatus> deliveryStatuses;
    private Integer successfulDeliveries;
    private Integer failedDeliveries;
    private Integer pendingDeliveries;
    
    // Tracking information
    private String trackingId;
    private LocalDateTime estimatedDeliveryTime;
    private LocalDateTime actualDeliveryTime;
    
    // Processing metadata
    private Long processingTimeMs;
    private String processingNode;
    private String version;
    
    // Recipients information (for batch notifications)
    private Integer totalRecipients;
    private List<RecipientStatus> recipientStatuses;
    
    // Retry information
    private Integer retryCount;
    private Integer maxRetries;
    private LocalDateTime nextRetryTime;
    
    // Compliance and audit
    private Boolean auditTrailCreated;
    private String auditId;
    private List<String> complianceFlags;
    
    // Channel-specific details
    private Map<String, Object> channelMetadata;

    /**
     * Notification status enumeration
     */
    public enum NotificationStatus {
        QUEUED,           // Notification is queued for processing
        PROCESSING,       // Notification is being processed
        SENT,            // Notification has been sent
        DELIVERED,       // Notification was successfully delivered
        FAILED,          // Notification failed to send
        PARTIALLY_SENT,  // Some channels succeeded, others failed
        RETRY_SCHEDULED, // Notification is scheduled for retry
        EXPIRED,         // Notification expired before delivery
        CANCELLED        // Notification was cancelled
    }

    /**
     * Channel delivery status
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChannelDeliveryStatus {
        private String channel; // EMAIL, SMS, PUSH, etc.
        private DeliveryStatus status;
        private String statusMessage;
        private LocalDateTime deliveryTime;
        private String trackingId;
        private Integer retryCount;
        private String failureReason;
        private Map<String, Object> metadata;
        
        public enum DeliveryStatus {
            QUEUED,
            SENT,
            DELIVERED,
            READ,           // For email/in-app
            CLICKED,        // For email/push with links
            FAILED,
            BOUNCED,        // For email
            UNSUBSCRIBED,   // User unsubscribed
            BLOCKED        // Blocked by provider/user settings
        }
    }

    /**
     * Individual recipient status (for batch notifications)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecipientStatus {
        private UUID recipientId;
        private String recipientEmail;
        private String recipientPhone;
        private NotificationStatus status;
        private List<ChannelDeliveryStatus> channelStatuses;
        private String failureReason;
        private LocalDateTime processedTime;
    }

    /**
     * Factory method for successful notification response
     */
    public static NotificationResponse success(UUID notificationId, String trackingId) {
        return NotificationResponse.builder()
                .notificationId(notificationId)
                .trackingId(trackingId)
                .status(NotificationStatus.SENT)
                .message("Notification sent successfully")
                .timestamp(LocalDateTime.now())
                .auditTrailCreated(true)
                .auditId(UUID.randomUUID().toString())
                .build();
    }

    /**
     * Factory method for failed notification response
     */
    public static NotificationResponse failure(UUID notificationId, String errorCode, 
                                             String errorMessage, List<String> errorDetails) {
        return NotificationResponse.builder()
                .notificationId(notificationId)
                .status(NotificationStatus.FAILED)
                .message("Notification failed to send")
                .errorCode(errorCode)
                .errorDetails(errorDetails)
                .timestamp(LocalDateTime.now())
                .auditTrailCreated(true)
                .auditId(UUID.randomUUID().toString())
                .build();
    }

    /**
     * Factory method for partial success response
     */
    public static NotificationResponse partialSuccess(UUID notificationId, String trackingId,
                                                    List<ChannelDeliveryStatus> deliveryStatuses) {
        int successful = (int) deliveryStatuses.stream()
                .filter(status -> status.getStatus() == ChannelDeliveryStatus.DeliveryStatus.SENT ||
                               status.getStatus() == ChannelDeliveryStatus.DeliveryStatus.DELIVERED)
                .count();
        
        int failed = deliveryStatuses.size() - successful;
        
        return NotificationResponse.builder()
                .notificationId(notificationId)
                .trackingId(trackingId)
                .status(NotificationStatus.PARTIALLY_SENT)
                .message(String.format("Notification partially sent: %d successful, %d failed", 
                                     successful, failed))
                .deliveryStatuses(deliveryStatuses)
                .successfulDeliveries(successful)
                .failedDeliveries(failed)
                .timestamp(LocalDateTime.now())
                .auditTrailCreated(true)
                .auditId(UUID.randomUUID().toString())
                .build();
    }

    /**
     * Determines if the notification was successfully sent to at least one channel
     */
    public boolean isSuccessful() {
        return status == NotificationStatus.SENT || 
               status == NotificationStatus.DELIVERED ||
               status == NotificationStatus.PARTIALLY_SENT;
    }

    /**
     * Determines if the notification completely failed
     */
    public boolean isFailed() {
        return status == NotificationStatus.FAILED || 
               status == NotificationStatus.EXPIRED ||
               status == NotificationStatus.CANCELLED;
    }

    /**
     * Determines if the notification is still being processed
     */
    public boolean isPending() {
        return status == NotificationStatus.QUEUED ||
               status == NotificationStatus.PROCESSING ||
               status == NotificationStatus.RETRY_SCHEDULED;
    }

    /**
     * Gets the overall delivery success rate
     */
    public double getSuccessRate() {
        if (totalRecipients == null || totalRecipients == 0) {
            // Single recipient notification
            if (deliveryStatuses != null && !deliveryStatuses.isEmpty()) {
                long successful = deliveryStatuses.stream()
                        .filter(status -> status.getStatus() == ChannelDeliveryStatus.DeliveryStatus.SENT ||
                                       status.getStatus() == ChannelDeliveryStatus.DeliveryStatus.DELIVERED)
                        .count();
                return (double) successful / deliveryStatuses.size();
            }
            return isSuccessful() ? 1.0 : 0.0;
        }
        
        // Multi-recipient notification
        if (successfulDeliveries != null) {
            return (double) successfulDeliveries / totalRecipients;
        }
        
        return 0.0;
    }
}