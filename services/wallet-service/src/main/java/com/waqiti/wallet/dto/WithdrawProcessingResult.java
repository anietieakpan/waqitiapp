package com.waqiti.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Result DTO for withdrawal processing operations
 */
@Data
@AllArgsConstructor
public class WithdrawProcessingResult {
    private boolean successful;
    private String providerTransactionId;
    private String errorCode;
    private String errorMessage;
    
    public static WithdrawProcessingResult successful(String providerTransactionId) {
        return new WithdrawProcessingResult(true, providerTransactionId, null, null);
    }
    
    public static WithdrawProcessingResult failed(String errorCode, String errorMessage) {
        return new WithdrawProcessingResult(false, null, errorCode, errorMessage);
    }
}