package com.waqiti.account.mapper;

import com.waqiti.account.dto.request.CreateAccountRequestDTO;
import com.waqiti.account.dto.response.AccountResponseDTO;
import com.waqiti.account.entity.Account;
import com.waqiti.common.mapper.BaseMapper;
import org.mapstruct.*;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mapper interface for Account entity and DTO conversions
 * 
 * Uses MapStruct for automatic mapping generation with custom configurations
 * for complex field mappings and data transformations.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Mapper(
    componentModel = "spring",
    injectionStrategy = InjectionStrategy.CONSTRUCTOR,
    nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.WARN,
    uses = {AccountMapperHelper.class}
)
@Component
public interface AccountMapper extends BaseMapper<Account, AccountResponseDTO> {
    
    /**
     * Convert Account entity to AccountResponseDTO
     */
    @Override
    @Mappings({
        @Mapping(target = "paymentMethodCount", expression = "java(entity.getPaymentMethods() != null ? entity.getPaymentMethods().size() : 0)"),
        @Mapping(target = "subAccountCount", expression = "java(entity.getSubAccounts() != null ? entity.getSubAccounts().size() : 0)"),
        @Mapping(target = "parentAccountId", source = "parentAccount.id"),
        @Mapping(target = "holdAmount", expression = "java(calculateHoldAmount(entity))"),
        @Mapping(target = "isAccountActive", expression = "java(entity.isAccountActive())"),
        @Mapping(target = "daysSinceLastTransaction", expression = "java(calculateDaysSinceLastTransaction(entity))"),
        @Mapping(target = "accountAgeInDays", expression = "java(calculateAccountAge(entity))")
    })
    AccountResponseDTO toDto(Account entity);
    
    /**
     * Convert CreateAccountRequestDTO to Account entity
     */
    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "version", ignore = true),
        @Mapping(target = "createdAt", ignore = true),
        @Mapping(target = "updatedAt", ignore = true),
        @Mapping(target = "createdBy", ignore = true),
        @Mapping(target = "updatedBy", ignore = true),
        @Mapping(target = "deleted", constant = "false"),
        @Mapping(target = "deletedAt", ignore = true),
        @Mapping(target = "deletedBy", ignore = true),
        @Mapping(target = "active", constant = "true"),
        @Mapping(target = "businessKey", ignore = true),
        @Mapping(target = "tenantId", ignore = true),
        @Mapping(target = "metadata", source = "metadata"),
        @Mapping(target = "accountNumber", ignore = true),
        @Mapping(target = "status", constant = "PENDING_ACTIVATION"),
        @Mapping(target = "balance", source = "initialDeposit"),
        @Mapping(target = "availableBalance", source = "initialDeposit"),
        @Mapping(target = "ledgerBalance", source = "initialDeposit"),
        @Mapping(target = "accountType", expression = "java(mapAccountType(dto.getAccountType()))"),
        @Mapping(target = "accountCategory", expression = "java(mapAccountCategory(dto.getAccountCategory()))"),
        @Mapping(target = "tierLevel", expression = "java(mapTierLevel(dto.getTierLevel()))"),
        @Mapping(target = "kycLevel", expression = "java(mapKycLevel(dto.getKycLevel()))"),
        @Mapping(target = "dailySpent", constant = "0"),
        @Mapping(target = "monthlySpent", constant = "0"),
        @Mapping(target = "frozen", constant = "false"),
        @Mapping(target = "openedAt", expression = "java(java.time.LocalDateTime.now())"),
        @Mapping(target = "notificationPreferences", expression = "java(mapNotificationPreferences(dto.getNotificationPreferences()))"),
        @Mapping(target = "paymentMethods", ignore = true),
        @Mapping(target = "transactions", ignore = true),
        @Mapping(target = "subAccounts", ignore = true),
        @Mapping(target = "parentAccount", ignore = true),
        @Mapping(target = "tags", ignore = true)
    })
    Account createAccountFromRequest(CreateAccountRequestDTO dto);
    
    /**
     * Update Account entity from AccountResponseDTO
     */
    @Override
    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "version", ignore = true),
        @Mapping(target = "createdAt", ignore = true),
        @Mapping(target = "createdBy", ignore = true),
        @Mapping(target = "deleted", ignore = true),
        @Mapping(target = "deletedAt", ignore = true),
        @Mapping(target = "deletedBy", ignore = true),
        @Mapping(target = "accountNumber", ignore = true),
        @Mapping(target = "userId", ignore = true),
        @Mapping(target = "paymentMethods", ignore = true),
        @Mapping(target = "transactions", ignore = true),
        @Mapping(target = "subAccounts", ignore = true),
        @Mapping(target = "parentAccount", ignore = true)
    })
    void updateEntityFromDto(AccountResponseDTO dto, @MappingTarget Account entity);
    
    /**
     * Convert list of Account entities to list of AccountResponseDTOs
     */
    @Override
    List<AccountResponseDTO> toDtoList(List<Account> entities);
    
    /**
     * After mapping enhancement for DTO
     */
    @AfterMapping
    default void enhanceDto(@MappingTarget AccountResponseDTO dto) {
        if (dto != null) {
            dto.calculateDerivedFields();
        }
    }
    
    /**
     * After mapping enhancement for entity
     */
    @AfterMapping
    default void enhanceEntity(@MappingTarget Account entity) {
        if (entity != null && entity.getAccountNumber() == null) {
            // Account number will be generated in @PrePersist
        }
    }
    
    // Helper methods for custom mappings
    
    default java.math.BigDecimal calculateHoldAmount(Account entity) {
        if (entity.getBalance() != null && entity.getAvailableBalance() != null) {
            return entity.getBalance().subtract(entity.getAvailableBalance());
        }
        return java.math.BigDecimal.ZERO;
    }
    
    default Long calculateDaysSinceLastTransaction(Account entity) {
        if (entity.getLastTransactionAt() != null) {
            return java.time.Duration.between(
                entity.getLastTransactionAt(), 
                java.time.LocalDateTime.now()
            ).toDays();
        }
        log.warn("CRITICAL: Account lastTransactionAt is null - Cannot calculate days since last transaction");
        return 0L; // Default to 0 for safety
    }
    
    default Long calculateAccountAge(Account entity) {
        if (entity.getOpenedAt() != null) {
            return java.time.Duration.between(
                entity.getOpenedAt(), 
                java.time.LocalDateTime.now()
            ).toDays();
        }
        log.warn("CRITICAL: Account openedAt is null - Cannot calculate account age");
        return 0L; // Default to 0 for safety
    }
    
    default Account.AccountType mapAccountType(String type) {
        return type != null ? Account.AccountType.valueOf(type) : Account.AccountType.SAVINGS;
    }
    
    default Account.AccountCategory mapAccountCategory(String category) {
        return category != null ? Account.AccountCategory.valueOf(category) : Account.AccountCategory.PERSONAL;
    }
    
    default Account.TierLevel mapTierLevel(String level) {
        return level != null ? Account.TierLevel.valueOf(level) : Account.TierLevel.STANDARD;
    }
    
    default Account.KycLevel mapKycLevel(String level) {
        return level != null ? Account.KycLevel.valueOf(level) : Account.KycLevel.LEVEL_1;
    }
    
    default String mapNotificationPreferences(CreateAccountRequestDTO.NotificationPreferences prefs) {
        if (prefs == null) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(prefs);
        } catch (Exception e) {
            log.error("CRITICAL: Failed to serialize notification preferences - Account creation may fail", e);
            throw new RuntimeException("Failed to serialize notification preferences", e);
        }
    }
}

