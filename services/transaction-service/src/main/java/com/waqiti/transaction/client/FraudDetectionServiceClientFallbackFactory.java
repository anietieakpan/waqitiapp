package com.waqiti.transaction.client;

import com.waqiti.transaction.dto.FraudCheckRequest;
import com.waqiti.transaction.dto.FraudCheckResponse;
import com.waqiti.transaction.dto.FraudScoreResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

/**
 * Production-ready fallback factory for Fraud Detection Service.
 * Implements comprehensive fallback strategies with observability and graceful degradation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FraudDetectionServiceClientFallbackFactory implements FallbackFactory<FraudDetectionServiceClient> {

    private final MeterRegistry meterRegistry;
    
    // Metrics counters for monitoring fallback usage
    private static final String FALLBACK_COUNTER = "fraud_detection_fallback_total";
    private static final String FALLBACK_METHOD_TAG = "method";

    @Override
    public FraudDetectionServiceClient create(Throwable cause) {
        return new FraudDetectionServiceClientFallback(cause, meterRegistry);
    }

    /**
     * Fallback implementation with intelligent fallback strategies
     */
    @Slf4j
    @RequiredArgsConstructor
    private static class FraudDetectionServiceClientFallback implements FraudDetectionServiceClient {
        
        private final Throwable cause;
        private final MeterRegistry meterRegistry;

        @Override
        public FraudCheckResponse performFraudCheck(FraudCheckRequest request) {
            logFallback("performFraudCheck", cause);
            incrementFallbackCounter("performFraudCheck");

            // Implement intelligent fallback based on request characteristics
            FraudCheckResponse.FraudDecision decision = determineFallbackDecision(request);
            Double riskScore = calculateFallbackRiskScore(request);

            return FraudCheckResponse.builder()
                .transactionId(request.getTransactionId())
                .checkId(UUID.randomUUID())
                .checkTimestamp(LocalDateTime.now())
                .decision(decision)
                .riskScore(riskScore)
                .riskLevel(mapScoreToRiskLevel(riskScore))
                .fraudIndicators(Collections.emptyList())
                .triggeredRules(Collections.singletonList("FALLBACK_MODE"))
                .recommendedActions(Collections.singletonList("Manual review required - fraud service unavailable"))
                .requiresManualReview(true)
                .requiresAdditionalVerification(riskScore > 50.0)
                .verificationMethod(riskScore > 75.0 ? "BIOMETRIC" : "SMS_OTP")
                .mlConfidenceScore(0.0) // No ML processing in fallback
                .processingTimeMs(1L) // Instant fallback processing
                .processingNode("FALLBACK")
                .checkVersion("FALLBACK-1.0")
                .build();
        }

        @Override
        public FraudScoreResponse getFraudScore(String transactionId) {
            logFallback("getFraudScore", cause);
            incrementFallbackCounter("getFraudScore");

            return FraudScoreResponse.builder()
                .transactionId(UUID.fromString(transactionId))
                .overallScore(50.0) // Neutral score in fallback mode
                .riskCategory("MEDIUM")
                .confidence(0.1) // Low confidence in fallback
                .components(FraudScoreResponse.ScoreComponents.builder()
                    .behavioralScore(50.0)
                    .velocityScore(50.0)
                    .deviceScore(50.0)
                    .locationScore(50.0)
                    .networkScore(50.0)
                    .transactionPatternScore(50.0)
                    .paymentMethodScore(50.0)
                    .merchantScore(50.0)
                    .timeBasedScore(50.0)
                    .amountBasedScore(50.0)
                    .build())
                .scoreTrend("STABLE")
                .modelVersion("FALLBACK")
                .approvalThreshold(25.0)
                .reviewThreshold(50.0)
                .declineThreshold(90.0)
                .riskMitigationSuggestions(Collections.singletonList("Manual review - fraud service unavailable"))
                .requiresEnhancedMonitoring(true)
                .recommendedReviewPeriodDays(1)
                .scoreCalculationTime(LocalDateTime.now())
                .calculationTimeMs(1L)
                .scoringEngine("FALLBACK")
                .build();
        }

        @Override
        public void blockTransaction(String transactionId, String reason) {
            logFallback("blockTransaction", cause);
            incrementFallbackCounter("blockTransaction");
            
            // In fallback mode, log the blocking request but don't fail
            log.warn("Fallback: Transaction {} block requested with reason: {} - fraud service unavailable", 
                    transactionId, reason);
        }

        @Override
        public void whitelistTransaction(String transactionId) {
            logFallback("whitelistTransaction", cause);
            incrementFallbackCounter("whitelistTransaction");
            
            // In fallback mode, log the whitelist request
            log.warn("Fallback: Transaction {} whitelist requested - fraud service unavailable", transactionId);
        }

        @Override
        public void triggerInvestigation(com.waqiti.transaction.service.FraudDetectionService.FraudInvestigationRequest request) {
            logFallback("triggerInvestigation", cause);
            incrementFallbackCounter("triggerInvestigation");
            
            // In fallback mode, log the investigation request
            log.warn("Fallback: Fraud investigation requested - fraud service unavailable. Request: {}", request);
        }

        /**
         * Determine fallback decision based on request characteristics
         */
        private FraudCheckResponse.FraudDecision determineFallbackDecision(FraudCheckRequest request) {
            if (request == null) {
                return FraudCheckResponse.FraudDecision.REVIEW;
            }

            // Apply conservative fallback logic
            if (request.getAmount() != null && request.getAmount().doubleValue() > 10000.0) {
                return FraudCheckResponse.FraudDecision.REVIEW; // High amount - review required
            }

            if (Boolean.TRUE.equals(request.getIsInternational())) {
                return FraudCheckResponse.FraudDecision.CHALLENGE; // International - additional verification
            }

            if (Boolean.TRUE.equals(request.getIsFirstTransaction())) {
                return FraudCheckResponse.FraudDecision.CHALLENGE; // First transaction - verify user
            }

            if (request.getFailedAttempts() != null && request.getFailedAttempts() > 2) {
                return FraudCheckResponse.FraudDecision.REVIEW; // Multiple failures - manual review
            }

            // Default to approve with monitoring for low-risk scenarios
            return FraudCheckResponse.FraudDecision.APPROVE;
        }

        /**
         * Calculate fallback risk score based on available request data
         */
        private Double calculateFallbackRiskScore(FraudCheckRequest request) {
            if (request == null) {
                return 75.0; // High risk when no data available
            }

            double baseScore = 25.0; // Start with low risk
            
            // Adjust based on available indicators
            if (request.getAmount() != null && request.getAmount().doubleValue() > 5000.0) {
                baseScore += 20.0;
            }
            
            if (Boolean.TRUE.equals(request.getIsInternational())) {
                baseScore += 15.0;
            }
            
            if (Boolean.TRUE.equals(request.getIsFirstTransaction())) {
                baseScore += 10.0;
            }
            
            if (request.getFailedAttempts() != null && request.getFailedAttempts() > 0) {
                baseScore += (request.getFailedAttempts() * 10.0);
            }
            
            if (Boolean.TRUE.equals(request.getIsHighRiskMerchant())) {
                baseScore += 25.0;
            }

            return Math.min(baseScore, 100.0);
        }

        /**
         * Map numeric risk score to risk level string
         */
        private String mapScoreToRiskLevel(Double score) {
            if (score == null) return "UNKNOWN";
            if (score < 25.0) return "LOW";
            if (score < 50.0) return "MEDIUM";
            if (score < 75.0) return "HIGH";
            return "CRITICAL";
        }

        /**
         * Log fallback usage with detailed context
         */
        private void logFallback(String method, Throwable cause) {
            log.warn("FraudDetectionService fallback activated for method: {} due to: {}", 
                    method, cause != null ? cause.getMessage() : "Unknown error", cause);
        }

        /**
         * Increment fallback metrics counter
         */
        private void incrementFallbackCounter(String method) {
            if (meterRegistry != null) {
                Counter.builder(FALLBACK_COUNTER)
                    .tag(FALLBACK_METHOD_TAG, method)
                    .tag("service", "fraud-detection")
                    .description("Number of fallback activations for fraud detection service")
                    .register(meterRegistry)
                    .increment();
            }
        }
    }
}