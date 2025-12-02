package com.waqiti.common.idempotency;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ENHANCED: Persistent idempotency record for production-grade duplicate prevention
 * Supports both Redis (fast access) and Database (durability) storage
 * Critical for financial transaction integrity across service restarts
 */
@Entity
@Table(name = "idempotency_records", indexes = {
    @Index(name = "idx_idempotency_key", columnList = "idempotencyKey", unique = true),
    @Index(name = "idx_service_operation", columnList = "serviceName, operationType"),
    @Index(name = "idx_expires_at", columnList = "expiresAt"),
    @Index(name = "idx_user_operation", columnList = "userId, operationType"),
    @Index(name = "idx_correlation_id", columnList = "correlationId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@EntityListeners(AuditingEntityListener.class)
public class IdempotencyRecord {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Column(length = 128)
    private String eventId; // For event-driven idempotency tracking

    @Column(nullable = false)
    private UUID operationId;
    
    @Column(nullable = false, length = 64)
    private String serviceName;
    
    @Column(nullable = false, length = 64)
    private String operationType;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private IdempotencyStatus status;
    
    @Lob
    @Column(columnDefinition = "TEXT")
    private String requestHash;
    
    @Lob
    @Column(columnDefinition = "TEXT")
    private String result;
    
    @Column(length = 2000)
    private String error;
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    
    @LastModifiedDate
    private Instant completedAt;

    private Instant processedAt;

    private java.time.Duration ttl;

    @Column(nullable = false)
    private LocalDateTime expiresAt;
    
    @Version
    private Long version;
    
    // Enhanced audit fields for compliance
    @Column(length = 128)
    private String correlationId;
    
    @Column(length = 64)
    private String userId;
    
    @Column(length = 64)
    private String sessionId;
    
    @Column(length = 45)
    private String clientIpAddress;
    
    @Column(length = 512)
    private String userAgent;
    
    @Column(length = 64)
    private String deviceFingerprint;
    
    // Retry tracking for failed operations
    @Column
    @Builder.Default
    private Integer retryCount = 0;
    
    @Column
    private LocalDateTime lastRetryAt;
    
    // Business context
    @Column(precision = 19, scale = 4)
    private java.math.BigDecimal amount;
    
    @Column(length = 3)
    private String currency;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (expiresAt == null) {
            // Default expiration: 24 hours for financial operations
            expiresAt = LocalDateTime.now().plusHours(24);
        }
        if (status == null) {
            status = IdempotencyStatus.IN_PROGRESS;
        }
    }
    
    public boolean isCompleted() {
        return status == IdempotencyStatus.COMPLETED;
    }
    
    public boolean isInProgress() {
        return status == IdempotencyStatus.IN_PROGRESS;
    }
    
    public boolean isFailed() {
        return status == IdempotencyStatus.FAILED;
    }
    
    /**
     * Check if this idempotency record has expired
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
    
    /**
     * Mark operation as completed with response data
     */
    public void markCompleted(String responseData) {
        this.status = IdempotencyStatus.COMPLETED;
        this.result = responseData;
        this.completedAt = Instant.now();
    }
    
    /**
     * Mark operation as failed with error message
     */
    public void markFailed(String errorMessage) {
        this.status = IdempotencyStatus.FAILED;
        this.error = errorMessage;
        this.completedAt = Instant.now();
    }
    
    /**
     * Mark record as expired
     */
    public void markExpired() {
        this.status = IdempotencyStatus.EXPIRED;
    }
    
    /**
     * Increment retry count for failed operations
     */
    public void incrementRetry() {
        this.retryCount = (this.retryCount == null ? 0 : this.retryCount) + 1;
        this.lastRetryAt = LocalDateTime.now();
        this.status = IdempotencyStatus.IN_PROGRESS;
    }
    
    /**
     * Check if max retries exceeded
     */
    public boolean isMaxRetriesExceeded(int maxRetries) {
        return this.retryCount != null && this.retryCount >= maxRetries;
    }
}