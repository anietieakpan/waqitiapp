package com.waqiti.savings.dto;

import com.waqiti.savings.domain.SavingsGoal;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for SavingsGoal entity.
 *
 * @author Waqiti Development Team
 * @version 1.0
 * @since 2025-11-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Savings goal details")
public class SavingsGoalResponse {

    @Schema(description = "Unique goal identifier")
    private UUID id;

    @Schema(description = "Goal owner user ID")
    private UUID userId;

    @Schema(description = "Associated savings account ID")
    private UUID accountId;

    @Schema(description = "Goal name", example = "Dream Vacation to Hawaii")
    private String name;

    @Schema(description = "Goal description")
    private String description;

    @Schema(description = "Goal category", example = "VACATION")
    private SavingsGoal.Category category;

    @Schema(description = "Target amount to save", example = "5000.00")
    private BigDecimal targetAmount;

    @Schema(description = "Current saved amount", example = "2500.00")
    private BigDecimal currentAmount;

    @Schema(description = "Currency", example = "USD")
    private String currency;

    @Schema(description = "Target completion date")
    private LocalDateTime targetDate;

    @Schema(description = "Goal priority", example = "HIGH")
    private SavingsGoal.Priority priority;

    @Schema(description = "Goal visibility", example = "PRIVATE")
    private SavingsGoal.Visibility visibility;

    @Schema(description = "Goal icon", example = "plane")
    private String icon;

    @Schema(description = "Goal color", example = "#3498DB")
    private String color;

    @Schema(description = "Goal image URL")
    private String imageUrl;

    @Schema(description = "Progress percentage", example = "50.00")
    private BigDecimal progressPercentage;

    @Schema(description = "Goal status", example = "ACTIVE")
    private SavingsGoal.Status status;

    @Schema(description = "Goal completion timestamp")
    private LocalDateTime completedAt;

    @Schema(description = "Auto-save enabled")
    private Boolean autoSaveEnabled;

    @Schema(description = "Flexible target allowed")
    private Boolean flexibleTarget;

    @Schema(description = "Withdrawals allowed")
    private Boolean allowWithdrawals;

    @Schema(description = "Interest rate", example = "4.50")
    private BigDecimal interestRate;

    @Schema(description = "Total interest earned", example = "45.25")
    private BigDecimal interestEarned;

    @Schema(description = "Total contributions count", example = "25")
    private Integer totalContributions;

    @Schema(description = "Average monthly contribution", example = "250.00")
    private BigDecimal averageMonthlyContribution;

    @Schema(description = "Required monthly saving to reach goal", example = "300.00")
    private BigDecimal requiredMonthlySaving;

    @Schema(description = "Projected completion date")
    private LocalDateTime projectedCompletionDate;

    @Schema(description = "Current contribution streak", example = "6")
    private Integer currentStreak;

    @Schema(description = "Longest contribution streak", example = "12")
    private Integer longestStreak;

    @Schema(description = "Last contribution timestamp")
    private LocalDateTime lastContributionAt;

    @Schema(description = "Days remaining to target date", example = "180")
    private Integer daysRemaining;

    @Schema(description = "Months remaining to target date", example = "6")
    private Integer monthsRemaining;

    @Schema(description = "Is goal on track")
    private Boolean onTrack;

    @Schema(description = "Is goal overdue")
    private Boolean overdue;

    @Schema(description = "Goal creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Last update timestamp")
    private LocalDateTime updatedAt;
}
