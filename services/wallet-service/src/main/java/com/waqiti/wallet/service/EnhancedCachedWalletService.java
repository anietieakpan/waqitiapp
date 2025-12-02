package com.waqiti.wallet.service;

import com.waqiti.wallet.domain.*;
import com.waqiti.wallet.dto.*;
import com.waqiti.wallet.repository.WalletRepository;
import com.waqiti.wallet.repository.TransactionRepository;
import com.waqiti.common.cache.FinancialCacheService;
import com.waqiti.common.cache.CacheEvictionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Enhanced Wallet Service with comprehensive caching strategy
 * Implements multi-level caching for wallet balances, metadata, and transaction history
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnhancedCachedWalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final FinancialCacheService financialCacheService;
    private final CacheEvictionService cacheEvictionService;
    private final WalletService baseWalletService;

    // ==================== WALLET BALANCE CACHING ====================

    /**
     * Get wallet balance with caching (1 minute TTL for consistency)
     */
    @Cacheable(value = "walletBalances", key = "#walletId", 
               condition = "#walletId != null",
               unless = "#result == null")
    @Transactional(readOnly = true)
    public WalletBalance getWalletBalance(UUID walletId) {
        log.debug("Getting wallet balance for wallet: {}", walletId);
        
        Wallet wallet = walletRepository.findById(walletId)
            .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + walletId));
        
        return WalletBalance.builder()
            .walletId(walletId)
            .balance(wallet.getBalance())
            .currency(wallet.getCurrency())
            .lastUpdated(wallet.getLastTransactionDate())
            .availableBalance(wallet.getAvailableBalance())
            .reservedAmount(wallet.getReservedAmount())
            .build();
    }

    /**
     * Get multiple wallet balances with batch optimization
     */
    @Cacheable(value = "walletBalances", key = "#walletIds")
    @Transactional(readOnly = true)
    public Map<UUID, WalletBalance> getBatchWalletBalances(Set<UUID> walletIds) {
        log.debug("Getting batch wallet balances for {} wallets", walletIds.size());
        
        List<Wallet> wallets = walletRepository.findAllById(walletIds);
        
        return wallets.stream()
            .collect(Collectors.toMap(
                Wallet::getId,
                wallet -> WalletBalance.builder()
                    .walletId(wallet.getId())
                    .balance(wallet.getBalance())
                    .currency(wallet.getCurrency())
                    .lastUpdated(wallet.getLastTransactionDate())
                    .availableBalance(wallet.getAvailableBalance())
                    .reservedAmount(wallet.getReservedAmount())
                    .build()
            ));
    }

    /**
     * Update wallet balance and invalidate cache
     */
    @CacheEvict(value = "walletBalances", key = "#walletId")
    @Transactional
    public WalletBalance updateWalletBalance(UUID walletId, BigDecimal newBalance) {
        log.info("Updating wallet balance for wallet: {} to amount: {}", walletId, newBalance);
        
        Wallet wallet = walletRepository.findById(walletId)
            .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + walletId));
        
        wallet.setBalance(newBalance);
        wallet.setLastTransactionDate(LocalDateTime.now());
        wallet = walletRepository.save(wallet);
        
        // Build and cache the new balance
        WalletBalance balance = WalletBalance.builder()
            .walletId(walletId)
            .balance(wallet.getBalance())
            .currency(wallet.getCurrency())
            .lastUpdated(wallet.getLastTransactionDate())
            .availableBalance(wallet.getAvailableBalance())
            .reservedAmount(wallet.getReservedAmount())
            .build();
        
        // Manually update cache
        financialCacheService.putWalletBalance(walletId, balance);
        
        // Invalidate related caches
        invalidateWalletRelatedCaches(walletId);
        
        return balance;
    }

    // ==================== WALLET METADATA CACHING ====================

    /**
     * Get wallet metadata with caching (10 minutes TTL)
     */
    @Cacheable(value = "walletMetadata", key = "#walletId",
               condition = "#walletId != null",
               unless = "#result == null")
    @Transactional(readOnly = true)
    public WalletMetadata getWalletMetadata(UUID walletId) {
        log.debug("Getting wallet metadata for wallet: {}", walletId);
        
        Wallet wallet = walletRepository.findById(walletId)
            .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + walletId));
        
        return WalletMetadata.builder()
            .walletId(walletId)
            .userId(wallet.getUserId())
            .walletType(wallet.getWalletType())
            .currency(wallet.getCurrency())
            .status(wallet.getStatus())
            .createdAt(wallet.getCreatedAt())
            .isActive(wallet.isActive())
            .dailyLimit(wallet.getDailyLimit())
            .monthlyLimit(wallet.getMonthlyLimit())
            .build();
    }

    /**
     * Get wallet by user ID and currency with caching
     */
    @Cacheable(value = "walletMetadata", key = "'user_' + #userId + '_' + #currency",
               condition = "#userId != null and #currency != null",
               unless = "#result == null")
    @Transactional(readOnly = true)
    public Optional<WalletMetadata> getWalletByUserAndCurrency(UUID userId, String currency) {
        log.debug("Getting wallet for user: {} and currency: {}", userId, currency);
        
        Optional<Wallet> wallet = walletRepository.findByUserIdAndCurrency(userId, currency);
        
        return wallet.map(w -> WalletMetadata.builder()
            .walletId(w.getId())
            .userId(w.getUserId())
            .walletType(w.getWalletType())
            .currency(w.getCurrency())
            .status(w.getStatus())
            .createdAt(w.getCreatedAt())
            .isActive(w.isActive())
            .dailyLimit(w.getDailyLimit())
            .monthlyLimit(w.getMonthlyLimit())
            .build());
    }

    /**
     * Get all wallets for user with caching
     */
    @Cacheable(value = "walletMetadata", key = "'user_wallets_' + #userId",
               condition = "#userId != null",
               unless = "#result == null or #result.isEmpty()")
    @Transactional(readOnly = true)
    public List<WalletMetadata> getUserWallets(UUID userId) {
        log.debug("Getting all wallets for user: {}", userId);
        
        List<Wallet> wallets = walletRepository.findByUserId(userId);
        
        return wallets.stream()
            .map(wallet -> WalletMetadata.builder()
                .walletId(wallet.getId())
                .userId(wallet.getUserId())
                .walletType(wallet.getWalletType())
                .currency(wallet.getCurrency())
                .status(wallet.getStatus())
                .createdAt(wallet.getCreatedAt())
                .isActive(wallet.isActive())
                .dailyLimit(wallet.getDailyLimit())
                .monthlyLimit(wallet.getMonthlyLimit())
                .build())
            .collect(Collectors.toList());
    }

    // ==================== TRANSACTION HISTORY CACHING ====================

    /**
     * Get transaction history with caching (30 minutes TTL)
     */
    @Cacheable(value = "transactionHistory", 
               key = "#walletId + '_' + #pageable.pageNumber + '_' + #pageable.pageSize",
               condition = "#walletId != null",
               unless = "#result == null or #result.isEmpty()")
    @Transactional(readOnly = true)
    public Page<TransactionSummary> getTransactionHistory(UUID walletId, Pageable pageable) {
        log.debug("Getting transaction history for wallet: {}, page: {}", walletId, pageable.getPageNumber());
        
        Page<Transaction> transactions = transactionRepository.findByWalletIdOrderByCreatedAtDesc(walletId, pageable);
        
        return transactions.map(this::mapToTransactionSummary);
    }

    /**
     * Get recent transactions with caching (5 minutes TTL)
     */
    @Cacheable(value = "transactionHistory", key = "'recent_' + #walletId + '_' + #limit",
               condition = "#walletId != null",
               unless = "#result == null or #result.isEmpty()")
    @Transactional(readOnly = true)
    public List<TransactionSummary> getRecentTransactions(UUID walletId, int limit) {
        log.debug("Getting {} recent transactions for wallet: {}", limit, walletId);
        
        List<Transaction> transactions = transactionRepository.findTopNByWalletIdOrderByCreatedAtDesc(walletId, limit);
        
        return transactions.stream()
            .map(this::mapToTransactionSummary)
            .collect(Collectors.toList());
    }

    /**
     * Get transaction summary with caching
     */
    @Cacheable(value = "transactionSummaries", key = "#walletId + '_' + #startDate + '_' + #endDate",
               condition = "#walletId != null",
               unless = "#result == null")
    @Transactional(readOnly = true)
    public TransactionSummaryReport getTransactionSummary(UUID walletId, LocalDateTime startDate, LocalDateTime endDate) {
        log.debug("Getting transaction summary for wallet: {} from {} to {}", walletId, startDate, endDate);
        
        List<Transaction> transactions = transactionRepository.findByWalletIdAndCreatedAtBetween(walletId, startDate, endDate);
        
        BigDecimal totalIncoming = transactions.stream()
            .filter(t -> t.getType() == TransactionType.CREDIT)
            .map(Transaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalOutgoing = transactions.stream()
            .filter(t -> t.getType() == TransactionType.DEBIT)
            .map(Transaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return TransactionSummaryReport.builder()
            .walletId(walletId)
            .startDate(startDate)
            .endDate(endDate)
            .totalTransactions(transactions.size())
            .totalIncoming(totalIncoming)
            .totalOutgoing(totalOutgoing)
            .netAmount(totalIncoming.subtract(totalOutgoing))
            .build();
    }

    // ==================== WRITE OPERATIONS WITH CACHE INVALIDATION ====================

    /**
     * Create wallet with cache invalidation
     */
    @Caching(evict = {
        @CacheEvict(value = "walletMetadata", key = "'user_wallets_' + #request.userId"),
        @CacheEvict(value = "walletMetadata", key = "'user_' + #request.userId + '_' + #request.currency")
    })
    @Transactional
    public WalletResponse createWallet(CreateWalletRequest request) {
        log.info("Creating wallet for user: {} with caching", request.getUserId());
        
        WalletResponse response = baseWalletService.createWallet(request);
        
        // Warm the cache with new wallet metadata
        warmWalletCaches(response.getWalletId(), request.getUserId());
        
        return response;
    }

    /**
     * Transfer funds with comprehensive cache invalidation
     */
    @Caching(evict = {
        @CacheEvict(value = "walletBalances", key = "#request.sourceWalletId"),
        @CacheEvict(value = "walletBalances", key = "#request.targetWalletId"),
        @CacheEvict(value = "transactionHistory", allEntries = true),
        @CacheEvict(value = "transactionSummaries", allEntries = true)
    })
    @Transactional
    public TransferResponse transfer(TransferRequest request) {
        log.info("Processing transfer with cache invalidation: {} -> {}", 
                 request.getSourceWalletId(), request.getTargetWalletId());
        
        TransferResponse response = baseWalletService.transfer(request);
        
        // Invalidate related caches
        invalidateWalletRelatedCaches(request.getSourceWalletId());
        invalidateWalletRelatedCaches(request.getTargetWalletId());
        
        // Warm updated balances
        CompletableFuture.runAsync(() -> {
            try {
                getWalletBalance(request.getSourceWalletId());
                getWalletBalance(request.getTargetWalletId());
            } catch (Exception e) {
                log.warn("Failed to warm cache after transfer", e);
            }
        });
        
        return response;
    }

    /**
     * Credit wallet with cache invalidation
     */
    @Caching(evict = {
        @CacheEvict(value = "walletBalances", key = "#request.walletId"),
        @CacheEvict(value = "transactionHistory", allEntries = true),
        @CacheEvict(value = "transactionSummaries", allEntries = true)
    })
    @Transactional
    public TransactionResponse credit(CreditRequest request) {
        log.info("Processing credit with cache invalidation for wallet: {}", request.getWalletId());
        
        TransactionResponse response = baseWalletService.credit(request);
        
        // Invalidate and warm cache
        invalidateWalletRelatedCaches(request.getWalletId());
        
        CompletableFuture.runAsync(() -> {
            try {
                getWalletBalance(request.getWalletId());
            } catch (Exception e) {
                log.warn("Failed to warm cache after credit", e);
            }
        });
        
        return response;
    }

    /**
     * Debit wallet with cache invalidation
     */
    @Caching(evict = {
        @CacheEvict(value = "walletBalances", key = "#request.walletId"),
        @CacheEvict(value = "transactionHistory", allEntries = true),
        @CacheEvict(value = "transactionSummaries", allEntries = true)
    })
    @Transactional
    public TransactionResponse debit(DebitRequest request) {
        log.info("Processing debit with cache invalidation for wallet: {}", request.getWalletId());
        
        TransactionResponse response = baseWalletService.debit(request);
        
        // Invalidate and warm cache
        invalidateWalletRelatedCaches(request.getWalletId());
        
        CompletableFuture.runAsync(() -> {
            try {
                getWalletBalance(request.getWalletId());
            } catch (Exception e) {
                log.warn("Failed to warm cache after debit", e);
            }
        });
        
        return response;
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Invalidate all wallet-related caches
     */
    private void invalidateWalletRelatedCaches(UUID walletId) {
        try {
            // Get wallet to find user ID
            Wallet wallet = walletRepository.findById(walletId).orElse(null);
            if (wallet != null) {
                cacheEvictionService.evictFromCache("walletMetadata", "user_wallets_" + wallet.getUserId());
                cacheEvictionService.evictFromCache("walletMetadata", "user_" + wallet.getUserId() + "_" + wallet.getCurrency());
            }
            
            // Evict transaction-related caches
            cacheEvictionService.evictCachePattern("transactionHistory", walletId.toString() + "_*");
            cacheEvictionService.evictCachePattern("transactionSummaries", walletId.toString() + "_*");
            
        } catch (Exception e) {
            log.warn("Failed to invalidate wallet-related caches for wallet: {}", walletId, e);
        }
    }

    /**
     * Warm wallet caches after creation
     */
    private void warmWalletCaches(UUID walletId, UUID userId) {
        CompletableFuture.runAsync(() -> {
            try {
                // Warm balance cache
                getWalletBalance(walletId);
                
                // Warm metadata cache
                getWalletMetadata(walletId);
                
                // Warm user wallets cache
                getUserWallets(userId);
                
                log.debug("Warmed caches for wallet: {}", walletId);
            } catch (Exception e) {
                log.warn("Failed to warm caches for wallet: {}", walletId, e);
            }
        });
    }

    /**
     * Map transaction to summary
     */
    private TransactionSummary mapToTransactionSummary(Transaction transaction) {
        return TransactionSummary.builder()
            .transactionId(transaction.getId())
            .walletId(transaction.getWalletId())
            .type(transaction.getType())
            .amount(transaction.getAmount())
            .currency(transaction.getCurrency())
            .description(transaction.getDescription())
            .status(transaction.getStatus())
            .createdAt(transaction.getCreatedAt())
            .reference(transaction.getReference())
            .build();
    }

    // ==================== CACHE MANAGEMENT OPERATIONS ====================

    /**
     * Manually refresh wallet balance cache
     */
    @CachePut(value = "walletBalances", key = "#walletId")
    public WalletBalance refreshWalletBalanceCache(UUID walletId) {
        log.info("Manually refreshing wallet balance cache for wallet: {}", walletId);
        return getWalletBalance(walletId);
    }

    /**
     * Preload critical wallet data into cache
     */
    public void preloadWalletData(UUID userId) {
        log.info("Preloading wallet data for user: {}", userId);
        
        CompletableFuture.runAsync(() -> {
            try {
                // Load all user wallets
                List<WalletMetadata> wallets = getUserWallets(userId);
                
                // Load balances for all wallets
                wallets.forEach(wallet -> {
                    try {
                        getWalletBalance(wallet.getWalletId());
                        getRecentTransactions(wallet.getWalletId(), 10);
                    } catch (Exception e) {
                        log.warn("Failed to preload data for wallet: {}", wallet.getWalletId(), e);
                    }
                });
                
                log.info("Completed preloading wallet data for user: {}", userId);
            } catch (Exception e) {
                log.error("Failed to preload wallet data for user: {}", userId, e);
            }
        });
    }

    /**
     * Clear all caches for a specific wallet
     */
    public void clearWalletCaches(UUID walletId) {
        log.info("Clearing all caches for wallet: {}", walletId);
        
        try {
            cacheEvictionService.evictFromCache("walletBalances", walletId.toString());
            cacheEvictionService.evictFromCache("walletMetadata", walletId.toString());
            cacheEvictionService.evictCachePattern("transactionHistory", walletId.toString() + "_*");
            cacheEvictionService.evictCachePattern("transactionSummaries", walletId.toString() + "_*");
            
            invalidateWalletRelatedCaches(walletId);
            
            log.info("Successfully cleared all caches for wallet: {}", walletId);
        } catch (Exception e) {
            log.error("Failed to clear caches for wallet: {}", walletId, e);
        }
    }
}