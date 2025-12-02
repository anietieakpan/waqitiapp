package com.waqiti.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Data Transfer Object for Spending Category information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpendingCategoryDTO {
    
    /**
     * Category name
     */
    private String categoryName;
    
    /**
     * Number of transactions in this category
     */
    private Long transactionCount;
    
    /**
     * Total amount spent in this category
     */
    private BigDecimal totalAmount;
    
    /**
     * Calculate percentage of total spending
     */
    public BigDecimal calculatePercentage(BigDecimal totalSpending) {
        if (totalSpending == null || totalSpending.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return totalAmount.divide(totalSpending, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
    }
    
    /**
     * Calculate average transaction amount
     */
    public BigDecimal getAverageTransactionAmount() {
        if (transactionCount == null || transactionCount == 0) {
            return BigDecimal.ZERO;
        }
        return totalAmount.divide(new BigDecimal(transactionCount), 2, RoundingMode.HALF_UP);
    }
}