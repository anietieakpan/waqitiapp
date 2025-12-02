package com.waqiti.savings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for contribution operations.
 *
 * @author Waqiti Development Team
 * @version 1.0
 * @since 2025-11-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Contribution result")
public class ContributionResponse {

    @Schema(description = "Contribution ID")
    private UUID contributionId;

    @Schema(description = "Goal ID")
    private UUID goalId;

    @Schema(description = "Contribution amount", example = "250.00")
    private BigDecimal amount;

    @Schema(description = "Goal balance after contribution", example = "2750.00")
    private BigDecimal newGoalBalance;

    @Schema(description = "Updated progress percentage", example = "55.00")
    private BigDecimal progressPercentage;

    @Schema(description = "Contribution type", example = "MANUAL")
    private String contributionType;

    @Schema(description = "Contribution status", example = "COMPLETED")
    private String status;

    @Schema(description = "Contribution timestamp")
    private LocalDateTime contributionDate;

    @Schema(description = "Milestones achieved with this contribution")
    private java.util.List<String> milestonesAchieved;

    @Schema(description = "Goal completed with this contribution")
    private Boolean goalCompleted = false;

    @Schema(description = "Success message")
    private String message;
}
