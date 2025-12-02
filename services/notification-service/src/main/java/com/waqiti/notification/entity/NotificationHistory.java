package com.waqiti.notification.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification_history", indexes = {
    @Index(name = "idx_user_id", columnList = "userId"),
    @Index(name = "idx_template_code", columnList = "templateCode"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_scheduled_for", columnList = "scheduledFor"),
    @Index(name = "idx_created_at", columnList = "createdAt"),
    @Index(name = "idx_user_template_created", columnList = "userId, templateCode, createdAt"),
    @Index(name = "idx_notif_status_scheduled", columnList = "status, scheduledFor"),
    @Index(name = "idx_notif_user_status", columnList = "userId, status"),
    @Index(name = "idx_notif_retry_next", columnList = "retryCount, nextRetryAt"),
    @Index(name = "idx_notif_user_read", columnList = "userId, isRead"),
    @Index(name = "idx_notif_user_read_created", columnList = "userId, isRead, createdAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationHistory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private UUID userId;
    
    @Column(nullable = false, length = 100)
    private String templateCode;
    
    @Column(nullable = false, length = 50)
    private String type;
    
    @Column(nullable = false, length = 200)
    private String deliveryChannels;
    
    @Column(length = 50)
    private String status;
    
    @Column(length = 100)
    private String subject;
    
    @Column(columnDefinition = "TEXT")
    private String content;
    
    @Column(columnDefinition = "TEXT")
    private String templateParameters;
    
    @Column
    private LocalDateTime scheduledFor;
    
    @Column
    private LocalDateTime sentAt;
    
    @Column
    private LocalDateTime deliveredAt;
    
    @Column
    private LocalDateTime readAt;
    
    @Column(length = 500)
    private String errorMessage;
    
    @Column
    private Integer retryCount;
    
    @Column
    private LocalDateTime nextRetryAt;
    
    @Column(length = 50)
    private String priority;
    
    @Column(length = 100)
    private String campaignId;
    
    @Column(length = 100)
    private String batchId;
    
    @Column
    private Boolean isRead;
    
    @Column
    private Boolean isClicked;
    
    @Column
    private LocalDateTime clickedAt;
    
    @Column(length = 200)
    private String deviceInfo;
    
    @Column(length = 50)
    private String locale;
    
    @Column(length = 50)
    private String timezone;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    // Convenience methods
    
    public boolean isScheduled() {
        return "SCHEDULED".equals(status);
    }
    
    public boolean isSent() {
        return "SENT".equals(status);
    }
    
    public boolean isDelivered() {
        return "DELIVERED".equals(status);
    }
    
    public boolean isFailed() {
        return "FAILED".equals(status);
    }
    
    public boolean canRetry() {
        return isFailed() && (retryCount == null || retryCount < 3) && 
               (nextRetryAt == null || nextRetryAt.isBefore(LocalDateTime.now()));
    }
    
    public void incrementRetryCount() {
        this.retryCount = (this.retryCount == null) ? 1 : this.retryCount + 1;
        // Exponential backoff: 5 min, 15 min, 45 min
        int backoffMinutes = 5 * (int) Math.pow(3, this.retryCount - 1);
        this.nextRetryAt = LocalDateTime.now().plusMinutes(backoffMinutes);
    }
    
    public void markAsRead() {
        this.isRead = true;
        this.readAt = LocalDateTime.now();
    }
    
    public void markAsClicked() {
        this.isClicked = true;
        this.clickedAt = LocalDateTime.now();
        if (this.isRead == null || !this.isRead) {
            markAsRead();
        }
    }
    
    /**
     * Gets the user ID as String for compatibility
     */
    public String getUserIdAsString() {
        return userId != null ? userId.toString() : null;
    }
    
    /**
     * Gets the status with optional default parameter
     */
    public String getStatus(String defaultStatus) {
        return status != null ? status : defaultStatus;
    }
    
    /**
     * Sets the user ID from String
     */
    public void setUserId(String userIdString) {
        this.userId = userIdString != null ? UUID.fromString(userIdString) : null;
    }
}