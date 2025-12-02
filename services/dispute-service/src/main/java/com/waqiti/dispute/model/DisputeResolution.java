package com.waqiti.dispute.model;

import com.waqiti.dispute.entity.ResolutionDecision;
import com.waqiti.dispute.entity.ResolutionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Dispute resolution details
 */
@Data
@Builder
public class DisputeResolution {
    
    private String id;
    private String disputeId;
    private ResolutionDecision decision;
    private ResolutionType resolutionType;
    private String resolvedBy;
    private LocalDateTime resolvedAt;
    private String reason;
    
    // Financial details
    private BigDecimal refundAmount;
    private BigDecimal merchantLiabilityAmount;
    private BigDecimal platformLiabilityAmount;
    private String refundTransactionId;
    private LocalDateTime refundProcessedAt;
    
    // Evidence used
    private List<String> evidenceIds;
    private Map<String, Double> evidenceWeights;
    
    // Communication
    private String customerNotificationId;
    private String merchantNotificationId;
    private boolean customerNotified;
    private boolean merchantNotified;
    
    // Audit
    private Map<String, Object> auditData;
    private String approvalChain;
    
    /**
     * Check if resolution involves refund
     */
    public boolean hasRefund() {
        return refundAmount != null && refundAmount.compareTo(BigDecimal.ZERO) > 0;
    }
    
    /**
     * Check if resolution is automated
     */
    public boolean isAutomated() {
        return resolutionType == ResolutionType.AUTOMATED;
    }
    
    /**
     * Get liability summary
     */
    public String getLiabilitySummary() {
        if (merchantLiabilityAmount != null && platformLiabilityAmount != null) {
            return String.format("Merchant: %s, Platform: %s", 
                merchantLiabilityAmount, platformLiabilityAmount);
        } else if (merchantLiabilityAmount != null) {
            return String.format("Merchant: %s", merchantLiabilityAmount);
        } else if (platformLiabilityAmount != null) {
            return String.format("Platform: %s", platformLiabilityAmount);
        }
        return "No liability assigned";
    }
}