package com.waqiti.payroll.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * PayrollBatch Entity
 *
 * Represents a complete payroll processing batch for a company.
 * Tracks all payroll execution metrics, compliance status, and audit trail.
 *
 * CRITICAL: Contains sensitive financial data - encrypted at rest
 * AUDIT: All changes logged for regulatory compliance
 * RETENTION: 7 years for IRS/DOL requirements
 */
@Entity
@Table(name = "payroll_batches", indexes = {
    @Index(name = "idx_payroll_batch_id_company", columnList = "payroll_batch_id,company_id", unique = true),
    @Index(name = "idx_company_pay_period", columnList = "company_id,pay_period"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_processed_at", columnList = "processed_at"),
    @Index(name = "idx_correlation_id", columnList = "correlation_id")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"fundReservationId"})
public class PayrollBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "payroll_batch_id", nullable = false, length = 100)
    private String payrollBatchId;

    @Column(name = "company_id", nullable = false, length = 100)
    private String companyId;

    @Column(name = "pay_period", nullable = false)
    private LocalDate payPeriod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payroll_type", nullable = false, length = 50)
    private PayrollType payrollType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private BatchStatus status;

    @Column(name = "total_employees")
    private Integer totalEmployees;

    @Column(name = "valid_employees")
    private Integer validEmployees;

    @Column(name = "invalid_employees")
    private Integer invalidEmployees;

    @Column(name = "successful_payments")
    private Integer successfulPayments;

    @Column(name = "failed_payments")
    private Integer failedPayments;

    @Column(name = "gross_amount", precision = 19, scale = 4)
    private BigDecimal grossAmount;

    @Column(name = "total_deductions", precision = 19, scale = 4)
    private BigDecimal totalDeductions;

    @Column(name = "total_tax_withheld", precision = 19, scale = 4)
    private BigDecimal totalTaxWithheld;

    @Column(name = "net_amount", precision = 19, scale = 4)
    private BigDecimal netAmount;

    @Column(name = "fund_reservation_id", length = 100)
    private String fundReservationId;

    @Column(name = "compliance_violations")
    private Integer complianceViolations;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "correlation_id", nullable = false, length = 200)
    private String correlationId;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    // Business logic methods

    public boolean isCompleted() {
        return status == BatchStatus.COMPLETED || status == BatchStatus.COMPLETED_WITH_REVIEW;
    }

    public boolean isFailed() {
        return status == BatchStatus.FAILED;
    }

    public boolean hasCompliance Violations() {
        return complianceViolations != null && complianceViolations > 0;
    }

    public boolean requiresReview() {
        return status == BatchStatus.COMPLETED_WITH_REVIEW || hasComplianceViolations();
    }

    public double getSuccessRate() {
        if (totalEmployees == null || totalEmployees == 0) return 0.0;
        return (successfulPayments != null ? successfulPayments : 0) * 100.0 / totalEmployees;
    }

    public void incrementRetryCount() {
        this.retryCount = (this.retryCount == null ? 0 : this.retryCount) + 1;
    }

    public void markAsCompleted() {
        this.status = BatchStatus.COMPLETED;
        this.processedAt = LocalDateTime.now();
    }

    public void markAsFailed(String reason) {
        this.status = BatchStatus.FAILED;
        this.failureReason = reason;
        this.processedAt = LocalDateTime.now();
    }
}
