package com.waqiti.wallet.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing a hold/reservation on wallet funds.
 * Used for authorization holds and pending transactions.
 */
@Entity
@Table(name = "wallet_holds",
    indexes = {
        @Index(name = "idx_wallet_holds_wallet_id", columnList = "wallet_id"),
        @Index(name = "idx_wallet_holds_status", columnList = "status"),
        @Index(name = "idx_wallet_holds_expires_at", columnList = "expires_at")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletHold {
    
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;
    
    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;
    
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private HoldStatus status;
    
    @Column(name = "reference_type", length = 50)
    private String referenceType;
    
    @Column(name = "reference_id")
    private UUID referenceId;
    
    @Column(name = "description", length = 500)
    private String description;
    
    @Column(name = "merchant_id")
    private UUID merchantId;
    
    @Column(name = "merchant_name", length = 255)
    private String merchantName;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    
    @Column(name = "released_at")
    private Instant releasedAt;
    
    @Column(name = "captured_at")
    private Instant capturedAt;
    
    @Column(name = "capture_amount", precision = 19, scale = 4)
    private BigDecimal captureAmount;
    
    @Column(name = "release_reason", length = 255)
    private String releaseReason;
    
    @Version
    @Column(name = "version")
    private Long version;
    
    /**
     * Check if the hold is expired
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
    
    /**
     * Check if the hold is active
     */
    public boolean isActive() {
        return status == HoldStatus.ACTIVE && !isExpired();
    }
}