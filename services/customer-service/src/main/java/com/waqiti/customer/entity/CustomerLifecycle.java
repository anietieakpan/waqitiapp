package com.waqiti.customer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Customer Lifecycle Entity
 *
 * Represents customer lifecycle stage tracking including stage transitions,
 * engagement metrics, and retention analytics.
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-11-19
 */
@Entity
@Table(name = "customer_lifecycle", indexes = {
    @Index(name = "idx_customer_lifecycle_customer", columnList = "customer_id"),
    @Index(name = "idx_customer_lifecycle_stage", columnList = "lifecycle_stage"),
    @Index(name = "idx_customer_lifecycle_entered", columnList = "stage_entered_at"),
    @Index(name = "idx_customer_lifecycle_churn", columnList = "churn_probability")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "customer")
@EqualsAndHashCode(of = "lifecycleId")
public class CustomerLifecycle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "lifecycle_id", unique = true, nullable = false, length = 100)
    private String lifecycleId;

    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", referencedColumnName = "customer_id", insertable = false, updatable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_stage", nullable = false, length = 20)
    private LifecycleStage lifecycleStage;

    @Column(name = "stage_entered_at", nullable = false)
    @Builder.Default
    private LocalDateTime stageEnteredAt = LocalDateTime.now();

    @Column(name = "stage_duration_days")
    private Integer stageDurationDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_stage", length = 20)
    private LifecycleStage previousStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "next_stage", length = 20)
    private LifecycleStage nextStage;

    @Column(name = "lifecycle_score", precision = 5, scale = 2)
    private BigDecimal lifecycleScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "engagement_level", length = 20)
    private EngagementLevel engagementLevel;

    @Column(name = "churn_probability", precision = 5, scale = 4)
    private BigDecimal churnProbability;

    @Enumerated(EnumType.STRING)
    @Column(name = "retention_priority", length = 20)
    private RetentionPriority retentionPriority;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Enums
    public enum LifecycleStage {
        PROSPECT,
        ONBOARDING,
        ACTIVE,
        AT_RISK,
        DORMANT,
        CHURNED,
        REACTIVATED
    }

    public enum EngagementLevel {
        VERY_HIGH,
        HIGH,
        MEDIUM,
        LOW,
        VERY_LOW,
        NONE
    }

    public enum RetentionPriority {
        CRITICAL,
        HIGH,
        MEDIUM,
        LOW,
        NONE
    }

    /**
     * Check if customer is at risk
     *
     * @return true if lifecycle stage is AT_RISK
     */
    public boolean isAtRisk() {
        return lifecycleStage == LifecycleStage.AT_RISK;
    }

    /**
     * Check if customer is dormant
     *
     * @return true if lifecycle stage is DORMANT
     */
    public boolean isDormant() {
        return lifecycleStage == LifecycleStage.DORMANT;
    }

    /**
     * Check if customer has churned
     *
     * @return true if lifecycle stage is CHURNED
     */
    public boolean isChurned() {
        return lifecycleStage == LifecycleStage.CHURNED;
    }

    /**
     * Check if customer is active
     *
     * @return true if lifecycle stage is ACTIVE or REACTIVATED
     */
    public boolean isActive() {
        return lifecycleStage == LifecycleStage.ACTIVE ||
               lifecycleStage == LifecycleStage.REACTIVATED;
    }

    /**
     * Check if customer has high churn risk
     *
     * @return true if churn probability is greater than 0.7
     */
    public boolean isHighChurnRisk() {
        return churnProbability != null &&
               churnProbability.compareTo(new BigDecimal("0.70")) > 0;
    }

    /**
     * Check if customer has critical retention priority
     *
     * @return true if retention priority is CRITICAL
     */
    public boolean isCriticalRetention() {
        return retentionPriority == RetentionPriority.CRITICAL;
    }

    /**
     * Transition to new lifecycle stage
     *
     * @param newStage the new lifecycle stage
     */
    public void transitionToStage(LifecycleStage newStage) {
        this.previousStage = this.lifecycleStage;
        this.lifecycleStage = newStage;
        this.stageEnteredAt = LocalDateTime.now();
        this.stageDurationDays = 0;
    }

    /**
     * Update churn probability
     *
     * @param probability the churn probability (0.0 to 1.0)
     */
    public void updateChurnProbability(BigDecimal probability) {
        if (probability != null &&
            probability.compareTo(BigDecimal.ZERO) >= 0 &&
            probability.compareTo(BigDecimal.ONE) <= 0) {
            this.churnProbability = probability;
            updateRetentionPriority();
        } else {
            throw new IllegalArgumentException("Churn probability must be between 0 and 1");
        }
    }

    /**
     * Update retention priority based on churn probability
     */
    private void updateRetentionPriority() {
        if (churnProbability == null) {
            this.retentionPriority = RetentionPriority.NONE;
        } else if (churnProbability.compareTo(new BigDecimal("0.80")) > 0) {
            this.retentionPriority = RetentionPriority.CRITICAL;
        } else if (churnProbability.compareTo(new BigDecimal("0.60")) > 0) {
            this.retentionPriority = RetentionPriority.HIGH;
        } else if (churnProbability.compareTo(new BigDecimal("0.40")) > 0) {
            this.retentionPriority = RetentionPriority.MEDIUM;
        } else if (churnProbability.compareTo(new BigDecimal("0.20")) > 0) {
            this.retentionPriority = RetentionPriority.LOW;
        } else {
            this.retentionPriority = RetentionPriority.NONE;
        }
    }

    /**
     * Calculate days in current stage
     *
     * @return days in current stage
     */
    public long getDaysInStage() {
        if (stageEnteredAt == null) {
            return 0;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(stageEnteredAt, LocalDateTime.now());
    }
}
