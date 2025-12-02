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

@Slf4j
@Component
@RequiredArgsConstructor
public class WalletServiceClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${services.wallet-service.url:http://wallet-service:8080}")
    private String walletServiceUrl;

    @Value("${services.wallet-service.timeout:5000}")
    private int timeoutMillis;

    @Cacheable(value = "walletOwnership", key = "#userId + ':' + #walletId", unless = "#result == null")
    public Boolean checkWalletOwnership(String userId, String walletId) {
        log.debug("Checking wallet ownership: userId={}, walletId={}", userId, walletId);
        
        try {
            return webClientBuilder.build()
                .get()
                .uri(walletServiceUrl + "/api/wallets/{walletId}/owner/{userId}/check", walletId, userId)
                .retrieve()
                .onStatus(HttpStatus.NOT_FOUND::equals, response -> Mono.just(false))
                .bodyToMono(Boolean.class)
                .timeout(Duration.ofMillis(timeoutMillis))
                .retryWhen(Retry.backoff(2, Duration.ofMillis(100))
                    .filter(throwable -> !(throwable instanceof WebClientResponseException.NotFound)))
                .onErrorResume(throwable -> {
                    log.error("Error checking wallet ownership: userId={}, walletId={}", 
                        userId, walletId, throwable);
                    return Mono.just(false);
                })
                .block();
                
        } catch (Exception e) {
            log.error("Failed to check wallet ownership: userId={}, walletId={}", 
                userId, walletId, e);
            return false;
        }
    }

    public WalletDetailsResponse getWalletDetails(String walletId) {
        log.debug("Fetching wallet details: walletId={}", walletId);
        
        try {
            return webClientBuilder.build()
                .get()
                .uri(walletServiceUrl + "/api/wallets/{walletId}", walletId)
                .retrieve()
                .bodyToMono(WalletDetailsResponse.class)
                .timeout(Duration.ofMillis(timeoutMillis))
                .retryWhen(Retry.backoff(2, Duration.ofMillis(100)))
                .block();
                
        } catch (WebClientResponseException.NotFound e) {
            log.warn("Wallet not found: walletId={}", walletId);
            return null;
        } catch (Exception e) {
            log.error("Failed to fetch wallet details: walletId={}", walletId, e);
            return null;
        }
    }

    public static class WalletDetailsResponse {
        private String id;
        private String userId;
        private String currency;
        private String status;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}