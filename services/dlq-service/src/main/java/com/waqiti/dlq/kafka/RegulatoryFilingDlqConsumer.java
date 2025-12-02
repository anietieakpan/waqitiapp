package com.waqiti.dlq.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseConsumer;
import com.waqiti.common.metrics.MetricsCollector;
import com.waqiti.common.monitoring.CircuitBreaker;
import com.waqiti.dlq.dto.RegulatoryFilingDlqEventDto;
import com.waqiti.dlq.service.RegulatoryFilingDlqService;
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
 * Critical DLQ Consumer for failed regulatory filing events.
 * Handles high-priority regulatory filing failures with immediate escalation,
 * automated regulatory deadline management, alternative filing procedures,
 * and regulatory authority communication.
 *
 * This consumer processes critical regulatory filing failures that could result in
 * regulatory violations, fines, or compliance breaches with severe business consequences.
 *
 * @author Waqiti Development Team
 * @version 1.0
 * @since 2024-01-01
 */
@Slf4j
@Component
public class RegulatoryFilingDlqConsumer extends BaseConsumer<RegulatoryFilingDlqEventDto> {

    private static final String TOPIC_NAME = "regulatory-filing-dlq";
    private static final String CONSUMER_GROUP = "dlq-service-regulatory-filing";
    private static final String ESCALATION_TOPIC = "regulatory-filing-escalation";

    private final RegulatoryFilingDlqService dlqService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MetricsCollector metricsCollector;
    private final CircuitBreaker circuitBreaker;

    // Regulatory Filing Metrics
    private final Counter dlqEventsProcessedCounter;
    private final Counter dlqEventsFailedCounter;
    private final Counter criticalFilingFailuresCounter;
    private final Counter regulatoryEscalationsCounter;
    private final Counter alternativeFilingActivationsCounter;
    private final Counter emergencyFilingProceduresCounter;
    private final Timer dlqProcessingTimer;
    private final Timer filingRecoveryTimer;

    @Autowired
    public RegulatoryFilingDlqConsumer(
            RegulatoryFilingDlqService dlqService,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            MetricsCollector metricsCollector,
            MeterRegistry meterRegistry) {

        this.dlqService = dlqService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.metricsCollector = metricsCollector;
        this.circuitBreaker = new CircuitBreaker("regulatory-filing-dlq", 3, Duration.ofMinutes(1));

        // Initialize regulatory filing metrics
        this.dlqEventsProcessedCounter = Counter.builder("regulatory_filing_dlq_events_processed_total")
                .description("Total number of regulatory filing DLQ events processed")
                .tag("service", "dlq")
                .tag("consumer", "regulatory-filing")
                .register(meterRegistry);

        this.dlqEventsFailedCounter = Counter.builder("regulatory_filing_dlq_events_failed_total")
                .description("Total number of failed regulatory filing DLQ events")
                .tag("service", "dlq")
                .tag("consumer", "regulatory-filing")
                .register(meterRegistry);

        this.criticalFilingFailuresCounter = Counter.builder("critical_filing_failures_total")
                .description("Total number of critical regulatory filing failures")
                .tag("service", "dlq")
                .register(meterRegistry);

        this.regulatoryEscalationsCounter = Counter.builder("regulatory_escalations_total")
                .description("Total number of regulatory escalations")
                .tag("service", "dlq")
                .tag("type", "regulatory-filing")
                .register(meterRegistry);

        this.alternativeFilingActivationsCounter = Counter.builder("alternative_filing_activations_total")
                .description("Total number of alternative filing procedure activations")
                .tag("service", "dlq")
                .register(meterRegistry);

        this.emergencyFilingProceduresCounter = Counter.builder("emergency_filing_procedures_total")
                .description("Total number of emergency filing procedures initiated")
                .tag("service", "dlq")
                .register(meterRegistry);

        this.dlqProcessingTimer = Timer.builder("regulatory_filing_dlq_processing_duration")
                .description("Time taken to process regulatory filing DLQ events")
                .tag("service", "dlq")
                .register(meterRegistry);

        this.filingRecoveryTimer = Timer.builder("filing_recovery_duration")
                .description("Time taken for regulatory filing recovery")
                .tag("service", "dlq")
                .register(meterRegistry);
    }

