package com.waqiti.payment.kafka;

import com.waqiti.common.events.PaymentAuthorizationFailureEvent;
import com.waqiti.payment.domain.PaymentTransaction;
import com.waqiti.payment.repository.PaymentTransactionRepository;
import com.waqiti.payment.service.PaymentAuthorizationService;
import com.waqiti.payment.service.PaymentRetryService;
import com.waqiti.payment.service.RiskAssessmentService;
import com.waqiti.payment.metrics.PaymentMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
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
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.PostConstruct;
import java.math.BigDecimal;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentAuthorizationFailuresConsumer {

    private final PaymentTransactionRepository transactionRepository;
    private final PaymentAuthorizationService authorizationService;
    private final PaymentRetryService retryService;
    private final RiskAssessmentService riskAssessmentService;
    private final PaymentMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000;

    // Financial impact tracking
    private final AtomicLong totalFailedAmount = new AtomicLong(0);
    private final AtomicLong failureCount = new AtomicLong(0);

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Counter financialImpactCounter;
    private Timer processingTimer;
    private Gauge failedAmountGauge;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("payment_authorization_failures_processed_total")
            .description("Total number of successfully processed payment authorization failure events")
            .register(meterRegistry);
        errorCounter = Counter.builder("payment_authorization_failures_errors_total")
            .description("Total number of payment authorization failure processing errors")
            .register(meterRegistry);
        financialImpactCounter = Counter.builder("payment_authorization_failures_financial_impact")
            .description("Financial impact of payment authorization failures")
            .register(meterRegistry);
        processingTimer = Timer.builder("payment_authorization_failures_processing_duration")
            .description("Time taken to process payment authorization failure events")
            .register(meterRegistry);
        failedAmountGauge = Gauge.builder("payment_authorization_failed_amount_total")
            .description("Total amount of failed payment authorizations")
            .register(meterRegistry, totalFailedAmount, AtomicLong::get);
    }

    @KafkaListener(
        topics = {"payment-authorization-failures", "payment-declined-events", "authorization-timeout-events"},
        groupId = "payment-authorization-failures-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    @CircuitBreaker(name = "payment-authorization-failures", fallbackMethod = "handlePaymentAuthorizationFailureFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handlePaymentAuthorizationFailureEvent(
            @Payload PaymentAuthorizationFailureEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("auth-fail-%s-p%d-o%d", event.getTransactionId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getTransactionId(), event.getFailureCode(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.warn("Processing payment authorization failure: transactionId={}, failureCode={}, amount={}, merchantId={}",
                event.getTransactionId(), event.getFailureCode(), event.getAmount(), event.getMerchantId());

            // Clean expired entries periodically
            cleanExpiredEntries();

            // Financial impact assessment
            BigDecimal failureAmount = event.getAmount();
            assessFinancialImpact(failureAmount, event.getFailureCode(), correlationId);

            switch (event.getFailureType()) {
                case INSUFFICIENT_FUNDS:
                    handleInsufficientFundsFailure(event, correlationId);
                    break;

                case INVALID_CARD:
                    handleInvalidCardFailure(event, correlationId);
                    break;

                case EXPIRED_CARD:
                    handleExpiredCardFailure(event, correlationId);
                    break;

                case FRAUD_SUSPECTED:
                    handleFraudSuspectedFailure(event, correlationId);
                    break;

                case AUTHORIZATION_TIMEOUT:
                    handleAuthorizationTimeoutFailure(event, correlationId);
                    break;

                case ISSUER_DECLINED:
                    handleIssuerDeclinedFailure(event, correlationId);
                    break;

                case NETWORK_ERROR:
                    handleNetworkErrorFailure(event, correlationId);
                    break;

                case LIMIT_EXCEEDED:
                    handleLimitExceededFailure(event, correlationId);
                    break;

                case INVALID_PIN:
                    handleInvalidPinFailure(event, correlationId);
                    break;

                case SYSTEM_ERROR:
                    handleSystemErrorFailure(event, correlationId);
                    break;

                default:
                    log.warn("Unknown authorization failure type: {}", event.getFailureType());
                    handleGenericAuthorizationFailure(event, correlationId);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logPaymentEvent("PAYMENT_AUTHORIZATION_FAILURE_PROCESSED", event.getTransactionId(),
                Map.of("failureType", event.getFailureType(), "failureCode", event.getFailureCode(),
                    "amount", event.getAmount(), "merchantId", event.getMerchantId(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process payment authorization failure event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("payment-authorization-failure-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handlePaymentAuthorizationFailureFallback(
            PaymentAuthorizationFailureEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("auth-fail-fallback-%s-p%d-o%d", event.getTransactionId(), partition, offset);

        log.error("Circuit breaker fallback triggered for payment authorization failure: transactionId={}, error={}",
            event.getTransactionId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("payment-authorization-failures-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send critical alert for high-value transactions
        if (event.getAmount().compareTo(BigDecimal.valueOf(10000)) > 0) {
            try {
                notificationService.sendExecutiveAlert(
                    "High-Value Payment Authorization Failure - Circuit Breaker Triggered",
                    String.format("Transaction %s (Amount: %s) failed: %s",
                        event.getTransactionId(), event.getAmount(), ex.getMessage()),
                    "CRITICAL"
                );
            } catch (Exception notificationEx) {
                log.error("Failed to send executive alert: {}", notificationEx.getMessage());
            }
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltPaymentAuthorizationFailureEvent(
            @Payload PaymentAuthorizationFailureEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-auth-fail-%s-%d", event.getTransactionId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Payment authorization failure permanently failed: transactionId={}, topic={}, error={}",
            event.getTransactionId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logPaymentEvent("PAYMENT_AUTHORIZATION_FAILURE_DLT_EVENT", event.getTransactionId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "failureType", event.getFailureType(), "amount", event.getAmount(),
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Payment Authorization Failure Dead Letter Event",
                String.format("Transaction %s (Amount: %s) sent to DLT: %s",
                    event.getTransactionId(), event.getAmount(), exceptionMessage),
                Map.of("transactionId", event.getTransactionId(), "topic", topic,
                    "correlationId", correlationId, "amount", event.getAmount())
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private void assessFinancialImpact(BigDecimal amount, String failureCode, String correlationId) {
        long amountCents = amount.multiply(BigDecimal.valueOf(100)).longValue();
        totalFailedAmount.addAndGet(amountCents);
        failureCount.incrementAndGet();
        financialImpactCounter.increment(amountCents);

        // Alert if cumulative failed amount exceeds threshold
        if (totalFailedAmount.get() > 100000000) { // $1M in cents
            try {
                notificationService.sendExecutiveAlert(
                    "Critical: Payment Authorization Failures Exceed $1M",
                    String.format("Cumulative failed authorization amount: $%,.2f",
                        totalFailedAmount.get() / 100.0),
                    "CRITICAL"
                );
                // Reset counter after alert
                totalFailedAmount.set(0);
            } catch (Exception ex) {
                log.error("Failed to send financial impact alert: {}", ex.getMessage());
            }
        }

        // High-value transaction alert
        if (amount.compareTo(BigDecimal.valueOf(50000)) > 0) {
            try {
                notificationService.sendExecutiveAlert(
                    "High-Value Payment Authorization Failure",
                    String.format("Transaction amount: $%,.2f, Failure: %s", amount, failureCode),
                    "HIGH"
                );
            } catch (Exception ex) {
                log.error("Failed to send high-value failure alert: {}", ex.getMessage());
            }
        }
    }

    private void handleInsufficientFundsFailure(PaymentAuthorizationFailureEvent event, String correlationId) {
        updateTransactionStatus(event.getTransactionId(), "INSUFFICIENT_FUNDS", event.getFailureReason());

        // Notify customer about insufficient funds
        notificationService.sendNotification(event.getUserId(), "Payment Declined - Insufficient Funds",
            "Your payment was declined due to insufficient funds. Please check your account balance.",
            correlationId);

        // Suggest alternative payment methods
        kafkaTemplate.send("payment-alternatives-suggestions", Map.of(
            "userId", event.getUserId(),
            "transactionId", event.getTransactionId(),
            "suggestedActions", Arrays.asList("Add funds", "Use different card", "Use bank transfer"),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordAuthorizationFailure("INSUFFICIENT_FUNDS", event.getAmount());
    }

    private void handleFraudSuspectedFailure(PaymentAuthorizationFailureEvent event, String correlationId) {
        updateTransactionStatus(event.getTransactionId(), "FRAUD_SUSPECTED", event.getFailureReason());

        // Trigger fraud investigation
        kafkaTemplate.send("fraud-investigation-requests", Map.of(
            "transactionId", event.getTransactionId(),
            "userId", event.getUserId(),
            "merchantId", event.getMerchantId(),
            "amount", event.getAmount(),
            "suspicionLevel", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Temporary card freeze
        kafkaTemplate.send("card-security-actions", Map.of(
            "cardId", event.getCardId(),
            "action", "TEMPORARY_FREEZE",
            "reason", "FRAUD_SUSPECTED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendSecurityAlert(event.getUserId(), "Suspicious Activity Detected",
            "We've temporarily restricted your card due to suspicious activity. Please contact us to verify your identity.",
            correlationId);

        metricsService.recordAuthorizationFailure("FRAUD_SUSPECTED", event.getAmount());
    }

    private void handleAuthorizationTimeoutFailure(PaymentAuthorizationFailureEvent event, String correlationId) {
        updateTransactionStatus(event.getTransactionId(), "AUTHORIZATION_TIMEOUT", event.getFailureReason());

        // Check if retry is appropriate
        if (retryService.canRetry(event.getTransactionId())) {
            // Schedule retry
            kafkaTemplate.send("payment-retry-requests", Map.of(
                "transactionId", event.getTransactionId(),
                "retryType", "AUTHORIZATION_TIMEOUT",
                "scheduleDelay", 30000, // 30 seconds
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        // Check network connectivity to payment processors
        kafkaTemplate.send("network-connectivity-checks", Map.of(
            "target", "PAYMENT_PROCESSORS",
            "priority", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordAuthorizationFailure("AUTHORIZATION_TIMEOUT", event.getAmount());
    }

    private void handleNetworkErrorFailure(PaymentAuthorizationFailureEvent event, String correlationId) {
        updateTransactionStatus(event.getTransactionId(), "NETWORK_ERROR", event.getFailureReason());

        // Trigger system health check
        kafkaTemplate.send("system-health-checks", Map.of(
            "component", "PAYMENT_GATEWAY",
            "priority", "CRITICAL",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Schedule automatic retry
        if (retryService.canRetry(event.getTransactionId())) {
            kafkaTemplate.send("payment-retry-requests", Map.of(
                "transactionId", event.getTransactionId(),
                "retryType", "NETWORK_ERROR",
                "scheduleDelay", 60000, // 1 minute
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordAuthorizationFailure("NETWORK_ERROR", event.getAmount());
    }

    private void handleInvalidCardFailure(PaymentAuthorizationFailureEvent event, String correlationId) {
        updateTransactionStatus(event.getTransactionId(), "INVALID_CARD", event.getFailureReason());

        // Update card status
        kafkaTemplate.send("card-status-updates", Map.of(
            "cardId", event.getCardId(),
            "status", "INVALID",
            "reason", "AUTHORIZATION_FAILURE",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendNotification(event.getUserId(), "Payment Declined - Invalid Card",
            "Your payment was declined due to invalid card information. Please verify your card details.",
            correlationId);

        metricsService.recordAuthorizationFailure("INVALID_CARD", event.getAmount());
    }

    private void handleExpiredCardFailure(PaymentAuthorizationFailureEvent event, String correlationId) {
        updateTransactionStatus(event.getTransactionId(), "EXPIRED_CARD", event.getFailureReason());

        // Suggest card renewal
        kafkaTemplate.send("card-renewal-suggestions", Map.of(
            "userId", event.getUserId(),
            "cardId", event.getCardId(),
            "priority", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendNotification(event.getUserId(), "Payment Declined - Expired Card",
            "Your payment was declined because your card has expired. Please use a different payment method or request a new card.",
            correlationId);

        metricsService.recordAuthorizationFailure("EXPIRED_CARD", event.getAmount());
    }

    private void handleIssuerDeclinedFailure(PaymentAuthorizationFailureEvent event, String correlationId) {
        updateTransactionStatus(event.getTransactionId(), "ISSUER_DECLINED", event.getFailureReason());

        notificationService.sendNotification(event.getUserId(), "Payment Declined by Issuer",
            "Your payment was declined by your card issuer. Please contact your bank for more information.",
            correlationId);

        metricsService.recordAuthorizationFailure("ISSUER_DECLINED", event.getAmount());
    }

    private void handleLimitExceededFailure(PaymentAuthorizationFailureEvent event, String correlationId) {
        updateTransactionStatus(event.getTransactionId(), "LIMIT_EXCEEDED", event.getFailureReason());

        // Check if limit increase is possible
        kafkaTemplate.send("limit-review-requests", Map.of(
            "userId", event.getUserId(),
            "currentLimit", event.getCurrentLimit(),
            "requestedAmount", event.getAmount(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendNotification(event.getUserId(), "Payment Declined - Limit Exceeded",
            "Your payment exceeds your current limit. You can request a limit increase or use a smaller amount.",
            correlationId);

        metricsService.recordAuthorizationFailure("LIMIT_EXCEEDED", event.getAmount());
    }

    private void handleInvalidPinFailure(PaymentAuthorizationFailureEvent event, String correlationId) {
        updateTransactionStatus(event.getTransactionId(), "INVALID_PIN", event.getFailureReason());

        // Check for multiple PIN failures (potential security threat)
        if (riskAssessmentService.hasMultiplePinFailures(event.getCardId())) {
            kafkaTemplate.send("card-security-actions", Map.of(
                "cardId", event.getCardId(),
                "action", "PIN_RESET_REQUIRED",
                "reason", "MULTIPLE_PIN_FAILURES",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        notificationService.sendNotification(event.getUserId(), "Payment Declined - Invalid PIN",
            "Your payment was declined due to an incorrect PIN. Please try again or reset your PIN.",
            correlationId);

        metricsService.recordAuthorizationFailure("INVALID_PIN", event.getAmount());
    }

    private void handleSystemErrorFailure(PaymentAuthorizationFailureEvent event, String correlationId) {
        updateTransactionStatus(event.getTransactionId(), "SYSTEM_ERROR", event.getFailureReason());

        // Trigger immediate system health check
        kafkaTemplate.send("system-health-alerts", Map.of(
            "component", "PAYMENT_AUTHORIZATION",
            "severity", "CRITICAL",
            "error", event.getFailureReason(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Schedule retry after system recovery
        kafkaTemplate.send("payment-retry-requests", Map.of(
            "transactionId", event.getTransactionId(),
            "retryType", "SYSTEM_ERROR",
            "scheduleDelay", 300000, // 5 minutes
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Payment System Error",
            String.format("System error in payment authorization: %s", event.getFailureReason()),
            "CRITICAL");

        metricsService.recordAuthorizationFailure("SYSTEM_ERROR", event.getAmount());
    }

    private void handleGenericAuthorizationFailure(PaymentAuthorizationFailureEvent event, String correlationId) {
        updateTransactionStatus(event.getTransactionId(), "UNKNOWN_FAILURE", event.getFailureReason());

        // Log for investigation
        auditService.logPaymentEvent("UNKNOWN_AUTHORIZATION_FAILURE", event.getTransactionId(),
            Map.of("failureType", event.getFailureType(), "failureCode", event.getFailureCode(),
                "failureReason", event.getFailureReason(), "correlationId", correlationId,
                "requiresInvestigation", true, "timestamp", Instant.now()));

        notificationService.sendNotification(event.getUserId(), "Payment Processing Issue",
            "We're experiencing an issue processing your payment. Please try again later or contact support.",
            correlationId);

        metricsService.recordAuthorizationFailure("UNKNOWN", event.getAmount());
    }

    private void updateTransactionStatus(String transactionId, String status, String reason) {
        try {
            PaymentTransaction transaction = transactionRepository.findById(transactionId).orElse(null);
            if (transaction != null) {
                transaction.setStatus(status);
                transaction.setFailureReason(reason);
                transaction.setUpdatedAt(LocalDateTime.now());
                transactionRepository.save(transaction);
            }
        } catch (Exception e) {
            log.error("Failed to update transaction status: {}", e.getMessage());
        }
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) {
            return false;
        }

        if (System.currentTimeMillis() - timestamp > TTL_24_HOURS) {
            processedEvents.remove(eventKey);
            return false;
        }

        return true;
    }

    private void markEventAsProcessed(String eventKey) {
        processedEvents.put(eventKey, System.currentTimeMillis());
    }

    private void cleanExpiredEntries() {
        if (processedEvents.size() > 1000) {
            long currentTime = System.currentTimeMillis();
            processedEvents.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > TTL_24_HOURS);
        }
    }
}