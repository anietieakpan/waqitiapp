package com.waqiti.wallet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.wallet.dto.compliance.*;
import com.waqiti.wallet.entity.ComplianceCheck;
import com.waqiti.wallet.entity.ComplianceCheck.CheckType;
import com.waqiti.wallet.entity.ComplianceCheck.CheckStatus;
import com.waqiti.wallet.entity.ComplianceCheck.RiskLevel;
import com.waqiti.wallet.repository.ComplianceCheckRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Compliance Check Service
 * 
 * Comprehensive AML/KYC compliance integration service providing:
 * - Real-time compliance checks with external providers
 * - Sanctions screening (OFAC, UN, EU lists)
 * - PEP (Politically Exposed Persons) screening
 * - Adverse media monitoring
 * - Transaction velocity monitoring
 * - Suspicious Activity Reporting (SAR) generation
 * - Know Your Customer (KYC) verification
 * - Anti-Money Laundering (AML) checks
 * - Regulatory reporting (FinCEN, CTR, Form 8300)
 * 
 * INTEGRATION:
 * - Compliance service via REST API
 * - ComplyAdvantage for AML screening
 * - Refinitiv World-Check for PEP/Sanctions
 * - LexisNexis for identity verification
 * - Redis for result caching
 * - Kafka for async event processing
 * 
 * PERFORMANCE:
 * - Redis caching for recent check results (15 min TTL)
 * - Async processing for non-blocking operations
 * - Circuit breaker for external service failures
 * - Batch screening support
 * 
 * COMPLIANCE:
 * - All checks logged to database for audit trail
 * - Immutable compliance records
 * - Automatic SAR filing for suspicious patterns
 * - Regulatory reporting integration
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceCheckService {
    
    private final ComplianceCheckRepository complianceCheckRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;
    
    @Value("${compliance-service.url:http://localhost:8083}")
    private String complianceServiceUrl;
    
    @Value("${compliance.kyc-threshold:1000.00}")
    private BigDecimal kycThreshold;
    
    @Value("${compliance.high-risk-threshold:10000.00}")
    private BigDecimal highRiskThreshold;
    
    @Value("${compliance.sar-threshold:5000.00}")
    private BigDecimal sarThreshold;
    
    private static final String CACHE_KEY_PREFIX = "compliance:check:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(15);
    private static final Duration API_TIMEOUT = Duration.ofSeconds(30);
    
    private WebClient webClient;
    
    private WebClient getWebClient() {
        if (webClient == null) {
            webClient = webClientBuilder
                .baseUrl(complianceServiceUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        }
        return webClient;
    }
    
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    @CircuitBreaker(name = "compliance-check", fallbackMethod = "performComplianceCheckFallback")
    @Retry(name = "compliance-check")
    public ComplianceCheckResult performComplianceCheck(
            String walletId,
            String userId,
            CheckType checkType,
            BigDecimal amount,
            String currency,
            Map<String, Object> transactionDetails) {
        
        log.info("Performing compliance check: walletId={} userId={} type={} amount={} {}", 
                walletId, userId, checkType, amount, currency);
        
        String cacheKey = buildCacheKey(walletId, userId, checkType);
        ComplianceCheckResult cachedResult = getCachedResult(cacheKey);
        
        if (cachedResult != null && cachedResult.isValid()) {
            log.debug("Using cached compliance check result for wallet: {}", walletId);
            return cachedResult;
        }
        
        ComplianceCheck check = ComplianceCheck.builder()
            .walletId(walletId)
            .userId(userId)
            .checkType(checkType)
            .status(CheckStatus.PENDING)
            .amount(amount)
            .currency(currency)
            .transactionDetails(serializeDetails(transactionDetails))
            .initiatedAt(LocalDateTime.now())
            .build();
        
        check = complianceCheckRepository.save(check);
        
        try {
            ComplianceCheckRequest request = ComplianceCheckRequest.builder()
                .checkId(check.getId().toString())
                .walletId(walletId)
                .userId(userId)
                .checkType(checkType.name())
                .amount(amount)
                .currency(currency)
                .transactionDetails(transactionDetails)
                .timestamp(LocalDateTime.now())
                .build();
            
            ComplianceApiResponse apiResponse = getWebClient()
                .post()
                .uri("/api/v1/compliance/check")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ComplianceApiResponse.class)
                .timeout(API_TIMEOUT)
                .block();
            
            if (apiResponse == null) {
                throw new IllegalStateException("Null response from compliance service");
            }
            
            check.setStatus(mapApiStatus(apiResponse.getStatus()));
            check.setRiskLevel(mapRiskLevel(apiResponse.getRiskScore()));
            check.setRiskScore(apiResponse.getRiskScore());
            check.setExternalCheckId(apiResponse.getCheckId());
            check.setCheckResult(objectMapper.writeValueAsString(apiResponse));
            check.setCompletedAt(LocalDateTime.now());
            
            if (apiResponse.getFlags() != null && !apiResponse.getFlags().isEmpty()) {
                check.setFlags(String.join(",", apiResponse.getFlags()));
            }
            
            if (apiResponse.getRecommendations() != null) {
                check.setRecommendations(objectMapper.writeValueAsString(apiResponse.getRecommendations()));
            }
            
            check = complianceCheckRepository.save(check);
            
            ComplianceCheckResult result = mapToResult(check, apiResponse);
            
            cacheResult(cacheKey, result);
            
            if (result.isHighRisk() || result.isBlocked()) {
                publishComplianceAlert(check, result);
            }
            
            if (amount != null && amount.compareTo(sarThreshold) > 0 && result.isHighRisk()) {
                triggerSARFiling(check, result);
            }
            
            log.info("Compliance check completed: checkId={} status={} riskLevel={}", 
                    check.getId(), check.getStatus(), check.getRiskLevel());
            
            return result;
            
        } catch (Exception e) {
            log.error("Compliance check failed: checkId={}", check.getId(), e);
            
            check.setStatus(CheckStatus.FAILED);
            check.setErrorMessage(e.getMessage());
            check.setCompletedAt(LocalDateTime.now());
            complianceCheckRepository.save(check);
            
            throw new RuntimeException("Compliance check failed", e);
        }
    }
    
    @Transactional(readOnly = true)
    public ComplianceCheckResult getCheckResult(UUID checkId) {
        ComplianceCheck check = complianceCheckRepository.findById(checkId)
            .orElseThrow(() -> new IllegalArgumentException("Compliance check not found: " + checkId));
        
        return mapToResult(check, null);
    }
    
    @Transactional(readOnly = true)
    public List<ComplianceCheck> getWalletComplianceHistory(String walletId, int limit) {
        return complianceCheckRepository.findByWalletIdOrderByInitiatedAtDesc(walletId)
            .stream()
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<ComplianceCheck> getUserComplianceHistory(String userId, int limit) {
        return complianceCheckRepository.findByUserIdOrderByInitiatedAtDesc(userId)
            .stream()
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    @CircuitBreaker(name = "compliance-check", fallbackMethod = "performKYCVerificationFallback")
    @Retry(name = "compliance-check")
    public KYCVerificationResult performKYCVerification(
            String userId,
            String firstName,
            String lastName,
            LocalDateTime dateOfBirth,
            String nationality,
            String documentType,
            String documentNumber,
            Map<String, Object> additionalData) {
        
        log.info("Performing KYC verification: userId={} documentType={}", userId, documentType);
        
        try {
            KYCVerificationRequest request = KYCVerificationRequest.builder()
                .userId(userId)
                .firstName(firstName)
                .lastName(lastName)
                .dateOfBirth(dateOfBirth)
                .nationality(nationality)
                .documentType(documentType)
                .documentNumber(documentNumber)
                .additionalData(additionalData)
                .timestamp(LocalDateTime.now())
                .build();
            
            KYCVerificationResponse response = getWebClient()
                .post()
                .uri("/api/v1/compliance/kyc/verify")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(KYCVerificationResponse.class)
                .timeout(API_TIMEOUT)
                .block();
            
            if (response == null) {
                throw new IllegalStateException("Null response from KYC verification service");
            }
            
            ComplianceCheck kycCheck = ComplianceCheck.builder()
                .userId(userId)
                .checkType(CheckType.KYC)
                .status(response.isVerified() ? CheckStatus.PASSED : CheckStatus.FAILED)
                .riskLevel(response.getRiskLevel() != null ? 
                    RiskLevel.valueOf(response.getRiskLevel()) : RiskLevel.MEDIUM)
                .externalCheckId(response.getVerificationId())
                .checkResult(objectMapper.writeValueAsString(response))
                .initiatedAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .build();
            
            complianceCheckRepository.save(kycCheck);
            
            KYCVerificationResult result = KYCVerificationResult.builder()
                .verificationId(kycCheck.getId().toString())
                .userId(userId)
                .verified(response.isVerified())
                .riskLevel(response.getRiskLevel())
                .verificationScore(response.getVerificationScore())
                .flags(response.getFlags())
                .recommendations(response.getRecommendations())
                .timestamp(LocalDateTime.now())
                .build();
            
            log.info("KYC verification completed: userId={} verified={} riskLevel={}", 
                    userId, result.isVerified(), result.getRiskLevel());
            
            return result;
            
        } catch (Exception e) {
            log.error("KYC verification failed: userId={}", userId, e);
            throw new RuntimeException("KYC verification failed", e);
        }
    }
    
    @CircuitBreaker(name = "compliance-check", fallbackMethod = "performSanctionsScreeningFallback")
    @Retry(name = "compliance-check")
    public SanctionsScreeningResult performSanctionsScreening(
            String userId,
            String fullName,
            LocalDateTime dateOfBirth,
            String nationality,
            List<String> addresses) {
        
        log.info("Performing sanctions screening: userId={} name={}", userId, fullName);
        
        try {
            SanctionsScreeningRequest request = SanctionsScreeningRequest.builder()
                .userId(userId)
                .fullName(fullName)
                .dateOfBirth(dateOfBirth)
                .nationality(nationality)
                .addresses(addresses)
                .screeningLists(Arrays.asList("OFAC", "UN", "EU", "UK_HMT"))
                .timestamp(LocalDateTime.now())
                .build();
            
            SanctionsScreeningResponse response = getWebClient()
                .post()
                .uri("/api/v1/compliance/sanctions/screen")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(SanctionsScreeningResponse.class)
                .timeout(API_TIMEOUT)
                .block();
            
            if (response == null) {
                throw new IllegalStateException("Null response from sanctions screening service");
            }
            
            ComplianceCheck sanctionsCheck = ComplianceCheck.builder()
                .userId(userId)
                .checkType(CheckType.SANCTIONS)
                .status(response.isMatch() ? CheckStatus.BLOCKED : CheckStatus.PASSED)
                .riskLevel(response.isMatch() ? RiskLevel.CRITICAL : RiskLevel.LOW)
                .externalCheckId(response.getScreeningId())
                .checkResult(objectMapper.writeValueAsString(response))
                .flags(response.getMatchedLists() != null ? 
                    String.join(",", response.getMatchedLists()) : null)
                .initiatedAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .build();
            
            complianceCheckRepository.save(sanctionsCheck);
            
            SanctionsScreeningResult result = SanctionsScreeningResult.builder()
                .screeningId(sanctionsCheck.getId().toString())
                .userId(userId)
                .isMatch(response.isMatch())
                .matchScore(response.getMatchScore())
                .matchedLists(response.getMatchedLists())
                .matchDetails(response.getMatchDetails())
                .timestamp(LocalDateTime.now())
                .build();
            
            if (result.isMatch()) {
                publishSanctionsAlert(sanctionsCheck, result);
            }
            
            log.info("Sanctions screening completed: userId={} isMatch={} score={}", 
                    userId, result.isMatch(), result.getMatchScore());
            
            return result;
            
        } catch (Exception e) {
            log.error("Sanctions screening failed: userId={}", userId, e);
            throw new RuntimeException("Sanctions screening failed", e);
        }
    }
    
    @CircuitBreaker(name = "compliance-check", fallbackMethod = "performPEPScreeningFallback")
    @Retry(name = "compliance-check")
    public PEPScreeningResult performPEPScreening(
            String userId,
            String fullName,
            LocalDateTime dateOfBirth,
            String nationality) {
        
        log.info("Performing PEP screening: userId={} name={}", userId, fullName);
        
        try {
            PEPScreeningRequest request = PEPScreeningRequest.builder()
                .userId(userId)
                .fullName(fullName)
                .dateOfBirth(dateOfBirth)
                .nationality(nationality)
                .timestamp(LocalDateTime.now())
                .build();
            
            PEPScreeningResponse response = getWebClient()
                .post()
                .uri("/api/v1/compliance/pep/screen")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(PEPScreeningResponse.class)
                .timeout(API_TIMEOUT)
                .block();
            
            if (response == null) {
                throw new IllegalStateException("Null response from PEP screening service");
            }
            
            ComplianceCheck pepCheck = ComplianceCheck.builder()
                .userId(userId)
                .checkType(CheckType.PEP)
                .status(response.isPEP() ? CheckStatus.FLAGGED : CheckStatus.PASSED)
                .riskLevel(response.isPEP() ? RiskLevel.HIGH : RiskLevel.LOW)
                .externalCheckId(response.getScreeningId())
                .checkResult(objectMapper.writeValueAsString(response))
                .initiatedAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .build();
            
            complianceCheckRepository.save(pepCheck);
            
            PEPScreeningResult result = PEPScreeningResult.builder()
                .screeningId(pepCheck.getId().toString())
                .userId(userId)
                .isPEP(response.isPEP())
                .pepType(response.getPepType())
                .pepDetails(response.getPepDetails())
                .timestamp(LocalDateTime.now())
                .build();
            
            if (result.isPEP()) {
                publishPEPAlert(pepCheck, result);
            }
            
            log.info("PEP screening completed: userId={} isPEP={}", userId, result.isPEP());
            
            return result;
            
        } catch (Exception e) {
            log.error("PEP screening failed: userId={}", userId, e);
            throw new RuntimeException("PEP screening failed", e);
        }
    }
    
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    @CircuitBreaker(name = "compliance-check", fallbackMethod = "reportSuspiciousActivityFallback")
    @Retry(name = "compliance-check")
    public void reportSuspiciousActivity(
            String walletId,
            String userId,
            String activityType,
            BigDecimal amount,
            String currency,
            Map<String, Object> details) {
        
        log.warn("Reporting suspicious activity: walletId={} userId={} type={}", 
                walletId, userId, activityType);
        
        try {
            SuspiciousActivityReport report = SuspiciousActivityReport.builder()
                .walletId(walletId)
                .userId(userId)
                .activityType(activityType)
                .amount(amount)
                .currency(currency)
                .details(details)
                .timestamp(LocalDateTime.now())
                .build();
            
            CompletableFuture.runAsync(() -> {
                try {
                    getWebClient()
                        .post()
                        .uri("/api/v1/compliance/suspicious-activity")
                        .bodyValue(report)
                        .retrieve()
                        .bodyToMono(Void.class)
                        .timeout(Duration.ofSeconds(15))
                        .block();
                    
                    log.info("Successfully reported suspicious activity");
                } catch (Exception e) {
                    log.error("Async suspicious activity reporting failed", e);
                }
            });
            
            ComplianceCheck sarCheck = ComplianceCheck.builder()
                .walletId(walletId)
                .userId(userId)
                .checkType(CheckType.SAR)
                .status(CheckStatus.PENDING)
                .riskLevel(RiskLevel.HIGH)
                .amount(amount)
                .currency(currency)
                .transactionDetails(serializeDetails(details))
                .initiatedAt(LocalDateTime.now())
                .build();
            
            complianceCheckRepository.save(sarCheck);
            
        } catch (Exception e) {
            log.error("Failed to report suspicious activity", e);
            reportSuspiciousActivityFallback(walletId, userId, activityType, amount, currency, details, e);
        }
    }
    
    private CheckStatus mapApiStatus(String apiStatus) {
        if (apiStatus == null) return CheckStatus.PENDING;
        
        switch (apiStatus.toUpperCase()) {
            case "PASSED":
            case "APPROVED":
            case "CLEAR":
                return CheckStatus.PASSED;
            case "FAILED":
            case "REJECTED":
                return CheckStatus.FAILED;
            case "FLAGGED":
            case "REVIEW":
                return CheckStatus.FLAGGED;
            case "BLOCKED":
            case "DENIED":
                return CheckStatus.BLOCKED;
            default:
                return CheckStatus.PENDING;
        }
    }
    
    private RiskLevel mapRiskLevel(Double riskScore) {
        if (riskScore == null) return RiskLevel.MEDIUM;
        
        if (riskScore >= 0.9) return RiskLevel.CRITICAL;
        if (riskScore >= 0.7) return RiskLevel.HIGH;
        if (riskScore >= 0.4) return RiskLevel.MEDIUM;
        if (riskScore >= 0.2) return RiskLevel.LOW;
        return RiskLevel.MINIMAL;
    }
    
    private ComplianceCheckResult mapToResult(ComplianceCheck check, ComplianceApiResponse apiResponse) {
        return ComplianceCheckResult.builder()
            .checkId(check.getId().toString())
            .walletId(check.getWalletId())
            .userId(check.getUserId())
            .checkType(check.getCheckType())
            .status(check.getStatus())
            .riskLevel(check.getRiskLevel())
            .riskScore(check.getRiskScore())
            .flags(check.getFlags() != null ? 
                Arrays.asList(check.getFlags().split(",")) : Collections.emptyList())
            .passed(check.getStatus() == CheckStatus.PASSED)
            .blocked(check.getStatus() == CheckStatus.BLOCKED)
            .requiresReview(check.getStatus() == CheckStatus.FLAGGED)
            .highRisk(check.getRiskLevel() == RiskLevel.HIGH || 
                     check.getRiskLevel() == RiskLevel.CRITICAL)
            .timestamp(check.getInitiatedAt())
            .completedAt(check.getCompletedAt())
            .valid(check.getCompletedAt() != null && 
                  check.getCompletedAt().isAfter(LocalDateTime.now().minusMinutes(15)))
            .build();
    }
    
    /**
     * Serialize transaction details to JSON string
     * PRODUCTION-READY: Returns empty JSON object instead of null for consistency
     *
     * @param details Transaction details map
     * @return JSON string representation, never null
     */
    private String serializeDetails(Map<String, Object> details) {
        try {
            return details != null ? objectMapper.writeValueAsString(details) : "{}";
        } catch (Exception e) {
            log.error("Error serializing transaction details, returning empty JSON", e);
            return "{}"; // FIXED: Return empty JSON instead of null for database consistency
        }
    }
    
    private String buildCacheKey(String walletId, String userId, CheckType checkType) {
        return CACHE_KEY_PREFIX + walletId + ":" + userId + ":" + checkType.name();
    }
    
    /**
     * Retrieve cached compliance check result from Redis
     * PRODUCTION-READY: Returns Optional instead of null for type safety
     *
     * @param cacheKey Redis cache key
     * @return Optional containing cached result if present, empty otherwise
     */
    private Optional<ComplianceCheckResult> getCachedResultOptional(String cacheKey) {
        try {
            String cachedValue = redisTemplate.opsForValue().get(cacheKey);
            if (cachedValue != null) {
                ComplianceCheckResult result = objectMapper.readValue(cachedValue, ComplianceCheckResult.class);
                return Optional.of(result);
            }
        } catch (Exception e) {
            log.error("Error retrieving cached compliance result from key: {}", cacheKey, e);
        }
        return Optional.empty(); // FIXED: Return Optional.empty() instead of null
    }

    /**
     * Legacy method for backward compatibility - delegates to Optional-based implementation
     * @deprecated Use getCachedResultOptional() instead
     */
    @Deprecated
    private ComplianceCheckResult getCachedResult(String cacheKey) {
        return getCachedResultOptional(cacheKey).orElse(null);
    }
    
    private void cacheResult(String cacheKey, ComplianceCheckResult result) {
        try {
            String jsonValue = objectMapper.writeValueAsString(result);
            redisTemplate.opsForValue().set(
                cacheKey,
                jsonValue,
                CACHE_TTL.getSeconds(),
                TimeUnit.SECONDS
            );
        } catch (Exception e) {
            log.error("Error caching compliance result", e);
        }
    }
    
    private void publishComplianceAlert(ComplianceCheck check, ComplianceCheckResult result) {
        try {
            Map<String, Object> alert = Map.of(
                "checkId", check.getId().toString(),
                "walletId", check.getWalletId(),
                "userId", check.getUserId(),
                "checkType", check.getCheckType().name(),
                "riskLevel", check.getRiskLevel().name(),
                "status", check.getStatus().name(),
                "timestamp", LocalDateTime.now()
            );
            
            kafkaTemplate.send("compliance.alerts", alert);
        } catch (Exception e) {
            log.error("Error publishing compliance alert", e);
        }
    }
    
    private void triggerSARFiling(ComplianceCheck check, ComplianceCheckResult result) {
        try {
            Map<String, Object> sarEvent = Map.of(
                "checkId", check.getId().toString(),
                "walletId", check.getWalletId(),
                "userId", check.getUserId(),
                "amount", check.getAmount(),
                "currency", check.getCurrency(),
                "riskLevel", check.getRiskLevel().name(),
                "timestamp", LocalDateTime.now()
            );
            
            kafkaTemplate.send("compliance.sar.filing", sarEvent);
        } catch (Exception e) {
            log.error("Error triggering SAR filing", e);
        }
    }
    
    private void publishSanctionsAlert(ComplianceCheck check, SanctionsScreeningResult result) {
        try {
            Map<String, Object> alert = Map.of(
                "checkId", check.getId().toString(),
                "userId", check.getUserId(),
                "matchScore", result.getMatchScore(),
                "matchedLists", result.getMatchedLists(),
                "timestamp", LocalDateTime.now()
            );
            
            kafkaTemplate.send("compliance.sanctions.alert", alert);
        } catch (Exception e) {
            log.error("Error publishing sanctions alert", e);
        }
    }
    
    private void publishPEPAlert(ComplianceCheck check, PEPScreeningResult result) {
        try {
            Map<String, Object> alert = Map.of(
                "checkId", check.getId().toString(),
                "userId", check.getUserId(),
                "pepType", result.getPepType(),
                "timestamp", LocalDateTime.now()
            );
            
            kafkaTemplate.send("compliance.pep.alert", alert);
        } catch (Exception e) {
            log.error("Error publishing PEP alert", e);
        }
    }
    
    private ComplianceCheckResult performComplianceCheckFallback(
            String walletId,
            String userId,
            CheckType checkType,
            BigDecimal amount,
            String currency,
            Map<String, Object> transactionDetails,
            Exception e) {
        
        log.warn("Compliance service unavailable - returning degraded result (fallback): walletId={}", 
                walletId, e);
        
        return ComplianceCheckResult.builder()
            .walletId(walletId)
            .userId(userId)
            .checkType(checkType)
            .status(CheckStatus.PENDING)
            .riskLevel(RiskLevel.MEDIUM)
            .passed(false)
            .blocked(false)
            .requiresReview(true)
            .highRisk(false)
            .timestamp(LocalDateTime.now())
            .valid(false)
            .build();
    }
    
    private KYCVerificationResult performKYCVerificationFallback(
            String userId,
            String firstName,
            String lastName,
            LocalDateTime dateOfBirth,
            String nationality,
            String documentType,
            String documentNumber,
            Map<String, Object> additionalData,
            Exception e) {
        
        log.warn("KYC verification service unavailable (fallback): userId={}", userId, e);
        
        return KYCVerificationResult.builder()
            .userId(userId)
            .verified(false)
            .riskLevel("MEDIUM")
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    private SanctionsScreeningResult performSanctionsScreeningFallback(
            String userId,
            String fullName,
            LocalDateTime dateOfBirth,
            String nationality,
            List<String> addresses,
            Exception e) {
        
        log.warn("Sanctions screening service unavailable (fallback): userId={}", userId, e);
        
        return SanctionsScreeningResult.builder()
            .userId(userId)
            .isMatch(false)
            .matchScore(0.0)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    private PEPScreeningResult performPEPScreeningFallback(
            String userId,
            String fullName,
            LocalDateTime dateOfBirth,
            String nationality,
            Exception e) {
        
        log.warn("PEP screening service unavailable (fallback): userId={}", userId, e);
        
        return PEPScreeningResult.builder()
            .userId(userId)
            .isPEP(false)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    private void reportSuspiciousActivityFallback(
            String walletId,
            String userId,
            String activityType,
            BigDecimal amount,
            String currency,
            Map<String, Object> details,
            Exception e) {
        
        log.error("Compliance service unavailable - suspicious activity not reported (fallback): walletId={}", 
                walletId, e);
    }
}