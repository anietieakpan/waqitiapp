package com.waqiti.insurance.kafka;

import com.waqiti.common.events.InsurancePolicyCancellationEvent;
import com.waqiti.insurance.domain.PolicyCancellation;
import com.waqiti.insurance.repository.PolicyCancellationRepository;
import com.waqiti.insurance.repository.PolicyRepository;
import com.waqiti.insurance.service.PolicyCancellationService;
import com.waqiti.insurance.service.RefundCalculationService;
import com.waqiti.insurance.metrics.InsuranceMetricsService;
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
public class InsurancePolicyCancellationEventsConsumer {

    private final PolicyCancellationRepository policyCancellationRepository;
    private final PolicyRepository policyRepository;
    private final PolicyCancellationService policyCancellationService;
    private final RefundCalculationService refundCalculationService;
    private final InsuranceMetricsService metricsService;
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
        successCounter = Counter.builder("insurance_policy_cancellation_processed_total")
            .description("Total number of successfully processed insurance policy cancellation events")
            .register(meterRegistry);
        errorCounter = Counter.builder("insurance_policy_cancellation_errors_total")
            .description("Total number of insurance policy cancellation processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("insurance_policy_cancellation_processing_duration")
            .description("Time taken to process insurance policy cancellation events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"insurance-policy-cancellation-events", "policy-termination-workflow", "policy-lapse-requests"},
        groupId = "insurance-policy-cancellation-service-group",
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
    @CircuitBreaker(name = "insurance-policy-cancellation", fallbackMethod = "handleInsurancePolicyCancellationEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleInsurancePolicyCancellationEvent(
            @Payload InsurancePolicyCancellationEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("policy-cancellation-%s-p%d-o%d", event.getPolicyId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getPolicyId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing policy cancellation event: policyId={}, reason={}, effectiveDate={}",
                event.getPolicyId(), event.getCancellationReason(), event.getEffectiveDate());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getEventType()) {
                case CANCELLATION_REQUESTED:
                    processCancellationRequest(event, correlationId);
                    break;

                case CANCELLATION_REVIEW:
                    reviewCancellationRequest(event, correlationId);
                    break;

                case REFUND_CALCULATION:
                    calculateRefund(event, correlationId);
                    break;

                case UNDERWRITER_APPROVAL:
                    processUnderwriterApproval(event, correlationId);
                    break;

                case POLICY_TERMINATED:
                    terminatePolicy(event, correlationId);
                    break;

                case REFUND_PROCESSED:
                    processRefund(event, correlationId);
                    break;

                case REINSTATEMENT_REQUESTED:
                    processReinstatementRequest(event, correlationId);
                    break;

                case LAPSE_INITIATED:
                    initiateLapse(event, correlationId);
                    break;

                case CANCELLATION_REJECTED:
                    rejectCancellation(event, correlationId);
                    break;

                default:
                    log.warn("Unknown policy cancellation event type: {}", event.getEventType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logAccountEvent("INSURANCE_POLICY_CANCELLATION_EVENT_PROCESSED", event.getPolicyId(),
                Map.of("eventType", event.getEventType(), "cancellationReason", event.getCancellationReason(),
                    "effectiveDate", event.getEffectiveDate(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process policy cancellation event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("insurance-policy-cancellation-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleInsurancePolicyCancellationEventFallback(
            InsurancePolicyCancellationEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("policy-cancellation-fallback-%s-p%d-o%d", event.getPolicyId(), partition, offset);

        log.error("Circuit breaker fallback triggered for policy cancellation: policyId={}, error={}",
            event.getPolicyId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("insurance-policy-cancellation-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Insurance Policy Cancellation Circuit Breaker Triggered",
                String.format("Policy %s cancellation failed: %s", event.getPolicyId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltInsurancePolicyCancellationEvent(
            @Payload InsurancePolicyCancellationEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-policy-cancellation-%s-%d", event.getPolicyId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Policy cancellation permanently failed: policyId={}, topic={}, error={}",
            event.getPolicyId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logAccountEvent("INSURANCE_POLICY_CANCELLATION_DLT_EVENT", event.getPolicyId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Insurance Policy Cancellation Dead Letter Event",
                String.format("Policy %s cancellation sent to DLT: %s", event.getPolicyId(), exceptionMessage),
                Map.of("policyId", event.getPolicyId(), "topic", topic, "correlationId", correlationId)
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

    private void processCancellationRequest(InsurancePolicyCancellationEvent event, String correlationId) {
        PolicyCancellation cancellation = PolicyCancellation.builder()
            .policyId(event.getPolicyId())
            .policyHolderId(event.getPolicyHolderId())
            .cancellationReason(event.getCancellationReason())
            .requestedBy(event.getRequestedBy())
            .status("PENDING_REVIEW")
            .requestedAt(LocalDateTime.now())
            .effectiveDate(event.getEffectiveDate())
            .correlationId(correlationId)
            .build();
        policyCancellationRepository.save(cancellation);

        policyCancellationService.validateCancellationEligibility(event.getPolicyId());
        policyCancellationService.checkCancellationRestrictions(event.getPolicyId());

        notificationService.sendNotification(event.getPolicyHolderId(), "Policy Cancellation Request Received",
            "We have received your policy cancellation request and will review it within 2 business days.",
            correlationId);

        kafkaTemplate.send("policy-termination-workflow", Map.of(
            "policyId", event.getPolicyId(),
            "eventType", "CANCELLATION_REVIEW",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordCancellationRequested(event.getCancellationReason());

        log.info("Policy cancellation requested: policyId={}, reason={}",
            event.getPolicyId(), event.getCancellationReason());
    }

    private void reviewCancellationRequest(InsurancePolicyCancellationEvent event, String correlationId) {
        PolicyCancellation cancellation = policyCancellationRepository.findByPolicyId(event.getPolicyId())
            .orElseThrow(() -> new RuntimeException("Policy cancellation not found"));

        cancellation.setStatus("UNDER_REVIEW");
        cancellation.setReviewStartedAt(LocalDateTime.now());
        cancellation.setReviewedBy(event.getReviewedBy());
        policyCancellationRepository.save(cancellation);

        policyCancellationService.performComplianceChecks(event.getPolicyId());
        policyCancellationService.checkOutstandingPremiums(event.getPolicyId());
        policyCancellationService.verifyNoActiveClaims(event.getPolicyId());

        kafkaTemplate.send("policy-termination-workflow", Map.of(
            "policyId", event.getPolicyId(),
            "eventType", "REFUND_CALCULATION",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordCancellationReviewInitiated();

        log.info("Policy cancellation review initiated: policyId={}, reviewedBy={}",
            event.getPolicyId(), event.getReviewedBy());
    }

    private void calculateRefund(InsurancePolicyCancellationEvent event, String correlationId) {
        PolicyCancellation cancellation = policyCancellationRepository.findByPolicyId(event.getPolicyId())
            .orElseThrow(() -> new RuntimeException("Policy cancellation not found"));

        cancellation.setStatus("REFUND_CALCULATION");
        cancellation.setRefundCalculationStartedAt(LocalDateTime.now());
        policyCancellationRepository.save(cancellation);

        var refundAmount = refundCalculationService.calculateRefund(event.getPolicyId(), event.getEffectiveDate());
        var penalties = refundCalculationService.calculateCancellationPenalties(event.getPolicyId());

        cancellation.setRefundAmount(refundAmount);
        cancellation.setPenaltyAmount(penalties);
        cancellation.setNetRefund(refundAmount.subtract(penalties));
        policyCancellationRepository.save(cancellation);

        if (refundAmount.compareTo(java.math.BigDecimal.ZERO) > 0) {
            kafkaTemplate.send("policy-termination-workflow", Map.of(
                "policyId", event.getPolicyId(),
                "eventType", "UNDERWRITER_APPROVAL",
                "refundAmount", refundAmount,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        } else {
            kafkaTemplate.send("policy-termination-workflow", Map.of(
                "policyId", event.getPolicyId(),
                "eventType", "POLICY_TERMINATED",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordRefundCalculated();

        log.info("Refund calculated: policyId={}, refund={}, penalty={}",
            event.getPolicyId(), refundAmount, penalties);
    }

    private void processUnderwriterApproval(InsurancePolicyCancellationEvent event, String correlationId) {
        PolicyCancellation cancellation = policyCancellationRepository.findByPolicyId(event.getPolicyId())
            .orElseThrow(() -> new RuntimeException("Policy cancellation not found"));

        cancellation.setStatus("UNDERWRITER_APPROVAL");
        cancellation.setUnderwriterApprovalRequestedAt(LocalDateTime.now());
        policyCancellationRepository.save(cancellation);

        boolean approved = policyCancellationService.requestUnderwriterApproval(event.getPolicyId(),
            cancellation.getRefundAmount());

        if (approved) {
            cancellation.setUnderwriterApproved(true);
            cancellation.setUnderwriterApprovedAt(LocalDateTime.now());
            policyCancellationRepository.save(cancellation);

            kafkaTemplate.send("policy-termination-workflow", Map.of(
                "policyId", event.getPolicyId(),
                "eventType", "POLICY_TERMINATED",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        } else {
            kafkaTemplate.send("policy-termination-workflow", Map.of(
                "policyId", event.getPolicyId(),
                "eventType", "CANCELLATION_REJECTED",
                "rejectionReason", "UNDERWRITER_DECLINED",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordUnderwriterApproval(approved);

        log.info("Underwriter approval processed: policyId={}, approved={}",
            event.getPolicyId(), approved);
    }

    private void terminatePolicy(InsurancePolicyCancellationEvent event, String correlationId) {
        PolicyCancellation cancellation = policyCancellationRepository.findByPolicyId(event.getPolicyId())
            .orElseThrow(() -> new RuntimeException("Policy cancellation not found"));

        cancellation.setStatus("TERMINATED");
        cancellation.setTerminatedAt(LocalDateTime.now());
        policyCancellationRepository.save(cancellation);

        var policy = policyRepository.findById(event.getPolicyId()).orElseThrow();
        policy.setStatus("TERMINATED");
        policy.setTerminationDate(event.getEffectiveDate());
        policy.setTerminationReason(event.getCancellationReason());
        policyRepository.save(policy);

        policyCancellationService.terminatePolicy(event.getPolicyId(), event.getEffectiveDate());
        policyCancellationService.cancelAutomaticPayments(event.getPolicyId());

        if (cancellation.getNetRefund().compareTo(java.math.BigDecimal.ZERO) > 0) {
            kafkaTemplate.send("policy-termination-workflow", Map.of(
                "policyId", event.getPolicyId(),
                "eventType", "REFUND_PROCESSED",
                "refundAmount", cancellation.getNetRefund(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        notificationService.sendNotification(event.getPolicyHolderId(), "Policy Terminated",
            "Your insurance policy has been terminated as requested.",
            correlationId);

        kafkaTemplate.send("insurance-lifecycle-events", Map.of(
            "policyId", event.getPolicyId(),
            "policyHolderId", event.getPolicyHolderId(),
            "eventType", "POLICY_TERMINATED",
            "terminationReason", event.getCancellationReason(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordPolicyTerminated(event.getCancellationReason());

        log.info("Policy terminated: policyId={}, reason={}",
            event.getPolicyId(), event.getCancellationReason());
    }

    private void processRefund(InsurancePolicyCancellationEvent event, String correlationId) {
        PolicyCancellation cancellation = policyCancellationRepository.findByPolicyId(event.getPolicyId())
            .orElseThrow(() -> new RuntimeException("Policy cancellation not found"));

        cancellation.setStatus("REFUND_PROCESSING");
        cancellation.setRefundProcessedAt(LocalDateTime.now());
        policyCancellationRepository.save(cancellation);

        String refundId = refundCalculationService.processRefund(event.getPolicyId(),
            cancellation.getNetRefund());

        notificationService.sendNotification(event.getPolicyHolderId(), "Refund Processing",
            "Your policy cancellation refund is being processed and will be sent to your account.",
            correlationId);

        kafkaTemplate.send("payment-processing-events", Map.of(
            "refundId", refundId,
            "policyId", event.getPolicyId(),
            "amount", cancellation.getNetRefund(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordRefundProcessed();

        log.info("Refund processed: policyId={}, refundId={}, amount={}",
            event.getPolicyId(), refundId, cancellation.getNetRefund());
    }

    private void processReinstatementRequest(InsurancePolicyCancellationEvent event, String correlationId) {
        PolicyCancellation cancellation = policyCancellationRepository.findByPolicyId(event.getPolicyId())
            .orElseThrow(() -> new RuntimeException("Policy cancellation not found"));

        if ("TERMINATED".equals(cancellation.getStatus())) {
            boolean eligible = policyCancellationService.checkReinstatementEligibility(event.getPolicyId());

            if (eligible) {
                cancellation.setStatus("REINSTATEMENT_PENDING");
                cancellation.setReinstatementRequestedAt(LocalDateTime.now());
                policyCancellationRepository.save(cancellation);

                policyCancellationService.initiateReinstatement(event.getPolicyId());

                notificationService.sendNotification(event.getPolicyHolderId(), "Reinstatement Request Received",
                    "We have received your policy reinstatement request and are reviewing it.",
                    correlationId);
            } else {
                notificationService.sendNotification(event.getPolicyHolderId(), "Reinstatement Not Eligible",
                    "Your policy is not eligible for reinstatement at this time.",
                    correlationId);
            }

            metricsService.recordReinstatementRequested(eligible);
        }

        log.info("Reinstatement request processed: policyId={}", event.getPolicyId());
    }

    private void initiateLapse(InsurancePolicyCancellationEvent event, String correlationId) {
        PolicyCancellation cancellation = PolicyCancellation.builder()
            .policyId(event.getPolicyId())
            .policyHolderId(event.getPolicyHolderId())
            .cancellationReason("LAPSE_NON_PAYMENT")
            .status("LAPSED")
            .lapseInitiatedAt(LocalDateTime.now())
            .effectiveDate(event.getEffectiveDate())
            .correlationId(correlationId)
            .build();
        policyCancellationRepository.save(cancellation);

        var policy = policyRepository.findById(event.getPolicyId()).orElseThrow();
        policy.setStatus("LAPSED");
        policy.setLapseDate(event.getEffectiveDate());
        policyRepository.save(policy);

        policyCancellationService.processLapse(event.getPolicyId());

        notificationService.sendNotification(event.getPolicyHolderId(), "Policy Lapsed",
            "Your insurance policy has lapsed due to non-payment. Contact us to discuss reinstatement options.",
            correlationId);

        metricsService.recordPolicyLapsed();

        log.info("Policy lapsed: policyId={}", event.getPolicyId());
    }

    private void rejectCancellation(InsurancePolicyCancellationEvent event, String correlationId) {
        PolicyCancellation cancellation = policyCancellationRepository.findByPolicyId(event.getPolicyId())
            .orElseThrow(() -> new RuntimeException("Policy cancellation not found"));

        cancellation.setStatus("REJECTED");
        cancellation.setRejectedAt(LocalDateTime.now());
        cancellation.setRejectionReason(event.getRejectionReason());
        policyCancellationRepository.save(cancellation);

        notificationService.sendNotification(event.getPolicyHolderId(), "Policy Cancellation Request Denied",
            String.format("Your policy cancellation request was denied. Reason: %s", event.getRejectionReason()),
            correlationId);

        metricsService.recordCancellationRejected(event.getRejectionReason());

        log.warn("Policy cancellation rejected: policyId={}, reason={}",
            event.getPolicyId(), event.getRejectionReason());
    }
}