    /**
     * Processes regulatory filing DLQ events with immediate priority and regulatory deadline management.
     *
     * @param eventJson The JSON representation of the regulatory filing DLQ event
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
        maxAttempts = 2, // Reduced retries for immediate regulatory attention
        backoff = @Backoff(delay = 200, multiplier = 2)
    )
    public void processRegulatoryFilingDlqEvent(
            String eventJson,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start();
        String correlationId = UUID.randomUUID().toString();

        try {
            log.error("CRITICAL: Processing failed regulatory filing event - CorrelationId: {}, Key: {}, Partition: {}, Offset: {}",
                    correlationId, key, partition, timestamp);

            // Deserialize DLQ event
            RegulatoryFilingDlqEventDto dlqEvent = deserializeEvent(eventJson, correlationId);
            if (dlqEvent == null) {
                return;
            }

            // Validate DLQ event
            validateEvent(dlqEvent, correlationId);

            // Process with circuit breaker
            circuitBreaker.executeSupplier(() -> {
                processRegulatoryFilingFailure(dlqEvent, correlationId);
                return null;
            });

            // Track metrics
            dlqEventsProcessedCounter.increment();
            metricsCollector.recordEventProcessed(TOPIC_NAME, "success");

            // Acknowledge message
            acknowledgment.acknowledge();

            log.warn("Regulatory filing DLQ event processed - CorrelationId: {}, FilingId: {}, RecoveryAction: {}",
                    correlationId, dlqEvent.getFilingId(), dlqEvent.getRecoveryAction());

        } catch (Exception e) {
            handleProcessingError(eventJson, key, correlationId, e, acknowledgment);
        } finally {
            sample.stop(dlqProcessingTimer);
        }
    }

    /**
     * Processes regulatory filing failures with comprehensive compliance protection measures.
     */
    private void processRegulatoryFilingFailure(RegulatoryFilingDlqEventDto dlqEvent, String correlationId) {
        Timer.Sample recoveryTimer = Timer.start();

        try {
            log.error("Processing critical regulatory filing failure - FilingId: {}, Regulator: {} - CorrelationId: {}",
                    dlqEvent.getFilingId(), dlqEvent.getRegulator(), correlationId);

            criticalFilingFailuresCounter.increment();

            // Immediate regulatory deadline assessment
            var deadlineAssessment = dlqService.assessRegulatoryDeadline(dlqEvent, correlationId);

            // Process based on filing type and regulatory urgency
            switch (dlqEvent.getFilingType()) {
                case "SEC_10K":
                    processSecForm10KFailure(dlqEvent, deadlineAssessment, correlationId);
                    break;
                case "SEC_10Q":
                    processSecForm10QFailure(dlqEvent, deadlineAssessment, correlationId);
                    break;
                case "SEC_8K":
                    processSecForm8KFailure(dlqEvent, deadlineAssessment, correlationId);
                    break;
                case "FINCEN_SAR":
                    processFinCenSarFailure(dlqEvent, deadlineAssessment, correlationId);
                    break;
                case "FINCEN_CTR":
                    processFinCenCtrFailure(dlqEvent, deadlineAssessment, correlationId);
                    break;
                case "FDIC_QUARTERLY_REPORT":
                    processFdicQuarterlyReportFailure(dlqEvent, deadlineAssessment, correlationId);
                    break;
                case "OCC_RISK_ASSESSMENT":
                    processOccRiskAssessmentFailure(dlqEvent, deadlineAssessment, correlationId);
                    break;
                case "CFPB_COMPLAINT_RESPONSE":
                    processCfpbComplaintResponseFailure(dlqEvent, deadlineAssessment, correlationId);
                    break;
                case "GDPR_BREACH_NOTIFICATION":
                    processGdprBreachNotificationFailure(dlqEvent, deadlineAssessment, correlationId);
                    break;
                case "AML_ANNUAL_REPORT":
                    processAmlAnnualReportFailure(dlqEvent, deadlineAssessment, correlationId);
                    break;
                default:
                    processGenericRegulatoryFilingFailure(dlqEvent, deadlineAssessment, correlationId);
            }

            // Emergency escalation for imminent deadlines
            if (deadlineAssessment.requiresEmergencyEscalation()) {
                initiateRegulatoryEmergencyEscalation(dlqEvent, deadlineAssessment, correlationId);
            }

            // Update regulatory compliance dashboards
            updateRegulatoryComplianceDashboards(dlqEvent, deadlineAssessment, correlationId);

            // Prepare regulatory authority communication if needed
            if (deadlineAssessment.requiresRegulatoryCommu nication()) {
                prepareRegulatoryAuthorityCommunication(dlqEvent, deadlineAssessment, correlationId);
            }

        } finally {
            recoveryTimer.stop(filingRecoveryTimer);
        }
    }

