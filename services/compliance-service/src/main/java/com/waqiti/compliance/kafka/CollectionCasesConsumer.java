package com.waqiti.compliance.kafka;

import com.waqiti.common.events.CollectionCasesEvent;
import com.waqiti.compliance.domain.CollectionCase;
import com.waqiti.compliance.repository.CollectionCaseRepository;
import com.waqiti.compliance.service.CollectionService;
import com.waqiti.compliance.service.ComplianceWorkflowService;
import com.waqiti.compliance.metrics.ComplianceMetricsService;
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
public class CollectionCasesConsumer {

    private final CollectionCaseRepository collectionCaseRepository;
    private final CollectionService collectionService;
    private final ComplianceWorkflowService workflowService;
    private final ComplianceMetricsService metricsService;
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
        successCounter = Counter.builder("collection_cases_processed_total")
            .description("Total number of successfully processed collection cases events")
            .register(meterRegistry);
        errorCounter = Counter.builder("collection_cases_errors_total")
            .description("Total number of collection cases processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("collection_cases_processing_duration")
            .description("Time taken to process collection cases events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"collection-cases"},
        groupId = "compliance-collection-cases-group",
        containerFactory = "criticalComplianceKafkaListenerContainerFactory",
        concurrency = "3"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "collection-cases", fallbackMethod = "handleCollectionCasesEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleCollectionCasesEvent(
            @Payload CollectionCasesEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("collection-%s-p%d-o%d", event.getCaseId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getCaseId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing collection case: caseId={}, accountId={}, status={}",
                event.getCaseId(), event.getAccountId(), event.getStatus());

            cleanExpiredEntries();

            switch (event.getEventType()) {
                case CASE_CREATED:
                    processCaseCreated(event, correlationId);
                    break;

                case CASE_ASSIGNED:
                    processCaseAssigned(event, correlationId);
                    break;

                case CASE_STATUS_UPDATED:
                    processCaseStatusUpdated(event, correlationId);
                    break;

                case PAYMENT_RECEIVED:
                    processPaymentReceived(event, correlationId);
                    break;

                case CASE_ESCALATED:
                    processCaseEscalated(event, correlationId);
                    break;

                case CASE_CLOSED:
                    processCaseClosed(event, correlationId);
                    break;

                case CASE_REOPENED:
                    processCaseReopened(event, correlationId);
                    break;

                default:
                    log.warn("Unknown collection cases event type: {}", event.getEventType());
                    break;
            }

            markEventAsProcessed(eventKey);

            auditService.logComplianceEvent("COLLECTION_CASES_EVENT_PROCESSED", event.getCaseId(),
                Map.of("eventType", event.getEventType(), "accountId", event.getAccountId(),
                    "status", event.getStatus(), "correlationId", correlationId,
                    "amount", event.getAmount(), "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process collection cases event: {}", e.getMessage(), e);

            kafkaTemplate.send("compliance-collection-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleCollectionCasesEventFallback(
            CollectionCasesEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("collection-fallback-%s-p%d-o%d", event.getCaseId(), partition, offset);

        log.error("Circuit breaker fallback triggered for collection cases: caseId={}, error={}",
            event.getCaseId(), ex.getMessage());

        kafkaTemplate.send("collection-cases-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        try {
            notificationService.sendOperationalAlert(
                "Collection Cases Circuit Breaker Triggered",
                String.format("Collection case %s failed: %s", event.getCaseId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltCollectionCasesEvent(
            @Payload CollectionCasesEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-collection-%s-%d", event.getCaseId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Collection cases permanently failed: caseId={}, topic={}, error={}",
            event.getCaseId(), topic, exceptionMessage);

        auditService.logComplianceEvent("COLLECTION_CASES_DLT_EVENT", event.getCaseId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        try {
            notificationService.sendCriticalAlert(
                "Collection Cases Dead Letter Event",
                String.format("Collection case %s sent to DLT: %s", event.getCaseId(), exceptionMessage),
                Map.of("caseId", event.getCaseId(), "topic", topic, "correlationId", correlationId)
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

    private void processCaseCreated(CollectionCasesEvent event, String correlationId) {
        CollectionCase collectionCase = CollectionCase.builder()
            .caseId(event.getCaseId())
            .accountId(event.getAccountId())
            .customerId(event.getCustomerId())
            .amount(event.getAmount())
            .status("OPEN")
            .caseType(event.getCaseType())
            .priority(determinePriority(event.getAmount()))
            .createdAt(LocalDateTime.now())
            .dueDate(calculateDueDate(event.getAmount()))
            .correlationId(correlationId)
            .build();
        collectionCaseRepository.save(collectionCase);

        collectionService.initializeCase(event.getCaseId(), event.getAccountId());

        kafkaTemplate.send("compliance-dashboard", Map.of(
            "eventType", "COLLECTION_CASE_CREATED",
            "caseId", event.getCaseId(),
            "accountId", event.getAccountId(),
            "amount", event.getAmount(),
            "priority", collectionCase.getPriority(),
            "dueDate", collectionCase.getDueDate(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        if (event.getAmount().compareTo(java.math.BigDecimal.valueOf(50000)) > 0) {
            kafkaTemplate.send("compliance-alerts", Map.of(
                "alertType", "HIGH_VALUE_COLLECTION_CASE",
                "caseId", event.getCaseId(),
                "amount", event.getAmount(),
                "priority", "HIGH",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordCollectionCaseCreated(event.getCaseType());

        log.info("Collection case created: caseId={}, amount={}, priority={}",
            event.getCaseId(), event.getAmount(), collectionCase.getPriority());
    }

    private void processCaseAssigned(CollectionCasesEvent event, String correlationId) {
        CollectionCase collectionCase = collectionCaseRepository.findByCaseId(event.getCaseId())
            .orElseThrow(() -> new RuntimeException("Collection case not found"));

        collectionCase.setAssignedTo(event.getAssignedTo());
        collectionCase.setAssignedAt(LocalDateTime.now());
        collectionCaseRepository.save(collectionCase);

        collectionService.assignCase(event.getCaseId(), event.getAssignedTo());

        notificationService.sendNotification(event.getAssignedTo(), "Collection Case Assigned",
            String.format("You have been assigned collection case %s for account %s (Amount: %s)",
                event.getCaseId(), event.getAccountId(), event.getAmount()),
            correlationId);

        metricsService.recordCollectionCaseAssigned();

        log.info("Collection case assigned: caseId={}, assignedTo={}",
            event.getCaseId(), event.getAssignedTo());
    }

    private void processCaseStatusUpdated(CollectionCasesEvent event, String correlationId) {
        CollectionCase collectionCase = collectionCaseRepository.findByCaseId(event.getCaseId())
            .orElseThrow(() -> new RuntimeException("Collection case not found"));

        String oldStatus = collectionCase.getStatus();
        collectionCase.setStatus(event.getStatus());
        collectionCase.setStatusUpdatedAt(LocalDateTime.now());
        collectionCase.setStatusNotes(event.getStatusNotes());
        collectionCaseRepository.save(collectionCase);

        collectionService.updateCaseStatus(event.getCaseId(), event.getStatus(), event.getStatusNotes());

        kafkaTemplate.send("compliance-dashboard", Map.of(
            "eventType", "COLLECTION_CASE_STATUS_UPDATED",
            "caseId", event.getCaseId(),
            "oldStatus", oldStatus,
            "newStatus", event.getStatus(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordCollectionCaseStatusChanged(oldStatus, event.getStatus());

        log.info("Collection case status updated: caseId={}, from={}, to={}",
            event.getCaseId(), oldStatus, event.getStatus());
    }

    private void processPaymentReceived(CollectionCasesEvent event, String correlationId) {
        CollectionCase collectionCase = collectionCaseRepository.findByCaseId(event.getCaseId())
            .orElseThrow(() -> new RuntimeException("Collection case not found"));

        collectionCase.setAmountCollected(event.getAmountReceived());
        collectionCase.setLastPaymentDate(LocalDateTime.now());

        java.math.BigDecimal remainingAmount = collectionCase.getAmount().subtract(event.getAmountReceived());
        collectionCase.setRemainingAmount(remainingAmount);

        if (remainingAmount.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            collectionCase.setStatus("RESOLVED");
            collectionCase.setResolvedAt(LocalDateTime.now());
        }

        collectionCaseRepository.save(collectionCase);

        collectionService.recordPayment(event.getCaseId(), event.getAmountReceived());

        kafkaTemplate.send("compliance-dashboard", Map.of(
            "eventType", "COLLECTION_PAYMENT_RECEIVED",
            "caseId", event.getCaseId(),
            "amountReceived", event.getAmountReceived(),
            "remainingAmount", remainingAmount,
            "isResolved", remainingAmount.compareTo(java.math.BigDecimal.ZERO) <= 0,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        if (remainingAmount.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            kafkaTemplate.send("collection-cases", Map.of(
                "eventType", "CASE_CLOSED",
                "caseId", event.getCaseId(),
                "closureReason", "PAYMENT_RECEIVED",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordCollectionPaymentReceived(event.getAmountReceived());

        log.info("Collection payment received: caseId={}, amount={}, remaining={}",
            event.getCaseId(), event.getAmountReceived(), remainingAmount);
    }

    private void processCaseEscalated(CollectionCasesEvent event, String correlationId) {
        CollectionCase collectionCase = collectionCaseRepository.findByCaseId(event.getCaseId())
            .orElseThrow(() -> new RuntimeException("Collection case not found"));

        collectionCase.setStatus("ESCALATED");
        collectionCase.setEscalatedAt(LocalDateTime.now());
        collectionCase.setEscalationReason(event.getEscalationReason());
        collectionCase.setPriority("HIGH");
        collectionCaseRepository.save(collectionCase);

        collectionService.escalateCase(event.getCaseId(), event.getEscalationReason());

        kafkaTemplate.send("compliance-alerts", Map.of(
            "alertType", "COLLECTION_CASE_ESCALATED",
            "caseId", event.getCaseId(),
            "accountId", event.getAccountId(),
            "amount", event.getAmount(),
            "escalationReason", event.getEscalationReason(),
            "priority", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Collection Case Escalated",
            String.format("Collection case %s escalated: %s (Amount: %s)",
                event.getCaseId(), event.getEscalationReason(), event.getAmount()),
            "HIGH");

        metricsService.recordCollectionCaseEscalated();

        log.warn("Collection case escalated: caseId={}, reason={}",
            event.getCaseId(), event.getEscalationReason());
    }

    private void processCaseClosed(CollectionCasesEvent event, String correlationId) {
        CollectionCase collectionCase = collectionCaseRepository.findByCaseId(event.getCaseId())
            .orElseThrow(() -> new RuntimeException("Collection case not found"));

        collectionCase.setStatus("CLOSED");
        collectionCase.setClosedAt(LocalDateTime.now());
        collectionCase.setClosureReason(event.getClosureReason());
        collectionCaseRepository.save(collectionCase);

        collectionService.closeCase(event.getCaseId(), event.getClosureReason());

        kafkaTemplate.send("compliance-dashboard", Map.of(
            "eventType", "COLLECTION_CASE_CLOSED",
            "caseId", event.getCaseId(),
            "closureReason", event.getClosureReason(),
            "amountCollected", collectionCase.getAmountCollected(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordCollectionCaseClosed(event.getClosureReason());

        log.info("Collection case closed: caseId={}, reason={}",
            event.getCaseId(), event.getClosureReason());
    }

    private void processCaseReopened(CollectionCasesEvent event, String correlationId) {
        CollectionCase collectionCase = collectionCaseRepository.findByCaseId(event.getCaseId())
            .orElseThrow(() -> new RuntimeException("Collection case not found"));

        collectionCase.setStatus("REOPENED");
        collectionCase.setReopenedAt(LocalDateTime.now());
        collectionCase.setReopenReason(event.getReopenReason());
        collectionCaseRepository.save(collectionCase);

        collectionService.reopenCase(event.getCaseId(), event.getReopenReason());

        kafkaTemplate.send("compliance-alerts", Map.of(
            "alertType", "COLLECTION_CASE_REOPENED",
            "caseId", event.getCaseId(),
            "reopenReason", event.getReopenReason(),
            "priority", "MEDIUM",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordCollectionCaseReopened();

        log.info("Collection case reopened: caseId={}, reason={}",
            event.getCaseId(), event.getReopenReason());
    }

    private String determinePriority(java.math.BigDecimal amount) {
        if (amount.compareTo(java.math.BigDecimal.valueOf(100000)) > 0) {
            return "CRITICAL";
        } else if (amount.compareTo(java.math.BigDecimal.valueOf(50000)) > 0) {
            return "HIGH";
        } else if (amount.compareTo(java.math.BigDecimal.valueOf(10000)) > 0) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    private LocalDateTime calculateDueDate(java.math.BigDecimal amount) {
        if (amount.compareTo(java.math.BigDecimal.valueOf(100000)) > 0) {
            return LocalDateTime.now().plusDays(7);
        } else if (amount.compareTo(java.math.BigDecimal.valueOf(50000)) > 0) {
            return LocalDateTime.now().plusDays(14);
        } else if (amount.compareTo(java.math.BigDecimal.valueOf(10000)) > 0) {
            return LocalDateTime.now().plusDays(30);
        } else {
            return LocalDateTime.now().plusDays(60);
        }
    }
}