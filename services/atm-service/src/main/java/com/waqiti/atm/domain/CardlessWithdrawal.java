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
@Table(name = "cardless_withdrawals")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardlessWithdrawal {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "account_id", nullable = false)
    private UUID accountId;
    
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    
    @Column(name = "currency", length = 3)
    private String currency = "USD";
    
    @Column(name = "withdrawal_code", unique = true, nullable = false)
    private String withdrawalCode;
    
    @Column(name = "security_code", nullable = false)
    private String securityCode;
    
    @Column(name = "recipient_mobile")
    private String recipientMobile;
    
    @Column(name = "recipient_name")
    private String recipientName;
    
    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private WithdrawalStatus status;
    
    @Column(name = "atm_id")
    private UUID atmId;
    
    @Column(name = "transaction_id")
    private String transactionId;
    
    @Column(name = "attempt_count")
    private Integer attemptCount = 0;
    
    @Column(name = "dispensed_amount", precision = 19, scale = 2)
    private BigDecimal dispensedAmount;
    
    @Column(name = "failure_reason")
    private String failureReason;
    
    @CreationTimestamp
    @Column(name = "initiated_at", nullable = false, updatable = false)
    private LocalDateTime initiatedAt;
    
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    
    @Column(name = "qr_generated_at")
    private LocalDateTime qrGeneratedAt;
    
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;
    
    public enum WithdrawalStatus {
        INITIATED, VERIFIED, COMPLETED, CANCELLED, EXPIRED, FAILED
    }
}