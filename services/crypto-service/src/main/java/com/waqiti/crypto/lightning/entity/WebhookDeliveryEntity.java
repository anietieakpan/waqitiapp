package com.waqiti.crypto.lightning.entity;

import com.waqiti.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Entity representing a Lightning webhook delivery attempt
 */
@Entity
@Table(name = "lightning_webhook_deliveries", indexes = {
    @Index(name = "idx_delivery_webhook", columnList = "webhookId"),
    @Index(name = "idx_delivery_status", columnList = "status"),
    @Index(name = "idx_delivery_created", columnList = "createdAt"),
    @Index(name = "idx_delivery_event_type", columnList = "eventType")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@ToString(exclude = {"payload", "responseBody"})
public class WebhookDeliveryEntity extends BaseEntity {

    @Column(nullable = false)
    private String webhookId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private WebhookEventType eventType;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private WebhookDeliveryStatus status;

    @Column
    private Integer responseCode;

    @Column(columnDefinition = "TEXT")
    private String responseBody;

    @Column
    private String responseHeaders;

    @Column
    private Integer attemptCount;

    @Column
    private Instant attemptedAt;

    @Column
    private Instant deliveredAt;

    @Column
    private Long durationMs;

    @Column
    private String errorMessage;

    @Column
    private String userAgent;

    @Column
    private Instant nextRetryAt;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        super.onCreate();
        if (status == null) {
            status = WebhookDeliveryStatus.PENDING;
        }
        if (attemptCount == null) {
            attemptCount = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        super.onUpdate();
    }

    /**
     * Check if delivery was successful
     */
    public boolean isSuccessful() {
        return status == WebhookDeliveryStatus.DELIVERED;
    }

    /**
     * Check if delivery failed permanently
     */
    public boolean isFailed() {
        return status == WebhookDeliveryStatus.FAILED;
    }

    /**
     * Check if delivery is still pending
     */
    public boolean isPending() {
        return status == WebhookDeliveryStatus.PENDING;
    }

    /**
     * Check if delivery should be retried
     */
    public boolean shouldRetry(int maxRetries) {
        return status == WebhookDeliveryStatus.PENDING && 
               attemptCount < maxRetries &&
               (nextRetryAt == null || nextRetryAt.isBefore(Instant.now()));
    }

    /**
     * Calculate next retry time with exponential backoff
     */
    public void calculateNextRetry() {
        if (attemptCount == 0) {
            nextRetryAt = Instant.now().plusSeconds(30); // 30 seconds
        } else {
            long backoffSeconds = Math.min(300, 30 * (long) Math.pow(2, attemptCount - 1)); // Max 5 minutes
            nextRetryAt = Instant.now().plusSeconds(backoffSeconds);
        }
    }
}