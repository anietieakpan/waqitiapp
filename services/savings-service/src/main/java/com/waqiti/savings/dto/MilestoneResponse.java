package com.waqiti.savings.dto;

import com.waqiti.savings.domain.Milestone;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for Milestone entity.
 *
 * @author Waqiti Development Team
 * @version 1.0
 * @since 2025-11-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Milestone achievement details")
public class MilestoneResponse {

    @Schema(description = "Milestone ID")
    private UUID id;

    @Schema(description = "Goal ID")
    private UUID goalId;

    @Schema(description = "User ID")
    private UUID userId;

    @Schema(description = "Milestone name", example = "25% Complete")
    private String name;

    @Schema(description = "Milestone description")
    private String description;

    @Schema(description = "Target percentage", example = "25.00")
    private BigDecimal targetPercentage;

    @Schema(description = "Target amount", example = "1250.00")
    private BigDecimal targetAmount;

    @Schema(description = "Target date")
    private LocalDateTime targetDate;

    @Schema(description = "Milestone status", example = "ACHIEVED")
    private Milestone.MilestoneStatus status;

    @Schema(description = "Achievement timestamp")
    private LocalDateTime achievedAt;

    @Schema(description = "Amount when achieved", example = "1250.00")
    private BigDecimal achievementAmount;

    @Schema(description = "Reward type", example = "BADGE")
    private String rewardType;

    @Schema(description = "Reward value")
    private String rewardValue;

    @Schema(description = "Is reward claimed")
    private Boolean rewardClaimed;

    @Schema(description = "Reward claim timestamp")
    private LocalDateTime rewardClaimedAt;

    @Schema(description = "Milestone icon", example = "trophy")
    private String icon;

    @Schema(description = "Milestone color", example = "#FFD700")
    private String color;

    @Schema(description = "Badge image URL")
    private String badgeUrl;

    @Schema(description = "Display order", example = "1")
    private Integer displayOrder;

    @Schema(description = "Is custom milestone")
    private Boolean isCustom;

    @Schema(description = "Can reward be claimed")
    private Boolean canClaimReward;

    @Schema(description = "Is milestone overdue")
    private Boolean isOverdue;

    @Schema(description = "Creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Last update timestamp")
    private LocalDateTime updatedAt;
}
