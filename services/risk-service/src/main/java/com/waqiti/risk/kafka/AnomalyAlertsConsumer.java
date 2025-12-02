package com.waqiti.risk.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.risk.service.*;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.metrics.MetricsService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-grade Kafka consumer for anomaly alerts
 * Handles anomaly detection alerts with risk assessment, threat analysis,
 * and automated response workflows for real-time risk management
 * 
 * Critical for: Risk management, anomaly detection, threat response
 * SLA: Must process anomaly alerts within 8 seconds for timely risk assessment
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AnomalyAlertsConsumer {

    private final AnomalyAlertService anomalyAlertService;
    private final RiskAssessmentService riskAssessmentService;
    private final ThreatAnalysisService threatAnalysisService;
    private final RiskMitigationService riskMitigationService;
    private final RiskNotificationService riskNotificationService;
    private final RiskReportingService riskReportingService;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final ObjectMapper objectMapper;

    // Idempotency cache with 24-hour TTL
    private final Map<String, Instant> processedEventIds = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_HOURS = 24;

    // Metrics
    private final Counter anomalyAlertsCounter = Counter.builder("anomaly_alerts_processed_total")
            .description("Total number of anomaly alerts processed")
            .register(metricsService.getMeterRegistry());

    private final Counter highRiskAlertsCounter = Counter.builder("high_risk_anomaly_alerts_total")
            .description("Total number of high risk anomaly alerts processed")
            .register(metricsService.getMeterRegistry());

    private final Counter processingFailuresCounter = Counter.builder("anomaly_alerts_processing_failed_total")
            .description("Total number of anomaly alert events that failed processing")
            .register(metricsService.getMeterRegistry());

    private final Timer processingTimer = Timer.builder("anomaly_alert_processing_duration")
            .description("Time taken to process anomaly alert events")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = {"anomaly-alerts"},
        groupId = "risk-service-anomaly-alerts-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "anomaly-alerts-processor", fallbackMethod = "handleAnomalyAlertProcessingFailure")
    @Retry(name = "anomaly-alerts-processor")
    public void processAnomalyAlert(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String eventId = event.getEventId();
        
        log.info("Processing anomaly alert: {} from topic: {} partition: {} offset: {}", 
                eventId, topic, partition, offset);

        try {
            // Check idempotency
            if (isEventAlreadyProcessed(eventId)) {
                log.info("Anomaly alert event {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }

            // Extract and validate anomaly alert data
            AnomalyAlertData alertData = extractAnomalyAlertData(event.getPayload());
            validateAnomalyAlert(alertData);

            // Mark as processed for idempotency
            markEventAsProcessed(eventId);

            // Process anomaly alert
            processAnomaly(alertData, event);

            // Record successful processing metrics
            anomalyAlertsCounter.increment();
            
            if ("HIGH".equals(alertData.getRiskLevel()) || "CRITICAL".equals(alertData.getRiskLevel())) {
                highRiskAlertsCounter.increment();
            }
            
            // Audit the alert processing
            auditAnomalyAlertProcessing(alertData, event, "SUCCESS");

            log.info("Successfully processed anomaly alert: {} for account: {} - risk level: {} confidence: {}", 
                    eventId, alertData.getAccountId(), alertData.getRiskLevel(), alertData.getConfidenceScore());

            acknowledgment.acknowledge();

        } catch (IllegalArgumentException e) {
            log.error("Invalid anomaly alert event data: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process anomaly alert event: {}", eventId, e);
            processingFailuresCounter.increment();
            auditAnomalyAlertProcessing(null, event, "FAILED: " + e.getMessage());
            throw new RuntimeException("Anomaly alert event processing failed", e);

        } finally {
            sample.stop(processingTimer);
            cleanupIdempotencyCache();
        }
    }

    private boolean isEventAlreadyProcessed(String eventId) {
        Instant processedTime = processedEventIds.get(eventId);
        if (processedTime != null) {
            if (ChronoUnit.HOURS.between(processedTime, Instant.now()) < IDEMPOTENCY_TTL_HOURS) {
                return true;
            } else {
                processedEventIds.remove(eventId);
            }
        }
        return false;
    }

    private void markEventAsProcessed(String eventId) {
        processedEventIds.put(eventId, Instant.now());
    }

    private void cleanupIdempotencyCache() {
        Instant cutoff = Instant.now().minus(IDEMPOTENCY_TTL_HOURS, ChronoUnit.HOURS);
        processedEventIds.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }

    private AnomalyAlertData extractAnomalyAlertData(Map<String, Object> payload) throws JsonProcessingException {
        return AnomalyAlertData.builder()
                .alertId(extractString(payload, "alertId"))
                .anomalyId(extractString(payload, "anomalyId"))
                .accountId(extractString(payload, "accountId"))
                .userId(extractString(payload, "userId"))
                .anomalyType(extractString(payload, "anomalyType"))
                .riskLevel(extractString(payload, "riskLevel"))
                .confidenceScore(extractDouble(payload, "confidenceScore"))
                .severityLevel(extractString(payload, "severityLevel"))
                .description(extractString(payload, "description"))
                .detectionMethod(extractString(payload, "detectionMethod"))
                .detectedFeatures(extractStringList(payload, "detectedFeatures"))
                .anomalyScore(extractDouble(payload, "anomalyScore"))
                .baselineValue(extractDouble(payload, "baselineValue"))
                .actualValue(extractDouble(payload, "actualValue"))
                .deviationPercent(extractDouble(payload, "deviationPercent"))
                .transactionAmount(extractBigDecimal(payload, "transactionAmount"))
                .transactionCurrency(extractString(payload, "transactionCurrency"))
                .locationInfo(extractMap(payload, "locationInfo"))
                .deviceInfo(extractMap(payload, "deviceInfo"))
                .timeWindow(extractString(payload, "timeWindow"))
                .historicalContext(extractMap(payload, "historicalContext"))
                .relatedEvents(extractStringList(payload, "relatedEvents"))
                .detectionTimestamp(extractInstant(payload, "detectionTimestamp"))
                .alertTimestamp(extractInstant(payload, "alertTimestamp"))
                .sourceSystem(extractString(payload, "sourceSystem"))
                .businessImpact(extractString(payload, "businessImpact"))
                .recommendedActions(extractStringList(payload, "recommendedActions"))
                .build();
    }

    private void validateAnomalyAlert(AnomalyAlertData alertData) {
        if (alertData.getAlertId() == null || alertData.getAlertId().trim().isEmpty()) {
            throw new IllegalArgumentException("Alert ID is required");
        }
        
        if (alertData.getAnomalyId() == null || alertData.getAnomalyId().trim().isEmpty()) {
            throw new IllegalArgumentException("Anomaly ID is required");
        }
        
        if (alertData.getAccountId() == null || alertData.getAccountId().trim().isEmpty()) {
            throw new IllegalArgumentException("Account ID is required");
        }
        
        if (alertData.getAnomalyType() == null || alertData.getAnomalyType().trim().isEmpty()) {
            throw new IllegalArgumentException("Anomaly type is required");
        }
        
        if (alertData.getRiskLevel() == null || alertData.getRiskLevel().trim().isEmpty()) {
            throw new IllegalArgumentException("Risk level is required");
        }

        List<String> validRiskLevels = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
        if (!validRiskLevels.contains(alertData.getRiskLevel())) {
            throw new IllegalArgumentException("Invalid risk level: " + alertData.getRiskLevel());
        }

        if (alertData.getConfidenceScore() == null || 
            alertData.getConfidenceScore() < 0.0 || alertData.getConfidenceScore() > 1.0) {
            throw new IllegalArgumentException("Confidence score must be between 0.0 and 1.0");
        }
        
        if (alertData.getDetectionTimestamp() == null) {
            throw new IllegalArgumentException("Detection timestamp is required");
        }
    }

    private void processAnomaly(AnomalyAlertData alertData, GenericKafkaEvent event) {
        log.info("Processing anomaly alert - Type: {}, Risk: {}, Confidence: {}, Account: {}, Amount: {}", 
                alertData.getAnomalyType(), alertData.getRiskLevel(), alertData.getConfidenceScore(),
                alertData.getAccountId(), alertData.getTransactionAmount());

        try {
            // Record anomaly alert
            String alertRecordId = anomalyAlertService.recordAnomalyAlert(alertData);

            // Perform comprehensive risk assessment
            RiskAssessmentResult riskAssessment = performRiskAssessment(alertData);

            // Conduct threat analysis
            ThreatAnalysisResult threatAnalysis = performThreatAnalysis(alertData, riskAssessment);

            // Apply risk mitigation measures
            RiskMitigationResult mitigation = applyRiskMitigation(alertData, riskAssessment, threatAnalysis);

            // Send notifications based on risk level
            sendAnomalyNotifications(alertData, riskAssessment, threatAnalysis);

            // Update risk monitoring and dashboards
            updateRiskMonitoring(alertData, riskAssessment);

            // Generate risk reports if needed
            generateRiskReports(alertData, riskAssessment, threatAnalysis);

            // Create follow-up actions
            createFollowUpActions(alertData, riskAssessment, mitigation);

            log.info("Anomaly alert processed - AlertId: {}, RecordId: {}, ThreatLevel: {}, MitigationActions: {}", 
                    alertData.getAlertId(), alertRecordId, threatAnalysis.getThreatLevel(), 
                    mitigation.getAppliedActions().size());

        } catch (Exception e) {
            log.error("Failed to process anomaly alert for account: {}", 
                    alertData.getAccountId(), e);
            
            // Apply emergency risk mitigation
            applyEmergencyRiskMitigation(alertData, e);
            
            throw new RuntimeException("Anomaly alert processing failed", e);
        }
    }

    private RiskAssessmentResult performRiskAssessment(AnomalyAlertData alertData) {
        // Comprehensive risk assessment considering multiple factors
        RiskFactors riskFactors = riskAssessmentService.calculateRiskFactors(
                alertData.getAccountId(),
                alertData.getAnomalyType(),
                alertData.getConfidenceScore(),
                alertData.getTransactionAmount(),
                alertData.getHistoricalContext()
        );

        // Account-specific risk profile analysis
        AccountRiskProfile accountRisk = riskAssessmentService.getAccountRiskProfile(
                alertData.getAccountId()
        );

        // Contextual risk analysis
        ContextualRiskAnalysis contextualRisk = riskAssessmentService.analyzeContextualRisk(
                alertData.getLocationInfo(),
                alertData.getDeviceInfo(),
                alertData.getTimeWindow()
        );

        // Calculate overall risk score
        double overallRiskScore = riskAssessmentService.calculateOverallRiskScore(
                riskFactors,
                accountRisk,
                contextualRisk,
                alertData.getAnomalyScore()
        );

        return RiskAssessmentResult.builder()
                .assessmentId(java.util.UUID.randomUUID().toString())
                .alertId(alertData.getAlertId())
                .accountId(alertData.getAccountId())
                .riskFactors(riskFactors)
                .accountRiskProfile(accountRisk)
                .contextualRisk(contextualRisk)
                .overallRiskScore(overallRiskScore)
                .riskLevel(determineRiskLevel(overallRiskScore))
                .assessmentTimestamp(Instant.now())
                .recommendedActions(generateRiskBasedRecommendations(overallRiskScore, riskFactors))
                .build();
    }

    private String determineRiskLevel(double riskScore) {
        if (riskScore >= 0.8) return "CRITICAL";
        if (riskScore >= 0.6) return "HIGH";
        if (riskScore >= 0.4) return "MEDIUM";
        return "LOW";
    }

    private List<String> generateRiskBasedRecommendations(double riskScore, RiskFactors riskFactors) {
        List<String> recommendations = new java.util.ArrayList<>();

        if (riskScore >= 0.8) {
            recommendations.add("IMMEDIATE_ACCOUNT_REVIEW");
            recommendations.add("ENHANCED_TRANSACTION_MONITORING");
            recommendations.add("ESCALATE_TO_RISK_TEAM");
        }

        if (riskScore >= 0.6) {
            recommendations.add("INCREASE_MONITORING_FREQUENCY");
            recommendations.add("APPLY_TRANSACTION_LIMITS");
            recommendations.add("REQUIRE_ADDITIONAL_VERIFICATION");
        }

        if (riskFactors.hasLocationRisk()) {
            recommendations.add("VERIFY_LOCATION_AUTHENTICITY");
        }

        if (riskFactors.hasDeviceRisk()) {
            recommendations.add("DEVICE_SECURITY_CHECK");
        }

        if (recommendations.isEmpty()) {
            recommendations.add("CONTINUE_STANDARD_MONITORING");
        }

        return recommendations;
    }

    private ThreatAnalysisResult performThreatAnalysis(AnomalyAlertData alertData, RiskAssessmentResult riskAssessment) {
        // Analyze threat patterns
        ThreatPatternAnalysis patterns = threatAnalysisService.analyzeThreatPatterns(
                alertData.getAnomalyType(),
                alertData.getDetectedFeatures(),
                alertData.getRelatedEvents()
        );

        // Check for attack signatures
        AttackSignatureAnalysis signatures = threatAnalysisService.analyzeAttackSignatures(
                alertData.getAccountId(),
                alertData.getLocationInfo(),
                alertData.getDeviceInfo(),
                alertData.getHistoricalContext()
        );

        // Assess threat actor profiles
        ThreatActorAssessment actors = threatAnalysisService.assessThreatActors(
                patterns,
                signatures,
                riskAssessment.getContextualRisk()
        );

        // Determine threat level
        String threatLevel = threatAnalysisService.determineThreatLevel(
                patterns,
                signatures,
                actors,
                riskAssessment.getOverallRiskScore()
        );

        return ThreatAnalysisResult.builder()
                .analysisId(java.util.UUID.randomUUID().toString())
                .alertId(alertData.getAlertId())
                .threatPatterns(patterns)
                .attackSignatures(signatures)
                .threatActors(actors)
                .threatLevel(threatLevel)
                .threatScore(calculateThreatScore(patterns, signatures, actors))
                .analysisTimestamp(Instant.now())
                .threatMitigationRecommendations(generateThreatMitigationRecommendations(threatLevel, patterns))
                .build();
    }

    private double calculateThreatScore(ThreatPatternAnalysis patterns, 
                                      AttackSignatureAnalysis signatures, 
                                      ThreatActorAssessment actors) {
        double patternScore = patterns.getConfidenceScore() * 0.4;
        double signatureScore = signatures.getMatchConfidence() * 0.4;
        double actorScore = actors.getThreatLevel() * 0.2;
        
        return Math.min(1.0, patternScore + signatureScore + actorScore);
    }

    private List<String> generateThreatMitigationRecommendations(String threatLevel, ThreatPatternAnalysis patterns) {
        List<String> recommendations = new java.util.ArrayList<>();

        switch (threatLevel) {
            case "CRITICAL":
                recommendations.add("IMMEDIATE_INCIDENT_RESPONSE");
                recommendations.add("ISOLATE_AFFECTED_ACCOUNTS");
                recommendations.add("ACTIVATE_SECURITY_PROTOCOLS");
                break;
                
            case "HIGH":
                recommendations.add("ENHANCED_SECURITY_MONITORING");
                recommendations.add("APPLY_PROTECTIVE_CONTROLS");
                recommendations.add("NOTIFY_SECURITY_TEAM");
                break;
                
            case "MEDIUM":
                recommendations.add("INCREASED_VIGILANCE");
                recommendations.add("MONITOR_RELATED_ACCOUNTS");
                break;
                
            default:
                recommendations.add("STANDARD_MONITORING");
        }

        if (patterns.indicatesAutomatedAttack()) {
            recommendations.add("IMPLEMENT_RATE_LIMITING");
            recommendations.add("ENHANCE_BOT_DETECTION");
        }

        return recommendations;
    }

    private RiskMitigationResult applyRiskMitigation(AnomalyAlertData alertData, 
                                                   RiskAssessmentResult riskAssessment,
                                                   ThreatAnalysisResult threatAnalysis) {
        List<String> appliedActions = new java.util.ArrayList<>();

        // Apply risk-based mitigation measures
        if ("CRITICAL".equals(riskAssessment.getRiskLevel()) || "CRITICAL".equals(threatAnalysis.getThreatLevel())) {
            riskMitigationService.applyCriticalRiskMitigation(alertData.getAccountId());
            appliedActions.add("CRITICAL_RISK_MITIGATION_APPLIED");
        }

        if ("HIGH".equals(riskAssessment.getRiskLevel()) || "HIGH".equals(threatAnalysis.getThreatLevel())) {
            riskMitigationService.applyHighRiskMitigation(alertData.getAccountId());
            appliedActions.add("HIGH_RISK_MITIGATION_APPLIED");
        }

        // Apply anomaly-specific mitigation
        switch (alertData.getAnomalyType()) {
            case "TRANSACTION_AMOUNT_ANOMALY":
                riskMitigationService.applyTransactionLimits(alertData.getAccountId(), alertData.getTransactionAmount());
                appliedActions.add("TRANSACTION_LIMITS_APPLIED");
                break;
                
            case "LOCATION_ANOMALY":
                riskMitigationService.applyLocationBasedControls(alertData.getAccountId(), alertData.getLocationInfo());
                appliedActions.add("LOCATION_CONTROLS_APPLIED");
                break;
                
            case "FREQUENCY_ANOMALY":
                riskMitigationService.applyFrequencyLimits(alertData.getAccountId());
                appliedActions.add("FREQUENCY_LIMITS_APPLIED");
                break;
                
            case "DEVICE_ANOMALY":
                riskMitigationService.applyDeviceControls(alertData.getAccountId(), alertData.getDeviceInfo());
                appliedActions.add("DEVICE_CONTROLS_APPLIED");
                break;
        }

        // Apply threat-specific mitigation
        for (String recommendation : threatAnalysis.getThreatMitigationRecommendations()) {
            try {
                switch (recommendation) {
                    case "IMMEDIATE_INCIDENT_RESPONSE":
                        riskMitigationService.triggerIncidentResponse(alertData.getAccountId(), threatAnalysis);
                        appliedActions.add("INCIDENT_RESPONSE_TRIGGERED");
                        break;
                        
                    case "ENHANCE_SECURITY_MONITORING":
                        riskMitigationService.enhanceSecurityMonitoring(alertData.getAccountId());
                        appliedActions.add("ENHANCED_MONITORING_ENABLED");
                        break;
                        
                    case "IMPLEMENT_RATE_LIMITING":
                        riskMitigationService.implementRateLimiting(alertData.getAccountId());
                        appliedActions.add("RATE_LIMITING_IMPLEMENTED");
                        break;
                }
            } catch (Exception e) {
                log.error("Failed to apply mitigation action: {}", recommendation, e);
            }
        }

        return RiskMitigationResult.builder()
                .mitigationId(java.util.UUID.randomUUID().toString())
                .alertId(alertData.getAlertId())
                .accountId(alertData.getAccountId())
                .appliedActions(appliedActions)
                .mitigationTimestamp(Instant.now())
                .effectivenessScore(calculateMitigationEffectiveness(appliedActions))
                .build();
    }

    private double calculateMitigationEffectiveness(List<String> appliedActions) {
        // Simple effectiveness calculation based on number and type of actions
        double baseEffectiveness = Math.min(1.0, appliedActions.size() * 0.2);
        
        // Boost for critical actions
        if (appliedActions.contains("CRITICAL_RISK_MITIGATION_APPLIED") || 
            appliedActions.contains("INCIDENT_RESPONSE_TRIGGERED")) {
            baseEffectiveness += 0.3;
        }
        
        return Math.min(1.0, baseEffectiveness);
    }

    private void sendAnomalyNotifications(AnomalyAlertData alertData, 
                                        RiskAssessmentResult riskAssessment,
                                        ThreatAnalysisResult threatAnalysis) {
        // Send notifications based on risk and threat levels
        if ("CRITICAL".equals(riskAssessment.getRiskLevel()) || "CRITICAL".equals(threatAnalysis.getThreatLevel())) {
            riskNotificationService.sendCriticalRiskAlert(
                    "Critical Anomaly Detected",
                    alertData,
                    riskAssessment,
                    threatAnalysis
            );
        }

        if ("HIGH".equals(riskAssessment.getRiskLevel()) || "HIGH".equals(threatAnalysis.getThreatLevel())) {
            riskNotificationService.sendHighRiskAlert(
                    "High Risk Anomaly Detected",
                    alertData,
                    riskAssessment
            );
        }

        // Send team notifications
        riskNotificationService.sendTeamNotification(
                "RISK_TEAM",
                "ANOMALY_DETECTED",
                Map.of(
                        "alertId", alertData.getAlertId(),
                        "accountId", alertData.getAccountId(),
                        "riskLevel", riskAssessment.getRiskLevel(),
                        "threatLevel", threatAnalysis.getThreatLevel()
                )
        );

        // Send customer notifications if needed
        if (shouldNotifyCustomer(alertData, riskAssessment)) {
            riskNotificationService.sendCustomerRiskAlert(
                    alertData.getAccountId(),
                    alertData.getAnomalyType(),
                    riskAssessment.getRiskLevel()
            );
        }
    }

    private boolean shouldNotifyCustomer(AnomalyAlertData alertData, RiskAssessmentResult riskAssessment) {
        return ("HIGH".equals(riskAssessment.getRiskLevel()) || "CRITICAL".equals(riskAssessment.getRiskLevel())) &&
               List.of("TRANSACTION_AMOUNT_ANOMALY", "LOCATION_ANOMALY", "DEVICE_ANOMALY")
                       .contains(alertData.getAnomalyType());
    }

    private void updateRiskMonitoring(AnomalyAlertData alertData, RiskAssessmentResult riskAssessment) {
        // Update risk dashboards
        riskReportingService.updateRiskDashboard(
                alertData.getAccountId(),
                alertData.getAnomalyType(),
                riskAssessment.getRiskLevel(),
                riskAssessment.getOverallRiskScore()
        );

        // Update account risk profile
        riskAssessmentService.updateAccountRiskProfile(
                alertData.getAccountId(),
                riskAssessment
        );

        // Update monitoring metrics
        metricsService.recordAnomalyMetrics(
                alertData.getAnomalyType(),
                riskAssessment.getRiskLevel(),
                alertData.getConfidenceScore(),
                riskAssessment.getOverallRiskScore()
        );
    }

    private void generateRiskReports(AnomalyAlertData alertData, 
                                   RiskAssessmentResult riskAssessment,
                                   ThreatAnalysisResult threatAnalysis) {
        // Generate detailed risk report for high-risk cases
        if ("HIGH".equals(riskAssessment.getRiskLevel()) || "CRITICAL".equals(riskAssessment.getRiskLevel())) {
            riskReportingService.generateDetailedRiskReport(
                    alertData,
                    riskAssessment,
                    threatAnalysis
            );
        }

        // Add to daily risk summary
        riskReportingService.addToDailyRiskSummary(
                alertData.getAnomalyType(),
                riskAssessment.getRiskLevel(),
                alertData.getAccountId()
        );
    }

    private void createFollowUpActions(AnomalyAlertData alertData, 
                                     RiskAssessmentResult riskAssessment,
                                     RiskMitigationResult mitigation) {
        // Create follow-up tasks based on risk level
        if ("CRITICAL".equals(riskAssessment.getRiskLevel())) {
            anomalyAlertService.createCriticalFollowUpTask(
                    alertData.getAlertId(),
                    alertData.getAccountId(),
                    "Manual review required within 1 hour"
            );
        }

        if ("HIGH".equals(riskAssessment.getRiskLevel())) {
            anomalyAlertService.createHighPriorityFollowUpTask(
                    alertData.getAlertId(),
                    alertData.getAccountId(),
                    "Review required within 4 hours"
            );
        }

        // Schedule effectiveness review for applied mitigations
        anomalyAlertService.scheduleMitigationEffectivenessReview(
                mitigation.getMitigationId(),
                alertData.getAccountId(),
                java.time.Duration.ofHours(24)
        );
    }

    private void applyEmergencyRiskMitigation(AnomalyAlertData alertData, Exception error) {
        log.error("Applying emergency risk mitigation due to processing failure");
        
        try {
            // Apply conservative risk mitigation
            riskMitigationService.applyEmergencyMitigation(alertData.getAccountId());
            
            // Send emergency alert
            riskNotificationService.sendEmergencyAlert(
                    "Anomaly Processing Failed - Emergency Mitigation Applied",
                    String.format("Failed to process anomaly for account %s: %s", 
                            alertData.getAccountId(), error.getMessage())
            );
            
        } catch (Exception e) {
            log.error("Emergency risk mitigation also failed", e);
        }
    }

    private void handleValidationError(GenericKafkaEvent event, IllegalArgumentException e) {
        log.error("Anomaly alert event validation failed for event: {}", event.getEventId(), e);
        
        auditService.auditSecurityEvent(
                "ANOMALY_ALERT_VALIDATION_ERROR",
                null,
                "Anomaly alert event validation failed: " + e.getMessage(),
                Map.of(
                        "eventId", event.getEventId(),
                        "error", e.getMessage(),
                        "payload", event.getPayload()
                )
        );

        // Send to validation errors topic for analysis
        riskNotificationService.sendValidationErrorAlert(event, e.getMessage());
    }

    private void auditAnomalyAlertProcessing(AnomalyAlertData alertData, GenericKafkaEvent event, String status) {
        try {
            auditService.auditSecurityEvent(
                    "ANOMALY_ALERT_PROCESSED",
                    alertData != null ? alertData.getAccountId() : null,
                    String.format("Anomaly alert event processing %s", status),
                    Map.of(
                            "eventId", event.getEventId(),
                            "alertId", alertData != null ? alertData.getAlertId() : "unknown",
                            "accountId", alertData != null ? alertData.getAccountId() : "unknown",
                            "anomalyType", alertData != null ? alertData.getAnomalyType() : "unknown",
                            "riskLevel", alertData != null ? alertData.getRiskLevel() : "unknown",
                            "status", status,
                            "timestamp", Instant.now()
                    )
            );
        } catch (Exception e) {
            log.error("Failed to audit anomaly alert processing", e);
        }
    }

    @DltHandler
    public void handleDlt(
            @Payload GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "kafka_dlt-original-topic", required = false) String originalTopic) {
        
        log.error("Anomaly alert event sent to DLT - EventId: {}, OriginalTopic: {}", 
                event.getEventId(), originalTopic);

        try {
            AnomalyAlertData alertData = extractAnomalyAlertData(event.getPayload());
            
            // Apply emergency mitigation for DLT events
            riskMitigationService.applyEmergencyMitigation(alertData.getAccountId());

            // Send critical DLT alert
            riskNotificationService.sendCriticalAlert(
                    "CRITICAL: Anomaly Alert in DLT",
                    "Anomaly alert could not be processed - emergency mitigation applied"
            );

            // Audit DLT handling
            auditService.auditSecurityEvent(
                    "ANOMALY_ALERT_DLT",
                    alertData.getAccountId(),
                    "Anomaly alert event sent to Dead Letter Queue - emergency mitigation applied",
                    Map.of(
                            "eventId", event.getEventId(),
                            "alertId", alertData.getAlertId(),
                            "accountId", alertData.getAccountId(),
                            "anomalyType", alertData.getAnomalyType(),
                            "riskLevel", alertData.getRiskLevel(),
                            "originalTopic", originalTopic
                    )
            );

        } catch (Exception e) {
            log.error("Failed to handle anomaly alert DLT event: {}", event.getEventId(), e);
        }
    }

    // Circuit breaker fallback method
    public void handleAnomalyAlertProcessingFailure(GenericKafkaEvent event, String topic, int partition,
                                                   long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("Circuit breaker activated for anomaly alert processing - EventId: {}", 
                event.getEventId(), e);

        try {
            AnomalyAlertData alertData = extractAnomalyAlertData(event.getPayload());
            
            // Apply maximum protection
            riskMitigationService.applyEmergencyMitigation(alertData.getAccountId());

            // Send system alert
            riskNotificationService.sendSystemAlert(
                    "Anomaly Alert Circuit Breaker Open",
                    "Anomaly alert processing is failing - risk management compromised"
            );

        } catch (Exception ex) {
            log.error("Failed to handle anomaly alert circuit breaker fallback", ex);
        }

        acknowledgment.acknowledge();
    }

    // Helper extraction methods
    private String extractString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private Double extractDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Double) return (Double) value;
        if (value instanceof Number) return ((Number) value).doubleValue();
        return Double.parseDouble(value.toString());
    }

    private BigDecimal extractBigDecimal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return new BigDecimal(value.toString());
        return new BigDecimal(value.toString());
    }

    private Instant extractInstant(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Instant) return (Instant) value;
        if (value instanceof Long) return Instant.ofEpochMilli((Long) value);
        return Instant.parse(value.toString());
    }

    @SuppressWarnings("unchecked")
    private List<String> extractStringList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return Map.of();
    }

    // Data classes
    @lombok.Data
    @lombok.Builder
    public static class AnomalyAlertData {
        private String alertId;
        private String anomalyId;
        private String accountId;
        private String userId;
        private String anomalyType;
        private String riskLevel;
        private Double confidenceScore;
        private String severityLevel;
        private String description;
        private String detectionMethod;
        private List<String> detectedFeatures;
        private Double anomalyScore;
        private Double baselineValue;
        private Double actualValue;
        private Double deviationPercent;
        private BigDecimal transactionAmount;
        private String transactionCurrency;
        private Map<String, Object> locationInfo;
        private Map<String, Object> deviceInfo;
        private String timeWindow;
        private Map<String, Object> historicalContext;
        private List<String> relatedEvents;
        private Instant detectionTimestamp;
        private Instant alertTimestamp;
        private String sourceSystem;
        private String businessImpact;
        private List<String> recommendedActions;
    }

    @lombok.Data
    @lombok.Builder
    public static class RiskAssessmentResult {
        private String assessmentId;
        private String alertId;
        private String accountId;
        private RiskFactors riskFactors;
        private AccountRiskProfile accountRiskProfile;
        private ContextualRiskAnalysis contextualRisk;
        private Double overallRiskScore;
        private String riskLevel;
        private Instant assessmentTimestamp;
        private List<String> recommendedActions;
    }

    @lombok.Data
    @lombok.Builder
    public static class ThreatAnalysisResult {
        private String analysisId;
        private String alertId;
        private ThreatPatternAnalysis threatPatterns;
        private AttackSignatureAnalysis attackSignatures;
        private ThreatActorAssessment threatActors;
        private String threatLevel;
        private Double threatScore;
        private Instant analysisTimestamp;
        private List<String> threatMitigationRecommendations;
    }

    @lombok.Data
    @lombok.Builder
    public static class RiskMitigationResult {
        private String mitigationId;
        private String alertId;
        private String accountId;
        private List<String> appliedActions;
        private Instant mitigationTimestamp;
        private Double effectivenessScore;
    }

    // Supporting data classes
    @lombok.Data
    @lombok.Builder
    public static class RiskFactors {
        private Double transactionRisk;
        private Double locationRisk;
        private Double deviceRisk;
        private Double timeRisk;
        private Double historicalRisk;
        
        public boolean hasLocationRisk() {
            return locationRisk != null && locationRisk > 0.5;
        }
        
        public boolean hasDeviceRisk() {
            return deviceRisk != null && deviceRisk > 0.5;
        }
    }

    @lombok.Data
    @lombok.Builder
    public static class AccountRiskProfile {
        private String accountId;
        private String riskTier;
        private Double historicalRiskScore;
        private Integer anomalyCount;
        private String lastAnomalyDate;
    }

    @lombok.Data
    @lombok.Builder
    public static class ContextualRiskAnalysis {
        private Double timeBasedRisk;
        private Double locationBasedRisk;
        private Double deviceBasedRisk;
        private Double behaviorBasedRisk;
    }

    @lombok.Data
    @lombok.Builder
    public static class ThreatPatternAnalysis {
        private List<String> detectedPatterns;
        private Double confidenceScore;
        private String attackType;
        private boolean indicatesAutomatedAttack;
    }

    @lombok.Data
    @lombok.Builder
    public static class AttackSignatureAnalysis {
        private List<String> matchedSignatures;
        private Double matchConfidence;
        private String attackCategory;
    }

    @lombok.Data
    @lombok.Builder
    public static class ThreatActorAssessment {
        private String actorProfile;
        private Double threatLevel;
        private String sophisticationLevel;
        private List<String> motivations;
    }
}