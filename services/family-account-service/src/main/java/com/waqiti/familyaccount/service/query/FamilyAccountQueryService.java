package com.waqiti.familyaccount.service.query;

import com.waqiti.familyaccount.domain.FamilyAccount;
import com.waqiti.familyaccount.domain.FamilyMember;
import com.waqiti.familyaccount.dto.FamilyAccountDto;
import com.waqiti.familyaccount.exception.FamilyAccountNotFoundException;
import com.waqiti.familyaccount.exception.UnauthorizedAccessException;
import com.waqiti.familyaccount.repository.FamilyAccountRepository;
import com.waqiti.familyaccount.repository.FamilyMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Family Account Query Service
 *
 * Read-only query service following CQRS pattern
 * Handles all read operations for family accounts
 *
 * @author Waqiti Family Account Team
 * @version 2.0.0
 * @since 2025-10-17
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FamilyAccountQueryService {

    private final FamilyAccountRepository familyAccountRepository;
    private final FamilyMemberRepository familyMemberRepository;

    /**
     * Get family account by family ID
     *
     * @param familyId Family account ID
     * @param requestingUserId User requesting the information
     * @return Family account DTO
     * @throws FamilyAccountNotFoundException if account not found
     * @throws UnauthorizedAccessException if user not authorized
     */
    @Transactional(readOnly = true)
    public FamilyAccountDto getFamilyAccount(String familyId, String requestingUserId) {
        log.debug("Query: Getting family account: {} for user: {}", familyId, requestingUserId);

        FamilyAccount familyAccount = familyAccountRepository.findByFamilyId(familyId)
            .orElseThrow(() -> new FamilyAccountNotFoundException(familyId));

        validateUserAccess(familyAccount, requestingUserId);

        return mapToDto(familyAccount);
    }

    /**
     * Get all family accounts where user is a parent
     *
     * @param userId User ID
     * @return List of family account DTOs
     */
    @Transactional(readOnly = true)
    public List<FamilyAccountDto> getFamilyAccountsByParent(String userId) {
        log.debug("Query: Getting family accounts for parent: {}", userId);

        List<FamilyAccount> familyAccounts = familyAccountRepository.findByParentUserId(userId);

        return familyAccounts.stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());
    }

    /**
     * Get family account by primary parent user ID
     *
     * @param primaryParentUserId Primary parent user ID
     * @return Family account DTO or null if not found
     */
    @Transactional(readOnly = true)
    public FamilyAccountDto getFamilyAccountByPrimaryParent(String primaryParentUserId) {
        log.debug("Query: Getting family account for primary parent: {}", primaryParentUserId);

        return familyAccountRepository.findByPrimaryParentUserId(primaryParentUserId)
            .map(this::mapToDto)
            .orElse(null);
    }

    /**
     * Check if family account exists for primary parent
     *
     * @param primaryParentUserId Primary parent user ID
     * @return True if exists
     */
    @Transactional(readOnly = true)
    public boolean familyAccountExists(String primaryParentUserId) {
        return familyAccountRepository.existsByPrimaryParentUserId(primaryParentUserId);
    }

    /**
     * Get family accounts with auto savings enabled
     *
     * @return List of family account DTOs
     */
    @Transactional(readOnly = true)
    public List<FamilyAccountDto> getFamilyAccountsWithAutoSavings() {
        log.debug("Query: Getting family accounts with auto savings enabled");

        List<FamilyAccount> familyAccounts = familyAccountRepository.findByAutoSavingsEnabled(true);

        return familyAccounts.stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());
    }

    /**
     * Get family accounts by allowance day of month
     * Useful for scheduled allowance processing
     *
     * @param dayOfMonth Day of month (1-31)
     * @return List of family account DTOs
     */
    @Transactional(readOnly = true)
    public List<FamilyAccountDto> getFamilyAccountsByAllowanceDay(Integer dayOfMonth) {
        log.debug("Query: Getting family accounts for allowance day: {}", dayOfMonth);

        List<FamilyAccount> familyAccounts = familyAccountRepository.findByAllowanceDayOfMonth(dayOfMonth);

        return familyAccounts.stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());
    }

    /**
     * Count family accounts by primary parent
     *
     * @param primaryParentUserId Primary parent user ID
     * @return Count of family accounts
     */
    @Transactional(readOnly = true)
    public long countFamilyAccounts(String primaryParentUserId) {
        return familyAccountRepository.countByPrimaryParentUserId(primaryParentUserId);
    }

    /**
     * Validate user has access to family account
     */
    private void validateUserAccess(FamilyAccount familyAccount, String userId) {
        // Check if user is parent
        boolean isParent = familyAccount.getPrimaryParentUserId().equals(userId)
            || (familyAccount.getSecondaryParentUserId() != null
                && familyAccount.getSecondaryParentUserId().equals(userId));

        if (isParent) {
            return;
        }

        // Check if user is family member with view permission
        familyMemberRepository.findByFamilyAccountAndUserId(familyAccount, userId)
            .ifPresentOrElse(
                member -> {
                    if (!member.getCanViewFamilyAccount()) {
                        throw new UnauthorizedAccessException(userId, familyAccount.getFamilyId());
                    }
                },
                () -> {
                    throw new UnauthorizedAccessException(userId, familyAccount.getFamilyId());
                }
            );
    }

    /**
     * Map FamilyAccount entity to DTO
     */
    private FamilyAccountDto mapToDto(FamilyAccount familyAccount) {
        long memberCount = familyMemberRepository.countByFamilyAccountAndMemberStatus(
            familyAccount,
            FamilyMember.MemberStatus.ACTIVE
        );

        return FamilyAccountDto.builder()
            .familyId(familyAccount.getFamilyId())
            .familyName(familyAccount.getFamilyName())
            .primaryParentUserId(familyAccount.getPrimaryParentUserId())
            .secondaryParentUserId(familyAccount.getSecondaryParentUserId())
            .familyWalletId(familyAccount.getFamilyWalletId())
            .allowanceDayOfMonth(familyAccount.getAllowanceDayOfMonth())
            .autoSavingsPercentage(familyAccount.getAutoSavingsPercentage())
            .autoSavingsEnabled(familyAccount.getAutoSavingsEnabled())
            .defaultDailyLimit(familyAccount.getDefaultDailyLimit())
            .defaultWeeklyLimit(familyAccount.getDefaultWeeklyLimit())
            .defaultMonthlyLimit(familyAccount.getDefaultMonthlyLimit())
            .createdAt(familyAccount.getCreatedAt())
            .updatedAt(familyAccount.getUpdatedAt())
            .memberCount((int) memberCount)
            .build();
    }
}
