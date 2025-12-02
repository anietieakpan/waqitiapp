package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseConsumer;
import com.waqiti.common.metrics.MetricsCollector;
import com.waqiti.common.monitoring.CircuitBreaker;
import com.waqiti.compliance.dto.RegulatoryBreachEventDto;
import com.waqiti.compliance.service.RegulatoryBreachService;
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
 * Consumer for processing regulatory breach events.
 * Handles violations of banking regulations, compliance framework breaches,
 * regulatory deadline misses, and mandatory reporting requirements.
 *
 * @author Waqiti Development Team
 * @version 1.0
 * @since 2024-01-01
 */
@Slf4j
@Component
public class RegulatoryBreachEventsConsumer extends BaseConsumer<RegulatoryBreachEventDto> {

    private static final String TOPIC_NAME = "regulatory-breach-events";
    private static final String CONSUMER_GROUP = "compliance-service-regulatory-breach";
    private static final String DLQ_TOPIC = "regulatory-breach-events-dlq";

    private final RegulatoryBreachService breachService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MetricsCollector metricsCollector;
    private final CircuitBreaker circuitBreaker;

    // Metrics
    private final Counter eventsProcessedCounter;
    private final Counter eventsFailedCounter;
    private final Counter regulatoryBreachesCounter;
    private final Counter criticalBreachesCounter;
    private final Counter regulatorNotificationsCounter;
    private final Counter remediationActionsCounter;
    private final Timer processingTimer;
    private final Timer breachAssessmentTimer;

    @Autowired
    public RegulatoryBreachEventsConsumer(
            RegulatoryBreachService breachService,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            MetricsCollector metricsCollector,
            MeterRegistry meterRegistry) {

        this.breachService = breachService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.metricsCollector = metricsCollector;
        this.circuitBreaker = new CircuitBreaker("regulatory-breach", 5, Duration.ofMinutes(2));

        // Initialize metrics
        this.eventsProcessedCounter = Counter.builder("regulatory_breach_events_processed_total")
                .description("Total number of regulatory breach events processed")
                .tag("service", "compliance")
                .tag("consumer", "regulatory-breach")
                .register(meterRegistry);

        this.eventsFailedCounter = Counter.builder("regulatory_breach_events_failed_total")
                .description("Total number of failed regulatory breach events")
                .tag("service", "compliance")
                .tag("consumer", "regulatory-breach")
                .register(meterRegistry);

        this.regulatoryBreachesCounter = Counter.builder("regulatory_breaches_total")
                .description("Total number of regulatory breaches detected")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.criticalBreachesCounter = Counter.builder("critical_breaches_total")
                .description("Total number of critical regulatory breaches")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.regulatorNotificationsCounter = Counter.builder("regulator_notifications_total")
                .description("Total number of regulator notifications sent")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.remediationActionsCounter = Counter.builder("remediation_actions_total")
                .description("Total number of remediation actions initiated")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.processingTimer = Timer.builder("regulatory_breach_processing_duration")
                .description("Time taken to process regulatory breach events")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.breachAssessmentTimer = Timer.builder("breach_assessment_duration")
                .description("Time taken for breach assessment")
                .tag("service", "compliance")
                .register(meterRegistry);
    }

