package com.waqiti.atm.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "atm_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ATMTransaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "account_id", nullable = false)
    private UUID accountId;
    
    @Column(name = "card_id")
    private UUID cardId;
    
    @Column(name = "atm_id", nullable = false)
    private UUID atmId;
    
    @Column(name = "atm_location")
    private String atmLocation;
    
    @Column(name = "transaction_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;
    
    @Column(name = "amount", precision = 19, scale = 2)
    private BigDecimal amount;
    
    @Column(name = "currency", length = 3)
    private String currency = "USD";
    
    @Column(name = "balance_before", precision = 19, scale = 2)
    private BigDecimal balanceBefore;
    
    @Column(name = "balance_after", precision = 19, scale = 2)
    private BigDecimal balanceAfter;
    
    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private TransactionStatus status;
    
    @Column(name = "reference_number", unique = true)
    private String referenceNumber;
    
    @Column(name = "auth_code")
    private String authCode;
    
    @Column(name = "response_code")
    private String responseCode;
    
    @Column(name = "response_message")
    private String responseMessage;
    
    @Column(name = "fee_amount", precision = 19, scale = 2)
    private BigDecimal feeAmount;
    
    @Column(name = "is_cardless")
    private Boolean isCardless = false;
    
    @Column(name = "cardless_withdrawal_id")
    private UUID cardlessWithdrawalId;
    
    @CreationTimestamp
    @Column(name = "transaction_date", nullable = false, updatable = false)
    private LocalDateTime transactionDate;
    
    @Column(name = "posted_date")
    private LocalDateTime postedDate;
    
    @Version
    private Long version;
    
    public enum TransactionType {
        WITHDRAWAL, BALANCE_INQUIRY, MINI_STATEMENT, PIN_CHANGE, 
        TRANSFER, DEPOSIT, BILL_PAYMENT
    }
    
    public enum TransactionStatus {
        SUCCESS, FAILED, PENDING, REVERSED, CANCELLED
    }
}