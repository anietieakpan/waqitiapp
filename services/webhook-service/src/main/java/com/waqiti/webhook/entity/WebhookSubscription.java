package com.waqiti.webhook.entity;

import com.waqiti.webhook.model.WebhookStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * WebhookSubscription entity
 *
 * Represents a customer's subscription to webhook events.
 * Customers can subscribe to specific event types and receive
 * HTTP POST callbacks to their specified URL.
 *
 * PRODUCTION-GRADE FEATURES:
 * - Multi-event type support
 * - Custom headers configuration
 * - Retry configuration per subscription
 * - Secret-based HMAC signature verification
 * - Success/failure tracking
 * - Optimistic locking with @Version
 * - Audit fields for compliance
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-10-20
 */
@Entity
@Table(name = "webhook_subscriptions", indexes = {
    @Index(name = "idx_subscription_user_id", columnList = "user_id"),
    @Index(name = "idx_subscription_client_id", columnList = "client_id"),
    @Index(name = "idx_subscription_status", columnList = "status"),
    @Index(name = "idx_subscription_active", columnList = "is_active"),
    @Index(name = "idx_subscription_url", columnList = "url"),
    @Index(name = "idx_subscription_created", columnList = "created_at"),
    @Index(name = "idx_subscription_tenant", columnList = "tenant_id")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookSubscription {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    /**
     * User ID who owns this subscription
     */
    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    /**
     * Client/Application ID (for multi-client scenarios)
     */
    @Column(name = "client_id", length = 36)
    private String clientId;

    /**
     * Tenant ID (for multi-tenancy support)
     */
    @Column(name = "tenant_id", length = 36)
    private String tenantId;

    /**
     * Webhook endpoint URL (where callbacks will be sent)
     */
    @Column(name = "url", nullable = false, length = 500)
    private String url;

    /**
     * Secret key for HMAC-SHA256 signature generation
     * Customer verifies webhook authenticity using this
     */
    @Column(name = "secret", length = 500)
    private String secret;

    /**
     * Event types this subscription listens to
     * Examples: PAYMENT_CREATED, PAYMENT_COMPLETED, USER_REGISTERED, etc.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "webhook_subscription_event_types",
        joinColumns = @JoinColumn(name = "subscription_id")
    )
    @Column(name = "event_type", length = 100)
    @Builder.Default
    private Set<String> eventTypes = new HashSet<>();

    /**
     * Subscription active/inactive flag
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    /**
     * Subscription status
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private WebhookStatus status = WebhookStatus.ACTIVE;

    /**
     * Human-readable description
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * Custom headers to include in webhook requests
     * Stored as key-value pairs
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "webhook_subscription_headers",
        joinColumns = @JoinColumn(name = "subscription_id")
    )
    @MapKeyColumn(name = "header_name", length = 100)
    @Column(name = "header_value", length = 500)
    @Builder.Default
    private Map<String, String> headers = new HashMap<>();

    /**
     * HTTP timeout in milliseconds (default: 30 seconds)
     */
    @Column(name = "timeout_ms", nullable = false)
    @Builder.Default
    private Integer timeout = 30000;

    /**
     * Maximum retry attempts for failed deliveries
     */
    @Column(name = "max_retries", nullable = false)
    @Builder.Default
    private Integer maxRetries = 3;

    /**
     * When the subscription was created
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * When the subscription was last updated
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * When the last webhook was delivered (successful or failed)
     */
    @Column(name = "last_delivery_at")
    private LocalDateTime lastDeliveryAt;

    /**
     * Total successful deliveries count
     */
    @Column(name = "successful_deliveries", nullable = false)
    @Builder.Default
    private Long successfulDeliveries = 0L;

    /**
     * Total failed deliveries count
     */
    @Column(name = "failed_deliveries", nullable = false)
    @Builder.Default
    private Long failedDeliveries = 0L;

    /**
     * Optimistic locking version
     * Prevents concurrent modification conflicts
     */
    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    // ========== BUSINESS LOGIC METHODS ==========

    /**
     * Check if subscription is enabled for specific event type
     */
    public boolean isEnabledForEvent(String eventType) {
        return isActive &&
               status == WebhookStatus.ACTIVE &&
               eventTypes != null &&
               eventTypes.contains(eventType);
    }

    /**
     * Check if subscription has exceeded failure threshold
     */
    public boolean hasExceededFailureThreshold(double thresholdPercent) {
        long totalDeliveries = successfulDeliveries + failedDeliveries;
        if (totalDeliveries == 0) {
            return false;
        }
        double failureRate = (double) failedDeliveries / totalDeliveries * 100.0;
        return failureRate >= thresholdPercent;
    }

    /**
     * Get success rate as percentage
     */
    public double getSuccessRate() {
        long totalDeliveries = successfulDeliveries + failedDeliveries;
        if (totalDeliveries == 0) {
            return 0.0;
        }
        return (double) successfulDeliveries / totalDeliveries * 100.0;
    }

    /**
     * Get failure rate as percentage
     */
    public double getFailureRate() {
        return 100.0 - getSuccessRate();
    }

    /**
     * Get total delivery attempts
     */
    public long getTotalDeliveries() {
        return successfulDeliveries + failedDeliveries;
    }

    /**
     * Increment successful delivery counter
     */
    public void incrementSuccessfulDeliveries() {
        this.successfulDeliveries++;
        this.lastDeliveryAt = LocalDateTime.now();
    }

    /**
     * Increment failed delivery counter
     */
    public void incrementFailedDeliveries() {
        this.failedDeliveries++;
        this.lastDeliveryAt = LocalDateTime.now();
    }

    /**
     * Disable subscription (auto-disable on excessive failures)
     */
    public void disable(String reason) {
        this.isActive = false;
        this.status = WebhookStatus.DISABLED;
        this.updatedAt = LocalDateTime.now();
        // Could log reason to audit table
    }

    /**
     * Enable subscription
     */
    public void enable() {
        this.isActive = true;
        this.status = WebhookStatus.ACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Check if subscription is stale (no recent deliveries)
     */
    public boolean isStale(int daysThreshold) {
        if (lastDeliveryAt == null) {
            // Never delivered - check creation date
            return createdAt.plusDays(daysThreshold).isBefore(LocalDateTime.now());
        }
        return lastDeliveryAt.plusDays(daysThreshold).isBefore(LocalDateTime.now());
    }

    /**
     * Validate subscription configuration
     */
    public boolean isValid() {
        return url != null && !url.trim().isEmpty() &&
               eventTypes != null && !eventTypes.isEmpty() &&
               timeout != null && timeout > 0 &&
               maxRetries != null && maxRetries >= 0;
    }

    /**
     * Check if subscription needs health check
     */
    public boolean needsHealthCheck() {
        // Needs health check if:
        // 1. Active but has recent failures
        // 2. Failure rate > 50%
        // 3. Last delivery > 24 hours ago
        if (!isActive) {
            return false;
        }

        if (hasExceededFailureThreshold(50.0)) {
            return true;
        }

        if (lastDeliveryAt != null &&
            lastDeliveryAt.plusHours(24).isBefore(LocalDateTime.now())) {
            return true;
        }

        return false;
    }

    /**
     * Reset delivery statistics
     */
    public void resetStatistics() {
        this.successfulDeliveries = 0L;
        this.failedDeliveries = 0L;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Add event type to subscription
     */
    public void addEventType(String eventType) {
        if (this.eventTypes == null) {
            this.eventTypes = new HashSet<>();
        }
        this.eventTypes.add(eventType);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Remove event type from subscription
     */
    public void removeEventType(String eventType) {
        if (this.eventTypes != null) {
            this.eventTypes.remove(eventType);
            this.updatedAt = LocalDateTime.now();
        }
    }

    /**
     * Add custom header
     */
    public void addHeader(String name, String value) {
        if (this.headers == null) {
            this.headers = new HashMap<>();
        }
        this.headers.put(name, value);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Remove custom header
     */
    public void removeHeader(String name) {
        if (this.headers != null) {
            this.headers.remove(name);
            this.updatedAt = LocalDateTime.now();
        }
    }

    /**
     * Get age in days
     */
    public long getAgeInDays() {
        return java.time.temporal.ChronoUnit.DAYS.between(createdAt, LocalDateTime.now());
    }

    /**
     * Get days since last delivery
     */
    public Long getDaysSinceLastDelivery() {
        if (lastDeliveryAt == null) {
            return null;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(lastDeliveryAt, LocalDateTime.now());
    }
}
