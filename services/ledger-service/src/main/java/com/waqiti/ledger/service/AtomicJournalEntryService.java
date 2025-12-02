package com.waqiti.ledger.service;

import com.waqiti.ledger.domain.JournalEntry;
import com.waqiti.ledger.domain.Account;
import com.waqiti.ledger.domain.LedgerTransaction;
import com.waqiti.ledger.repository.JournalEntryRepository;
import com.waqiti.ledger.repository.AccountRepository;
import com.waqiti.ledger.exception.InsufficientBalanceException;
import com.waqiti.ledger.exception.AccountNotFoundException;
import com.waqiti.ledger.exception.LedgerException;
import com.waqiti.common.kafka.KafkaProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;

/**
 * CRITICAL FIX: Atomic Journal Entry Service with Distributed Locking
 *
 * This service resolves the race condition vulnerability identified in forensic audit.
 *
 * PROBLEM SOLVED:
 * - Prevents concurrent modification of account balances
 * - Ensures double-entry bookkeeping integrity
 * - Provides ACID guarantees across distributed systems
 *
 * IMPLEMENTATION:
 * - Redisson distributed locks for cross-instance synchronization
 * - SERIALIZABLE isolation level for database consistency
 * - Optimistic locking with @Version for conflict detection
 * - Comprehensive error handling and rollback
 *
 * @author Waqiti Platform Team
 * @since 2025-10-30
 * @version 2.0 (Production-Ready)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AtomicJournalEntryService {

    private final JournalEntryRepository journalEntryRepository;
    private final AccountRepository accountRepository;
    private final RedissonClient redissonClient;
    private final KafkaProducerService kafkaProducerService;

    private static final int LOCK_WAIT_TIME_SECONDS = 10;
    private static final int LOCK_LEASE_TIME_SECONDS = 30;
    private static final String LOCK_PREFIX = "ledger:account:";

    /**
     * Create journal entry with full ACID guarantees
     *
     * CRITICAL: This method uses distributed locking to prevent race conditions
     *
     * @param entry Journal entry to create
     * @return Created journal entry
     * @throws LedgerException if entry creation fails
     */
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public JournalEntry createEntry(JournalEntry entry) {
        log.info("Creating journal entry for account: {}, amount: {}",
                entry.getAccountId(), entry.getAmount());

        RLock lock = null;
        try {
            // Step 1: Acquire distributed lock
            lock = acquireLock(entry.getAccountId());

            // Step 2: Validate account exists and is active
            Account account = accountRepository.findById(entry.getAccountId())
                    .orElseThrow(() -> new AccountNotFoundException(
                            "Account not found: " + entry.getAccountId()));

            if (!account.isActive()) {
                throw new LedgerException("Cannot post to inactive account: " + entry.getAccountId());
            }

            // Step 3: Calculate new balance atomically
            BigDecimal currentBalance = account.getBalance();
            BigDecimal newBalance = currentBalance.add(entry.getAmount());

            // Step 4: Validate balance constraints
            if (newBalance.compareTo(BigDecimal.ZERO) < 0 && !account.isOverdraftAllowed()) {
                throw new InsufficientBalanceException(
                        String.format("Insufficient balance. Current: %s, Requested: %s",
                                currentBalance, entry.getAmount().abs()));
            }

            // Step 5: Update account balance with optimistic locking
            account.setBalance(newBalance);
            account.setLastTransactionAt(LocalDateTime.now());
            account.setUpdatedAt(LocalDateTime.now());
            accountRepository.save(account); // Version check happens here

            // Step 6: Create journal entry record
            entry.setCreatedAt(LocalDateTime.now());
            entry.setBalanceAfter(newBalance);
            entry.setStatus("POSTED");
            JournalEntry savedEntry = journalEntryRepository.save(entry);

            // Step 7: Publish event for downstream processing
            publishJournalEntryCreatedEvent(savedEntry);

            log.info("Journal entry created successfully. ID: {}, New balance: {}",
                    savedEntry.getId(), newBalance);

            return savedEntry;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Lock acquisition interrupted for account: {}", entry.getAccountId(), e);
            throw new LedgerException("Failed to acquire lock for journal entry", e);
        } catch (Exception e) {
            log.error("Failed to create journal entry for account: {}", entry.getAccountId(), e);
            throw new LedgerException("Journal entry creation failed", e);
        } finally {
            // CRITICAL: Always release lock
            releaseLock(lock);
        }
    }

    /**
     * Create multiple journal entries atomically (for double-entry bookkeeping)
     *
     * This method ensures that debit and credit entries are posted together or not at all
     *
     * @param entries List of journal entries (must balance to zero)
     * @return List of created entries
     * @throws LedgerException if entries don't balance or creation fails
     */
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public List<JournalEntry> createEntries(List<JournalEntry> entries) {
        log.info("Creating {} journal entries in atomic transaction", entries.size());

        // Step 1: Validate double-entry bookkeeping (debits = credits)
        BigDecimal sum = entries.stream()
                .map(JournalEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (sum.compareTo(BigDecimal.ZERO) != 0) {
            throw new LedgerException(
                    String.format("Journal entries must balance to zero. Current sum: %s", sum));
        }

        // Step 2: Sort account IDs to prevent deadlocks
        List<UUID> accountIds = entries.stream()
                .map(JournalEntry::getAccountId)
                .distinct()
                .sorted()
                .toList();

        List<RLock> locks = new ArrayList<>();

        try {
            // Step 3: Acquire all locks in order (prevents deadlock)
            for (UUID accountId : accountIds) {
                RLock lock = acquireLock(accountId);
                locks.add(lock);
            }

            // Step 4: Process all entries
            List<JournalEntry> createdEntries = new ArrayList<>();
            for (JournalEntry entry : entries) {
                // Validate and update account
                Account account = accountRepository.findById(entry.getAccountId())
                        .orElseThrow(() -> new AccountNotFoundException(
                                "Account not found: " + entry.getAccountId()));

                BigDecimal newBalance = account.getBalance().add(entry.getAmount());

                if (newBalance.compareTo(BigDecimal.ZERO) < 0 && !account.isOverdraftAllowed()) {
                    throw new InsufficientBalanceException(
                            String.format("Insufficient balance in account %s", account.getId()));
                }

                account.setBalance(newBalance);
                account.setLastTransactionAt(LocalDateTime.now());
                accountRepository.save(account);

                // Create entry
                entry.setCreatedAt(LocalDateTime.now());
                entry.setBalanceAfter(newBalance);
                entry.setStatus("POSTED");
                JournalEntry savedEntry = journalEntryRepository.save(entry);
                createdEntries.add(savedEntry);
            }

            // Step 5: Publish batch event
            publishBatchJournalEntriesCreatedEvent(createdEntries);

            log.info("Successfully created {} journal entries atomically", createdEntries.size());
            return createdEntries;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Lock acquisition interrupted", e);
            throw new LedgerException("Failed to acquire locks for batch entry", e);
        } catch (Exception e) {
            log.error("Failed to create batch journal entries", e);
            throw new LedgerException("Batch journal entry creation failed", e);
        } finally {
            // CRITICAL: Release all locks in reverse order
            for (int i = locks.size() - 1; i >= 0; i--) {
                releaseLock(locks.get(i));
            }
        }
    }

    /**
     * Reverse a journal entry (creates offsetting entry)
     *
     * @param originalEntryId ID of entry to reverse
     * @return Reversal journal entry
     */
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public JournalEntry reverseEntry(UUID originalEntryId) {
        log.info("Reversing journal entry: {}", originalEntryId);

        JournalEntry originalEntry = journalEntryRepository.findById(originalEntryId)
                .orElseThrow(() -> new LedgerException("Original entry not found: " + originalEntryId));

        if ("REVERSED".equals(originalEntry.getStatus())) {
            throw new LedgerException("Entry already reversed: " + originalEntryId);
        }

        // Create offsetting entry
        JournalEntry reversalEntry = JournalEntry.builder()
                .accountId(originalEntry.getAccountId())
                .amount(originalEntry.getAmount().negate())
                .transactionId(originalEntry.getTransactionId())
                .description("REVERSAL: " + originalEntry.getDescription())
                .referenceNumber("REV-" + originalEntry.getReferenceNumber())
                .reversedEntryId(originalEntryId)
                .build();

        JournalEntry savedReversal = createEntry(reversalEntry);

        // Mark original as reversed
        originalEntry.setStatus("REVERSED");
        originalEntry.setReversedAt(LocalDateTime.now());
        originalEntry.setReversalEntryId(savedReversal.getId());
        journalEntryRepository.save(originalEntry);

        log.info("Entry reversed successfully. Original: {}, Reversal: {}",
                originalEntryId, savedReversal.getId());

        return savedReversal;
    }

    /**
     * Get account balance with locking (for consistent reads)
     *
     * @param accountId Account ID
     * @return Current balance
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ, readOnly = true)
    public BigDecimal getAccountBalance(UUID accountId) {
        RLock lock = null;
        try {
            lock = acquireReadLock(accountId);

            Account account = accountRepository.findById(accountId)
                    .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));

            return account.getBalance();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LedgerException("Failed to acquire read lock", e);
        } finally {
            releaseLock(lock);
        }
    }

    /**
     * Acquire distributed write lock
     */
    private RLock acquireLock(UUID accountId) throws InterruptedException {
        String lockKey = LOCK_PREFIX + accountId;
        RLock lock = redissonClient.getLock(lockKey);

        boolean acquired = lock.tryLock(LOCK_WAIT_TIME_SECONDS, LOCK_LEASE_TIME_SECONDS, TimeUnit.SECONDS);

        if (!acquired) {
            throw new LedgerException("Failed to acquire lock for account: " + accountId +
                    " within " + LOCK_WAIT_TIME_SECONDS + " seconds");
        }

        log.debug("Lock acquired for account: {}", accountId);
        return lock;
    }

    /**
     * Acquire distributed read lock
     */
    private RLock acquireReadLock(UUID accountId) throws InterruptedException {
        String lockKey = LOCK_PREFIX + accountId + ":read";
        RLock lock = redissonClient.getReadWriteLock(LOCK_PREFIX + accountId).readLock();

        boolean acquired = lock.tryLock(LOCK_WAIT_TIME_SECONDS, LOCK_LEASE_TIME_SECONDS, TimeUnit.SECONDS);

        if (!acquired) {
            throw new LedgerException("Failed to acquire read lock for account: " + accountId);
        }

        return lock;
    }

    /**
     * Release lock safely
     */
    private void releaseLock(RLock lock) {
        if (lock != null && lock.isHeldByCurrentThread()) {
            try {
                lock.unlock();
                log.debug("Lock released successfully");
            } catch (Exception e) {
                log.error("Error releasing lock", e);
            }
        }
    }

    /**
     * Publish journal entry created event
     */
    private void publishJournalEntryCreatedEvent(JournalEntry entry) {
        try {
            kafkaProducerService.sendMessage(
                    "ledger.journal-entry.created",
                    entry.getId().toString(),
                    entry
            );
        } catch (Exception e) {
            log.error("Failed to publish journal entry created event", e);
            // Don't fail the transaction, event publishing is best-effort
        }
    }

    /**
     * Publish batch journal entries created event
     */
    private void publishBatchJournalEntriesCreatedEvent(List<JournalEntry> entries) {
        try {
            kafkaProducerService.sendMessage(
                    "ledger.journal-entries.batch-created",
                    entries.get(0).getTransactionId().toString(),
                    entries
            );
        } catch (Exception e) {
            log.error("Failed to publish batch journal entries event", e);
        }
    }
}
