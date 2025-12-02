package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseConsumer;
import com.waqiti.common.metrics.MetricsCollector;
import com.waqiti.common.monitoring.CircuitBreaker;
import com.waqiti.compliance.dto.ComplianceDeadlineEventDto;
import com.waqiti.compliance.service.ComplianceDeadlineService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Consumer for processing compliance deadline events.
 * Handles regulatory reporting deadlines, compliance milestones, deadline reminders,
 * and deadline breach notifications with automated escalation procedures.
 *
 * @author Waqiti Development Team
 * @version 1.0
 * @since 2024-01-01
 */
@Slf4j
@Component
public class ComplianceDeadlineEventsConsumer extends BaseConsumer<ComplianceDeadlineEventDto> {

    private static final String TOPIC_NAME = "compliance-deadline-events";
    private static final String CONSUMER_GROUP = "compliance-service-deadline";
    private static final String DLQ_TOPIC = "compliance-deadline-events-dlq";

    private final ComplianceDeadlineService deadlineService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MetricsCollector metricsCollector;
    private final CircuitBreaker circuitBreaker;

    // Metrics
    private final Counter eventsProcessedCounter;
    private final Counter eventsFailedCounter;
    private final Counter deadlineAlertsCounter;
    private final Counter deadlineBreachesCounter;
    private final Counter escalationsCounter;
    private final Counter remediationActionsCounter;
    private final Timer processingTimer;
    private final Timer deadlineAssessmentTimer;

    @Autowired
    public ComplianceDeadlineEventsConsumer(
            ComplianceDeadlineService deadlineService,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            MetricsCollector metricsCollector,
            MeterRegistry meterRegistry) {

        this.deadlineService = deadlineService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.metricsCollector = metricsCollector;
        this.circuitBreaker = new CircuitBreaker("compliance-deadline", 5, Duration.ofMinutes(2));

        // Initialize metrics
        this.eventsProcessedCounter = Counter.builder("compliance_deadline_events_processed_total")
                .description("Total number of compliance deadline events processed")
                .tag("service", "compliance")
                .tag("consumer", "compliance-deadline")
                .register(meterRegistry);

        this.eventsFailedCounter = Counter.builder("compliance_deadline_events_failed_total")
                .description("Total number of failed compliance deadline events")
                .tag("service", "compliance")
                .tag("consumer", "compliance-deadline")
                .register(meterRegistry);

        this.deadlineAlertsCounter = Counter.builder("deadline_alerts_total")
                .description("Total number of deadline alerts generated")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.deadlineBreachesCounter = Counter.builder("deadline_breaches_total")
                .description("Total number of deadline breaches")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.escalationsCounter = Counter.builder("deadline_escalations_total")
                .description("Total number of deadline escalations")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.remediationActionsCounter = Counter.builder("deadline_remediation_actions_total")
                .description("Total number of deadline remediation actions")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.processingTimer = Timer.builder("compliance_deadline_processing_duration")
                .description("Time taken to process compliance deadline events")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.deadlineAssessmentTimer = Timer.builder("deadline_assessment_duration")
                .description("Time taken for deadline assessment")
                .tag("service", "compliance")
                .register(meterRegistry);
    }

