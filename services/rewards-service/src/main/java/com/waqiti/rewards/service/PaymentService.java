package com.waqiti.rewards.service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Payment service interface for wallet operations
 */
public interface PaymentService {
    
    /**
     * Credit amount to user's wallet
     */
    void creditWallet(String userId, BigDecimal amount, String currency, 
                     String description, Map<String, Object> metadata);
    
    /**
     * Debit amount from user's wallet
     */
    void debitWallet(String userId, BigDecimal amount, String currency, 
                    String description, Map<String, Object> metadata);
    
    /**
     * Get user's wallet balance
     */
    BigDecimal getWalletBalance(String userId, String currency);
    
    /**
     * Check if user has sufficient balance
     */
    boolean hasSufficientBalance(String userId, BigDecimal amount, String currency);
    
    /**
     * Create payment transaction
     */
    String createPaymentTransaction(String userId, BigDecimal amount, String currency,
                                   String description, Map<String, Object> metadata);
    
    /**
     * Process refund
     */
    void processRefund(String transactionId, BigDecimal amount, String reason);
    
    /**
     * Get transaction details
     */
    Map<String, Object> getTransactionDetails(String transactionId);
}