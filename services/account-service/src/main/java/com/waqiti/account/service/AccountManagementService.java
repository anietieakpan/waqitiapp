package com.waqiti.account.service;

import com.waqiti.account.domain.Account;
import com.waqiti.account.dto.*;
import com.waqiti.account.repository.AccountRepository;
import com.waqiti.account.client.LedgerServiceClient;
import com.waqiti.account.client.ComplianceServiceClient;
import com.waqiti.account.exception.AccountNotFoundException;
import com.waqiti.account.exception.InsufficientFundsException;
import com.waqiti.account.exception.AccountOperationNotAllowedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Account Management Service
 * 
 * Comprehensive account lifecycle management service providing:
 * - Account creation, activation, and closure
 * - Balance management and fund reservations
 * - Account status updates and compliance controls
 * - Account hierarchy and relationship management
 * - Real-time balance calculations and validations
 * - Integration with ledger and compliance services
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountManagementService {

    private final AccountRepository accountRepository;
    private final LedgerServiceClient ledgerServiceClient;
    private final ComplianceServiceClient complianceServiceClient;
    private final AccountNumberGeneratorService accountNumberGenerator;
    private final AccountValidationService accountValidationService;
    private final Clock clock;

    /**
     * Creates a new account with comprehensive validation and setup
     */
    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        log.info("Creating account for user: {} type: {}", request.getUserId(), request.getAccountType());
        
        // Validate request
        accountValidationService.validateCreateAccountRequest(request);
        
        // Check compliance requirements
        ComplianceCheckResult complianceResult = complianceServiceClient.checkAccountCreationCompliance(
            request.getUserId(), request.getAccountType());
        
        if (!complianceResult.isApproved()) {
            throw new AccountOperationNotAllowedException("Account creation failed compliance check: " + 
                complianceResult.getReason());
        }
        
        // Generate account number
        String accountNumber = accountNumberGenerator.generateAccountNumber(request.getAccountType());
        
        // Create account entity
        Account account = Account.builder()
            .accountNumber(accountNumber)
            .userId(request.getUserId())
            .accountName(request.getAccountName())
            .accountType(Account.AccountType.valueOf(request.getAccountType()))
            .accountCategory(determineAccountCategory(request.getAccountType()))
            .status(Account.AccountStatus.PENDING)
            .currency(request.getCurrency())
            .currentBalance(BigDecimal.ZERO)
            .availableBalance(BigDecimal.ZERO)
            .pendingBalance(BigDecimal.ZERO)
            .reservedBalance(BigDecimal.ZERO)
            .dailyTransactionLimit(request.getDailyLimit())
            .monthlyTransactionLimit(request.getMonthlyLimit())
            .minimumBalance(request.getMinimumBalance())
            .maximumBalance(request.getMaximumBalance())
            .complianceLevel(Account.ComplianceLevel.STANDARD)
            .openedDate(LocalDateTime.now(clock))
            .metadata(request.getMetadata())
            .build();
        
        account = accountRepository.save(account);
        
        // Auto-activate if no manual approval required
        if (!requiresManualApproval(request)) {
            account.setStatus(Account.AccountStatus.ACTIVE);
            account = accountRepository.save(account);
        }
        
        log.info("Created account: {} for user: {}", account.getAccountNumber(), request.getUserId());
        
        return mapToAccountResponse(account);
    }

    /**
     * Retrieves comprehensive account details
     */
    @Cacheable(value = "accountDetails", key = "#accountId")
    public AccountDetailsResponse getAccountDetails(UUID accountId) {
        Account account = getAccountById(accountId);
        
        // Get real-time balance from ledger service
        BalanceInquiryResponse ledgerBalance = ledgerServiceClient.getAccountBalance(accountId);
        
        // Update local balance cache if needed
        if (balanceNeedsUpdate(account, ledgerBalance)) {
            updateAccountBalance(account, ledgerBalance);
        }
        
        return AccountDetailsResponse.builder()
            .accountId(account.getAccountId())
            .accountNumber(account.getAccountNumber())
            .userId(account.getUserId())
            .accountName(account.getAccountName())
            .accountType(account.getAccountType().toString())
            .accountCategory(account.getAccountCategory().toString())
            .status(account.getStatus().toString())
            .currency(account.getCurrency())
            .currentBalance(ledgerBalance.getCurrentBalance())
            .availableBalance(ledgerBalance.getAvailableBalance())
            .pendingBalance(ledgerBalance.getPendingBalance())
            .reservedBalance(ledgerBalance.getReservedBalance())
            .dailyTransactionLimit(account.getDailyTransactionLimit())
            .monthlyTransactionLimit(account.getMonthlyTransactionLimit())
            .minimumBalance(account.getMinimumBalance())
            .maximumBalance(account.getMaximumBalance())
            .complianceLevel(account.getComplianceLevel().toString())
            .openedDate(account.getOpenedDate())
            .lastTransactionDate(account.getLastTransactionDate())
            .metadata(account.getMetadata())
            .build();
    }

    /**
     * Gets all accounts for a user
     */
    public List<AccountSummaryResponse> getUserAccounts(UUID userId) {
        List<Account> accounts = accountRepository.findByUserIdAndStatus(userId, Account.AccountStatus.ACTIVE);
        
        return accounts.stream()
            .map(this::mapToAccountSummaryResponse)
            .collect(Collectors.toList());
    }

    /**
     * Gets current account balance with real-time validation
     */
    @Cacheable(value = "accountBalance", key = "#accountId")
    public AccountBalanceResponse getAccountBalance(UUID accountId) {
        Account account = getAccountById(accountId);
        
        // Get authoritative balance from ledger service
        BalanceInquiryResponse ledgerBalance = ledgerServiceClient.getAccountBalance(accountId);
        
        return AccountBalanceResponse.builder()
            .accountId(accountId)
            .currentBalance(ledgerBalance.getCurrentBalance())
            .availableBalance(ledgerBalance.getAvailableBalance())
            .pendingBalance(ledgerBalance.getPendingBalance())
            .reservedBalance(ledgerBalance.getReservedBalance())
            .effectiveCreditLimit(account.getCreditLimit())
            .totalAvailableFunds(calculateTotalAvailableFunds(ledgerBalance, account.getCreditLimit()))
            .lastUpdated(ledgerBalance.getLastUpdated())
            .build();
    }

    /**
     * Updates account status with comprehensive validation
     */
    @Transactional
    @CacheEvict(value = {"accountDetails", "accountBalance"}, key = "#accountId")
    public void updateAccountStatus(UUID accountId, UpdateAccountStatusRequest request) {
        Account account = getAccountById(accountId);
        Account.AccountStatus newStatus = Account.AccountStatus.valueOf(request.getStatus());
        
        // Validate status transition
        if (!isValidStatusTransition(account.getStatus(), newStatus)) {
            throw new AccountOperationNotAllowedException(
                String.format("Invalid status transition from %s to %s", account.getStatus(), newStatus));
        }
        
        // Check compliance for status changes
        if (requiresComplianceCheck(newStatus)) {
            ComplianceCheckResult complianceResult = complianceServiceClient.checkStatusChangeCompliance(
                accountId, newStatus.toString());
            
            if (!complianceResult.isApproved()) {
                throw new AccountOperationNotAllowedException("Status change failed compliance check: " + 
                    complianceResult.getReason());
            }
        }
        
        account.setStatus(newStatus);
        
        if (newStatus == Account.AccountStatus.FROZEN && request.getReason() != null) {
            account.setFreezeReason(request.getReason());
        }
        
        if (newStatus == Account.AccountStatus.CLOSED && request.getReason() != null) {
            account.setClosureReason(request.getReason());
            account.setClosedDate(LocalDateTime.now());
        }
        
        accountRepository.save(account);
        
        log.info("Updated account status: {} from {} to {}", accountId, 
                account.getStatus(), newStatus);
    }

    /**
     * Updates account transaction limits
     */
    @Transactional
    @CacheEvict(value = "accountDetails", key = "#accountId")
    public void updateAccountLimits(UUID accountId, UpdateAccountLimitsRequest request) {
        Account account = getAccountById(accountId);
        
        // Validate limits
        accountValidationService.validateTransactionLimits(request);
        
        account.setDailyTransactionLimit(request.getDailyLimit());
        account.setMonthlyTransactionLimit(request.getMonthlyLimit());
        account.setMinimumBalance(request.getMinimumBalance());
        account.setMaximumBalance(request.getMaximumBalance());
        account.setCreditLimit(request.getCreditLimit());
        
        accountRepository.save(account);
        
        log.info("Updated account limits for account: {}", accountId);
    }

    /**
     * Freezes account for security or compliance reasons
     */
    @Transactional
    @CacheEvict(value = {"accountDetails", "accountBalance"}, key = "#accountId")
    public void freezeAccount(UUID accountId, FreezeAccountRequest request) {
        Account account = getAccountById(accountId);
        
        if (account.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new AccountOperationNotAllowedException("Only active accounts can be frozen");
        }
        
        account.setStatus(Account.AccountStatus.FROZEN);
        account.setFreezeReason(request.getReason());
        
        accountRepository.save(account);
        
        log.warn("Froze account: {} - Reason: {}", accountId, request.getReason());
    }

    /**
     * Unfreezes a previously frozen account
     */
    @Transactional
    @CacheEvict(value = {"accountDetails", "accountBalance"}, key = "#accountId")
    public void unfreezeAccount(UUID accountId) {
        Account account = getAccountById(accountId);
        
        if (account.getStatus() != Account.AccountStatus.FROZEN) {
            throw new AccountOperationNotAllowedException("Only frozen accounts can be unfrozen");
        }
        
        account.setStatus(Account.AccountStatus.ACTIVE);
        account.setFreezeReason(null);
        
        accountRepository.save(account);
        
        log.info("Unfroze account: {}", accountId);
    }

    /**
     * Closes account permanently
     */
    @Transactional
    @CacheEvict(value = {"accountDetails", "accountBalance"}, key = "#accountId")
    public void closeAccount(UUID accountId, CloseAccountRequest request) {
        Account account = getAccountById(accountId);
        
        // Verify account can be closed
        if (!canCloseAccount(account)) {
            throw new AccountOperationNotAllowedException("Account cannot be closed - outstanding balance or pending transactions");
        }
        
        account.setStatus(Account.AccountStatus.CLOSED);
        account.setClosureReason(request.getReason());
        account.setClosedDate(LocalDateTime.now());
        
        accountRepository.save(account);
        
        log.info("Closed account: {} - Reason: {}", accountId, request.getReason());
    }

    /**
     * Reserves funds for pending transactions
     */
    @Transactional
    @CacheEvict(value = "accountBalance", key = "#accountId")
    public ReserveFundsResponse reserveFunds(UUID accountId, ReserveFundsRequest request) {
        Account account = getAccountById(accountId);
        
        if (!account.isOperational()) {
            throw new AccountOperationNotAllowedException("Account is not operational");
        }
        
        // Check available balance
        BalanceInquiryResponse balance = ledgerServiceClient.getAccountBalance(accountId);
        
        if (!hasSufficientFunds(balance, request.getAmount())) {
            throw new InsufficientFundsException("Insufficient funds to reserve: " + request.getAmount());
        }
        
        // Reserve funds in ledger service
        ReserveFundsResult result = ledgerServiceClient.reserveFunds(accountId, request.getAmount(), 
            request.getReservationId(), request.getReason());
        
        if (!result.isSuccess()) {
            throw new AccountOperationNotAllowedException("Failed to reserve funds: " + result.getFailureReason());
        }
        
        log.info("Reserved funds: {} for account: {} reservation: {}", 
                request.getAmount(), accountId, request.getReservationId());
        
        return ReserveFundsResponse.builder()
            .reservationId(request.getReservationId())
            .accountId(accountId)
            .amount(request.getAmount())
            .success(true)
            .build();
    }

    /**
     * Releases previously reserved funds
     */
    @Transactional
    @CacheEvict(value = "accountBalance", key = "#accountId")
    public void releaseReservedFunds(UUID accountId, ReleaseReservedFundsRequest request) {
        Account account = getAccountById(accountId);
        
        // Release funds in ledger service
        ReleaseReservedFundsResult result = ledgerServiceClient.releaseReservedFunds(
            accountId, request.getReservationId(), request.getAmount());
        
        if (!result.isSuccess()) {
            throw new AccountOperationNotAllowedException("Failed to release reserved funds: " + 
                result.getFailureReason());
        }
        
        log.info("Released reserved funds: reservation {} for account: {}", 
                request.getReservationId(), accountId);
    }

    /**
     * Advanced account search with multiple criteria
     */
    public Page<AccountSummaryResponse> searchAccounts(AccountSearchCriteria criteria, Pageable pageable) {
        Page<Account> accounts = accountRepository.findAccountsByCriteria(
            criteria.getUserId(),
            criteria.getAccountType() != null ? Account.AccountType.valueOf(criteria.getAccountType()) : null,
            criteria.getStatus() != null ? Account.AccountStatus.valueOf(criteria.getStatus()) : null,
            criteria.getCurrency(),
            criteria.getMinBalance(),
            criteria.getMaxBalance(),
            pageable
        );
        
        return accounts.map(this::mapToAccountSummaryResponse);
    }

    /**
     * Gets comprehensive account statistics
     */
    public AccountStatisticsResponse getAccountStatistics() {
        return AccountStatisticsResponse.builder()
            .totalAccounts(accountRepository.count())
            .activeAccounts(accountRepository.countByStatus(Account.AccountStatus.ACTIVE))
            .frozenAccounts(accountRepository.countByStatus(Account.AccountStatus.FROZEN))
            .closedAccounts(accountRepository.countByStatus(Account.AccountStatus.CLOSED))
            .accountsByType(getAccountCountByType())
            .accountsByCurrency(getAccountCountByCurrency())
            .totalBalance(getTotalSystemBalance())
            .build();
    }

    /**
     * Gets accounts requiring compliance review
     */
    public List<AccountSummaryResponse> getAccountsForComplianceReview() {
        List<Account> accounts = accountRepository.findAccountsForComplianceReview();
        
        return accounts.stream()
            .map(this::mapToAccountSummaryResponse)
            .collect(Collectors.toList());
    }

    /**
     * Gets dormant accounts with no recent activity
     */
    public List<AccountSummaryResponse> getDormantAccounts(int inactiveDays) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(inactiveDays);
        List<Account> accounts = accountRepository.findDormantAccounts(cutoffDate);
        
        return accounts.stream()
            .map(this::mapToAccountSummaryResponse)
            .collect(Collectors.toList());
    }

    // Private helper methods

    private Account getAccountById(UUID accountId) {
        return accountRepository.findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
    }

    private Account.AccountCategory determineAccountCategory(String accountType) {
        return switch (Account.AccountType.valueOf(accountType)) {
            case USER_WALLET, USER_SAVINGS, USER_CREDIT, BUSINESS_OPERATING, BUSINESS_ESCROW, MERCHANT -> 
                Account.AccountCategory.LIABILITY;
            case SYSTEM_ASSET, NOSTRO -> Account.AccountCategory.ASSET;
            case SYSTEM_LIABILITY -> Account.AccountCategory.LIABILITY;
            case FEE_COLLECTION -> Account.AccountCategory.REVENUE;
            case SUSPENSE, TRANSIT, RESERVE -> Account.AccountCategory.ASSET;
        };
    }

    private boolean requiresManualApproval(CreateAccountRequest request) {
        return Account.AccountType.valueOf(request.getAccountType()) == Account.AccountType.BUSINESS_OPERATING ||
               Account.AccountType.valueOf(request.getAccountType()) == Account.AccountType.MERCHANT;
    }

    private boolean balanceNeedsUpdate(Account account, BalanceInquiryResponse ledgerBalance) {
        return !account.getCurrentBalance().equals(ledgerBalance.getCurrentBalance());
    }

    private void updateAccountBalance(Account account, BalanceInquiryResponse ledgerBalance) {
        account.setCurrentBalance(ledgerBalance.getCurrentBalance());
        account.setAvailableBalance(ledgerBalance.getAvailableBalance());
        account.setPendingBalance(ledgerBalance.getPendingBalance());
        account.setReservedBalance(ledgerBalance.getReservedBalance());
        accountRepository.save(account);
    }

    private BigDecimal calculateTotalAvailableFunds(BalanceInquiryResponse balance, BigDecimal creditLimit) {
        BigDecimal effectiveCreditLimit = creditLimit != null ? creditLimit : BigDecimal.ZERO;
        return balance.getAvailableBalance().add(effectiveCreditLimit);
    }

    private boolean isValidStatusTransition(Account.AccountStatus currentStatus, Account.AccountStatus newStatus) {
        return switch (currentStatus) {
            case PENDING -> newStatus == Account.AccountStatus.ACTIVE || 
                          newStatus == Account.AccountStatus.CLOSED;
            case ACTIVE -> newStatus == Account.AccountStatus.SUSPENDED || 
                          newStatus == Account.AccountStatus.FROZEN ||
                          newStatus == Account.AccountStatus.DORMANT ||
                          newStatus == Account.AccountStatus.CLOSED;
            case SUSPENDED -> newStatus == Account.AccountStatus.ACTIVE ||
                             newStatus == Account.AccountStatus.CLOSED;
            case FROZEN -> newStatus == Account.AccountStatus.ACTIVE ||
                          newStatus == Account.AccountStatus.CLOSED;
            case DORMANT -> newStatus == Account.AccountStatus.ACTIVE ||
                           newStatus == Account.AccountStatus.CLOSED;
            case CLOSED -> false; // Cannot transition from closed
        };
    }

    private boolean requiresComplianceCheck(Account.AccountStatus newStatus) {
        return newStatus == Account.AccountStatus.FROZEN || 
               newStatus == Account.AccountStatus.CLOSED;
    }

    private boolean canCloseAccount(Account account) {
        // Check if account has zero balance and no pending transactions
        BalanceInquiryResponse balance = ledgerServiceClient.getAccountBalance(account.getAccountId());
        
        return balance.getCurrentBalance().compareTo(BigDecimal.ZERO) == 0 &&
               balance.getPendingBalance().compareTo(BigDecimal.ZERO) == 0 &&
               balance.getReservedBalance().compareTo(BigDecimal.ZERO) == 0;
    }

    private boolean hasSufficientFunds(BalanceInquiryResponse balance, BigDecimal amount) {
        return balance.getAvailableBalance().compareTo(amount) >= 0;
    }

    private AccountResponse mapToAccountResponse(Account account) {
        return AccountResponse.builder()
            .accountId(account.getAccountId())
            .accountNumber(account.getAccountNumber())
            .userId(account.getUserId())
            .accountName(account.getAccountName())
            .accountType(account.getAccountType().toString())
            .status(account.getStatus().toString())
            .currency(account.getCurrency())
            .currentBalance(account.getCurrentBalance())
            .openedDate(account.getOpenedDate())
            .build();
    }

    private AccountSummaryResponse mapToAccountSummaryResponse(Account account) {
        return AccountSummaryResponse.builder()
            .accountId(account.getAccountId())
            .accountNumber(account.getAccountNumber())
            .accountName(account.getAccountName())
            .accountType(account.getAccountType().toString())
            .status(account.getStatus().toString())
            .currency(account.getCurrency())
            .currentBalance(account.getCurrentBalance())
            .availableBalance(account.getAvailableBalance())
            .lastTransactionDate(account.getLastTransactionDate())
            .build();
    }

    private Map<String, Long> getAccountCountByType() {
        List<Object[]> results = accountRepository.getAccountCountByType();
        Map<String, Long> countByType = new HashMap<>();
        
        for (Object[] result : results) {
            Account.AccountType type = (Account.AccountType) result[0];
            Long count = (Long) result[1];
            countByType.put(type.toString(), count);
        }
        
        return countByType;
    }

    private Map<String, Long> getAccountCountByCurrency() {
        List<Object[]> results = accountRepository.getAccountCountByCurrency();
        Map<String, Long> countByCurrency = new HashMap<>();
        
        for (Object[] result : results) {
            String currency = (String) result[0];
            Long count = (Long) result[1];
            countByCurrency.put(currency, count);
        }
        
        return countByCurrency;
    }

    private BigDecimal getTotalSystemBalance() {
        BigDecimal totalBalance = accountRepository.getTotalSystemBalance();
        return totalBalance != null ? totalBalance : BigDecimal.ZERO;
    }
    
    /**
     * SECURITY METHOD: Check if user owns account
     * Used by security service for authorization checks
     * Prevents horizontal privilege escalation
     * 
     * @param accountId The account ID to check
     * @param userId The user ID claiming ownership
     * @return true if user owns the account, false otherwise
     */
    public boolean checkAccountOwnership(UUID accountId, UUID userId) {
        log.debug("SECURITY: Checking account ownership - accountId={}, userId={}", accountId, userId);
        
        try {
            Optional<Account> accountOpt = accountRepository.findById(accountId);
            
            if (accountOpt.isEmpty()) {
                log.warn("SECURITY: Account not found - accountId={}", accountId);
                return false;
            }
            
            Account account = accountOpt.get();
            boolean isOwner = userId.equals(account.getUserId());
            
            if (!isOwner) {
                log.warn("SECURITY: Ownership check FAILED - user {} does NOT own account {} (owner is {})", 
                    userId, accountId, account.getUserId());
            } else {
                log.debug("SECURITY: Ownership check SUCCESS - user {} owns account {}", 
                    userId, accountId);
            }
            
            return isOwner;
            
        } catch (Exception e) {
            log.error("SECURITY: Error checking account ownership - accountId={}, userId={}", 
                accountId, userId, e);
            return false;
        }
    }
}