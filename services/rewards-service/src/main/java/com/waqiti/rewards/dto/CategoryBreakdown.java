package com.waqiti.rewards.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal; /**
 * Category breakdown class for analytics
 */
@Data
@Builder
public class CategoryBreakdown {
    private String category;
    private String categoryName;
    private BigDecimal spending;
    private BigDecimal cashbackEarned;
    private Long transactionCount;
    private BigDecimal averageTransactionAmount;
    private BigDecimal effectiveRate;
}
