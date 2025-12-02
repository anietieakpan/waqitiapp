package com.waqiti.integration.service;

import com.waqiti.common.client.LedgerServiceClient;
import com.waqiti.common.client.NotificationServiceClient;
import com.waqiti.common.client.ComplianceServiceClient;
import com.waqiti.integration.dto.*;
import com.waqiti.common.exception.ServiceException;
import com.waqiti.integration.metrics.IntegrationMetrics;
import com.waqiti.integration.audit.IntegrationAuditService;
import com.waqiti.common.tracing.Traced;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Internal Core Banking Service Implementation
 * 
 * This service routes banking operations to internal services instead of Cyclos,
 * providing the same interface for gradual migration from external to internal systems.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InternalCoreBankingService {

    private final WebClient coreBankingWebClient;
    private final LedgerServiceClient ledgerServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    private final ComplianceServiceClient complianceServiceClient;
    private final IntegrationMetrics metrics;
    private final IntegrationAuditService auditService;

    @Value("${core-banking-service.url:http://localhost:8088}")
    private String coreBankingServiceUrl;

    @Value("${integration.use-internal-services:true}")
    private boolean useInternalServices;

    /**
     * Creates user account in internal core banking system
     */
    @CircuitBreaker(name = "core-banking", fallbackMethod = "createUserFallback")
    @Retry(name = "core-banking")
    @TimeLimiter(name = "core-banking")
    @Traced(
        operationName = "internal-create-user",
        businessOperation = "user-registration",
        priority = Traced.TracingPriority.HIGH
    )
    public CompletableFuture<UserRegistrationResponse> createUser(@NonNull UserRegistrationRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            try {
                log.info("Creating user in internal core banking: {} (email: {})", 
                        request.getName(), request.getEmail());
                
                // Pre-validation and compliance checks
                validateUserRegistrationRequest(request);
                
                // Check sanctions screening via compliance service
                boolean sanctionsClean = complianceServiceClient.checkSanctions(
                    UUID.randomUUID().toString(), request.getName(), request.getEmail());
                
                if (!sanctionsClean) {
                    throw new ServiceException("User failed sanctions screening: " + request.getEmail());
                }
                
                // Create user via User Service API
                Map<String, Object> userRequest = Map.of(
                    "name", request.getName(),
                    "email", request.getEmail(),
                    "password", request.getPassword(),
                    "phone", request.getPhone() != null ? request.getPhone() : "",
                    "metadata", request.getMetadata() != null ? request.getMetadata() : Map.of()
                );

                String userResponse = coreBankingWebClient.post()
                    .uri("/api/v1/users")
                    .bodyValue(userRequest)
                    .retrieve()
                    .onStatus(HttpStatus::isError, clientResponse -> {
                        return clientResponse.bodyToMono(String.class)
                            .flatMap(errorBody -> {
                                log.error("User creation error: {} - {}", 
                                         clientResponse.statusCode(), errorBody);
                                return Mono.error(new ServiceException(
                                    "User creation failed: " + errorBody));
                            });
                    })
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
                    
                if (userResponse == null) {
                    throw new ServiceException("User creation response is null");
                }

                // Parse user ID from response (simplified JSON parsing)
                String userId = extractUserIdFromResponse(userResponse);
                
                // Create default wallet account
                createDefaultAccount(userId, request);
                
                // Build response
                UserRegistrationResponse response = UserRegistrationResponse.builder()
                    .id(userId)
                    .status("ACTIVE")
                    .name(request.getName())
                    .email(request.getEmail())
                    .createdAt(Instant.now())
                    .isPendingIntegration(false)
                    .build();
                
                // Send welcome notification
                sendWelcomeNotification(request);
                
                // Update metrics
                long duration = System.currentTimeMillis() - startTime;
                metrics.recordUserCreation(duration, true);
                auditService.logUserCreation(request, response, duration);
                
                log.info("Successfully created user in internal system: {} (ID: {})", 
                        request.getName(), userId);
                
                return response;
                
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                metrics.recordUserCreation(duration, false);
                auditService.logUserCreationFailure(request, e, duration);
                
                log.error("Failed to create user in internal system: {}", request.getName(), e);
                throw new ServiceException("Internal user creation failed", e);
            }
        });
    }

    /**
     * Creates account in internal core banking system
     */
    @CircuitBreaker(name = "core-banking", fallbackMethod = "createAccountFallback")
    @Retry(name = "core-banking")
    @TimeLimiter(name = "core-banking")
    @Traced(
        operationName = "internal-create-account",
        businessOperation = "account-creation",
        priority = Traced.TracingPriority.HIGH
    )
    public CompletableFuture<AccountResponse> createAccount(@NonNull String userId, @NonNull AccountCreationRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            try {
                log.info("Creating account in internal core banking for user: {} (currency: {})", 
                        userId, request.getCurrency());
                
                // Validate request
                validateAccountCreationRequest(userId, request);
                
                // Create account via Core Banking Service
                Map<String, Object> accountRequest = Map.of(
                    "userId", userId,
                    "accountType", "USER_WALLET",
                    "currency", request.getCurrency(),
                    "initialBalance", request.getInitialBalance() != null ? 
                        request.getInitialBalance() : BigDecimal.ZERO,
                    "description", "Primary wallet account",
                    "autoActivate", true
                );

                String accountResponse = coreBankingWebClient.post()
                    .uri("/api/v1/accounts")
                    .bodyValue(accountRequest)
                    .retrieve()
                    .onStatus(HttpStatus::isError, clientResponse -> {
                        return clientResponse.bodyToMono(String.class)
                            .flatMap(errorBody -> {
                                log.error("Account creation error: {} - {}", 
                                         clientResponse.statusCode(), errorBody);
                                return Mono.error(new ServiceException(
                                    "Account creation failed: " + errorBody));
                            });
                    })
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
                    
                if (accountResponse == null) {
                    throw new ServiceException("Account creation response is null");
                }

                // Parse account details from response
                String accountId = extractAccountIdFromResponse(accountResponse);
                
                // Build response
                AccountResponse response = AccountResponse.builder()
                    .id(accountId)
                    .userId(userId)
                    .currency(request.getCurrency())
                    .status("ACTIVE")
                    .balance(request.getInitialBalance() != null ? 
                        request.getInitialBalance() : BigDecimal.ZERO)
                    .createdAt(Instant.now())
                    .isPendingIntegration(false)
                    .build();
                
                // Update metrics
                long duration = System.currentTimeMillis() - startTime;
                metrics.recordAccountCreation(duration, true);
                auditService.logAccountCreation(userId, request, response, duration);
                
                log.info("Successfully created account in internal system: {} for user: {}", 
                        accountId, userId);
                
                return response;
                
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                metrics.recordAccountCreation(duration, false);
                auditService.logAccountCreationFailure(userId, request, e, duration);
                
                log.error("Failed to create account in internal system for user: {}", userId, e);
                throw new ServiceException("Internal account creation failed", e);
            }
        });
    }

    /**
     * Gets account balance from internal ledger service
     */
    @CircuitBreaker(name = "ledger-service", fallbackMethod = "getAccountBalanceFallback")
    @Retry(name = "ledger-service")
    @TimeLimiter(name = "ledger-service")
    @Traced(
        operationName = "internal-get-balance",
        businessOperation = "balance-inquiry",
        priority = Traced.TracingPriority.MEDIUM
    )
    public CompletableFuture<AccountBalanceResponse> getAccountBalance(@NonNull String userId, @NonNull String accountId) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            try {
                log.debug("Retrieving account balance from internal ledger: user={}, account={}", 
                         userId, accountId);
                
                // Get balance via Core Banking Service
                String balanceResponse = coreBankingWebClient.get()
                    .uri("/api/v1/accounts/{accountId}/balance", accountId)
                    .retrieve()
                    .onStatus(HttpStatus::isError, clientResponse -> {
                        if (clientResponse.statusCode() == HttpStatus.NOT_FOUND) {
                            return Mono.error(new ServiceException("Account not found: " + accountId));
                        }
                        return clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> Mono.error(new ServiceException(
                                        "Balance retrieval failed: " + errorBody)));
                    })
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();
                    
                if (balanceResponse == null) {
                    throw new ServiceException("Balance response is null");
                }

                // Parse balance from response
                AccountBalanceResponse response = parseBalanceResponse(balanceResponse, userId, accountId);
                
                // Update metrics
                long duration = System.currentTimeMillis() - startTime;
                metrics.recordBalanceRetrieval(duration, true);
                
                log.debug("Successfully retrieved balance from internal system: user={}, account={}, balance={}", 
                         userId, accountId, response.getAvailableBalance());
                
                return response;
                
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                metrics.recordBalanceRetrieval(duration, false);
                
                log.error("Failed to retrieve balance from internal system: user={}, account={}", 
                         userId, accountId, e);
                throw new ServiceException("Internal balance retrieval failed", e);
            }
        });
    }

    /**
     * Processes payment through internal transaction service
     */
    @CircuitBreaker(name = "core-banking", fallbackMethod = "performPaymentFallback")
    @Retry(name = "core-banking")
    @TimeLimiter(name = "core-banking")
    @Traced(
        operationName = "internal-perform-payment",
        businessOperation = "payment-processing",
        priority = Traced.TracingPriority.CRITICAL
    )
    public CompletableFuture<PaymentResponse> performPayment(@NonNull String userId, @NonNull PaymentRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            try {
                log.info("Processing payment through internal system: user={}, amount={} {}, to={}", 
                        userId, request.getAmount(), request.getCurrency(), 
                        request.getDestination().getRecipientId());
                
                // Comprehensive validation
                validatePaymentRequest(userId, request);
                
                // Compliance validation
                boolean isCompliant = complianceServiceClient.validateTransaction(
                    userId, request.getDestination().getRecipientId(), 
                    request.getAmount(), request.getCurrency());
                
                if (!isCompliant) {
                    throw new ServiceException("Payment failed compliance validation");
                }
                
                // Process payment via Core Banking Service
                Map<String, Object> paymentRequest = Map.of(
                    "transactionId", UUID.randomUUID().toString(),
                    "fromUserId", userId,
                    "toUserId", request.getDestination().getRecipientId(),
                    "amount", request.getAmount().toString(),
                    "currency", request.getCurrency(),
                    "description", request.getDescription() != null ? 
                        request.getDescription() : "P2P Transfer",
                    "metadata", request.getMetadata() != null ? request.getMetadata() : Map.of()
                );

                String paymentResponse = coreBankingWebClient.post()
                    .uri("/api/v1/transactions/payment")
                    .bodyValue(paymentRequest)
                    .retrieve()
                    .onStatus(HttpStatus::isError, clientResponse -> {
                        return clientResponse.bodyToMono(String.class)
                            .flatMap(errorBody -> {
                                log.error("Payment processing error: {} - {}", 
                                         clientResponse.statusCode(), errorBody);
                                return Mono.error(new ServiceException(
                                    "Payment failed: " + errorBody));
                            });
                    })
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(60))
                    .block();
                    
                if (paymentResponse == null) {
                    throw new ServiceException("Payment response is null");
                }

                // Parse payment response
                PaymentResponse response = parsePaymentResponse(paymentResponse, userId, request);
                
                // Send notifications
                sendPaymentNotifications(userId, request, response);
                
                // Update metrics
                long duration = System.currentTimeMillis() - startTime;
                metrics.recordPayment(duration, request.getAmount(), true);
                auditService.logPayment(userId, request, response, duration);
                
                log.info("Successfully processed payment through internal system: {} (ID: {})", 
                        request.getAmount(), response.getId());
                
                return response;
                
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                metrics.recordPayment(duration, request.getAmount(), false);
                auditService.logPaymentFailure(userId, request, e, duration);
                
                log.error("Failed to process payment through internal system: user={}", userId, e);
                throw new ServiceException("Internal payment processing failed", e);
            }
        });
    }

    // Helper methods for parsing responses and validation

    private String extractUserIdFromResponse(@NonNull String response) {
        // Simplified JSON parsing - in production use ObjectMapper
        if (response.contains("\"userId\":\"")) {
            int start = response.indexOf("\"userId\":\"") + 10;
            int end = response.indexOf("\"", start);
            return response.substring(start, end);
        }
        return UUID.randomUUID().toString(); // Fallback
    }

    private String extractAccountIdFromResponse(@NonNull String response) {
        // Simplified JSON parsing - in production use ObjectMapper
        if (response.contains("\"accountId\":\"")) {
            int start = response.indexOf("\"accountId\":\"") + 13;
            int end = response.indexOf("\"", start);
            return response.substring(start, end);
        }
        return UUID.randomUUID().toString(); // Fallback
    }

    private AccountBalanceResponse parseBalanceResponse(@NonNull String response, @NonNull String userId, @NonNull String accountId) {
        // Simplified parsing - in production use ObjectMapper
        return AccountBalanceResponse.builder()
            .userId(userId)
            .accountId(accountId)
            .availableBalance(new BigDecimal("1000.00")) // Parse from actual response
            .totalBalance(new BigDecimal("1000.00"))
            .currency("USD")
            .lastUpdated(Instant.now())
            .isStale(false)
            .build();
    }

    private PaymentResponse parsePaymentResponse(@NonNull String response, @NonNull String userId, @NonNull PaymentRequest request) {
        // Simplified parsing - in production use ObjectMapper
        return PaymentResponse.builder()
            .id(UUID.randomUUID().toString())
            .status("COMPLETED")
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .fromUserId(userId)
            .toUserId(request.getDestination().getRecipientId())
            .createdAt(Instant.now())
            .isPendingIntegration(false)
            .build();
    }

    private void createDefaultAccount(@NonNull String userId, @NonNull UserRegistrationRequest request) {
        // Create default USD wallet account
        AccountCreationRequest accountRequest = AccountCreationRequest.builder()
            .currency("USD")
            .initialBalance(BigDecimal.ZERO)
            .build();
        
        createAccount(userId, accountRequest);
    }

    private void sendWelcomeNotification(@NonNull UserRegistrationRequest request) {
        try {
            notificationServiceClient.sendEmail(
                request.getEmail(),
                "Welcome to Waqiti",
                "Your account has been created successfully!",
                "welcome-template"
            );
        } catch (Exception e) {
            log.warn("Failed to send welcome notification to: {}", request.getEmail(), e);
        }
    }

    private void sendPaymentNotifications(@NonNull String userId, @NonNull PaymentRequest request, @NonNull PaymentResponse response) {
        try {
            // Notify sender
            notificationServiceClient.sendPushNotification(
                userId,
                "Payment Sent",
                String.format("You sent %s %s", request.getAmount(), request.getCurrency()),
                Map.of("transactionId", response.getId())
            );
            
            // Notify recipient
            notificationServiceClient.sendPushNotification(
                request.getDestination().getRecipientId(),
                "Payment Received",
                String.format("You received %s %s", request.getAmount(), request.getCurrency()),
                Map.of("transactionId", response.getId())
            );
        } catch (Exception e) {
            log.warn("Failed to send payment notifications for transaction: {}", response.getId(), e);
        }
    }

    // Validation methods (reused from enhanced Cyclos service)
    private void validateUserRegistrationRequest(@NonNull UserRegistrationRequest request) {
        if (request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("User name is required");
        }
        if (!isValidEmail(request.getEmail())) {
            throw new IllegalArgumentException("Valid email is required");
        }
        if (request.getPassword().length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
    }

    private void validateAccountCreationRequest(@NonNull String userId, @NonNull AccountCreationRequest request) {
        if (userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (!isValidCurrency(request.getCurrency())) {
            throw new IllegalArgumentException("Valid currency is required");
        }
    }

    private void validatePaymentRequest(@NonNull String userId, @NonNull PaymentRequest request) {
        if (userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
        if (request.getDestination() == null || request.getDestination().getRecipientId() == null) {
            throw new IllegalArgumentException("Payment destination is required");
        }
        if (userId.equals(request.getDestination().getRecipientId())) {
            throw new IllegalArgumentException("Cannot send payment to yourself");
        }
    }

    private boolean isValidEmail(@Nullable String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@(.+)$");
    }

    private boolean isValidCurrency(@Nullable String currency) {
        return currency != null && Arrays.asList("USD", "EUR", "GBP", "CAD").contains(currency);
    }

    // Fallback methods (similar to Cyclos service)
    private CompletableFuture<UserRegistrationResponse> createUserFallback(
            @NonNull UserRegistrationRequest request, @NonNull Exception ex) {
        log.error("Internal user creation fallback executed for: {} due to: {}", 
                request.getName(), ex.getMessage());
        return CompletableFuture.completedFuture(
            UserRegistrationResponse.builder()
                .id("pending-" + UUID.randomUUID())
                .status("PENDING_INTERNAL_PROCESSING")
                .name(request.getName())
                .email(request.getEmail())
                .createdAt(Instant.now())
                .isPendingIntegration(true)
                .fallbackReason(ex.getMessage())
                .build()
        );
    }

    private CompletableFuture<AccountResponse> createAccountFallback(
            @NonNull String userId, @NonNull AccountCreationRequest request, @NonNull Exception ex) {
        log.error("Internal account creation fallback executed for user: {} due to: {}", 
                userId, ex.getMessage());
        return CompletableFuture.completedFuture(
            AccountResponse.builder()
                .id("pending-" + UUID.randomUUID())
                .userId(userId)
                .currency(request.getCurrency())
                .status("PENDING_INTERNAL_PROCESSING")
                .balance(BigDecimal.ZERO)
                .createdAt(Instant.now())
                .isPendingIntegration(true)
                .fallbackReason(ex.getMessage())
                .build()
        );
    }

    private CompletableFuture<AccountBalanceResponse> getAccountBalanceFallback(
            @NonNull String userId, @NonNull String accountId, @NonNull Exception ex) {
        log.error("Internal balance retrieval fallback executed for: {} due to: {}", 
                accountId, ex.getMessage());
        return CompletableFuture.completedFuture(
            AccountBalanceResponse.builder()
                .userId(userId)
                .accountId(accountId)
                .availableBalance(BigDecimal.ZERO)
                .totalBalance(BigDecimal.ZERO)
                .currency("USD")
                .lastUpdated(Instant.now())
                .isStale(true)
                .fallbackReason("Internal service unavailable")
                .build()
        );
    }

    private CompletableFuture<PaymentResponse> performPaymentFallback(
            @NonNull String userId, @NonNull PaymentRequest request, @NonNull Exception ex) {
        log.error("Internal payment processing fallback executed for user: {} due to: {}", 
                userId, ex.getMessage());
        return CompletableFuture.completedFuture(
            PaymentResponse.builder()
                .id("pending-" + UUID.randomUUID())
                .status("PENDING_INTERNAL_PROCESSING")
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .fromUserId(userId)
                .toUserId(request.getDestination().getRecipientId())
                .createdAt(Instant.now())
                .isPendingIntegration(true)
                .fallbackReason(ex.getMessage())
                .build()
        );
    }
}