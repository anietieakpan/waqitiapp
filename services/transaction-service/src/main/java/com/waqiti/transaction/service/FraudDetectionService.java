package com.waqiti.transaction.service;

import com.waqiti.transaction.client.FraudDetectionServiceClient;
import com.waqiti.transaction.domain.Transaction;
import com.waqiti.transaction.dto.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Lazy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Production-ready fraud detection service with comprehensive capabilities.
 * Implements circuit breaker, retry, caching, and comprehensive fraud analysis.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FraudDetectionService {

    @Lazy
    private final FraudDetectionService self;
    private final FraudDetectionServiceClient fraudDetectionServiceClient;
    private final MeterRegistry meterRegistry;

    @Value("${fraud.detection.enabled:true}")
    private boolean fraudDetectionEnabled;

    @Value("${fraud.detection.high-value-threshold:10000}")
    private BigDecimal highValueThreshold;

    @Value("${fraud.detection.critical-value-threshold:50000}")
    private BigDecimal criticalValueThreshold;

    @Value("${fraud.detection.cache.ttl:300}")
    private int cacheTtlSeconds;

    // Metrics
    private static final String FRAUD_CHECK_COUNTER = "fraud_detection_checks_total";
    private static final String FRAUD_CHECK_TIMER = "fraud_detection_check_duration";
    private static final String FRAUD_DECISION_COUNTER = "fraud_detection_decisions_total";

    /**
     * Comprehensive fraud check for transactions with resilience patterns
     */
    @CircuitBreaker(name = "fraud-detection", fallbackMethod = "checkTransactionFallback")
    @Retry(name = "fraud-detection")
    @TimeLimiter(name = "fraud-detection")
    public CompletableFuture<FraudCheckResult> checkTransactionAsync(Transaction transaction) {
        return CompletableFuture.supplyAsync(() -> checkTransaction(transaction));
    }

    /**
     * Synchronous fraud check for immediate validation
     */
    public FraudCheckResult checkTransaction(Transaction transaction) {
        Timer.Sample sample = Timer.start(meterRegistry);
        incrementCounter(FRAUD_CHECK_COUNTER, "type", "transaction");

        try {
            if (!fraudDetectionEnabled) {
                return createDefaultResult(transaction.getId().toString(), "Fraud detection disabled");
            }

            log.debug("Performing fraud check for transaction: id={}, amount={}, fromUser={}, toUser={}", 
                transaction.getId(), transaction.getAmount(), transaction.getFromUserId(), transaction.getToUserId());

            // Build comprehensive fraud check request
            FraudCheckRequest fraudRequest = buildFraudCheckRequest(transaction);

            // Execute fraud check with metrics
            FraudCheckResponse response = fraudDetectionServiceClient.performFraudCheck(fraudRequest);
            
            // Convert response and add business logic
            FraudCheckResult result = convertToFraudCheckResult(response);
            
            // Record decision metrics
            incrementCounter(FRAUD_DECISION_COUNTER, "decision", result.getDecision().name().toLowerCase());
            
            log.info("Fraud check completed: transactionId={}, decision={}, riskScore={}", 
                transaction.getId(), result.getDecision(), result.getRiskScore());
            
            return result;
            
        } catch (Exception e) {
            log.error("Fraud check failed for transaction: {}", transaction.getId(), e);
            incrementCounter(FRAUD_CHECK_COUNTER, "type", "error");
            return createFailsafeResult(transaction.getId().toString(), e.getMessage());
        } finally {
            sample.stop(Timer.builder(FRAUD_CHECK_TIMER).register(meterRegistry));
        }
    }

    /**
     * Check transfer for fraud indicators (legacy support)
     */
    public FraudCheckResult checkTransfer(TransferRequest request) {
        if (!fraudDetectionEnabled) {
            return createDefaultResult(request.getTransactionId().toString(), "Fraud detection disabled");
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        incrementCounter(FRAUD_CHECK_COUNTER, "type", "transfer");

        try {
            log.debug("Checking fraud for transfer: userId={}, amount={}, transactionId={}", 
                request.getSenderId(), request.getAmount(), request.getTransactionId());

            // Build fraud check request from transfer request
            FraudCheckRequest fraudRequest = buildFraudCheckRequestFromTransfer(request);

            // Call fraud detection service
            FraudCheckResponse response = fraudDetectionServiceClient.performFraudCheck(fraudRequest);
            
            // Convert to internal result format
            FraudCheckResult result = convertToFraudCheckResult(response);
            
            incrementCounter(FRAUD_DECISION_COUNTER, "decision", result.getDecision().name().toLowerCase());
            
            return result;
            
        } catch (Exception e) {
            log.error("Fraud check failed for transfer: {}", request.getTransactionId(), e);
            incrementCounter(FRAUD_CHECK_COUNTER, "type", "error");
            return createFailsafeResult(request.getTransactionId().toString(), e.getMessage());
        } finally {
            sample.stop(Timer.builder(FRAUD_CHECK_TIMER).register(meterRegistry));
        }
    }

    /**
     * Get cached fraud score for a specific transaction
     */
    @Cacheable(value = "fraudScores", key = "#transactionId", unless = "#result == null")
    @CircuitBreaker(name = "fraud-detection", fallbackMethod = "getFraudScoreFallback")
    @Retry(name = "fraud-detection")
    public FraudScoreResponse getFraudScore(String transactionId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            log.debug("Retrieving fraud score for transaction: {}", transactionId);
            FraudScoreResponse response = fraudDetectionServiceClient.getFraudScore(transactionId);
            
            if (response != null) {
                log.debug("Fraud score retrieved: transactionId={}, score={}, category={}", 
                    transactionId, response.getOverallScore(), response.getRiskCategory());
            }
            
            return response;
        } catch (Exception e) {
            log.warn("Failed to get fraud score for transaction: {}", transactionId, e);
            throw e; // Let circuit breaker handle fallback
        } finally {
            sample.stop(Timer.builder("fraud_score_retrieval_duration").register(meterRegistry));
        }
    }
    
    /**
     * Simple fraud score getter for backward compatibility
     */
    public double getSimpleFraudScore(String transactionId) {
        try {
            FraudScoreResponse response = self.getFraudScore(transactionId);
            return response != null && response.getOverallScore() != null ? 
                response.getOverallScore() : 50.0; // Default neutral score
        } catch (Exception e) {
            log.warn("Failed to get simple fraud score for transaction: {}", transactionId, e);
            return 50.0; // Default neutral score
        }
    }

    /**
     * Block a transaction due to fraud with comprehensive tracking
     */
    @CircuitBreaker(name = "fraud-detection", fallbackMethod = "blockTransactionFallback")
    @Retry(name = "fraud-detection")
    public void blockTransaction(String transactionId, String reason) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            log.info("Blocking transaction due to fraud: transactionId={}, reason={}", transactionId, reason);
            
            fraudDetectionServiceClient.blockTransaction(transactionId, reason);
            
            // Record blocking metrics
            incrementCounter("fraud_transaction_blocks_total", "reason", reason);
            
            log.info("Successfully blocked transaction: transactionId={}, reason={}", transactionId, reason);
            
        } catch (Exception e) {
            log.error("Failed to block transaction: transactionId={}, reason={}", transactionId, reason, e);
            incrementCounter("fraud_transaction_block_failures_total", "reason", "service_error");
            throw new FraudServiceException("Failed to block transaction: " + e.getMessage(), e);
        } finally {
            sample.stop(Timer.builder("fraud_transaction_block_duration").register(meterRegistry));
        }
    }

    /**
     * Whitelist a transaction (mark as safe) with audit trail
     */
    @CircuitBreaker(name = "fraud-detection", fallbackMethod = "whitelistTransactionFallback")
    @Retry(name = "fraud-detection")
    public void whitelistTransaction(String transactionId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            log.info("Whitelisting transaction: transactionId={}", transactionId);
            
            fraudDetectionServiceClient.whitelistTransaction(transactionId);
            
            // Record whitelisting metrics
            incrementCounter("fraud_transaction_whitelists_total", "status", "success");
            
            log.info("Successfully whitelisted transaction: transactionId={}", transactionId);
            
        } catch (Exception e) {
            log.error("Failed to whitelist transaction: transactionId={}", transactionId, e);
            incrementCounter("fraud_transaction_whitelist_failures_total", "reason", "service_error");
            throw new FraudServiceException("Failed to whitelist transaction: " + e.getMessage(), e);
        } finally {
            sample.stop(Timer.builder("fraud_transaction_whitelist_duration").register(meterRegistry));
        }
    }

    /**
     * Trigger comprehensive fraud investigation for suspicious activity
     */
    @CircuitBreaker(name = "fraud-detection", fallbackMethod = "triggerInvestigationFallback")
    @Retry(name = "fraud-detection")
    public void triggerInvestigation(String requestId, String customerId, String merchantId, 
            String transactionId, String reason, Double fraudScore, String riskLevel) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            log.info("Triggering fraud investigation - Request: {}, Customer: {}, Merchant: {}, Transaction: {}, Score: {}", 
                requestId, customerId, merchantId, transactionId, fraudScore);
            
            // Determine investigation priority based on fraud score and risk level
            String priority = determinePriority(fraudScore, riskLevel);
            
            FraudInvestigationRequest investigationRequest = FraudInvestigationRequest.builder()
                .investigationId(requestId)
                .customerId(customerId)
                .merchantId(merchantId)
                .transactionId(transactionId)
                .reason(reason)
                .fraudScore(fraudScore)
                .riskLevel(riskLevel)
                .priority(priority)
                .timestamp(LocalDateTime.now())
                .build();
            
            fraudDetectionServiceClient.triggerInvestigation(investigationRequest);
            
            // Record investigation metrics
            incrementCounter("fraud_investigations_triggered_total", 
                "priority", priority, 
                "risk_level", riskLevel != null ? riskLevel : "unknown");
            
            log.info("Successfully triggered fraud investigation: requestId={}, priority={}", requestId, priority);
            
        } catch (Exception e) {
            log.error("Failed to trigger fraud investigation: requestId={}, reason={}", requestId, reason, e);
            incrementCounter("fraud_investigation_failures_total", "reason", "service_error");
            // Don't throw exception for investigation failures - these are async processes
        } finally {
            sample.stop(Timer.builder("fraud_investigation_trigger_duration").register(meterRegistry));
        }
    }
    
    /**
     * Batch fraud check for multiple transactions
     */
    @CircuitBreaker(name = "fraud-detection", fallbackMethod = "batchFraudCheckFallback")
    @Retry(name = "fraud-detection")
    public Map<UUID, FraudCheckResult> batchFraudCheck(List<Transaction> transactions) {
        Timer.Sample sample = Timer.start(meterRegistry);
        Map<UUID, FraudCheckResult> results = new HashMap<>();
        
        try {
            if (!fraudDetectionEnabled || transactions.isEmpty()) {
                return createDefaultBatchResults(transactions);
            }
            
            log.info("Performing batch fraud check for {} transactions", transactions.size());
            
            // Build batch request
            List<FraudCheckRequest> batchRequest = transactions.stream()
                .map(this::buildFraudCheckRequest)
                .toList();
            
            // Execute batch fraud check
            List<FraudCheckResponse> batchResponse = fraudDetectionServiceClient
                .checkTransactionsBatch(batchRequest).getBody();
            
            // Process results
            if (batchResponse != null) {
                for (FraudCheckResponse response : batchResponse) {
                    FraudCheckResult result = convertToFraudCheckResult(response);
                    results.put(response.getTransactionId(), result);
                    
                    incrementCounter(FRAUD_DECISION_COUNTER, "decision", result.getDecision().name().toLowerCase());
                }
            }
            
            incrementCounter("fraud_batch_checks_total", "size", String.valueOf(transactions.size()));
            
            log.info("Batch fraud check completed: processed={}, results={}", 
                transactions.size(), results.size());
            
            return results;
            
        } catch (Exception e) {
            log.error("Batch fraud check failed for {} transactions", transactions.size(), e);
            incrementCounter("fraud_batch_check_failures_total", "reason", "service_error");
            return createFailsafeBatchResults(transactions, e.getMessage());
        } finally {
            sample.stop(Timer.builder("fraud_batch_check_duration").register(meterRegistry));
        }
    }

    // ============ HELPER METHODS ============
    
    /**
     * Build comprehensive fraud check request from transaction
     */
    private FraudCheckRequest buildFraudCheckRequest(Transaction transaction) {
        return FraudCheckRequest.builder()
            .transactionId(transaction.getId())
            .userId(UUID.fromString(transaction.getFromUserId()))
            .amount(transaction.getAmount())
            .currency(transaction.getCurrency())
            .transactionType(transaction.getType().name())
            .recipientId(transaction.getToUserId() != null ? UUID.fromString(transaction.getToUserId()) : null)
            .recipientAccountNumber(transaction.getTargetAccountId())
            .transactionTimestamp(transaction.getCreatedAt())
            .isInternational(isInternationalTransaction(transaction))
            .isHighRiskMerchant(isHighRiskMerchant(transaction))
            .isUnusualAmount(isUnusualAmount(transaction.getAmount()))
            .transactionType(transaction.getType().name())
            .channel(transaction.getChannel())
            .previousRiskScore(transaction.getFraudScore())
            .build();
    }
    
    /**
     * Build fraud check request from transfer request
     */
    private FraudCheckRequest buildFraudCheckRequestFromTransfer(TransferRequest request) {
        return FraudCheckRequest.builder()
            .transactionId(request.getTransactionId())
            .userId(request.getSenderId())
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .transactionType(request.getTransferType() != null ? request.getTransferType() : "TRANSFER")
            .recipientId(request.getRecipientId())
            .transactionTimestamp(request.getRequestTimestamp() != null ? request.getRequestTimestamp() : LocalDateTime.now())
            .isInternational(request.getIsInternational())
            .isFirstTransaction(request.getIsFirstTime())
            .deviceId(request.getDeviceId())
            .deviceFingerprint(request.getDeviceFingerprint())
            .ipAddress(request.getIpAddress())
            .userAgent(request.getUserAgent())
            .channel(request.getChannel())
            .isUnusualAmount(isUnusualAmount(request.getAmount()))
            .additionalMetadata(request.getMetadata() != null ? new HashMap<>(request.getMetadata()) : new HashMap<>())
            .build();
    }
    
    /**
     * Convert fraud check response to internal result format
     */
    private FraudCheckResult convertToFraudCheckResult(FraudCheckResponse response) {
        FraudDecision decision = mapToInternalDecision(response.getDecision());
        
        return FraudCheckResult.builder()
            .transactionId(response.getTransactionId().toString())
            .userId(response.getUserId() != null ? response.getUserId().toString() : null)
            .riskScore(response.getRiskScore() != null ? response.getRiskScore().intValue() : 0)
            .riskLevel(response.getRiskLevel())
            .decision(decision)
            .blocked(response.shouldBlock())
            .reason(buildReasonFromResponse(response))
            .riskFactors(extractRiskFactors(response))
            .recommendations(response.getRecommendedActions() != null ? response.getRecommendedActions() : Collections.emptyList())
            .checkDate(response.getCheckTimestamp() != null ? response.getCheckTimestamp() : LocalDateTime.now())
            .mlConfidenceScore(response.getMlConfidenceScore())
            .requiresManualReview(response.getRequiresManualReview())
            .requiresAdditionalVerification(response.needsVerification())
            .verificationMethod(response.getVerificationMethod())
            .build();
    }
    
    /**
     * Determine investigation priority based on fraud score and risk level
     */
    private String determinePriority(Double fraudScore, String riskLevel) {
        if (fraudScore != null && fraudScore >= 90.0) return "CRITICAL";
        if (fraudScore != null && fraudScore >= 75.0) return "HIGH";
        if ("CRITICAL".equalsIgnoreCase(riskLevel)) return "CRITICAL";
        if ("HIGH".equalsIgnoreCase(riskLevel)) return "HIGH";
        if (fraudScore != null && fraudScore >= 50.0) return "MEDIUM";
        return "LOW";
    }
    
    /**
     * Check if transaction is international
     */
    private boolean isInternationalTransaction(Transaction transaction) {
        // Logic to determine if transaction is international
        // This would typically check source and target countries
        return transaction.getMetadata() != null && 
               "true".equals(transaction.getMetadata().get("isInternational"));
    }
    
    /**
     * Check if merchant is high risk
     */
    private boolean isHighRiskMerchant(Transaction transaction) {
        // Logic to determine high-risk merchants
        return transaction.getMerchantId() != null && 
               transaction.getMetadata() != null &&
               "true".equals(transaction.getMetadata().get("highRiskMerchant"));
    }
    
    /**
     * Check if amount is unusual
     */
    private boolean isUnusualAmount(BigDecimal amount) {
        return amount != null && amount.compareTo(highValueThreshold) > 0;
    }
    
    private FraudCheckResult createDefaultResult(String transactionId, String reason) {
        return FraudCheckResult.builder()
            .transactionId(transactionId)
            .riskScore(0)
            .riskLevel("VERY_LOW")
            .decision(FraudDecision.APPROVE)
            .blocked(false)
            .reason(reason)
            .riskFactors(Collections.emptyList())
            .recommendations(List.of("PROCEED_WITH_NORMAL_PROCESSING"))
            .checkDate(LocalDateTime.now())
            .build();
    }

    private FraudCheckResult createFailsafeResult(String transactionId, String errorMessage) {
        // SECURITY FIX: Fail-closed - BLOCK ALL transactions when fraud service unavailable
        return FraudCheckResult.builder()
            .transactionId(transactionId)
            .riskScore(95) // Critical risk when service is unavailable
            .riskLevel("CRITICAL")
            .decision(FraudDecision.DECLINE)
            .blocked(true) // ALWAYS block when service is down for security (fail-closed)
            .reason("SECURITY: Fraud detection service unavailable - transaction blocked (fail-closed): " + errorMessage)
            .riskFactors(List.of("FRAUD_SERVICE_UNAVAILABLE", "FAIL_CLOSED_SECURITY"))
            .recommendations(List.of("MANUAL_REVIEW_REQUIRED", "RETRY_AFTER_SERVICE_RECOVERY"))
            .checkDate(LocalDateTime.now())
            .requiresManualReview(true)
            .build();
    }

    // ============ FALLBACK METHODS ============
    
    /**
     * Fallback method for transaction fraud check
     */
    public CompletableFuture<FraudCheckResult> checkTransactionFallback(Transaction transaction, Exception ex) {
        log.warn("Fraud detection fallback activated for transaction: {}", transaction.getId(), ex);
        incrementCounter("fraud_detection_fallbacks_total", "method", "checkTransaction");
        return CompletableFuture.completedFuture(createFailsafeResult(transaction.getId().toString(), ex.getMessage()));
    }
    
    /**
     * Fallback method for fraud score retrieval
     */
    public FraudScoreResponse getFraudScoreFallback(String transactionId, Exception ex) {
        log.error("CRITICAL SECURITY: Fraud score fallback activated - BLOCKING transaction: {} (fail-closed)", transactionId, ex);
        incrementCounter("fraud_detection_fallbacks_total", "method", "getFraudScore");

        // SECURITY FIX: Fail-closed - return CRITICAL risk score to block transaction
        return FraudScoreResponse.builder()
            .transactionId(UUID.fromString(transactionId))
            .overallScore(95.0) // Critical risk score - blocks transaction
            .riskCategory("CRITICAL")
            .confidence(1.0) // High confidence in blocking for security
            .scoreCalculationTime(LocalDateTime.now())
            .scoringEngine("FALLBACK_FAIL_CLOSED")
            .reason("Fraud detection service unavailable - transaction blocked for security (fail-closed)")
            .blocked(true)
            .build();
    }
    
    /**
     * Fallback method for transaction blocking
     */
    public void blockTransactionFallback(String transactionId, String reason, Exception ex) {
        log.error("Failed to block transaction {}, will attempt manual intervention: {}", transactionId, reason, ex);
        incrementCounter("fraud_detection_fallbacks_total", "method", "blockTransaction");
        // Could trigger manual notification here
    }
    
    /**
     * Fallback method for transaction whitelisting
     */
    public void whitelistTransactionFallback(String transactionId, Exception ex) {
        log.error("Failed to whitelist transaction {}, manual intervention may be required", transactionId, ex);
        incrementCounter("fraud_detection_fallbacks_total", "method", "whitelistTransaction");
    }
    
    /**
     * Fallback method for investigation triggering
     */
    public void triggerInvestigationFallback(String requestId, String customerId, String merchantId, 
            String transactionId, String reason, Double fraudScore, String riskLevel, Exception ex) {
        log.error("Failed to trigger fraud investigation for request: {}, will attempt manual escalation", requestId, ex);
        incrementCounter("fraud_detection_fallbacks_total", "method", "triggerInvestigation");
    }
    
    /**
     * Fallback method for batch fraud check
     */
    public Map<UUID, FraudCheckResult> batchFraudCheckFallback(List<Transaction> transactions, Exception ex) {
        log.warn("Batch fraud check fallback activated for {} transactions", transactions.size(), ex);
        incrementCounter("fraud_detection_fallbacks_total", "method", "batchFraudCheck");
        return createFailsafeBatchResults(transactions, ex.getMessage());
    }
    
    // ============ UTILITY METHODS ============
    
    private void incrementCounter(String counterName, String... tags) {
        if (meterRegistry != null) {
            Counter.builder(counterName)
                .tags(tags)
                .register(meterRegistry)
                .increment();
        }
    }
    
    private FraudDecision mapToInternalDecision(FraudCheckResponse.FraudDecision externalDecision) {
        if (externalDecision == null) return FraudDecision.REVIEW;
        
        return switch (externalDecision) {
            case APPROVE -> FraudDecision.APPROVE;
            case DECLINE -> FraudDecision.DECLINE;
            case REVIEW -> FraudDecision.REVIEW;
            case CHALLENGE -> FraudDecision.CHALLENGE;
            case HOLD -> FraudDecision.HOLD;
            case ESCALATE -> FraudDecision.ESCALATE;
        };
    }
    
    private String buildReasonFromResponse(FraudCheckResponse response) {
        StringBuilder reason = new StringBuilder();
        
        if (response.getTriggeredRules() != null && !response.getTriggeredRules().isEmpty()) {
            reason.append("Triggered rules: ").append(String.join(", ", response.getTriggeredRules()));
        }
        
        if (response.getFraudIndicators() != null && !response.getFraudIndicators().isEmpty()) {
            if (reason.length() > 0) reason.append("; ");
            reason.append("Fraud indicators detected: ").append(response.getFraudIndicators().size());
        }
        
        return reason.length() > 0 ? reason.toString() : "Standard fraud check completed";
    }
    
    private List<String> extractRiskFactors(FraudCheckResponse response) {
        List<String> factors = new ArrayList<>();
        
        if (response.getRiskFactors() != null) {
            response.getRiskFactors().forEach((key, value) -> {
                if (value > 0.5) { // Only include significant risk factors
                    factors.add(key + "=" + String.format("%.2f", value));
                }
            });
        }
        
        return factors.isEmpty() ? Collections.singletonList("NO_SIGNIFICANT_RISK_FACTORS") : factors;
    }
    
    private Map<UUID, FraudCheckResult> createDefaultBatchResults(List<Transaction> transactions) {
        return transactions.stream().collect(
            java.util.stream.Collectors.toMap(
                Transaction::getId,
                tx -> createDefaultResult(tx.getId().toString(), "Fraud detection disabled")
            )
        );
    }
    
    private Map<UUID, FraudCheckResult> createFailsafeBatchResults(List<Transaction> transactions, String errorMessage) {
        return transactions.stream().collect(
            java.util.stream.Collectors.toMap(
                Transaction::getId,
                tx -> createFailsafeResult(tx.getId().toString(), errorMessage)
            )
        );
    }

    // ============ DTOs ============
    
    public enum FraudDecision {
        APPROVE,
        DECLINE,
        REVIEW,
        CHALLENGE,
        HOLD,
        ESCALATE
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FraudCheckResult {
        private String transactionId;
        private String userId;
        private Integer riskScore;
        private String riskLevel;
        private FraudDecision decision;
        private Boolean blocked;
        private String reason;
        private List<String> riskFactors;
        private List<String> recommendations;
        private LocalDateTime checkDate;
        private Double mlConfidenceScore;
        private Boolean requiresManualReview;
        private Boolean requiresAdditionalVerification;
        private String verificationMethod;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FraudInvestigationRequest {
        private String investigationId;
        private String customerId;
        private String merchantId;
        private String transactionId;
        private String reason;
        private Double fraudScore;
        private String riskLevel;
        private String priority;
        private LocalDateTime timestamp;
    }
    
    /**
     * Custom exception for fraud service errors
     */
    public static class FraudServiceException extends RuntimeException {
        public FraudServiceException(String message) {
            super(message);
        }
        
        public FraudServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}