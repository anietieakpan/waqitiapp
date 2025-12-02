package com.waqiti.wallet.service;

import com.waqiti.common.audit.AuditEventType;
import com.waqiti.common.audit.AuditSeverity;
import com.waqiti.common.audit.annotation.Auditable;
import com.waqiti.common.locking.FinancialOperationLockManager;
import com.waqiti.common.exception.ConcurrencyException;
import com.waqiti.wallet.domain.Wallet;
import com.waqiti.wallet.domain.WalletTransaction;
import com.waqiti.wallet.dto.*;
import com.waqiti.wallet.exception.InsufficientFundsException;
import com.waqiti.wallet.exception.WalletNotFoundException;
import com.waqiti.wallet.repository.WalletRepository;
import com.waqiti.wallet.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Secure Wallet Balance Service that prevents race conditions and double-spending
 * using distributed locking and database-level pessimistic locking.
 * 
 * Fixes Critical Vulnerabilities:
 * - Race conditions in balance updates
 * - Double-spending in concurrent transactions
 * - Fund reservation lost on service restart
 * - Inconsistent wallet states
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecureWalletBalanceService {

    private final FinancialOperationLockManager lockManager;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final WalletValidationService validationService;

    /**
     * Securely updates wallet balance with distributed locking
     */
    @Auditable(
        eventType = "WALLET_BALANCE_UPDATE",
        category = AuditEventType.WALLET_UPDATED,
        severity = AuditSeverity.HIGH,
        description = "Wallet balance updated with distributed locking",
        affectedResourceExpression = "#walletId",
        pciRelevant = true,
        soxRelevant = true,
        captureParameters = true,
        captureReturnValue = true,
        criticalOperation = true,
        metadata = {
            "debitAmount: #debitAmount",
            "creditAmount: #creditAmount", 
            "transactionId: #transactionId",
            "description: #description"
        }
    )
    @Transactional(propagation = Propagation.REQUIRED)
    @CacheEvict(value = {"wallet-balance", "wallet-available-balance"}, key = "#walletId")
    public WalletBalanceDto updateWalletBalance(UUID walletId, BigDecimal debitAmount, 
                                              BigDecimal creditAmount, String transactionId, 
                                              String description) {
        
        return distributedLockManager.executeWithExclusiveLock(walletId, () -> {
            log.info("Executing secure wallet balance update: walletId={}, debit={}, credit={}, txnId={}", 
                    walletId, debitAmount, creditAmount, transactionId);
            
            // Get wallet with database lock (SELECT FOR UPDATE)
            Wallet wallet = getWalletForUpdate(walletId);
            
            // Validate the operation
            validateBalanceUpdate(wallet, debitAmount, creditAmount);
            
            // Calculate new balances
            BigDecimal currentBalance = wallet.getBalance();
            BigDecimal availableBalance = wallet.getAvailableBalance();
            
            BigDecimal newCurrentBalance = currentBalance;
            BigDecimal newAvailableBalance = availableBalance;
            
            // Apply debit
            if (debitAmount != null && debitAmount.compareTo(BigDecimal.ZERO) > 0) {
                // Validate sufficient funds before debiting
                if (availableBalance.compareTo(debitAmount) < 0) {
                    throw new InsufficientFundsException(
                        "Insufficient funds: required=" + debitAmount + 
                        ", available=" + availableBalance);
                }
                
                newCurrentBalance = currentBalance.subtract(debitAmount);
                newAvailableBalance = availableBalance.subtract(debitAmount);
                
                log.debug("Debit applied: walletId={}, amount={}, newBalance={}", 
                         walletId, debitAmount, newCurrentBalance);
            }
            
            // Apply credit
            if (creditAmount != null && creditAmount.compareTo(BigDecimal.ZERO) > 0) {
                newCurrentBalance = currentBalance.add(creditAmount);
                newAvailableBalance = availableBalance.add(creditAmount);
                
                log.debug("Credit applied: walletId={}, amount={}, newBalance={}", 
                         walletId, creditAmount, newCurrentBalance);
            }
            
            // Update wallet atomically
            wallet.setBalance(newCurrentBalance);
            wallet.setAvailableBalance(newAvailableBalance);
            wallet.setUpdatedAt(LocalDateTime.now());
            wallet.incrementVersion(); // Optimistic locking
            
            // Save with version check
            Wallet savedWallet = walletRepository.save(wallet);
            
            // Create transaction record
            WalletTransaction transaction = createTransactionRecord(
                savedWallet, debitAmount, creditAmount, transactionId, description);
            transactionRepository.save(transaction);
            
            log.info("Wallet balance update completed: walletId={}, newBalance={}, newAvailable={}", 
                    walletId, savedWallet.getBalance(), savedWallet.getAvailableBalance());
            
            return WalletBalanceDto.builder()
                .walletId(savedWallet.getId())
                .balance(savedWallet.getBalance())
                .availableBalance(savedWallet.getAvailableBalance())
                .currency(savedWallet.getCurrency())
                .lastUpdated(savedWallet.getUpdatedAt())
                .version(savedWallet.getVersion())
                .build();
        });
    }

    /**
     * Securely transfers funds between wallets with distributed locking
     */
    @Auditable(
        eventType = "WALLET_FUNDS_TRANSFER",
        category = AuditEventType.TRANSFER_COMPLETED,
        severity = AuditSeverity.HIGH,
        description = "Secure funds transfer between wallets with distributed locking",
        affectedResourceExpression = "#fromWalletId + ':' + #toWalletId",
        pciRelevant = true,
        soxRelevant = true,
        captureParameters = true,
        captureReturnValue = true,
        criticalOperation = true,
        riskScore = 75,
        metadata = {
            "fromWalletId: #fromWalletId",
            "toWalletId: #toWalletId",
            "amount: #amount",
            "description: #description",
            "idempotencyKey: #idempotencyKey"
        }
    )
    @Transactional(propagation = Propagation.REQUIRED)
    public WalletTransferResultDto transferFunds(UUID fromWalletId, UUID toWalletId, 
                                               BigDecimal amount, String description, 
                                               String idempotencyKey) {
        
        return lockManager.executeTransfer(fromWalletId, toWalletId, amount, () -> {
            log.info("Executing secure wallet transfer: from={}, to={}, amount={}, idempotency={}", 
                    fromWalletId, toWalletId, amount, idempotencyKey);
            
            // Check for duplicate transfer (idempotency)
            if (idempotencyKey != null && transferExists(idempotencyKey)) {
                log.warn("Duplicate transfer attempt detected: idempotencyKey={}", idempotencyKey);
                throw new IllegalStateException("Transfer already exists for idempotency key: " + idempotencyKey);
            }
            
            // Get both wallets with database locks
            Wallet fromWallet = getWalletForUpdate(fromWalletId);
            Wallet toWallet = getWalletForUpdate(toWalletId);
            
            // Validate transfer
            validateTransfer(fromWallet, toWallet, amount);
            
            // Check sufficient funds
            if (fromWallet.getAvailableBalance().compareTo(amount) < 0) {
                throw new InsufficientFundsException(
                    "Insufficient funds for transfer: required=" + amount + 
                    ", available=" + fromWallet.getAvailableBalance());
            }
            
            // Execute transfer atomically
            BigDecimal fromNewBalance = fromWallet.getBalance().subtract(amount);
            BigDecimal fromNewAvailable = fromWallet.getAvailableBalance().subtract(amount);
            
            BigDecimal toNewBalance = toWallet.getBalance().add(amount);
            BigDecimal toNewAvailable = toWallet.getAvailableBalance().add(amount);
            
            // Update wallets
            fromWallet.setBalance(fromNewBalance);
            fromWallet.setAvailableBalance(fromNewAvailable);
            fromWallet.setUpdatedAt(LocalDateTime.now());
            fromWallet.incrementVersion();
            
            toWallet.setBalance(toNewBalance);
            toWallet.setAvailableBalance(toNewAvailable);
            toWallet.setUpdatedAt(LocalDateTime.now());
            toWallet.incrementVersion();
            
            // Save both wallets
            Wallet savedFromWallet = walletRepository.save(fromWallet);
            Wallet savedToWallet = walletRepository.save(toWallet);
            
            // Create transaction records
            String transferId = UUID.randomUUID().toString();
            
            WalletTransaction fromTransaction = createTransactionRecord(
                savedFromWallet, amount, null, transferId, "Transfer to " + toWalletId);
            
            WalletTransaction toTransaction = createTransactionRecord(
                savedToWallet, null, amount, transferId, "Transfer from " + fromWalletId);
            
            if (idempotencyKey != null) {
                fromTransaction.setIdempotencyKey(idempotencyKey);
                toTransaction.setIdempotencyKey(idempotencyKey + "_credit");
            }
            
            transactionRepository.save(fromTransaction);
            transactionRepository.save(toTransaction);
            
            log.info("Wallet transfer completed: from={}, to={}, amount={}, transferId={}", 
                    fromWalletId, toWalletId, amount, transferId);
            
            return WalletTransferResultDto.builder()
                .transferId(transferId)
                .fromWalletId(fromWalletId)
                .toWalletId(toWalletId)
                .amount(amount)
                .currency(fromWallet.getCurrency())
                .description(description)
                .status(TransferStatus.COMPLETED)
                .fromWalletBalance(savedFromWallet.getBalance())
                .toWalletBalance(savedToWallet.getBalance())
                .executedAt(LocalDateTime.now())
                .build();
        });
    }

    /**
     * Securely reserves funds with distributed locking
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public FundReservationResultDto reserveFunds(UUID walletId, UUID transactionId, 
                                                BigDecimal amount, Duration reservationDuration, 
                                                String idempotencyKey) {
        
        return lockManager.executeBalanceUpdate(walletId, () -> {
            log.info("Executing secure fund reservation: walletId={}, transactionId={}, amount={}", 
                    walletId, transactionId, amount);
            
            // Get wallet with database lock
            Wallet wallet = getWalletForUpdate(walletId);
            
            // Validate reservation
            validationService.validateWalletActive(wallet);
            validationService.validatePositiveAmount(amount);
            
            // Check if reservation already exists (idempotency)
            if (reservationExists(walletId, transactionId)) {
                log.warn("Fund reservation already exists: walletId={}, transactionId={}", 
                        walletId, transactionId);
                throw new IllegalStateException("Reservation already exists for transaction: " + transactionId);
            }
            
            // Check available balance
            if (wallet.getAvailableBalance().compareTo(amount) < 0) {
                throw new InsufficientFundsException(
                    "Insufficient funds for reservation: required=" + amount + 
                    ", available=" + wallet.getAvailableBalance());
            }
            
            // Create reservation using the lock manager
            boolean reservationCreated = lockManager.executeFundReservation(
                walletId, transactionId, amount, 
                reservationDuration != null ? reservationDuration : Duration.ofMinutes(5));
            
            if (!reservationCreated) {
                throw new ConcurrencyException("Failed to create fund reservation", 
                        "fund_reservation", walletId.toString());
            }
            
            // Update available balance
            BigDecimal newAvailableBalance = wallet.getAvailableBalance().subtract(amount);
            wallet.setAvailableBalance(newAvailableBalance);
            wallet.setUpdatedAt(LocalDateTime.now());
            wallet.incrementVersion();
            
            Wallet savedWallet = walletRepository.save(wallet);
            
            log.info("Fund reservation created: walletId={}, transactionId={}, amount={}", 
                    walletId, transactionId, amount);
            
            return FundReservationResultDto.builder()
                .reservationId(UUID.randomUUID())
                .walletId(walletId)
                .transactionId(transactionId)
                .amount(amount)
                .currency(wallet.getCurrency())
                .status(ReservationStatus.ACTIVE)
                .expiresAt(LocalDateTime.now().plus(reservationDuration))
                .createdAt(LocalDateTime.now())
                .build();
        });
    }

    /**
     * Gets wallet balance with caching
     */
    @Cacheable(value = "wallet-balance", key = "#walletId")
    public WalletBalanceDto getWalletBalance(UUID walletId) {
        Wallet wallet = walletRepository.findById(walletId)
            .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + walletId));
        
        return WalletBalanceDto.builder()
            .walletId(wallet.getId())
            .balance(wallet.getBalance())
            .availableBalance(wallet.getAvailableBalance())
            .currency(wallet.getCurrency())
            .lastUpdated(wallet.getUpdatedAt())
            .version(wallet.getVersion())
            .build();
    }

    /**
     * Gets wallet with SELECT FOR UPDATE lock
     */
    private Wallet getWalletForUpdate(UUID walletId) {
        return walletRepository.findByIdForUpdate(walletId)
            .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + walletId));
    }

    /**
     * Validates balance update operation
     */
    private void validateBalanceUpdate(Wallet wallet, BigDecimal debitAmount, BigDecimal creditAmount) {
        validationService.validateWalletActive(wallet);
        
        if (debitAmount != null) {
            validationService.validatePositiveAmount(debitAmount);
        }
        
        if (creditAmount != null) {
            validationService.validatePositiveAmount(creditAmount);
        }
        
        if ((debitAmount == null || debitAmount.equals(BigDecimal.ZERO)) && 
            (creditAmount == null || creditAmount.equals(BigDecimal.ZERO))) {
            throw new IllegalArgumentException("Either debit or credit amount must be specified");
        }
    }

    /**
     * Validates transfer request
     */
    private void validateTransfer(Wallet fromWallet, Wallet toWallet, BigDecimal amount) {
        validationService.validateWalletActive(fromWallet);
        validationService.validateWalletActive(toWallet);
        validationService.validatePositiveAmount(amount);
        
        if (fromWallet.getId().equals(toWallet.getId())) {
            throw new IllegalArgumentException("Cannot transfer to the same wallet");
        }
        
        if (!fromWallet.getCurrency().equals(toWallet.getCurrency())) {
            throw new IllegalArgumentException("Currency mismatch in transfer wallets");
        }
    }

    /**
     * Creates a transaction record
     */
    private WalletTransaction createTransactionRecord(Wallet wallet, BigDecimal debitAmount, 
                                                    BigDecimal creditAmount, String transactionId, 
                                                    String description) {
        return WalletTransaction.builder()
            .id(UUID.randomUUID())
            .walletId(wallet.getId())
            .transactionId(transactionId)
            .debitAmount(debitAmount)
            .creditAmount(creditAmount)
            .balanceAfter(wallet.getBalance())
            .currency(wallet.getCurrency())
            .description(description)
            .status(TransactionStatus.COMPLETED)
            .createdAt(LocalDateTime.now())
            .build();
    }

    /**
     * Checks if a transfer already exists
     */
    private boolean transferExists(String idempotencyKey) {
        return transactionRepository.existsByIdempotencyKey(idempotencyKey);
    }

    /**
     * Checks if a fund reservation already exists
     */
    private boolean reservationExists(UUID walletId, UUID transactionId) {
        return walletRepository.existsActiveFundReservation(walletId, transactionId);
    }
}