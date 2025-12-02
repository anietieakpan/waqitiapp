package com.waqiti.user.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * Feign Client for Transaction Service
 */
@FeignClient(
    name = "transaction-service",
    fallback = TransactionServiceClientFallback.class
)
public interface TransactionServiceClient {

    @GetMapping("/actuator/health")
    Boolean healthCheck();

    @PostMapping("/api/v1/transactions/anonymize")
    void anonymizeUserTransactions(
        @RequestParam("userId") UUID userId,
        @RequestParam("anonymizedId") String anonymizedId
    );
}
