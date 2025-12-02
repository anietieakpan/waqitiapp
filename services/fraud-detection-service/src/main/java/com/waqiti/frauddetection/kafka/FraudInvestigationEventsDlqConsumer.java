package com.waqiti.frauddetection.kafka;

import com.waqiti.common.events.FraudInvestigationEvent;
import com.waqiti.frauddetection.domain.FraudInvestigationCase;
import com.waqiti.frauddetection.repository.FraudInvestigationCaseRepository;
import com.waqiti.frauddetection.service.FraudInvestigationService;
import com.waqiti.frauddetection.service.FraudEscalationService;
import com.waqiti.frauddetection.metrics.FraudMetricsService;
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
public class FraudInvestigationEventsDlqConsumer {

    private final FraudInvestigationCaseRepository investigationCaseRepository;
    private final FraudInvestigationService investigationService;
    private final FraudEscalationService escalationService;
    private final FraudMetricsService metricsService;
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
    private Counter criticalInvestigationCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("fraud_investigation_dlq_processed_total")
            .description("Total number of successfully processed fraud investigation DLQ events")
            .register(meterRegistry);
        errorCounter = Counter.builder("fraud_investigation_dlq_errors_total")
            .description("Total number of fraud investigation DLQ processing errors")
            .register(meterRegistry);
        criticalInvestigationCounter = Counter.builder("fraud_investigations_critical_total")
            .description("Total number of critical fraud investigations requiring escalation")
            .register(meterRegistry);
        processingTimer = Timer.builder("fraud_investigation_dlq_processing_duration")
            .description("Time taken to process fraud investigation DLQ events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"fraud-investigation-events-dlq", "fraud-case-management-dlq", "investigation-alerts-dlq"},
        groupId = "fraud-investigation-dlq-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "fraud-investigation-dlq", fallbackMethod = "handleFraudInvestigationDlqEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleFraudInvestigationDlqEvent(
            @Payload FraudInvestigationEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("investigation-dlq-%s-p%d-o%d", event.getCaseId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getCaseId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Fraud investigation DLQ event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.error("Processing CRITICAL fraud investigation from DLQ: caseId={}, type={}, priority={}, topic={}",
                event.getCaseId(), event.getEventType(), event.getPriority(), topic);

            // Clean expired entries periodically
            cleanExpiredEntries();

            // DLQ investigation events indicate critical fraud case management failures
            if (isCriticalInvestigation(event)) {
                criticalInvestigationCounter.increment();
                escalateCriticalInvestigation(event, correlationId, topic);
            }

            switch (event.getEventType()) {
                case CASE_CREATED:
                    processCaseCreatedDlq(event, correlationId, topic);
                    break;

                case CASE_ASSIGNED:
                    processCaseAssignedDlq(event, correlationId, topic);
                    break;

                case EVIDENCE_COLLECTED:
                    processEvidenceCollectedDlq(event, correlationId, topic);
                    break;

                case CASE_ESCALATED:
                    processCaseEscalatedDlq(event, correlationId, topic);
                    break;

                case CASE_CLOSED:
                    processCaseClosedDlq(event, correlationId, topic);
                    break;

                case URGENT_REVIEW:
                    processUrgentReviewDlq(event, correlationId, topic);
                    break;

                case LAW_ENFORCEMENT_REFERRAL:
                    processLawEnforcementReferralDlq(event, correlationId, topic);
                    break;

                case REGULATORY_REPORTING:
                    processRegulatoryReportingDlq(event, correlationId, topic);
                    break;

                default:
                    processGenericInvestigationDlq(event, correlationId, topic);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logFraudEvent("FRAUD_INVESTIGATION_DLQ_PROCESSED", event.getCaseId(),
                Map.of("eventType", event.getEventType(), "priority", event.getPriority(),
                    "investigatorId", event.getInvestigatorId(), "correlationId", correlationId,
                    "dlqTopic", topic, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process fraud investigation DLQ event: {}", e.getMessage(), e);

            // Send to fraud investigation escalation
            sendInvestigationEscalation(event, correlationId, topic, e);

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleFraudInvestigationDlqEventFallback(
            FraudInvestigationEvent event,
            int partition,
            long offset,
            String topic,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("investigation-dlq-fallback-%s-p%d-o%d", event.getCaseId(), partition, offset);

        log.error("Circuit breaker fallback triggered for fraud investigation DLQ: caseId={}, topic={}, error={}",
            event.getCaseId(), topic, ex.getMessage());

        sendInvestigationEscalation(event, correlationId, topic, ex);
        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltFraudInvestigationEvent(
            @Payload FraudInvestigationEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-investigation-%s-%d", event.getCaseId(), System.currentTimeMillis());

        log.error("CRITICAL: Fraud investigation DLQ permanently failed - caseId={}, topic={}, error={}",
            event.getCaseId(), topic, exceptionMessage);

        auditService.logFraudEvent("FRAUD_INVESTIGATION_DLQ_DLT_EVENT", event.getCaseId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        sendInvestigationEscalation(event, correlationId, topic, new RuntimeException(exceptionMessage));
    }

    private boolean isCriticalInvestigation(FraudInvestigationEvent event) {
        return "CRITICAL".equals(event.getPriority()) ||
               "URGENT".equals(event.getPriority()) ||
               Arrays.asList("LAW_ENFORCEMENT_REFERRAL", "REGULATORY_REPORTING", "URGENT_REVIEW").contains(event.getEventType().toString()) ||
               event.getFinancialImpact() > 50000.0;
    }

    private void processCaseCreatedDlq(FraudInvestigationEvent event, String correlationId, String topic) {
        FraudInvestigationCase investigationCase = FraudInvestigationCase.builder()
            .caseId(event.getCaseId())
            .caseType("FRAUD_INVESTIGATION_DLQ")
            .priority(event.getPriority())
            .description(String.format("Fraud investigation case from DLQ: %s", event.getDescription()))
            .investigatorId(event.getInvestigatorId())
            .financialImpact(event.getFinancialImpact())
            .correlationId(correlationId)
            .source(topic)
            .status("REQUIRES_IMMEDIATE_ASSIGNMENT")
            .createdAt(LocalDateTime.now())
            .build();
        investigationCaseRepository.save(investigationCase);

        investigationService.assignHighPriorityInvestigator(event.getCaseId(), "DLQ_CASE_CREATION");
        escalationService.escalateCaseCreation(event, correlationId);

        // Investigation team notification
        notificationService.sendInvestigationAlert(
            "CRITICAL: Fraud Investigation Case from DLQ",
            String.format("Fraud investigation case %s created from DLQ - immediate assignment required", event.getCaseId()),
            "CRITICAL"
        );

        log.error("Fraud investigation case created DLQ processed: caseId={}", event.getCaseId());
    }

    private void processCaseAssignedDlq(FraudInvestigationEvent event, String correlationId, String topic) {
        investigationService.recordCaseAssignment(event.getCaseId(), event.getInvestigatorId(), "DLQ_SOURCE");
        escalationService.escalateCaseAssignment(event, correlationId);

        log.error("Fraud investigation case assignment DLQ processed: caseId={}, investigator={}",
            event.getCaseId(), event.getInvestigatorId());
    }

    private void processEvidenceCollectedDlq(FraudInvestigationEvent event, String correlationId, String topic) {
        investigationService.recordEvidenceCollection(event.getCaseId(), event.getDescription());
        escalationService.escalateEvidenceCollection(event, correlationId);

        log.error("Fraud investigation evidence collection DLQ processed: caseId={}", event.getCaseId());
    }

    private void processCaseEscalatedDlq(FraudInvestigationEvent event, String correlationId, String topic) {
        investigationService.recordCaseEscalation(event.getCaseId(), event.getDescription());
        escalationService.escalateCaseEscalation(event, correlationId);

        // Senior investigation team notification
        notificationService.sendInvestigationAlert(
            "Case Escalation from DLQ",
            String.format("Investigation case %s escalated from DLQ - senior review required", event.getCaseId()),
            "HIGH"
        );

        log.error("Fraud investigation case escalation DLQ processed: caseId={}", event.getCaseId());
    }

    private void processCaseClosedDlq(FraudInvestigationEvent event, String correlationId, String topic) {
        investigationService.recordCaseClosure(event.getCaseId(), event.getDescription());
        escalationService.escalateCaseClosure(event, correlationId);

        log.error("Fraud investigation case closure DLQ processed: caseId={}", event.getCaseId());
    }

    private void processUrgentReviewDlq(FraudInvestigationEvent event, String correlationId, String topic) {
        investigationService.recordUrgentReview(event.getCaseId(), event.getDescription());
        investigationService.assignSeniorInvestigator(event.getCaseId(), "DLQ_URGENT_REVIEW");
        escalationService.escalateUrgentReview(event, correlationId);

        // Management notification for urgent reviews
        notificationService.sendManagementAlert(
            "URGENT: Investigation Review from DLQ",
            String.format("Urgent investigation review for case %s from DLQ", event.getCaseId()),
            "URGENT"
        );

        log.error("Fraud investigation urgent review DLQ processed: caseId={}", event.getCaseId());
    }

    private void processLawEnforcementReferralDlq(FraudInvestigationEvent event, String correlationId, String topic) {
        investigationService.recordLawEnforcementReferral(event.getCaseId(), event.getDescription());
        escalationService.escalateLawEnforcementReferral(event, correlationId);

        // Legal team and executive notification
        notificationService.sendLegalAlert(
            "CRITICAL: Law Enforcement Referral from DLQ",
            String.format("Law enforcement referral for case %s from DLQ - legal review required", event.getCaseId()),
            "CRITICAL"
        );

        notificationService.sendExecutiveAlert(
            "CRITICAL: Law Enforcement Referral Required",
            String.format("Investigation case %s requires law enforcement referral", event.getCaseId()),
            Map.of("caseId", event.getCaseId(), "correlationId", correlationId)
        );

        log.error("Fraud investigation law enforcement referral DLQ processed: caseId={}", event.getCaseId());
    }

    private void processRegulatoryReportingDlq(FraudInvestigationEvent event, String correlationId, String topic) {
        investigationService.recordRegulatoryReporting(event.getCaseId(), event.getDescription());
        escalationService.escalateRegulatoryReporting(event, correlationId);

        // Compliance team notification
        notificationService.sendComplianceAlert(
            "CRITICAL: Regulatory Reporting from DLQ",
            String.format("Regulatory reporting required for case %s from DLQ", event.getCaseId()),
            "CRITICAL"
        );

        log.error("Fraud investigation regulatory reporting DLQ processed: caseId={}", event.getCaseId());
    }

    private void processGenericInvestigationDlq(FraudInvestigationEvent event, String correlationId, String topic) {
        investigationService.recordGenericInvestigation(event.getCaseId(), event.getDescription(), "DLQ_GENERIC");
        escalationService.escalateGenericInvestigation(event, correlationId);

        log.warn("Generic fraud investigation DLQ processed: caseId={}, type={}",
            event.getCaseId(), event.getEventType());
    }

    private void escalateCriticalInvestigation(FraudInvestigationEvent event, String correlationId, String topic) {
        try {
            notificationService.sendInvestigationAlert(
                "CRITICAL: Fraud Investigation from DLQ Requires Immediate Action",
                String.format("Critical fraud investigation case %s from DLQ topic %s requires immediate attention. " +
                    "Type: %s, Priority: %s, Financial Impact: $%.2f",
                    event.getCaseId(), topic, event.getEventType(), event.getPriority(), event.getFinancialImpact()),
                Map.of(
                    "caseId", event.getCaseId(),
                    "correlationId", correlationId,
                    "dlqTopic", topic,
                    "eventType", event.getEventType(),
                    "priority", event.getPriority(),
                    "financialImpact", event.getFinancialImpact(),
                    "priority", "CRITICAL_INVESTIGATION"
                )
            );
        } catch (Exception ex) {
            log.error("Failed to send critical investigation escalation: {}", ex.getMessage());
        }
    }

    private void sendInvestigationEscalation(FraudInvestigationEvent event, String correlationId, String topic, Exception ex) {
        try {
            notificationService.sendInvestigationAlert(
                "SYSTEM CRITICAL: Fraud Investigation DLQ Processing Failure",
                String.format("CRITICAL SYSTEM FAILURE: Unable to process fraud investigation from DLQ for case %s. " +
                    "This indicates a serious investigation system failure requiring immediate intervention. " +
                    "Topic: %s, Error: %s", event.getCaseId(), topic, ex.getMessage()),
                Map.of(
                    "caseId", event.getCaseId(),
                    "correlationId", correlationId,
                    "dlqTopic", topic,
                    "errorMessage", ex.getMessage(),
                    "priority", "INVESTIGATION_SYSTEM_CRITICAL_FAILURE"
                )
            );
        } catch (Exception notificationEx) {
            log.error("CRITICAL: Failed to send investigation escalation for DLQ failure: {}", notificationEx.getMessage());
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
}