package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fee Calculation Response DTO
 *
 * Contains detailed fee breakdown for transactions including base fees,
 * percentage fees, tiered pricing, discounts, and total calculations.
 *
 * COMPLIANCE RELEVANCE:
 * - SOX: Fee transparency and documentation
 * - Consumer Protection: Clear fee disclosure
 * - Merchant Agreements: Fee structure compliance
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 * @since 2024-01-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeCalculationResponse {

    /**
     * Calculation identifier
     */
    private UUID calculationId;

    /**
     * Transaction amount (before fees)
     */
    @NotNull
    @PositiveOrZero
    private BigDecimal transactionAmount;

    /**
     * Currency code (ISO 4217)
     */
    @NotNull
    private String currency;

    /**
     * Transaction type
     * Values: CARD_PAYMENT, ACH_TRANSFER, WIRE_TRANSFER, INTERNATIONAL_TRANSFER,
     *         WALLET_TRANSFER, CASH_DEPOSIT, CHECK_DEPOSIT, REFUND, CHARGEBACK
     */
    @NotNull
    private String transactionType;

    /**
     * Base fee amount (flat fee)
     */
    @PositiveOrZero
    private BigDecimal baseFee;

    /**
     * Percentage fee rate (e.g., 2.9 for 2.9%)
     */
    @PositiveOrZero
    private BigDecimal percentageFeeRate;

    /**
     * Percentage fee amount
     */
    @PositiveOrZero
    private BigDecimal percentageFeeAmount;

    /**
     * Total fee amount
     */
    @NotNull
    @PositiveOrZero
    private BigDecimal feeAmount;

    /**
     * Net amount (transaction amount + fees or - fees depending on context)
     */
    @NotNull
    private BigDecimal netAmount;

    /**
     * Processing fee
     */
    @PositiveOrZero
    private BigDecimal processingFee;

    /**
     * Network fee (card network, ACH network, etc.)
     */
    @PositiveOrZero
    private BigDecimal networkFee;

    /**
     * Service fee
     */
    @PositiveOrZero
    private BigDecimal serviceFee;

    /**
     * Platform fee
     */
    @PositiveOrZero
    private BigDecimal platformFee;

    /**
     * International transaction fee
     */
    @PositiveOrZero
    private BigDecimal internationalFee;

    /**
     * Currency conversion fee
     */
    @PositiveOrZero
    private BigDecimal currencyConversionFee;

    /**
     * Expedited processing fee
     */
    @PositiveOrZero
    private BigDecimal expeditedFee;

    /**
     * Tiered pricing level applied
     */
    private String pricingTier;

    /**
     * Volume discount percentage
     */
    private BigDecimal volumeDiscountPercentage;

    /**
     * Volume discount amount
     */
    @PositiveOrZero
    private BigDecimal volumeDiscountAmount;

    /**
     * Promotional discount code
     */
    private String promotionalCode;

    /**
     * Promotional discount amount
     */
    @PositiveOrZero
    private BigDecimal promotionalDiscountAmount;

    /**
     * Total discounts applied
     */
    @PositiveOrZero
    private BigDecimal totalDiscounts;

    /**
     * Fee breakdown by category
     */
    private Map<String, BigDecimal> feeBreakdown;

    /**
     * Fee calculation method
     * Values: STANDARD, TIERED, VOLUME_BASED, NEGOTIATED, PROMOTIONAL
     */
    private String calculationMethod;

    /**
     * Fee schedule ID used
     */
    private UUID feeScheduleId;

    /**
     * Fee schedule version
     */
    private String feeScheduleVersion;

    /**
     * Minimum fee applied flag
     */
    private boolean minimumFeeApplied;

    /**
     * Minimum fee amount if applied
     */
    private BigDecimal minimumFeeAmount;

    /**
     * Maximum fee applied flag
     */
    private boolean maximumFeeApplied;

    /**
     * Maximum fee amount if applied
     */
    private BigDecimal maximumFeeAmount;

    /**
     * Fee cap reached flag
     */
    private boolean feeCapReached;

    /**
     * Merchant category code (MCC)
     */
    private String merchantCategoryCode;

    /**
     * Payment method specific fees
     */
    private Map<String, BigDecimal> paymentMethodFees;

    /**
     * Risk-based fee adjustment
     */
    private BigDecimal riskBasedAdjustment;

    /**
     * Compliance-related fees
     */
    private BigDecimal complianceFee;

    /**
     * Tax amount if applicable
     */
    @PositiveOrZero
    private BigDecimal taxAmount;

    /**
     * Tax rate percentage
     */
    private BigDecimal taxRate;

    /**
     * Fee components list
     */
    private List<FeeComponent> feeComponents;

    /**
     * Effective date of fee calculation
     */
    private LocalDateTime effectiveDate;

    /**
     * Calculation timestamp
     */
    @NotNull
    private LocalDateTime calculatedAt;

    /**
     * Calculated by (user/system)
     */
    private String calculatedBy;

    /**
     * Fee estimate flag (vs actual fee)
     */
    private boolean isEstimate;

    /**
     * Notes or additional information
     */
    private String notes;

    /**
     * Fee Component nested class
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeeComponent {
        private String componentName;
        private String componentType;
        private BigDecimal componentAmount;
        private String description;
        private boolean waived;
    }
}
