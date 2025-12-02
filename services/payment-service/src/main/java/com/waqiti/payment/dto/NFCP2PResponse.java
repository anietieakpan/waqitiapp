package com.waqiti.payment.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response DTO for NFC P2P transfers
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NFCP2PResponse {

    private String transactionId;
    private String transferId;
    private String status;
    private BigDecimal amount;
    private String currency;
    private String senderId;
    private String recipientId;
    private String message;
    private Instant processedAt;
    private String signature;
    
    // Processing details
    private String processingMethod;
    private Long processingTimeMs;
    
    // Fee information
    private BigDecimal transferFee;
    private BigDecimal netAmount;
    
    // Security information
    private String securityLevel;
    private boolean fraudCheckPassed;
    private String riskScore;
    
    // NFC specific fields
    private String nfcSessionId;
    private String nfcProtocolVersion;
    private boolean bothDevicesSecure;
    
    // Settlement information
    private Instant estimatedDelivery;
    private String deliveryMethod;
    
    // Recipient information
    private String recipientName;
    private String recipientAvatar;
    
    // Error information (if any)
    private String errorCode;
    private String errorMessage;
    
    // Additional metadata
    private String metadata;

    /**
     * Creates a successful P2P transfer response
     */
    public static NFCP2PResponse success(String transactionId, String transferId, 
                                        BigDecimal amount, String currency,
                                        String senderId, String recipientId) {
        return NFCP2PResponse.builder()
                .transactionId(transactionId)
                .transferId(transferId)
                .status("SUCCESS")
                .amount(amount)
                .currency(currency)
                .senderId(senderId)
                .recipientId(recipientId)
                .processedAt(Instant.now())
                .fraudCheckPassed(true)
                .build();
    }

    /**
     * Creates a failed P2P transfer response
     */
    public static NFCP2PResponse failure(String transferId, String errorCode, 
                                        String errorMessage) {
        return NFCP2PResponse.builder()
                .transferId(transferId)
                .status("FAILED")
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .processedAt(Instant.now())
                .build();
    }

    /**
     * Creates a pending P2P transfer response
     */
    public static NFCP2PResponse pending(String transactionId, String transferId, 
                                        BigDecimal amount, String currency) {
        return NFCP2PResponse.builder()
                .transactionId(transactionId)
                .transferId(transferId)
                .status("PENDING")
                .amount(amount)
                .currency(currency)
                .processedAt(Instant.now())
                .build();
    }

    /**
     * Checks if the transfer was successful
     */
    public boolean isSuccessful() {
        return "SUCCESS".equals(status);
    }

    /**
     * Checks if the transfer is pending
     */
    public boolean isPending() {
        return "PENDING".equals(status);
    }

    /**
     * Checks if the transfer failed
     */
    public boolean isFailed() {
        return "FAILED".equals(status);
    }
}