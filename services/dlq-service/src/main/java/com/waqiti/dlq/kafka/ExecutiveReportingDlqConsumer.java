package com.waqiti.dlq.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseConsumer;
import com.waqiti.common.metrics.MetricsCollector;
import com.waqiti.common.monitoring.CircuitBreaker;
import com.waqiti.dlq.dto.ExecutiveReportingDlqEventDto;
import com.waqiti.dlq.service.ExecutiveReportingDlqService;
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
 * Critical DLQ Consumer for failed executive reporting events.
 * Handles high-priority executive reporting failures with immediate escalation,
 * alternative data source activation, real-time dashboard updates, and executive notification.
 *
 * This consumer processes critical reporting failures that could impact executive
 * decision-making, board reporting, investor relations, or regulatory compliance.
 *
 * @author Waqiti Development Team
 * @version 1.0
 * @since 2024-01-01
 */
@Slf4j
@Component
public class ExecutiveReportingDlqConsumer extends BaseConsumer<ExecutiveReportingDlqEventDto> {

    private static final String TOPIC_NAME = "executive-reporting-dlq";
    private static final String CONSUMER_GROUP = "dlq-service-executive-reporting";
    private static final String ESCALATION_TOPIC = "executive-reporting-escalation";

    private final ExecutiveReportingDlqService dlqService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MetricsCollector metricsCollector;
    private final CircuitBreaker circuitBreaker;

    // Executive Reporting Metrics
    private final Counter dlqEventsProcessedCounter;
    private final Counter dlqEventsFailedCounter;
    private final Counter criticalReportFailuresCounter;
    private final Counter executiveEscalationsCounter;
    private final Counter alternativeDataSourceActivationsCounter;
    private final Counter emergencyReportGenerationsCounter;
    private final Timer dlqProcessingTimer;
    private final Timer reportRecoveryTimer;

    @Autowired
    public ExecutiveReportingDlqConsumer(
            ExecutiveReportingDlqService dlqService,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            MetricsCollector metricsCollector,
            MeterRegistry meterRegistry) {

        this.dlqService = dlqService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.metricsCollector = metricsCollector;
        this.circuitBreaker = new CircuitBreaker("executive-reporting-dlq", 3, Duration.ofMinutes(1));

        // Initialize executive reporting metrics
        this.dlqEventsProcessedCounter = Counter.builder("executive_reporting_dlq_events_processed_total")
                .description("Total number of executive reporting DLQ events processed")
                .tag("service", "dlq")
                .tag("consumer", "executive-reporting")
                .register(meterRegistry);

        this.dlqEventsFailedCounter = Counter.builder("executive_reporting_dlq_events_failed_total")
                .description("Total number of failed executive reporting DLQ events")
                .tag("service", "dlq")
                .tag("consumer", "executive-reporting")
                .register(meterRegistry);

        this.criticalReportFailuresCounter = Counter.builder("critical_report_failures_total")
                .description("Total number of critical report failures")
                .tag("service", "dlq")
                .register(meterRegistry);

        this.executiveEscalationsCounter = Counter.builder("executive_escalations_total")
                .description("Total number of executive escalations")
                .tag("service", "dlq")
                .tag("type", "executive-reporting")
                .register(meterRegistry);

        this.alternativeDataSourceActivationsCounter = Counter.builder("alternative_data_source_activations_total")
                .description("Total number of alternative data source activations")
                .tag("service", "dlq")
                .register(meterRegistry);

        this.emergencyReportGenerationsCounter = Counter.builder("emergency_report_generations_total")
                .description("Total number of emergency report generations")
                .tag("service", "dlq")
                .register(meterRegistry);

        this.dlqProcessingTimer = Timer.builder("executive_reporting_dlq_processing_duration")
                .description("Time taken to process executive reporting DLQ events")
                .tag("service", "dlq")
                .register(meterRegistry);

        this.reportRecoveryTimer = Timer.builder("report_recovery_duration")
                .description("Time taken for report recovery")
                .tag("service", "dlq")
                .register(meterRegistry);
    }

