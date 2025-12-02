package com.waqiti.payment.service;

import com.waqiti.payment.entity.FundRelease;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class MerchantService {
    
    private final WebClient.Builder webClientBuilder;
    
    @Value("${merchant-service.url:http://localhost:8086}")
    private String merchantServiceUrl;
    
    @Value("${merchant.default-daily-limit:100000.00}")
    private BigDecimal defaultDailyLimit;
    
    @Value("${merchant.high-value-threshold:100000.00}")
    private BigDecimal highValueThreshold;
    
    private WebClient webClient;
    
    private WebClient getWebClient() {
        if (webClient == null) {
            webClient = webClientBuilder.baseUrl(merchantServiceUrl).build();
        }
        return webClient;
    }
    
    @CircuitBreaker(name = "merchant-service", fallbackMethod = "isActiveFallback")
    @Retry(name = "merchant-service")
    public boolean isActive(String merchantId) {
        log.debug("Checking if merchant is active: {}", merchantId);
        
        try {
            MerchantStatusResponse response = getWebClient().get()
                .uri("/api/v1/merchants/{merchantId}/status", merchantId)
                .retrieve()
                .bodyToMono(MerchantStatusResponse.class)
                .timeout(Duration.ofSeconds(5))
                .block();
            
            return response != null && "ACTIVE".equals(response.getStatus());
            
        } catch (Exception e) {
            log.error("Failed to check merchant status for {}", merchantId, e);
            return isActiveFallback(merchantId, e);
        }
    }
    
    private boolean isActiveFallback(String merchantId, Exception e) {
        log.warn("Using fallback for merchant status check: {}", merchantId);
        return false;
    }
    
    @CircuitBreaker(name = "merchant-service", fallbackMethod = "isUnderInvestigationFallback")
    @Retry(name = "merchant-service")
    public boolean isUnderInvestigation(String merchantId) {
        log.debug("Checking if merchant is under investigation: {}", merchantId);
        
        try {
            MerchantStatusResponse response = getWebClient().get()
                .uri("/api/v1/merchants/{merchantId}/compliance-status", merchantId)
                .retrieve()
                .bodyToMono(MerchantStatusResponse.class)
                .timeout(Duration.ofSeconds(5))
                .block();
            
            return response != null && response.isUnderInvestigation();
            
        } catch (Exception e) {
            log.error("Failed to check investigation status for {}", merchantId, e);
            return isUnderInvestigationFallback(merchantId, e);
        }
    }
    
    private boolean isUnderInvestigationFallback(String merchantId, Exception e) {
        log.warn("Using fallback for investigation status check: {}", merchantId);
        return true;
    }
    
    @CircuitBreaker(name = "merchant-service", fallbackMethod = "hasEnhancedVerificationFallback")
    @Retry(name = "merchant-service")
    public boolean hasEnhancedVerification(String merchantId) {
        log.debug("Checking enhanced verification for merchant: {}", merchantId);
        
        try {
            MerchantVerificationResponse response = getWebClient().get()
                .uri("/api/v1/merchants/{merchantId}/verification", merchantId)
                .retrieve()
                .bodyToMono(MerchantVerificationResponse.class)
                .timeout(Duration.ofSeconds(5))
                .block();
            
            return response != null && response.isEnhancedVerified();
            
        } catch (Exception e) {
            log.error("Failed to check enhanced verification for {}", merchantId, e);
            return hasEnhancedVerificationFallback(merchantId, e);
        }
    }
    
    private boolean hasEnhancedVerificationFallback(String merchantId, Exception e) {
        log.warn("Using fallback for enhanced verification check: {}", merchantId);
        return false;
    }
    
    @CircuitBreaker(name = "merchant-service", fallbackMethod = "getDailyReleaseLimitFallback")
    @Retry(name = "merchant-service")
    public BigDecimal getDailyReleaseLimit(String merchantId) {
        log.debug("Getting daily release limit for merchant: {}", merchantId);
        
        try {
            MerchantLimitsResponse response = getWebClient().get()
                .uri("/api/v1/merchants/{merchantId}/limits", merchantId)
                .retrieve()
                .bodyToMono(MerchantLimitsResponse.class)
                .timeout(Duration.ofSeconds(5))
                .block();
            
            return response != null ? response.getDailyReleaseLimit() : defaultDailyLimit;
            
        } catch (Exception e) {
            log.error("Failed to get daily release limit for {}", merchantId, e);
            return getDailyReleaseLimitFallback(merchantId, e);
        }
    }
    
    private BigDecimal getDailyReleaseLimitFallback(String merchantId, Exception e) {
        log.warn("Using fallback for daily release limit: {}", merchantId);
        return defaultDailyLimit;
    }
    
    @CircuitBreaker(name = "merchant-service", fallbackMethod = "isTrustedFallback")
    @Retry(name = "merchant-service")
    public boolean isTrusted(String merchantId) {
        log.debug("Checking if merchant is trusted: {}", merchantId);
        
        try {
            MerchantTrustResponse response = getWebClient().get()
                .uri("/api/v1/merchants/{merchantId}/trust-level", merchantId)
                .retrieve()
                .bodyToMono(MerchantTrustResponse.class)
                .timeout(Duration.ofSeconds(5))
                .block();
            
            return response != null && "TRUSTED".equals(response.getTrustLevel());
            
        } catch (Exception e) {
            log.error("Failed to check trust level for {}", merchantId, e);
            return isTrustedFallback(merchantId, e);
        }
    }
    
    private boolean isTrustedFallback(String merchantId, Exception e) {
        log.warn("Using fallback for trust level check: {}", merchantId);
        return false;
    }
    
    @CircuitBreaker(name = "merchant-service", fallbackMethod = "isHighVolumeFallback")
    @Retry(name = "merchant-service")
    public boolean isHighVolume(String merchantId) {
        log.debug("Checking if merchant is high volume: {}", merchantId);
        
        try {
            MerchantProfileResponse response = getWebClient().get()
                .uri("/api/v1/merchants/{merchantId}/profile", merchantId)
                .retrieve()
                .bodyToMono(MerchantProfileResponse.class)
                .timeout(Duration.ofSeconds(5))
                .block();
            
            return response != null && response.isHighVolume();
            
        } catch (Exception e) {
            log.error("Failed to check high volume status for {}", merchantId, e);
            return isHighVolumeFallback(merchantId, e);
        }
    }
    
    private boolean isHighVolumeFallback(String merchantId, Exception e) {
        log.warn("Using fallback for high volume check: {}", merchantId);
        return false;
    }
    
    @CircuitBreaker(name = "merchant-service", fallbackMethod = "isHighRiskFallback")
    @Retry(name = "merchant-service")
    public boolean isHighRisk(String merchantId) {
        log.debug("Checking if merchant is high risk: {}", merchantId);
        
        try {
            MerchantRiskResponse response = getWebClient().get()
                .uri("/api/v1/merchants/{merchantId}/risk-assessment", merchantId)
                .retrieve()
                .bodyToMono(MerchantRiskResponse.class)
                .timeout(Duration.ofSeconds(5))
                .block();
            
            return response != null && "HIGH".equals(response.getRiskLevel());
            
        } catch (Exception e) {
            log.error("Failed to check risk level for {}", merchantId, e);
            return isHighRiskFallback(merchantId, e);
        }
    }
    
    private boolean isHighRiskFallback(String merchantId, Exception e) {
        log.warn("Using fallback for risk level check: {}", merchantId);
        return true;
    }
    
    @CircuitBreaker(name = "merchant-service", fallbackMethod = "hasWebhookEnabledFallback")
    @Retry(name = "merchant-service")
    public boolean hasWebhookEnabled(String merchantId) {
        log.debug("Checking webhook configuration for merchant: {}", merchantId);
        
        try {
            MerchantWebhookResponse response = getWebClient().get()
                .uri("/api/v1/merchants/{merchantId}/webhook-config", merchantId)
                .retrieve()
                .bodyToMono(MerchantWebhookResponse.class)
                .timeout(Duration.ofSeconds(5))
                .block();
            
            return response != null && response.isWebhookEnabled();
            
        } catch (Exception e) {
            log.error("Failed to check webhook config for {}", merchantId, e);
            return hasWebhookEnabledFallback(merchantId, e);
        }
    }
    
    private boolean hasWebhookEnabledFallback(String merchantId, Exception e) {
        log.warn("Using fallback for webhook check: {}", merchantId);
        return false;
    }
    
    @CircuitBreaker(name = "merchant-service", fallbackMethod = "sendWebhookFallback")
    @Retry(name = "merchant-service")
    public void sendWebhook(String merchantId, String eventType, FundRelease fundRelease) {
        log.info("Sending webhook to merchant: {} for event: {}", merchantId, eventType);
        
        try {
            Map<String, Object> webhookPayload = new HashMap<>();
            webhookPayload.put("eventType", eventType);
            webhookPayload.put("releaseId", fundRelease.getReleaseId());
            webhookPayload.put("merchantId", merchantId);
            webhookPayload.put("amount", fundRelease.getAmount());
            webhookPayload.put("currency", fundRelease.getCurrency());
            webhookPayload.put("status", fundRelease.getStatus().toString());
            webhookPayload.put("timestamp", fundRelease.getLastUpdated());
            
            getWebClient().post()
                .uri("/api/v1/merchants/{merchantId}/webhook/deliver", merchantId)
                .bodyValue(webhookPayload)
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(Duration.ofSeconds(10))
                .block();
            
            log.info("Webhook sent successfully to merchant: {}", merchantId);
            
        } catch (Exception e) {
            log.error("Failed to send webhook to merchant {}", merchantId, e);
            sendWebhookFallback(merchantId, eventType, fundRelease, e);
        }
    }
    
    private void sendWebhookFallback(String merchantId, String eventType, FundRelease fundRelease, Exception e) {
        log.warn("Webhook delivery failed for merchant: {} - will be retried later", merchantId);
    }
    
    @CircuitBreaker(name = "merchant-service", fallbackMethod = "updateAvailableBalanceFallback")
    @Retry(name = "merchant-service")
    public void updateAvailableBalance(String merchantId, BigDecimal amount) {
        log.info("Updating available balance for merchant: {} by {}", merchantId, amount);
        
        try {
            Map<String, Object> request = Map.of(
                "merchantId", merchantId,
                "amount", amount.toString(),
                "operation", amount.compareTo(BigDecimal.ZERO) >= 0 ? "CREDIT" : "DEBIT"
            );
            
            getWebClient().post()
                .uri("/api/v1/merchants/{merchantId}/balance/update", merchantId)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(Duration.ofSeconds(10))
                .block();
            
            log.info("Balance updated successfully for merchant: {}", merchantId);
            
        } catch (Exception e) {
            log.error("Failed to update balance for merchant {}", merchantId, e);
            updateAvailableBalanceFallback(merchantId, amount, e);
        }
    }
    
    private void updateAvailableBalanceFallback(String merchantId, BigDecimal amount, Exception e) {
        log.error("Balance update failed for merchant: {} - CRITICAL: Manual reconciliation required", merchantId);
    }
    
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class MerchantStatusResponse {
        private String status;
        private boolean underInvestigation;
    }
    
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class MerchantVerificationResponse {
        private boolean enhancedVerified;
        private String verificationType;
    }
    
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class MerchantLimitsResponse {
        private BigDecimal dailyReleaseLimit;
        private BigDecimal monthlyReleaseLimit;
    }
    
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class MerchantTrustResponse {
        private String trustLevel;
        private Integer trustScore;
    }
    
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class MerchantProfileResponse {
        private boolean highVolume;
        private String tier;
    }
    
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class MerchantRiskResponse {
        private String riskLevel;
        private Integer riskScore;
    }
    
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class MerchantWebhookResponse {
        private boolean webhookEnabled;
        private String webhookUrl;
    }

    public boolean isAuthorizedForBatchPayments(String merchantId) {
        log.debug("Checking if merchant is authorized for batch payments: {}", merchantId);
        return isActive(merchantId) && !isHighRisk(merchantId);
    }
}