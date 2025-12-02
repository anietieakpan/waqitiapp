package com.waqiti.frauddetection.kafka;

import com.waqiti.common.events.FraudActivityLogEvent;
import com.waqiti.frauddetection.domain.FraudActivityLog;
import com.waqiti.frauddetection.repository.FraudActivityLogRepository;
import com.waqiti.frauddetection.service.FraudActivityService;
import com.waqiti.frauddetection.service.FraudAnalyticsService;
import com.waqiti.frauddetection.service.ThreatIntelligenceService;
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
public class FraudActivityLogsConsumer {

    private final FraudActivityLogRepository activityLogRepository;
    private final FraudActivityService activityService;
    private final FraudAnalyticsService analyticsService;
    private final ThreatIntelligenceService threatIntelligenceService;
    private final FraudMetricsService metricsService;
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
        successCounter = Counter.builder("fraud_activity_logs_processed_total")
            .description("Total number of successfully processed fraud activity log events")
            .register(meterRegistry);
        errorCounter = Counter.builder("fraud_activity_logs_errors_total")
            .description("Total number of fraud activity log processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("fraud_activity_logs_processing_duration")
            .description("Time taken to process fraud activity log events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"fraud-activity-logs"},
        groupId = "fraud-activity-logs-service-group",
        containerFactory = "criticalFraudKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 8000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "fraud-activity-logs", fallbackMethod = "handleActivityLogEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 8000))
    public void handleActivityLogEvent(
            @Payload FraudActivityLogEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("fraud-activity-%s-p%d-o%d", event.getActivityId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getActivityId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing fraud activity log: activityId={}, eventType={}, activityType={}, severity={}",
                event.getActivityId(), event.getEventType(), event.getActivityType(), event.getSeverity());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getEventType()) {
                case FRAUD_ACTIVITY_DETECTED:
                    processFraudActivityDetected(event, correlationId);
                    break;

                case SUSPICIOUS_ACTIVITY_LOGGED:
                    processSuspiciousActivityLogged(event, correlationId);
                    break;

                case ACTIVITY_PATTERN_IDENTIFIED:
                    processActivityPatternIdentified(event, correlationId);
                    break;

                case THREAT_INTELLIGENCE_MATCH:
                    processThreatIntelligenceMatch(event, correlationId);
                    break;

                case ACTIVITY_ESCALATED:
                    processActivityEscalated(event, correlationId);
                    break;

                case ACTIVITY_INVESTIGATION_STARTED:
                    processActivityInvestigationStarted(event, correlationId);
                    break;

                case ACTIVITY_INVESTIGATION_COMPLETED:
                    processActivityInvestigationCompleted(event, correlationId);
                    break;

                case ACTIVITY_FALSE_POSITIVE:
                    processActivityFalsePositive(event, correlationId);
                    break;

                case ACTIVITY_CONFIRMED_FRAUD:
                    processActivityConfirmedFraud(event, correlationId);
                    break;

                case ACTIVITY_ARCHIVED:
                    processActivityArchived(event, correlationId);
                    break;

                default:
                    log.warn("Unknown fraud activity log event type: {}", event.getEventType());
                    processUnknownActivityLogEvent(event, correlationId);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logFraudEvent("FRAUD_ACTIVITY_LOG_EVENT_PROCESSED", event.getActivityId(),
                Map.of("eventType", event.getEventType(), "activityType", event.getActivityType(),
                    "severity", event.getSeverity(), "userId", event.getUserId(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process fraud activity log event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("fraud-activity-logs-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleActivityLogEventFallback(
            FraudActivityLogEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("fraud-activity-fallback-%s-p%d-o%d", event.getActivityId(), partition, offset);

        log.error("Circuit breaker fallback triggered for fraud activity log: activityId={}, error={}",
            event.getActivityId(), ex.getMessage());

        // Create security incident for circuit breaker
        activityService.createSecurityIncident(
            "FRAUD_ACTIVITY_LOG_CIRCUIT_BREAKER",
            String.format("Fraud activity log circuit breaker triggered for activity %s", event.getActivityId()),
            "HIGH",
            Map.of("activityId", event.getActivityId(), "eventType", event.getEventType(),
                "activityType", event.getActivityType(), "severity", event.getSeverity(),
                "error", ex.getMessage(), "correlationId", correlationId)
        );

        // Send to dead letter queue
        kafkaTemplate.send("fraud-activity-logs-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Fraud Activity Log Circuit Breaker",
                String.format("Fraud activity log processing failed for activity %s: %s",
                    event.getActivityId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send critical alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltActivityLogEvent(
            @Payload FraudActivityLogEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-fraud-activity-%s-%d", event.getActivityId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Fraud activity log permanently failed: activityId={}, topic={}, error={}",
            event.getActivityId(), topic, exceptionMessage);

        // Create critical security incident
        activityService.createSecurityIncident(
            "FRAUD_ACTIVITY_LOG_DLT_EVENT",
            String.format("Fraud activity log sent to DLT for activity %s", event.getActivityId()),
            "CRITICAL",
            Map.of("activityId", event.getActivityId(), "originalTopic", topic,
                "errorMessage", exceptionMessage, "activityType", event.getActivityType(),
                "severity", event.getSeverity(), "correlationId", correlationId,
                "requiresImmediateAction", true)
        );

        // Save to dead letter store for manual investigation
        auditService.logCriticalFraudEvent("FRAUD_ACTIVITY_LOG_DLT_EVENT", event.getActivityId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send emergency alert
        try {
            notificationService.sendEmergencyAlert(
                "Fraud Activity Log Dead Letter Event",
                String.format("CRITICAL: Fraud activity log for activity %s sent to DLT: %s",
                    event.getActivityId(), exceptionMessage),
                Map.of("activityId", event.getActivityId(), "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send emergency DLT alert: {}", ex.getMessage());
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

    private void processFraudActivityDetected(FraudActivityLogEvent event, String correlationId) {
        // Create fraud activity log record
        FraudActivityLog activityLog = FraudActivityLog.builder()
            .activityId(event.getActivityId())
            .userId(event.getUserId())
            .sessionId(event.getSessionId())
            .activityType(event.getActivityType())
            .severity(event.getSeverity())
            .status("DETECTED")
            .detectedAt(LocalDateTime.now())
            .detectionMethod(event.getDetectionMethod())
            .riskScore(event.getRiskScore())
            .activityDetails(event.getActivityDetails())
            .sourceIp(event.getSourceIp())
            .userAgent(event.getUserAgent())
            .deviceFingerprint(event.getDeviceFingerprint())
            .correlationId(correlationId)
            .build();

        activityLogRepository.save(activityLog);

        // Analyze activity for patterns
        analyticsService.analyzeActivityForPatterns(event.getActivityId(), event.getActivityType(),
            event.getActivityDetails(), event.getRiskScore());

        // Check against threat intelligence
        boolean threatMatch = threatIntelligenceService.checkThreatIntelligence(
            event.getSourceIp(), event.getUserAgent(), event.getDeviceFingerprint());

        if (threatMatch) {
            kafkaTemplate.send("fraud-activity-logs", Map.of(
                "activityId", event.getActivityId(),
                "eventType", "THREAT_INTELLIGENCE_MATCH",
                "activityType", event.getActivityType(),
                "severity", "HIGH",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        // Trigger automated response based on severity
        if (event.getSeverity().equals("HIGH") || event.getSeverity().equals("CRITICAL")) {
            activityService.triggerAutomatedResponse(event.getActivityId(), event.getActivityType(),
                event.getSeverity());

            // Send immediate alert
            notificationService.sendFraudAlert(
                "High Severity Fraud Activity Detected",
                String.format("Fraud activity detected: %s (Risk Score: %d)",
                    event.getActivityType(), event.getRiskScore()),
                "HIGH"
            );
        }

        metricsService.recordFraudActivityDetected(event.getActivityType(), event.getSeverity());

        log.info("Fraud activity detected: activityId={}, type={}, severity={}, riskScore={}",
            event.getActivityId(), event.getActivityType(), event.getSeverity(), event.getRiskScore());
    }

    private void processSuspiciousActivityLogged(FraudActivityLogEvent event, String correlationId) {
        // Update activity log
        FraudActivityLog activityLog = activityLogRepository.findByActivityId(event.getActivityId())
            .orElse(createNewActivityLog(event, correlationId));

        activityLog.setStatus("SUSPICIOUS");
        activityLog.setSuspiciousIndicators(event.getSuspiciousIndicators());
        activityLog.setAnalyzedAt(LocalDateTime.now());
        activityLogRepository.save(activityLog);

        // Enhanced monitoring for user
        activityService.enableEnhancedMonitoring(event.getUserId(), event.getActivityType(),
            event.getSuspiciousIndicators());

        // Check if pattern escalation is needed
        if (activityService.shouldEscalateForPatterns(event.getUserId(), event.getActivityType())) {
            kafkaTemplate.send("fraud-activity-logs", Map.of(
                "activityId", event.getActivityId(),
                "eventType", "ACTIVITY_ESCALATED",
                "activityType", event.getActivityType(),
                "escalationReason", "SUSPICIOUS_PATTERN_DETECTED",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordSuspiciousActivityLogged(event.getActivityType());

        log.info("Suspicious activity logged: activityId={}, type={}, indicators={}",
            event.getActivityId(), event.getActivityType(), event.getSuspiciousIndicators());
    }

    private void processActivityPatternIdentified(FraudActivityLogEvent event, String correlationId) {
        // Update activity log with pattern information
        FraudActivityLog activityLog = activityLogRepository.findByActivityId(event.getActivityId())
            .orElse(createNewActivityLog(event, correlationId));

        activityLog.setStatus("PATTERN_IDENTIFIED");
        activityLog.setPatternType(event.getPatternType());
        activityLog.setPatternDetails(event.getPatternDetails());
        activityLog.setPatternConfidence(event.getPatternConfidence());
        activityLogRepository.save(activityLog);

        // Create pattern analysis
        analyticsService.createPatternAnalysis(event.getActivityId(), event.getPatternType(),
            event.getPatternDetails(), event.getPatternConfidence());

        // Check if pattern requires immediate action
        if (event.getPatternConfidence() > 80) {
            kafkaTemplate.send("fraud-activity-logs", Map.of(
                "activityId", event.getActivityId(),
                "eventType", "ACTIVITY_ESCALATED",
                "activityType", event.getActivityType(),
                "escalationReason", "HIGH_CONFIDENCE_PATTERN",
                "patternType", event.getPatternType(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        // Update pattern repository for future detections
        analyticsService.updatePatternRepository(event.getPatternType(), event.getPatternDetails(),
            event.getPatternConfidence());

        metricsService.recordActivityPatternIdentified(event.getPatternType(), event.getPatternConfidence());

        log.info("Activity pattern identified: activityId={}, patternType={}, confidence={}",
            event.getActivityId(), event.getPatternType(), event.getPatternConfidence());
    }

    private void processThreatIntelligenceMatch(FraudActivityLogEvent event, String correlationId) {
        // Update activity log with threat intelligence match
        FraudActivityLog activityLog = activityLogRepository.findByActivityId(event.getActivityId())
            .orElse(createNewActivityLog(event, correlationId));

        activityLog.setStatus("THREAT_MATCH");
        activityLog.setThreatIntelligenceMatch(event.getThreatIntelligenceMatch());
        activityLog.setThreatSeverity(event.getThreatSeverity());
        activityLog.setThreatSource(event.getThreatSource());
        activityLogRepository.save(activityLog);

        // Block or restrict based on threat level
        if (event.getThreatSeverity().equals("HIGH") || event.getThreatSeverity().equals("CRITICAL")) {
            activityService.implementImmediateBlock(event.getUserId(), event.getActivityType(),
                "THREAT_INTELLIGENCE_MATCH");

            // Send critical alert
            notificationService.sendCriticalAlert(
                "Threat Intelligence Match",
                String.format("Critical threat intelligence match for activity %s: %s",
                    event.getActivityId(), event.getThreatIntelligenceMatch()),
                "CRITICAL"
            );
        }

        // Update threat intelligence with new data
        threatIntelligenceService.updateThreatData(event.getThreatIntelligenceMatch(),
            event.getActivityDetails(), event.getThreatSeverity());

        metricsService.recordThreatIntelligenceMatch(event.getThreatSeverity(), event.getThreatSource());

        log.warn("Threat intelligence match: activityId={}, threat={}, severity={}",
            event.getActivityId(), event.getThreatIntelligenceMatch(), event.getThreatSeverity());
    }

    private void processActivityEscalated(FraudActivityLogEvent event, String correlationId) {
        // Update activity log
        FraudActivityLog activityLog = activityLogRepository.findByActivityId(event.getActivityId())
            .orElse(createNewActivityLog(event, correlationId));

        activityLog.setStatus("ESCALATED");
        activityLog.setEscalatedAt(LocalDateTime.now());
        activityLog.setEscalatedBy(event.getEscalatedBy());
        activityLog.setEscalationReason(event.getEscalationReason());
        activityLog.setEscalatedTo(event.getEscalatedTo());
        activityLogRepository.save(activityLog);

        // Create escalation ticket
        String escalationTicketId = activityService.createEscalationTicket(event.getActivityId(),
            event.getEscalationReason(), event.getEscalatedTo());

        // Send escalation notification
        notificationService.sendEscalationAlert(
            "Fraud Activity Escalated",
            String.format("Fraud activity %s escalated: %s", event.getActivityId(), event.getEscalationReason()),
            "HIGH"
        );

        // Auto-assign to fraud analyst if high priority
        if (event.getSeverity().equals("HIGH") || event.getSeverity().equals("CRITICAL")) {
            activityService.autoAssignToAnalyst(escalationTicketId, event.getActivityType());
        }

        metricsService.recordActivityEscalated(event.getActivityType(), event.getEscalationReason());

        log.warn("Fraud activity escalated: activityId={}, reason={}, escalatedTo={}",
            event.getActivityId(), event.getEscalationReason(), event.getEscalatedTo());
    }

    private void processActivityInvestigationStarted(FraudActivityLogEvent event, String correlationId) {
        // Update activity log
        FraudActivityLog activityLog = activityLogRepository.findByActivityId(event.getActivityId())
            .orElse(createNewActivityLog(event, correlationId));

        activityLog.setStatus("UNDER_INVESTIGATION");
        activityLog.setInvestigationStartedAt(LocalDateTime.now());
        activityLog.setInvestigatedBy(event.getInvestigatedBy());
        activityLog.setInvestigationId(event.getInvestigationId());
        activityLogRepository.save(activityLog);

        // Setup investigation workflow
        activityService.setupInvestigationWorkflow(event.getInvestigationId(), event.getActivityId(),
            event.getActivityType(), event.getInvestigatedBy());

        // Gather additional context
        analyticsService.gatherInvestigationContext(event.getActivityId(), event.getUserId(),
            event.getInvestigationId());

        metricsService.recordActivityInvestigationStarted(event.getActivityType());

        log.info("Fraud activity investigation started: activityId={}, investigationId={}, investigatedBy={}",
            event.getActivityId(), event.getInvestigationId(), event.getInvestigatedBy());
    }

    private void processActivityInvestigationCompleted(FraudActivityLogEvent event, String correlationId) {
        // Update activity log
        FraudActivityLog activityLog = activityLogRepository.findByActivityId(event.getActivityId())
            .orElse(createNewActivityLog(event, correlationId));

        activityLog.setStatus("INVESTIGATION_COMPLETED");
        activityLog.setInvestigationCompletedAt(LocalDateTime.now());
        activityLog.setInvestigationFindings(event.getInvestigationFindings());
        activityLog.setInvestigationConclusion(event.getInvestigationConclusion());
        activityLogRepository.save(activityLog);

        // Process investigation results
        switch (event.getInvestigationConclusion()) {
            case "CONFIRMED_FRAUD":
                kafkaTemplate.send("fraud-activity-logs", Map.of(
                    "activityId", event.getActivityId(),
                    "eventType", "ACTIVITY_CONFIRMED_FRAUD",
                    "activityType", event.getActivityType(),
                    "investigationFindings", event.getInvestigationFindings(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                ));
                break;

            case "FALSE_POSITIVE":
                kafkaTemplate.send("fraud-activity-logs", Map.of(
                    "activityId", event.getActivityId(),
                    "eventType", "ACTIVITY_FALSE_POSITIVE",
                    "activityType", event.getActivityType(),
                    "investigationFindings", event.getInvestigationFindings(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                ));
                break;

            default:
                // Investigation inconclusive or requires further review
                activityService.scheduleFollowUpReview(event.getActivityId(), event.getInvestigationConclusion());
                break;
        }

        metricsService.recordActivityInvestigationCompleted(event.getActivityType(), event.getInvestigationConclusion());

        log.info("Fraud activity investigation completed: activityId={}, conclusion={}",
            event.getActivityId(), event.getInvestigationConclusion());
    }

    private void processActivityFalsePositive(FraudActivityLogEvent event, String correlationId) {
        // Update activity log
        FraudActivityLog activityLog = activityLogRepository.findByActivityId(event.getActivityId())
            .orElse(createNewActivityLog(event, correlationId));

        activityLog.setStatus("FALSE_POSITIVE");
        activityLog.setFalsePositiveConfirmedAt(LocalDateTime.now());
        activityLog.setFalsePositiveReason(event.getFalsePositiveReason());
        activityLogRepository.save(activityLog);

        // Update ML models to reduce future false positives
        analyticsService.updateMLModelsForFalsePositive(event.getActivityType(), event.getActivityDetails(),
            event.getFalsePositiveReason());

        // Restore user access if it was restricted
        activityService.restoreUserAccess(event.getUserId(), "FALSE_POSITIVE_CONFIRMED");

        // Update detection rules
        activityService.updateDetectionRules(event.getActivityType(), event.getActivityDetails(),
            "REDUCE_SENSITIVITY");

        metricsService.recordActivityFalsePositive(event.getActivityType(), event.getFalsePositiveReason());

        log.info("Fraud activity marked as false positive: activityId={}, reason={}",
            event.getActivityId(), event.getFalsePositiveReason());
    }

    private void processActivityConfirmedFraud(FraudActivityLogEvent event, String correlationId) {
        // Update activity log
        FraudActivityLog activityLog = activityLogRepository.findByActivityId(event.getActivityId())
            .orElse(createNewActivityLog(event, correlationId));

        activityLog.setStatus("CONFIRMED_FRAUD");
        activityLog.setFraudConfirmedAt(LocalDateTime.now());
        activityLog.setFraudType(event.getFraudType());
        activityLog.setImpactAssessment(event.getImpactAssessment());
        activityLogRepository.save(activityLog);

        // Implement permanent restrictions
        activityService.implementPermanentRestrictions(event.getUserId(), event.getFraudType());

        // Update ML models with confirmed fraud data
        analyticsService.updateMLModelsForConfirmedFraud(event.getActivityType(), event.getActivityDetails(),
            event.getFraudType());

        // Generate fraud report
        String fraudReportId = activityService.generateFraudReport(event.getActivityId(),
            event.getFraudType(), event.getImpactAssessment());

        // Send confirmed fraud alert
        notificationService.sendCriticalAlert(
            "Confirmed Fraud Activity",
            String.format("Fraud confirmed for activity %s: %s", event.getActivityId(), event.getFraudType()),
            "CRITICAL"
        );

        // Update threat intelligence
        threatIntelligenceService.updateThreatDataWithConfirmedFraud(event.getSourceIp(),
            event.getDeviceFingerprint(), event.getFraudType());

        metricsService.recordActivityConfirmedFraud(event.getActivityType(), event.getFraudType());

        log.error("Fraud activity confirmed: activityId={}, fraudType={}, reportId={}",
            event.getActivityId(), event.getFraudType(), fraudReportId);
    }

    private void processActivityArchived(FraudActivityLogEvent event, String correlationId) {
        // Update activity log
        FraudActivityLog activityLog = activityLogRepository.findByActivityId(event.getActivityId())
            .orElse(createNewActivityLog(event, correlationId));

        activityLog.setStatus("ARCHIVED");
        activityLog.setArchivedAt(LocalDateTime.now());
        activityLog.setArchivedBy(event.getArchivedBy());
        activityLog.setArchiveReason(event.getArchiveReason());
        activityLogRepository.save(activityLog);

        // Move to long-term storage
        activityService.moveToLongTermStorage(event.getActivityId());

        // Clean up active monitoring
        activityService.removeActiveMonitoring(event.getUserId(), event.getActivityId());

        metricsService.recordActivityArchived(event.getActivityType(), event.getArchiveReason());

        log.info("Fraud activity archived: activityId={}, reason={}",
            event.getActivityId(), event.getArchiveReason());
    }

    private void processUnknownActivityLogEvent(FraudActivityLogEvent event, String correlationId) {
        // Create incident for unknown event type
        activityService.createSecurityIncident(
            "UNKNOWN_FRAUD_ACTIVITY_LOG_EVENT",
            String.format("Unknown fraud activity log event type %s for activity %s",
                event.getEventType(), event.getActivityId()),
            "MEDIUM",
            Map.of("activityId", event.getActivityId(), "unknownEventType", event.getEventType(),
                "activityType", event.getActivityType(), "correlationId", correlationId)
        );

        log.warn("Unknown fraud activity log event: activityId={}, eventType={}, activityType={}",
            event.getActivityId(), event.getEventType(), event.getActivityType());
    }

    private FraudActivityLog createNewActivityLog(FraudActivityLogEvent event, String correlationId) {
        return FraudActivityLog.builder()
            .activityId(event.getActivityId())
            .userId(event.getUserId())
            .sessionId(event.getSessionId())
            .activityType(event.getActivityType())
            .severity(event.getSeverity())
            .detectedAt(LocalDateTime.now())
            .activityDetails(event.getActivityDetails())
            .sourceIp(event.getSourceIp())
            .userAgent(event.getUserAgent())
            .deviceFingerprint(event.getDeviceFingerprint())
            .correlationId(correlationId)
            .build();
    }
}