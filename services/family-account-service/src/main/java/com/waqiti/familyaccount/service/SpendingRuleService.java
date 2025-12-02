package com.waqiti.familyaccount.service;

import com.waqiti.familyaccount.domain.FamilyAccount;
import com.waqiti.familyaccount.domain.FamilyMember;
import com.waqiti.familyaccount.domain.FamilySpendingRule;
import com.waqiti.familyaccount.dto.CreateSpendingRuleRequest;
import com.waqiti.familyaccount.exception.FamilyAccountException;
import com.waqiti.familyaccount.exception.FamilyAccountNotFoundException;
import com.waqiti.familyaccount.exception.UnauthorizedAccessException;
import com.waqiti.familyaccount.repository.FamilyAccountRepository;
import com.waqiti.familyaccount.repository.FamilySpendingRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Spending Rule Service
 *
 * Manages spending rules for family accounts
 * Enforces merchant restrictions, time restrictions, amount limits
 *
 * @author Waqiti Family Account Team
 * @version 2.0.0
 * @since 2025-10-17
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SpendingRuleService {

    private final FamilyAccountRepository familyAccountRepository;
    private final FamilySpendingRuleRepository spendingRuleRepository;

    /**
     * Create spending rule
     *
     * @param request Create spending rule request
     * @param requestingUserId User creating rule (must be parent)
     * @return Created spending rule
     * @throws FamilyAccountNotFoundException if family account not found
     * @throws UnauthorizedAccessException if user not authorized
     */
    @Transactional
    public FamilySpendingRule createSpendingRule(CreateSpendingRuleRequest request, String requestingUserId) {
        log.info("Creating spending rule for family: {}", request.getFamilyId());

        FamilyAccount familyAccount = familyAccountRepository.findByFamilyId(request.getFamilyId())
            .orElseThrow(() -> new FamilyAccountNotFoundException(request.getFamilyId()));

        // Only parents can create rules
        validateParentAccess(familyAccount, requestingUserId);

        // Validate rule doesn't already exist with same name
        if (spendingRuleRepository.findByFamilyAccountAndRuleName(familyAccount, request.getRuleName()).isPresent()) {
            throw new FamilyAccountException("Rule with name already exists: " + request.getRuleName());
        }

        // Validate rule configuration
        validateRuleConfiguration(request);

        FamilySpendingRule rule = FamilySpendingRule.builder()
            .familyAccount(familyAccount)
            .ruleName(request.getRuleName())
            .ruleType(request.getRuleType())
            .ruleScope(request.getRuleScope())
            .targetMemberId(request.getTargetMemberId())
            .targetAgeGroup(request.getTargetAgeGroup())
            .restrictedMerchantCategory(request.getRestrictedMerchantCategory())
            .maxTransactionAmount(request.getMaxTransactionAmount())
            .timeRestrictionStart(request.getTimeRestrictionStart())
            .timeRestrictionEnd(request.getTimeRestrictionEnd())
            .requiresApproval(request.getRequiresApproval() != null ? request.getRequiresApproval() : false)
            .isActive(request.getIsActive() != null ? request.getIsActive() : true)
            .createdAt(LocalDateTime.now())
            .build();

        rule = spendingRuleRepository.save(rule);

        log.info("Successfully created spending rule: {}", rule.getId());

        return rule;
    }

    /**
     * Get all spending rules for family account
     *
     * @param familyId Family account ID
     * @param requestingUserId User requesting rules
     * @return List of spending rules
     * @throws FamilyAccountNotFoundException if family account not found
     * @throws UnauthorizedAccessException if user not authorized
     */
    @Transactional(readOnly = true)
    public List<FamilySpendingRule> getFamilySpendingRules(String familyId, String requestingUserId) {
        log.debug("Getting spending rules for family: {}", familyId);

        FamilyAccount familyAccount = familyAccountRepository.findByFamilyId(familyId)
            .orElseThrow(() -> new FamilyAccountNotFoundException(familyId));

        // Only parents can view rules
        validateParentAccess(familyAccount, requestingUserId);

        return spendingRuleRepository.findByFamilyAccount(familyAccount);
    }

    /**
     * Get active spending rules applicable to a family member
     *
     * @param familyMember Family member
     * @return List of applicable rules
     */
    @Transactional(readOnly = true)
    public List<FamilySpendingRule> getApplicableRules(FamilyMember familyMember) {
        FamilySpendingRule.AgeGroup ageGroup = determineAgeGroup(familyMember);

        List<FamilySpendingRule> rules = spendingRuleRepository.findApplicableRules(
            familyMember.getFamilyAccount(),
            familyMember.getUserId(),
            ageGroup
        );

        // Filter only active rules
        return rules.stream()
            .filter(FamilySpendingRule::getIsActive)
            .toList();
    }

    /**
     * Check if transaction violates any spending rules
     *
     * @param familyMember Family member
     * @param merchantCategory Merchant category
     * @param transactionAmount Transaction amount
     * @param transactionTime Transaction time
     * @return Violated rule or null if no violations
     */
    @Transactional(readOnly = true)
    public FamilySpendingRule checkRuleViolation(
            FamilyMember familyMember,
            String merchantCategory,
            BigDecimal transactionAmount,
            LocalDateTime transactionTime) {

        List<FamilySpendingRule> applicableRules = getApplicableRules(familyMember);

        for (FamilySpendingRule rule : applicableRules) {
            if (violatesRule(rule, merchantCategory, transactionAmount, transactionTime)) {
                log.warn("Transaction violates spending rule: {} for member: {}",
                    rule.getRuleName(), familyMember.getUserId());
                return rule;
            }
        }

        // No rule violations found - transaction is allowed
        log.debug("No spending rule violations found for member: {}", familyMember.getUserId());
        return null; // null indicates no violations (transaction allowed)
    }

    /**
     * Check if transaction violates a specific rule
     */
    private boolean violatesRule(
            FamilySpendingRule rule,
            String merchantCategory,
            BigDecimal transactionAmount,
            LocalDateTime transactionTime) {

        switch (rule.getRuleType()) {
            case MERCHANT_RESTRICTION:
                if (rule.getRestrictedMerchantCategory() != null
                        && rule.getRestrictedMerchantCategory().equalsIgnoreCase(merchantCategory)) {
                    return true;
                }
                break;

            case AMOUNT_LIMIT:
                if (rule.getMaxTransactionAmount() != null
                        && transactionAmount.compareTo(rule.getMaxTransactionAmount()) > 0) {
                    return true;
                }
                break;

            case TIME_RESTRICTION:
                if (rule.getTimeRestrictionStart() != null && rule.getTimeRestrictionEnd() != null) {
                    LocalTime txTime = transactionTime.toLocalTime();
                    LocalTime startTime = LocalTime.parse(rule.getTimeRestrictionStart());
                    LocalTime endTime = LocalTime.parse(rule.getTimeRestrictionEnd());

                    // Check if time is outside allowed window
                    if (txTime.isBefore(startTime) || txTime.isAfter(endTime)) {
                        return true;
                    }
                }
                break;

            case APPROVAL_REQUIRED:
                // This doesn't block the transaction, just requires approval
                return false;

            default:
                return false;
        }

        return false;
    }

    /**
     * Update spending rule
     *
     * @param ruleId Rule ID
     * @param isActive Active status
     * @param requestingUserId User updating rule
     * @return Updated rule
     */
    @Transactional
    public FamilySpendingRule updateSpendingRule(Long ruleId, Boolean isActive, String requestingUserId) {
        log.info("Updating spending rule: {}", ruleId);

        FamilySpendingRule rule = spendingRuleRepository.findById(ruleId)
            .orElseThrow(() -> new FamilyAccountException("Spending rule not found: " + ruleId));

        // Only parents can update rules
        validateParentAccess(rule.getFamilyAccount(), requestingUserId);

        if (isActive != null) {
            rule.setIsActive(isActive);
        }

        rule.setUpdatedAt(LocalDateTime.now());
        rule = spendingRuleRepository.save(rule);

        log.info("Successfully updated spending rule: {}", ruleId);

        return rule;
    }

    /**
     * Delete spending rule
     *
     * @param ruleId Rule ID
     * @param requestingUserId User deleting rule
     */
    @Transactional
    public void deleteSpendingRule(Long ruleId, String requestingUserId) {
        log.warn("Deleting spending rule: {}", ruleId);

        FamilySpendingRule rule = spendingRuleRepository.findById(ruleId)
            .orElseThrow(() -> new FamilyAccountException("Spending rule not found: " + ruleId));

        // Only parents can delete rules
        validateParentAccess(rule.getFamilyAccount(), requestingUserId);

        spendingRuleRepository.delete(rule);

        log.info("Successfully deleted spending rule: {}", ruleId);
    }

    /**
     * Determine age group for family member
     */
    private FamilySpendingRule.AgeGroup determineAgeGroup(FamilyMember familyMember) {
        int age = java.time.Period.between(familyMember.getDateOfBirth(), java.time.LocalDate.now()).getYears();

        if (age < 13) {
            return FamilySpendingRule.AgeGroup.CHILD;
        } else if (age < 18) {
            return FamilySpendingRule.AgeGroup.TEEN;
        } else {
            return FamilySpendingRule.AgeGroup.ADULT;
        }
    }

    /**
     * Validate rule configuration
     */
    private void validateRuleConfiguration(CreateSpendingRuleRequest request) {
        if (request.getRuleScope() == FamilySpendingRule.RuleScope.SPECIFIC_MEMBER
                && request.getTargetMemberId() == null) {
            throw new FamilyAccountException("Target member ID required for SPECIFIC_MEMBER scope");
        }

        if (request.getRuleScope() == FamilySpendingRule.RuleScope.AGE_GROUP
                && request.getTargetAgeGroup() == null) {
            throw new FamilyAccountException("Target age group required for AGE_GROUP scope");
        }

        if (request.getRuleType() == FamilySpendingRule.RuleType.MERCHANT_RESTRICTION
                && request.getRestrictedMerchantCategory() == null) {
            throw new FamilyAccountException("Restricted merchant category required for MERCHANT_RESTRICTION type");
        }

        if (request.getRuleType() == FamilySpendingRule.RuleType.AMOUNT_LIMIT
                && request.getMaxTransactionAmount() == null) {
            throw new FamilyAccountException("Max transaction amount required for AMOUNT_LIMIT type");
        }

        if (request.getRuleType() == FamilySpendingRule.RuleType.TIME_RESTRICTION
                && (request.getTimeRestrictionStart() == null || request.getTimeRestrictionEnd() == null)) {
            throw new FamilyAccountException("Time restrictions required for TIME_RESTRICTION type");
        }
    }

    /**
     * Validate user is a parent
     */
    private void validateParentAccess(FamilyAccount familyAccount, String userId) {
        boolean isParent = familyAccount.getPrimaryParentUserId().equals(userId)
            || (familyAccount.getSecondaryParentUserId() != null
                && familyAccount.getSecondaryParentUserId().equals(userId));

        if (!isParent) {
            throw new UnauthorizedAccessException("Only parents can manage spending rules");
        }
    }
}
