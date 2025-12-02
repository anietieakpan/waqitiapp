package com.waqiti.ledger.service;

import com.waqiti.ledger.domain.Account;
import com.waqiti.ledger.dto.*;
import com.waqiti.ledger.repository.AccountRepository;
import com.waqiti.ledger.exception.AccountNotFoundException;
import com.waqiti.ledger.exception.ChartOfAccountsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Chart of Accounts Service
 * 
 * Manages the hierarchical structure of accounts and provides
 * account classification and validation services for the double-entry
 * ledger system.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChartOfAccountsService {

    private final AccountRepository accountRepository;
    
    // Standard account type classifications
    private static final Set<Account.AccountType> ASSET_TYPES = Set.of(
        Account.AccountType.ASSET, 
        Account.AccountType.CURRENT_ASSET, 
        Account.AccountType.FIXED_ASSET
    );
    
    private static final Set<Account.AccountType> LIABILITY_TYPES = Set.of(
        Account.AccountType.LIABILITY,
        Account.AccountType.CURRENT_LIABILITY,
        Account.AccountType.LONG_TERM_LIABILITY
    );
    
    private static final Set<Account.AccountType> EQUITY_TYPES = Set.of(
        Account.AccountType.EQUITY,
        Account.AccountType.RETAINED_EARNINGS,
        Account.AccountType.PAID_IN_CAPITAL
    );
    
    private static final Set<Account.AccountType> REVENUE_TYPES = Set.of(
        Account.AccountType.REVENUE,
        Account.AccountType.OPERATING_REVENUE,
        Account.AccountType.OTHER_REVENUE
    );
    
    private static final Set<Account.AccountType> EXPENSE_TYPES = Set.of(
        Account.AccountType.EXPENSE,
        Account.AccountType.OPERATING_EXPENSE,
        Account.AccountType.OTHER_EXPENSE
    );

    /**
     * Gets the complete chart of accounts with hierarchical structure
     */
    @Cacheable("chartOfAccounts")
    public ChartOfAccountsResponse getChartOfAccounts() {
        try {
            List<Account> allAccounts = accountRepository.findAllByOrderByAccountCodeAsc();
            
            // Build hierarchical structure
            Map<UUID, List<Account>> parentChildMap = buildParentChildMap(allAccounts);
            List<AccountNode> rootNodes = buildAccountTree(allAccounts, parentChildMap, null);
            
            return ChartOfAccountsResponse.builder()
                .accounts(rootNodes)
                .totalAccounts(allAccounts.size())
                .lastUpdated(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to get chart of accounts", e);
            throw new ChartOfAccountsException("Failed to retrieve chart of accounts", e);
        }
    }

    /**
     * Creates a new account in the chart of accounts
     */
    @Transactional
    public CreateAccountResponse createAccount(CreateAccountRequest request) {
        try {
            log.info("Creating account: {} - {}", request.getAccountCode(), request.getAccountName());
            
            // Validate account code uniqueness
            if (accountRepository.existsByAccountCode(request.getAccountCode())) {
                throw new ChartOfAccountsException("Account code already exists: " + request.getAccountCode());
            }
            
            // Validate parent account if specified
            if (request.getParentAccountId() != null) {
                Account parentAccount = accountRepository.findById(request.getParentAccountId())
                    .orElseThrow(() -> new AccountNotFoundException("Parent account not found: " + request.getParentAccountId()));
                
                // Validate parent-child account type compatibility
                validateAccountTypeCompatibility(request.getAccountType(), parentAccount.getAccountType());
            }
            
            Account account = Account.builder()
                .accountCode(request.getAccountCode())
                .accountName(request.getAccountName())
                .accountType(Account.AccountType.valueOf(request.getAccountType()))
                .parentAccountId(request.getParentAccountId())
                .description(request.getDescription())
                .isActive(true)
                .allowsTransactions(request.isAllowsTransactions())
                .currency(request.getCurrency())
                .normalBalance(Account.NormalBalance.valueOf(request.getNormalBalance()))
                .createdAt(LocalDateTime.now())
                .lastUpdated(LocalDateTime.now())
                .build();
            
            Account savedAccount = accountRepository.save(account);
            
            log.info("Successfully created account: {} with ID: {}", 
                savedAccount.getAccountCode(), savedAccount.getAccountId());
            
            return CreateAccountResponse.builder()
                .accountId(savedAccount.getAccountId())
                .accountCode(savedAccount.getAccountCode())
                .accountName(savedAccount.getAccountName())
                .success(true)
                .build();
                
        } catch (ChartOfAccountsException | AccountNotFoundException e) {
            log.error("Failed to create account: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error creating account", e);
            throw new ChartOfAccountsException("Failed to create account", e);
        }
    }

    /**
     * Updates an existing account
     */
    @Transactional
    public UpdateAccountResponse updateAccount(UUID accountId, UpdateAccountRequest request) {
        try {
            Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
            
            // Update modifiable fields
            if (request.getAccountName() != null) {
                account.setAccountName(request.getAccountName());
            }
            
            if (request.getDescription() != null) {
                account.setDescription(request.getDescription());
            }
            
            if (request.getIsActive() != null) {
                account.setIsActive(request.getIsActive());
            }
            
            if (request.getAllowsTransactions() != null) {
                account.setAllowsTransactions(request.getAllowsTransactions());
            }
            
            account.setLastUpdated(LocalDateTime.now());
            
            Account updatedAccount = accountRepository.save(account);
            
            log.info("Successfully updated account: {}", updatedAccount.getAccountCode());
            
            return UpdateAccountResponse.builder()
                .accountId(updatedAccount.getAccountId())
                .success(true)
                .build();
                
        } catch (AccountNotFoundException e) {
            log.error("Account not found for update: {}", accountId);
            throw e;
        } catch (Exception e) {
            log.error("Failed to update account: {}", accountId, e);
            throw new ChartOfAccountsException("Failed to update account", e);
        }
    }

    /**
     * Gets account details by ID
     */
    @Cacheable(value = "account", key = "#accountId")
    public AccountDetailsResponse getAccount(UUID accountId) {
        try {
            Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
            
            return mapToAccountDetailsResponse(account);
            
        } catch (AccountNotFoundException e) {
            log.error("Account not found: {}", accountId);
            throw e;
        } catch (Exception e) {
            log.error("Failed to get account: {}", accountId, e);
            throw new ChartOfAccountsException("Failed to retrieve account", e);
        }
    }

    /**
     * Gets all accounts by type
     */
    public List<AccountDetailsResponse> getAccountsByType(Account.AccountType accountType) {
        try {
            List<Account> accounts = accountRepository.findByAccountTypeAndIsActiveTrue(accountType);
            
            return accounts.stream()
                .map(this::mapToAccountDetailsResponse)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Failed to get accounts by type: {}", accountType, e);
            throw new ChartOfAccountsException("Failed to retrieve accounts by type", e);
        }
    }

    /**
     * Gets all active accounts that allow transactions
     */
    public List<AccountDetailsResponse> getTransactionAccounts() {
        try {
            List<Account> accounts = accountRepository.findByIsActiveTrueAndAllowsTransactionsTrue();
            
            return accounts.stream()
                .map(this::mapToAccountDetailsResponse)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Failed to get transaction accounts", e);
            throw new ChartOfAccountsException("Failed to retrieve transaction accounts", e);
        }
    }

    /**
     * Checks if an account is of asset type
     */
    public boolean isAssetAccount(UUID accountId) {
        try {
            Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
            
            return ASSET_TYPES.contains(account.getAccountType());
            
        } catch (Exception e) {
            log.error("Failed to check if account is asset type: {}", accountId, e);
            return false;
        }
    }

    /**
     * Checks if an account is of liability type
     */
    public boolean isLiabilityAccount(UUID accountId) {
        try {
            Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
            
            return LIABILITY_TYPES.contains(account.getAccountType());
            
        } catch (Exception e) {
            log.error("Failed to check if account is liability type: {}", accountId, e);
            return false;
        }
    }

    /**
     * Checks if an account is of equity type
     */
    public boolean isEquityAccount(UUID accountId) {
        try {
            Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
            
            return EQUITY_TYPES.contains(account.getAccountType());
            
        } catch (Exception e) {
            log.error("Failed to check if account is equity type: {}", accountId, e);
            return false;
        }
    }

    /**
     * Checks if an account is of revenue type
     */
    public boolean isRevenueAccount(UUID accountId) {
        try {
            Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
            
            return REVENUE_TYPES.contains(account.getAccountType());
            
        } catch (Exception e) {
            log.error("Failed to check if account is revenue type: {}", accountId, e);
            return false;
        }
    }

    /**
     * Checks if an account is of expense type
     */
    public boolean isExpenseAccount(UUID accountId) {
        try {
            Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
            
            return EXPENSE_TYPES.contains(account.getAccountType());
            
        } catch (Exception e) {
            log.error("Failed to check if account is expense type: {}", accountId, e);
            return false;
        }
    }

    /**
     * Gets the account classification (ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE)
     */
    public AccountClassification getAccountClassification(UUID accountId) {
        try {
            Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
            
            if (ASSET_TYPES.contains(account.getAccountType())) {
                return AccountClassification.ASSET;
            } else if (LIABILITY_TYPES.contains(account.getAccountType())) {
                return AccountClassification.LIABILITY;
            } else if (EQUITY_TYPES.contains(account.getAccountType())) {
                return AccountClassification.EQUITY;
            } else if (REVENUE_TYPES.contains(account.getAccountType())) {
                return AccountClassification.REVENUE;
            } else if (EXPENSE_TYPES.contains(account.getAccountType())) {
                return AccountClassification.EXPENSE;
            } else {
                throw new ChartOfAccountsException("Unknown account classification for: " + account.getAccountType());
            }
            
        } catch (AccountNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get account classification: {}", accountId, e);
            throw new ChartOfAccountsException("Failed to get account classification", e);
        }
    }

    /**
     * Validates if an account allows transactions
     */
    public void validateAccountForTransaction(UUID accountId) {
        try {
            Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
            
            if (!account.getIsActive()) {
                throw new ChartOfAccountsException("Account is inactive: " + accountId);
            }
            
            if (!account.getAllowsTransactions()) {
                throw new ChartOfAccountsException("Account does not allow transactions: " + accountId);
            }
            
        } catch (AccountNotFoundException | ChartOfAccountsException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to validate account for transaction: {}", accountId, e);
            throw new ChartOfAccountsException("Failed to validate account", e);
        }
    }

    // Private helper methods

    private Map<UUID, List<Account>> buildParentChildMap(List<Account> allAccounts) {
        Map<UUID, List<Account>> parentChildMap = new HashMap<>();
        
        for (Account account : allAccounts) {
            UUID parentId = account.getParentAccountId();
            parentChildMap.computeIfAbsent(parentId, k -> new ArrayList<>()).add(account);
        }
        
        return parentChildMap;
    }

    private List<AccountNode> buildAccountTree(List<Account> allAccounts, 
                                             Map<UUID, List<Account>> parentChildMap, 
                                             UUID parentId) {
        List<Account> children = parentChildMap.getOrDefault(parentId, new ArrayList<>());
        
        return children.stream()
            .map(account -> AccountNode.builder()
                .account(mapToAccountDetailsResponse(account))
                .children(buildAccountTree(allAccounts, parentChildMap, account.getAccountId()))
                .build())
            .collect(Collectors.toList());
    }

    private void validateAccountTypeCompatibility(String childType, Account.AccountType parentType) {
        Account.AccountType childAccountType = Account.AccountType.valueOf(childType);
        
        // Implement business rules for parent-child account type compatibility
        // For example: CURRENT_ASSET can only be child of ASSET
        if (childAccountType == Account.AccountType.CURRENT_ASSET && parentType != Account.AccountType.ASSET) {
            throw new ChartOfAccountsException("Current Asset accounts can only be children of Asset accounts");
        }
        
        if (childAccountType == Account.AccountType.FIXED_ASSET && parentType != Account.AccountType.ASSET) {
            throw new ChartOfAccountsException("Fixed Asset accounts can only be children of Asset accounts");
        }
        
        // Add more validation rules as needed
    }

    private AccountDetailsResponse mapToAccountDetailsResponse(Account account) {
        return AccountDetailsResponse.builder()
            .accountId(account.getAccountId())
            .accountCode(account.getAccountCode())
            .accountName(account.getAccountName())
            .accountType(account.getAccountType().toString())
            .parentAccountId(account.getParentAccountId())
            .description(account.getDescription())
            .isActive(account.getIsActive())
            .allowsTransactions(account.getAllowsTransactions())
            .currency(account.getCurrency())
            .normalBalance(account.getNormalBalance().toString())
            .createdAt(account.getCreatedAt())
            .lastUpdated(account.getLastUpdated())
            .build();
    }

    /**
     * Update account by account code (String)
     */
    @Transactional
    public UpdateAccountResponse updateAccount(String accountCode, UpdateAccountRequest request) {
        Account account = accountRepository.findByAccountCode(accountCode)
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountCode));

        return updateAccount(account.getAccountId(), request);
    }

    /**
     * Deactivate account by account code
     */
    @Transactional
    public void deactivateAccount(String accountCode) {
        Account account = accountRepository.findByAccountCode(accountCode)
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountCode));

        account.setIsActive(false);
        account.setLastUpdated(LocalDateTime.now());
        accountRepository.save(account);

        log.info("Deactivated account: {}", accountCode);
    }

    /**
     * Search accounts with pagination
     */
    public org.springframework.data.domain.Page<LedgerAccountResponse> searchAccounts(
            String query, org.springframework.data.domain.Pageable pageable) {

        org.springframework.data.domain.Page<Account> accounts =
            accountRepository.findByAccountNameContainingIgnoreCaseOrAccountCodeContainingIgnoreCase(
                query, query, pageable);

        return accounts.map(this::mapToLedgerAccountResponse);
    }

    /**
     * Get all account types
     */
    public List<String> getAccountTypes() {
        return Arrays.stream(Account.AccountType.values())
            .map(Enum::name)
            .collect(Collectors.toList());
    }

    /**
     * Validate account creation request
     */
    public ValidationResponse validateAccount(CreateLedgerAccountRequest request) {
        List<String> errors = new ArrayList<>();

        // Check if account code already exists
        if (accountRepository.existsByAccountCode(request.getAccountCode())) {
            errors.add("Account code already exists: " + request.getAccountCode());
        }

        // Validate account type
        try {
            Account.AccountType.valueOf(request.getAccountType());
        } catch (IllegalArgumentException e) {
            errors.add("Invalid account type: " + request.getAccountType());
        }

        // Validate parent account if specified
        if (request.getParentAccountId() != null) {
            Optional<Account> parent = accountRepository.findById(request.getParentAccountId());
            if (parent.isEmpty()) {
                errors.add("Parent account not found");
            }
        }

        return ValidationResponse.builder()
            .valid(errors.isEmpty())
            .errors(errors)
            .build();
    }

    /**
     * Get account hierarchy starting from a parent
     */
    public AccountHierarchyResponse getAccountHierarchy(UUID parentAccountId) {
        Account parent = accountRepository.findById(parentAccountId)
            .orElseThrow(() -> new AccountNotFoundException("Account not found"));

        return buildAccountHierarchy(parent);
    }

    /**
     * Bulk create accounts
     */
    @Transactional
    public BulkCreateResponse bulkCreateAccounts(List<CreateLedgerAccountRequest> requests) {
        List<LedgerAccountResponse> created = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (CreateLedgerAccountRequest request : requests) {
            try {
                ValidationResponse validation = validateAccount(request);
                if (!validation.isValid()) {
                    errors.add("Account " + request.getAccountCode() + ": " +
                        String.join(", ", validation.getErrors()));
                    continue;
                }

                LedgerAccountResponse response = createLedgerAccount(request);
                created.add(response);
            } catch (Exception e) {
                errors.add("Account " + request.getAccountCode() + ": " + e.getMessage());
            }
        }

        return BulkCreateResponse.builder()
            .totalRequests(requests.size())
            .successCount(created.size())
            .failureCount(errors.size())
            .createdAccounts(created)
            .errors(errors)
            .build();
    }

    /**
     * Export chart of accounts
     */
    public byte[] exportChartOfAccounts(String format) {
        List<Account> accounts = accountRepository.findAll();

        if ("CSV".equalsIgnoreCase(format)) {
            return exportAsCSV(accounts);
        } else if ("JSON".equalsIgnoreCase(format)) {
            return exportAsJSON(accounts);
        } else {
            throw new ChartOfAccountsException("Unsupported export format: " + format);
        }
    }

    // Helper methods

    private LedgerAccountResponse mapToLedgerAccountResponse(Account account) {
        return LedgerAccountResponse.builder()
            .accountId(account.getAccountId())
            .accountCode(account.getAccountCode())
            .accountName(account.getAccountName())
            .accountType(account.getAccountType().toString())
            .parentAccountId(account.getParentAccountId())
            .isActive(account.getIsActive())
            .allowsTransactions(account.getAllowsTransactions())
            .normalBalance(account.getNormalBalance().toString())
            .build();
    }

    private AccountHierarchyResponse buildAccountHierarchy(Account account) {
        List<Account> children = accountRepository.findByParentAccountId(account.getAccountId());

        return AccountHierarchyResponse.builder()
            .account(mapToLedgerAccountResponse(account))
            .children(children.stream()
                .map(this::buildAccountHierarchy)
                .collect(Collectors.toList()))
            .build();
    }

    private byte[] exportAsCSV(List<Account> accounts) {
        StringBuilder csv = new StringBuilder();
        csv.append("Account Code,Account Name,Type,Parent Code,Active,Currency\n");

        for (Account account : accounts) {
            csv.append(account.getAccountCode()).append(",")
               .append(account.getAccountName()).append(",")
               .append(account.getAccountType()).append(",")
               .append(account.getParentAccountId() != null ? account.getParentAccountId() : "").append(",")
               .append(account.getIsActive()).append(",")
               .append(account.getCurrency()).append("\n");
        }

        return csv.toString().getBytes();
    }

    private byte[] exportAsJSON(List<Account> accounts) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsBytes(accounts.stream()
                .map(this::mapToLedgerAccountResponse)
                .collect(Collectors.toList()));
        } catch (Exception e) {
            throw new ChartOfAccountsException("Failed to export as JSON", e);
        }
    }

    /**
     * CRITICAL METHOD - Resolves the wallet liability account for a given wallet ID
     * This method is called by LedgerServiceImpl.getWalletBalance()
     *
     * Wallets are represented as LIABILITY accounts in the double-entry system because:
     * - Customer deposits CREATE a liability for the company
     * - The company owes the customer their wallet balance
     * - Debits DECREASE wallet balance (paying out to customer)
     * - Credits INCREASE wallet balance (receiving from customer)
     *
     * @param walletId The UUID of the wallet
     * @return The UUID of the corresponding liability account
     * @throws AccountNotFoundException if wallet account not found
     */
    @Cacheable(value = "walletLiabilityAccounts", key = "#walletId")
    public UUID resolveWalletLiabilityAccount(UUID walletId) {
        try {
            log.debug("Resolving wallet liability account for wallet: {}", walletId);

            // First, try to find existing mapping through AccountResolutionService integration
            // This delegates to the proper service that manages wallet-account mappings
            Optional<Account> walletAccount = accountRepository
                .findByAccountCodeAndIsActiveTrue("WALLET-" + walletId.toString().substring(0, 8));

            if (walletAccount.isPresent()) {
                UUID accountId = walletAccount.get().getAccountId();
                log.debug("Found existing wallet liability account: {} for wallet: {}", accountId, walletId);
                return accountId;
            }

            // If not found, this is an error condition
            log.error("No liability account found for wallet: {}", walletId);
            throw new AccountNotFoundException(
                "No liability account mapping found for wallet: " + walletId +
                ". Wallet must be mapped to a ledger account before querying balance.");

        } catch (AccountNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to resolve wallet liability account for wallet: {}", walletId, e);
            throw new ChartOfAccountsException(
                "Failed to resolve wallet liability account for wallet: " + walletId, e);
        }
    }

    /**
     * ENHANCED METHOD - Resolves wallet liability account with currency support
     * Used when wallet-account mapping includes currency information
     *
     * @param walletId The wallet UUID
     * @param currency The wallet currency (USD, EUR, etc.)
     * @return The UUID of the corresponding liability account
     */
    @Cacheable(value = "walletLiabilityAccounts", key = "#walletId + '_' + #currency")
    public UUID resolveWalletLiabilityAccount(UUID walletId, String currency) {
        try {
            log.debug("Resolving wallet liability account for wallet: {} with currency: {}",
                walletId, currency);

            // Try to find account with currency-specific code
            String accountCode = String.format("WALLET-%s-%s",
                walletId.toString().substring(0, 8), currency);

            Optional<Account> walletAccount = accountRepository
                .findByAccountCodeAndIsActiveTrue(accountCode);

            if (walletAccount.isPresent()) {
                UUID accountId = walletAccount.get().getAccountId();
                log.debug("Found currency-specific wallet liability account: {} for wallet: {} ({})",
                    accountId, walletId, currency);
                return accountId;
            }

            // Fall back to non-currency-specific resolution
            log.warn("No currency-specific account found, falling back to standard resolution");
            return resolveWalletLiabilityAccount(walletId);

        } catch (Exception e) {
            log.error("Failed to resolve wallet liability account for wallet: {} with currency: {}",
                walletId, currency, e);
            throw new ChartOfAccountsException(
                "Failed to resolve wallet liability account", e);
        }
    }

    /**
     * Validates that an account is a valid wallet liability account
     *
     * @param accountId The account ID to validate
     * @return true if the account is a liability account, false otherwise
     */
    public boolean isWalletLiabilityAccount(UUID accountId) {
        try {
            Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));

            // Wallet accounts should be LIABILITY type
            boolean isLiability = LIABILITY_TYPES.contains(account.getAccountType());

            // Additional validation: check if account code matches wallet pattern
            boolean isWalletPattern = account.getAccountCode() != null &&
                account.getAccountCode().startsWith("WALLET-");

            return isLiability && isWalletPattern;

        } catch (Exception e) {
            log.error("Failed to validate wallet liability account: {}", accountId, e);
            return false;
        }
    }

    /**
     * Creates a new wallet liability account
     * Used when a new wallet is created and needs a corresponding ledger account
     *
     * @param walletId The wallet UUID
     * @param currency The wallet currency
     * @param userId The user who owns the wallet
     * @return The created account ID
     */
    @Transactional
    public UUID createWalletLiabilityAccount(UUID walletId, String currency, UUID userId) {
        try {
            log.info("Creating wallet liability account for wallet: {} with currency: {}",
                walletId, currency);

            // Generate unique account code
            String accountCode = String.format("WALLET-%s-%s",
                walletId.toString().substring(0, 8), currency);

            // Check if account already exists
            if (accountRepository.existsByAccountCode(accountCode)) {
                throw new ChartOfAccountsException(
                    "Wallet liability account already exists: " + accountCode);
            }

            // Find parent liability account (should be "CUSTOMER_WALLETS" or similar)
            Account parentLiabilityAccount = accountRepository
                .findByAccountCode("2000-CUSTOMER-WALLETS")
                .orElseGet(() -> createParentWalletLiabilityAccount());

            // Create new wallet liability account
            Account walletAccount = Account.builder()
                .accountCode(accountCode)
                .accountName(String.format("Wallet %s (%s)",
                    walletId.toString().substring(0, 8), currency))
                .accountType(Account.AccountType.CURRENT_LIABILITY)
                .parentAccountId(parentLiabilityAccount.getAccountId())
                .description(String.format("Customer wallet liability for user: %s", userId))
                .isActive(true)
                .allowsTransactions(true)
                .currency(currency)
                .normalBalance(Account.NormalBalance.CREDIT) // Liabilities have CREDIT normal balance
                .createdAt(LocalDateTime.now())
                .lastUpdated(LocalDateTime.now())
                .build();

            Account savedAccount = accountRepository.save(walletAccount);

            log.info("Successfully created wallet liability account: {} with ID: {}",
                savedAccount.getAccountCode(), savedAccount.getAccountId());

            return savedAccount.getAccountId();

        } catch (ChartOfAccountsException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create wallet liability account for wallet: {}", walletId, e);
            throw new ChartOfAccountsException("Failed to create wallet liability account", e);
        }
    }

    /**
     * Creates the parent "Customer Wallets" liability account if it doesn't exist
     */
    @Transactional
    protected Account createParentWalletLiabilityAccount() {
        try {
            log.info("Creating parent customer wallets liability account");

            Account parentAccount = Account.builder()
                .accountCode("2000-CUSTOMER-WALLETS")
                .accountName("Customer Wallets")
                .accountType(Account.AccountType.CURRENT_LIABILITY)
                .parentAccountId(null)
                .description("Parent account for all customer wallet liabilities")
                .isActive(true)
                .allowsTransactions(false) // Parent accounts typically don't allow direct transactions
                .currency("USD") // Default currency for parent
                .normalBalance(Account.NormalBalance.CREDIT)
                .createdAt(LocalDateTime.now())
                .lastUpdated(LocalDateTime.now())
                .build();

            return accountRepository.save(parentAccount);

        } catch (Exception e) {
            log.error("Failed to create parent wallet liability account", e);
            throw new ChartOfAccountsException("Failed to create parent wallet liability account", e);
        }
    }

    // Keep existing AccountClassification enum
    public enum AccountClassification {
        ASSET,
        LIABILITY,
        EQUITY,
        REVENUE,
        EXPENSE
    }
}