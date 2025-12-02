package com.waqiti.payment.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "split_payment_participants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SplitPaymentParticipant {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "split_payment_id", nullable = false)
    private SplitPayment splitPayment;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(nullable = false)
    private boolean paid;

    @Column(name = "payment_date")
    private LocalDateTime paymentDate;

    @Column(nullable = false, name = "created_at")
    private LocalDateTime createdAt;

    @Column(nullable = false, name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Creates a new split payment participant
     */
    public static SplitPaymentParticipant create(SplitPayment splitPayment, UUID userId, BigDecimal amount) {
        SplitPaymentParticipant participant = new SplitPaymentParticipant();
        participant.splitPayment = splitPayment;
        participant.userId = userId;
        participant.amount = amount;
        participant.paid = false;
        participant.createdAt = LocalDateTime.now();
        participant.updatedAt = LocalDateTime.now();
        return participant;
    }

    /**
     * Marks the participant as paid
     */
    public void markAsPaid(UUID transactionId) {
        if (this.paid) {
            throw new IllegalStateException("Participant has already paid");
        }
        
        this.paid = true;
        this.transactionId = transactionId;
        this.paymentDate = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Updates the participant's amount
     */
    public void updateAmount(BigDecimal amount) {
        if (this.paid) {
            throw new IllegalStateException("Cannot update amount for a participant who has already paid");
        }
        
        this.amount = amount;
        this.updatedAt = LocalDateTime.now();
    }
}