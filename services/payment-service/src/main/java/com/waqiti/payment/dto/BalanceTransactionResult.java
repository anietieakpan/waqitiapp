package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * DTO for balance transaction results
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BalanceTransactionResult {
    
    private boolean success;
    private String transactionId;
    private String customerId;
    private String accountId;
    
    private BigDecimal amount;
    private String currency;
    private String transactionType; // DEBIT, CREDIT
    
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private BigDecimal availableBalanceBefore;
    private BigDecimal availableBalanceAfter;
    
    private Instant processedAt;
    private String reference;
    private String description;
    
    private String status; // COMPLETED, FAILED, PENDING
    private String errorCode;
    private String errorMessage;
    private String failureReason;
    
    private Map<String, Object> metadata;
    
    /**
     * Creates a successful transaction result
     */
    public static BalanceTransactionResult successful(String transactionId, String customerId, 
                                                    BigDecimal amount, String currency, String type) {
        return BalanceTransactionResult.builder()
                .success(true)
                .transactionId(transactionId)
                .customerId(customerId)
                .amount(amount)
                .currency(currency)
                .transactionType(type)
                .status("COMPLETED")
                .processedAt(Instant.now())
                .build();
    }
    
    /**
     * Creates a failed transaction result
     */
    public static BalanceTransactionResult failed(String customerId, String errorMessage) {
        return BalanceTransactionResult.builder()
                .success(false)
                .customerId(customerId)
                .status("FAILED")
                .errorMessage(errorMessage)
                .build();
    }
}