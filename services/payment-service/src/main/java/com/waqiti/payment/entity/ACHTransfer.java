package com.waqiti.payment.entity;

import com.waqiti.common.encryption.EncryptedStringConverter;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing an ACH (Automated Clearing House) transfer
 */
@Entity
@Table(name = "ach_transfers",
    indexes = {
        @Index(name = "idx_ach_user_id", columnList = "user_id"),
        @Index(name = "idx_ach_wallet_id", columnList = "wallet_id"),
        @Index(name = "idx_ach_status", columnList = "status"),
        @Index(name = "idx_ach_created_at", columnList = "created_at"),
        @Index(name = "idx_ach_external_ref", columnList = "external_reference_id"),
        @Index(name = "idx_ach_idempotency", columnList = "idempotency_key", unique = true)
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class ACHTransfer {
    
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;
    
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 20)
    private TransferDirection direction;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ACHTransferStatus status;
    
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "routing_number", nullable = false, length = 500)
    private String routingNumber; // PCI DSS Requirement: Encrypted with AES-256-GCM

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "account_number", nullable = false, length = 500)
    private String accountNumber; // PCI DSS Requirement: Encrypted with AES-256-GCM
    
    @Column(name = "account_holder_name", nullable = false, length = 100)
    private String accountHolderName;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private BankAccountType accountType;
    
    @Column(name = "description", length = 255)
    private String description;
    
    @Column(name = "external_reference_id", length = 100)
    private String externalReferenceId;
    
    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "batch_id", length = 100)
    private String batchId;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;
    
    @Column(name = "return_code", length = 10)
    private String returnCode;
    
    @Column(name = "return_reason", length = 255)
    private String returnReason;
    
    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "returned_at")
    private LocalDateTime returnedAt;
    
    @Column(name = "expected_completion_date")
    private LocalDate expectedCompletionDate;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "instant_deposit_processed", nullable = false)
    private boolean instantDepositProcessed = false;
    
    @Column(name = "instant_deposit_at")
    private LocalDateTime instantDepositAt;
    
    @Version
    @Column(name = "version")
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}