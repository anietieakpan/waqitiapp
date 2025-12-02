package com.waqiti.transaction.service;

import com.waqiti.common.client.CoreBankingServiceClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {
    
    private final CoreBankingServiceClient coreBankingClient;
    private final WebClient.Builder webClientBuilder;
    
    @Value("${account-service.url:http://localhost:8081}")
    private String accountServiceUrl;
    
    private WebClient webClient;
    
    private WebClient getWebClient() {
        if (webClient == null) {
            webClient = webClientBuilder.baseUrl(accountServiceUrl).build();
        }
        return webClient;
    }
    
    @CircuitBreaker(name = "account-service", fallbackMethod = "accountExistsFallback")
    @Retry(name = "account-service")
    public boolean accountExists(String accountId) {
        log.debug("Checking if account exists: {}", accountId);
        
        try {
            Boolean exists = getWebClient().get()
                    .uri("/api/v1/accounts/{accountId}/exists", accountId)
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();
            
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.error("Failed to check if account exists: {}", accountId, e);
            return accountExistsFallback(accountId, e);
        }
    }
    
    @CircuitBreaker(name = "account-service", fallbackMethod = "applyRestrictionsFallback")
    @Retry(name = "account-service")
    public void applyTransactionRestrictions(String accountId, Object blockType, String blockReason) {
        log.info("Applying transaction restrictions to account: {} type: {} reason: {}", 
                accountId, blockType, blockReason);
        
        try {
            Map<String, Object> request = Map.of(
                    "accountId", accountId,
                    "restrictionType", blockType != null ? blockType.toString() : "GENERAL_BLOCK",
                    "reason", blockReason,
                    "active", true
            );
            
            getWebClient().post()
                    .uri("/api/v1/accounts/{accountId}/restrictions", accountId)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            
            log.info("Successfully applied transaction restrictions to account: {}", accountId);
        } catch (Exception e) {
            log.error("Failed to apply transaction restrictions to account: {}", accountId, e);
            applyRestrictionsFallback(accountId, blockType, blockReason, e);
        }
    }
    
    @CircuitBreaker(name = "account-service", fallbackMethod = "removeRestrictionsFallback")
    @Retry(name = "account-service")
    public void removeTransactionRestrictions(String accountId) {
        log.info("Removing transaction restrictions from account: {}", accountId);
        
        try {
            getWebClient().delete()
                    .uri("/api/v1/accounts/{accountId}/restrictions", accountId)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            
            log.info("Successfully removed transaction restrictions from account: {}", accountId);
        } catch (Exception e) {
            log.error("Failed to remove transaction restrictions from account: {}", accountId, e);
            removeRestrictionsFallback(accountId, e);
        }
    }
    
    @CircuitBreaker(name = "account-service", fallbackMethod = "getCustomerAccountsFallback")
    @Retry(name = "account-service")
    public List<String> getCustomerAccounts(String customerId) {
        log.debug("Getting accounts for customer: {}", customerId);
        
        try {
            List<String> accounts = getWebClient().get()
                    .uri("/api/v1/customers/{customerId}/accounts", customerId)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .collectList()
                    .timeout(Duration.ofSeconds(10))
                    .block();
            
            return accounts != null ? accounts : List.of();
        } catch (Exception e) {
            log.error("Failed to get customer accounts: {}", customerId, e);
            return getCustomerAccountsFallback(customerId, e);
        }
    }
    
    @CircuitBreaker(name = "account-service", fallbackMethod = "freezeFundsFallback")
    @Retry(name = "account-service")
    public Object freezeFunds(Object freezeRequest) {
        log.info("Freezing funds: {}", freezeRequest);
        
        try {
            Object result = getWebClient().post()
                    .uri("/api/v1/accounts/freeze-funds")
                    .bodyValue(freezeRequest)
                    .retrieve()
                    .bodyToMono(Object.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();
            
            log.info("Successfully froze funds");
            return result;
        } catch (Exception e) {
            log.error("Failed to freeze funds", e);
            return freezeFundsFallback(freezeRequest, e);
        }
    }
    
    @CircuitBreaker(name = "account-service", fallbackMethod = "unfreezeFundsFallback")
    @Retry(name = "account-service")
    public Object unfreezeFunds(String freezeId, String reason) {
        log.info("Unfreezing funds: {} reason: {}", freezeId, reason);
        
        try {
            Map<String, Object> request = Map.of(
                    "freezeId", freezeId,
                    "reason", reason
            );
            
            Object result = getWebClient().post()
                    .uri("/api/v1/accounts/unfreeze-funds")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Object.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();
            
            log.info("Successfully unfroze funds: {}", freezeId);
            return result;
        } catch (Exception e) {
            log.error("Failed to unfreeze funds: {}", freezeId, e);
            return unfreezeFundsFallback(freezeId, reason, e);
        }
    }
    
    private boolean accountExistsFallback(String accountId, Exception e) {
        log.warn("Account existence check fallback for: {} - assuming exists", accountId);
        return true;
    }
    
    private void applyRestrictionsFallback(String accountId, Object blockType, String blockReason, Exception e) {
        log.error("Apply restrictions fallback for: {} - restrictions not applied", accountId);
    }
    
    private void removeRestrictionsFallback(String accountId, Exception e) {
        log.error("Remove restrictions fallback for: {} - restrictions not removed", accountId);
    }
    
    private List<String> getCustomerAccountsFallback(String customerId, Exception e) {
        log.warn("Get customer accounts fallback for: {} - returning empty list", customerId);
        return List.of();
    }
    
    private Object freezeFundsFallback(Object freezeRequest, Exception e) {
        log.error("Freeze funds fallback - funds not frozen");
        return Map.of("status", "FALLBACK", "message", "Service unavailable");
    }
    
    private Object unfreezeFundsFallback(String freezeId, String reason, Exception e) {
        log.error("Unfreeze funds fallback for: {} - funds not unfrozen", freezeId);
        return Map.of("status", "FALLBACK", "message", "Service unavailable");
    }
}