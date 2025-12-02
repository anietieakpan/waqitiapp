package com.waqiti.dispute.kafka;

import com.waqiti.payment.dto.PaymentChargebackEvent;
import com.waqiti.dispute.entity.Dispute;
import com.waqiti.dispute.service.TransactionDisputeService;
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
import jakarta.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class ChargebackAlertValidationErrorsConsumer {

    private final TransactionDisputeService disputeService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("chargeback_alert_validation_errors_processed_total")
            .description("Total number of successfully processed chargeback validation error events")
            .register(meterRegistry);
        errorCounter = Counter.builder("chargeback_alert_validation_errors_errors_total")
            .description("Total number of chargeback validation error processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("chargeback_alert_validation_errors_processing_duration")
            .description("Time taken to process chargeback validation error events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"chargeback-alert-validation-errors"},
        groupId = "dispute-chargeback-validation-errors-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "chargeback-validation-errors", fallbackMethod = "handleChargebackValidationErrorEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleChargebackValidationErrorEvent(
            @Payload Map<String, Object> validationErrorEvent,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String chargebackId = (String) validationErrorEvent.get("chargebackId");
        String correlationId = String.format("validation-error-%s-p%d-o%d", chargebackId, partition, offset);
        String eventKey = String.format("%s-%s-%s", chargebackId,
            validationErrorEvent.get("errorType"), validationErrorEvent.get("timestamp"));

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing chargeback validation error: chargebackId={}, errorType={}, field={}",
                chargebackId, validationErrorEvent.get("errorType"), validationErrorEvent.get("invalidField"));

            // Clean expired entries periodically
            cleanExpiredEntries();

            // Process validation error
            processValidationError(validationErrorEvent, correlationId);

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logDisputeEvent("CHARGEBACK_VALIDATION_ERROR_PROCESSED", chargebackId,
                Map.of("chargebackId", chargebackId, "errorType", validationErrorEvent.get("errorType"),
                    "invalidField", validationErrorEvent.get("invalidField"),
                    "errorMessage", validationErrorEvent.get("errorMessage"),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process chargeback validation error: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("chargeback-validation-error-fallback-events", Map.of(
                "originalEvent", validationErrorEvent, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleChargebackValidationErrorEventFallback(
            Map<String, Object> validationErrorEvent,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String chargebackId = (String) validationErrorEvent.get("chargebackId");
        String correlationId = String.format("validation-error-fallback-%s-p%d-o%d", chargebackId, partition, offset);

        log.error("Circuit breaker fallback triggered for chargeback validation error: chargebackId={}, error={}",
            chargebackId, ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("chargeback-alert-validation-errors-dlq", Map.of(
            "originalEvent", validationErrorEvent,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Chargeback Validation Error Circuit Breaker Triggered",
                String.format("Chargeback %s validation error processing failed: %s", chargebackId, ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltChargebackValidationErrorEvent(
            @Payload Map<String, Object> validationErrorEvent,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String chargebackId = (String) validationErrorEvent.get("chargebackId");
        String correlationId = String.format("dlt-validation-error-%s-%d", chargebackId, System.currentTimeMillis());

        log.error("Dead letter topic handler - Chargeback validation error permanently failed: chargebackId={}, topic={}, error={}",
            chargebackId, topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logDisputeEvent("CHARGEBACK_VALIDATION_ERROR_DLT_EVENT", chargebackId,
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "chargebackId", chargebackId, "validationError", validationErrorEvent,
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Chargeback Validation Error Dead Letter Event",
                String.format("Chargeback %s validation error sent to DLT: %s", chargebackId, exceptionMessage),
                Map.of("chargebackId", chargebackId, "topic", topic, "correlationId", correlationId)
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

        // Check if the entry has expired
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
        if (processedEvents.size() > 1000) { // Only clean when we have many entries
            long currentTime = System.currentTimeMillis();
            processedEvents.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > TTL_24_HOURS);
        }
    }

    private void processValidationError(Map<String, Object> validationErrorEvent, String correlationId) {
        String chargebackId = (String) validationErrorEvent.get("chargebackId");
        String errorType = (String) validationErrorEvent.get("errorType");
        String invalidField = (String) validationErrorEvent.get("invalidField");
        String errorMessage = (String) validationErrorEvent.get("errorMessage");
        String severity = (String) validationErrorEvent.getOrDefault("severity", "MEDIUM");

        log.warn("Processing chargeback validation error: chargebackId={}, type={}, field={}, severity={}",
            chargebackId, errorType, invalidField, severity);

        // Process based on error type
        switch (errorType) {
            case "MISSING_REQUIRED_FIELD":
                processMissingFieldError(validationErrorEvent, correlationId);
                break;

            case "INVALID_FORMAT":
                processFormatError(validationErrorEvent, correlationId);
                break;

            case "INVALID_VALUE":
                processValueError(validationErrorEvent, correlationId);
                break;

            case "BUSINESS_RULE_VIOLATION":
                processBusinessRuleError(validationErrorEvent, correlationId);
                break;

            case "DATA_CONSISTENCY_ERROR":
                processConsistencyError(validationErrorEvent, correlationId);
                break;

            default:
                processGenericValidationError(validationErrorEvent, correlationId);
                break;
        }

        // Handle severity-based actions
        handleSeverityBasedActions(validationErrorEvent, severity, correlationId);

        // Flag chargeback for manual review if critical errors
        if ("CRITICAL".equals(severity) || "HIGH".equals(severity)) {
            flagForManualReview(chargebackId, errorType, correlationId);
        }

        // Update validation error metrics
        meterRegistry.counter("chargeback_validation_errors_total",
            "error_type", errorType,
            "field", invalidField,
            "severity", severity).increment();

        log.info("Chargeback validation error processed: chargebackId={}, errorType={}",
            chargebackId, errorType);
    }

    private void processMissingFieldError(Map<String, Object> errorEvent, String correlationId) {
        String chargebackId = (String) errorEvent.get("chargebackId");
        String missingField = (String) errorEvent.get("invalidField");

        log.warn("Missing required field for chargeback {}: {}", chargebackId, missingField);

        // Try to retrieve missing data from other sources
        disputeService.enrichChargebackData(chargebackId, missingField, correlationId);

        // If critical fields are missing, escalate
        if (isCriticalField(missingField)) {
            notificationService.sendHighPriorityAlert(
                "Critical Chargeback Data Missing",
                String.format("Chargeback %s missing critical field: %s", chargebackId, missingField),
                Map.of("chargebackId", chargebackId, "missingField", missingField)
            );
        }
    }

    private void processFormatError(Map<String, Object> errorEvent, String correlationId) {
        String chargebackId = (String) errorEvent.get("chargebackId");
        String invalidField = (String) errorEvent.get("invalidField");
        String expectedFormat = (String) errorEvent.get("expectedFormat");

        log.warn("Invalid format for chargeback {}: field={}, expected={}",
            chargebackId, invalidField, expectedFormat);

        // Attempt data transformation
        disputeService.transformChargebackData(chargebackId, invalidField, expectedFormat, correlationId);

        // Log for pattern analysis
        kafkaTemplate.send("data-quality-events", Map.of(
            "type", "FORMAT_ERROR",
            "entity", "CHARGEBACK",
            "entityId", chargebackId,
            "field", invalidField,
            "expectedFormat", expectedFormat,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void processValueError(Map<String, Object> errorEvent, String correlationId) {
        String chargebackId = (String) errorEvent.get("chargebackId");
        String invalidField = (String) errorEvent.get("invalidField");
        Object invalidValue = errorEvent.get("invalidValue");

        log.warn("Invalid value for chargeback {}: field={}, value={}",
            chargebackId, invalidField, invalidValue);

        // Validate against business rules
        disputeService.validateChargebackBusinessRules(chargebackId, invalidField, invalidValue, correlationId);

        // Check if value can be corrected
        if (isCorrectableValue(invalidField, invalidValue)) {
            disputeService.correctChargebackValue(chargebackId, invalidField, invalidValue, correlationId);
        }
    }

    private void processBusinessRuleError(Map<String, Object> errorEvent, String correlationId) {
        String chargebackId = (String) errorEvent.get("chargebackId");
        String ruleName = (String) errorEvent.get("ruleName");
        String ruleDescription = (String) errorEvent.get("ruleDescription");

        log.warn("Business rule violation for chargeback {}: rule={}, description={}",
            chargebackId, ruleName, ruleDescription);

        // Send to business rules review queue
        kafkaTemplate.send("business-rules-violations", Map.of(
            "entityType", "CHARGEBACK",
            "entityId", chargebackId,
            "ruleName", ruleName,
            "ruleDescription", ruleDescription,
            "requiresReview", true,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Notify compliance team for rule violations
        notificationService.sendOperationalAlert(
            "Chargeback Business Rule Violation",
            String.format("Chargeback %s violates rule: %s", chargebackId, ruleName),
            "MEDIUM"
        );
    }

    private void processConsistencyError(Map<String, Object> errorEvent, String correlationId) {
        String chargebackId = (String) errorEvent.get("chargebackId");
        String inconsistentFields = (String) errorEvent.get("inconsistentFields");

        log.warn("Data consistency error for chargeback {}: fields={}", chargebackId, inconsistentFields);

        // Trigger data reconciliation
        disputeService.reconcileChargebackData(chargebackId, inconsistentFields, correlationId);

        // Track consistency issues for monitoring
        meterRegistry.counter("chargeback_data_consistency_errors_total",
            "fields", inconsistentFields).increment();
    }

    private void processGenericValidationError(Map<String, Object> errorEvent, String correlationId) {
        String chargebackId = (String) errorEvent.get("chargebackId");
        String errorMessage = (String) errorEvent.get("errorMessage");

        log.warn("Generic validation error for chargeback {}: {}", chargebackId, errorMessage);

        // Send to generic error handling queue
        kafkaTemplate.send("generic-validation-errors", Map.of(
            "entityType", "CHARGEBACK",
            "entityId", chargebackId,
            "errorMessage", errorMessage,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleSeverityBasedActions(Map<String, Object> errorEvent, String severity, String correlationId) {
        String chargebackId = (String) errorEvent.get("chargebackId");

        switch (severity) {
            case "CRITICAL":
                // Block processing until fixed
                disputeService.blockChargebackProcessing(chargebackId, "VALIDATION_ERROR", correlationId);
                notificationService.sendCriticalAlert(
                    "Critical Chargeback Validation Error",
                    String.format("Chargeback %s has critical validation errors", chargebackId),
                    Map.of("chargebackId", chargebackId, "severity", severity)
                );
                break;

            case "HIGH":
                // Flag for priority review
                disputeService.flagChargebackForReview(chargebackId, "HIGH_PRIORITY_VALIDATION", correlationId);
                break;

            case "MEDIUM":
                // Standard processing with logging
                disputeService.logValidationIssue(chargebackId, "MEDIUM_PRIORITY_VALIDATION", correlationId);
                break;

            default:
                // Low priority - log only
                log.info("Low priority validation error for chargeback: {}", chargebackId);
                break;
        }
    }

    private void flagForManualReview(String chargebackId, String errorType, String correlationId) {
        kafkaTemplate.send("chargeback-manual-queue", Map.of(
            "chargebackId", chargebackId,
            "priority", "HIGH",
            "reason", "VALIDATION_ERROR",
            "errorType", errorType,
            "assignedTo", "DATA_VALIDATION_TEAM",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Chargeback flagged for manual review due to validation error: {}", chargebackId);
    }

    private boolean isCriticalField(String fieldName) {
        return Set.of("chargebackId", "transactionId", "chargebackAmount",
                      "currency", "reasonCode", "customerId", "merchantId")
                .contains(fieldName);
    }

    private boolean isCorrectableValue(String fieldName, Object value) {
        // Logic to determine if a value can be automatically corrected
        return value != null && !value.toString().trim().isEmpty();
    }
}