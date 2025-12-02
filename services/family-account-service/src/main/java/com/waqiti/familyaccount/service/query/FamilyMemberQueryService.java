package com.waqiti.familyaccount.service.query;

import com.waqiti.familyaccount.domain.FamilyAccount;
import com.waqiti.familyaccount.domain.FamilyMember;
import com.waqiti.familyaccount.dto.FamilyMemberDto;
import com.waqiti.familyaccount.exception.FamilyAccountNotFoundException;
import com.waqiti.familyaccount.exception.FamilyMemberNotFoundException;
import com.waqiti.familyaccount.exception.UnauthorizedAccessException;
import com.waqiti.familyaccount.repository.FamilyAccountRepository;
import com.waqiti.familyaccount.repository.FamilyMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Family Member Query Service
 *
 * Read-only query service following CQRS pattern
 * Handles all read operations for family members
 *
 * @author Waqiti Family Account Team
 * @version 2.0.0
 * @since 2025-10-17
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FamilyMemberQueryService {

    private final FamilyAccountRepository familyAccountRepository;
    private final FamilyMemberRepository familyMemberRepository;

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
        log.debug("Query: Getting family member: {} for user: {}", userId, requestingUserId);

        FamilyMember familyMember = familyMemberRepository.findByUserId(userId)
            .orElseThrow(() -> new FamilyMemberNotFoundException(userId));

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
        log.debug("Query: Getting family members for family: {}", familyId);

        FamilyAccount familyAccount = familyAccountRepository.findByFamilyId(familyId)
            .orElseThrow(() -> new FamilyAccountNotFoundException(familyId));

        validateMemberAccess(familyAccount, requestingUserId);

        List<FamilyMember> members = familyMemberRepository.findByFamilyAccount(familyAccount);

        return members.stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());
    }

    /**
     * Get active family members for a family account
     *
     * @param familyId Family account ID
     * @param requestingUserId User requesting the information
     * @return List of active family member DTOs
     */
    @Transactional(readOnly = true)
    public List<FamilyMemberDto> getActiveFamilyMembers(String familyId, String requestingUserId) {
        log.debug("Query: Getting active family members for family: {}", familyId);

        FamilyAccount familyAccount = familyAccountRepository.findByFamilyId(familyId)
            .orElseThrow(() -> new FamilyAccountNotFoundException(familyId));

        validateMemberAccess(familyAccount, requestingUserId);

        List<FamilyMember> members = familyMemberRepository.findByFamilyAccountAndMemberStatus(
            familyAccount,
            FamilyMember.MemberStatus.ACTIVE
        );

        return members.stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());
    }

    /**
     * Get family members by role
     *
     * @param familyId Family account ID
     * @param role Member role
     * @param requestingUserId User requesting the information
     * @return List of family member DTOs
     */
    @Transactional(readOnly = true)
    public List<FamilyMemberDto> getFamilyMembersByRole(
            String familyId,
            FamilyMember.MemberRole role,
            String requestingUserId) {

        log.debug("Query: Getting family members by role: {} for family: {}", role, familyId);

        FamilyAccount familyAccount = familyAccountRepository.findByFamilyId(familyId)
            .orElseThrow(() -> new FamilyAccountNotFoundException(familyId));

        validateParentAccess(familyAccount, requestingUserId);

        List<FamilyMember> members = familyMemberRepository.findByFamilyAccountAndMemberRole(familyAccount, role);

        return members.stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());
    }

    /**
     * Get family members with allowance greater than specified amount
     *
     * @param familyId Family account ID
     * @param amount Minimum allowance amount
     * @param requestingUserId User requesting the information
     * @return List of family member DTOs
     */
    @Transactional(readOnly = true)
    public List<FamilyMemberDto> getFamilyMembersWithAllowanceGreaterThan(
            String familyId,
            BigDecimal amount,
            String requestingUserId) {

        log.debug("Query: Getting family members with allowance > {} for family: {}", amount, familyId);

        FamilyAccount familyAccount = familyAccountRepository.findByFamilyId(familyId)
            .orElseThrow(() -> new FamilyAccountNotFoundException(familyId));

        validateParentAccess(familyAccount, requestingUserId);

        List<FamilyMember> members = familyMemberRepository.findByFamilyAccountAndAllowanceAmountGreaterThan(
            familyAccount,
            amount
        );

        return members.stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());
    }

    /**
     * Get family members by age range
     *
     * @param familyId Family account ID
     * @param minAge Minimum age
     * @param maxAge Maximum age
     * @param requestingUserId User requesting the information
     * @return List of family member DTOs
     */
    @Transactional(readOnly = true)
    public List<FamilyMemberDto> getFamilyMembersByAgeRange(
            String familyId,
            int minAge,
            int maxAge,
            String requestingUserId) {

        log.debug("Query: Getting family members with age {}-{} for family: {}", minAge, maxAge, familyId);

        FamilyAccount familyAccount = familyAccountRepository.findByFamilyId(familyId)
            .orElseThrow(() -> new FamilyAccountNotFoundException(familyId));

        validateParentAccess(familyAccount, requestingUserId);

        List<FamilyMember> members = familyMemberRepository.findByFamilyAccountAndAgeRange(
            familyAccount,
            minAge,
            maxAge
        );

        return members.stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());
    }

    /**
     * Get family members due for allowance
     *
     * @param familyId Family account ID
     * @param date Date to check against
     * @param requestingUserId User requesting the information
     * @return List of family member DTOs
     */
    @Transactional(readOnly = true)
    public List<FamilyMemberDto> getFamilyMembersDueForAllowance(
            String familyId,
            LocalDate date,
            String requestingUserId) {

        log.debug("Query: Getting family members due for allowance for family: {}", familyId);

        FamilyAccount familyAccount = familyAccountRepository.findByFamilyId(familyId)
            .orElseThrow(() -> new FamilyAccountNotFoundException(familyId));

        validateParentAccess(familyAccount, requestingUserId);

        List<FamilyMember> members = familyMemberRepository.findMembersDueForAllowance(familyAccount, date);

        return members.stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());
    }

    /**
     * Get family members with transaction approval required
     *
     * @param familyId Family account ID
     * @param requestingUserId User requesting the information
     * @return List of family member DTOs
     */
    @Transactional(readOnly = true)
    public List<FamilyMemberDto> getFamilyMembersRequiringApproval(String familyId, String requestingUserId) {
        log.debug("Query: Getting family members requiring approval for family: {}", familyId);

        FamilyAccount familyAccount = familyAccountRepository.findByFamilyId(familyId)
            .orElseThrow(() -> new FamilyAccountNotFoundException(familyId));

        validateParentAccess(familyAccount, requestingUserId);

        List<FamilyMember> members = familyMemberRepository.findByFamilyAccountAndTransactionApprovalRequired(
            familyAccount,
            true
        );

        return members.stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());
    }

    /**
     * Count active family members
     *
     * @param familyId Family account ID
     * @return Count of active members
     */
    @Transactional(readOnly = true)
    public long countActiveFamilyMembers(String familyId) {
        FamilyAccount familyAccount = familyAccountRepository.findByFamilyId(familyId)
            .orElseThrow(() -> new FamilyAccountNotFoundException(familyId));

        return familyMemberRepository.countByFamilyAccountAndMemberStatus(
            familyAccount,
            FamilyMember.MemberStatus.ACTIVE
        );
    }

    /**
     * Check if user is a family member
     *
     * @param familyId Family account ID
     * @param userId User ID
     * @return True if user is a member
     */
    @Transactional(readOnly = true)
    public boolean isFamilyMember(String familyId, String userId) {
        FamilyAccount familyAccount = familyAccountRepository.findByFamilyId(familyId)
            .orElseThrow(() -> new FamilyAccountNotFoundException(familyId));

        return familyMemberRepository.existsByFamilyAccountAndUserId(familyAccount, userId);
    }

    /**
     * Validate user is a parent
     */
    private void validateParentAccess(FamilyAccount familyAccount, String userId) {
        boolean isParent = familyAccount.getPrimaryParentUserId().equals(userId)
            || (familyAccount.getSecondaryParentUserId() != null
                && familyAccount.getSecondaryParentUserId().equals(userId));

        if (!isParent) {
            throw new UnauthorizedAccessException("Only parents can access this information");
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