    /**
     * Processes regulatory breach events with comprehensive remediation planning.
     *
     * @param eventJson The JSON representation of the regulatory breach event
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
    public void processRegulatoryBreachEvent(
            String eventJson,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start();
        String correlationId = UUID.randomUUID().toString();

        try {
            log.error("Processing regulatory breach event - CorrelationId: {}, Key: {}, Partition: {}, Offset: {}",
                    correlationId, key, partition, timestamp);

            // Deserialize event
            RegulatoryBreachEventDto event = deserializeEvent(eventJson, correlationId);
            if (event == null) {
                return;
            }

            // Validate event
            validateEvent(event, correlationId);

            // Process with circuit breaker
            circuitBreaker.executeSupplier(() -> {
                processBreachEvent(event, correlationId);
                return null;
            });

            // Track metrics
            eventsProcessedCounter.increment();
            metricsCollector.recordEventProcessed(TOPIC_NAME, "success");

            // Acknowledge message
            acknowledgment.acknowledge();

            log.warn("Successfully processed regulatory breach event - CorrelationId: {}, BreachType: {}, Severity: {}",
                    correlationId, event.getBreachType(), event.getSeverity());

        } catch (Exception e) {
            handleProcessingError(eventJson, key, correlationId, e, acknowledgment);
        } finally {
            sample.stop(processingTimer);
        }
    }

    /**
     * Processes the regulatory breach event with comprehensive assessment and remediation.
     */
    private void processBreachEvent(RegulatoryBreachEventDto event, String correlationId) {
        Timer.Sample assessmentTimer = Timer.start();

        try {
            log.warn("Processing regulatory breach - Type: {}, Regulation: {} - CorrelationId: {}",
                    event.getBreachType(), event.getRegulation(), correlationId);

            regulatoryBreachesCounter.increment();

            // Comprehensive breach assessment
            var breachAssessment = breachService.assessBreach(event, correlationId);

            // Process based on breach type
            switch (event.getBreachType()) {
                case "BSA_AML_VIOLATION":
                    processBsaAmlViolation(event, breachAssessment, correlationId);
                    break;
                case "KYC_COMPLIANCE_FAILURE":
                    processKycComplianceFailure(event, breachAssessment, correlationId);
                    break;
                case "CAPITAL_ADEQUACY_BREACH":
                    processCapitalAdequacyBreach(event, breachAssessment, correlationId);
                    break;
                case "LIQUIDITY_REQUIREMENT_BREACH":
                    processLiquidityRequirementBreach(event, breachAssessment, correlationId);
                    break;
                case "CONSUMER_PROTECTION_VIOLATION":
                    processConsumerProtectionViolation(event, breachAssessment, correlationId);
                    break;
                case "DATA_PRIVACY_BREACH":
                    processDataPrivacyBreach(event, breachAssessment, correlationId);
                    break;
                case "REPORTING_DEADLINE_MISSED":
                    processReportingDeadlineMissed(event, breachAssessment, correlationId);
                    break;
                case "OPERATIONAL_RISK_BREACH":
                    processOperationalRiskBreach(event, breachAssessment, correlationId);
                    break;
                case "MARKET_CONDUCT_VIOLATION":
                    processMarketConductViolation(event, breachAssessment, correlationId);
                    break;
                default:
                    processGenericRegulatoryBreach(event, breachAssessment, correlationId);
            }

            // Initiate immediate remediation if critical
            if ("CRITICAL".equals(event.getSeverity()) || breachAssessment.requiresImmediateAction()) {
                initiateImmediateRemediation(event, breachAssessment, correlationId);
                criticalBreachesCounter.increment();
            }

            // Regulatory notification requirements
            handleRegulatoryNotifications(event, breachAssessment, correlationId);

            // Remediation planning
            planRemediationActions(event, breachAssessment, correlationId);

            // Update compliance tracking
            breachService.updateComplianceTracking(event, breachAssessment, correlationId);

        } finally {
            assessmentTimer.stop(breachAssessmentTimer);
        }
    }

    /**
     * Processes BSA/AML regulation violations.
     */
    private void processBsaAmlViolation(RegulatoryBreachEventDto event,
                                      var breachAssessment, String correlationId) {
        log.error("BSA/AML violation detected - CorrelationId: {}, Details: {}",
                correlationId, event.getBreachDetails());

        // Enhanced AML monitoring
        breachService.enhanceAmlMonitoring(event.getAffectedEntities(), correlationId);

        // SAR filing assessment
        if (breachAssessment.requiresSarFiling()) {
            breachService.initiateSarFiling(event, breachAssessment, correlationId);
        }

        // FinCEN notification if required
        if (breachAssessment.requiresFinCenNotification()) {
            breachService.notifyFinCen(event, breachAssessment, correlationId);
            regulatorNotificationsCounter.increment();
        }

        // AML program review
        breachService.scheduleAmlProgramReview(event, correlationId);
    }

