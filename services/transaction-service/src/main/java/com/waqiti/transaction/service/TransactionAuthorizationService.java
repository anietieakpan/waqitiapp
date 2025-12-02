package com.waqiti.transaction.service;

import com.waqiti.transaction.entity.Transaction;
import com.waqiti.transaction.repository.TransactionRepository;
import com.waqiti.transaction.client.WalletServiceClient;
import com.waqiti.transaction.dto.WalletOwnershipResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Service for transaction access authorization.
 * Ensures users can only access transactions they are authorized to view.
 * 
 * @author Waqiti Transaction Team
 * @since 2.0.0
 */
@Service("transactionAuthorizationService")
@RequiredArgsConstructor
@Slf4j
public class TransactionAuthorizationService {

    @Lazy
    private final TransactionAuthorizationService self;
    private final TransactionRepository transactionRepository;
    private final WalletServiceClient walletServiceClient;
    
    @Value("${transaction.authorization.cache.ttl:300}")
    private int cacheTimeToLiveSeconds;

    /**
     * Check if a user can access a specific transaction
     */
    public boolean canAccessTransaction(UUID transactionId, String userId) {
        try {
            Optional<Transaction> transactionOpt = transactionRepository.findById(transactionId);
            
            if (transactionOpt.isEmpty()) {
                log.warn("Transaction not found: {}", transactionId);
                return false;
            }

            Transaction transaction = transactionOpt.get();
            
            // Check if user is involved in the transaction
            boolean canAccess = isUserInvolvedInTransaction(transaction, userId);
            
            if (!canAccess) {
                log.warn("User {} attempted to access transaction {} without authorization", 
                        userId, transactionId);
            }
            
            return canAccess;

        } catch (Exception e) {
            log.error("Error checking transaction access for user: {} transaction: {}", 
                    userId, transactionId, e);
            return false;
        }
    }

    /**
     * Check if user is the owner of the transaction
     */
    public boolean isTransactionOwner(UUID transactionId, String userId) {
        try {
            Optional<Transaction> transactionOpt = transactionRepository.findById(transactionId);
            
            if (transactionOpt.isEmpty()) {
                return false;
            }

            Transaction transaction = transactionOpt.get();
            return userId.equals(transaction.getUserId());

        } catch (Exception e) {
            log.error("Error checking transaction ownership", e);
            return false;
        }
    }

    /**
     * Check if user can modify a transaction
     */
    public boolean canModifyTransaction(UUID transactionId, String userId) {
        try {
            if (!canAccessTransaction(transactionId, userId)) {
                return false;
            }

            Optional<Transaction> transactionOpt = transactionRepository.findById(transactionId);
            if (transactionOpt.isEmpty()) {
                return false;
            }

            Transaction transaction = transactionOpt.get();
            
            // Only allow modification if user is the owner and transaction is in modifiable state
            return userId.equals(transaction.getUserId()) && 
                   isTransactionModifiable(transaction);

        } catch (Exception e) {
            log.error("Error checking transaction modification rights", e);
            return false;
        }
    }

    /**
     * Check if a user is involved in a transaction (sender, receiver, or owner)
     */
    private boolean isUserInvolvedInTransaction(Transaction transaction, String userId) {
        // Check if user is the transaction owner
        if (userId.equals(transaction.getUserId())) {
            return true;
        }

        // Check if user is associated with source or destination wallets
        // This would require wallet ownership checks - placeholder implementation
        if (transaction.getFromWalletId() != null) {
            if (self.isUserWalletOwner(transaction.getFromWalletId(), userId)) {
                return true;
            }
        }

        if (transaction.getToWalletId() != null) {
            if (self.isUserWalletOwner(transaction.getToWalletId(), userId)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if transaction is in a state that allows modification
     */
    private boolean isTransactionModifiable(Transaction transaction) {
        // Transactions can only be modified if they're pending or in certain states
        return "PENDING".equals(transaction.getStatus()) || 
               "DRAFT".equals(transaction.getStatus());
    }

    /**
     * Check if user owns a specific wallet
     * Implements comprehensive wallet ownership verification with caching and fallback
     */
    @Cacheable(value = "walletOwnership", key = "#walletId + '_' + #userId", 
               unless = "#result == null")
    public boolean isUserWalletOwner(UUID walletId, String userId) {
        try {
            log.debug("Checking wallet ownership - walletId: {}, userId: {}", walletId, userId);
            
            // Async call with timeout for performance
            CompletableFuture<WalletOwnershipResponse> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return walletServiceClient.checkWalletOwnership(walletId, userId);
                } catch (Exception e) {
                    log.error("Error calling wallet service for ownership check", e);
                    // Fallback to database check
                    return checkWalletOwnershipFromDatabase(walletId, userId);
                }
            });
            
            // Wait with timeout to prevent blocking
            WalletOwnershipResponse response = future.get(2, TimeUnit.SECONDS);
            
            if (response != null && response.isValid()) {
                boolean isOwner = response.isOwner();
                log.debug("Wallet ownership check result - walletId: {}, userId: {}, isOwner: {}", 
                        walletId, userId, isOwner);
                return isOwner;
            }
            
            // If response is invalid, check secondary validation
            return performSecondaryOwnershipValidation(walletId, userId);
            
        } catch (Exception e) {
            log.error("Error checking wallet ownership - walletId: {}, userId: {}", 
                    walletId, userId, e);
            
            // In case of error, perform security-first approach
            // Check if we can verify ownership through alternative means
            return checkWalletOwnershipFallback(walletId, userId);
        }
    }
    
