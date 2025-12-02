package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Chargeback Response DTO
 *
 * Contains comprehensive chargeback processing results including chargeback details,
 * dispute status, timeline, evidence tracking, and resolution information.
 *
 * COMPLIANCE RELEVANCE:
 * - PCI DSS: Chargeback transaction security
 * - SOX: Financial dispute documentation
 * - Card Network Rules: Visa, Mastercard chargeback handling
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 * @since 2024-01-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargebackResponse {

    /**
     * Unique chargeback identifier
     */
    @NotNull
    private UUID chargebackId;

    /**
     * Original payment identifier
     */
    @NotNull
    private UUID paymentId;

    /**
     * Chargeback amount
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
     * Chargeback status
     * Values: RECEIVED, UNDER_REVIEW, ACCEPTED, DISPUTED, WON, LOST, REVERSED
     */
    @NotNull
    private String status;

    /**
     * Chargeback reason code
     */
    @NotNull
    private String reasonCode;

    /**
     * Human-readable reason description
     */
    private String reason;

    /**
     * Chargeback type
     * Values: FRAUD, AUTHORIZATION, PROCESSING_ERROR, CONSUMER_DISPUTE, FRIENDLY_FRAUD
     */
    private String chargebackType;

    /**
     * Customer ID
     */
    private UUID customerId;

    /**
     * Merchant ID
     */
    @NotNull
    private UUID merchantId;

    /**
     * Card network
     * Values: VISA, MASTERCARD, AMEX, DISCOVER
     */
    private String cardNetwork;

    /**
     * Case number from card network
     */
    private String caseNumber;

    /**
     * ARN (Acquirer Reference Number)
     */
    private String acquirerReferenceNumber;

    /**
     * Chargeback received date
     */
    @NotNull
    private LocalDateTime receivedAt;

    /**
     * Dispute deadline
     */
    private LocalDateTime disputeDeadline;

    /**
     * Response submitted date
     */
    private LocalDateTime responseSubmittedAt;

    /**
     * Resolution date
     */
    private LocalDateTime resolvedAt;

    /**
     * Expected resolution date
     */
    private LocalDateTime expectedResolutionDate;

    /**
     * Days until deadline
     */
    private int daysUntilDeadline;

    /**
     * Original transaction date
     */
    private LocalDateTime originalTransactionDate;

    /**
     * Chargeback fee amount
     */
    private BigDecimal chargebackFee;

    /**
     * Total loss amount (amount + fees)
     */
    private BigDecimal totalLoss;

    /**
     * Evidence submitted flag
     */
    private boolean evidenceSubmitted;

    /**
     * Evidence submission date
     */
    private LocalDateTime evidenceSubmittedAt;

    /**
     * Evidence documents list
     */
    private List<String> evidenceDocuments;

    /**
     * Merchant response text
     */
    private String merchantResponse;

    /**
     * Dispute won flag
     */
    private boolean won;

    /**
     * Win/loss reason
     */
    private String resolutionReason;

    /**
     * Amount recovered if won
     */
    private BigDecimal amountRecovered;

    /**
     * Representment flag
     */
    private boolean representmentFiled;

    /**
     * Representment deadline
     */
    private LocalDateTime representmentDeadline;

    /**
     * Pre-arbitration flag
     */
    private boolean preArbitration;

    /**
     * Arbitration flag
     */
    private boolean arbitration;

    /**
     * Risk score (0-100)
     */
    private int riskScore;

    /**
     * Fraud likelihood score
     */
    private double fraudLikelihood;

    /**
     * Assigned to (support agent)
     */
    private UUID assignedTo;

    /**
     * Priority level
     * Values: LOW, MEDIUM, HIGH, CRITICAL
     */
    private String priority;

    /**
     * Notification sent flag
     */
    private boolean notificationSent;

    /**
     * Notification sent to merchant
     */
    private boolean merchantNotified;

    /**
     * Compliance check status
     */
    private String complianceStatus;

    /**
     * Internal notes
     */
    private String internalNotes;

    /**
     * External processor reference
     */
    private String processorReference;

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
