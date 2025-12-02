package com.waqiti.familyaccount.repository;

import com.waqiti.familyaccount.domain.FamilyAccount;
import com.waqiti.familyaccount.domain.FamilyMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Family Member Repository
 *
 * Data access layer for FamilyMember entities
 *
 * @author Waqiti Family Account Team
 * @version 2.0.0
 * @since 2025-10-17
 */
@Repository
public interface FamilyMemberRepository extends JpaRepository<FamilyMember, Long> {

    /**
     * Find family member by user ID
     */
    Optional<FamilyMember> findByUserId(String userId);

    /**
     * Find family member by family account and user ID
     */
    Optional<FamilyMember> findByFamilyAccountAndUserId(FamilyAccount familyAccount, String userId);

    /**
     * Find all members of a family account
     */
    List<FamilyMember> findByFamilyAccount(FamilyAccount familyAccount);

    /**
     * Find members by family account and status
     */
    List<FamilyMember> findByFamilyAccountAndMemberStatus(FamilyAccount familyAccount, FamilyMember.MemberStatus status);

    /**
     * Check if member exists in family account
     */
    boolean existsByFamilyAccountAndUserId(FamilyAccount familyAccount, String userId);

    /**
     * Find members with allowance amount greater than specified
     */
    List<FamilyMember> findByFamilyAccountAndAllowanceAmountGreaterThan(FamilyAccount familyAccount, BigDecimal amount);

    /**
     * Find members by member role
     */
    List<FamilyMember> findByFamilyAccountAndMemberRole(FamilyAccount familyAccount, FamilyMember.MemberRole role);

    /**
     * Find members by age group
     */
    @Query("SELECT fm FROM FamilyMember fm WHERE fm.familyAccount = :familyAccount AND " +
           "FUNCTION('YEAR', CURRENT_DATE) - FUNCTION('YEAR', fm.dateOfBirth) BETWEEN :minAge AND :maxAge")
    List<FamilyMember> findByFamilyAccountAndAgeRange(
        @Param("familyAccount") FamilyAccount familyAccount,
        @Param("minAge") int minAge,
        @Param("maxAge") int maxAge);

    /**
     * Find members with last allowance date before specified date
     */
    @Query("SELECT fm FROM FamilyMember fm WHERE fm.familyAccount = :familyAccount AND " +
           "(fm.lastAllowanceDate IS NULL OR fm.lastAllowanceDate < :date)")
    List<FamilyMember> findMembersDueForAllowance(
        @Param("familyAccount") FamilyAccount familyAccount,
        @Param("date") LocalDate date);

    /**
     * Count active members in family account
     */
    long countByFamilyAccountAndMemberStatus(FamilyAccount familyAccount, FamilyMember.MemberStatus status);

    /**
     * Find members with transaction approval required
     */
    List<FamilyMember> findByFamilyAccountAndTransactionApprovalRequired(FamilyAccount familyAccount, Boolean required);
}
