package com.waqiti.transaction.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Fee Configuration Entity
 *
 * <p>Manages fee structures for various transaction types including:</p>
 * <ul>
 *   <li>Fixed fees (flat amount)</li>
 *   <li>Percentage-based fees</li>
 *   <li>Tiered fees based on transaction amount</li>
 *   <li>Regional fee variations</li>
 *   <li>Merchant-specific fee structures</li>
 *   <li>Volume-based discount tiers</li>
 * </ul>
 *
 * <p><b>CRITICAL FIX:</b> This entity was missing and causing NullPointerException
 * in FeeCalculationService and TransactionProcessingService.</p>
 *
 * <h2>Fee Calculation Priority:</h2>
 * <ol>
 *   <li>Merchant-specific configuration (highest priority)</li>
 *   <li>Regional configuration</li>
 *   <li>Volume-tier configuration</li>
 *   <li>Amount-range configuration</li>
 *   <li>Default configuration (lowest priority)</li>
 * </ol>
 *
 * <h2>Supported Transaction Types:</h2>
 * <ul>
 *   <li>TRANSFER - P2P transfers</li>
 *   <li>PAYMENT - Merchant payments</li>
 *   <li>WITHDRAWAL - ATM/bank withdrawals</li>
 *   <li>DEPOSIT - Cash deposits</li>
 *   <li>INTERNATIONAL - Cross-border transfers</li>
 *   <li>CRYPTO - Cryptocurrency transactions</li>
 *   <li>BILL_PAY - Bill payments</li>
 * </ul>
 *
 * @author Waqiti Platform Team
 * @version 2.0.0-PRODUCTION
 * @since 2025-01-15 - CRITICAL FIX
 */
@Entity
@Table(name = "fee_configurations", indexes = {
    @Index(name = "idx_transaction_type", columnList = "transaction_type"),
    @Index(name = "idx_currency", columnList = "currency"),
    @Index(name = "idx_active", columnList = "active"),
    @Index(name = "idx_merchant_id", columnList = "merchant_id"),
    @Index(name = "idx_region", columnList = "region"),
    @Index(name = "idx_priority", columnList = "priority DESC")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"metadata"})
public class FeeConfiguration {

    /**
     * Primary key
     */
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Transaction type this fee applies to
     * Examples: TRANSFER, PAYMENT, WITHDRAWAL, DEPOSIT, INTERNATIONAL
     */
    @Column(name = "transaction_type", nullable = false, length = 50)
    private String transactionType;

    /**
     * Currency code (ISO 4217)
     * Examples: USD, EUR, GBP, NGN
     */
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /**
     * Fixed fee amount (flat fee regardless of transaction amount)
     * Example: $1.50 per transaction
     */
    @Column(name = "fixed_fee", precision = 19, scale = 4)
    private BigDecimal fixedFee;

    /**
     * Percentage fee rate (applied to transaction amount)
     * Example: 2.5% = 2.5
     * Stored as percentage value (not decimal), so 2.5% is stored as 2.5, not 0.025
     */
    @Column(name = "percentage_fee", precision = 8, scale = 4)
    private BigDecimal percentageFee;

    /**
     * Minimum fee amount (cap on low-value transactions)
     * Example: Minimum $0.50 even if percentage fee is lower
     */
    @Column(name = "min_fee", precision = 19, scale = 4)
    private BigDecimal minFee;

    /**
     * Maximum fee amount (cap on high-value transactions)
     * Example: Maximum $50 even if percentage fee is higher
     */
    @Column(name = "max_fee", precision = 19, scale = 4)
    private BigDecimal maxFee;

    /**
     * Minimum transaction amount for this fee configuration to apply
     * Used for tiered fee structures
     * Example: For transactions >= $1000
     */
    @Column(name = "min_amount", precision = 19, scale = 4)
    private BigDecimal minAmount;

    /**
     * Maximum transaction amount for this fee configuration to apply
     * Used for tiered fee structures
     * Example: For transactions <= $10000
     */
    @Column(name = "max_amount", precision = 19, scale = 4)
    private BigDecimal maxAmount;

    /**
     * Merchant ID for merchant-specific fee configuration
     * Null for standard fees
     */
    @Column(name = "merchant_id", length = 100)
    private String merchantId;

    /**
     * Region code for regional fee variations
     * Examples: US-CA (California), UK, EU, NG-LAG (Lagos, Nigeria)
     */
    @Column(name = "region", length = 50)
    private String region;

    /**
     * Payment method this fee applies to
     * Examples: CARD, ACH, WIRE, CRYPTO, WALLET
     */
    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    /**
     * Volume tier for volume-based pricing
     * Examples: BRONZE, SILVER, GOLD, PLATINUM
     * Higher tiers get lower fees
     */
    @Column(name = "volume_tier", length = 20)
    private String volumeTier;

    /**
     * Priority for fee selection when multiple configurations match
     * Higher number = higher priority
     * Default: 0
     */
    @Column(name = "priority", nullable = false)
    @Builder.Default
    private Integer priority = 0;

    /**
     * Whether this is the default/fallback configuration
     * Used when no specific configuration matches
     */
    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private Boolean isDefault = false;

