package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Transaction Dispute Opened Event
 *
 * Published when a customer opens a dispute for a transaction
 * Triggers investigation, provisional credit evaluation, and chargeback workflows
 *
 * Regulatory Compliance:
 * - Regulation E: Electronic fund transfer disputes
 * - Regulation Z: Credit card dispute rights
 * - PCI DSS: Secure dispute data handling
 *
 * @author Waqiti Platform Team
 * @version 1.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionDisputeOpenedEvent extends FinancialEvent {

    // Dispute Identifiers
    private UUID disputeId;
    private UUID transactionId;
    private String disputeReferenceNumber;

    // Party Information
    private UUID customerId;
    private UUID merchantId;
    private String merchantName;
    private String merchantCategory;

    // Dispute Details
    private String disputeType;  // FRAUD, UNAUTHORIZED, QUALITY, NOT_RECEIVED, etc.
    private String disputeReason;
    private String customerDescription;
    private BigDecimal disputedAmount;
    private BigDecimal transactionAmount;
    private String transactionCurrency;

    // Transaction Context
    private LocalDateTime transactionDate;
    private String transactionType;
    private String paymentMethod;
    private String cardLast4;
    private String authorizationCode;

    // Dispute Classification
    private String disputePriority;  // LOW, MEDIUM, HIGH, CRITICAL
    private String disputeCategory;  // CHARGEBACK, RETRIEVAL, INQUIRY
    private boolean provisionalCreditEligible;
    private boolean chargebackRisk;

    // Risk Assessment
    private Double fraudScore;
    private boolean suspectedFraud;
    private String riskFactors;
    private boolean firstTimeDispute;
    private Integer customerDisputeHistory;

    // Regulatory & Compliance
    private String regulatoryCategory;  // REG_E, REG_Z, VISA, MASTERCARD
    private Integer regulatoryDeadlineDays;
    private LocalDateTime provisionalCreditDeadline;
    private LocalDateTime resolutionDeadline;

    // Evidence & Documentation
    private String evidenceRequired;
    private boolean merchantContactAttempted;
    private String merchantResponse;

    // Channel Information
    private String disputeChannel;  // APP, WEB, PHONE, EMAIL, BRANCH
    private String customerIpAddress;
    private String deviceFingerprint;
    private String userAgent;

    // Metadata
    private Map<String, Object> additionalMetadata;
    private String sourceService;
    private String initiatedBy;
    private Instant openedAt;

    // Financial Impact
    private boolean accountDebited;
    private BigDecimal availableBalance;
    private BigDecimal pendingDisputes;

    /**
     * Dispute Types
     */
    public enum DisputeType {
        FRAUD,                    // Unauthorized fraudulent transaction
        UNAUTHORIZED,             // Card/account used without permission
        NOT_RECEIVED,            // Goods/services not received
        DEFECTIVE_PRODUCT,       // Product defective or not as described
        DUPLICATE_CHARGE,        // Charged multiple times
        INCORRECT_AMOUNT,        // Wrong amount charged
        CANCELLED_SUBSCRIPTION,   // Subscription cancelled but still charged
        MERCHANT_ERROR,          // Merchant processing error
        ATM_CASH_NOT_DISPENSED,  // ATM did not dispense cash
        QUALITY_DISPUTE,         // Service/product quality issue
        CREDIT_NOT_PROCESSED,    // Refund/credit not processed
        OTHER                     // Other dispute reason
    }

    /**
     * Dispute Priority Levels
     */
    public enum DisputePriority {
        LOW,       // Standard processing (>$50, low fraud risk)
        MEDIUM,    // Expedited processing ($50-$500, moderate risk)
        HIGH,      // Priority processing ($500-$5000, high value)
        CRITICAL   // Immediate attention (>$5000, confirmed fraud, regulatory risk)
    }

    /**
     * Create a fraud dispute event
     */
    public static TransactionDisputeOpenedEvent createFraudDispute(
            UUID disputeId,
            UUID transactionId,
            UUID customerId,
            BigDecimal disputedAmount,
            String reason) {

        return TransactionDisputeOpenedEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("TRANSACTION_DISPUTE_OPENED")
                .eventCategory("DISPUTE")
                .disputeId(disputeId)
                .transactionId(transactionId)
                .customerId(customerId)
                .disputedAmount(disputedAmount)
                .disputeType(DisputeType.FRAUD.name())
                .disputeReason(reason)
                .suspectedFraud(true)
                .provisionalCreditEligible(true)
                .chargebackRisk(true)
                .disputePriority(DisputePriority.HIGH.name())
                .regulatoryCategory("REG_E")
                .regulatoryDeadlineDays(10)
                .openedAt(Instant.now())
                .timestamp(Instant.now())
                .sourceSystem("dispute-service")
                .build();
    }

    /**
     * Create a quality dispute event
     */
    public static TransactionDisputeOpenedEvent createQualityDispute(
            UUID disputeId,
            UUID transactionId,
            UUID customerId,
            String merchantName,
            BigDecimal disputedAmount,
            String description) {

        return TransactionDisputeOpenedEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("TRANSACTION_DISPUTE_OPENED")
                .eventCategory("DISPUTE")
                .disputeId(disputeId)
                .transactionId(transactionId)
                .customerId(customerId)
                .merchantName(merchantName)
                .disputedAmount(disputedAmount)
                .disputeType(DisputeType.QUALITY_DISPUTE.name())
                .customerDescription(description)
                .suspectedFraud(false)
                .provisionalCreditEligible(false)
                .chargebackRisk(false)
                .disputePriority(DisputePriority.MEDIUM.name())
                .regulatoryCategory("MERCHANT")
                .regulatoryDeadlineDays(30)
                .merchantContactAttempted(false)
                .openedAt(Instant.now())
                .timestamp(Instant.now())
                .sourceSystem("dispute-service")
                .build();
    }

    /**
     * Determine if provisional credit is required
     */
    public boolean requiresProvisionalCredit() {
        return provisionalCreditEligible &&
               disputedAmount != null &&
               disputedAmount.compareTo(BigDecimal.ZERO) > 0 &&
               (DisputeType.FRAUD.name().equals(disputeType) ||
                DisputeType.UNAUTHORIZED.name().equals(disputeType));
    }

    /**
     * Calculate SLA deadline based on regulatory requirements
     */
    public LocalDateTime calculateSLADeadline() {
        if (regulatoryDeadlineDays != null && openedAt != null) {
            return LocalDateTime.ofInstant(openedAt, java.time.ZoneOffset.UTC)
                    .plusDays(regulatoryDeadlineDays);
        }
        // Default to 45 days if not specified
        return LocalDateTime.ofInstant(openedAt != null ? openedAt : Instant.now(),
                java.time.ZoneOffset.UTC).plusDays(45);
    }

    /**
     * Check if dispute is high risk for chargeback
     */
    public boolean isHighChargebackRisk() {
        return chargebackRisk ||
               (fraudScore != null && fraudScore > 0.7) ||
               (disputedAmount != null && disputedAmount.compareTo(new BigDecimal("5000")) > 0);
    }

    // Override methods from FinancialEvent
    // Note: getEventId() is already properly implemented in parent FinancialEvent class

    @Override
    public String getUserId() {
        return customerId != null ? customerId.toString() : null;
    }

    @Override
    public UUID getEntityId() {
        return disputeId;
    }

    @Override
    public BigDecimal getAmount() {
        return disputedAmount;
    }

    @Override
    public String getCurrency() {
        return transactionCurrency;
    }
}
