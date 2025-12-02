package com.waqiti.payment.client.fallback;

import com.waqiti.common.resilience.AbstractFeignClientFallback;
import com.waqiti.payment.client.WalletServiceClient;
import com.waqiti.wallet.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * WALLET SERVICE FALLBACK
 *
 * Provides graceful degradation when wallet-service is unavailable.
 *
 * CRITICAL FINANCIAL OPERATION:
 * Wallet operations involve real money. When the service is down, we must:
 * - Maintain data consistency
 * - Queue updates for async processing
 * - Return cached balance data when safe
 * - Prevent duplicate transactions
 *
 * FALLBACK STRATEGY:
 * 1. For READ operations: Return cached data (last known good state)
 * 2. For WRITE operations: Queue for async processing + idempotency check
 * 3. For balance checks: Return cached balance with staleness indicator
 * 4. For critical operations: Fail fast to prevent data corruption
 *
 * DATA CONSISTENCY:
 * All wallet updates are queued with idempotency keys to prevent
 * duplicate processing when service recovers.
 *
 * @author Waqiti Payment Team
 * @version 1.0.0
 * @since 2025-01-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletServiceClientFallback
    extends AbstractFeignClientFallback
    implements WalletServiceClient {

    private final CacheManager cacheManager;
    private final AsyncWalletOperationQueue operationQueue;

    @Override
    protected String getServiceName() {
        return "wallet-service";
    }

    /**
     * Get wallet balance - return cached value
     */
    @Override
    public WalletBalanceResponse getBalance(UUID walletId) {
        logFallback("getBalance", null);

        // Try to get cached balance
        Optional<WalletBalanceResponse> cached = getCachedBalance(walletId);
        if (cached.isPresent()) {
            WalletBalanceResponse response = cached.get();
            response.setStale(true);
            response.setMessage("Wallet service unavailable - using cached balance");
            logCacheHit("getBalance", response);

            log.info("FALLBACK: Returning cached balance - WalletId: {}, Balance: ${}, Age: {} seconds",
                walletId, response.getAvailableBalance(),
                getCacheAge(walletId));

            return response;
        }

        // No cached data available - return error response
        log.error("FALLBACK: No cached balance available for wallet {}", walletId);
        WalletBalanceResponse errorResponse = new WalletBalanceResponse();
        errorResponse.setWalletId(walletId);
        errorResponse.setAvailableBalance(BigDecimal.ZERO);
        errorResponse.setError(true);
        errorResponse.setErrorMessage("Wallet service unavailable and no cached data");
        return errorResponse;
    }

    /**
     * Debit wallet - queue for async processing
     */
    @Override
    public WalletOperationResponse debit(UUID walletId, DebitRequest request) {
        logFallback("debit", null);

        // Queue operation for async processing
        AsyncWalletOperation operation = AsyncWalletOperation.builder()
            .operationType("DEBIT")
            .walletId(walletId)
            .amount(request.getAmount())
            .transactionId(request.getTransactionId())
            .idempotencyKey(request.getIdempotencyKey())
            .description(request.getDescription())
            .metadata(request.getMetadata())
            .queuedAt(java.time.LocalDateTime.now())
            .priority("HIGH") // Financial operations are high priority
            .build();

        operationQueue.add(operation);
        logQueuedOperation("debit", operation);

        log.warn("FALLBACK: Debit operation queued - WalletId: {}, Amount: ${}, TransactionId: {}",
            walletId, request.getAmount(), request.getTransactionId());

        // Return pending response
        WalletOperationResponse response = new WalletOperationResponse();
        response.setWalletId(walletId);
        response.setOperationId(UUID.randomUUID());
        response.setStatus("PENDING");
        response.setMessage("Wallet service unavailable - operation queued for processing");
        response.setPendingProcessing(true);
        response.setQueuedAt(java.time.LocalDateTime.now());

        return response;
    }

    /**
     * Credit wallet - queue for async processing
     */
    @Override
    public WalletOperationResponse credit(UUID walletId, CreditRequest request) {
        logFallback("credit", null);

        // Queue operation for async processing
        AsyncWalletOperation operation = AsyncWalletOperation.builder()
            .operationType("CREDIT")
            .walletId(walletId)
            .amount(request.getAmount())
            .transactionId(request.getTransactionId())
            .idempotencyKey(request.getIdempotencyKey())
            .description(request.getDescription())
            .metadata(request.getMetadata())
            .queuedAt(java.time.LocalDateTime.now())
            .priority("HIGH")
            .build();

        operationQueue.add(operation);
        logQueuedOperation("credit", operation);

        log.warn("FALLBACK: Credit operation queued - WalletId: {}, Amount: ${}, TransactionId: {}",
            walletId, request.getAmount(), request.getTransactionId());

        // Return pending response
        WalletOperationResponse response = new WalletOperationResponse();
        response.setWalletId(walletId);
        response.setOperationId(UUID.randomUUID());
        response.setStatus("PENDING");
        response.setMessage("Wallet service unavailable - operation queued for processing");
        response.setPendingProcessing(true);
        response.setQueuedAt(java.time.LocalDateTime.now());

        return response;
    }

    /**
     * Transfer between wallets - queue for async processing
     */
    @Override
    public WalletTransferResponse transfer(WalletTransferRequest request) {
        logFallback("transfer", null);

        // Queue transfer operation (atomic debit + credit)
        AsyncWalletOperation operation = AsyncWalletOperation.builder()
            .operationType("TRANSFER")
            .walletId(request.getSourceWalletId())
            .targetWalletId(request.getTargetWalletId())
            .amount(request.getAmount())
            .transactionId(request.getTransactionId())
            .idempotencyKey(request.getIdempotencyKey())
            .description(request.getDescription())
            .metadata(request.getMetadata())
            .queuedAt(java.time.LocalDateTime.now())
            .priority("CRITICAL") // Transfers are critical - must be atomic
            .build();

        operationQueue.add(operation);
        logQueuedOperation("transfer", operation);

        log.warn("FALLBACK: Transfer operation queued - From: {}, To: {}, Amount: ${}, TransactionId: {}",
            request.getSourceWalletId(), request.getTargetWalletId(),
            request.getAmount(), request.getTransactionId());

        // Return pending response
        WalletTransferResponse response = new WalletTransferResponse();
        response.setTransferId(UUID.randomUUID());
        response.setSourceWalletId(request.getSourceWalletId());
        response.setTargetWalletId(request.getTargetWalletId());
        response.setAmount(request.getAmount());
        response.setStatus("PENDING");
        response.setMessage("Wallet service unavailable - transfer queued for atomic processing");
        response.setPendingProcessing(true);
        response.setQueuedAt(java.time.LocalDateTime.now());

        return response;
    }

    /**
     * Get wallet details - return cached value
     */
    @Override
    public WalletDetailsResponse getWalletDetails(UUID walletId) {
        logFallback("getWalletDetails", null);

        // Try cache
        Optional<WalletDetailsResponse> cached = getCachedWalletDetails(walletId);
        if (cached.isPresent()) {
            WalletDetailsResponse response = cached.get();
            response.setStale(true);
            response.setMessage("Wallet service unavailable - using cached details");
            logCacheHit("getWalletDetails", response);
            return response;
        }

        // No cached data
        log.error("FALLBACK: No cached wallet details for {}", walletId);
        WalletDetailsResponse errorResponse = new WalletDetailsResponse();
        errorResponse.setWalletId(walletId);
        errorResponse.setError(true);
        errorResponse.setErrorMessage("Wallet service unavailable and no cached data");
        return errorResponse;
    }

    /**
     * Validate wallet ownership - fail fast (security critical)
     */
    @Override
    public boolean validateOwnership(UUID userId, UUID walletId) {
        logFallback("validateOwnership", null);

        // Security-critical operation - try cache but fail closed if unavailable
        Optional<Boolean> cached = getCachedOwnership(userId, walletId);
        if (cached.isPresent()) {
            logCacheHit("validateOwnership", cached.get());
            return cached.get();
        }

        // SECURITY: Fail closed when service unavailable and no cache
        log.error("FALLBACK: Cannot validate ownership - WalletId: {}, UserId: {} - DENYING ACCESS",
            walletId, userId);
        return false; // Fail closed for security
    }

    /**
     * Get cached balance
     */
    private Optional<WalletBalanceResponse> getCachedBalance(UUID walletId) {
        try {
            var cache = cacheManager.getCache("walletBalanceCache");
            if (cache != null) {
                WalletBalanceResponse cached = cache.get(walletId, WalletBalanceResponse.class);
                if (cached != null && isCacheValid(cached)) {
                    return Optional.of(cached);
                }
            }
        } catch (Exception e) {
            log.error("FALLBACK: Error accessing wallet balance cache", e);
        }
        return Optional.empty();
    }

    /**
     * Get cached wallet details
     */
    private Optional<WalletDetailsResponse> getCachedWalletDetails(UUID walletId) {
        try {
            var cache = cacheManager.getCache("walletDetailsCache");
            if (cache != null) {
                WalletDetailsResponse cached = cache.get(walletId, WalletDetailsResponse.class);
                if (cached != null) {
                    return Optional.of(cached);
                }
            }
        } catch (Exception e) {
            log.error("FALLBACK: Error accessing wallet details cache", e);
        }
        return Optional.empty();
    }

    /**
     * Get cached ownership validation
     */
    private Optional<Boolean> getCachedOwnership(UUID userId, UUID walletId) {
        try {
            var cache = cacheManager.getCache("walletOwnershipCache");
            if (cache != null) {
                String key = userId + "_" + walletId;
                Boolean cached = cache.get(key, Boolean.class);
                if (cached != null) {
                    return Optional.of(cached);
                }
            }
        } catch (Exception e) {
            log.error("FALLBACK: Error accessing ownership cache", e);
        }
        return Optional.empty();
    }

    /**
     * Check if cached balance is still valid (not too old)
     */
    private boolean isCacheValid(WalletBalanceResponse cached) {
        if (cached.getTimestamp() == null) {
            return false;
        }

        // Cache valid for 10 minutes
        long ageMinutes = java.time.Duration.between(
            cached.getTimestamp(),
            java.time.LocalDateTime.now()
        ).toMinutes();

        return ageMinutes < 10;
    }

    /**
     * Get cache age in seconds
     */
    private long getCacheAge(UUID walletId) {
        try {
            var cache = cacheManager.getCache("walletBalanceCache");
            if (cache != null) {
                WalletBalanceResponse cached = cache.get(walletId, WalletBalanceResponse.class);
                if (cached != null && cached.getTimestamp() != null) {
                    return java.time.Duration.between(
                        cached.getTimestamp(),
                        java.time.LocalDateTime.now()
                    ).toSeconds();
                }
            }
        } catch (Exception e) {
            log.error("FALLBACK: Error calculating cache age", e);
        }
        return 0;
    }

    /**
     * Async wallet operation for queueing
     */
    @lombok.Builder
    @lombok.Data
    private static class AsyncWalletOperation {
        private String operationType; // DEBIT, CREDIT, TRANSFER
        private UUID walletId;
        private UUID targetWalletId; // For transfers
        private BigDecimal amount;
        private UUID transactionId;
        private String idempotencyKey;
        private String description;
        private java.util.Map<String, String> metadata;
        private java.time.LocalDateTime queuedAt;
        private String priority; // HIGH, CRITICAL
    }
}
