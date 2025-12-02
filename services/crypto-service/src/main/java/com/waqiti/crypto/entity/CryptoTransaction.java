/**
 * Crypto Transaction Entity
 * JPA entity representing cryptocurrency transactions
 */
package com.waqiti.crypto.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "crypto_transactions", indexes = {
    @Index(name = "idx_crypto_user_id", columnList = "user_id"),
    @Index(name = "idx_crypto_wallet_id", columnList = "wallet_id"),
    @Index(name = "idx_crypto_status", columnList = "status"),
    @Index(name = "idx_crypto_type", columnList = "transaction_type"),
    @Index(name = "idx_crypto_currency", columnList = "currency"),
    @Index(name = "idx_crypto_created_at", columnList = "created_at"),
    @Index(name = "idx_crypto_scheduled_for", columnList = "scheduled_for"),
    @Index(name = "idx_crypto_user_status", columnList = "user_id, status"),
    @Index(name = "idx_crypto_wallet_status", columnList = "wallet_id, status"),
    @Index(name = "idx_crypto_user_created", columnList = "user_id, created_at"),
    @Index(name = "idx_crypto_event_id", columnList = "event_id"),
    @Index(name = "idx_crypto_blockchain_tx_hash", columnList = "blockchain_tx_hash")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CryptoTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "wallet_id")
    private UUID walletId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private CryptoTransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false)
    private CryptoCurrency currency;

    @Column(name = "amount", nullable = false, precision = 36, scale = 18)
    private BigDecimal amount;

    @Column(name = "usd_value", precision = 12, scale = 2)
    private BigDecimal usdValue;

    @Column(name = "fee", nullable = false, precision = 36, scale = 18)
    @Builder.Default
    private BigDecimal fee = BigDecimal.ZERO;

    @Column(name = "price", precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "from_address")
    private String fromAddress;

    @Column(name = "to_address")
    private String toAddress;

    @Column(name = "memo", columnDefinition = "TEXT")
    private String memo;

    @Column(name = "tx_hash")
    private String txHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private CryptoTransactionStatus status = CryptoTransactionStatus.PENDING;

    @Column(name = "confirmations")
    @Builder.Default
    private Integer confirmations = 0;

    @Column(name = "block_number")
    private Long blockNumber;

    @Column(name = "block_hash")
    private String blockHash;

    @Column(name = "risk_score", precision = 5, scale = 2)
    private BigDecimal riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level")
    private RiskLevel riskLevel;

    @Column(name = "approval_required")
    @Builder.Default
    private Boolean approvalRequired = false;

    @Column(name = "review_required")
    @Builder.Default
    private Boolean reviewRequired = false;

    @Column(name = "scheduled_for")
    private LocalDateTime scheduledFor;

    @Column(name = "broadcasted_at")
    private LocalDateTime broadcastedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "event_id", unique = true)
    private String eventId;

    @Column(name = "blockchain_tx_hash", unique = true)
    private String blockchainTxHash;

    @Column(name = "network")
    private String network;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "blocked_at")
    private LocalDateTime blockedAt;

    @Column(name = "hold_at")
    private LocalDateTime holdAt;

    @Column(name = "review_reason", columnDefinition = "TEXT")
    private String reviewReason;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Version
    private Long version;
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Convenience methods for status updates
    public void markAsBroadcasted(String txHash) {
        this.txHash = txHash;
        this.status = CryptoTransactionStatus.BROADCASTED;
        this.broadcastedAt = LocalDateTime.now();
    }

    public void markAsConfirmed(int confirmations) {
        this.confirmations = confirmations;
        this.status = CryptoTransactionStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    public void markAsCompleted() {
        this.status = CryptoTransactionStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void markAsFailed(String reason) {
        this.status = CryptoTransactionStatus.FAILED;
        this.failureReason = reason;
        this.failedAt = LocalDateTime.now();
    }

    public void requireApproval() {
        this.approvalRequired = true;
        this.status = CryptoTransactionStatus.PENDING_APPROVAL;
    }

    public void requireReview() {
        this.reviewRequired = true;
        this.status = CryptoTransactionStatus.PENDING_REVIEW;
    }

    public void scheduleForDelay(LocalDateTime scheduledTime) {
        this.scheduledFor = scheduledTime;
        this.status = CryptoTransactionStatus.PENDING_DELAY;
    }
}