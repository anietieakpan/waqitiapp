package com.waqiti.grouppayment.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "group_payment_participants")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupPaymentParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_payment_id", nullable = false)
    private GroupPayment groupPayment;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String email;

    @Column
    private String displayName;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal owedAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal paidAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ParticipantStatus status;

    @Column
    private String paymentMethod;

    @Column
    private String transactionId;

    @Column
    private Instant paidAt;

    @Column
    private Instant invitedAt;

    @Column
    private Instant acceptedAt;

    @Column
    private Integer remindersSent;

    @Column
    private Instant lastReminderSent;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public enum ParticipantStatus {
        INVITED,
        ACCEPTED,
        DECLINED,
        PAID,
        PARTIALLY_PAID,
        OVERDUE
    }

    public BigDecimal getRemainingAmount() {
        return owedAmount.subtract(paidAmount);
    }

    public boolean isPaidInFull() {
        return paidAmount.compareTo(owedAmount) >= 0;
    }
}