package com.waqiti.account.entity;

import com.waqiti.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Transaction entity representing financial transactions
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_transaction_account", columnList = "account_id"),
    @Index(name = "idx_transaction_reference", columnList = "transaction_reference", unique = true),
    @Index(name = "idx_transaction_date", columnList = "transaction_date"),
    @Index(name = "idx_transaction_type", columnList = "transaction_type"),
    @Index(name = "idx_transaction_status", columnList = "status")
})
@Getter
@Setter
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Audited
public class Transaction extends BaseEntity {
    
    @Column(name = "transaction_reference", unique = true, nullable = false, length = 50)
    @ToString.Include
    private String transactionReference;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;
    
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
    
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType transactionType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatus status;
    
    @Column(name = "description", length = 500)
    private String description;
    
    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;
    
    @Column(name = "value_date")
    private LocalDateTime valueDate;
    
    @Column(name = "posting_date")
    private LocalDateTime postingDate;
    
    @Column(name = "balance_before", precision = 19, scale = 4)
    private BigDecimal balanceBefore;
    
    @Column(name = "balance_after", precision = 19, scale = 4)
    private BigDecimal balanceAfter;
    
    @Column(name = "fee_amount", precision = 19, scale = 4)
    private BigDecimal feeAmount;
    
    @Column(name = "counterparty_account")
    private String counterpartyAccount;
    
    @Column(name = "counterparty_name", length = 200)
    private String counterpartyName;
    
    @Column(name = "channel", length = 50)
    private String channel;
    
    @Column(name = "device_id", length = 100)
    private String deviceId;
    
    @Column(name = "location", length = 200)
    private String location;
    
    @Column(name = "reversal_reference")
    private String reversalReference;
    
    @Column(name = "is_reversal", nullable = false)
    private Boolean isReversal;
    
    @Column(name = "external_reference")
    private String externalReference;
    
    @Column(name = "processing_time_ms")
    private Long processingTimeMs;
    
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;
    
    @PrePersist
    @Override
    protected void onPrePersist() {
        super.onPrePersist();
        
        if (transactionReference == null) {
            transactionReference = generateTransactionReference();
        }
        
        if (transactionDate == null) {
            transactionDate = LocalDateTime.now();
        }
        
        if (status == null) {
            status = TransactionStatus.PENDING;
        }
        
        if (isReversal == null) {
            isReversal = false;
        }
        
        setBusinessKey(transactionReference);
    }
    
    private String generateTransactionReference() {
        LocalDateTime now = LocalDateTime.now();
        return String.format("TXN-%04d%02d%02d-%s", 
            now.getYear(), now.getMonthValue(), now.getDayOfMonth(),
            UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }
    
    public enum TransactionType {
        DEPOSIT,
        WITHDRAWAL,
        TRANSFER,
        PAYMENT,
        FEE,
        INTEREST,
        REVERSAL,
        ADJUSTMENT
    }
    
    public enum TransactionStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        CANCELLED,
        REVERSED
    }
}