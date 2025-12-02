package com.waqiti.rewards.dto;

import lombok.Builder;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

/**
 * Request DTO for admin rewards adjustment
 */
@Data
@Builder
public class AdjustRewardsRequest {
    
    /**
     * Cashback adjustment amount (can be positive or negative)
     */
    private BigDecimal cashbackAdjustment;
    
    /**
     * Points adjustment amount (can be positive or negative)
     */
    private Long pointsAdjustment;
    
    /**
     * Reason for the adjustment
     */
    @NotBlank
    private String reason;
    
    /**
     * Adjustment type
     */
    private AdjustmentType type;
    
    /**
     * Reference transaction ID (if applicable)
     */
    private String referenceTransactionId;
    
    /**
     * Additional notes
     */
    private String notes;
    
    /**
     * Whether to send notification to user
     */
    private Boolean sendNotification;
    
    public enum AdjustmentType {
        MANUAL_CORRECTION,
        SYSTEM_ERROR_FIX,
        PROMOTIONAL_BONUS,
        CUSTOMER_SERVICE_GESTURE,
        REFUND_ADJUSTMENT,
        TECHNICAL_ISSUE_COMPENSATION,
        OTHER
    }
}