package com.waqiti.payment.ach;

import com.waqiti.payment.entity.PaymentTransaction;
import com.waqiti.payment.dto.PaymentRequest;
import com.waqiti.payment.dto.PaymentResponse;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for ACH (Automated Clearing House) Transfer Service
 * Handles bank-to-bank transfers and direct deposits
 */
public interface ACHTransferService {
    
    /**
     * Initiate an ACH transfer
     */
    CompletableFuture<PaymentResponse> initiateTransfer(PaymentRequest request);
    
    /**
     * Verify bank account for ACH transfers
     */
    CompletableFuture<Boolean> verifyBankAccount(String accountNumber, String routingNumber);
    
    /**
     * Process direct deposit
     */
    CompletableFuture<PaymentResponse> processDirectDeposit(
            UUID userId, 
            String accountNumber, 
            String routingNumber, 
            BigDecimal amount
    );
    
    /**
     * Check ACH transfer status
     */
    CompletableFuture<String> getTransferStatus(String transferId);
    
    /**
     * Cancel pending ACH transfer
     */
    CompletableFuture<Boolean> cancelTransfer(String transferId);
    
    /**
     * Get ACH transfer details
     */
    CompletableFuture<Map<String, Object>> getTransferDetails(String transferId);
    
    /**
     * Validate ACH routing number
     */
    boolean validateRoutingNumber(String routingNumber);
    
    /**
     * Get estimated transfer time
     */
    int getEstimatedTransferDays(String transferType);
    
    /**
     * Process ACH return/reversal
     */
    CompletableFuture<PaymentResponse> processReturn(String originalTransferId, String returnCode);
    
    /**
     * Get ACH transfer limits
     */
    Map<String, BigDecimal> getTransferLimits(UUID userId);
}