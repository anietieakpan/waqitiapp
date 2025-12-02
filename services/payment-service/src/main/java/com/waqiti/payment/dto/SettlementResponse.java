package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Settlement Response DTO
 *
 * Contains comprehensive settlement processing results including batch details,
 * transaction summaries, reconciliation data, and payout information.
 *
 * COMPLIANCE RELEVANCE:
 * - SOX: Financial settlement audit trail
 * - PCI DSS: Payment settlement security
 * - Banking Regulations: Settlement reporting requirements
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 * @since 2024-01-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementResponse {

    /**
     * Unique settlement identifier
     */
    @NotNull
    private UUID settlementId;

    /**
     * Settlement batch identifier
     */
    @NotNull
    private UUID batchId;

    /**
     * Settlement status
     * Values: PENDING, PROCESSING, COMPLETED, FAILED, REVERSED, CANCELLED
     */
    @NotNull
    private String status;

    /**
     * Total settlement amount
     */
    @NotNull
    @PositiveOrZero
    private BigDecimal totalAmount;

    /**
     * Currency code (ISO 4217)
     */
    @NotNull
    private String currency;

    /**
     * Number of transactions in settlement
     */
    @Positive
    private int transactionCount;

    /**
     * Settlement date
     */
    @NotNull
    private LocalDate settlementDate;

    /**
     * Settlement period start date
     */
    private LocalDate periodStartDate;

    /**
     * Settlement period end date
     */
    private LocalDate periodEndDate;

    /**
     * Merchant ID
     */
    private UUID merchantId;

    /**
     * Merchant account number
     */
    private String merchantAccountNumber;

    /**
     * Gross sales amount
     */
    @PositiveOrZero
    private BigDecimal grossSalesAmount;

    /**
     * Total refunds amount
     */
    @PositiveOrZero
    private BigDecimal totalRefunds;

    /**
     * Total chargebacks amount
     */
    @PositiveOrZero
    private BigDecimal totalChargebacks;

    /**
     * Total fees amount
     */
    @PositiveOrZero
    private BigDecimal totalFees;

    /**
     * Total adjustments
     */
    private BigDecimal totalAdjustments;

    /**
     * Net settlement amount (after all deductions)
     */
    @NotNull
    private BigDecimal netAmount;

    /**
     * Previous balance carried forward
     */
    private BigDecimal previousBalance;

    /**
     * Reserve amount held
     */
    @PositiveOrZero
    private BigDecimal reserveAmount;

    /**
     * Reserve percentage
     */
    private BigDecimal reservePercentage;

    /**
     * Reserve release date
     */
    private LocalDate reserveReleaseDate;

    /**
     * Payout amount (net - reserve)
     */
    @NotNull
    private BigDecimal payoutAmount;

    /**
     * Payout method
     * Values: ACH, WIRE_TRANSFER, CHECK, WALLET
     */
    private String payoutMethod;

    /**
     * Payout status
     * Values: PENDING, SENT, COMPLETED, FAILED
     */
    private String payoutStatus;

    /**
     * Expected payout date
     */
    private LocalDate expectedPayoutDate;

    /**
     * Actual payout date
     */
    private LocalDate actualPayoutDate;

    /**
     * Bank account details (last 4 digits)
     */
    private String bankAccountLast4;

    /**
     * Transaction breakdown by type
     */
    private Map<String, TransactionSummary> transactionBreakdown;

    /**
     * Fee breakdown
     */
    private Map<String, BigDecimal> feeBreakdown;

    /**
     * Card type breakdown
     */
    private Map<String, TransactionSummary> cardTypeBreakdown;

    /**
     * Settlement initiated timestamp
     */
    @NotNull
    private LocalDateTime initiatedAt;

    /**
     * Settlement completed timestamp
     */
    private LocalDateTime completedAt;

    /**
     * Settlement processing duration (ms)
     */
    private long processingDurationMs;

    /**
     * Reconciliation status
     * Values: MATCHED, DISCREPANCY, UNDER_REVIEW, RESOLVED
     */
    private String reconciliationStatus;

    /**
     * Discrepancy amount if any
     */
    private BigDecimal discrepancyAmount;

    /**
     * Discrepancy reason
     */
    private String discrepancyReason;

    /**
     * Reconciled flag
     */
    private boolean reconciled;

    /**
     * Reconciliation date
     */
    private LocalDateTime reconciledAt;

    /**
     * Reconciled by
     */
    private UUID reconciledBy;

    /**
     * Settlement file name
     */
    private String settlementFileName;

    /**
     * Settlement file URL
     */
    private String settlementFileUrl;

    /**
     * Report generated flag
     */
    private boolean reportGenerated;

    /**
     * Report URL
     */
    private String reportUrl;

    /**
     * Processor reference number
     */
    private String processorReference;

    /**
     * External settlement ID
     */
    private String externalSettlementId;

    /**
     * Notification sent flag
     */
    private boolean notificationSent;

    /**
     * Error code if settlement failed
     */
    private String errorCode;

    /**
     * Error message if settlement failed
     */
    private String errorMessage;

    /**
     * Retry count
     */
    private int retryCount;

    /**
     * Notes or additional information
     */
    private String notes;

    /**
     * Audit trail reference
     */
    private UUID auditTrailId;

    /**
     * Created timestamp
     */
    private LocalDateTime createdAt;

    /**
     * Last updated timestamp
     */
    private LocalDateTime updatedAt;

    /**
     * Transaction Summary nested class
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionSummary {
        private int count;
        private BigDecimal amount;
        private BigDecimal fees;
        private BigDecimal netAmount;
    }
}
