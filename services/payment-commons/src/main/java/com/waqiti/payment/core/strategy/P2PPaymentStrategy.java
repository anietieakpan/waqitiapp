package com.waqiti.payment.core.strategy;

import com.waqiti.payment.core.model.*;
import com.waqiti.payment.core.provider.PaymentProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Strategy for peer-to-peer payments
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class P2PPaymentStrategy implements PaymentStrategy {

    private final Map<ProviderType, PaymentProvider> paymentProviders;

    @Override
    public PaymentResult executePayment(PaymentRequest request) {
        log.info("Executing P2P payment: from={}, to={}, amount={}", 
                request.getFromUserId(), request.getToUserId(), request.getAmount());
        
        try {
            // Get provider
            PaymentProvider provider = paymentProviders.get(request.getProviderType());
            if (provider == null) {
                return PaymentResult.error("Provider not available: " + request.getProviderType());
            }
            
            // Process payment
            return provider.processPayment(request);
            
        } catch (Exception e) {
            log.error("P2P payment failed: ", e);
            return PaymentResult.error("P2P payment failed: " + e.getMessage());
        }
    }

    @Override
    public PaymentType getPaymentType() {
        return PaymentType.P2P;
    }

    @Override
    public boolean canHandle(PaymentRequest request) {
        return request.getType() == PaymentType.P2P;
    }

    @Override
    public int getPriority() {
        return 10; // High priority for core feature
    }
}