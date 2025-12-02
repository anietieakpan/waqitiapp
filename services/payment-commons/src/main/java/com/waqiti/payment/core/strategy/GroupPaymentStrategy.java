package com.waqiti.payment.core.strategy;

import com.waqiti.payment.core.model.*;
import com.waqiti.payment.core.provider.PaymentProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Strategy for group payments (split bills, group expenses)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class GroupPaymentStrategy implements PaymentStrategy {

    private final Map<ProviderType, PaymentProvider> paymentProviders;

    @Override
    public PaymentResult executePayment(PaymentRequest request) {
        log.info("Executing group payment: from={}, amount={}, participants={}", 
                request.getFromUserId(), request.getAmount(), 
                request.getMetadata().get("participantCount"));
        
        try {
            // Validate group payment metadata
            if (!hasRequiredGroupMetadata(request)) {
                return PaymentResult.error("Missing required group payment metadata");
            }
            
            PaymentProvider provider = paymentProviders.get(request.getProviderType());
            if (provider == null) {
                return PaymentResult.error("Provider not available: " + request.getProviderType());
            }
            
            // Calculate split amounts
            BigDecimal splitAmount = calculateSplitAmount(request);
            
            // Process group payment
            return provider.processPayment(request);
            
        } catch (Exception e) {
            log.error("Group payment failed: ", e);
            return PaymentResult.error("Group payment failed: " + e.getMessage());
        }
    }

    @Override
    public PaymentType getPaymentType() {
        return PaymentType.GROUP;
    }

    @Override
    public boolean canHandle(PaymentRequest request) {
        return request.getType() == PaymentType.GROUP;
    }

    @Override
    public int getPriority() {
        return 7; // Medium priority
    }
    
    private boolean hasRequiredGroupMetadata(PaymentRequest request) {
        Map<String, Object> metadata = request.getMetadata();
        return metadata != null && 
               metadata.containsKey("participants") && 
               metadata.containsKey("splitType");
    }
    
    private BigDecimal calculateSplitAmount(PaymentRequest request) {
        Map<String, Object> metadata = request.getMetadata();
        @SuppressWarnings("unchecked")
        List<String> participants = (List<String>) metadata.get("participants");
        
        if (participants == null || participants.isEmpty()) {
            return request.getAmount();
        }
        
        return request.getAmount().divide(new BigDecimal(participants.size()), 2, java.math.RoundingMode.HALF_UP);
    }
}