    /**
     * Processes KYC compliance failures.
     */
    private void processKycComplianceFailure(RegulatoryBreachEventDto event,
                                           var breachAssessment, String correlationId) {
        log.warn("KYC compliance failure detected - CorrelationId: {}", correlationId);

        // Customer verification remediation
        breachService.initiateCustomerVerificationRemediation(event.getAffectedEntities(), correlationId);

        // Enhanced due diligence
        breachService.applyEnhancedDueDiligence(event.getAffectedEntities(), correlationId);

        // KYC process review
        breachService.reviewKycProcesses(event, breachAssessment, correlationId);

        // Regulator notification if material
        if (breachAssessment.isMaterialWeakness()) {
            breachService.notifyBankingRegulators(event, breachAssessment, correlationId);
            regulatorNotificationsCounter.increment();
        }
    }

    /**
     * Processes capital adequacy breaches.
     */
    private void processCapitalAdequacyBreach(RegulatoryBreachEventDto event,
                                            var breachAssessment, String correlationId) {
        log.error("Capital adequacy breach detected - CorrelationId: {}, Ratio: {}",
                correlationId, event.getMetricValues());

        // Immediate capital assessment
        var capitalAssessment = breachService.assessCapitalPosition(event, correlationId);

        // Prompt corrective action if required
        if (capitalAssessment.requiresPromptCorrectiveAction()) {
            breachService.initiatePromptCorrectiveAction(event, capitalAssessment, correlationId);
            criticalBreachesCounter.increment();
        }

        // Regulator notification (mandatory)
        breachService.notifyCapitalAdequacyBreach(event, capitalAssessment, correlationId);
        regulatorNotificationsCounter.increment();

        // Capital restoration plan
        breachService.developCapitalRestorationPlan(event, capitalAssessment, correlationId);
        remediationActionsCounter.increment();
    }

    /**
     * Processes liquidity requirement breaches.
     */
    private void processLiquidityRequirementBreach(RegulatoryBreachEventDto event,
                                                 var breachAssessment, String correlationId) {
        log.error("Liquidity requirement breach detected - CorrelationId: {}", correlationId);

        // Liquidity stress testing
        var liquidityStress = breachService.performLiquidityStressTesting(event, correlationId);

        // Liquidity contingency plan activation
        if (liquidityStress.requiresContingencyActivation()) {
            breachService.activateLiquidityContingencyPlan(event, liquidityStress, correlationId);
            criticalBreachesCounter.increment();
        }

        // LCR/NSFR remediation
        breachService.initiateLiquidityRatioRemediation(event, breachAssessment, correlationId);
        remediationActionsCounter.increment();

        // Regulator notification
        breachService.notifyLiquidityBreach(event, breachAssessment, correlationId);
        regulatorNotificationsCounter.increment();
    }

    /**
     * Processes consumer protection violations.
     */
    private void processConsumerProtectionViolation(RegulatoryBreachEventDto event,
                                                  var breachAssessment, String correlationId) {
        log.warn("Consumer protection violation detected - CorrelationId: {}", correlationId);

        // Customer remediation
        breachService.initiateCustomerRemediation(event.getAffectedEntities(), correlationId);

        // CFPB notification if required
        if (breachAssessment.requiresCfpbNotification()) {
            breachService.notifyCfpb(event, breachAssessment, correlationId);
            regulatorNotificationsCounter.increment();
        }

        // Fair lending review
        if (event.getBreachDetails().contains("lending")) {
            breachService.initiateFairLendingReview(event, correlationId);
        }

        // Consumer protection training
        breachService.scheduleConsumerProtectionTraining(event, correlationId);
        remediationActionsCounter.increment();
    }

    /**
     * Processes data privacy breaches.
     */
    private void processDataPrivacyBreach(RegulatoryBreachEventDto event,
                                        var breachAssessment, String correlationId) {
        log.error("Data privacy breach detected - CorrelationId: {}, Records: {}",
                correlationId, event.getAffectedRecordCount());

        // Data breach response
        breachService.activateDataBreachResponse(event, correlationId);

        // Customer notification
        if (breachAssessment.requiresCustomerNotification()) {
            breachService.initiateCustomerBreachNotification(event.getAffectedEntities(), correlationId);
        }

        // Regulatory notification (multiple agencies)
        breachService.notifyDataPrivacyBreach(event, breachAssessment, correlationId);
        regulatorNotificationsCounter.increment();

        // Security enhancement
        breachService.enhanceDataSecurityMeasures(event, correlationId);
        remediationActionsCounter.increment();
    }

