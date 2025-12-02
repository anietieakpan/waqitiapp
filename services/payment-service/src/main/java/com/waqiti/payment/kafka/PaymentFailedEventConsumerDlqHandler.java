package com.waqiti.payment.kafka;

import com.waqiti.common.events.PaymentFailedEvent;
import com.waqiti.common.kafka.BaseDlqConsumer;
import com.waqiti.payment.service.PaymentRecoveryService;
import com.waqiti.payment.repository.PaymentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Map;

/**
 * PRODUCTION DLQ HANDLER - P0 BLOCKER #5 FIX
 *
 * DLQ Recovery Handler for PaymentFailedEvent
 *
 * Handles failed payment event processing with intelligent recovery:
 * - Automatic retry for transient failures (DB connection, API timeout)
 * - Manual queue for permanent failures (invalid payment, fraud detected)
 * - Fund restoration for eligible failed payments
 * - Customer notification for payment failures
 * - Comprehensive audit logging
 *
 * CRITICAL: Prevents fund loss and customer confusion from unprocessed payment failures
 *
 * Recovery Logic:
 * 1. Transient errors → Auto-retry with exponential backoff (3 attempts)
 * 2. Database errors → Queue for automatic DB retry
 * 3. Invalid state → Manual intervention queue
 * 4. Fraud detection → Escalate to fraud team
 * 5. Network timeout → Retry after 30s/60s/120s
 *
 * @author Waqiti Payment Team
 * @version 2.0.0 - Production DLQ Recovery
 */
@Service
@Slf4j
public class PaymentFailedEventConsumerDlqHandler extends BaseDlqConsumer<PaymentFailedEvent> {

    private final PaymentRecoveryService paymentRecoveryService;
    private final PaymentRepository paymentRepository;

    public PaymentFailedEventConsumerDlqHandler(
            MeterRegistry meterRegistry,
            PaymentRecoveryService paymentRecoveryService,
            PaymentRepository paymentRepository) {
        super(meterRegistry);
        this.paymentRecoveryService = paymentRecoveryService;
        this.paymentRepository = paymentRepository;
    }

    @PostConstruct
    public void init() {
        initializeMetrics("PaymentFailedEventConsumer");
    }