    /**
     * Database fallback for wallet ownership check
     */
    private WalletOwnershipResponse checkWalletOwnershipFromDatabase(UUID walletId, String userId) {
        try {
            // Query transaction history to infer ownership
            boolean hasTransactionHistory = transactionRepository
                    .existsByFromWalletIdAndUserId(walletId, userId);
            
            if (hasTransactionHistory) {
                return WalletOwnershipResponse.builder()
                        .walletId(walletId)
                        .userId(userId)
                        .isOwner(true)
                        .valid(true)
                        .source("DATABASE")
                        .build();
            }
            
            return WalletOwnershipResponse.builder()
                    .walletId(walletId)
                    .userId(userId)
                    .isOwner(false)
                    .valid(true)
                    .source("DATABASE")
                    .build();
                    
        } catch (Exception e) {
            log.error("Database fallback failed for wallet ownership check", e);
            return WalletOwnershipResponse.builder()
                    .walletId(walletId)
                    .userId(userId)
                    .isOwner(false)
                    .valid(false)
                    .error(e.getMessage())
                    .build();
        }
    }
    
    /**
     * Secondary validation through cross-service checks
     */
    private boolean performSecondaryOwnershipValidation(UUID walletId, String userId) {
        try {
            // Check if user has recent successful transactions from this wallet
            long recentTransactionCount = transactionRepository
                    .countRecentSuccessfulTransactionsByWalletAndUser(walletId, userId, 30);
            
            if (recentTransactionCount > 0) {
                log.info("Secondary validation passed - user {} has {} recent transactions from wallet {}",
                        userId, recentTransactionCount, walletId);
                return true;
            }
            
            // Check if wallet was created by this user (if we track that)
            Optional<Transaction> firstTransaction = transactionRepository
                    .findFirstByFromWalletIdOrderByCreatedAtAsc(walletId);
                    
            if (firstTransaction.isPresent() && 
                userId.equals(firstTransaction.get().getUserId())) {
                log.info("Secondary validation passed - user {} created wallet {}",
                        userId, walletId);
                return true;
            }
            
            log.warn("Secondary validation failed for wallet {} and user {}", walletId, userId);
            return false;
            
        } catch (Exception e) {
            log.error("Error in secondary ownership validation", e);
            return false;
        }
    }
    
    /**
     * Final fallback with strict security
     */
    private boolean checkWalletOwnershipFallback(UUID walletId, String userId) {
        try {
            // In production, we should fail securely
            // Only allow if we can definitively prove ownership
            
            // Check if this is a system wallet that the user should have access to
            if (isSystemWallet(walletId) && isSystemUser(userId)) {
                return true;
            }
            
            // Check transaction patterns for strong ownership indicators
            boolean hasStrongOwnershipPattern = transactionRepository
                    .hasStrongOwnershipPattern(walletId, userId);
                    
            if (hasStrongOwnershipPattern) {
                log.info("Fallback validation passed based on strong ownership pattern");
                return true;
            }
            
            // Default to secure denial
            log.warn("Fallback validation denied access - insufficient ownership proof");
            return false;
            
        } catch (Exception e) {
            log.error("Critical error in fallback ownership check", e);
            // Fail securely - deny access on error
            return false;
        }
    }
    
    /**
     * Check if wallet is a system wallet
     */
    private boolean isSystemWallet(UUID walletId) {
        // System wallets have specific UUID patterns or are in a specific list
        String walletIdStr = walletId.toString();
        return walletIdStr.startsWith("00000000-") || 
               walletIdStr.equals("system-fees-wallet");
    }
    
    /**
     * Check if user is a system user
     */
    private boolean isSystemUser(String userId) {
        return "SYSTEM".equals(userId) || 
               userId.startsWith("service-account-");
    }

    /**
     * Get transaction access level for user
     */
    public TransactionAccessLevel getAccessLevel(UUID transactionId, String userId) {
        try {
            if (!canAccessTransaction(transactionId, userId)) {
                return TransactionAccessLevel.NONE;
            }

            if (isTransactionOwner(transactionId, userId)) {
                return TransactionAccessLevel.OWNER;
            }

            return TransactionAccessLevel.PARTICIPANT;

        } catch (Exception e) {
            log.error("Error determining access level", e);
            return TransactionAccessLevel.NONE;
        }
    }

    /**
     * Transaction access levels
     */
    public enum TransactionAccessLevel {
        NONE,        // No access
        PARTICIPANT, // Can view transaction
        OWNER        // Can view and potentially modify transaction
    }
}