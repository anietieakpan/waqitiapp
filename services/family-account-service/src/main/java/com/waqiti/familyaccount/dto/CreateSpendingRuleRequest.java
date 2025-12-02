package com.waqiti.familyaccount.dto;

import com.waqiti.familyaccount.domain.FamilySpendingRule;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Create Spending Rule Request DTO
 *
 * @author Waqiti Family Account Team
 * @version 2.0.0
 * @since 2025-10-17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSpendingRuleRequest {

    @NotBlank(message = "Family ID is required")
    private String familyId;

    @NotBlank(message = "Rule name is required")
    private String ruleName;

    @NotNull(message = "Rule type is required")
    private FamilySpendingRule.RuleType ruleType;

    @NotNull(message = "Rule scope is required")
    private FamilySpendingRule.RuleScope ruleScope;

    private String targetMemberId;

    private FamilySpendingRule.AgeGroup targetAgeGroup;

    private String restrictedMerchantCategory;

    private BigDecimal maxTransactionAmount;

    private String timeRestrictionStart;

    private String timeRestrictionEnd;

    private Boolean requiresApproval;

    private Boolean isActive;
}
