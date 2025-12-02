package com.waqiti.security.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountServiceClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${services.account-service.url:http://account-service:8080}")
    private String accountServiceUrl;

    @Value("${services.account-service.timeout:5000}")
    private int timeoutMillis;

    @Cacheable(value = "accountOwnership", key = "#userId + ':' + #accountId", unless = "#result == null")
    public Boolean checkAccountOwnership(String userId, String accountId) {
        log.debug("Checking account ownership: userId={}, accountId={}", userId, accountId);
        
        try {
            return webClientBuilder.build()
                .get()
                .uri(accountServiceUrl + "/api/accounts/{accountId}/owner/{userId}/check", accountId, userId)
                .retrieve()
                .onStatus(HttpStatus.NOT_FOUND::equals, response -> Mono.error(new WebClientResponseException.NotFound(
                    response.statusCode().value(), "Not Found", null, null, null)))
                .bodyToMono(Boolean.class)
                .timeout(Duration.ofMillis(timeoutMillis))
                .retryWhen(Retry.backoff(2, Duration.ofMillis(100))
                    .filter(throwable -> !(throwable instanceof WebClientResponseException.NotFound)))
                .onErrorResume(throwable -> {
                    log.error("Error checking account ownership: userId={}, accountId={}", 
                        userId, accountId, throwable);
                    return Mono.just(false);
                })
                .block();
                
        } catch (Exception e) {
            log.error("Failed to check account ownership: userId={}, accountId={}", 
                userId, accountId, e);
            return false;
        }
    }

    public AccountDetailsResponse getAccountDetails(String accountId) {
        log.debug("Fetching account details: accountId={}", accountId);
        
        try {
            return webClientBuilder.build()
                .get()
                .uri(accountServiceUrl + "/api/accounts/{accountId}", accountId)
                .retrieve()
                .bodyToMono(AccountDetailsResponse.class)
                .timeout(Duration.ofMillis(timeoutMillis))
                .retryWhen(Retry.backoff(2, Duration.ofMillis(100)))
                .block();
                
        } catch (WebClientResponseException.NotFound e) {
            log.warn("Account not found: accountId={}", accountId);
            return null;
        } catch (Exception e) {
            log.error("Failed to fetch account details: accountId={}", accountId, e);
            return null;
        }
    }

    public static class AccountDetailsResponse {
        private String id;
        private String userId;
        private String accountNumber;
        private String accountType;
        private String status;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public String getAccountNumber() { return accountNumber; }
        public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
        
        public String getAccountType() { return accountType; }
        public void setAccountType(String accountType) { this.accountType = accountType; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}