package com.waqiti.familyaccount.dto;

import com.waqiti.familyaccount.domain.FamilyMember;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Family Member Response DTO
 * API response object for family member operations
 *
 * @author Waqiti Family Account Team
 * @version 2.0.0
 * @since 2025-10-17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FamilyMemberResponse {

    private String userId;
    private String familyId;
    private FamilyMember.MemberRole memberRole;
    private FamilyMember.MemberStatus memberStatus;
    private LocalDate dateOfBirth;
    private Integer age;
    private BigDecimal allowanceAmount;
    private String allowanceFrequency;
    private LocalDate lastAllowanceDate;
    private BigDecimal dailySpendingLimit;
    private BigDecimal weeklySpendingLimit;
    private BigDecimal monthlySpendingLimit;
    private Boolean transactionApprovalRequired;
    private Boolean canViewFamilyAccount;
    private LocalDateTime joinedAt;
    private String message;
}
