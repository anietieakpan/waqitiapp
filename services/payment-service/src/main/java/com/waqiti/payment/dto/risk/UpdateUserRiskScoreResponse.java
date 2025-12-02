/**
 * Update User Risk Score Response DTO
 * Response for user risk score update operations
 */
package com.waqiti.payment.dto.risk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRiskScoreResponse {
    
    /**
     * Whether the update was successful
     */
    private Boolean success;
    
    /**
     * User ID that was updated
     */
    private String userId;
    
    /**
     * Alert ID that triggered the update
     */
    private String alertId;
    
    /**
     * Updated risk score
     */
    private Double updatedRiskScore;
    
    /**
     * Updated risk level
     */
    private String updatedRiskLevel;
    
    /**
     * Previous risk score for comparison
     */
    private Double previousRiskScore;
    
    /**
     * Previous risk level for comparison
     */
    private String previousRiskLevel;
    
    /**
     * Whether enhanced monitoring was enabled
     */
    private Boolean enhancedMonitoringEnabled;
    
    /**
     * When the update was processed
     */
    private Instant updatedAt;
    
    /**
     * Next review date for the user's risk assessment
     */
    private Instant nextReviewDate;
    
    /**
     * Any additional actions recommended
     */
    private String recommendedActions;
    
    /**
     * Error message if update failed
     */
    private String errorMessage;
    
    /**
     * Correlation ID for tracking
     */
    private String correlationId;
}