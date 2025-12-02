package com.waqiti.atm.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "atm_withdrawals")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ATMWithdrawal {

    @Id
    private UUID id;

    @Column(name = "atm_id", nullable = false)
    private UUID atmId;

    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    private String currency = "USD";

    @Column(name = "authorization_code")
    private String authorizationCode;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private WithdrawalStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "withdrawal_date", nullable = false)
    private LocalDateTime withdrawalDate;

    @Column(name = "dispensed_at")
    private LocalDateTime dispensedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    public enum WithdrawalStatus {
        PROCESSING, COMPLETED, FAILED, REVERSED
    }
}
