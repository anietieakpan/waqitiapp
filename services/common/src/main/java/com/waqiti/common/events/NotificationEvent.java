package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Notification event for tracking communications
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent extends FinancialEvent {

    @Getter(AccessLevel.NONE)
    private UUID eventId;
    private UUID notificationId;

    @Getter(AccessLevel.NONE)
    private String eventType;
    private String notificationType;
    private String channel;
    private String status;
    private String recipient;
    private String subject;
    private String content;
    private String templateId;
    private Map<String, Object> templateVariables;
    private List<String> attachments;
    private Instant timestamp;
    private Instant scheduledAt;
    private Instant sentAt;
    private Instant deliveredAt;
    private Instant readAt;
    private String failureReason;
    private Integer retryCount;
    private Map<String, Object> metadata;

    @Getter(AccessLevel.NONE)
    private UUID correlationId;

    @Getter(AccessLevel.NONE)
    private String priority;

    // Override DomainEvent methods to convert UUID to String
    public String getEventId() {
        return eventId != null ? eventId.toString() : null;
    }

    public String getCorrelationId() {
        return correlationId != null ? correlationId.toString() : null;
    }

    public Integer getPriority() {
        // Convert string priority to integer (HIGH=1, MEDIUM=2, LOW=3)
        if (priority == null) return 3;
        switch (priority.toUpperCase()) {
            case "HIGH": case "CRITICAL": return 1;
            case "MEDIUM": case "NORMAL": return 2;
            case "LOW": default: return 3;
        }
    }

    /**
     * Notification event types
     */
    public enum EventType {
        NOTIFICATION_CREATED,
        NOTIFICATION_SCHEDULED,
        NOTIFICATION_SENT,
        NOTIFICATION_DELIVERED,
        NOTIFICATION_READ,
        NOTIFICATION_FAILED,
        NOTIFICATION_RETRIED,
        NOTIFICATION_CANCELLED,
        NOTIFICATION_EXPIRED,
        NOTIFICATION_BOUNCED,
        NOTIFICATION_CLICKED,
        NOTIFICATION_UNSUBSCRIBED
    }
    
    /**
     * Notification channels
     */
    public enum Channel {
        EMAIL,
        SMS,
        PUSH,
        IN_APP,
        WEBHOOK,
        SLACK,
        TEAMS
    }
    
    /**
     * Create notification created event
     */
    public static NotificationEvent created(UUID userId, String channel, String recipient, String subject) {
        return NotificationEvent.builder()
            .eventId(UUID.randomUUID())
            .notificationId(UUID.randomUUID())
            .userId(userId)
            .eventType(EventType.NOTIFICATION_CREATED.name())
            .channel(channel)
            .recipient(recipient)
            .subject(subject)
            .status("CREATED")
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Create notification sent event
     */
    public static NotificationEvent sent(UUID notificationId, UUID userId, String channel, String recipient) {
        return NotificationEvent.builder()
            .eventId(UUID.randomUUID())
            .notificationId(notificationId)
            .userId(userId)
            .eventType(EventType.NOTIFICATION_SENT.name())
            .channel(channel)
            .recipient(recipient)
            .status("SENT")
            .sentAt(Instant.now())
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Create notification delivered event
     */
    public static NotificationEvent delivered(UUID notificationId, UUID userId) {
        return NotificationEvent.builder()
            .eventId(UUID.randomUUID())
            .notificationId(notificationId)
            .userId(userId)
            .eventType(EventType.NOTIFICATION_DELIVERED.name())
            .status("DELIVERED")
            .deliveredAt(Instant.now())
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Create notification failed event
     */
    public static NotificationEvent failed(UUID notificationId, UUID userId, String failureReason) {
        return NotificationEvent.builder()
            .eventId(UUID.randomUUID())
            .notificationId(notificationId)
            .userId(userId)
            .eventType(EventType.NOTIFICATION_FAILED.name())
            .status("FAILED")
            .failureReason(failureReason)
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Check if notification is sent
     */
    public boolean isSent() {
        return sentAt != null || "SENT".equals(status) || "DELIVERED".equals(status);
    }
    
    /**
     * Check if notification is delivered
     */
    public boolean isDelivered() {
        return deliveredAt != null || "DELIVERED".equals(status);
    }
    
    /**
     * Check if notification is read
     */
    public boolean isRead() {
        return readAt != null || "READ".equals(status);
    }
    
    /**
     * Check if notification failed
     */
    public boolean isFailed() {
        return "FAILED".equals(status) || "BOUNCED".equals(status);
    }
    
    /**
     * Check if notification is high priority
     */
    public boolean isHighPriority() {
        return "HIGH".equals(priority) || "URGENT".equals(priority);
    }
    
    /**
     * Get delivery time in seconds
     */
    public Long getDeliveryTimeSeconds() {
        if (sentAt != null && deliveredAt != null) {
            return deliveredAt.getEpochSecond() - sentAt.getEpochSecond();
        }
        return null;
    }
    
    /**
     * Get event age in seconds
     */
    public long getAgeInSeconds() {
        if (timestamp == null) {
            return 0;
        }
        return Instant.now().getEpochSecond() - timestamp.getEpochSecond();
    }
}