    /**
     * Processes executive reporting DLQ events with immediate priority and executive notification.
     *
     * @param eventJson The JSON representation of the executive reporting DLQ event
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
        maxAttempts = 2, // Reduced retries for immediate executive notification
        backoff = @Backoff(delay = 300, multiplier = 2)
    )
    public void processExecutiveReportingDlqEvent(
            String eventJson,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start();
        String correlationId = UUID.randomUUID().toString();

        try {
            log.error("CRITICAL: Processing failed executive reporting event - CorrelationId: {}, Key: {}, Partition: {}, Offset: {}",
                    correlationId, key, partition, timestamp);

            // Deserialize DLQ event
            ExecutiveReportingDlqEventDto dlqEvent = deserializeEvent(eventJson, correlationId);
            if (dlqEvent == null) {
                return;
            }

            // Validate DLQ event
            validateEvent(dlqEvent, correlationId);

            // Process with circuit breaker
            circuitBreaker.executeSupplier(() -> {
                processExecutiveReportingFailure(dlqEvent, correlationId);
                return null;
            });

            // Track metrics
            dlqEventsProcessedCounter.increment();
            metricsCollector.recordEventProcessed(TOPIC_NAME, "success");

            // Acknowledge message
            acknowledgment.acknowledge();

            log.warn("Executive reporting DLQ event processed - CorrelationId: {}, ReportId: {}, RecoveryAction: {}",
                    correlationId, dlqEvent.getReportId(), dlqEvent.getRecoveryAction());

        } catch (Exception e) {
            handleProcessingError(eventJson, key, correlationId, e, acknowledgment);
        } finally {
            sample.stop(dlqProcessingTimer);
        }
    }

    /**
     * Processes executive reporting failures with comprehensive business continuity measures.
     */
    private void processExecutiveReportingFailure(ExecutiveReportingDlqEventDto dlqEvent, String correlationId) {
        Timer.Sample recoveryTimer = Timer.start();

        try {
            log.error("Processing critical executive reporting failure - ReportId: {}, ReportType: {} - CorrelationId: {}",
                    dlqEvent.getReportId(), dlqEvent.getReportType(), correlationId);

            criticalReportFailuresCounter.increment();

            // Immediate business impact assessment
            var businessImpactAssessment = dlqService.assessExecutiveReportingImpact(dlqEvent, correlationId);

            // Process based on report type and criticality
            switch (dlqEvent.getReportType()) {
                case "BOARD_PRESENTATION":
                    processBoardPresentationFailure(dlqEvent, businessImpactAssessment, correlationId);
                    break;
                case "INVESTOR_RELATIONS_REPORT":
                    processInvestorRelationsReportFailure(dlqEvent, businessImpactAssessment, correlationId);
                    break;
                case "REGULATORY_EXECUTIVE_SUMMARY":
                    processRegulatoryExecutiveSummaryFailure(dlqEvent, businessImpactAssessment, correlationId);
                    break;
                case "FINANCIAL_PERFORMANCE_DASHBOARD":
                    processFinancialPerformanceDashboardFailure(dlqEvent, businessImpactAssessment, correlationId);
                    break;
                case "RISK_MANAGEMENT_SUMMARY":
                    processRiskManagementSummaryFailure(dlqEvent, businessImpactAssessment, correlationId);
                    break;
                case "STRATEGIC_METRICS_REPORT":
                    processStrategicMetricsReportFailure(dlqEvent, businessImpactAssessment, correlationId);
                    break;
                case "OPERATIONAL_EXCELLENCE_REPORT":
                    processOperationalExcellenceReportFailure(dlqEvent, businessImpactAssessment, correlationId);
                    break;
                case "CUSTOMER_INSIGHTS_EXECUTIVE_SUMMARY":
                    processCustomerInsightsExecutiveSummaryFailure(dlqEvent, businessImpactAssessment, correlationId);
                    break;
                case "COMPETITIVE_ANALYSIS_REPORT":
                    processCompetitiveAnalysisReportFailure(dlqEvent, businessImpactAssessment, correlationId);
                    break;
                default:
                    processGenericExecutiveReportFailure(dlqEvent, businessImpactAssessment, correlationId);
            }

            // Executive notification for critical reports
            if (businessImpactAssessment.requiresExecutiveNotification()) {
                initiateExecutiveNotification(dlqEvent, businessImpactAssessment, correlationId);
            }

            // Update executive dashboards with failure status
            updateExecutiveDashboards(dlqEvent, businessImpactAssessment, correlationId);

            // Prepare alternative reporting if needed
            if (businessImpactAssessment.requiresAlternativeReporting()) {
                prepareAlternativeReporting(dlqEvent, businessImpactAssessment, correlationId);
            }

        } finally {
            recoveryTimer.stop(reportRecoveryTimer);
        }
    }

