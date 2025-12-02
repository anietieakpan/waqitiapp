package com.waqiti.payment.core.strategy;

import com.waqiti.payment.core.model.*;
import com.waqiti.payment.core.provider.PaymentProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Strategy for cryptocurrency payments
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CryptoPaymentStrategy implements PaymentStrategy {

    private final Map<ProviderType, PaymentProvider> paymentProviders;

    @Override
    public PaymentResult executePayment(PaymentRequest request) {
        log.info("Executing crypto payment: from={}, to={}, amount={}, currency={}", 
                request.getFromUserId(), request.getToUserId(), request.getAmount(),
                request.getMetadata().get("cryptoCurrency"));
        
        try {
            // Validate crypto payment metadata
            if (!hasRequiredCryptoMetadata(request)) {
                return PaymentResult.error("Missing required crypto payment metadata");
            }
            
            // Check network fees and confirmation times
            if (!validateNetworkConditions(request)) {
                return PaymentResult.error("Network conditions not suitable for crypto payment");
            }
            
            PaymentProvider provider = paymentProviders.get(request.getProviderType());
            if (provider == null) {
                return PaymentResult.error("Crypto provider not available: " + request.getProviderType());
            }
            
            // Process crypto payment
            PaymentResult result = provider.processPayment(request);
            
            // Monitor transaction on blockchain if successful
            if (result.isSuccess()) {
                monitorBlockchainTransaction(request, result);
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("Crypto payment failed: ", e);
            return PaymentResult.error("Crypto payment failed: " + e.getMessage());
        }
    }

    @Override
    public PaymentType getPaymentType() {
        return PaymentType.CRYPTO;
    }

    @Override
    public boolean canHandle(PaymentRequest request) {
        return request.getType() == PaymentType.CRYPTO;
    }

    @Override
    public int getPriority() {
        return 5; // Lower priority due to volatility and complexity
    }
    
    private boolean hasRequiredCryptoMetadata(PaymentRequest request) {
        Map<String, Object> metadata = request.getMetadata();
        return metadata != null && 
               metadata.containsKey("cryptoCurrency") && 
               metadata.containsKey("walletAddress") &&
               metadata.containsKey("networkType");
    }
    
    private boolean validateNetworkConditions(PaymentRequest request) {
        // In production, this would check network fees and congestion
        String currency = (String) request.getMetadata().get("cryptoCurrency");
        BigDecimal amount = request.getAmount();
        
        // Example validation: minimum amount for crypto transactions
        BigDecimal minAmount = new BigDecimal("10.00");
        return amount.compareTo(minAmount) >= 0;
    }
    
    private void monitorBlockchainTransaction(PaymentRequest request, PaymentResult result) {
        log.info("Starting blockchain monitoring for transaction: {} on network: {}", 
                result.getTransactionId(), request.getMetadata().get("networkType"));
        // In production, this would monitor the transaction on the blockchain
    }
}