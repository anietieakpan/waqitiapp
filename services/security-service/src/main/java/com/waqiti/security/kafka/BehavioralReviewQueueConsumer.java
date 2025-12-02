package com.waqiti.security.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.security.service.BehavioralReviewService;
import com.waqiti.security.service.SecurityNotificationService;
import com.waqiti.security.service.ThreatResponseService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
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

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class BehavioralReviewQueueConsumer {

    private final BehavioralReviewService behavioralReviewService;
    private final SecurityNotificationService securityNotificationService;
    private final ThreatResponseService threatResponseService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
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
        successCounter = Counter.builder("behavioral_review_queue_processed_total")
            .description("Total number of successfully processed behavioral review queue events")
            .register(meterRegistry);
        errorCounter = Counter.builder("behavioral_review_queue_errors_total")
            .description("Total number of behavioral review queue processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("behavioral_review_queue_processing_duration")
            .description("Time taken to process behavioral review queue events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"behavioral-review-queue", "security-behavioral-reviews", "behavioral-case-queue"},
        groupId = "security-service-behavioral-review-queue-group",
        containerFactory = "criticalSecurityKafkaListenerContainerFactory",
        concurrency = "3"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "behavioral-review-queue", fallbackMethod = "handleBehavioralReviewQueueFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleBehavioralReviewQueue(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("behavioral-review-p%d-o%d", partition, offset);

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);

            String reviewId = (String) event.get("reviewId");
            String userId = (String) event.get("userId");
            String reviewType = (String) event.get("reviewType");
            String priority = (String) event.get("priority");
            String eventKey = String.format("%s-%s-%s", reviewId, userId, event.get("timestamp"));

            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing behavioral review queue: reviewId={}, userId={}, type={}, priority={}",
                reviewId, userId, reviewType, priority);

            // Clean expired entries periodically
            cleanExpiredEntries();

            String status = (String) event.get("status");
            String assignedTo = (String) event.get("assignedTo");
            LocalDateTime queuedAt = LocalDateTime.parse((String) event.get("queuedAt"));
            LocalDateTime dueDate = event.get("dueDate") != null ?
                LocalDateTime.parse((String) event.get("dueDate")) : null;
            @SuppressWarnings("unchecked")
            Map<String, Object> reviewData = (Map<String, Object>) event.get("reviewData");
            @SuppressWarnings("unchecked")
            List<String> findings = (List<String>) event.getOrDefault("findings", List.of());
            String reviewerNotes = (String) event.get("reviewerNotes");
            @SuppressWarnings("unchecked")
            Map<String, Object> evidence = (Map<String, Object>) event.getOrDefault("evidence", Map.of());
            String escalationLevel = (String) event.getOrDefault("escalationLevel", "NORMAL");
            Boolean requiresManagerApproval = (Boolean) event.getOrDefault("requiresManagerApproval", false);

            // Process review queue event based on status
            switch (status) {
                case "QUEUED":
                    processQueuedReview(reviewId, userId, reviewType, priority, assignedTo,
                        queuedAt, dueDate, reviewData, escalationLevel, correlationId);
                    break;

                case "IN_PROGRESS":
                    processInProgressReview(reviewId, userId, reviewType, assignedTo,
                        reviewData, evidence, correlationId);
                    break;

                case "COMPLETED":
                    processCompletedReview(reviewId, userId, reviewType, assignedTo,
                        findings, reviewerNotes, evidence, requiresManagerApproval, correlationId);
                    break;

                case "ESCALATED":
                    processEscalatedReview(reviewId, userId, reviewType, escalationLevel,
                        findings, evidence, correlationId);
                    break;

                case "APPROVED":
                    processApprovedReview(reviewId, userId, reviewType, assignedTo,
                        findings, reviewerNotes, correlationId);
                    break;

                case "REJECTED":
                    processRejectedReview(reviewId, userId, reviewType, assignedTo,
                        findings, reviewerNotes, correlationId);
                    break;

                case "REASSIGNED":
                    processReassignedReview(reviewId, userId, reviewType, assignedTo,
                        reviewData, correlationId);
                    break;

                default:
                    processGenericReviewQueueEvent(reviewId, userId, reviewType, status,
                        reviewData, correlationId);
                    break;
            }

            // Handle priority escalation
            if ("HIGH".equals(priority) || "CRITICAL".equals(priority)) {
                handleHighPriorityReview(reviewId, userId, reviewType, priority, dueDate,
                    escalationLevel, correlationId);
            }

            // Check for overdue reviews
            if (dueDate != null && LocalDateTime.now().isAfter(dueDate) && !"COMPLETED".equals(status)) {
                handleOverdueReview(reviewId, userId, reviewType, assignedTo, dueDate, correlationId);
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logSecurityEvent("BEHAVIORAL_REVIEW_QUEUE_PROCESSED", userId,
                Map.of("reviewId", reviewId, "reviewType", reviewType, "status", status,
                    "priority", priority, "assignedTo", assignedTo, "escalationLevel", escalationLevel,
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process behavioral review queue event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("behavioral-review-queue-fallback-events", Map.of(
                "originalEvent", eventJson, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleBehavioralReviewQueueFallback(
            String eventJson,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("behavioral-review-fallback-p%d-o%d", partition, offset);

        log.error("Circuit breaker fallback triggered for behavioral review queue: error={}", ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("behavioral-review-queue-dlq", Map.of(
            "originalEvent", eventJson,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Behavioral Review Queue Circuit Breaker Triggered",
                String.format("Behavioral review queue processing failed: %s", ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltBehavioralReviewQueue(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-behavioral-review-%d", System.currentTimeMillis());

        log.error("Dead letter topic handler - Behavioral review queue permanently failed: topic={}, error={}",
            topic, exceptionMessage);

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            String reviewId = (String) event.get("reviewId");
            String userId = (String) event.get("userId");
            String reviewType = (String) event.get("reviewType");

            // Save to dead letter store for manual investigation
            auditService.logSecurityEvent("BEHAVIORAL_REVIEW_QUEUE_DLT_EVENT", userId,
                Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                    "reviewId", reviewId, "reviewType", reviewType, "correlationId", correlationId,
                    "requiresManualIntervention", true, "timestamp", Instant.now()));

            // Send critical alert
            securityNotificationService.sendCriticalAlert(
                "Behavioral Review Queue Dead Letter Event",
                String.format("Behavioral review %s for user %s sent to DLT: %s", reviewId, userId, exceptionMessage),
                Map.of("reviewId", reviewId, "userId", userId, "topic", topic, "correlationId", correlationId)
            );

        } catch (Exception ex) {
            log.error("Failed to parse behavioral review queue DLT event: {}", eventJson, ex);
        }
    }

    private void processQueuedReview(String reviewId, String userId, String reviewType,
                                   String priority, String assignedTo, LocalDateTime queuedAt,
                                   LocalDateTime dueDate, Map<String, Object> reviewData,
                                   String escalationLevel, String correlationId) {
        try {
            behavioralReviewService.processQueuedReview(reviewId, userId, reviewType, priority,
                assignedTo, queuedAt, dueDate, reviewData, escalationLevel);

            log.info("Queued review processed: reviewId={}, userId={}, assignedTo={}",
                reviewId, userId, assignedTo);

        } catch (Exception e) {
            log.error("Failed to process queued review: reviewId={}, userId={}",
                reviewId, userId, e);
            throw new RuntimeException("Queued review processing failed", e);
        }
    }

    private void processInProgressReview(String reviewId, String userId, String reviewType,
                                       String assignedTo, Map<String, Object> reviewData,
                                       Map<String, Object> evidence, String correlationId) {
        try {
            behavioralReviewService.processInProgressReview(reviewId, userId, reviewType,
                assignedTo, reviewData, evidence);

            log.info("In-progress review processed: reviewId={}, userId={}, assignedTo={}",
                reviewId, userId, assignedTo);

        } catch (Exception e) {
            log.error("Failed to process in-progress review: reviewId={}, userId={}",
                reviewId, userId, e);
            throw new RuntimeException("In-progress review processing failed", e);
        }
    }

    private void processCompletedReview(String reviewId, String userId, String reviewType,
                                      String assignedTo, List<String> findings, String reviewerNotes,
                                      Map<String, Object> evidence, Boolean requiresManagerApproval,
                                      String correlationId) {
        try {
            behavioralReviewService.processCompletedReview(reviewId, userId, reviewType,
                assignedTo, findings, reviewerNotes, evidence, requiresManagerApproval);

            if (requiresManagerApproval) {
                behavioralReviewService.escalateForManagerApproval(reviewId, userId, reviewType,
                    findings, reviewerNotes, correlationId);
            }

            log.info("Completed review processed: reviewId={}, userId={}, findings={}",
                reviewId, userId, findings.size());

        } catch (Exception e) {
            log.error("Failed to process completed review: reviewId={}, userId={}",
                reviewId, userId, e);
            throw new RuntimeException("Completed review processing failed", e);
        }
    }

    private void processEscalatedReview(String reviewId, String userId, String reviewType,
                                      String escalationLevel, List<String> findings,
                                      Map<String, Object> evidence, String correlationId) {
        try {
            behavioralReviewService.processEscalatedReview(reviewId, userId, reviewType,
                escalationLevel, findings, evidence);

            // Send escalation notification
            securityNotificationService.notifySecurityTeam(reviewId, userId, reviewType,
                escalationLevel, "REVIEW_ESCALATED", findings);

            log.info("Escalated review processed: reviewId={}, userId={}, level={}",
                reviewId, userId, escalationLevel);

        } catch (Exception e) {
            log.error("Failed to process escalated review: reviewId={}, userId={}",
                reviewId, userId, e);
            throw new RuntimeException("Escalated review processing failed", e);
        }
    }

    private void processApprovedReview(String reviewId, String userId, String reviewType,
                                     String assignedTo, List<String> findings, String reviewerNotes,
                                     String correlationId) {
        try {
            behavioralReviewService.processApprovedReview(reviewId, userId, reviewType,
                assignedTo, findings, reviewerNotes);

            // Execute approved actions
            threatResponseService.executeApprovedReviewActions(reviewId, userId, reviewType,
                findings, correlationId);

            log.info("Approved review processed: reviewId={}, userId={}, reviewer={}",
                reviewId, userId, assignedTo);

        } catch (Exception e) {
            log.error("Failed to process approved review: reviewId={}, userId={}",
                reviewId, userId, e);
            throw new RuntimeException("Approved review processing failed", e);
        }
    }

    private void processRejectedReview(String reviewId, String userId, String reviewType,
                                     String assignedTo, List<String> findings, String reviewerNotes,
                                     String correlationId) {
        try {
            behavioralReviewService.processRejectedReview(reviewId, userId, reviewType,
                assignedTo, findings, reviewerNotes);

            log.info("Rejected review processed: reviewId={}, userId={}, reviewer={}",
                reviewId, userId, assignedTo);

        } catch (Exception e) {
            log.error("Failed to process rejected review: reviewId={}, userId={}",
                reviewId, userId, e);
            throw new RuntimeException("Rejected review processing failed", e);
        }
    }

    private void processReassignedReview(String reviewId, String userId, String reviewType,
                                       String assignedTo, Map<String, Object> reviewData,
                                       String correlationId) {
        try {
            behavioralReviewService.processReassignedReview(reviewId, userId, reviewType,
                assignedTo, reviewData);

            log.info("Reassigned review processed: reviewId={}, userId={}, newAssignee={}",
                reviewId, userId, assignedTo);

        } catch (Exception e) {
            log.error("Failed to process reassigned review: reviewId={}, userId={}",
                reviewId, userId, e);
            throw new RuntimeException("Reassigned review processing failed", e);
        }
    }

    private void processGenericReviewQueueEvent(String reviewId, String userId, String reviewType,
                                              String status, Map<String, Object> reviewData,
                                              String correlationId) {
        try {
            behavioralReviewService.processGenericReviewQueueEvent(reviewId, userId, reviewType,
                status, reviewData);

            log.info("Generic review queue event processed: reviewId={}, userId={}, status={}",
                reviewId, userId, status);

        } catch (Exception e) {
            log.error("Failed to process generic review queue event: reviewId={}, userId={}",
                reviewId, userId, e);
            throw new RuntimeException("Generic review queue event processing failed", e);
        }
    }

    private void handleHighPriorityReview(String reviewId, String userId, String reviewType,
                                        String priority, LocalDateTime dueDate, String escalationLevel,
                                        String correlationId) {
        try {
            behavioralReviewService.handleHighPriorityReview(reviewId, userId, reviewType,
                priority, dueDate, escalationLevel);

            // Send high priority notification
            securityNotificationService.notifySecurityTeam(reviewId, userId, reviewType,
                priority, "HIGH_PRIORITY_REVIEW", List.of("Requires immediate attention"));

            log.info("High priority review handled: reviewId={}, userId={}, priority={}",
                reviewId, userId, priority);

        } catch (Exception e) {
            log.error("Failed to handle high priority review: reviewId={}, userId={}",
                reviewId, userId, e);
            // Don't throw exception as priority handling failure shouldn't block processing
        }
    }

    private void handleOverdueReview(String reviewId, String userId, String reviewType,
                                   String assignedTo, LocalDateTime dueDate, String correlationId) {
        try {
            behavioralReviewService.handleOverdueReview(reviewId, userId, reviewType,
                assignedTo, dueDate);

            // Send overdue notification
            securityNotificationService.notifySecurityTeam(reviewId, userId, reviewType,
                "OVERDUE", "REVIEW_OVERDUE", List.of("Review is past due date"));

            log.warn("Overdue review handled: reviewId={}, userId={}, assignedTo={}",
                reviewId, userId, assignedTo);

        } catch (Exception e) {
            log.error("Failed to handle overdue review: reviewId={}, userId={}",
                reviewId, userId, e);
            // Don't throw exception as overdue handling failure shouldn't block processing
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
}