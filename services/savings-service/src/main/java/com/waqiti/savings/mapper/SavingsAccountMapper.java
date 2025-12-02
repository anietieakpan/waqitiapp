package com.waqiti.savings.mapper;

import com.waqiti.savings.domain.SavingsAccount;
import com.waqiti.savings.dto.CreateSavingsAccountRequest;
import com.waqiti.savings.dto.SavingsAccountResponse;
import com.waqiti.savings.dto.UpdateSavingsAccountRequest;
import org.mapstruct.*;

import java.util.List;

/**
 * MapStruct mapper for SavingsAccount entity.
 * Handles conversions between entities and DTOs.
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
public interface SavingsAccountMapper {

    /**
     * Convert SavingsAccount entity to response DTO.
     *
     * @param account the savings account entity
     * @return the response DTO
     */
    SavingsAccountResponse toResponse(SavingsAccount account);

    /**
     * Convert list of SavingsAccount entities to list of response DTOs.
     *
     * @param accounts list of savings account entities
     * @return list of response DTOs
     */
    List<SavingsAccountResponse> toResponseList(List<SavingsAccount> accounts);

    /**
     * Convert CreateSavingsAccountRequest to SavingsAccount entity.
     *
     * @param request the create request
     * @return the savings account entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "accountNumber", ignore = true)
    @Mapping(target = "balance", constant = "0")
    @Mapping(target = "availableBalance", constant = "0")
    @Mapping(target = "status", constant = "ACTIVE")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    SavingsAccount toEntity(CreateSavingsAccountRequest request);

    /**
     * Update existing SavingsAccount entity from UpdateSavingsAccountRequest.
     * Only updates non-null fields from the request.
     *
     * @param request the update request
     * @param account the existing account to update
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "accountNumber", ignore = true)
    @Mapping(target = "accountType", ignore = true)
    @Mapping(target = "balance", ignore = true)
    @Mapping(target = "availableBalance", ignore = true)
    @Mapping(target = "currency", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntity(UpdateSavingsAccountRequest request, @MappingTarget SavingsAccount account);
}
