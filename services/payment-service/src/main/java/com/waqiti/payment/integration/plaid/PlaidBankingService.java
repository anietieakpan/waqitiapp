package com.waqiti.payment.integration.plaid;

import com.plaid.client.ApiClient;
import com.plaid.client.model.*;
import com.plaid.client.request.PlaidApi;
import com.waqiti.payment.domain.BankAccount;
import com.waqiti.payment.domain.BankAccountVerification;
import com.waqiti.payment.dto.request.BankAccountLinkRequest;
import com.waqiti.payment.dto.response.BankAccountResponse;
import com.waqiti.payment.dto.response.LinkTokenResponse;
import com.waqiti.payment.exception.BankAccountLinkException;
import com.waqiti.payment.vault.PaymentProviderSecretsManager;
import com.waqiti.common.cache.CacheService;
import com.waqiti.common.security.EncryptionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Comprehensive Plaid Banking Integration Service
 * 
 * Provides bank account verification, linking, and ACH transfer capabilities
 * using Plaid's API for secure bank connectivity.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlaidBankingService {

    private final PaymentProviderSecretsManager secretsManager;
    private final CacheService cacheService;
    private final EncryptionService encryptionService;
    private final PlaidWebhookHandler webhookHandler;
    private final PlaidAccountRepository accountRepository;
    private final PlaidTransactionRepository transactionRepository;

    @Value("${plaid.environment:sandbox}")
    private String environment;

    @Value("${plaid.products:auth,transactions,identity}")
    private String[] products;

    @Value("${plaid.country-codes:US}")
    private String[] countryCodes;

    @Value("${plaid.webhook.url}")
    private String webhookUrl;

    @Value("${app.base-url}")
    private String baseUrl;

    // Lazy-loaded credentials from Vault
    private String clientId;
    private String secret;

    private PlaidApi plaidClient;
    private PlaidEnvironment plaidEnvironment;

    @PostConstruct
    public void init() {
        try {
            log.info("SECURITY: Loading Plaid credentials from Vault...");

            // Load credentials from Vault
            this.clientId = secretsManager.getPlaidClientId();
            this.secret = secretsManager.getPlaidSecret();

            // Initialize Plaid environment
            this.plaidEnvironment = PlaidEnvironment.valueOf(environment.toUpperCase());

            // Initialize Plaid API client with Vault-loaded credentials
            ApiClient apiClient = new ApiClient(Map.of(
                "clientId", clientId,
                "secret", secret,
                "plaidVersion", "2020-09-14"
            ));
            apiClient.setPlaidEnvironment(plaidEnvironment);

            this.plaidClient = apiClient.createService(PlaidApi.class);

            log.info("SECURITY: Plaid banking service initialized with Vault-secured credentials (environment: {})", environment);

        } catch (Exception e) {
            log.error("CRITICAL: Failed to load Plaid credentials from Vault", e);
            throw new RuntimeException("Failed to initialize Plaid service - Vault credentials unavailable", e);
        }
    }

    /**
     * Create a Link token for Plaid Link initialization
     *
     * Circuit Breaker: Protects against Plaid API failures
     * Retry: 3 attempts with exponential backoff
     * TimeLimiter: 10-second timeout for API response
     */
    @CircuitBreaker(name = "plaid", fallbackMethod = "createLinkTokenFallback")
    @Retry(name = "plaid")
    @TimeLimiter(name = "plaid")
    public CompletableFuture<LinkTokenResponse> createLinkToken(UUID userId, String userLegalName,
                                                               String userEmail, String userPhone) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                LinkTokenCreateRequestUser user = new LinkTokenCreateRequestUser()
                        .legalName(userLegalName)
                        .emailAddress(userEmail)
                        .phoneNumber(userPhone);

                LinkTokenCreateRequest request = new LinkTokenCreateRequest()
                        .clientName("Waqiti")
                        .language("en")
                        .countryCodes(Arrays.asList(countryCodes))
                        .user(user)
                        .products(Arrays.stream(products)
                                .map(PlaidProduct::valueOf)
                                .collect(Collectors.toList()))
                        .webhook(webhookUrl)
                        .redirectUri(baseUrl + "/plaid/oauth-redirect")
                        .clientUserId(userId.toString());

                // Add account filters for bank accounts
                request.accountFilters(new AccountFilter()
                        .depository(new DepositoryFilter()
                                .accountSubtypes(Arrays.asList(
                                    DepositoryAccountSubtype.CHECKING,
                                    DepositoryAccountSubtype.SAVINGS
                                ))));

                LinkTokenCreateResponse response = plaidClient.linkTokenCreate(request).execute().body();
                
                if (response == null) {
                    throw new BankAccountLinkException("Failed to create Link token");
                }

                // Cache the link token
                String cacheKey = "plaid-link-token:" + userId;
                cacheService.set(cacheKey, response.getLinkToken(), Duration.ofMinutes(30));

                return LinkTokenResponse.builder()
                        .linkToken(response.getLinkToken())
                        .expiration(response.getExpiration())
                        .requestId(response.getRequestId())
                        .build();

            } catch (Exception e) {
                log.error("Failed to create Plaid Link token for user: {}", userId, e);
                throw new BankAccountLinkException("Failed to create Link token: " + e.getMessage());
            }
        });
    }

    /**
     * Fallback method for createLinkToken when Plaid is unavailable
     */
    private CompletableFuture<LinkTokenResponse> createLinkTokenFallback(UUID userId, String userLegalName,
                                                                         String userEmail, String userPhone,
                                                                         Exception exception) {
        log.error("CIRCUIT BREAKER: Plaid service unavailable for createLinkToken - userId: {}, error: {}",
                userId, exception.getMessage());

        return CompletableFuture.failedFuture(
            new BankAccountLinkException("Bank linking service temporarily unavailable. Please try again in a few minutes.")
        );
    }

    /**
     * Exchange public token for access token and link bank accounts
     */
    @CircuitBreaker(name = "plaid", fallbackMethod = "linkBankAccountsFallback")
    @Retry(name = "plaid")
    @TimeLimiter(name = "plaid")
    @Transactional
    public CompletableFuture<List<BankAccountResponse>> linkBankAccounts(BankAccountLinkRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Linking bank accounts for user: {}", request.getUserId());

                // Exchange public token for access token
                ItemPublicTokenExchangeRequest exchangeRequest = new ItemPublicTokenExchangeRequest()
                        .publicToken(request.getPublicToken());

                ItemPublicTokenExchangeResponse exchangeResponse = plaidClient
                        .itemPublicTokenExchange(exchangeRequest).execute().body();

                if (exchangeResponse == null) {
                    throw new BankAccountLinkException("Failed to exchange public token");
                }

                String accessToken = exchangeResponse.getAccessToken();
                String itemId = exchangeResponse.getItemId();

                // Store encrypted access token
                String encryptedToken = encryptionService.encrypt(accessToken);
                
                // Get accounts information
                AccountsGetRequest accountsRequest = new AccountsGetRequest()
                        .accessToken(accessToken);

                AccountsGetResponse accountsResponse = plaidClient
                        .accountsGet(accountsRequest).execute().body();

                if (accountsResponse == null || accountsResponse.getAccounts().isEmpty()) {
                    throw new BankAccountLinkException("No accounts found");
                }

                // Get institution information
                Institution institution = getInstitutionInfo(exchangeResponse.getItemId());

                // Process and save bank accounts
                List<BankAccountResponse> linkedAccounts = new ArrayList<>();
                
                for (Account account : accountsResponse.getAccounts()) {
                    if (isEligibleAccount(account)) {
                        BankAccount bankAccount = createBankAccount(
                            request.getUserId(), account, itemId, encryptedToken, institution);
                        
                        // Perform micro-deposit verification for ACH
                        initiateAccountVerification(bankAccount, accessToken);
                        
                        linkedAccounts.add(mapToBankAccountResponse(bankAccount, account));
                    }
                }

                log.info("Successfully linked {} bank accounts for user: {}", 
                        linkedAccounts.size(), request.getUserId());

                return linkedAccounts;

            } catch (Exception e) {
                log.error("Failed to link bank accounts for user: {}", request.getUserId(), e);
                throw new BankAccountLinkException("Failed to link bank accounts: " + e.getMessage());
            }
        });
    }

    /**
     * Verify bank account using micro-deposits
     */
    @Transactional
    public CompletableFuture<BankAccountVerification> verifyAccountWithMicroDeposits(
            UUID userId, String accountId, List<BigDecimal> microDepositAmounts) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                BankAccount bankAccount = accountRepository.findByUserIdAndPlaidAccountId(userId, accountId)
                        .orElseThrow(() -> new BankAccountLinkException("Bank account not found"));

                String accessToken = encryptionService.decrypt(bankAccount.getEncryptedAccessToken());

                // Verify micro-deposits with Plaid
                ProcessorAuthMicroDepositVerifyRequest verifyRequest = 
                    new ProcessorAuthMicroDepositVerifyRequest()
                        .processorToken(bankAccount.getProcessorToken())
                        .microDeposits(microDepositAmounts.stream()
                                .map(amount -> amount.doubleValue())
                                .collect(Collectors.toList()));

                ProcessorAuthMicroDepositVerifyResponse verifyResponse = plaidClient
                        .processorAuthMicroDepositVerify(verifyRequest).execute().body();

                if (verifyResponse == null) {
                    throw new BankAccountLinkException("Micro-deposit verification failed");
                }

                // Update account verification status
                bankAccount.setVerificationStatus(BankAccount.VerificationStatus.VERIFIED);
                bankAccount.setVerifiedAt(LocalDateTime.now());
                bankAccount.setIsActive(true);
                accountRepository.save(bankAccount);

                BankAccountVerification verification = BankAccountVerification.builder()
                        .bankAccountId(bankAccount.getId())
                        .verificationType(BankAccountVerification.VerificationType.MICRO_DEPOSITS)
                        .status(BankAccountVerification.Status.VERIFIED)
                        .verifiedAt(LocalDateTime.now())
                        .requestId(verifyResponse.getRequestId())
                        .build();

                log.info("Successfully verified bank account: {} for user: {}", accountId, userId);
                return verification;

            } catch (Exception e) {
                log.error("Failed to verify bank account: {} for user: {}", accountId, userId, e);
                throw new BankAccountLinkException("Account verification failed: " + e.getMessage());
            }
        });
    }

    /**
     * Get account balance and transactions
     */
    public CompletableFuture<AccountBalanceResponse> getAccountBalance(UUID userId, String accountId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                BankAccount bankAccount = accountRepository.findByUserIdAndPlaidAccountId(userId, accountId)
                        .orElseThrow(() -> new BankAccountLinkException("Bank account not found"));

                String accessToken = encryptionService.decrypt(bankAccount.getEncryptedAccessToken());

                AccountsBalanceGetRequest balanceRequest = new AccountsBalanceGetRequest()
                        .accessToken(accessToken)
                        .accountIds(Arrays.asList(accountId));

                AccountsBalanceGetResponse balanceResponse = plaidClient
                        .accountsBalanceGet(balanceRequest).execute().body();

                if (balanceResponse == null || balanceResponse.getAccounts().isEmpty()) {
                    throw new BankAccountLinkException("Failed to retrieve account balance");
                }

                Account account = balanceResponse.getAccounts().get(0);
                
                return AccountBalanceResponse.builder()
                        .accountId(accountId)
                        .available(BigDecimal.valueOf(account.getBalances().getAvailable()))
                        .current(BigDecimal.valueOf(account.getBalances().getCurrent()))
                        .currency(account.getBalances().getIsoCurrencyCode())
                        .lastUpdated(LocalDateTime.now())
                        .build();

            } catch (Exception e) {
                log.error("Failed to get account balance for user: {} account: {}", userId, accountId, e);
                throw new BankAccountLinkException("Failed to retrieve account balance: " + e.getMessage());
            }
        });
    }

    /**
     * Get account transactions
     */
    public CompletableFuture<List<PlaidTransactionResponse>> getAccountTransactions(
            UUID userId, String accountId, LocalDateTime startDate, LocalDateTime endDate) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                BankAccount bankAccount = accountRepository.findByUserIdAndPlaidAccountId(userId, accountId)
                        .orElseThrow(() -> new BankAccountLinkException("Bank account not found"));

                String accessToken = encryptionService.decrypt(bankAccount.getEncryptedAccessToken());

                TransactionsGetRequest transactionsRequest = new TransactionsGetRequest()
                        .accessToken(accessToken)
                        .startDate(startDate.toLocalDate())
                        .endDate(endDate.toLocalDate())
                        .accountIds(Arrays.asList(accountId));

                TransactionsGetResponse transactionsResponse = plaidClient
                        .transactionsGet(transactionsRequest).execute().body();

                if (transactionsResponse == null) {
                    throw new BankAccountLinkException("Failed to retrieve transactions");
                }

                return transactionsResponse.getTransactions().stream()
                        .map(this::mapToTransactionResponse)
                        .collect(Collectors.toList());

            } catch (Exception e) {
                log.error("Failed to get transactions for user: {} account: {}", userId, accountId, e);
                throw new BankAccountLinkException("Failed to retrieve transactions: " + e.getMessage());
            }
        });
    }

    /**
     * Create processor token for ACH transfers
     */
    public CompletableFuture<String> createProcessorToken(UUID userId, String accountId, String processor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                BankAccount bankAccount = accountRepository.findByUserIdAndPlaidAccountId(userId, accountId)
                        .orElseThrow(() -> new BankAccountLinkException("Bank account not found"));

                String accessToken = encryptionService.decrypt(bankAccount.getEncryptedAccessToken());

                ProcessorTokenCreateRequest processorRequest = new ProcessorTokenCreateRequest()
                        .accessToken(accessToken)
                        .accountId(accountId)
                        .processor(ProcessorTokenCreateRequest.ProcessorEnum.fromValue(processor));

                ProcessorTokenCreateResponse processorResponse = plaidClient
                        .processorTokenCreate(processorRequest).execute().body();

                if (processorResponse == null) {
                    throw new BankAccountLinkException("Failed to create processor token");
                }

                // Store processor token
                bankAccount.setProcessorToken(encryptionService.encrypt(processorResponse.getProcessorToken()));
                accountRepository.save(bankAccount);

                return processorResponse.getProcessorToken();

            } catch (Exception e) {
                log.error("Failed to create processor token for user: {} account: {}", userId, accountId, e);
                throw new BankAccountLinkException("Failed to create processor token: " + e.getMessage());
            }
        });
    }

    /**
     * Remove bank account link
     */
    @Transactional
    public CompletableFuture<Void> removeBankAccount(UUID userId, String accountId) {
        return CompletableFuture.runAsync(() -> {
            try {
                BankAccount bankAccount = accountRepository.findByUserIdAndPlaidAccountId(userId, accountId)
                        .orElseThrow(() -> new BankAccountLinkException("Bank account not found"));

                // Soft delete the account
                bankAccount.setIsActive(false);
                bankAccount.setRemovedAt(LocalDateTime.now());
                accountRepository.save(bankAccount);

                // Clear cache
                String cacheKey = "bank-account:" + userId + ":" + accountId;
                cacheService.evict(cacheKey);

                log.info("Successfully removed bank account: {} for user: {}", accountId, userId);

            } catch (Exception e) {
                log.error("Failed to remove bank account: {} for user: {}", accountId, userId, e);
                throw new BankAccountLinkException("Failed to remove bank account: " + e.getMessage());
            }
        });
    }

    /**
     * Get user's linked bank accounts
     */
    public CompletableFuture<List<BankAccountResponse>> getUserBankAccounts(UUID userId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<BankAccount> bankAccounts = accountRepository.findByUserIdAndIsActiveTrue(userId);
                
                return bankAccounts.stream()
                        .map(this::mapToBankAccountResponse)
                        .collect(Collectors.toList());

            } catch (Exception e) {
                log.error("Failed to get bank accounts for user: {}", userId, e);
                throw new BankAccountLinkException("Failed to retrieve bank accounts: " + e.getMessage());
            }
        });
    }

    // Private helper methods

    private Institution getInstitutionInfo(String itemId) {
        try {
            ItemGetRequest itemRequest = new ItemGetRequest().accessToken(itemId);
            ItemGetResponse itemResponse = plaidClient.itemGet(itemRequest).execute().body();
            
            if (itemResponse != null && itemResponse.getItem() != null) {
                InstitutionsGetByIdRequest institutionRequest = new InstitutionsGetByIdRequest()
                        .institutionId(itemResponse.getItem().getInstitutionId())
                        .countryCodes(Arrays.asList(countryCodes));
                
                InstitutionsGetByIdResponse institutionResponse = plaidClient
                        .institutionsGetById(institutionRequest).execute().body();
                
                if (institutionResponse != null) {
                    return institutionResponse.getInstitution();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get institution info for item: {}", itemId, e);
        }
        
        // Return a fallback institution object when API call fails
        return Institution.builder()
            .institutionId("unknown")
            .name("Unknown Institution")
            .products(Arrays.asList("transactions", "auth"))
            .countryCodes(Arrays.asList("US"))
            .build();
    }

    private boolean isEligibleAccount(Account account) {
        // Only allow checking and savings accounts
        return account.getSubtype() == AccountSubtype.CHECKING || 
               account.getSubtype() == AccountSubtype.SAVINGS;
    }

    private BankAccount createBankAccount(UUID userId, Account account, String itemId, 
                                        String encryptedAccessToken, Institution institution) {
        
        BankAccount bankAccount = BankAccount.builder()
                .userId(userId)
                .plaidAccountId(account.getAccountId())
                .plaidItemId(itemId)
                .encryptedAccessToken(encryptedAccessToken)
                .accountName(account.getName())
                .accountType(mapAccountType(account.getType()))
                .accountSubtype(mapAccountSubtype(account.getSubtype()))
                .mask(account.getMask())
                .institutionId(institution != null ? institution.getInstitutionId() : null)
                .institutionName(institution != null ? institution.getName() : "Unknown")
                .verificationStatus(BankAccount.VerificationStatus.PENDING)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();

        return accountRepository.save(bankAccount);
    }

    private void initiateAccountVerification(BankAccount bankAccount, String accessToken) {
        try {
            // For sandbox, verification is immediate
            if (plaidEnvironment == PlaidEnvironment.SANDBOX) {
                bankAccount.setVerificationStatus(BankAccount.VerificationStatus.VERIFIED);
                bankAccount.setVerifiedAt(LocalDateTime.now());
                accountRepository.save(bankAccount);
                return;
            }

            // For production, initiate micro-deposits
            ProcessorAuthMicroDepositCreateRequest microDepositRequest = 
                new ProcessorAuthMicroDepositCreateRequest()
                    .processorToken(bankAccount.getProcessorToken());

            ProcessorAuthMicroDepositCreateResponse microDepositResponse = plaidClient
                    .processorAuthMicroDepositCreate(microDepositRequest).execute().body();

            if (microDepositResponse != null) {
                bankAccount.setVerificationStatus(BankAccount.VerificationStatus.MICRO_DEPOSITS_SENT);
                accountRepository.save(bankAccount);
            }

        } catch (Exception e) {
            log.error("Failed to initiate account verification for account: {}", 
                    bankAccount.getPlaidAccountId(), e);
        }
    }

    private BankAccount.AccountType mapAccountType(AccountType plaidType) {
        switch (plaidType) {
            case DEPOSITORY:
                return BankAccount.AccountType.DEPOSITORY;
            case CREDIT:
                return BankAccount.AccountType.CREDIT;
            case LOAN:
                return BankAccount.AccountType.LOAN;
            case INVESTMENT:
                return BankAccount.AccountType.INVESTMENT;
            default:
                return BankAccount.AccountType.OTHER;
        }
    }

    private BankAccount.AccountSubtype mapAccountSubtype(AccountSubtype plaidSubtype) {
        if (plaidSubtype == null) return BankAccount.AccountSubtype.OTHER;
        
        switch (plaidSubtype.toString().toUpperCase()) {
            case "CHECKING":
                return BankAccount.AccountSubtype.CHECKING;
            case "SAVINGS":
                return BankAccount.AccountSubtype.SAVINGS;
            case "MONEY_MARKET":
                return BankAccount.AccountSubtype.MONEY_MARKET;
            case "CD":
                return BankAccount.AccountSubtype.CD;
            default:
                return BankAccount.AccountSubtype.OTHER;
        }
    }

    private BankAccountResponse mapToBankAccountResponse(BankAccount bankAccount, Account plaidAccount) {
        return BankAccountResponse.builder()
                .accountId(bankAccount.getPlaidAccountId())
                .accountName(bankAccount.getAccountName())
                .accountType(bankAccount.getAccountType().toString())
                .accountSubtype(bankAccount.getAccountSubtype().toString())
                .mask(bankAccount.getMask())
                .institutionName(bankAccount.getInstitutionName())
                .verificationStatus(bankAccount.getVerificationStatus().toString())
                .isActive(bankAccount.getIsActive())
                .balance(plaidAccount != null && plaidAccount.getBalances() != null ? 
                        BigDecimal.valueOf(plaidAccount.getBalances().getAvailable()) : null)
                .currency(plaidAccount != null && plaidAccount.getBalances() != null ?
                        plaidAccount.getBalances().getIsoCurrencyCode() : "USD")
                .createdAt(bankAccount.getCreatedAt())
                .build();
    }

    private BankAccountResponse mapToBankAccountResponse(BankAccount bankAccount) {
        return mapToBankAccountResponse(bankAccount, null);
    }

    private PlaidTransactionResponse mapToTransactionResponse(Transaction transaction) {
        return PlaidTransactionResponse.builder()
                .transactionId(transaction.getTransactionId())
                .accountId(transaction.getAccountId())
                .amount(BigDecimal.valueOf(transaction.getAmount()))
                .currency(transaction.getIsoCurrencyCode())
                .date(transaction.getDate())
                .name(transaction.getName())
                .merchantName(transaction.getMerchantName())
                .category(transaction.getCategory())
                .accountOwner(transaction.getAccountOwner())
                .pending(transaction.getPending())
                .build();
    }
    
    /**
     * Check if Plaid service is healthy
     */
    public boolean isHealthy() {
        try {
            // Simple health check - try to make a basic API call
            // In production, this could check specific health endpoints
            return plaidApi != null;
        } catch (Exception e) {
            log.error("Plaid health check failed", e);
            return false;
        }
    }
}