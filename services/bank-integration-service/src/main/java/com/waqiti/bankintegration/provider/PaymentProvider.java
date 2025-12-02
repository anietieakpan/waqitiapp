package com.waqiti.bankintegration.provider;

import com.waqiti.bankintegration.domain.*;
import com.waqiti.bankintegration.dto.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Interface for all payment provider implementations.
 * 
 * Defines the contract for payment processing, account management,
 * and provider-specific operations across different payment systems.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
public interface PaymentProvider {
    
    /**
     * Get provider name
     */
    String getName();
    
    /**
     * Get provider type
     */
    ProviderType getProviderType();
    
    /**
     * Initialize the provider
     */
    void initialize() throws Exception;
    
    /**
     * Shutdown the provider
     */
    void shutdown() throws Exception;
    
    /**
     * Emergency shutdown (immediate, may not be clean)
     */
    void emergencyShutdown();
    
    /**
     * Check if provider supports currency
     */
    boolean supportsCurrency(String currency);
    
    /**
     * Check if provider supports transaction amount
     */
    boolean supportsAmount(BigDecimal amount);
    
    /**
     * Validate bank account
     */
    AccountValidationResult validateAccount(String accountNumber, 
                                          String routingNumber, 
                                          String accountType);
    
    /**
     * Process payment
     */
    PaymentResult processPayment(PaymentProviderRequest request);
    
    /**
     * Get payment status
     */
    PaymentStatusInfo getPaymentStatus(String providerTransactionId);
    
    /**
     * Process refund
     */
    RefundResult processRefund(RefundProviderRequest request);
    
    /**
     * Get account balance
     */
    BalanceInfo getAccountBalance(BankAccount bankAccount);
    
    /**
     * Initiate micro deposits for verification
     */
    void initiateMicroDeposits(BankAccount bankAccount);
    
    /**
     * Verify micro deposits
     */
    boolean verifyMicroDeposits(BankAccount bankAccount, List<BigDecimal> amounts);
    
    /**
     * Get provider health status
     */
    ProviderHealthStatus getHealthStatus();
    
    /**
     * Get supported payment methods
     */
    List<String> getSupportedPaymentMethods();
    
    /**
     * Get supported currencies
     */
    List<String> getSupportedCurrencies();
    
    /**
     * Get minimum transaction amount
     */
    BigDecimal getMinTransactionAmount();
    
    /**
     * Get maximum transaction amount
     */
    BigDecimal getMaxTransactionAmount();
    
    /**
     * Get provider configuration
     */
    ProviderConfiguration getConfiguration();
    
    /**
     * Update provider configuration
     */
    void updateConfiguration(ProviderConfiguration configuration);
}