package com.waqiti.payment.kafka;

import com.waqiti.common.events.PaymentGatewayFailureEvent;
import com.waqiti.payment.domain.GatewayFailure;
import com.waqiti.payment.repository.GatewayFailureRepository;
import com.waqiti.payment.service.PaymentGatewayService;
import com.waqiti.payment.service.GatewayHealthService;
import com.waqiti.payment.service.GatewayFailoverService;
import com.waqiti.payment.metrics.GatewayMetricsService;
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
public class PaymentGatewayFailuresConsumer {

    private final GatewayFailureRepository failureRepository;
    private final PaymentGatewayService gatewayService;
    private final GatewayHealthService healthService;
    private final GatewayFailoverService failoverService;
    private final GatewayMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000;

    // Financial impact tracking
    private final AtomicLong totalImpactedAmount = new AtomicLong(0);
    private final AtomicLong gatewayFailureCount = new AtomicLong(0);

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Counter financialImpactCounter;
    private Timer processingTimer;
    private Gauge impactedAmountGauge;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("payment_gateway_failures_processed_total")
            .description("Total number of successfully processed payment gateway failure events")
            .register(meterRegistry);
        errorCounter = Counter.builder("payment_gateway_failures_errors_total")
            .description("Total number of payment gateway failure processing errors")
            .register(meterRegistry);
        financialImpactCounter = Counter.builder("payment_gateway_failures_financial_impact")
            .description("Financial impact of payment gateway failures")
            .register(meterRegistry);
        processingTimer = Timer.builder("payment_gateway_failures_processing_duration")
            .description("Time taken to process payment gateway failure events")
            .register(meterRegistry);
        impactedAmountGauge = Gauge.builder("payment_gateway_impacted_amount_total")
            .description("Total amount impacted by payment gateway failures")
            .register(meterRegistry, totalImpactedAmount, AtomicLong::get);
    }

    @KafkaListener(
        topics = {"payment-gateway-failures", "gateway-connectivity-failures", "gateway-timeout-events"},
        groupId = "payment-gateway-failures-service-group",
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
    @CircuitBreaker(name = "payment-gateway-failures", fallbackMethod = "handleGatewayFailureFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handlePaymentGatewayFailureEvent(
            @Payload PaymentGatewayFailureEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("gateway-fail-%s-p%d-o%d", event.getGatewayId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getGatewayId(), event.getFailureType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.error("Processing payment gateway failure: gatewayId={}, failureType={}, errorCode={}, impactedTransactions={}",
                event.getGatewayId(), event.getFailureType(), event.getErrorCode(), event.getImpactedTransactionCount());

            // Clean expired entries periodically
            cleanExpiredEntries();

            // Financial impact assessment
            assessFinancialImpact(event.getImpactedAmount(), event.getFailureType(), correlationId);

            switch (event.getFailureType()) {
                case CONNECTIVITY_FAILURE:
                    handleConnectivityFailure(event, correlationId);
                    break;

                case TIMEOUT_ERROR:
                    handleTimeoutError(event, correlationId);
                    break;

                case AUTHENTICATION_FAILURE:
                    handleAuthenticationFailure(event, correlationId);
                    break;

                case CERTIFICATE_ERROR:
                    handleCertificateError(event, correlationId);
                    break;

                case API_RATE_LIMIT_EXCEEDED:
                    handleRateLimitExceeded(event, correlationId);
                    break;

                case SERVICE_UNAVAILABLE:
                    handleServiceUnavailable(event, correlationId);
                    break;

                case INVALID_RESPONSE:
                    handleInvalidResponse(event, correlationId);
                    break;

                case CONFIGURATION_ERROR:
                    handleConfigurationError(event, correlationId);
                    break;

                case VERSION_MISMATCH:
                    handleVersionMismatch(event, correlationId);
                    break;

                case CAPACITY_EXCEEDED:
                    handleCapacityExceeded(event, correlationId);
                    break;

                default:
                    log.warn("Unknown gateway failure type: {}", event.getFailureType());
                    handleGenericGatewayFailure(event, correlationId);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logGatewayEvent("PAYMENT_GATEWAY_FAILURE_PROCESSED", event.getGatewayId(),
                Map.of("failureType", event.getFailureType(), "errorCode", event.getErrorCode(),
                    "impactedTransactions", event.getImpactedTransactionCount(),
                    "impactedAmount", event.getImpactedAmount(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process payment gateway failure event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("gateway-failure-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleGatewayFailureFallback(
            PaymentGatewayFailureEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("gateway-fail-fallback-%s-p%d-o%d", event.getGatewayId(), partition, offset);

        log.error("Circuit breaker fallback triggered for gateway failure: gatewayId={}, error={}",
            event.getGatewayId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("payment-gateway-failures-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send critical alert for high-impact failures
        if (event.getImpactedTransactionCount() > 100 ||
            event.getImpactedAmount().compareTo(BigDecimal.valueOf(100000)) > 0) {
            try {
                notificationService.sendExecutiveAlert(
                    "Critical Gateway Failure - Circuit Breaker Triggered",
                    String.format("Gateway %s failure impacting %d transactions ($%,.2f): %s",
                        event.getGatewayId(), event.getImpactedTransactionCount(),
                        event.getImpactedAmount(), ex.getMessage()),
                    "CRITICAL"
                );
            } catch (Exception notificationEx) {
                log.error("Failed to send executive alert: {}", notificationEx.getMessage());
            }
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltGatewayFailureEvent(
            @Payload PaymentGatewayFailureEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-gateway-fail-%s-%d", event.getGatewayId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Gateway failure permanently failed: gatewayId={}, topic={}, error={}",
            event.getGatewayId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logGatewayEvent("GATEWAY_FAILURE_DLT_EVENT", event.getGatewayId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "failureType", event.getFailureType(), "impactedAmount", event.getImpactedAmount(),
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Payment Gateway Failure Dead Letter Event",
                String.format("Gateway %s failure sent to DLT (Impact: %d transactions, $%,.2f): %s",
                    event.getGatewayId(), event.getImpactedTransactionCount(),
                    event.getImpactedAmount(), exceptionMessage),
                Map.of("gatewayId", event.getGatewayId(), "topic", topic,
                    "correlationId", correlationId, "impactedAmount", event.getImpactedAmount())
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private void assessFinancialImpact(BigDecimal impactedAmount, String failureType, String correlationId) {
        long amountCents = impactedAmount.multiply(BigDecimal.valueOf(100)).longValue();
        totalImpactedAmount.addAndGet(amountCents);
        gatewayFailureCount.incrementAndGet();
        financialImpactCounter.increment(amountCents);

        // Alert if cumulative impacted amount exceeds threshold
        if (totalImpactedAmount.get() > 1000000000) { // $10M in cents
            try {
                notificationService.sendExecutiveAlert(
                    "CRITICAL: Gateway Failures Impact Exceeds $10M",
                    String.format("Cumulative gateway failure impact: $%,.2f. System stability review required.",
                        totalImpactedAmount.get() / 100.0),
                    "CRITICAL"
                );
                // Reset counter after alert
                totalImpactedAmount.set(0);
            } catch (Exception ex) {
                log.error("Failed to send financial impact alert: {}", ex.getMessage());
            }
        }

        // High-impact failure alert
        if (impactedAmount.compareTo(BigDecimal.valueOf(500000)) > 0) {
            try {
                notificationService.sendExecutiveAlert(
                    "High-Impact Gateway Failure",
                    String.format("Gateway failure impact: $%,.2f, Type: %s", impactedAmount, failureType),
                    "HIGH"
                );
            } catch (Exception ex) {
                log.error("Failed to send high-impact failure alert: {}", ex.getMessage());
            }
        }
    }

    private void handleConnectivityFailure(PaymentGatewayFailureEvent event, String correlationId) {
        createFailureRecord(event, "CONNECTIVITY_FAILURE", correlationId);

        // Immediate failover to backup gateway
        failoverService.initiateFailover(event.getGatewayId(), "CONNECTIVITY_FAILURE");

        // Trigger network diagnostics
        kafkaTemplate.send("network-diagnostics-requests", Map.of(
            "target", event.getGatewayEndpoint(),
            "diagnosticType", "CONNECTIVITY",
            "priority", "CRITICAL",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Schedule connection retry
        kafkaTemplate.send("gateway-reconnection-requests", Map.of(
            "gatewayId", event.getGatewayId(),
            "retryType", "CONNECTIVITY",
            "scheduleDelay", 60000, // 1 minute
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Payment Gateway Connectivity Failure",
            String.format("Gateway %s connectivity failed, impacting %d transactions",
                event.getGatewayId(), event.getImpactedTransactionCount()),
            "CRITICAL");

        metricsService.recordGatewayFailure("CONNECTIVITY_FAILURE", event.getImpactedAmount());
    }

    private void handleTimeoutError(PaymentGatewayFailureEvent event, String correlationId) {
        createFailureRecord(event, "TIMEOUT_ERROR", correlationId);

        // Adjust timeout configurations
        kafkaTemplate.send("gateway-configuration-updates", Map.of(
            "gatewayId", event.getGatewayId(),
            "configurationType", "TIMEOUT_ADJUSTMENT",
            "newTimeoutValue", event.getCurrentTimeout() * 1.5, // Increase by 50%
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Check gateway performance
        healthService.performHealthCheck(event.getGatewayId());

        // Retry impacted transactions
        kafkaTemplate.send("transaction-retry-requests", Map.of(
            "gatewayId", event.getGatewayId(),
            "retryReason", "TIMEOUT_ERROR",
            "impactedTransactions", event.getImpactedTransactionIds(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordGatewayFailure("TIMEOUT_ERROR", event.getImpactedAmount());
    }

    private void handleAuthenticationFailure(PaymentGatewayFailureEvent event, String correlationId) {
        createFailureRecord(event, "AUTHENTICATION_FAILURE", correlationId);

        // Refresh authentication credentials
        kafkaTemplate.send("gateway-auth-refresh-requests", Map.of(
            "gatewayId", event.getGatewayId(),
            "authType", "CREDENTIAL_REFRESH",
            "priority", "IMMEDIATE",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Validate API keys and certificates
        kafkaTemplate.send("gateway-credential-validation", Map.of(
            "gatewayId", event.getGatewayId(),
            "validationType", "COMPLETE",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Gateway Authentication Failure",
            String.format("Gateway %s authentication failed, refreshing credentials",
                event.getGatewayId()),
            "HIGH");

        metricsService.recordGatewayFailure("AUTHENTICATION_FAILURE", event.getImpactedAmount());
    }

    private void handleCertificateError(PaymentGatewayFailureEvent event, String correlationId) {
        createFailureRecord(event, "CERTIFICATE_ERROR", correlationId);

        // Check certificate validity
        kafkaTemplate.send("certificate-validation-requests", Map.of(
            "gatewayId", event.getGatewayId(),
            "certificateType", "SSL_TLS",
            "validationType", "EXPIRATION_CHECK",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Renew certificate if expired
        kafkaTemplate.send("certificate-renewal-requests", Map.of(
            "gatewayId", event.getGatewayId(),
            "renewalType", "EMERGENCY",
            "priority", "CRITICAL",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Gateway Certificate Error",
            String.format("Gateway %s certificate error, initiating renewal",
                event.getGatewayId()),
            "HIGH");

        metricsService.recordGatewayFailure("CERTIFICATE_ERROR", event.getImpactedAmount());
    }

    private void handleRateLimitExceeded(PaymentGatewayFailureEvent event, String correlationId) {
        createFailureRecord(event, "RATE_LIMIT_EXCEEDED", correlationId);

        // Implement rate limiting strategy
        kafkaTemplate.send("rate-limiting-adjustments", Map.of(
            "gatewayId", event.getGatewayId(),
            "adjustmentType", "INCREASE_LIMITS",
            "newRateLimit", event.getCurrentRateLimit() * 1.25, // Increase by 25%
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Distribute load to other gateways
        failoverService.redistributeLoad(event.getGatewayId(), "RATE_LIMIT_EXCEEDED");

        // Queue impacted transactions
        kafkaTemplate.send("transaction-queue-requests", Map.of(
            "gatewayId", event.getGatewayId(),
            "queueType", "RATE_LIMIT_RECOVERY",
            "impactedTransactions", event.getImpactedTransactionIds(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordGatewayFailure("RATE_LIMIT_EXCEEDED", event.getImpactedAmount());
    }

    private void handleServiceUnavailable(PaymentGatewayFailureEvent event, String correlationId) {
        createFailureRecord(event, "SERVICE_UNAVAILABLE", correlationId);

        // Immediate failover
        failoverService.initiateFailover(event.getGatewayId(), "SERVICE_UNAVAILABLE");

        // Check gateway status
        kafkaTemplate.send("gateway-status-checks", Map.of(
            "gatewayId", event.getGatewayId(),
            "checkType", "SERVICE_AVAILABILITY",
            "priority", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Schedule periodic availability checks
        kafkaTemplate.send("gateway-monitoring-schedules", Map.of(
            "gatewayId", event.getGatewayId(),
            "monitoringType", "AVAILABILITY",
            "checkInterval", 300000, // 5 minutes
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Gateway Service Unavailable",
            String.format("Gateway %s service unavailable, failover initiated",
                event.getGatewayId()),
            "CRITICAL");

        metricsService.recordGatewayFailure("SERVICE_UNAVAILABLE", event.getImpactedAmount());
    }

    private void handleInvalidResponse(PaymentGatewayFailureEvent event, String correlationId) {
        createFailureRecord(event, "INVALID_RESPONSE", correlationId);

        // Log response for analysis
        auditService.logGatewayEvent("INVALID_RESPONSE_RECEIVED", event.getGatewayId(),
            Map.of("responseData", event.getInvalidResponse(), "expectedFormat", event.getExpectedFormat(),
                "correlationId", correlationId, "timestamp", Instant.now()));

        // Validate response parser
        kafkaTemplate.send("response-parser-validation", Map.of(
            "gatewayId", event.getGatewayId(),
            "validationType", "PARSER_LOGIC",
            "sampleResponse", event.getInvalidResponse(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Retry with alternative parsing
        kafkaTemplate.send("response-parsing-retry", Map.of(
            "gatewayId", event.getGatewayId(),
            "retryType", "ALTERNATIVE_PARSER",
            "impactedTransactions", event.getImpactedTransactionIds(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordGatewayFailure("INVALID_RESPONSE", event.getImpactedAmount());
    }

    private void handleConfigurationError(PaymentGatewayFailureEvent event, String correlationId) {
        createFailureRecord(event, "CONFIGURATION_ERROR", correlationId);

        // Validate gateway configuration
        kafkaTemplate.send("gateway-configuration-validation", Map.of(
            "gatewayId", event.getGatewayId(),
            "validationType", "COMPLETE_CONFIG",
            "configError", event.getConfigurationError(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Restore last known good configuration
        kafkaTemplate.send("configuration-rollback-requests", Map.of(
            "gatewayId", event.getGatewayId(),
            "rollbackType", "LAST_KNOWN_GOOD",
            "priority", "IMMEDIATE",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Gateway Configuration Error",
            String.format("Gateway %s configuration error, rolling back changes",
                event.getGatewayId()),
            "HIGH");

        metricsService.recordGatewayFailure("CONFIGURATION_ERROR", event.getImpactedAmount());
    }

    private void handleVersionMismatch(PaymentGatewayFailureEvent event, String correlationId) {
        createFailureRecord(event, "VERSION_MISMATCH", correlationId);

        // Update API version
        kafkaTemplate.send("gateway-version-updates", Map.of(
            "gatewayId", event.getGatewayId(),
            "currentVersion", event.getCurrentApiVersion(),
            "requiredVersion", event.getRequiredApiVersion(),
            "updateType", "API_VERSION",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Test version compatibility
        kafkaTemplate.send("version-compatibility-tests", Map.of(
            "gatewayId", event.getGatewayId(),
            "testType", "API_VERSION_COMPATIBILITY",
            "targetVersion", event.getRequiredApiVersion(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordGatewayFailure("VERSION_MISMATCH", event.getImpactedAmount());
    }

    private void handleCapacityExceeded(PaymentGatewayFailureEvent event, String correlationId) {
        createFailureRecord(event, "CAPACITY_EXCEEDED", correlationId);

        // Increase gateway capacity
        kafkaTemplate.send("gateway-scaling-requests", Map.of(
            "gatewayId", event.getGatewayId(),
            "scalingType", "CAPACITY_INCREASE",
            "currentCapacity", event.getCurrentCapacity(),
            "requestedCapacity", event.getCurrentCapacity() * 1.5,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Load balance to other gateways
        failoverService.redistributeLoad(event.getGatewayId(), "CAPACITY_EXCEEDED");

        notificationService.sendOperationalAlert("Gateway Capacity Exceeded",
            String.format("Gateway %s capacity exceeded, scaling up resources",
                event.getGatewayId()),
            "HIGH");

        metricsService.recordGatewayFailure("CAPACITY_EXCEEDED", event.getImpactedAmount());
    }

    private void handleGenericGatewayFailure(PaymentGatewayFailureEvent event, String correlationId) {
        createFailureRecord(event, "UNKNOWN_FAILURE", correlationId);

        // Log for investigation
        auditService.logGatewayEvent("UNKNOWN_GATEWAY_FAILURE", event.getGatewayId(),
            Map.of("failureType", event.getFailureType(), "errorCode", event.getErrorCode(),
                "errorMessage", event.getErrorMessage(), "correlationId", correlationId,
                "requiresInvestigation", true, "timestamp", Instant.now()));

        notificationService.sendOperationalAlert("Unknown Gateway Failure",
            String.format("Unknown gateway failure for %s: %s",
                event.getGatewayId(), event.getErrorMessage()),
            "HIGH");

        metricsService.recordGatewayFailure("UNKNOWN", event.getImpactedAmount());
    }

    private void createFailureRecord(PaymentGatewayFailureEvent event, String failureType, String correlationId) {
        try {
            GatewayFailure failure = GatewayFailure.builder()
                .gatewayId(event.getGatewayId())
                .failureType(failureType)
                .errorCode(event.getErrorCode())
                .errorMessage(event.getErrorMessage())
                .impactedTransactionCount(event.getImpactedTransactionCount())
                .impactedAmount(event.getImpactedAmount())
                .failureTime(LocalDateTime.now())
                .status("OPEN")
                .correlationId(correlationId)
                .build();

            failureRepository.save(failure);
        } catch (Exception e) {
            log.error("Failed to create gateway failure record: {}", e.getMessage());
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