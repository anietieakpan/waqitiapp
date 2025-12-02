package com.waqiti.corebanking.service;

import com.waqiti.corebanking.domain.Account;
import com.waqiti.corebanking.domain.LedgerEntry;
import com.waqiti.corebanking.domain.Transaction;
import com.waqiti.corebanking.repository.AccountRepository;
import com.waqiti.corebanking.repository.LedgerEntryRepository;
import com.waqiti.corebanking.repository.TransactionRepository;
import com.waqiti.corebanking.exception.AccountNotFoundException;
import com.waqiti.corebanking.exception.InsufficientFundsException;
import com.waqiti.corebanking.exception.TransactionProcessingException;
import com.waqiti.common.locking.DistributedLockService;
import com.waqiti.common.idempotency.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Double Entry Bookkeeping Service
 * 
 * Core service implementing double-entry bookkeeping principles.
 * Ensures all transactions maintain balanced debits and credits.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DoubleEntryBookkeepingService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final TransactionRepository transactionRepository;
    private final DistributedLockService lockService;
    private final IdempotencyService idempotencyService;
    private final AtomicLong entryNumberGenerator = new AtomicLong(1);

    /**
     * Processes a transaction using double-entry bookkeeping
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Transaction processTransaction(TransactionRequest request) {
        // Check idempotency
        if (request.getIdempotencyKey() != null) {
            var existingResult = idempotencyService.checkIdempotency(
                request.getIdempotencyKey(), Transaction.class);
            if (existingResult.isPresent()) {
                log.info("Returning existing transaction for idempotency key: {}", 
                    request.getIdempotencyKey());
                return existingResult.get().getResult();
            }
        }

        // Create transaction record
        Transaction transaction = createTransaction(request);
        transaction = transactionRepository.save(transaction);

        try {
            // Process the double-entry bookkeeping
            List<LedgerEntry> entries = createLedgerEntries(transaction, request);
            
            // Apply entries to accounts
            applyLedgerEntries(entries);
            
            // Mark transaction as completed
            transaction.markAsCompleted();
            transaction = transactionRepository.save(transaction);

            // Store idempotency result
            if (request.getIdempotencyKey() != null) {
                idempotencyService.storeResult(request.getIdempotencyKey(), transaction);
            }

            log.info("Successfully processed transaction: {}", transaction.getTransactionNumber());
            return transaction;

        } catch (Exception e) {
            // Mark transaction as failed
            transaction.markAsFailed(e.getMessage());
            transactionRepository.save(transaction);
            
            log.error("Failed to process transaction: {}", transaction.getTransactionNumber(), e);
            throw new TransactionProcessingException("Transaction processing failed", e);
        }
    }

    /**
     * Creates balanced ledger entries for a transaction
     */
    private List<LedgerEntry> createLedgerEntries(Transaction transaction, TransactionRequest request) {
        List<LedgerEntry> entries = new ArrayList<>();
        LocalDateTime entryDate = transaction.getValueDate();

        switch (transaction.getTransactionType()) {
            case P2P_TRANSFER:
                entries.addAll(createP2PTransferEntries(transaction, request, entryDate));
                break;
            case DEPOSIT:
                entries.addAll(createDepositEntries(transaction, request, entryDate));
                break;
            case WITHDRAWAL:
                entries.addAll(createWithdrawalEntries(transaction, request, entryDate));
                break;
            case FEE_CHARGE:
                entries.addAll(createFeeChargeEntries(transaction, request, entryDate));
                break;
            case INTERNAL_TRANSFER:
                entries.addAll(createInternalTransferEntries(transaction, request, entryDate));
                break;
            default:
                throw new TransactionProcessingException("Unsupported transaction type: " + 
                    transaction.getTransactionType());
        }

        // Validate entries are balanced
        validateBalancedEntries(entries);

        // Save entries
        return ledgerEntryRepository.saveAll(entries);
    }

    /**
     * Creates P2P transfer entries
     */
    private List<LedgerEntry> createP2PTransferEntries(Transaction transaction, 
            TransactionRequest request, LocalDateTime entryDate) {
        List<LedgerEntry> entries = new ArrayList<>();

        // Debit source account
        entries.add(createLedgerEntry(
            transaction,
            request.getSourceAccountId(),
            LedgerEntry.EntryType.DEBIT,
            transaction.getAmount(),
            entryDate,
            "P2P Transfer - Debit"
        ));

        // Credit target account
        entries.add(createLedgerEntry(
            transaction,
            request.getTargetAccountId(),
            LedgerEntry.EntryType.CREDIT,
            transaction.getAmount(),
            entryDate,
            "P2P Transfer - Credit"
        ));

        // Handle fees if present
        if (transaction.getFeeAmount() != null && transaction.getFeeAmount().compareTo(BigDecimal.ZERO) > 0) {
            // Additional debit from source for fee
            entries.add(createLedgerEntry(
                transaction,
                request.getSourceAccountId(),
                LedgerEntry.EntryType.DEBIT,
                transaction.getFeeAmount(),
                entryDate,
                "P2P Transfer Fee - Debit"
            ));

            // Credit fee collection account
            UUID feeAccountId = getSystemAccount(Account.AccountType.FEE_COLLECTION);
            entries.add(createLedgerEntry(
                transaction,
                feeAccountId,
                LedgerEntry.EntryType.CREDIT,
                transaction.getFeeAmount(),
                entryDate,
                "P2P Transfer Fee - Credit"
            ));
        }

        return entries;
    }

    /**
     * Creates deposit entries
     */
    private List<LedgerEntry> createDepositEntries(Transaction transaction, 
            TransactionRequest request, LocalDateTime entryDate) {
        List<LedgerEntry> entries = new ArrayList<>();

        // Credit user account
        entries.add(createLedgerEntry(
            transaction,
            request.getTargetAccountId(),
            LedgerEntry.EntryType.CREDIT,
            transaction.getAmount(),
            entryDate,
            "Deposit - Credit to user account"
        ));

        // Debit system cash account
        UUID systemCashAccountId = getSystemAccount(Account.AccountType.SYSTEM_ASSET);
        entries.add(createLedgerEntry(
            transaction,
            systemCashAccountId,
            LedgerEntry.EntryType.DEBIT,
            transaction.getAmount(),
            entryDate,
            "Deposit - Debit from system cash"
        ));

        return entries;
    }

    /**
     * Creates withdrawal entries
     */
    private List<LedgerEntry> createWithdrawalEntries(Transaction transaction, 
            TransactionRequest request, LocalDateTime entryDate) {
        List<LedgerEntry> entries = new ArrayList<>();

        // Debit user account
        entries.add(createLedgerEntry(
            transaction,
            request.getSourceAccountId(),
            LedgerEntry.EntryType.DEBIT,
            transaction.getAmount(),
            entryDate,
            "Withdrawal - Debit from user account"
        ));

        // Credit system cash account
        UUID systemCashAccountId = getSystemAccount(Account.AccountType.SYSTEM_ASSET);
        entries.add(createLedgerEntry(
            transaction,
            systemCashAccountId,
            LedgerEntry.EntryType.CREDIT,
            transaction.getAmount(),
            entryDate,
            "Withdrawal - Credit to system cash"
        ));

        return entries;
    }

    /**
     * Creates fee charge entries
     */
    private List<LedgerEntry> createFeeChargeEntries(Transaction transaction, 
            TransactionRequest request, LocalDateTime entryDate) {
        List<LedgerEntry> entries = new ArrayList<>();

        // Debit user account
        entries.add(createLedgerEntry(
            transaction,
            request.getSourceAccountId(),
            LedgerEntry.EntryType.DEBIT,
            transaction.getAmount(),
            entryDate,
            "Fee Charge - Debit"
        ));

        // Credit fee collection account
        UUID feeAccountId = getSystemAccount(Account.AccountType.FEE_COLLECTION);
        entries.add(createLedgerEntry(
            transaction,
            feeAccountId,
            LedgerEntry.EntryType.CREDIT,
            transaction.getAmount(),
            entryDate,
            "Fee Charge - Credit"
        ));

        return entries;
    }

    /**
     * Creates internal transfer entries
     */
    private List<LedgerEntry> createInternalTransferEntries(Transaction transaction, 
            TransactionRequest request, LocalDateTime entryDate) {
        List<LedgerEntry> entries = new ArrayList<>();

        // Debit source account
        entries.add(createLedgerEntry(
            transaction,
            request.getSourceAccountId(),
            LedgerEntry.EntryType.DEBIT,
            transaction.getAmount(),
            entryDate,
            "Internal Transfer - Debit"
        ));

        // Credit target account
        entries.add(createLedgerEntry(
            transaction,
            request.getTargetAccountId(),
            LedgerEntry.EntryType.CREDIT,
            transaction.getAmount(),
            entryDate,
            "Internal Transfer - Credit"
        ));

        return entries;
    }

    /**
     * Creates a single ledger entry
     */
    private LedgerEntry createLedgerEntry(Transaction transaction, UUID accountId, 
            LedgerEntry.EntryType entryType, BigDecimal amount, LocalDateTime entryDate, String description) {
        return LedgerEntry.builder()
            .transactionId(transaction.getId())
            .accountId(accountId)
            .entryNumber(entryNumberGenerator.getAndIncrement())
            .entryType(entryType)
            .amount(amount)
            .currency(transaction.getCurrency())
            .description(description)
            .reference(transaction.getReference())
            .externalReference(transaction.getExternalReference())
            .status(LedgerEntry.EntryStatus.PENDING)
            .entryDate(entryDate)
            .valueDate(entryDate)
            .createdBy("SYSTEM")
            .build();
    }

    /**
     * Applies ledger entries to accounts with proper locking
     */
    private void applyLedgerEntries(List<LedgerEntry> entries) {
        for (LedgerEntry entry : entries) {
            String lockKey = "account_" + entry.getAccountId();
            
            var lock = lockService.acquireLock(lockKey, 
                java.time.Duration.ofSeconds(30), 
                java.time.Duration.ofMinutes(5));
            
            try {
                if (lock == null) {
                    throw new TransactionProcessingException("Failed to acquire lock for account: " + entry.getAccountId());
                }

                Account account = accountRepository.findById(entry.getAccountId())
                    .orElseThrow(() -> new AccountNotFoundException("Account not found: " + entry.getAccountId()));

                // Validate account can handle the operation
                if (entry.isDebit() && !account.canDebit(entry.getAmount())) {
                    throw new InsufficientFundsException("Insufficient funds in account: " + account.getAccountNumber());
                }

                if (entry.isCredit() && !account.canCredit(entry.getAmount())) {
                    throw new TransactionProcessingException("Cannot credit account: " + account.getAccountNumber());
                }

                // Apply the entry to account balance
                BigDecimal newBalance = calculateNewBalance(account, entry);
                entry.setRunningBalance(newBalance);

                // Update account balances
                if (entry.isDebit()) {
                    account.debit(entry.getAmount());
                } else {
                    account.credit(entry.getAmount());
                }

                // Mark entry as posted
                entry.markAsPosted();

                // Save changes
                accountRepository.save(account);
                ledgerEntryRepository.save(entry);

                log.debug("Applied entry {} to account {}: {} {} {}", 
                    entry.getId(), account.getAccountNumber(), 
                    entry.getEntryType(), entry.getAmount(), entry.getCurrency());

            } finally {
                if (lock != null) {
                    lock.release();
                }
            }
        }
    }

    /**
     * Calculates new balance after entry
     */
    private BigDecimal calculateNewBalance(Account account, LedgerEntry entry) {
        BigDecimal currentBalance = account.getCurrentBalance();
        
        if (entry.isDebit()) {
            return currentBalance.subtract(entry.getAmount());
        } else {
            return currentBalance.add(entry.getAmount());
        }
    }

    /**
     * Validates that entries are balanced (total debits = total credits)
     */
    private void validateBalancedEntries(List<LedgerEntry> entries) {
        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;

        for (LedgerEntry entry : entries) {
            if (entry.isDebit()) {
                totalDebits = totalDebits.add(entry.getAmount());
            } else {
                totalCredits = totalCredits.add(entry.getAmount());
            }
        }

        if (totalDebits.compareTo(totalCredits) != 0) {
            throw new TransactionProcessingException(
                String.format("Unbalanced entries: Debits=%s, Credits=%s", totalDebits, totalCredits));
        }

        log.debug("Validated balanced entries: Debits={}, Credits={}", totalDebits, totalCredits);
    }

    /**
     * Gets system account by type
     */
    private UUID getSystemAccount(Account.AccountType accountType) {
        return accountRepository.findSystemAccountByType(accountType)
            .map(Account::getAccountId)
            .orElseThrow(() -> new AccountNotFoundException("System account not found: " + accountType));
    }

    /**
     * Creates transaction record
     */
    private Transaction createTransaction(TransactionRequest request) {
        return Transaction.builder()
            .transactionNumber(generateTransactionNumber())
            .transactionType(request.getTransactionType())
            .sourceAccountId(request.getSourceAccountId())
            .targetAccountId(request.getTargetAccountId())
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .feeAmount(request.getFeeAmount())
            .description(request.getDescription())
            .reference(request.getReference())
            .externalReference(request.getExternalReference())
            .status(Transaction.TransactionStatus.PROCESSING)
            .initiatedBy(request.getInitiatedBy())
            .priority(request.getPriority() != null ? request.getPriority() : Transaction.TransactionPriority.NORMAL)
            .transactionDate(LocalDateTime.now())
            .valueDate(request.getValueDate() != null ? request.getValueDate() : LocalDateTime.now())
            .idempotencyKey(request.getIdempotencyKey())
            .metadata(request.getMetadata())
            .createdBy("SYSTEM")
            .build();
    }

    /**
     * Generates unique transaction number
     */
    private final SecureRandom secureRandom = new SecureRandom();
    
    private String generateTransactionNumber() {
        return "TXN-" + System.currentTimeMillis() + "-" + 
               String.format("%06d", secureRandom.nextInt(1000000));
    }

    // DTOs

    @lombok.Data
    @lombok.Builder
    public static class TransactionRequest {
        private Transaction.TransactionType transactionType;
        private UUID sourceAccountId;
        private UUID targetAccountId;
        private BigDecimal amount;
        private String currency;
        private BigDecimal feeAmount;
        private String description;
        private String reference;
        private String externalReference;
        private UUID initiatedBy;
        private Transaction.TransactionPriority priority;
        private LocalDateTime valueDate;
        private String idempotencyKey;
        private String metadata;
    }
}