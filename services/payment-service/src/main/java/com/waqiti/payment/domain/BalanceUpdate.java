package com.waqiti.payment.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "balance_updates", indexes = {
    @Index(name = "idx_account_id", columnList = "account_id"),
    @Index(name = "idx_transaction_id", columnList = "transaction_id"),
    @Index(name = "idx_update_type", columnList = "update_type"),
    @Index(name = "idx_processed_at", columnList = "processed_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceUpdate {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "original_transaction_id")
    private String originalTransactionId;

    @Column(name = "update_type", nullable = false)
    private String updateType;

    @Column(name = "amount", precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "previous_balance", precision = 19, scale = 4)
    private BigDecimal previousBalance;

    @Column(name = "new_balance", precision = 19, scale = 4)
    private BigDecimal newBalance;

    @Column(name = "available_balance", precision = 19, scale = 4)
    private BigDecimal availableBalance;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "adjustment_reason", length = 255)
    private String adjustmentReason;

    @Column(name = "hold_reason", length = 255)
    private String holdReason;

    @Column(name = "reversal_reason", length = 255)
    private String reversalReason;

    @Column(name = "fee_type", length = 100)
    private String feeType;

    @Column(name = "interest_rate", precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "correlation_id", length = 255)
    private String correlationId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
