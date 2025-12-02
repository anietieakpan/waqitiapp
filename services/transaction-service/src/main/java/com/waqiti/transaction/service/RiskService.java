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
public class RiskService {
    
    private final WebClient.Builder webClientBuilder;
    
    @Value("${risk-service.url:http://localhost:8090}")
    private String riskServiceUrl;
    
    private WebClient webClient;
    
    private WebClient getWebClient() {
        if (webClient == null) {
            webClient = webClientBuilder.baseUrl(riskServiceUrl).build();
        }
        return webClient;
    }
    
    @CircuitBreaker(name = "risk-service", fallbackMethod = "assessBlockRiskFallback")
    @Retry(name = "risk-service")
    public Object assessBlockRisk(Object riskRequest) {
        log.debug("Assessing block risk: {}", riskRequest);
        
        try {
            Object result = getWebClient().post()
                    .uri("/api/v1/risk/assess-block")
                    .bodyValue(riskRequest)
                    .retrieve()
                    .bodyToMono(Object.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();
            
            log.debug("Successfully assessed block risk");
            return result;
            
        } catch (Exception e) {
            log.error("Failed to assess block risk", e);
            return assessBlockRiskFallback(riskRequest, e);
        }
    }
    
    @CircuitBreaker(name = "risk-service", fallbackMethod = "calculateEnhancedRiskScoreFallback")
    @Retry(name = "risk-service")
    public int calculateEnhancedRiskScore(Object targetType, String targetId, Integer baseRiskScore) {
        log.debug("Calculating enhanced risk score for: {} {} baseScore: {}", 
                targetType, targetId, baseRiskScore);
        
        try {
            Map<String, Object> request = Map.of(
                    "targetType", targetType.toString(),
                    "targetId", targetId,
                    "baseRiskScore", baseRiskScore != null ? baseRiskScore : 0
            );
            
            Integer enhancedScore = getWebClient().post()
                    .uri("/api/v1/risk/calculate-enhanced-score")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Integer.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            
            log.debug("Enhanced risk score calculated: {}", enhancedScore);
            return enhancedScore != null ? enhancedScore : (baseRiskScore != null ? baseRiskScore : 50);
            
        } catch (Exception e) {
            log.error("Failed to calculate enhanced risk score", e);
            return calculateEnhancedRiskScoreFallback(targetType, targetId, baseRiskScore, e);
        }
    }
    
    @CircuitBreaker(name = "risk-service", fallbackMethod = "updateRiskProfileFallback")
    @Retry(name = "risk-service")
    public void updateRiskProfile(Object targetType, String targetId, int riskScore) {
        log.debug("Updating risk profile for: {} {} score: {}", targetType, targetId, riskScore);
        
        try {
            Map<String, Object> request = Map.of(
                    "targetType", targetType.toString(),
                    "targetId", targetId,
                    "riskScore", riskScore
            );
            
            getWebClient().post()
                    .uri("/api/v1/risk/update-profile")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            
            log.debug("Successfully updated risk profile");
            
        } catch (Exception e) {
            log.error("Failed to update risk profile", e);
            updateRiskProfileFallback(targetType, targetId, riskScore, e);
        }
    }
    
    @CircuitBreaker(name = "risk-service", fallbackMethod = "clearBlockRelatedFlagsFallback")
    @Retry(name = "risk-service")
    public void clearBlockRelatedFlags(Object targetType, String targetId, String blockId) {
        log.debug("Clearing block-related risk flags for: {} {} blockId: {}", 
                targetType, targetId, blockId);
        
        try {
            Map<String, Object> request = Map.of(
                    "targetType", targetType.toString(),
                    "targetId", targetId,
                    "blockId", blockId
            );
            
            getWebClient().post()
                    .uri("/api/v1/risk/clear-block-flags")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            
            log.debug("Successfully cleared block-related risk flags");
            
        } catch (Exception e) {
            log.error("Failed to clear block-related risk flags", e);
            clearBlockRelatedFlagsFallback(targetType, targetId, blockId, e);
        }
    }
    
    @CircuitBreaker(name = "risk-service", fallbackMethod = "reevaluateRiskProfileFallback")
    @Retry(name = "risk-service")
    public void reevaluateRiskProfile(Object targetType, String targetId) {
        log.debug("Reevaluating risk profile for: {} {}", targetType, targetId);
        
        try {
            Map<String, Object> request = Map.of(
                    "targetType", targetType.toString(),
                    "targetId", targetId
            );
            
            getWebClient().post()
                    .uri("/api/v1/risk/reevaluate-profile")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();
            
            log.debug("Successfully reevaluated risk profile");
            
        } catch (Exception e) {
            log.error("Failed to reevaluate risk profile", e);
            reevaluateRiskProfileFallback(targetType, targetId, e);
        }
    }
    
    @CircuitBreaker(name = "risk-service", fallbackMethod = "updateBlockMetricsFallback")
    @Retry(name = "risk-service")
    public void updateBlockMetrics(Object request, Object result) {
        log.debug("Updating risk block metrics: {} result: {}", request, result);
        
        try {
            Map<String, Object> metricsUpdate = Map.of(
                    "blockRequest", request,
                    "blockResult", result,
                    "timestamp", System.currentTimeMillis()
            );
            
            getWebClient().post()
                    .uri("/api/v1/risk/block-metrics")
                    .bodyValue(metricsUpdate)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            
            log.debug("Successfully updated risk block metrics");
            
        } catch (Exception e) {
            log.error("Failed to update risk block metrics", e);
            updateBlockMetricsFallback(request, result, e);
        }
    }
    
    private Object assessBlockRiskFallback(Object riskRequest, Exception e) {
        log.warn("Risk service unavailable - using default risk assessment (fallback)");
        return Map.of("riskLevel", "MEDIUM", "isHighRisk", false, "message", "Service unavailable");
    }
    
    private int calculateEnhancedRiskScoreFallback(Object targetType, String targetId, 
                                                  Integer baseRiskScore, Exception e) {
        log.warn("Risk service unavailable - using base risk score (fallback): {}", baseRiskScore);
        return baseRiskScore != null ? baseRiskScore : 50;
    }
    
    private void updateRiskProfileFallback(Object targetType, String targetId, int riskScore, Exception e) {
        log.warn("Risk service unavailable - risk profile not updated (fallback): {} {}", 
                targetType, targetId);
    }
    
    private void clearBlockRelatedFlagsFallback(Object targetType, String targetId, 
                                               String blockId, Exception e) {
        log.warn("Risk service unavailable - block flags not cleared (fallback): {}", blockId);
    }
    
    private void reevaluateRiskProfileFallback(Object targetType, String targetId, Exception e) {
        log.warn("Risk service unavailable - risk profile not reevaluated (fallback): {} {}", 
                targetType, targetId);
    }
    
    private void updateBlockMetricsFallback(Object request, Object result, Exception e) {
        log.warn("Risk service unavailable - block metrics not updated (fallback)");
    }
}