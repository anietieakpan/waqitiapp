package com.waqiti.billingorchestrator.client;

import com.waqiti.common.api.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Fallback implementation for WalletServiceClient
 *
 * @author Waqiti Billing Team
 * @version 1.0
 * @since 2025-10-17
 */
@Component
@Slf4j
public class WalletServiceClientFallback implements WalletServiceClient {

    @Override
    public ApiResponse<WalletBalanceResponse> getBalance(UUID walletId, String currency) {
        log.error("Wallet service unavailable - cannot retrieve balance: walletId={}", walletId);
        return ApiResponse.error("WALLET_SERVICE_UNAVAILABLE",
                "Wallet service is temporarily unavailable");
    }

    @Override
    public ApiResponse<Boolean> hasSufficientBalance(UUID walletId, BigDecimal amount, String currency) {
        log.error("Wallet service unavailable - cannot check balance: walletId={}", walletId);
        // Fail-safe: don't proceed with billing if wallet service is down
        return ApiResponse.error("WALLET_SERVICE_UNAVAILABLE",
                "Wallet service is temporarily unavailable");
    }
}
