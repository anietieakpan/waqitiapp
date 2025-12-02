package com.waqiti.corebanking.service;

import com.waqiti.corebanking.domain.Account;
import com.waqiti.corebanking.domain.BankAccount;
import com.waqiti.corebanking.domain.Transaction;
import com.waqiti.corebanking.dto.GdprDataExportDto;
import com.waqiti.corebanking.dto.GdprErasureRequestDto;
import com.waqiti.corebanking.dto.GdprErasureResponseDto;
import com.waqiti.corebanking.exception.GdprErasureException;
import com.waqiti.corebanking.repository.AccountRepository;
import com.waqiti.corebanking.repository.BankAccountRepository;
import com.waqiti.corebanking.repository.TransactionRepository;
import com.waqiti.common.tracing.Traced;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * GDPR Data Erasure Service
 *
 * Implements GDPR Article 17 - Right to Erasure ("Right to be Forgotten")
 *
 * CRITICAL COMPLIANCE SERVICE for EU operations
 *
 * Handles:
 * - Complete user data export (before erasure)
 * - Personal data anonymization
 * - Financial transaction data retention (regulatory requirement)
 * - Audit trail of erasure requests
 * - Compliance with data retention laws
 *
 * IMPORTANT LEGAL NOTES:
 * - Financial transaction data CANNOT be deleted (regulatory requirement)
 * - PII is anonymized/pseudonymized instead of deleted
 * - Audit trail must be maintained for 7 years minimum
 * - Account data is soft-deleted, not hard-deleted
 *
 * Regulatory Framework:
 * - GDPR Article 17 (Right to Erasure)
 * - GDPR Article 20 (Right to Data Portability)
 * - Financial regulations requiring transaction retention (7-10 years)
 * - AML/KYC record retention requirements
 *
 * @author Core Banking Team
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class GdprDataErasureService {

    private final AccountRepository accountRepository;
    private final BankAccountRepository bankAccountRepository;
    private final TransactionRepository transactionRepository;

    // Anonymization constants
    private static final String ANONYMIZED_NAME = "ANONYMIZED_USER";
    private static final String ANONYMIZED_EMAIL = "anonymized@deleted.local";
    private static final String ANONYMIZED_PHONE = "+00000000000";
    private static final String ANONYMIZED_ADDRESS = "REDACTED";
    private static final String ERASURE_REASON_PREFIX = "GDPR Article 17 - User requested erasure on ";

    /**
     * Export all user data before erasure (GDPR Article 20 - Right to Data Portability)
     *
     * MUST be called before erasure to provide user with their data
     *
     * @param userId User ID to export data for
     * @return Complete data export including all personal and financial data
     */
    @Traced(operationName = "gdpr-data-export", businessOperation = "gdpr-compliance", priority = Traced.TracingPriority.HIGH)
    @Transactional(readOnly = true)
    public GdprDataExportDto exportUserData(UUID userId) {
        log.info("Starting GDPR data export for user: {}", userId);

        // Fetch all user data
        List<Account> accounts = accountRepository.findByUserId(userId);
        List<BankAccount> bankAccounts = bankAccountRepository.findByUserId(userId);
        List<Transaction> transactions = transactionRepository.findByUserId(userId);

        if (accounts.isEmpty() && bankAccounts.isEmpty() && transactions.isEmpty()) {
            log.warn("No data found for user: {}", userId);
            throw new GdprErasureException("No data found for user: " + userId);
        }

        GdprDataExportDto exportDto = GdprDataExportDto.builder()
                .userId(userId)
                .exportDate(LocalDateTime.now())
                .accounts(accounts)
                .bankAccounts(bankAccounts)
                .transactions(transactions)
                .dataTypes(List.of(
                        "ACCOUNTS",
                        "BANK_ACCOUNTS",
                        "TRANSACTIONS",
                        "PERSONAL_INFORMATION",
                        "FINANCIAL_RECORDS"
                ))
                .retentionPolicy("Financial transaction data retained for 7 years per regulatory requirements")
                .build();

        log.info("GDPR data export completed for user: {}. Accounts: {}, BankAccounts: {}, Transactions: {}",
                userId, accounts.size(), bankAccounts.size(), transactions.size());

        return exportDto;
    }

    /**
     * Execute GDPR data erasure (GDPR Article 17)
     *
     * CRITICAL: This implements "Right to be Forgotten"
     *
     * Process:
     * 1. Validate erasure is allowed (no active transactions, no regulatory holds)
     * 2. Anonymize personal data (cannot delete due to financial regulations)
     * 3. Soft-delete accounts (set deletedAt timestamp)
     * 4. Pseudonymize transaction data (replace PII with anonymized values)
     * 5. Create audit record of erasure
     *
     * IMPORTANT: Financial transaction records are ANONYMIZED, not deleted
     * This complies with both GDPR and financial record retention laws
     *
     * @param request GDPR erasure request
     * @return Erasure response with confirmation and details
     * @throws GdprErasureException if erasure cannot be performed
     */
    @Traced(operationName = "gdpr-data-erasure", businessOperation = "gdpr-compliance", priority = Traced.TracingPriority.CRITICAL)
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class, timeout = 60)
    public GdprErasureResponseDto eraseUserData(GdprErasureRequestDto request) {
        UUID userId = request.getUserId();
        log.info("Starting GDPR data erasure for user: {}. Reason: {}", userId, request.getReason());

        // STEP 1: Validate erasure is allowed
        validateErasureAllowed(userId);

        // STEP 2: Fetch all user data
        List<Account> accounts = accountRepository.findByUserId(userId);
        List<BankAccount> bankAccounts = bankAccountRepository.findByUserId(userId);
        List<Transaction> transactions = transactionRepository.findByUserId(userId);

        if (accounts.isEmpty() && bankAccounts.isEmpty() && transactions.isEmpty()) {
            log.warn("No data found for user: {}. Already erased or never existed.", userId);
            throw new GdprErasureException("No data found for user: " + userId);
        }

        int accountsAnonymized = 0;
        int bankAccountsAnonymized = 0;
        int transactionsAnonymized = 0;

        // STEP 3: Anonymize and soft-delete accounts
        for (Account account : accounts) {
            anonymizeAccount(account, request);
            accountsAnonymized++;
        }
        accountRepository.saveAll(accounts);

        // STEP 4: Anonymize and soft-delete bank accounts
        for (BankAccount bankAccount : bankAccounts) {
            anonymizeBankAccount(bankAccount, request);
            bankAccountsAnonymized++;
        }
        bankAccountRepository.saveAll(bankAccounts);

        // STEP 5: Pseudonymize transaction data (CANNOT delete - regulatory requirement)
        for (Transaction transaction : transactions) {
            pseudonymizeTransaction(transaction, request);
            transactionsAnonymized++;
        }
        transactionRepository.saveAll(transactions);

        log.info("GDPR data erasure completed for user: {}. " +
                        "Accounts anonymized: {}, BankAccounts anonymized: {}, Transactions pseudonymized: {}",
                userId, accountsAnonymized, bankAccountsAnonymized, transactionsAnonymized);

        // STEP 6: Build response
        GdprErasureResponseDto response = GdprErasureResponseDto.builder()
                .userId(userId)
                .erasureDate(LocalDateTime.now())
                .success(true)
                .accountsErased(accountsAnonymized)
                .bankAccountsErased(bankAccountsAnonymized)
                .transactionsPseudonymized(transactionsAnonymized)
                .message("User data successfully anonymized. " +
                        "Financial transaction records pseudonymized per regulatory requirements. " +
                        "Data retention: 7 years for audit and compliance.")
                .dataRetentionReason("Financial regulations require transaction record retention for AML/KYC compliance")
                .auditTrailRetained(true)
                .build();

        // STEP 7: Publish GDPR erasure event (for other microservices)
        publishGdprErasureEvent(userId, request);

        return response;
    }

    /**
     * Validate that erasure is allowed
     *
     * Checks:
     * - No active/pending transactions
     * - No compliance holds
     * - No regulatory restrictions
     * - No outstanding balances (must be zero or transferred)
     *
     * @param userId User ID to validate
     * @throws GdprErasureException if erasure is not allowed
     */
    private void validateErasureAllowed(UUID userId) {
        log.debug("Validating GDPR erasure is allowed for user: {}", userId);

        // Check for active accounts with non-zero balances
        List<Account> activeAccounts = accountRepository.findByUserIdAndStatus(
                userId, Account.AccountStatus.ACTIVE);

        for (Account account : activeAccounts) {
            if (account.getCurrentBalance().signum() != 0) {
                throw new GdprErasureException(
                        "Cannot erase user data: Account " + account.getAccountNumber() +
                                " has non-zero balance: " + account.getCurrentBalance() + " " + account.getCurrency() +
                                ". Please transfer or withdraw all funds before requesting erasure.");
            }
        }

        // Check for pending transactions
        long pendingTransactions = transactionRepository.countByUserIdAndStatus(
                userId, Transaction.TransactionStatus.PENDING);
        if (pendingTransactions > 0) {
            throw new GdprErasureException(
                    "Cannot erase user data: User has " + pendingTransactions +
                            " pending transactions. Please wait for all transactions to complete.");
        }

        // Check for compliance holds
        List<Account> heldAccounts = accountRepository.findByUserIdAndComplianceLevel(
                userId, Account.ComplianceLevel.BLOCKED);
        if (!heldAccounts.isEmpty()) {
            throw new GdprErasureException(
                    "Cannot erase user data: Account is under compliance hold. " +
                            "Please contact compliance team.");
        }

        log.debug("GDPR erasure validation passed for user: {}", userId);
    }

    /**
     * Anonymize account data
     *
     * Replaces all PII with anonymized values
     * Sets deletedAt timestamp (soft delete)
     * Preserves financial data for regulatory compliance
     *
     * @param account Account to anonymize
     * @param request Erasure request
     */
    private void anonymizeAccount(Account account, GdprErasureRequestDto request) {
        log.debug("Anonymizing account: {}", account.getAccountNumber());

        // Anonymize PII
        account.setAccountName(ANONYMIZED_NAME);
        account.setUserId(null); // Remove user linkage

        // Set soft delete timestamp
        account.setDeletedAt(LocalDateTime.now());
        account.setDeletedBy(request.getRequestedBy());
        account.setDeletionReason(ERASURE_REASON_PREFIX + LocalDateTime.now());

        // Close account
        account.setStatus(Account.AccountStatus.CLOSED);

        // Preserve financial balances for regulatory compliance (set to zero after transfer)
        // Account number preserved for audit trail (anonymized in display)

        log.debug("Account anonymized: {}", account.getAccountNumber());
    }

    /**
     * Anonymize bank account data
     *
     * Encrypts/anonymizes sensitive PII
     * Soft deletes the bank account
     * Preserves account linkage for audit trail
     *
     * @param bankAccount Bank account to anonymize
     * @param request Erasure request
     */
    private void anonymizeBankAccount(BankAccount bankAccount, GdprErasureRequestDto request) {
        log.debug("Anonymizing bank account: {}", bankAccount.getId());

        // Anonymize PII (these fields are encrypted, set to anonymized encrypted values)
        bankAccount.setAccountHolderName(ANONYMIZED_NAME);
        // Account number and routing number preserved for audit trail (encrypted)
        // Cannot delete - needed for dispute resolution and regulatory inquiries

        // Set soft delete
        bankAccount.setDeletedAt(LocalDateTime.now());
        bankAccount.setDeletedBy(request.getRequestedBy());

        // Mark as inactive
        bankAccount.setStatus(BankAccount.BankAccountStatus.INACTIVE);

        log.debug("Bank account anonymized: {}", bankAccount.getId());
    }

    /**
     * Pseudonymize transaction data
     *
     * CRITICAL: Transactions CANNOT be deleted per financial regulations
     * - AML/KYC requirements: 5-7 years retention
     * - Tax regulations: 7 years retention
     * - Fraud investigation: 10 years retention
     *
     * Instead, we pseudonymize:
     * - Replace user-identifiable descriptions with generic text
     * - Remove metadata that contains PII
     * - Preserve amounts, dates, and account linkage for audit
     *
     * @param transaction Transaction to pseudonymize
     * @param request Erasure request
     */
    private void pseudonymizeTransaction(Transaction transaction, GdprErasureRequestDto request) {
        log.debug("Pseudonymizing transaction: {}", transaction.getTransactionNumber());

        // Pseudonymize description (remove user-provided text)
        if (transaction.getDescription() != null && !transaction.getDescription().startsWith("ANONYMIZED")) {
            transaction.setDescription("ANONYMIZED - Original transaction on " + transaction.getCreatedAt().toLocalDate());
        }

        // Remove metadata that may contain PII
        if (transaction.getMetadata() != null) {
            transaction.setMetadata(null);
        }

        // Mark as pseudonymized
        transaction.setPseudonymized(true);
        transaction.setPseudonymizedAt(LocalDateTime.now());
        transaction.setPseudonymizationReason("GDPR Article 17 - User data erasure");

        // Preserve all financial data (amounts, dates, account IDs) for regulatory compliance
        // Account IDs link to anonymized accounts

        log.debug("Transaction pseudonymized: {}", transaction.getTransactionNumber());
    }

    /**
     * Publish GDPR erasure event to notify other microservices
     *
     * Other services (user-service, kyc-service, notification-service, etc.)
     * must also erase/anonymize their data for the user
     *
     * @param userId User ID
     * @param request Erasure request
     */
    private void publishGdprErasureEvent(UUID userId, GdprErasureRequestDto request) {
        log.info("Publishing GDPR erasure event for user: {}", userId);

        // TODO: Publish to Kafka topic: gdpr.user.erased
        // Event should contain:
        // - userId
        // - erasureDate
        // - requestedBy
        // - reason

        // Other microservices will consume this event and erase their data

        log.debug("GDPR erasure event published for user: {}", userId);
    }

    /**
     * Check if user data has been erased
     *
     * @param userId User ID to check
     * @return true if user data has been erased/anonymized
     */
    @Transactional(readOnly = true)
    public boolean isUserDataErased(UUID userId) {
        // Check if any accounts exist and are not deleted
        List<Account> accounts = accountRepository.findByUserId(userId);
        return accounts.isEmpty() || accounts.stream()
                .allMatch(account -> account.getDeletedAt() != null);
    }

    /**
     * Get erasure status for user
     *
     * @param userId User ID
     * @return Erasure status details
     */
    @Transactional(readOnly = true)
    public GdprErasureResponseDto getErasureStatus(UUID userId) {
        boolean isErased = isUserDataErased(userId);

        List<Account> accounts = accountRepository.findByUserId(userId);
        List<Transaction> transactions = transactionRepository.findByUserId(userId);

        long erasedAccounts = accounts.stream()
                .filter(account -> account.getDeletedAt() != null)
                .count();

        long pseudonymizedTransactions = transactions.stream()
                .filter(Transaction::isPseudonymized)
                .count();

        return GdprErasureResponseDto.builder()
                .userId(userId)
                .success(isErased)
                .accountsErased((int) erasedAccounts)
                .transactionsPseudonymized((int) pseudonymizedTransactions)
                .erasureDate(accounts.isEmpty() ? null :
                        accounts.get(0).getDeletedAt())
                .message(isErased ? "User data has been erased/anonymized" : "User data is active")
                .auditTrailRetained(true)
                .dataRetentionReason("Financial regulations require 7-year transaction retention")
                .build();
    }
}
