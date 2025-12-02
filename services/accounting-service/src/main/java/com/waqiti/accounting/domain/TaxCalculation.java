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
 * Tax Calculation entity
 * Tracks tax calculations for transactions
 */
@Entity
@Table(name = "tax_calculation", indexes = {
    @Index(name = "idx_tax_transaction", columnList = "transaction_id"),
    @Index(name = "idx_tax_type", columnList = "tax_type")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxCalculation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @NotNull
    @Column(name = "tax_type", nullable = false, length = 50)
    private String taxType;

    @NotNull
    @Column(name = "tax_rate", precision = 5, scale = 4, nullable = false)
    @DecimalMin(value = "0.0000")
    private BigDecimal taxRate;

    @NotNull
    @Column(name = "tax_amount", precision = 18, scale = 2, nullable = false)
    @DecimalMin(value = "0.00")
    private BigDecimal taxAmount;

    @NotNull
    @Column(name = "taxable_amount", precision = 18, scale = 2, nullable = false)
    @DecimalMin(value = "0.00")
    private BigDecimal taxableAmount;

    @NotNull
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "jurisdiction", length = 100)
    private String jurisdiction;

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
