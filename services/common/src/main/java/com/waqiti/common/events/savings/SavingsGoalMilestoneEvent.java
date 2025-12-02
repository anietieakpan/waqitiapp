package com.waqiti.common.events.savings;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavingsGoalMilestoneEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "Event ID is required")
    @JsonProperty("event_id")
    private String eventId;

    @NotBlank(message = "Correlation ID is required")
    @JsonProperty("correlation_id")
    private String correlationId;

    @NotNull(message = "Timestamp is required")
    @JsonProperty("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime timestamp;

    @JsonProperty("event_version")
    private String eventVersion = "1.0";

    @NotBlank(message = "Event source is required")
    @JsonProperty("source")
    private String source;

    @NotBlank(message = "User ID is required")
    @JsonProperty("user_id")
    private String userId;

    @NotBlank(message = "Goal ID is required")
    @JsonProperty("goal_id")
    private String goalId;

    @NotBlank(message = "Goal name is required")
    @JsonProperty("goal_name")
    private String goalName;

    @JsonProperty("goal_category")
    private String goalCategory;

    @NotBlank(message = "Milestone type is required")
    @JsonProperty("milestone_type")
    private String milestoneType;

    @NotNull(message = "Milestone percentage is required")
    @JsonProperty("milestone_percentage")
    private Integer milestonePercentage;

    @NotNull(message = "Current amount is required")
    @JsonProperty("current_amount")
    private BigDecimal currentAmount;

    @NotNull(message = "Target amount is required")
    @JsonProperty("target_amount")
    private BigDecimal targetAmount;

    @NotBlank(message = "Currency is required")
    @JsonProperty("currency")
    private String currency;

    @JsonProperty("achieved_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime achievedAt;

    @JsonProperty("goal_created_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime goalCreatedAt;

    @JsonProperty("target_date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate targetDate;

    @JsonProperty("is_first_milestone")
    private Boolean isFirstMilestone;

    @JsonProperty("contributions_count")
    private Integer contributionsCount;

    @JsonProperty("last_contribution_amount")
    private BigDecimal lastContributionAmount;

    @JsonProperty("last_contribution_date")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime lastContributionDate;

    @JsonProperty("contribution_date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate contributionDate;

    @JsonProperty("projected_completion_date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate projectedCompletionDate;

    @JsonProperty("savings_frequency")
    private String savingsFrequency;

    @JsonProperty("is_auto_save_enabled")
    private Boolean isAutoSaveEnabled;
}