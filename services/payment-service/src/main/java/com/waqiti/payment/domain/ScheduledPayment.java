package com.waqiti.payment.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "scheduled_payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduledPayment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID senderId;

    @Column(nullable = false)
    private UUID recipientId;
    
    @Column(nullable = false)
    private UUID sourceWalletId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScheduledPaymentStatus status;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScheduledPaymentFrequency frequency;

    @Column(nullable = false, name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;
    
    @Column(name = "next_execution_date")
    private LocalDate nextExecutionDate;
    
    @Column(name = "last_execution_date")
    private LocalDate lastExecutionDate;
    
    @Column(name = "total_executions")
    private int totalExecutions;
    
    @Column(name = "completed_executions")
    private int completedExecutions;
    
    @Column(name = "max_executions")
    private Integer maxExecutions;

    @Column(nullable = false, name = "created_at")
    private LocalDateTime createdAt;

    @Column(nullable = false, name = "updated_at")
    private LocalDateTime updatedAt;
    
    @OneToMany(mappedBy = "scheduledPayment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ScheduledPaymentExecution> executions = new ArrayList<>();

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
     * Creates a new scheduled payment
     */
    public static ScheduledPayment create(UUID senderId, UUID recipientId, UUID sourceWalletId,
                                        BigDecimal amount, String currency, String description,
                                        ScheduledPaymentFrequency frequency, LocalDate startDate,
                                        LocalDate endDate, Integer maxExecutions) {
        ScheduledPayment payment = new ScheduledPayment();
        payment.senderId = senderId;
        payment.recipientId = recipientId;
        payment.sourceWalletId = sourceWalletId;
        payment.amount = amount;
        payment.currency = currency;
        payment.description = description;
        payment.status = ScheduledPaymentStatus.ACTIVE;
        payment.frequency = frequency;
        payment.startDate = startDate;
        payment.endDate = endDate;
        payment.nextExecutionDate = startDate;
        payment.totalExecutions = 0;
        payment.completedExecutions = 0;
        payment.maxExecutions = maxExecutions;
        payment.createdAt = LocalDateTime.now();
        payment.updatedAt = LocalDateTime.now();
        return payment;
    }

    /**
     * Pauses the scheduled payment
     */
    public void pause() {
        validateActive();
        
        this.status = ScheduledPaymentStatus.PAUSED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Resumes the scheduled payment
     */
    public void resume() {
        if (this.status != ScheduledPaymentStatus.PAUSED) {
            throw new IllegalStateException("Scheduled payment is not paused");
        }
        
        this.status = ScheduledPaymentStatus.ACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Cancels the scheduled payment
     */
    public void cancel() {
        if (this.status == ScheduledPaymentStatus.COMPLETED || 
            this.status == ScheduledPaymentStatus.CANCELED) {
            throw new IllegalStateException("Cannot cancel a completed or already canceled scheduled payment");
        }
        
        this.status = ScheduledPaymentStatus.CANCELED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Marks the scheduled payment as completed
     */
    public void complete() {
        this.status = ScheduledPaymentStatus.COMPLETED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Records a successful execution
     */
    public ScheduledPaymentExecution recordExecution(UUID transactionId) {
        validateActive();
        
        if (this.nextExecutionDate == null || LocalDate.now().isBefore(this.nextExecutionDate)) {
            throw new IllegalStateException("Not yet time for next execution");
        }
        
        ScheduledPaymentExecution execution = ScheduledPaymentExecution.create(
                this, transactionId, this.amount, this.currency);
        
        this.executions.add(execution);
        this.completedExecutions++;
        this.lastExecutionDate = LocalDate.now();
        this.nextExecutionDate = calculateNextExecutionDate();
        
        // Check if this was the last execution
        if ((this.maxExecutions != null && this.completedExecutions >= this.maxExecutions) ||
            (this.endDate != null && (this.nextExecutionDate == null || this.nextExecutionDate.isAfter(this.endDate)))) {
            this.complete();
        }
        
        this.updatedAt = LocalDateTime.now();
        return execution;
    }

    /**
     * Records a failed execution attempt
     */
    public ScheduledPaymentExecution recordFailedExecution(String errorMessage) {
        validateActive();
        
        ScheduledPaymentExecution execution = ScheduledPaymentExecution.createFailed(
                this, this.amount, this.currency, errorMessage);
        
        this.executions.add(execution);
        this.updatedAt = LocalDateTime.now();
        return execution;
    }

    /**
     * Calculates the next execution date based on frequency
     */
    private LocalDate calculateNextExecutionDate() {
        if (this.lastExecutionDate == null) {
            return this.startDate;
        }
        
        LocalDate nextDate = switch (this.frequency) {
            case DAILY -> this.lastExecutionDate.plusDays(1);
            case WEEKLY -> this.lastExecutionDate.plusWeeks(1);
            case BIWEEKLY -> this.lastExecutionDate.plusWeeks(2);
            case MONTHLY -> this.lastExecutionDate.plusMonths(1);
            case QUARTERLY -> this.lastExecutionDate.plusMonths(3);
            case YEARLY -> this.lastExecutionDate.plusYears(1);
            case ONE_TIME -> null; // No next date for one-time payments
        };
        
        // If we've reached the end date, return null
        if (this.endDate != null && nextDate != null && nextDate.isAfter(this.endDate)) {
            return null;
        }
        
        return nextDate;
    }

    /**
     * Validates that the scheduled payment is active
     */
    private void validateActive() {
        if (this.status != ScheduledPaymentStatus.ACTIVE) {
            throw new IllegalStateException("Scheduled payment is not active");
        }
    }
}