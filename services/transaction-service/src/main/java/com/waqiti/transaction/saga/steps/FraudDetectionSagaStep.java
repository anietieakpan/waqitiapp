package com.waqiti.transaction.saga.steps;

import com.waqiti.transaction.client.FraudDetectionServiceClient;
import com.waqiti.transaction.dto.FraudCheckRequest;
import com.waqiti.transaction.dto.FraudCheckResponse;
import com.waqiti.transaction.saga.TransactionSagaContext;
import com.waqiti.transaction.saga.SagaStepResult;
import com.waqiti.common.saga.SagaStep;
import com.waqiti.common.saga.SagaStepStatus;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * PRODUCTION-READY Fraud Detection Saga Step
 *
 * Integrates with existing fraud-detection-service for real-time ML-based fraud detection
 *
 * Features:
 * - Real-time ML fraud scoring (TensorFlow, PyTorch, scikit-learn)
 * - Behavioral analysis (velocity checks, pattern matching)
 * - Device fingerprinting validation
 * - IP reputation screening
 * - Amount anomaly detection
 * - Circuit breaker for resilience
 * - Comprehensive audit logging
 *
 * Compensation:
 * - Records fraud check bypass in audit trail
 * - No actual compensation needed (read-only operation)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FraudDetectionSagaStep implements SagaStep<TransactionSagaContext> {

    private final FraudDetectionServiceClient fraudDetectionClient;
    private final MeterRegistry meterRegistry;

    private static final String STEP_NAME = "FRAUD_DETECTION";
    private static final double FRAUD_THRESHOLD = 0.75; // 75% fraud score threshold
    private static final double HIGH_RISK_THRESHOLD = 0.90; // 90% automatic block

    @Override
    public String getStepName() {
        return STEP_NAME;
    }

    @Override
    @CircuitBreaker(name = "fraudDetection", fallbackMethod = "fallbackFraudCheck")
    @Retry(name = "fraudDetection")
    public CompletableFuture<SagaStepResult> execute(TransactionSagaContext context) {
        Timer.Sample timer = Timer.start(meterRegistry);

        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("FRAUD CHECK: Starting fraud detection for transaction: {} amount: {} {}",
                    context.getTransactionId(), context.getAmount(), context.getCurrency());

                // Build comprehensive fraud check request
                FraudCheckRequest request = FraudCheckRequest.builder()
                    .transactionId(context.getTransactionId())
                    .userId(context.getUserId())
                    .sourceWalletId(context.getSourceWalletId())
                    .destinationWalletId(context.getDestinationWalletId())
                    .amount(context.getAmount())
                    .currency(context.getCurrency())
                    .transactionType(context.getTransactionType().name())
                    .ipAddress(context.getIpAddress())
                    .deviceFingerprint(context.getDeviceFingerprint())
                    .userAgent(context.getUserAgent())
                    .geolocation(context.getGeolocation())
                    .timestamp(LocalDateTime.now())
                    .build();

                // Call fraud detection service with ML scoring
                FraudCheckResponse response = fraudDetectionClient.performFraudCheck(request);

                // Record fraud score metrics
                meterRegistry.gauge("transaction.fraud.score", response.getRiskScore());

                // Analyze fraud score and make decision
                SagaStepResult result = analyzeFraudScore(context, response);

                // Record step completion metrics
                timer.stop(Timer.builder("transaction.saga.step.fraud.time")
                    .tag("result", result.getStatus().name())
                    .tag("risk_level", response.getRiskLevel().name())
                    .register(meterRegistry));

                // Increment counters
                meterRegistry.counter("transaction.fraud.checks",
                    "result", result.getStatus().name(),
                    "risk", response.getRiskLevel().name()).increment();

                return result;

            } catch (Exception e) {
                log.error("FRAUD CHECK FAILED: Error during fraud detection for transaction: {}",
                    context.getTransactionId(), e);

                timer.stop(Timer.builder("transaction.saga.step.fraud.time")
                    .tag("result", "error")
                    .tag("risk_level", "unknown")
                    .register(meterRegistry));

                meterRegistry.counter("transaction.fraud.errors").increment();

                // CRITICAL: Fail transaction on fraud check error (conservative approach)
                return SagaStepResult.builder()
                    .stepName(STEP_NAME)
                    .status(SagaStepStatus.FAILED)
                    .errorMessage("Fraud detection service unavailable: " + e.getMessage())
                    .timestamp(LocalDateTime.now())
                    .build();
            }
        });
    }

    /**
     * Analyze fraud score and make authorization decision
     */
    private SagaStepResult analyzeFraudScore(TransactionSagaContext context, FraudCheckResponse response) {
        double fraudScore = response.getRiskScore();
        String riskLevel = response.getRiskLevel().name();

        // CRITICAL: Block high-risk transactions immediately
        if (fraudScore >= HIGH_RISK_THRESHOLD) {
            log.warn("FRAUD BLOCKED: High-risk transaction detected. ID: {}, Score: {}, Reasons: {}",
                context.getTransactionId(), fraudScore, response.getReasons());

            // Store fraud context for compensation/audit
            context.setFraudScore(fraudScore);
            context.setFraudReasons(response.getReasons());
            context.setFraudBlocked(true);

            return SagaStepResult.builder()
                .stepName(STEP_NAME)
                .status(SagaStepStatus.FAILED)
                .errorMessage(String.format("Transaction blocked - High fraud risk (score: %.2f%%)",
                    fraudScore * 100))
                .data("fraudScore", fraudScore)
                .data("riskLevel", riskLevel)
                .data("reasons", response.getReasons())
                .timestamp(LocalDateTime.now())
                .build();
        }

        // WARNING: Flag medium-risk transactions for review (but allow to proceed)
        if (fraudScore >= FRAUD_THRESHOLD) {
            log.warn("FRAUD WARNING: Medium-risk transaction flagged. ID: {}, Score: {}, Reasons: {}",
                context.getTransactionId(), fraudScore, response.getReasons());

            // Flag for manual review but allow transaction
            context.setFraudScore(fraudScore);
            context.setFraudReasons(response.getReasons());
            context.setRequiresReview(true);

            // Proceed with transaction but flag for review
            return SagaStepResult.builder()
                .stepName(STEP_NAME)
                .status(SagaStepStatus.SUCCESS)
                .message(String.format("Transaction flagged for review (fraud score: %.2f%%)",
                    fraudScore * 100))
                .data("fraudScore", fraudScore)
                .data("riskLevel", riskLevel)
                .data("requiresReview", true)
                .timestamp(LocalDateTime.now())
                .build();
        }

        // SUCCESS: Low fraud risk, proceed normally
        log.info("FRAUD PASSED: Low-risk transaction cleared. ID: {}, Score: {}",
            context.getTransactionId(), fraudScore);

        context.setFraudScore(fraudScore);
        context.setFraudReasons(response.getReasons());

        return SagaStepResult.builder()
            .stepName(STEP_NAME)
            .status(SagaStepStatus.SUCCESS)
            .message(String.format("Fraud check passed (score: %.2f%%)", fraudScore * 100))
            .data("fraudScore", fraudScore)
            .data("riskLevel", riskLevel)
            .timestamp(LocalDateTime.now())
            .build();
    }

    /**
     * Fallback method when fraud detection service is unavailable
     * CONSERVATIVE approach: Block high-value transactions, allow low-value
     */
    private CompletableFuture<SagaStepResult> fallbackFraudCheck(TransactionSagaContext context, Exception ex) {
        log.error("FRAUD FALLBACK: Using conservative fallback for transaction: {} due to: {}",
            context.getTransactionId(), ex.getMessage());

        meterRegistry.counter("transaction.fraud.fallback").increment();

        // Conservative threshold: $5,000
        java.math.BigDecimal highValueThreshold = new java.math.BigDecimal("5000");

        if (context.getAmount().compareTo(highValueThreshold) >= 0) {
            // BLOCK high-value transactions when fraud service is down
            log.warn("FRAUD FALLBACK BLOCK: High-value transaction blocked due to fraud service unavailability. ID: {}, Amount: {}",
                context.getTransactionId(), context.getAmount());

            return CompletableFuture.completedFuture(SagaStepResult.builder()
                .stepName(STEP_NAME)
                .status(SagaStepStatus.FAILED)
                .errorMessage("Transaction blocked - Fraud detection service unavailable (conservative mode)")
                .data("fallbackMode", true)
                .data("amount", context.getAmount())
                .timestamp(LocalDateTime.now())
                .build());
        } else {
            // ALLOW low-value transactions when fraud service is down
            log.warn("FRAUD FALLBACK ALLOW: Low-value transaction allowed despite fraud service unavailability. ID: {}, Amount: {}",
                context.getTransactionId(), context.getAmount());

            context.setFraudCheckBypassed(true);

            return CompletableFuture.completedFuture(SagaStepResult.builder()
                .stepName(STEP_NAME)
                .status(SagaStepStatus.SUCCESS)
                .message("Fraud check bypassed (service unavailable, low-value transaction)")
                .data("fallbackMode", true)
                .data("bypassed", true)
                .timestamp(LocalDateTime.now())
                .build());
        }
    }

    @Override
    public CompletableFuture<SagaStepResult> compensate(TransactionSagaContext context, SagaStepResult originalResult) {
        // Fraud detection is a read-only check - no actual compensation needed
        // Just log the compensation for audit trail

        log.info("FRAUD COMPENSATION: Recording fraud check bypass for transaction: {}",
            context.getTransactionId());

        return CompletableFuture.completedFuture(SagaStepResult.builder()
            .stepName(STEP_NAME + "_COMPENSATION")
            .status(SagaStepStatus.SUCCESS)
            .message("Fraud check compensation completed (no action required)")
            .timestamp(LocalDateTime.now())
            .build());
    }
}
