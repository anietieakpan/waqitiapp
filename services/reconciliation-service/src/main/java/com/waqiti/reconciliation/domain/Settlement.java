package com.waqiti.reconciliation.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.Type;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "settlements", indexes = {
    @Index(name = "idx_settlements_status", columnList = "status"),
    @Index(name = "idx_settlements_settlement_date", columnList = "settlement_date"),
    @Index(name = "idx_settlements_external_ref", columnList = "external_reference"),
    @Index(name = "idx_settlements_account_id", columnList = "account_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Settlement {

    @Id
    @Column(name = "settlement_id")
    @Type(type = "uuid-char")
    private UUID settlementId;

    @Column(name = "external_reference", length = 100)
    @Size(max = 100)
    private String externalReference;

    @Column(name = "account_id", nullable = false)
    @Type(type = "uuid-char")
    @NotNull
    private UUID accountId;

    @Column(name = "counterparty_id")
    @Type(type = "uuid-char")
    private UUID counterpartyId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    @DecimalMin(value = "0.0001", message = "Settlement amount must be positive")
    @NotNull
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    @Size(min = 3, max = 3, message = "Currency code must be 3 characters")
    @NotNull
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_type", nullable = false, length = 30)
    @NotNull
    private SettlementType settlementType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @NotNull
    private SettlementStatus status;

    @Column(name = "settlement_date", nullable = false)
    @NotNull
    private LocalDateTime settlementDate;

    @Column(name = "value_date")
    private LocalDateTime valueDate;

    @Column(name = "reconciled_at")
    private LocalDateTime reconciledAt;

    @Column(name = "external_confirmation_reference", length = 100)
    @Size(max = 100)
    private String externalConfirmationReference;

    @Column(name = "settlement_instruction", columnDefinition = "TEXT")
    private String settlementInstruction;

    @Column(name = "reconciliation_notes", columnDefinition = "TEXT")
    private String reconciliationNotes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    public enum SettlementType {
        DEBIT("Debit Settlement"),
        CREDIT("Credit Settlement"),
        NET("Net Settlement"),
        GROSS("Gross Settlement"),
        DVP("Delivery versus Payment"),
        PVP("Payment versus Payment");

        private final String description;

        SettlementType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum SettlementStatus {
        PENDING("Pending Settlement"),
        PROCESSING("Processing"),
        SETTLED("Settled"),
        FAILED("Settlement Failed"),
        CANCELLED("Cancelled"),
        RECONCILIATION_PENDING("Reconciliation Pending"),
        RECONCILED("Reconciled"),
        BREAK_DETECTED("Break Detected");

        private final String description;

        SettlementStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public void markReconciled() {
        this.status = SettlementStatus.RECONCILED;
        this.reconciledAt = LocalDateTime.now();
    }

    public void markReconciliationPending() {
        this.status = SettlementStatus.RECONCILIATION_PENDING;
    }

    public void markBreakDetected(String notes) {
        this.status = SettlementStatus.BREAK_DETECTED;
        this.reconciliationNotes = notes;
    }

    public boolean isReconciled() {
        return SettlementStatus.RECONCILED.equals(status);
    }

    public boolean requiresReconciliation() {
        return SettlementStatus.RECONCILIATION_PENDING.equals(status) || 
               SettlementStatus.BREAK_DETECTED.equals(status);
    }
}