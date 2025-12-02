package com.waqiti.transaction.client;

import com.waqiti.common.exception.ServiceUnavailableException;
import com.waqiti.transaction.dto.WalletOwnershipResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback implementation for WalletServiceClient.
 *
 * This fallback is triggered when:
 * - Wallet service is unavailable
 * - Circuit breaker is open
 * - Request times out
 *
 * Security Strategy: Fail-secure approach
 * - Deny all ownership validations when service is down
 * - Throw exceptions for critical operations
 * - Prevent unauthorized access during outages
 *
 * @author Waqiti Platform Team
 * @since 1.0.0
 */
@Component
@Slf4j
public class WalletServiceClientFallback implements WalletServiceClient {

    /**
     * Fallback for wallet ownership check.
     *
     * SECURITY: Fail-secure - returns false (no ownership) when service is unavailable.
     * This prevents unauthorized access if the wallet service is down.
     *
     * @param walletId The wallet ID
     * @param username The username
     * @return WalletOwnershipResponse indicating no ownership (fail-secure)
     */
    @Override
    public WalletOwnershipResponse checkWalletOwnership(String walletId, String username) {
        log.error("Wallet service unavailable - denying ownership check (fail-secure): walletId={}, username={}",
                  walletId, username);

        // CRITICAL SECURITY: Fail secure - deny ownership when service is down
        return WalletOwnershipResponse.builder()
            .walletId(walletId)
            .username(username)
            .isOwner(false) // DENY access
            .hasAccess(false) // DENY access
            .accessType("NONE")
            .walletStatus("SERVICE_UNAVAILABLE")
            .isActive(false)
            .build();
    }

    /**
     * Fallback for wallet existence check.
     *
     * Throws exception to fail-fast when service is unavailable.
     *
     * @param walletId The wallet ID
     * @throws ServiceUnavailableException always (fail-fast)
     */
    @Override
    public void checkWalletExists(String walletId) {
        log.error("Wallet service unavailable - cannot verify wallet existence: walletId={}", walletId);

        throw new ServiceUnavailableException(
            "Wallet service is currently unavailable. Please try again later.",
            "WALLET_SERVICE_UNAVAILABLE"
        );
    }

    /**
     * Fallback for wallet status check.
     *
     * @param walletId The wallet ID
     * @return "SERVICE_UNAVAILABLE" status
     */
    @Override
    public String getWalletStatus(String walletId) {
        log.error("Wallet service unavailable - cannot retrieve wallet status: walletId={}", walletId);
        return "SERVICE_UNAVAILABLE";
    }
}
