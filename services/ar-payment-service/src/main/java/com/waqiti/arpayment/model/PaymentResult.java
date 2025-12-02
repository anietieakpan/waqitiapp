package com.waqiti.arpayment.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Represents the result of a payment operation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResult {
    private boolean success;
    private UUID paymentId;
    private String transactionId;
    private PaymentStatus status;
    private BigDecimal amount;
    private String currency;
    private Instant timestamp;
    private String errorCode;
    private String errorMessage;
    private Map<String, Object> details;
    private String receiptUrl;
    private ProcessingInfo processingInfo;
    
    public enum PaymentStatus {
        SUCCESS,
        PENDING,
        PROCESSING,
        FAILED,
        CANCELLED,
        TIMEOUT,
        REQUIRES_ACTION,
        PARTIALLY_COMPLETED
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessingInfo {
        private String processor;
        private String referenceNumber;
        private long processingTimeMs;
        private Map<String, String> processorResponse;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public UUID getPaymentId() {
        return paymentId != null ? paymentId : UUID.randomUUID();
    }
    
    public String getTransactionId() {
        return transactionId != null ? transactionId : "TXN-" + System.currentTimeMillis();
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
}