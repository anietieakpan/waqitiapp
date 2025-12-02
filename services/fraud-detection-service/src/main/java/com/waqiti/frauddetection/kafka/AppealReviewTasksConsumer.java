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
 * Production-grade Kafka consumer for appeal review task events
 * Handles fraud decision appeals and review task management
 *
 * Critical for: Fraud appeal processing, customer satisfaction, regulatory compliance
 * SLA: Must process appeal tasks within 60 seconds for customer service efficiency
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AppealReviewTasksConsumer {

    private final FraudAppealService fraudAppealService;
    private final AppealReviewService appealReviewService;
    private final FraudAnalystService fraudAnalystService;
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
        successCounter = Counter.builder("appeal_review_tasks_processed_total")
            .description("Total number of successfully processed appeal review task events")
            .register(meterRegistry);
        errorCounter = Counter.builder("appeal_review_tasks_errors_total")
            .description("Total number of appeal review task processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("appeal_review_tasks_processing_duration")
            .description("Time taken to process appeal review task events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"appeal-review-tasks", "fraud-appeals", "appeal-workflow"},
        groupId = "fraud-appeal-review-tasks-group",
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
    @CircuitBreaker(name = "appeal-review-tasks", fallbackMethod = "handleAppealReviewTaskEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleAppealReviewTaskEvent(
            @Payload GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("appeal-%s-p%d-o%d", event.getId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing appeal review task: id={}, type={}, userId={}",
                event.getId(), event.getEventType(), event.getData().get("userId"));

            // Clean expired entries periodically
            cleanExpiredEntries();

            String userId = (String) event.getData().get("userId");
            String appealId = (String) event.getData().get("appealId");
            String originalDecisionId = (String) event.getData().get("originalDecisionId");

            switch (event.getEventType()) {
                case "APPEAL_SUBMITTED":
                    handleAppealSubmitted(event, userId, appealId, originalDecisionId, correlationId);
                    break;

                case "APPEAL_ASSIGNED":
                    handleAppealAssigned(event, appealId, correlationId);
                    break;

                case "APPEAL_UNDER_REVIEW":
                    handleAppealUnderReview(event, appealId, correlationId);
                    break;

                case "APPEAL_ADDITIONAL_INFO_REQUESTED":
                    handleAdditionalInfoRequested(event, userId, appealId, correlationId);
                    break;

                case "APPEAL_ADDITIONAL_INFO_RECEIVED":
                    handleAdditionalInfoReceived(event, appealId, correlationId);
                    break;

                case "APPEAL_APPROVED":
                    handleAppealApproved(event, userId, appealId, originalDecisionId, correlationId);
                    break;

                case "APPEAL_REJECTED":
                    handleAppealRejected(event, userId, appealId, correlationId);
                    break;

                case "APPEAL_ESCALATED":
                    handleAppealEscalated(event, appealId, correlationId);
                    break;

                case "APPEAL_WITHDRAWN":
                    handleAppealWithdrawn(event, userId, appealId, correlationId);
                    break;

                default:
                    log.warn("Unknown appeal review task event type: {}", event.getEventType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logSecurityEvent("APPEAL_REVIEW_TASK_PROCESSED", userId,
                Map.of("eventType", event.getEventType(), "appealId", appealId,
                    "originalDecisionId", originalDecisionId, "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process appeal review task event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("appeal-review-tasks-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleAppealReviewTaskEventFallback(
            GenericKafkaEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("appeal-fallback-%s-p%d-o%d", event.getId(), partition, offset);

        log.error("Circuit breaker fallback triggered for appeal review task: id={}, error={}",
            event.getId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("appeal-review-tasks-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Appeal Review Tasks Consumer Circuit Breaker Triggered",
                String.format("Appeal review task event %s failed: %s", event.getId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltAppealReviewTaskEvent(
            @Payload GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-appeal-%s-%d", event.getId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Appeal review task permanently failed: id={}, topic={}, error={}",
            event.getId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logSecurityEvent("APPEAL_REVIEW_TASK_DLT_EVENT",
            String.valueOf(event.getData().get("userId")),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Appeal Review Task Event Dead Letter",
                String.format("Appeal review task event %s sent to DLT: %s", event.getId(), exceptionMessage),
                Map.of("eventId", event.getId(), "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private void handleAppealSubmitted(GenericKafkaEvent event, String userId, String appealId, String originalDecisionId, String correlationId) {
        log.info("Appeal submitted: userId={}, appealId={}, originalDecision={}", userId, appealId, originalDecisionId);

        // Process the new appeal submission
        fraudAppealService.processAppealSubmission(appealId, userId, originalDecisionId);

        // Notify customer of appeal receipt
        customerCommunicationService.sendAppealReceiptConfirmation(userId, appealId);

        // Route to appropriate review queue
        appealReviewService.routeToReviewQueue(appealId, originalDecisionId);

        // Send to appeal workflow
        kafkaTemplate.send("appeal-workflow", Map.of(
            "appealId", appealId,
            "userId", userId,
            "eventType", "APPEAL_QUEUED_FOR_REVIEW",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Appeal submission processed: appealId={}", appealId);
    }

    private void handleAppealAssigned(GenericKafkaEvent event, String appealId, String correlationId) {
        String analystId = (String) event.getData().get("analystId");
        log.info("Appeal assigned: appealId={}, analystId={}", appealId, analystId);

        // Assign appeal to fraud analyst
        fraudAnalystService.assignAppealToAnalyst(appealId, analystId);

        // Notify analyst of new assignment
        fraudNotificationService.notifyAnalystOfAppealAssignment(analystId, appealId);

        // Update appeal status
        fraudAppealService.updateAppealStatus(appealId, "ASSIGNED");

        log.info("Appeal assignment processed: appealId={}, analyst={}", appealId, analystId);
    }

    private void handleAppealUnderReview(GenericKafkaEvent event, String appealId, String correlationId) {
        String analystId = (String) event.getData().get("analystId");
        log.info("Appeal under review: appealId={}, analystId={}", appealId, analystId);

        // Start the appeal review process
        appealReviewService.startAppealReview(appealId, analystId);

        // Update appeal status
        fraudAppealService.updateAppealStatus(appealId, "UNDER_REVIEW");

        // Start review timer for SLA tracking
        fraudAnalystService.startReviewTimer(appealId, analystId);

        log.info("Appeal review started: appealId={}", appealId);
    }

    private void handleAdditionalInfoRequested(GenericKafkaEvent event, String userId, String appealId, String correlationId) {
        String requestedInfo = (String) event.getData().get("requestedInfo");
        log.info("Additional info requested: userId={}, appealId={}, info={}", userId, appealId, requestedInfo);

        // Pause the review process
        appealReviewService.pauseReview(appealId, "ADDITIONAL_INFO_REQUESTED");

        // Send request to customer
        customerCommunicationService.requestAdditionalInformation(userId, appealId, requestedInfo);

        // Update appeal status
        fraudAppealService.updateAppealStatus(appealId, "AWAITING_ADDITIONAL_INFO");

        log.info("Additional info request sent: appealId={}", appealId);
    }

    private void handleAdditionalInfoReceived(GenericKafkaEvent event, String appealId, String correlationId) {
        log.info("Additional info received: appealId={}", appealId);

        // Resume the review process
        appealReviewService.resumeReview(appealId);

        // Update appeal status
        fraudAppealService.updateAppealStatus(appealId, "UNDER_REVIEW");

        // Notify assigned analyst
        String analystId = fraudAnalystService.getAssignedAnalyst(appealId);
        fraudNotificationService.notifyAnalystOfInfoReceived(analystId, appealId);

        log.info("Additional info processed: appealId={}", appealId);
    }

    private void handleAppealApproved(GenericKafkaEvent event, String userId, String appealId, String originalDecisionId, String correlationId) {
        String reviewNotes = (String) event.getData().get("reviewNotes");
        log.info("Appeal approved: userId={}, appealId={}, originalDecision={}", userId, appealId, originalDecisionId);

        // Process appeal approval
        fraudAppealService.approveAppeal(appealId, reviewNotes);

        // Reverse the original fraud decision
        fraudAppealService.reverseOriginalDecision(originalDecisionId, appealId);

        // Notify customer of approval
        customerCommunicationService.sendAppealApprovalNotification(userId, appealId);

        // Update metrics
        fraudAnalystService.recordAppealDecision(appealId, "APPROVED");

        // Send to fraud processing for decision reversal
        kafkaTemplate.send("fraud-decision-reversals", Map.of(
            "originalDecisionId", originalDecisionId,
            "appealId", appealId,
            "userId", userId,
            "eventType", "DECISION_REVERSED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Appeal approval processed: appealId={}", appealId);
    }

    private void handleAppealRejected(GenericKafkaEvent event, String userId, String appealId, String correlationId) {
        String rejectionReason = (String) event.getData().get("rejectionReason");
        String reviewNotes = (String) event.getData().get("reviewNotes");
        log.info("Appeal rejected: userId={}, appealId={}, reason={}", userId, appealId, rejectionReason);

        // Process appeal rejection
        fraudAppealService.rejectAppeal(appealId, rejectionReason, reviewNotes);

        // Notify customer of rejection
        customerCommunicationService.sendAppealRejectionNotification(userId, appealId, rejectionReason);

        // Update metrics
        fraudAnalystService.recordAppealDecision(appealId, "REJECTED");

        log.info("Appeal rejection processed: appealId={}", appealId);
    }

    private void handleAppealEscalated(GenericKafkaEvent event, String appealId, String correlationId) {
        String escalationReason = (String) event.getData().get("escalationReason");
        String supervisorId = (String) event.getData().get("supervisorId");
        log.info("Appeal escalated: appealId={}, reason={}, supervisor={}", appealId, escalationReason, supervisorId);

        // Process appeal escalation
        appealReviewService.escalateAppeal(appealId, escalationReason, supervisorId);

        // Notify supervisor
        fraudNotificationService.notifySupervisorOfEscalation(supervisorId, appealId, escalationReason);

        // Update appeal status
        fraudAppealService.updateAppealStatus(appealId, "ESCALATED");

        log.info("Appeal escalation processed: appealId={}", appealId);
    }

    private void handleAppealWithdrawn(GenericKafkaEvent event, String userId, String appealId, String correlationId) {
        String withdrawalReason = (String) event.getData().get("withdrawalReason");
        log.info("Appeal withdrawn: userId={}, appealId={}, reason={}", userId, appealId, withdrawalReason);

        // Process appeal withdrawal
        fraudAppealService.withdrawAppeal(appealId, withdrawalReason);

        // Send confirmation to customer
        customerCommunicationService.sendAppealWithdrawalConfirmation(userId, appealId);

        // Update metrics
        fraudAnalystService.recordAppealDecision(appealId, "WITHDRAWN");

        log.info("Appeal withdrawal processed: appealId={}", appealId);
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