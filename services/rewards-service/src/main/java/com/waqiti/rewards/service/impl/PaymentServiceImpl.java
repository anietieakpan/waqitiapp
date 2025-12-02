package com.waqiti.rewards.service.impl;

import com.waqiti.rewards.client.PaymentServiceClient;
import com.waqiti.rewards.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    
    private final PaymentServiceClient paymentServiceClient;
    
    @Override
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void creditWallet(String userId, BigDecimal amount, String currency, 
                           String description, Map<String, Object> metadata) {
        try {
            log.debug("Crediting wallet for user: {}, amount: {} {}", userId, amount, currency);
            
            paymentServiceClient.creditWallet(
                userId, 
                amount, 
                currency, 
                description, 
                metadata != null ? metadata : Map.of()
            );
            
            log.info("Successfully credited {} {} to wallet for user {}", 
                amount, currency, userId);
                
        } catch (Exception e) {
            log.error("Failed to credit wallet for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to credit wallet", e);
        }
    }
    
    @Override
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void debitWallet(String userId, BigDecimal amount, String currency, 
                          String description, Map<String, Object> metadata) {
        try {
            log.debug("Debiting wallet for user: {}, amount: {} {}", userId, amount, currency);
            
            paymentServiceClient.debitWallet(
                userId, 
                amount, 
                currency, 
                description, 
                metadata != null ? metadata : Map.of()
            );
            
            log.info("Successfully debited {} {} from wallet for user {}", 
                amount, currency, userId);
                
        } catch (Exception e) {
            log.error("Failed to debit wallet for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to debit wallet", e);
        }
    }
    
    @Override
    public BigDecimal getWalletBalance(String userId, String currency) {
        try {
            log.debug("Getting wallet balance for user: {}, currency: {}", userId, currency);
            
            BigDecimal balance = paymentServiceClient.getWalletBalance(userId, currency);
            
            log.debug("Wallet balance for user {}: {} {}", userId, balance, currency);
            return balance;
            
        } catch (Exception e) {
            log.error("Failed to get wallet balance for user {}: {}", userId, e.getMessage(), e);
            return BigDecimal.ZERO; // Return zero balance on error
        }
    }
    
    @Override
    public boolean hasSufficientBalance(String userId, BigDecimal amount, String currency) {
        try {
            BigDecimal balance = getWalletBalance(userId, currency);
            boolean sufficient = balance.compareTo(amount) >= 0;
            
            log.debug("Sufficient balance check for user {}: {} >= {} = {}", 
                userId, balance, amount, sufficient);
            
            return sufficient;
            
        } catch (Exception e) {
            log.error("Failed to check balance for user {}: {}", userId, e.getMessage(), e);
            return false; // Return false on error for safety
        }
    }
    
    @Override
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public String createPaymentTransaction(String userId, BigDecimal amount, String currency,
                                         String description, Map<String, Object> metadata) {
        try {
            log.debug("Creating payment transaction for user: {}, amount: {} {}", 
                userId, amount, currency);
            
            String transactionId = paymentServiceClient.createTransaction(
                userId, 
                amount, 
                currency, 
                description, 
                metadata != null ? metadata : Map.of()
            );
            
            log.info("Created payment transaction {} for user {}", transactionId, userId);
            return transactionId;
            
        } catch (Exception e) {
            log.error("Failed to create payment transaction for user {}: {}", 
                userId, e.getMessage(), e);
            throw new RuntimeException("Failed to create payment transaction", e);
        }
    }
    
    @Override
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void processRefund(String transactionId, BigDecimal amount, String reason) {
        try {
            log.debug("Processing refund for transaction: {}, amount: {}", transactionId, amount);
            
            paymentServiceClient.processRefund(transactionId, amount, reason);
            
            log.info("Successfully processed refund for transaction {}: {}", 
                transactionId, amount);
                
        } catch (Exception e) {
            log.error("Failed to process refund for transaction {}: {}", 
                transactionId, e.getMessage(), e);
            throw new RuntimeException("Failed to process refund", e);
        }
    }
    
    @Override
    public Map<String, Object> getTransactionDetails(String transactionId) {
        try {
            log.debug("Getting transaction details for: {}", transactionId);
            
            Map<String, Object> details = paymentServiceClient.getTransactionDetails(transactionId);
            
            log.debug("Retrieved transaction details for {}", transactionId);
            return details;
            
        } catch (Exception e) {
            log.error("Failed to get transaction details for {}: {}", 
                transactionId, e.getMessage(), e);
            return Map.of(); // Return empty map on error
        }
    }
}