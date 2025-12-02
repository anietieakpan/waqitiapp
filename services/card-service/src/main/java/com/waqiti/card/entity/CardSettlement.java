package com.waqiti.card.entity;

import com.waqiti.card.enums.SettlementStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * CardSettlement entity - Settlement record
 * Represents the settlement of a card transaction with the card network
 *
 * Settlements occur after transactions are completed and authorized,
 * representing the final clearing and movement of funds.
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Entity
@Table(name = "card_settlement", indexes = {
    @Index(name = "idx_settlement_id", columnList = "settlement_id"),
    @Index(name = "idx_settlement_transaction", columnList = "transaction_id"),
    @Index(name = "idx_settlement_status", columnList = "settlement_status"),
    @Index(name = "idx_settlement_date", columnList = "settlement_date"),
    @Index(name = "idx_settlement_batch", columnList = "batch_id"),
    @Index(name = "idx_settlement_reconciliation", columnList = "reconciliation_status"),
    @Index(name = "idx_settlement_deleted", columnList = "deleted_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CardSettlement extends BaseAuditEntity {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // ========================================================================
    // SETTLEMENT IDENTIFICATION
    // ========================================================================

    @Column(name = "settlement_id", unique = true, nullable = false, length = 100)
    @NotBlank(message = "Settlement ID is required")
    private String settlementId;

    @Column(name = "external_settlement_id", length = 100)
    @Size(max = 100)
    private String externalSettlementId;

    @Column(name = "batch_id", length = 100)
    @Size(max = 100)
    private String batchId;

    @Column(name = "batch_sequence_number")
    @Min(0)
    private Integer batchSequenceNumber;

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

    @Column(name = "authorization_id")
    private UUID authorizationId;

    // ========================================================================
    // SETTLEMENT DETAILS
    // ========================================================================

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_status", nullable = false, length = 30)
    @NotNull(message = "Settlement status is required")
    @Builder.Default
    private SettlementStatus settlementStatus = SettlementStatus.PENDING;

    @Column(name = "settlement_date")
    private LocalDateTime settlementDate;

    @Column(name = "expected_settlement_date")
    private LocalDateTime expectedSettlementDate;

    @Column(name = "actual_settlement_date")
    private LocalDateTime actualSettlementDate;

    @Column(name = "initiated_date")
    @Builder.Default
    private LocalDateTime initiatedDate = LocalDateTime.now();

    // ========================================================================
    // FINANCIAL DETAILS
    // ========================================================================

    @Column(name = "settlement_amount", precision = 18, scale = 2, nullable = false)
    @NotNull
    @DecimalMin(value = "0.00")
    private BigDecimal settlementAmount;

    @Column(name = "currency_code", length = 3, nullable = false)
    @NotBlank
    @Size(min = 3, max = 3)
    private String currencyCode;

    @Column(name = "transaction_amount", precision = 18, scale = 2)
    private BigDecimal transactionAmount;

    @Column(name = "interchange_fee", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal interchangeFee = BigDecimal.ZERO;

    @Column(name = "assessment_fee", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal assessmentFee = BigDecimal.ZERO;

    @Column(name = "network_fee", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal networkFee = BigDecimal.ZERO;

    @Column(name = "processor_fee", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal processorFee = BigDecimal.ZERO;

    @Column(name = "total_fees", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal totalFees = BigDecimal.ZERO;

    @Column(name = "net_settlement_amount", precision = 18, scale = 2)
    private BigDecimal netSettlementAmount;

    // ========================================================================
    // MERCHANT DETAILS
    // ========================================================================

    @Column(name = "merchant_id", length = 100)
    @Size(max = 100)
    private String merchantId;

    @Column(name = "merchant_name", length = 255)
    @Size(max = 255)
    private String merchantName;

    @Column(name = "merchant_category_code", length = 4)
    @Size(min = 4, max = 4)
    private String merchantCategoryCode;

    // ========================================================================
    // NETWORK & PROCESSOR
    // ========================================================================

    @Column(name = "network_name", length = 50)
    @Size(max = 50)
    private String networkName;

    @Column(name = "network_reference_number", length = 100)
    @Size(max = 100)
    private String networkReferenceNumber;

    @Column(name = "processor_name", length = 50)
    @Size(max = 50)
    private String processorName;

    @Column(name = "processor_reference_number", length = 100)
    @Size(max = 100)
    private String processorReferenceNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "network_response", columnDefinition = "jsonb")
    private Map<String, Object> networkResponse;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "processor_response", columnDefinition = "jsonb")
    private Map<String, Object> processorResponse;

    // ========================================================================
    // RECONCILIATION
    // ========================================================================

    @Column(name = "reconciliation_status", length = 30)
    @Size(max = 30)
    private String reconciliationStatus;

    @Column(name = "reconciliation_date")
    private LocalDateTime reconciliationDate;

    @Column(name = "is_reconciled")
    @Builder.Default
    private Boolean isReconciled = false;

    @Column(name = "reconciliation_discrepancy_amount", precision = 18, scale = 2)
    private BigDecimal reconciliationDiscrepancyAmount;

    @Column(name = "reconciliation_notes", columnDefinition = "TEXT")
    private String reconciliationNotes;

    // ========================================================================
    // FAILURE & RETRY
    // ========================================================================

    @Column(name = "failure_reason", length = 255)
    @Size(max = 255)
    private String failureReason;

    @Column(name = "failure_code", length = 10)
    @Size(max = 10)
    private String failureCode;

    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "max_retry_count")
    @Builder.Default
    private Integer maxRetryCount = 3;

    @Column(name = "last_retry_date")
    private LocalDateTime lastRetryDate;

    @Column(name = "next_retry_date")
    private LocalDateTime nextRetryDate;

    // ========================================================================
    // REVERSAL
    // ========================================================================

    @Column(name = "is_reversed")
    @Builder.Default
    private Boolean isReversed = false;

    @Column(name = "reversal_date")
    private LocalDateTime reversalDate;

    @Column(name = "reversal_reason", length = 255)
    @Size(max = 255)
    private String reversalReason;

    @Column(name = "reversal_amount", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal reversalAmount = BigDecimal.ZERO;

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
     * Check if settlement is completed
     */
    @Transient
    public boolean isCompleted() {
        return settlementStatus == SettlementStatus.SETTLED ||
               settlementStatus == SettlementStatus.RECONCILED;
    }

    /**
     * Check if settlement is pending
     */
    @Transient
    public boolean isPending() {
        return settlementStatus == SettlementStatus.PENDING ||
               settlementStatus == SettlementStatus.PROCESSING;
    }

    /**
     * Check if settlement has failed
     */
    @Transient
    public boolean hasFailed() {
        return settlementStatus == SettlementStatus.FAILED;
    }

    /**
     * Check if settlement is on hold
     */
    @Transient
    public boolean isOnHold() {
        return settlementStatus == SettlementStatus.ON_HOLD;
    }

    /**
     * Check if can retry settlement
     */
    @Transient
    public boolean canRetry() {
        return hasFailed() &&
               retryCount < maxRetryCount &&
               !isReversed;
    }

    /**
     * Check if there is a reconciliation discrepancy
     */
    @Transient
    public boolean hasReconciliationDiscrepancy() {
        return reconciliationDiscrepancyAmount != null &&
               reconciliationDiscrepancyAmount.compareTo(BigDecimal.ZERO) != 0;
    }

    /**
     * Calculate total fees
     */
    public void calculateTotalFees() {
        BigDecimal total = BigDecimal.ZERO;
        if (interchangeFee != null) {
            total = total.add(interchangeFee);
        }
        if (assessmentFee != null) {
            total = total.add(assessmentFee);
        }
        if (networkFee != null) {
            total = total.add(networkFee);
        }
        if (processorFee != null) {
            total = total.add(processorFee);
        }
        this.totalFees = total;
    }

    /**
     * Calculate net settlement amount
     */
    public void calculateNetSettlementAmount() {
        if (settlementAmount == null) {
            this.netSettlementAmount = BigDecimal.ZERO;
            return;
        }
        calculateTotalFees();
        this.netSettlementAmount = settlementAmount.subtract(totalFees);
    }

    /**
     * Mark settlement as processing
     */
    public void markAsProcessing() {
        this.settlementStatus = SettlementStatus.PROCESSING;
    }

    /**
     * Mark settlement as settled
     */
    public void markAsSettled() {
        this.settlementStatus = SettlementStatus.SETTLED;
        this.settlementDate = LocalDateTime.now();
        this.actualSettlementDate = LocalDateTime.now();
    }

    /**
     * Mark settlement as failed
     */
    public void markAsFailed(String reason, String code) {
        this.settlementStatus = SettlementStatus.FAILED;
        this.failureReason = reason;
        this.failureCode = code;
    }

    /**
     * Increment retry count and schedule next retry
     */
    public void incrementRetryCount() {
        this.retryCount++;
        this.lastRetryDate = LocalDateTime.now();

        // Exponential backoff: 1 hour, 4 hours, 16 hours
        long hoursToWait = (long) Math.pow(4, retryCount - 1);
        this.nextRetryDate = LocalDateTime.now().plusHours(hoursToWait);
    }

    /**
     * Mark as reconciled
     */
    public void markAsReconciled(BigDecimal discrepancy) {
        this.settlementStatus = SettlementStatus.RECONCILED;
        this.reconciliationStatus = "RECONCILED";
        this.isReconciled = true;
        this.reconciliationDate = LocalDateTime.now();
        this.reconciliationDiscrepancyAmount = discrepancy;
    }

    /**
     * Mark reconciliation discrepancy
     */
    public void markDiscrepancy(BigDecimal discrepancy, String notes) {
        this.settlementStatus = SettlementStatus.DISCREPANCY;
        this.reconciliationStatus = "DISCREPANCY";
        this.reconciliationDiscrepancyAmount = discrepancy;
        this.reconciliationNotes = notes;
    }

    /**
     * Reverse settlement
     */
    public void reverse(String reason, BigDecimal amount) {
        this.isReversed = true;
        this.reversalDate = LocalDateTime.now();
        this.reversalReason = reason;
        this.reversalAmount = amount;
        this.settlementStatus = SettlementStatus.REVERSED;
    }

    /**
     * Put settlement on hold
     */
    public void putOnHold(String reason) {
        this.settlementStatus = SettlementStatus.ON_HOLD;
        this.notes = (this.notes != null ? this.notes + "\n" : "") + "ON HOLD: " + reason;
    }

    @PrePersist
    @Override
    protected void onCreate() {
        super.onCreate();
        if (settlementStatus == null) {
            settlementStatus = SettlementStatus.PENDING;
        }
        if (initiatedDate == null) {
            initiatedDate = LocalDateTime.now();
        }
        if (retryCount == null) {
            retryCount = 0;
        }
        if (maxRetryCount == null) {
            maxRetryCount = 3;
        }
        if (isReconciled == null) {
            isReconciled = false;
        }
        if (isReversed == null) {
            isReversed = false;
        }

        // Calculate derived amounts
        calculateNetSettlementAmount();

        // Set expected settlement date (T+2 business days is typical)
        if (expectedSettlementDate == null) {
            expectedSettlementDate = LocalDateTime.now().plusDays(2);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        super.onUpdate();
        // Recalculate net amount if fees changed
        calculateNetSettlementAmount();
    }
}