    /**
     * Whether this configuration is currently active
     */
    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * Effective start date for this fee configuration
     */
    @Column(name = "effective_from")
    private LocalDateTime effectiveFrom;

    /**
     * Effective end date for this fee configuration
     * Null means no expiration
     */
    @Column(name = "effective_to")
    private LocalDateTime effectiveTo;

    /**
     * Description of this fee configuration
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * Internal notes for administrative purposes
     */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /**
     * Last review date for compliance purposes
     * Fee configurations should be reviewed periodically
     */
    @Column(name = "last_reviewed_at")
    private LocalDateTime lastReviewedAt;

    /**
     * User who last reviewed this configuration
     */
    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    /**
     * JSON metadata for additional configuration
     * Can store dynamic fee rules, promotional codes, etc.
     */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    /**
     * Creation timestamp
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * User who created this configuration
     */
    @Column(name = "created_by", length = 100)
    private String createdBy;

    /**
     * Last update timestamp
     */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * User who last updated this configuration
     */
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    /**
     * Version for optimistic locking
     */
    @Version
    @Column(name = "version")
    private Long version;

    // ========================================
    // Business Logic Methods
    // ========================================

    /**
     * Calculate fee for a given transaction amount
     *
     * @param amount transaction amount
     * @return calculated fee amount
     */
    public BigDecimal calculateFee(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal calculatedFee = BigDecimal.ZERO;

        // Add fixed fee
        if (fixedFee != null && fixedFee.compareTo(BigDecimal.ZERO) > 0) {
            calculatedFee = calculatedFee.add(fixedFee);
        }

        // Add percentage fee
        if (percentageFee != null && percentageFee.compareTo(BigDecimal.ZERO) > 0) {
            // percentageFee is stored as percentage (e.g., 2.5 for 2.5%)
            // Divide by 100 to get decimal multiplier
            BigDecimal percentageAmount = amount
                    .multiply(percentageFee)
                    .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
            calculatedFee = calculatedFee.add(percentageAmount);
        }

        // Apply minimum fee cap
        if (minFee != null && calculatedFee.compareTo(minFee) < 0) {
            calculatedFee = minFee;
        }

        // Apply maximum fee cap
        if (maxFee != null && calculatedFee.compareTo(maxFee) > 0) {
            calculatedFee = maxFee;
        }

        return calculatedFee.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Check if this configuration applies to the given transaction amount
     *
     * @param amount transaction amount
     * @return true if configuration applies to this amount
     */
    public boolean appliesToAmount(BigDecimal amount) {
        if (amount == null) {
            return false;
        }

        // Check minimum amount
        if (minAmount != null && amount.compareTo(minAmount) < 0) {
            return false;
        }

        // Check maximum amount
        if (maxAmount != null && amount.compareTo(maxAmount) > 0) {
            return false;
        }

        return true;
    }

    /**
     * Check if this configuration is currently effective
     *
     * @return true if configuration is currently in effect
     */
    public boolean isEffective() {
        LocalDateTime now = LocalDateTime.now();

        // Check active flag
        if (!active) {
            return false;
        }

        // Check effective from date
        if (effectiveFrom != null && now.isBefore(effectiveFrom)) {
            return false;
        }

        // Check effective to date
        if (effectiveTo != null && now.isAfter(effectiveTo)) {
            return false;
        }

        return true;
    }

    /**
     * Get a human-readable description of this fee configuration
     *
     * @return formatted fee description
     */
    public String getFeeDescription() {
        StringBuilder sb = new StringBuilder();

        if (fixedFee != null && fixedFee.compareTo(BigDecimal.ZERO) > 0) {
            sb.append(String.format("%s %.2f", currency, fixedFee));
        }

        if (percentageFee != null && percentageFee.compareTo(BigDecimal.ZERO) > 0) {
            if (sb.length() > 0) {
                sb.append(" + ");
            }
            sb.append(String.format("%.2f%%", percentageFee));
        }

        if (minFee != null) {
            sb.append(String.format(" (min: %s %.2f)", currency, minFee));
        }

        if (maxFee != null) {
            sb.append(String.format(" (max: %s %.2f)", currency, maxFee));
        }

        if (sb.length() == 0) {
            return "No fee";
        }

        return sb.toString();
    }

    /**
     * Mark configuration for review
     *
     * @param reviewedBy user performing review
     */
    public void markReviewed(String reviewedBy) {
        this.lastReviewedAt = LocalDateTime.now();
        this.reviewedBy = reviewedBy;
    }

    /**
     * Check if configuration needs review (hasn't been reviewed in 90 days)
     *
     * @return true if review is needed
     */
    public boolean needsReview() {
        if (lastReviewedAt == null) {
            return true;
        }
        return lastReviewedAt.isBefore(LocalDateTime.now().minusDays(90));
    }

    /**
     * Deactivate this configuration
     *
     * @param deactivatedBy user deactivating the configuration
     */
    public void deactivate(String deactivatedBy) {
        this.active = false;
        this.updatedBy = deactivatedBy;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Activate this configuration
     *
     * @param activatedBy user activating the configuration
     */
    public void activate(String activatedBy) {
        this.active = true;
        this.updatedBy = activatedBy;
        this.updatedAt = LocalDateTime.now();
    }
}
