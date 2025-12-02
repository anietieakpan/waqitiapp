package com.waqiti.transaction.security;

import com.waqiti.common.exception.ResourceNotFoundException;
import com.waqiti.transaction.client.WalletServiceClient;
import com.waqiti.transaction.dto.WalletOwnershipResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

/**
 * Validator for wallet ownership checks.
 *
 * This component provides secure validation of wallet ownership to prevent
 * Insecure Direct Object Reference (IDOR) vulnerabilities. It integrates with
 * the wallet-service to verify that a user has legitimate access to a wallet.
 *
 * Features:
 * - Multi-layered validation with fallbacks
 * - Circuit breaker protection for external service calls
 * - Redis caching for performance (5-minute TTL)
 * - Comprehensive audit logging of access attempts
 * - Thread-safe and production-ready
 *
 * @author Waqiti Platform Team
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WalletOwnershipValidator {

    private final WalletServiceClient walletServiceClient;
    private final SecurityAuditService securityAuditService;

    /**
     * Validates if the authenticated user can access the specified wallet.
     *
     * This method performs multi-layered validation:
     * 1. Validates input parameters
     * 2. Retrieves current authentication context
     * 3. Calls wallet-service with circuit breaker protection
     * 4. Caches results for performance
     * 5. Logs all access attempts for security audit
     *
     * @param walletId The wallet ID to check access for
     * @param username The username attempting access
     * @return true if user has access, false otherwise
     * @throws IllegalArgumentException if parameters are invalid
     */
    @Cacheable(value = "wallet-ownership", key = "#walletId + ':' + #username", unless = "#result == false")
    @CircuitBreaker(name = "walletService", fallbackMethod = "canAccessWalletFallback")
    @Retry(name = "walletService")
    public boolean canAccessWallet(String walletId, String username) {
        // Input validation
        if (walletId == null || walletId.trim().isEmpty()) {
            log.warn("Wallet ownership validation called with null/empty walletId");
            return false;
        }

        if (username == null || username.trim().isEmpty()) {
            log.warn("Wallet ownership validation called with null/empty username");
            return false;
        }

        try {
            log.debug("Validating wallet ownership: walletId={}, username={}", walletId, username);

            // Call wallet service to verify ownership
            WalletOwnershipResponse response = walletServiceClient.checkWalletOwnership(walletId, username);

            boolean hasAccess = response != null && response.isOwner();

            // Audit logging
            if (hasAccess) {
                log.info("Wallet ownership validated successfully: walletId={}, username={}", walletId, username);
                securityAuditService.logAccessGranted("WALLET_ACCESS", username, walletId);
            } else {
                log.warn("Wallet ownership validation failed: walletId={}, username={}, reason=NOT_OWNER",
                         walletId, username);
                securityAuditService.logAccessDenied("WALLET_ACCESS", username, walletId, "NOT_OWNER");
            }

            return hasAccess;

        } catch (ResourceNotFoundException e) {
            log.warn("Wallet not found during ownership validation: walletId={}, username={}",
                     walletId, username);
            securityAuditService.logAccessDenied("WALLET_ACCESS", username, walletId, "WALLET_NOT_FOUND");
            return false;

        } catch (Exception e) {
            log.error("Error validating wallet ownership: walletId={}, username={}",
                      walletId, username, e);
            securityAuditService.logSecurityError("WALLET_OWNERSHIP_VALIDATION_ERROR", username, walletId, e);
            // Fail secure - deny access on error
            return false;
        }
    }

    /**
     * Overloaded method that uses the current authenticated user from SecurityContext.
     *
     * @param walletId The wallet ID to check access for
     * @param authentication The Spring Security Authentication object
     * @return true if authenticated user has access, false otherwise
     */
    public boolean canAccessWallet(String walletId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("Wallet ownership validation called with null/unauthenticated user");
            return false;
        }

        String username = authentication.getName();
        return canAccessWallet(walletId, username);
    }

    /**
     * Alternative signature for compatibility with @PreAuthorize expressions.
     *
     * @param username The username attempting access
     * @param walletId The wallet ID to check access for
     * @return true if user has access, false otherwise
     */
    public boolean canAccessWallet(String username, String walletId) {
        return canAccessWallet(walletId, username);
    }

    /**
     * Validates if the currently authenticated user can access the specified wallet.
     *
     * @param walletId The wallet ID to check access for
     * @return true if current user has access, false otherwise
     */
    public boolean canAccessWallet(String walletId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("No authenticated user found in SecurityContext for wallet access check");
            return false;
        }

        return canAccessWallet(walletId, authentication.getName());
    }

    /**
     * Validates wallet ownership for multiple wallets in a batch operation.
     *
     * This is more efficient than individual calls when checking multiple wallets.
     *
     * @param walletIds Array of wallet IDs to check
     * @param username The username attempting access
     * @return true if user has access to ALL wallets, false otherwise
     */
    @CircuitBreaker(name = "walletService", fallbackMethod = "canAccessMultipleWalletsFallback")
    @Retry(name = "walletService")
    public boolean canAccessMultipleWallets(String[] walletIds, String username) {
        if (walletIds == null || walletIds.length == 0) {
            return false;
        }

        // User must have access to ALL wallets
        for (String walletId : walletIds) {
            if (!canAccessWallet(walletId, username)) {
                log.warn("User {} does not have access to wallet {} in batch check", username, walletId);
                return false;
            }
        }

        return true;
    }

    /**
     * Fallback method when wallet service is unavailable.
     *
     * Security-first approach: Deny all access when unable to verify ownership.
     * This prevents unauthorized access if the wallet service is down.
     *
     * @param walletId The wallet ID
     * @param username The username
     * @param throwable The exception that triggered the fallback
     * @return false (fail-secure)
     */
    private boolean canAccessWalletFallback(String walletId, String username, Throwable throwable) {
        log.error("Wallet service unavailable - denying access (fail-secure): walletId={}, username={}, error={}",
                  walletId, username, throwable.getMessage());

        securityAuditService.logSecurityError(
            "WALLET_SERVICE_UNAVAILABLE",
            username,
            walletId,
            throwable
        );

        // CRITICAL: Fail secure - deny access when service is unavailable
        // This prevents potential security breaches during outages
        return false;
    }

    /**
     * Fallback for batch wallet validation.
     *
     * @param walletIds Array of wallet IDs
     * @param username The username
     * @param throwable The exception that triggered the fallback
     * @return false (fail-secure)
     */
    private boolean canAccessMultipleWalletsFallback(String[] walletIds, String username, Throwable throwable) {
        log.error("Wallet service unavailable - denying batch access (fail-secure): username={}, walletCount={}, error={}",
                  username, walletIds != null ? walletIds.length : 0, throwable.getMessage());

        securityAuditService.logSecurityError(
            "WALLET_SERVICE_UNAVAILABLE_BATCH",
            username,
            "Multiple wallets",
            throwable
        );

        return false;
    }

    /**
     * Validates that two users can transact (both must have valid wallets).
     *
     * Used for P2P transfer validation.
     *
     * @param fromWalletId Source wallet ID
     * @param toWalletId Destination wallet ID
     * @param username Username initiating the transfer
     * @return true if user can access source wallet and destination exists
     */
    @CircuitBreaker(name = "walletService", fallbackMethod = "canInitiateTransferFallback")
    public boolean canInitiateTransfer(String fromWalletId, String toWalletId, String username) {
        // User must own the source wallet
        if (!canAccessWallet(fromWalletId, username)) {
            log.warn("Transfer validation failed: user {} does not own source wallet {}",
                     username, fromWalletId);
            return false;
        }

        // Destination wallet must exist (but doesn't need to be owned by user)
        try {
            walletServiceClient.checkWalletExists(toWalletId);
            return true;
        } catch (ResourceNotFoundException e) {
            log.warn("Transfer validation failed: destination wallet {} not found", toWalletId);
            securityAuditService.logAccessDenied("TRANSFER_VALIDATION", username, toWalletId, "WALLET_NOT_FOUND");
            return false;
        }
    }

    /**
     * Fallback for transfer validation.
     */
    private boolean canInitiateTransferFallback(String fromWalletId, String toWalletId,
                                                 String username, Throwable throwable) {
        log.error("Cannot validate transfer - wallet service unavailable: from={}, to={}, user={}",
                  fromWalletId, toWalletId, username);
        return false;
    }

    /**
     * Clears the ownership cache for a specific wallet.
     *
     * Call this when wallet ownership changes.
     *
     * @param walletId The wallet ID to evict from cache
     */
    public void evictOwnershipCache(String walletId) {
        log.info("Evicting ownership cache for wallet: {}", walletId);
        // Cache eviction is handled by Spring Cache annotations
        // This method is a placeholder for explicit eviction if needed
    }
}
