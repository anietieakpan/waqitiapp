package com.waqiti.familyaccount.dto;

import com.waqiti.familyaccount.domain.FamilyMember;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Add Family Member Request DTO
 *
 * @author Waqiti Family Account Team
 * @version 2.0.0
 * @since 2025-10-17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddFamilyMemberRequest {

    @NotBlank(message = "Family ID is required")
    private String familyId;

    @NotBlank(message = "User ID is required")
    private String userId;

    @NotNull(message = "Member role is required")
    private FamilyMember.MemberRole memberRole;

    @NotNull(message = "Date of birth is required")
    private LocalDate dateOfBirth;

    private BigDecimal allowanceAmount;

    private String allowanceFrequency;

    private BigDecimal dailySpendingLimit;

    private BigDecimal weeklySpendingLimit;

    private BigDecimal monthlySpendingLimit;

    private Boolean transactionApprovalRequired;

    private Boolean canViewFamilyAccount;
}
