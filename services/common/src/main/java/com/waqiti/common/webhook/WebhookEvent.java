package com.waqiti.common.webhook;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.waqiti.common.audit.Auditable;
import com.waqiti.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Webhook event entity for reliable webhook delivery
 * Stores webhook data with encryption and audit trail
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "webhook_events", indexes = {
    @Index(name = "idx_webhook_events_status", columnList = "status"),
    @Index(name = "idx_webhook_events_event_type", columnList = "eventType"),
    @Index(name = "idx_webhook_events_next_attempt", columnList = "nextAttemptAt"),
    @Index(name = "idx_webhook_events_created_at", columnList = "createdAt"),
    @Index(name = "idx_webhook_events_endpoint_url", columnList = "endpointUrl"),
    @Index(name = "idx_webhook_events_retry_count", columnList = "retryCount")
})
public class WebhookEvent extends BaseEntity {
    
    /**
     * Webhook endpoint URL
     */
    @Column(name = "endpoint_url", nullable = false, length = 2048)
    private String endpointUrl;
    
    /**
     * Event type (e.g., payment.completed, user.created)
     */
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;
    
    /**
     * Webhook payload (encrypted)
     */
    @Lob
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;
    
    /**
     * HTTP headers to send with webhook
     */
    @Lob
    @Column(name = "headers", columnDefinition = "TEXT")
    @Convert(converter = HeadersConverter.class)
    private Map<String, String> headers;
    
    /**
     * Webhook status
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private WebhookStatus status = WebhookStatus.PENDING;
    
    /**
     * Current retry attempt count
     */
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;
    
    /**
     * Maximum retry attempts allowed
     */
    @Column(name = "max_retry_attempts", nullable = false)
    @Builder.Default
    private int maxRetryAttempts = 5;
    
    /**
     * When the next retry attempt should occur
     */
    @Column(name = "next_attempt_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ", timezone = "UTC")
    private Instant nextAttemptAt;
    
    /**
     * When the webhook was last attempted
     */
    @Column(name = "last_attempt_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ", timezone = "UTC")
    private Instant lastAttemptAt;
    
    /**
     * When the webhook was successfully delivered
     */
    @Column(name = "delivered_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ", timezone = "UTC")
    private Instant deliveredAt;
    
    /**
     * When the webhook expires and should no longer be retried
     */
    @Column(name = "expires_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ", timezone = "UTC")
    private Instant expiresAt;
    
    /**
     * Last HTTP response status code
     */
    @Column(name = "last_response_status")
    private Integer lastResponseStatus;
    
    /**
     * Last HTTP response body
     */
    @Lob
    @Column(name = "last_response_body", columnDefinition = "TEXT")
    private String lastResponseBody;
    
    /**
     * Last error message
     */
    @Column(name = "last_error_message", length = 1000)
    private String lastErrorMessage;
    
    /**
     * Webhook secret for signature verification
     */
    @Column(name = "webhook_secret", length = 255)
    @JsonIgnore
    private String webhookSecret;
    
    /**
     * HTTP method to use (default POST)
     */
    @Column(name = "http_method", length = 10)
    @Builder.Default
    private String httpMethod = "POST";
    
    /**
     * Content type for the webhook payload
     */
    @Column(name = "content_type", length = 100)
    @Builder.Default
    private String contentType = "application/json";
    
    /**
     * Request timeout in milliseconds
     */
    @Column(name = "timeout_ms")
    @Builder.Default
    private Integer timeoutMs = 30000;
    
    /**
     * Whether to verify SSL certificates
     */
    @Column(name = "verify_ssl")
    @Builder.Default
    private boolean verifySsl = true;
    
    /**
     * Tags for categorizing webhooks
     */
    @ElementCollection
    @CollectionTable(name = "webhook_event_tags", 
                    joinColumns = @JoinColumn(name = "webhook_event_id"))
    @Column(name = "tag")
    @Builder.Default
    private Set<String> tags = new HashSet<>();
    
    /**
     * Idempotency key to prevent duplicate webhooks
     */
    @Column(name = "idempotency_key", length = 255, unique = true)
    private String idempotencyKey;

    /**
     * Correlation ID for tracking related events
     */
    @Column(name = "correlation_id", length = 255)
    private String correlationId;
    
    /**
     * Source system or service that created this webhook
     */
    @Column(name = "source_service", length = 100)
    private String sourceService;
    
    /**
     * Priority level for webhook processing
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "priority")
    @Builder.Default
    private WebhookPriority priority = WebhookPriority.NORMAL;
    
    /**
     * Additional metadata as JSON
     */
    @Lob
    @Column(name = "metadata", columnDefinition = "TEXT")
    @Convert(converter = MetadataConverter.class)
    private Map<String, Object> metadata;
    
    /**
     * Webhook status enumeration
     */
    public enum WebhookStatus {
        PENDING,        // Ready to be sent
        PROCESSING,     // Currently being processed
        DELIVERED,      // Successfully delivered
        FAILED,         // Failed after max retries
        CANCELLED,      // Manually cancelled
        EXPIRED,        // Expired before delivery
        DEAD_LETTER     // Moved to dead letter queue
    }
    
    /**
     * Webhook priority levels
     */
    public enum WebhookPriority {
        LOW,
        NORMAL,
        HIGH,
        CRITICAL
    }
    
    /**
     * Check if webhook can be retried
     */
    public boolean canRetry() {
        return status == WebhookStatus.PENDING && 
               retryCount < maxRetryAttempts &&
               (expiresAt == null || Instant.now().isBefore(expiresAt));
    }
    
    /**
     * Check if webhook has expired
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
    
    /**
     * Increment retry count and update next attempt time
     */
    public void incrementRetryCount(Instant nextAttempt) {
        this.retryCount++;
        this.nextAttemptAt = nextAttempt;
        this.lastAttemptAt = Instant.now();
        
        if (this.retryCount >= this.maxRetryAttempts) {
            this.status = WebhookStatus.FAILED;
        }
    }
    
    /**
     * Mark webhook as delivered
     */
    public void markAsDelivered(int responseStatus, String responseBody) {
        this.status = WebhookStatus.DELIVERED;
        this.deliveredAt = Instant.now();
        this.lastResponseStatus = responseStatus;
        this.lastResponseBody = responseBody;
        this.lastErrorMessage = null;
    }
    
    /**
     * Mark webhook as failed
     */
    public void markAsFailed(String errorMessage, Integer responseStatus, String responseBody) {
        this.status = WebhookStatus.FAILED;
        this.lastErrorMessage = errorMessage;
        this.lastResponseStatus = responseStatus;
        this.lastResponseBody = responseBody;
        this.lastAttemptAt = Instant.now();
    }
    
    /**
     * Mark webhook as cancelled
     */
    public void markAsCancelled() {
        this.status = WebhookStatus.CANCELLED;
    }
    
    /**
     * Mark webhook as expired
     */
    public void markAsExpired() {
        this.status = WebhookStatus.EXPIRED;
    }
    
    /**
     * Mark webhook as dead letter
     */
    public void markAsDeadLetter() {
        this.status = WebhookStatus.DEAD_LETTER;
    }
    
    /**
     * Get success status based on HTTP response code
     */
    public static boolean isSuccessResponse(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }
    
    /**
     * Get retry delay in milliseconds based on attempt count
     */
    public static long calculateRetryDelay(int attemptCount, long baseDelay, double multiplier, long maxDelay) {
        long delay = (long) (baseDelay * Math.pow(multiplier, attemptCount - 1));
        return Math.min(delay, maxDelay);
    }
}