package com.waqiti.ledger.dto;

import com.waqiti.ledger.service.BudgetManagementService.BudgetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Request DTO for creating a new budget
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBudgetRequest {
    
    @NotBlank(message = "Budget name is required")
    @Size(max = 100, message = "Budget name cannot exceed 100 characters")
    private String budgetName;
    
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;
    
    @NotNull(message = "Budget type is required")
    private BudgetType budgetType;
    
    @NotNull(message = "Start date is required")
    private LocalDate startDate;
    
    @NotNull(message = "End date is required")
    private LocalDate endDate;
    
    @NotEmpty(message = "At least one budget line item is required")
    @Valid
    private List<CreateBudgetLineItemRequest> lineItems;
    
    @NotBlank(message = "Created by is required")
    private String createdBy;
    
    private String departmentId;
    private String projectId;
    private String costCenterId;
    
    private boolean enableAlerts;
    private boolean enableApprovals;
    private boolean enableRollover;
    
    @Builder.Default
    private String currency = "USD";
    
    private String notes;
}