    /**
     * Processes reporting deadline misses.
     */
    private void processReportingDeadlineMissed(RegulatoryBreachEventDto event,
                                              var breachAssessment, String correlationId) {
        log.warn("Reporting deadline missed - CorrelationId: {}, Report: {}",
                correlationId, event.getReportType());

        // Expedited reporting
        breachService.expediteReport(event.getReportType(), event.getReportingPeriod(), correlationId);

        // Late filing notification
        breachService.submitLateFiling(event, breachAssessment, correlationId);
        regulatorNotificationsCounter.increment();

        // Process improvement
        breachService.improveReportingProcesses(event, correlationId);
        remediationActionsCounter.increment();

        // Deadline monitoring enhancement
        breachService.enhanceDeadlineMonitoring(event.getReportType(), correlationId);
    }

    /**
     * Processes operational risk breaches.
     */
    private void processOperationalRiskBreach(RegulatoryBreachEventDto event,
                                            var breachAssessment, String correlationId) {
        log.warn("Operational risk breach detected - CorrelationId: {}", correlationId);

        // Risk assessment update
        breachService.updateOperationalRiskAssessment(event, correlationId);

        // Control enhancement
        breachService.enhanceOperationalControls(event, breachAssessment, correlationId);
        remediationActionsCounter.increment();

        // Business continuity review
        if (breachAssessment.affectsBusinessContinuity()) {
            breachService.reviewBusinessContinuityPlan(event, correlationId);
        }

        // Risk reporting
        breachService.updateRiskReporting(event, breachAssessment, correlationId);
    }

    /**
     * Processes market conduct violations.
     */
    private void processMarketConductViolation(RegulatoryBreachEventDto event,
                                             var breachAssessment, String correlationId) {
        log.warn("Market conduct violation detected - CorrelationId: {}", correlationId);

        // Trading review
        breachService.reviewTradingActivities(event, correlationId);

        // Market manipulation assessment
        if (breachAssessment.indicatesMarketManipulation()) {
            breachService.investigateMarketManipulation(event, correlationId);

            // SEC/CFTC notification
            breachService.notifySecCftc(event, breachAssessment, correlationId);
            regulatorNotificationsCounter.increment();
        }

        // Market conduct training
        breachService.scheduleMarketConductTraining(event, correlationId);
        remediationActionsCounter.increment();
    }

    /**
     * Processes generic regulatory breaches.
     */
    private void processGenericRegulatoryBreach(RegulatoryBreachEventDto event,
                                              var breachAssessment, String correlationId) {
        log.warn("Generic regulatory breach detected - CorrelationId: {}", correlationId);

        // Standard breach assessment
        breachService.performStandardBreachAssessment(event, correlationId);

        // Compliance review
        breachService.scheduleComplianceReview(event, correlationId);
        remediationActionsCounter.increment();

        // Generic remediation
        breachService.applyGenericRemediation(event, breachAssessment, correlationId);
    }

    /**
     * Initiates immediate remediation for critical breaches.
     */
    private void initiateImmediateRemediation(RegulatoryBreachEventDto event,
                                            var breachAssessment, String correlationId) {
        log.error("Initiating immediate remediation - CorrelationId: {}", correlationId);

        // Crisis management activation
        breachService.activateCrisisManagement(event, correlationId);

        // Senior management notification
        breachService.notifySeniorManagement(event, breachAssessment, correlationId);

        // Board notification if required
        if (breachAssessment.requiresBoardNotification()) {
            breachService.notifyBoard(event, breachAssessment, correlationId);
        }

        // Emergency response team
        breachService.assembleEmergencyResponseTeam(event, correlationId);
    }

