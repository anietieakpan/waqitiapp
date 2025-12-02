package com.waqiti.common.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * CRITICAL PRODUCTION FIX - FraudAnalysisRequest
 * Simplified fraud analysis request model
 * For comprehensive analysis, use FraudAssessmentRequest
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudAnalysisRequest {
    
    private String transactionId;
    private String userId;
    private String accountId;
    private BigDecimal amount;
    private String currency;
    private String transactionType;
    private String ipAddress;
    private String deviceId;
    private String email;
    private LocalDateTime timestamp;
    private Map<String, Object> context;
    
    /**
     * Convert to comprehensive FraudAssessmentRequest
     */
    public FraudAssessmentRequest toFraudAssessmentRequest() {
        return FraudAssessmentRequest.builder()
            .requestId("REQ-" + System.currentTimeMillis())
            .transactionId(transactionId)
            .userId(userId)
            .accountId(accountId)
            .amount(amount)
            .currency(currency != null ? currency : "USD")
            .transactionType(transactionType)
            .ipAddress(ipAddress)
            .email(email)
            .transactionTimestamp(timestamp != null ? timestamp.atZone(java.time.ZoneOffset.UTC).toInstant() : java.time.Instant.now())
            .build();
    }
    
    /**
     * Create from FraudAssessmentRequest
     */
    public static FraudAnalysisRequest fromFraudAssessmentRequest(FraudAssessmentRequest request) {
        return FraudAnalysisRequest.builder()
            .transactionId(request.getTransactionId())
            .userId(request.getUserId())
            .accountId(request.getAccountId())
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .transactionType(request.getTransactionType())
            .ipAddress(request.getIpAddress())
            .email(request.getEmail())
            .timestamp(request.getTransactionTimestamp().atZone(java.time.ZoneOffset.UTC).toLocalDateTime())
            .build();
    }
    
    /**
     * Basic validation
     */
    public boolean isValid() {
        return transactionId != null && !transactionId.isEmpty() &&
               userId != null && !userId.isEmpty() &&
               amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }
    
    /**
     * Get risk level based on amount
     */
    public String getRiskLevel() {
        if (amount == null) return "UNKNOWN";
        
        if (amount.compareTo(new BigDecimal("50000")) >= 0) {
            return "CRITICAL";
        } else if (amount.compareTo(new BigDecimal("10000")) >= 0) {
            return "HIGH";
        } else if (amount.compareTo(new BigDecimal("1000")) >= 0) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }
}