    /**
     * Processes SEC Form 10-K failures with annual report compliance procedures.
     */
    private void processSecForm10KFailure(RegulatoryFilingDlqEventDto dlqEvent,
                                        var deadlineAssessment, String correlationId) {
        log.error("CRITICAL: SEC Form 10-K filing failure - CorrelationId: {}, FiscalYear: {}",
                correlationId, dlqEvent.getFiscalYear());

        // Check 10-K filing deadline proximity
        var form10KDeadline = dlqService.assessForm10KDeadlineProximity(dlqEvent, correlationId);

        if (form10KDeadline.isWithin15Days()) {
            log.error("Form 10-K deadline within 15 days - Activating emergency procedures - CorrelationId: {}", correlationId);

            // Activate emergency 10-K filing protocol
            var emergencyProtocol = dlqService.activateEmergencyForm10KProtocol(dlqEvent, correlationId);
            emergencyFilingProceduresCounter.increment();

            // Engage external SEC filing specialists if available
            var externalSpecialists = dlqService.engageExternalSecFilingSpecialists(dlqEvent, correlationId);

            if (externalSpecialists.isAvailable()) {
                // Transfer filing to external specialists
                var externalFilingResult = dlqService.transferFilingToExternalSpecialists(dlqEvent, externalSpecialists, correlationId);
                alternativeFilingActivationsCounter.increment();

                if (externalFilingResult.isSuccessful()) {
                    log.info("Form 10-K transferred to external specialists - CorrelationId: {}", correlationId);
                } else {
                    // Escalate to CEO and Legal immediately
                    dlqService.escalateToCeoAndLegal(dlqEvent, externalFilingResult, correlationId);
                    regulatoryEscalationsCounter.increment();
                }
            } else {
                // Internal emergency filing procedures
                var internalEmergencyFiling = dlqService.executeInternalEmergencyForm10KFiling(dlqEvent, correlationId);

                if (!internalEmergencyFiling.isSuccessful()) {
                    // Consider filing extension request with SEC
                    dlqService.considerForm10KExtensionRequest(dlqEvent, internalEmergencyFiling, correlationId);
                    regulatoryEscalationsCounter.increment();
                }
            }
        } else {
            // Standard 10-K recovery procedures
            var recoveryResult = dlqService.attemptForm10KRecovery(dlqEvent, correlationId);

            if (!recoveryResult.isSuccessful()) {
                // Schedule emergency audit and filing session
                dlqService.scheduleEmergencyAuditAndFilingSession(dlqEvent, recoveryResult, correlationId);
            }
        }
    }

    /**
     * Processes FinCEN SAR filing failures with AML compliance procedures.
     */
    private void processFinCenSarFailure(RegulatoryFilingDlqEventDto dlqEvent,
                                       var deadlineAssessment, String correlationId) {
        log.error("CRITICAL: FinCEN SAR filing failure - CorrelationId: {}, SarId: {}",
                correlationId, dlqEvent.getSarId());

        // SAR filings have strict 30-day deadline
        var sarDeadline = dlqService.assessSarFilingDeadline(dlqEvent, correlationId);

        if (sarDeadline.isWithin7Days()) {
            log.error("SAR filing deadline within 7 days - Activating emergency AML procedures - CorrelationId: {}", correlationId);

            // Activate emergency SAR filing protocol
            var emergencySarProtocol = dlqService.activateEmergencySarFilingProtocol(dlqEvent, correlationId);
            emergencyFilingProceduresCounter.increment();

            // Validate SAR data integrity
            var sarDataValidation = dlqService.validateSarDataIntegrity(dlqEvent, correlationId);

            if (sarDataValidation.hasIntegrityIssues()) {
                // Emergency data remediation
                var dataRemediation = dlqService.performEmergencySarDataRemediation(dlqEvent, sarDataValidation, correlationId);

                if (dataRemediation.isSuccessful()) {
                    // Retry SAR filing with corrected data
                    var retrySarFiling = dlqService.retrySarFilingWithCorrectedData(dlqEvent, dataRemediation, correlationId);
                    alternativeFilingActivationsCounter.increment();
                } else {
                    // Escalate to AML Officer and Compliance
                    dlqService.escalateToAmlOfficerAndCompliance(dlqEvent, dataRemediation, correlationId);
                    regulatoryEscalationsCounter.increment();
                }
            } else {
                // Direct SAR filing retry
                var sarRetryResult = dlqService.retrySarFiling(dlqEvent, correlationId);

                if (!sarRetryResult.isSuccessful()) {
                    // Manual SAR filing preparation
                    dlqService.prepareManualSarFiling(dlqEvent, sarRetryResult, correlationId);
                    alternativeFilingActivationsCounter.increment();
                }
            }
        } else {
            // Standard SAR recovery
            var sarRecovery = dlqService.attemptSarRecovery(dlqEvent, correlationId);

            if (!sarRecovery.isSuccessful()) {
                dlqService.escalateToAmlTeam(dlqEvent, sarRecovery, correlationId);
            }
        }
    }

