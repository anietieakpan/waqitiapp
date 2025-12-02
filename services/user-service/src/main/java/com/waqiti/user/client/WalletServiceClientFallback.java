package com.waqiti.user.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Fallback implementation for WalletServiceClient
 * Provides degraded functionality when wallet-service is unavailable
 */
@Slf4j
@Component
public class WalletServiceClientFallback implements WalletServiceClient {

    @Override
    public Boolean healthCheck() {
        log.warn("FALLBACK: wallet-service health check failed");
        return false;
    }

    @Override
    public void anonymizeUserWallets(UUID userId, String anonymizedId) {
        log.error("FALLBACK: Cannot anonymize wallets for user {} - wallet-service unavailable",
                userId);
        throw new RuntimeException("Wallet service unavailable - cannot complete anonymization");
    }
}