    /**
     * Handles regulatory notification requirements.
     */
    private void handleRegulatoryNotifications(RegulatoryBreachEventDto event,
                                             var breachAssessment, String correlationId) {
        var notificationRequirements = breachService.assessNotificationRequirements(event, breachAssessment);

        for (var requirement : notificationRequirements) {
            switch (requirement.getRegulator()) {
                case "FDIC":
                    breachService.notifyFdic(event, breachAssessment, correlationId);
                    break;
                case "OCC":
                    breachService.notifyOcc(event, breachAssessment, correlationId);
                    break;
                case "FEDERAL_RESERVE":
                    breachService.notifyFederalReserve(event, breachAssessment, correlationId);
                    break;
                case "FINCEN":
                    breachService.notifyFinCen(event, breachAssessment, correlationId);
                    break;
                case "CFPB":
                    breachService.notifyCfpb(event, breachAssessment, correlationId);
                    break;
                case "SEC":
                    breachService.notifySec(event, breachAssessment, correlationId);
                    break;
                default:
                    breachService.notifyGenericRegulator(requirement.getRegulator(), event, correlationId);
            }
            regulatorNotificationsCounter.increment();
        }
    }

    /**
     * Plans comprehensive remediation actions.
     */
    private void planRemediationActions(RegulatoryBreachEventDto event,
                                      var breachAssessment, String correlationId) {
        // Remediation planning
        var remediationPlan = breachService.developRemediationPlan(event, breachAssessment, correlationId);

        // Execute immediate actions
        breachService.executeImmediateActions(remediationPlan, correlationId);

        // Schedule long-term actions
        breachService.scheduleLongTermActions(remediationPlan, correlationId);

        // Monitoring and tracking
        breachService.establishRemediationMonitoring(remediationPlan, correlationId);

        remediationActionsCounter.increment();
    }

    /**
     * Deserializes the event JSON into a RegulatoryBreachEventDto.
     */
    private RegulatoryBreachEventDto deserializeEvent(String eventJson, String correlationId) {
        try {
            return objectMapper.readValue(eventJson, RegulatoryBreachEventDto.class);
        } catch (Exception e) {
            log.error("Failed to deserialize regulatory breach event - CorrelationId: {}, Error: {}",
                     correlationId, e.getMessage(), e);
            eventsFailedCounter.increment();
            metricsCollector.recordEventProcessed(TOPIC_NAME, "deserialization_error");
            return null;
        }
    }

    /**
     * Validates the regulatory breach event.
     */
    private void validateEvent(RegulatoryBreachEventDto event, String correlationId) {
        if (event.getBreachType() == null || event.getBreachType().trim().isEmpty()) {
            throw new IllegalArgumentException("Breach type is required");
        }

        if (event.getRegulation() == null || event.getRegulation().trim().isEmpty()) {
            throw new IllegalArgumentException("Regulation information is required");
        }

        if (event.getSeverity() == null || event.getSeverity().trim().isEmpty()) {
            throw new IllegalArgumentException("Severity level is required");
        }

        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Timestamp is required for regulatory breaches");
        }

        if (event.getBreachDetails() == null || event.getBreachDetails().trim().isEmpty()) {
            throw new IllegalArgumentException("Breach details are required");
        }
    }

    /**
     * Handles processing errors with regulatory compliance logging.
     */
    private void handleProcessingError(String eventJson, String key, String correlationId,
                                     Exception error, Acknowledgment acknowledgment) {
        log.error("Failed to process regulatory breach event - CorrelationId: {}, Key: {}, Error: {}",
                 correlationId, key, error.getMessage(), error);

        eventsFailedCounter.increment();
        metricsCollector.recordEventProcessed(TOPIC_NAME, "error");

        try {
            // Send to DLQ with regulatory priority
            kafkaTemplate.send(DLQ_TOPIC, key, Map.of(
                "originalEvent", eventJson,
                "error", error.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now().toString(),
                "service", "compliance-service",
                "consumer", "regulatory-breach",
                "priority", "HIGH",
                "regulatoryEvent", true
            ));

            log.warn("Sent failed regulatory breach event to DLQ - CorrelationId: {}", correlationId);

        } catch (Exception dlqError) {
            log.error("Failed to send regulatory breach event to DLQ - CorrelationId: {}, Error: {}",
                     correlationId, dlqError.getMessage(), dlqError);
        }

        // Acknowledge to prevent reprocessing
        acknowledgment.acknowledge();
    }

    @Override
    protected String getConsumerName() {
        return "RegulatoryBreachEventsConsumer";
    }

    @Override
    protected String getTopicName() {
        return TOPIC_NAME;
    }
}