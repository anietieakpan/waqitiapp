package com.waqiti.wallet.domain;

import com.waqiti.common.events.AccountFreezeRequestEvent;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Domain entity representing a wallet freeze record for compliance and fraud prevention.
 *
 * <p>Tracks all wallet freeze events including:
 * <ul>
 *   <li>Fraud detection freezes (ML-based risk scores)</li>
 *   <li>Compliance freezes (AML, OFAC, PEP, KYC)</li>
 *   <li>Legal order freezes (court orders, regulatory demands)</li>
 *   <li>Manual security team freezes</li>
 * </ul>
 *
 * <p><b>Audit Trail:</b> All freeze events are immutable and maintain complete audit history
 * for regulatory compliance (FinCEN, OFAC, PCI DSS).
 *
 * @author Waqiti Security Team
 * @version 1.0.0
 * @since 2025-10-18
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "wallet_freeze_records")
@CompoundIndexes({
    @CompoundIndex(name = "wallet_active_idx", def = "{'walletId': 1, 'active': 1}"),
    @CompoundIndex(name = "user_active_idx", def = "{'userId': 1, 'active': 1}"),
    @CompoundIndex(name = "frozen_at_idx", def = "{'frozenAt': -1}")
})
public class WalletFreezeRecord {

    /**
     * Unique freeze record identifier (PK).
     */
    @Id
    private UUID freezeId;

    /**
     * Wallet that was frozen.
     */
    @Indexed
    private UUID walletId;

    /**
     * User who owns the wallet.
     */
    @Indexed
    private UUID userId;

    /**
     * Detailed reason for the freeze (audit trail).
     */
    private String freezeReason;

    /**
     * Severity level: CRITICAL, HIGH, MEDIUM, LOW.
     */
    private String severity;

    /**
     * Type of freeze scope applied.
     */
    private AccountFreezeRequestEvent.FreezeScope freezeScope;

    /**
     * Timestamp when freeze was initiated.
     */
    @Indexed
    private LocalDateTime frozenAt;

    /**
     * System or user who initiated the freeze.
     */
    private String frozenBy;

    /**
     * Whether the freeze is currently active.
     */
    @Indexed
    private boolean active;

    /**
     * Timestamp when freeze was lifted (null if still active).
     */
    private LocalDateTime unfrozenAt;

    /**
     * User or system that lifted the freeze.
     */
    private String unfrozenBy;

    /**
     * Reason for lifting the freeze.
     */
    private String unfreezeReason;

    /**
     * Related fraud event ID (if triggered by fraud detection).
     */
    private UUID fraudEventId;

    /**
     * Related compliance case ID (if applicable).
     */
    private String complianceCaseId;

    /**
     * Related legal order ID (if applicable).
     */
    private String legalOrderId;

    /**
     * Risk score that triggered the freeze (0-100).
     */
    private Double riskScore;

    /**
     * Detected fraud patterns (comma-separated).
     */
    private String detectedPatterns;

    /**
     * Scheduled review date for manual compliance review.
     */
    private LocalDateTime reviewDate;

    /**
     * Additional metadata (JSON string).
     */
    private String metadata;

    /**
     * Record creation timestamp.
     */
    private LocalDateTime createdAt;

    /**
     * Record last update timestamp.
     */
    private LocalDateTime updatedAt;

    /**
     * Lifecycle hook: set timestamps before persisting.
     */
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = LocalDateTime.now();
    }

    /**
     * Unfreeze this record.
     *
     * @param unfreezeReason reason for unfreezing
     * @param unfrozenBy who is unfreezing
     */
    public void unfreeze(String unfreezeReason, String unfrozenBy) {
        this.active = false;
        this.unfrozenAt = LocalDateTime.now();
        this.unfrozenBy = unfrozenBy;
        this.unfreezeReason = unfreezeReason;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Check if freeze is expired based on review date.
     *
     * @return true if review date has passed
     */
    public boolean isExpired() {
        return reviewDate != null && LocalDateTime.now().isAfter(reviewDate);
    }

    /**
     * Get freeze duration in hours.
     *
     * @return duration in hours
     */
    public long getFreezeDurationHours() {
        LocalDateTime endTime = unfrozenAt != null ? unfrozenAt : LocalDateTime.now();
        return java.time.Duration.between(frozenAt, endTime).toHours();
    }
}
