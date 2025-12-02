package com.waqiti.user.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * Feign Client for Wallet Service
 *
 * Provides integration with wallet-service for:
 * - Wallet anonymization (GDPR Article 17)
 * - Health checks
 */
@FeignClient(
    name = "wallet-service",
    fallback = WalletServiceClientFallback.class
)
public interface WalletServiceClient {

    /**
     * Health check endpoint
     *
     * @return true if service is healthy
     */
    @GetMapping("/actuator/health")
    Boolean healthCheck();

    /**
     * Anonymize user wallets for GDPR compliance
     *
     * @param userId User ID to anonymize
     * @param anonymizedId Anonymized replacement ID
     */
    @PostMapping("/api/v1/wallets/anonymize")
    void anonymizeUserWallets(
        @RequestParam("userId") UUID userId,
        @RequestParam("anonymizedId") String anonymizedId
    );
}
