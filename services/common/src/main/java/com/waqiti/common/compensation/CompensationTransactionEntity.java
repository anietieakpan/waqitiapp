package com.waqiti.common.compensation;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * JPA entity for persisting compensation transactions.
 * Ensures durability and provides audit trail.
 */
@Entity
@Table(name = "compensation_transactions", indexes = {
    @Index(name = "idx_compensation_status", columnList = "status"),
    @Index(name = "idx_compensation_original_tx", columnList = "original_transaction_id"),
    @Index(name = "idx_compensation_created_at", columnList = "created_at"),
    @Index(name = "idx_compensation_priority", columnList = "priority")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompensationTransactionEntity {

    @Id
    @Column(name = "compensation_id", length = 64)
    private String compensationId;

    @Column(name = "original_transaction_id", nullable = false, length = 64)
    private String originalTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private CompensationTransaction.CompensationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private CompensationTransaction.CompensationStatus status;

    @Column(name = "target_service", length = 100)
    private String targetService;

    @Column(name = "compensation_action", length = 255)
    private String compensationAction;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "compensation_data", columnDefinition = "jsonb")
    private Map<String, Object> compensationData;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "original_error", columnDefinition = "TEXT")
    private String originalError;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private CompensationTransaction.CompensationPriority priority;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries = 3;

    @Column(name = "current_retry", nullable = false)
    private int currentRetry = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "initiated_by", length = 100)
    private String initiatedBy;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /**
     * Convert to domain model
     */
    public CompensationTransaction toDomainModel() {
        return CompensationTransaction.builder()
            .compensationId(this.compensationId)
            .originalTransactionId(this.originalTransactionId)
            .type(this.type)
            .status(this.status)
            .targetService(this.targetService)
            .compensationAction(this.compensationAction)
            .compensationData(this.compensationData)
            .reason(this.reason)
            .originalError(this.originalError)
            .priority(this.priority)
            .maxRetries(this.maxRetries)
            .currentRetry(this.currentRetry)
            .createdAt(this.createdAt)
            .lastAttemptAt(this.lastAttemptAt)
            .completedAt(this.completedAt)
            .initiatedBy(this.initiatedBy)
            .correlationId(this.correlationId)
            .metadata(this.metadata)
            .build();
    }

    /**
     * Create from domain model
     */
    public static CompensationTransactionEntity fromDomainModel(CompensationTransaction domain) {
        return CompensationTransactionEntity.builder()
            .compensationId(domain.getCompensationId())
            .originalTransactionId(domain.getOriginalTransactionId())
            .type(domain.getType())
            .status(domain.getStatus())
            .targetService(domain.getTargetService())
            .compensationAction(domain.getCompensationAction())
            .compensationData(domain.getCompensationData())
            .reason(domain.getReason())
            .originalError(domain.getOriginalError())
            .priority(domain.getPriority())
            .maxRetries(domain.getMaxRetries())
            .currentRetry(domain.getCurrentRetry())
            .createdAt(domain.getCreatedAt())
            .lastAttemptAt(domain.getLastAttemptAt())
            .completedAt(domain.getCompletedAt())
            .initiatedBy(domain.getInitiatedBy())
            .correlationId(domain.getCorrelationId())
            .metadata(domain.getMetadata())
            .build();
    }
}
