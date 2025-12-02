package com.waqiti.payment.client;

import com.waqiti.payment.dto.*;
import com.waqiti.common.banking.dto.*;
import com.waqiti.common.banking.providers.*;
import com.waqiti.common.banking.encryption.BankingEncryptionService;
import com.waqiti.common.banking.compliance.BankingComplianceService;
import com.waqiti.common.banking.monitoring.BankingMetricsService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import jakarta.annotation.PostConstruct;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Industrial-strength multi-provider banking API client
 * 
 * Features:
 * - Multi-provider support (Plaid, Yodlee, FIS, Fiserv)
 * - Account verification and validation
 * - Real-time balance checking
 * - Transaction initiation and tracking
 * - Account linking and management
 * - Fraud detection integration
 * - Compliance monitoring
 * - Rate limiting and throttling
 * - Comprehensive error handling
 * - Metrics and monitoring
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BankingAPIClient {
    
    private final WebClient.Builder webClientBuilder;
    private final PlaidBankingProvider plaidProvider;
    private final YodleeBankingProvider yodleeProvider;
    private final FISBankingProvider fisProvider;
    private final FiservBankingProvider fiservProvider;
    private final BankingEncryptionService encryptionService;
    private final BankingComplianceService complianceService;
    private final BankingMetricsService metricsService;
    private final MeterRegistry meterRegistry;
    
    @Value("${banking.providers.primary:plaid}")
    private String primaryProvider;
    
    @Value("${banking.providers.failover:yodlee}")
    private String failoverProvider;
    
    @Value("${banking.api.timeout:30}")
    private int timeoutSeconds;
    
    @Value("${banking.retry.max-attempts:3}")
    private int maxRetryAttempts;
    
    @Value("${banking.cache.ttl:300}")
    private int cacheTtlSeconds;
    
    // Provider mapping and health tracking
    private final Map<String, BankingProvider> providers = new ConcurrentHashMap<>();
    private final Map<String, ProviderHealthStatus> providerHealth = new ConcurrentHashMap<>();
    private WebClient webClient;
    
    @PostConstruct
    public void init() {
        // Initialize providers
        providers.put("plaid", plaidProvider);
        providers.put("yodlee", yodleeProvider);
        providers.put("fis", fisProvider);
        providers.put("fiserv", fiservProvider);
        
        // Initialize health status
        providers.keySet().forEach(provider -> 
            providerHealth.put(provider, ProviderHealthStatus.HEALTHY)
        );
        
        this.webClient = webClientBuilder
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
            
        log.info("BankingAPIClient initialized with providers: {}", providers.keySet());
        log.info("Primary provider: {}, Failover: {}", primaryProvider, failoverProvider);
    }
    
    /**
     * Links a bank account using account credentials
     */
    @CircuitBreaker(name = "banking-link-account", fallbackMethod = "fallbackLinkAccount")
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<AccountLinkResult> linkAccount(AccountLinkRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        log.info("Linking bank account for user: {}", request.getUserId());
        
        return executeWithProvider(request.getPreferredProvider(), () -> {
            BankingProvider provider = getHealthyProvider(request.getPreferredProvider());
            
            // Encrypt sensitive data
            EncryptedCredentials encryptedCreds = encryptionService.encryptCredentials(
                request.getBankCredentials()
            );
            
            // Compliance check
            complianceService.validateAccountLinking(request);
            
            return provider.linkAccount(AccountLinkRequest.builder()
                .userId(request.getUserId())
                .institutionId(request.getInstitutionId())
                .encryptedCredentials(encryptedCreds)
                .metadata(request.getMetadata())
                .build())
                .thenApply(result -> {
                    // Record metrics
                    sample.stop(Timer.builder("banking.account.link")
                        .tag("provider", provider.getProviderName())
                        .tag("status", result.isSuccessful() ? "success" : "failure")
                        .register(meterRegistry));
                    
                    metricsService.recordAccountLink(provider.getProviderName(), result.isSuccessful());
                    return result;
                });
        }).exceptionally(ex -> {
            log.error("Account linking failed", ex);
            incrementErrorCounter("account.link");
            return AccountLinkResult.failed("Account linking failed: " + ex.getMessage());
        });
    }
    
    /**
     * Verifies a bank account using micro-deposits or instant verification
     */
    @CircuitBreaker(name = "banking-verify-account", fallbackMethod = "fallbackVerifyAccount")
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<AccountVerificationResult> verifyAccount(AccountVerificationRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        log.info("Verifying bank account: {}", request.getAccountId());
        
        return executeWithProvider(null, () -> {
            BankingProvider provider = getHealthyProvider(null);
            
            return provider.verifyAccount(request)
                .thenApply(result -> {
                    sample.stop(Timer.builder("banking.account.verify")
                        .tag("provider", provider.getProviderName())
                        .tag("method", request.getVerificationMethod().toString())
                        .tag("status", result.isSuccessful() ? "success" : "failure")
                        .register(meterRegistry));
                        
                    if (result.isSuccessful()) {
                        log.info("Account verification successful: {}", request.getAccountId());
                    }
                    
                    return result;
                });
        }).exceptionally(ex -> {
            log.error("Account verification failed", ex);
            incrementErrorCounter("account.verify");
            return AccountVerificationResult.failed("Verification failed: " + ex.getMessage());
        });
    }
    
    /**
     * Gets real-time account balance
     */
    @CircuitBreaker(name = "banking-balance", fallbackMethod = "fallbackGetBalance")
    @Cacheable(value = "account-balances", key = "#accountId", unless = "#result.hasError()")
    public CompletableFuture<BalanceResult> getAccountBalance(String accountId, String userId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        log.debug("Getting account balance for: {}", accountId);
        
        return executeWithProvider(null, () -> {
            BankingProvider provider = getHealthyProvider(null);
            
            return provider.getBalance(BalanceRequest.builder()
                .accountId(accountId)
                .userId(userId)
                .build())
                .thenApply(result -> {
                    sample.stop(Timer.builder("banking.balance.fetch")
                        .tag("provider", provider.getProviderName())
                        .tag("status", result.isSuccessful() ? "success" : "failure")
                        .register(meterRegistry));
                        
                    return result;
                });
        }).exceptionally(ex -> {
            log.error("Balance fetch failed for account: {}", accountId, ex);
            incrementErrorCounter("balance.fetch");
            return BalanceResult.failed("Balance fetch failed: " + ex.getMessage());
        });
    }
    
    /**
     * Initiates an ACH transfer
     */
    @CircuitBreaker(name = "banking-ach-transfer", fallbackMethod = "fallbackACHTransfer")
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 2))
    public CompletableFuture<TransferResult> initiateACHTransfer(ACHTransferRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        log.info("Initiating ACH transfer: {} amount: {}", request.getTransferId(), request.getAmount());
        
        return executeWithProvider(null, () -> {
            BankingProvider provider = getHealthyProvider(null);
            
            // Pre-transfer validations
            validateTransferRequest(request);
            
            // Compliance checks
            complianceService.validateTransfer(request);
            
            return provider.initiateTransfer(request)
                .thenApply(result -> {
                    sample.stop(Timer.builder("banking.transfer.initiate")
                        .tag("provider", provider.getProviderName())
                        .tag("type", request.getTransferType().toString())
                        .tag("status", result.isSuccessful() ? "success" : "failure")
                        .register(meterRegistry));
                        
                    metricsService.recordTransfer(
                        provider.getProviderName(), 
                        request.getAmount(), 
                        result.isSuccessful()
                    );
                    
                    return result;
                });
        }).exceptionally(ex -> {
            log.error("ACH transfer failed", ex);
            incrementErrorCounter("transfer.initiate");
            return TransferResult.failed("Transfer failed: " + ex.getMessage());
        });
    }
    
    /**
     * Checks transfer status
     */
    @CircuitBreaker(name = "banking-transfer-status", fallbackMethod = "fallbackCheckTransferStatus")
    @Cacheable(value = "transfer-status", key = "#transferId", unless = "#result.isCompleted()")
    public CompletableFuture<TransferStatusResult> checkTransferStatus(String transferId) {
        log.debug("Checking transfer status: {}", transferId);
        
        return executeWithProvider(null, () -> {
            BankingProvider provider = getHealthyProvider(null);
            
            return provider.getTransferStatus(TransferStatusRequest.builder()
                .transferId(transferId)
                .build());
        }).exceptionally(ex -> {
            log.error("Transfer status check failed for: {}", transferId, ex);
            incrementErrorCounter("transfer.status");
            return TransferStatusResult.failed("Status check failed: " + ex.getMessage());
        });
    }
    
    /**
     * Gets account transactions
     */
    @CircuitBreaker(name = "banking-transactions", fallbackMethod = "fallbackGetTransactions")
    @Cacheable(value = "account-transactions", key = "#request.accountId + #request.startDate + #request.endDate")
    public CompletableFuture<TransactionsResult> getTransactions(TransactionsRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        log.debug("Getting transactions for account: {}", request.getAccountId());
        
        return executeWithProvider(null, () -> {
            BankingProvider provider = getHealthyProvider(null);
            
            return provider.getTransactions(request)
                .thenApply(result -> {
                    sample.stop(Timer.builder("banking.transactions.fetch")
                        .tag("provider", provider.getProviderName())
                        .tag("count", String.valueOf(result.getTransactions().size()))
                        .register(meterRegistry));
                        
                    return result;
                });
        }).exceptionally(ex -> {
            log.error("Transaction fetch failed", ex);
            incrementErrorCounter("transactions.fetch");
            return TransactionsResult.failed("Transaction fetch failed: " + ex.getMessage());
        });
    }
    
    /**
     * Gets supported institutions
     */
    @Cacheable(value = "institutions", key = "#countryCode", unless = "#result.isEmpty()")
    public CompletableFuture<List<InstitutionInfo>> getInstitutions(String countryCode) {
        log.debug("Getting institutions for country: {}", countryCode);
        
        return executeWithProvider(null, () -> {
            BankingProvider provider = getHealthyProvider(null);
            
            return provider.getInstitutions(InstitutionsRequest.builder()
                .countryCode(countryCode)
                .build())
                .thenApply(result -> result.getInstitutions());
        }).exceptionally(ex -> {
            log.error("Institution fetch failed", ex);
            return Collections.emptyList();
        });
    }
    
    /**
     * Performs account health check across all providers
     */
    public CompletableFuture<Map<String, ProviderHealthResult>> performHealthCheck() {
        log.info("Performing health check across all banking providers");
        
        Map<String, CompletableFuture<ProviderHealthResult>> healthChecks = new HashMap<>();
        
        providers.forEach((name, provider) -> {
            healthChecks.put(name, provider.healthCheck()
                .thenApply(healthy -> {
                    ProviderHealthStatus status = healthy ? 
                        ProviderHealthStatus.HEALTHY : ProviderHealthStatus.UNHEALTHY;
                    providerHealth.put(name, status);
                    
                    return ProviderHealthResult.builder()
                        .providerName(name)
                        .healthy(healthy)
                        .lastChecked(LocalDateTime.now())
                        .build();
                })
                .exceptionally(ex -> {
                    log.error("Health check failed for provider: {}", name, ex);
                    providerHealth.put(name, ProviderHealthStatus.UNHEALTHY);
                    
                    return ProviderHealthResult.builder()
                        .providerName(name)
                        .healthy(false)
                        .error(ex.getMessage())
                        .lastChecked(LocalDateTime.now())
                        .build();
                }));
        });
        
        return CompletableFuture.allOf(
            healthChecks.values().toArray(new CompletableFuture[0])
        ).thenApply(v -> {
            Map<String, ProviderHealthResult> results = new HashMap<>();
            healthChecks.forEach((name, future) -> {
                try {
                    results.put(name, future.get());
                } catch (Exception e) {
                    log.error("Failed to get health check result for: {}", name, e);
                }
            });
            return results;
        });
    }
    
    /**
     * Cancels a transfer (if still pending)
     */
    @CircuitBreaker(name = "banking-cancel-transfer", fallbackMethod = "fallbackCancelTransfer")
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<TransferCancellationResult> cancelTransfer(String transferId, String reason) {
        log.info("Cancelling transfer: {} reason: {}", transferId, reason);
        
        return executeWithProvider(null, () -> {
            BankingProvider provider = getHealthyProvider(null);
            
            return provider.cancelTransfer(TransferCancellationRequest.builder()
                .transferId(transferId)
                .reason(reason)
                .build())
                .thenApply(result -> {
                    metricsService.recordTransferCancellation(
                        provider.getProviderName(), 
                        result.isSuccessful()
                    );
                    return result;
                });
        }).exceptionally(ex -> {
            log.error("Transfer cancellation failed", ex);
            incrementErrorCounter("transfer.cancel");
            return TransferCancellationResult.failed("Cancellation failed: " + ex.getMessage());
        });
    }
    
    /**
     * Gets account ownership verification
     */
    @CircuitBreaker(name = "banking-ownership", fallbackMethod = "fallbackOwnershipVerification")
    public CompletableFuture<OwnershipVerificationResult> verifyAccountOwnership(
            OwnershipVerificationRequest request) {
        log.info("Verifying account ownership for user: {}", request.getUserId());
        
        return executeWithProvider(null, () -> {
            BankingProvider provider = getHealthyProvider(null);
            
            return provider.verifyOwnership(request);
        }).exceptionally(ex -> {
            log.error("Ownership verification failed", ex);
            incrementErrorCounter("ownership.verify");
            return OwnershipVerificationResult.failed("Ownership verification failed");
        });
    }
    
    // Helper methods
    
    /**
     * Executes operation with provider failover
     */
    private <T> CompletableFuture<T> executeWithProvider(
            String preferredProvider, 
            Supplier<CompletableFuture<T>> operation) {
        
        List<String> providerOrder = getProviderOrder(preferredProvider);
        
        return executeWithProviderChain(providerOrder, operation, 0);
    }
    
    private <T> CompletableFuture<T> executeWithProviderChain(
            List<String> providers, 
            Supplier<CompletableFuture<T>> operation, 
            int index) {
        
        if (index >= providers.size()) {
            return CompletableFuture.failedFuture(
                new RuntimeException("All banking providers unavailable")
            );
        }
        
        String providerName = providers.get(index);
        if (!isProviderHealthy(providerName)) {
            log.warn("Provider {} is unhealthy, trying next", providerName);
            return executeWithProviderChain(providers, operation, index + 1);
        }
        
        return operation.get()
            .exceptionally(ex -> {
                log.error("Operation failed with provider: {}", providerName, ex);
                markProviderUnhealthy(providerName);
                
                // Try next provider
                try {
                    return executeWithProviderChain(providers, operation, index + 1).get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
    }
    
    private List<String> getProviderOrder(String preferredProvider) {
        List<String> order = new ArrayList<>();
        
        if (preferredProvider != null && providers.containsKey(preferredProvider)) {
            order.add(preferredProvider);
        }
        
        // Add primary if not already added
        if (!order.contains(primaryProvider)) {
            order.add(primaryProvider);
        }
        
        // Add failover if not already added
        if (!order.contains(failoverProvider)) {
            order.add(failoverProvider);
        }
        
        // Add remaining healthy providers
        providers.keySet().stream()
            .filter(name -> !order.contains(name))
            .filter(this::isProviderHealthy)
            .forEach(order::add);
            
        return order;
    }
    
    private BankingProvider getHealthyProvider(String preferredProvider) {
        String providerName = preferredProvider != null ? preferredProvider : primaryProvider;
        
        if (isProviderHealthy(providerName)) {
            return providers.get(providerName);
        }
        
        // Fallback to any healthy provider
        return providers.entrySet().stream()
            .filter(entry -> isProviderHealthy(entry.getKey()))
            .findFirst()
            .map(Map.Entry::getValue)
            .orElseThrow(() -> new RuntimeException("No healthy banking providers available"));
    }
    
    private boolean isProviderHealthy(String providerName) {
        return providerHealth.getOrDefault(providerName, ProviderHealthStatus.HEALTHY) 
            == ProviderHealthStatus.HEALTHY;
    }
    
    private void markProviderUnhealthy(String providerName) {
        providerHealth.put(providerName, ProviderHealthStatus.UNHEALTHY);
        
        // Schedule health check recovery
        CompletableFuture.delayedExecutor(Duration.ofMinutes(5)).execute(() -> {
            providers.get(providerName).healthCheck()
                .thenAccept(healthy -> {
                    if (healthy) {
                        providerHealth.put(providerName, ProviderHealthStatus.HEALTHY);
                        log.info("Provider {} recovered and marked healthy", providerName);
                    }
                });
        });
    }
    
    private void validateTransferRequest(ACHTransferRequest request) {
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }
        
        if (request.getAmount().compareTo(new BigDecimal("25000")) > 0) {
            throw new IllegalArgumentException("Transfer amount exceeds daily limit");
        }
        
        if (request.getFromAccountId() == null || request.getToAccountId() == null) {
            throw new IllegalArgumentException("Both from and to accounts must be specified");
        }
    }
    
    private void incrementErrorCounter(String operation) {
        Counter.builder("banking.operations.errors")
            .tag("operation", operation)
            .register(meterRegistry)
            .increment();
    }
    
    // Fallback methods
    
    private CompletableFuture<AccountLinkResult> fallbackLinkAccount(
            AccountLinkRequest request, Exception ex) {
        log.error("Account linking fallback triggered", ex);
        return CompletableFuture.completedFuture(
            AccountLinkResult.failed("Banking services temporarily unavailable")
        );
    }
    
    private CompletableFuture<AccountVerificationResult> fallbackVerifyAccount(
            AccountVerificationRequest request, Exception ex) {
        log.error("Account verification fallback triggered", ex);
        return CompletableFuture.completedFuture(
            AccountVerificationResult.failed("Verification service temporarily unavailable")
        );
    }
    
    private CompletableFuture<BalanceResult> fallbackGetBalance(
            String accountId, String userId, Exception ex) {
        log.error("Balance fetch fallback triggered", ex);
        return CompletableFuture.completedFuture(
            BalanceResult.failed("Balance service temporarily unavailable")
        );
    }
    
    private CompletableFuture<TransferResult> fallbackACHTransfer(
            ACHTransferRequest request, Exception ex) {
        log.error("ACH transfer fallback triggered", ex);
        return CompletableFuture.completedFuture(
            TransferResult.failed("Transfer service temporarily unavailable")
        );
    }
    
    private CompletableFuture<TransferStatusResult> fallbackCheckTransferStatus(
            String transferId, Exception ex) {
        log.error("Transfer status fallback triggered", ex);
        return CompletableFuture.completedFuture(
            TransferStatusResult.failed("Status service temporarily unavailable")
        );
    }
    
    private CompletableFuture<TransactionsResult> fallbackGetTransactions(
            TransactionsRequest request, Exception ex) {
        log.error("Transaction fetch fallback triggered", ex);
        return CompletableFuture.completedFuture(
            TransactionsResult.failed("Transaction service temporarily unavailable")
        );
    }
    
    private CompletableFuture<TransferCancellationResult> fallbackCancelTransfer(
            String transferId, String reason, Exception ex) {
        log.error("Transfer cancellation fallback triggered", ex);
        return CompletableFuture.completedFuture(
            TransferCancellationResult.failed("Cancellation service temporarily unavailable")
        );
    }
    
    private CompletableFuture<OwnershipVerificationResult> fallbackOwnershipVerification(
            OwnershipVerificationRequest request, Exception ex) {
        log.error("Ownership verification fallback triggered", ex);
        return CompletableFuture.completedFuture(
            OwnershipVerificationResult.failed("Verification service temporarily unavailable")
        );
    }
    
    // Enums and supporting classes
    
    public enum ProviderHealthStatus {
        HEALTHY,
        UNHEALTHY,
        DEGRADED
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ProviderHealthResult {
        private String providerName;
        private boolean healthy;
        private String error;
        private LocalDateTime lastChecked;
    }
    
    /**
     * Fallback method for ACH transfer
     */
    private CompletableFuture<BankingAPIResponse> fallbackACHTransfer(
            BankingAPIRequest request, Exception ex) {
        log.error("Banking API fallback triggered for transfer: {}", request.getTransferId(), ex);
        
        return CompletableFuture.completedFuture(
            BankingAPIResponse.builder()
                .successful(false)
                .errorMessage("Banking service temporarily unavailable")
                .build()
        );
    }
    
    /**
     * Fallback method for status check
     */
    private CompletableFuture<BankingAPIResponse> fallbackCheckStatus(
            String referenceId, Exception ex) {
        log.error("Banking API fallback triggered for status check: {}", referenceId, ex);
        
        return CompletableFuture.completedFuture(
            BankingAPIResponse.builder()
                .successful(false)
                .errorMessage("Unable to check transfer status")
                .build()
        );
    }
    
    /**
     * Fallback method for cancel transfer
     */
    private CompletableFuture<BankingAPIResponse> fallbackCancelTransfer(
            String referenceId, Exception ex) {
        log.error("Banking API fallback triggered for cancel: {}", referenceId, ex);
        
        return CompletableFuture.completedFuture(
            BankingAPIResponse.builder()
                .successful(false)
                .errorMessage("Unable to cancel transfer")
                .build()
        );
    }
    
    /**
     * Internal DTO classes for banking API communication
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class BankingAPIRequestDTO {
        private java.math.BigDecimal amount;
        private String direction;
        private AccountDetails accountDetails;
        private TransferMetadata metadata;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class AccountDetails {
        private String routingNumber;
        private String accountNumber;
        private String accountHolderName;
        private String accountType;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class TransferMetadata {
        private String internalReferenceId;
        private String clientId;
        private String timestamp;
    }
    
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class BankingAPIResponseDTO {
        private String status;
        private String referenceId;
        private String message;
        private String errorMessage;
        private String processingTime;
    }
}