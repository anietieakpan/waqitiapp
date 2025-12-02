package com.waqiti.account.model;

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

/**
 * Account Closure Entity - Production Implementation
 *
 * Tracks account closure requests and processing status
 * 7-year retention for regulatory compliance
 *
 * @author Waqiti Platform Team
 * @version 2.0.0
 */
@Entity
@Table(name = "account_closures", indexes = {
    @Index(name = "idx_ac_account_id", columnList = "account_id"),
    @Index(name = "idx_ac_customer_id", columnList = "customer_id"),
    @Index(name = "idx_ac_status", columnList = "status"),
    @Index(name = "idx_ac_closure_date", columnList = "closure_date"),
    @Index(name = "idx_ac_closure_type", columnList = "closure_type")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountClosure {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false, length = 50)
    private String accountId;

    @Column(name = "customer_id", nullable = false, length = 50)
    private String customerId;

    @Column(name = "closure_type", length = 50)
    private String closureType; // VOLUNTARY, INVOLUNTARY, REGULATORY

    @Column(name = "closure_reason", length = 200)
    private String closureReason;

    @Column(name = "status", length = 20)
    private String status; // PENDING, IN_PROGRESS, COMPLETED, FAILED, REJECTED

    @Column(name = "closure_date")
    private LocalDateTime closureDate;

    @Column(name = "final_balance", precision = 19, scale = 4)
    private BigDecimal finalBalance;

    @Column(name = "accrued_interest", precision = 19, scale = 4)
    private BigDecimal accruedInterest;

    @Column(name = "closure_fees", precision = 19, scale = 4)
    private BigDecimal closureFees;

    @Column(name = "disbursement_amount", precision = 19, scale = 4)
    private BigDecimal disbursementAmount;

    @Column(name = "disbursement_method", length = 50)
    private String disbursementMethod;

    @Column(name = "requested_by", length = 100)
    private String requestedBy;

    @Column(name = "processed_by", length = 100)
    private String processedBy;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version")
    private Long version;
}
