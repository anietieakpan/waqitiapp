package com.waqiti.payment.kafka;

import com.waqiti.common.events.PaymentErrorEvent;
import com.waqiti.payment.service.PaymentErrorService;
import com.waqiti.payment.service.PaymentRetryService;
import com.waqiti.payment.service.PaymentMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.common.idempotency.RedisIdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Instant;
import java.util.*;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentErrorsConsumer {

    private final PaymentErrorService errorService;
    private final PaymentRetryService retryService;
    private final PaymentMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final RedisIdempotencyService idempotencyService;

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("payment_errors_processed_total")
            .description("Total number of successfully processed payment error events")
            .register(meterRegistry);
        errorCounter = Counter.builder("payment_errors_processing_errors_total")
            .description("Total number of payment error processing failures")
            .register(meterRegistry);
        processingTimer = Timer.builder("payment_errors_processing_duration")
            .description("Time taken to process payment error events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"payment-errors", "payment-failures", "payment-processing-errors"},
        groupId = "payment-errors-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "8"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    @CircuitBreaker(name = "payment-errors", fallbackMethod = "handlePaymentErrorEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handlePaymentErrorEvent(
            @Payload PaymentErrorEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("error-%s-p%d-o%d", event.getPaymentId(), partition, offset);

        // Build idempotency key: service:eventType:eventId
        String idempotencyKey = idempotencyService.buildIdempotencyKey(
            "payment-service",
            "PaymentErrorEvent",
            event.getPaymentId() + ":" + event.getErrorCode() + ":" + event.getTimestamp()
        );

        try {
            // Universal Redis-backed idempotency check
            if (idempotencyService.isProcessed(idempotencyKey)) {
                log.info("⏭️ Payment error event already processed, skipping: paymentId={}", event.getPaymentId());
                acknowledgment.acknowledge();
                return;
            }

            log.error("Processing payment error: paymentId={}, errorCode={}, message={}",
                event.getPaymentId(), event.getErrorCode(), event.getErrorMessage());

            switch (event.getErrorSeverity()) {
                case CRITICAL:
                    processCriticalError(event, correlationId);
                    break;

                case HIGH:
                    processHighSeverityError(event, correlationId);
                    break;

                case MEDIUM:
                    processMediumSeverityError(event, correlationId);
                    break;

                case LOW:
                    processLowSeverityError(event, correlationId);
                    break;

                default:
                    processUnknownError(event, correlationId);
                    break;
            }

            // Handle specific error types
            handleSpecificErrorType(event, correlationId);

            // Mark event as processed (24-hour TTL for financial operations)
            idempotencyService.markFinancialOperationProcessed(idempotencyKey);

            auditService.logPaymentEvent("PAYMENT_ERROR_EVENT_PROCESSED", event.getPaymentId(),
                Map.of("errorCode", event.getErrorCode(), "errorSeverity", event.getErrorSeverity(),
                    "errorMessage", event.getErrorMessage(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process payment error event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("payment-errors-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            processingTimer.stop(sample);
        }
    }

    public void handlePaymentErrorEventFallback(
            PaymentErrorEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("error-fallback-%s-p%d-o%d", event.getPaymentId(), partition, offset);

        log.error("Circuit breaker fallback triggered for payment error: paymentId={}, error={}",
            event.getPaymentId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("payment-errors-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send critical notification
        try {
            notificationService.sendCriticalAlert(
                "Payment Error Processing Circuit Breaker Triggered",
                String.format("Payment %s error processing failed: %s", event.getPaymentId(), ex.getMessage()),
                "CRITICAL"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send critical alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltPaymentErrorEvent(
            @Payload PaymentErrorEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-error-%s-%d", event.getPaymentId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Payment error permanently failed: paymentId={}, topic={}, error={}",
            event.getPaymentId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logPaymentEvent("PAYMENT_ERROR_DLT_EVENT", event.getPaymentId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "originalErrorCode", event.getErrorCode(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send emergency alert
        try {
            notificationService.sendEmergencyAlert(
                "Payment Error Dead Letter Event",
                String.format("Payment %s error sent to DLT: %s", event.getPaymentId(), exceptionMessage),
                Map.of("paymentId", event.getPaymentId(), "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send emergency DLT alert: {}", ex.getMessage());
        }
    }

    private void processCriticalError(PaymentErrorEvent event, String correlationId) {
        // Log critical error
        errorService.recordCriticalError(
            event.getPaymentId(),
            event.getErrorCode(),
            event.getErrorMessage(),
            event.getStackTrace(),
            event.getTimestamp()
        );

        // Immediately halt payment processing for this payment
        errorService.haltPaymentProcessing(event.getPaymentId());

        // Send immediate alert to on-call team
        notificationService.sendEmergencyAlert(
            "Critical Payment Error",
            String.format("Critical error in payment %s: %s", event.getPaymentId(), event.getErrorMessage()),
            Map.of("paymentId", event.getPaymentId(), "errorCode", event.getErrorCode(), "correlationId", correlationId)
        );

        // Escalate to payment team
        kafkaTemplate.send("payment-escalations", Map.of(
            "type", "CRITICAL_ERROR",
            "paymentId", event.getPaymentId(),
            "errorCode", event.getErrorCode(),
            "priority", "EMERGENCY",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.incrementCriticalErrors(event.getErrorCode());

        log.error("Critical payment error processed: paymentId={}, errorCode={}",
            event.getPaymentId(), event.getErrorCode());
    }

    private void processHighSeverityError(PaymentErrorEvent event, String correlationId) {
        errorService.recordHighSeverityError(
            event.getPaymentId(),
            event.getErrorCode(),
            event.getErrorMessage(),
            event.getTimestamp()
        );

        // Check if this payment should be retried
        if (retryService.shouldRetryPayment(event.getPaymentId(), event.getErrorCode())) {
            // Schedule retry with exponential backoff
            retryService.schedulePaymentRetry(event.getPaymentId(), correlationId);

            kafkaTemplate.send("payment-retry-events", Map.of(
                "paymentId", event.getPaymentId(),
                "errorCode", event.getErrorCode(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        } else {
            // Mark payment as failed
            errorService.markPaymentAsFailed(event.getPaymentId(), event.getErrorCode());
        }

        // Send alert to payment operations
        notificationService.sendOperationalAlert(
            "High Severity Payment Error",
            String.format("High severity error in payment %s: %s", event.getPaymentId(), event.getErrorMessage()),
            "HIGH"
        );

        metricsService.incrementHighSeverityErrors(event.getErrorCode());

        log.warn("High severity payment error processed: paymentId={}, errorCode={}",
            event.getPaymentId(), event.getErrorCode());
    }

    private void processMediumSeverityError(PaymentErrorEvent event, String correlationId) {
        errorService.recordMediumSeverityError(
            event.getPaymentId(),
            event.getErrorCode(),
            event.getErrorMessage(),
            event.getTimestamp()
        );

        // Check if this is a retryable error
        if (retryService.isRetryableError(event.getErrorCode()) &&
            retryService.hasRetriesRemaining(event.getPaymentId())) {

            retryService.schedulePaymentRetry(event.getPaymentId(), correlationId);

            log.info("Scheduling retry for payment {} due to medium severity error: {}",
                event.getPaymentId(), event.getErrorCode());
        } else {
            // Handle non-retryable or exceeded retry limit
            errorService.handleNonRetryableError(event.getPaymentId(), event.getErrorCode());
        }

        metricsService.incrementMediumSeverityErrors(event.getErrorCode());

        log.info("Medium severity payment error processed: paymentId={}, errorCode={}",
            event.getPaymentId(), event.getErrorCode());
    }

    private void processLowSeverityError(PaymentErrorEvent event, String correlationId) {
        errorService.recordLowSeverityError(
            event.getPaymentId(),
            event.getErrorCode(),
            event.getErrorMessage(),
            event.getTimestamp()
        );

        // Log for monitoring but don't block payment
        errorService.logWarningLevel(event.getPaymentId(), event.getErrorMessage());

        metricsService.incrementLowSeverityErrors(event.getErrorCode());

        log.debug("Low severity payment error processed: paymentId={}, errorCode={}",
            event.getPaymentId(), event.getErrorCode());
    }

    private void processUnknownError(PaymentErrorEvent event, String correlationId) {
        errorService.recordUnknownError(
            event.getPaymentId(),
            event.getErrorCode(),
            event.getErrorMessage(),
            event.getTimestamp()
        );

        // Send to error analysis queue
        kafkaTemplate.send("payment-error-analysis", Map.of(
            "paymentId", event.getPaymentId(),
            "errorCode", event.getErrorCode(),
            "errorMessage", event.getErrorMessage(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.incrementUnknownErrors(event.getErrorCode());

        log.warn("Unknown payment error processed: paymentId={}, errorCode={}",
            event.getPaymentId(), event.getErrorCode());
    }

    private void handleSpecificErrorType(PaymentErrorEvent event, String correlationId) {
        switch (event.getErrorCategory()) {
            case INSUFFICIENT_FUNDS:
                handleInsufficientFundsError(event, correlationId);
                break;

            case INVALID_CARD:
                handleInvalidCardError(event, correlationId);
                break;

            case EXPIRED_CARD:
                handleExpiredCardError(event, correlationId);
                break;

            case FRAUD_SUSPECTED:
                handleFraudSuspectedError(event, correlationId);
                break;

            case NETWORK_ERROR:
                handleNetworkError(event, correlationId);
                break;

            case GATEWAY_ERROR:
                handleGatewayError(event, correlationId);
                break;

            case TIMEOUT:
                handleTimeoutError(event, correlationId);
                break;

            case CONFIGURATION_ERROR:
                handleConfigurationError(event, correlationId);
                break;

            default:
                log.info("No specific handler for error category: {}", event.getErrorCategory());
                break;
        }
    }

    private void handleInsufficientFundsError(PaymentErrorEvent event, String correlationId) {
        // Send notification to customer about insufficient funds
        kafkaTemplate.send("customer-notifications", Map.of(
            "type", "INSUFFICIENT_FUNDS",
            "paymentId", event.getPaymentId(),
            "customerId", event.getCustomerId(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleInvalidCardError(PaymentErrorEvent event, String correlationId) {
        // Flag card for review
        kafkaTemplate.send("card-verification-events", Map.of(
            "type", "INVALID_CARD_DETECTED",
            "paymentId", event.getPaymentId(),
            "cardToken", event.getCardToken(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleExpiredCardError(PaymentErrorEvent event, String correlationId) {
        // Send card renewal reminder
        kafkaTemplate.send("customer-notifications", Map.of(
            "type", "CARD_EXPIRED",
            "paymentId", event.getPaymentId(),
            "customerId", event.getCustomerId(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleFraudSuspectedError(PaymentErrorEvent event, String correlationId) {
        // Send to fraud detection system
        kafkaTemplate.send("fraud-detection-events", Map.of(
            "type", "FRAUD_SUSPECTED",
            "paymentId", event.getPaymentId(),
            "customerId", event.getCustomerId(),
            "riskScore", event.getRiskScore(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleNetworkError(PaymentErrorEvent event, String correlationId) {
        // Schedule automatic retry for network errors
        if (retryService.hasRetriesRemaining(event.getPaymentId())) {
            retryService.schedulePaymentRetry(event.getPaymentId(), correlationId);
        }
    }

    private void handleGatewayError(PaymentErrorEvent event, String correlationId) {
        // Check gateway health and possibly route to alternate gateway
        kafkaTemplate.send("gateway-health-events", Map.of(
            "type", "GATEWAY_ERROR",
            "gatewayId", event.getGatewayId(),
            "errorCode", event.getErrorCode(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleTimeoutError(PaymentErrorEvent event, String correlationId) {
        // Check if payment was processed despite timeout
        kafkaTemplate.send("payment-status-check", Map.of(
            "paymentId", event.getPaymentId(),
            "reason", "TIMEOUT_VERIFICATION",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleConfigurationError(PaymentErrorEvent event, String correlationId) {
        // Alert configuration management
        notificationService.sendOperationalAlert(
            "Payment Configuration Error",
            String.format("Configuration error in payment %s: %s", event.getPaymentId(), event.getErrorMessage()),
            "HIGH"
        );

        // Send to configuration review queue
        kafkaTemplate.send("configuration-review-events", Map.of(
            "type", "PAYMENT_CONFIG_ERROR",
            "paymentId", event.getPaymentId(),
            "errorDetails", event.getErrorMessage(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }
}