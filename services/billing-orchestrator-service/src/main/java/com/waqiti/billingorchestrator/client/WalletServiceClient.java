package com.waqiti.billingorchestrator.client;

import com.waqiti.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Feign Client for Wallet Service
 *
 * Integrates with existing wallet-service microservice
 *
 * @author Waqiti Billing Team
 * @version 1.0
 * @since 2025-10-17
 */
@FeignClient(
    name = "wallet-service",
    path = "/api/v1/wallets",
    fallback = WalletServiceClientFallback.class
)
public interface WalletServiceClient {

    /**
     * Get wallet balance
     *
     * Calls: GET /api/v1/wallets/{walletId}/balance
     */
    @GetMapping("/{walletId}/balance")
    ApiResponse<WalletBalanceResponse> getBalance(
            @PathVariable UUID walletId,
            @RequestParam(defaultValue = "USD") String currency);

    /**
     * Check if wallet has sufficient balance
     *
     * Calls: GET /api/v1/wallets/{walletId}/check-balance
     */
    @GetMapping("/{walletId}/check-balance")
    ApiResponse<Boolean> hasSufficientBalance(
            @PathVariable UUID walletId,
            @RequestParam BigDecimal amount,
            @RequestParam String currency);

    /**
     * Wallet balance response DTO
     */
    record WalletBalanceResponse(
        UUID walletId,
        BigDecimal availableBalance,
        BigDecimal reservedBalance,
        BigDecimal totalBalance,
        String currency
    ) {}
}
