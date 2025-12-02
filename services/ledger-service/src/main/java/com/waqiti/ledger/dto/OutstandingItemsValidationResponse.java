package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Outstanding Items Validation Response DTO
 * 
 * Contains results of validating outstanding items from previous reconciliation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutstandingItemsValidationResponse {
    
    private UUID bankAccountId;
    private List<OutstandingItemValidation> validations;
    
    // Summary counts
    private Integer totalItems;
    private Integer clearedItems;
    private Integer stalledItems;
    private Integer stillOutstandingItems;
    
    // Amounts
    private BigDecimal totalOutstandingAmount;
    private BigDecimal clearedAmount;
    private BigDecimal stalledAmount;
    
    // Analysis
    private String clearingRate; // percentage of items that cleared
    private String averageClearingDays;
    private List<String> clearingPatterns;
    
    // Generation info
    private LocalDateTime validatedAt;
    private String validatedBy;
}