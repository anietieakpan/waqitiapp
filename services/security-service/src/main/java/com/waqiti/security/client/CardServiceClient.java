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
public class CardServiceClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${services.virtual-card-service.url:http://virtual-card-service:8080}")
    private String cardServiceUrl;

    @Value("${services.virtual-card-service.timeout:5000}")
    private int timeoutMillis;

    @Cacheable(value = "cardOwnership", key = "#userId + ':' + #cardId", unless = "#result == null")
    public Boolean checkCardOwnership(String userId, String cardId) {
        log.debug("Checking card ownership: userId={}, cardId={}", userId, cardId);
        
        try {
            return webClientBuilder.build()
                .get()
                .uri(cardServiceUrl + "/api/cards/{cardId}/owner/{userId}/check", cardId, userId)
                .retrieve()
                .onStatus(HttpStatus.NOT_FOUND::equals, response -> Mono.just(false))
                .bodyToMono(Boolean.class)
                .timeout(Duration.ofMillis(timeoutMillis))
                .retryWhen(Retry.backoff(2, Duration.ofMillis(100))
                    .filter(throwable -> !(throwable instanceof WebClientResponseException.NotFound)))
                .onErrorResume(throwable -> {
                    log.error("Error checking card ownership: userId={}, cardId={}", 
                        userId, cardId, throwable);
                    return Mono.just(false);
                })
                .block();
                
        } catch (Exception e) {
            log.error("Failed to check card ownership: userId={}, cardId={}", 
                userId, cardId, e);
            return false;
        }
    }

    public CardDetailsResponse getCardDetails(String cardId) {
        log.debug("Fetching card details: cardId={}", cardId);
        
        try {
            return webClientBuilder.build()
                .get()
                .uri(cardServiceUrl + "/api/cards/{cardId}", cardId)
                .retrieve()
                .bodyToMono(CardDetailsResponse.class)
                .timeout(Duration.ofMillis(timeoutMillis))
                .retryWhen(Retry.backoff(2, Duration.ofMillis(100)))
                .block();
                
        } catch (WebClientResponseException.NotFound e) {
            log.warn("Card not found: cardId={}", cardId);
            return null;
        } catch (Exception e) {
            log.error("Failed to fetch card details: cardId={}", cardId, e);
            return null;
        }
    }

    public static class CardDetailsResponse {
        private String id;
        private String userId;
        private String cardNumber;
        private String cardType;
        private String status;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public String getCardNumber() { return cardNumber; }
        public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }
        
        public String getCardType() { return cardType; }
        public void setCardType(String cardType) { this.cardType = cardType; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}