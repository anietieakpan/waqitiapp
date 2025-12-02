package com.waqiti.customer.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Account Closure Entity
 *
 * Records account closure details for regulatory compliance and audit trail
 * Retained for 7 years per banking regulations
 */
@Entity
@Table(name = "account_closures", indexes = {
    @Index(name = "idx_account_closures_account_id", columnList = "account_id"),
    @Index(name = "idx_account_closures_customer_id", columnList = "customer_id"),
    @Index(name = "idx_account_closures_closure_date", columnList = "closure_date"),
    @Index(name = "idx_account_closures_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountClosure {

    @Id
    @Column(name = "closure_id", length = 50)
    private String closureId;

    @Column(name = "account_id", nullable = false, length = 50)
    private String accountId;

    @Column(name = "customer_id", nullable = false, length = 50)
    private String customerId;

    @Column(name = "closure_reason", length = 100)
    private String closureReason;

    @Column(name = "closure_type", length = 50)
    private String closureType; // VOLUNTARY, INVOLUNTARY, REGULATORY

    @Column(name = "closure_date", nullable = false)
    private LocalDateTime closureDate;

    @Column(name = "final_disbursement", precision = 19, scale = 4)
    private BigDecimal finalDisbursement;

    @Column(name = "disbursement_method", length = 50)
    private String disbursementMethod;

    @Column(name = "status", length = 20)
    private String status; // PENDING, IN_PROGRESS, COMPLETED, FAILED

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    public String getClosureId() {
        return closureId;
    }
}
