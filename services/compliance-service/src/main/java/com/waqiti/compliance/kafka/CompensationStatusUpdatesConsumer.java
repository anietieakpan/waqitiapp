package com.waqiti.compliance.kafka;

import com.waqiti.common.events.CompensationStatusUpdatesEvent;
import com.waqiti.compliance.domain.CompensationStatus;
import com.waqiti.compliance.repository.CompensationStatusRepository;
import com.waqiti.compliance.service.CompensationService;
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
public class CompensationStatusUpdatesConsumer {

    private final CompensationStatusRepository compensationStatusRepository;
    private final CompensationService compensationService;
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
        successCounter = Counter.builder("compensation_status_updates_processed_total")
            .description("Total number of successfully processed compensation status updates events")
            .register(meterRegistry);
        errorCounter = Counter.builder("compensation_status_updates_errors_total")
            .description("Total number of compensation status updates processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("compensation_status_updates_processing_duration")
            .description("Time taken to process compensation status updates events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"compensation-status-updates"},
        groupId = "compliance-compensation-status-updates-group",
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
    @CircuitBreaker(name = "compensation-status-updates", fallbackMethod = "handleCompensationStatusUpdatesEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleCompensationStatusUpdatesEvent(
            @Payload CompensationStatusUpdatesEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("comp-status-%s-p%d-o%d", event.getRequestId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getRequestId(), event.getEventType(), event.getTimestamp());

        try {
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing compensation status update: requestId={}, oldStatus={}, newStatus={}",
                event.getRequestId(), event.getOldStatus(), event.getNewStatus());

            cleanExpiredEntries();

            switch (event.getEventType()) {
                case STATUS_CHANGED:
                    processStatusChanged(event, correlationId);
                    break;

                case REVIEW_STATUS_UPDATED:
                    processReviewStatusUpdated(event, correlationId);
                    break;

                case PAYMENT_STATUS_UPDATED:
                    processPaymentStatusUpdated(event, correlationId);
                    break;

                case APPROVAL_STATUS_UPDATED:
                    processApprovalStatusUpdated(event, correlationId);
                    break;

                case ESCALATION_STATUS_UPDATED:
                    processEscalationStatusUpdated(event, correlationId);
                    break;

                default:
                    log.warn("Unknown compensation status updates event type: {}", event.getEventType());
                    break;
            }

            markEventAsProcessed(eventKey);

            auditService.logComplianceEvent("COMPENSATION_STATUS_UPDATES_EVENT_PROCESSED", event.getRequestId(),
                Map.of("eventType", event.getEventType(), "oldStatus", event.getOldStatus(),
                    "newStatus", event.getNewStatus(), "correlationId", correlationId,
                    "updatedBy", event.getUpdatedBy(), "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process compensation status updates event: {}", e.getMessage(), e);

            kafkaTemplate.send("compliance-compensation-status-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleCompensationStatusUpdatesEventFallback(
            CompensationStatusUpdatesEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("comp-status-fallback-%s-p%d-o%d", event.getRequestId(), partition, offset);

        log.error("Circuit breaker fallback triggered for compensation status updates: requestId={}, error={}",
            event.getRequestId(), ex.getMessage());

        kafkaTemplate.send("compensation-status-updates-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        try {
            notificationService.sendOperationalAlert(
                "Compensation Status Updates Circuit Breaker Triggered",
                String.format("Compensation status update %s failed: %s", event.getRequestId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltCompensationStatusUpdatesEvent(
            @Payload CompensationStatusUpdatesEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-comp-status-%s-%d", event.getRequestId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Compensation status updates permanently failed: requestId={}, topic={}, error={}",
            event.getRequestId(), topic, exceptionMessage);

        auditService.logComplianceEvent("COMPENSATION_STATUS_UPDATES_DLT_EVENT", event.getRequestId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        try {
            notificationService.sendCriticalAlert(
                "Compensation Status Updates Dead Letter Event",
                String.format("Compensation status update %s sent to DLT: %s", event.getRequestId(), exceptionMessage),
                Map.of("requestId", event.getRequestId(), "topic", topic, "correlationId", correlationId)
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

    private void processStatusChanged(CompensationStatusUpdatesEvent event, String correlationId) {
        CompensationStatus status = CompensationStatus.builder()
            .requestId(event.getRequestId())
            .oldStatus(event.getOldStatus())
            .newStatus(event.getNewStatus())
            .updatedBy(event.getUpdatedBy())
            .updatedAt(LocalDateTime.now())
            .statusReason(event.getStatusReason())
            .correlationId(correlationId)
            .build();
        compensationStatusRepository.save(status);

        compensationService.updateStatus(event.getRequestId(), event.getNewStatus(), event.getStatusReason());

        kafkaTemplate.send("compliance-dashboard", Map.of(
            "eventType", "COMPENSATION_STATUS_CHANGED",
            "requestId", event.getRequestId(),
            "oldStatus", event.getOldStatus(),
            "newStatus", event.getNewStatus(),
            "updatedBy", event.getUpdatedBy(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Notify customer of important status changes
        if (isCustomerNotifiableStatus(event.getNewStatus())) {
            notificationService.sendNotification(event.getCustomerId(), "Compensation Request Status Updated",
                String.format("Your compensation request status has been updated to: %s", event.getNewStatus()),
                correlationId);
        }

        metricsService.recordCompensationStatusChanged(event.getOldStatus(), event.getNewStatus());

        log.info("Compensation status changed: requestId={}, from={}, to={}",
            event.getRequestId(), event.getOldStatus(), event.getNewStatus());
    }

    private void processReviewStatusUpdated(CompensationStatusUpdatesEvent event, String correlationId) {
        compensationService.updateReviewStatus(event.getRequestId(), event.getReviewStatus(), event.getReviewNotes());

        kafkaTemplate.send("compliance-dashboard", Map.of(
            "eventType", "COMPENSATION_REVIEW_STATUS_UPDATED",
            "requestId", event.getRequestId(),
            "reviewStatus", event.getReviewStatus(),
            "reviewedBy", event.getUpdatedBy(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        if ("REVIEW_FAILED".equals(event.getReviewStatus()) || "ADDITIONAL_INFO_REQUIRED".equals(event.getReviewStatus())) {
            kafkaTemplate.send("compliance-alerts", Map.of(
                "alertType", "COMPENSATION_REVIEW_ISSUE",
                "requestId", event.getRequestId(),
                "reviewStatus", event.getReviewStatus(),
                "priority", "MEDIUM",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordCompensationReviewStatusUpdated(event.getReviewStatus());

        log.info("Compensation review status updated: requestId={}, reviewStatus={}",
            event.getRequestId(), event.getReviewStatus());
    }

    private void processPaymentStatusUpdated(CompensationStatusUpdatesEvent event, String correlationId) {
        compensationService.updatePaymentStatus(event.getRequestId(), event.getPaymentStatus(), event.getPaymentReference());

        kafkaTemplate.send("compliance-dashboard", Map.of(
            "eventType", "COMPENSATION_PAYMENT_STATUS_UPDATED",
            "requestId", event.getRequestId(),
            "paymentStatus", event.getPaymentStatus(),
            "paymentReference", event.getPaymentReference(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        if ("PAYMENT_FAILED".equals(event.getPaymentStatus())) {
            kafkaTemplate.send("compliance-alerts", Map.of(
                "alertType", "COMPENSATION_PAYMENT_FAILED",
                "requestId", event.getRequestId(),
                "paymentReference", event.getPaymentReference(),
                "priority", "HIGH",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            notificationService.sendOperationalAlert("Compensation Payment Failed",
                String.format("Compensation payment failed for request %s. Reference: %s",
                    event.getRequestId(), event.getPaymentReference()),
                "HIGH");
        } else if ("PAYMENT_COMPLETED".equals(event.getPaymentStatus())) {
            notificationService.sendNotification(event.getCustomerId(), "Compensation Payment Completed",
                String.format("Your compensation payment has been completed. Reference: %s", event.getPaymentReference()),
                correlationId);
        }

        metricsService.recordCompensationPaymentStatusUpdated(event.getPaymentStatus());

        log.info("Compensation payment status updated: requestId={}, paymentStatus={}",
            event.getRequestId(), event.getPaymentStatus());
    }

    private void processApprovalStatusUpdated(CompensationStatusUpdatesEvent event, String correlationId) {
        compensationService.updateApprovalStatus(event.getRequestId(), event.getApprovalStatus(), event.getApprovalNotes());

        kafkaTemplate.send("compliance-dashboard", Map.of(
            "eventType", "COMPENSATION_APPROVAL_STATUS_UPDATED",
            "requestId", event.getRequestId(),
            "approvalStatus", event.getApprovalStatus(),
            "approvedBy", event.getUpdatedBy(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        if ("REQUIRES_SENIOR_APPROVAL".equals(event.getApprovalStatus())) {
            kafkaTemplate.send("compliance-alerts", Map.of(
                "alertType", "COMPENSATION_SENIOR_APPROVAL_REQUIRED",
                "requestId", event.getRequestId(),
                "amount", event.getAmount(),
                "priority", "HIGH",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            notificationService.sendOperationalAlert("Senior Approval Required",
                String.format("Compensation request %s requires senior approval (Amount: %s)",
                    event.getRequestId(), event.getAmount()),
                "HIGH");
        }

        metricsService.recordCompensationApprovalStatusUpdated(event.getApprovalStatus());

        log.info("Compensation approval status updated: requestId={}, approvalStatus={}",
            event.getRequestId(), event.getApprovalStatus());
    }

    private void processEscalationStatusUpdated(CompensationStatusUpdatesEvent event, String correlationId) {
        compensationService.updateEscalationStatus(event.getRequestId(), event.getEscalationStatus(), event.getEscalationReason());

        kafkaTemplate.send("compliance-dashboard", Map.of(
            "eventType", "COMPENSATION_ESCALATION_STATUS_UPDATED",
            "requestId", event.getRequestId(),
            "escalationStatus", event.getEscalationStatus(),
            "escalationReason", event.getEscalationReason(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        if ("ESCALATED_TO_LEGAL".equals(event.getEscalationStatus()) || "ESCALATED_TO_EXECUTIVE".equals(event.getEscalationStatus())) {
            kafkaTemplate.send("compliance-alerts", Map.of(
                "alertType", "COMPENSATION_HIGH_LEVEL_ESCALATION",
                "requestId", event.getRequestId(),
                "escalationStatus", event.getEscalationStatus(),
                "escalationReason", event.getEscalationReason(),
                "priority", "CRITICAL",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            notificationService.sendCriticalAlert("High-Level Compensation Escalation",
                String.format("Compensation request %s escalated to %s. Reason: %s",
                    event.getRequestId(), event.getEscalationStatus(), event.getEscalationReason()),
                Map.of("requestId", event.getRequestId(), "escalationLevel", event.getEscalationStatus()));
        }

        metricsService.recordCompensationEscalationStatusUpdated(event.getEscalationStatus());

        log.warn("Compensation escalation status updated: requestId={}, escalationStatus={}",
            event.getRequestId(), event.getEscalationStatus());
    }

    private boolean isCustomerNotifiableStatus(String status) {
        return Arrays.asList("APPROVED", "REJECTED", "PAID", "PAYMENT_FAILED", "UNDER_REVIEW", "ADDITIONAL_INFO_REQUIRED")
            .contains(status);
    }
}