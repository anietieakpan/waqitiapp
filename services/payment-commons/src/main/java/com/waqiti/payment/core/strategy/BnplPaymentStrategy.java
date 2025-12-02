package com.waqiti.payment.core.strategy;

import com.waqiti.payment.core.model.*;
import com.waqiti.payment.core.provider.PaymentProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Strategy for Buy Now Pay Later (BNPL) payments
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BnplPaymentStrategy implements PaymentStrategy {

    private final Map<ProviderType, PaymentProvider> paymentProviders;

    @Override
    public PaymentResult executePayment(PaymentRequest request) {
        log.info("Executing BNPL payment: from={}, amount={}, installments={}", 
                request.getFromUserId(), request.getAmount(), 
                request.getMetadata().get("installments"));
        
        try {
            // Validate BNPL metadata
            if (!hasRequiredBnplMetadata(request)) {
                return PaymentResult.error("Missing required BNPL metadata");
            }
            
            // Check credit limit
            if (!checkCreditLimit(request)) {
                return PaymentResult.error("Insufficient credit limit for BNPL");
            }
            
            PaymentProvider provider = paymentProviders.get(request.getProviderType());
            if (provider == null) {
                return PaymentResult.error("Provider not available: " + request.getProviderType());
            }
            
            // Process BNPL payment
            PaymentResult result = provider.processPayment(request);
            
            // Create installment schedule if successful
            if (result.isSuccess()) {
                createInstallmentSchedule(request);
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("BNPL payment failed: ", e);
            return PaymentResult.error("BNPL payment failed: " + e.getMessage());
        }
    }

    @Override
    public PaymentType getPaymentType() {
        return PaymentType.BNPL;
    }

    @Override
    public boolean canHandle(PaymentRequest request) {
        return request.getType() == PaymentType.BNPL;
    }

    @Override
    public int getPriority() {
        return 6; // Medium priority
    }
    
    private boolean hasRequiredBnplMetadata(PaymentRequest request) {
        Map<String, Object> metadata = request.getMetadata();
        return metadata != null && 
               metadata.containsKey("installments") && 
               metadata.containsKey("interestRate");
    }
    
    private boolean checkCreditLimit(PaymentRequest request) {
        // In production, this would check user's credit limit
        BigDecimal amount = request.getAmount();
        BigDecimal maxBnplAmount = new BigDecimal("5000.00"); // Example limit
        return amount.compareTo(maxBnplAmount) <= 0;
    }
    
    private void createInstallmentSchedule(PaymentRequest request) {
        log.info("Creating installment schedule for BNPL payment: {}", request.getPaymentId());
        // In production, this would create the installment schedule
    }
}