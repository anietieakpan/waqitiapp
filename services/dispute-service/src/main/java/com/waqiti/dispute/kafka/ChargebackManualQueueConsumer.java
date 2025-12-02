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
public class ChargebackManualQueueConsumer {

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
        successCounter = Counter.builder("chargeback_manual_queue_processed_total")
            .description("Total number of successfully processed chargeback manual queue events")
            .register(meterRegistry);
        errorCounter = Counter.builder("chargeback_manual_queue_errors_total")
            .description("Total number of chargeback manual queue processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("chargeback_manual_queue_processing_duration")
            .description("Time taken to process chargeback manual queue events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"chargeback-manual-queue"},
        groupId = "dispute-chargeback-manual-queue-group",
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
    @CircuitBreaker(name = "chargeback-manual-queue", fallbackMethod = "handleChargebackManualQueueEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleChargebackManualQueueEvent(
            @Payload Map<String, Object> manualQueueEvent,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String chargebackId = (String) manualQueueEvent.get("chargebackId");
        String correlationId = String.format("manual-queue-%s-p%d-o%d", chargebackId, partition, offset);
        String eventKey = String.format("%s-%s-%s", chargebackId,
            manualQueueEvent.get("reason"), manualQueueEvent.get("timestamp"));

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing chargeback manual queue: chargebackId={}, reason={}, priority={}, assignedTo={}",
                chargebackId, manualQueueEvent.get("reason"), manualQueueEvent.get("priority"),
                manualQueueEvent.get("assignedTo"));

            // Clean expired entries periodically
            cleanExpiredEntries();

            // Process manual queue item
            processManualQueueItem(manualQueueEvent, correlationId);

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logDisputeEvent("CHARGEBACK_MANUAL_QUEUE_PROCESSED", chargebackId,
                Map.of("chargebackId", chargebackId, "transactionId", manualQueueEvent.get("transactionId"),
                    "reason", manualQueueEvent.get("reason"), "priority", manualQueueEvent.get("priority"),
                    "assignedTo", manualQueueEvent.get("assignedTo"),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process chargeback manual queue: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("chargeback-manual-queue-fallback-events", Map.of(
                "originalEvent", manualQueueEvent, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleChargebackManualQueueEventFallback(
            Map<String, Object> manualQueueEvent,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String chargebackId = (String) manualQueueEvent.get("chargebackId");
        String correlationId = String.format("manual-queue-fallback-%s-p%d-o%d", chargebackId, partition, offset);

        log.error("Circuit breaker fallback triggered for chargeback manual queue: chargebackId={}, error={}",
            chargebackId, ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("chargeback-manual-queue-dlq", Map.of(
            "originalEvent", manualQueueEvent,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Chargeback Manual Queue Circuit Breaker Triggered",
                String.format("Chargeback %s manual queue processing failed: %s", chargebackId, ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltChargebackManualQueueEvent(
            @Payload Map<String, Object> manualQueueEvent,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String chargebackId = (String) manualQueueEvent.get("chargebackId");
        String correlationId = String.format("dlt-manual-queue-%s-%d", chargebackId, System.currentTimeMillis());

        log.error("Dead letter topic handler - Chargeback manual queue permanently failed: chargebackId={}, topic={}, error={}",
            chargebackId, topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logDisputeEvent("CHARGEBACK_MANUAL_QUEUE_DLT_EVENT", chargebackId,
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "chargebackId", chargebackId, "manualQueueEvent", manualQueueEvent,
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Chargeback Manual Queue Dead Letter Event",
                String.format("Chargeback %s manual queue sent to DLT: %s", chargebackId, exceptionMessage),
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

    private void processManualQueueItem(Map<String, Object> manualQueueEvent, String correlationId) {
        String chargebackId = (String) manualQueueEvent.get("chargebackId");
        String reason = (String) manualQueueEvent.get("reason");
        String priority = (String) manualQueueEvent.getOrDefault("priority", "MEDIUM");
        String assignedTo = (String) manualQueueEvent.get("assignedTo");
        LocalDateTime deadline = (LocalDateTime) manualQueueEvent.get("deadline");

        log.info("Processing manual queue item: chargebackId={}, reason={}, priority={}, assignedTo={}",
            chargebackId, reason, priority, assignedTo);

        // Update dispute status
        disputeService.setChargebackStatus(chargebackId, "MANUAL_REVIEW", correlationId);

        // Process based on reason
        switch (reason) {
            case "CRITICAL_FAILURE_ALERT":
                processCriticalFailureManualReview(manualQueueEvent, correlationId);
                break;

            case "VALIDATION_ERROR":
                processValidationErrorManualReview(manualQueueEvent, correlationId);
                break;

            case "INVESTIGATION_COMPLETE":
                processInvestigationCompleteReview(manualQueueEvent, correlationId);
                break;

            case "HIGH_VALUE_CHARGEBACK":
                processHighValueChargebackReview(manualQueueEvent, correlationId);
                break;

            case "COMPLEX_CASE":
                processComplexCaseReview(manualQueueEvent, correlationId);
                break;

            case "MANAGER_REVIEW":
                processManagerReview(manualQueueEvent, correlationId);
                break;

            case "EXCEPTION_HANDLING":
                processExceptionHandling(manualQueueEvent, correlationId);
                break;

            case "DLQ_RECOVERY":
                processDLQRecovery(manualQueueEvent, correlationId);
                break;

            default:
                processStandardManualReview(manualQueueEvent, correlationId);
                break;
        }

        // Assign to appropriate team member
        assignToTeamMember(chargebackId, assignedTo, priority, reason, correlationId);

        // Set up deadline monitoring if applicable
        if (deadline != null) {
            setupDeadlineMonitoring(chargebackId, deadline, correlationId);
        }

        // Update queue metrics
        updateQueueMetrics(reason, priority, assignedTo);

        log.info("Manual queue item processed: chargebackId={}, assignedTo={}", chargebackId, assignedTo);
    }

    private void processCriticalFailureManualReview(Map<String, Object> manualQueueEvent, String correlationId) {
        String chargebackId = (String) manualQueueEvent.get("chargebackId");

        log.warn("Processing critical failure manual review: chargebackId={}", chargebackId);

        // Create urgent task
        createManualTask(chargebackId, "CRITICAL_FAILURE_REVIEW", "CRITICAL",
            "Review and resolve critical failure in chargeback processing", correlationId);

        // Escalate to management
        notificationService.sendCriticalAlert(
            "Critical Chargeback Failure - Manual Review Required",
            String.format("Chargeback %s requires immediate manual intervention due to critical failure", chargebackId),
            Map.of("chargebackId", chargebackId, "urgency", "IMMEDIATE")
        );

        // Block further automated processing
        disputeService.blockAutomatedProcessing(chargebackId, "CRITICAL_FAILURE", correlationId);
    }

    private void processValidationErrorManualReview(Map<String, Object> manualQueueEvent, String correlationId) {
        String chargebackId = (String) manualQueueEvent.get("chargebackId");
        String errorType = (String) manualQueueEvent.get("errorType");

        log.info("Processing validation error manual review: chargebackId={}, errorType={}", chargebackId, errorType);

        // Create validation review task
        createManualTask(chargebackId, "VALIDATION_ERROR_REVIEW", "HIGH",
            String.format("Review and correct validation errors: %s", errorType), correlationId);

        // Gather additional data if needed
        requestAdditionalData(chargebackId, errorType, correlationId);

        // Assign to data validation specialist
        assignToSpecialist(chargebackId, "DATA_VALIDATION_SPECIALIST", correlationId);
    }

    private void processInvestigationCompleteReview(Map<String, Object> manualQueueEvent, String correlationId) {
        String chargebackId = (String) manualQueueEvent.get("chargebackId");

        log.info("Processing investigation complete review: chargebackId={}", chargebackId);

        // Create decision-making task
        createManualTask(chargebackId, "REPRESENTMENT_DECISION", "MEDIUM",
            "Review investigation results and decide on representment strategy", correlationId);

        // Retrieve investigation results
        retrieveInvestigationResults(chargebackId, correlationId);

        // Assign to senior analyst
        assignToSeniorAnalyst(chargebackId, correlationId);
    }

    private void processHighValueChargebackReview(Map<String, Object> manualQueueEvent, String correlationId) {
        String chargebackId = (String) manualQueueEvent.get("chargebackId");
        Object amount = manualQueueEvent.get("amount");

        log.info("Processing high-value chargeback review: chargebackId={}, amount={}", chargebackId, amount);

        // Create high-value review task
        createManualTask(chargebackId, "HIGH_VALUE_REVIEW", "HIGH",
            String.format("High-value chargeback review - Amount: %s", amount), correlationId);

        // Require manager approval
        requireManagerApproval(chargebackId, correlationId);

        // Escalate to senior management
        notificationService.sendExecutiveAlert(
            "High-Value Chargeback Review",
            String.format("High-value chargeback %s requires senior review - Amount: %s", chargebackId, amount),
            Map.of("chargebackId", chargebackId, "amount", amount, "priority", "EXECUTIVE")
        );
    }

    private void processComplexCaseReview(Map<String, Object> manualQueueEvent, String correlationId) {
        String chargebackId = (String) manualQueueEvent.get("chargebackId");
        String complexity = (String) manualQueueEvent.get("complexity");

        log.info("Processing complex case review: chargebackId={}, complexity={}", chargebackId, complexity);

        // Create complex case task
        createManualTask(chargebackId, "COMPLEX_CASE_REVIEW", "HIGH",
            String.format("Complex case requiring specialized review: %s", complexity), correlationId);

        // Assign to complex case specialist
        assignToSpecialist(chargebackId, "COMPLEX_CASE_SPECIALIST", correlationId);

        // Set extended deadline
        setExtendedDeadline(chargebackId, correlationId);
    }

    private void processManagerReview(Map<String, Object> manualQueueEvent, String correlationId) {
        String chargebackId = (String) manualQueueEvent.get("chargebackId");

        log.info("Processing manager review: chargebackId={}", chargebackId);

        // Create manager review task
        createManualTask(chargebackId, "MANAGER_APPROVAL", "HIGH",
            "Requires manager review and approval", correlationId);

        // Assign to manager
        assignToManager(chargebackId, correlationId);

        // Notify management
        notificationService.sendManagementAlert(
            "Chargeback Manager Review Required",
            String.format("Chargeback %s requires manager review", chargebackId),
            Map.of("chargebackId", chargebackId, "priority", "MANAGEMENT")
        );
    }

    private void processExceptionHandling(Map<String, Object> manualQueueEvent, String correlationId) {
        String chargebackId = (String) manualQueueEvent.get("chargebackId");
        String exceptionType = (String) manualQueueEvent.get("exceptionType");

        log.warn("Processing exception handling: chargebackId={}, exceptionType={}", chargebackId, exceptionType);

        // Create exception handling task
        createManualTask(chargebackId, "EXCEPTION_HANDLING", "HIGH",
            String.format("Handle exception: %s", exceptionType), correlationId);

        // Assign to exception handling specialist
        assignToSpecialist(chargebackId, "EXCEPTION_HANDLER", correlationId);

        // Document exception
        documentException(chargebackId, exceptionType, correlationId);
    }

    private void processDLQRecovery(Map<String, Object> manualQueueEvent, String correlationId) {
        String chargebackId = (String) manualQueueEvent.get("chargebackId");

        log.info("Processing DLQ recovery: chargebackId={}", chargebackId);

        // Create DLQ recovery task
        createManualTask(chargebackId, "DLQ_RECOVERY", "MEDIUM",
            "Recover and process chargeback from Dead Letter Queue", correlationId);

        // Analyze DLQ cause
        analyzeDLQCause(chargebackId, correlationId);

        // Assign to technical specialist
        assignToSpecialist(chargebackId, "TECHNICAL_SPECIALIST", correlationId);
    }

    private void processStandardManualReview(Map<String, Object> manualQueueEvent, String correlationId) {
        String chargebackId = (String) manualQueueEvent.get("chargebackId");

        log.info("Processing standard manual review: chargebackId={}", chargebackId);

        // Create standard review task
        createManualTask(chargebackId, "STANDARD_REVIEW", "MEDIUM",
            "Standard manual review of chargeback", correlationId);

        // Assign to available analyst
        assignToAvailableAnalyst(chargebackId, correlationId);
    }

    private void createManualTask(String chargebackId, String taskType, String priority, String description, String correlationId) {
        kafkaTemplate.send("manual-tasks", Map.of(
            "chargebackId", chargebackId,
            "taskType", taskType,
            "priority", priority,
            "description", description,
            "status", "ASSIGNED",
            "createdAt", Instant.now(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void assignToTeamMember(String chargebackId, String assignedTo, String priority, String reason, String correlationId) {
        if (assignedTo == null || assignedTo.isEmpty()) {
            assignedTo = determineAssignee(priority, reason);
        }

        // Update dispute assignment
        disputeService.assignChargebackReview(chargebackId, assignedTo, correlationId);

        // Send notification to assignee
        notificationService.sendNotification(assignedTo, "Chargeback Manual Review Assigned",
            String.format("You have been assigned to manually review chargeback %s. Reason: %s", chargebackId, reason),
            correlationId);

        // Track assignment metrics
        meterRegistry.counter("chargeback_manual_assignments_total",
            "assignee", assignedTo,
            "priority", priority,
            "reason", reason).increment();
    }

    private void setupDeadlineMonitoring(String chargebackId, LocalDateTime deadline, String correlationId) {
        kafkaTemplate.send("deadline-monitoring", Map.of(
            "chargebackId", chargebackId,
            "deadline", deadline,
            "monitoringType", "MANUAL_REVIEW",
            "reminderSchedule", Arrays.asList("7_DAYS", "3_DAYS", "1_DAY", "4_HOURS"),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private String determineAssignee(String priority, String reason) {
        switch (priority) {
            case "CRITICAL":
                return "senior-manager-1";
            case "HIGH":
                return "senior-analyst-1";
            default:
                return "analyst-1";
        }
    }

    private void requireManagerApproval(String chargebackId, String correlationId) {
        disputeService.setManagerApprovalRequired(chargebackId, true, correlationId);
    }

    private void assignToSpecialist(String chargebackId, String specialistType, String correlationId) {
        String specialist = getSpecialist(specialistType);
        disputeService.assignChargebackReview(chargebackId, specialist, correlationId);
    }

    private void assignToSeniorAnalyst(String chargebackId, String correlationId) {
        disputeService.assignChargebackReview(chargebackId, "senior-analyst-1", correlationId);
    }

    private void assignToManager(String chargebackId, String correlationId) {
        disputeService.assignChargebackReview(chargebackId, "dispute-manager-1", correlationId);
    }

    private void assignToAvailableAnalyst(String chargebackId, String correlationId) {
        disputeService.assignChargebackReview(chargebackId, "analyst-1", correlationId);
    }

    private String getSpecialist(String specialistType) {
        switch (specialistType) {
            case "DATA_VALIDATION_SPECIALIST":
                return "data-specialist-1";
            case "COMPLEX_CASE_SPECIALIST":
                return "complex-case-specialist-1";
            case "EXCEPTION_HANDLER":
                return "exception-handler-1";
            case "TECHNICAL_SPECIALIST":
                return "technical-specialist-1";
            default:
                return "specialist-1";
        }
    }

    private void retrieveInvestigationResults(String chargebackId, String correlationId) {
        kafkaTemplate.send("investigation-results-request", Map.of(
            "chargebackId", chargebackId,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void requestAdditionalData(String chargebackId, String errorType, String correlationId) {
        kafkaTemplate.send("additional-data-request", Map.of(
            "chargebackId", chargebackId,
            "dataType", errorType,
            "priority", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void setExtendedDeadline(String chargebackId, String correlationId) {
        LocalDateTime extendedDeadline = LocalDateTime.now().plusDays(10);
        disputeService.setChargebackDeadline(chargebackId, extendedDeadline, correlationId);
    }

    private void documentException(String chargebackId, String exceptionType, String correlationId) {
        kafkaTemplate.send("exception-documentation", Map.of(
            "chargebackId", chargebackId,
            "exceptionType", exceptionType,
            "documentedAt", Instant.now(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void analyzeDLQCause(String chargebackId, String correlationId) {
        kafkaTemplate.send("dlq-analysis", Map.of(
            "chargebackId", chargebackId,
            "analysisType", "DLQ_ROOT_CAUSE",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void updateQueueMetrics(String reason, String priority, String assignedTo) {
        meterRegistry.counter("chargeback_manual_queue_items_total",
            "reason", reason,
            "priority", priority).increment();

        meterRegistry.gauge("chargeback_manual_queue_backlog",
            getQueueBacklog());
    }

    private double getQueueBacklog() {
        // In real implementation, this would query the actual queue size
        return 10.0; // Placeholder
    }
}