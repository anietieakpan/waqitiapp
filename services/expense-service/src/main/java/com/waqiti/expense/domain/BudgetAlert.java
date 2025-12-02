package com.waqiti.expense.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * BudgetAlert Entity
 * Represents alerts triggered when budget thresholds are reached
 */
@Entity
@Table(name = "budget_alerts", indexes = {
    @Index(name = "idx_budget_alerts_budget", columnList = "budget_id"),
    @Index(name = "idx_budget_alerts_triggered", columnList = "is_triggered"),
    @Index(name = "idx_budget_alerts_acknowledged", columnList = "is_acknowledged"),
    @Index(name = "idx_budget_alerts_triggered_at", columnList = "triggered_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@EntityListeners(AuditingEntityListener.class)
public class BudgetAlert {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "budget_id", nullable = false)
    private Budget budget;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 50)
    private AlertType alertType;

    @Column(name = "threshold_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal thresholdPercentage;

    @Column(name = "triggered_at")
    private LocalDateTime triggeredAt;

    @Column(name = "is_triggered")
    private Boolean isTriggered = false;

    @Column(name = "is_acknowledged")
    private Boolean isAcknowledged = false;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Alert types for budget monitoring
     */
    public enum AlertType {
        WARNING,        // Warning threshold reached (e.g., 80%)
        CRITICAL,       // Critical threshold reached (e.g., 95%)
        EXCEEDED,       // Budget exceeded
        APPROACHING,    // Approaching budget based on projection
        CUSTOM          // Custom alert threshold
    }

    /**
     * Trigger the alert
     */
    public void trigger(String alertMessage) {
        this.isTriggered = true;
        this.triggeredAt = LocalDateTime.now();
        this.message = alertMessage;
    }

    /**
     * Acknowledge the alert
     */
    public void acknowledge() {
        this.isAcknowledged = true;
        this.acknowledgedAt = LocalDateTime.now();
    }

    /**
     * Reset the alert (for recurring budgets)
     */
    public void reset() {
        this.isTriggered = false;
        this.triggeredAt = null;
        this.isAcknowledged = false;
        this.acknowledgedAt = null;
        this.message = null;
    }

    /**
     * Check if alert needs to be sent (triggered but not acknowledged)
     */
    public boolean needsNotification() {
        return Boolean.TRUE.equals(isTriggered) && Boolean.FALSE.equals(isAcknowledged);
    }
}
