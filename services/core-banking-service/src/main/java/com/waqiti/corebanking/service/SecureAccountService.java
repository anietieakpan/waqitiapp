package com.waqiti.corebanking.service;

import com.waqiti.common.locking.FinancialOperationLockManager;
import com.waqiti.common.exception.ConcurrencyException;
import com.waqiti.corebanking.domain.Account;
import com.waqiti.corebanking.dto.*;
import com.waqiti.corebanking.exception.InsufficientFundsException;
import com.waqiti.corebanking.repository.AccountRepository;
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
 * Secure Account Service that uses distributed locking to prevent race conditions
 * and double-spending in financial operations.
 * 
 * Addresses Critical Security Vulnerabilities:
 * - Race conditions in balance updates
 * - Double-spending in concurrent transactions
 * - Fund reservation conflicts
 * - Inconsistent account states
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecureAccountService {

    private final FinancialOperationLockManager lockManager;
    private final AccountRepository accountRepository;
    private final AccountValidationService validationService;
    
    // Default fund reservation duration
    private static final Duration DEFAULT_RESERVATION_DURATION = Duration.ofMinutes(5);

    /**
     * Securely updates account balance with distributed locking
     */
    @Transactional(propagation = Propagation.REQUIRED)
    @CacheEvict(value = "account-balances", key = "#accountId")
    public AccountBalanceDto updateAccountBalance(UUID accountId, BigDecimal debitAmount, 
                                                BigDecimal creditAmount, String description) {
        
        return lockManager.executeBalanceUpdate(accountId, () -> {
            log.info("Executing secure balance update: accountId={}, debit={}, credit={}", 
                    accountId, debitAmount, creditAmount);
            
            // Get account with database lock (SELECT FOR UPDATE)
            Account account = getAccountForUpdate(accountId);
            
            // Validate the operation
            validateBalanceUpdate(account, debitAmount, creditAmount);
            
            // Calculate new balances
            BigDecimal newCurrentBalance = account.getCurrentBalance();
            BigDecimal newAvailableBalance = account.getAvailableBalance();
            
            if (debitAmount != null && debitAmount.compareTo(BigDecimal.ZERO) > 0) {
                newCurrentBalance = newCurrentBalance.subtract(debitAmount);
                newAvailableBalance = newAvailableBalance.subtract(debitAmount);
                
                // Final validation to prevent overdraft
                if (newAvailableBalance.compareTo(BigDecimal.ZERO) < 0 && 
                    !account.hasOverdraftProtection()) {
                    throw new InsufficientFundsException(
                        "Insufficient funds: required=" + debitAmount + 
                        ", available=" + account.getAvailableBalance());
                }
            }
            
            if (creditAmount != null && creditAmount.compareTo(BigDecimal.ZERO) > 0) {
                newCurrentBalance = newCurrentBalance.add(creditAmount);
                newAvailableBalance = newAvailableBalance.add(creditAmount);
            }
            
            // Update account atomically
            account.setCurrentBalance(newCurrentBalance);
            account.setAvailableBalance(newAvailableBalance);
            account.setLastTransactionDate(LocalDateTime.now());
            account.updateVersion(); // Optimistic locking
            
            // Save with version check
            Account savedAccount = accountRepository.save(account);
            
            log.info("Balance update completed: accountId={}, newBalance={}", 
                    accountId, savedAccount.getCurrentBalance());
            
            return AccountBalanceDto.builder()
                .accountId(savedAccount.getAccountId())
                .currentBalance(savedAccount.getCurrentBalance())
                .availableBalance(savedAccount.getAvailableBalance())
                .currency(savedAccount.getCurrency())
                .lastUpdated(savedAccount.getLastTransactionDate())
                .version(savedAccount.getVersion())
                .build();
        });
    }

    /**
     * Securely reserves funds with distributed locking to prevent double-spending
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public FundReservationDto reserveFunds(UUID accountId, UUID transactionId, BigDecimal amount, 
                                         Duration reservationDuration) {
        
        if (reservationDuration == null) {
            reservationDuration = DEFAULT_RESERVATION_DURATION;
        }
        
        final Duration finalDuration = reservationDuration;
        
        return lockManager.executeBalanceUpdate(accountId, () -> {
            log.info("Executing secure fund reservation: accountId={}, transactionId={}, amount={}", 
                    accountId, transactionId, amount);
            
            // Get account with database lock
            Account account = getAccountForUpdate(accountId);
            
            // Validate reservation request
            validateFundReservation(account, amount);
            
            // Check if reservation already exists (idempotency)
            if (reservationExists(accountId, transactionId)) {
                log.warn("Fund reservation already exists: accountId={}, transactionId={}", 
                        accountId, transactionId);
                throw new IllegalStateException("Reservation already exists for transaction: " + transactionId);
            }
            
            // Check available balance
            if (account.getAvailableBalance().compareTo(amount) < 0) {
                throw new InsufficientFundsException(
                    "Insufficient funds for reservation: required=" + amount + 
                    ", available=" + account.getAvailableBalance());
            }
            
            // Create reservation using the lock manager's implementation
            boolean reservationCreated = lockManager.executeFundReservation(
                accountId, transactionId, amount, finalDuration);
            
            if (!reservationCreated) {
                throw new ConcurrencyException("Failed to create fund reservation", 
                        "fund_reservation", accountId.toString());
            }
            
            // Update available balance
            BigDecimal newAvailableBalance = account.getAvailableBalance().subtract(amount);
            account.setAvailableBalance(newAvailableBalance);
            account.updateVersion();
            
            Account savedAccount = accountRepository.save(account);
            
            log.info("Fund reservation created successfully: accountId={}, transactionId={}, amount={}", 
                    accountId, transactionId, amount);
            
            return FundReservationDto.builder()
                .reservationId(UUID.randomUUID()) // This would come from the lock manager
                .accountId(accountId)
                .transactionId(transactionId)
                .amount(amount)
                .currency(account.getCurrency())
                .status(FundReservationStatus.ACTIVE)
                .expiresAt(LocalDateTime.now().plus(finalDuration))
                .createdAt(LocalDateTime.now())
                .build();
        });
    }

    /**
     * Securely executes a transfer between two accounts
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public TransferResultDto executeTransfer(UUID fromAccountId, UUID toAccountId, 
                                           BigDecimal amount, String description) {
        
        return lockManager.executeTransfer(fromAccountId, toAccountId, amount, () -> {
            log.info("Executing secure transfer: from={}, to={}, amount={}", 
                    fromAccountId, toAccountId, amount);
            
            // Get both accounts with database locks in consistent order
            Account fromAccount = getAccountForUpdate(fromAccountId);
            Account toAccount = getAccountForUpdate(toAccountId);
            
            // Validate transfer
            validateTransfer(fromAccount, toAccount, amount);
            
            // Check sufficient funds
            if (fromAccount.getAvailableBalance().compareTo(amount) < 0) {
                throw new InsufficientFundsException(
                    "Insufficient funds for transfer: required=" + amount + 
                    ", available=" + fromAccount.getAvailableBalance());
            }
            
            // Execute transfer atomically
            BigDecimal fromNewBalance = fromAccount.getCurrentBalance().subtract(amount);
            BigDecimal fromNewAvailable = fromAccount.getAvailableBalance().subtract(amount);
            
            BigDecimal toNewBalance = toAccount.getCurrentBalance().add(amount);
            BigDecimal toNewAvailable = toAccount.getAvailableBalance().add(amount);
            
            // Update accounts
            fromAccount.setCurrentBalance(fromNewBalance);
            fromAccount.setAvailableBalance(fromNewAvailable);
            fromAccount.setLastTransactionDate(LocalDateTime.now());
            fromAccount.updateVersion();
            
            toAccount.setCurrentBalance(toNewBalance);
            toAccount.setAvailableBalance(toNewAvailable);
            toAccount.setLastTransactionDate(LocalDateTime.now());
            toAccount.updateVersion();
            
            // Save both accounts
            Account savedFromAccount = accountRepository.save(fromAccount);
            Account savedToAccount = accountRepository.save(toAccount);
            
            log.info("Transfer executed successfully: from={}, to={}, amount={}", 
                    fromAccountId, toAccountId, amount);
            
            return TransferResultDto.builder()
                .transferId(UUID.randomUUID())
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .amount(amount)
                .currency(fromAccount.getCurrency())
                .description(description)
                .status(TransferStatus.COMPLETED)
                .fromAccountBalance(savedFromAccount.getCurrentBalance())
                .toAccountBalance(savedToAccount.getCurrentBalance())
                .executedAt(LocalDateTime.now())
                .build();
        });
    }

    /**
     * Gets account balance with caching
     */
    @Cacheable(value = "account-balances", key = "#accountId")
    public AccountBalanceDto getAccountBalance(UUID accountId) {
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        
        return AccountBalanceDto.builder()
            .accountId(account.getAccountId())
            .currentBalance(account.getCurrentBalance())
            .availableBalance(account.getAvailableBalance())
            .currency(account.getCurrency())
            .lastUpdated(account.getLastTransactionDate())
            .version(account.getVersion())
            .build();
    }

    /**
     * Gets account with SELECT FOR UPDATE lock
     */
    private Account getAccountForUpdate(UUID accountId) {
        // This would use a custom repository method with SELECT FOR UPDATE
        return accountRepository.findByIdForUpdate(accountId)
            .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
    }

    /**
     * Validates balance update operation
     */
    private void validateBalanceUpdate(Account account, BigDecimal debitAmount, BigDecimal creditAmount) {
        validationService.validateAccountActive(account);
        
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
     * Validates fund reservation request
     */
    private void validateFundReservation(Account account, BigDecimal amount) {
        validationService.validateAccountActive(account);
        validationService.validatePositiveAmount(amount);
        
        if (!account.supportsReservations()) {
            throw new IllegalArgumentException("Account does not support fund reservations");
        }
    }

    /**
     * Validates transfer request
     */
    private void validateTransfer(Account fromAccount, Account toAccount, BigDecimal amount) {
        validationService.validateAccountActive(fromAccount);
        validationService.validateAccountActive(toAccount);
        validationService.validatePositiveAmount(amount);
        
        if (fromAccount.getId().equals(toAccount.getId())) {
            throw new IllegalArgumentException("Cannot transfer to the same account");
        }
        
        if (!fromAccount.getCurrency().equals(toAccount.getCurrency())) {
            throw new IllegalArgumentException("Currency mismatch in transfer accounts");
        }
    }

    /**
     * Checks if a fund reservation already exists
     */
    private boolean reservationExists(UUID accountId, UUID transactionId) {
        // This would query the fund_reservations table
        return accountRepository.existsActiveFundReservation(accountId, transactionId);
    }
}