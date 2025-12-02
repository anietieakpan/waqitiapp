package com.waqiti.analytics.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Category insight model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryInsight {
    private String categoryName;
    private BigDecimal totalSpent;
    private Integer transactionCount;
    private BigDecimal averageTransaction;
    private BigDecimal percentageOfTotal;
    private String trend; // INCREASING, DECREASING, STABLE
    private BigDecimal monthOverMonthChange;
    private List<String> topMerchants;
    private String categoryBehavior;
}