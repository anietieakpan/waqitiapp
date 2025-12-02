package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentProcessorResponse {

    private String transactionId;
    
    private String externalTransactionId;
    
    private PaymentStatus status;
    
    private String statusDescription;
    
    private BigDecimal amount;
    
    private String currency;
    
    private BigDecimal processingFee;
    
    private String processorName;
    
    @Builder.Default
    private LocalDateTime processedAt = LocalDateTime.now();
    
    private String authorizationCode;
    
    private String merchantId;
    
    private String terminalId;
    
    private PaymentMethod paymentMethod;
    
    private String cardLast4;
    
    private String cardType;
    
    private String errorCode;
    
    private String errorMessage;
    
    private String riskScore;
    
    private RiskAssessment riskAssessment;
    
    private List<ProcessingEvent> events;
    
    private Map<String, String> metadata;
    
    private String settlementId;
    
    private LocalDateTime settlementDate;

    public enum PaymentStatus {
        PENDING,
        AUTHORIZED,
        CAPTURED,
        SETTLED,
        FAILED,
        CANCELLED,
        REFUNDED,
        CHARGEBACK
    }

    public enum PaymentMethod {
        CREDIT_CARD,
        DEBIT_CARD,
        BANK_TRANSFER,
        DIGITAL_WALLET,
        CRYPTOCURRENCY,
        DIRECT_DEBIT
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskAssessment {
        private String riskLevel;
        private int riskScore;
        private List<String> riskFactors;
        private boolean requiresReview;
        private String fraudCheckResult;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessingEvent {
        private String eventType;
        private LocalDateTime timestamp;
        private String description;
        private String responseCode;
        private Map<String, Object> eventData;
    }

    public boolean isSuccessful() {
        return PaymentStatus.AUTHORIZED.equals(status) ||
               PaymentStatus.CAPTURED.equals(status) ||
               PaymentStatus.SETTLED.equals(status);
    }

    public boolean isFailed() {
        return PaymentStatus.FAILED.equals(status) ||
               PaymentStatus.CANCELLED.equals(status);
    }

    public boolean isPending() {
        return PaymentStatus.PENDING.equals(status);
    }

    public boolean isSettled() {
        return PaymentStatus.SETTLED.equals(status) && settlementDate != null;
    }

    public boolean hasRiskIssues() {
        return riskAssessment != null && riskAssessment.isRequiresReview();
    }

    public boolean hasProcessingFee() {
        return processingFee != null && processingFee.compareTo(BigDecimal.ZERO) > 0;
    }

    public BigDecimal getNetAmount() {
        if (amount == null) return BigDecimal.ZERO;
        BigDecimal fee = processingFee != null ? processingFee : BigDecimal.ZERO;
        return amount.subtract(fee);
    }
}