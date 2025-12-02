package com.waqiti.familyaccount.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Family Account DTO
 *
 * @author Waqiti Family Account Team
 * @version 2.0.0
 * @since 2025-10-17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FamilyAccountDto {

    private String familyId;
    private String familyName;
    private String primaryParentUserId;
    private String secondaryParentUserId;
    private String familyWalletId;
    private Integer allowanceDayOfMonth;
    private BigDecimal autoSavingsPercentage;
    private Boolean autoSavingsEnabled;
    private BigDecimal defaultDailyLimit;
    private BigDecimal defaultWeeklyLimit;
    private BigDecimal defaultMonthlyLimit;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer memberCount;
}
