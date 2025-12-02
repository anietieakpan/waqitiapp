package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Reconciliation Recommendation DTO
 * 
 * Contains actionable recommendations based on reconciliation analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationRecommendation {
    
    private String priority; // HIGH, MEDIUM, LOW
    private String category; // VARIANCE, UNMATCHED, STALE_ITEMS, PROCESS_IMPROVEMENT
    private String title;
    private String description;
    private String action;
    
    // Implementation details
    private String assignedTo;
    private LocalDateTime dueDate;
    private String status; // PENDING, IN_PROGRESS, COMPLETED
    
    // Impact assessment
    private String impactLevel; // HIGH, MEDIUM, LOW
    private String riskMitigation;
    private String expectedOutcome;
    
    // Additional context
    private String supportingEvidence;
    private String references;
    private LocalDateTime createdAt;
}