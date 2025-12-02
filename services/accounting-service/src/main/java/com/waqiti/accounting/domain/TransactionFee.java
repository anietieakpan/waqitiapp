package com.waqiti.accounting.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Transaction Fee entity
 * Tracks fees charged on transactions
 */
@Entity
@Table(name = "transaction_fee", indexes = {
    @Index(name = "idx_fee_transaction", columnList = "transaction_id"),
    @Index(name = "idx_fee_type", columnList = "fee_type")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionFee {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @NotNull
    @Column(name = "fee_type", nullable = false, length = 50)
    private String feeType;

    @NotNull
    @Column(name = "amount", precision = 18, scale = 2, nullable = false)
    @DecimalMin(value = "0.00")
    private BigDecimal amount;

    @NotNull
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "fee_percentage", precision = 5, scale = 4)
    private BigDecimal feePercentage;

    @Column(name = "fixed_amount", precision = 18, scale = 2)
    private BigDecimal fixedAmount;

    @Column(name = "description", length = 500)
    private String description;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
