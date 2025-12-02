package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Budget line item DTO representing individual budget allocations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetLineItem {
    
    private UUID lineItemId;
    private UUID budgetId;
    
    private UUID accountId;
    private String accountCode;
    private String accountName;
    
    private String category;
    private String subcategory;
    
    private BigDecimal budgetAmount;
    private BigDecimal actualAmount;
    private BigDecimal variance;
    private BigDecimal utilizationPercentage;
    
    private BigDecimal alertThreshold; // Percentage threshold for alerts (e.g., 80.0 for 80%)
    private BigDecimal warningThreshold; // Warning threshold (e.g., 90.0 for 90%)
    
    private String status; // ON_TRACK, AT_RISK, OVER_BUDGET, UNDER_UTILIZED
    private String priority; // LOW, MEDIUM, HIGH, CRITICAL
    
    private String notes;
    private String description;
    
    // Quarterly/Monthly breakdown
    private BigDecimal q1Amount;
    private BigDecimal q2Amount;
    private BigDecimal q3Amount;
    private BigDecimal q4Amount;
    
    // Tracking fields
    private String createdBy;
    private LocalDateTime createdAt;
    private String lastModifiedBy;
    private LocalDateTime lastModifiedAt;
    
    // Approval workflow
    private boolean requiresApproval;
    private String approvedBy;
    private LocalDateTime approvedAt;
    
    // Cost center tracking
    private String costCenter;
    private String department;
    private String project;
    
    // Additional metadata
    private String vendor;
    private String contractReference;
    private LocalDateTime contractStartDate;
    private LocalDateTime contractEndDate;
    
    /**
     * Calculate utilization percentage
     */
    public BigDecimal calculateUtilization() {
        if (budgetAmount == null || budgetAmount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal actual = actualAmount != null ? actualAmount : BigDecimal.ZERO;
        return actual.divide(budgetAmount, 4, java.math.RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
    }
    
    /**
     * Calculate variance amount
     */
    public BigDecimal calculateVariance() {
        BigDecimal budget = budgetAmount != null ? budgetAmount : BigDecimal.ZERO;
        BigDecimal actual = actualAmount != null ? actualAmount : BigDecimal.ZERO;
        return budget.subtract(actual);
    }
    
    /**
     * Check if alert threshold is exceeded
     */
    public boolean isAlertThresholdExceeded() {
        if (alertThreshold == null) {
            return false;
        }
        
        BigDecimal utilization = calculateUtilization();
        return utilization.compareTo(alertThreshold) > 0;
    }
    
    /**
     * Check if warning threshold is exceeded
     */
    public boolean isWarningThresholdExceeded() {
        if (warningThreshold == null) {
            return false;
        }
        
        BigDecimal utilization = calculateUtilization();
        return utilization.compareTo(warningThreshold) > 0;
    }
    
    /**
     * Get remaining budget amount
     */
    public BigDecimal getRemainingBudget() {
        BigDecimal budget = budgetAmount != null ? budgetAmount : BigDecimal.ZERO;
        BigDecimal actual = actualAmount != null ? actualAmount : BigDecimal.ZERO;
        return budget.subtract(actual);
    }
}