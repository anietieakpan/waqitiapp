package com.waqiti.wallet.service;

import com.waqiti.common.exception.BusinessException;
import com.waqiti.wallet.domain.Wallet;
import com.waqiti.wallet.dto.WalletBalance;
import com.waqiti.wallet.repository.OptimizedWalletRepository;
import com.waqiti.common.client.CoreBankingServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Optimized wallet service with batch operations and async processing
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OptimizedWalletService {
    
    private final OptimizedWalletRepository walletRepository;
    private final CoreBankingServiceClient coreBankingServiceClient;
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(2);
    
    /**
     * Get user wallets with optimized queries and async balance updates
     */
    @Transactional(readOnly = true)
    public List<WalletBalance> getUserWalletsOptimized(UUID userId) {
        // Fetch wallets with user data in single query
        List<Wallet> wallets = walletRepository.findActiveWalletsByUserIdWithUser(userId);
        
        // Convert to DTOs
        List<WalletBalance> walletBalances = wallets.stream()
            .map(this::toWalletBalance)
            .collect(Collectors.toList());
        
        // Async balance update - non-blocking
        asyncUpdateBalances(wallets);
        
        return walletBalances;
    }
    
    /**
     * Async balance updates with batch processing.
     *
     * SECURITY FIX: Replaced Object[] with type-safe WalletBalanceUpdate to prevent
     * ClassCastException at runtime. All return paths now use proper type.
     */
    @Async
    public CompletableFuture<Void> asyncUpdateBalances(List<Wallet> wallets) {
        if (wallets.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        List<CompletableFuture<WalletBalanceUpdate>> futures = wallets.stream()
            .map(wallet -> CompletableFuture.supplyAsync(() -> {
                try {
                    // Remote balance sync removed - no longer using Cyclos
                    BigDecimal remoteBalance = wallet.getBalance();
                    if (wallet.getBalance().compareTo(remoteBalance) != 0) {
                        return new WalletBalanceUpdate(wallet.getId(), remoteBalance);
                    }
                    // No update needed - balance matches
                    return null;
                } catch (Exception e) {
                    log.error("Failed to fetch balance for wallet: {}", wallet.getId(), e);
                    // ✅ TYPE-SAFE: Return WalletBalanceUpdate with error status
                    return new WalletBalanceUpdate(wallet.getId(), BigDecimal.ZERO, "FETCH_ERROR");
                }
            }, executorService))
            .collect(Collectors.toList());

        // Wait for all balance checks to complete
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );

        return allFutures.thenAccept(v -> {
            // Collect successful updates only (filter out nulls and errors)
            List<WalletBalanceUpdate> successfulUpdates = futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .filter(WalletBalanceUpdate::isSuccess)
                .collect(Collectors.toList());

            // Track errors for monitoring
            long errorCount = futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .filter(WalletBalanceUpdate::isError)
                .count();

            if (errorCount > 0) {
                log.warn("Encountered {} errors during balance sync", errorCount);
            }

            // ✅ TYPE-SAFE: Convert to Object[] only for repository method
            List<Object[]> updates = successfulUpdates.stream()
                .map(update -> new Object[]{update.walletId, update.newBalance})
                .collect(Collectors.toList());

            // Batch update all balances in single query
            if (!updates.isEmpty()) {
                walletRepository.batchUpdateBalances(updates);
                log.info("Batch updated {} wallet balances", updates.size());
            }
        });
    }
    
    /**
     * Optimized balance check with caching
     */
    @Cacheable(value = "wallet-balances", key = "#walletId", unless = "#result == null")
    public BigDecimal getWalletBalance(UUID walletId) {
        return walletRepository.getBalanceById(walletId)
            .orElseThrow(() -> new BusinessException("Wallet not found"));
    }
    
    /**
     * Update wallet balance with optimistic locking and retry
     */
    @Transactional
    public void updateBalanceWithRetry(UUID walletId, BigDecimal amount, int maxRetries) {
        int attempts = 0;
        while (attempts < maxRetries) {
            try {
                Wallet wallet = walletRepository.findById(walletId)
                    .orElseThrow(() -> new BusinessException("Wallet not found"));
                
                int updated = walletRepository.updateBalanceOptimistic(
                    walletId, amount, LocalDateTime.now(), wallet.getVersion()
                );
                
                if (updated > 0) {
                    return; // Success
                }
                
                attempts++;
                if (attempts < maxRetries) {
                    // Exponential backoff
                    Thread.sleep((long) (Math.pow(2, attempts) * 10));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException("Balance update interrupted");
            }
        }
        throw new BusinessException("Failed to update balance after " + maxRetries + " attempts");
    }
    
    /**
     * Batch create wallets
     */
    @Transactional
    public void batchCreateWallets(List<CreateWalletRequest> requests) {
        List<Object[]> walletData = requests.stream()
            .map(req -> new Object[]{
                UUID.randomUUID(),
                generateWalletId(),
                req.getUserId(),
                req.getCurrency(),
                BigDecimal.ZERO,
                "ACTIVE",
                req.getWalletType(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                0L
            })
            .collect(Collectors.toList());
        
        walletRepository.batchInsertWallets(walletData);
    }
    
    /**
     * Scheduled reconciliation with batch processing
     */
    public void scheduleReconciliation() {
        scheduledExecutor.scheduleWithFixedDelay(() -> {
            try {
                reconcileWalletBatch();
            } catch (Exception e) {
                log.error("Reconciliation batch failed", e);
            }
        }, 1, 5, TimeUnit.MINUTES);
    }
    
    @Transactional
    protected void reconcileWalletBatch() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(30);
        List<Wallet> walletsToReconcile = walletRepository
            .findWalletsNeedingReconciliation(cutoffTime, 100);
        
        if (walletsToReconcile.isEmpty()) {
            return;
        }
        
        log.info("Starting reconciliation for {} wallets", walletsToReconcile.size());
        
        // Parallel reconciliation
        List<CompletableFuture<ReconciliationResult>> futures = walletsToReconcile.stream()
            .map(wallet -> CompletableFuture.supplyAsync(() -> 
                reconcileWallet(wallet), executorService))
            .collect(Collectors.toList());
        
        // Wait for completion and batch update
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenAccept(v -> {
                List<Object[]> updates = futures.stream()
                    .map(CompletableFuture::join)
                    .filter(result -> result.needsUpdate)
                    .map(result -> new Object[]{result.walletId, result.reconciledBalance})
                    .collect(Collectors.toList());
                
                if (!updates.isEmpty()) {
                    walletRepository.batchUpdateBalances(updates);
                    log.info("Reconciled {} wallets", updates.size());
                }
            });
    }
    
    private ReconciliationResult reconcileWallet(Wallet wallet) {
        try {
            // Get balance from Core Banking Service
            BigDecimal remoteBalance = coreBankingServiceClient.getWalletBalance(wallet.getId().toString());
            boolean needsUpdate = wallet.getBalance().compareTo(remoteBalance) != 0;
            return new ReconciliationResult(wallet.getId(), remoteBalance, needsUpdate);
        } catch (Exception e) {
            log.error("Failed to reconcile wallet: {}", wallet.getId(), e);
            return new ReconciliationResult(wallet.getId(), wallet.getBalance(), false);
        }
    }
    
    /**
     * Stream processing for large datasets
     */
    @Transactional(readOnly = true)
    public void processStaleWallets(LocalDateTime cutoffDate) {
        try (var walletStream = walletRepository.streamStaleWallets(cutoffDate)) {
            walletStream
                .parallel()
                .forEach(wallet -> {
                    // Process each wallet without loading all into memory
                    processStaleWallet(wallet);
                });
        }
    }
    
    private void processStaleWallet(Wallet wallet) {
        log.debug("Processing stale wallet: {}", wallet.getId());
        
        try {
            // Check if wallet is truly stale by verifying last activity
            LocalDateTime lastActivity = walletRepository.getLastActivityTime(wallet.getId());
            LocalDateTime threshold = LocalDateTime.now().minusHours(24);
            
            if (lastActivity != null && lastActivity.isBefore(threshold)) {
                log.info("Wallet {} is stale, last activity: {}", wallet.getId(), lastActivity);
                
                // Force balance reconciliation for stale wallets
                ReconciliationResult result = reconcileWallet(wallet);
                if (result.needsUpdate) {
                    walletRepository.updateBalance(wallet.getId(), result.reconciledBalance);
                    log.info("Updated stale wallet {} balance from {} to {}", 
                        wallet.getId(), wallet.getBalance(), result.reconciledBalance);
                }
                
                // Mark wallet as recently checked to avoid frequent processing
                walletRepository.updateLastCheckedTime(wallet.getId(), LocalDateTime.now());
                
                // Notify monitoring system about stale wallet
                CompletableFuture.runAsync(() -> {
                    try {
                        Map<String, Object> metadata = Map.of(
                            "walletId", wallet.getId(),
                            "lastActivity", lastActivity,
                            "oldBalance", wallet.getBalance(),
                            "newBalance", result.reconciledBalance
                        );
                        // Would normally publish to monitoring topic
                        log.info("Stale wallet processed: {}", metadata);
                    } catch (Exception e) {
                        log.error("Failed to notify monitoring system about stale wallet: {}", wallet.getId(), e);
                    }
                }, executorService);
            }
        } catch (Exception e) {
            log.error("Failed to process stale wallet: {}", wallet.getId(), e);
        }
    }
    
    private WalletBalance toWalletBalance(Wallet wallet) {
        return WalletBalance.builder()
            .walletId(wallet.getId().toString())
            .balance(wallet.getBalance())
            .currency(wallet.getCurrency())
            .status(wallet.getStatus().name())
            .build();
    }
    
    private String generateWalletId() {
        return "W" + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(1000, 9999);
    }
    
    // Inner classes for data transfer
    /**
     * Type-safe wallet balance update result.
     *
     * SECURITY FIX: Added optional status field to eliminate Object[] type safety issues.
     * Prevents ClassCastException at runtime.
     */
    private static class WalletBalanceUpdate {
        final UUID walletId;
        final BigDecimal newBalance;
        final String status;  // Optional: "SUCCESS", "FETCH_ERROR", "UNKNOWN_STATUS"

        WalletBalanceUpdate(UUID walletId, BigDecimal newBalance) {
            this.walletId = walletId;
            this.newBalance = newBalance;
            this.status = "SUCCESS";
        }

        WalletBalanceUpdate(UUID walletId, BigDecimal newBalance, String status) {
            this.walletId = walletId;
            this.newBalance = newBalance;
            this.status = status;
        }

        boolean isSuccess() {
            return "SUCCESS".equals(status);
        }

        boolean isError() {
            return "FETCH_ERROR".equals(status) || "UNKNOWN_STATUS".equals(status);
        }
    }
    
    private static class ReconciliationResult {
        final UUID walletId;
        final BigDecimal reconciledBalance;
        final boolean needsUpdate;
        
        ReconciliationResult(UUID walletId, BigDecimal reconciledBalance, boolean needsUpdate) {
            this.walletId = walletId;
            this.reconciledBalance = reconciledBalance;
            this.needsUpdate = needsUpdate;
        }
    }
    
    public static class CreateWalletRequest {
        private UUID userId;
        private String currency;
        private String walletType;
        
        // Getters and setters
        public UUID getUserId() { return userId; }
        public void setUserId(UUID userId) { this.userId = userId; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        public String getWalletType() { return walletType; }
        public void setWalletType(String walletType) { this.walletType = walletType; }
    }
}