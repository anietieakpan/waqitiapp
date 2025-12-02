package com.waqiti.frauddetection.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.frauddetection.service.*;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-grade Kafka consumer for async reversal validation error events
 * Handles validation errors during asynchronous transaction reversals
 *
 * Critical for: Reversal validation, error handling, data integrity
 * SLA: Must process validation errors within 20 seconds for proper error handling
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AsyncReversalValidationErrorsConsumer {

    private final AsyncReversalService asyncReversalService;
    private final ReversalValidationService reversalValidationService;
    private final ErrorHandlingService errorHandlingService;
    private final CustomerCommunicationService customerCommunicationService;
    private final FraudNotificationService fraudNotificationService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("async_reversal_validation_errors_processed_total")
            .description("Total number of successfully processed async reversal validation error events")
            .register(meterRegistry);
        errorCounter = Counter.builder("async_reversal_validation_errors_errors_total")
            .description("Total number of async reversal validation error processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("async_reversal_validation_errors_processing_duration")
            .description("Time taken to process async reversal validation error events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"async-reversal-validation-errors", "fraud-reversal-validation-errors"},
        groupId = "fraud-async-reversal-validation-errors-group",
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
    @CircuitBreaker(name = "async-reversal-validation-errors", fallbackMethod = "handleAsyncReversalValidationErrorEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleAsyncReversalValidationErrorEvent(
            @Payload GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("async-rev-val-err-%s-p%d-o%d", event.getId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing async reversal validation error: id={}, type={}, reversalId={}",
                event.getId(), event.getEventType(), event.getData().get("reversalId"));

            // Clean expired entries periodically
            cleanExpiredEntries();

            String reversalId = (String) event.getData().get("reversalId");
            String transactionId = (String) event.getData().get("transactionId");
            String userId = (String) event.getData().get("userId");

            switch (event.getEventType()) {
                case "VALIDATION_FAILED":
                    handleValidationFailed(event, reversalId, transactionId, userId, correlationId);
                    break;

                case "BUSINESS_RULE_VIOLATION":
                    handleBusinessRuleViolation(event, reversalId, transactionId, userId, correlationId);
                    break;

                case "INSUFFICIENT_FUNDS":
                    handleInsufficientFunds(event, reversalId, transactionId, userId, correlationId);
                    break;

                case "ACCOUNT_FROZEN":
                    handleAccountFrozen(event, reversalId, transactionId, userId, correlationId);
                    break;

                case "AUTHORIZATION_FAILED":
                    handleAuthorizationFailed(event, reversalId, transactionId, userId, correlationId);
                    break;

                case "DUPLICATE_REVERSAL":
                    handleDuplicateReversal(event, reversalId, transactionId, userId, correlationId);
                    break;

                case "INVALID_TRANSACTION_STATE":
                    handleInvalidTransactionState(event, reversalId, transactionId, userId, correlationId);
                    break;

                case "REGULATORY_RESTRICTION":
                    handleRegulatoryRestriction(event, reversalId, transactionId, userId, correlationId);
                    break;

                case "DATA_INTEGRITY_ERROR":
                    handleDataIntegrityError(event, reversalId, transactionId, correlationId);
                    break;

                default:
                    log.warn("Unknown async reversal validation error type: {}", event.getEventType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logFinancialEvent("ASYNC_REVERSAL_VALIDATION_ERROR_PROCESSED", userId,
                Map.of("eventType", event.getEventType(), "reversalId", reversalId,
                    "transactionId", transactionId, "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process async reversal validation error event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("async-reversal-validation-errors-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleAsyncReversalValidationErrorEventFallback(
            GenericKafkaEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("async-rev-val-err-fallback-%s-p%d-o%d", event.getId(), partition, offset);

        log.error("Circuit breaker fallback triggered for async reversal validation error: id={}, error={}",
            event.getId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("async-reversal-validation-errors-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Async Reversal Validation Errors Consumer Circuit Breaker Triggered",
                String.format("Reversal validation error event %s failed: %s", event.getId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltAsyncReversalValidationErrorEvent(
            @Payload GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-async-rev-val-err-%s-%d", event.getId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Async reversal validation error permanently failed: id={}, topic={}, error={}",
            event.getId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logFinancialEvent("ASYNC_REVERSAL_VALIDATION_ERROR_DLT_EVENT",
            String.valueOf(event.getData().get("userId")),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Async Reversal Validation Error Event Dead Letter",
                String.format("Reversal validation error event %s sent to DLT: %s", event.getId(), exceptionMessage),
                Map.of("eventId", event.getId(), "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private void handleValidationFailed(GenericKafkaEvent event, String reversalId, String transactionId, String userId, String correlationId) {
        String validationError = (String) event.getData().get("validationError");
        String fieldName = (String) event.getData().get("fieldName");
        log.warn("Reversal validation failed: reversalId={}, error={}, field={}", reversalId, validationError, fieldName);

        // Record validation failure
        reversalValidationService.recordValidationFailure(reversalId, validationError, fieldName);

        // Determine if error is recoverable
        boolean isRecoverable = reversalValidationService.isValidationErrorRecoverable(validationError);

        if (isRecoverable) {
            // Attempt to fix validation issue
            reversalValidationService.attemptValidationFix(reversalId, validationError, fieldName);
        } else {
            // Reject reversal
            asyncReversalService.rejectReversal(reversalId, "VALIDATION_FAILED", validationError);

            // Notify customer
            customerCommunicationService.sendReversalValidationFailedNotification(userId, reversalId, validationError);
        }

        log.info("Validation failure processed: reversalId={}, recoverable={}", reversalId, isRecoverable);
    }

    private void handleBusinessRuleViolation(GenericKafkaEvent event, String reversalId, String transactionId, String userId, String correlationId) {
        String violatedRule = (String) event.getData().get("violatedRule");
        String ruleDescription = (String) event.getData().get("ruleDescription");
        log.warn("Business rule violation: reversalId={}, rule={}, description={}", reversalId, violatedRule, ruleDescription);

        // Record rule violation
        reversalValidationService.recordBusinessRuleViolation(reversalId, violatedRule, ruleDescription);

        // Check if override is possible
        boolean canOverride = reversalValidationService.canOverrideBusinessRule(violatedRule);

        if (canOverride) {
            // Route for manual approval
            asyncReversalService.routeForManualApproval(reversalId, "BUSINESS_RULE_OVERRIDE", violatedRule);
        } else {
            // Reject reversal
            asyncReversalService.rejectReversal(reversalId, "BUSINESS_RULE_VIOLATION", ruleDescription);

            // Notify customer
            customerCommunicationService.sendBusinessRuleViolationNotification(userId, reversalId, ruleDescription);
        }

        log.info("Business rule violation processed: reversalId={}, canOverride={}", reversalId, canOverride);
    }

    private void handleInsufficientFunds(GenericKafkaEvent event, String reversalId, String transactionId, String userId, String correlationId) {
        log.warn("Insufficient funds for reversal: reversalId={}, transactionId={}", reversalId, transactionId);

        // Record insufficient funds error
        reversalValidationService.recordInsufficientFundsError(reversalId, transactionId);

        // Check for partial reversal possibility
        boolean canPartialReverse = reversalValidationService.checkPartialReversalPossibility(reversalId, transactionId);

        if (canPartialReverse) {
            // Propose partial reversal
            asyncReversalService.proposePartialReversal(reversalId, transactionId);
        } else {
            // Reject reversal
            asyncReversalService.rejectReversal(reversalId, "INSUFFICIENT_FUNDS", "Account has insufficient funds for reversal");

            // Notify customer
            customerCommunicationService.sendInsufficientFundsNotification(userId, reversalId);
        }

        log.info("Insufficient funds processed: reversalId={}, canPartialReverse={}", reversalId, canPartialReverse);
    }

    private void handleAccountFrozen(GenericKafkaEvent event, String reversalId, String transactionId, String userId, String correlationId) {
        String freezeReason = (String) event.getData().get("freezeReason");
        log.warn("Account frozen during reversal: reversalId={}, userId={}, reason={}", reversalId, userId, freezeReason);

        // Record frozen account error
        reversalValidationService.recordAccountFrozenError(reversalId, userId, freezeReason);

        // Check freeze type and implications
        String freezeType = reversalValidationService.determineFreezeType(userId, freezeReason);

        // Handle based on freeze type
        if ("TEMPORARY_FRAUD_FREEZE".equals(freezeType)) {
            // Queue for post-unfreeze processing
            asyncReversalService.queueForPostUnfreezeProcessing(reversalId, userId);
        } else {
            // Reject reversal
            asyncReversalService.rejectReversal(reversalId, "ACCOUNT_FROZEN", freezeReason);
        }

        // Notify customer
        customerCommunicationService.sendAccountFrozenReversalNotification(userId, reversalId, freezeReason);

        log.info("Account frozen error processed: reversalId={}, freezeType={}", reversalId, freezeType);
    }

    private void handleAuthorizationFailed(GenericKafkaEvent event, String reversalId, String transactionId, String userId, String correlationId) {
        String authFailureReason = (String) event.getData().get("authFailureReason");
        log.warn("Authorization failed for reversal: reversalId={}, reason={}", reversalId, authFailureReason);

        // Record authorization failure
        reversalValidationService.recordAuthorizationFailure(reversalId, authFailureReason);

        // Check if re-authorization is possible
        boolean canReauthorize = reversalValidationService.canReauthorize(reversalId, authFailureReason);

        if (canReauthorize) {
            // Attempt re-authorization
            asyncReversalService.attemptReauthorization(reversalId, correlationId);
        } else {
            // Reject reversal
            asyncReversalService.rejectReversal(reversalId, "AUTHORIZATION_FAILED", authFailureReason);

            // Notify customer
            customerCommunicationService.sendAuthorizationFailedNotification(userId, reversalId);
        }

        log.info("Authorization failure processed: reversalId={}, canReauthorize={}", reversalId, canReauthorize);
    }

    private void handleDuplicateReversal(GenericKafkaEvent event, String reversalId, String transactionId, String userId, String correlationId) {
        String originalReversalId = (String) event.getData().get("originalReversalId");
        log.warn("Duplicate reversal detected: reversalId={}, originalReversalId={}", reversalId, originalReversalId);

        // Record duplicate detection
        reversalValidationService.recordDuplicateReversal(reversalId, originalReversalId);

        // Check original reversal status
        String originalStatus = reversalValidationService.getReversalStatus(originalReversalId);

        // Cancel duplicate reversal
        asyncReversalService.cancelReversal(reversalId, "DUPLICATE_REVERSAL", originalReversalId);

        // Notify customer about duplicate
        customerCommunicationService.sendDuplicateReversalNotification(userId, reversalId, originalReversalId, originalStatus);

        log.info("Duplicate reversal processed: reversalId={}, originalStatus={}", reversalId, originalStatus);
    }

    private void handleInvalidTransactionState(GenericKafkaEvent event, String reversalId, String transactionId, String userId, String correlationId) {
        String currentState = (String) event.getData().get("currentState");
        String expectedState = (String) event.getData().get("expectedState");
        log.warn("Invalid transaction state for reversal: reversalId={}, current={}, expected={}",
            reversalId, currentState, expectedState);

        // Record state validation error
        reversalValidationService.recordInvalidStateError(reversalId, transactionId, currentState, expectedState);

        // Check if state can be corrected
        boolean canCorrectState = reversalValidationService.canCorrectTransactionState(transactionId, currentState, expectedState);

        if (canCorrectState) {
            // Attempt state correction
            reversalValidationService.attemptStateCorrection(transactionId, expectedState);

            // Retry reversal
            asyncReversalService.retryReversal(reversalId, "STATE_CORRECTED");
        } else {
            // Reject reversal
            asyncReversalService.rejectReversal(reversalId, "INVALID_TRANSACTION_STATE",
                String.format("Transaction in %s state, expected %s", currentState, expectedState));
        }

        log.info("Invalid transaction state processed: reversalId={}, canCorrect={}", reversalId, canCorrectState);
    }

    private void handleRegulatoryRestriction(GenericKafkaEvent event, String reversalId, String transactionId, String userId, String correlationId) {
        String restriction = (String) event.getData().get("restriction");
        String regulation = (String) event.getData().get("regulation");
        log.warn("Regulatory restriction for reversal: reversalId={}, restriction={}, regulation={}",
            reversalId, restriction, regulation);

        // Record regulatory restriction
        reversalValidationService.recordRegulatoryRestriction(reversalId, restriction, regulation);

        // Check for compliance override possibility
        boolean canComplianceOverride = reversalValidationService.canComplianceOverride(restriction, regulation);

        if (canComplianceOverride) {
            // Route to compliance team
            asyncReversalService.routeToComplianceReview(reversalId, restriction, regulation);
        } else {
            // Reject reversal
            asyncReversalService.rejectReversal(reversalId, "REGULATORY_RESTRICTION", restriction);

            // Notify customer
            customerCommunicationService.sendRegulatoryRestrictionNotification(userId, reversalId, regulation);
        }

        log.info("Regulatory restriction processed: reversalId={}, canOverride={}", reversalId, canComplianceOverride);
    }

    private void handleDataIntegrityError(GenericKafkaEvent event, String reversalId, String transactionId, String correlationId) {
        String integrityError = (String) event.getData().get("integrityError");
        String affectedData = (String) event.getData().get("affectedData");
        log.error("Data integrity error during reversal: reversalId={}, error={}, data={}",
            reversalId, integrityError, affectedData);

        // Record data integrity error
        errorHandlingService.recordDataIntegrityError(reversalId, transactionId, integrityError, affectedData);

        // This is critical - send to manual intervention
        asyncReversalService.escalateToManualIntervention(reversalId, "DATA_INTEGRITY_ERROR", integrityError);

        // Send critical alert
        fraudNotificationService.sendDataIntegrityAlert(reversalId, transactionId, integrityError, correlationId);

        log.info("Data integrity error escalated: reversalId={}", reversalId);
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
}