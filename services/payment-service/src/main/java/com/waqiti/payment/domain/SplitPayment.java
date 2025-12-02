package com.waqiti.payment.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "split_payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SplitPayment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID organizerId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SplitPaymentStatus status;

    @Column(nullable = false, name = "expiry_date")
    private LocalDateTime expiryDate;

    @Column(nullable = false, name = "created_at")
    private LocalDateTime createdAt;

    @Column(nullable = false, name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "splitPayment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SplitPaymentParticipant> participants = new ArrayList<>();

    @Version
    private Long version;

    // Audit fields
    @Setter
    @Column(name = "created_by")
    private String createdBy;
    
    @Setter
    @Column(name = "updated_by")
    private String updatedBy;

    /**
     * Creates a new split payment
     */
    public static SplitPayment create(UUID organizerId, String title, String description,
                                    BigDecimal totalAmount, String currency, int expiryDays) {
        SplitPayment splitPayment = new SplitPayment();
        splitPayment.organizerId = organizerId;
        splitPayment.title = title;
        splitPayment.description = description;
        splitPayment.totalAmount = totalAmount;
        splitPayment.currency = currency;
        splitPayment.status = SplitPaymentStatus.ACTIVE;
        splitPayment.expiryDate = LocalDateTime.now().plusDays(expiryDays);
        splitPayment.createdAt = LocalDateTime.now();
        splitPayment.updatedAt = LocalDateTime.now();
        return splitPayment;
    }

    /**
     * Adds a participant to the split payment
     */
    public SplitPaymentParticipant addParticipant(UUID userId, BigDecimal amount) {
        validateActive();
        
        // Check if participant already exists
        for (SplitPaymentParticipant participant : participants) {
            if (participant.getUserId().equals(userId)) {
                throw new IllegalArgumentException("User is already a participant");
            }
        }
        
        SplitPaymentParticipant participant = SplitPaymentParticipant.create(this, userId, amount);
        participants.add(participant);
        this.updatedAt = LocalDateTime.now();
        return participant;
    }

    /**
     * Removes a participant from the split payment
     */
    public void removeParticipant(UUID userId) {
        validateActive();
        
        participants.removeIf(participant -> participant.getUserId().equals(userId));
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Updates a participant's amount
     */
    public void updateParticipantAmount(UUID userId, BigDecimal amount) {
        validateActive();
        
        participants.stream()
                .filter(participant -> participant.getUserId().equals(userId))
                .findFirst()
                .ifPresent(participant -> participant.updateAmount(amount));
        
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Marks the split payment as completed
     */
    public void complete() {
        validateActive();
        
        this.status = SplitPaymentStatus.COMPLETED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Cancels the split payment
     */
    public void cancel() {
        validateActive();
        
        this.status = SplitPaymentStatus.CANCELED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Marks the split payment as expired
     */
    public void expire() {
        validateActive();
        
        if (!isExpired()) {
            throw new IllegalStateException("Cannot expire a split payment that is not yet expired");
        }
        
        this.status = SplitPaymentStatus.EXPIRED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Checks if the split payment is expired
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiryDate);
    }

    /**
     * Gets the total amount paid by all participants
     */
    public BigDecimal getTotalPaidAmount() {
        return participants.stream()
                .filter(SplitPaymentParticipant::isPaid)
                .map(SplitPaymentParticipant::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Gets the total amount remaining to be paid
     */
    public BigDecimal getRemainingAmount() {
        return totalAmount.subtract(getTotalPaidAmount());
    }

    /**
     * Checks if the split payment is fully paid
     */
    public boolean isFullyPaid() {
        return getTotalPaidAmount().compareTo(totalAmount) >= 0;
    }

    /**
     * Gets the completion percentage
     */
    public BigDecimal getCompletionPercentage() {
        if (totalAmount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        return getTotalPaidAmount()
                .multiply(new BigDecimal("100"))
                .divide(totalAmount, 2, RoundingMode.HALF_UP);
    }

    /**
     * Validates that the split payment is active
     */
    private void validateActive() {
        if (this.status != SplitPaymentStatus.ACTIVE) {
            throw new IllegalStateException("Split payment is not active");
        }
    }
}