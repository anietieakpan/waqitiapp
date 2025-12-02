package com.waqiti.transaction.service;

import com.waqiti.common.client.ComplianceServiceClient;
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
public class ComplianceService {
    
    private final ComplianceServiceClient complianceClient;
    private final WebClient.Builder webClientBuilder;
    
    @Value("${compliance-service.url:http://localhost:8087}")
    private String complianceServiceUrl;
    
    private WebClient webClient;
    
    private WebClient getWebClient() {
        if (webClient == null) {
            webClient = webClientBuilder.baseUrl(complianceServiceUrl).build();
        }
        return webClient;
    }
    
    @CircuitBreaker(name = "compliance-service", fallbackMethod = "createReviewCaseFallback")
    @Retry(name = "compliance-service")
    public Object createReviewCase(Object reviewRequest) {
        log.info("Creating compliance review case: {}", reviewRequest);
        
        try {
            Object caseResult = getWebClient().post()
                    .uri("/api/v1/compliance/cases")
                    .bodyValue(reviewRequest)
                    .retrieve()
                    .bodyToMono(Object.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
            
            log.info("Successfully created compliance review case");
            return caseResult;
        } catch (Exception e) {
            log.error("Failed to create compliance review case", e);
            return createReviewCaseFallback(reviewRequest, e);
        }
    }
    
    @CircuitBreaker(name = "compliance-service", fallbackMethod = "updateBlockMonitoringFallback")
    @Retry(name = "compliance-service")
    public void updateBlockMonitoring(Object request, Object result) {
        log.debug("Updating compliance block monitoring: {} result: {}", request, result);
        
        try {
            Map<String, Object> monitoringUpdate = Map.of(
                    "blockRequest", request,
                    "blockResult", result,
                    "timestamp", System.currentTimeMillis()
            );
            
            getWebClient().post()
                    .uri("/api/v1/compliance/block-monitoring")
                    .bodyValue(monitoringUpdate)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();
            
            log.debug("Successfully updated compliance block monitoring");
        } catch (Exception e) {
            log.error("Failed to update compliance block monitoring", e);
            updateBlockMonitoringFallback(request, result, e);
        }
    }
    
    private Object createReviewCaseFallback(Object reviewRequest, Exception e) {
        log.warn("Compliance service unavailable - review case not created");
        return Map.of("status", "PENDING_MANUAL_CREATION", "message", "Service unavailable");
    }
    
    private void updateBlockMonitoringFallback(Object request, Object result, Exception e) {
        log.warn("Compliance service unavailable - block monitoring not updated");
    }
}