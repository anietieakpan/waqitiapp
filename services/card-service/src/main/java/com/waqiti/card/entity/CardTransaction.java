package com.waqiti.card.entity;

import com.waqiti.card.enums.TransactionStatus;
import com.waqiti.card.enums.TransactionType;
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
 * CardTransaction entity - Transaction master record
 * Represents a card transaction (purchase, withdrawal, refund, etc.)
 *
 * Consolidated from card_transaction and card_processing_transaction tables
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Entity
@Table(name = "card_transaction", indexes = {
    @Index(name = "idx_transaction_id", columnList = "transaction_id"),
    @Index(name = "idx_transaction_card", columnList = "card_id"),
    @Index(name = "idx_transaction_user", columnList = "user_id"),
    @Index(name = "idx_transaction_type", columnList = "transaction_type"),
    @Index(name = "idx_transaction_status", columnList = "transaction_status"),
    @Index(name = "idx_transaction_date", columnList = "transaction_date"),
    @Index(name = "idx_transaction_merchant", columnList = "merchant_id"),
    @Index(name = "idx_transaction_auth_code", columnList = "authorization_code"),
    @Index(name = "idx_transaction_settlement", columnList = "settlement_date"),
    @Index(name = "idx_transaction_deleted", columnList = "deleted_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CardTransaction extends BaseAuditEntity {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // ========================================================================
    // TRANSACTION IDENTIFICATION
    // ========================================================================

    @Column(name = "transaction_id", unique = true, nullable = false, length = 100)
    @NotBlank(message = "Transaction ID is required")
    private String transactionId;

    @Column(name = "external_transaction_id", length = 100)
    @Size(max = 100)
    private String externalTransactionId;

    @Column(name = "reference_number", length = 100)
    @Size(max = 100)
    private String referenceNumber;

    @Column(name = "retrieval_reference_number", length = 12)
    @Size(max = 12)
    private String retrievalReferenceNumber;

    @Column(name = "idempotency_key", unique = true, length = 64)
    @Size(max = 64)
    private String idempotencyKey;

    // ========================================================================
    // CARD & USER REFERENCES
    // ========================================================================

    @Column(name = "card_id", nullable = false)
    @NotNull(message = "Card ID is required")
    private UUID cardId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", insertable = false, updatable = false)
    private Card card;

    @Column(name = "user_id", nullable = false)
    @NotNull(message = "User ID is required")
    private UUID userId;

    @Column(name = "account_id")
    private UUID accountId;

    // ========================================================================
    // TRANSACTION DETAILS
    // ========================================================================

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    @NotNull(message = "Transaction type is required")
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_status", nullable = false, length = 30)
    @NotNull(message = "Transaction status is required")
    @Builder.Default
    private TransactionStatus transactionStatus = TransactionStatus.PENDING;

    @Column(name = "transaction_date", nullable = false)
    @NotNull
    @Builder.Default
    private LocalDateTime transactionDate = LocalDateTime.now();

    @Column(name = "local_transaction_date")
    private LocalDateTime localTransactionDate;

    // ========================================================================
    // FINANCIAL DETAILS
    // ========================================================================

    @Column(name = "amount", precision = 18, scale = 2, nullable = false)
    @NotNull
    @DecimalMin(value = "0.00")
    private BigDecimal amount;

    @Column(name = "currency_code", length = 3, nullable = false)
    @NotBlank
    @Size(min = 3, max = 3)
    private String currencyCode;

    @Column(name = "billing_amount", precision = 18, scale = 2)
    private BigDecimal billingAmount;

    @Column(name = "billing_currency_code", length = 3)
    @Size(min = 3, max = 3)
    private String billingCurrencyCode;

    @Column(name = "exchange_rate", precision = 18, scale = 8)
    private BigDecimal exchangeRate;

    @Column(name = "fee_amount", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal feeAmount = BigDecimal.ZERO;

    @Column(name = "cashback_amount", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal cashbackAmount = BigDecimal.ZERO;

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

    @Column(name = "merchant_category_description", length = 255)
    @Size(max = 255)
    private String merchantCategoryDescription;

    @Column(name = "merchant_city", length = 100)
    @Size(max = 100)
    private String merchantCity;

    @Column(name = "merchant_state", length = 100)
    @Size(max = 100)
    private String merchantState;

    @Column(name = "merchant_country", length = 3)
    @Size(min = 2, max = 3)
    private String merchantCountry;

    @Column(name = "merchant_postal_code", length = 20)
    @Size(max = 20)
    private String merchantPostalCode;

    // ========================================================================
    // AUTHORIZATION & PROCESSING
    // ========================================================================

    @Column(name = "authorization_code", length = 6)
    @Size(max = 6)
    private String authorizationCode;

    @Column(name = "authorization_id")
    private UUID authorizationId;

    @Column(name = "processor_response_code", length = 10)
    @Size(max = 10)
    private String processorResponseCode;

    @Column(name = "processor_response_message", length = 255)
    @Size(max = 255)
    private String processorResponseMessage;

    @Column(name = "network_response_code", length = 10)
    @Size(max = 10)
    private String networkResponseCode;

    @Column(name = "network_transaction_id", length = 100)
    @Size(max = 100)
    private String networkTransactionId;

    // ========================================================================
    // FRAUD & RISK ASSESSMENT
    // ========================================================================

    @Column(name = "fraud_score", precision = 5, scale = 2)
    @DecimalMin(value = "0.00")
    @DecimalMax(value = "100.00")
    private BigDecimal fraudScore;

    @Column(name = "risk_level", length = 20)
    @Size(max = 20)
    private String riskLevel;

    @Column(name = "risk_assessment", columnDefinition = "TEXT")
    private String riskAssessment;

    @Column(name = "fraud_check_passed")
    private Boolean fraudCheckPassed;

    @Column(name = "velocity_check_passed")
    private Boolean velocityCheckPassed;

    @Column(name = "three_ds_authenticated")
    private Boolean threeDsAuthenticated;

    @Column(name = "three_ds_eci", length = 2)
    @Size(max = 2)
    private String threeDsEci;

    // ========================================================================
    // POINT OF SERVICE DATA (JSONB)
    // ========================================================================

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "point_of_service_data", columnDefinition = "jsonb")
    private Map<String, Object> pointOfServiceData;

    @Column(name = "pos_entry_mode", length = 3)
    @Size(max = 3)
    private String posEntryMode;

    @Column(name = "pos_condition_code", length = 2)
    @Size(max = 2)
    private String posConditionCode;

    @Column(name = "terminal_id", length = 50)
    @Size(max = 50)
    private String terminalId;

    @Column(name = "terminal_type", length = 20)
    @Size(max = 20)
    private String terminalType;

    @Column(name = "is_contactless")
    private Boolean isContactless;

    @Column(name = "is_online")
    private Boolean isOnline;

    @Column(name = "is_international")
    private Boolean isInternational;

    @Column(name = "is_recurring")
    private Boolean isRecurring;

    // ========================================================================
    // SETTLEMENT DETAILS
    // ========================================================================

    @Column(name = "settlement_date")
    private LocalDateTime settlementDate;

    @Column(name = "settlement_id")
    private UUID settlementId;

    @Column(name = "batch_id", length = 100)
    @Size(max = 100)
    private String batchId;

    // ========================================================================
    // BALANCE SNAPSHOT
    // ========================================================================

    @Column(name = "available_balance_before", precision = 18, scale = 2)
    private BigDecimal availableBalanceBefore;

    @Column(name = "available_balance_after", precision = 18, scale = 2)
    private BigDecimal availableBalanceAfter;

    @Column(name = "ledger_balance_before", precision = 18, scale = 2)
    private BigDecimal ledgerBalanceBefore;

    @Column(name = "ledger_balance_after", precision = 18, scale = 2)
    private BigDecimal ledgerBalanceAfter;

    // ========================================================================
    // METADATA & ADDITIONAL INFO
    // ========================================================================

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // ========================================================================
    // REVERSAL & DISPUTE
    // ========================================================================

    @Column(name = "is_reversed")
    @Builder.Default
    private Boolean isReversed = false;

    @Column(name = "reversal_date")
    private LocalDateTime reversalDate;

    @Column(name = "reversal_reason", length = 255)
    @Size(max = 255)
    private String reversalReason;

    @Column(name = "original_transaction_id")
    private UUID originalTransactionId;

    @Column(name = "is_disputed")
    @Builder.Default
    private Boolean isDisputed = false;

    @Column(name = "dispute_id")
    private UUID disputeId;

    // ========================================================================
    // RELATIONSHIPS
    // ========================================================================

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<CardAuthorization> authorizations = new ArrayList<>();

    @OneToOne(mappedBy = "transaction", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private CardSettlement settlement;

    @OneToOne(mappedBy = "transaction", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private CardDispute dispute;

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Check if transaction is completed successfully
     */
    @Transient
    public boolean isCompleted() {
        return transactionStatus == TransactionStatus.COMPLETED ||
               transactionStatus == TransactionStatus.SETTLED;
    }

    /**
     * Check if transaction is pending
     */
    @Transient
    public boolean isPending() {
        return transactionStatus == TransactionStatus.PENDING ||
               transactionStatus == TransactionStatus.AUTHORIZED ||
               transactionStatus == TransactionStatus.SETTLING;
    }

    /**
     * Check if transaction is failed
     */
    @Transient
    public boolean isFailed() {
        return transactionStatus == TransactionStatus.DECLINED ||
               transactionStatus == TransactionStatus.FAILED ||
               transactionStatus == TransactionStatus.TIMEOUT ||
               transactionStatus == TransactionStatus.FRAUD_BLOCKED;
    }

    /**
     * Check if transaction is international
     */
    @Transient
    public boolean isInternationalTransaction() {
        return isInternational != null && isInternational;
    }

    /**
     * Check if currency conversion was applied
     */
    @Transient
    public boolean hasCurrencyConversion() {
        return billingCurrencyCode != null &&
               !currencyCode.equals(billingCurrencyCode);
    }

    /**
     * Get total amount including fees
     */
    @Transient
    public BigDecimal getTotalAmount() {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = amount;
        if (feeAmount != null) {
            total = total.add(feeAmount);
        }
        return total;
    }

    /**
     * Mark transaction as reversed
     */
    public void reverse(String reason) {
        this.isReversed = true;
        this.reversalDate = LocalDateTime.now();
        this.reversalReason = reason;
        this.transactionStatus = TransactionStatus.REVERSED;
    }

    /**
     * Mark transaction as disputed
     */
    public void markAsDisputed(UUID disputeId) {
        this.isDisputed = true;
        this.disputeId = disputeId;
        this.transactionStatus = TransactionStatus.DISPUTED;
    }

    /**
     * Check if transaction is high risk
     */
    @Transient
    public boolean isHighRisk() {
        if (fraudScore == null) {
            return false;
        }
        return fraudScore.compareTo(new BigDecimal("75.00")) > 0;
    }

    @PrePersist
    @Override
    protected void onCreate() {
        super.onCreate();
        if (transactionStatus == null) {
            transactionStatus = TransactionStatus.PENDING;
        }
        if (transactionDate == null) {
            transactionDate = LocalDateTime.now();
        }
        if (isReversed == null) {
            isReversed = false;
        }
        if (isDisputed == null) {
            isDisputed = false;
        }
    }
}