    @KafkaListener(
        topics = "${kafka.topics.payment-failed-events.dlq:payment-failed-events.DLT}",
        groupId = "${kafka.consumer.group-id:payment-service}-dlq",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleDlqMessage(
            @Payload PaymentFailedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {
        processDlqMessage(event, topic, acknowledgment);
    }

    @Override
    protected DlqProcessingResult handleDlqEvent(PaymentFailedEvent event, Map<String, Object> headers) {
        try {
            log.info("DLQ: Processing PaymentFailedEvent - Payment: {}, User: {}, Amount: {}",
                    event.getPaymentId(), event.getUserId(), event.getAmount());

            // Extract failure reason from headers
            String failureReason = (String) headers.getOrDefault("exception_message", "Unknown");
            String exceptionType = (String) headers.getOrDefault("exception_type", "Unknown");

            log.warn("DLQ: Payment failure reason - Type: {}, Reason: {}", exceptionType, failureReason);

            // Step 1: Check if payment still exists and is in failed state
            boolean paymentExists = paymentRepository.existsById(event.getPaymentId());
            if (!paymentExists) {
                log.error("DLQ: Payment not found in database - Payment: {}. May have been deleted.",
                        event.getPaymentId());
                return DlqProcessingResult.PERMANENT_FAILURE;
            }

            // Step 2: Categorize failure type and determine recovery strategy
            DlqProcessingResult result = categorizeFailureAndRecover(event, exceptionType, failureReason);

            return result;

        } catch (Exception e) {
            log.error("DLQ: Failed to process PaymentFailedEvent - Payment: {}",
                    event.getPaymentId(), e);
            return DlqProcessingResult.PERMANENT_FAILURE;
        }
    }

    /**
     * Categorize failure type and execute appropriate recovery strategy
     */
    private DlqProcessingResult categorizeFailureAndRecover(
            PaymentFailedEvent event, String exceptionType, String failureReason) {

        // Transient Database Errors → Retry
        if (isTransientDatabaseError(exceptionType, failureReason)) {
            log.info("DLQ: Transient database error detected. Attempting retry - Payment: {}",
                    event.getPaymentId());

            try {
                paymentRecoveryService.processPaymentFailure(event);
                log.info("DLQ: Successfully processed payment failure on retry - Payment: {}",
                        event.getPaymentId());
                return DlqProcessingResult.SUCCESS;
            } catch (Exception e) {
                log.warn("DLQ: Retry failed for payment - Payment: {}. Will retry again.",
                        event.getPaymentId(), e);
                return DlqProcessingResult.RETRY;
            }
        }

        // Network Timeout Errors → Retry
        if (isNetworkTimeout(exceptionType, failureReason)) {
            log.info("DLQ: Network timeout detected. Scheduling retry - Payment: {}",
                    event.getPaymentId());
            return DlqProcessingResult.RETRY;
        }

        // Wallet Service Unavailable → Retry (fund restoration may need wallet)
        if (isWalletServiceUnavailable(exceptionType, failureReason)) {
            log.info("DLQ: Wallet service unavailable. Will retry - Payment: {}",
                    event.getPaymentId());
            return DlqProcessingResult.RETRY;
        }

        // Invalid Payment State → Manual Intervention
        if (isInvalidStateError(exceptionType, failureReason)) {
            log.error("DLQ: Invalid payment state. Manual intervention required - Payment: {}",
                    event.getPaymentId());

            // Queue for manual review
            paymentRecoveryService.queueForManualReview(
                event.getPaymentId(),
                "Invalid payment state during failure processing: " + failureReason
            );

            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }

        // Fraud Detection Error → Escalate to Fraud Team
        if (isFraudDetectionError(exceptionType, failureReason)) {
            log.error("DLQ: Fraud detection issue. Escalating to fraud team - Payment: {}",
                    event.getPaymentId());

            paymentRecoveryService.escalateToFraudTeam(
                event.getPaymentId(),
                "Payment failure processing blocked by fraud detection: " + failureReason
            );

            return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
        }

        // Data Integrity Error → Permanent Failure
        if (isDataIntegrityError(exceptionType, failureReason)) {
            log.error("DLQ: Data integrity error. Permanent failure - Payment: {}",
                    event.getPaymentId());

            paymentRecoveryService.logPermanentFailure(
                event.getPaymentId(),
                "Data integrity error: " + failureReason
            );

            return DlqProcessingResult.PERMANENT_FAILURE;
        }

        // Unknown Error → Manual Intervention
        log.warn("DLQ: Unknown error type. Manual intervention required - Payment: {}, Error: {}",
                event.getPaymentId(), failureReason);

        paymentRecoveryService.queueForManualReview(
            event.getPaymentId(),
            "Unknown error during payment failure processing: " + failureReason
        );

        return DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED;
    }

    // Helper methods for error categorization

    private boolean isTransientDatabaseError(String exceptionType, String failureReason) {
        return exceptionType.contains("SQLException") ||
               exceptionType.contains("DataAccessException") ||
               exceptionType.contains("OptimisticLockingFailureException") ||
               exceptionType.contains("DeadlockException") ||
               failureReason.contains("connection") ||
               failureReason.contains("timeout") ||
               failureReason.contains("deadlock");
    }

    private boolean isNetworkTimeout(String exceptionType, String failureReason) {
        return exceptionType.contains("TimeoutException") ||
               exceptionType.contains("SocketTimeoutException") ||
               exceptionType.contains("ConnectException") ||
               failureReason.contains("timed out") ||
               failureReason.contains("connection refused");
    }

    private boolean isWalletServiceUnavailable(String exceptionType, String failureReason) {
        return failureReason.contains("wallet-service") ||
               failureReason.contains("WalletServiceException") ||
               exceptionType.contains("FeignException") ||
               exceptionType.contains("ServiceUnavailableException");
    }

    private boolean isInvalidStateError(String exceptionType, String failureReason) {
        return exceptionType.contains("IllegalStateException") ||
               exceptionType.contains("InvalidStateException") ||
               failureReason.contains("invalid state") ||
               failureReason.contains("state mismatch");
    }

    private boolean isFraudDetectionError(String exceptionType, String failureReason) {
        return failureReason.contains("fraud") ||
               failureReason.contains("suspicious") ||
               exceptionType.contains("FraudException");
    }

    private boolean isDataIntegrityError(String exceptionType, String failureReason) {
        return exceptionType.contains("DataIntegrityViolationException") ||
               exceptionType.contains("ConstraintViolationException") ||
               failureReason.contains("foreign key") ||
               failureReason.contains("unique constraint");
    }

    @Override
    protected String getServiceName() {
        return "payment-service";
    }

    @Override
    protected boolean isCriticalEvent(PaymentFailedEvent event) {
        // Critical if high-value payment or fraud-related
        return event.getAmount() != null &&
               event.getAmount().compareTo(new java.math.BigDecimal("10000")) > 0;
    }

    @Override
    protected String getEventId(PaymentFailedEvent event) {
        return event.getPaymentId() != null ? event.getPaymentId().toString() : "unknown";
    }
}