    /**
     * Processes GDPR breach notification failures with privacy compliance procedures.
     */
    private void processGdprBreachNotificationFailure(RegulatoryFilingDlqEventDto dlqEvent,
                                                    var deadlineAssessment, String correlationId) {
        log.error("CRITICAL: GDPR breach notification failure - CorrelationId: {}, BreachId: {}",
                correlationId, dlqEvent.getBreachId());

        // GDPR has strict 72-hour notification requirement
        var gdprDeadline = dlqService.assessGdprNotificationDeadline(dlqEvent, correlationId);

        if (gdprDeadline.isWithin12Hours()) {
            log.error("GDPR notification deadline within 12 hours - Activating emergency privacy procedures - CorrelationId: {}", correlationId);

            // Activate emergency GDPR notification protocol
            var emergencyGdprProtocol = dlqService.activateEmergencyGdprNotificationProtocol(dlqEvent, correlationId);
            emergencyFilingProceduresCounter.increment();

            // Identify relevant supervisory authority
            var supervisoryAuthority = dlqService.identifyRelevantSupervisoryAuthority(dlqEvent, correlationId);

            // Prepare emergency GDPR notification
            var emergencyNotification = dlqService.prepareEmergencyGdprNotification(dlqEvent, supervisoryAuthority, correlationId);

            if (emergencyNotification.isSuccessful()) {
                // Submit via alternative channels if needed
                var alternativeSubmission = dlqService.submitViaAlternativeChannels(dlqEvent, emergencyNotification, correlationId);
                alternativeFilingActivationsCounter.increment();

                if (alternativeSubmission.isSuccessful()) {
                    log.info("GDPR notification submitted via alternative channels - CorrelationId: {}", correlationId);
                } else {
                    // Direct communication with supervisory authority
                    dlqService.initiateDirectCommunicationWithSupervisoryAuthority(dlqEvent, correlationId);
                    regulatoryEscalationsCounter.increment();
                }
            } else {
                // Escalate to DPO and Legal
                dlqService.escalateToDpoAndLegal(dlqEvent, emergencyNotification, correlationId);
                regulatoryEscalationsCounter.increment();
            }
        } else {
            // Standard GDPR notification recovery
            var gdprRecovery = dlqService.attemptGdprNotificationRecovery(dlqEvent, correlationId);

            if (!gdprRecovery.isSuccessful()) {
                dlqService.escalateToPrivacyTeam(dlqEvent, gdprRecovery, correlationId);
            }
        }
    }

    /**
     * Processes CFPB complaint response failures with consumer protection procedures.
     */
    private void processCfpbComplaintResponseFailure(RegulatoryFilingDlqEventDto dlqEvent,
                                                   var deadlineAssessment, String correlationId) {
        log.error("CRITICAL: CFPB complaint response failure - CorrelationId: {}, ComplaintId: {}",
                correlationId, dlqEvent.getComplaintId());

        // CFPB responses typically have 15-day deadline
        var cfpbDeadline = dlqService.assessCfpbResponseDeadline(dlqEvent, correlationId);

        if (cfpbDeadline.isWithin3Days()) {
            log.error("CFPB response deadline within 3 days - Activating emergency consumer protection procedures - CorrelationId: {}", correlationId);

            // Activate emergency CFPB response protocol
            var emergencyCfpbProtocol = dlqService.activateEmergencyCfpbResponseProtocol(dlqEvent, correlationId);
            emergencyFilingProceduresCounter.increment();

            // Analyze complaint details
            var complaintAnalysis = dlqService.analyzeComplaintDetails(dlqEvent, correlationId);

            // Prepare emergency response
            var emergencyResponse = dlqService.prepareEmergencyCfpbResponse(dlqEvent, complaintAnalysis, correlationId);

            if (emergencyResponse.isSuccessful()) {
                // Submit emergency response
                var responseSubmission = dlqService.submitEmergencyCfpbResponse(dlqEvent, emergencyResponse, correlationId);
                alternativeFilingActivationsCounter.increment();

                if (!responseSubmission.isSuccessful()) {
                    // Escalate to Consumer Protection Officer
                    dlqService.escalateToConsumerProtectionOfficer(dlqEvent, responseSubmission, correlationId);
                    regulatoryEscalationsCounter.increment();
                }
            } else {
                // Escalate to Customer Service and Legal
                dlqService.escalateToCustomerServiceAndLegal(dlqEvent, emergencyResponse, correlationId);
                regulatoryEscalationsCounter.increment();
            }
        } else {
            // Standard CFPB response recovery
            var cfpbRecovery = dlqService.attemptCfpbResponseRecovery(dlqEvent, correlationId);

            if (!cfpbRecovery.isSuccessful()) {
                dlqService.escalateToCustomerProtectionTeam(dlqEvent, cfpbRecovery, correlationId);
            }
        }
    }

