package com.waqiti.virtualcard.dto;

import com.waqiti.virtualcard.domain.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Response DTO for card transactions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardTransactionResponse {
    
    private String transactionId;
    
    private boolean approved;
    
    private TransactionStatus status;
    
    private String authorizationCode;
    
    private String responseCode;
    
    private String message;
    
    private String declineReason;
    
    private BigDecimal amount;
    
    private String currency;
    
    private BigDecimal balance;
    
    private BigDecimal availableBalance;
    
    private LocalDateTime timestamp;
    
    private String merchantName;
    
    private String merchantId;
    
    private String merchantCategory;
    
    private String terminalId;
    
    private String retrievalReference;
    
    private String networkReference;
    
    private Integer riskScore;
    
    private Integer fraudScore;
    
    private boolean mfaRequired;
    
    private String mfaChallenge;
    
    private Map<String, String> metadata;
    
    /**
     * Creates a declined transaction response
     */
    public static CardTransactionResponse declined(String responseCode, String message) {
        return CardTransactionResponse.builder()
            .approved(false)
            .status(TransactionStatus.DECLINED)
            .responseCode(responseCode)
            .message(message)
            .declineReason(message)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * Creates an approved transaction response
     */
    public static CardTransactionResponse approved(String transactionId, String authCode) {
        return CardTransactionResponse.builder()
            .transactionId(transactionId)
            .approved(true)
            .status(TransactionStatus.APPROVED)
            .authorizationCode(authCode)
            .message("Transaction approved")
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * Creates a pending transaction response
     */
    public static CardTransactionResponse pending(String transactionId, String message) {
        return CardTransactionResponse.builder()
            .transactionId(transactionId)
            .approved(false)
            .status(TransactionStatus.PENDING)
            .message(message)
            .timestamp(LocalDateTime.now())
            .build();
    }
}