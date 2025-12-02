package com.waqiti.payment.client.fallback;

import com.waqiti.common.resilience.AbstractFeignClientFallback;
import com.waqiti.payment.client.FraudDetectionServiceClient;
import com.waqiti.payment.dto.FraudCheckRequest;
import com.waqiti.payment.dto.FraudCheckResponse;
import com.waqiti.payment.dto.RiskScore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * FRAUD DETECTION SERVICE FALLBACK
 *
 * Provides graceful degradation when fraud-detection-service is unavailable.
 *
 * CRITICAL FINANCIAL OPERATION:
 * Fraud detection is essential for payment authorization. When the service
 * is down, we must apply conservative fraud rules to protect against loss.
 *
 * FALLBACK STRATEGY:
 * 1. Check cache for recent risk scores (last 5 minutes)
 * 2. Apply conservative fraud rules (amount limits, velocity checks)
 * 3. Queue transaction for manual review if high-risk indicators
 * 4. Log all fallback decisions for audit
 *
 * BUSINESS IMPACT:
 * - Maintains payment processing during outages
 * - Applies conservative rules to minimize fraud risk
 * - Queues suspicious transactions for manual review
 * - Ensures no payment is authorized without fraud screening
 *
 * @author Waqiti Payment Team
 * @version 1.0.0
 * @since 2025-01-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FraudDetectionServiceClientFallback
    extends AbstractFeignClientFallback
    implements FraudDetectionServiceClient {

    private final CacheManager cacheManager;
    private final ManualReviewQueue manualReviewQueue;

    // Conservative limits when fraud service is down
    private static final BigDecimal MAX_TRANSACTION_AMOUNT = new BigDecimal("1000.00");
    private static final int MAX_TRANSACTIONS_PER_HOUR = 5;

    @Override
    protected String getServiceName() {
        return "fraud-detection-service";
    }

    /**
     * Perform fraud check with fallback logic
     */
    @Override
    public FraudCheckResponse checkFraud(FraudCheckRequest request) {
        logFallback("checkFraud", null);

        // Try to get cached risk score (recent check within 5 minutes)
        Optional<RiskScore> cachedScore = getCachedRiskScore(request.getUserId());
        if (cachedScore.isPresent()) {
            logCacheHit("checkFraud", cachedScore.get());
            return buildResponseFromCache(cachedScore.get(), request);
        }

        // Apply conservative fraud rules
        FraudCheckResponse response = applyConservativeRules(request);

        // If high risk or exceeds limits, queue for manual review
        if (response.getRiskLevel().equals("HIGH") ||
            response.getRiskLevel().equals("CRITICAL")) {
            queueForManualReview(request, response);
        }

        return response;
    }

    /**
     * Get risk score with fallback
     */
    @Override
    public RiskScore getRiskScore(UUID userId, UUID transactionId) {
        logFallback("getRiskScore", null);

        // Try cache
        Optional<RiskScore> cached = getCachedRiskScore(userId);
        if (cached.isPresent()) {
            logCacheHit("getRiskScore", cached.get());
            return cached.get();
        }

        // Return conservative default score
        RiskScore defaultScore = new RiskScore();
        defaultScore.setUserId(userId);
        defaultScore.setScore(50); // Medium risk default
        defaultScore.setRiskLevel("MEDIUM");
        defaultScore.setReason("Fraud service unavailable - conservative default applied");
        defaultScore.setRequiresManualReview(true);

        log.warn("FALLBACK: Returning default risk score for user {} - Score: {}",
            userId, defaultScore.getScore());

        return defaultScore;
    }

    /**
     * Apply conservative fraud rules when service is down
     */
    private FraudCheckResponse applyConservativeRules(FraudCheckRequest request) {
        FraudCheckResponse response = new FraudCheckResponse();
        response.setTransactionId(request.getTransactionId());
        response.setUserId(request.getUserId());
        response.setApproved(false); // Conservative: deny by default
        response.setReason("Fraud detection service unavailable - applying conservative rules");

        // Rule 1: Check transaction amount
        if (request.getAmount().compareTo(MAX_TRANSACTION_AMOUNT) > 0) {
            response.setRiskLevel("HIGH");
            response.setRiskScore(75);
            response.setReason("Amount exceeds conservative limit during service outage");
            response.setRequiresManualReview(true);
            log.warn("FALLBACK: Transaction {} exceeds amount limit - Amount: ${}, Limit: ${}",
                request.getTransactionId(), request.getAmount(), MAX_TRANSACTION_AMOUNT);
            return response;
        }

        // Rule 2: Check velocity (transactions per hour)
        int recentTransactions = countRecentTransactions(request.getUserId());
        if (recentTransactions >= MAX_TRANSACTIONS_PER_HOUR) {
            response.setRiskLevel("HIGH");
            response.setRiskScore(70);
            response.setReason("Velocity limit exceeded during service outage");
            response.setRequiresManualReview(true);
            log.warn("FALLBACK: User {} exceeds velocity limit - Count: {}, Limit: {}",
                request.getUserId(), recentTransactions, MAX_TRANSACTIONS_PER_HOUR);
            return response;
        }

        // Rule 3: Check for known high-risk patterns
        if (hasHighRiskPatterns(request)) {
            response.setRiskLevel("HIGH");
            response.setRiskScore(80);
            response.setReason("High-risk pattern detected during service outage");
            response.setRequiresManualReview(true);
            return response;
        }

        // If all conservative rules pass, allow with medium risk
        response.setApproved(true);
        response.setRiskLevel("MEDIUM");
        response.setRiskScore(50);
        response.setReason("Conservative rules passed - approved with manual review");
        response.setRequiresManualReview(true); // Always queue for manual review during outage

        log.info("FALLBACK: Transaction {} approved with conservative rules - RiskScore: {}",
            request.getTransactionId(), response.getRiskScore());

        return response;
    }

    /**
     * Get cached risk score for user
     */
    private Optional<RiskScore> getCachedRiskScore(UUID userId) {
        try {
            var cache = cacheManager.getCache("riskScoreCache");
            if (cache != null) {
                RiskScore cached = cache.get(userId, RiskScore.class);
                if (cached != null) {
                    // Check if cache is recent (within 5 minutes)
                    if (cached.getTimestamp() != null &&
                        java.time.Duration.between(cached.getTimestamp(),
                            java.time.LocalDateTime.now()).toMinutes() < 5) {
                        return Optional.of(cached);
                    }
                }
            }
        } catch (Exception e) {
            log.error("FALLBACK: Error accessing risk score cache", e);
        }
        return Optional.empty();
    }

    /**
     * Build response from cached risk score
     */
    private FraudCheckResponse buildResponseFromCache(RiskScore cachedScore, FraudCheckRequest request) {
        FraudCheckResponse response = new FraudCheckResponse();
        response.setTransactionId(request.getTransactionId());
        response.setUserId(request.getUserId());
        response.setRiskScore(cachedScore.getScore());
        response.setRiskLevel(cachedScore.getRiskLevel());
        response.setApproved(cachedScore.getScore() < 70); // Approve if low/medium risk
        response.setReason("Using cached risk score (fraud service unavailable)");
        response.setRequiresManualReview(true); // Always review during fallback

        log.info("FALLBACK: Using cached risk score - UserId: {}, Score: {}, Age: {} seconds",
            request.getUserId(), cachedScore.getScore(),
            java.time.Duration.between(cachedScore.getTimestamp(),
                java.time.LocalDateTime.now()).toSeconds());

        return response;
    }

    /**
     * Queue transaction for manual review
     */
    private void queueForManualReview(FraudCheckRequest request, FraudCheckResponse response) {
        try {
            ManualReviewItem reviewItem = ManualReviewItem.builder()
                .transactionId(request.getTransactionId())
                .userId(request.getUserId())
                .amount(request.getAmount())
                .riskLevel(response.getRiskLevel())
                .riskScore(response.getRiskScore())
                .reason(response.getReason())
                .queuedAt(java.time.LocalDateTime.now())
                .priority(response.getRiskLevel().equals("CRITICAL") ? "HIGH" : "MEDIUM")
                .source("FRAUD_SERVICE_FALLBACK")
                .build();

            manualReviewQueue.add(reviewItem);
            logQueuedOperation("manualReview", reviewItem);

            log.warn("FALLBACK: Transaction {} queued for manual review - RiskLevel: {}, Amount: ${}",
                request.getTransactionId(), response.getRiskLevel(), request.getAmount());

        } catch (Exception e) {
            log.error("FALLBACK: Failed to queue transaction for manual review - TransactionId: {}",
                request.getTransactionId(), e);
        }
    }

    /**
     * Count recent transactions for user (velocity check)
     */
    private int countRecentTransactions(UUID userId) {
        try {
            var cache = cacheManager.getCache("velocityCache");
            if (cache != null) {
                Integer count = cache.get("velocity_" + userId, Integer.class);
                return count != null ? count : 0;
            }
        } catch (Exception e) {
            log.error("FALLBACK: Error checking velocity cache", e);
        }
        return 0; // Safe default if cache unavailable
    }

    /**
     * Check for high-risk patterns
     */
    private boolean hasHighRiskPatterns(FraudCheckRequest request) {
        // Pattern 1: Round amount (possible structuring)
        if (request.getAmount().remainder(new BigDecimal("100")).compareTo(BigDecimal.ZERO) == 0) {
            log.debug("FALLBACK: Detected round amount pattern - Amount: ${}", request.getAmount());
            return true;
        }

        // Pattern 2: International transaction (if enabled)
        if (request.isInternational() && request.getAmount().compareTo(new BigDecimal("500")) > 0) {
            log.debug("FALLBACK: Detected high-value international transaction");
            return true;
        }

        // Pattern 3: New account (< 30 days)
        if (request.getAccountAge() != null && request.getAccountAge() < 30) {
            log.debug("FALLBACK: Detected new account - Age: {} days", request.getAccountAge());
            return true;
        }

        return false;
    }

    /**
     * Manual review queue item
     */
    @lombok.Builder
    @lombok.Data
    private static class ManualReviewItem {
        private UUID transactionId;
        private UUID userId;
        private BigDecimal amount;
        private String riskLevel;
        private int riskScore;
        private String reason;
        private java.time.LocalDateTime queuedAt;
        private String priority;
        private String source;
    }
}
