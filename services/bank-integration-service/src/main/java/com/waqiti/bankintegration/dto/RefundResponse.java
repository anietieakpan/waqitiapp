package com.waqiti.bankintegration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Refund Response DTO
 * 
 * Contains the result of a refund processing request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundResponse {
    
    private String refundId;
    private String providerRefundId;
    private String originalTransactionId;
    
    private RefundStatus status;
    private BigDecimal amount;
    private String currency;
    
    private Instant processedAt;
    private Instant expectedSettlementDate;
    
    // Error information
    private String errorCode;
    private String errorMessage;
    
    // Provider-specific data
    private Map<String, Object> additionalData;
    
    // Fee information
    private BigDecimal refundFee;
    private BigDecimal netRefundAmount;
    
    // Additional helper methods for compatibility
    public String getMessage() {
        return errorMessage != null ? errorMessage : "Refund processed successfully";
    }
    
    public void setMessage(String message) {
        this.errorMessage = message;
    }
    
    public boolean getSuccess() {
        return status == RefundStatus.SUCCESS || status == RefundStatus.COMPLETED;
    }
    
    public void setSuccess(boolean success) {
        if (success) {
            this.status = RefundStatus.SUCCESS;
        } else {
            this.status = RefundStatus.FAILED;
        }
    }
    
    public String getRefundTransactionId() {
        return refundId;
    }
    
    public void setRefundTransactionId(String refundTransactionId) {
        this.refundId = refundTransactionId;
    }
    
    public String getTimestamp() {
        return processedAt != null ? processedAt.toString() : Instant.now().toString();
    }
    
    public void setTimestamp(String timestamp) {
        this.processedAt = Instant.parse(timestamp);
    }
}