    /**
     * Processes board presentation failures with immediate alternative preparation.
     */
    private void processBoardPresentationFailure(ExecutiveReportingDlqEventDto dlqEvent,
                                               var businessImpactAssessment, String correlationId) {
        log.error("CRITICAL: Board presentation failure - CorrelationId: {}, BoardMeeting: {}",
                correlationId, dlqEvent.getBoardMeetingId());

        // Check board meeting proximity
        var boardMeetingProximity = dlqService.assessBoardMeetingProximity(dlqEvent, correlationId);

        if (boardMeetingProximity.isWithin24Hours()) {
            log.error("Board meeting within 24 hours - Activating emergency protocols - CorrelationId: {}", correlationId);

            // Activate emergency board presentation protocol
            var emergencyProtocol = dlqService.activateEmergencyBoardPresentationProtocol(dlqEvent, correlationId);
            emergencyReportGenerationsCounter.increment();

            // Generate alternative data sources for presentation
            var alternativeDataSources = dlqService.activateAlternativeBoardDataSources(dlqEvent, correlationId);
            alternativeDataSourceActivationsCounter.increment();

            // Create emergency board presentation
            var emergencyPresentation = dlqService.createEmergencyBoardPresentation(dlqEvent, alternativeDataSources, correlationId);

            if (emergencyPresentation.isSuccessful()) {
                log.info("Emergency board presentation created - CorrelationId: {}, PresentationId: {}",
                        correlationId, emergencyPresentation.getPresentationId());

                // Notify board members of alternative presentation
                dlqService.notifyBoardMembersOfAlternativePresentation(dlqEvent, emergencyPresentation, correlationId);
            } else {
                // Escalate to CEO/CFO immediately
                dlqService.escalateToCeoAndCfo(dlqEvent, emergencyPresentation, correlationId);
                executiveEscalationsCounter.increment();
            }
        } else {
            // Standard board presentation recovery
            var recoveryResult = dlqService.attemptBoardPresentationRecovery(dlqEvent, correlationId);

            if (!recoveryResult.isSuccessful()) {
                // Schedule emergency data recovery session
                dlqService.scheduleEmergencyDataRecoverySession(dlqEvent, recoveryResult, correlationId);
            }
        }
    }

