package com.waqiti.savings.mapper;

import com.waqiti.savings.domain.Milestone;
import com.waqiti.savings.dto.CreateMilestoneRequest;
import com.waqiti.savings.dto.MilestoneResponse;
import org.mapstruct.*;

import java.util.List;

/**
 * MapStruct mapper for Milestone entity.
 * Handles conversions between milestone entities and DTOs with calculated fields.
 *
 * @author Waqiti Development Team
 * @version 1.0
 * @since 2025-11-19
 */
@Mapper(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface MilestoneMapper {

    /**
     * Convert Milestone entity to response DTO.
     * Includes calculated fields for reward claiming and overdue status.
     *
     * @param milestone the milestone entity
     * @return the response DTO with calculated fields
     */
    @Mapping(target = "canClaimReward", expression = "java(milestone.canClaimReward())")
    @Mapping(target = "isOverdue", expression = "java(milestone.isOverdue())")
    MilestoneResponse toResponse(Milestone milestone);

    /**
     * Convert list of Milestone entities to list of response DTOs.
     *
     * @param milestones list of milestone entities
     * @return list of response DTOs
     */
    List<MilestoneResponse> toResponseList(List<Milestone> milestones);

    /**
     * Convert CreateMilestoneRequest to Milestone entity.
     *
     * @param request the create request
     * @return the milestone entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "goalId", ignore = true) // Set by service
    @Mapping(target = "userId", ignore = true) // Set by service
    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "achievedAt", ignore = true)
    @Mapping(target = "achievementAmount", ignore = true)
    @Mapping(target = "rewardClaimed", constant = "false")
    @Mapping(target = "rewardClaimedAt", ignore = true)
    @Mapping(target = "approachNotificationSent", constant = "false")
    @Mapping(target = "achievementNotificationSent", constant = "false")
    @Mapping(target = "metadata", ignore = true)
    @Mapping(target = "isCustom", constant = "true")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "modifiedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    Milestone toEntity(CreateMilestoneRequest request);

    /**
     * Update existing Milestone entity from request.
     * Only updates modifiable fields.
     *
     * @param request the update request
     * @param milestone the existing milestone to update
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "goalId", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "achievedAt", ignore = true)
    @Mapping(target = "achievementAmount", ignore = true)
    @Mapping(target = "rewardClaimed", ignore = true)
    @Mapping(target = "rewardClaimedAt", ignore = true)
    @Mapping(target = "approachNotificationSent", ignore = true)
    @Mapping(target = "achievementNotificationSent", ignore = true)
    @Mapping(target = "metadata", ignore = true)
    @Mapping(target = "isCustom", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "modifiedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntity(CreateMilestoneRequest request, @MappingTarget Milestone milestone);

    /**
     * Create a system-generated milestone.
     *
     * @param percentage the target percentage (e.g., 25 for 25%)
     * @param goalId the goal ID
     * @param userId the user ID
     * @return the milestone entity
     */
    default Milestone createSystemMilestone(
        java.math.BigDecimal percentage,
        java.util.UUID goalId,
        java.util.UUID userId
    ) {
        return Milestone.builder()
            .goalId(goalId)
            .userId(userId)
            .name(percentage.intValue() + "% Complete")
            .description("Reached " + percentage.intValue() + "% of your savings goal")
            .targetPercentage(percentage)
            .status(Milestone.MilestoneStatus.PENDING)
            .isCustom(false)
            .notifyOnApproach(true)
            .notifyOnAchievement(true)
            .rewardClaimed(false)
            .approachNotificationSent(false)
            .achievementNotificationSent(false)
            .displayOrder(percentage.intValue())
            .build();
    }
}