    /**
     * Initiates regulatory emergency escalation procedures for critical filing failures.
     */
    private void initiateRegulatoryEmergencyEscalation(RegulatoryFilingDlqEventDto dlqEvent,
                                                     var deadlineAssessment, String correlationId) {
        log.error("REGULATORY EMERGENCY ESCALATION: Critical filing failure - CorrelationId: {}, Regulator: {}, Deadline: {}",
                correlationId, dlqEvent.getRegulator(), deadlineAssessment.getDeadline());

        regulatoryEscalationsCounter.increment();

        // Immediate notification to compliance leadership
        dlqService.notifyComplianceLeadership(dlqEvent, deadlineAssessment, correlationId);

        // Activate regulatory incident response team
        dlqService.activateRegulatoryIncidentResponseTeam(dlqEvent, correlationId);

        // Create emergency regulatory bridge
        dlqService.createEmergencyRegulatoryBridge(dlqEvent, correlationId);

        // Prepare regulatory authority proactive communication
        dlqService.prepareProactiveRegulatoryAuthorityCommunication(dlqEvent, deadlineAssessment, correlationId);

        // Send escalation event
        try {
            kafkaTemplate.send(ESCALATION_TOPIC, dlqEvent.getFilingId(), Map.of(
                "escalationType", "REGULATORY_FILING_EMERGENCY",
                "filingId", dlqEvent.getFilingId(),
                "filingType", dlqEvent.getFilingType(),
                "regulator", dlqEvent.getRegulator(),
                "deadline", deadlineAssessment.getDeadline().toString(),
                "hoursUntilDeadline", deadlineAssessment.getHoursUntilDeadline(),
                "correlationId", correlationId,
                "timestamp", Instant.now().toString(),
                "requiresImmediateAction", true
            ));
        } catch (Exception e) {
            log.error("Failed to send regulatory emergency escalation - CorrelationId: {}, Error: {}",
                     correlationId, e.getMessage());
        }
    }

    /**
     * Updates regulatory compliance dashboards with filing failure status and deadlines.
     */
    private void updateRegulatoryComplianceDashboards(RegulatoryFilingDlqEventDto dlqEvent,
                                                    var deadlineAssessment, String correlationId) {
        // Update filing status dashboard
        dlqService.updateFilingStatusDashboard(dlqEvent, deadlineAssessment, correlationId);

        // Show regulatory deadline countdown
        dlqService.showRegulatoryDeadlineCountdown(dlqEvent, deadlineAssessment, correlationId);

        // Display alternative filing procedures being used
        dlqService.displayAlternativeFilingProcedures(dlqEvent, correlationId);

        // Add regulatory compliance alerts
        dlqService.addRegulatoryComplianceAlerts(dlqEvent, deadlineAssessment, correlationId);
    }

    /**
     * Prepares regulatory authority communication for proactive engagement.
     */
    private void prepareRegulatoryAuthorityCommunication(RegulatoryFilingDlqEventDto dlqEvent,
                                                       var deadlineAssessment, String correlationId) {
        log.info("Preparing regulatory authority communication - CorrelationId: {}, Regulator: {}",
                correlationId, dlqEvent.getRegulator());

        // Draft proactive communication to regulator
        var proactiveCommunication = dlqService.draftProactiveRegulatoryCommuni cation(dlqEvent, deadlineAssessment, correlationId);

        // Prepare extension request if appropriate
        if (deadlineAssessment.allowsExtensionRequest()) {
            var extensionRequest = dlqService.prepareFilingExtensionRequest(dlqEvent, deadlineAssessment, correlationId);
            dlqService.submitFilingExtensionRequest(dlqEvent, extensionRequest, correlationId);
        }

        // Schedule regulatory liaison meeting if needed
        if (deadlineAssessment.requiresRegulatoryMeeting()) {
            dlqService.scheduleEmergencyRegulatoryLiaisonMeeting(dlqEvent, deadlineAssessment, correlationId);
        }
    }

