package com.waqiti.card.entity;

import com.waqiti.card.enums.DisputeStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CardDispute entity - Dispute/Chargeback record
 * Represents a cardholder dispute of a transaction
 *
 * Disputes follow the card network dispute resolution process:
 * 1. Cardholder files dispute
 * 2. Investigation
 * 3. Merchant response
 * 4. Resolution (favor cardholder or merchant)
 * 5. Possible arbitration
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Entity
@Table(name = "card_dispute", indexes = {
    @Index(name = "idx_dispute_id", columnList = "dispute_id"),
    @Index(name = "idx_dispute_transaction", columnList = "transaction_id"),
    @Index(name = "idx_dispute_card", columnList = "card_id"),
    @Index(name = "idx_dispute_user", columnList = "user_id"),
    @Index(name = "idx_dispute_status", columnList = "dispute_status"),
    @Index(name = "idx_dispute_filed_date", columnList = "filed_date"),
    @Index(name = "idx_dispute_category", columnList = "dispute_category"),
    @Index(name = "idx_dispute_deleted", columnList = "deleted_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CardDispute extends BaseAuditEntity {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // ========================================================================
    // DISPUTE IDENTIFICATION
    // ========================================================================

    @Column(name = "dispute_id", unique = true, nullable = false, length = 100)
    @NotBlank(message = "Dispute ID is required")
    private String disputeId;

    @Column(name = "case_number", unique = true, length = 50)
    @Size(max = 50)
    private String caseNumber;

    @Column(name = "network_case_id", length = 100)
    @Size(max = 100)
    private String networkCaseId;

    // ========================================================================
    // REFERENCES
    // ========================================================================

    @Column(name = "transaction_id", nullable = false)
    @NotNull(message = "Transaction ID is required")
    private UUID transactionId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", insertable = false, updatable = false)
    private CardTransaction transaction;

    @Column(name = "card_id", nullable = false)
    @NotNull(message = "Card ID is required")
    private UUID cardId;

    @Column(name = "user_id", nullable = false)
    @NotNull(message = "User ID is required")
    private UUID userId;

    // ========================================================================
    // DISPUTE DETAILS
    // ========================================================================

    @Enumerated(EnumType.STRING)
    @Column(name = "dispute_status", nullable = false, length = 40)
    @NotNull(message = "Dispute status is required")
    @Builder.Default
    private DisputeStatus disputeStatus = DisputeStatus.OPEN;

    @Column(name = "dispute_category", length = 50)
    @Size(max = 50)
    private String disputeCategory;

    @Column(name = "dispute_reason_code", length = 10)
    @Size(max = 10)
    private String disputeReasonCode;

    @Column(name = "dispute_reason", columnDefinition = "TEXT")
    private String disputeReason;

    @Column(name = "cardholder_explanation", columnDefinition = "TEXT")
    private String cardholderExplanation;

    // ========================================================================
    // FINANCIAL DETAILS
    // ========================================================================

    @Column(name = "disputed_amount", precision = 18, scale = 2, nullable = false)
    @NotNull
    @DecimalMin(value = "0.00")
    private BigDecimal disputedAmount;

    @Column(name = "currency_code", length = 3, nullable = false)
    @NotBlank
    @Size(min = 3, max = 3)
    private String currencyCode;

    @Column(name = "transaction_amount", precision = 18, scale = 2)
    private BigDecimal transactionAmount;

    @Column(name = "provisional_credit_amount", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal provisionalCreditAmount = BigDecimal.ZERO;

    @Column(name = "final_credit_amount", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal finalCreditAmount = BigDecimal.ZERO;

    @Column(name = "chargeback_amount", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal chargebackAmount = BigDecimal.ZERO;

    // ========================================================================
    // TIMELINE
    // ========================================================================

    @Column(name = "filed_date", nullable = false)
    @NotNull
    @Builder.Default
    private LocalDateTime filedDate = LocalDateTime.now();

    @Column(name = "merchant_response_deadline")
    private LocalDateTime merchantResponseDeadline;

    @Column(name = "merchant_response_date")
    private LocalDateTime merchantResponseDate;

    @Column(name = "investigation_started_date")
    private LocalDateTime investigationStartedDate;

    @Column(name = "investigation_completed_date")
    private LocalDateTime investigationCompletedDate;

    @Column(name = "resolution_date")
    private LocalDateTime resolutionDate;

    @Column(name = "closed_date")
    private LocalDateTime closedDate;

    // ========================================================================
    // MERCHANT DETAILS
    // ========================================================================

    @Column(name = "merchant_id", length = 100)
    @Size(max = 100)
    private String merchantId;

    @Column(name = "merchant_name", length = 255)
    @Size(max = 255)
    private String merchantName;

    @Column(name = "merchant_response", columnDefinition = "TEXT")
    private String merchantResponse;

    @Column(name = "merchant_evidence_submitted")
    private Boolean merchantEvidenceSubmitted;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "merchant_evidence", columnDefinition = "jsonb")
    private Map<String, Object> merchantEvidence;

    // ========================================================================
    // CARDHOLDER EVIDENCE
    // ========================================================================

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cardholder_evidence", columnDefinition = "jsonb")
    private Map<String, Object> cardholderEvidence;

    @ElementCollection
    @CollectionTable(name = "card_dispute_documents", joinColumns = @JoinColumn(name = "dispute_id"))
    @Column(name = "document_url")
    @Builder.Default
    private List<String> documentUrls = new ArrayList<>();

    // ========================================================================
    // PROVISIONAL CREDIT
    // ========================================================================

    @Column(name = "provisional_credit_issued")
    @Builder.Default
    private Boolean provisionalCreditIssued = false;

    @Column(name = "provisional_credit_date")
    private LocalDateTime provisionalCreditDate;

    @Column(name = "provisional_credit_reversed")
    @Builder.Default
    private Boolean provisionalCreditReversed = false;

    @Column(name = "provisional_credit_reversal_date")
    private LocalDateTime provisionalCreditReversalDate;

    // ========================================================================
    // CHARGEBACK
    // ========================================================================

    @Column(name = "chargeback_issued")
    @Builder.Default
    private Boolean chargebackIssued = false;

    @Column(name = "chargeback_date")
    private LocalDateTime chargebackDate;

    @Column(name = "chargeback_reference_number", length = 100)
    @Size(max = 100)
    private String chargebackReferenceNumber;

    @Column(name = "representment_received")
    @Builder.Default
    private Boolean representmentReceived = false;

    @Column(name = "representment_date")
    private LocalDateTime representmentDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "representment_evidence", columnDefinition = "jsonb")
    private Map<String, Object> representmentEvidence;

    // ========================================================================
    // RESOLUTION
    // ========================================================================

    @Column(name = "resolution_outcome", length = 50)
    @Size(max = 50)
    private String resolutionOutcome;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "resolved_in_favor_of", length = 20)
    @Size(max = 20)
    private String resolvedInFavorOf;

    @Column(name = "final_decision_maker", length = 50)
    @Size(max = 50)
    private String finalDecisionMaker;

    // ========================================================================
    // ARBITRATION
    // ========================================================================

    @Column(name = "escalated_to_arbitration")
    @Builder.Default
    private Boolean escalatedToArbitration = false;

    @Column(name = "arbitration_date")
    private LocalDateTime arbitrationDate;

    @Column(name = "arbitration_outcome", length = 100)
    @Size(max = 100)
    private String arbitrationOutcome;

    @Column(name = "arbitration_fee", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal arbitrationFee = BigDecimal.ZERO;

    // ========================================================================
    // NETWORK COMMUNICATION
    // ========================================================================

    @Column(name = "network_name", length = 50)
    @Size(max = 50)
    private String networkName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "network_messages", columnDefinition = "jsonb")
    private List<Map<String, Object>> networkMessages;

    // ========================================================================
    // FRAUD INDICATOR
    // ========================================================================

    @Column(name = "is_fraud_related")
    @Builder.Default
    private Boolean isFraudRelated = false;

    @Column(name = "fraud_report_filed")
    @Builder.Default
    private Boolean fraudReportFiled = false;

    @Column(name = "law_enforcement_notified")
    @Builder.Default
    private Boolean lawEnforcementNotified = false;

    // ========================================================================
    // INTERNAL TRACKING
    // ========================================================================

    @Column(name = "assigned_to", length = 100)
    @Size(max = 100)
    private String assignedTo;

    @Column(name = "priority", length = 20)
    @Size(max = 20)
    private String priority;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "internal_notes", columnDefinition = "jsonb")
    private List<Map<String, Object>> internalNotes;

    // ========================================================================
    // METADATA
    // ========================================================================

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Check if dispute is open/active
     */
    @Transient
    public boolean isActive() {
        return disputeStatus != DisputeStatus.CLOSED &&
               disputeStatus != DisputeStatus.WITHDRAWN &&
               deletedAt == null;
    }

    /**
     * Check if dispute is resolved
     */
    @Transient
    public boolean isResolved() {
        return disputeStatus == DisputeStatus.RESOLVED_CARDHOLDER_FAVOR ||
               disputeStatus == DisputeStatus.RESOLVED_MERCHANT_FAVOR ||
               disputeStatus == DisputeStatus.CLOSED;
    }

    /**
     * Check if waiting for merchant response
     */
    @Transient
    public boolean isWaitingForMerchantResponse() {
        return disputeStatus == DisputeStatus.AWAITING_MERCHANT_RESPONSE &&
               merchantResponseDeadline != null &&
               LocalDateTime.now().isBefore(merchantResponseDeadline);
    }

    /**
     * Check if merchant response is overdue
     */
    @Transient
    public boolean isMerchantResponseOverdue() {
        return disputeStatus == DisputeStatus.AWAITING_MERCHANT_RESPONSE &&
               merchantResponseDeadline != null &&
               LocalDateTime.now().isAfter(merchantResponseDeadline);
    }

    /**
     * Issue provisional credit to cardholder
     */
    public void issueProvisionalCredit(BigDecimal amount) {
        this.provisionalCreditIssued = true;
        this.provisionalCreditAmount = amount;
        this.provisionalCreditDate = LocalDateTime.now();
    }

    /**
     * Reverse provisional credit
     */
    public void reverseProvisionalCredit() {
        this.provisionalCreditReversed = true;
        this.provisionalCreditReversalDate = LocalDateTime.now();
    }

    /**
     * Issue chargeback
     */
    public void issueChargeback(BigDecimal amount, String referenceNumber) {
        this.chargebackIssued = true;
        this.chargebackAmount = amount;
        this.chargebackDate = LocalDateTime.now();
        this.chargebackReferenceNumber = referenceNumber;
        this.disputeStatus = DisputeStatus.CHARGEBACK_ISSUED;
    }

    /**
     * Receive representment from merchant
     */
    public void receiveRepresentment(Map<String, Object> evidence) {
        this.representmentReceived = true;
        this.representmentDate = LocalDateTime.now();
        this.representmentEvidence = evidence;
        this.disputeStatus = DisputeStatus.REPRESENTMENT_RECEIVED;
    }

    /**
     * Escalate to arbitration
     */
    public void escalateToArbitration(BigDecimal fee) {
        this.escalatedToArbitration = true;
        this.arbitrationDate = LocalDateTime.now();
        this.arbitrationFee = fee;
        this.disputeStatus = DisputeStatus.ARBITRATION;
    }

    /**
     * Resolve in favor of cardholder
     */
    public void resolveInFavorOfCardholder(String outcome, BigDecimal creditAmount) {
        this.disputeStatus = DisputeStatus.RESOLVED_CARDHOLDER_FAVOR;
        this.resolutionDate = LocalDateTime.now();
        this.resolutionOutcome = outcome;
        this.resolvedInFavorOf = "CARDHOLDER";
        this.finalCreditAmount = creditAmount;
    }

    /**
     * Resolve in favor of merchant
     */
    public void resolveInFavorOfMerchant(String outcome) {
        this.disputeStatus = DisputeStatus.RESOLVED_MERCHANT_FAVOR;
        this.resolutionDate = LocalDateTime.now();
        this.resolutionOutcome = outcome;
        this.resolvedInFavorOf = "MERCHANT";

        // Reverse provisional credit if issued
        if (provisionalCreditIssued && !provisionalCreditReversed) {
            reverseProvisionalCredit();
        }
    }

    /**
     * Withdraw dispute
     */
    public void withdraw(String reason) {
        this.disputeStatus = DisputeStatus.WITHDRAWN;
        this.closedDate = LocalDateTime.now();
        this.resolutionNotes = "Withdrawn by cardholder: " + reason;

        // Reverse provisional credit if issued
        if (provisionalCreditIssued && !provisionalCreditReversed) {
            reverseProvisionalCredit();
        }
    }

    /**
     * Close dispute
     */
    public void close(String notes) {
        this.disputeStatus = DisputeStatus.CLOSED;
        this.closedDate = LocalDateTime.now();
        this.resolutionNotes = notes;
    }

    /**
     * Add internal note
     */
    public void addInternalNote(String author, String note) {
        if (internalNotes == null) {
            internalNotes = new ArrayList<>();
        }
        Map<String, Object> noteEntry = Map.of(
            "timestamp", LocalDateTime.now().toString(),
            "author", author,
            "note", note
        );
        internalNotes.add(noteEntry);
    }

    @PrePersist
    @Override
    protected void onCreate() {
        super.onCreate();
        if (disputeStatus == null) {
            disputeStatus = DisputeStatus.OPEN;
        }
        if (filedDate == null) {
            filedDate = LocalDateTime.now();
        }
        if (provisionalCreditIssued == null) {
            provisionalCreditIssued = false;
        }
        if (provisionalCreditReversed == null) {
            provisionalCreditReversed = false;
        }
        if (chargebackIssued == null) {
            chargebackIssued = false;
        }
        if (representmentReceived == null) {
            representmentReceived = false;
        }
        if (escalatedToArbitration == null) {
            escalatedToArbitration = false;
        }
        if (isFraudRelated == null) {
            isFraudRelated = false;
        }
        if (fraudReportFiled == null) {
            fraudReportFiled = false;
        }
        if (lawEnforcementNotified == null) {
            lawEnforcementNotified = false;
        }

        // Set merchant response deadline (typically 10 days from filing)
        if (merchantResponseDeadline == null) {
            merchantResponseDeadline = filedDate.plusDays(10);
        }
    }
}
