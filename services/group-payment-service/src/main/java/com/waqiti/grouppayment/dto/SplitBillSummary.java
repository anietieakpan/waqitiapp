package com.waqiti.grouppayment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Split Bill Summary DTO
 * Provides summary statistics about the split bill calculation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SplitBillSummary {

    private Integer totalParticipants;

    private BigDecimal averageAmount;

    private BigDecimal highestAmount;

    private BigDecimal lowestAmount;

    private Integer totalAdjustments;

    private BigDecimal totalTaxes;

    private BigDecimal totalTips;

    private BigDecimal totalDiscounts;

    private Integer totalItems;
}
