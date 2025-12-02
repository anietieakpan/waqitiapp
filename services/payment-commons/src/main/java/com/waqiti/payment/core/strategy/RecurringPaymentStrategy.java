package com.waqiti.payment.core.strategy;

import com.waqiti.payment.core.model.*;
import com.waqiti.payment.core.provider.PaymentProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Strategy for recurring payments
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RecurringPaymentStrategy implements PaymentStrategy {

    private final Map<ProviderType, PaymentProvider> paymentProviders;

    @Override
    public PaymentResult executePayment(PaymentRequest request) {
        log.info("Executing recurring payment: from={}, amount={}, frequency={}", 
                request.getFromUserId(), request.getAmount(), request.getMetadata().get("frequency"));
        
        try {
            // Validate recurring payment metadata
            if (!hasRequiredRecurringMetadata(request)) {
                return PaymentResult.error("Missing required recurring payment metadata");
            }
            
            PaymentProvider provider = paymentProviders.get(request.getProviderType());
            if (provider == null) {
                return PaymentResult.error("Provider not available: " + request.getProviderType());
            }
            
            // Process recurring payment
            PaymentResult result = provider.processPayment(request);
            
            // Schedule next payment if successful
            if (result.isSuccess()) {
                scheduleNextPayment(request);
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("Recurring payment failed: ", e);
            return PaymentResult.error("Recurring payment failed: " + e.getMessage());
        }
    }

    @Override
    public PaymentType getPaymentType() {
        return PaymentType.RECURRING;
    }

    @Override
    public boolean canHandle(PaymentRequest request) {
        return request.getType() == PaymentType.RECURRING;
    }

    @Override
    public int getPriority() {
        return 8; // Medium-high priority
    }
    
    private boolean hasRequiredRecurringMetadata(PaymentRequest request) {
        Map<String, Object> metadata = request.getMetadata();
        return metadata != null && 
               metadata.containsKey("frequency") && 
               metadata.containsKey("nextPaymentDate");
    }
    
    private void scheduleNextPayment(PaymentRequest request) {
        // In production, this would schedule the next payment
        log.info("Scheduling next recurring payment for paymentId={}", request.getPaymentId());
    }
}