package com.waqiti.payment.kafka;

import com.waqiti.common.events.CardProcessingFailureEvent;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.payment.domain.CardProcessingFailure;
import com.waqiti.payment.repository.CardProcessingFailureRepository;
import com.waqiti.payment.service.CardProcessingService;
import com.waqiti.payment.service.CardNetworkService;
import com.waqiti.payment.service.CardSecurityService;
import com.waqiti.payment.metrics.CardProcessingMetricsService;
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
public class CardProcessingFailuresConsumer {

    private final CardProcessingFailureRepository failureRepository;
    private final CardProcessingService cardProcessingService;
    private final CardNetworkService networkService;
    private final CardSecurityService securityService;
    private final CardProcessingMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final UniversalDLQHandler dlqHandler;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000;

    // Financial impact tracking
    private final AtomicLong totalCardFailureAmount = new AtomicLong(0);
    private final AtomicLong cardFailureCount = new AtomicLong(0);

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Counter financialImpactCounter;
    private Timer processingTimer;
    private Gauge cardFailureAmountGauge;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("card_processing_failures_processed_total")
            .description("Total number of successfully processed card processing failure events")
            .register(meterRegistry);
        errorCounter = Counter.builder("card_processing_failures_errors_total")
            .description("Total number of card processing failure processing errors")
            .register(meterRegistry);
        financialImpactCounter = Counter.builder("card_processing_failures_financial_impact")
            .description("Financial impact of card processing failures")
            .register(meterRegistry);
        processingTimer = Timer.builder("card_processing_failures_processing_duration")
            .description("Time taken to process card processing failure events")
            .register(meterRegistry);
        cardFailureAmountGauge = Gauge.builder("card_processing_failure_amount_total")
            .description("Total amount affected by card processing failures")
            .register(meterRegistry, totalCardFailureAmount, AtomicLong::get);
    }

    @KafkaListener(
        topics = {"card-processing-failures", "card-network-failures", "card-authorization-failures"},
        groupId = "card-processing-failures-service-group",
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
    @CircuitBreaker(name = "card-processing-failures", fallbackMethod = "handleCardProcessingFailureFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleCardProcessingFailureEvent(
            @Payload CardProcessingFailureEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("card-fail-%s-p%d-o%d", event.getTransactionId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getTransactionId(), event.getFailureCode(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.error("Processing card processing failure: transactionId={}, failureCode={}, amount={}, cardNetwork={}",
                event.getTransactionId(), event.getFailureCode(), event.getAmount(), event.getCardNetwork());

            // Clean expired entries periodically
            cleanExpiredEntries();

            // Financial impact assessment
            assessFinancialImpact(event.getAmount(), event.getFailureCode(), correlationId);

            switch (event.getFailureType()) {
                case NETWORK_TIMEOUT:
                    handleNetworkTimeout(event, correlationId);
                    break;

                case ISSUER_UNAVAILABLE:
                    handleIssuerUnavailable(event, correlationId);
                    break;

                case CARD_DECLINED:
                    handleCardDeclined(event, correlationId);
                    break;

                case INVALID_CARD_DATA:
                    handleInvalidCardData(event, correlationId);
                    break;

                case SECURITY_VIOLATION:
                    handleSecurityViolation(event, correlationId);
                    break;

                case PROCESSING_ERROR:
                    handleProcessingError(event, correlationId);
                    break;

                case CURRENCY_NOT_SUPPORTED:
                    handleCurrencyNotSupported(event, correlationId);
                    break;

                case MERCHANT_CONFIGURATION_ERROR:
                    handleMerchantConfigurationError(event, correlationId);
                    break;

                case FRAUD_DETECTION_FAILURE:
                    handleFraudDetectionFailure(event, correlationId);
                    break;

                case SETTLEMENT_FAILURE:
                    handleSettlementFailure(event, correlationId);
                    break;

                default:
                    log.warn("Unknown card processing failure type: {}", event.getFailureType());
                    handleGenericCardProcessingFailure(event, correlationId);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logCardProcessingEvent("CARD_PROCESSING_FAILURE_PROCESSED", event.getTransactionId(),
                Map.of("failureType", event.getFailureType(), "failureCode", event.getFailureCode(),
                    "amount", event.getAmount(), "cardNetwork", event.getCardNetwork(),
                    "merchantId", event.getMerchantId(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process card processing failure event: {}", e.getMessage(), e);

            // Send to DLQ with context
            dlqHandler.handleFailedMessage(
                "card-processing-failures",
                event,
                e,
                Map.of(
                    "transactionId", event.getTransactionId(),
                    "failureType", event.getFailureType().toString(),
                    "failureCode", event.getFailureCode(),
                    "amount", event.getAmount().toString(),
                    "cardNetwork", event.getCardNetwork(),
                    "correlationId", correlationId,
                    "partition", String.valueOf(partition),
                    "offset", String.valueOf(offset)
                )
            );

            // Send fallback event
            kafkaTemplate.send("card-processing-failure-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            processingTimer.stop(sample);
        }
    }

    public void handleCardProcessingFailureFallback(
            CardProcessingFailureEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("card-fail-fallback-%s-p%d-o%d", event.getTransactionId(), partition, offset);

        log.error("Circuit breaker fallback triggered for card processing failure: transactionId={}, error={}",
            event.getTransactionId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("card-processing-failures-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send critical alert for high-value transactions
        if (event.getAmount().compareTo(BigDecimal.valueOf(10000)) > 0) {
            try {
                notificationService.sendExecutiveAlert(
                    "High-Value Card Processing Failure - Circuit Breaker Triggered",
                    String.format("Card transaction %s (Amount: %s) failed: %s",
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
    public void handleDltCardProcessingFailureEvent(
            @Payload CardProcessingFailureEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-card-fail-%s-%d", event.getTransactionId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Card processing failure permanently failed: transactionId={}, topic={}, error={}",
            event.getTransactionId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logCardProcessingEvent("CARD_PROCESSING_FAILURE_DLT_EVENT", event.getTransactionId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "failureType", event.getFailureType(), "amount", event.getAmount(),
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Card Processing Failure Dead Letter Event",
                String.format("Card transaction %s (Amount: %s) sent to DLT: %s",
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
        totalCardFailureAmount.addAndGet(amountCents);
        cardFailureCount.incrementAndGet();
        financialImpactCounter.increment(amountCents);

        // Alert if cumulative card failure amount exceeds threshold
        if (totalCardFailureAmount.get() > 10000000000L) { // $100M in cents
            try {
                notificationService.sendExecutiveAlert(
                    "CRITICAL: Card Processing Failures Exceed $100M",
                    String.format("Cumulative card failure impact: $%,.2f. Payment network review required.",
                        totalCardFailureAmount.get() / 100.0),
                    "CRITICAL"
                );
                // Reset counter after alert
                totalCardFailureAmount.set(0);
            } catch (Exception ex) {
                log.error("Failed to send financial impact alert: {}", ex.getMessage());
            }
        }

        // High-value card transaction failure alert
        if (amount.compareTo(BigDecimal.valueOf(10000)) > 0) {
            try {
                notificationService.sendExecutiveAlert(
                    "High-Value Card Processing Failure",
                    String.format("Card transaction amount: $%,.2f, Failure: %s", amount, failureCode),
                    "HIGH"
                );
            } catch (Exception ex) {
                log.error("Failed to send high-value card failure alert: {}", ex.getMessage());
            }
        }
    }

    private void handleNetworkTimeout(CardProcessingFailureEvent event, String correlationId) {
        createFailureRecord(event, "NETWORK_TIMEOUT", correlationId);

        // Test network connectivity to card network
        kafkaTemplate.send("card-network-connectivity-tests", Map.of(
            "cardNetwork", event.getCardNetwork(),
            "testType", "CONNECTIVITY_DIAGNOSTIC",
            "priority", "HIGH",
            "transactionId", event.getTransactionId(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Schedule transaction retry
        kafkaTemplate.send("card-transaction-retry-schedules", Map.of(
            "transactionId", event.getTransactionId(),
            "retryType", "NETWORK_TIMEOUT",
            "scheduleDelay", 300000, // 5 minutes
            "maxRetries", 3,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordCardProcessingFailure("NETWORK_TIMEOUT", event.getAmount());
    }

    private void handleIssuerUnavailable(CardProcessingFailureEvent event, String correlationId) {
        createFailureRecord(event, "ISSUER_UNAVAILABLE", correlationId);

        // Check issuer status
        kafkaTemplate.send("issuer-status-checks", Map.of(
            "issuerCode", event.getIssuerCode(),
            "cardNetwork", event.getCardNetwork(),
            "checkType", "AVAILABILITY",
            "transactionId", event.getTransactionId(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Route to alternative network if available
        kafkaTemplate.send("alternative-network-routing", Map.of(
            "transactionId", event.getTransactionId(),
            "primaryNetwork", event.getCardNetwork(),
            "routingType", "ISSUER_UNAVAILABLE",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Card Issuer Unavailable",
            String.format("Card issuer %s unavailable for network %s",
                event.getIssuerCode(), event.getCardNetwork()),
            "HIGH");

        metricsService.recordCardProcessingFailure("ISSUER_UNAVAILABLE", event.getAmount());
    }

    private void handleCardDeclined(CardProcessingFailureEvent event, String correlationId) {
        createFailureRecord(event, "CARD_DECLINED", correlationId);

        // Analyze decline reason
        kafkaTemplate.send("card-decline-analysis", Map.of(
            "transactionId", event.getTransactionId(),
            "declineCode", event.getDeclineCode(),
            "declineReason", event.getDeclineReason(),
            "analysisType", "DECLINE_PATTERN",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Check for retry eligibility
        kafkaTemplate.send("card-retry-eligibility-checks", Map.of(
            "transactionId", event.getTransactionId(),
            "declineCode", event.getDeclineCode(),
            "checkType", "RETRY_RULES",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Notify customer about decline
        notificationService.sendCustomerAlert(event.getCardHolderId(),
            "Card Transaction Declined",
            String.format("Your card transaction was declined: %s", event.getDeclineReason()),
            correlationId);

        metricsService.recordCardProcessingFailure("CARD_DECLINED", event.getAmount());
    }

    private void handleInvalidCardData(CardProcessingFailureEvent event, String correlationId) {
        createFailureRecord(event, "INVALID_CARD_DATA", correlationId);

        // Validate card data format
        kafkaTemplate.send("card-data-validation", Map.of(
            "transactionId", event.getTransactionId(),
            "validationType", "CARD_DATA_FORMAT",
            "invalidFields", event.getInvalidFields(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Check for data corruption
        kafkaTemplate.send("card-data-integrity-checks", Map.of(
            "transactionId", event.getTransactionId(),
            "checkType", "DATA_CORRUPTION",
            "suspectedFields", event.getInvalidFields(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendCustomerAlert(event.getCardHolderId(),
            "Invalid Card Information",
            "There was an issue with your card information. Please verify your card details.",
            correlationId);

        metricsService.recordCardProcessingFailure("INVALID_CARD_DATA", event.getAmount());
    }

    private void handleSecurityViolation(CardProcessingFailureEvent event, String correlationId) {
        createFailureRecord(event, "SECURITY_VIOLATION", correlationId);

        // Enhanced security screening
        kafkaTemplate.send("enhanced-security-screening", Map.of(
            "transactionId", event.getTransactionId(),
            "securityViolationType", event.getSecurityViolationType(),
            "screeningType", "ENHANCED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Temporary card restriction
        kafkaTemplate.send("card-security-restrictions", Map.of(
            "cardId", event.getCardId(),
            "restrictionType", "SECURITY_VIOLATION",
            "duration", "24_HOURS",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendSecurityAlert(event.getCardHolderId(),
            "Security Violation Detected",
            "We've detected a security issue with your card and temporarily restricted it for your protection.",
            correlationId);

        metricsService.recordCardProcessingFailure("SECURITY_VIOLATION", event.getAmount());
    }

    private void handleProcessingError(CardProcessingFailureEvent event, String correlationId) {
        createFailureRecord(event, "PROCESSING_ERROR", correlationId);

        // System health check
        kafkaTemplate.send("card-processing-health-checks", Map.of(
            "component", "CARD_PROCESSING_ENGINE",
            "healthCheckType", "COMPREHENSIVE",
            "priority", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Retry with alternative processor
        kafkaTemplate.send("alternative-processor-routing", Map.of(
            "transactionId", event.getTransactionId(),
            "primaryProcessor", event.getProcessorId(),
            "routingType", "PROCESSING_ERROR",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Card Processing Error",
            String.format("Processing error for transaction %s: %s",
                event.getTransactionId(), event.getErrorMessage()),
            "HIGH");

        metricsService.recordCardProcessingFailure("PROCESSING_ERROR", event.getAmount());
    }

    private void handleCurrencyNotSupported(CardProcessingFailureEvent event, String correlationId) {
        createFailureRecord(event, "CURRENCY_NOT_SUPPORTED", correlationId);

        // Check supported currencies
        kafkaTemplate.send("card-currency-support-checks", Map.of(
            "cardNetwork", event.getCardNetwork(),
            "currency", event.getCurrency(),
            "checkType", "CURRENCY_SUPPORT",
            "transactionId", event.getTransactionId(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Suggest currency conversion
        kafkaTemplate.send("currency-conversion-suggestions", Map.of(
            "transactionId", event.getTransactionId(),
            "originalCurrency", event.getCurrency(),
            "supportedCurrencies", event.getSupportedCurrencies(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendCustomerAlert(event.getCardHolderId(),
            "Currency Not Supported",
            String.format("Currency %s is not supported for this transaction", event.getCurrency()),
            correlationId);

        metricsService.recordCardProcessingFailure("CURRENCY_NOT_SUPPORTED", event.getAmount());
    }

    private void handleMerchantConfigurationError(CardProcessingFailureEvent event, String correlationId) {
        createFailureRecord(event, "MERCHANT_CONFIGURATION_ERROR", correlationId);

        // Validate merchant configuration
        kafkaTemplate.send("merchant-configuration-validation", Map.of(
            "merchantId", event.getMerchantId(),
            "validationType", "COMPLETE_CONFIG",
            "configurationError", event.getConfigurationError(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Auto-correct configuration if possible
        kafkaTemplate.send("merchant-configuration-correction", Map.of(
            "merchantId", event.getMerchantId(),
            "correctionType", "AUTO_FIX",
            "configurationError", event.getConfigurationError(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendMerchantAlert(event.getMerchantId(),
            "Configuration Error",
            "There's a configuration issue with your payment setup. Our team will contact you shortly.",
            correlationId);

        metricsService.recordCardProcessingFailure("MERCHANT_CONFIGURATION_ERROR", event.getAmount());
    }

    private void handleFraudDetectionFailure(CardProcessingFailureEvent event, String correlationId) {
        createFailureRecord(event, "FRAUD_DETECTION_FAILURE", correlationId);

        // Enhanced fraud screening
        kafkaTemplate.send("enhanced-fraud-screening", Map.of(
            "transactionId", event.getTransactionId(),
            "fraudScore", event.getFraudScore(),
            "riskFactors", event.getRiskFactors(),
            "screeningType", "ENHANCED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Manual review request
        kafkaTemplate.send("manual-fraud-review-requests", Map.of(
            "transactionId", event.getTransactionId(),
            "reviewType", "FRAUD_DETECTION_FAILURE",
            "priority", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendSecurityAlert(event.getCardHolderId(),
            "Transaction Under Review",
            "Your transaction is being reviewed for security purposes. We'll notify you once complete.",
            correlationId);

        metricsService.recordCardProcessingFailure("FRAUD_DETECTION_FAILURE", event.getAmount());
    }

    private void handleSettlementFailure(CardProcessingFailureEvent event, String correlationId) {
        createFailureRecord(event, "SETTLEMENT_FAILURE", correlationId);

        // Check settlement account balance
        kafkaTemplate.send("settlement-account-checks", Map.of(
            "merchantId", event.getMerchantId(),
            "requiredAmount", event.getAmount(),
            "checkType", "SETTLEMENT_BALANCE",
            "transactionId", event.getTransactionId(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Schedule settlement retry
        kafkaTemplate.send("settlement-retry-schedules", Map.of(
            "transactionId", event.getTransactionId(),
            "retryType", "SETTLEMENT_FAILURE",
            "scheduleDelay", 3600000, // 1 hour
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Card Settlement Failure",
            String.format("Settlement failure for transaction %s: %s",
                event.getTransactionId(), event.getSettlementError()),
            "HIGH");

        metricsService.recordCardProcessingFailure("SETTLEMENT_FAILURE", event.getAmount());
    }

    private void handleGenericCardProcessingFailure(CardProcessingFailureEvent event, String correlationId) {
        createFailureRecord(event, "UNKNOWN_FAILURE", correlationId);

        // Log for investigation
        auditService.logCardProcessingEvent("UNKNOWN_CARD_PROCESSING_FAILURE", event.getTransactionId(),
            Map.of("failureType", event.getFailureType(), "failureCode", event.getFailureCode(),
                "errorMessage", event.getErrorMessage(), "correlationId", correlationId,
                "requiresInvestigation", true, "timestamp", Instant.now()));

        notificationService.sendOperationalAlert("Unknown Card Processing Failure",
            String.format("Unknown card processing failure for transaction %s: %s",
                event.getTransactionId(), event.getErrorMessage()),
            "HIGH");

        metricsService.recordCardProcessingFailure("UNKNOWN", event.getAmount());
    }

    private void createFailureRecord(CardProcessingFailureEvent event, String failureType, String correlationId) {
        try {
            CardProcessingFailure failure = CardProcessingFailure.builder()
                .transactionId(event.getTransactionId())
                .failureType(failureType)
                .failureCode(event.getFailureCode())
                .errorMessage(event.getErrorMessage())
                .amount(event.getAmount())
                .currency(event.getCurrency())
                .cardNetwork(event.getCardNetwork())
                .merchantId(event.getMerchantId())
                .failureTime(LocalDateTime.now())
                .status("OPEN")
                .correlationId(correlationId)
                .build();

            failureRepository.save(failure);
        } catch (Exception e) {
            log.error("Failed to create card processing failure record: {}", e.getMessage());
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