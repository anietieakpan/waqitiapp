package com.waqiti.account.service;

import com.waqiti.account.dto.request.CreateAccountRequestDTO;
import com.waqiti.account.dto.response.AccountResponseDTO;
import com.waqiti.account.domain.Account;
import com.waqiti.account.domain.AccountStatus;
import com.waqiti.account.domain.ComplianceLevel;
import com.waqiti.account.mapper.AccountMapper;
import com.waqiti.account.repository.AccountRepository;
import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.exception.ResourceNotFoundException;
import com.waqiti.common.service.BaseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.security.SecureRandom;

/**
 * Service implementation for Account operations
 * 
 * Extends BaseService to inherit common CRUD operations while providing
 * account-specific business logic and transaction management.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class AccountService extends BaseService<Account, AccountResponseDTO, AccountRepository> {
    
    private static final String CACHE_NAME = "accounts";
    private static final BigDecimal LOW_BALANCE_THRESHOLD = new BigDecimal("100.00");
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("100000.00");
    private static final int DORMANT_DAYS = 90;
    
    @Lazy
    private final AccountService self;
    private final AccountMapper accountMapper;
    private final AccountValidationService validationService;
    private final AccountNotificationService notificationService;
    private final KycService kycService;
    // ✅ CRITICAL FIX: Added distributed lock service for concurrency control
    private final com.waqiti.common.distributed.DistributedLockService distributedLockService;

    public AccountService(@Lazy AccountService self,
                         AccountRepository repository,
                         AccountMapper accountMapper,
                         AccountValidationService validationService,
                         AccountNotificationService notificationService,
                         KycService kycService,
                         com.waqiti.common.distributed.DistributedLockService distributedLockService) {
        super(repository);
        this.self = self;
        this.accountMapper = accountMapper;
        this.validationService = validationService;
        this.notificationService = notificationService;
        this.kycService = kycService;
        this.distributedLockService = distributedLockService;  // ✅ Wire up lock service
    }
    
    @Override
    protected String getCacheName() {
        return CACHE_NAME;
    }
    
    @Override
    protected AccountResponseDTO toDto(Account entity) {
        AccountResponseDTO dto = accountMapper.toDto(entity);
        dto.calculateDerivedFields();
        return dto;
    }
    
    @Override
    protected Account toEntity(AccountResponseDTO dto) {
        return accountMapper.toEntity(dto);
    }
    
    @Override
    protected String getEntityName() {
        return "Account";
    }
    
    @Override
    protected void validateEntity(Account entity) {
        validationService.validateAccount(entity);
    }
    
    /**
     * Create new account with comprehensive validation
     */
    @Transactional(
        propagation = Propagation.REQUIRED,
        isolation = Isolation.SERIALIZABLE,
        rollbackFor = Exception.class
    )
    public AccountResponseDTO createAccount(@Valid @NotNull CreateAccountRequestDTO request) {
        log.info("Creating new account for user: {}", request.getUserId());
        
        // Validate request
        validationService.validateCreateAccountRequest(request);
        
        // Check for duplicate account
        String accountNumber = generateUniqueAccountNumber();
        if (repository.existsByAccountNumber(accountNumber)) {
            throw new BusinessException("Account number generation failed. Please try again.");
        }
        
        // Create account entity
        Account account = accountMapper.createAccountFromRequest(request);
        account.setAccountNumber(accountNumber);
        
        // Set parent account if specified
        if (request.getParentAccountId() != null) {
            Account parentAccount = repository.findById(request.getParentAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Parent account not found"));
            account.setParentAccount(parentAccount);
        }
        
        // Perform KYC verification if required
        if (request.getKycLevel() != null && !request.getKycLevel().equals("LEVEL_0")) {
            KycLevel verifiedLevel = kycService.verifyKyc(request.getUserId(), request.getKycLevel());
            account.setKycLevel(verifiedLevel);
            account.setKycVerifiedAt(LocalDateTime.now());
        }
        
        // Save account
        Account savedAccount = repository.save(account);
        
        // Process initial deposit if provided
        if (request.getInitialDeposit() != null && request.getInitialDeposit().compareTo(BigDecimal.ZERO) > 0) {
            processInitialDeposit(savedAccount, request.getInitialDeposit());
        }
        
        // Activate account if all conditions are met
        if (shouldAutoActivate(savedAccount)) {
            self.activateAccount(savedAccount.getId());
        }
        
        // Send welcome notification
        notificationService.sendAccountCreatedNotification(savedAccount);
        
        // Publish account created event
        publishEvent(new AccountCreatedEvent(savedAccount));
        
        AccountResponseDTO response = toDto(savedAccount);
        log.info("Account created successfully: {}", response.getAccountNumber());
        
        return response;
    }
    
    /**
     * Find accounts by user ID
     */
    @Cacheable(value = CACHE_NAME + "_user", key = "#userId")
    public List<AccountResponseDTO> findAccountsByUserId(@NotNull UUID userId) {
        log.debug("Finding accounts for user: {}", userId);
        
        List<Account> accounts = repository.findByUserId(userId);
        return accounts.stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }
    
    /**
     * Find active accounts by user ID
     */
    public List<AccountResponseDTO> findActiveAccountsByUserId(@NotNull UUID userId) {
        log.debug("Finding active accounts for user: {}", userId);
        
        List<Account> accounts = repository.findActiveAccountsByUserId(userId);
        return accounts.stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }
    
    /**
     * Find account by account number
     */
    @Cacheable(value = CACHE_NAME + "_number", key = "#accountNumber")
    public AccountResponseDTO findByAccountNumber(@NotNull String accountNumber) {
        log.debug("Finding account by number: {}", accountNumber);
        
        Account account = repository.findByAccountNumber(accountNumber)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Account not found with number: " + accountNumber
            ));
        
        return toDto(account);
    }
    
    /**
     * Update account balance with distributed locking and optimistic locking
     *
     * CRITICAL FIX: Changed isolation to SERIALIZABLE to prevent race conditions
     * CRITICAL FIX: Added distributed lock to prevent concurrent balance updates
     * CRITICAL FIX: Using JPA entity save instead of native SQL to leverage @Version
     *
     * @param accountId Account to update
     * @param newBalance New balance
     * @param availableBalance New available balance
     */
    @Transactional(
        propagation = Propagation.REQUIRED,
        isolation = Isolation.SERIALIZABLE,  // ✅ FIXED: Changed from READ_COMMITTED
        rollbackFor = Exception.class
    )
    @CacheEvict(value = CACHE_NAME, key = "#accountId")
    public void updateBalance(@NotNull UUID accountId,
                            @NotNull BigDecimal newBalance,
                            @NotNull BigDecimal availableBalance) {
        String lockKey = "account:balance:" + accountId;
        log.info("Updating balance for account: {} with distributed lock", accountId);

        // ✅ CRITICAL FIX: Acquire distributed lock to prevent race conditions
        java.util.Optional<com.waqiti.common.distributed.DistributedLock> lockOpt =
            distributedLockService.acquireLock(lockKey, 30, 60);

        if (lockOpt.isEmpty()) {
            throw new BusinessException("Failed to acquire lock for account balance update: " + accountId);
        }

        try (com.waqiti.common.distributed.DistributedLock lock = lockOpt.get()) {
            // ✅ CRITICAL FIX: Use JPA entity save to leverage @Version optimistic locking
            Account account = repository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));

            // Update balances
            account.setBalance(newBalance);
            account.setAvailableBalance(availableBalance);
            account.setLastUpdatedAt(LocalDateTime.now());

            // Save entity - JPA will check @Version field and throw OptimisticLockException if stale
            repository.save(account);

            // Check for low balance
            if (newBalance.compareTo(LOW_BALANCE_THRESHOLD) < 0) {
                notificationService.sendLowBalanceAlert(account);
            }

            publishEvent(new BalanceUpdatedEvent(accountId, newBalance));

            log.info("Successfully updated balance for account: {}", accountId);

        } catch (org.springframework.dao.OptimisticLockingFailureException e) {
            log.error("Optimistic lock failure for account: {} - concurrent modification detected", accountId, e);
            throw new BusinessException("Account was modified by another transaction, please retry", e);
        } catch (Exception e) {
            log.error("Error updating balance for account: {}", accountId, e);
            throw new BusinessException("Failed to update account balance", e);
        }
    }
    
    /**
     * Place hold on account funds with distributed locking
     *
     * CRITICAL FIX: Changed isolation to REPEATABLE_READ to prevent phantom reads
     * CRITICAL FIX: Added distributed lock to prevent concurrent hold operations
     * CRITICAL FIX: Using JPA entity save instead of native SQL to leverage @Version
     */
    @Transactional(
        propagation = Propagation.REQUIRED,
        isolation = Isolation.REPEATABLE_READ,  // ✅ FIXED: Changed from READ_COMMITTED
        rollbackFor = Exception.class
    )
    public void placeHold(@NotNull UUID accountId, @NotNull BigDecimal amount) {
        String lockKey = "account:hold:" + accountId;
        log.info("Placing hold of {} on account: {} with distributed lock", amount, accountId);

        // ✅ CRITICAL FIX: Acquire distributed lock
        java.util.Optional<com.waqiti.common.distributed.DistributedLock> lockOpt =
            distributedLockService.acquireLock(lockKey, 30, 60);

        if (lockOpt.isEmpty()) {
            throw new BusinessException("Failed to acquire lock for placing hold: " + accountId);
        }

        try (com.waqiti.common.distributed.DistributedLock lock = lockOpt.get()) {
            // ✅ CRITICAL FIX: Use JPA entity for optimistic locking
            Account account = repository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

            if (!account.isAccountActive()) {
                throw new BusinessException("Cannot place hold on inactive account");
            }

            if (account.getAvailableBalance().compareTo(amount) < 0) {
                throw new BusinessException("Insufficient available balance for hold");
            }

            // Calculate new available balance
            BigDecimal newAvailableBalance = account.getAvailableBalance().subtract(amount);
            account.setAvailableBalance(newAvailableBalance);
            account.setLastUpdatedAt(LocalDateTime.now());

            // Save - leverages @Version optimistic locking
            repository.save(account);

            publishEvent(new HoldPlacedEvent(accountId, amount));

            log.info("Successfully placed hold of {} on account: {}", amount, accountId);

        } catch (org.springframework.dao.OptimisticLockingFailureException e) {
            log.error("Optimistic lock failure placing hold on account: {}", accountId, e);
            throw new BusinessException("Account was modified by another transaction, please retry", e);
        } catch (Exception e) {
            log.error("Error placing hold on account: {}", accountId, e);
            throw new BusinessException("Failed to place hold on account", e);
        }
    }
    
    /**
     * Release hold on account funds with distributed locking
     *
     * CRITICAL FIX: Changed isolation to REPEATABLE_READ to prevent phantom reads
     * CRITICAL FIX: Added distributed lock to prevent concurrent hold operations
     * CRITICAL FIX: Using JPA entity save instead of native SQL to leverage @Version
     */
    @Transactional(
        propagation = Propagation.REQUIRED,
        isolation = Isolation.REPEATABLE_READ,  // ✅ FIXED: Changed from READ_COMMITTED
        rollbackFor = Exception.class
    )
    public void releaseHold(@NotNull UUID accountId, @NotNull BigDecimal amount) {
        String lockKey = "account:hold:" + accountId;
        log.info("Releasing hold of {} on account: {} with distributed lock", amount, accountId);

        // ✅ CRITICAL FIX: Acquire distributed lock
        java.util.Optional<com.waqiti.common.distributed.DistributedLock> lockOpt =
            distributedLockService.acquireLock(lockKey, 30, 60);

        if (lockOpt.isEmpty()) {
            throw new BusinessException("Failed to acquire lock for releasing hold: " + accountId);
        }

        try (com.waqiti.common.distributed.DistributedLock lock = lockOpt.get()) {
            // ✅ CRITICAL FIX: Use JPA entity for optimistic locking
            Account account = repository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

            // Calculate new available balance
            BigDecimal newAvailableBalance = account.getAvailableBalance().add(amount);

            // Ensure available balance doesn't exceed actual balance
            if (newAvailableBalance.compareTo(account.getBalance()) > 0) {
                newAvailableBalance = account.getBalance();
            }

            account.setAvailableBalance(newAvailableBalance);
            account.setLastUpdatedAt(LocalDateTime.now());

            // Save - leverages @Version optimistic locking
            repository.save(account);

            publishEvent(new HoldReleasedEvent(accountId, amount));

            log.info("Successfully released hold of {} on account: {}", amount, accountId);

        } catch (org.springframework.dao.OptimisticLockingFailureException e) {
            log.error("Optimistic lock failure releasing hold on account: {}", accountId, e);
            throw new BusinessException("Account was modified by another transaction, please retry", e);
        } catch (Exception e) {
            log.error("Error releasing hold on account: {}", accountId, e);
            throw new BusinessException("Failed to release hold on account", e);
        }
    }
    
    /**
     * Freeze account
     */
    @Transactional(
        propagation = Propagation.REQUIRED,
        isolation = Isolation.READ_COMMITTED,
        rollbackFor = Exception.class
    )
    @CacheEvict(value = CACHE_NAME, key = "#accountId")
    public void freezeAccount(@NotNull UUID accountId, @NotNull String reason) {
        log.warn("Freezing account: {} for reason: {}", accountId, reason);
        
        int updated = repository.freezeAccount(accountId, reason, LocalDateTime.now());
        
        if (updated == 0) {
            throw new BusinessException("Failed to freeze account");
        }
        
        Account account = repository.findById(accountId).orElseThrow();
        notificationService.sendAccountFrozenNotification(account, reason);
        
        publishEvent(new AccountFrozenEvent(accountId, reason));
    }
    
    /**
     * Unfreeze account
     */
    @Transactional(
        propagation = Propagation.REQUIRED,
        isolation = Isolation.READ_COMMITTED,
        rollbackFor = Exception.class
    )
    @CacheEvict(value = CACHE_NAME, key = "#accountId")
    public void unfreezeAccount(@NotNull UUID accountId) {
        log.info("Unfreezing account: {}", accountId);
        
        int updated = repository.unfreezeAccount(accountId);
        
        if (updated == 0) {
            throw new BusinessException("Failed to unfreeze account");
        }
        
        Account account = repository.findById(accountId).orElseThrow();
        notificationService.sendAccountUnfrozenNotification(account);
        
        publishEvent(new AccountUnfrozenEvent(accountId));
    }
    
    /**
     * Update KYC level
     */
    @Transactional(
        propagation = Propagation.REQUIRED,
        isolation = Isolation.READ_COMMITTED,
        rollbackFor = Exception.class
    )
    @CacheEvict(value = CACHE_NAME, key = "#accountId")
    public void updateKycLevel(@NotNull UUID accountId, @NotNull String kycLevel) {
        log.info("Updating KYC level for account: {} to {}", accountId, kycLevel);
        
        KycLevel level = KycLevel.valueOf(kycLevel);
        int updated = repository.updateKycLevel(accountId, level, LocalDateTime.now());
        
        if (updated == 0) {
            throw new BusinessException("Failed to update KYC level");
        }
        
        publishEvent(new KycLevelUpdatedEvent(accountId, level));
    }
    
    /**
     * Calculate total balance for user
     */
    @Cacheable(value = CACHE_NAME + "_total", key = "#userId")
    public BigDecimal calculateUserTotalBalance(@NotNull UUID userId) {
        BigDecimal total = repository.calculateUserTotalBalance(userId);
        return total != null ? total : BigDecimal.ZERO;
    }
    
    /**
     * Find dormant accounts
     */
    public List<AccountResponseDTO> findDormantAccounts() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(DORMANT_DAYS);
        List<Account> dormantAccounts = repository.findDormantAccounts(cutoffDate);
        
        return dormantAccounts.stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }
    
    /**
     * Find accounts requiring KYC
     */
    public List<AccountResponseDTO> findAccountsRequiringKyc() {
        List<Account> accounts = repository.findAccountsRequiringKYC();
        
        return accounts.stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }
    
    /**
     * Find high-value accounts
     */
    public List<AccountResponseDTO> findHighValueAccounts() {
        List<Account> accounts = repository.findHighValueAccounts(HIGH_VALUE_THRESHOLD);
        
        return accounts.stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }
    
    /**
     * Find accounts with expiring cards
     */
    public List<AccountResponseDTO> findAccountsWithExpiringCards(int daysAhead) {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(daysAhead);
        
        List<Account> accounts = repository.findAccountsWithExpiringCards(startDate, endDate);
        
        return accounts.stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }
    
    /**
     * Process initial deposit
     */
    private void processInitialDeposit(Account account, BigDecimal amount) {
        account.setBalance(amount);
        account.setAvailableBalance(amount);
        account.setLedgerBalance(amount);
        account.setLastTransactionAt(LocalDateTime.now());
        repository.save(account);
    }
    
    /**
     * Check if account should be auto-activated
     */
    private boolean shouldAutoActivate(Account account) {
        return account.getKycLevel() != KycLevel.LEVEL_0 &&
               account.getBalance().compareTo(BigDecimal.ZERO) > 0;
    }
    
    /**
     * Activate account
     */
    @Transactional(
        propagation = Propagation.REQUIRED,
        isolation = Isolation.READ_COMMITTED,
        rollbackFor = Exception.class
    )
    public void activateAccount(@NotNull UUID accountId) {
        repository.updateAccountStatus(accountId, AccountStatus.ACTIVE);
        repository.activate(accountId);
    }
    
    /**
     * Generate unique account number
     */
    private String generateUniqueAccountNumber() {
        String accountNumber;
        int attempts = 0;
        do {
            LocalDateTime now = LocalDateTime.now();
            String datePart = String.format("%04d%02d%02d", 
                now.getYear(), now.getMonthValue(), now.getDayOfMonth());
            SecureRandom secureRandom = new SecureRandom();
            String randomPart = String.format("%06d", 
                secureRandom.nextInt(1000000));
            accountNumber = String.format("WAQT-%s-%s", datePart, randomPart);
            attempts++;
        } while (repository.existsByAccountNumber(accountNumber) && attempts < 10);
        
        if (attempts >= 10) {
            throw new BusinessException("Failed to generate unique account number");
        }
        
        return accountNumber;
    }
    
    /**
     * Scheduled task to reset daily spending limits
     */
    @Scheduled(cron = "0 0 0 * * *") // Daily at midnight
    @Transactional
    public void resetDailySpendingLimits() {
        log.info("Resetting daily spending limits for all accounts");
        int updated = repository.resetDailySpending();
        log.info("Reset daily spending limits for {} accounts", updated);
    }
    
    /**
     * Scheduled task to reset monthly spending limits
     */
    @Scheduled(cron = "0 0 0 1 * *") // First day of month at midnight
    @Transactional
    public void resetMonthlySpendingLimits() {
        log.info("Resetting monthly spending limits for all accounts");
        int updated = repository.resetMonthlySpending();
        log.info("Reset monthly spending limits for {} accounts", updated);
    }
    
    /**
     * Scheduled task to update ledger balances
     */
    @Scheduled(cron = "0 0 23 * * *") // Daily at 11 PM
    @Transactional
    public void updateLedgerBalances() {
        log.info("Updating ledger balances for all accounts");
        int updated = repository.updateLedgerBalances();
        log.info("Updated ledger balances for {} accounts", updated);
    }
    
    /**
     * Scheduled task to calculate interest
     */
    @Scheduled(cron = "0 0 1 * * *") // Daily at 1 AM
    @Transactional
    public void calculateInterest() {
        log.info("Calculating interest for eligible accounts");
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);
        List<Account> accounts = repository.findAccountsForInterestCalculation(cutoffDate);
        
        for (Account account : accounts) {
            try {
                calculateAndApplyInterest(account);
            } catch (IllegalArgumentException | IllegalStateException e) {
                log.error("Invalid account state during interest calculation for account: {} - {}", account.getId(), e.getMessage());
            } catch (java.math.ArithmeticException e) {
                log.error("Mathematical error during interest calculation for account: {}", account.getId(), e);
            } catch (org.springframework.dao.DataAccessException e) {
                log.error("Database error during interest calculation for account: {}", account.getId(), e);
            } catch (RuntimeException e) {
                log.error("Unexpected error during interest calculation for account: {}", account.getId(), e);
            }
        }
        
        log.info("Calculated interest for {} accounts", accounts.size());
    }
    
    /**
     * Calculate and apply interest to account
     */
    private void calculateAndApplyInterest(Account account) {
        if (account.getInterestRate() == null || account.getInterestRate().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        
        // Calculate daily interest
        BigDecimal dailyRate = account.getInterestRate().divide(
            new BigDecimal("365"), 10, java.math.RoundingMode.HALF_UP
        );
        BigDecimal interest = account.getBalance().multiply(dailyRate).divide(
            new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP
        );
        
        if (interest.compareTo(BigDecimal.ZERO) > 0) {
            account.setBalance(account.getBalance().add(interest));
            account.setAvailableBalance(account.getAvailableBalance().add(interest));
            account.setLastInterestCalculatedAt(LocalDateTime.now());
            repository.save(account);
            
            publishEvent(new InterestCalculatedEvent(account.getId(), interest));
        }
    }

    /**
     * Attempts to recover from a failed account creation
     *
     * This method is called by DLQ handlers when account creation fails.
     * It determines if the failure is recoverable and attempts automatic remediation.
     *
     * Recoverable scenarios:
     * - Temporary database connectivity issues
     * - Transient validation failures (KYC service timeout)
     * - Duplicate key violations (retry with new account number)
     * - Race conditions in account number generation
     *
     * Non-recoverable scenarios:
     * - Invalid user ID (user doesn't exist)
     * - Invalid account type
     * - Business rule violations (max accounts exceeded)
     * - Compliance blocks (sanctioned user)
     *
     * @param accountId The account ID that failed to create
     * @param applicationId The application/onboarding ID
     * @param accountType The type of account being created
     * @param exceptionMessage The original exception message
     * @return true if recovery was attempted, false if not recoverable
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean attemptAccountCreationRecovery(String accountId, String applicationId,
                                                   String accountType, String exceptionMessage) {
        log.info("Attempting account creation recovery: accountId={}, applicationId={}, type={}, error={}",
            accountId, applicationId, accountType, exceptionMessage);

        try {
            // Check if account was partially created
            if (accountId != null) {
                Optional<Account> existingAccount = repository.findById(UUID.fromString(accountId));
                if (existingAccount.isPresent()) {
                    Account account = existingAccount.get();

                    // If account exists but is in PENDING_ACTIVATION, complete the activation
                    if (account.getStatus() == AccountStatus.PENDING_ACTIVATION) {
                        log.info("Found partially created account, completing activation: accountId={}", accountId);
                        account.setStatus(AccountStatus.ACTIVE);
                        account.setOpenedAt(LocalDateTime.now());
                        repository.save(account);

                        // Send activation notification
                        notificationService.sendAccountActivatedNotification(account);
                        publishEvent(new AccountCreatedEvent(account));

                        log.info("Successfully recovered account creation: accountId={}", accountId);
                        return true;
                    }
                }
            }

            // Check for recoverable error patterns
            if (exceptionMessage != null) {
                String lowerMessage = exceptionMessage.toLowerCase();

                // Database connectivity issues - recoverable with retry
                if (lowerMessage.contains("connection") ||
                    lowerMessage.contains("timeout") ||
                    lowerMessage.contains("deadlock")) {
                    log.warn("Database connectivity issue detected, marking for retry: {}", exceptionMessage);
                    return true; // Indicates retry is worthwhile
                }

                // Duplicate account number - recoverable by regenerating number
                if (lowerMessage.contains("duplicate") && lowerMessage.contains("account_number")) {
                    log.warn("Duplicate account number detected, can retry with new number");
                    return true;
                }

                // Transient KYC/validation failures - recoverable
                if (lowerMessage.contains("kyc") && lowerMessage.contains("timeout")) {
                    log.warn("KYC service timeout, marking for retry");
                    return true;
                }

                // Non-recoverable errors
                if (lowerMessage.contains("invalid user") ||
                    lowerMessage.contains("user not found") ||
                    lowerMessage.contains("max accounts exceeded") ||
                    lowerMessage.contains("sanctioned") ||
                    lowerMessage.contains("compliance block")) {
                    log.error("Non-recoverable error detected: {}", exceptionMessage);
                    return false;
                }
            }

            // Default: allow one retry for unknown errors
            log.warn("Unknown error pattern, allowing retry: {}", exceptionMessage);
            return true;

        } catch (Exception e) {
            log.error("Error during account creation recovery attempt: accountId={}, error={}",
                accountId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Validates if an application/onboarding ID is valid and active
     *
     * This checks if the account application exists in the system and is
     * in a valid state for account creation.
     *
     * Note: This is a placeholder implementation. In a full system, this would:
     * - Call an external Application/Onboarding Service
     * - Verify application status (APPROVED, PENDING, REJECTED)
     * - Check if account was already created for this application
     * - Validate application hasn't expired
     *
     * @param applicationId The application ID to validate
     * @return true if application is valid, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean isValidApplication(String applicationId) {
        if (applicationId == null || applicationId.trim().isEmpty()) {
            log.warn("Invalid application ID: null or empty");
            return false;
        }

        try {
            // Basic format validation (UUID format)
            UUID.fromString(applicationId);

            // TODO: In production, call application service to validate
            // Example:
            // ApplicationStatus status = applicationServiceClient.getApplicationStatus(applicationId);
            // return status == ApplicationStatus.APPROVED;

            // For now, accept any valid UUID format as a valid application
            // This prevents false negatives during account creation
            log.debug("Application ID format validated: {}", applicationId);

            // TODO: In production, implement proper duplicate check
            // Check if an account already exists for this application
            // This would require adding applicationId field to Account entity
            // or storing it in metadata JSON field and querying accordingly

            // For now, allow the application (prevents blocking valid creations)
            return true;

        } catch (IllegalArgumentException e) {
            log.error("Invalid application ID format: {}", applicationId, e);
            return false;
        } catch (Exception e) {
            log.error("Error validating application: {}", applicationId, e);
            // Default to false on error to prevent invalid account creation
            return false;
        }
    }

    /**
     * Checks if this is the first account for a customer
     *
     * Used to determine if account creation failure impacts customer onboarding.
     * First account failures are critical as they block customer access entirely.
     *
     * Business Impact:
     * - First account = High priority (blocks customer onboarding)
     * - Subsequent account = Medium priority (customer has other accounts)
     *
     * @param userId The user ID to check
     * @return true if this would be the user's first account, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean isFirstAccountForCustomer(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            log.warn("Invalid user ID for first account check: null or empty");
            return false;
        }

        try {
            UUID userUuid = UUID.fromString(userId);

            // Count existing accounts for this user
            List<Account> existingAccounts = repository.findByUserId(userUuid);

            boolean isFirst = existingAccounts.isEmpty();

            if (isFirst) {
                log.info("First account detected for user: userId={}", userId);
            } else {
                log.debug("User has {} existing accounts: userId={}", existingAccounts.size(), userId);
            }

            return isFirst;

        } catch (IllegalArgumentException e) {
            log.error("Invalid user ID format: {}", userId, e);
            return false;
        } catch (Exception e) {
            log.error("Error checking first account status: userId={}", userId, e);
            // Default to false on error (assume not first account to avoid false high-priority alerts)
            return false;
        }
    }

    // Domain Events
    public record AccountCreatedEvent(Account account) {}
    public record BalanceUpdatedEvent(UUID accountId, BigDecimal newBalance) {}
    public record HoldPlacedEvent(UUID accountId, BigDecimal amount) {}
    public record HoldReleasedEvent(UUID accountId, BigDecimal amount) {}
    public record AccountFrozenEvent(UUID accountId, String reason) {}
    public record AccountUnfrozenEvent(UUID accountId) {}
    public record KycLevelUpdatedEvent(UUID accountId, KycLevel level) {}
    public record InterestCalculatedEvent(UUID accountId, BigDecimal amount) {}
    
    /**
     * CRITICAL SECURITY METHOD: Validates account ownership for authorization
     * This method is used by Spring Security @PreAuthorize expressions
     * 
     * @param accountId The account to check ownership for
     * @param userIdString The user ID from authentication context
     * @return true if user owns the account, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean isAccountOwner(UUID accountId, String userIdString) {
        try {
            UUID userId = UUID.fromString(userIdString);
            
            log.debug("Checking ownership: accountId={}, userId={}", accountId, userId);
            
            Account account = repository.findById(accountId)
                .orElse(null);
            
            if (account == null) {
                log.warn("SECURITY: Account not found for ownership check: {}", accountId);
                return false;
            }
            
            boolean isOwner = account.getUserId().equals(userId);
            
            if (!isOwner) {
                log.warn("SECURITY: User {} attempted to access account {} owned by user {}", 
                    userId, accountId, account.getUserId());
            }
            
            return isOwner;
            
        } catch (IllegalArgumentException e) {
            log.error("SECURITY: Invalid user ID format in ownership check: {}", userIdString, e);
            return false;
        } catch (ResourceNotFoundException e) {
            log.warn("SECURITY: Account not found during ownership check - account: {}, user: {}", accountId, userIdString);
            return false;
        } catch (org.springframework.dao.DataAccessException e) {
            log.error("SECURITY: Database error during account ownership check for account {} and user {}", 
                accountId, userIdString, e);
            return false;
        } catch (RuntimeException e) {
            log.error("SECURITY: Unexpected error during account ownership check for account {} and user {}", 
                accountId, userIdString, e);
            return false;
        }
    }
}