package com.waqiti.dispute.dto;

import com.waqiti.dispute.entity.ResolutionDecision;
import com.waqiti.dispute.entity.ResolutionType;
import lombok.Builder;
import lombok.Data;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Request to resolve a dispute
 */
@Data
@Builder
public class ResolutionRequest {
    
    @NotNull(message = "Resolution decision is required")
    private ResolutionDecision decision;
    
    @NotNull(message = "Resolution type is required")
    private ResolutionType resolutionType;
    
    @NotBlank(message = "Resolver ID is required")
    private String resolvedBy;
    
    @NotBlank(message = "Resolution reason is required")
    private String reason;
    
    // Refund details
    private BigDecimal refundAmount;
    
    @Min(0)
    @Max(100)
    private Integer refundPercentage;
    
    // Liability distribution
    private BigDecimal merchantLiabilityAmount;
    private BigDecimal platformLiabilityAmount;
    
    // Communication
    private String customerMessage;
    private String merchantMessage;
    private boolean sendNotifications = true;
    
    // Evidence used in resolution
    private String[] evidenceIds;
    
    // Additional notes
    private String internalNotes;
    
    /**
     * Create full refund resolution
     */
    public static ResolutionRequest fullRefund(String resolvedBy, String reason) {
        return ResolutionRequest.builder()
            .decision(ResolutionDecision.APPROVED_FULL_REFUND)
            .resolutionType(ResolutionType.MANUAL)
            .resolvedBy(resolvedBy)
            .reason(reason)
            .refundPercentage(100)
            .sendNotifications(true)
            .build();
    }
    
    /**
     * Create partial refund resolution
     */
    public static ResolutionRequest partialRefund(String resolvedBy, String reason, int refundPercentage) {
        return ResolutionRequest.builder()
            .decision(ResolutionDecision.APPROVED_PARTIAL_REFUND)
            .resolutionType(ResolutionType.MANUAL)
            .resolvedBy(resolvedBy)
            .reason(reason)
            .refundPercentage(refundPercentage)
            .sendNotifications(true)
            .build();
    }
    
    /**
     * Create rejection resolution
     */
    public static ResolutionRequest reject(String resolvedBy, String reason) {
        return ResolutionRequest.builder()
            .decision(ResolutionDecision.REJECTED)
            .resolutionType(ResolutionType.MANUAL)
            .resolvedBy(resolvedBy)
            .reason(reason)
            .sendNotifications(true)
            .build();
    }
    
    /**
     * Create automated resolution
     */
    public static ResolutionRequest automated(ResolutionDecision decision, String reason) {
        return ResolutionRequest.builder()
            .decision(decision)
            .resolutionType(ResolutionType.AUTOMATED)
            .resolvedBy("SYSTEM")
            .reason(reason)
            .sendNotifications(true)
            .build();
    }
}