    /**
     * Processes investor relations report failures with investor communication protocols.
     */
    private void processInvestorRelationsReportFailure(ExecutiveReportingDlqEventDto dlqEvent,
                                                     var businessImpactAssessment, String correlationId) {
        log.error("Critical investor relations report failure - CorrelationId: {}, ReportingPeriod: {}",
                correlationId, dlqEvent.getReportingPeriod());

        // Check SEC filing deadlines
        var secFilingProximity = dlqService.assessSecFilingProximity(dlqEvent, correlationId);

        if (secFilingProximity.isWithinFilingDeadline()) {
            log.error("SEC filing deadline approaching - Activating emergency IR protocols - CorrelationId: {}", correlationId);

            // Activate alternative financial data sources
            var alternativeFinancialData = dlqService.activateAlternativeFinancialDataSources(dlqEvent, correlationId);
            alternativeDataSourceActivationsCounter.increment();

            // Generate emergency investor report
            var emergencyReport = dlqService.generateEmergencyInvestorReport(dlqEvent, alternativeFinancialData, correlationId);
            emergencyReportGenerationsCounter.increment();

            if (emergencyReport.isSuccessful()) {
                // Validate against SEC requirements
                var secValidation = dlqService.validateAgainstSecRequirements(emergencyReport, correlationId);

                if (secValidation.isCompliant()) {
                    // Proceed with emergency filing
                    dlqService.proceedWithEmergencyFiling(dlqEvent, emergencyReport, correlationId);
                } else {
                    // Escalate to legal and compliance
                    dlqService.escalateToLegalAndCompliance(dlqEvent, secValidation, correlationId);
                    executiveEscalationsCounter.increment();
                }
            } else {
                // Emergency escalation to IR team and CFO
                dlqService.escalateToIrTeamAndCfo(dlqEvent, emergencyReport, correlationId);
                executiveEscalationsCounter.increment();
            }
        } else {
            // Standard IR report recovery
            var recoveryResult = dlqService.attemptInvestorReportRecovery(dlqEvent, correlationId);

            if (!recoveryResult.isSuccessful()) {
                dlqService.escalateToInvestorRelationsTeam(dlqEvent, recoveryResult, correlationId);
            }
        }
    }

    /**
     * Processes financial performance dashboard failures with real-time metrics recovery.
     */
    private void processFinancialPerformanceDashboardFailure(ExecutiveReportingDlqEventDto dlqEvent,
                                                           var businessImpactAssessment, String correlationId) {
        log.error("Critical financial performance dashboard failure - CorrelationId: {}", correlationId);

        // Assess real-time financial monitoring impact
        var realTimeImpact = dlqService.assessRealTimeFinancialMonitoringImpact(dlqEvent, correlationId);

        if (realTimeImpact.affectsCriticalMetrics()) {
            // Activate backup financial monitoring systems
            var backupSystems = dlqService.activateBackupFinancialMonitoringSystems(dlqEvent, correlationId);
            alternativeDataSourceActivationsCounter.increment();

            if (backupSystems.isSuccessful()) {
                // Regenerate critical financial metrics
                var metricsRecovery = dlqService.regenerateCriticalFinancialMetrics(dlqEvent, backupSystems, correlationId);

                if (metricsRecovery.isSuccessful()) {
                    log.info("Financial metrics recovered using backup systems - CorrelationId: {}", correlationId);

                    // Update executive dashboards with recovered data
                    dlqService.updateExecutiveDashboardsWithRecoveredData(dlqEvent, metricsRecovery, correlationId);
                } else {
                    // Manual financial data collection
                    dlqService.initiateManualFinancialDataCollection(dlqEvent, metricsRecovery, correlationId);
                    executiveEscalationsCounter.increment();
                }
            } else {
                // Emergency financial monitoring protocol
                dlqService.activateEmergencyFinancialMonitoringProtocol(dlqEvent, correlationId);
                emergencyReportGenerationsCounter.increment();
            }
        }

        // Notify finance executives of dashboard status
        dlqService.notifyFinanceExecutivesOfDashboardStatus(dlqEvent, realTimeImpact, correlationId);
    }

    /**
     * Processes risk management summary failures with risk monitoring continuity.
     */
    private void processRiskManagementSummaryFailure(ExecutiveReportingDlqEventDto dlqEvent,
                                                   var businessImpactAssessment, String correlationId) {
        log.error("Critical risk management summary failure - CorrelationId: {}", correlationId);

        // Assess risk monitoring continuity impact
        var riskMonitoringImpact = dlqService.assessRiskMonitoringContinuityImpact(dlqEvent, correlationId);

        if (riskMonitoringImpact.affectsCriticalRiskMetrics()) {
            // Activate alternative risk data sources
            var alternativeRiskSources = dlqService.activateAlternativeRiskDataSources(dlqEvent, correlationId);
            alternativeDataSourceActivationsCounter.increment();

            // Generate emergency risk summary
            var emergencyRiskSummary = dlqService.generateEmergencyRiskSummary(dlqEvent, alternativeRiskSources, correlationId);
            emergencyReportGenerationsCounter.increment();

            if (emergencyRiskSummary.isSuccessful()) {
                // Validate risk thresholds and alerts
                var riskValidation = dlqService.validateRiskThresholdsAndAlerts(emergencyRiskSummary, correlationId);

                if (riskValidation.hasThresholdBreaches()) {
                    // Immediate escalation to CRO
                    dlqService.escalateToChiefRiskOfficer(dlqEvent, riskValidation, correlationId);
                    executiveEscalationsCounter.increment();
                }

                // Update risk management dashboards
                dlqService.updateRiskManagementDashboards(dlqEvent, emergencyRiskSummary, correlationId);
            } else {
                // Critical escalation - risk monitoring compromised
                dlqService.escalateRiskMonitoringCompromise(dlqEvent, emergencyRiskSummary, correlationId);
                executiveEscalationsCounter.increment();
            }
        }
    }

