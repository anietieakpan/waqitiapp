package com.waqiti.transaction.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FraudService {
    
    private final WebClient.Builder webClientBuilder;
    
    @Value("${fraud-detection-service.url:http://localhost:8089}")
    private String fraudServiceUrl;
    
    private WebClient webClient;
    
    private WebClient getWebClient() {
        if (webClient == null) {
            webClient = webClientBuilder.baseUrl(fraudServiceUrl).build();
        }
        return webClient;
    }
    
    @CircuitBreaker(name = "fraud-service", fallbackMethod = "updateBlockStatusFallback")
    @Retry(name = "fraud-service")
    public void updateBlockStatus(Object request, Object result) {
        log.debug("Updating fraud block status: {} result: {}", request, result);
        
        try {
            Map<String, Object> updateRequest = Map.of(
                    "blockRequest", request,
                    "blockResult", result,
                    "timestamp", System.currentTimeMillis()
            );
            
            getWebClient().post()
                    .uri("/api/v1/fraud/block-status")
                    .bodyValue(updateRequest)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            
            log.debug("Successfully updated fraud block status");
        } catch (Exception e) {
            log.error("Failed to update fraud block status", e);
            updateBlockStatusFallback(request, result, e);
        }
    }
    
    private void updateBlockStatusFallback(Object request, Object result, Exception e) {
        log.warn("Fraud service unavailable - block status not updated in fraud system");
    }
}