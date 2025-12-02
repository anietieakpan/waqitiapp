package com.waqiti.familyaccount.service;

import com.waqiti.familyaccount.domain.FamilyAccount;
import com.waqiti.familyaccount.dto.CreateFamilyAccountRequest;
import com.waqiti.familyaccount.dto.FamilyAccountDto;
import com.waqiti.familyaccount.dto.UpdateFamilyAccountRequest;
import com.waqiti.familyaccount.exception.FamilyAccountException;
import com.waqiti.familyaccount.exception.FamilyAccountNotFoundException;
import com.waqiti.familyaccount.exception.UnauthorizedAccessException;
import com.waqiti.familyaccount.repository.FamilyAccountRepository;
import com.waqiti.familyaccount.repository.FamilyMemberRepository;
import com.waqiti.familyaccount.service.integration.FamilyExternalServiceFacade;
import com.waqiti.familyaccount.service.validation.FamilyValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Family Account Management Service
 *
 * Handles CRUD operations for family accounts
 * Follows Single Responsibility Principle
 *
 * @author Waqiti Family Account Team
 * @version 2.0.0
 * @since 2025-10-17
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FamilyAccountManagementService {

    private final FamilyAccountRepository familyAccountRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final FamilyValidationService familyValidationService;
    private final FamilyExternalServiceFacade externalServiceFacade;

    /**
     * Create a new family account
     *
     * @param request Create family account request
     * @param requestingUserId User creating the account
     * @return Created family account DTO
     * @throws FamilyAccountException if validation fails
     */
    @Transactional
    public FamilyAccountDto createFamilyAccount(CreateFamilyAccountRequest request, String requestingUserId) {
        log.info("Creating family account for primary parent: {}", request.getPrimaryParentUserId());

        // Validate requesting user matches primary parent
        if (!requestingUserId.equals(request.getPrimaryParentUserId())) {
            throw new UnauthorizedAccessException("Only the primary parent can create their family account");
        }

        // Validate parent eligibility
        familyValidationService.validateParentEligibility(request.getPrimaryParentUserId());

        // Check if family account already exists for this parent
        if (familyAccountRepository.existsByPrimaryParentUserId(request.getPrimaryParentUserId())) {
            throw new FamilyAccountException("Family account already exists for user: " + request.getPrimaryParentUserId());
        }

        // Validate secondary parent if provided
        if (request.getSecondaryParentUserId() != null) {
            familyValidationService.validateParentEligibility(request.getSecondaryParentUserId());
        }

        // Validate spending limits
        if (request.getDefaultDailyLimit() != null || request.getDefaultWeeklyLimit() != null
                || request.getDefaultMonthlyLimit() != null) {
            familyValidationService.validateSpendingLimits(
                request.getDefaultDailyLimit(),
                request.getDefaultWeeklyLimit(),
                request.getDefaultMonthlyLimit()
            );
        }

        // Create family wallet through external service
        String familyId = "FAM-" + UUID.randomUUID().toString();
        String familyWalletId = externalServiceFacade.createFamilyWallet(familyId, request.getPrimaryParentUserId());

        // Create family account entity
        FamilyAccount familyAccount = FamilyAccount.builder()
            .familyId(familyId)
            .familyName(request.getFamilyName())
            .primaryParentUserId(request.getPrimaryParentUserId())
            .secondaryParentUserId(request.getSecondaryParentUserId())
            .familyWalletId(familyWalletId)
            .allowanceDayOfMonth(request.getAllowanceDayOfMonth())
            .autoSavingsPercentage(request.getAutoSavingsPercentage())
            .autoSavingsEnabled(request.getAutoSavingsEnabled() != null ? request.getAutoSavingsEnabled() : false)
            .defaultDailyLimit(request.getDefaultDailyLimit())
            .defaultWeeklyLimit(request.getDefaultWeeklyLimit())
            .defaultMonthlyLimit(request.getDefaultMonthlyLimit())
            .createdAt(LocalDateTime.now())
            .build();

        familyAccount = familyAccountRepository.save(familyAccount);

        // Send notification (non-blocking)
        externalServiceFacade.sendFamilyAccountCreatedNotification(
            familyId,
            request.getPrimaryParentUserId(),
            request.getFamilyName()
        );

        log.info("Successfully created family account: {}", familyId);

        return mapToDto(familyAccount);
    }

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
        log.debug("Getting family account: {} for user: {}", familyId, requestingUserId);

        FamilyAccount familyAccount = familyAccountRepository.findByFamilyId(familyId)
            .orElseThrow(() -> new FamilyAccountNotFoundException(familyId));

        // Validate user has access to this family account
        validateUserAccess(familyAccount, requestingUserId);

        return mapToDto(familyAccount);
    }

    /**
     * Update family account
     *
     * @param familyId Family account ID
     * @param request Update request
     * @param requestingUserId User making the update
     * @return Updated family account DTO
     * @throws FamilyAccountNotFoundException if account not found
     * @throws UnauthorizedAccessException if user not authorized
     */
    @Transactional
    public FamilyAccountDto updateFamilyAccount(String familyId, UpdateFamilyAccountRequest request, String requestingUserId) {
        log.info("Updating family account: {} by user: {}", familyId, requestingUserId);

        FamilyAccount familyAccount = familyAccountRepository.findByFamilyId(familyId)
            .orElseThrow(() -> new FamilyAccountNotFoundException(familyId));

        // Only parents can update family account
        validateParentAccess(familyAccount, requestingUserId);

        // Update fields if provided
        if (request.getFamilyName() != null) {
            familyAccount.setFamilyName(request.getFamilyName());
        }

        if (request.getSecondaryParentUserId() != null) {
            familyValidationService.validateParentEligibility(request.getSecondaryParentUserId());
            familyAccount.setSecondaryParentUserId(request.getSecondaryParentUserId());
        }

        if (request.getAllowanceDayOfMonth() != null) {
            familyAccount.setAllowanceDayOfMonth(request.getAllowanceDayOfMonth());
        }

        if (request.getAutoSavingsPercentage() != null) {
            familyAccount.setAutoSavingsPercentage(request.getAutoSavingsPercentage());
        }

        if (request.getAutoSavingsEnabled() != null) {
            familyAccount.setAutoSavingsEnabled(request.getAutoSavingsEnabled());
        }

        // Update spending limits with validation
        if (request.getDefaultDailyLimit() != null || request.getDefaultWeeklyLimit() != null
                || request.getDefaultMonthlyLimit() != null) {
            familyValidationService.validateSpendingLimits(
                request.getDefaultDailyLimit() != null ? request.getDefaultDailyLimit() : familyAccount.getDefaultDailyLimit(),
                request.getDefaultWeeklyLimit() != null ? request.getDefaultWeeklyLimit() : familyAccount.getDefaultWeeklyLimit(),
                request.getDefaultMonthlyLimit() != null ? request.getDefaultMonthlyLimit() : familyAccount.getDefaultMonthlyLimit()
            );

            if (request.getDefaultDailyLimit() != null) {
                familyAccount.setDefaultDailyLimit(request.getDefaultDailyLimit());
            }
            if (request.getDefaultWeeklyLimit() != null) {
                familyAccount.setDefaultWeeklyLimit(request.getDefaultWeeklyLimit());
            }
            if (request.getDefaultMonthlyLimit() != null) {
                familyAccount.setDefaultMonthlyLimit(request.getDefaultMonthlyLimit());
            }
        }

        familyAccount.setUpdatedAt(LocalDateTime.now());
        familyAccount = familyAccountRepository.save(familyAccount);

        log.info("Successfully updated family account: {}", familyId);

        return mapToDto(familyAccount);
    }

    /**
     * Delete family account (soft delete - mark as inactive)
     *
     * @param familyId Family account ID
     * @param requestingUserId User requesting deletion
     * @throws FamilyAccountNotFoundException if account not found
     * @throws UnauthorizedAccessException if user not authorized
     */
    @Transactional
    public void deleteFamilyAccount(String familyId, String requestingUserId) {
        log.warn("Deleting family account: {} by user: {}", familyId, requestingUserId);

        FamilyAccount familyAccount = familyAccountRepository.findByFamilyId(familyId)
            .orElseThrow(() -> new FamilyAccountNotFoundException(familyId));

        // Only primary parent can delete family account
        if (!familyAccount.getPrimaryParentUserId().equals(requestingUserId)) {
            throw new UnauthorizedAccessException("Only primary parent can delete family account");
        }

        // Delete family account (hard delete for now, consider soft delete in production)
        familyAccountRepository.delete(familyAccount);

        // Send notification
        externalServiceFacade.sendFamilyAccountDeletedNotification(familyId, requestingUserId);

        log.info("Successfully deleted family account: {}", familyId);
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
     * Validate user is a parent of family account
     */
    private void validateParentAccess(FamilyAccount familyAccount, String userId) {
        boolean isParent = familyAccount.getPrimaryParentUserId().equals(userId)
            || (familyAccount.getSecondaryParentUserId() != null
                && familyAccount.getSecondaryParentUserId().equals(userId));

        if (!isParent) {
            throw new UnauthorizedAccessException("Only parents can perform this action");
        }
    }

    /**
     * Map FamilyAccount entity to DTO
     */
    private FamilyAccountDto mapToDto(FamilyAccount familyAccount) {
        long memberCount = familyMemberRepository.countByFamilyAccountAndMemberStatus(
            familyAccount,
            com.waqiti.familyaccount.domain.FamilyMember.MemberStatus.ACTIVE
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
