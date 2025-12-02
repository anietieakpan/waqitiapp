package com.waqiti.notification.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "push_notification_logs", indexes = {
    @Index(name = "idx_log_notification_id", columnList = "notification_id"),
    @Index(name = "idx_log_user_id", columnList = "user_id"),
    @Index(name = "idx_log_type", columnList = "type"),
    @Index(name = "idx_log_status", columnList = "status"),
    @Index(name = "idx_log_sent_at", columnList = "sent_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushNotificationLog {
    
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private String id;
    
    @Column(name = "notification_id", nullable = false)
    private String notificationId;
    
    @Column(name = "user_id")
    private String userId;
    
    @Column(name = "device_token_id")
    private String deviceTokenId;
    
    @Column(name = "topic")
    private String topic;
    
    @Column(name = "type", nullable = false)
    private String type;
    
    @Column(name = "title", nullable = false)
    private String title;
    
    @Column(name = "body", columnDefinition = "TEXT")
    private String body;
    
    @Column(name = "image_url")
    private String imageUrl;
    
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "notification_data", joinColumns = @JoinColumn(name = "log_id"))
    @MapKeyColumn(name = "data_key")
    @Column(name = "data_value")
    private Map<String, String> data;
    
    @Column(name = "status", nullable = false)
    private String status; // SENT, FAILED, PENDING
    
    @Column(name = "fcm_message_id")
    private String fcmMessageId;
    
    @Column(name = "error_code")
    private String errorCode;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "sent_count")
    private Integer sentCount;
    
    @Column(name = "failed_count")
    private Integer failedCount;
    
    @Column(name = "device_count")
    private Integer deviceCount;
    
    @Column(name = "priority")
    private String priority;
    
    @Column(name = "ttl")
    private Integer ttl;
    
    @CreationTimestamp
    @Column(name = "sent_at", nullable = false, updatable = false)
    private LocalDateTime sentAt;
    
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;
    
    @Column(name = "read_at")
    private LocalDateTime readAt;
    
    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;
    
    @Column(name = "last_retry_at")
    private LocalDateTime lastRetryAt;
}