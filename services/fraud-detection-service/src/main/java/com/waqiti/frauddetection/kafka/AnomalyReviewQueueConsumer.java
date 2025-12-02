package com.waqiti.frauddetection.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.frauddetection.service.*;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.metrics.MetricsService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-grade Kafka consumer for anomaly review queue events
 * Handles manual review queue processing for anomalies requiring human analysis
 * 
 * Critical for: Manual review workflows, analyst workload management, quality assurance
 * SLA: Must process queue events within 30 seconds for analyst efficiency
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AnomalyReviewQueueConsumer {

    private final AnomalyReviewService anomalyReviewService;
    private final ReviewWorkflowService reviewWorkflowService;
    private final FraudAnalystService fraudAnalystService;
    private final FraudNotificationService fraudNotificationService;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final ObjectMapper objectMapper;

    // Idempotency cache with 24-hour TTL
    private final Map<String, Instant> processedEventIds = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_HOURS = 24;

    // Metrics
    private final Counter reviewQueueCounter = Counter.builder("anomaly_review_queue_processed_total")
            .description("Total number of anomaly review queue events processed")
            .register(metricsService.getMeterRegistry());

    private final Timer processingTimer = Timer.builder("anomaly_review_queue_processing_duration")
            .description("Time taken to process anomaly review queue events")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = {"anomaly-review-queue"},
        groupId = "fraud-service-anomaly-review-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "anomaly-review-queue-processor", fallbackMethod = "handleAnomalyReviewFailure")
    @Retry(name = "anomaly-review-queue-processor")
    public void processAnomalyReviewQueue(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String eventId = event.getEventId();
        
        log.info("Processing anomaly review queue: {} from topic: {} partition: {} offset: {}", 
                eventId, topic, partition, offset);

        try {
            // Check idempotency
            if (isEventAlreadyProcessed(eventId)) {
                log.info("Review queue event {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }

            // Extract and validate review queue data
            AnomalyReviewData reviewData = extractReviewData(event.getPayload());
            validateReviewData(reviewData);

            // Mark as processed for idempotency
            markEventAsProcessed(eventId);

            // Process review queue
            processReviewQueue(reviewData, event);

            // Record successful processing metrics
            reviewQueueCounter.increment();
            
            // Audit the review processing
            auditReviewQueueProcessing(reviewData, event, "SUCCESS");

            log.info("Successfully processed review queue: {} for anomaly: {} - priority: {}", 
                    eventId, reviewData.getAnomalyId(), reviewData.getPriority());

            acknowledgment.acknowledge();

        } catch (IllegalArgumentException e) {
            log.error("Invalid review queue event data: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process review queue event: {}", eventId, e);
            auditReviewQueueProcessing(null, event, "FAILED: " + e.getMessage());
            throw new RuntimeException("Review queue event processing failed", e);

        } finally {
            sample.stop(processingTimer);
            cleanupIdempotencyCache();
        }
    }

    private boolean isEventAlreadyProcessed(String eventId) {
        Instant processedTime = processedEventIds.get(eventId);
        if (processedTime != null) {
            if (ChronoUnit.HOURS.between(processedTime, Instant.now()) < IDEMPOTENCY_TTL_HOURS) {
                return true;
            } else {
                processedEventIds.remove(eventId);
            }
        }
        return false;
    }

    private void markEventAsProcessed(String eventId) {
        processedEventIds.put(eventId, Instant.now());
    }

    private void cleanupIdempotencyCache() {
        Instant cutoff = Instant.now().minus(IDEMPOTENCY_TTL_HOURS, ChronoUnit.HOURS);
        processedEventIds.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }

    private AnomalyReviewData extractReviewData(Map<String, Object> payload) {
        return AnomalyReviewData.builder()
                .reviewId(extractString(payload, "reviewId"))
                .anomalyId(extractString(payload, "anomalyId"))
                .accountId(extractString(payload, "accountId"))
                .anomalyType(extractString(payload, "anomalyType"))
                .priority(extractString(payload, "priority"))
                .assignedAnalyst(extractString(payload, "assignedAnalyst"))
                .reviewStatus(extractString(payload, "reviewStatus"))
                .anomalyData(extractMap(payload, "anomalyData"))
                .reviewInstructions(extractString(payload, "reviewInstructions"))
                .dueDate(extractInstant(payload, "dueDate"))
                .createdAt(extractInstant(payload, "createdAt"))
                .build();
    }

    private void validateReviewData(AnomalyReviewData reviewData) {
        if (reviewData.getReviewId() == null || reviewData.getReviewId().trim().isEmpty()) {
            throw new IllegalArgumentException("Review ID is required");
        }
        
        if (reviewData.getAnomalyId() == null || reviewData.getAnomalyId().trim().isEmpty()) {
            throw new IllegalArgumentException("Anomaly ID is required");
        }
        
        if (reviewData.getAccountId() == null || reviewData.getAccountId().trim().isEmpty()) {
            throw new IllegalArgumentException("Account ID is required");
        }
        
        if (reviewData.getPriority() == null || reviewData.getPriority().trim().isEmpty()) {
            throw new IllegalArgumentException("Priority is required");
        }
    }

    private void processReviewQueue(AnomalyReviewData reviewData, GenericKafkaEvent event) {
        log.info("Processing review queue - ReviewId: {}, AnomalyId: {}, Priority: {}, Analyst: {}", 
                reviewData.getReviewId(), reviewData.getAnomalyId(), 
                reviewData.getPriority(), reviewData.getAssignedAnalyst());

        try {
            // Create review case
            String caseId = anomalyReviewService.createReviewCase(reviewData);

            // Assign to analyst or queue
            if (reviewData.getAssignedAnalyst() != null) {
                assignToAnalyst(reviewData, caseId);
            } else {
                assignToQueue(reviewData, caseId);
            }

            // Set up review workflow
            reviewWorkflowService.setupReviewWorkflow(caseId, reviewData);

            // Send notifications
            sendReviewNotifications(reviewData, caseId);

            log.info("Review queue processed - CaseId: {}, AssignedTo: {}", 
                    caseId, reviewData.getAssignedAnalyst() != null ? reviewData.getAssignedAnalyst() : "QUEUE");

        } catch (Exception e) {
            log.error("Failed to process review queue for anomaly: {}", reviewData.getAnomalyId(), e);
            throw new RuntimeException("Review queue processing failed", e);
        }
    }

    private void assignToAnalyst(AnomalyReviewData reviewData, String caseId) {
        fraudAnalystService.assignCase(reviewData.getAssignedAnalyst(), caseId, reviewData);
        fraudNotificationService.sendAnalystAssignment(reviewData.getAssignedAnalyst(), reviewData);
    }

    private void assignToQueue(AnomalyReviewData reviewData, String caseId) {
        String assignedAnalyst = fraudAnalystService.assignToAvailableAnalyst(caseId, reviewData.getPriority());
        if (assignedAnalyst != null) {
            reviewData.setAssignedAnalyst(assignedAnalyst);
            fraudNotificationService.sendAnalystAssignment(assignedAnalyst, reviewData);
        } else {
            fraudNotificationService.sendQueueAlert("No available analysts for priority: " + reviewData.getPriority());
        }
    }

    private void sendReviewNotifications(AnomalyReviewData reviewData, String caseId) {
        // Send based on priority
        if ("HIGH".equals(reviewData.getPriority()) || "CRITICAL".equals(reviewData.getPriority())) {
            fraudNotificationService.sendHighPriorityReviewAlert(reviewData, caseId);
        }
    }

    private void handleValidationError(GenericKafkaEvent event, IllegalArgumentException e) {
        log.error("Review queue validation failed for event: {}", event.getEventId(), e);
        
        auditService.auditSecurityEvent(
                "ANOMALY_REVIEW_QUEUE_VALIDATION_ERROR",
                null,
                "Review queue validation failed: " + e.getMessage(),
                Map.of(
                        "eventId", event.getEventId(),
                        "error", e.getMessage(),
                        "payload", event.getPayload()
                )
        );
    }

    private void auditReviewQueueProcessing(AnomalyReviewData reviewData, GenericKafkaEvent event, String status) {
        try {
            auditService.auditSecurityEvent(
                    "ANOMALY_REVIEW_QUEUE_PROCESSED",
                    reviewData != null ? reviewData.getAccountId() : null,
                    String.format("Anomaly review queue processing %s", status),
                    Map.of(
                            "eventId", event.getEventId(),
                            "reviewId", reviewData != null ? reviewData.getReviewId() : "unknown",
                            "anomalyId", reviewData != null ? reviewData.getAnomalyId() : "unknown",
                            "accountId", reviewData != null ? reviewData.getAccountId() : "unknown",
                            "priority", reviewData != null ? reviewData.getPriority() : "unknown",
                            "status", status,
                            "timestamp", Instant.now()
                    )
            );
        } catch (Exception e) {
            log.error("Failed to audit review queue processing", e);
        }
    }

    @DltHandler
    public void handleDlt(@Payload GenericKafkaEvent event,
                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("Review queue event sent to DLT - EventId: {}", event.getEventId());
    }

    public void handleAnomalyReviewFailure(GenericKafkaEvent event, String topic, int partition,
                                          long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("Circuit breaker activated for review queue processing - EventId: {}", event.getEventId(), e);
        acknowledgment.acknowledge();
    }

    // Helper methods
    private String extractString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private Instant extractInstant(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Instant) return (Instant) value;
        if (value instanceof Long) return Instant.ofEpochMilli((Long) value);
        return Instant.parse(value.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return Map.of();
    }

    // Data classes
    @lombok.Data
    @lombok.Builder
    public static class AnomalyReviewData {
        private String reviewId;
        private String anomalyId;
        private String accountId;
        private String anomalyType;
        private String priority;
        private String assignedAnalyst;
        private String reviewStatus;
        private Map<String, Object> anomalyData;
        private String reviewInstructions;
        private Instant dueDate;
        private Instant createdAt;
    }
}