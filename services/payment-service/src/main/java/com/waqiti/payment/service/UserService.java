package com.waqiti.payment.service;

import com.waqiti.common.client.UserServiceClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    
    private final WebClient.Builder webClientBuilder;
    
    @Value("${user-service.url:http://localhost:8072}")
    private String userServiceUrl;
    
    private WebClient webClient;
    
    private WebClient getWebClient() {
        if (webClient == null) {
            webClient = webClientBuilder.baseUrl(userServiceUrl).build();
        }
        return webClient;
    }
    
    @CircuitBreaker(name = "user-service", fallbackMethod = "getUserByIdFallback")
    @Retry(name = "user-service")
    public Object getUserById(String userId) {
        log.debug("Getting user by ID: {}", userId);
        
        try {
            Object user = getWebClient().get()
                    .uri("/api/v1/users/{userId}", userId)
                    .retrieve()
                    .bodyToMono(Object.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();
            
            log.debug("Successfully retrieved user: {}", userId);
            return user;
            
        } catch (Exception e) {
            log.error("Failed to get user: {}", userId, e);
            throw e;
        }
    }
    
    private Object getUserByIdFallback(String userId, Exception e) {
        log.warn("User service unavailable - using fallback for user: {}", userId);
        return java.util.Map.of("id", userId, "status", "UNKNOWN");
    }
}