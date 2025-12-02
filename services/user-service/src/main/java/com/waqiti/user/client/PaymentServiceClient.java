package com.waqiti.user.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * Feign Client for Payment Service
 */
@FeignClient(
    name = "payment-service",
    fallback = PaymentServiceClientFallback.class
)
public interface PaymentServiceClient {

    @GetMapping("/actuator/health")
    Boolean healthCheck();

    @PostMapping("/api/v1/payments/anonymize")
    void anonymizeUserPayments(
        @RequestParam("userId") UUID userId,
        @RequestParam("anonymizedId") String anonymizedId
    );
}
