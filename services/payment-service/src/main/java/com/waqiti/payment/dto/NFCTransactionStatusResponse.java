package com.waqiti.payment.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response DTO for NFC transaction status
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NFCTransactionStatusResponse {

    private String transactionId;
    private String paymentId;
    private String transferId;
    private String status; // PENDING, PROCESSING, SUCCESS, FAILED, CANCELLED
    private String transactionType; // MERCHANT_PAYMENT, P2P_TRANSFER, CONTACT_EXCHANGE
    private BigDecimal amount;
    private String currency;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;
    
    // Participant information
    private String senderId;
    private String recipientId;
    private String merchantId;
    private String customerId;
    
    // Processing details
    private String processingStage;
    private Integer completionPercentage;
    private String estimatedCompletionTime;
    
    // Financial details
    private BigDecimal processingFee;
    private BigDecimal netAmount;
    private String feeBreakdown;
    
    // Security and compliance
    private String securityLevel;
    private boolean fraudCheckPassed;
    private String riskScore;
    private String complianceStatus;
    
    // NFC specific information
    private String nfcSessionId;
    private String nfcProtocolVersion;
    private boolean secureElementUsed;
    
    // Settlement information
    private Instant estimatedSettlement;
    private String settlementMethod;
    private String settlementStatus;
    
    // Error information (if any)
    private String errorCode;
    private String errorMessage;
    private String[] errorDetails;
    
    // Blockchain/audit trail
    private String blockchainTxHash;
    private String auditTrailId;
    
    // Receipt and documentation
    private String receiptUrl;
    private String invoiceUrl;
    private String confirmationCode;
    
    // Additional metadata
    private String metadata;

    /**
     * Checks if the transaction is in a final state
     */
    public boolean isFinalState() {
        return "SUCCESS".equals(status) || 
               "FAILED".equals(status) || 
               "CANCELLED".equals(status);
    }

    /**
     * Checks if the transaction is still processing
     */
    public boolean isProcessing() {
        return "PENDING".equals(status) || "PROCESSING".equals(status);
    }

    /**
     * Checks if the transaction was successful
     */
    public boolean isSuccessful() {
        return "SUCCESS".equals(status);
    }

    /**
     * Checks if the transaction failed
     */
    public boolean isFailed() {
        return "FAILED".equals(status);
    }

    /**
     * Checks if the transaction was cancelled
     */
    public boolean isCancelled() {
        return "CANCELLED".equals(status);
    }

    /**
     * Gets the processing duration in milliseconds
     */
    public long getProcessingDurationMs() {
        if (createdAt == null) {
            return 0L;
        }
        
        Instant endTime = completedAt != null ? completedAt : Instant.now();
        return endTime.toEpochMilli() - createdAt.toEpochMilli();
    }

    /**
     * Checks if fraud check was passed
     */
    public boolean passedFraudCheck() {
        return fraudCheckPassed;
    }

    /**
     * Gets estimated completion percentage
     */
    public int getCompletionPercentageOrDefault() {
        if (completionPercentage == null) {
            if (isFinalState()) {
                return 100;
            } else if ("PROCESSING".equals(status)) {
                return 50;
            } else {
                return 0;
            }
        }
        return Math.max(0, Math.min(100, completionPercentage));
    }
}