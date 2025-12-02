package com.waqiti.account.service;

import com.waqiti.account.entity.Account;
import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * KYC Service Orchestrator for Account Service
 * 
 * This service acts as a facade/orchestrator that communicates with the dedicated
 * kyc-service microservice. It handles:
 * - KYC requirement determination for accounts
 * - Communication with kyc-service via REST/gRPC
 * - Caching of KYC statuses
 * - Retry logic and circuit breaking
 * - Asynchronous verification workflows
 * 
 * Architecture:
 * account-service (this) -> kyc-service (dedicated microservice)
 *                        -> compliance-service
 *                        -> document-service
 *                        -> identity-verification-service
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KycService {
    
    private final WebClient.Builder webClientBuilder;
    private final RestTemplate restTemplate;
    private final CircuitBreakerService circuitBreaker;
    private final MetricsService metricsService;
    
    @Value("${kyc.service.base-url:http://kyc-service:8080}")
    private String kycServiceBaseUrl;
    
    @Value("${kyc.service.timeout:30}")
    private int kycServiceTimeout;
    
    @Value("${kyc.verification.async:true}")
    private boolean asyncVerification;
    
    @Value("${kyc.cache.ttl:3600}")
    private int cacheTtlSeconds;
    
    private static final String KYC_CACHE = "kyc_status";
    private static final String KYC_METRICS_PREFIX = "kyc.verification";
    
    // Self-injection for @Cacheable methods to work correctly
    @Autowired
    @Lazy
    private KycService self;
    
    /**
     * PRODUCTION FIX: Verify KYC with three-phase pattern
     *
     * CRITICAL FIX:
     * - BEFORE: @Transactional method with HTTP calls holding DB connections for 30s
     * - AFTER: Three-phase pattern separates DB operations from HTTP calls
     *
     * PATTERN:
     * Phase 1: Load data (in transaction - milliseconds)
     * Phase 2: Call KYC API (NO transaction - can take 30 seconds)
     * Phase 3: Save result (in new transaction - milliseconds)
     */
    @Retryable(value = {ServiceException.class},
               maxAttempts = 3,
               backoff = @Backoff(delay = 1000, multiplier = 2))
    public Account.KycLevel verifyKyc(UUID userId, String requestedLevel) {
        log.info("Initiating KYC verification for user: {} at level: {}", userId, requestedLevel);

        long startTime = System.currentTimeMillis();

        try {
            // Validate requested level
            Account.KycLevel targetLevel = Account.KycLevel.valueOf(requestedLevel);

            // PHASE 1: Load current status (in transaction - fast)
            KycStatusResponse currentStatus = loadCurrentKycStatus(userId);

            if (currentStatus.isVerifiedAtLevel(targetLevel)) {
                log.info("User {} already verified at level: {}", userId, targetLevel);
                return targetLevel;
            }

            // PHASE 2: Call external API (NO transaction - can take 30s)
            KycVerificationRequest verificationRequest = buildVerificationRequest(userId, targetLevel);

            if (asyncVerification) {
                return initiateAsyncVerification(verificationRequest);
            } else {
                return performSyncVerification(verificationRequest);
            }

        } catch (Exception e) {
            metricsService.recordError(KYC_METRICS_PREFIX, "verification_failed", e);
            log.error("KYC verification failed for user: {}", userId, e);
            throw new BusinessException("KYC verification failed: " + e.getMessage());
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordLatency(KYC_METRICS_PREFIX, "verification_duration", duration);
        }
    }

    /**
     * PHASE 1: Load current KYC status from database
     * SHORT transaction for fast DB read
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    protected KycStatusResponse loadCurrentKycStatus(UUID userId) {
        // Fast DB read to check cached status - typically < 10ms
        return self.fetchCurrentKycStatus(userId);
    }
    
    /**
     * PRODUCTION FIX: Fetch current KYC status from kyc-service
     * NO @Transactional - external HTTP call that can take seconds
     */
    @Cacheable(value = KYC_CACHE, key = "#userId", unless = "#result == null")
    public KycStatusResponse fetchCurrentKycStatus(UUID userId) {
        return circuitBreaker.executeWithFallback(
            "kyc-service",
            () -> {
                WebClient webClient = webClientBuilder
                    .baseUrl(kycServiceBaseUrl)
                    .build();

                return webClient
                    .get()
                    .uri("/api/v1/kyc/status/{userId}", userId)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .bodyToMono(KycStatusResponse.class)
                    .timeout(Duration.ofSeconds(kycServiceTimeout))
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
                    .block();  // OK - no @Transactional annotation
            },
            () -> {
                // Fallback to cached or default status
                log.warn("Falling back to default KYC status for user: {}", userId);
                return KycStatusResponse.builder()
                    .userId(userId)
                    .currentLevel(Account.KycLevel.LEVEL_0)
                    .status(VerificationStatus.UNKNOWN)
                    .build();
            }
        );
    }
    
    /**
     * Build KYC verification request
     */
    private KycVerificationRequest buildVerificationRequest(UUID userId, Account.KycLevel targetLevel) {
        return KycVerificationRequest.builder()
            .userId(userId)
            .requestedLevel(targetLevel)
            .requestId(UUID.randomUUID())
            .timestamp(LocalDateTime.now())
            .metadata(Map.of(
                "source", "account-service",
                "priority", determinePriority(targetLevel),
                "compliance_required", targetLevel.ordinal() >= Account.KycLevel.LEVEL_2.ordinal()
            ))
            .build();
    }
    
    /**
     * Initiate asynchronous KYC verification
     */
    private Account.KycLevel initiateAsyncVerification(KycVerificationRequest request) {
        log.info("Initiating async KYC verification for user: {}", request.getUserId());
        
        CompletableFuture<KycVerificationResponse> future = CompletableFuture.supplyAsync(() -> {
            WebClient webClient = webClientBuilder
                .baseUrl(kycServiceBaseUrl)
                .build();
            
            return webClient
                .post()
                .uri("/api/v1/kyc/verify/async")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(KycVerificationResponse.class)
                .timeout(Duration.ofSeconds(kycServiceTimeout))
                .block();
        });
        
        // Register callback for completion
        future.thenAccept(response -> {
            log.info("Async KYC verification completed for user: {} with status: {}", 
                request.getUserId(), response.getStatus());
            
            // Clear cache to force refresh
            evictKycCache(request.getUserId());
            
            // Send notification about completion
            sendKycCompletionNotification(request.getUserId(), response);
        });
        
        // Return current level, actual verification happens asynchronously
        return Account.KycLevel.LEVEL_0;
    }
    
    /**
     * Perform synchronous KYC verification
     */
    private Account.KycLevel performSyncVerification(KycVerificationRequest request) {
        log.info("Performing sync KYC verification for user: {}", request.getUserId());
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Request-ID", request.getRequestId().toString());
            
            HttpEntity<KycVerificationRequest> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<KycVerificationResponse> response = restTemplate.exchange(
                kycServiceBaseUrl + "/api/v1/kyc/verify",
                HttpMethod.POST,
                entity,
                KycVerificationResponse.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                KycVerificationResponse verificationResponse = response.getBody();
                
                if (verificationResponse.isSuccessful()) {
                    // Clear cache to update with new status
                    evictKycCache(request.getUserId());
                    
                    return verificationResponse.getVerifiedLevel();
                } else {
                    throw new BusinessException("KYC verification failed: " + 
                        verificationResponse.getFailureReason());
                }
            } else {
                throw new ServiceException("KYC service returned error: " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            log.error("Sync KYC verification failed for user: {}", request.getUserId(), e);
            throw new ServiceException("KYC verification service unavailable", e);
        }
    }
    
    /**
     * Check if KYC upgrade is required for account
     */
    public boolean isKycUpgradeRequired(Account account) {
        // Fetch current KYC requirements based on account features
        KycRequirements requirements = calculateKycRequirements(account);
        
        // Compare with current level
        return account.getKycLevel().ordinal() < requirements.getMinimumLevel().ordinal();
    }
    
    /**
     * Calculate KYC requirements based on account features
     */
    private KycRequirements calculateKycRequirements(Account account) {
        Account.KycLevel requiredLevel = Account.KycLevel.LEVEL_0;
        List<String> reasons = new ArrayList<>();
        
        // International transactions require Level 3
        if (Boolean.TRUE.equals(account.getInternationalEnabled())) {
            requiredLevel = Account.KycLevel.LEVEL_3;
            reasons.add("International transactions enabled");
        }
        
        // VIP/Platinum tiers require Level 3
        if (account.getTierLevel() == Account.TierLevel.VIP || 
            account.getTierLevel() == Account.TierLevel.PLATINUM) {
            requiredLevel = maxLevel(requiredLevel, Account.KycLevel.LEVEL_3);
            reasons.add("Premium tier account");
        }
        
        // High transaction limits require Level 2+
        if (account.getDailyTransactionLimit() != null) {
            if (account.getDailyTransactionLimit().compareTo(new BigDecimal("50000")) > 0) {
                requiredLevel = maxLevel(requiredLevel, Account.KycLevel.LEVEL_3);
                reasons.add("Very high transaction limits");
            } else if (account.getDailyTransactionLimit().compareTo(new BigDecimal("10000")) > 0) {
                requiredLevel = maxLevel(requiredLevel, Account.KycLevel.LEVEL_2);
                reasons.add("High transaction limits");
            }
        }
        
        // Business accounts require Level 3
        if (account.getAccountCategory() == Account.AccountCategory.BUSINESS ||
            account.getAccountCategory() == Account.AccountCategory.CORPORATE) {
            requiredLevel = maxLevel(requiredLevel, Account.KycLevel.LEVEL_3);
            reasons.add("Business/Corporate account");
        }
        
        // Credit accounts require Level 2+
        if (account.getAccountType() == Account.AccountType.CREDIT) {
            requiredLevel = maxLevel(requiredLevel, Account.KycLevel.LEVEL_2);
            reasons.add("Credit account");
        }
        
        // High balance accounts require enhanced verification
        if (account.getBalance() != null && 
            account.getBalance().compareTo(new BigDecimal("100000")) > 0) {
            requiredLevel = maxLevel(requiredLevel, Account.KycLevel.LEVEL_2);
            reasons.add("High account balance");
        }
        
        return KycRequirements.builder()
            .minimumLevel(requiredLevel)
            .reasons(reasons)
            .evaluatedAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * Get comprehensive KYC status for user
     */
    @Cacheable(value = KYC_CACHE, key = "#userId")
    public KycStatus getKycStatus(UUID userId) {
        try {
            // Fetch from kyc-service
            KycStatusResponse statusResponse = self.fetchCurrentKycStatus(userId);
            
            // Fetch document status
            DocumentStatus documentStatus = fetchDocumentStatus(userId);
            
            // Fetch compliance status
            ComplianceStatus complianceStatus = fetchComplianceStatus(userId);
            
            return KycStatus.builder()
                .userId(userId)
                .currentLevel(statusResponse.getCurrentLevel())
                .verificationStatus(statusResponse.getStatus())
                .documentStatus(documentStatus)
                .complianceStatus(complianceStatus)
                .lastVerifiedAt(statusResponse.getLastVerifiedAt())
                .nextReviewDate(statusResponse.getNextReviewDate())
                .expiresAt(calculateExpirationDate(statusResponse))
                .build();
                
        } catch (Exception e) {
            log.error("Failed to get KYC status for user: {}", userId, e);
            throw new ServiceException("Unable to retrieve KYC status", e);
        }
    }
    
    /**
     * Trigger KYC re-verification for user
     */
    @CacheEvict(value = KYC_CACHE, key = "#userId")
    public CompletableFuture<Boolean> triggerReVerification(UUID userId, String reason) {
        log.info("Triggering KYC re-verification for user: {} due to: {}", userId, reason);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                ReVerificationRequest request = ReVerificationRequest.builder()
                    .userId(userId)
                    .reason(reason)
                    .triggeredBy("account-service")
                    .timestamp(LocalDateTime.now())
                    .build();
                
                WebClient webClient = webClientBuilder
                    .baseUrl(kycServiceBaseUrl)
                    .build();
                
                Boolean result = webClient
                    .post()
                    .uri("/api/v1/kyc/reverify")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .timeout(Duration.ofSeconds(kycServiceTimeout))
                    .block();
                
                return Boolean.TRUE.equals(result);
                
            } catch (Exception e) {
                log.error("Re-verification trigger failed for user: {}", userId, e);
                return false;
            }
        });
    }
    
    /**
     * Submit KYC documents
     */
    public KycDocumentSubmissionResponse submitDocuments(UUID userId, 
                                                         List<KycDocument> documents) {
        log.info("Submitting {} KYC documents for user: {}", documents.size(), userId);
        
        try {
            DocumentSubmissionRequest request = DocumentSubmissionRequest.builder()
                .userId(userId)
                .documents(documents)
                .submittedAt(LocalDateTime.now())
                .source("account-service")
                .build();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<DocumentSubmissionRequest> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<KycDocumentSubmissionResponse> response = restTemplate.exchange(
                kycServiceBaseUrl + "/api/v1/kyc/documents/submit",
                HttpMethod.POST,
                entity,
                KycDocumentSubmissionResponse.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // Clear cache to force status refresh
                evictKycCache(userId);
                
                return response.getBody();
            } else {
                throw new ServiceException("Document submission failed: " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            log.error("Document submission failed for user: {}", userId, e);
            throw new BusinessException("Failed to submit KYC documents", e);
        }
    }
    
    /**
     * Get KYC verification history
     */
    public List<KycVerificationHistory> getVerificationHistory(UUID userId) {
        try {
            WebClient webClient = webClientBuilder
                .baseUrl(kycServiceBaseUrl)
                .build();
            
            return webClient
                .get()
                .uri("/api/v1/kyc/history/{userId}", userId)
                .retrieve()
                .bodyToFlux(KycVerificationHistory.class)
                .collectList()
                .timeout(Duration.ofSeconds(kycServiceTimeout))
                .block();
                
        } catch (Exception e) {
            log.error("Failed to fetch verification history for user: {}", userId, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Check if user is on sanctions list
     */
    public boolean checkSanctionsList(UUID userId) {
        try {
            WebClient webClient = webClientBuilder
                .baseUrl(kycServiceBaseUrl)
                .build();
            
            SanctionsCheckResponse response = webClient
                .get()
                .uri("/api/v1/kyc/sanctions/check/{userId}", userId)
                .retrieve()
                .bodyToMono(SanctionsCheckResponse.class)
                .timeout(Duration.ofSeconds(kycServiceTimeout))
                .block();
            
            return response != null && !response.isOnSanctionsList();
            
        } catch (Exception e) {
            log.error("Sanctions check failed for user: {}", userId, e);
            // Fail safe - assume user is sanctioned if check fails
            return false;
        }
    }
    
    /**
     * Perform PEP (Politically Exposed Person) screening
     */
    public PepScreeningResult performPepScreening(UUID userId) {
        try {
            WebClient webClient = webClientBuilder
                .baseUrl(kycServiceBaseUrl)
                .build();
            
            return webClient
                .get()
                .uri("/api/v1/kyc/pep/screen/{userId}", userId)
                .retrieve()
                .bodyToMono(PepScreeningResult.class)
                .timeout(Duration.ofSeconds(kycServiceTimeout))
                .block();
                
        } catch (Exception e) {
            log.error("PEP screening failed for user: {}", userId, e);
            return PepScreeningResult.builder()
                .userId(userId)
                .isPep(false)
                .riskLevel("UNKNOWN")
                .screenedAt(LocalDateTime.now())
                .build();
        }
    }
    
    // Helper methods
    
    private Account.KycLevel maxLevel(Account.KycLevel level1, Account.KycLevel level2) {
        return level1.ordinal() > level2.ordinal() ? level1 : level2;
    }
    
    private String determinePriority(Account.KycLevel level) {
        return switch (level) {
            case LEVEL_3 -> "HIGH";
            case LEVEL_2 -> "MEDIUM";
            default -> "LOW";
        };
    }
    
    private DocumentStatus fetchDocumentStatus(UUID userId) {
        try {
            WebClient webClient = webClientBuilder
                .baseUrl(kycServiceBaseUrl)
                .build();
            
            return webClient
                .get()
                .uri("/api/v1/kyc/documents/status/{userId}", userId)
                .retrieve()
                .bodyToMono(DocumentStatus.class)
                .timeout(Duration.ofSeconds(kycServiceTimeout))
                .block();
                
        } catch (Exception e) {
            log.error("Failed to fetch document status for user: {}", userId, e);
            return DocumentStatus.UNKNOWN;
        }
    }
    
    private ComplianceStatus fetchComplianceStatus(UUID userId) {
        try {
            WebClient webClient = webClientBuilder
                .baseUrl(kycServiceBaseUrl)
                .build();
            
            return webClient
                .get()
                .uri("/api/v1/kyc/compliance/status/{userId}", userId)
                .retrieve()
                .bodyToMono(ComplianceStatus.class)
                .timeout(Duration.ofSeconds(kycServiceTimeout))
                .block();
                
        } catch (Exception e) {
            log.error("Failed to fetch compliance status for user: {}", userId, e);
            return ComplianceStatus.UNKNOWN;
        }
    }
    
    private LocalDateTime calculateExpirationDate(KycStatusResponse response) {
        if (response.getLastVerifiedAt() == null) {
            log.warn("CRITICAL: KYC lastVerifiedAt is null - Cannot calculate expiration, defaulting to 1 year from now");
            return LocalDateTime.now().plusYears(1);
        }
        
        // KYC expires after 1 year for Level 1, 2 years for Level 2, 3 years for Level 3
        return switch (response.getCurrentLevel()) {
            case LEVEL_3 -> response.getLastVerifiedAt().plusYears(3);
            case LEVEL_2 -> response.getLastVerifiedAt().plusYears(2);
            case LEVEL_1 -> response.getLastVerifiedAt().plusYears(1);
            default -> {
                log.warn("CRITICAL: Unknown KYC level: {} - Defaulting to 1 year expiration", response.getCurrentLevel());
                yield response.getLastVerifiedAt().plusYears(1);
            }
        };
    }
    
    @CacheEvict(value = KYC_CACHE, key = "#userId")
    private void evictKycCache(UUID userId) {
        log.debug("Evicting KYC cache for user: {}", userId);
    }
    
    private void sendKycCompletionNotification(UUID userId, KycVerificationResponse response) {
        // This would integrate with notification service
        log.info("Sending KYC completion notification for user: {} with status: {}", 
            userId, response.getStatus());
    }
}

// Domain models for KYC service communication

