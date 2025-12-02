package com.waqiti.risk.model;

import com.waqiti.risk.dto.RiskAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Risk Rule Entity
 *
 * Defines business rules for risk assessment including:
 * - Rule conditions
 * - Thresholds
 * - Actions to take
 * - Priority and criticality
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "risk_rules")
public class RiskRule {

    @Id
    private String id;

    @Indexed(unique = true)
    @NotBlank
    private String ruleName;

    @NotBlank
    private String ruleType; // AMOUNT_THRESHOLD, VELOCITY_CHECK, FACTOR_SCORE, GEOGRAPHIC_RESTRICTION, TIME_RESTRICTION, PATTERN_MATCH, COMPOSITE

    @NotBlank
    private String description;

    @NotNull
    @Builder.Default
    private Boolean enabled = true;

    @Builder.Default
    private Boolean critical = false; // If true, triggers immediate high-risk classification

    @NotNull
    @Min(0)
    @Max(100)
    private Integer priority; // Higher number = higher priority

    // Rule Conditions
    private String ruleCondition; // JSON or expression

    // Thresholds
    private BigDecimal thresholdAmount;
    private Double scoreThreshold;
    private Integer countThreshold;
    private Integer timeWindowMinutes;

    // Geographic Restrictions
    private List<String> blockedCountries;
    private List<String> allowedCountries;

    // Time Restrictions
    private Integer restrictedStartHour; // 0-23
    private Integer restrictedEndHour;   // 0-23
    private List<String> restrictedDays; // MON, TUE, etc.

    // Composite Rule Support
    private List<String> subRules; // IDs of sub-rules
    private Integer requiredConditions; // How many sub-rules must match

    // Risk Scoring
    @Min(0)
    @Max(1)
    private Double riskScore; // Score to assign if rule triggers (0.0 - 1.0)

    private String riskLevel; // LOW, MEDIUM, HIGH

    // Actions
    private List<RiskAction> actions; // Actions to take when rule triggers

    // Rule Execution
    @Builder.Default
    private Long executionCount = 0L;

    @Builder.Default
    private Long triggerCount = 0L;

    private LocalDateTime lastExecutedAt;
    private LocalDateTime lastTriggeredAt;

    // Performance Metrics
    private Long averageExecutionTimeMs;
    private Double triggerRate; // triggerCount / executionCount

    // Rule Lifecycle
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveUntil;

    private String createdBy;
    private String updatedBy;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    // Metadata
    private Map<String, Object> metadata;

    private String version;
    private String category; // FRAUD, COMPLIANCE, AML, etc.

    // DRL Definition (if using Drools)
    private String drlDefinition;

    /**
     * Check if rule is currently effective
     */
    public boolean isEffective() {
        LocalDateTime now = LocalDateTime.now();

        if (!enabled) {
            return false;
        }

        if (effectiveFrom != null && now.isBefore(effectiveFrom)) {
            return false;
        }

        if (effectiveUntil != null && now.isAfter(effectiveUntil)) {
            return false;
        }

        return true;
    }

    /**
     * Record rule execution
     */
    public void recordExecution(boolean triggered) {
        this.executionCount++;
        this.lastExecutedAt = LocalDateTime.now();

        if (triggered) {
            this.triggerCount++;
            this.lastTriggeredAt = LocalDateTime.now();
        }

        // Update trigger rate
        if (executionCount > 0) {
            this.triggerRate = (double) triggerCount / executionCount;
        }

        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Update execution metrics
     */
    public void updateExecutionMetrics(long executionTimeMs) {
        if (averageExecutionTimeMs == null) {
            averageExecutionTimeMs = executionTimeMs;
        } else {
            // Moving average
            averageExecutionTimeMs = (averageExecutionTimeMs + executionTimeMs) / 2;
        }
    }
}
