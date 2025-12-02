package com.waqiti.crypto.lightning.entity;

import com.waqiti.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.Set;

/**
 * Entity representing a Lightning Network webhook registration
 */
@Entity
@Table(name = "lightning_webhooks", indexes = {
    @Index(name = "idx_webhook_user", columnList = "userId"),
    @Index(name = "idx_webhook_status", columnList = "status"),
    @Index(name = "idx_webhook_payment_hash", columnList = "paymentHash"),
    @Index(name = "idx_webhook_expires", columnList = "expiresAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@ToString(exclude = {"secret"})
public class WebhookEntity extends BaseEntity {

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false, length = 2048)
    private String url;

    @ElementCollection(targetClass = WebhookEventType.class)
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "webhook_events", joinColumns = @JoinColumn(name = "webhook_id"))
    @Column(name = "event_type")
    private Set<WebhookEventType> events;

    @Column(length = 512)
    private String secret;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private WebhookStatus status;

    @Column
    private String paymentHash;

    @Column
    private Instant expiresAt;

    @Column
    private Instant lastSuccessAt;

    @Column
    private Instant lastFailureAt;

    @Column
    private Integer deliveryCount;

    @Column
    private Integer failureCount;

    @Column
    private String description;

    @Column
    private Boolean enabledRetries;

    @Column
    private Integer maxRetries;

    @Column
    private Integer timeoutSeconds;

    @Column
    private String userAgent;

    @Column(columnDefinition = "TEXT")
    private String customHeaders;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        super.onCreate();
        if (status == null) {
            status = WebhookStatus.ACTIVE;
        }
        if (deliveryCount == null) {
            deliveryCount = 0;
        }
        if (failureCount == null) {
            failureCount = 0;
        }
        if (enabledRetries == null) {
            enabledRetries = true;
        }
        if (maxRetries == null) {
            maxRetries = 3;
        }
        if (timeoutSeconds == null) {
            timeoutSeconds = 30;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        super.onUpdate();
    }

    /**
     * Check if webhook is active and not expired
     */
    public boolean isActive() {
        return status == WebhookStatus.ACTIVE && 
               (expiresAt == null || expiresAt.isAfter(Instant.now()));
    }

    /**
     * Check if webhook supports a specific event type
     */
    public boolean supportsEvent(WebhookEventType eventType) {
        return events != null && events.contains(eventType);
    }

    /**
     * Calculate success rate
     */
    public double getSuccessRate() {
        if (deliveryCount == 0) {
            return 0.0;
        }
        int successCount = deliveryCount - failureCount;
        return (double) successCount / deliveryCount * 100.0;
    }

    /**
     * Check if webhook should be suspended due to failures
     */
    public boolean shouldSuspend() {
        return failureCount > 50 || (deliveryCount > 10 && getSuccessRate() < 10.0);
    }
}