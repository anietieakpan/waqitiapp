package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Request DTO for creating budget line items
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBudgetLineItemRequest {
    
    @NotNull(message = "Account ID is required")
    private UUID accountId;
    
    @NotBlank(message = "Account name is required")
    @Size(max = 100, message = "Account name cannot exceed 100 characters")
    private String accountName;
    
    @NotBlank(message = "Category is required")
    @Size(max = 50, message = "Category cannot exceed 50 characters")
    private String category;
    
    @Size(max = 50, message = "Subcategory cannot exceed 50 characters")
    private String subcategory;
    
    @NotNull(message = "Budget amount is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Budget amount must be greater than zero")
    private BigDecimal budgetAmount;
    
    @DecimalMin(value = "0.0", message = "Alert threshold must be non-negative")
    private BigDecimal alertThreshold;
    
    @DecimalMin(value = "0.0", message = "Warning threshold must be non-negative")
    private BigDecimal warningThreshold;
    
    private String priority; // LOW, MEDIUM, HIGH, CRITICAL
    
    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    private String notes;
    
    @Size(max = 200, message = "Description cannot exceed 200 characters")
    private String description;
    
    // Quarterly breakdown (optional)
    private BigDecimal q1Amount;
    private BigDecimal q2Amount;
    private BigDecimal q3Amount;
    private BigDecimal q4Amount;
    
    // Cost center information
    private String costCenter;
    private String department;
    private String project;
    
    // Contract information (if applicable)
    private String vendor;
    private String contractReference;
    private LocalDateTime contractStartDate;
    private LocalDateTime contractEndDate;
    
    // Approval requirements
    private boolean requiresApproval;
    
    @Builder.Default
    private boolean isActive = true;
}