package com.waqiti.payment.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Universal payment result model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResult {
    
    private UUID paymentId;
    private String transactionId;
    private PaymentStatus status;
    private BigDecimal amount;
    private String currency;
    private String message;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime processedAt;
    private LocalDateTime settledAt;
    
    // Provider specific data
    private String providerTransactionId;
    private String providerReference;
    private String providerResponse;
    private ProviderType providerType;
    private PaymentType paymentType;
    
    // Fee information
    private BigDecimal processingFee;
    private BigDecimal networkFee;
    private BigDecimal totalFees;
    private FeeCalculation fees;
    
    // Metadata
    private Map<String, Object> metadata;
    private Map<String, Object> additionalData;
    private String estimatedSettlement;
    
    // Factory methods
    public static PaymentResult success(UUID paymentId, BigDecimal amount, String message) {
        return PaymentResult.builder()
            .paymentId(paymentId)
            .status(PaymentStatus.COMPLETED)
            .amount(amount)
            .message(message)
            .processedAt(LocalDateTime.now())
            .build();
    }
    
    public static PaymentResult error(String errorMessage) {
        return PaymentResult.builder()
            .status(PaymentStatus.FAILED)
            .errorMessage(errorMessage)
            .processedAt(LocalDateTime.now())
            .build();
    }
    
    public static PaymentResult fraudBlocked(String reason) {
        return PaymentResult.builder()
            .status(PaymentStatus.FRAUD_BLOCKED)
            .errorMessage(reason)
            .errorCode("FRAUD_DETECTED")
            .processedAt(LocalDateTime.now())
            .build();
    }
    
    public static PaymentResult partialSuccess(UUID paymentId, BigDecimal amount, String message) {
        return PaymentResult.builder()
            .paymentId(paymentId)
            .status(PaymentStatus.PARTIALLY_COMPLETED)
            .amount(amount)
            .message(message)
            .processedAt(LocalDateTime.now())
            .build();
    }
    
    public boolean isSuccess() {
        return status == PaymentStatus.COMPLETED;
    }
    
    public boolean isFailed() {
        return status == PaymentStatus.FAILED || status == PaymentStatus.FRAUD_BLOCKED;
    }
    
    public boolean isPending() {
        return status == PaymentStatus.PENDING || status == PaymentStatus.PROCESSING;
    }
}