package com.waqiti.bnpl.domain;

import com.waqiti.bnpl.domain.enums.BnplPlanStatus;
import com.waqiti.bnpl.domain.enums.PaymentFrequency;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing a BNPL (Buy Now Pay Later) plan
 */
@Entity
@Table(name = "bnpl_plans")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class BnplPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_number", unique = true, nullable = false)
    private String planNumber;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "merchant_id")
    private String merchantId;

    @Column(name = "merchant_name")
    private String merchantName;

    @Column(name = "order_reference")
    private String orderReference;

    @Column(name = "purchase_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal purchaseAmount;

    @Column(name = "down_payment", precision = 19, scale = 2)
    private BigDecimal downPayment;

    @Column(name = "finance_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal financeAmount;

    @Column(name = "interest_rate", precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Column(name = "total_interest", precision = 19, scale = 2)
    private BigDecimal totalInterest;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "number_of_installments", nullable = false)
    private Integer numberOfInstallments;

    @Column(name = "installment_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal installmentAmount;

    @Column(name = "payment_frequency", nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentFrequency paymentFrequency;

    @Column(name = "first_payment_date", nullable = false)
    private LocalDate firstPaymentDate;

    @Column(name = "last_payment_date", nullable = false)
    private LocalDate lastPaymentDate;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private BnplPlanStatus status;

    @Column(name = "total_paid", precision = 19, scale = 2)
    private BigDecimal totalPaid;

    @Column(name = "remaining_balance", precision = 19, scale = 2)
    private BigDecimal remainingBalance;

    @Column(name = "late_fees", precision = 19, scale = 2)
    private BigDecimal lateFees;

    @Column(name = "description")
    private String description;

    @Column(name = "terms_accepted")
    private Boolean termsAccepted;

    @Column(name = "terms_accepted_at")
    private LocalDateTime termsAcceptedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @OneToMany(mappedBy = "bnplPlan", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<BnplInstallment> installments = new ArrayList<>();

    @OneToMany(mappedBy = "bnplPlan", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<BnplTransaction> transactions = new ArrayList<>();

    @OneToOne(mappedBy = "bnplPlan", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private CreditAssessment creditAssessment;

    @Column(name = "metadata")
    @Convert(converter = MetadataConverter.class)
    private MetadataMap metadata;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    // Business methods

    public void approve() {
        if (this.status != BnplPlanStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Can only approve plans in PENDING_APPROVAL status");
        }
        this.status = BnplPlanStatus.APPROVED;
        this.approvedAt = LocalDateTime.now();
    }

    public void activate() {
        if (this.status != BnplPlanStatus.APPROVED) {
            throw new IllegalStateException("Can only activate approved plans");
        }
        this.status = BnplPlanStatus.ACTIVE;
        this.activatedAt = LocalDateTime.now();
    }

    public void complete() {
        if (this.remainingBalance.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException("Cannot complete plan with remaining balance");
        }
        this.status = BnplPlanStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void cancel(String reason) {
        if (this.status == BnplPlanStatus.COMPLETED || this.status == BnplPlanStatus.CANCELLED) {
            throw new IllegalStateException("Cannot cancel completed or already cancelled plans");
        }
        this.status = BnplPlanStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
        this.cancellationReason = reason;
    }

    public void addPayment(BigDecimal amount) {
        this.totalPaid = this.totalPaid.add(amount);
        this.remainingBalance = this.totalAmount.subtract(this.totalPaid);
    }

    public void addLateFee(BigDecimal fee) {
        this.lateFees = this.lateFees.add(fee);
        this.totalAmount = this.totalAmount.add(fee);
        this.remainingBalance = this.remainingBalance.add(fee);
    }

    public boolean isOverdue() {
        if (this.status != BnplPlanStatus.ACTIVE) {
            return false;
        }
        
        return installments.stream()
                .anyMatch(installment -> installment.isOverdue());
    }

    public BigDecimal getOverdueAmount() {
        return installments.stream()
                .filter(BnplInstallment::isOverdue)
                .map(BnplInstallment::getAmountDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BnplInstallment getNextDueInstallment() {
        return installments.stream()
                .filter(i -> !i.isPaid())
                .findFirst()
                .orElse(null);
    }

    public int getPaidInstallmentsCount() {
        return (int) installments.stream()
                .filter(BnplInstallment::isPaid)
                .count();
    }

    public BigDecimal getCompletionPercentage() {
        if (totalAmount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return totalPaid.divide(totalAmount, 2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
}