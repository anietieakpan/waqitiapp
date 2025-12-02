package com.waqiti.payroll.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * PayrollPayment Entity
 *
 * Represents an individual employee payment within a payroll batch.
 * Tracks payment details, tax withholding, and transaction status.
 *
 * CRITICAL: PII and financial data - encrypted at rest
 * COMPLIANCE: Subject to SOX, FLSA, FCRA regulations
 * RETENTION: 7 years minimum for IRS requirements
 */
@Entity
@Table(name = "payroll_payments", indexes = {
    @Index(name = "idx_payroll_batch_id", columnList = "payroll_batch_id"),
    @Index(name = "idx_employee_id", columnList = "employee_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_transaction_id", columnList = "transaction_id"),
    @Index(name = "idx_processed_at", columnList = "processed_at")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"bankAccountNumber", "routingNumber"})
public class PayrollPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "payroll_batch_id", nullable = false, length = 100)
    private String payrollBatchId;

    @Column(name = "employee_id", nullable = false, length = 100)
    private String employeeId;

    @Column(name = "employee_name", length = 200)
    private String employeeName;

    @Column(name = "employee_email", length = 200)
    private String employeeEmail;

    @Column(name = "gross_amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal grossAmount;

    @Column(name = "base_pay", precision = 19, scale = 4)
    private BigDecimal basePay;

    @Column(name = "overtime_pay", precision = 19, scale = 4)
    private BigDecimal overtimePay;

    @Column(name = "bonus_pay", precision = 19, scale = 4)
    private BigDecimal bonusPay;

    @Column(name = "commission_pay", precision = 19, scale = 4)
    private BigDecimal commissionPay;

    @Column(name = "total_deductions", precision = 19, scale = 4)
    private BigDecimal totalDeductions;

    @Column(name = "federal_tax", precision = 19, scale = 4)
    private BigDecimal federalTax;

    @Column(name = "state_tax", precision = 19, scale = 4)
    private BigDecimal stateTax;

    @Column(name = "social_security_tax", precision = 19, scale = 4)
    private BigDecimal socialSecurityTax;

    @Column(name = "medicare_tax", precision = 19, scale = 4)
    private BigDecimal medicareTax;

    @Column(name = "local_tax", precision = 19, scale = 4)
    private BigDecimal localTax;

    @Column(name = "tax_withheld", precision = 19, scale = 4)
    private BigDecimal taxWithheld;

    @Column(name = "health_insurance", precision = 19, scale = 4)
    private BigDecimal healthInsurance;

    @Column(name = "retirement_401k", precision = 19, scale = 4)
    private BigDecimal retirement401k;

    @Column(name = "garnishments", precision = 19, scale = 4)
    private BigDecimal garnishments;

    @Column(name = "other_deductions", precision = 19, scale = 4)
    private BigDecimal otherDeductions;

    @Column(name = "net_amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal netAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private PaymentStatus status;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod; // ACH, DIRECT_DEPOSIT, CHECK, WIRE

    @Column(name = "bank_account_number", length = 100)
    private String bankAccountNumber; // Encrypted

    @Column(name = "routing_number", length = 50)
    private String routingNumber; // Encrypted

    @Column(name = "bank_name", length = 200)
    private String bankName;

    @Column(name = "transaction_id", length = 200)
    private String transactionId;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

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

    public boolean isSuccessful() {
        return status == PaymentStatus.COMPLETED || status == PaymentStatus.SETTLED;
    }

    public boolean isFailed() {
        return status == PaymentStatus.FAILED || status == PaymentStatus.REJECTED;
    }

    public boolean isPending() {
        return status == PaymentStatus.PENDING || status == PaymentStatus.PROCESSING;
    }

    public void markAsCompleted(String transactionId) {
        this.status = PaymentStatus.COMPLETED;
        this.transactionId = transactionId;
        this.processedAt = LocalDateTime.now();
    }

    public void markAsFailed(String errorMessage) {
        this.status = PaymentStatus.FAILED;
        this.errorMessage = errorMessage;
        this.processedAt = LocalDateTime.now();
    }

    public void incrementRetryCount() {
        this.retryCount = (this.retryCount == null ? 0 : this.retryCount) + 1;
    }

    public BigDecimal calculateTotalTaxes() {
        BigDecimal total = BigDecimal.ZERO;
        if (federalTax != null) total = total.add(federalTax);
        if (stateTax != null) total = total.add(stateTax);
        if (socialSecurityTax != null) total = total.add(socialSecurityTax);
        if (medicareTax != null) total = total.add(medicareTax);
        if (localTax != null) total = total.add(localTax);
        return total;
    }

    public BigDecimal calculateTotalBenefitDeductions() {
        BigDecimal total = BigDecimal.ZERO;
        if (healthInsurance != null) total = total.add(healthInsurance);
        if (retirement401k != null) total = total.add(retirement401k);
        return total;
    }
}