    /**
     * Initiates executive notification for critical report failures.
     */
    private void initiateExecutiveNotification(ExecutiveReportingDlqEventDto dlqEvent,
                                             var businessImpactAssessment, String correlationId) {
        log.error("EXECUTIVE NOTIFICATION: Critical executive reporting failure - CorrelationId: {}, Impact: {}",
                correlationId, businessImpactAssessment.getImpactLevel());

        executiveEscalationsCounter.increment();

        // Determine notification recipients based on report type and impact
        var notificationRecipients = dlqService.determineExecutiveNotificationRecipients(dlqEvent, businessImpactAssessment);

        // Send immediate notifications
        dlqService.sendImmediateExecutiveNotifications(dlqEvent, notificationRecipients, correlationId);

        // Schedule executive briefing if required
        if (businessImpactAssessment.requiresExecutiveBriefing()) {
            dlqService.scheduleEmergencyExecutiveBriefing(dlqEvent, businessImpactAssessment, correlationId);
        }

        // Create executive dashboard alert
        dlqService.createExecutiveDashboardAlert(dlqEvent, businessImpactAssessment, correlationId);

        // Send escalation event
        try {
            kafkaTemplate.send(ESCALATION_TOPIC, dlqEvent.getReportId(), Map.of(
                "escalationType", "EXECUTIVE_REPORTING_FAILURE",
                "reportId", dlqEvent.getReportId(),
                "reportType", dlqEvent.getReportType(),
                "businessImpact", businessImpactAssessment.getImpactLevel(),
                "correlationId", correlationId,
                "timestamp", Instant.now().toString(),
                "requiresExecutiveAttention", true
            ));
        } catch (Exception e) {
            log.error("Failed to send executive escalation - CorrelationId: {}, Error: {}",
                     correlationId, e.getMessage());
        }
    }

    /**
     * Updates executive dashboards with report failure status and alternative data.
     */
    private void updateExecutiveDashboards(ExecutiveReportingDlqEventDto dlqEvent,
                                         var businessImpactAssessment, String correlationId) {
        // Update dashboard status indicators
        dlqService.updateDashboardStatusIndicators(dlqEvent, businessImpactAssessment, correlationId);

        // Show alternative data sources being used
        dlqService.showAlternativeDataSourcesInDashboard(dlqEvent, correlationId);

        // Display estimated recovery time
        dlqService.displayEstimatedRecoveryTime(dlqEvent, businessImpactAssessment, correlationId);

        // Add executive alerts for failed reports
        dlqService.addExecutiveAlertsForFailedReports(dlqEvent, correlationId);
    }

