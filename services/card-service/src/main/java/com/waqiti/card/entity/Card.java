package com.waqiti.card.entity;

import com.waqiti.card.enums.CardBrand;
import com.waqiti.card.enums.CardStatus;
import com.waqiti.card.enums.CardType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Card entity - Master card record
 * Represents a physical or virtual payment card issued to a user
 *
 * This is the central entity that consolidates card_account from card-processing-service
 * and card from card-service
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Entity
@Table(name = "card", indexes = {
    @Index(name = "idx_card_user", columnList = "user_id"),
    @Index(name = "idx_card_account", columnList = "account_id"),
    @Index(name = "idx_card_status", columnList = "card_status"),
    @Index(name = "idx_card_expiry", columnList = "expiry_date"),
    @Index(name = "idx_card_product", columnList = "product_id"),
    @Index(name = "idx_card_pan_token", columnList = "pan_token"),
    @Index(name = "idx_card_last_transaction", columnList = "last_transaction_date"),
    @Index(name = "idx_card_deleted", columnList = "deleted_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Card extends BaseAuditEntity {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // ========================================================================
    // CARD IDENTIFICATION
    // ========================================================================

    @Column(name = "card_id", unique = true, nullable = false, length = 50)
    @NotBlank(message = "Card ID is required")
    private String cardId;

    @Column(name = "card_number_encrypted", nullable = false)
    @NotBlank(message = "Card number is required")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String cardNumberEncrypted;

    @Column(name = "card_number_last_four", nullable = false, length = 4)
    @NotBlank
    @Size(min = 4, max = 4)
    private String cardNumberLastFour;

    @Column(name = "pan_token", unique = true, length = 100)
    private String panToken;

    // ========================================================================
    // CARD TYPE & NETWORK
    // ========================================================================

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false, length = 20)
    @NotNull(message = "Card type is required")
    private CardType cardType;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_brand", nullable = false, length = 20)
    @NotNull(message = "Card brand is required")
    private CardBrand cardBrand;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_status", nullable = false, length = 20)
    @NotNull(message = "Card status is required")
    @Builder.Default
    private CardStatus cardStatus = CardStatus.PENDING_ACTIVATION;

    // ========================================================================
    // CARD PRODUCT & OWNER
    // ========================================================================

    @Column(name = "product_id", length = 100)
    private String productId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", referencedColumnName = "product_id", insertable = false, updatable = false)
    private CardProduct product;

    @Column(name = "user_id", nullable = false)
    @NotNull(message = "User ID is required")
    private UUID userId;

    @Column(name = "account_id", nullable = false)
    @NotNull(message = "Account ID is required")
    private UUID accountId;

    // ========================================================================
    // CARD DETAILS
    // ========================================================================

    @Column(name = "issue_date", nullable = false)
    @NotNull
    @Builder.Default
    private LocalDate issueDate = LocalDate.now();

    @Column(name = "expiry_date", nullable = false)
    @NotNull
    @Future(message = "Expiry date must be in the future")
    private LocalDate expiryDate;

    @Column(name = "cvv_encrypted")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String cvvEncrypted;

    @Column(name = "embossed_name", length = 255)
    @Size(max = 255)
    private String embossedName;

    // ========================================================================
    // PIN MANAGEMENT
    // ========================================================================

    @Column(name = "pin_hash")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String pinHash;

    @Column(name = "pin_attempts")
    @Min(0)
    @Builder.Default
    private Integer pinAttempts = 0;

    @Column(name = "pin_locked_until")
    private LocalDateTime pinLockedUntil;

    @Column(name = "pin_set")
    @Builder.Default
    private Boolean pinSet = false;

    // ========================================================================
    // ACTIVATION CODE (BCrypt hashed, temporary storage)
    // ========================================================================

    @Column(name = "activation_code_hash")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String activationCodeHash;

    @Column(name = "activation_code_expiry")
    private LocalDateTime activationCodeExpiry;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    // ========================================================================
    // FINANCIAL LIMITS & BALANCES
    // ========================================================================

    @Column(name = "credit_limit", precision = 18, scale = 2)
    @DecimalMin(value = "0.00", message = "Credit limit must be positive")
    private BigDecimal creditLimit;

    @Column(name = "available_credit", precision = 18, scale = 2)
    @DecimalMin(value = "0.00")
    private BigDecimal availableCredit;

    @Column(name = "ledger_balance", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal ledgerBalance = BigDecimal.ZERO;

    @Column(name = "outstanding_balance", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal outstandingBalance = BigDecimal.ZERO;

    @Column(name = "statement_balance", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal statementBalance = BigDecimal.ZERO;

    @Column(name = "minimum_payment", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal minimumPayment = BigDecimal.ZERO;

    @Column(name = "cash_limit", precision = 18, scale = 2)
    private BigDecimal cashLimit;

    @Column(name = "daily_spend_limit", precision = 18, scale = 2)
    private BigDecimal dailySpendLimit;

    @Column(name = "monthly_spend_limit", precision = 18, scale = 2)
    private BigDecimal monthlySpendLimit;

    // ========================================================================
    // PAYMENT DETAILS
    // ========================================================================

    @Column(name = "payment_due_date")
    private LocalDate paymentDueDate;

    @Column(name = "last_payment_date")
    private LocalDateTime lastPaymentDate;

    @Column(name = "last_transaction_date")
    private LocalDateTime lastTransactionDate;

    @Column(name = "interest_rate", precision = 5, scale = 4)
    @DecimalMin(value = "0.0000")
    @DecimalMax(value = "1.0000")
    private BigDecimal interestRate;

    // ========================================================================
    // FEES
    // ========================================================================

    @Column(name = "overlimit_fee", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal overlimitFee = BigDecimal.ZERO;

    @Column(name = "late_payment_fee", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal latePaymentFee = BigDecimal.ZERO;

    @Column(name = "foreign_transaction_fee_rate", precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal foreignTransactionFeeRate = BigDecimal.ZERO;

    // ========================================================================
    // REWARDS & PROGRAM
    // ========================================================================

    @Column(name = "rewards_program", length = 50)
    @Size(max = 50)
    private String rewardsProgram;

    @Column(name = "rewards_balance", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal rewardsBalance = BigDecimal.ZERO;

    // ========================================================================
    // CARD FEATURES & CAPABILITIES
    // ========================================================================

    @Column(name = "is_contactless")
    @Builder.Default
    private Boolean isContactless = true;

    @Column(name = "is_virtual")
    @Builder.Default
    private Boolean isVirtual = false;

    @Column(name = "is_international_enabled")
    @Builder.Default
    private Boolean isInternationalEnabled = false;

    @Column(name = "is_online_enabled")
    @Builder.Default
    private Boolean isOnlineEnabled = true;

    // ========================================================================
    // CARD LIFECYCLE
    // ========================================================================

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @Column(name = "blocked_at")
    private LocalDateTime blockedAt;

    @Column(name = "blocked_reason")
    private String blockedReason;

    // ========================================================================
    // REPLACEMENT & DELIVERY
    // ========================================================================

    @Column(name = "replacement_reason", length = 100)
    @Size(max = 100)
    private String replacementReason;

    @Column(name = "replaced_card_id", length = 50)
    @Size(max = 50)
    private String replacedCardId;

    @Column(name = "replacement_card_id", length = 50)
    @Size(max = 50)
    private String replacementCardId;

    @Column(name = "delivery_address", columnDefinition = "TEXT")
    private String deliveryAddress;

    @Column(name = "delivery_status", length = 20)
    @Size(max = 20)
    private String deliveryStatus;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    // ========================================================================
    // RELATIONSHIPS
    // ========================================================================

    @OneToMany(mappedBy = "card", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<CardTransaction> transactions = new ArrayList<>();

    @OneToMany(mappedBy = "card", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<CardAuthorization> authorizations = new ArrayList<>();

    @OneToMany(mappedBy = "card", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<CardLimit> limits = new ArrayList<>();

    @OneToMany(mappedBy = "card", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<CardStatement> statements = new ArrayList<>();

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Check if card is expired
     */
    @Transient
    public boolean isExpired() {
        return expiryDate != null && expiryDate.isBefore(LocalDate.now());
    }

    /**
     * Check if card is active and usable
     */
    @Transient
    public boolean isUsable() {
        return cardStatus == CardStatus.ACTIVE && !isExpired() && deletedAt == null;
    }

    /**
     * Check if card is blocked
     */
    @Transient
    public boolean isBlocked() {
        return cardStatus == CardStatus.BLOCKED ||
               cardStatus == CardStatus.FRAUD_BLOCKED ||
               cardStatus == CardStatus.LOST_STOLEN ||
               cardStatus == CardStatus.SUSPENDED;
    }

    /**
     * Check if PIN is locked
     */
    @Transient
    public boolean isPinLocked() {
        return pinLockedUntil != null && pinLockedUntil.isAfter(LocalDateTime.now());
    }

    /**
     * Get masked card number (e.g., ****-****-****-1234)
     */
    @Transient
    public String getMaskedCardNumber() {
        if (cardNumberLastFour == null) {
            return "****-****-****-****";
        }
        return "****-****-****-" + cardNumberLastFour;
    }

    /**
     * Check if card has sufficient credit
     */
    @Transient
    public boolean hasSufficientCredit(BigDecimal amount) {
        if (availableCredit == null || amount == null) {
            return false;
        }
        return availableCredit.compareTo(amount) >= 0;
    }

    /**
     * Deduct from available credit
     */
    public void deductCredit(BigDecimal amount) {
        if (availableCredit != null && amount != null) {
            this.availableCredit = this.availableCredit.subtract(amount);
            this.outstandingBalance = this.outstandingBalance.add(amount);
        }
    }

    /**
     * Restore available credit (for reversals/refunds)
     */
    public void restoreCredit(BigDecimal amount) {
        if (availableCredit != null && amount != null) {
            this.availableCredit = this.availableCredit.add(amount);
            this.outstandingBalance = this.outstandingBalance.subtract(amount);
        }
    }

    /**
     * Mark card as activated
     */
    public void activate() {
        this.cardStatus = CardStatus.ACTIVE;
        this.activatedAt = LocalDateTime.now();
    }

    /**
     * Block card with reason
     */
    public void block(String reason) {
        this.cardStatus = CardStatus.BLOCKED;
        this.blockedAt = LocalDateTime.now();
        this.blockedReason = reason;
    }

    /**
     * Unblock card
     */
    public void unblock() {
        this.cardStatus = CardStatus.ACTIVE;
        this.blockedAt = null;
        this.blockedReason = null;
    }

    /**
     * Increment PIN attempts
     */
    public void incrementPinAttempts() {
        this.pinAttempts = (this.pinAttempts == null ? 0 : this.pinAttempts) + 1;

        // Lock PIN after 3 failed attempts
        if (this.pinAttempts >= 3) {
            this.pinLockedUntil = LocalDateTime.now().plusHours(24);
        }
    }

    /**
     * Reset PIN attempts (after successful PIN entry)
     */
    public void resetPinAttempts() {
        this.pinAttempts = 0;
        this.pinLockedUntil = null;
    }

    @PrePersist
    @Override
    protected void onCreate() {
        super.onCreate();
        if (cardStatus == null) {
            cardStatus = CardStatus.PENDING_ACTIVATION;
        }
        if (issueDate == null) {
            issueDate = LocalDate.now();
        }
    }
}
