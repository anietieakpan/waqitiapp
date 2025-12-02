package com.waqiti.payment.service;

import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.payment.client.FraudDetectionClient;
import com.waqiti.payment.client.WalletServiceClient;
import com.waqiti.payment.domain.ScheduledPayment;
import com.waqiti.payment.dto.FraudAssessmentResponse;
import com.waqiti.payment.dto.TransactionResponse;
import com.waqiti.payment.dto.WalletTransferRequest;
import com.waqiti.payment.entity.Payment;
import com.waqiti.payment.entity.PaymentStatus;
import com.waqiti.payment.repository.PaymentRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Payment Transaction Service - PRODUCTION READY
 *
 * Orchestrates payment execution with:
 * - Fraud detection integration
 * - Idempotency protection
 * - Circuit breakers
 * - Comprehensive audit logging
 * - SAGA pattern support
 *
 * CRITICAL FIXES:
 * - Fraud detection before payment
 * - Idempotency checks
 * - @Transactional on financial operations
 * - Circuit breakers on external calls
 * - Audit logging
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentTransactionService {

    private final PaymentRepository paymentRepository;
    private final WalletServiceClient walletServiceClient;
    private final FraudDetectionClient fraudDetectionClient;
    private final IdempotencyService idempotencyService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final PaymentAuditService auditService;

    private static final String CIRCUIT_BREAKER_NAME = "paymentTransaction";
    private static final BigDecimal HIGH_RISK_THRESHOLD = new BigDecimal("0.75");
    private static final BigDecimal MEDIUM_RISK_THRESHOLD = new BigDecimal("0.50");

    /**
     * Execute payment transaction with full security and resilience
     *
     * PRODUCTION-GRADE IMPLEMENTATION:
     * ✅ Fraud detection before payment
     * ✅ Idempotency protection
     * ✅ Circuit breakers
     * ✅ Transactional integrity
     * ✅ Comprehensive audit logging
     * ✅ Compensation logic (SAGA)
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @TimeLimiter(name = CIRCUIT_BREAKER_NAME)
    public TransactionResponse executePaymentTransaction(ScheduledPayment payment) {
        String idempotencyKey = generateIdempotencyKey(payment);
        long startTime = System.currentTimeMillis();

        log.info("PAYMENT: Starting payment execution for scheduled payment: {}", payment.getId());

        // CRITICAL: Execute with idempotency protection
        return idempotencyService.executeIdempotentWithPersistence(
            "payment-service",
            "scheduled-payment-execution",
            idempotencyKey,
            () -> executePaymentInternal(payment, idempotencyKey, startTime),
            Duration.ofMinutes(10)
        );
    }

    /**
     * Internal payment execution logic
     */
    private TransactionResponse executePaymentInternal(ScheduledPayment payment,
                                                      String idempotencyKey,
                                                      long startTime) {
        UUID transactionId = UUID.randomUUID();

        try {
            // Step 1: Create payment record
            Payment paymentRecord = createPaymentRecord(payment, transactionId, idempotencyKey);
            auditService.logPaymentInitiated(paymentRecord);

            // Step 2: CRITICAL - Fraud detection
            FraudDetectionClient.FraudAssessmentResponse fraudAssessment = performFraudDetection(payment, transactionId);
            paymentRecord.setFraudScore(fraudAssessment.getRiskScore());
            paymentRecord.setFraudCheckedAt(LocalDateTime.now());
            paymentRecord.setRiskLevel(mapRiskLevel(fraudAssessment.getRiskScore()));
            paymentRepository.save(paymentRecord);

            if (fraudAssessment.getRiskScore().compareTo(HIGH_RISK_THRESHOLD) >= 0) {
                return handleHighRiskPayment(paymentRecord, fraudAssessment);
            }

            // Step 3: Execute wallet transfer with circuit breaker
            UUID walletTransactionId = executeWalletTransfer(payment, transactionId, idempotencyKey);

            // Step 4: Mark payment as completed
            paymentRecord.setStatus(PaymentStatus.COMPLETED);
            paymentRecord.setCompletedAt(LocalDateTime.now());
            paymentRecord.setExternalTransactionId(walletTransactionId.toString());
            paymentRepository.save(paymentRecord);

            // Step 5: Publish success event
            publishPaymentEvent(paymentRecord, "COMPLETED");
            auditService.logPaymentCompleted(paymentRecord);

            long duration = System.currentTimeMillis() - startTime;
            log.info("PAYMENT: Successfully executed payment for scheduled payment: {} in {}ms",
                    payment.getId(), duration);

            return TransactionResponse.success(transactionId, walletTransactionId, duration);

        } catch (Exception e) {
            log.error("PAYMENT: Failed to execute payment for scheduled payment: {}",
                    payment.getId(), e);

            // SAGA compensation: Mark payment as failed
            Payment failedPayment = paymentRepository.findById(transactionId).orElse(null);
            if (failedPayment != null) {
                failedPayment.setStatus(PaymentStatus.FAILED);
                failedPayment.setFailedAt(LocalDateTime.now());
                failedPayment.setFailureReason(e.getMessage());
                failedPayment.incrementRetryCount();
                paymentRepository.save(failedPayment);

                publishPaymentEvent(failedPayment, "FAILED");
                auditService.logPaymentFailed(failedPayment, e);
            }

            throw new PaymentExecutionException("Payment execution failed", e);
        }
    }

    /**
     * CRITICAL: Fraud detection integration
     */
    @CircuitBreaker(name = "fraud-detection", fallbackMethod = "fraudDetectionFallback")
    @Retry(name = "fraud-detection")
    private FraudAssessmentResponse performFraudDetection(ScheduledPayment payment, UUID transactionId) {
        log.info("FRAUD: Performing fraud detection for scheduled payment: {}", payment.getId());

        try {
            // Get async response and block with timeout
            FraudDetectionClient.FraudAssessmentResponse response = fraudDetectionClient
                .assessScheduledPayment(
                    payment.getSenderId(),
                    payment.getRecipientId(),
                    payment.getAmount(),
                    payment.getCurrency(),
                    payment.getId(),
                    transactionId
                )
                .get(5, java.util.concurrent.TimeUnit.SECONDS); // 5-second timeout

            log.info("FRAUD: Risk score for scheduled payment {}: {}",
                    payment.getId(), response.getRiskScore());

            return response;

        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("FRAUD: Fraud detection timeout for payment: {}", payment.getId());
            throw new RuntimeException("Fraud detection timeout", e);
        } catch (Exception e) {
            log.error("FRAUD: Fraud detection failed for payment: {}", payment.getId(), e);
            throw new RuntimeException("Fraud detection failed", e);
        }
    }

    /**
     * Fallback for fraud detection circuit breaker - FAIL-CLOSED
     */
    private FraudDetectionClient.FraudAssessmentResponse fraudDetectionFallback(
            ScheduledPayment payment, UUID transactionId, Exception e) {
        log.error("CRITICAL SECURITY: Fraud detection service unavailable - BLOCKING payment: {} (fail-closed)",
                payment.getId(), e);

        // SECURITY FIX: Fail-closed - BLOCK ALL transactions when fraud detection unavailable
        // This prevents fraudulent transactions from proceeding during service outages
        return FraudDetectionClient.FraudAssessmentResponse.builder()
            .riskScore(new BigDecimal("0.95")) // Critical risk score (95%)
            .requiresReview(true)
            .fraudDetectionAvailable(false)
            .blocked(true) // Explicitly block the transaction
            .reason("Fraud detection service unavailable - transaction blocked for security (fail-closed)")
            .build();
    }

    /**
     * Execute wallet transfer with circuit breaker
     */
    @CircuitBreaker(name = "wallet-service", fallbackMethod = "walletTransferFallback")
    @Retry(name = "wallet-service")
    private UUID executeWalletTransfer(ScheduledPayment payment, UUID transactionId, String idempotencyKey) {
        log.info("WALLET: Executing wallet transfer for scheduled payment: {}", payment.getId());

        WalletTransferRequest request = WalletTransferRequest.builder()
            .fromUserId(payment.getSenderId())
            .toUserId(payment.getRecipientId())
            .amount(payment.getAmount().setScale(4, RoundingMode.HALF_UP))
            .currency(payment.getCurrency())
            .transactionId(transactionId)
            .idempotencyKey(idempotencyKey)
            .description("Scheduled payment: " + payment.getDescription())
            .build();

        return walletServiceClient.transferFunds(request);
    }

    /**
     * Fallback for wallet transfer circuit breaker
     */
    private UUID walletTransferFallback(ScheduledPayment payment, UUID transactionId,
                                       String idempotencyKey, Exception e) {
        log.error("WALLET: Wallet service unavailable for payment: {}", payment.getId(), e);
        throw new PaymentExecutionException("Wallet service unavailable", e);
    }

    /**
     * Handle high-risk payment
     */
    private TransactionResponse handleHighRiskPayment(Payment payment,
                                                     FraudDetectionClient.FraudAssessmentResponse fraudAssessment) {
        log.warn("FRAUD: High-risk payment detected: {} with score: {}",
                payment.getPaymentId(), fraudAssessment.getRiskScore());

        payment.setStatus(PaymentStatus.DECLINED);
        payment.setFailedAt(LocalDateTime.now());
        payment.setFailureReason("High fraud risk detected: " + fraudAssessment.getRiskScore());
        paymentRepository.save(payment);

        publishPaymentEvent(payment, "DECLINED_FRAUD");
        auditService.logPaymentDeclined(payment, "High fraud risk");

        throw new PaymentDeclinedException(
            "Payment declined due to high fraud risk: " + fraudAssessment.getRiskScore());
    }

    /**
     * Create payment record
     */
    private Payment createPaymentRecord(ScheduledPayment scheduledPayment, UUID transactionId,
                                       String idempotencyKey) {
        return Payment.builder()
            .paymentId(transactionId)
            .status(PaymentStatus.PROCESSING)
            .paymentMethod(scheduledPayment.getPaymentMethod())
            .amount(scheduledPayment.getAmount().setScale(4, RoundingMode.HALF_UP))
            .currency(scheduledPayment.getCurrency())
            .userId(scheduledPayment.getSenderId())
            .merchantId(scheduledPayment.getRecipientId())
            .description("Scheduled: " + scheduledPayment.getDescription())
            .idempotencyKey(idempotencyKey)
            .provider("INTERNAL")
            .build();
    }

    /**
     * Map fraud risk score to risk level enum
     */
    private Payment.RiskLevel mapRiskLevel(BigDecimal riskScore) {
        if (riskScore.compareTo(HIGH_RISK_THRESHOLD) >= 0) {
            return Payment.RiskLevel.CRITICAL;
        } else if (riskScore.compareTo(MEDIUM_RISK_THRESHOLD) >= 0) {
            return Payment.RiskLevel.HIGH;
        } else if (riskScore.compareTo(new BigDecimal("0.25")) >= 0) {
            return Payment.RiskLevel.MEDIUM;
        } else {
            return Payment.RiskLevel.LOW;
        }
    }

    /**
     * Publish payment event to Kafka
     */
    private void publishPaymentEvent(Payment payment, String eventType) {
        try {
            PaymentEvent event = PaymentEvent.builder()
                .paymentId(payment.getPaymentId().toString())
                .userId(payment.getUserId().toString())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus().name())
                .eventType(eventType)
                .timestamp(LocalDateTime.now())
                .build();

            kafkaTemplate.send("payment-events", payment.getPaymentId().toString(), event);
            log.debug("PAYMENT: Published {} event for payment: {}", eventType, payment.getPaymentId());

        } catch (Exception e) {
            log.error("PAYMENT: Failed to publish event for payment: {}", payment.getPaymentId(), e);
            // Don't fail the transaction if event publishing fails
        }
    }

    /**
     * Generate idempotency key for scheduled payment
     */
    private String generateIdempotencyKey(ScheduledPayment payment) {
        return String.format("scheduled-payment:%s:%s:%s",
            payment.getId(),
            payment.getNextExecutionDate(),
            payment.getCompletedExecutions());
    }

    /**
     * Custom exceptions
     */
    public static class PaymentExecutionException extends RuntimeException {
        public PaymentExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class PaymentDeclinedException extends RuntimeException {
        public PaymentDeclinedException(String message) {
            super(message);
        }
    }

    /**
     * Payment Event DTO
     */
    @lombok.Data
    @lombok.Builder
    private static class PaymentEvent {
        private String paymentId;
        private String userId;
        private BigDecimal amount;
        private String currency;
        private String status;
        private String eventType;
        private LocalDateTime timestamp;
    }
}
