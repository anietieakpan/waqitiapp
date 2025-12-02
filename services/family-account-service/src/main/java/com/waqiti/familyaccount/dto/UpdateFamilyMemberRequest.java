package com.waqiti.familyaccount.dto;

import com.waqiti.familyaccount.domain.FamilyMember;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Update Family Member Request DTO
 *
 * @author Waqiti Family Account Team
 * @version 2.0.0
 * @since 2025-10-17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateFamilyMemberRequest {

    private FamilyMember.MemberRole memberRole;

    private BigDecimal allowanceAmount;

    private String allowanceFrequency;

    private BigDecimal dailySpendingLimit;

    private BigDecimal weeklySpendingLimit;

    private BigDecimal monthlySpendingLimit;

    private Boolean transactionApprovalRequired;

    private Boolean canViewFamilyAccount;
}
