package com.waqiti.savings.mapper;

import com.waqiti.savings.domain.SavingsContribution;
import com.waqiti.savings.dto.ContributeRequest;
import com.waqiti.savings.dto.ContributionResponse;
import org.mapstruct.*;

import java.util.List;

/**
 * MapStruct mapper for SavingsContribution entity.
 * Handles conversions between contribution entities and DTOs.
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
public interface ContributionMapper {

    /**
     * Convert SavingsContribution entity to response DTO.
     *
     * @param contribution the contribution entity
     * @return the response DTO
     */
    @Mapping(target = "contributionId", source = "id")
    @Mapping(target = "contributionType", source = "type")
    @Mapping(target = "contributionDate", source = "createdAt")
    @Mapping(target = "newGoalBalance", ignore = true) // Set by service
    @Mapping(target = "progressPercentage", ignore = true) // Set by service
    @Mapping(target = "milestonesAchieved", ignore = true) // Set by service
    @Mapping(target = "goalCompleted", ignore = true) // Set by service
    @Mapping(target = "message", ignore = true) // Set by service
    ContributionResponse toResponse(SavingsContribution contribution);

    /**
     * Convert list of SavingsContribution entities to list of response DTOs.
     *
     * @param contributions list of contribution entities
     * @return list of response DTOs
     */
    List<ContributionResponse> toResponseList(List<SavingsContribution> contributions);

    /**
     * Convert ContributeRequest to SavingsContribution entity.
     *
     * @param request the contribution request
     * @return the contribution entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "goalId", ignore = true) // Set by service
    @Mapping(target = "userId", ignore = true) // Set by service
    @Mapping(target = "type", constant = "MANUAL")
    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "transactionId", ignore = true) // Set by service
    @Mapping(target = "processedAt", ignore = true)
    @Mapping(target = "failedAt", ignore = true)
    @Mapping(target = "failureReason", ignore = true)
    @Mapping(target = "retryCount", constant = "0")
    @Mapping(target = "nextRetryAt", ignore = true)
    @Mapping(target = "withdrawalReason", ignore = true)
    @Mapping(target = "destinationAccount", ignore = true)
    @Mapping(target = "metadata", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "modifiedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    SavingsContribution toEntity(ContributeRequest request);

    /**
     * Create a withdrawal contribution from amount and reason.
     *
     * @param amount the withdrawal amount (should be negative)
     * @param reason the withdrawal reason
     * @return the withdrawal contribution entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "goalId", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "amount", source = "amount")
    @Mapping(target = "type", constant = "WITHDRAWAL")
    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "withdrawalReason", source = "reason")
    @Mapping(target = "source", ignore = true)
    @Mapping(target = "note", ignore = true)
    @Mapping(target = "isAutoSave", constant = "false")
    @Mapping(target = "autoSaveRuleId", ignore = true)
    @Mapping(target = "paymentMethod", ignore = true)
    @Mapping(target = "transactionId", ignore = true)
    @Mapping(target = "destinationAccount", ignore = true)
    @Mapping(target = "processedAt", ignore = true)
    @Mapping(target = "failedAt", ignore = true)
    @Mapping(target = "failureReason", ignore = true)
    @Mapping(target = "retryCount", constant = "0")
    @Mapping(target = "nextRetryAt", ignore = true)
    @Mapping(target = "metadata", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "modifiedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    SavingsContribution createWithdrawal(java.math.BigDecimal amount, String reason);
}
