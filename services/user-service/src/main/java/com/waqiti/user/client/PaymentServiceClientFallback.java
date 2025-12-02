package com.waqiti.user.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class PaymentServiceClientFallback implements PaymentServiceClient {

    @Override
    public Boolean healthCheck() {
        log.warn("FALLBACK: payment-service health check failed");
        return false;
    }

    @Override
    public void anonymizeUserPayments(UUID userId, String anonymizedId) {
        log.error("FALLBACK: Cannot anonymize payments for user {} - payment-service unavailable",
                userId);
        throw new RuntimeException("Payment service unavailable - cannot complete anonymization");
    }
}
