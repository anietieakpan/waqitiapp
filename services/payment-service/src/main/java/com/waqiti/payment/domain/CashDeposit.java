package com.waqiti.payment.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a cash deposit reference/transaction
 */
@Entity
@Table(name = "cash_deposits")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashDeposit {
    
    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "reference_code", unique = true, nullable = false, length = 20)
    private String referenceCode;
    
    @Column(name = "barcode", unique = true, length = 50)
    private String barcode;
    
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    
    @Column(name = "fee", nullable = false, precision = 19, scale = 2)
    private BigDecimal fee;
    
    @Column(name = "net_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal netAmount;
    
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CashDepositStatus status;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 30)
    private CashDepositProvider provider;
    
    @Column(name = "network_reference", length = 100)
    private String networkReference;
    
    @Column(name = "network_transaction_id", length = 100)
    private String networkTransactionId;
    
    @Column(name = "location_id")
    private String locationId;
    
    @Column(name = "location_name")
    private String locationName;
    
    @Column(name = "location_address")
    private String locationAddress;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;
    
    @Column(name = "cancellation_reason")
    private String cancellationReason;
    
    @Column(name = "wallet_credited")
    private Boolean walletCredited;
    
    @Column(name = "wallet_credit_transaction_id")
    private String walletCreditTransactionId;
    
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    @Column(name = "device_id", length = 100)
    private String deviceId;
    
    @Column(name = "fraud_score")
    private Integer fraudScore;
    
    @Column(name = "fraud_check_passed")
    private Boolean fraudCheckPassed;
    
    @Version
    @Column(name = "version")
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = CashDepositStatus.PENDING;
        }
        if (walletCredited == null) {
            walletCredited = false;
        }
    }
    
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
    
    public boolean isActive() {
        return status == CashDepositStatus.PENDING && !isExpired();
    }
    
    public boolean canBeCompleted() {
        return status == CashDepositStatus.PENDING && !isExpired();
    }
    
    public boolean canBeCancelled() {
        return status == CashDepositStatus.PENDING;
    }
}

