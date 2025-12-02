package com.waqiti.familyaccount.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Update Family Account Request DTO
 *
 * @author Waqiti Family Account Team
 * @version 2.0.0
 * @since 2025-10-17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateFamilyAccountRequest {

    @Size(min = 2, max = 100, message = "Family name must be between 2 and 100 characters")
    private String familyName;

    private String secondaryParentUserId;

    private Integer allowanceDayOfMonth;

    private BigDecimal autoSavingsPercentage;

    private Boolean autoSavingsEnabled;

    private BigDecimal defaultDailyLimit;

    private BigDecimal defaultWeeklyLimit;

    private BigDecimal defaultMonthlyLimit;
}
