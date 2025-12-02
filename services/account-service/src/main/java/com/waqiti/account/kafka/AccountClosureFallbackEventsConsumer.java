package com.waqiti.account.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.account.service.AccountClosureService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountClosureFallbackEventsConsumer extends BaseDlqConsumer {

    private final AccountClosureService accountClosureService;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private final Counter processedCounter;
    private final Counter errorCounter;
    private final Timer processingTimer;

    public AccountClosureFallbackEventsConsumer(AccountClosureService accountClosureService, MeterRegistry meterRegistry) {
        super("account-closure-fallback-events");
        this.accountClosureService = accountClosureService;
        this.meterRegistry = meterRegistry;
        this.processedCounter = Counter.builder("account_closure_fallback_events_processed_total")
                .description("Total account closure fallback events processed")
                .register(meterRegistry);
        this.errorCounter = Counter.builder("account_closure_fallback_events_errors_total")
                .description("Total account closure fallback events errors")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("account_closure_fallback_events_duration")
                .description("Account closure fallback events processing duration")
                .register(meterRegistry);
    }

    @KafkaListener(
        topics = "account-closure-fallback-events",
        groupId = "account-service-account-closure-fallback-events-group",
        containerFactory = "kafkaListenerContainerFactory",
        properties = {
            "spring.kafka.consumer.isolation-level=read_committed",
            "spring.kafka.consumer.auto-offset-reset=earliest",
            "spring.kafka.consumer.max-poll-interval-ms=600000",
            "spring.kafka.consumer.session-timeout-ms=30000",
            "spring.kafka.consumer.heartbeat-interval-ms=10000"
        }
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000),
        autoCreateTopics = "true",
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class},
        traversingCauses = "true",
        retryTopicSuffix = "-retry",
        dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "account-closure-fallback", fallbackMethod = "handleAccountClosureFallbackCircuitBreaker")
    public void handleAccountClosureFallbackEvents(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long timestamp,
            @Header(value = "X-Original-Topic", required = false) String originalTopic,
            @Header(value = "X-Failure-Reason", required = false) String failureReason,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String eventId = generateEventId(record, topic, partition, offset);
        String correlationId = generateCorrelationId();

        try {
            if (isAlreadyProcessed(eventId)) {
                log.debug("Account closure fallback event already processed: {}", eventId);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing account closure fallback event: topic={}, partition={}, offset={}, key={}, " +
                    "correlationId={}, originalTopic={}, failureReason={}",
                     topic, partition, offset, record.key(), correlationId, originalTopic, failureReason);

            String closureData = record.value();
            validateClosureData(closureData, eventId);

            // Process account closure fallback with enhanced recovery strategy
            AccountClosureRecoveryResult result = accountClosureService.processAccountClosureFallback(
                closureData,
                record.key(),
                correlationId,
                originalTopic,
                failureReason,
                Instant.ofEpochMilli(timestamp != null ? timestamp : System.currentTimeMillis())
            );

            // Handle recovery result with comprehensive checks
            if (result.isSuccessful()) {
                handleSuccessfulRecovery(result, correlationId);
            } else if (result.isPartialSuccess()) {
                handlePartialRecovery(result, correlationId);
            } else {
                handleFailedRecovery(result, eventId, correlationId);
            }

            markAsProcessed(eventId);
            processedCounter.increment();
            acknowledgment.acknowledge();

            log.info("Successfully processed account closure fallback event: eventId={}, accountId={}, " +
                    "correlationId={}, recoveryStatus={}",
                    eventId, result.getAccountId(), correlationId, result.getRecoveryStatus());

        } catch (ValidationException e) {
            errorCounter.increment();
            log.error("Validation error in account closure fallback: eventId={}, correlationId={}, error={}",
                     eventId, correlationId, e.getMessage());
            handleValidationFailure(record, e, correlationId);
            acknowledgment.acknowledge();
        } catch (ComplianceException e) {
            errorCounter.increment();
            log.error("Compliance error in account closure fallback: eventId={}, correlationId={}, error={}",
                     eventId, correlationId, e.getMessage());
            handleComplianceFailure(record, e, correlationId);
            throw e; // Compliance issues must be retried
        } catch (RecoverableException e) {
            errorCounter.increment();
            log.warn("Recoverable error in account closure fallback: eventId={}, correlationId={}, error={}",
                    eventId, correlationId, e.getMessage());
            throw e;
        } catch (Exception e) {
            errorCounter.increment();
            log.error("Critical error in account closure fallback: eventId={}, correlationId={}",
                     eventId, correlationId, e);
            handleCriticalFailure(record, e, correlationId);
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    @DltHandler
    public void handleDlt(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
            @Header(KafkaHeaders.ORIGINAL_OFFSET) long originalOffset,
            @Header(KafkaHeaders.ORIGINAL_PARTITION) int originalPartition,
            @Header(value = "X-Retry-Count", required = false) Integer retryCount) {

        String correlationId = generateCorrelationId();
        log.error("Account closure fallback event sent to DLT: topic={}, originalPartition={}, " +
                 "originalOffset={}, correlationId={}, retryCount={}, error={}",
                 topic, originalPartition, originalOffset, correlationId, retryCount, exceptionMessage);

        // Execute DLT recovery protocol
        executeDltRecoveryProtocol(record, topic, exceptionMessage, correlationId);

        // Store for manual intervention
        storeInPermanentFailureStorage(record, topic, exceptionMessage, correlationId, retryCount);

        // Send critical compliance alert for account closure failures
        sendComplianceAlert(record, topic, exceptionMessage, correlationId);

        // Update DLT metrics
        Counter.builder("account_closure_fallback_dlt_events_total")
                .description("Total account closure fallback events sent to DLT")
                .tag("topic", topic)
                .tag("severity", "critical")
                .register(meterRegistry)
                .increment();
    }

    public void handleAccountClosureFallbackCircuitBreaker(
            ConsumerRecord<String, String> record,
            String topic, int partition, long offset, Long timestamp,
            String originalTopic, String failureReason,
            Acknowledgment acknowledgment, Exception ex) {

        String correlationId = generateCorrelationId();
        log.error("Circuit breaker activated for account closure fallback: correlationId={}, error={}",
                 correlationId, ex.getMessage());

        // Store in circuit breaker recovery queue with priority
        storeInCircuitBreakerQueue(record, correlationId, Priority.HIGH);

        // Send operational alert
        sendOperationalAlert(correlationId, ex);

        // Acknowledge to prevent blocking
        acknowledgment.acknowledge();

        // Update circuit breaker metrics
        Counter.builder("account_closure_fallback_circuit_breaker_activations_total")
                .tag("severity", "high")
                .register(meterRegistry)
                .increment();
    }

    private void validateClosureData(String closureData, String eventId) {
        if (closureData == null || closureData.trim().isEmpty()) {
            throw new ValidationException("Closure data is null or empty for eventId: " + eventId);
        }

        // Validate required fields for account closure
        if (!closureData.contains("accountId")) {
            throw new ValidationException("Closure data missing accountId for eventId: " + eventId);
        }

        if (!closureData.contains("closureReason")) {
            throw new ValidationException("Closure data missing closureReason for eventId: " + eventId);
        }

        if (!closureData.contains("finalBalance")) {
            throw new ValidationException("Closure data missing finalBalance for eventId: " + eventId);
        }

        // Validate compliance requirements
        if (!closureData.contains("regulatoryApproval")) {
            log.warn("Closure data missing regulatoryApproval for eventId: {}", eventId);
        }
    }

    private void handleSuccessfulRecovery(AccountClosureRecoveryResult result, String correlationId) {
        log.info("Account closure successfully recovered: accountId={}, method={}, correlationId={}",
                result.getAccountId(), result.getRecoveryMethod(), correlationId);

        // Send success notification
        notificationService.sendClosureRecoveryNotification(
            result.getAccountId(),
            result.getCustomerId(),
            RecoveryStatus.SUCCESSFUL,
            correlationId
        );

        // Update audit trail
        auditService.recordClosureRecovery(
            result.getAccountId(),
            result.getRecoveryMethod(),
            correlationId,
            AuditStatus.SUCCESS
        );

        // Clear any pending alerts
        alertService.clearPendingAlerts(result.getAccountId(), AlertType.ACCOUNT_CLOSURE_FAILURE);
    }

    private void handlePartialRecovery(AccountClosureRecoveryResult result, String correlationId) {
        log.warn("Account closure partially recovered: accountId={}, pendingItems={}, correlationId={}",
                result.getAccountId(), result.getPendingItems(), correlationId);

        // Queue pending items for manual review
        result.getPendingItems().forEach(item ->
            manualReviewQueue.add(
                ManualReviewItem.builder()
                    .accountId(result.getAccountId())
                    .itemType(item.getType())
                    .description(item.getDescription())
                    .correlationId(correlationId)
                    .priority(Priority.MEDIUM)
                    .build()
            )
        );

        // Send partial recovery notification
        notificationService.sendPartialRecoveryNotification(
            result.getAccountId(),
            result.getCustomerId(),
            result.getPendingItems(),
            correlationId
        );
    }

    private void handleFailedRecovery(AccountClosureRecoveryResult result, String eventId, String correlationId) {
        log.error("Account closure recovery failed: accountId={}, reason={}, correlationId={}",
                result.getAccountId(), result.getFailureReason(), correlationId);

        // Escalate to compliance team
        complianceService.escalateClosureFailure(
            result.getAccountId(),
            result.getFailureReason(),
            eventId,
            correlationId,
            CompliancePriority.CRITICAL
        );

        // Create incident ticket
        incidentService.createIncident(
            IncidentType.ACCOUNT_CLOSURE_FAILURE,
            result.getAccountId(),
            result.getFailureReason(),
            correlationId,
            Severity.HIGH
        );
    }

    private void handleComplianceFailure(ConsumerRecord<String, String> record, ComplianceException e, String correlationId) {
        // Store compliance violation for regulatory reporting
        complianceViolationRepository.save(
            ComplianceViolation.builder()
                .accountId(extractAccountId(record.value()))
                .violationType(ViolationType.ACCOUNT_CLOSURE)
                .description(e.getMessage())
                .correlationId(correlationId)
                .severity(Severity.CRITICAL)
                .timestamp(Instant.now())
                .requiresReporting(true)
                .build()
        );

        // Send immediate compliance alert
        complianceAlertService.sendCriticalAlert(
            ComplianceAlertType.ACCOUNT_CLOSURE_VIOLATION,
            e.getMessage(),
            correlationId
        );
    }

    private void executeDltRecoveryProtocol(ConsumerRecord<String, String> record, String topic,
                                           String exceptionMessage, String correlationId) {
        try {
            // Attempt final recovery using DLT protocol
            DltRecoveryResult recoveryResult = dltRecoveryService.attemptFinalRecovery(
                record.key(),
                record.value(),
                topic,
                exceptionMessage,
                correlationId
            );

            if (recoveryResult.isPartiallyRecovered()) {
                log.info("Partial recovery achieved in DLT protocol: correlationId={}, recoveredItems={}",
                        correlationId, recoveryResult.getRecoveredItems());
            }
        } catch (Exception e) {
            log.error("DLT recovery protocol failed: correlationId={}", correlationId, e);
        }
    }

    private void sendComplianceAlert(ConsumerRecord<String, String> record, String topic,
                                    String exceptionMessage, String correlationId) {
        complianceAlertService.sendAccountClosureFailureAlert(
            AlertLevel.CRITICAL,
            "Account closure permanently failed - regulatory compliance at risk",
            Map.of(
                "topic", topic,
                "accountId", extractAccountId(record.value()),
                "error", exceptionMessage,
                "correlationId", correlationId,
                "regulatoryImpact", "HIGH",
                "requiredAction", "Immediate manual intervention required"
            )
        );
    }

    private void storeInCircuitBreakerQueue(ConsumerRecord<String, String> record, String correlationId, Priority priority) {
        circuitBreakerRecoveryQueue.add(
            CircuitBreakerRecoveryItem.builder()
                .topic("account-closure-fallback-events")
                .key(record.key())
                .value(record.value())
                .correlationId(correlationId)
                .priority(priority)
                .retryCount(0)
                .maxRetries(5)
                .nextRetryTime(Instant.now().plus(Duration.ofMinutes(5)))
                .build()
        );
    }

    private void sendOperationalAlert(String correlationId, Exception ex) {
        operationalAlertService.sendAlert(
            AlertType.CIRCUIT_BREAKER_ACTIVATED,
            "Account closure fallback circuit breaker activated",
            Map.of(
                "service", "account-closure-fallback",
                "correlationId", correlationId,
                "error", ex.getMessage(),
                "impact", "Account closures may be delayed"
            )
        );
    }

    private boolean isAlreadyProcessed(String eventId) {
        Long processTime = processedEvents.get(eventId);
        if (processTime != null) {
            return System.currentTimeMillis() - processTime < Duration.ofHours(24).toMillis();
        }
        return false;
    }

    private void markAsProcessed(String eventId) {
        processedEvents.put(eventId, System.currentTimeMillis());
        if (processedEvents.size() > 10000) {
            cleanupOldProcessedEvents();
        }
    }

    private void cleanupOldProcessedEvents() {
        long cutoffTime = System.currentTimeMillis() - Duration.ofHours(24).toMillis();
        processedEvents.entrySet().removeIf(entry -> entry.getValue() < cutoffTime);
    }

    private String extractAccountId(String value) {
        // Extract accountId from JSON value
        try {
            return objectMapper.readTree(value).get("accountId").asText();
        } catch (Exception e) {
            return "unknown";
        }
    }
}