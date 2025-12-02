package com.waqiti.user.service;

import com.waqiti.user.domain.User;
import com.waqiti.user.domain.UserSession;
import com.waqiti.user.domain.PaymentMethod;
import com.waqiti.user.domain.ApiKey;
import com.waqiti.user.repository.UserRepository;
import com.waqiti.user.repository.UserSessionRepository;
import com.waqiti.user.repository.PaymentMethodRepository;
import com.waqiti.user.repository.ApiKeyRepository;
import com.waqiti.user.repository.AccessTokenRepository;
import com.waqiti.common.client.SecurityServiceClient;
import com.waqiti.common.metrics.service.MetricsService;
import com.waqiti.common.cache.CacheService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Comprehensive account management service for user operations.
 * Handles account lifecycle, security, payments, subscriptions, and compliance.
 * 
 * Critical for account closure, security enforcement, and data management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserRepository userRepository;
    private final UserSessionRepository sessionRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final AccessTokenRepository accessTokenRepository;

    private final SecurityServiceClient securityServiceClient;
    private final NotificationService notificationService;
    private final MetricsService metricsService;
    private final CacheService cacheService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final WebClient.Builder webClientBuilder;
    
    @Value("${payment-service.url:http://localhost:8082}")
    private String paymentServiceUrl;
    
    @Value("${recurring-payment-service.url:http://localhost:8087}")
    private String recurringPaymentServiceUrl;

    @Value("${rewards-service.url:http://localhost:8088}")
    private String rewardsServiceUrl;

    @Value("${dispute-service.url:http://localhost:8089}")
    private String disputeServiceUrl;
    
    private static final String BLOCKED_USERS_CACHE = "blocked_users";
    private static final String USER_TOKENS_CACHE = "user_tokens:";
    private static final String USER_SESSIONS_CACHE = "user_sessions:";
    
    private WebClient recurringPaymentServiceClient;
    private WebClient rewardsServiceClient;
    private WebClient disputeServiceClient;

    // Security and Access Management

    /**
     * Block all access for a user account immediately
     */
    @Transactional
    @CircuitBreaker(name = "account-service")
    @Retry(name = "account-service")
    public void blockAllAccess(String userId) {
        log.warn("Blocking all access for user: {}", userId);
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Update user status in database
            UUID userUuid = UUID.fromString(userId);
            User user = userRepository.findById(userUuid)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
            
            user.setBlocked(true);
            user.setBlockedAt(LocalDateTime.now());
            user.setBlockReason("ACCOUNT_CLOSURE_SECURITY");
            user.setActive(false);
            
            userRepository.save(user);
            
            // Add to blocked users cache
            redisTemplate.opsForSet().add(BLOCKED_USERS_CACHE, userId);
            redisTemplate.expire(BLOCKED_USERS_CACHE, 24, TimeUnit.HOURS);
            
            // Terminate all active sessions
            terminateAllSessions(userId);
            
            // Revoke all tokens
            revokeAllTokens(userId);
            
            // Invalidate all API keys
            invalidateAllApiKeys(userId);
            
            // Clear user caches
            clearUserCaches(userId);
            
            // Notify security systems
            notifySecuritySystems(userId, "ACCESS_BLOCKED");
            
            // Record metrics
            metricsService.incrementCounter("account.access.blocked",
                Map.of("reason", "ACCOUNT_CLOSURE", "userId", userId));
            
            metricsService.recordTimer("account.block.processing_time",
                System.currentTimeMillis() - startTime,
                Map.of("operation", "BLOCK_ALL_ACCESS"));
            
            log.warn("Successfully blocked all access for user: {}", userId);
            
        } catch (Exception e) {
            log.error("Failed to block access for user {}: {}", userId, e.getMessage(), e);
            metricsService.recordFailedOperation("blockAllAccess", e.getMessage());
            throw new RuntimeException("Failed to block user access", e);
        }
    }

    /**
     * Get count of pending transactions for user
     */
    @CircuitBreaker(name = "payment-service", fallbackMethod = "getPendingTransactionCountFallback")
    @Retry(name = "payment-service")
    public int getPendingTransactionCount(String userId) {
        log.debug("Getting pending transaction count for user: {}", userId);
        
        try {
            String response = getPaymentServiceClient().get()
                .uri("/api/v1/transactions/pending/count/{userId}", userId)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5))
                .block();
            
            return Integer.parseInt(response);
            
        } catch (Exception e) {
            log.error("Failed to get pending transaction count for {}: {}", userId, e.getMessage());
            return getPendingTransactionCountFallback(userId, e);
        }
    }
    
    private int getPendingTransactionCountFallback(String userId, Exception e) {
        log.warn("Using fallback for pending transaction count: {}", userId);
        // Conservative fallback - assume there might be pending transactions
        return 1;
    }

    /**
     * Get count of active disputes for user
     */
    @CircuitBreaker(name = "dispute-service", fallbackMethod = "getActiveDisputeCountFallback")
    @Retry(name = "dispute-service")
    public int getActiveDisputeCount(String userId) {
        log.debug("Getting active dispute count for user: {}", userId);
        
        try {
            String response = getDisputeServiceClient().get()
                .uri("/api/v1/disputes/active/count/{userId}", userId)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5))
                .block();
            
            return Integer.parseInt(response);
            
        } catch (Exception e) {
            log.error("Failed to get active dispute count for {}: {}", userId, e.getMessage());
            return getActiveDisputeCountFallback(userId, e);
        }
    }
    
    private int getActiveDisputeCountFallback(String userId, Exception e) {
        log.warn("Using fallback for active dispute count: {}", userId);
        // Check local dispute repository as fallback
        return disputeRepository.countActiveDisputesByUserId(userId);
    }

    // Subscription Management

    /**
     * Get active subscriptions for user
     */
    @CircuitBreaker(name = "subscription-service", fallbackMethod = "getActiveSubscriptionsFallback")
    @Retry(name = "subscription-service")
    public List<String> getActiveSubscriptions(String userId) {
        log.debug("Getting active subscriptions for user: {}", userId);
        
        try {
            List<String> subscriptions = getSubscriptionServiceClient().get()
                .uri("/api/v1/subscriptions/active/{userId}", userId)
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(Duration.ofSeconds(10))
                .collectList()
                .block();
            
            return subscriptions != null ? subscriptions : new ArrayList<>();
            
        } catch (Exception e) {
            log.error("Failed to get active subscriptions for {}: {}", userId, e.getMessage());
            return getActiveSubscriptionsFallback(userId, e);
        }
    }
    
    private List<String> getActiveSubscriptionsFallback(String userId, Exception e) {
        log.warn("Using fallback for active subscriptions: {}", userId);
        return subscriptionRepository.findActiveSubscriptionIdsByUserId(userId);
    }

    /**
     * Cancel subscription for user
     */
    @Transactional
    @CircuitBreaker(name = "subscription-service", fallbackMethod = "cancelSubscriptionFallback")
    @Retry(name = "subscription-service")
    public boolean cancelSubscription(String userId, String subscriptionId) {
        log.info("Cancelling subscription {} for user: {}", subscriptionId, userId);
        
        try {
            Map<String, Object> request = Map.of(
                "userId", userId,
                "subscriptionId", subscriptionId,
                "reason", "ACCOUNT_CLOSURE",
                "immediateCancel", true
            );
            
            Boolean result = getSubscriptionServiceClient().post()
                .uri("/api/v1/subscriptions/cancel")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Boolean.class)
                .timeout(Duration.ofSeconds(15))
                .block();
            
            boolean success = Boolean.TRUE.equals(result);
            
            if (success) {
                metricsService.incrementCounter("subscription.cancelled",
                    Map.of("reason", "ACCOUNT_CLOSURE", "userId", userId));
            }
            
            return success;
            
        } catch (Exception e) {
            log.error("Failed to cancel subscription {} for {}: {}", subscriptionId, userId, e.getMessage());
            return cancelSubscriptionFallback(userId, subscriptionId, e);
        }
    }
    
    private boolean cancelSubscriptionFallback(String userId, String subscriptionId, Exception e) {
        log.warn("Using fallback for subscription cancellation: {} {}", userId, subscriptionId);
        // Mark for manual processing
        return false;
    }

    /**
     * Get linked accounts for user
     */
    public List<String> getLinkedAccounts(String userId) {
        log.debug("Getting linked accounts for user: {}", userId);
        
        try {
            UUID userUuid = UUID.fromString(userId);
            User user = userRepository.findById(userUuid)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
            
            // Get linked payment accounts, bank accounts, etc.
            List<String> linkedAccounts = new ArrayList<>();
            
            // Add payment method accounts
            List<PaymentMethod> paymentMethods = paymentMethodRepository.findActiveByUserId(userId);
            linkedAccounts.addAll(paymentMethods.stream()
                .map(PaymentMethod::getExternalAccountId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));
            
            // Add other linked accounts from user profile
            if (user.getLinkedAccounts() != null) {
                linkedAccounts.addAll(user.getLinkedAccounts());
            }
            
            return linkedAccounts.stream().distinct().collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("Failed to get linked accounts for {}: {}", userId, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Get recurring payment count for user
     */
    @CircuitBreaker(name = "payment-service", fallbackMethod = "getRecurringPaymentCountFallback")
    @Retry(name = "payment-service")
    public int getRecurringPaymentCount(String userId) {
        log.debug("Getting recurring payment count for user: {}", userId);
        
        try {
            String response = getPaymentServiceClient().get()
                .uri("/api/v1/recurring-payments/count/{userId}", userId)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5))
                .block();
            
            return Integer.parseInt(response);
            
        } catch (Exception e) {
            log.error("Failed to get recurring payment count for {}: {}", userId, e.getMessage());
            return getRecurringPaymentCountFallback(userId, e);
        }
    }
    
    private int getRecurringPaymentCountFallback(String userId, Exception e) {
        log.warn("Using fallback for recurring payment count: {}", userId);
        return recurringPaymentRepository.countActiveByUserId(userId);
    }

    /**
     * Cancel all recurring payments for user
     */
    @Transactional
    @CircuitBreaker(name = "payment-service", fallbackMethod = "cancelAllRecurringPaymentsFallback")
    @Retry(name = "payment-service")
    public int cancelAllRecurringPayments(String userId) {
        log.info("Cancelling all recurring payments for user: {}", userId);
        
        try {
            Map<String, Object> request = Map.of(
                "userId", userId,
                "reason", "ACCOUNT_CLOSURE",
                "immediateCancel", true
            );
            
            String response = getPaymentServiceClient().post()
                .uri("/api/v1/recurring-payments/cancel-all")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(20))
                .block();
            
            int cancelled = Integer.parseInt(response);
            
            metricsService.incrementCounter("recurring.payments.cancelled",
                Map.of("count", String.valueOf(cancelled), "reason", "ACCOUNT_CLOSURE"));
            
            return cancelled;
            
        } catch (Exception e) {
            log.error("Failed to cancel recurring payments for {}: {}", userId, e.getMessage());
            return cancelAllRecurringPaymentsFallback(userId, e);
        }
    }
    
    private int cancelAllRecurringPaymentsFallback(String userId, Exception e) {
        log.warn("Using fallback for recurring payment cancellation: {}", userId);
        return recurringPaymentRepository.cancelAllByUserId(userId);
    }

    /**
     * Get loyalty points balance
     */
    @CircuitBreaker(name = "loyalty-service", fallbackMethod = "getLoyaltyPointsBalanceFallback")
    @Retry(name = "loyalty-service")
    public BigDecimal getLoyaltyPointsBalance(String userId) {
        log.debug("Getting loyalty points balance for user: {}", userId);
        
        try {
            String response = getLoyaltyServiceClient().get()
                .uri("/api/v1/loyalty/balance/{userId}", userId)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5))
                .block();
            
            return new BigDecimal(response);
            
        } catch (Exception e) {
            log.error("Failed to get loyalty points balance for {}: {}", userId, e.getMessage());
            return getLoyaltyPointsBalanceFallback(userId, e);
        }
    }
    
    private BigDecimal getLoyaltyPointsBalanceFallback(String userId, Exception e) {
        log.warn("Using fallback for loyalty points balance: {}", userId);
        return loyaltyPointsRepository.getBalanceByUserId(userId);
    }

    // Token and Session Management

    /**
     * Revoke all tokens for user
     */
    @Transactional
    public int revokeAllTokens(String userId) {
        log.info("Revoking all tokens for user: {}", userId);
        
        try {
            // Revoke access tokens
            int accessTokensRevoked = accessTokenRepository.revokeAllByUserId(userId);
            
            // Revoke refresh tokens
            int refreshTokensRevoked = accessTokenRepository.revokeAllRefreshTokensByUserId(userId);
            
            // Clear token cache
            clearUserTokenCache(userId);
            
            int totalRevoked = accessTokensRevoked + refreshTokensRevoked;
            
            metricsService.incrementCounter("tokens.revoked",
                Map.of("count", String.valueOf(totalRevoked), "reason", "ACCOUNT_CLOSURE"));
            
            log.info("Revoked {} tokens for user: {}", totalRevoked, userId);
            
            return totalRevoked;
            
        } catch (Exception e) {
            log.error("Failed to revoke tokens for {}: {}", userId, e.getMessage());
            metricsService.recordFailedOperation("revokeAllTokens", e.getMessage());
            return 0;
        }
    }

    /**
     * Invalidate all API keys for user
     */
    @Transactional
    public int invalidateAllApiKeys(String userId) {
        log.info("Invalidating all API keys for user: {}", userId);
        
        try {
            List<ApiKey> apiKeys = apiKeyRepository.findActiveByUserId(userId);
            
            for (ApiKey apiKey : apiKeys) {
                apiKey.setActive(false);
                apiKey.setRevokedAt(LocalDateTime.now());
                apiKey.setRevocationReason("ACCOUNT_CLOSURE");
            }
            
            apiKeyRepository.saveAll(apiKeys);
            
            // Clear API key cache
            clearUserApiKeyCache(userId);
            
            metricsService.incrementCounter("api.keys.invalidated",
                Map.of("count", String.valueOf(apiKeys.size()), "reason", "ACCOUNT_CLOSURE"));
            
            log.info("Invalidated {} API keys for user: {}", apiKeys.size(), userId);
            
            return apiKeys.size();
            
        } catch (Exception e) {
            log.error("Failed to invalidate API keys for {}: {}", userId, e.getMessage());
            metricsService.recordFailedOperation("invalidateAllApiKeys", e.getMessage());
            return 0;
        }
    }

    /**
     * Terminate all sessions for user
     */
    @Transactional
    public int terminateAllSessions(String userId) {
        log.info("Terminating all sessions for user: {}", userId);
        
        try {
            List<UserSession> activeSessions = sessionRepository.findActiveByUserId(userId);
            
            for (UserSession session : activeSessions) {
                session.setActive(false);
                session.setTerminatedAt(LocalDateTime.now());
                session.setTerminationReason("ACCOUNT_CLOSURE");
            }
            
            sessionRepository.saveAll(activeSessions);
            
            // Clear session cache
            clearUserSessionCache(userId);
            
            metricsService.incrementCounter("sessions.terminated",
                Map.of("count", String.valueOf(activeSessions.size()), "reason", "ACCOUNT_CLOSURE"));
            
            log.info("Terminated {} sessions for user: {}", activeSessions.size(), userId);
            
            return activeSessions.size();
            
        } catch (Exception e) {
            log.error("Failed to terminate sessions for {}: {}", userId, e.getMessage());
            metricsService.recordFailedOperation("terminateAllSessions", e.getMessage());
            return 0;
        }
    }

    /**
     * Delete all payment methods for user
     */
    @Transactional
    public int deleteAllPaymentMethods(String userId) {
        log.info("Deleting all payment methods for user: {}", userId);
        
        try {
            List<PaymentMethod> paymentMethods = paymentMethodRepository.findActiveByUserId(userId);
            
            for (PaymentMethod paymentMethod : paymentMethods) {
                // Mark as deleted rather than physical delete for audit trail
                paymentMethod.setDeleted(true);
                paymentMethod.setDeletedAt(LocalDateTime.now());
                paymentMethod.setDeletionReason("ACCOUNT_CLOSURE");
                
                // Clear sensitive data
                paymentMethod.setTokenizedCardNumber("DELETED");
                paymentMethod.setExpiryMonth(null);
                paymentMethod.setExpiryYear(null);
            }
            
            paymentMethodRepository.saveAll(paymentMethods);
            
            metricsService.incrementCounter("payment.methods.deleted",
                Map.of("count", String.valueOf(paymentMethods.size()), "reason", "ACCOUNT_CLOSURE"));
            
            log.info("Deleted {} payment methods for user: {}", paymentMethods.size(), userId);
            
            return paymentMethods.size();
            
        } catch (Exception e) {
            log.error("Failed to delete payment methods for {}: {}", userId, e.getMessage());
            metricsService.recordFailedOperation("deleteAllPaymentMethods", e.getMessage());
            return 0;
        }
    }

    // Provider and Service Information

    /**
     * Get payment providers used by user
     */
    public List<String> getPaymentProviders(String userId) {
        log.debug("Getting payment providers for user: {}", userId);
        
        try {
            List<PaymentMethod> paymentMethods = paymentMethodRepository.findByUserId(userId);
            
            return paymentMethods.stream()
                .map(PaymentMethod::getProvider)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("Failed to get payment providers for {}: {}", userId, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Get integrated services used by user
     */
    public List<String> getIntegratedServices(String userId) {
        log.debug("Getting integrated services for user: {}", userId);
        
        try {
            UUID userUuid = UUID.fromString(userId);
            User user = userRepository.findById(userUuid)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
            
            List<String> services = new ArrayList<>();
            
            // Add services based on user's usage history
            if (user.getIntegratedServices() != null) {
                services.addAll(user.getIntegratedServices());
            }
            
            // Add services based on payment methods
            List<String> paymentProviders = getPaymentProviders(userId);
            services.addAll(paymentProviders);
            
            // Add subscription services
            List<String> subscriptionServices = getActiveSubscriptions(userId)
                .stream()
                .map(this::extractServiceFromSubscription)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            services.addAll(subscriptionServices);
            
            return services.stream().distinct().collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("Failed to get integrated services for {}: {}", userId, e.getMessage());
            return new ArrayList<>();
        }
    }

    // Authentication and Verification

    /**
     * Verify authentication token for user
     */
    public boolean verifyToken(String userId, String token) {
        log.debug("Verifying token for user: {}", userId);
        
        try {
            return securityService.verifyUserToken(userId, token);
            
        } catch (Exception e) {
            log.error("Failed to verify token for {}: {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * Verify security answers for user
     */
    public boolean verifySecurityAnswers(String userId, Map<String, String> answers) {
        log.debug("Verifying security answers for user: {}", userId);
        
        try {
            UUID userUuid = UUID.fromString(userId);
            User user = userRepository.findById(userUuid)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
            
            return securityService.verifySecurityAnswers(user, answers);
            
        } catch (Exception e) {
            log.error("Failed to verify security answers for {}: {}", userId, e.getMessage());
            return false;
        }
    }

    // Private helper methods

    private WebClient getPaymentServiceClient() {
        if (paymentServiceClient == null) {
            paymentServiceClient = webClientBuilder.baseUrl(paymentServiceUrl).build();
        }
        return paymentServiceClient;
    }

    private WebClient getSubscriptionServiceClient() {
        if (subscriptionServiceClient == null) {
            subscriptionServiceClient = webClientBuilder.baseUrl(subscriptionServiceUrl).build();
        }
        return subscriptionServiceClient;
    }

    private WebClient getLoyaltyServiceClient() {
        if (loyaltyServiceClient == null) {
            loyaltyServiceClient = webClientBuilder.baseUrl(loyaltyServiceUrl).build();
        }
        return loyaltyServiceClient;
    }

    private WebClient getDisputeServiceClient() {
        if (disputeServiceClient == null) {
            disputeServiceClient = webClientBuilder.baseUrl(disputeServiceUrl).build();
        }
        return disputeServiceClient;
    }

    private void clearUserCaches(String userId) {
        try {
            String[] cacheKeys = {
                USER_TOKENS_CACHE + userId,
                USER_SESSIONS_CACHE + userId,
                "user_profile:" + userId,
                "user_permissions:" + userId,
                "user_preferences:" + userId
            };
            
            redisTemplate.delete(Arrays.asList(cacheKeys));
            
        } catch (Exception e) {
            log.warn("Failed to clear user caches for {}: {}", userId, e.getMessage());
        }
    }

    private void clearUserTokenCache(String userId) {
        try {
            redisTemplate.delete(USER_TOKENS_CACHE + userId);
        } catch (Exception e) {
            log.warn("Failed to clear token cache for {}: {}", userId, e.getMessage());
        }
    }

    private void clearUserApiKeyCache(String userId) {
        try {
            redisTemplate.delete("api_keys:" + userId);
        } catch (Exception e) {
            log.warn("Failed to clear API key cache for {}: {}", userId, e.getMessage());
        }
    }

    private void clearUserSessionCache(String userId) {
        try {
            redisTemplate.delete(USER_SESSIONS_CACHE + userId);
        } catch (Exception e) {
            log.warn("Failed to clear session cache for {}: {}", userId, e.getMessage());
        }
    }

    private void notifySecuritySystems(String userId, String action) {
        try {
            Map<String, Object> securityEvent = Map.of(
                "userId", userId,
                "action", action,
                "timestamp", LocalDateTime.now(),
                "source", "AccountService"
            );
            
            notificationService.sendSecurityAlert(securityEvent);
            
        } catch (Exception e) {
            log.warn("Failed to notify security systems for {}: {}", userId, e.getMessage());
        }
    }

    private String extractServiceFromSubscription(String subscriptionId) {
        // Extract service name from subscription ID pattern
        if (subscriptionId.contains("_")) {
            return subscriptionId.split("_")[0];
        }
        return null;
    }
}