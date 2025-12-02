package com.waqiti.payment.core.strategy;

import com.waqiti.payment.core.model.*;
import com.waqiti.payment.core.provider.PaymentProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Strategy for merchant payments (POS, online checkout)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MerchantPaymentStrategy implements PaymentStrategy {

    private final Map<ProviderType, PaymentProvider> paymentProviders;

    @Override
    public PaymentResult executePayment(PaymentRequest request) {
        log.info("Executing merchant payment: from={}, merchant={}, amount={}", 
                request.getFromUserId(), request.getToUserId(), request.getAmount());
        
        try {
            // Validate merchant payment metadata
            if (!hasRequiredMerchantMetadata(request)) {
                return PaymentResult.error("Missing required merchant payment metadata");
            }
            
            // Verify merchant status
            if (!verifyMerchantStatus(request)) {
                return PaymentResult.error("Merchant not verified or inactive");
            }
            
            PaymentProvider provider = paymentProviders.get(request.getProviderType());
            if (provider == null) {
                return PaymentResult.error("Provider not available: " + request.getProviderType());
            }
            
            // Process merchant payment
            PaymentResult result = provider.processPayment(request);
            
            // Update merchant analytics if successful
            if (result.isSuccess()) {
                updateMerchantAnalytics(request, result);
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("Merchant payment failed: ", e);
            return PaymentResult.error("Merchant payment failed: " + e.getMessage());
        }
    }

    @Override
    public PaymentType getPaymentType() {
        return PaymentType.MERCHANT;
    }

    @Override
    public boolean canHandle(PaymentRequest request) {
        return request.getType() == PaymentType.MERCHANT;
    }

    @Override
    public int getPriority() {
        return 9; // High priority for business transactions
    }
    
    private boolean hasRequiredMerchantMetadata(PaymentRequest request) {
        Map<String, Object> metadata = request.getMetadata();
        return metadata != null && 
               metadata.containsKey("merchantId") && 
               metadata.containsKey("orderId");
    }
    
    private boolean verifyMerchantStatus(PaymentRequest request) {
        // In production, this would verify merchant is active and verified
        String merchantId = (String) request.getMetadata().get("merchantId");
        return merchantId != null && !merchantId.isEmpty();
    }
    
    private void updateMerchantAnalytics(PaymentRequest request, PaymentResult result) {
        log.info("Updating merchant analytics for payment: {} -> merchant: {}", 
                request.getPaymentId(), request.getMetadata().get("merchantId"));
        // In production, this would update merchant transaction analytics
    }
}