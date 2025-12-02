package com.waqiti.frauddetection.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.frauddetection.service.*;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-grade Kafka consumer for ATO detection critical failures
 * Handles critical failure events from ATO detection system with immediate
 * response protocols, escalation procedures, and comprehensive monitoring
 * 
 * Critical for: Account security, system reliability, incident response
 * SLA: Must process critical failures within 5 seconds for immediate escalation
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ATODetectionCriticalFailuresConsumer {

    private final ATOFailureService atoFailureService;
    private final IncidentResponseService incidentResponseService;
    private final AccountProtectionService accountProtectionService;
    private final FraudNotificationService fraudNotificationService;
    private final EscalationService escalationService;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final ObjectMapper objectMapper;

    // Idempotency cache with 24-hour TTL
    private final Map<String, Instant> processedEventIds = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_HOURS = 24;

    // Metrics
    private final Counter criticalFailuresCounter = Counter.builder("ato_critical_failures_total")
            .description("Total number of ATO critical failures processed")
            .register(metricsService.getMeterRegistry());

    private final Counter processingFailuresCounter = Counter.builder("ato_critical_failures_processing_failed_total")
            .description("Total number of ATO critical failure events that failed processing")
            .register(metricsService.getMeterRegistry());

    private final Timer processingTimer = Timer.builder("ato_critical_failure_processing_duration")
            .description("Time taken to process ATO critical failure events")
            .register(metricsService.getMeterRegistry());

    @KafkaListener(
        topics = {"ato-detection-critical-failures"},
        groupId = "fraud-service-ato-critical-failures-processor",
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
    @CircuitBreaker(name = "ato-critical-failures-processor", fallbackMethod = "handleCriticalFailureProcessingFailure")
    @Retry(name = "ato-critical-failures-processor")
    public void processCriticalFailureEvent(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(metricsService.getMeterRegistry());
        String eventId = event.getEventId();
        
        log.error("CRITICAL: Processing ATO detection critical failure: {} from topic: {} partition: {} offset: {}", 
                eventId, topic, partition, offset);

        try {
            // Check idempotency
            if (isEventAlreadyProcessed(eventId)) {
                log.info("Critical failure event {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }

            // Extract and validate critical failure data
            ATOCriticalFailureData failureData = extractCriticalFailureData(event.getPayload());
            validateCriticalFailureEvent(failureData);

            // Mark as processed for idempotency
            markEventAsProcessed(eventId);

            // Process critical failure with immediate priority
            processCriticalFailure(failureData, event);

            // Record successful processing metrics
            criticalFailuresCounter.increment();
            
            // Audit the critical failure processing
            auditCriticalFailureProcessing(failureData, event, "SUCCESS");

            log.error("CRITICAL: Successfully processed ATO critical failure: {} for account: {} - severity: {}", 
                    eventId, failureData.getAccountId(), failureData.getSeverityLevel());

            acknowledgment.acknowledge();

        } catch (IllegalArgumentException e) {
            log.error("CRITICAL: Invalid ATO critical failure event data: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("CRITICAL: Failed to process ATO critical failure event: {}", eventId, e);
            processingFailuresCounter.increment();
            auditCriticalFailureProcessing(null, event, "FAILED: " + e.getMessage());
            throw new RuntimeException("ATO critical failure event processing failed", e);

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

    private ATOCriticalFailureData extractCriticalFailureData(Map<String, Object> payload) throws JsonProcessingException {
        return ATOCriticalFailureData.builder()
                .failureId(extractString(payload, "failureId"))
                .originalEventId(extractString(payload, "originalEventId"))
                .accountId(extractString(payload, "accountId"))
                .userId(extractString(payload, "userId"))
                .failureType(extractString(payload, "failureType"))
                .severityLevel(extractString(payload, "severityLevel"))
                .errorMessage(extractString(payload, "errorMessage"))
                .errorCode(extractString(payload, "errorCode"))
                .stackTrace(extractString(payload, "stackTrace"))
                .systemComponent(extractString(payload, "systemComponent"))
                .affectedServices(extractStringList(payload, "affectedServices"))
                .detectionDetails(extractMap(payload, "detectionDetails"))
                .systemState(extractMap(payload, "systemState"))
                .failureTimestamp(extractInstant(payload, "failureTimestamp"))
                .detectionTimestamp(extractInstant(payload, "detectionTimestamp"))
                .impactAssessment(extractString(payload, "impactAssessment"))
                .recommendedActions(extractStringList(payload, "recommendedActions"))
                .build();
    }

    private void validateCriticalFailureEvent(ATOCriticalFailureData failureData) {
        if (failureData.getFailureId() == null || failureData.getFailureId().trim().isEmpty()) {
            throw new IllegalArgumentException("Failure ID is required");
        }
        
        if (failureData.getAccountId() == null || failureData.getAccountId().trim().isEmpty()) {
            throw new IllegalArgumentException("Account ID is required");
        }
        
        if (failureData.getFailureType() == null || failureData.getFailureType().trim().isEmpty()) {
            throw new IllegalArgumentException("Failure type is required");
        }
        
        if (failureData.getSeverityLevel() == null || failureData.getSeverityLevel().trim().isEmpty()) {
            throw new IllegalArgumentException("Severity level is required");
        }
        
        List<String> validSeverityLevels = List.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
        if (!validSeverityLevels.contains(failureData.getSeverityLevel())) {
            throw new IllegalArgumentException("Invalid severity level: " + failureData.getSeverityLevel());
        }
        
        if (failureData.getFailureTimestamp() == null) {
            throw new IllegalArgumentException("Failure timestamp is required");
        }
    }

    private void processCriticalFailure(ATOCriticalFailureData failureData, GenericKafkaEvent event) {
        log.error("CRITICAL: Processing ATO critical failure - Type: {}, Severity: {}, Account: {}, Component: {}", 
                failureData.getFailureType(), failureData.getSeverityLevel(), 
                failureData.getAccountId(), failureData.getSystemComponent());

        try {
            // Record critical failure incident
            String incidentId = atoFailureService.recordCriticalFailure(failureData);

            // Immediate account protection based on failure type
            applyImmediateProtection(failureData);

            // Create incident response case
            String responseId = incidentResponseService.createIncident(
                    incidentId,
                    failureData.getFailureType(),
                    failureData.getSeverityLevel(),
                    failureData.getAccountId(),
                    failureData.getErrorMessage(),
                    failureData.getSystemComponent()
            );

            // Escalate based on severity level
            handleEscalation(failureData, incidentId, responseId);

            // Send immediate notifications
            sendCriticalFailureNotifications(failureData, incidentId);

            // Perform impact assessment
            CriticalFailureImpact impact = assessFailureImpact(failureData);

            // Take corrective actions
            executeCorrectiveActions(failureData, impact);

            // Update system monitoring and alerts
            updateSystemMonitoring(failureData, incidentId);

            log.error("CRITICAL: ATO critical failure processed - IncidentId: {}, ResponseId: {}, ImpactLevel: {}", 
                    incidentId, responseId, impact.getImpactLevel());

        } catch (Exception e) {
            log.error("CRITICAL: Failed to process ATO critical failure for account: {}", 
                    failureData.getAccountId(), e);
            
            // Emergency fallback procedures
            executeEmergencyFallback(failureData, e);
            
            throw new RuntimeException("Critical failure processing failed", e);
        }
    }

    private void applyImmediateProtection(ATOCriticalFailureData failureData) {
        switch (failureData.getFailureType()) {
            case "DETECTION_ENGINE_FAILURE":
                // Apply conservative protection when detection engine fails
                accountProtectionService.applyConservativeProtection(failureData.getAccountId());
                break;
                
            case "AUTHENTICATION_SYSTEM_FAILURE":
                // Immediately lock account if auth system fails
                accountProtectionService.emergencyAccountLock(failureData.getAccountId(), "AUTH_SYSTEM_FAILURE");
                break;
                
            case "DATA_CORRUPTION":
                // Freeze account activity if data corruption detected
                accountProtectionService.freezeAccountActivity(failureData.getAccountId(), "DATA_CORRUPTION");
                break;
                
            case "SECURITY_BREACH_DETECTED":
                // Maximum security lockdown
                accountProtectionService.maximumSecurityLockdown(failureData.getAccountId());
                break;
                
            default:
                // Default protective measures
                accountProtectionService.applyDefaultEmergencyProtection(failureData.getAccountId());
        }
    }

    private void handleEscalation(ATOCriticalFailureData failureData, String incidentId, String responseId) {
        switch (failureData.getSeverityLevel()) {
            case "CRITICAL":
                escalationService.escalateToExecutiveTeam(incidentId, failureData);
                escalationService.escalateToSecurityTeam(incidentId, failureData);
                escalationService.escalateToIncidentCommander(incidentId, failureData);
                break;
                
            case "HIGH":
                escalationService.escalateToSecurityTeam(incidentId, failureData);
                escalationService.escalateToOnCallTeam(incidentId, failureData);
                break;
                
            case "MEDIUM":
                escalationService.escalateToOnCallTeam(incidentId, failureData);
                break;
                
            default:
                escalationService.escalateToStandardTeam(incidentId, failureData);
        }
    }

    private void sendCriticalFailureNotifications(ATOCriticalFailureData failureData, String incidentId) {
        // Immediate emergency notifications
        fraudNotificationService.sendEmergencyAlert(
                "CRITICAL ATO SYSTEM FAILURE",
                String.format("Critical failure in ATO detection system - Account: %s, Type: %s, Severity: %s", 
                        failureData.getAccountId(), failureData.getFailureType(), failureData.getSeverityLevel()),
                failureData.getAccountId(),
                incidentId
        );

        // Page security on-call team
        fraudNotificationService.pageSecurityTeam(
                "ATO_CRITICAL_FAILURE",
                failureData.getAccountId(),
                failureData.getFailureType(),
                incidentId
        );

        // Executive notifications for critical failures
        if ("CRITICAL".equals(failureData.getSeverityLevel())) {
            fraudNotificationService.sendExecutiveAlert(
                    "Critical ATO System Failure",
                    failureData,
                    incidentId
            );
        }

        // Customer communication if account affected
        if (shouldNotifyCustomer(failureData)) {
            fraudNotificationService.sendCustomerSecurityAlert(
                    failureData.getAccountId(),
                    "security_system_issue",
                    Map.of("incident_id", incidentId)
            );
        }
    }

    private boolean shouldNotifyCustomer(ATOCriticalFailureData failureData) {
        return List.of("AUTHENTICATION_SYSTEM_FAILURE", "SECURITY_BREACH_DETECTED", "ACCOUNT_DATA_CORRUPTION")
                .contains(failureData.getFailureType());
    }

    private CriticalFailureImpact assessFailureImpact(ATOCriticalFailureData failureData) {
        return atoFailureService.assessCriticalFailureImpact(
                failureData.getFailureType(),
                failureData.getSeverityLevel(),
                failureData.getAffectedServices(),
                failureData.getSystemState()
        );
    }

    private void executeCorrectiveActions(ATOCriticalFailureData failureData, CriticalFailureImpact impact) {
        for (String action : failureData.getRecommendedActions()) {
            try {
                switch (action) {
                    case "RESTART_DETECTION_ENGINE":
                        atoFailureService.restartDetectionEngine(failureData.getSystemComponent());
                        break;
                        
                    case "FAILOVER_TO_BACKUP":
                        atoFailureService.failoverToBackupSystem(failureData.getSystemComponent());
                        break;
                        
                    case "ENABLE_MAINTENANCE_MODE":
                        atoFailureService.enableMaintenanceMode(failureData.getSystemComponent());
                        break;
                        
                    case "ISOLATE_AFFECTED_COMPONENT":
                        atoFailureService.isolateAffectedComponent(failureData.getSystemComponent());
                        break;
                        
                    default:
                        log.warn("Unknown corrective action: {}", action);
                }
            } catch (Exception e) {
                log.error("Failed to execute corrective action: {}", action, e);
            }
        }
    }

    private void updateSystemMonitoring(ATOCriticalFailureData failureData, String incidentId) {
        // Update monitoring dashboards
        metricsService.recordCriticalFailure(
                failureData.getFailureType(),
                failureData.getSeverityLevel(),
                failureData.getSystemComponent(),
                incidentId
        );

        // Set up enhanced monitoring for affected account
        metricsService.enableEnhancedMonitoring(
                failureData.getAccountId(),
                "CRITICAL_FAILURE_RECOVERY"
        );

        // Update system health indicators
        metricsService.updateSystemHealthIndicator(
                failureData.getSystemComponent(),
                "DEGRADED"
        );
    }

    private void executeEmergencyFallback(ATOCriticalFailureData failureData, Exception error) {
        log.error("EMERGENCY: Executing fallback procedures for critical failure processing");
        
        try {
            // Emergency account protection
            accountProtectionService.applyMaximumProtection(failureData.getAccountId());
            
            // Emergency notification
            fraudNotificationService.sendEmergencySystemAlert(
                    "ATO Critical Failure Processing Failed",
                    String.format("Failed to process critical failure for account %s: %s", 
                            failureData.getAccountId(), error.getMessage())
            );
            
            // Manual intervention required
            escalationService.escalateToManualIntervention(
                    failureData.getAccountId(),
                    "CRITICAL_FAILURE_PROCESSING_FAILED",
                    error.getMessage()
            );
            
        } catch (Exception e) {
            log.error("EMERGENCY: Fallback procedures also failed", e);
        }
    }

    private void handleValidationError(GenericKafkaEvent event, IllegalArgumentException e) {
        log.error("CRITICAL: ATO critical failure event validation failed for event: {}", event.getEventId(), e);
        
        auditService.auditSecurityEvent(
                "ATO_CRITICAL_FAILURE_VALIDATION_ERROR",
                null,
                "ATO critical failure event validation failed: " + e.getMessage(),
                Map.of(
                        "eventId", event.getEventId(),
                        "error", e.getMessage(),
                        "payload", event.getPayload()
                )
        );

        // Send to validation errors topic for analysis
        fraudNotificationService.sendValidationErrorAlert(event, e.getMessage());
    }

    private void auditCriticalFailureProcessing(ATOCriticalFailureData failureData, GenericKafkaEvent event, String status) {
        try {
            auditService.auditSecurityEvent(
                    "ATO_CRITICAL_FAILURE_PROCESSED",
                    failureData != null ? failureData.getAccountId() : null,
                    String.format("ATO critical failure event processing %s", status),
                    Map.of(
                            "eventId", event.getEventId(),
                            "failureId", failureData != null ? failureData.getFailureId() : "unknown",
                            "accountId", failureData != null ? failureData.getAccountId() : "unknown",
                            "failureType", failureData != null ? failureData.getFailureType() : "unknown",
                            "severityLevel", failureData != null ? failureData.getSeverityLevel() : "unknown",
                            "status", status,
                            "timestamp", Instant.now()
                    )
            );
        } catch (Exception e) {
            log.error("Failed to audit ATO critical failure processing", e);
        }
    }

    @DltHandler
    public void handleDlt(
            @Payload GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "kafka_dlt-original-topic", required = false) String originalTopic) {
        
        log.error("CRITICAL: ATO critical failure event sent to DLT - EventId: {}, OriginalTopic: {}", 
                event.getEventId(), originalTopic);

        try {
            ATOCriticalFailureData failureData = extractCriticalFailureData(event.getPayload());
            
            // Emergency procedures when critical failure processing fails
            accountProtectionService.applyMaximumProtection(failureData.getAccountId());
            
            // Escalate to highest level
            escalationService.escalateToExecutiveTeam(
                    "DLT_CRITICAL_FAILURE",
                    failureData
            );

            // Send emergency alert
            fraudNotificationService.sendEmergencySystemAlert(
                    "CRITICAL: ATO Critical Failure Event in DLT",
                    "Critical failure event could not be processed - manual intervention required immediately"
            );

            // Audit DLT handling
            auditService.auditSecurityEvent(
                    "ATO_CRITICAL_FAILURE_DLT",
                    failureData.getAccountId(),
                    "ATO critical failure event sent to Dead Letter Queue - IMMEDIATE MANUAL INTERVENTION REQUIRED",
                    Map.of(
                            "eventId", event.getEventId(),
                            "failureId", failureData.getFailureId(),
                            "accountId", failureData.getAccountId(),
                            "failureType", failureData.getFailureType(),
                            "severityLevel", failureData.getSeverityLevel(),
                            "originalTopic", originalTopic
                    )
            );

        } catch (Exception e) {
            log.error("EMERGENCY: Failed to handle ATO critical failure DLT event: {}", event.getEventId(), e);
        }
    }

    // Circuit breaker fallback method
    public void handleCriticalFailureProcessingFailure(GenericKafkaEvent event, String topic, int partition,
                                                      long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("EMERGENCY: Circuit breaker activated for ATO critical failure processing - EventId: {}", 
                event.getEventId(), e);

        try {
            ATOCriticalFailureData failureData = extractCriticalFailureData(event.getPayload());
            
            // Maximum protection for affected account
            accountProtectionService.applyMaximumProtection(failureData.getAccountId());

            // Emergency system alert
            fraudNotificationService.sendEmergencySystemAlert(
                    "ATO Critical Failure Circuit Breaker Open",
                    "ATO critical failure processing is down - system security severely compromised"
            );

            // Escalate to manual intervention
            escalationService.escalateToManualIntervention(
                    failureData.getAccountId(),
                    "CRITICAL_FAILURE_CIRCUIT_BREAKER",
                    e.getMessage()
            );

        } catch (Exception ex) {
            log.error("EMERGENCY: Failed to handle critical failure circuit breaker fallback", ex);
        }

        acknowledgment.acknowledge();
    }

    // Helper extraction methods
    private String extractString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
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
    public static class ATOCriticalFailureData {
        private String failureId;
        private String originalEventId;
        private String accountId;
        private String userId;
        private String failureType;
        private String severityLevel;
        private String errorMessage;
        private String errorCode;
        private String stackTrace;
        private String systemComponent;
        private List<String> affectedServices;
        private Map<String, Object> detectionDetails;
        private Map<String, Object> systemState;
        private Instant failureTimestamp;
        private Instant detectionTimestamp;
        private String impactAssessment;
        private List<String> recommendedActions;
    }

    @lombok.Data
    @lombok.Builder
    public static class CriticalFailureImpact {
        private String impactLevel;
        private List<String> affectedSystems;
        private Integer affectedAccountCount;
        private String businessImpact;
        private List<String> recommendedActions;
    }
}