package com.waqiti.customer.client.fallback;

import com.waqiti.customer.client.WalletServiceClient;
import com.waqiti.customer.client.dto.WalletBalanceResponse;
import com.waqiti.customer.client.dto.WalletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Fallback implementation for WalletServiceClient.
 * Provides circuit breaker pattern implementation with safe default values
 * when wallet-service is unavailable or experiencing issues.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-20
 */
@Component
@Slf4j
public class WalletServiceClientFallback implements WalletServiceClient {

    @Override
    public WalletResponse getWallet(String walletId) {
        log.error("WalletServiceClient.getWallet fallback triggered for walletId: {}", walletId);
        return null;
    }

    @Override
    public List<WalletResponse> getWalletsByCustomerId(String customerId) {
        log.error("WalletServiceClient.getWalletsByCustomerId fallback triggered for customerId: {}", customerId);
        return Collections.emptyList();
    }

    @Override
    public WalletBalanceResponse getBalance(String walletId) {
        log.error("WalletServiceClient.getBalance fallback triggered for walletId: {}", walletId);
        return null;
    }

    @Override
    public void freezeWallet(String walletId) {
        log.error("WalletServiceClient.freezeWallet fallback triggered for walletId: {}. Wallet freeze operation failed.", walletId);
    }
}
