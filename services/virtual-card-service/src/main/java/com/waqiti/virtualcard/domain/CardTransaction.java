package com.waqiti.virtualcard.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Card Transaction Entity
 * Records all transactions made with virtual cards
 */
@Entity
@Table(name = "card_transactions", indexes = {
    @Index(name = "idx_card_id", columnList = "card_id"),
    @Index(name = "idx_transaction_id", columnList = "transaction_id", unique = true),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_transaction_date", columnList = "transaction_date"),
    @Index(name = "idx_merchant_name", columnList = "merchant_name"),
    @Index(name = "idx_authorization_code", columnList = "authorization_code")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CardTransaction {
    
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @EqualsAndHashCode.Include
    private String id;
    
    @Column(name = "card_id", nullable = false)
    private String cardId;
    
    @Column(name = "transaction_id", nullable = false, unique = true, length = 100)
    private String transactionId;
    
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    
    @Column(name = "original_amount", precision = 19, scale = 2)
    private BigDecimal originalAmount;
    
    @Column(name = "original_currency", length = 3)
    private String originalCurrency;
    
    @Column(name = "exchange_rate", precision = 19, scale = 8)
    private BigDecimal exchangeRate;
    
    @Column(name = "merchant_name", nullable = false, length = 200)
    private String merchantName;
    
    @Column(name = "merchant_id", length = 50)
    private String merchantId;
    
    @Column(name = "merchant_category", length = 100)
    private String merchantCategory;
    
    @Column(name = "merchant_category_code", length = 4)
    private String merchantCategoryCode;
    
    @Column(name = "merchant_country", length = 3)
    private String merchantCountry;
    
    @Column(name = "merchant_city", length = 100)
    private String merchantCity;
    
    @Column(name = "terminal_id", length = 50)
    private String terminalId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private TransactionType transactionType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatus status;
    
    @Column(name = "authorization_code", length = 10)
    private String authorizationCode;
    
    @Column(name = "network_response_code", length = 10)
    private String networkResponseCode;
    
    @Column(name = "decline_reason", length = 200)
    private String declineReason;
    
    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;
    
    @Column(name = "settlement_date")
    private LocalDateTime settlementDate;
    
    @Column(name = "posting_date")
    private LocalDateTime postingDate;
    
    @Column(name = "reversal_date")
    private LocalDateTime reversalDate;
    
    @Column(name = "is_international", nullable = false)
    private boolean isInternational;
    
    @Column(name = "is_online", nullable = false)
    private boolean isOnline;
    
    @Column(name = "is_contactless", nullable = false)
    private boolean isContactless;
    
    @Column(name = "is_recurring", nullable = false)
    private boolean isRecurring;
    
    @Column(name = "entry_mode", length = 30)
    private String entryMode;
    
    @Column(name = "processing_method", length = 30)
    private String processingMethod;
    
    @Column(name = "card_present", nullable = false)
    private boolean cardPresent;
    
    @Column(name = "cardholder_present", nullable = false)
    private boolean cardholderPresent;
    
    @Column(name = "three_d_secure", nullable = false)
    private boolean threeDSecure;
    
    @Column(name = "three_d_secure_version", length = 10)
    private String threeDSecureVersion;
    
    @Column(name = "risk_score")
    private Integer riskScore;
    
    @Column(name = "fraud_score")
    private Integer fraudScore;
    
    @Column(name = "velocity_score")
    private Integer velocityScore;
    
    @Column(name = "fee_amount", precision = 19, scale = 2)
    private BigDecimal feeAmount;
    
    @Column(name = "fee_type", length = 50)
    private String feeType;
    
    @Column(name = "interchange_fee", precision = 19, scale = 2)
    private BigDecimal interchangeFee;
    
    @Column(name = "network_fee", precision = 19, scale = 2)
    private BigDecimal networkFee;
    
    @Column(name = "issuer_fee", precision = 19, scale = 2)
    private BigDecimal issuerFee;
    
    @Column(name = "acquirer_reference", length = 50)
    private String acquirerReference;
    
    @Column(name = "issuer_reference", length = 50)
    private String issuerReference;
    
    @Column(name = "trace_number", length = 20)
    private String traceNumber;
    
    @Column(name = "batch_number", length = 20)
    private String batchNumber;
    
    @Column(name = "retrieval_reference", length = 20)
    private String retrievalReference;
    
    @Column(name = "pos_data", length = 20)
    private String posData;
    
    @Column(name = "card_acceptor_id", length = 50)
    private String cardAcceptorId;
    
    @Column(name = "card_acceptor_terminal", length = 20)
    private String cardAcceptorTerminal;
    
    @Column(name = "transaction_description", length = 500)
    private String transactionDescription;
    
    @Column(name = "mfa_verified", nullable = false)
    private boolean mfaVerified;
    
    @Column(name = "notification_sent", nullable = false)
    private boolean notificationSent;
    
    @ElementCollection
    @CollectionTable(
        name = "transaction_metadata",
        joinColumns = @JoinColumn(name = "transaction_id")
    )
    @MapKeyColumn(name = "key")
    @Column(name = "value")
    private Map<String, String> metadata;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (transactionDate == null) {
            transactionDate = LocalDateTime.now();
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    /**
     * Check if transaction is approved
     */
    public boolean isApproved() {
        return status == TransactionStatus.APPROVED || status == TransactionStatus.COMPLETED;
    }
    
    /**
     * Check if transaction is declined
     */
    public boolean isDeclined() {
        return status == TransactionStatus.DECLINED || status == TransactionStatus.REJECTED;
    }
    
    /**
     * Check if transaction is pending
     */
    public boolean isPending() {
        return status == TransactionStatus.PENDING || status == TransactionStatus.PROCESSING;
    }
    
    /**
     * Check if transaction is settled
     */
    public boolean isSettled() {
        return status == TransactionStatus.COMPLETED && settlementDate != null;
    }
    
    /**
     * Get effective amount (considering currency conversion)
     */
    public BigDecimal getEffectiveAmount() {
        return originalAmount != null ? originalAmount : amount;
    }
    
    /**
     * Get effective currency
     */
    public String getEffectiveCurrency() {
        return originalCurrency != null ? originalCurrency : currency;
    }
}