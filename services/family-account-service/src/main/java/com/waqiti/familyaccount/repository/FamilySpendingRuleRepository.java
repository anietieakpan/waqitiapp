package com.waqiti.familyaccount.repository;

import com.waqiti.familyaccount.domain.FamilyAccount;
import com.waqiti.familyaccount.domain.FamilySpendingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Family Spending Rule Repository
 *
 * Data access layer for FamilySpendingRule entities
 *
 * @author Waqiti Family Account Team
 * @version 2.0.0
 * @since 2025-10-17
 */
@Repository
public interface FamilySpendingRuleRepository extends JpaRepository<FamilySpendingRule, Long> {

    /**
     * Find all rules for a family account
     */
    List<FamilySpendingRule> findByFamilyAccount(FamilyAccount familyAccount);

    /**
     * Find rules by rule type
     */
    List<FamilySpendingRule> findByFamilyAccountAndRuleType(FamilyAccount familyAccount, FamilySpendingRule.RuleType ruleType);

    /**
     * Find rules by rule scope
     */
    List<FamilySpendingRule> findByFamilyAccountAndRuleScope(FamilyAccount familyAccount, FamilySpendingRule.RuleScope ruleScope);

    /**
     * Find rules applicable to specific member
     */
    @Query("SELECT fsr FROM FamilySpendingRule fsr WHERE fsr.familyAccount = :familyAccount AND " +
           "(fsr.ruleScope = 'FAMILY_WIDE' OR " +
           "(fsr.ruleScope = 'SPECIFIC_MEMBER' AND fsr.targetMemberId = :memberId) OR " +
           "(fsr.ruleScope = 'AGE_GROUP' AND fsr.targetAgeGroup = :ageGroup))")
    List<FamilySpendingRule> findApplicableRules(
        @Param("familyAccount") FamilyAccount familyAccount,
        @Param("memberId") String memberId,
        @Param("ageGroup") FamilySpendingRule.AgeGroup ageGroup);

    /**
     * Find active rules (enabled)
     */
    @Query("SELECT fsr FROM FamilySpendingRule fsr WHERE fsr.familyAccount = :familyAccount AND fsr.isActive = true")
    List<FamilySpendingRule> findActiveRules(@Param("familyAccount") FamilyAccount familyAccount);

    /**
     * Find rule by name
     */
    Optional<FamilySpendingRule> findByFamilyAccountAndRuleName(FamilyAccount familyAccount, String ruleName);

    /**
     * Count active rules
     */
    long countByFamilyAccountAndIsActive(FamilyAccount familyAccount, Boolean isActive);
}
