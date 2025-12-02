package com.waqiti.payment.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.waqiti.payment.domain.PaymentLinkTransaction.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for payment link transactions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentLinkTransactionResponse {
    
    private UUID id;
    private String transactionId;
    
    // Payment link information
    private UUID paymentLinkId;
    private String linkId;
    private String linkTitle;
    
    // Payer information
    private UUID payerId;
    private String payerEmail;
    private String payerName;
    private String payerDisplayName; // Enriched from user service
    
    // Transaction details
    private BigDecimal amount;
    private String currency;
    private String paymentNote;
    private TransactionStatus status;
    private String paymentMethod;
    
    // Payment processing information
    private String paymentReference;
    private String providerTransactionId;
    private String failureReason;
    
    // Additional metadata
    private Map<String, String> metadata;
    private String ipAddress;
    private String userAgent;
    
    // Timestamps
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime processedAt;
    
    // Computed fields
    private Boolean isCompleted;
    private Boolean isFailed;
    private Boolean isPending;
    private Boolean isAnonymous;
    private Long processingTimeMs; // Time taken to process
    
    // Receipt information
    private String receiptUrl;
    private String receiptNumber;
    
    // Refund information (if applicable)
    private Boolean isRefundable;
    private BigDecimal refundedAmount;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime refundedAt;
    
    // Risk and fraud information
    private Double fraudScore; // 0.0 to 1.0
    private String riskLevel; // "LOW", "MEDIUM", "HIGH"
    private Map<String, Object> riskFactors;
    
    // Builder helper methods
    public static PaymentLinkTransactionResponse fromDomain(
            com.waqiti.payment.domain.PaymentLinkTransaction transaction) {
        
        return PaymentLinkTransactionResponse.builder()
                .id(transaction.getId())
                .transactionId(transaction.getTransactionId())
                .paymentLinkId(transaction.getPaymentLink().getId())
                .linkId(transaction.getPaymentLink().getLinkId())
                .linkTitle(transaction.getPaymentLink().getTitle())
                .payerId(transaction.getPayerId())
                .payerEmail(transaction.getPayerEmail())
                .payerName(transaction.getPayerName())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .paymentNote(transaction.getPaymentNote())
                .status(transaction.getStatus())
                .paymentMethod(transaction.getPaymentMethod())
                .paymentReference(transaction.getPaymentReference())
                .providerTransactionId(transaction.getProviderTransactionId())
                .failureReason(transaction.getFailureReason())
                .metadata(transaction.getMetadata())
                .ipAddress(transaction.getIpAddress())
                .userAgent(transaction.getUserAgent())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .processedAt(transaction.getProcessedAt())
                .isCompleted(transaction.isCompleted())
                .isFailed(transaction.isFailed())
                .isPending(transaction.isPending())
                .isAnonymous(transaction.isAnonymous())
                .processingTimeMs(calculateProcessingTime(transaction))
                .build();
    }
    
    private static Long calculateProcessingTime(
            com.waqiti.payment.domain.PaymentLinkTransaction transaction) {
        if (transaction.getProcessedAt() != null && transaction.getCreatedAt() != null) {
            return java.time.Duration.between(
                    transaction.getCreatedAt(), 
                    transaction.getProcessedAt()
            ).toMillis();
        }
        return null;
    }
}