package com.waqiti.webhook.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Webhook entity for tracking webhook deliveries
 */
@Entity
@Table(name = "webhooks", indexes = {
    @Index(name = "idx_webhook_status", columnList = "status"),
    @Index(name = "idx_webhook_created", columnList = "created_at"),
    @Index(name = "idx_webhook_endpoint", columnList = "endpoint_url"),
    @Index(name = "idx_webhook_event", columnList = "event_type"),
    @Index(name = "idx_webhook_retry", columnList = "status,next_retry_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Webhook {
    
    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;
    
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;
    
    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;
    
    @Column(name = "endpoint_url", nullable = false, length = 500)
    private String endpointUrl;
    
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private WebhookStatus status;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private WebhookPriority priority = WebhookPriority.NORMAL;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;
    
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;
    
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;
    
    @Column(name = "failed_at")
    private LocalDateTime failedAt;
    
    @Column(name = "expired_at")
    private LocalDateTime expiredAt;
    
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;
    
    @Column(name = "last_error_message", length = 1000)
    private String lastErrorMessage;
    
    @Column(name = "last_response_code")
    private Integer lastResponseCode;
    
    @Column(name = "total_delivery_time_ms")
    private Long totalDeliveryTimeMs;
    
    @Column(name = "subscription_id", length = 36)
    private String subscriptionId;
    
    @Column(name = "tenant_id", length = 36)
    private String tenantId;
    
    @Version
    @Column(name = "version")
    private Long version;
    
    /**
     * Check if webhook can be retried
     */
    public boolean canRetry(int maxAttempts) {
        return status == WebhookStatus.PENDING_RETRY && 
               attemptCount < maxAttempts &&
               (nextRetryAt == null || LocalDateTime.now().isAfter(nextRetryAt));
    }
    
    /**
     * Check if webhook is expired
     */
    public boolean isExpired(int maxAgeHours) {
        long hoursSinceCreation = java.time.Duration.between(createdAt, LocalDateTime.now()).toHours();
        return hoursSinceCreation >= maxAgeHours;
    }
    
    /**
     * Get age in hours
     */
    public long getAgeInHours() {
        return java.time.Duration.between(createdAt, LocalDateTime.now()).toHours();
    }
}