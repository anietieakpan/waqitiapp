package com.waqiti.payment.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing an instant deposit transaction
 * Used for processing immediate ACH transfers through debit card networks
 */
@Entity
@Table(name = "instant_deposits",
    indexes = {
        @Index(name = "idx_instant_deposit_ach_transfer_id", columnList = "ach_transfer_id", unique = true),
        @Index(name = "idx_instant_deposit_user_id", columnList = "user_id"),
        @Index(name = "idx_instant_deposit_wallet_id", columnList = "wallet_id"),
        @Index(name = "idx_instant_deposit_status", columnList = "status"),
        @Index(name = "idx_instant_deposit_created_at", columnList = "created_at"),
        @Index(name = "idx_instant_deposit_debit_card_id", columnList = "debit_card_id"),
        @Index(name = "idx_instant_deposit_network_ref", columnList = "network_reference_id")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class InstantDeposit {
    
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "ach_transfer_id", nullable = false, unique = true)
    private UUID achTransferId;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;
    
    @Column(name = "original_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal originalAmount;
    
    @Column(name = "fee_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal feeAmount;
    
    @Column(name = "net_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal netAmount;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InstantDepositStatus status;
    
    @Column(name = "debit_card_id", nullable = false)
    private UUID debitCardId;
    
    @Column(name = "device_id", length = 255)
    private String deviceId;
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    @Column(name = "network_reference_id", length = 100)
    private String networkReferenceId;
    
    @Column(name = "network_response_code", length = 10)
    private String networkResponseCode;
    
    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @Column(name = "failed_at")
    private LocalDateTime failedAt;
    
    @Column(name = "failure_reason", length = 500)
    private String failureReason;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
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