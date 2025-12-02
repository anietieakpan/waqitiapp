package com.waqiti.accounting.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Settlement Entry entity
 * Tracks merchant settlements and payouts
 */
@Entity
@Table(name = "settlement_entry", indexes = {
    @Index(name = "idx_settlement_merchant", columnList = "merchant_id"),
    @Index(name = "idx_settlement_date", columnList = "settlement_date"),
    @Index(name = "idx_settlement_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementEntry {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @NotNull
    @Column(name = "transaction_id", nullable = false, length = 100)
    private String transactionId;

    @NotNull
    @Column(name = "merchant_id", nullable = false)
    private String merchantId;

    @NotNull
    @Column(name = "gross_amount", precision = 18, scale = 2, nullable = false)
    @DecimalMin(value = "0.00")
    private BigDecimal grossAmount;

    @NotNull
    @Column(name = "processing_fee", precision = 18, scale = 2, nullable = false)
    @DecimalMin(value = "0.00")
    private BigDecimal processingFee;

    @NotNull
    @Column(name = "total_fees", precision = 18, scale = 2, nullable = false)
    @DecimalMin(value = "0.00")
    private BigDecimal totalFees;

    @NotNull
    @Column(name = "taxes", precision = 18, scale = 2, nullable = false)
    @DecimalMin(value = "0.00")
    private BigDecimal taxes;

    @NotNull
    @Column(name = "net_amount", precision = 18, scale = 2, nullable = false)
    @DecimalMin(value = "0.00")
    private BigDecimal netAmount;

    @NotNull
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SettlementStatus status;

    @NotNull
    @Column(name = "settlement_date", nullable = false)
    private LocalDate settlementDate;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @Column(name = "payout_reference", length = 100)
    private String payoutReference;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }
}
