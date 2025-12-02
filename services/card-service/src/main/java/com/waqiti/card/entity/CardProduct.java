package com.waqiti.card.entity;

import com.waqiti.card.enums.CardBrand;
import com.waqiti.card.enums.CardType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CardProduct entity - Product/Program definitions
 * Represents a card product offering with associated terms, limits, and fees
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Entity
@Table(name = "card_product", indexes = {
    @Index(name = "idx_product_id", columnList = "product_id"),
    @Index(name = "idx_product_type", columnList = "product_type"),
    @Index(name = "idx_product_network", columnList = "card_network"),
    @Index(name = "idx_product_issuer", columnList = "issuer_id"),
    @Index(name = "idx_product_active", columnList = "is_active"),
    @Index(name = "idx_product_deleted", columnList = "deleted_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CardProduct extends BaseAuditEntity {

    @Id
    @Column(name = "product_id", unique = true, nullable = false, length = 100)
    @NotBlank(message = "Product ID is required")
    private String productId;

    // ========================================================================
    // PRODUCT IDENTIFICATION
    // ========================================================================

    @Column(name = "product_name", nullable = false, length = 255)
    @NotBlank(message = "Product name is required")
    private String productName;

    @Column(name = "product_description", columnDefinition = "TEXT")
    private String productDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 20)
    @NotNull(message = "Product type is required")
    private CardType productType;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_network", nullable = false, length = 20)
    @NotNull(message = "Card network is required")
    private CardBrand cardNetwork;

    // ========================================================================
    // BIN & ISSUER DETAILS
    // ========================================================================

    @Column(name = "bin_range_start", length = 6)
    @Size(min = 6, max = 6)
    private String binRangeStart;

    @Column(name = "bin_range_end", length = 6)
    @Size(min = 6, max = 6)
    private String binRangeEnd;

    @Column(name = "issuer_id", length = 100)
    @Size(max = 100)
    private String issuerId;

    @Column(name = "issuer_name", length = 255)
    @Size(max = 255)
    private String issuerName;

    @Column(name = "program_manager", length = 255)
    @Size(max = 255)
    private String programManager;

    @Column(name = "processor", length = 100)
    @Size(max = 100)
    private String processor;

    // ========================================================================
    // FINANCIAL SETTINGS
    // ========================================================================

    @Column(name = "currency_code", length = 3)
    @NotBlank
    @Size(min = 3, max = 3)
    private String currencyCode;

    @Column(name = "default_credit_limit", precision = 18, scale = 2)
    @DecimalMin(value = "0.00")
    private BigDecimal defaultCreditLimit;

    @Column(name = "minimum_credit_limit", precision = 18, scale = 2)
    @DecimalMin(value = "0.00")
    private BigDecimal minimumCreditLimit;

    @Column(name = "maximum_credit_limit", precision = 18, scale = 2)
    @DecimalMin(value = "0.00")
    private BigDecimal maximumCreditLimit;

    @Column(name = "default_cash_limit", precision = 18, scale = 2)
    @DecimalMin(value = "0.00")
    private BigDecimal defaultCashLimit;

    @Column(name = "default_daily_limit", precision = 18, scale = 2)
    @DecimalMin(value = "0.00")
    private BigDecimal defaultDailyLimit;

    @Column(name = "default_monthly_limit", precision = 18, scale = 2)
    @DecimalMin(value = "0.00")
    private BigDecimal defaultMonthlyLimit;

    // ========================================================================
    // LIMITS CONFIGURATION (JSONB)
    // ========================================================================

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "spending_limits", columnDefinition = "jsonb")
    private Map<String, Object> spendingLimits;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "transaction_limits", columnDefinition = "jsonb")
    private Map<String, Object> transactionLimits;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "velocity_limits", columnDefinition = "jsonb")
    private Map<String, Object> velocityLimits;

    // ========================================================================
    // FEE STRUCTURE (JSONB)
    // ========================================================================

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fee_structure", columnDefinition = "jsonb")
    private Map<String, Object> feeStructure;

    @Column(name = "annual_fee", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal annualFee = BigDecimal.ZERO;

    @Column(name = "monthly_fee", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal monthlyFee = BigDecimal.ZERO;

    @Column(name = "issuance_fee", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal issuanceFee = BigDecimal.ZERO;

    @Column(name = "replacement_fee", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal replacementFee = BigDecimal.ZERO;

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
    // INTEREST RATES
    // ========================================================================

    @Column(name = "default_interest_rate", precision = 5, scale = 4)
    @DecimalMin(value = "0.0000")
    @DecimalMax(value = "1.0000")
    private BigDecimal defaultInterestRate;

    @Column(name = "penalty_interest_rate", precision = 5, scale = 4)
    @DecimalMin(value = "0.0000")
    @DecimalMax(value = "1.0000")
    private BigDecimal penaltyInterestRate;

    @Column(name = "cash_advance_interest_rate", precision = 5, scale = 4)
    @DecimalMin(value = "0.0000")
    @DecimalMax(value = "1.0000")
    private BigDecimal cashAdvanceInterestRate;

    // ========================================================================
    // REWARDS PROGRAM (JSONB)
    // ========================================================================

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rewards_program", columnDefinition = "jsonb")
    private Map<String, Object> rewardsProgram;

    @Column(name = "rewards_enabled")
    @Builder.Default
    private Boolean rewardsEnabled = false;

    @Column(name = "cashback_rate", precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal cashbackRate = BigDecimal.ZERO;

    // ========================================================================
    // FEATURES & CAPABILITIES
    // ========================================================================

    @Column(name = "contactless_enabled")
    @Builder.Default
    private Boolean contactlessEnabled = true;

    @Column(name = "virtual_card_enabled")
    @Builder.Default
    private Boolean virtualCardEnabled = true;

    @Column(name = "international_enabled")
    @Builder.Default
    private Boolean internationalEnabled = false;

    @Column(name = "online_transactions_enabled")
    @Builder.Default
    private Boolean onlineTransactionsEnabled = true;

    @Column(name = "atm_withdrawals_enabled")
    @Builder.Default
    private Boolean atmWithdrawalsEnabled = true;

    @Column(name = "pin_required")
    @Builder.Default
    private Boolean pinRequired = true;

    @Column(name = "three_ds_required")
    @Builder.Default
    private Boolean threeDsRequired = false;

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "activation_date")
    private LocalDate activationDate;

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    @Column(name = "card_validity_years")
    @Min(1)
    @Max(10)
    @Builder.Default
    private Integer cardValidityYears = 3;

    // ========================================================================
    // COMPLIANCE & LEGAL
    // ========================================================================

    @Column(name = "terms_and_conditions", columnDefinition = "TEXT")
    private String termsAndConditions;

    @Column(name = "terms_version", length = 20)
    @Size(max = 20)
    private String termsVersion;

    @Column(name = "regulatory_notes", columnDefinition = "TEXT")
    private String regulatoryNotes;

    // ========================================================================
    // RELATIONSHIPS
    // ========================================================================

    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Card> cards = new ArrayList<>();

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Check if product is currently active
     */
    @Transient
    public boolean isCurrentlyActive() {
        if (!isActive) {
            return false;
        }
        LocalDate now = LocalDate.now();
        if (activationDate != null && now.isBefore(activationDate)) {
            return false;
        }
        if (expirationDate != null && now.isAfter(expirationDate)) {
            return false;
        }
        return deletedAt == null;
    }

    /**
     * Check if BIN is within this product's range
     */
    @Transient
    public boolean isBinInRange(String bin) {
        if (bin == null || bin.length() < 6) {
            return false;
        }
        if (binRangeStart == null || binRangeEnd == null) {
            return false;
        }
        String binPrefix = bin.substring(0, 6);
        return binPrefix.compareTo(binRangeStart) >= 0 &&
               binPrefix.compareTo(binRangeEnd) <= 0;
    }

    /**
     * Calculate card expiry date from issue date
     */
    @Transient
    public LocalDate calculateExpiryDate(LocalDate issueDate) {
        if (issueDate == null || cardValidityYears == null) {
            return null;
        }
        return issueDate.plusYears(cardValidityYears);
    }

    /**
     * Check if credit limit is within product bounds
     */
    @Transient
    public boolean isValidCreditLimit(BigDecimal creditLimit) {
        if (creditLimit == null) {
            return false;
        }
        if (minimumCreditLimit != null && creditLimit.compareTo(minimumCreditLimit) < 0) {
            return false;
        }
        if (maximumCreditLimit != null && creditLimit.compareTo(maximumCreditLimit) > 0) {
            return false;
        }
        return true;
    }

    @PrePersist
    @Override
    protected void onCreate() {
        super.onCreate();
        if (isActive == null) {
            isActive = true;
        }
        if (activationDate == null) {
            activationDate = LocalDate.now();
        }
    }
}
