package com.waqiti.savings.dto;

import com.waqiti.savings.domain.AutoSaveRule;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for AutoSaveRule entity.
 *
 * @author Waqiti Development Team
 * @version 1.0
 * @since 2025-11-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Auto-save rule details")
public class AutoSaveRuleResponse {

    @Schema(description = "Rule ID")
    private UUID id;

    @Schema(description = "Goal ID")
    private UUID goalId;

    @Schema(description = "User ID")
    private UUID userId;

    @Schema(description = "Rule type", example = "ROUND_UP")
    private AutoSaveRule.RuleType ruleType;

    @Schema(description = "Fixed amount", example = "100.00")
    private BigDecimal amount;

    @Schema(description = "Percentage", example = "10.00")
    private BigDecimal percentage;

    @Schema(description = "Round up to amount", example = "1.00")
    private BigDecimal roundUpTo;

    @Schema(description = "Maximum amount per execution", example = "500.00")
    private BigDecimal maxAmount;

    @Schema(description = "Minimum amount per execution", example = "5.00")
    private BigDecimal minAmount;

    @Schema(description = "Execution frequency", example = "MONTHLY")
    private AutoSaveRule.Frequency frequency;

    @Schema(description = "Trigger type", example = "INCOME_BASED")
    private AutoSaveRule.TriggerType triggerType;

    @Schema(description = "Is rule active")
    private Boolean isActive;

    @Schema(description = "Is rule paused")
    private Boolean isPaused;

    @Schema(description = "Rule priority", example = "5")
    private Integer priority;

    @Schema(description = "Last execution timestamp")
    private LocalDateTime lastExecutedAt;

    @Schema(description = "Next execution timestamp")
    private LocalDateTime nextExecutionAt;

    @Schema(description = "Total executions", example = "25")
    private Integer executionCount;

    @Schema(description = "Successful executions", example = "23")
    private Integer successfulExecutions;

    @Schema(description = "Failed executions", example = "2")
    private Integer failedExecutions;

    @Schema(description = "Total amount saved through this rule", example = "2500.00")
    private BigDecimal totalSaved;

    @Schema(description = "Average save amount", example = "100.00")
    private BigDecimal averageSaveAmount;

    @Schema(description = "Success rate percentage", example = "92.00")
    private BigDecimal successRate;

    @Schema(description = "Rule status", example = "ACTIVE")
    private String status;

    @Schema(description = "Rule creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Last update timestamp")
    private LocalDateTime updatedAt;
}
