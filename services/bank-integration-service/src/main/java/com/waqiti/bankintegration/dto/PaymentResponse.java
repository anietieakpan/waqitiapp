package com.waqiti.bankintegration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Payment Response DTO
 * 
 * Contains the result of a payment processing request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    
    private String requestId;
    private String transactionId;
    private String providerTransactionId;
    private Long providerId;
    
    private PaymentStatus status;
    private BigDecimal amount;
    private String currency;
    
    private Instant processedAt;
    private Instant settledAt;
    
    // Error information
    private String errorCode;
    private String errorMessage;
    private String declineReason;
    
    // Additional provider-specific data
    private Map<String, Object> additionalData;
    
    // 3D Secure or additional authentication
    private AuthenticationRequired authenticationRequired;
    
    // Fee information
    private BigDecimal processingFee;
    private BigDecimal netAmount;
    
    // Risk assessment results
    private RiskAssessment riskAssessment;
    
    // Additional helper methods for compatibility
    public String getMessage() {
        return errorMessage != null ? errorMessage : "Payment processed successfully";
    }
    
    public void setMessage(String message) {
        this.errorMessage = message;
    }
    
    public boolean getSuccess() {
        return status == PaymentStatus.SUCCESS || status == PaymentStatus.COMPLETED;
    }
    
    public void setSuccess(boolean success) {
        if (success) {
            this.status = PaymentStatus.SUCCESS;
        } else {
            this.status = PaymentStatus.FAILED;
        }
    }
    
    public String getTimestamp() {
        return processedAt != null ? processedAt.toString() : Instant.now().toString();
    }
    
    public void setTimestamp(String timestamp) {
        this.processedAt = Instant.parse(timestamp);
    }
    
    public BigDecimal getEstimatedSettlement() {
        return netAmount != null ? netAmount : amount;
    }
    
    public void setEstimatedSettlement(BigDecimal estimatedSettlement) {
        this.netAmount = estimatedSettlement;
    }
}