package com.waqiti.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * VelocityRule Entity - Production-Grade Velocity Check Rules
 *
 * Represents configurable rules for transaction velocity checks to prevent fraud.
 * Rules can limit transaction count and/or amount within specific time windows.
 *
 * FEATURES:
 * - Flexible rule types (daily limit, hourly velocity, large amount alerts)
 * - Time window configuration
 * - Amount thresholds (min/max)
 * - Alert-only mode (for monitoring without blocking)
 * - Priority-based evaluation
 * - Enable/disable toggle
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Production-Ready)
 */
@Entity
@Table(
    name = "velocity_rules",
    indexes = {
        @Index(name = "idx_velocity_rules_enabled", columnList = "enabled, priority"),
        @Index(name = "idx_velocity_rules_type", columnList = "rule_type, enabled"),
        @Index(name = "idx_velocity_rules_priority", columnList = "priority, enabled")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(of = {"id"})
public class VelocityRule {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Human-readable rule name
     */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /**
     * Rule type for categorization and logic routing
     */
    @Column(name = "rule_type", nullable = false, length = 50)
    private String ruleType;

    /**
     * Maximum number of transactions allowed within time window
     */
    @Column(name = "max_count")
    private Integer maxCount;

    /**
     * Time window in seconds for velocity calculation
     */
    @Column(name = "time_window_seconds")
    private Integer timeWindowSeconds;

    /**
     * Minimum transaction amount threshold (optional)
     */
    @Column(name = "min_amount", precision = 19, scale = 4)
    private BigDecimal minAmount;

    /**
     * Maximum transaction amount threshold (optional)
     */
    @Column(name = "max_amount", precision = 19, scale = 4)
    private BigDecimal maxAmount;

    /**
     * Alert only mode - trigger alert but don't block transaction
     */
    @Column(name = "alert_only")
    @Builder.Default
    private Boolean alertOnly = false;

    /**
     * Rule enabled/disabled toggle
     */
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    /**
     * Rule evaluation priority (lower number = higher priority)
     */
    @Column(name = "priority")
    @Builder.Default
    private Integer priority = 100;

    /**
     * Rule description for documentation
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * User/role scope (null = applies to all users)
     */
    @Column(name = "user_scope", length = 100)
    private String userScope;

    /**
     * Creation timestamp
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Last update timestamp
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Last triggered timestamp
     */
    @Column(name = "last_triggered_at")
    private Instant lastTriggeredAt;

    /**
     * Number of times this rule has been triggered
     */
    @Column(name = "trigger_count")
    @Builder.Default
    private Long triggerCount = 0L;

    /**
     * Get time window as Duration
     */
    @Transient
    public Duration getTimeWindow() {
        return timeWindowSeconds != null ?
            Duration.ofSeconds(timeWindowSeconds) :
            Duration.ofDays(1); // Default 24 hours
    }

    /**
     * Check if rule applies to a given amount
     */
    @Transient
    public boolean appliesToAmount(BigDecimal amount) {
        if (amount == null) {
            return false;
        }

        boolean meetsMin = (minAmount == null || amount.compareTo(minAmount) >= 0);
        boolean meetsMax = (maxAmount == null || amount.compareTo(maxAmount) <= 0);

        return meetsMin && meetsMax;
    }

    /**
     * Increment trigger count and update timestamp
     */
    public void recordTrigger() {
        this.triggerCount = (this.triggerCount == null ? 0L : this.triggerCount) + 1;
        this.lastTriggeredAt = Instant.now();
    }

    /**
     * Velocity rule types
     */
    public static class RuleType {
        public static final String DAILY_LIMIT = "DAILY_LIMIT";
        public static final String HOURLY_VELOCITY = "HOURLY_VELOCITY";
        public static final String LARGE_AMOUNT = "LARGE_AMOUNT";
        public static final String RAPID_SUCCESSION = "RAPID_SUCCESSION";
        public static final String CUMULATIVE_DAILY = "CUMULATIVE_DAILY";
        public static final String FIRST_TRANSACTION = "FIRST_TRANSACTION";
        public static final String CROSS_BORDER = "CROSS_BORDER";
        public static final String HIGH_RISK_MERCHANT = "HIGH_RISK_MERCHANT";
    }
}
