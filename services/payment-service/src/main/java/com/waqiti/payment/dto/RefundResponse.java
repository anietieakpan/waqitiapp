package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Refund Response DTO
 *
 * Contains comprehensive refund processing results including refund details,
 * status, timeline, and compliance tracking information.
 *
 * COMPLIANCE RELEVANCE:
 * - PCI DSS: Refund transaction audit trail
 * - SOX: Financial reversal documentation
 * - GDPR: Customer financial data handling
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 * @since 2024-01-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundResponse {

    /**
     * Unique refund identifier
     */
    @NotNull
    private UUID refundId;

    /**
     * Original payment identifier being refunded
     */
    @NotNull
    private UUID paymentId;

    /**
     * Refund amount
     */
    @NotNull
    @Positive
    private BigDecimal amount;

    /**
     * Currency code (ISO 4217)
     */
    @NotNull
    private String currency;

    /**
     * Refund status
     * Values: INITIATED, PROCESSING, COMPLETED, FAILED, REVERSED
     */
    @NotNull
    private String status;

    /**
     * Reason for refund
     */
    private String reason;

    /**
     * Refund type
     * Values: FULL, PARTIAL, CHARGEBACK_RELATED
     */
    private String refundType;

    /**
     * Customer ID associated with the refund
     */
    private UUID customerId;

    /**
     * Merchant ID if applicable
     */
    private UUID merchantId;

    /**
     * Payment method used for refund
     * Values: CARD, BANK_ACCOUNT, WALLET, ORIGINAL_METHOD
     */
    private String refundMethod;

    /**
     * Original payment method
     */
    private String originalPaymentMethod;

    /**
     * Refund initiation timestamp
     */
    @NotNull
    private LocalDateTime initiatedAt;

    /**
     * Refund completion timestamp
     */
    private LocalDateTime completedAt;

    /**
     * Expected completion date
     */
    private LocalDateTime expectedCompletionDate;

    /**
     * Processing fee if applicable
     */
    private BigDecimal processingFee;

    /**
     * Net refund amount (after fees)
     */
    private BigDecimal netRefundAmount;

    /**
     * Transaction reference number
     */
    private String transactionReference;

    /**
     * External gateway reference
     */
    private String gatewayReference;

    /**
     * Approval code from payment processor
     */
    private String approvalCode;

    /**
     * Refund initiated by (user/system/admin)
     */
    private String initiatedBy;

    /**
     * Approval required flag
     */
    private boolean requiresApproval;

    /**
     * Approved by (if applicable)
     */
    private UUID approvedBy;

    /**
     * Approval timestamp
     */
    private LocalDateTime approvedAt;

    /**
     * Error code if refund failed
     */
    private String errorCode;

    /**
     * Error message if refund failed
     */
    private String errorMessage;

    /**
     * Retry count for failed refunds
     */
    private int retryCount;

    /**
     * Maximum retry attempts allowed
     */
    private int maxRetryAttempts;

    /**
     * Notification sent flag
     */
    private boolean notificationSent;

    /**
     * Compliance check status
     */
    private String complianceStatus;

    /**
     * Fraud check result
     */
    private String fraudCheckResult;

    /**
     * Risk score (0-100)
     */
    private int riskScore;

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
}
