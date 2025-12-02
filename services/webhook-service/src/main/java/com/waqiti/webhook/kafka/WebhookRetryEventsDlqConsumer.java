package com.waqiti.webhook.kafka;

import com.waqiti.common.events.WebhookRetryEvent;
import com.waqiti.webhook.domain.WebhookDeliveryFailure;
import com.waqiti.webhook.repository.WebhookDeliveryFailureRepository;
import com.waqiti.webhook.service.WebhookDeliveryService;
import com.waqiti.webhook.service.WebhookEscalationService;
import com.waqiti.webhook.metrics.WebhookMetricsService;
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
import io.micrometer.core.instrument.MeterRegistry;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class WebhookRetryEventsDlqConsumer {

    private final WebhookDeliveryFailureRepository deliveryFailureRepository;
    private final WebhookDeliveryService deliveryService;
    private final WebhookEscalationService escalationService;
    private final WebhookMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000;

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Counter criticalWebhookFailureCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("webhook_retry_dlq_processed_total")
            .description("Total number of successfully processed webhook retry DLQ events")
            .register(meterRegistry);
        errorCounter = Counter.builder("webhook_retry_dlq_errors_total")
            .description("Total number of webhook retry DLQ processing errors")
            .register(meterRegistry);
        criticalWebhookFailureCounter = Counter.builder("webhook_critical_failures_total")
            .description("Total number of critical webhook failures requiring escalation")
            .register(meterRegistry);
        processingTimer = Timer.builder("webhook_retry_dlq_processing_duration")
            .description("Time taken to process webhook retry DLQ events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"webhook-retry-events-dlq", "webhook-delivery-failures-dlq", "webhook-critical-failures-dlq"},
        groupId = "webhook-retry-dlq-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "webhook-retry-dlq", fallbackMethod = "handleWebhookRetryDlqEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleWebhookRetryDlqEvent(
            @Payload WebhookRetryEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("webhook-dlq-%s-p%d-o%d", event.getWebhookId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getWebhookId(), event.getRetryAttempt(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Webhook retry DLQ event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.error("Processing CRITICAL webhook retry from DLQ: webhookId={}, attempts={}, endpoint={}, topic={}",
                event.getWebhookId(), event.getRetryAttempt(), event.getEndpointUrl(), topic);

            // Clean expired entries periodically
            cleanExpiredEntries();

            // DLQ webhook events indicate critical delivery failures
            if (isCriticalWebhookFailure(event)) {
                criticalWebhookFailureCounter.increment();
                escalateWebhookFailureToOperations(event, correlationId, topic);
            }

            switch (event.getEventType()) {
                case PAYMENT_WEBHOOK_FAILURE:
                    processPaymentWebhookFailureDlq(event, correlationId, topic);
                    break;

                case TRANSACTION_WEBHOOK_FAILURE:
                    processTransactionWebhookFailureDlq(event, correlationId, topic);
                    break;

                case COMPLIANCE_WEBHOOK_FAILURE:
                    processComplianceWebhookFailureDlq(event, correlationId, topic);
                    break;

                case FRAUD_ALERT_WEBHOOK_FAILURE:
                    processFraudAlertWebhookFailureDlq(event, correlationId, topic);
                    break;

                case REGULATORY_WEBHOOK_FAILURE:
                    processRegulatoryWebhookFailureDlq(event, correlationId, topic);
                    break;

                case ACCOUNT_WEBHOOK_FAILURE:
                    processAccountWebhookFailureDlq(event, correlationId, topic);
                    break;

                case CUSTOMER_WEBHOOK_FAILURE:
                    processCustomerWebhookFailureDlq(event, correlationId, topic);
                    break;

                default:
                    processGenericWebhookFailureDlq(event, correlationId, topic);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logWebhookEvent("WEBHOOK_RETRY_DLQ_PROCESSED", event.getWebhookId(),
                Map.of("eventType", event.getEventType(), "retryAttempt", event.getRetryAttempt(),
                    "endpointUrl", event.getEndpointUrl(), "correlationId", correlationId,
                    "dlqTopic", topic, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process webhook retry DLQ event: {}", e.getMessage(), e);

            // Send to operations escalation for webhook DLQ failures
            sendOperationsEscalation(event, correlationId, topic, e);

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleWebhookRetryDlqEventFallback(
            WebhookRetryEvent event,
            int partition,
            long offset,
            String topic,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("webhook-dlq-fallback-%s-p%d-o%d", event.getWebhookId(), partition, offset);

        log.error("Circuit breaker fallback triggered for webhook retry DLQ: webhookId={}, topic={}, error={}",
            event.getWebhookId(), topic, ex.getMessage());

        // Critical: webhook DLQ circuit breaker means delivery system failure
        sendOperationsEscalation(event, correlationId, topic, ex);

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltWebhookRetryEvent(
            @Payload WebhookRetryEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-webhook-%s-%d", event.getWebhookId(), System.currentTimeMillis());

        log.error("CRITICAL: Webhook retry DLQ permanently failed - webhookId={}, topic={}, error={}",
            event.getWebhookId(), topic, exceptionMessage);

        // Save to audit trail for webhook monitoring
        auditService.logWebhookEvent("WEBHOOK_RETRY_DLQ_DLT_EVENT", event.getWebhookId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Immediate operations escalation for DLT webhook failures
        sendOperationsEscalation(event, correlationId, topic, new RuntimeException(exceptionMessage));
    }

    private boolean isCriticalWebhookFailure(WebhookRetryEvent event) {
        return event.getRetryAttempt() >= 5 ||
               Arrays.asList("PAYMENT_WEBHOOK_FAILURE", "COMPLIANCE_WEBHOOK_FAILURE",
                           "FRAUD_ALERT_WEBHOOK_FAILURE", "REGULATORY_WEBHOOK_FAILURE").contains(event.getEventType().toString()) ||
               event.getLastHttpStatus() >= 500;
    }

    private void processPaymentWebhookFailureDlq(WebhookRetryEvent event, String correlationId, String topic) {
        WebhookDeliveryFailure failure = WebhookDeliveryFailure.builder()
            .webhookId(event.getWebhookId())
            .failureType("PAYMENT_WEBHOOK_DLQ")
            .severity("CRITICAL")
            .description(String.format("Payment webhook failure from DLQ: %s", event.getFailureReason()))
            .endpointUrl(event.getEndpointUrl())
            .retryAttempts(event.getRetryAttempt())
            .lastHttpStatus(event.getLastHttpStatus())
            .correlationId(correlationId)
            .source(topic)
            .createdAt(LocalDateTime.now())
            .requiresManualIntervention(true)
            .build();
        deliveryFailureRepository.save(failure);

        deliveryService.disableWebhookEndpoint(event.getEndpointUrl(), "DLQ_PAYMENT_FAILURE");
        escalationService.escalatePaymentWebhookFailure(event, correlationId);

        // Notify payment operations team
        notificationService.sendPaymentAlert(
            "CRITICAL: Payment Webhook Delivery Failed (DLQ)",
            String.format("Payment webhook %s failed delivery from DLQ - endpoint disabled", event.getWebhookId()),
            "CRITICAL"
        );

        log.error("Payment webhook failure DLQ processed: webhookId={}", event.getWebhookId());
    }

    private void processTransactionWebhookFailureDlq(WebhookRetryEvent event, String correlationId, String topic) {
        deliveryService.recordTransactionWebhookFailure(event.getWebhookId(), event.getFailureReason(), "DLQ_SOURCE");
        escalationService.escalateTransactionWebhookFailure(event, correlationId);

        // Transaction team notification
        notificationService.sendTransactionAlert(
            "Transaction Webhook Delivery Failed (DLQ)",
            String.format("Transaction webhook %s failed delivery - may affect reconciliation", event.getWebhookId()),
            "HIGH"
        );

        log.error("Transaction webhook failure DLQ processed: webhookId={}", event.getWebhookId());
    }

    private void processComplianceWebhookFailureDlq(WebhookRetryEvent event, String correlationId, String topic) {
        deliveryService.recordComplianceWebhookFailure(event.getWebhookId(), event.getFailureReason());
        escalationService.escalateComplianceWebhookFailure(event, correlationId);

        // Compliance team notification
        notificationService.sendComplianceAlert(
            "CRITICAL: Compliance Webhook Delivery Failed (DLQ)",
            String.format("Compliance webhook %s failed delivery - regulatory impact possible", event.getWebhookId()),
            "CRITICAL"
        );

        log.error("Compliance webhook failure DLQ processed: webhookId={}", event.getWebhookId());
    }

    private void processFraudAlertWebhookFailureDlq(WebhookRetryEvent event, String correlationId, String topic) {
        deliveryService.recordFraudAlertWebhookFailure(event.getWebhookId(), event.getFailureReason());
        escalationService.escalateFraudAlertWebhookFailure(event, correlationId);

        // Fraud team notification
        notificationService.sendFraudAlert(
            "CRITICAL: Fraud Alert Webhook Delivery Failed (DLQ)",
            String.format("Fraud alert webhook %s failed delivery - security implications", event.getWebhookId()),
            "CRITICAL"
        );

        log.error("Fraud alert webhook failure DLQ processed: webhookId={}", event.getWebhookId());
    }

    private void processRegulatoryWebhookFailureDlq(WebhookRetryEvent event, String correlationId, String topic) {
        deliveryService.recordRegulatoryWebhookFailure(event.getWebhookId(), event.getFailureReason());
        escalationService.escalateRegulatoryWebhookFailure(event, correlationId);

        // Regulatory team notification
        notificationService.sendRegulatoryAlert(
            "CRITICAL: Regulatory Webhook Delivery Failed (DLQ)",
            String.format("Regulatory webhook %s failed delivery - compliance breach risk", event.getWebhookId()),
            Map.of("webhookId", event.getWebhookId(), "correlationId", correlationId)
        );

        log.error("Regulatory webhook failure DLQ processed: webhookId={}", event.getWebhookId());
    }

    private void processAccountWebhookFailureDlq(WebhookRetryEvent event, String correlationId, String topic) {
        deliveryService.recordAccountWebhookFailure(event.getWebhookId(), event.getFailureReason());
        escalationService.escalateAccountWebhookFailure(event, correlationId);

        log.error("Account webhook failure DLQ processed: webhookId={}", event.getWebhookId());
    }

    private void processCustomerWebhookFailureDlq(WebhookRetryEvent event, String correlationId, String topic) {
        deliveryService.recordCustomerWebhookFailure(event.getWebhookId(), event.getFailureReason());
        escalationService.escalateCustomerWebhookFailure(event, correlationId);

        log.error("Customer webhook failure DLQ processed: webhookId={}", event.getWebhookId());
    }

    private void processGenericWebhookFailureDlq(WebhookRetryEvent event, String correlationId, String topic) {
        deliveryService.recordGenericWebhookFailure(event.getWebhookId(), event.getFailureReason(), "DLQ_GENERIC");
        escalationService.escalateGenericWebhookFailure(event, correlationId);

        log.warn("Generic webhook failure DLQ processed: webhookId={}, type={}",
            event.getWebhookId(), event.getEventType());
    }

    private void escalateWebhookFailureToOperations(WebhookRetryEvent event, String correlationId, String topic) {
        try {
            notificationService.sendOperationalAlert(
                "CRITICAL: Webhook Delivery Failure from DLQ",
                String.format("Critical webhook delivery failure for %s from DLQ topic %s. " +
                    "Type: %s, Attempts: %d, Endpoint: %s, Reason: %s",
                    event.getWebhookId(), topic, event.getEventType(), event.getRetryAttempt(),
                    event.getEndpointUrl(), event.getFailureReason()),
                Map.of(
                    "webhookId", event.getWebhookId(),
                    "correlationId", correlationId,
                    "dlqTopic", topic,
                    "eventType", event.getEventType(),
                    "retryAttempts", event.getRetryAttempt(),
                    "endpointUrl", event.getEndpointUrl(),
                    "priority", "WEBHOOK_DELIVERY_CRITICAL"
                )
            );
        } catch (Exception ex) {
            log.error("Failed to send operations escalation: {}", ex.getMessage());
        }
    }

    private void sendOperationsEscalation(WebhookRetryEvent event, String correlationId, String topic, Exception ex) {
        try {
            notificationService.sendOperationalAlert(
                "SYSTEM CRITICAL: Webhook DLQ Processing Failure",
                String.format("CRITICAL SYSTEM FAILURE: Unable to process webhook retry from DLQ for %s. " +
                    "This indicates a serious webhook delivery system failure. " +
                    "Topic: %s, Error: %s", event.getWebhookId(), topic, ex.getMessage()),
                Map.of(
                    "webhookId", event.getWebhookId(),
                    "correlationId", correlationId,
                    "dlqTopic", topic,
                    "errorMessage", ex.getMessage(),
                    "priority", "WEBHOOK_SYSTEM_CRITICAL_FAILURE"
                )
            );
        } catch (Exception notificationEx) {
            log.error("CRITICAL: Failed to send operations escalation for webhook DLQ failure: {}", notificationEx.getMessage());
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