package com.waqiti.customer.kafka;

import com.waqiti.common.events.CustomerBlockRequestEvent;
import com.waqiti.customer.service.CustomerBlockingService;
import com.waqiti.customer.service.CustomerSecurityService;
import com.waqiti.customer.service.CustomerMetricsService;
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

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class CustomerBlockRequestsConsumer {

    private final CustomerBlockingService blockingService;
    private final CustomerSecurityService securityService;
    private final CustomerMetricsService metricsService;
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
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("customer_block_requests_processed_total")
            .description("Total number of successfully processed customer block request events")
            .register(meterRegistry);
        errorCounter = Counter.builder("customer_block_requests_errors_total")
            .description("Total number of customer block request processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("customer_block_requests_processing_duration")
            .description("Time taken to process customer block request events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"customer-block-requests", "account-suspension-requests", "customer-security-actions"},
        groupId = "customer-blocking-group",
        containerFactory = "criticalCustomerKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "customer-block-requests", fallbackMethod = "handleCustomerBlockRequestEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleCustomerBlockRequestEvent(
            @Payload CustomerBlockRequestEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("customer-block-%s-p%d-o%d", event.getCustomerId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getCustomerId(), event.getRequestType(), event.getTimestamp());

        try {
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.warn("Processing customer block request: customerId={}, type={}, reason={}, severity={}",
                event.getCustomerId(), event.getRequestType(), event.getBlockReason(), event.getSeverity());

            cleanExpiredEntries();

            switch (event.getRequestType()) {
                case IMMEDIATE_BLOCK:
                    handleImmediateBlock(event, correlationId);
                    break;

                case TEMPORARY_SUSPENSION:
                    handleTemporarySuspension(event, correlationId);
                    break;

                case PERMANENT_BLOCK:
                    handlePermanentBlock(event, correlationId);
                    break;

                case FRAUD_PREVENTION_BLOCK:
                    handleFraudPreventionBlock(event, correlationId);
                    break;

                case COMPLIANCE_BLOCK:
                    handleComplianceBlock(event, correlationId);
                    break;

                case SECURITY_INCIDENT_BLOCK:
                    handleSecurityIncidentBlock(event, correlationId);
                    break;

                case UNBLOCK_REQUEST:
                    handleUnblockRequest(event, correlationId);
                    break;

                case BLOCK_APPEAL:
                    handleBlockAppeal(event, correlationId);
                    break;

                default:
                    log.warn("Unknown customer block request type: {}", event.getRequestType());
                    break;
            }

            markEventAsProcessed(eventKey);

            auditService.logCustomerEvent("CUSTOMER_BLOCK_REQUEST_PROCESSED", event.getCustomerId(),
                Map.of("requestType", event.getRequestType(), "blockReason", event.getBlockReason(),
                    "severity", event.getSeverity(), "requestedBy", event.getRequestedBy(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process customer block request: {}", e.getMessage(), e);

            kafkaTemplate.send("customer-block-requests-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleCustomerBlockRequestEventFallback(
            CustomerBlockRequestEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("customer-block-fallback-%s-p%d-o%d", event.getCustomerId(), partition, offset);

        log.error("Circuit breaker fallback for customer block request: customerId={}, error={}",
            event.getCustomerId(), ex.getMessage());

        kafkaTemplate.send("customer-block-requests-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        try {
            notificationService.sendOperationalAlert(
                "Customer Block Request Processing Failure",
                String.format("Customer %s block request processing failed: %s", event.getCustomerId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltCustomerBlockRequestEvent(
            @Payload CustomerBlockRequestEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-customer-block-%s-%d", event.getCustomerId(), System.currentTimeMillis());

        log.error("DLT handler - Customer block request failed: customerId={}, topic={}, error={}",
            event.getCustomerId(), topic, exceptionMessage);

        auditService.logCustomerEvent("CUSTOMER_BLOCK_REQUEST_DLT_EVENT", event.getCustomerId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "requestType", event.getRequestType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        try {
            notificationService.sendCriticalAlert(
                "Customer Block Request DLT Event",
                String.format("Customer %s block request sent to DLT: %s", event.getCustomerId(), exceptionMessage),
                Map.of("customerId", event.getCustomerId(), "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
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

    private void handleImmediateBlock(CustomerBlockRequestEvent event, String correlationId) {
        log.error("Processing immediate block for customer: customerId={}, reason={}",
            event.getCustomerId(), event.getBlockReason());

        blockingService.executeImmediateBlock(event.getCustomerId(), event.getBlockReason());

        notificationService.sendCustomerNotification(
            event.getCustomerId(),
            "Account Temporarily Blocked",
            "Your account has been temporarily blocked. Please contact customer support for assistance.",
            correlationId
        );

        metricsService.recordCustomerBlocked(event.getCustomerId(), "IMMEDIATE");

        // Send to compliance monitoring
        kafkaTemplate.send("customer-compliance-events", Map.of(
            "eventType", "CUSTOMER_IMMEDIATE_BLOCK",
            "customerId", event.getCustomerId(),
            "reason", event.getBlockReason(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleTemporarySuspension(CustomerBlockRequestEvent event, String correlationId) {
        log.warn("Processing temporary suspension: customerId={}, duration={} days",
            event.getCustomerId(), event.getSuspensionDurationDays());

        blockingService.executeTemporarySuspension(
            event.getCustomerId(), event.getSuspensionDurationDays(), event.getBlockReason());

        notificationService.sendCustomerNotification(
            event.getCustomerId(),
            "Account Temporarily Suspended",
            String.format("Your account has been suspended for %d days. Reason: %s",
                event.getSuspensionDurationDays(), event.getBlockReason()),
            correlationId
        );

        metricsService.recordCustomerSuspended(event.getCustomerId(), event.getSuspensionDurationDays());
    }

    private void handlePermanentBlock(CustomerBlockRequestEvent event, String correlationId) {
        log.error("Processing PERMANENT block for customer: customerId={}, reason={}",
            event.getCustomerId(), event.getBlockReason());

        blockingService.executePermanentBlock(event.getCustomerId(), event.getBlockReason());

        notificationService.sendCustomerNotification(
            event.getCustomerId(),
            "Account Permanently Closed",
            String.format("Your account has been permanently closed. Reason: %s", event.getBlockReason()),
            correlationId
        );

        metricsService.recordCustomerPermanentlyBlocked(event.getCustomerId());

        // Trigger account closure workflow
        kafkaTemplate.send("account-closure-workflow", Map.of(
            "customerId", event.getCustomerId(),
            "eventType", "PERMANENT_BLOCK_INITIATED",
            "reason", event.getBlockReason(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleFraudPreventionBlock(CustomerBlockRequestEvent event, String correlationId) {
        log.error("Processing fraud prevention block: customerId={}, fraudIndicators={}",
            event.getCustomerId(), event.getFraudIndicators());

        blockingService.executeFraudPreventionBlock(
            event.getCustomerId(), event.getFraudIndicators(), event.getBlockReason());

        securityService.freezeCustomerAssets(event.getCustomerId(), "FRAUD_PREVENTION");

        notificationService.sendCustomerNotification(
            event.getCustomerId(),
            "Account Security Hold",
            "Your account is on security hold due to suspicious activity. Please contact us immediately.",
            correlationId
        );

        metricsService.recordFraudPreventionBlock(event.getCustomerId());

        // Send to fraud investigation
        kafkaTemplate.send("fraud-investigation-events", Map.of(
            "eventType", "CUSTOMER_FRAUD_BLOCK",
            "customerId", event.getCustomerId(),
            "fraudIndicators", event.getFraudIndicators(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleComplianceBlock(CustomerBlockRequestEvent event, String correlationId) {
        log.warn("Processing compliance block: customerId={}, regulation={}",
            event.getCustomerId(), event.getRegulation());

        blockingService.executeComplianceBlock(
            event.getCustomerId(), event.getRegulation(), event.getBlockReason());

        notificationService.sendCustomerNotification(
            event.getCustomerId(),
            "Account Compliance Hold",
            String.format("Your account is on hold for compliance review. Regulation: %s", event.getRegulation()),
            correlationId
        );

        metricsService.recordComplianceBlock(event.getCustomerId(), event.getRegulation());
    }

    private void handleSecurityIncidentBlock(CustomerBlockRequestEvent event, String correlationId) {
        log.error("Processing security incident block: customerId={}, incidentType={}",
            event.getCustomerId(), event.getSecurityIncidentType());

        blockingService.executeSecurityIncidentBlock(
            event.getCustomerId(), event.getSecurityIncidentType(), event.getBlockReason());

        securityService.revokeAllSessions(event.getCustomerId());
        securityService.requirePasswordReset(event.getCustomerId());

        notificationService.sendCustomerNotification(
            event.getCustomerId(),
            "Account Security Alert",
            "Your account has been secured due to a security incident. Please reset your password.",
            correlationId
        );

        metricsService.recordSecurityIncidentBlock(event.getCustomerId());
    }

    private void handleUnblockRequest(CustomerBlockRequestEvent event, String correlationId) {
        log.info("Processing unblock request: customerId={}, requestReason={}",
            event.getCustomerId(), event.getUnblockReason());

        blockingService.processUnblockRequest(
            event.getCustomerId(), event.getUnblockReason(), event.getRequestedBy());

        notificationService.sendCustomerNotification(
            event.getCustomerId(),
            "Account Unblock Request Received",
            "We have received your account unblock request and will review it within 2 business days.",
            correlationId
        );

        metricsService.recordUnblockRequest(event.getCustomerId());
    }

    private void handleBlockAppeal(CustomerBlockRequestEvent event, String correlationId) {
        log.info("Processing block appeal: customerId={}, appealReason={}",
            event.getCustomerId(), event.getAppealReason());

        blockingService.processBlockAppeal(
            event.getCustomerId(), event.getAppealReason(), event.getSupportingDocuments());

        notificationService.sendCustomerNotification(
            event.getCustomerId(),
            "Block Appeal Received",
            "We have received your appeal and will review it within 5 business days.",
            correlationId
        );

        metricsService.recordBlockAppeal(event.getCustomerId());

        // Send to legal review if needed
        if ("LEGAL_DISPUTE".equals(event.getAppealReason())) {
            kafkaTemplate.send("legal-review-events", Map.of(
                "eventType", "CUSTOMER_BLOCK_APPEAL",
                "customerId", event.getCustomerId(),
                "appealReason", event.getAppealReason(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }
    }
}