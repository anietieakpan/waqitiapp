package com.waqiti.compliance.kafka;

import com.waqiti.common.events.CompensationRequestsEvent;
import com.waqiti.compliance.domain.CompensationRequest;
import com.waqiti.compliance.repository.CompensationRequestRepository;
import com.waqiti.compliance.service.CompensationService;
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
public class CompensationRequestsConsumer {

    private final CompensationRequestRepository compensationRequestRepository;
    private final CompensationService compensationService;
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
        successCounter = Counter.builder("compensation_requests_processed_total")
            .description("Total number of successfully processed compensation requests events")
            .register(meterRegistry);
        errorCounter = Counter.builder("compensation_requests_errors_total")
            .description("Total number of compensation requests processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("compensation_requests_processing_duration")
            .description("Time taken to process compensation requests events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"compensation-requests"},
        groupId = "compliance-compensation-requests-group",
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
    @CircuitBreaker(name = "compensation-requests", fallbackMethod = "handleCompensationRequestsEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleCompensationRequestsEvent(
            @Payload CompensationRequestsEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("compensation-%s-p%d-o%d", event.getRequestId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getRequestId(), event.getEventType(), event.getTimestamp());

        try {
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing compensation request: requestId={}, customerId={}, amount={}",
                event.getRequestId(), event.getCustomerId(), event.getAmount());

            cleanExpiredEntries();

            switch (event.getEventType()) {
                case REQUEST_SUBMITTED:
                    processRequestSubmitted(event, correlationId);
                    break;

                case REQUEST_REVIEWED:
                    processRequestReviewed(event, correlationId);
                    break;

                case REQUEST_APPROVED:
                    processRequestApproved(event, correlationId);
                    break;

                case REQUEST_REJECTED:
                    processRequestRejected(event, correlationId);
                    break;

                case COMPENSATION_PAID:
                    processCompensationPaid(event, correlationId);
                    break;

                case REQUEST_ESCALATED:
                    processRequestEscalated(event, correlationId);
                    break;

                default:
                    log.warn("Unknown compensation requests event type: {}", event.getEventType());
                    break;
            }

            markEventAsProcessed(eventKey);

            auditService.logComplianceEvent("COMPENSATION_REQUESTS_EVENT_PROCESSED", event.getRequestId(),
                Map.of("eventType", event.getEventType(), "customerId", event.getCustomerId(),
                    "amount", event.getAmount(), "correlationId", correlationId,
                    "requestType", event.getRequestType(), "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process compensation requests event: {}", e.getMessage(), e);

            kafkaTemplate.send("compliance-compensation-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleCompensationRequestsEventFallback(
            CompensationRequestsEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("compensation-fallback-%s-p%d-o%d", event.getRequestId(), partition, offset);

        log.error("Circuit breaker fallback triggered for compensation requests: requestId={}, error={}",
            event.getRequestId(), ex.getMessage());

        kafkaTemplate.send("compensation-requests-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        try {
            notificationService.sendOperationalAlert(
                "Compensation Requests Circuit Breaker Triggered",
                String.format("Compensation request %s failed: %s", event.getRequestId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltCompensationRequestsEvent(
            @Payload CompensationRequestsEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-compensation-%s-%d", event.getRequestId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Compensation requests permanently failed: requestId={}, topic={}, error={}",
            event.getRequestId(), topic, exceptionMessage);

        auditService.logComplianceEvent("COMPENSATION_REQUESTS_DLT_EVENT", event.getRequestId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        try {
            notificationService.sendCriticalAlert(
                "Compensation Requests Dead Letter Event",
                String.format("Compensation request %s sent to DLT: %s", event.getRequestId(), exceptionMessage),
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

    private void processRequestSubmitted(CompensationRequestsEvent event, String correlationId) {
        CompensationRequest request = CompensationRequest.builder()
            .requestId(event.getRequestId())
            .customerId(event.getCustomerId())
            .accountId(event.getAccountId())
            .amount(event.getAmount())
            .requestType(event.getRequestType())
            .reason(event.getReason())
            .status("SUBMITTED")
            .priority(determinePriority(event.getAmount(), event.getRequestType()))
            .submittedAt(LocalDateTime.now())
            .dueDate(calculateDueDate(event.getRequestType()))
            .correlationId(correlationId)
            .build();
        compensationRequestRepository.save(request);

        compensationService.initializeRequest(event.getRequestId());

        kafkaTemplate.send("compliance-dashboard", Map.of(
            "eventType", "COMPENSATION_REQUEST_SUBMITTED",
            "requestId", event.getRequestId(),
            "customerId", event.getCustomerId(),
            "amount", event.getAmount(),
            "requestType", event.getRequestType(),
            "priority", request.getPriority(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        if (event.getAmount().compareTo(java.math.BigDecimal.valueOf(25000)) > 0) {
            kafkaTemplate.send("compliance-alerts", Map.of(
                "alertType", "HIGH_VALUE_COMPENSATION_REQUEST",
                "requestId", event.getRequestId(),
                "amount", event.getAmount(),
                "priority", "HIGH",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        kafkaTemplate.send("compensation-status-updates", Map.of(
            "eventType", "STATUS_UPDATED",
            "requestId", event.getRequestId(),
            "newStatus", "SUBMITTED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordCompensationRequestSubmitted(event.getRequestType());

        log.info("Compensation request submitted: requestId={}, amount={}, type={}",
            event.getRequestId(), event.getAmount(), event.getRequestType());
    }

    private void processRequestReviewed(CompensationRequestsEvent event, String correlationId) {
        CompensationRequest request = compensationRequestRepository.findByRequestId(event.getRequestId())
            .orElseThrow(() -> new RuntimeException("Compensation request not found"));

        request.setStatus("UNDER_REVIEW");
        request.setReviewedBy(event.getReviewedBy());
        request.setReviewStartedAt(LocalDateTime.now());
        request.setReviewNotes(event.getReviewNotes());
        compensationRequestRepository.save(request);

        compensationService.startReview(event.getRequestId(), event.getReviewedBy());

        kafkaTemplate.send("compensation-status-updates", Map.of(
            "eventType", "STATUS_UPDATED",
            "requestId", event.getRequestId(),
            "newStatus", "UNDER_REVIEW",
            "reviewedBy", event.getReviewedBy(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordCompensationRequestReviewed();

        log.info("Compensation request under review: requestId={}, reviewedBy={}",
            event.getRequestId(), event.getReviewedBy());
    }

    private void processRequestApproved(CompensationRequestsEvent event, String correlationId) {
        CompensationRequest request = compensationRequestRepository.findByRequestId(event.getRequestId())
            .orElseThrow(() -> new RuntimeException("Compensation request not found"));

        request.setStatus("APPROVED");
        request.setApprovedAt(LocalDateTime.now());
        request.setApprovedBy(event.getApprovedBy());
        request.setApprovedAmount(event.getApprovedAmount());
        request.setApprovalNotes(event.getApprovalNotes());
        compensationRequestRepository.save(request);

        compensationService.approveRequest(event.getRequestId(), event.getApprovedAmount(), event.getApprovalNotes());

        notificationService.sendNotification(event.getCustomerId(), "Compensation Request Approved",
            String.format("Your compensation request has been approved for %s", event.getApprovedAmount()),
            correlationId);

        kafkaTemplate.send("compensation-status-updates", Map.of(
            "eventType", "STATUS_UPDATED",
            "requestId", event.getRequestId(),
            "newStatus", "APPROVED",
            "approvedAmount", event.getApprovedAmount(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        kafkaTemplate.send("payment-processing", Map.of(
            "eventType", "COMPENSATION_PAYMENT_REQUESTED",
            "requestId", event.getRequestId(),
            "customerId", event.getCustomerId(),
            "amount", event.getApprovedAmount(),
            "paymentType", "COMPENSATION",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordCompensationRequestApproved(event.getApprovedAmount());

        log.info("Compensation request approved: requestId={}, amount={}",
            event.getRequestId(), event.getApprovedAmount());
    }

    private void processRequestRejected(CompensationRequestsEvent event, String correlationId) {
        CompensationRequest request = compensationRequestRepository.findByRequestId(event.getRequestId())
            .orElseThrow(() -> new RuntimeException("Compensation request not found"));

        request.setStatus("REJECTED");
        request.setRejectedAt(LocalDateTime.now());
        request.setRejectedBy(event.getRejectedBy());
        request.setRejectionReason(event.getRejectionReason());
        request.setRejectionNotes(event.getRejectionNotes());
        compensationRequestRepository.save(request);

        compensationService.rejectRequest(event.getRequestId(), event.getRejectionReason(), event.getRejectionNotes());

        notificationService.sendNotification(event.getCustomerId(), "Compensation Request Not Approved",
            String.format("Your compensation request has been rejected. Reason: %s", event.getRejectionReason()),
            correlationId);

        kafkaTemplate.send("compensation-status-updates", Map.of(
            "eventType", "STATUS_UPDATED",
            "requestId", event.getRequestId(),
            "newStatus", "REJECTED",
            "rejectionReason", event.getRejectionReason(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordCompensationRequestRejected(event.getRejectionReason());

        log.info("Compensation request rejected: requestId={}, reason={}",
            event.getRequestId(), event.getRejectionReason());
    }

    private void processCompensationPaid(CompensationRequestsEvent event, String correlationId) {
        CompensationRequest request = compensationRequestRepository.findByRequestId(event.getRequestId())
            .orElseThrow(() -> new RuntimeException("Compensation request not found"));

        request.setStatus("PAID");
        request.setPaidAt(LocalDateTime.now());
        request.setPaidAmount(event.getPaidAmount());
        request.setPaymentReference(event.getPaymentReference());
        compensationRequestRepository.save(request);

        compensationService.recordPayment(event.getRequestId(), event.getPaidAmount(), event.getPaymentReference());

        notificationService.sendNotification(event.getCustomerId(), "Compensation Payment Processed",
            String.format("Your compensation of %s has been processed. Reference: %s",
                event.getPaidAmount(), event.getPaymentReference()),
            correlationId);

        kafkaTemplate.send("compensation-status-updates", Map.of(
            "eventType", "STATUS_UPDATED",
            "requestId", event.getRequestId(),
            "newStatus", "PAID",
            "paidAmount", event.getPaidAmount(),
            "paymentReference", event.getPaymentReference(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        kafkaTemplate.send("compliance-dashboard", Map.of(
            "eventType", "COMPENSATION_PAYMENT_COMPLETED",
            "requestId", event.getRequestId(),
            "paidAmount", event.getPaidAmount(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordCompensationPaid(event.getPaidAmount());

        log.info("Compensation paid: requestId={}, amount={}, reference={}",
            event.getRequestId(), event.getPaidAmount(), event.getPaymentReference());
    }

    private void processRequestEscalated(CompensationRequestsEvent event, String correlationId) {
        CompensationRequest request = compensationRequestRepository.findByRequestId(event.getRequestId())
            .orElseThrow(() -> new RuntimeException("Compensation request not found"));

        request.setStatus("ESCALATED");
        request.setEscalatedAt(LocalDateTime.now());
        request.setEscalationReason(event.getEscalationReason());
        request.setPriority("HIGH");
        compensationRequestRepository.save(request);

        compensationService.escalateRequest(event.getRequestId(), event.getEscalationReason());

        kafkaTemplate.send("compliance-alerts", Map.of(
            "alertType", "COMPENSATION_REQUEST_ESCALATED",
            "requestId", event.getRequestId(),
            "amount", event.getAmount(),
            "escalationReason", event.getEscalationReason(),
            "priority", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Compensation Request Escalated",
            String.format("Compensation request %s escalated: %s (Amount: %s)",
                event.getRequestId(), event.getEscalationReason(), event.getAmount()),
            "HIGH");

        metricsService.recordCompensationRequestEscalated();

        log.warn("Compensation request escalated: requestId={}, reason={}",
            event.getRequestId(), event.getEscalationReason());
    }

    private String determinePriority(java.math.BigDecimal amount, String requestType) {
        if ("FRAUD_COMPENSATION".equals(requestType) || "SECURITY_BREACH".equals(requestType)) {
            return "HIGH";
        }
        
        if (amount.compareTo(java.math.BigDecimal.valueOf(50000)) > 0) {
            return "CRITICAL";
        } else if (amount.compareTo(java.math.BigDecimal.valueOf(25000)) > 0) {
            return "HIGH";
        } else if (amount.compareTo(java.math.BigDecimal.valueOf(5000)) > 0) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    private LocalDateTime calculateDueDate(String requestType) {
        switch (requestType) {
            case "FRAUD_COMPENSATION":
            case "SECURITY_BREACH":
                return LocalDateTime.now().plusDays(3);
            case "SYSTEM_ERROR":
                return LocalDateTime.now().plusDays(5);
            case "SERVICE_DISRUPTION":
                return LocalDateTime.now().plusDays(7);
            default:
                return LocalDateTime.now().plusDays(14);
        }
    }
}