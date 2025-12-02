package com.waqiti.dispute.kafka;

import com.waqiti.common.events.DisputeMonitoringTaskEvent;
import com.waqiti.dispute.domain.DisputeMonitoringTask;
import com.waqiti.dispute.repository.DisputeMonitoringTaskRepository;
import com.waqiti.dispute.service.DisputeMonitoringService;
import com.waqiti.dispute.service.TaskSchedulingService;
import com.waqiti.dispute.service.ComplianceMonitoringService;
import com.waqiti.dispute.metrics.DisputeMetricsService;
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
public class DisputeMonitoringTasksConsumer {

    private final DisputeMonitoringTaskRepository monitoringTaskRepository;
    private final DisputeMonitoringService monitoringService;
    private final TaskSchedulingService taskSchedulingService;
    private final ComplianceMonitoringService complianceMonitoringService;
    private final DisputeMetricsService metricsService;
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
        successCounter = Counter.builder("dispute_monitoring_tasks_processed_total")
            .description("Total number of successfully processed dispute monitoring task events")
            .register(meterRegistry);
        errorCounter = Counter.builder("dispute_monitoring_tasks_errors_total")
            .description("Total number of dispute monitoring task processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("dispute_monitoring_tasks_processing_duration")
            .description("Time taken to process dispute monitoring task events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"dispute-monitoring-tasks"},
        groupId = "dispute-monitoring-tasks-service-group",
        containerFactory = "disputeKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 8000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "dispute-monitoring-tasks", fallbackMethod = "handleMonitoringTaskEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 8000))
    public void handleMonitoringTaskEvent(
            @Payload DisputeMonitoringTaskEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("monitoring-task-%s-p%d-o%d", event.getTaskId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getTaskId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing dispute monitoring task: taskId={}, eventType={}, taskType={}",
                event.getTaskId(), event.getEventType(), event.getTaskType());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getEventType()) {
                case MONITORING_TASK_CREATED:
                    processMonitoringTaskCreated(event, correlationId);
                    break;

                case MONITORING_TASK_SCHEDULED:
                    processMonitoringTaskScheduled(event, correlationId);
                    break;

                case MONITORING_TASK_STARTED:
                    processMonitoringTaskStarted(event, correlationId);
                    break;

                case MONITORING_TASK_COMPLETED:
                    processMonitoringTaskCompleted(event, correlationId);
                    break;

                case MONITORING_TASK_FAILED:
                    processMonitoringTaskFailed(event, correlationId);
                    break;

                case MONITORING_TASK_RESCHEDULED:
                    processMonitoringTaskRescheduled(event, correlationId);
                    break;

                case MONITORING_TASK_CANCELLED:
                    processMonitoringTaskCancelled(event, correlationId);
                    break;

                case MONITORING_TASK_ESCALATED:
                    processMonitoringTaskEscalated(event, correlationId);
                    break;

                case COMPLIANCE_CHECK_REQUIRED:
                    processComplianceCheckRequired(event, correlationId);
                    break;

                default:
                    log.warn("Unknown dispute monitoring task event type: {}", event.getEventType());
                    processUnknownMonitoringTaskEvent(event, correlationId);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logDisputeEvent("DISPUTE_MONITORING_TASK_EVENT_PROCESSED", event.getTaskId(),
                Map.of("eventType", event.getEventType(), "taskType", event.getTaskType(),
                    "disputeId", event.getDisputeId(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process dispute monitoring task event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("dispute-monitoring-tasks-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleMonitoringTaskEventFallback(
            DisputeMonitoringTaskEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("monitoring-task-fallback-%s-p%d-o%d", event.getTaskId(), partition, offset);

        log.error("Circuit breaker fallback triggered for dispute monitoring task: taskId={}, error={}",
            event.getTaskId(), ex.getMessage());

        // Create incident for circuit breaker
        monitoringService.createMonitoringIncident(
            "DISPUTE_MONITORING_TASK_CIRCUIT_BREAKER",
            String.format("Dispute monitoring task circuit breaker triggered for task %s", event.getTaskId()),
            "HIGH",
            Map.of("taskId", event.getTaskId(), "eventType", event.getEventType(),
                "taskType", event.getTaskType(), "disputeId", event.getDisputeId(),
                "error", ex.getMessage(), "correlationId", correlationId)
        );

        // Send to dead letter queue
        kafkaTemplate.send("dispute-monitoring-tasks-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send alert
        try {
            notificationService.sendOperationalAlert(
                "Dispute Monitoring Task Circuit Breaker",
                String.format("Monitoring task %s processing failed: %s",
                    event.getTaskId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltMonitoringTaskEvent(
            @Payload DisputeMonitoringTaskEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-monitoring-task-%s-%d", event.getTaskId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Dispute monitoring task permanently failed: taskId={}, topic={}, error={}",
            event.getTaskId(), topic, exceptionMessage);

        // Create critical incident
        monitoringService.createMonitoringIncident(
            "DISPUTE_MONITORING_TASK_DLT_EVENT",
            String.format("Dispute monitoring task sent to DLT for task %s", event.getTaskId()),
            "CRITICAL",
            Map.of("taskId", event.getTaskId(), "originalTopic", topic,
                "errorMessage", exceptionMessage, "taskType", event.getTaskType(),
                "disputeId", event.getDisputeId(), "correlationId", correlationId,
                "requiresManualIntervention", true)
        );

        // Save to dead letter store for manual investigation
        auditService.logDisputeEvent("DISPUTE_MONITORING_TASK_DLT_EVENT", event.getTaskId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Dispute Monitoring Task Dead Letter Event",
                String.format("Monitoring task %s sent to DLT: %s",
                    event.getTaskId(), exceptionMessage),
                Map.of("taskId", event.getTaskId(), "topic", topic, "correlationId", correlationId)
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

    private void processMonitoringTaskCreated(DisputeMonitoringTaskEvent event, String correlationId) {
        // Create monitoring task record
        DisputeMonitoringTask task = DisputeMonitoringTask.builder()
            .taskId(event.getTaskId())
            .disputeId(event.getDisputeId())
            .taskType(event.getTaskType())
            .priority(event.getPriority())
            .description(event.getDescription())
            .status("CREATED")
            .createdAt(LocalDateTime.now())
            .createdBy(event.getCreatedBy())
            .targetDate(event.getTargetDate())
            .monitoringParameters(event.getMonitoringParameters())
            .correlationId(correlationId)
            .build();

        monitoringTaskRepository.save(task);

        // Setup monitoring task workflow
        monitoringService.setupMonitoringWorkflow(event.getTaskId(), event.getTaskType(),
            event.getMonitoringParameters());

        // Schedule task if needed
        if (event.getScheduleTime() != null) {
            kafkaTemplate.send("dispute-monitoring-tasks", Map.of(
                "taskId", event.getTaskId(),
                "eventType", "MONITORING_TASK_SCHEDULED",
                "taskType", event.getTaskType(),
                "disputeId", event.getDisputeId(),
                "scheduleTime", event.getScheduleTime(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        } else {
            // Start immediately
            kafkaTemplate.send("dispute-monitoring-tasks", Map.of(
                "taskId", event.getTaskId(),
                "eventType", "MONITORING_TASK_STARTED",
                "taskType", event.getTaskType(),
                "disputeId", event.getDisputeId(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordMonitoringTaskCreated(event.getTaskType());

        log.info("Dispute monitoring task created: taskId={}, type={}, disputeId={}",
            event.getTaskId(), event.getTaskType(), event.getDisputeId());
    }

    private void processMonitoringTaskScheduled(DisputeMonitoringTaskEvent event, String correlationId) {
        // Update task status
        DisputeMonitoringTask task = monitoringTaskRepository.findByTaskId(event.getTaskId())
            .orElseThrow(() -> new RuntimeException("Monitoring task not found"));

        task.setStatus("SCHEDULED");
        task.setScheduledAt(LocalDateTime.now());
        task.setScheduleTime(event.getScheduleTime());
        monitoringTaskRepository.save(task);

        // Schedule task execution
        taskSchedulingService.scheduleTask(event.getTaskId(), event.getScheduleTime(),
            event.getTaskType(), event.getMonitoringParameters());

        metricsService.recordMonitoringTaskScheduled(event.getTaskType());

        log.info("Dispute monitoring task scheduled: taskId={}, scheduleTime={}",
            event.getTaskId(), event.getScheduleTime());
    }

    private void processMonitoringTaskStarted(DisputeMonitoringTaskEvent event, String correlationId) {
        // Update task status
        DisputeMonitoringTask task = monitoringTaskRepository.findByTaskId(event.getTaskId())
            .orElseThrow(() -> new RuntimeException("Monitoring task not found"));

        task.setStatus("RUNNING");
        task.setStartedAt(LocalDateTime.now());
        monitoringTaskRepository.save(task);

        // Start monitoring execution
        monitoringService.startMonitoringExecution(event.getTaskId(), event.getTaskType(),
            event.getMonitoringParameters());

        // Setup progress tracking
        monitoringService.setupProgressTracking(event.getTaskId(), event.getProgressInterval());

        metricsService.recordMonitoringTaskStarted(event.getTaskType());

        log.info("Dispute monitoring task started: taskId={}, type={}",
            event.getTaskId(), event.getTaskType());
    }

    private void processMonitoringTaskCompleted(DisputeMonitoringTaskEvent event, String correlationId) {
        // Update task status
        DisputeMonitoringTask task = monitoringTaskRepository.findByTaskId(event.getTaskId())
            .orElseThrow(() -> new RuntimeException("Monitoring task not found"));

        task.setStatus("COMPLETED");
        task.setCompletedAt(LocalDateTime.now());
        task.setResults(event.getResults());
        task.setCompletionNotes(event.getCompletionNotes());
        monitoringTaskRepository.save(task);

        // Process monitoring results
        monitoringService.processMonitoringResults(event.getTaskId(), event.getResults());

        // Generate monitoring report
        String reportId = monitoringService.generateMonitoringReport(event.getTaskId(),
            event.getResults(), event.getCompletionNotes());

        // Update dispute status based on results
        kafkaTemplate.send("dispute-status-updates", Map.of(
            "disputeId", event.getDisputeId(),
            "status", "MONITORING_COMPLETED",
            "monitoringTaskId", event.getTaskId(),
            "results", event.getResults(),
            "reportId", reportId,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Check if compliance review is needed
        if (monitoringService.requiresComplianceReview(event.getResults())) {
            kafkaTemplate.send("dispute-monitoring-tasks", Map.of(
                "taskId", event.getTaskId(),
                "eventType", "COMPLIANCE_CHECK_REQUIRED",
                "taskType", event.getTaskType(),
                "disputeId", event.getDisputeId(),
                "complianceReason", "MONITORING_RESULTS_REVIEW",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordMonitoringTaskCompleted(event.getTaskType(),
            task.getStartedAt(), LocalDateTime.now());

        log.info("Dispute monitoring task completed: taskId={}, reportId={}, duration={}",
            event.getTaskId(), reportId,
            java.time.Duration.between(task.getStartedAt(), LocalDateTime.now()).toMinutes());
    }

    private void processMonitoringTaskFailed(DisputeMonitoringTaskEvent event, String correlationId) {
        // Update task status
        DisputeMonitoringTask task = monitoringTaskRepository.findByTaskId(event.getTaskId())
            .orElseThrow(() -> new RuntimeException("Monitoring task not found"));

        task.setStatus("FAILED");
        task.setFailedAt(LocalDateTime.now());
        task.setErrorMessage(event.getErrorMessage());
        task.setFailureReason(event.getFailureReason());
        monitoringTaskRepository.save(task);

        // Analyze failure and determine next steps
        String nextAction = monitoringService.analyzeFailure(event.getTaskId(),
            event.getFailureReason(), event.getErrorMessage());

        switch (nextAction) {
            case "RETRY":
                kafkaTemplate.send("dispute-monitoring-tasks", Map.of(
                    "taskId", event.getTaskId(),
                    "eventType", "MONITORING_TASK_RESCHEDULED",
                    "taskType", event.getTaskType(),
                    "disputeId", event.getDisputeId(),
                    "rescheduleReason", "AUTOMATIC_RETRY",
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                ));
                break;

            case "ESCALATE":
                kafkaTemplate.send("dispute-monitoring-tasks", Map.of(
                    "taskId", event.getTaskId(),
                    "eventType", "MONITORING_TASK_ESCALATED",
                    "taskType", event.getTaskType(),
                    "disputeId", event.getDisputeId(),
                    "escalationReason", event.getFailureReason(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                ));
                break;

            default:
                // Log for manual review
                notificationService.sendOperationalAlert(
                    "Monitoring Task Failed",
                    String.format("Monitoring task %s failed: %s", event.getTaskId(), event.getFailureReason()),
                    "MEDIUM"
                );
                break;
        }

        metricsService.recordMonitoringTaskFailed(event.getTaskType(), event.getFailureReason());

        log.warn("Dispute monitoring task failed: taskId={}, reason={}, nextAction={}",
            event.getTaskId(), event.getFailureReason(), nextAction);
    }

    private void processMonitoringTaskRescheduled(DisputeMonitoringTaskEvent event, String correlationId) {
        // Update task status
        DisputeMonitoringTask task = monitoringTaskRepository.findByTaskId(event.getTaskId())
            .orElseThrow(() -> new RuntimeException("Monitoring task not found"));

        task.setStatus("RESCHEDULED");
        task.setRescheduledAt(LocalDateTime.now());
        task.setRescheduleReason(event.getRescheduleReason());
        task.setNewScheduleTime(event.getNewScheduleTime());
        monitoringTaskRepository.save(task);

        // Reschedule task execution
        taskSchedulingService.rescheduleTask(event.getTaskId(), event.getNewScheduleTime(),
            event.getRescheduleReason());

        metricsService.recordMonitoringTaskRescheduled(event.getTaskType());

        log.info("Dispute monitoring task rescheduled: taskId={}, newTime={}, reason={}",
            event.getTaskId(), event.getNewScheduleTime(), event.getRescheduleReason());
    }

    private void processMonitoringTaskCancelled(DisputeMonitoringTaskEvent event, String correlationId) {
        // Update task status
        DisputeMonitoringTask task = monitoringTaskRepository.findByTaskId(event.getTaskId())
            .orElseThrow(() -> new RuntimeException("Monitoring task not found"));

        task.setStatus("CANCELLED");
        task.setCancelledAt(LocalDateTime.now());
        task.setCancellationReason(event.getCancellationReason());
        monitoringTaskRepository.save(task);

        // Cancel scheduled execution
        taskSchedulingService.cancelTask(event.getTaskId());

        // Clean up monitoring resources
        monitoringService.cleanupMonitoringResources(event.getTaskId());

        metricsService.recordMonitoringTaskCancelled(event.getTaskType());

        log.info("Dispute monitoring task cancelled: taskId={}, reason={}",
            event.getTaskId(), event.getCancellationReason());
    }

    private void processMonitoringTaskEscalated(DisputeMonitoringTaskEvent event, String correlationId) {
        // Update task status
        DisputeMonitoringTask task = monitoringTaskRepository.findByTaskId(event.getTaskId())
            .orElseThrow(() -> new RuntimeException("Monitoring task not found"));

        task.setStatus("ESCALATED");
        task.setEscalatedAt(LocalDateTime.now());
        task.setEscalationReason(event.getEscalationReason());
        task.setEscalatedTo(event.getEscalatedTo());
        monitoringTaskRepository.save(task);

        // Escalate to higher authority
        monitoringService.escalateMonitoringTask(event.getTaskId(), event.getEscalatedTo(),
            event.getEscalationReason());

        // Send escalation notification
        notificationService.sendEscalationAlert(
            "Monitoring Task Escalated",
            String.format("Monitoring task %s for dispute %s has been escalated: %s",
                event.getTaskId(), event.getDisputeId(), event.getEscalationReason()),
            "HIGH"
        );

        metricsService.recordMonitoringTaskEscalated(event.getTaskType());

        log.warn("Dispute monitoring task escalated: taskId={}, reason={}, escalatedTo={}",
            event.getTaskId(), event.getEscalationReason(), event.getEscalatedTo());
    }

    private void processComplianceCheckRequired(DisputeMonitoringTaskEvent event, String correlationId) {
        // Initiate compliance check
        String complianceCheckId = complianceMonitoringService.initiateComplianceCheck(
            event.getTaskId(), event.getDisputeId(), event.getComplianceReason());

        // Log compliance requirement
        auditService.logComplianceEvent("MONITORING_TASK_COMPLIANCE_CHECK_REQUIRED", event.getTaskId(),
            Map.of("disputeId", event.getDisputeId(), "complianceCheckId", complianceCheckId,
                "complianceReason", event.getComplianceReason(), "correlationId", correlationId,
                "timestamp", Instant.now()));

        // Send to compliance queue
        kafkaTemplate.send("compliance-review-queue", Map.of(
            "complianceCheckId", complianceCheckId,
            "taskId", event.getTaskId(),
            "disputeId", event.getDisputeId(),
            "checkType", "MONITORING_TASK_REVIEW",
            "reason", event.getComplianceReason(),
            "priority", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Send notification to compliance team
        notificationService.sendComplianceAlert(
            "Monitoring Task Compliance Check Required",
            String.format("Compliance check required for monitoring task %s: %s",
                event.getTaskId(), event.getComplianceReason()),
            Map.of("taskId", event.getTaskId(), "complianceCheckId", complianceCheckId,
                "correlationId", correlationId)
        );

        metricsService.recordComplianceCheckRequired(event.getTaskType());

        log.info("Compliance check required for monitoring task: taskId={}, checkId={}, reason={}",
            event.getTaskId(), complianceCheckId, event.getComplianceReason());
    }

    private void processUnknownMonitoringTaskEvent(DisputeMonitoringTaskEvent event, String correlationId) {
        // Create incident for unknown event type
        monitoringService.createMonitoringIncident(
            "UNKNOWN_DISPUTE_MONITORING_TASK_EVENT",
            String.format("Unknown dispute monitoring task event type %s for task %s",
                event.getEventType(), event.getTaskId()),
            "MEDIUM",
            Map.of("taskId", event.getTaskId(), "disputeId", event.getDisputeId(),
                "taskType", event.getTaskType(), "unknownEventType", event.getEventType(),
                "correlationId", correlationId)
        );

        log.warn("Unknown dispute monitoring task event: taskId={}, disputeId={}, eventType={}",
            event.getTaskId(), event.getDisputeId(), event.getEventType());
    }
}