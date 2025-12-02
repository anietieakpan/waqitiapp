package com.waqiti.account.service;

import com.waqiti.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Risk Assessment Service for Account Operations
 * 
 * Provides real-time risk scoring and assessment for account creation,
 * transactions, and other financial operations using ML models and rule engines.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskAssessmentService {
    
    private final WebClient.Builder webClientBuilder;
    private final RestTemplate restTemplate;
    private final CircuitBreakerService circuitBreaker;
    
    @Value("${risk.service.base-url:http://ml-service:8080}")
    private String riskServiceBaseUrl;
    
    @Value("${risk.assessment.timeout:10}")
    private int assessmentTimeout;
    
    @Value("${risk.score.high-threshold:80}")
    private int highRiskThreshold;
    
    @Value("${risk.score.medium-threshold:50}")
    private int mediumRiskThreshold;
    
    private final Map<UUID, RiskProfile> riskProfileCache = new ConcurrentHashMap<>();
    
    /**
     * Assess risk for account creation
     */
    public RiskAssessmentResult assessAccountCreationRisk(UUID userId, String accountType, BigDecimal initialDeposit) {
        log.info("Assessing account creation risk for user: {}, type: {}, amount: {}", 
            userId, accountType, initialDeposit);
        
        return circuitBreaker.executeWithFallback(
            "risk-assessment",
            () -> performRiskAssessment(userId, "ACCOUNT_CREATION", buildRiskFactors(accountType, initialDeposit)),
            () -> getFallbackRiskAssessment(userId, initialDeposit)
        );
    }
    
    /**
     * Assess transaction risk
     */
    public RiskAssessmentResult assessTransactionRisk(UUID userId, BigDecimal amount, String transactionType, String destinationAccount) {
        log.info("Assessing transaction risk for user: {}, amount: {}, type: {}", 
            userId, amount, transactionType);
        
        Map<String, Object> factors = new HashMap<>();
        factors.put("amount", amount);
        factors.put("transactionType", transactionType);
        factors.put("destinationAccount", destinationAccount);
        factors.put("timestamp", System.currentTimeMillis());
        
        return performRiskAssessment(userId, "TRANSACTION", factors);
    }
    
    /**
     * Perform risk assessment via ML service
     */
    private RiskAssessmentResult performRiskAssessment(UUID userId, String assessmentType, Map<String, Object> factors) {
        try {
            // Check cached risk profile
            RiskProfile cachedProfile = riskProfileCache.get(userId);
            if (cachedProfile != null && !cachedProfile.isExpired()) {
                factors.put("historicalRiskScore", cachedProfile.getScore());
            }
            
            // Build request
            RiskAssessmentRequest request = RiskAssessmentRequest.builder()
                .userId(userId)
                .assessmentType(assessmentType)
                .factors(factors)
                .timestamp(System.currentTimeMillis())
                .build();
            
            // Call ML service for risk scoring
            WebClient webClient = webClientBuilder
                .baseUrl(riskServiceBaseUrl)
                .build();
            
            RiskAssessmentResponse response = webClient
                .post()
                .uri("/api/v1/risk/assess")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(request), RiskAssessmentRequest.class)
                .retrieve()
                .bodyToMono(RiskAssessmentResponse.class)
                .timeout(Duration.ofSeconds(assessmentTimeout))
                .block();
            
            if (response != null) {
                // Update risk profile cache
                updateRiskProfile(userId, response.getRiskScore());
                
                // Build result
                return RiskAssessmentResult.builder()
                    .riskScore(response.getRiskScore())
                    .riskLevel(determineRiskLevel(response.getRiskScore()))
                    .riskFactors(response.getRiskFactors())
                    .recommendations(response.getRecommendations())
                    .requiresManualReview(response.getRiskScore() > highRiskThreshold)
                    .assessedAt(new Date())
                    .build();
            }
            
        } catch (Exception e) {
            log.error("Risk assessment failed for user: {}", userId, e);
        }
        
        // Return medium risk as default
        return getDefaultRiskAssessment();
    }
    
    /**
     * Build risk factors for assessment
     */
    private Map<String, Object> buildRiskFactors(String accountType, BigDecimal amount) {
        Map<String, Object> factors = new HashMap<>();
        factors.put("accountType", accountType);
        factors.put("amount", amount);
        factors.put("timestamp", System.currentTimeMillis());
        
        // Account type risk factors
        if ("CREDIT".equals(accountType) || "INVESTMENT".equals(accountType)) {
            factors.put("highRiskAccountType", true);
        }
        
        // Amount risk factors
        if (amount != null) {
            if (amount.compareTo(new BigDecimal("10000")) > 0) {
                factors.put("highValueTransaction", true);
            }
            if (amount.compareTo(new BigDecimal("100000")) > 0) {
                factors.put("veryHighValueTransaction", true);
            }
        }
        
        return factors;
    }
    
    /**
     * Determine risk level from score
     */
    private String determineRiskLevel(int riskScore) {
        if (riskScore >= highRiskThreshold) {
            return "HIGH";
        } else if (riskScore >= mediumRiskThreshold) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }
    
    /**
     * Update user risk profile cache
     */
    private void updateRiskProfile(UUID userId, int riskScore) {
        riskProfileCache.put(userId, RiskProfile.builder()
            .userId(userId)
            .score(riskScore)
            .lastUpdated(System.currentTimeMillis())
            .build());
    }
    
    /**
     * Get fallback risk assessment when service unavailable
     */
    private RiskAssessmentResult getFallbackRiskAssessment(UUID userId, BigDecimal amount) {
        log.warn("Using fallback risk assessment for user: {}", userId);
        
        // Conservative fallback based on amount
        int riskScore = 50; // Medium risk default
        
        if (amount != null) {
            if (amount.compareTo(new BigDecimal("10000")) > 0) {
                riskScore = 75; // High-medium risk for large amounts
            } else if (amount.compareTo(new BigDecimal("1000")) < 0) {
                riskScore = 25; // Low risk for small amounts
            }
        }
        
        return RiskAssessmentResult.builder()
            .riskScore(riskScore)
            .riskLevel(determineRiskLevel(riskScore))
            .riskFactors(List.of("Service unavailable - conservative assessment"))
            .recommendations(List.of("Manual review recommended"))
            .requiresManualReview(riskScore > 60)
            .assessedAt(new Date())
            .build();
    }
    
    /**
     * Get default risk assessment
     */
    private RiskAssessmentResult getDefaultRiskAssessment() {
        return RiskAssessmentResult.builder()
            .riskScore(50)
            .riskLevel("MEDIUM")
            .riskFactors(List.of("Default assessment"))
            .recommendations(List.of("Standard verification required"))
            .requiresManualReview(false)
            .assessedAt(new Date())
            .build();
    }
    
    /**
     * Clear risk profile cache for user
     */
    public void clearRiskProfile(UUID userId) {
        riskProfileCache.remove(userId);
        log.info("Cleared risk profile cache for user: {}", userId);
    }
    
    /**
     * Get current risk profile for user
     */
    public Optional<RiskProfile> getRiskProfile(UUID userId) {
        RiskProfile profile = riskProfileCache.get(userId);
        if (profile != null && !profile.isExpired()) {
            return Optional.of(profile);
        }
        return Optional.empty();
    }
}

// Domain models

