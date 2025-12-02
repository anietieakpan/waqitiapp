package com.waqiti.transaction.client;

import com.waqiti.transaction.dto.WalletOwnershipResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * Feign client for Wallet Service integration.
 *
 * This client provides integration with the wallet-service for:
 * - Wallet ownership validation
 * - Wallet existence checks
 * - Wallet status queries
 *
 * Features:
 * - Circuit breaker protection via Resilience4j
 * - Automatic service discovery via Eureka
 * - Retry logic with exponential backoff
 * - Fallback for graceful degradation
 *
 * @author Waqiti Platform Team
 * @since 1.0.0
 */
@FeignClient(
    name = "wallet-service",
    path = "/api/v1/wallets",
    fallback = WalletServiceClientFallback.class,
    configuration = WalletServiceClientConfiguration.class
)
public interface WalletServiceClient {

    /**
     * Checks if a user owns a specific wallet.
     *
     * @param walletId The wallet ID to check
     * @param username The username to validate ownership for
     * @return WalletOwnershipResponse with ownership details
     */
    @GetMapping("/{walletId}/ownership")
    WalletOwnershipResponse checkWalletOwnership(
        @PathVariable("walletId") String walletId,
        @RequestParam("username") String username
    );

    /**
     * Checks if a wallet exists in the system.
     *
     * @param walletId The wallet ID to check
     * @throws com.waqiti.common.exception.ResourceNotFoundException if wallet doesn't exist
     */
    @GetMapping("/{walletId}/exists")
    void checkWalletExists(@PathVariable("walletId") String walletId);

    /**
     * Gets wallet status.
     *
     * @param walletId The wallet ID
     * @return Wallet status (ACTIVE, SUSPENDED, CLOSED, FROZEN)
     */
    @GetMapping("/{walletId}/status")
    String getWalletStatus(@PathVariable("walletId") String walletId);
}
