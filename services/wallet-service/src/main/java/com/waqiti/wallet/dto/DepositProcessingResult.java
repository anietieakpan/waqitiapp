package com.waqiti.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Result DTO for deposit processing operations
 */
@Data
@AllArgsConstructor
public class DepositProcessingResult {
    private boolean successful;
    private String provider;
    private String providerTransactionId;
    private BigDecimal netAmount;
    private String errorCode;
    private String errorMessage;
    
    public static DepositProcessingResult successful(String provider, String providerTransactionId) {
        return new DepositProcessingResult(true, provider, providerTransactionId, null, null, null);
    }
    
    public static DepositProcessingResult successful(String provider, String providerTransactionId, BigDecimal netAmount) {
        return new DepositProcessingResult(true, provider, providerTransactionId, netAmount, null, null);
    }
    
    public static DepositProcessingResult failed(String errorCode, String errorMessage) {
        return new DepositProcessingResult(false, null, null, null, errorCode, errorMessage);
    }
}