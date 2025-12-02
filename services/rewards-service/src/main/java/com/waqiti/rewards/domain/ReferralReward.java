package com.waqiti.rewards.domain;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import com.waqiti.rewards.enums.RewardStatus;
import com.waqiti.rewards.enums.RewardType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Referral Reward Entity
 *
 * Represents individual reward transactions for referrals
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-08
 */
@Entity
@Table(name = "referral_rewards", indexes = {
    @Index(name = "idx_referral_rewards_referral", columnList = "referralId"),
    @Index(name = "idx_referral_rewards_recipient", columnList = "recipientUserId"),
    @Index(name = "idx_referral_rewards_program", columnList = "programId"),
    @Index(name = "idx_referral_rewards_status", columnList = "status"),
    @Index(name = "idx_referral_rewards_type", columnList = "rewardType"),
    @Index(name = "idx_referral_rewards_expiry", columnList = "expiryDate")
})
@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class ReferralReward {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @NotBlank(message = "Reward ID is required")
    @Column(unique = true, nullable = false, length = 100)
    private String rewardId;

    @NotBlank(message = "Referral ID is required")
    @Column(nullable = false, length = 100)
    private String referralId;

    @NotBlank(message = "Program ID is required")
    @Column(nullable = false, length = 100)
    private String programId;

    // ============================================================================
    // RECIPIENT INFORMATION
    // ============================================================================

    @NotNull(message = "Recipient user ID is required")
    @Column(nullable = false)
    private UUID recipientUserId;

    @NotBlank(message = "Recipient type is required")
    @Column(nullable = false, length = 20)
    private String recipientType; // REFERRER, REFEREE

    // ============================================================================
    // REWARD DETAILS
    // ============================================================================

    @NotNull(message = "Reward type is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private RewardType rewardType;

    @Min(value = 0, message = "Points amount must be non-negative")
    @Column
    private Long pointsAmount;

    @DecimalMin(value = "0.00", message = "Cashback amount must be non-negative")
    @Column(precision = 15, scale = 2)
    private BigDecimal cashbackAmount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    @Builder.Default
    @Column(length = 3, nullable = false)
    private String currency = "USD";

    // ============================================================================
    // STATUS AND LIFECYCLE
    // ============================================================================

    @NotNull(message = "Status is required")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 20)
    private RewardStatus status = RewardStatus.PENDING;

    @NotNull(message = "Earned timestamp is required")
    @Column(nullable = false)
    private LocalDateTime earnedAt;

    @Column
    private LocalDateTime approvedAt;

    @Column
    private LocalDateTime issuedAt;

    @Column
    private LocalDateTime redeemedAt;

    @Column
    private LocalDateTime expiredAt;

    @Column
    private LocalDateTime rejectedAt;

    @Column
    private LocalDate expiryDate;

    // ============================================================================
    // REDEMPTION DETAILS
    // ============================================================================

    @Size(max = 50, message = "Redemption method must not exceed 50 characters")
    @Column(length = 50)
    private String redemptionMethod; // WALLET_CREDIT, BANK_TRANSFER, VOUCHER

    @Size(max = 100, message = "Redemption reference must not exceed 100 characters")
    @Column(length = 100)
    private String redemptionReference;

    @Size(max = 100, message = "Account credited must not exceed 100 characters")
    @Column(length = 100)
    private String accountCredited;

    @Size(max = 100, message = "Transaction ID must not exceed 100 characters")
    @Column(length = 100)
    private String transactionId;

    // ============================================================================
    // REJECTION DETAILS
    // ============================================================================

    @Column(columnDefinition = "TEXT")
    private String rejectionReason;

    @Size(max = 50, message = "Rejection code must not exceed 50 characters")
    @Column(length = 50)
    private String rejectionCode;

    // ============================================================================
    // APPROVAL WORKFLOW
    // ============================================================================

    @Builder.Default
    @Column(nullable = false)
    private Boolean requiresApproval = false;

    @Size(max = 100, message = "Approved by must not exceed 100 characters")
    @Column(length = 100)
    private String approvedBy;

    @Column(columnDefinition = "TEXT")
    private String approvalNotes;

    // ============================================================================
    // METADATA
    // ============================================================================

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> rewardMetadata = new HashMap<>();

    // ============================================================================
    // AUDIT FIELDS
    // ============================================================================

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // ============================================================================
    // LIFECYCLE CALLBACKS
    // ============================================================================

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (earnedAt == null) {
            earnedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ============================================================================
    // BUSINESS LOGIC METHODS
    // ============================================================================

    /**
     * Approves the reward
     */
    public void approve(String approver, String notes) {
        this.status = RewardStatus.APPROVED;
        this.approvedAt = LocalDateTime.now();
        this.approvedBy = approver;
        this.approvalNotes = notes;
    }

    /**
     * Issues the reward to the recipient
     */
    public void issue(String method, String reference) {
        this.status = RewardStatus.ISSUED;
        this.issuedAt = LocalDateTime.now();
        this.redemptionMethod = method;
        this.redemptionReference = reference;
    }

    /**
     * Marks the reward as redeemed
     */
    public void redeem(String accountId, String txnId) {
        this.status = RewardStatus.REDEEMED;
        this.redeemedAt = LocalDateTime.now();
        this.accountCredited = accountId;
        this.transactionId = txnId;
    }

    /**
     * Rejects the reward
     */
    public void reject(String reason, String code) {
        this.status = RewardStatus.REJECTED;
        this.rejectedAt = LocalDateTime.now();
        this.rejectionReason = reason;
        this.rejectionCode = code;
    }

    /**
     * Expires the reward
     */
    public void expire() {
        this.status = RewardStatus.EXPIRED;
        this.expiredAt = LocalDateTime.now();
    }

    /**
     * Checks if the reward has expired
     */
    public boolean isExpired() {
        if (expiryDate == null) {
            return false;
        }
        return LocalDate.now().isAfter(expiryDate);
    }

    /**
     * Checks if the reward can be issued
     */
    public boolean canBeIssued() {
        return (status == RewardStatus.PENDING || status == RewardStatus.APPROVED) &&
               !isExpired();
    }

    /**
     * Checks if the reward can be redeemed
     */
    public boolean canBeRedeemed() {
        return status == RewardStatus.ISSUED && !isExpired();
    }

    /**
     * Gets the reward amount as BigDecimal (for both points and cashback)
     */
    public BigDecimal getRewardAmount() {
        if (rewardType == RewardType.POINTS && pointsAmount != null) {
            return BigDecimal.valueOf(pointsAmount);
        }
        return cashbackAmount != null ? cashbackAmount : BigDecimal.ZERO;
    }

    /**
     * Checks if this is a referrer reward
     */
    public boolean isReferrerReward() {
        return "REFERRER".equalsIgnoreCase(recipientType);
    }

    /**
     * Checks if this is a referee reward
     */
    public boolean isRefereeReward() {
        return "REFEREE".equalsIgnoreCase(recipientType);
    }

    /**
     * Calculates days until expiry (null if no expiry date)
     */
    public Long getDaysUntilExpiry() {
        if (expiryDate == null) {
            return null;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
    }
}
