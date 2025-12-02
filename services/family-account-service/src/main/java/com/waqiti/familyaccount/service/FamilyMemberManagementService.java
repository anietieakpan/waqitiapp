package com.waqiti.familyaccount.service;

import com.waqiti.familyaccount.domain.FamilyAccount;
import com.waqiti.familyaccount.domain.FamilyMember;
import com.waqiti.familyaccount.dto.AddFamilyMemberRequest;
import com.waqiti.familyaccount.dto.FamilyMemberDto;
import com.waqiti.familyaccount.dto.UpdateFamilyMemberRequest;
import com.waqiti.familyaccount.exception.FamilyAccountException;
import com.waqiti.familyaccount.exception.FamilyAccountNotFoundException;
import com.waqiti.familyaccount.exception.FamilyMemberNotFoundException;
import com.waqiti.familyaccount.exception.UnauthorizedAccessException;
import com.waqiti.familyaccount.repository.FamilyAccountRepository;
import com.waqiti.familyaccount.repository.FamilyMemberRepository;
import com.waqiti.familyaccount.service.integration.FamilyExternalServiceFacade;
import com.waqiti.familyaccount.service.validation.FamilyValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Family Member Management Service
 *
 * Handles CRUD operations for family members
 * Follows Single Responsibility Principle
 *
 * @author Waqiti Family Account Team
 * @version 2.0.0
 * @since 2025-10-17
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FamilyMemberManagementService {

    private final FamilyAccountRepository familyAccountRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final FamilyValidationService familyValidationService;
    private final FamilyExternalServiceFacade externalServiceFacade;

    /**
     * Add family member to family account
     *
     * @param request Add family member request
     * @param requestingUserId User adding the member (must be parent)
     * @return Added family member DTO
     * @throws FamilyAccountNotFoundException if family account not found
     * @throws FamilyAccountException if validation fails
     * @throws UnauthorizedAccessException if user not authorized
     */
    @Transactional
    public FamilyMemberDto addFamilyMember(AddFamilyMemberRequest request, String requestingUserId) {
        log.info("Adding family member {} to family {}", request.getUserId(), request.getFamilyId());

        // Get family account
        FamilyAccount familyAccount = familyAccountRepository.findByFamilyId(request.getFamilyId())
            .orElseThrow(() -> new FamilyAccountNotFoundException(request.getFamilyId()));

        // Validate requesting user is parent
        validateParentAccess(familyAccount, requestingUserId);

        // Validate member doesn't already exist
        if (familyMemberRepository.existsByFamilyAccountAndUserId(familyAccount, request.getUserId())) {
            throw new FamilyAccountException("User " + request.getUserId() + " is already a member of this family");
        }

        // Validate user exists
        if (!externalServiceFacade.userExists(request.getUserId())) {
            throw new FamilyAccountException("User does not exist: " + request.getUserId());
        }

        // Validate member age
        familyValidationService.validateMemberAge(request.getDateOfBirth());

        // Validate spending limits if provided
        if (request.getDailySpendingLimit() != null || request.getWeeklySpendingLimit() != null
                || request.getMonthlySpendingLimit() != null) {
            familyValidationService.validateSpendingLimits(
                request.getDailySpendingLimit(),
                request.getWeeklySpendingLimit(),
                request.getMonthlySpendingLimit()
            );
        }

        // Create member wallet
        String memberWalletId = externalServiceFacade.createMemberWallet(
            request.getFamilyId(),
            request.getUserId(),
            familyAccount.getFamilyWalletId()
        );

        // Create family member entity
        FamilyMember familyMember = FamilyMember.builder()
            .familyAccount(familyAccount)
            .userId(request.getUserId())
            .memberRole(request.getMemberRole())
            .memberStatus(FamilyMember.MemberStatus.ACTIVE)
            .dateOfBirth(request.getDateOfBirth())
            .walletId(memberWalletId)
            .allowanceAmount(request.getAllowanceAmount())
            .allowanceFrequency(request.getAllowanceFrequency())
            .dailySpendingLimit(request.getDailySpendingLimit() != null
                ? request.getDailySpendingLimit() : familyAccount.getDefaultDailyLimit())
            .weeklySpendingLimit(request.getWeeklySpendingLimit() != null
                ? request.getWeeklySpendingLimit() : familyAccount.getDefaultWeeklyLimit())
            .monthlySpendingLimit(request.getMonthlySpendingLimit() != null
                ? request.getMonthlySpendingLimit() : familyAccount.getDefaultMonthlyLimit())
            .transactionApprovalRequired(request.getTransactionApprovalRequired() != null
                ? request.getTransactionApprovalRequired() : false)
            .canViewFamilyAccount(request.getCanViewFamilyAccount() != null
                ? request.getCanViewFamilyAccount() : true)
            .joinedAt(LocalDateTime.now())
            .build();

        familyMember = familyMemberRepository.save(familyMember);

        // Send notification
        externalServiceFacade.sendFamilyMemberAddedNotification(
            request.getFamilyId(),
            request.getUserId(),
            familyAccount.getPrimaryParentUserId()
        );

        log.info("Successfully added family member {} to family {}", request.getUserId(), request.getFamilyId());

        return mapToDto(familyMember);
    }

    /**
     * Get family member by user ID
     *
     * @param userId User ID
     * @param requestingUserId User requesting the information
     * @return Family member DTO
     * @throws FamilyMemberNotFoundException if member not found
     * @throws UnauthorizedAccessException if user not authorized
     */
    @Transactional(readOnly = true)
    public FamilyMemberDto getFamilyMember(String userId, String requestingUserId) {
        log.debug("Getting family member: {} for user: {}", userId, requestingUserId);

        FamilyMember familyMember = familyMemberRepository.findByUserId(userId)
            .orElseThrow(() -> new FamilyMemberNotFoundException(userId));

        // Validate access
        validateMemberAccess(familyMember.getFamilyAccount(), requestingUserId);

        return mapToDto(familyMember);
    }

    /**
     * Get all family members for a family account
     *
     * @param familyId Family account ID
     * @param requestingUserId User requesting the information
     * @return List of family member DTOs
     * @throws FamilyAccountNotFoundException if family account not found
     * @throws UnauthorizedAccessException if user not authorized
     */
    @Transactional(readOnly = true)
    public List<FamilyMemberDto> getFamilyMembers(String familyId, String requestingUserId) {
        log.debug("Getting family members for family: {}", familyId);

        FamilyAccount familyAccount = familyAccountRepository.findByFamilyId(familyId)
            .orElseThrow(() -> new FamilyAccountNotFoundException(familyId));

        // Validate access
        validateMemberAccess(familyAccount, requestingUserId);

        List<FamilyMember> members = familyMemberRepository.findByFamilyAccount(familyAccount);

        return members.stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());
    }

    /**
     * Update family member
     *
     * @param userId User ID
     * @param request Update request
     * @param requestingUserId User making the update (must be parent)
     * @return Updated family member DTO
     * @throws FamilyMemberNotFoundException if member not found
     * @throws UnauthorizedAccessException if user not authorized
     */
    @Transactional
    public FamilyMemberDto updateFamilyMember(String userId, UpdateFamilyMemberRequest request, String requestingUserId) {
        log.info("Updating family member: {} by user: {}", userId, requestingUserId);

        FamilyMember familyMember = familyMemberRepository.findByUserId(userId)
            .orElseThrow(() -> new FamilyMemberNotFoundException(userId));

        // Only parents can update members
        validateParentAccess(familyMember.getFamilyAccount(), requestingUserId);

        // Update fields if provided
        if (request.getMemberRole() != null) {
            familyMember.setMemberRole(request.getMemberRole());
        }

        if (request.getAllowanceAmount() != null) {
            familyMember.setAllowanceAmount(request.getAllowanceAmount());
        }

        if (request.getAllowanceFrequency() != null) {
            familyMember.setAllowanceFrequency(request.getAllowanceFrequency());
        }

        // Update spending limits with validation
        if (request.getDailySpendingLimit() != null || request.getWeeklySpendingLimit() != null
                || request.getMonthlySpendingLimit() != null) {
            familyValidationService.validateSpendingLimits(
                request.getDailySpendingLimit() != null ? request.getDailySpendingLimit() : familyMember.getDailySpendingLimit(),
                request.getWeeklySpendingLimit() != null ? request.getWeeklySpendingLimit() : familyMember.getWeeklySpendingLimit(),
                request.getMonthlySpendingLimit() != null ? request.getMonthlySpendingLimit() : familyMember.getMonthlySpendingLimit()
            );

            if (request.getDailySpendingLimit() != null) {
                familyMember.setDailySpendingLimit(request.getDailySpendingLimit());
            }
            if (request.getWeeklySpendingLimit() != null) {
                familyMember.setWeeklySpendingLimit(request.getWeeklySpendingLimit());
            }
            if (request.getMonthlySpendingLimit() != null) {
                familyMember.setMonthlySpendingLimit(request.getMonthlySpendingLimit());
            }
        }

        if (request.getTransactionApprovalRequired() != null) {
            familyMember.setTransactionApprovalRequired(request.getTransactionApprovalRequired());
        }

        if (request.getCanViewFamilyAccount() != null) {
            familyMember.setCanViewFamilyAccount(request.getCanViewFamilyAccount());
        }

        familyMember.setUpdatedAt(LocalDateTime.now());
        familyMember = familyMemberRepository.save(familyMember);

        log.info("Successfully updated family member: {}", userId);

        return mapToDto(familyMember);
    }

    /**
     * Remove family member from family account
     *
     * @param userId User ID
     * @param requestingUserId User requesting removal (must be parent)
     * @throws FamilyMemberNotFoundException if member not found
     * @throws UnauthorizedAccessException if user not authorized
     */
    @Transactional
    public void removeFamilyMember(String userId, String requestingUserId) {
        log.warn("Removing family member: {} by user: {}", userId, requestingUserId);

        FamilyMember familyMember = familyMemberRepository.findByUserId(userId)
            .orElseThrow(() -> new FamilyMemberNotFoundException(userId));

        FamilyAccount familyAccount = familyMember.getFamilyAccount();

        // Only parents can remove members
        validateParentAccess(familyAccount, requestingUserId);

        // Cannot remove parent from family
        if (familyMember.getMemberRole() == FamilyMember.MemberRole.PARENT) {
            throw new FamilyAccountException("Cannot remove parent from family account");
        }

        // Soft delete - mark as inactive
        familyMember.setMemberStatus(FamilyMember.MemberStatus.INACTIVE);
        familyMember.setUpdatedAt(LocalDateTime.now());
        familyMemberRepository.save(familyMember);

        // Send notification
        externalServiceFacade.sendFamilyMemberRemovedNotification(
            familyAccount.getFamilyId(),
            userId,
            requestingUserId
        );

        log.info("Successfully removed family member: {}", userId);
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
     * Validate user has access to view member information
     */
    private void validateMemberAccess(FamilyAccount familyAccount, String userId) {
        // Parents always have access
        boolean isParent = familyAccount.getPrimaryParentUserId().equals(userId)
            || (familyAccount.getSecondaryParentUserId() != null
                && familyAccount.getSecondaryParentUserId().equals(userId));

        if (isParent) {
            return;
        }

        // Members can view if they have permission
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
     * Map FamilyMember entity to DTO
     */
    private FamilyMemberDto mapToDto(FamilyMember familyMember) {
        int age = Period.between(familyMember.getDateOfBirth(), LocalDate.now()).getYears();

        return FamilyMemberDto.builder()
            .userId(familyMember.getUserId())
            .familyId(familyMember.getFamilyAccount().getFamilyId())
            .memberRole(familyMember.getMemberRole())
            .memberStatus(familyMember.getMemberStatus())
            .dateOfBirth(familyMember.getDateOfBirth())
            .age(age)
            .allowanceAmount(familyMember.getAllowanceAmount())
            .allowanceFrequency(familyMember.getAllowanceFrequency())
            .lastAllowanceDate(familyMember.getLastAllowanceDate())
            .dailySpendingLimit(familyMember.getDailySpendingLimit())
            .weeklySpendingLimit(familyMember.getWeeklySpendingLimit())
            .monthlySpendingLimit(familyMember.getMonthlySpendingLimit())
            .transactionApprovalRequired(familyMember.getTransactionApprovalRequired())
            .canViewFamilyAccount(familyMember.getCanViewFamilyAccount())
            .joinedAt(familyMember.getJoinedAt())
            .updatedAt(familyMember.getUpdatedAt())
            .build();
    }
}
