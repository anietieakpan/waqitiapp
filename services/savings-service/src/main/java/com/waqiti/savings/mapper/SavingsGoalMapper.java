package com.waqiti.savings.mapper;

import com.waqiti.savings.domain.SavingsGoal;
import com.waqiti.savings.dto.CreateSavingsGoalRequest;
import com.waqiti.savings.dto.SavingsGoalResponse;
import org.mapstruct.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * MapStruct mapper for SavingsGoal entity.
 * Handles conversions between entities and DTOs with calculated fields.
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
public interface SavingsGoalMapper {

    /**
     * Convert SavingsGoal entity to response DTO.
     * Includes calculated fields like daysRemaining, onTrack, etc.
     *
     * @param goal the savings goal entity
     * @return the response DTO with calculated fields
     */
    @Mapping(target = "daysRemaining", expression = "java(calculateDaysRemaining(goal))")
    @Mapping(target = "monthsRemaining", expression = "java(calculateMonthsRemaining(goal))")
    @Mapping(target = "onTrack", expression = "java(isOnTrack(goal))")
    @Mapping(target = "overdue", expression = "java(isOverdue(goal))")
    SavingsGoalResponse toResponse(SavingsGoal goal);

    /**
     * Convert list of SavingsGoal entities to list of response DTOs.
     *
     * @param goals list of savings goal entities
     * @return list of response DTOs
     */
    List<SavingsGoalResponse> toResponseList(List<SavingsGoal> goals);

    /**
     * Convert CreateSavingsGoalRequest to SavingsGoal entity.
     *
     * @param request the create request
     * @return the savings goal entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "name", source = "goalName")
    @Mapping(target = "currentAmount", constant = "0")
    @Mapping(target = "progressPercentage", constant = "0")
    @Mapping(target = "status", constant = "ACTIVE")
    @Mapping(target = "totalContributions", constant = "0")
    @Mapping(target = "totalWithdrawals", constant = "0")
    @Mapping(target = "interestEarned", constant = "0")
    @Mapping(target = "currentStreak", constant = "0")
    @Mapping(target = "longestStreak", constant = "0")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    SavingsGoal toEntity(CreateSavingsGoalRequest request);

    /**
     * Calculate days remaining to target date.
     */
    default Integer calculateDaysRemaining(SavingsGoal goal) {
        if (goal.getTargetDate() == null) return null;
        long days = ChronoUnit.DAYS.between(LocalDateTime.now(), goal.getTargetDate());
        return days > 0 ? (int) days : 0;
    }

    /**
     * Calculate months remaining to target date.
     */
    default Integer calculateMonthsRemaining(SavingsGoal goal) {
        if (goal.getTargetDate() == null) return null;
        long months = ChronoUnit.MONTHS.between(LocalDateTime.now(), goal.getTargetDate());
        return months > 0 ? (int) months : 0;
    }

    /**
     * Check if goal is on track.
     */
    default Boolean isOnTrack(SavingsGoal goal) {
        if (goal.getRequiredMonthlySaving() == null || goal.getAverageMonthlyContribution() == null) {
            return true; // No data to determine
        }
        return goal.getAverageMonthlyContribution().compareTo(goal.getRequiredMonthlySaving()) >= 0;
    }

    /**
     * Check if goal is overdue.
     */
    default Boolean isOverdue(SavingsGoal goal) {
        return goal.getTargetDate() != null &&
               LocalDateTime.now().isAfter(goal.getTargetDate()) &&
               goal.getStatus() != SavingsGoal.Status.COMPLETED;
    }
}