    /**
     * Processes compliance deadline events with automated escalation and remediation.
     *
     * @param eventJson The JSON representation of the compliance deadline event
     * @param key The message key
     * @param partition The partition number
     * @param offset The message offset
     * @param timestamp The message timestamp
     * @param acknowledgment The acknowledgment for manual commit
     */
    @KafkaListener(
        topics = TOPIC_NAME,
        groupId = CONSUMER_GROUP,
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    @Retryable(
        value = {Exception.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void processComplianceDeadlineEvent(
            String eventJson,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start();
        String correlationId = UUID.randomUUID().toString();

        try {
            log.info("Processing compliance deadline event - CorrelationId: {}, Key: {}, Partition: {}, Offset: {}",
                    correlationId, key, partition, timestamp);

            // Deserialize event
            ComplianceDeadlineEventDto event = deserializeEvent(eventJson, correlationId);
            if (event == null) {
                return;
            }

            // Validate event
            validateEvent(event, correlationId);

            // Process with circuit breaker
            circuitBreaker.executeSupplier(() -> {
                processDeadlineEvent(event, correlationId);
                return null;
            });

            // Track metrics
            eventsProcessedCounter.increment();
            metricsCollector.recordEventProcessed(TOPIC_NAME, "success");

            // Acknowledge message
            acknowledgment.acknowledge();

            log.info("Successfully processed compliance deadline event - CorrelationId: {}, DeadlineType: {}, Status: {}",
                    correlationId, event.getDeadlineType(), event.getStatus());

        } catch (Exception e) {
            handleProcessingError(eventJson, key, correlationId, e, acknowledgment);
        } finally {
            sample.stop(processingTimer);
        }
    }

    /**
     * Processes the compliance deadline event with comprehensive tracking and escalation.
     */
    private void processDeadlineEvent(ComplianceDeadlineEventDto event, String correlationId) {
        Timer.Sample assessmentTimer = Timer.start();

        try {
            log.info("Processing compliance deadline - Type: {}, Due: {} - CorrelationId: {}",
                    event.getDeadlineType(), event.getDueDate(), correlationId);

            // Deadline assessment
            var deadlineAssessment = deadlineService.assessDeadline(event, correlationId);

            // Process based on deadline type
            switch (event.getDeadlineType()) {
                case "REGULATORY_REPORT":
                    processRegulatoryReportDeadline(event, deadlineAssessment, correlationId);
                    break;
                case "COMPLIANCE_AUDIT":
                    processComplianceAuditDeadline(event, deadlineAssessment, correlationId);
                    break;
                case "POLICY_REVIEW":
                    processPolicyReviewDeadline(event, deadlineAssessment, correlationId);
                    break;
                case "TRAINING_COMPLETION":
                    processTrainingCompletionDeadline(event, deadlineAssessment, correlationId);
                    break;
                case "RISK_ASSESSMENT":
                    processRiskAssessmentDeadline(event, deadlineAssessment, correlationId);
                    break;
                case "REMEDIATION_ACTION":
                    processRemediationActionDeadline(event, deadlineAssessment, correlationId);
                    break;
                case "CERTIFICATION_RENEWAL":
                    processCertificationRenewalDeadline(event, deadlineAssessment, correlationId);
                    break;
                case "VENDOR_ASSESSMENT":
                    processVendorAssessmentDeadline(event, deadlineAssessment, correlationId);
                    break;
                default:
                    processGenericComplianceDeadline(event, deadlineAssessment, correlationId);
            }

            // Handle deadline status
            handleDeadlineStatus(event, deadlineAssessment, correlationId);

            // Update tracking systems
            deadlineService.updateDeadlineTracking(event, deadlineAssessment, correlationId);

        } finally {
            assessmentTimer.stop(deadlineAssessmentTimer);
        }
    }

    /**
     * Processes regulatory report deadlines.
     */
    private void processRegulatoryReportDeadline(ComplianceDeadlineEventDto event,
                                               var deadlineAssessment, String correlationId) {
        log.info("Processing regulatory report deadline - CorrelationId: {}, Report: {}",
                correlationId, event.getReportType());

        switch (event.getStatus()) {
            case "UPCOMING":
                handleUpcomingRegulatoryDeadline(event, deadlineAssessment, correlationId);
                break;
            case "DUE_TODAY":
                handleDueTodayRegulatoryDeadline(event, deadlineAssessment, correlationId);
                break;
            case "OVERDUE":
                handleOverdueRegulatoryDeadline(event, deadlineAssessment, correlationId);
                break;
            case "COMPLETED":
                handleCompletedRegulatoryDeadline(event, deadlineAssessment, correlationId);
                break;
        }
    }

    /**
     * Handles upcoming regulatory deadlines.
     */
    private void handleUpcomingRegulatoryDeadline(ComplianceDeadlineEventDto event,
                                                var deadlineAssessment, String correlationId) {
        deadlineAlertsCounter.increment();

        // Calculate days until deadline
        long daysUntilDeadline = Duration.between(Instant.now(), event.getDueDate()).toDays();

        if (daysUntilDeadline <= 1) {
            // Critical: 1 day or less
            deadlineService.sendCriticalDeadlineAlert(event, correlationId);
            escalationsCounter.increment();
        } else if (daysUntilDeadline <= 3) {
            // High priority: 3 days or less
            deadlineService.sendHighPriorityDeadlineAlert(event, correlationId);
        } else if (daysUntilDeadline <= 7) {
            // Medium priority: 1 week or less
            deadlineService.sendMediumPriorityDeadlineAlert(event, correlationId);
        } else {
            // Standard reminder
            deadlineService.sendStandardDeadlineReminder(event, correlationId);
        }

        // Prepare report if not started
        if (!deadlineAssessment.isReportInProgress()) {
            deadlineService.initiateReportPreparation(event, correlationId);
            remediationActionsCounter.increment();
        }
    }

    /**
     * Handles due today regulatory deadlines.
     */
    private void handleDueTodayRegulatoryDeadline(ComplianceDeadlineEventDto event,
                                                var deadlineAssessment, String correlationId) {
        log.warn("Regulatory deadline due today - CorrelationId: {}, Report: {}",
                correlationId, event.getReportType());

        deadlineAlertsCounter.increment();
        escalationsCounter.increment();

        // Emergency escalation
        deadlineService.escalateToSeniorManagement(event, "Due today", correlationId);

        // Expedite completion
        deadlineService.expediteReportCompletion(event, correlationId);
        remediationActionsCounter.increment();

        // Prepare late filing procedures if needed
        deadlineService.prepareLateFilingProcedures(event, correlationId);
    }

    /**
     * Handles overdue regulatory deadlines.
     */
    private void handleOverdueRegulatoryDeadline(ComplianceDeadlineEventDto event,
                                               var deadlineAssessment, String correlationId) {
        log.error("CRITICAL: Regulatory deadline overdue - CorrelationId: {}, Report: {}, Days overdue: {}",
                correlationId, event.getReportType(), event.getDaysOverdue());

        deadlineBreachesCounter.increment();
        escalationsCounter.increment();

        // Crisis management
        deadlineService.activateDeadlineCrisisManagement(event, correlationId);

        // Immediate late filing
        deadlineService.submitLateFiling(event, correlationId);
        remediationActionsCounter.increment();

        // Regulator notification
        deadlineService.notifyRegulatorsOfLateFiling(event, correlationId);

        // Root cause analysis
        deadlineService.initiateRootCauseAnalysis(event, correlationId);
    }

    /**
     * Handles completed regulatory deadlines.
     */
    private void handleCompletedRegulatoryDeadline(ComplianceDeadlineEventDto event,
                                                 var deadlineAssessment, String correlationId) {
        log.info("Regulatory deadline completed - CorrelationId: {}, Report: {}",
                correlationId, event.getReportType());

        // Validate completion
        deadlineService.validateDeadlineCompletion(event, correlationId);

        // Update compliance calendar
        deadlineService.updateComplianceCalendar(event, correlationId);

        // Schedule next occurrence if recurring
        if (event.isRecurring()) {
            deadlineService.scheduleNextOccurrence(event, correlationId);
        }
    }

    /**
     * Processes compliance audit deadlines.
     */
    private void processComplianceAuditDeadline(ComplianceDeadlineEventDto event,
                                              var deadlineAssessment, String correlationId) {
        log.info("Processing compliance audit deadline - CorrelationId: {}", correlationId);

        // Audit preparation tracking
        var auditPreparation = deadlineService.assessAuditPreparation(event, correlationId);

        if (!auditPreparation.isOnTrack()) {
            deadlineService.escalateAuditPreparation(event, auditPreparation, correlationId);
            escalationsCounter.increment();
        }

        // Document collection status
        deadlineService.trackDocumentCollection(event, correlationId);

        // Stakeholder coordination
        deadlineService.coordinateAuditStakeholders(event, correlationId);
    }

    /**
     * Processes policy review deadlines.
     */
    private void processPolicyReviewDeadline(ComplianceDeadlineEventDto event,
                                           var deadlineAssessment, String correlationId) {
        log.info("Processing policy review deadline - CorrelationId: {}", correlationId);

        // Policy review status
        var reviewStatus = deadlineService.assessPolicyReviewStatus(event, correlationId);

        if (reviewStatus.isBehindSchedule()) {
            deadlineService.acceleratePolicyReview(event, correlationId);
            remediationActionsCounter.increment();
        }

        // Stakeholder approval tracking
        deadlineService.trackStakeholderApprovals(event, correlationId);

        // Implementation planning
        if (reviewStatus.isApprovalPending()) {
            deadlineService.planPolicyImplementation(event, correlationId);
        }
    }

    /**
     * Processes training completion deadlines.
     */
    private void processTrainingCompletionDeadline(ComplianceDeadlineEventDto event,
                                                 var deadlineAssessment, String correlationId) {
        log.info("Processing training completion deadline - CorrelationId: {}", correlationId);

        // Training completion rates
        var completionRates = deadlineService.assessTrainingCompletionRates(event, correlationId);

        if (completionRates.isBelowThreshold()) {
            deadlineService.escalateTrainingCompletion(event, completionRates, correlationId);
            escalationsCounter.increment();

            // Additional training sessions
            deadlineService.scheduleAdditionalTrainingSessions(event, correlationId);
            remediationActionsCounter.increment();
        }

        // Non-compliance tracking
        deadlineService.trackTrainingNonCompliance(event, correlationId);
    }

    /**
     * Processes risk assessment deadlines.
     */
    private void processRiskAssessmentDeadline(ComplianceDeadlineEventDto event,
                                             var deadlineAssessment, String correlationId) {
        log.info("Processing risk assessment deadline - CorrelationId: {}", correlationId);

        // Risk assessment progress
        var assessmentProgress = deadlineService.assessRiskAssessmentProgress(event, correlationId);

        if (!assessmentProgress.isOnSchedule()) {
            deadlineService.expediteRiskAssessment(event, correlationId);
            remediationActionsCounter.increment();
        }

        // Resource allocation
        deadlineService.optimizeResourceAllocation(event, assessmentProgress, correlationId);
    }

    /**
     * Processes remediation action deadlines.
     */
    private void processRemediationActionDeadline(ComplianceDeadlineEventDto event,
                                                var deadlineAssessment, String correlationId) {
        log.info("Processing remediation action deadline - CorrelationId: {}", correlationId);

        // Remediation progress tracking
        var remediationProgress = deadlineService.assessRemediationProgress(event, correlationId);

        if (remediationProgress.isAtRisk()) {
            deadlineService.escalateRemediationActions(event, remediationProgress, correlationId);
            escalationsCounter.increment();

            // Additional resources
            deadlineService.allocateAdditionalResources(event, correlationId);
            remediationActionsCounter.increment();
        }

        // Effectiveness measurement
        deadlineService.measureRemediationEffectiveness(event, correlationId);
    }

    /**
     * Processes certification renewal deadlines.
     */
    private void processCertificationRenewalDeadline(ComplianceDeadlineEventDto event,
                                                   var deadlineAssessment, String correlationId) {
        log.info("Processing certification renewal deadline - CorrelationId: {}", correlationId);

        // Renewal preparation
        var renewalPreparation = deadlineService.assessRenewalPreparation(event, correlationId);

        if (!renewalPreparation.isComplete()) {
            deadlineService.expediteCertificationRenewal(event, correlationId);
            remediationActionsCounter.increment();
        }

        // Documentation verification
        deadlineService.verifyRenewalDocumentation(event, correlationId);
    }

    /**
     * Processes vendor assessment deadlines.
     */
    private void processVendorAssessmentDeadline(ComplianceDeadlineEventDto event,
                                               var deadlineAssessment, String correlationId) {
        log.info("Processing vendor assessment deadline - CorrelationId: {}", correlationId);

        // Vendor assessment progress
        var assessmentProgress = deadlineService.assessVendorAssessmentProgress(event, correlationId);

        if (assessmentProgress.isDelayed()) {
            deadlineService.escalateVendorAssessment(event, correlationId);
            escalationsCounter.increment();
        }

        // Risk mitigation
        deadlineService.implementVendorRiskMitigation(event, correlationId);
    }

    /**
     * Processes generic compliance deadlines.
     */
    private void processGenericComplianceDeadline(ComplianceDeadlineEventDto event,
                                                var deadlineAssessment, String correlationId) {
        log.info("Processing generic compliance deadline - CorrelationId: {}", correlationId);

        // Standard deadline processing
        deadlineService.processStandardDeadline(event, deadlineAssessment, correlationId);

        // Generic remediation if needed
        if (deadlineAssessment.requiresAction()) {
            deadlineService.applyGenericRemediation(event, correlationId);
            remediationActionsCounter.increment();
        }
    }

    /**
     * Handles deadline status processing.
     */
    private void handleDeadlineStatus(ComplianceDeadlineEventDto event,
                                    var deadlineAssessment, String correlationId) {
        // Update deadline tracking
        deadlineService.updateDeadlineStatus(event, correlationId);

        // Generate alerts if needed
        if (deadlineAssessment.requiresAlert()) {
            deadlineService.generateDeadlineAlert(event, deadlineAssessment, correlationId);
            deadlineAlertsCounter.increment();
        }

        // Escalation if critical
        if (deadlineAssessment.requiresEscalation()) {
            deadlineService.escalateDeadline(event, deadlineAssessment, correlationId);
            escalationsCounter.increment();
        }

        // Remediation planning
        if (deadlineAssessment.requiresRemediation()) {
            deadlineService.planDeadlineRemediation(event, deadlineAssessment, correlationId);
            remediationActionsCounter.increment();
        }
    }

    /**
     * Deserializes the event JSON into a ComplianceDeadlineEventDto.
     */
    private ComplianceDeadlineEventDto deserializeEvent(String eventJson, String correlationId) {
        try {
            return objectMapper.readValue(eventJson, ComplianceDeadlineEventDto.class);
        } catch (Exception e) {
            log.error("Failed to deserialize compliance deadline event - CorrelationId: {}, Error: {}",
                     correlationId, e.getMessage(), e);
            eventsFailedCounter.increment();
            metricsCollector.recordEventProcessed(TOPIC_NAME, "deserialization_error");
            return null;
        }
    }

    /**
     * Validates the compliance deadline event.
     */
    private void validateEvent(ComplianceDeadlineEventDto event, String correlationId) {
        if (event.getDeadlineType() == null || event.getDeadlineType().trim().isEmpty()) {
            throw new IllegalArgumentException("Deadline type is required");
        }

        if (event.getDueDate() == null) {
            throw new IllegalArgumentException("Due date is required");
        }

        if (event.getStatus() == null || event.getStatus().trim().isEmpty()) {
            throw new IllegalArgumentException("Status is required");
        }

        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Timestamp is required");
        }

        // Validate reasonable due date
        if (event.getDueDate().isBefore(Instant.now().minus(Duration.ofDays(365)))) {
            log.warn("Compliance deadline is more than 1 year old - CorrelationId: {}, DueDate: {}",
                    correlationId, event.getDueDate());
        }
    }

    /**
     * Handles processing errors with deadline-specific logging.
     */
    private void handleProcessingError(String eventJson, String key, String correlationId,
                                     Exception error, Acknowledgment acknowledgment) {
        log.error("Failed to process compliance deadline event - CorrelationId: {}, Key: {}, Error: {}",
                 correlationId, key, error.getMessage(), error);

        eventsFailedCounter.increment();
        metricsCollector.recordEventProcessed(TOPIC_NAME, "error");

        try {
            // Send to DLQ
            kafkaTemplate.send(DLQ_TOPIC, key, Map.of(
                "originalEvent", eventJson,
                "error", error.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now().toString(),
                "service", "compliance-service",
                "consumer", "compliance-deadline"
            ));

            log.info("Sent failed compliance deadline event to DLQ - CorrelationId: {}", correlationId);

        } catch (Exception dlqError) {
            log.error("Failed to send compliance deadline event to DLQ - CorrelationId: {}, Error: {}",
                     correlationId, dlqError.getMessage(), dlqError);
        }

        // Acknowledge to prevent reprocessing
        acknowledgment.acknowledge();
    }

    @Override
    protected String getConsumerName() {
        return "ComplianceDeadlineEventsConsumer";
    }

    @Override
    protected String getTopicName() {
        return TOPIC_NAME;
    }
}