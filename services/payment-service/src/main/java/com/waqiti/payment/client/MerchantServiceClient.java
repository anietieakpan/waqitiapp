package com.waqiti.payment.client;

import com.waqiti.payment.merchant.dto.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.RetryBackoffSpec;

import java.time.Duration;
import java.util.Optional;

/**
 * Client for communication with merchant-service.
 * Handles merchant account management, verification, and risk assessment.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MerchantServiceClient {
    
    private final WebClient.Builder webClientBuilder;
    
    @Value("${merchant.service.base-url:http://merchant-service:8080}")
    private String merchantServiceBaseUrl;
    
    @Value("${merchant.service.timeout.seconds:10}")
    private int timeoutSeconds;
    
    @Value("${merchant.service.api-key:}")
    private String apiKey;
    
    private WebClient webClient;
    
    @javax.annotation.PostConstruct
    public void init() {
        this.webClient = webClientBuilder
            .baseUrl(merchantServiceBaseUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader("X-Service-Name", "payment-service")
            .defaultHeader("X-API-Key", apiKey)
            .build();
        
        log.info("MerchantServiceClient initialized with base URL: {}", merchantServiceBaseUrl);
    }
    
    /**
     * Get merchant account details
     */
    @CircuitBreaker(name = "merchant-service", fallbackMethod = "getMerchantAccountFallback")
    @Retry(name = "merchant-service")
    @Cacheable(value = "merchantAccount", key = "#merchantId", unless = "#result == null")
    public Optional<MerchantAccount> getMerchantAccount(String merchantId) {
        log.debug("Fetching merchant account: {}", merchantId);
        
        try {
            MerchantAccount account = webClient.get()
                .uri("/api/v1/merchants/{merchantId}", merchantId)
                .retrieve()
                .bodyToMono(MerchantAccount.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .block();
            
            return Optional.ofNullable(account);
        } catch (WebClientResponseException.NotFound e) {
            log.warn("Merchant account not found: {}", merchantId);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error fetching merchant account {}: {}", merchantId, e.getMessage());
            throw new MerchantServiceException("Failed to fetch merchant account", e);
        }
    }
    
    /**
     * Get merchant status
     */
    @CircuitBreaker(name = "merchant-service")
    @Retry(name = "merchant-service")
    public MerchantAccountStatus getMerchantStatus(String merchantId) {
        log.debug("Fetching merchant status: {}", merchantId);
        
        return webClient.get()
            .uri("/api/v1/merchants/{merchantId}/status", merchantId)
            .retrieve()
            .bodyToMono(MerchantAccountStatus.class)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .onErrorResume(WebClientResponseException.NotFound.class, e -> {
                log.warn("Merchant not found: {}", merchantId);
                return Mono.just(MerchantAccountStatus.notFound(merchantId));
            })
            .block();
    }
    
    /**
     * Get merchant fee structure
     */
    @CircuitBreaker(name = "merchant-service")
    @Cacheable(value = "merchantFeeStructure", key = "#merchantId")
    public MerchantFeeStructure getMerchantFeeStructure(String merchantId) {
        log.debug("Fetching merchant fee structure: {}", merchantId);
        
        return webClient.get()
            .uri("/api/v1/merchants/{merchantId}/fee-structure", merchantId)
            .retrieve()
            .bodyToMono(MerchantFeeStructure.class)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .onErrorResume(e -> {
                log.error("Error fetching fee structure for merchant {}: {}", merchantId, e.getMessage());
                return Mono.just(MerchantFeeStructure.defaultStructure());
            })
            .block();
    }
    
    /**
     * Update merchant risk profile
     */
    @Retry(name = "merchant-service")
    public void updateRiskProfile(UpdateRiskProfileRequest request) {
        log.debug("Updating risk profile for merchant: {}", request.getMerchantId());
        
        try {
            webClient.put()
                .uri("/api/v1/merchants/{merchantId}/risk-profile", request.getMerchantId())
                .bodyValue(request)
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .block();
            
            log.info("Successfully updated risk profile for merchant: {}", request.getMerchantId());
        } catch (Exception e) {
            log.error("Failed to update risk profile for merchant {}: {}", 
                request.getMerchantId(), e.getMessage());
            // Don't throw - risk profile update is not critical
        }
    }
    
    /**
     * Flag merchant account
     */
    public void flagMerchant(String merchantId, String flag) {
        log.info("Flagging merchant {} with: {}", merchantId, flag);
        
        try {
            FlagMerchantRequest request = FlagMerchantRequest.builder()
                .merchantId(merchantId)
                .flag(flag)
                .source("payment-service")
                .build();
            
            webClient.post()
                .uri("/api/v1/merchants/{merchantId}/flags", merchantId)
                .bodyValue(request)
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .subscribe(
                    result -> log.info("Successfully flagged merchant {} with {}", merchantId, flag),
                    error -> log.error("Failed to flag merchant {}: {}", merchantId, error.getMessage())
                );
        } catch (Exception e) {
            log.error("Error flagging merchant {}: {}", merchantId, e.getMessage());
        }
    }
    
    /**
     * Verify merchant for transaction
     */
    @CircuitBreaker(name = "merchant-service")
    public MerchantVerificationResult verifyMerchantForTransaction(
            String merchantId, 
            VerifyMerchantRequest request) {
        
        log.debug("Verifying merchant {} for transaction", merchantId);
        
        return webClient.post()
            .uri("/api/v1/merchants/{merchantId}/verify-transaction", merchantId)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(MerchantVerificationResult.class)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .onErrorResume(e -> {
                log.error("Error verifying merchant {}: {}", merchantId, e.getMessage());
                return Mono.just(MerchantVerificationResult.failed("Verification service unavailable"));
            })
            .block();
    }
    
    /**
     * Get merchant compliance status
     */
    @Cacheable(value = "merchantCompliance", key = "#merchantId")
    public MerchantComplianceStatus getComplianceStatus(String merchantId) {
        log.debug("Fetching compliance status for merchant: {}", merchantId);
        
        return webClient.get()
            .uri("/api/v1/merchants/{merchantId}/compliance", merchantId)
            .retrieve()
            .bodyToMono(MerchantComplianceStatus.class)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .onErrorResume(e -> {
                log.error("Error fetching compliance status for merchant {}: {}", merchantId, e.getMessage());
                return Mono.just(MerchantComplianceStatus.unknown());
            })
            .block();
    }
    
    /**
     * Fallback method for getMerchantAccount
     */
    public Optional<MerchantAccount> getMerchantAccountFallback(String merchantId, Exception ex) {
        log.warn("Using fallback for merchant account {}: {}", merchantId, ex.getMessage());
        
        // Return a minimal merchant account for degraded service
        MerchantAccount fallback = MerchantAccount.builder()
            .merchantId(merchantId)
            .status("UNKNOWN")
            .isActive(false)
            .build();
        
        return Optional.of(fallback);
    }
    
    /**
     * Custom exception for merchant service communication errors
     */
    public static class MerchantServiceException extends RuntimeException {
        public MerchantServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}