    /**
     * Deserializes the DLQ event JSON into a RegulatoryFilingDlqEventDto.
     */
    private RegulatoryFilingDlqEventDto deserializeEvent(String eventJson, String correlationId) {
        try {
            return objectMapper.readValue(eventJson, RegulatoryFilingDlqEventDto.class);
        } catch (Exception e) {
            log.error("Failed to deserialize regulatory filing DLQ event - CorrelationId: {}, Error: {}",
                     correlationId, e.getMessage(), e);
            dlqEventsFailedCounter.increment();
            metricsCollector.recordEventProcessed(TOPIC_NAME, "deserialization_error");
            return null;
        }
    }

    /**
     * Validates the regulatory filing DLQ event.
     */
    private void validateEvent(RegulatoryFilingDlqEventDto dlqEvent, String correlationId) {
        if (dlqEvent.getFilingId() == null || dlqEvent.getFilingId().trim().isEmpty()) {
            throw new IllegalArgumentException("Filing ID is required for regulatory filing DLQ events");
        }

        if (dlqEvent.getFilingType() == null || dlqEvent.getFilingType().trim().isEmpty()) {
            throw new IllegalArgumentException("Filing type is required");
        }

        if (dlqEvent.getRegulator() == null || dlqEvent.getRegulator().trim().isEmpty()) {
            throw new IllegalArgumentException("Regulator is required");
        }

        if (dlqEvent.getFilingDeadline() == null) {
            throw new IllegalArgumentException("Filing deadline is required");
        }

        if (dlqEvent.getFailureTimestamp() == null) {
            throw new IllegalArgumentException("Failure timestamp is required");
        }

        // Validate deadline proximity
        Duration timeUntilDeadline = Duration.between(Instant.now(), dlqEvent.getFilingDeadline());
        if (timeUntilDeadline.isNegative()) {
            log.error("CRITICAL: Regulatory filing deadline has passed - CorrelationId: {}, Deadline: {}",
                    correlationId, dlqEvent.getFilingDeadline());
        } else if (timeUntilDeadline.toHours() < 24) {
            log.error("URGENT: Regulatory filing deadline within 24 hours - CorrelationId: {}, Deadline: {}",
                    correlationId, dlqEvent.getFilingDeadline());
        }

        // Validate time sensitivity
        Duration timeSinceFailure = Duration.between(dlqEvent.getFailureTimestamp(), Instant.now());
        if (timeSinceFailure.toMinutes() > 15) {
            log.warn("Regulatory filing DLQ event is older than 15 minutes - CorrelationId: {}, Age: {} minutes",
                    correlationId, timeSinceFailure.toMinutes());
        }
    }

    /**
     * Handles processing errors with regulatory escalation procedures.
     */
    private void handleProcessingError(String eventJson, String key, String correlationId,
                                     Exception error, Acknowledgment acknowledgment) {
        log.error("CRITICAL: Failed to process regulatory filing DLQ event - CorrelationId: {}, Key: {}, Error: {}",
                 correlationId, key, error.getMessage(), error);

        dlqEventsFailedCounter.increment();
        metricsCollector.recordEventProcessed(TOPIC_NAME, "error");

        // Emergency notification for regulatory filing processing failures
        try {
            dlqService.notifyRegulatoryTeamOfProcessingFailure(correlationId, error);

            // Create critical regulatory incident
            dlqService.createCriticalRegulatoryIncident(correlationId, eventJson, error);

        } catch (Exception notificationError) {
            log.error("CRITICAL: Failed to notify regulatory team - CorrelationId: {}, Error: {}",
                     correlationId, notificationError.getMessage());
        }

        // Acknowledge to prevent infinite retry loops in regulatory systems
        acknowledgment.acknowledge();
    }

    @Override
    protected String getConsumerName() {
        return "RegulatoryFilingDlqConsumer";
    }

    @Override
    protected String getTopicName() {
        return TOPIC_NAME;
    }
}