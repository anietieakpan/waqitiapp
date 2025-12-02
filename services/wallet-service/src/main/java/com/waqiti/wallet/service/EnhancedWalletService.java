package com.waqiti.wallet.service;

import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.common.idempotency.IdempotencyResult;
import com.waqiti.common.locking.DistributedLockService;
import com.waqiti.common.locking.DistributedLock;
import com.waqiti.wallet.domain.*;
import com.waqiti.wallet.dto.*;
import com.waqiti.wallet.repository.TransactionRepository;
import com.waqiti.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Enhanced wallet service with distributed locking and idempotency controls.
 * Ensures financial integrity and prevents race conditions in wallet operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnhancedWalletService {
    
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final IntegrationService integrationService;
    private final TransactionLogger transactionLogger;
    private final DistributedLockService lockService;
    private final IdempotencyService idempotencyService;

    private static final String SYSTEM_USER = "SYSTEM";
    private static final Duration OPERATION_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Creates a new wallet with idempotency protection.
     */
    @Transactional
    public WalletResponse createWallet(CreateWalletRequest request) {
        String idempotencyKey = IdempotencyService.FinancialIdempotencyKeys
            .deposit(null, request.getUserId().toString(), "wallet_creation_" + request.getCurrency());
            
        UUID operationId = UUID.randomUUID();
        
        // Check for existing operation
        Optional<IdempotencyResult<WalletResponse>> existingResult = 
            idempotencyService.checkIdempotency(idempotencyKey, WalletResponse.class);
            
        if (existingResult.isPresent()) {
            if (existingResult.get().hasResult()) {
                log.info("Returning cached wallet creation result for user: {}", request.getUserId());
                return existingResult.get().getResult();
            } else if (existingResult.get().isInProgress()) {
                throw new IllegalStateException("Wallet creation already in progress for user: " + request.getUserId());
            }
        }

        // Start operation
        if (!idempotencyService.startOperation(idempotencyKey, operationId)) {
            throw new IllegalStateException("Failed to start wallet creation operation");
        }

        try {
            String lockKey = DistributedLockService.FinancialLocks.userAccountUpdate(request.getUserId());
            
            try (DistributedLock lock = lockService.acquireLock(lockKey, Duration.ofSeconds(10), OPERATION_TIMEOUT)) {
                if (lock == null) {
                    throw new IllegalStateException("Could not acquire lock for wallet creation");
                }

                log.info("Creating new wallet for user: {}, type: {}, currency: {}",
                        request.getUserId(), request.getWalletType(), request.getCurrency());

                // Check if user already has a wallet in this currency
                Optional<Wallet> existingWallet = walletRepository.findByUserIdAndCurrency(
                        request.getUserId(), request.getCurrency());

                if (existingWallet.isPresent()) {
                    log.warn("User already has a wallet in currency: {}", request.getCurrency());
                    throw new IllegalStateException(
                            "User already has a wallet in currency: " + request.getCurrency());
                }

                // Create the wallet in the external system
                String externalId = integrationService.createWallet(
                        request.getUserId(),
                        request.getWalletType(),
                        request.getAccountType(),
                        request.getCurrency());

                // Create the wallet in our system
                Wallet wallet = Wallet.create(
                        request.getUserId(),
                        externalId,
                        request.getWalletType(),
                        request.getAccountType(),
                        request.getCurrency());

                wallet.setCreatedBy(SYSTEM_USER);
                wallet = walletRepository.save(wallet);

                // Log wallet creation
                transactionLogger.logWalletEvent(
                        wallet.getUserId(),
                        wallet.getId(),
                        "WALLET_CREATED",
                        wallet.getBalance(),
                        wallet.getCurrency(),
                        null);

                WalletResponse response = mapToWalletResponse(wallet);
                
                // Complete operation
                idempotencyService.completeOperation(idempotencyKey, operationId, response);
                
                return response;
            }
        } catch (Exception e) {
            log.error("Wallet creation failed for user: {}", request.getUserId(), e);
            idempotencyService.failOperation(idempotencyKey, operationId, e.getMessage());
            throw e;
        }
    }

    /**
     * Transfers money between wallets with full financial integrity controls.
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public TransactionResponse transfer(TransferRequest request) {
        // Generate idempotency key
        String idempotencyKey = IdempotencyService.FinancialIdempotencyKeys
            .walletTransfer(request.getSourceWalletId(), request.getTargetWalletId(), 
                           request.getAmount().toString(), request.getClientTransactionId());
                           
        UUID operationId = UUID.randomUUID();
        
        // Check for existing operation
        Optional<IdempotencyResult<TransactionResponse>> existingResult = 
            idempotencyService.checkIdempotency(idempotencyKey, TransactionResponse.class);
            
        if (existingResult.isPresent()) {
            if (existingResult.get().hasResult()) {
                log.info("Returning cached transfer result for transaction: {}", request.getClientTransactionId());
                return existingResult.get().getResult();
            } else if (existingResult.get().isInProgress()) {
                throw new IllegalStateException("Transfer already in progress: " + request.getClientTransactionId());
            }
        }

        // Start operation
        if (!idempotencyService.startOperation(idempotencyKey, operationId)) {
            throw new IllegalStateException("Failed to start transfer operation");
        }

        try {
            log.info("Transferring {} from wallet {} to wallet {} (client tx: {})",
                    request.getAmount(), request.getSourceWalletId(), request.getTargetWalletId(),
                    request.getClientTransactionId());

            // Acquire distributed lock for transfer operation (prevents deadlocks)
            String lockKey = DistributedLockService.FinancialLocks
                .transferOperation(request.getSourceWalletId(), request.getTargetWalletId());
            
            try (DistributedLock lock = lockService.acquireLock(lockKey, Duration.ofSeconds(10), OPERATION_TIMEOUT)) {
                if (lock == null) {
                    throw new IllegalStateException("Could not acquire lock for transfer operation");
                }

                // Lock wallets in deterministic order to prevent deadlocks
                Wallet sourceWallet, targetWallet;
                if (request.getSourceWalletId().compareTo(request.getTargetWalletId()) < 0) {
                    sourceWallet = getWalletWithLock(request.getSourceWalletId());
                    targetWallet = getWalletWithLock(request.getTargetWalletId());
                } else {
                    targetWallet = getWalletWithLock(request.getTargetWalletId());
                    sourceWallet = getWalletWithLock(request.getSourceWalletId());
                }

                // Validate wallets
                validateWalletForTransfer(sourceWallet);
                validateWalletForTransfer(targetWallet);

                // Validate currencies match
                if (!sourceWallet.getCurrency().equals(targetWallet.getCurrency())) {
                    throw new IllegalArgumentException(
                            "Currency mismatch: source wallet currency is " + sourceWallet.getCurrency() +
                                    ", target wallet currency is " + targetWallet.getCurrency());
                }

                // Validate amount
                if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Transfer amount must be positive");
                }

                // Create transaction record
                Transaction transaction = transactionLogger.createTransactionAudit(
                        sourceWallet.getId(),
                        targetWallet.getId(),
                        request.getAmount(),
                        sourceWallet.getCurrency(),
                        TransactionType.TRANSFER,
                        request.getDescription());

                transaction.setCreatedBy(SYSTEM_USER);
                transaction = transactionRepository.save(transaction);

                try {
                    // Mark transaction as in progress
                    transaction.markInProgress();
                    transaction = transactionRepository.save(transaction);

                    // Check sufficient balance with real-time sync
                    ensureSufficientBalance(sourceWallet, request.getAmount());

                    // Perform transfer in external system
                    String externalId = integrationService.transferBetweenWallets(
                            sourceWallet,
                            targetWallet,
                            request.getAmount());

                    // Update wallet balances with real-time sync
                    updateWalletBalanceFromExternal(sourceWallet);
                    updateWalletBalanceFromExternal(targetWallet);

                    // Mark transaction as completed
                    transaction.complete(externalId);
                    transaction.setUpdatedBy(SYSTEM_USER);
                    transaction = transactionRepository.save(transaction);

                    // Log transaction
                    transactionLogger.logTransaction(transaction);

                    // Log wallet events for notifications
                    logTransferEvents(sourceWallet, targetWallet, request.getAmount(), transaction.getId());

                    TransactionResponse response = mapToTransactionResponse(transaction);
                    
                    // Complete operation
                    idempotencyService.completeOperation(idempotencyKey, operationId, response);
                    
                    return response;
                    
                } catch (Exception e) {
                    log.error("Transfer failed", e);

                    // Mark transaction as failed
                    transaction.fail(e.getMessage());
                    transaction.setUpdatedBy(SYSTEM_USER);
                    transactionRepository.save(transaction);

                    // Log failure
                    transactionLogger.logTransactionFailure(
                            transaction.getId(),
                            e.getMessage(),
                            e instanceof InsufficientBalanceException ? "INSUFFICIENT_FUNDS" : "TRANSFER_FAILED");

                    throw e;
                }
            }
        } catch (Exception e) {
            log.error("Transfer operation failed", e);
            idempotencyService.failOperation(idempotencyKey, operationId, e.getMessage());
            
            if (e instanceof InsufficientBalanceException) {
                throw (InsufficientBalanceException) e;
            } else if (e instanceof IllegalArgumentException || e instanceof IllegalStateException) {
                throw e;
            } else {
                throw new TransactionFailedException("Transfer failed: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Deposits money into a wallet with financial integrity controls.
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public TransactionResponse deposit(DepositRequest request) {
        // Generate idempotency key
        String idempotencyKey = IdempotencyService.FinancialIdempotencyKeys
            .deposit(request.getWalletId(), request.getAmount().toString(), 
                    request.getExternalTransactionId());
                    
        UUID operationId = UUID.randomUUID();
        
        // Check for existing operation
        Optional<IdempotencyResult<TransactionResponse>> existingResult = 
            idempotencyService.checkIdempotency(idempotencyKey, TransactionResponse.class);
            
        if (existingResult.isPresent()) {
            if (existingResult.get().hasResult()) {
                log.info("Returning cached deposit result for external tx: {}", request.getExternalTransactionId());
                return existingResult.get().getResult();
            } else if (existingResult.get().isInProgress()) {
                throw new IllegalStateException("Deposit already in progress: " + request.getExternalTransactionId());
            }
        }

        // Start operation
        if (!idempotencyService.startOperation(idempotencyKey, operationId)) {
            throw new IllegalStateException("Failed to start deposit operation");
        }

        try {
            log.info("Depositing {} into wallet {} (external tx: {})", 
                    request.getAmount(), request.getWalletId(), request.getExternalTransactionId());

            String lockKey = DistributedLockService.FinancialLocks.walletBalanceUpdate(request.getWalletId());
            
            try (DistributedLock lock = lockService.acquireLock(lockKey, Duration.ofSeconds(10), OPERATION_TIMEOUT)) {
                if (lock == null) {
                    throw new IllegalStateException("Could not acquire lock for deposit operation");
                }

                Wallet wallet = getWalletWithLock(request.getWalletId());
                validateWalletForDeposit(wallet);

                // Validate amount
                if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Deposit amount must be positive");
                }

                // Create transaction record
                Transaction transaction = transactionLogger.createTransactionAudit(
                        null,
                        wallet.getId(),
                        request.getAmount(),
                        wallet.getCurrency(),
                        TransactionType.DEPOSIT,
                        request.getDescription());

                transaction.setCreatedBy(SYSTEM_USER);
                transaction = transactionRepository.save(transaction);

                try {
                    // Mark transaction as in progress
                    transaction.markInProgress();
                    transaction = transactionRepository.save(transaction);

                    // Perform deposit in external system
                    String externalId = integrationService.depositToWallet(wallet, request.getAmount());

                    // Update wallet balance with real-time sync
                    updateWalletBalanceFromExternal(wallet);

                    // Mark transaction as completed
                    transaction.complete(externalId);
                    transaction.setUpdatedBy(SYSTEM_USER);
                    transaction = transactionRepository.save(transaction);

                    // Log transaction and events
                    transactionLogger.logTransaction(transaction);
                    transactionLogger.logWalletEvent(
                            wallet.getUserId(),
                            wallet.getId(),
                            "DEPOSIT",
                            request.getAmount(),
                            wallet.getCurrency(),
                            transaction.getId());

                    TransactionResponse response = mapToTransactionResponse(transaction);
                    
                    // Complete operation
                    idempotencyService.completeOperation(idempotencyKey, operationId, response);
                    
                    return response;
                    
                } catch (Exception e) {
                    log.error("Deposit failed", e);
                    transaction.fail(e.getMessage());
                    transaction.setUpdatedBy(SYSTEM_USER);
                    transactionRepository.save(transaction);

                    transactionLogger.logTransactionFailure(
                            transaction.getId(), e.getMessage(), "DEPOSIT_FAILED");
                    throw e;
                }
            }
        } catch (Exception e) {
            log.error("Deposit operation failed", e);
            idempotencyService.failOperation(idempotencyKey, operationId, e.getMessage());
            throw new TransactionFailedException("Deposit failed: " + e.getMessage(), e);
        }
    }

    /**
     * Gets a wallet with pessimistic lock for updates.
     */
    private Wallet getWalletWithLock(UUID walletId) {
        return walletRepository.findByIdWithLock(walletId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + walletId));
    }

    /**
     * Ensures wallet has sufficient balance, syncing with external system if needed.
     */
    private void ensureSufficientBalance(Wallet wallet, BigDecimal requiredAmount) {
        if (wallet.getBalance().compareTo(requiredAmount) < 0) {
            // Sync with external system
            updateWalletBalanceFromExternal(wallet);
            
            // Check again after sync
            if (wallet.getBalance().compareTo(requiredAmount) < 0) {
                throw new InsufficientBalanceException(
                        String.format("Insufficient balance: required %s %s, available %s %s",
                                requiredAmount, wallet.getCurrency(),
                                wallet.getBalance(), wallet.getCurrency()));
            }
        }
    }

    /**
     * Updates wallet balance from external system.
     */
    private void updateWalletBalanceFromExternal(Wallet wallet) {
        try {
            BigDecimal remoteBalance = integrationService.getWalletBalance(wallet);
            if (wallet.getBalance().compareTo(remoteBalance) != 0) {
                log.info("Wallet {} balance updated from {} to {}", 
                        wallet.getId(), wallet.getBalance(), remoteBalance);
                wallet.updateBalance(remoteBalance);
                wallet.setUpdatedBy(SYSTEM_USER);
                walletRepository.save(wallet);
            }
        } catch (Exception e) {
            log.warn("Failed to sync wallet balance from external system: {}", wallet.getId(), e);
            // Continue with local balance - external system might be temporarily unavailable
        }
    }

    /**
     * Logs wallet events for transfer operations.
     */
    private void logTransferEvents(Wallet sourceWallet, Wallet targetWallet, 
                                 BigDecimal amount, UUID transactionId) {
        transactionLogger.logWalletEvent(
                sourceWallet.getUserId(),
                sourceWallet.getId(),
                "TRANSFER_OUT",
                amount,
                sourceWallet.getCurrency(),
                transactionId);

        transactionLogger.logWalletEvent(
                targetWallet.getUserId(),
                targetWallet.getId(),
                "TRANSFER_IN",
                amount,
                targetWallet.getCurrency(),
                transactionId);
    }

    // Validation methods (keeping existing implementation)
    private void validateWalletForTransfer(Wallet wallet) {
        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new WalletNotActiveException("Wallet is not active: " + wallet.getId() +
                    ", status: " + wallet.getStatus());
        }
    }

    private void validateWalletForDeposit(Wallet wallet) {
        if (wallet.getStatus() != WalletStatus.ACTIVE && wallet.getStatus() != WalletStatus.FROZEN) {
            throw new WalletNotActiveException("Cannot deposit to a wallet with status: " +
                    wallet.getStatus());
        }
    }

    // Mapping methods (keeping existing implementation)
    private WalletResponse mapToWalletResponse(Wallet wallet) {
        return WalletResponse.builder()
                .id(wallet.getId())
                .userId(wallet.getUserId())
                .externalId(wallet.getExternalId())
                .walletType(wallet.getWalletType())
                .accountType(wallet.getAccountType())
                .balance(wallet.getBalance())
                .currency(wallet.getCurrency())
                .status(wallet.getStatus().toString())
                .createdAt(wallet.getCreatedAt())
                .updatedAt(wallet.getUpdatedAt())
                .build();
    }

    private TransactionResponse mapToTransactionResponse(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .externalId(transaction.getExternalId())
                .sourceWalletId(transaction.getSourceWalletId())
                .targetWalletId(transaction.getTargetWalletId())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .type(transaction.getType().toString())
                .status(transaction.getStatus().toString())
                .description(transaction.getDescription())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}