    /**
     * Prepares alternative reporting mechanisms for critical business continuity.
     */
    private void prepareAlternativeReporting(ExecutiveReportingDlqEventDto dlqEvent,
                                           var businessImpactAssessment, String correlationId) {
        log.info("Preparing alternative reporting - CorrelationId: {}, ReportType: {}",
                correlationId, dlqEvent.getReportType());

        // Identify alternative data sources
        var alternativeDataSources = dlqService.identifyAlternativeDataSources(dlqEvent, correlationId);

        // Generate alternative report format
        var alternativeReport = dlqService.generateAlternativeReportFormat(dlqEvent, alternativeDataSources, correlationId);

        if (alternativeReport.isSuccessful()) {
            // Distribute alternative report to stakeholders
            dlqService.distributeAlternativeReportToStakeholders(dlqEvent, alternativeReport, correlationId);

            // Update stakeholders about alternative reporting procedures
            dlqService.updateStakeholdersAboutAlternativeReporting(dlqEvent, alternativeReport, correlationId);
        } else {
            // Escalate alternative reporting failure
            dlqService.escalateAlternativeReportingFailure(dlqEvent, alternativeReport, correlationId);
        }
    }

    /**
     * Deserializes the DLQ event JSON into an ExecutiveReportingDlqEventDto.
     */
    private ExecutiveReportingDlqEventDto deserializeEvent(String eventJson, String correlationId) {
        try {
            return objectMapper.readValue(eventJson, ExecutiveReportingDlqEventDto.class);
        } catch (Exception e) {
            log.error("Failed to deserialize executive reporting DLQ event - CorrelationId: {}, Error: {}",
                     correlationId, e.getMessage(), e);
            dlqEventsFailedCounter.increment();
            metricsCollector.recordEventProcessed(TOPIC_NAME, "deserialization_error");
            return null;
        }
    }

    /**
     * Validates the executive reporting DLQ event.
     */
    private void validateEvent(ExecutiveReportingDlqEventDto dlqEvent, String correlationId) {
        if (dlqEvent.getReportId() == null || dlqEvent.getReportId().trim().isEmpty()) {
            throw new IllegalArgumentException("Report ID is required for executive reporting DLQ events");
        }

        if (dlqEvent.getReportType() == null || dlqEvent.getReportType().trim().isEmpty()) {
            throw new IllegalArgumentException("Report type is required");
        }

        if (dlqEvent.getFailureTimestamp() == null) {
            throw new IllegalArgumentException("Failure timestamp is required");
        }

        if (dlqEvent.getExecutiveCriticality() == null) {
            throw new IllegalArgumentException("Executive criticality assessment is required");
        }

        // Validate time sensitivity
        Duration timeSinceFailure = Duration.between(dlqEvent.getFailureTimestamp(), Instant.now());
        if (timeSinceFailure.toMinutes() > 30) {
            log.warn("Executive reporting DLQ event is older than 30 minutes - CorrelationId: {}, Age: {} minutes",
                    correlationId, timeSinceFailure.toMinutes());
        }

        // Validate business deadlines
        if (dlqEvent.hasBusinessDeadline() && dlqEvent.getBusinessDeadline().isBefore(Instant.now())) {
            log.error("Executive report deadline has passed - CorrelationId: {}, Deadline: {}",
                    correlationId, dlqEvent.getBusinessDeadline());
        }
    }

    /**
     * Handles processing errors with executive escalation procedures.
     */
    private void handleProcessingError(String eventJson, String key, String correlationId,
                                     Exception error, Acknowledgment acknowledgment) {
        log.error("CRITICAL: Failed to process executive reporting DLQ event - CorrelationId: {}, Key: {}, Error: {}",
                 correlationId, key, error.getMessage(), error);

        dlqEventsFailedCounter.increment();
        metricsCollector.recordEventProcessed(TOPIC_NAME, "error");

        // Emergency notification for executive reporting processing failures
        try {
            dlqService.notifyExecutiveTeamOfProcessingFailure(correlationId, error);

            // Create critical incident for executive reporting failure
            dlqService.createCriticalExecutiveReportingIncident(correlationId, eventJson, error);

        } catch (Exception notificationError) {
            log.error("CRITICAL: Failed to notify executive team - CorrelationId: {}, Error: {}",
                     correlationId, notificationError.getMessage());
        }

        // Acknowledge to prevent infinite retry loops
        acknowledgment.acknowledge();
    }

    @Override
    protected String getConsumerName() {
        return "ExecutiveReportingDlqConsumer";
    }

    @Override
    protected String getTopicName() {
        return TOPIC_NAME;
    }
}