package com.waqiti.grouppayment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Split Adjustment DTO
 * Represents adjustments made to participant amounts (rounding, discounts, etc.)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SplitAdjustment {

    private String participantId;

    private AdjustmentType type;

    private BigDecimal amount;

    private String description;

    public enum AdjustmentType {
        ROUNDING,
        DISCOUNT,
        ADDITIONAL,
        TAX,
        TIP,
        FEE,
        CREDIT
    }
}
