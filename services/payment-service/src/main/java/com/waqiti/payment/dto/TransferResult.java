package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Result DTO for transfer operations (P2P, bank transfers, etc.)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransferResult {

    private boolean successful;
    private String transactionId;
    private String transferId;
    private String transferReference;
    private String confirmationNumber;
    
    // Timing information
    private Long processingTimeMs;
    private Instant initiatedAt;
    private Instant completedAt;
    private Instant expectedSettlement;
    
    // Amount information
    private BigDecimal requestedAmount;
    private BigDecimal transferredAmount;
    private BigDecimal transferFee;
    private BigDecimal exchangeRate;
    private String currency;
    private String settlementCurrency;
    
    // Participants
    private String senderId;
    private String recipientId;
    private String senderAccountId;
    private String recipientAccountId;
    
    // Transfer details
    private String transferMethod;
    private String transferNetwork;
    private String routingInfo;
    private String transferMessage;
    
    // Status information
    private String status;
    private String statusCode;
    private String statusMessage;
    
    // Error information
    private String errorCode;
    private String errorMessage;
    private String failureReason;
    
    // Additional metadata
    private String providerName;
    private String providerTransactionId;
    private String receiptUrl;
    
    /**
     * Creates a successful transfer result
     */
    public static TransferResult success(String transactionId, String transferReference) {
        return TransferResult.builder()
                .successful(true)
                .transactionId(transactionId)
                .transferReference(transferReference)
                .status("COMPLETED")
                .completedAt(Instant.now())
                .build();
    }
    
    /**
     * Creates a failed transfer result
     */
    public static TransferResult failed(String transactionId, String errorMessage) {
        return TransferResult.builder()
                .successful(false)
                .transactionId(transactionId)
                .errorMessage(errorMessage)
                .status("FAILED")
                .completedAt(Instant.now())
                .build();
    }
    
    /**
     * Creates a pending transfer result
     */
    public static TransferResult pending(String transactionId, String transferReference) {
        return TransferResult.builder()
                .successful(false) // Not yet successful
                .transactionId(transactionId)
                .transferReference(transferReference)
                .status("PENDING")
                .initiatedAt(Instant.now())
                .build();
    }
}