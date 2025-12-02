package com.waqiti.common.bulkhead;

import lombok.Builder;
import lombok.Data;

/**
 * Summary of resource utilization across all bulkhead pools
 */
@Data
@Builder
public class ResourceUtilizationSummary {
    
    private double paymentProcessingUtilization;
    private double kycVerificationUtilization;
    private double fraudDetectionUtilization;
    private double notificationUtilization;
    private double analyticsUtilization;
    private double coreBankingUtilization;
    
    public double getAverageUtilization() {
        return (paymentProcessingUtilization + kycVerificationUtilization + 
                fraudDetectionUtilization + notificationUtilization + 
                analyticsUtilization + coreBankingUtilization) / 6.0;
    }
    
    public double getMaxUtilization() {
        return Math.max(paymentProcessingUtilization,
               Math.max(kycVerificationUtilization,
               Math.max(fraudDetectionUtilization,
               Math.max(notificationUtilization,
               Math.max(analyticsUtilization, coreBankingUtilization)))));
    }
    
    public double getMinUtilization() {
        return Math.min(paymentProcessingUtilization,
               Math.min(kycVerificationUtilization,
               Math.min(fraudDetectionUtilization,
               Math.min(notificationUtilization,
               Math.min(analyticsUtilization, coreBankingUtilization)))));
    }
    
    public ResourceType getMostUtilizedResourceType() {
        double max = getMaxUtilization();
        
        if (paymentProcessingUtilization == max) return ResourceType.PAYMENT_PROCESSING;
        if (kycVerificationUtilization == max) return ResourceType.KYC_VERIFICATION;
        if (fraudDetectionUtilization == max) return ResourceType.FRAUD_DETECTION;
        if (notificationUtilization == max) return ResourceType.NOTIFICATION;
        if (analyticsUtilization == max) return ResourceType.ANALYTICS;
        return ResourceType.CORE_BANKING;
    }
}