package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Outstanding Item Validation DTO
 * 
 * Contains validation results for an individual outstanding item
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutstandingItemValidation {
    
    private OutstandingItem item;
    private String status; // OUTSTANDING, CLEARED, STALE, WRITTEN_OFF
    
    // Validation details
    private Boolean cleared;
    private LocalDateTime clearingDate;
    private String clearingReference;
    
    // Analysis
    private Integer daysToClear;
    private String clearingMethod; // CHECK_PRESENTED, ELECTRONIC, MANUAL_REVERSAL
    private String validationNotes;
    
    // Actions taken
    private Boolean requiresAction;
    private String recommendedAction;
    private String actionTaken;
    
    // Metadata
    private LocalDateTime validatedAt;
    private String validatedBy;
}