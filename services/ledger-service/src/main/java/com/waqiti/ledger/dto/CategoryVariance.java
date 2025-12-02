package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO representing variance analysis by category
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryVariance {
    
    private String category;
    private BigDecimal budgetAmount;
    private BigDecimal actualAmount;
    private BigDecimal variance;
    private BigDecimal variancePercentage;
    private String trend; // IMPROVING, DETERIORATING, STABLE
    private String explanation;
}