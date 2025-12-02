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
@Table(name = "atm_deposits")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ATMDeposit {

    @Id
    private UUID id;

    @Column(name = "atm_id", nullable = false)
    private UUID atmId;

    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "deposit_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private DepositType depositType;

    @Column(name = "cash_amount", precision = 19, scale = 2)
    private BigDecimal cashAmount = BigDecimal.ZERO;

    @Column(name = "check_amount", precision = 19, scale = 2)
    private BigDecimal checkAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "number_of_checks")
    private Integer numberOfChecks = 0;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private DepositStatus status;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "hold_reason")
    private String holdReason;

    @Column(name = "receipt_number")
    private String receiptNumber;

    @Column(name = "deposit_date", nullable = false)
    private LocalDateTime depositDate;

    @Column(name = "cash_processed_at")
    private LocalDateTime cashProcessedAt;

    @Column(name = "check_processed_at")
    private LocalDateTime checkProcessedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    public enum DepositType {
        CASH, CHECK, MIXED
    }

    public enum DepositStatus {
        PROCESSING, COMPLETED, REJECTED, ON_HOLD, PARTIALLY_PROCESSED
    }
}
