package com.waqiti.ledger.dto;

import com.waqiti.ledger.service.BudgetManagementService.BudgetStatus;
import com.waqiti.ledger.service.BudgetManagementService.BudgetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Budget entity DTO representing a comprehensive budget with line items
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Budget {
    
    private UUID budgetId;
    private String budgetName;
    private String description;
    private BudgetType budgetType;
    private BudgetStatus status;
    
    private LocalDate startDate;
    private LocalDate endDate;
    
    private BigDecimal totalBudgetAmount;
    private BigDecimal totalActualAmount;
    private BigDecimal totalVariance;
    
    private List<BudgetLineItem> lineItems;
    
    private String createdBy;
    private LocalDateTime createdAt;
    private String lastModifiedBy;
    private LocalDateTime lastModifiedAt;
    
    private String departmentId;
    private String projectId;
    private String costCenterId;
    
    private boolean enableAlerts;
    private boolean enableApprovals;
    private boolean enableRollover;
    
    private String currency;
    private String notes;
    
    private String approvedBy;
    private LocalDateTime approvedAt;
    private String approvalNotes;
    
    // Version control
    private Integer version;
    private UUID parentBudgetId; // For budget revisions
    
    // Calculated fields
    private BigDecimal utilizationPercentage;
    private String healthStatus;
    private Integer daysRemaining;
}