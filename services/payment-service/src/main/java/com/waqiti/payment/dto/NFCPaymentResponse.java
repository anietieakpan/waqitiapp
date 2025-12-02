package com.waqiti.payment.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response DTO for NFC payment operations
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NFCPaymentResponse {

    private String transactionId;
    private String paymentId;
    private String status;
    private BigDecimal amount;
    private String currency;
    private String merchantId;
    private String customerId;
    private Instant processedAt;
    private String receiptUrl;
    private String signature;
    
    // Processing details
    private String processingMethod;
    private Long processingTimeMs;
    private String authorizationCode;
    
    // Fee information
    private BigDecimal processingFee;
    private BigDecimal merchantFee;
    private BigDecimal netAmount;
    
    // Security information
    private String securityLevel;
    private boolean fraudCheckPassed;
    private String riskScore;
    
    // NFC specific fields
    private String nfcSessionId;
    private String nfcProtocolVersion;
    private boolean secureElementUsed;
    
    // Settlement information
    private Instant estimatedSettlement;
    private String settlementMethod;
    
    // Error information (if any)
    private String errorCode;
    private String errorMessage;
    
    // Additional metadata
    private String metadata;

    /**
     * Creates a successful payment response
     */
    public static NFCPaymentResponse success(String transactionId, String paymentId, 
                                           BigDecimal amount, String currency) {
        return NFCPaymentResponse.builder()
                .transactionId(transactionId)
                .paymentId(paymentId)
                .status("SUCCESS")
                .amount(amount)
                .currency(currency)
                .processedAt(Instant.now())
                .fraudCheckPassed(true)
                .build();
    }

    /**
     * Creates a failed payment response
     */
    public static NFCPaymentResponse failure(String paymentId, String errorCode, 
                                           String errorMessage) {
        return NFCPaymentResponse.builder()
                .paymentId(paymentId)
                .status("FAILED")
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .processedAt(Instant.now())
                .build();
    }

    /**
     * Creates a pending payment response
     */
    public static NFCPaymentResponse pending(String transactionId, String paymentId, 
                                           BigDecimal amount, String currency) {
        return NFCPaymentResponse.builder()
                .transactionId(transactionId)
                .paymentId(paymentId)
                .status("PENDING")
                .amount(amount)
                .currency(currency)
                .processedAt(Instant.now())
                .build();
    }

    /**
     * Checks if the payment was successful
     */
    public boolean isSuccessful() {
        return "SUCCESS".equals(status);
    }

    /**
     * Checks if the payment is pending
     */
    public boolean isPending() {
        return "PENDING".equals(status);
    }

    /**
     * Checks if the payment failed
     */
    public boolean isFailed() {
        return "FAILED".equals(status);
    }
}