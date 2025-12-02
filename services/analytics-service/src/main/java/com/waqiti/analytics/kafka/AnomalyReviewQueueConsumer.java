package com.waqiti.analytics.kafka;

import com.waqiti.analytics.domain.AnomalyReview;
import com.waqiti.analytics.repository.AnomalyReviewRepository;
import com.waqiti.analytics.service.AnomalyReviewAnalyticsService;
import com.waqiti.analytics.service.ReviewQueueAnalyticsService;
import com.waqiti.analytics.service.ReviewerPerformanceService;
import com.waqiti.analytics.service.ReviewWorkflowService;
import com.waqiti.analytics.metrics.AnalyticsMetricsService;
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
public class AnomalyReviewQueueConsumer {

    private final AnomalyReviewRepository anomalyReviewRepository;
    private final AnomalyReviewAnalyticsService anomalyReviewAnalyticsService;
    private final ReviewQueueAnalyticsService reviewQueueAnalyticsService;
    private final ReviewerPerformanceService reviewerPerformanceService;
    private final ReviewWorkflowService reviewWorkflowService;
    private final AnalyticsMetricsService metricsService;
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
        successCounter = Counter.builder("anomaly_review_queue_processed_total")
            .description("Total number of successfully processed anomaly review queue events")
            .register(meterRegistry);
        errorCounter = Counter.builder("anomaly_review_queue_errors_total")
            .description("Total number of anomaly review queue processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("anomaly_review_queue_processing_duration")
            .description("Time taken to process anomaly review queue events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"anomaly-review-queue", "anomaly-review-assignments", "anomaly-review-decisions", "anomaly-review-escalations"},
        groupId = "analytics-anomaly-review-service-group",
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
    @CircuitBreaker(name = "anomaly-review-queue", fallbackMethod = "handleAnomalyReviewQueueEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleAnomalyReviewQueueEvent(
            @Payload Map<String, Object> event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String reviewId = String.valueOf(event.get("reviewId"));
        String correlationId = String.format("anomaly-review-%s-p%d-o%d", reviewId, partition, offset);
        String eventKey = String.format("%s-%s-%s", reviewId, event.get("eventType"), event.get("timestamp"));

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing anomaly review queue: reviewId={}, eventType={}, status={}",
                reviewId, event.get("eventType"), event.get("status"));

            // Clean expired entries periodically
            cleanExpiredEntries();

            String eventType = String.valueOf(event.get("eventType"));
            switch (eventType) {
                case "REVIEW_QUEUED":
                    processReviewQueued(event, correlationId);
                    break;

                case "REVIEW_ASSIGNED":
                    processReviewAssigned(event, correlationId);
                    break;

                case "REVIEW_STARTED":
                    processReviewStarted(event, correlationId);
                    break;

                case "REVIEW_COMPLETED":
                    processReviewCompleted(event, correlationId);
                    break;

                case "REVIEW_ESCALATED":
                    processReviewEscalated(event, correlationId);
                    break;

                case "REVIEW_REASSIGNED":
                    processReviewReassigned(event, correlationId);
                    break;

                case "REVIEW_CANCELLED":
                    processReviewCancelled(event, correlationId);
                    break;

                case "REVIEW_TIMEOUT":
                    processReviewTimeout(event, correlationId);
                    break;

                default:
                    log.warn("Unknown anomaly review queue event type: {}", eventType);
                    break;
            }

            // Analyze queue performance
            analyzeQueuePerformance(event, correlationId);

            // Update reviewer performance metrics
            updateReviewerPerformance(event, correlationId);

            // Generate workflow insights
            generateWorkflowInsights(event, correlationId);

            // Check queue thresholds
            checkQueueThresholds(correlationId);

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logAccountEvent("ANOMALY_REVIEW_QUEUE_EVENT_PROCESSED", reviewId,
                Map.of("eventType", eventType, "status", event.get("status"),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process anomaly review queue event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("anomaly-review-queue-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleAnomalyReviewQueueEventFallback(
            Map<String, Object> event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String reviewId = String.valueOf(event.get("reviewId"));
        String correlationId = String.format("anomaly-review-fallback-%s-p%d-o%d", reviewId, partition, offset);

        log.error("Circuit breaker fallback triggered for anomaly review queue: reviewId={}, error={}",
            reviewId, ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("anomaly-review-queue-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Anomaly Review Queue Circuit Breaker Triggered",
                String.format("Review %s analytics processing failed: %s", reviewId, ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltAnomalyReviewQueueEvent(
            @Payload Map<String, Object> event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String reviewId = String.valueOf(event.get("reviewId"));
        String correlationId = String.format("dlt-anomaly-review-%s-%d", reviewId, System.currentTimeMillis());

        log.error("Dead letter topic handler - Anomaly review queue permanently failed: reviewId={}, topic={}, error={}",
            reviewId, topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logAccountEvent("ANOMALY_REVIEW_QUEUE_DLT_EVENT", reviewId,
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.get("eventType"), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Anomaly Review Queue Dead Letter Event",
                String.format("Review %s analytics sent to DLT: %s", reviewId, exceptionMessage),
                Map.of("reviewId", reviewId, "topic", topic, "correlationId", correlationId)
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

    private void processReviewQueued(Map<String, Object> event, String correlationId) {
        String reviewId = String.valueOf(event.get("reviewId"));
        String anomalyId = String.valueOf(event.get("anomalyId"));
        String priority = String.valueOf(event.get("priority"));
        String queueType = String.valueOf(event.get("queueType"));

        // Save review record
        saveReviewRecord(event, "QUEUED", correlationId);

        // Update queue analytics
        reviewQueueAnalyticsService.recordQueuedReview(reviewId, anomalyId, priority, queueType);

        // Analyze queue size and wait times
        Map<String, Object> queueStats = reviewQueueAnalyticsService.analyzeQueueStats(queueType, priority);

        // Predict wait time
        long predictedWaitMinutes = reviewQueueAnalyticsService.predictWaitTime(queueType, priority);

        // Generate queue insights
        kafkaTemplate.send("analytics-insights", Map.of(
            "insightType", "REVIEW_QUEUE_STATUS",
            "reviewId", reviewId,
            "queueType", queueType,
            "priority", priority,
            "queueStats", queueStats,
            "predictedWaitMinutes", predictedWaitMinutes,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Review queued: reviewId={}, priority={}, predictedWait={}min", reviewId, priority, predictedWaitMinutes);
    }

    private void processReviewAssigned(Map<String, Object> event, String correlationId) {
        String reviewId = String.valueOf(event.get("reviewId"));
        String reviewerId = String.valueOf(event.get("reviewerId"));
        String assignmentMethod = String.valueOf(event.get("assignmentMethod"));
        Integer queueTimeMinutes = (Integer) event.get("queueTimeMinutes");

        // Update review record
        updateReviewRecord(reviewId, "ASSIGNED", event, correlationId);

        // Record assignment analytics
        reviewQueueAnalyticsService.recordReviewAssignment(reviewId, reviewerId, assignmentMethod, queueTimeMinutes);

        // Update reviewer workload
        reviewerPerformanceService.updateReviewerWorkload(reviewerId, "ASSIGNED");

        // Analyze assignment efficiency
        double assignmentEfficiency = reviewQueueAnalyticsService.calculateAssignmentEfficiency(assignmentMethod, queueTimeMinutes);

        log.info("Review assigned: reviewId={}, reviewer={}, queueTime={}min, efficiency={}",
            reviewId, reviewerId, queueTimeMinutes, assignmentEfficiency);
    }

    private void processReviewStarted(Map<String, Object> event, String correlationId) {
        String reviewId = String.valueOf(event.get("reviewId"));
        String reviewerId = String.valueOf(event.get("reviewerId"));
        Integer assignmentTimeMinutes = (Integer) event.get("assignmentTimeMinutes");

        // Update review record
        updateReviewRecord(reviewId, "IN_PROGRESS", event, correlationId);

        // Record review start analytics
        reviewerPerformanceService.recordReviewStart(reviewerId, reviewId, assignmentTimeMinutes);

        // Update active reviews count
        reviewQueueAnalyticsService.updateActiveReviewsCount(reviewerId, 1);

        log.info("Review started: reviewId={}, reviewer={}, assignmentTime={}min",
            reviewId, reviewerId, assignmentTimeMinutes);
    }

    private void processReviewCompleted(Map<String, Object> event, String correlationId) {
        String reviewId = String.valueOf(event.get("reviewId"));
        String reviewerId = String.valueOf(event.get("reviewerId"));
        String decision = String.valueOf(event.get("decision"));
        String confidence = String.valueOf(event.get("confidence"));
        Integer reviewTimeMinutes = (Integer) event.get("reviewTimeMinutes");
        String quality = String.valueOf(event.get("quality"));

        // Update review record
        updateReviewRecord(reviewId, "COMPLETED", event, correlationId);

        // Record completion analytics
        reviewerPerformanceService.recordReviewCompletion(reviewerId, reviewId, decision, confidence, reviewTimeMinutes, quality);

        // Update active reviews count
        reviewQueueAnalyticsService.updateActiveReviewsCount(reviewerId, -1);

        // Analyze review efficiency
        double reviewEfficiency = calculateReviewEfficiency(reviewTimeMinutes, quality);

        // Update reviewer performance metrics
        reviewerPerformanceService.updatePerformanceMetrics(reviewerId, reviewTimeMinutes, quality, decision);

        // Generate performance insights
        Map<String, Object> performanceInsights = reviewerPerformanceService.generatePerformanceInsights(reviewerId);
        if (!performanceInsights.isEmpty()) {
            kafkaTemplate.send("analytics-insights", Map.of(
                "insightType", "REVIEWER_PERFORMANCE",
                "reviewerId", reviewerId,
                "reviewId", reviewId,
                "efficiency", reviewEfficiency,
                "performanceInsights", performanceInsights,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        log.info("Review completed: reviewId={}, reviewer={}, decision={}, time={}min, efficiency={}",
            reviewId, reviewerId, decision, reviewTimeMinutes, reviewEfficiency);
    }

    private void processReviewEscalated(Map<String, Object> event, String correlationId) {
        String reviewId = String.valueOf(event.get("reviewId"));
        String originalReviewerId = String.valueOf(event.get("originalReviewerId"));
        String escalatedTo = String.valueOf(event.get("escalatedTo"));
        String escalationReason = String.valueOf(event.get("escalationReason"));
        Integer reviewTimeBeforeEscalation = (Integer) event.get("reviewTimeBeforeEscalation");

        // Update review record
        updateReviewRecord(reviewId, "ESCALATED", event, correlationId);

        // Record escalation analytics
        reviewWorkflowService.recordEscalation(reviewId, originalReviewerId, escalatedTo, escalationReason, reviewTimeBeforeEscalation);

        // Analyze escalation patterns
        Map<String, Object> escalationPatterns = reviewWorkflowService.analyzeEscalationPatterns(escalationReason);

        // Check for escalation trends
        boolean escalationTrend = reviewWorkflowService.detectEscalationTrends(originalReviewerId, escalationReason);
        if (escalationTrend) {
            kafkaTemplate.send("analytics-alerts", Map.of(
                "alertType", "HIGH_ESCALATION_RATE",
                "reviewerId", originalReviewerId,
                "escalationReason", escalationReason,
                "severity", "MEDIUM",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        log.info("Review escalated: reviewId={}, from={}, to={}, reason={}, trend={}",
            reviewId, originalReviewerId, escalatedTo, escalationReason, escalationTrend);
    }

    private void processReviewReassigned(Map<String, Object> event, String correlationId) {
        String reviewId = String.valueOf(event.get("reviewId"));
        String originalReviewerId = String.valueOf(event.get("originalReviewerId"));
        String newReviewerId = String.valueOf(event.get("newReviewerId"));
        String reassignmentReason = String.valueOf(event.get("reassignmentReason"));

        // Update review record
        updateReviewRecord(reviewId, "REASSIGNED", event, correlationId);

        // Record reassignment analytics
        reviewWorkflowService.recordReassignment(reviewId, originalReviewerId, newReviewerId, reassignmentReason);

        // Update reviewer workloads
        reviewerPerformanceService.updateReviewerWorkload(originalReviewerId, "REMOVED");
        reviewerPerformanceService.updateReviewerWorkload(newReviewerId, "ASSIGNED");

        // Analyze reassignment patterns
        Map<String, Object> reassignmentInsights = reviewWorkflowService.analyzeReassignmentPatterns(reassignmentReason);

        log.info("Review reassigned: reviewId={}, from={}, to={}, reason={}",
            reviewId, originalReviewerId, newReviewerId, reassignmentReason);
    }

    private void processReviewCancelled(Map<String, Object> event, String correlationId) {
        String reviewId = String.valueOf(event.get("reviewId"));
        String reviewerId = String.valueOf(event.get("reviewerId"));
        String cancellationReason = String.valueOf(event.get("cancellationReason"));

        // Update review record
        updateReviewRecord(reviewId, "CANCELLED", event, correlationId);

        // Record cancellation analytics
        reviewWorkflowService.recordCancellation(reviewId, reviewerId, cancellationReason);

        // Update active reviews count if reviewer was assigned
        if (reviewerId != null && !reviewerId.isEmpty()) {
            reviewQueueAnalyticsService.updateActiveReviewsCount(reviewerId, -1);
        }

        log.info("Review cancelled: reviewId={}, reviewer={}, reason={}", reviewId, reviewerId, cancellationReason);
    }

    private void processReviewTimeout(Map<String, Object> event, String correlationId) {
        String reviewId = String.valueOf(event.get("reviewId"));
        String reviewerId = String.valueOf(event.get("reviewerId"));
        Integer timeoutAfterMinutes = (Integer) event.get("timeoutAfterMinutes");
        String timeoutAction = String.valueOf(event.get("timeoutAction"));

        // Update review record
        updateReviewRecord(reviewId, "TIMEOUT", event, correlationId);

        // Record timeout analytics
        reviewWorkflowService.recordTimeout(reviewId, reviewerId, timeoutAfterMinutes, timeoutAction);

        // Update reviewer performance with timeout
        reviewerPerformanceService.recordReviewTimeout(reviewerId, reviewId, timeoutAfterMinutes);

        // Check for timeout patterns
        boolean timeoutPattern = reviewerPerformanceService.detectTimeoutPatterns(reviewerId);
        if (timeoutPattern) {
            kafkaTemplate.send("analytics-alerts", Map.of(
                "alertType", "REVIEWER_TIMEOUT_PATTERN",
                "reviewerId", reviewerId,
                "timeoutAfterMinutes", timeoutAfterMinutes,
                "severity", "MEDIUM",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        log.info("Review timeout: reviewId={}, reviewer={}, timeout={}min, pattern={}",
            reviewId, reviewerId, timeoutAfterMinutes, timeoutPattern);
    }

    private void saveReviewRecord(Map<String, Object> event, String status, String correlationId) {
        String reviewId = String.valueOf(event.get("reviewId"));
        String anomalyId = String.valueOf(event.get("anomalyId"));
        String priority = String.valueOf(event.get("priority"));
        String queueType = String.valueOf(event.get("queueType"));

        AnomalyReview review = AnomalyReview.builder()
            .id(UUID.randomUUID().toString())
            .reviewId(reviewId)
            .anomalyId(anomalyId)
            .status(status)
            .priority(priority)
            .queueType(queueType)
            .queuedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .metadata((Map<String, Object>) event.get("metadata"))
            .build();

        anomalyReviewRepository.save(review);

        // Record metrics
        metricsService.recordAnomalyReview(status, priority, queueType);
    }

    private void updateReviewRecord(String reviewId, String status, Map<String, Object> event, String correlationId) {
        Optional<AnomalyReview> reviewOpt = anomalyReviewRepository.findByReviewId(reviewId);
        if (reviewOpt.isPresent()) {
            AnomalyReview review = reviewOpt.get();
            review.setStatus(status);
            review.setLastUpdatedAt(LocalDateTime.now());

            // Update specific fields based on status
            switch (status) {
                case "ASSIGNED":
                    review.setAssignedTo(String.valueOf(event.get("reviewerId")));
                    review.setAssignedAt(LocalDateTime.now());
                    break;
                case "IN_PROGRESS":
                    review.setStartedAt(LocalDateTime.now());
                    break;
                case "COMPLETED":
                    review.setCompletedAt(LocalDateTime.now());
                    review.setDecision(String.valueOf(event.get("decision")));
                    review.setConfidence(String.valueOf(event.get("confidence")));
                    break;
            }

            anomalyReviewRepository.save(review);
        }
    }

    private void analyzeQueuePerformance(Map<String, Object> event, String correlationId) {
        String eventType = String.valueOf(event.get("eventType"));
        String queueType = String.valueOf(event.getOrDefault("queueType", "default"));

        // Analyze overall queue performance
        Map<String, Object> queuePerformance = reviewQueueAnalyticsService.analyzeQueuePerformance(queueType);

        // Generate performance insights
        if (!queuePerformance.isEmpty()) {
            kafkaTemplate.send("analytics-insights", Map.of(
                "insightType", "QUEUE_PERFORMANCE",
                "queueType", queueType,
                "eventType", eventType,
                "performance", queuePerformance,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        log.info("Queue performance analyzed: type={}, event={}", queueType, eventType);
    }

    private void updateReviewerPerformance(Map<String, Object> event, String correlationId) {
        String reviewerId = String.valueOf(event.getOrDefault("reviewerId", ""));
        if (!reviewerId.isEmpty()) {
            String eventType = String.valueOf(event.get("eventType"));

            // Update reviewer metrics based on event type
            reviewerPerformanceService.updateReviewerMetrics(reviewerId, eventType, event);

            log.info("Reviewer performance updated: reviewer={}, event={}", reviewerId, eventType);
        }
    }

    private void generateWorkflowInsights(Map<String, Object> event, String correlationId) {
        String eventType = String.valueOf(event.get("eventType"));
        String reviewId = String.valueOf(event.get("reviewId"));

        // Generate workflow insights
        Map<String, Object> workflowInsights = reviewWorkflowService.generateWorkflowInsights(eventType, event);

        kafkaTemplate.send("real-time-analytics", Map.of(
            "analyticsType", "WORKFLOW_INSIGHTS",
            "reviewId", reviewId,
            "eventType", eventType,
            "insights", workflowInsights,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Workflow insights generated: review={}, event={}", reviewId, eventType);
    }

    private void checkQueueThresholds(String correlationId) {
        // Check various queue thresholds
        Map<String, Integer> queueSizes = reviewQueueAnalyticsService.getCurrentQueueSizes();
        Map<String, Long> avgWaitTimes = reviewQueueAnalyticsService.getAverageWaitTimes();

        for (Map.Entry<String, Integer> entry : queueSizes.entrySet()) {
            String queueType = entry.getKey();
            Integer queueSize = entry.getValue();
            Integer threshold = getQueueSizeThreshold(queueType);

            if (queueSize > threshold) {
                kafkaTemplate.send("analytics-alerts", Map.of(
                    "alertType", "QUEUE_SIZE_THRESHOLD_BREACH",
                    "queueType", queueType,
                    "currentSize", queueSize,
                    "threshold", threshold,
                    "severity", queueSize > threshold * 1.5 ? "HIGH" : "MEDIUM",
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                ));
            }
        }

        for (Map.Entry<String, Long> entry : avgWaitTimes.entrySet()) {
            String queueType = entry.getKey();
            Long avgWaitMinutes = entry.getValue();
            Long threshold = getWaitTimeThreshold(queueType);

            if (avgWaitMinutes > threshold) {
                kafkaTemplate.send("analytics-alerts", Map.of(
                    "alertType", "WAIT_TIME_THRESHOLD_BREACH",
                    "queueType", queueType,
                    "currentWaitMinutes", avgWaitMinutes,
                    "thresholdMinutes", threshold,
                    "severity", avgWaitMinutes > threshold * 1.5 ? "HIGH" : "MEDIUM",
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                ));
            }
        }

        log.info("Queue thresholds checked: sizes={}, waitTimes={}", queueSizes.size(), avgWaitTimes.size());
    }

    private double calculateReviewEfficiency(Integer reviewTimeMinutes, String quality) {
        if (reviewTimeMinutes == null || reviewTimeMinutes <= 0) {
            return 0.0;
        }

        double baseEfficiency = 1.0;

        // Adjust efficiency based on review time
        if (reviewTimeMinutes <= 15) baseEfficiency = 1.0;
        else if (reviewTimeMinutes <= 30) baseEfficiency = 0.9;
        else if (reviewTimeMinutes <= 60) baseEfficiency = 0.8;
        else if (reviewTimeMinutes <= 120) baseEfficiency = 0.7;
        else baseEfficiency = 0.6;

        // Adjust efficiency based on quality
        switch (quality.toUpperCase()) {
            case "EXCELLENT": baseEfficiency *= 1.0; break;
            case "GOOD": baseEfficiency *= 0.95; break;
            case "AVERAGE": baseEfficiency *= 0.85; break;
            case "POOR": baseEfficiency *= 0.7; break;
            default: baseEfficiency *= 0.8; break;
        }

        return Math.max(0.0, Math.min(1.0, baseEfficiency));
    }

    private Integer getQueueSizeThreshold(String queueType) {
        switch (queueType.toUpperCase()) {
            case "HIGH_PRIORITY": return 10;
            case "MEDIUM_PRIORITY": return 25;
            case "LOW_PRIORITY": return 50;
            case "ESCALATED": return 5;
            default: return 30;
        }
    }

    private Long getWaitTimeThreshold(String queueType) {
        switch (queueType.toUpperCase()) {
            case "HIGH_PRIORITY": return 30L; // 30 minutes
            case "MEDIUM_PRIORITY": return 120L; // 2 hours
            case "LOW_PRIORITY": return 480L; // 8 hours
            case "ESCALATED": return 15L; // 15 minutes
            default: return 240L; // 4 hours
        }
    }
}