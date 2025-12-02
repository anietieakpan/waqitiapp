package com.waqiti.compliance.kafka;

import com.waqiti.common.events.ClusteringReviewQueueEvent;
import com.waqiti.compliance.domain.ClusteringReview;
import com.waqiti.compliance.repository.ClusteringReviewRepository;
import com.waqiti.compliance.service.ClusteringReviewService;
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
public class ClusteringReviewQueueConsumer {

    private final ClusteringReviewRepository clusteringReviewRepository;
    private final ClusteringReviewService clusteringReviewService;
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
        successCounter = Counter.builder("clustering_review_queue_processed_total")
            .description("Total number of successfully processed clustering review queue events")
            .register(meterRegistry);
        errorCounter = Counter.builder("clustering_review_queue_errors_total")
            .description("Total number of clustering review queue processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("clustering_review_queue_processing_duration")
            .description("Time taken to process clustering review queue events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"clustering-review-queue"},
        groupId = "compliance-clustering-review-queue-group",
        containerFactory = "criticalComplianceKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "clustering-review-queue", fallbackMethod = "handleClusteringReviewQueueEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleClusteringReviewQueueEvent(
            @Payload ClusteringReviewQueueEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("clustering-review-%s-p%d-o%d", event.getReviewId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getReviewId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing clustering review queue: reviewId={}, clusteringId={}, priority={}",
                event.getReviewId(), event.getClusteringId(), event.getPriority());

            cleanExpiredEntries();

            switch (event.getEventType()) {
                case REVIEW_QUEUED:
                    processReviewQueued(event, correlationId);
                    break;

                case REVIEW_ASSIGNED:
                    processReviewAssigned(event, correlationId);
                    break;

                case REVIEW_STARTED:
                    processReviewStarted(event, correlationId);
                    break;

                case REVIEW_COMPLETED:
                    processReviewCompleted(event, correlationId);
                    break;

                case REVIEW_ESCALATED:
                    processReviewEscalated(event, correlationId);
                    break;

                case REVIEW_REJECTED:
                    processReviewRejected(event, correlationId);
                    break;

                case REVIEW_PRIORITY_CHANGED:
                    processReviewPriorityChanged(event, correlationId);
                    break;

                default:
                    log.warn("Unknown clustering review queue event type: {}", event.getEventType());
                    break;
            }

            markEventAsProcessed(eventKey);

            auditService.logComplianceEvent("CLUSTERING_REVIEW_QUEUE_EVENT_PROCESSED", event.getReviewId(),
                Map.of("eventType", event.getEventType(), "clusteringId", event.getClusteringId(),
                    "priority", event.getPriority(), "correlationId", correlationId,
                    "reviewType", event.getReviewType(), "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process clustering review queue event: {}", e.getMessage(), e);

            kafkaTemplate.send("compliance-clustering-review-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleClusteringReviewQueueEventFallback(
            ClusteringReviewQueueEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("clustering-review-fallback-%s-p%d-o%d", event.getReviewId(), partition, offset);

        log.error("Circuit breaker fallback triggered for clustering review queue: reviewId={}, error={}",
            event.getReviewId(), ex.getMessage());

        kafkaTemplate.send("clustering-review-queue-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        try {
            notificationService.sendOperationalAlert(
                "Clustering Review Queue Circuit Breaker Triggered",
                String.format("Clustering review %s failed: %s", event.getReviewId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltClusteringReviewQueueEvent(
            @Payload ClusteringReviewQueueEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-clustering-review-%s-%d", event.getReviewId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Clustering review queue permanently failed: reviewId={}, topic={}, error={}",
            event.getReviewId(), topic, exceptionMessage);

        auditService.logComplianceEvent("CLUSTERING_REVIEW_QUEUE_DLT_EVENT", event.getReviewId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        try {
            notificationService.sendCriticalAlert(
                "Clustering Review Queue Dead Letter Event",
                String.format("Clustering review %s sent to DLT: %s", event.getReviewId(), exceptionMessage),
                Map.of("reviewId", event.getReviewId(), "topic", topic, "correlationId", correlationId)
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

    private void processReviewQueued(ClusteringReviewQueueEvent event, String correlationId) {
        ClusteringReview review = ClusteringReview.builder()
            .reviewId(event.getReviewId())
            .clusteringId(event.getClusteringId())
            .reviewType(event.getReviewType())
            .priority(event.getPriority())
            .status("QUEUED")
            .queuedAt(LocalDateTime.now())
            .dueDate(calculateDueDate(event.getPriority()))
            .correlationId(correlationId)
            .build();
        clusteringReviewRepository.save(review);

        workflowService.queueReview(event.getReviewId(), event.getReviewType(), event.getPriority());

        kafkaTemplate.send("compliance-dashboard", Map.of(
            "eventType", "CLUSTERING_REVIEW_QUEUED",
            "reviewId", event.getReviewId(),
            "clusteringId", event.getClusteringId(),
            "priority", event.getPriority(),
            "dueDate", review.getDueDate(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordClusteringReviewQueued(event.getReviewType(), event.getPriority());

        log.info("Clustering review queued: reviewId={}, clusteringId={}, priority={}",
            event.getReviewId(), event.getClusteringId(), event.getPriority());
    }

    private void processReviewAssigned(ClusteringReviewQueueEvent event, String correlationId) {
        ClusteringReview review = clusteringReviewRepository.findByReviewId(event.getReviewId())
            .orElseThrow(() -> new RuntimeException("Clustering review not found"));

        review.setStatus("ASSIGNED");
        review.setAssignedTo(event.getAssignedTo());
        review.setAssignedAt(LocalDateTime.now());
        clusteringReviewRepository.save(review);

        clusteringReviewService.assignReview(event.getReviewId(), event.getAssignedTo());

        notificationService.sendNotification(event.getAssignedTo(), "Clustering Review Assigned",
            String.format("You have been assigned clustering review %s for analysis %s",
                event.getReviewId(), event.getClusteringId()),
            correlationId);

        kafkaTemplate.send("compliance-tasks", Map.of(
            "taskType", "CLUSTERING_REVIEW",
            "reviewId", event.getReviewId(),
            "assignedTo", event.getAssignedTo(),
            "priority", event.getPriority(),
            "dueDate", review.getDueDate(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordClusteringReviewAssigned();

        log.info("Clustering review assigned: reviewId={}, assignedTo={}",
            event.getReviewId(), event.getAssignedTo());
    }

    private void processReviewStarted(ClusteringReviewQueueEvent event, String correlationId) {
        ClusteringReview review = clusteringReviewRepository.findByReviewId(event.getReviewId())
            .orElseThrow(() -> new RuntimeException("Clustering review not found"));

        review.setStatus("IN_PROGRESS");
        review.setStartedAt(LocalDateTime.now());
        clusteringReviewRepository.save(review);

        clusteringReviewService.startReview(event.getReviewId());

        kafkaTemplate.send("compliance-dashboard", Map.of(
            "eventType", "CLUSTERING_REVIEW_STARTED",
            "reviewId", event.getReviewId(),
            "clusteringId", event.getClusteringId(),
            "assignedTo", review.getAssignedTo(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordClusteringReviewStarted();

        log.info("Clustering review started: reviewId={}, assignedTo={}",
            event.getReviewId(), review.getAssignedTo());
    }

    private void processReviewCompleted(ClusteringReviewQueueEvent event, String correlationId) {
        ClusteringReview review = clusteringReviewRepository.findByReviewId(event.getReviewId())
            .orElseThrow(() -> new RuntimeException("Clustering review not found"));

        review.setStatus("COMPLETED");
        review.setCompletedAt(LocalDateTime.now());
        review.setReviewOutcome(event.getReviewOutcome());
        review.setReviewNotes(event.getReviewNotes());
        clusteringReviewRepository.save(review);

        clusteringReviewService.completeReview(event.getReviewId(), event.getReviewOutcome(), event.getReviewNotes());

        if ("VIOLATIONS_FOUND".equals(event.getReviewOutcome())) {
            kafkaTemplate.send("compliance-violations", Map.of(
                "violationType", "CLUSTERING_VIOLATIONS",
                "reviewId", event.getReviewId(),
                "clusteringId", event.getClusteringId(),
                "violations", event.getViolationsFound(),
                "severity", determineSeverity(event.getViolationsFound()),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        if ("ESCALATION_REQUIRED".equals(event.getReviewOutcome())) {
            kafkaTemplate.send("compliance-review-queue", Map.of(
                "eventType", "REVIEW_ESCALATED",
                "reviewId", event.getReviewId(),
                "clusteringId", event.getClusteringId(),
                "escalationReason", event.getEscalationReason(),
                "priority", "HIGH",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        kafkaTemplate.send("clustering-results", Map.of(
            "eventType", "REVIEW_COMPLETED",
            "clusteringId", event.getClusteringId(),
            "reviewId", event.getReviewId(),
            "reviewOutcome", event.getReviewOutcome(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordClusteringReviewCompleted(event.getReviewOutcome());

        log.info("Clustering review completed: reviewId={}, outcome={}",
            event.getReviewId(), event.getReviewOutcome());
    }

    private void processReviewEscalated(ClusteringReviewQueueEvent event, String correlationId) {
        ClusteringReview review = clusteringReviewRepository.findByReviewId(event.getReviewId())
            .orElseThrow(() -> new RuntimeException("Clustering review not found"));

        review.setStatus("ESCALATED");
        review.setEscalatedAt(LocalDateTime.now());
        review.setEscalationReason(event.getEscalationReason());
        review.setPriority("HIGH");
        clusteringReviewRepository.save(review);

        clusteringReviewService.escalateReview(event.getReviewId(), event.getEscalationReason());

        kafkaTemplate.send("compliance-alerts", Map.of(
            "alertType", "CLUSTERING_REVIEW_ESCALATED",
            "reviewId", event.getReviewId(),
            "clusteringId", event.getClusteringId(),
            "escalationReason", event.getEscalationReason(),
            "priority", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Clustering Review Escalated",
            String.format("Clustering review %s escalated: %s", event.getReviewId(), event.getEscalationReason()),
            "HIGH");

        metricsService.recordClusteringReviewEscalated();

        log.warn("Clustering review escalated: reviewId={}, reason={}",
            event.getReviewId(), event.getEscalationReason());
    }

    private void processReviewRejected(ClusteringReviewQueueEvent event, String correlationId) {
        ClusteringReview review = clusteringReviewRepository.findByReviewId(event.getReviewId())
            .orElseThrow(() -> new RuntimeException("Clustering review not found"));

        review.setStatus("REJECTED");
        review.setRejectedAt(LocalDateTime.now());
        review.setRejectionReason(event.getRejectionReason());
        clusteringReviewRepository.save(review);

        kafkaTemplate.send("clustering-results", Map.of(
            "eventType", "REVIEW_REJECTED",
            "clusteringId", event.getClusteringId(),
            "reviewId", event.getReviewId(),
            "rejectionReason", event.getRejectionReason(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordClusteringReviewRejected();

        log.info("Clustering review rejected: reviewId={}, reason={}",
            event.getReviewId(), event.getRejectionReason());
    }

    private void processReviewPriorityChanged(ClusteringReviewQueueEvent event, String correlationId) {
        ClusteringReview review = clusteringReviewRepository.findByReviewId(event.getReviewId())
            .orElseThrow(() -> new RuntimeException("Clustering review not found"));

        String oldPriority = review.getPriority();
        review.setPriority(event.getNewPriority());
        review.setDueDate(calculateDueDate(event.getNewPriority()));
        clusteringReviewRepository.save(review);

        workflowService.updateReviewPriority(event.getReviewId(), event.getNewPriority());

        if ("HIGH".equals(event.getNewPriority()) || "CRITICAL".equals(event.getNewPriority())) {
            notificationService.sendNotification(review.getAssignedTo(), "Review Priority Updated",
                String.format("Clustering review %s priority changed from %s to %s",
                    event.getReviewId(), oldPriority, event.getNewPriority()),
                correlationId);
        }

        metricsService.recordClusteringReviewPriorityChanged(oldPriority, event.getNewPriority());

        log.info("Clustering review priority changed: reviewId={}, from={}, to={}",
            event.getReviewId(), oldPriority, event.getNewPriority());
    }

    private LocalDateTime calculateDueDate(String priority) {
        switch (priority) {
            case "CRITICAL":
                return LocalDateTime.now().plusHours(4);
            case "HIGH":
                return LocalDateTime.now().plusDays(1);
            case "MEDIUM":
                return LocalDateTime.now().plusDays(3);
            case "LOW":
                return LocalDateTime.now().plusDays(7);
            default:
                return LocalDateTime.now().plusDays(5);
        }
    }

    private String determineSeverity(List<Map<String, Object>> violations) {
        if (violations == null || violations.isEmpty()) {
            return "LOW";
        }

        long criticalCount = violations.stream()
            .filter(v -> "CRITICAL".equals(v.get("severity")))
            .count();

        if (criticalCount > 0) {
            return "CRITICAL";
        }

        long highCount = violations.stream()
            .filter(v -> "HIGH".equals(v.get("severity")))
            .count();

        if (highCount > 2) {
            return "HIGH";
        } else if (highCount > 0) {
            return "MEDIUM";
        }

        return "LOW";
    }
}