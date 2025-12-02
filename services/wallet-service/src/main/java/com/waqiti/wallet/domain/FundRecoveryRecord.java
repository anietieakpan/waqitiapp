package com.waqiti.wallet.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Fund Recovery Record Entity
 * 
 * Tracks manual intervention cases for failed payment recoveries
 * Provides audit trail and operations management for stuck funds
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "fund_recovery_records", indexes = {
    @Index(name = "idx_payment_id", columnList = "payment_id"),
    @Index(name = "idx_wallet_id", columnList = "wallet_id"),
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_priority", columnList = "priority"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Document(collection = "fund_recovery_records")
public class FundRecoveryRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "payment_id", nullable = false)
    private String paymentId;

    @Column(name = "wallet_id", nullable = false)
    private String walletId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "failure_code", length = 50)
    private String failureCode;

    @Column(name = "error_details", columnDefinition = "TEXT")
    private String errorDetails;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RecoveryStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    private RecoveryPriority priority;

    @Column(name = "assigned_to")
    private String assignedTo;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Version
    @Column(name = "version")
    private Long version;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (status == RecoveryStatus.COMPLETED && resolvedAt == null) {
            resolvedAt = LocalDateTime.now();
        }
    }

    public enum RecoveryStatus {
        PENDING_MANUAL_REVIEW,
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }

    public enum RecoveryPriority {
        CRITICAL,
        HIGH,
        MEDIUM,
        LOW
    }
}