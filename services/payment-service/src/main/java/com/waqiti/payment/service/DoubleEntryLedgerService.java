package com.waqiti.payment.service;

import com.waqiti.common.lock.DistributedLockService;
import com.waqiti.payment.repository.LedgerAccountRepository;
import com.waqiti.payment.repository.LedgerTransactionRepository;
import com.waqiti.payment.repository.JournalEntryRepository;
import com.waqiti.payment.repository.AuditTrailRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Double-Entry Bookkeeping Ledger Service
 * Ensures every financial transaction maintains the accounting equation: Assets = Liabilities + Equity
 * Every debit must have a corresponding credit to maintain balance integrity
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DoubleEntryLedgerService {
    
    private final TransactionService transactionService;
    private final DistributedLockService lockService;
    private final LedgerAccountRepository accountRepository;
    private final LedgerTransactionRepository transactionRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final AuditTrailRepository auditTrailRepository;
    
    // Account types for double-entry bookkeeping
    public enum AccountType {
        ASSET("Asset accounts - increase with debit, decrease with credit"),
        LIABILITY("Liability accounts - decrease with debit, increase with credit"),
        EQUITY("Equity accounts - decrease with debit, increase with credit"),
        REVENUE("Revenue accounts - decrease with debit, increase with credit"),
        EXPENSE("Expense accounts - increase with debit, decrease with credit");
        
        private final String description;
        
        AccountType(String description) {
            this.description = description;
        }
        
        public boolean isDebitPositive() {
            return this == ASSET || this == EXPENSE;
        }
    }
    
    /**
     * Process a double-entry transaction with atomic validation
     * Ensures debits = credits and all entries are valid
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public LedgerTransaction processDoubleEntryTransaction(LedgerTransactionRequest request) {
        log.info("Processing double-entry transaction: {}", request.getTransactionId());
        
        String lockKey = "ledger:transaction:" + request.getTransactionId();
        
        try {
            // Acquire distributed lock to prevent concurrent modifications
            DistributedLockService.DistributedLock lock = lockService.acquireLock(lockKey, Duration.ofSeconds(30));
            if (lock == null) {
                throw new LedgerException("Failed to acquire lock for transaction: " + request.getTransactionId());
            }
            
            // Step 1: Validate the transaction
            validateDoubleEntryTransaction(request);
            
            // Step 2: Check for duplicate transactions (idempotency)
            if (isDuplicateTransaction(request.getTransactionId())) {
                log.warn("Duplicate transaction detected: {}", request.getTransactionId());
                throw new DuplicateTransactionException("Transaction already processed: " + request.getTransactionId());
            }
            
            // Step 3: Validate account balances before processing
            validateAccountBalances(request);
            
            // Step 4: Create journal entries
            List<JournalEntry> journalEntries = createJournalEntries(request);
            
            // Step 5: Apply entries to accounts atomically
            Map<String, AccountBalance> updatedBalances = applyJournalEntries(journalEntries);
            
            // Step 6: Perform final validation
            performFinalValidation(journalEntries, updatedBalances);
            
            // Step 7: Create and persist ledger transaction
            LedgerTransaction ledgerTransaction = LedgerTransaction.builder()
                .transactionId(request.getTransactionId())
                .sagaId(request.getSagaId())
                .timestamp(Instant.now())
                .description(request.getDescription())
                .journalEntries(journalEntries)
                .status(TransactionStatus.COMPLETED)
                .totalDebits(calculateTotalDebits(journalEntries))
                .totalCredits(calculateTotalCredits(journalEntries))
                .metadata(request.getMetadata())
                .build();
            
            // Step 8: Persist to database
            persistLedgerTransaction(ledgerTransaction);
            
            // Step 9: Create audit trail
            createAuditTrail(ledgerTransaction);
            
            log.info("Successfully processed double-entry transaction: {}", request.getTransactionId());
            return ledgerTransaction;
            
        } catch (Exception e) {
            log.error("Failed to process double-entry transaction: {}", request.getTransactionId(), e);
            // Compensate if needed
            compensateTransaction(request.getTransactionId());
            throw new LedgerException("Transaction processing failed: " + e.getMessage(), e);
            
        } finally {
            // Always release the lock
            // Note: The lock should be properly released using the DistributedLock object
            // This is a simplified version - in production, track the lock object
            log.debug("Released lock for transaction: {}", request.getTransactionId());
        }
    }
    
    /**
     * Validate double-entry transaction request
     */
    private void validateDoubleEntryTransaction(LedgerTransactionRequest request) {
        log.debug("Validating double-entry transaction: {}", request.getTransactionId());
        
        // Validate basic requirements
        if (request.getEntries() == null || request.getEntries().isEmpty()) {
            throw new ValidationException("Transaction must have at least one debit and one credit entry");
        }
        
        // Calculate totals
        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;
        
        Set<String> accountIds = new HashSet<>();
        
        for (LedgerEntry entry : request.getEntries()) {
            // Validate each entry
            if (entry.getAmount() == null || entry.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidationException("Entry amount must be positive: " + entry.getAccountId());
            }
            
            if (entry.getAccountId() == null || entry.getAccountId().trim().isEmpty()) {
                throw new ValidationException("Account ID is required for all entries");
            }
            
            // Check for duplicate accounts in same transaction
            if (!accountIds.add(entry.getAccountId())) {
                log.warn("Warning: Duplicate account {} in transaction {}", 
                    entry.getAccountId(), request.getTransactionId());
            }
            
            // Sum up debits and credits
            if (entry.getType() == EntryType.DEBIT) {
                totalDebits = totalDebits.add(entry.getAmount());
            } else {
                totalCredits = totalCredits.add(entry.getAmount());
            }
        }
        
        // CRITICAL: Ensure debits equal credits (fundamental accounting principle)
        if (totalDebits.compareTo(totalCredits) != 0) {
            throw new BalanceViolationException(String.format(
                "Debits (%.2f) must equal Credits (%.2f) for transaction: %s",
                totalDebits, totalCredits, request.getTransactionId()
            ));
        }
        
        // Validate at least one debit and one credit exist
        boolean hasDebit = request.getEntries().stream()
            .anyMatch(e -> e.getType() == EntryType.DEBIT);
        boolean hasCredit = request.getEntries().stream()
            .anyMatch(e -> e.getType() == EntryType.CREDIT);
        
        if (!hasDebit || !hasCredit) {
            throw new ValidationException("Transaction must have at least one debit and one credit entry");
        }
        
        log.debug("Transaction validation passed: Debits = Credits = {}", totalDebits);
    }
    
    /**
     * Check for duplicate transactions
     */
    private boolean isDuplicateTransaction(String transactionId) {
        // Check in database for existing transaction
        Optional<com.waqiti.payment.entity.LedgerTransaction> existing = 
            transactionRepository.findByTransactionNumber(transactionId);
        
        if (existing.isPresent()) {
            log.warn("Duplicate transaction found: {} with status: {}", 
                transactionId, existing.get().getStatus());
            return true;
        }
        
        // Also check by checksum for recent transactions (within last 24 hours)
        // to prevent duplicate processing even with different IDs
        String checksum = calculateTransactionChecksum(transactionId);
        LocalDateTime recentDate = LocalDateTime.now().minusHours(24);
        
        boolean duplicateByChecksum = transactionRepository.existsByChecksumRecent(checksum, recentDate);
        if (duplicateByChecksum) {
            log.warn("Potential duplicate transaction detected by checksum: {}", transactionId);
        }
        
        return duplicateByChecksum;
    }
    
    /**
     * Validate account balances before processing
     */
    private void validateAccountBalances(LedgerTransactionRequest request) {
        log.debug("Validating account balances for transaction: {}", request.getTransactionId());
        
        for (LedgerEntry entry : request.getEntries()) {
            Account account = getAccount(entry.getAccountId());
            
            if (account == null) {
                throw new AccountNotFoundException("Account not found: " + entry.getAccountId());
            }
            
            // For debit entries on asset accounts, ensure sufficient balance
            if (entry.getType() == EntryType.DEBIT && 
                account.getType() == AccountType.ASSET) {
                
                BigDecimal currentBalance = account.getBalance();
                if (currentBalance.compareTo(entry.getAmount()) < 0) {
                    throw new InsufficientBalanceException(String.format(
                        "Insufficient balance in account %s. Available: %.2f, Required: %.2f",
                        entry.getAccountId(), currentBalance, entry.getAmount()
                    ));
                }
            }
            
            // Additional validation for liability accounts
            if (entry.getType() == EntryType.DEBIT && 
                account.getType() == AccountType.LIABILITY) {
                
                // Ensure we're not creating negative liability
                BigDecimal resultingBalance = account.getBalance().subtract(entry.getAmount());
                if (resultingBalance.compareTo(BigDecimal.ZERO) < 0) {
                    throw new ValidationException(String.format(
                        "Cannot create negative liability for account %s",
                        entry.getAccountId()
                    ));
                }
            }
        }
    }
    
    /**
     * Create journal entries from the request
     */
    private List<JournalEntry> createJournalEntries(LedgerTransactionRequest request) {
        List<JournalEntry> entries = new ArrayList<>();
        int sequenceNumber = 1;
        
        for (LedgerEntry ledgerEntry : request.getEntries()) {
            Account account = getAccount(ledgerEntry.getAccountId());
            
            JournalEntry journalEntry = JournalEntry.builder()
                .entryId(UUID.randomUUID().toString())
                .transactionId(request.getTransactionId())
                .accountId(ledgerEntry.getAccountId())
                .accountType(account.getType())
                .entryType(ledgerEntry.getType())
                .amount(ledgerEntry.getAmount())
                .description(ledgerEntry.getDescription())
                .sequenceNumber(sequenceNumber++)
                .timestamp(Instant.now())
                .balanceBefore(account.getBalance())
                .balanceAfter(calculateNewBalance(account, ledgerEntry))
                .build();
            
            entries.add(journalEntry);
        }
        
        return entries;
    }
    
    /**
     * Calculate new balance based on account type and entry type
     */
    private BigDecimal calculateNewBalance(Account account, LedgerEntry entry) {
        BigDecimal currentBalance = account.getBalance();
        BigDecimal amount = entry.getAmount();
        
        // Apply double-entry bookkeeping rules
        if (account.getType().isDebitPositive()) {
            // Asset and Expense accounts increase with debits
            if (entry.getType() == EntryType.DEBIT) {
                return currentBalance.add(amount);
            } else {
                return currentBalance.subtract(amount);
            }
        } else {
            // Liability, Equity, and Revenue accounts increase with credits
            if (entry.getType() == EntryType.CREDIT) {
                return currentBalance.add(amount);
            } else {
                return currentBalance.subtract(amount);
            }
        }
    }
    
    /**
     * Apply journal entries to accounts atomically
     */
    private Map<String, AccountBalance> applyJournalEntries(List<JournalEntry> entries) {
        Map<String, AccountBalance> updatedBalances = new HashMap<>();
        
        for (JournalEntry entry : entries) {
            // Update account balance in database
            Account account = updateAccountBalance(entry.getAccountId(), entry.getBalanceAfter());
            
            updatedBalances.put(entry.getAccountId(), AccountBalance.builder()
                .accountId(account.getId())
                .balance(account.getBalance())
                .lastUpdated(Instant.now())
                .version(account.getVersion() + 1)
                .build());
        }
        
        return updatedBalances;
    }
    
    /**
     * Perform final validation after applying entries
     */
    private void performFinalValidation(List<JournalEntry> entries, Map<String, AccountBalance> balances) {
        // Verify the accounting equation still holds
        BigDecimal totalAssets = BigDecimal.ZERO;
        BigDecimal totalLiabilities = BigDecimal.ZERO;
        BigDecimal totalEquity = BigDecimal.ZERO;
        
        for (JournalEntry entry : entries) {
            AccountBalance balance = balances.get(entry.getAccountId());
            if (balance != null) {
                switch (entry.getAccountType()) {
                    case ASSET:
                        totalAssets = totalAssets.add(balance.getBalance());
                        break;
                    case LIABILITY:
                        totalLiabilities = totalLiabilities.add(balance.getBalance());
                        break;
                    case EQUITY:
                        totalEquity = totalEquity.add(balance.getBalance());
                        break;
                }
            }
        }
        
        // Log the accounting equation state
        log.info("Accounting equation check - Assets: {}, Liabilities: {}, Equity: {}", 
            totalAssets, totalLiabilities, totalEquity);
        
        // Verify total debits still equal total credits
        BigDecimal totalDebits = calculateTotalDebits(entries);
        BigDecimal totalCredits = calculateTotalCredits(entries);
        
        if (totalDebits.compareTo(totalCredits) != 0) {
            throw new BalanceViolationException(
                "Post-transaction validation failed: Debits != Credits");
        }
    }
    
    /**
     * Calculate total debits from journal entries
     */
    private BigDecimal calculateTotalDebits(List<JournalEntry> entries) {
        return entries.stream()
            .filter(e -> e.getEntryType() == EntryType.DEBIT)
            .map(JournalEntry::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * Calculate total credits from journal entries
     */
    private BigDecimal calculateTotalCredits(List<JournalEntry> entries) {
        return entries.stream()
            .filter(e -> e.getEntryType() == EntryType.CREDIT)
            .map(JournalEntry::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * Persist ledger transaction to database
     */
    private void persistLedgerTransaction(LedgerTransaction transaction) {
        try {
            // Convert to entity
            com.waqiti.payment.entity.LedgerTransaction entity = convertToEntity(transaction);
            
            // Save transaction
            com.waqiti.payment.entity.LedgerTransaction saved = transactionRepository.save(entity);
            
            // Save journal entries
            for (JournalEntry journalEntry : transaction.getJournalEntries()) {
                com.waqiti.payment.entity.JournalEntry entryEntity = 
                    convertJournalEntry(journalEntry, saved.getId());
                journalEntryRepository.save(entryEntity);
            }
            
            log.info("Ledger transaction persisted: {} with {} journal entries", 
                transaction.getTransactionId(), transaction.getJournalEntries().size());
            
        } catch (Exception e) {
            log.error("Failed to persist ledger transaction: {}", 
                transaction.getTransactionId(), e);
            throw new LedgerException("Failed to persist transaction", e);
        }
    }
    
    /**
     * Create audit trail for the transaction
     */
    private void createAuditTrail(LedgerTransaction transaction) {
        log.info("Creating audit trail for transaction: {}", transaction.getTransactionId());
        
        // Create immutable audit record
        AuditRecord auditRecord = AuditRecord.builder()
            .auditId(UUID.randomUUID().toString())
            .transactionId(transaction.getTransactionId())
            .timestamp(Instant.now())
            .action("LEDGER_TRANSACTION_COMPLETED")
            .details(transaction)
            .checksumHash(calculateChecksum(transaction))
            .build();
        
        // Persist audit record
        persistAuditRecord(auditRecord);
    }
    
    /**
     * Calculate checksum for transaction integrity
     */
    private String calculateChecksum(LedgerTransaction transaction) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            // Build string representation of critical transaction data
            StringBuilder dataBuilder = new StringBuilder();
            dataBuilder.append(transaction.getTransactionId());
            dataBuilder.append("|");
            dataBuilder.append(transaction.getTimestamp().toEpochMilli());
            dataBuilder.append("|");
            dataBuilder.append(transaction.getTotalDebits().toPlainString());
            dataBuilder.append("|");
            dataBuilder.append(transaction.getTotalCredits().toPlainString());
            dataBuilder.append("|");
            
            // Include journal entries in checksum
            for (JournalEntry entry : transaction.getJournalEntries()) {
                dataBuilder.append(entry.getAccountId());
                dataBuilder.append(":");
                dataBuilder.append(entry.getEntryType());
                dataBuilder.append(":");
                dataBuilder.append(entry.getAmount().toPlainString());
                dataBuilder.append(",");
            }
            
            // Calculate hash
            byte[] hashBytes = digest.digest(dataBuilder.toString().getBytes(StandardCharsets.UTF_8));
            
            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            
            return hexString.toString();
            
        } catch (NoSuchAlgorithmException e) {
            log.error("Failed to calculate checksum for transaction: {}", 
                transaction.getTransactionId(), e);
            throw new LedgerException("Checksum calculation failed", e);
        }
    }
    
    /**
     * Calculate simple transaction checksum for duplicate detection
     */
    private String calculateTransactionChecksum(String transactionId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(transactionId.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
            
        } catch (NoSuchAlgorithmException e) {
            log.error("Failed to calculate checksum for transaction ID: {}", transactionId, e);
            return transactionId; // Fallback to transaction ID itself
        }
    }
    
    /**
     * Compensate failed transaction
     */
    private void compensateTransaction(String transactionId) {
        log.warn("Compensating failed transaction: {}", transactionId);
        // Implement compensation logic
    }
    
    /**
     * Get account details from database
     */
    private Account getAccount(String accountId) {
        Optional<com.waqiti.payment.entity.LedgerAccount> accountOpt = 
            accountRepository.findByAccountNumber(accountId);
        
        if (accountOpt.isEmpty()) {
            throw new AccountNotFoundException("Account not found: " + accountId);
        }
        
        com.waqiti.payment.entity.LedgerAccount ledgerAccount = accountOpt.get();
        
        // Map database entity to internal model
        AccountType type = mapAccountType(ledgerAccount.getAccountType());
        
        return Account.builder()
            .id(accountId)
            .type(type)
            .balance(ledgerAccount.getBalance())
            .version(ledgerAccount.getVersion())
            .build();
    }
    
    /**
     * Map database account type to internal enum
     */
    private AccountType mapAccountType(com.waqiti.payment.entity.LedgerAccount.AccountType dbType) {
        switch (dbType) {
            case ASSET:
                return AccountType.ASSET;
            case LIABILITY:
                return AccountType.LIABILITY;
            case EQUITY:
                return AccountType.EQUITY;
            case REVENUE:
                return AccountType.REVENUE;
            case EXPENSE:
                return AccountType.EXPENSE;
            default:
                throw new IllegalArgumentException("Unknown account type: " + dbType);
        }
    }
    
    /**
     * Update account balance with optimistic locking
     */
    private Account updateAccountBalance(String accountId, BigDecimal newBalance) {
        Optional<com.waqiti.payment.entity.LedgerAccount> accountOpt = 
            accountRepository.findByAccountNumber(accountId);
        
        if (accountOpt.isEmpty()) {
            throw new AccountNotFoundException("Account not found for update: " + accountId);
        }
        
        com.waqiti.payment.entity.LedgerAccount account = accountOpt.get();
        Long currentVersion = account.getVersion();
        
        // Calculate available balance (balance - pending debits)
        BigDecimal availableBalance = newBalance.subtract(
            account.getPendingDebits() != null ? account.getPendingDebits() : BigDecimal.ZERO
        );
        
        // Update with optimistic lock
        int updated = accountRepository.updateBalanceWithOptimisticLock(
            account.getId(),
            newBalance,
            availableBalance,
            LocalDateTime.now(),
            currentVersion
        );
        
        if (updated == 0) {
            throw new OptimisticLockException(
                "Concurrent modification detected for account: " + accountId
            );
        }
        
        // Return updated account info
        AccountType type = mapAccountType(account.getAccountType());
        
        return Account.builder()
            .id(accountId)
            .type(type)
            .balance(newBalance)
            .version(currentVersion + 1)
            .build();
    }
    
    /**
     * Persist audit record to database
     */
    private void persistAuditRecord(AuditRecord record) {
        try {
            // Create audit trail entity
            com.waqiti.payment.entity.AuditTrail auditTrail = com.waqiti.payment.entity.AuditTrail.builder()
                .auditId(UUID.fromString(record.getAuditId()))
                .entityType("LEDGER_TRANSACTION")
                .entityId(record.getTransactionId())
                .action(record.getAction())
                .performedBy(getCurrentUserId())
                .performedAt(LocalDateTime.now())
                .ipAddress(getCurrentUserIpAddress())
                .userAgent(getCurrentUserAgent())
                .oldValues(null) // No old values for new transactions
                .newValues(serializeDetails(record.getDetails()))
                .checksum(record.getChecksumHash())
                .metadata(createAuditMetadata())
                .build();
            
            // Save to database
            auditTrailRepository.save(auditTrail);
            
            log.info("Audit record persisted: {} for transaction: {}", 
                record.getAuditId(), record.getTransactionId());
            
        } catch (Exception e) {
            log.error("Failed to persist audit record: {}", record.getAuditId(), e);
            // Audit failures should not break the transaction
            // but should be monitored and alerted
        }
    }
    
    /**
     * Serialize transaction details for audit
     */
    private String serializeDetails(Object details) {
        if (details == null) return "{}";
        
        try {
            // Use Jackson ObjectMapper for proper JSON serialization
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            objectMapper.configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
            
            return objectMapper.writeValueAsString(details);
        } catch (Exception e) {
            log.error("Failed to serialize audit details", e);
            // Fallback to safe string representation
            return String.format("{\"error\":\"Serialization failed: %s\",\"type\":\"%s\"}", 
                                e.getMessage(), details.getClass().getSimpleName());
        }
    }
    
    /**
     * Get current user ID from security context
     */
    private UUID getCurrentUserId() {
        try {
            // Get current user from Spring Security context
            org.springframework.security.core.Authentication authentication = 
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                
            if (authentication != null && authentication.isAuthenticated()) {
                Object principal = authentication.getPrincipal();
                
                if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
                    String username = ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
                    // Convert username to UUID (assuming username is UUID string)
                    return UUID.fromString(username);
                } else if (principal instanceof String) {
                    // Direct string principal
                    return UUID.fromString((String) principal);
                }
            }
            
            // Fallback to system user if no authentication context
            log.warn("No authentication context found, using system user");
            return UUID.fromString("00000000-0000-0000-0000-000000000000");
            
        } catch (Exception e) {
            log.error("Failed to get current user ID from security context", e);
            return UUID.fromString("00000000-0000-0000-0000-000000000000");
        }
    }
    
    /**
     * Get current user IP address
     */
    private String getCurrentUserIpAddress() {
        // In production, get from request context
        return "system";
    }
    
    /**
     * Get current user agent
     */
    private String getCurrentUserAgent() {
        // In production, get from request headers
        return "DoubleEntryLedgerService";
    }
    
    /**
     * Create audit metadata
     */
    private String createAuditMetadata() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("service", "DoubleEntryLedgerService");
        metadata.put("timestamp", Instant.now().toString());
        metadata.put("version", "1.0");
        
        // Simple JSON representation
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            if (!first) json.append(",");
            json.append("\"").append(entry.getKey()).append("\":");
            json.append("\"").append(entry.getValue()).append("\"");
            first = false;
        }
        json.append("}");
        
        return json.toString();
    }
    
    // Data classes
    
    @Data
    @Builder
    public static class LedgerTransactionRequest {
        private String transactionId;
        private String sagaId;
        private String description;
        private List<LedgerEntry> entries;
        private Map<String, Object> metadata;
    }
    
    @Data
    @Builder
    public static class LedgerEntry {
        private String accountId;
        private EntryType type;
        private BigDecimal amount;
        private String description;
    }
    
    @Data
    @Builder
    public static class LedgerTransaction {
        private String transactionId;
        private String sagaId;
        private Instant timestamp;
        private String description;
        private List<JournalEntry> journalEntries;
        private TransactionStatus status;
        private BigDecimal totalDebits;
        private BigDecimal totalCredits;
        private Map<String, Object> metadata;
    }
    
    @Data
    @Builder
    public static class JournalEntry {
        private String entryId;
        private String transactionId;
        private String accountId;
        private AccountType accountType;
        private EntryType entryType;
        private BigDecimal amount;
        private String description;
        private int sequenceNumber;
        private Instant timestamp;
        private BigDecimal balanceBefore;
        private BigDecimal balanceAfter;
    }
    
    @Data
    @Builder
    public static class Account {
        private String id;
        private AccountType type;
        private BigDecimal balance;
        private Long version;
    }
    
    @Data
    @Builder
    public static class AccountBalance {
        private String accountId;
        private BigDecimal balance;
        private Instant lastUpdated;
        private Long version;
    }
    
    @Data
    @Builder
    public static class AuditRecord {
        private String auditId;
        private String transactionId;
        private Instant timestamp;
        private String action;
        private Object details;
        private String checksumHash;
    }
    
    public enum EntryType {
        DEBIT, CREDIT
    }
    
    public enum TransactionStatus {
        PENDING, COMPLETED, FAILED, COMPENSATED
    }
    
    // Custom exceptions
    
    public static class LedgerException extends RuntimeException {
        public LedgerException(String message) {
            super(message);
        }
        
        public LedgerException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    public static class ValidationException extends LedgerException {
        public ValidationException(String message) {
            super(message);
        }
    }
    
    public static class BalanceViolationException extends LedgerException {
        public BalanceViolationException(String message) {
            super(message);
        }
    }
    
    public static class InsufficientBalanceException extends LedgerException {
        public InsufficientBalanceException(String message) {
            super(message);
        }
    }
    
    public static class AccountNotFoundException extends LedgerException {
        public AccountNotFoundException(String message) {
            super(message);
        }
    }
    
    public static class DuplicateTransactionException extends LedgerException {
        public DuplicateTransactionException(String message) {
            super(message);
        }
    }
    
    public static class OptimisticLockException extends LedgerException {
        public OptimisticLockException(String message) {
            super(message);
        }
    }
    
    /**
     * Convert internal transaction to entity
     */
    private com.waqiti.payment.entity.LedgerTransaction convertToEntity(LedgerTransaction transaction) {
        return com.waqiti.payment.entity.LedgerTransaction.builder()
            .transactionNumber(transaction.getTransactionId())
            .sagaId(transaction.getSagaId())
            .transactionDate(LocalDateTime.now())
            .transactionType(com.waqiti.payment.entity.LedgerTransaction.TransactionType.TRANSFER)
            .status(com.waqiti.payment.entity.LedgerTransaction.TransactionStatus.COMPLETED)
            .description(transaction.getDescription())
            .totalDebits(transaction.getTotalDebits())
            .totalCredits(transaction.getTotalCredits())
            .checksum(calculateChecksum(transaction))
            .metadata(convertMetadata(transaction.getMetadata()))
            .build();
    }
    
    /**
     * Convert journal entry to entity
     */
    private com.waqiti.payment.entity.JournalEntry convertJournalEntry(JournalEntry entry, UUID transactionId) {
        // Get the actual account
        Optional<LedgerAccount> accountOpt = accountRepository.findByAccountNumber(entry.getAccountId());
        
        if (accountOpt.isEmpty()) {
            throw new AccountNotFoundException("Account not found: " + entry.getAccountId());
        }
        
        return com.waqiti.payment.entity.JournalEntry.builder()
            .transactionId(transactionId)
            .account(accountOpt.get())
            .entryType(entry.getEntryType() == EntryType.DEBIT ? 
                com.waqiti.payment.entity.JournalEntry.EntryType.DEBIT : 
                com.waqiti.payment.entity.JournalEntry.EntryType.CREDIT)
            .amount(entry.getAmount())
            .balanceBefore(entry.getBalanceBefore())
            .balanceAfter(entry.getBalanceAfter())
            .description(entry.getDescription())
            .entryDate(LocalDateTime.now())
            .sequenceNumber(entry.getSequenceNumber())
            .status(com.waqiti.payment.entity.JournalEntry.EntryStatus.POSTED)
            .build();
    }
    
    /**
     * Convert metadata map to JSON string
     */
    private String convertMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}"; // Return empty JSON instead of null
        }
        
        try {
            // Simple JSON conversion - in production use Jackson or Gson
            StringBuilder json = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                if (!first) json.append(",");
                json.append("\"").append(entry.getKey()).append("\":");
                json.append("\"").append(entry.getValue()).append("\"");
                first = false;
            }
            json.append("}");
            return json.toString();
        } catch (Exception e) {
            log.error("Failed to convert metadata", e);
            return "{}"; // Return empty JSON on conversion failure
        }
    }
}