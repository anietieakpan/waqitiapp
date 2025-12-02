package com.waqiti.savings.mapper;

import com.waqiti.savings.domain.AutoSaveRule;
import com.waqiti.savings.dto.AutoSaveRuleResponse;
import com.waqiti.savings.dto.CreateAutoSaveRuleRequest;
import org.mapstruct.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * MapStruct mapper for AutoSaveRule entity.
 * Handles conversions between auto-save rule entities and DTOs with calculated fields.
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
public interface AutoSaveRuleMapper {

    /**
     * Convert AutoSaveRule entity to response DTO.
     * Includes calculated fields like success rate and status.
     *
     * @param rule the auto-save rule entity
     * @return the response DTO with calculated fields
     */
    @Mapping(target = "successRate", expression = "java(calculateSuccessRate(rule))")
    @Mapping(target = "status", expression = "java(determineStatus(rule))")
    AutoSaveRuleResponse toResponse(AutoSaveRule rule);

    /**
     * Convert list of AutoSaveRule entities to list of response DTOs.
     *
     * @param rules list of auto-save rule entities
     * @return list of response DTOs
     */
    List<AutoSaveRuleResponse> toResponseList(List<AutoSaveRule> rules);

    /**
     * Convert CreateAutoSaveRuleRequest to AutoSaveRule entity.
     *
     * @param request the create request
     * @return the auto-save rule entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "goalId", source = "goalId")
    @Mapping(target = "userId", ignore = true) // Set by service
    @Mapping(target = "isActive", constant = "true")
    @Mapping(target = "isPaused", constant = "false")
    @Mapping(target = "lastExecutedAt", ignore = true)
    @Mapping(target = "nextExecutionAt", ignore = true) // Calculated by service
    @Mapping(target = "executionCount", constant = "0")
    @Mapping(target = "successfulExecutions", constant = "0")
    @Mapping(target = "failedExecutions", constant = "0")
    @Mapping(target = "consecutiveFailures", constant = "0")
    @Mapping(target = "totalSaved", expression = "java(java.math.BigDecimal.ZERO)")
    @Mapping(target = "averageSaveAmount", ignore = true)
    @Mapping(target = "lastSaveAmount", ignore = true)
    @Mapping(target = "lastError", ignore = true)
    @Mapping(target = "lastErrorAt", ignore = true)
    @Mapping(target = "errorCount", constant = "0")
    @Mapping(target = "metadata", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "modifiedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    AutoSaveRule toEntity(CreateAutoSaveRuleRequest request);

    /**
     * Update existing AutoSaveRule entity from request.
     * Only updates modifiable fields.
     *
     * @param request the update request
     * @param rule the existing rule to update
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "goalId", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "lastExecutedAt", ignore = true)
    @Mapping(target = "nextExecutionAt", ignore = true)
    @Mapping(target = "executionCount", ignore = true)
    @Mapping(target = "successfulExecutions", ignore = true)
    @Mapping(target = "failedExecutions", ignore = true)
    @Mapping(target = "consecutiveFailures", ignore = true)
    @Mapping(target = "totalSaved", ignore = true)
    @Mapping(target = "averageSaveAmount", ignore = true)
    @Mapping(target = "lastSaveAmount", ignore = true)
    @Mapping(target = "lastError", ignore = true)
    @Mapping(target = "lastErrorAt", ignore = true)
    @Mapping(target = "errorCount", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "modifiedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntity(CreateAutoSaveRuleRequest request, @MappingTarget AutoSaveRule rule);

    /**
     * Calculate success rate percentage.
     */
    default BigDecimal calculateSuccessRate(AutoSaveRule rule) {
        if (rule.getExecutionCount() == null || rule.getExecutionCount() == 0) {
            return BigDecimal.ZERO;
        }

        int successful = rule.getSuccessfulExecutions() != null ? rule.getSuccessfulExecutions() : 0;
        BigDecimal rate = BigDecimal.valueOf(successful)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(rule.getExecutionCount()), 2, RoundingMode.HALF_UP);

        return rate;
    }

    /**
     * Determine rule status based on execution state.
     */
    default String determineStatus(AutoSaveRule rule) {
        if (!rule.getIsActive()) {
            return "INACTIVE";
        }
        if (rule.getIsPaused()) {
            return "PAUSED";
        }
        if (rule.isExpired()) {
            return "EXPIRED";
        }
        if (rule.getConsecutiveFailures() != null && rule.getConsecutiveFailures() >= 5) {
            return "SUSPENDED";
        }
        if (rule.hasErrors()) {
            return "ERROR";
        }
        return "ACTIVE";
    }
}
