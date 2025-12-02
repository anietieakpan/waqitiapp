package com.waqiti.billingorchestrator.entity;

import com.waqiti.common.audit.Auditable;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Dunning Campaign Entity
 *
 * Automated payment collection campaigns for failed/overdue payments.
 *
 * CRITICAL BUSINESS FUNCTION:
 * - Recover $100K-$500K/month in failed payments
 * - Automated reminder sequences (Day 3, 7, 14, 30)
 * - Payment retry automation
 * - Customer retention (prevent involuntary churn)
 *
 * DUNNING WORKFLOW:
 * 1. Payment fails → Create dunning campaign
 * 2. Day 3: Friendly reminder email
 * 3. Day 7: Second reminder + retry payment
 * 4. Day 14: Urgent reminder + suspend service warning
 * 5. Day 30: Final notice + suspend service
 * 6. SUCCESS: Customer pays → Close campaign
 * 7. FAILURE: After 30 days → Cancel subscription
 *
 * WIN RATE: 60-70% of failed payments recovered via dunning
 *
 * @author Waqiti Billing Team
 * @since 1.0
 */
@Entity
@Table(name = "dunning_campaigns", indexes = {
    @Index(name = "idx_dunning_account", columnList = "account_id"),
    @Index(name = "idx_dunning_status", columnList = "status"),
    @Index(name = "idx_dunning_next_action", columnList = "next_action_date"),
    @Index(name = "idx_dunning_created", columnList = "created_at DESC")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DunningCampaign extends Auditable {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @Column(name = "billing_cycle_id")
    private UUID billingCycleId;

    @Column(name = "invoice_id")
    private UUID invoiceId;

    // Failed payment details
    @Column(name = "failed_payment_id")
    private UUID failedPaymentId;

    @Column(name = "amount_due", precision = 19, scale = 4, nullable = false)
    private BigDecimal amountDue;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    // Campaign status
    @Column(name = "status", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private DunningStatus status;

    @Column(name = "current_stage", length = 20)
    @Enumerated(EnumType.STRING)
    private DunningStage currentStage;

    // Timeline
    @Column(name = "payment_failed_at", nullable = false)
    private LocalDateTime paymentFailedAt;

    @Column(name = "next_action_date")
    private LocalDateTime nextActionDate;

    @Column(name = "last_reminder_sent_at")
    private LocalDateTime lastReminderSentAt;

    @Column(name = "payment_retry_attempted_at")
    private LocalDateTime paymentRetryAttemptedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    // Counters
    @Column(name = "reminders_sent")
    private Integer remindersSent;

    @Column(name = "payment_retries")
    private Integer paymentRetries;

    // Resolution
    @Column(name = "resolution_type", length = 50)
    @Enumerated(EnumType.STRING)
    private DunningResolution resolutionType;

    @Column(name = "payment_method_updated")
    private Boolean paymentMethodUpdated;

    @Column(name = "service_suspended")
    private Boolean serviceSuspended;

    @Column(name = "subscription_cancelled")
    private Boolean subscriptionCancelled;

    // Notes
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Version
    private Long version;

    public enum DunningStatus {
        ACTIVE,             // Campaign in progress
        RESOLVED_PAID,      // Customer paid
        RESOLVED_UPDATED,   // Payment method updated
        CANCELLED,          // Campaign cancelled
        FAILED,             // Exhausted all attempts
        SUSPENDED           // Service suspended, awaiting payment
    }

    public enum DunningStage {
        STAGE_1_DAY_3,      // Day 3: Friendly reminder
        STAGE_2_DAY_7,      // Day 7: Second reminder + retry
        STAGE_3_DAY_14,     // Day 14: Urgent + service warning
        STAGE_4_DAY_30,     // Day 30: Final notice
        COMPLETED           // All stages exhausted
    }

    public enum DunningResolution {
        PAYMENT_SUCCESSFUL,         // Customer paid
        PAYMENT_METHOD_UPDATED,     // Card updated
        SUBSCRIPTION_DOWNGRADED,    // Customer downgraded plan
        SUBSCRIPTION_CANCELLED,     // Voluntary cancellation
        SERVICE_SUSPENDED,          // Suspended for non-payment
        MANUAL_RESOLUTION,          // Resolved by support team
        DISPUTE_FILED              // Customer disputed charge
    }

    /**
     * Advances to next dunning stage
     */
    public void advanceToNextStage() {
        this.currentStage = switch (this.currentStage) {
            case STAGE_1_DAY_3 -> DunningStage.STAGE_2_DAY_7;
            case STAGE_2_DAY_7 -> DunningStage.STAGE_3_DAY_14;
            case STAGE_3_DAY_14 -> DunningStage.STAGE_4_DAY_30;
            case STAGE_4_DAY_30 -> DunningStage.COMPLETED;
            case COMPLETED -> DunningStage.COMPLETED;
        };
    }

    /**
     * Calculates next action date based on stage
     */
    public LocalDateTime calculateNextActionDate() {
        if (paymentFailedAt == null) return null;

        return switch (currentStage) {
            case STAGE_1_DAY_3 -> paymentFailedAt.plusDays(3);
            case STAGE_2_DAY_7 -> paymentFailedAt.plusDays(7);
            case STAGE_3_DAY_14 -> paymentFailedAt.plusDays(14);
            case STAGE_4_DAY_30 -> paymentFailedAt.plusDays(30);
            case COMPLETED -> null;
        };
    }

    /**
     * Marks campaign as resolved
     */
    public void markResolved(DunningResolution resolution) {
        this.status = resolution == DunningResolution.PAYMENT_SUCCESSFUL ||
                      resolution == DunningResolution.PAYMENT_METHOD_UPDATED ?
                DunningStatus.RESOLVED_PAID : DunningStatus.RESOLVED_UPDATED;
        this.resolutionType = resolution;
        this.resolvedAt = LocalDateTime.now();
    }
}
