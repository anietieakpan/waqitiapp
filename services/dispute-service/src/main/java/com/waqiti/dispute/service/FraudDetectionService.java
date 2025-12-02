package com.waqiti.dispute.service;

import com.waqiti.dispute.client.FraudDetectionServiceClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Fraud Detection Service Integration
 *
 * Analyzes fraud risk for disputed transactions
 * Provides ML-powered fraud scores to inform dispute resolution decisions
 *
 * @author Waqiti Platform Team
 * @version 1.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FraudDetectionService {

    private final FraudDetectionServiceClient fraudDetectionClient;

    @Value("${service.auth.token:default-service-token}")
    private String serviceAuthToken;

    /**
     * Analyze fraud risk for a transaction
     * Results are cached to avoid repeated ML inference
     */
    @CircuitBreaker(name = "fraudDetectionService", fallbackMethod = "analyzeFraudRiskFallback")
    @Retry(name = "fraudDetectionService")
    @Cacheable(value = "fraud-analysis", key = "#transactionId", unless = "#result == null")
    public FraudDetectionServiceClient.FraudAnalysisResponse analyzeFraudRisk(UUID transactionId) {
        log.debug("Analyzing fraud risk for transaction: {}", transactionId);

        try {
            FraudDetectionServiceClient.FraudAnalysisResponse response =
                    fraudDetectionClient.analyzeFraudRisk(transactionId, serviceAuthToken);

            log.info("Fraud analysis complete - Transaction: {}, Score: {}, Risk: {}",
                    transactionId, response.getFraudScore(), response.getRiskLevel());

            return response;

        } catch (Exception e) {
            log.error("Failed to analyze fraud risk for transaction: {}", transactionId, e);
            throw new FraudAnalysisException("Fraud analysis failed", e);
        }
    }

    /**
     * Perform comprehensive fraud check for dispute
     */
    @CircuitBreaker(name = "fraudDetectionService", fallbackMethod = "checkDisputeFraudFallback")
    @Retry(name = "fraudDetectionService")
    public FraudDetectionServiceClient.DisputeFraudCheckResponse checkDisputeFraud(
            FraudDetectionServiceClient.DisputeFraudCheckRequest request) {

        log.debug("Performing fraud check for dispute: {}", request.getDisputeId());

        try {
            FraudDetectionServiceClient.DisputeFraudCheckResponse response =
                    fraudDetectionClient.checkDisputeFraud(request, serviceAuthToken);

            log.info("Dispute fraud check complete - Dispute: {}, Score: {}, Recommendation: {}",
                    request.getDisputeId(), response.getFraudScore(), response.getRecommendation());

            if (Boolean.TRUE.equals(response.getSuspectedFraud())) {
                log.warn("FRAUD ALERT: Suspected fraud detected in dispute {} - Score: {}, Type: {}",
                        request.getDisputeId(), response.getFraudScore(), response.getFraudType());
            }

            return response;

        } catch (Exception e) {
            log.error("Failed to perform fraud check for dispute: {}", request.getDisputeId(), e);
            throw new FraudAnalysisException("Dispute fraud check failed", e);
        }
    }

    // Circuit Breaker Fallback Methods

    /**
     * Fallback for fraud risk analysis when service unavailable
     * Returns default LOW risk to allow dispute processing to continue
     */
    private FraudDetectionServiceClient.FraudAnalysisResponse analyzeFraudRiskFallback(
            UUID transactionId, Exception e) {

        log.warn("CIRCUIT BREAKER FALLBACK: Fraud detection unavailable for transaction: {}, Error: {}",
                transactionId, e.getMessage());

        // Return default response with LOW risk - manual review required
        FraudDetectionServiceClient.FraudAnalysisResponse fallbackResponse =
                new FraudDetectionServiceClient.FraudAnalysisResponse();
        fallbackResponse.setFraudScore(0.0);
        fallbackResponse.setRiskLevel("LOW");
        fallbackResponse.setMessage("Fraud detection service unavailable - Default low risk assigned");

        log.info("Returning default LOW fraud risk for transaction: {}", transactionId);
        return fallbackResponse;
    }

    /**
     * Fallback for dispute fraud check when service unavailable
     */
    private FraudDetectionServiceClient.DisputeFraudCheckResponse checkDisputeFraudFallback(
            FraudDetectionServiceClient.DisputeFraudCheckRequest request, Exception e) {

        log.warn("CIRCUIT BREAKER FALLBACK: Fraud detection unavailable for dispute: {}, Error: {}",
                request.getDisputeId(), e.getMessage());

        // Return default response allowing dispute to proceed with manual review
        FraudDetectionServiceClient.DisputeFraudCheckResponse fallbackResponse =
                new FraudDetectionServiceClient.DisputeFraudCheckResponse();
        fallbackResponse.setFraudScore(0.0);
        fallbackResponse.setSuspectedFraud(false);
        fallbackResponse.setRecommendation("MANUAL_REVIEW");
        fallbackResponse.setMessage("Fraud detection service unavailable - Manual review required");

        log.warn("Dispute {} requires manual fraud review due to service unavailability", request.getDisputeId());
        return fallbackResponse;
    }

    /**
     * Fraud Analysis Exception
     */
    public static class FraudAnalysisException extends RuntimeException {
        public FraudAnalysisException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
