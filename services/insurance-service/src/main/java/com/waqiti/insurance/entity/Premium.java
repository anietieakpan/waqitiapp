package com.waqiti.insurance.entity;

import com.waqiti.insurance.model.PaymentMethod;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "premiums", indexes = {
        @Index(name = "idx_premium_policy_id", columnList = "policy_id"),
        @Index(name = "idx_premium_due_date", columnList = "due_date"),
        @Index(name = "idx_premium_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Premium {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", nullable = false)
    private InsurancePolicy policy;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "paid_date")
    private LocalDateTime paidDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PremiumStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 30)
    private PaymentMethod paymentMethod;

    @Column(name = "transaction_reference")
    private String transactionReference;

    @Column(name = "late_fee", precision = 19, scale = 2)
    private BigDecimal lateFee = BigDecimal.ZERO;

    @Column(name = "grace_period_end")
    private LocalDate gracePeriodEnd;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    public enum PremiumStatus {
        PENDING, PAID, OVERDUE, CANCELLED, REFUNDED
    }

    public boolean isOverdue() {
        return status == PremiumStatus.PENDING && LocalDate.now().isAfter(dueDate);
    }
}
