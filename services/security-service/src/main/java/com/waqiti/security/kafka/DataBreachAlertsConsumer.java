package com.waqiti.security.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.security.service.BreachResponseService;
import com.waqiti.security.service.IncidentManagementService;
import com.waqiti.security.service.SecurityNotificationService;
import com.waqiti.security.service.ForensicInvestigationService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.resilience.CircuitBreakerService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataBreachAlertsConsumer {
    
    private final BreachResponseService breachResponseService;
    private final IncidentManagementService incidentManagementService;
    private final SecurityNotificationService securityNotificationService;
    private final ForensicInvestigationService forensicInvestigationService;
    private final AuditService auditService;
    private final CircuitBreakerService circuitBreakerService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    // Idempotency tracking with 24-hour TTL
    private final ConcurrentHashMap<String, LocalDateTime> processedEvents = new ConcurrentHashMap<>();
    
    private Counter eventProcessedCounter;
    private Counter eventFailedCounter;
    private Timer processingTimer;
    
    @PostConstruct
    public void initMetrics() {
        eventProcessedCounter = Counter.builder("data_breach_alerts_processed_total")
                .description("Total number of data breach alerts processed")
                .register(meterRegistry);
        
        eventFailedCounter = Counter.builder("data_breach_alerts_failed_total")
                .description("Total number of data breach alerts that failed processing")
                .register(meterRegistry);
        
        processingTimer = Timer.builder("data_breach_alerts_processing_duration")
                .description("Time taken to process data breach alerts")
                .register(meterRegistry);
    }
    
    @KafkaListener(
        topics = {"data-breach-alerts", "security-breach-detected", "data-breach-notifications"},
        groupId = "security-service-data-breach-alerts-group",
        containerFactory = "criticalSecurityKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @CircuitBreaker(name = "data-breach-alerts", fallbackMethod = "handleCircuitBreakerFallback")
    @Retry(name = "data-breach-alerts")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleDataBreachAlert(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        log.info("SECURITY ALERT: Processing data breach alert - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
        
        LocalDateTime processingStartTime = LocalDateTime.now();
        String alertId = null;
        String incidentId = null;
        String breachType = null;
        String severity = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            alertId = (String) event.get("alertId");
            incidentId = (String) event.get("incidentId");
            breachType = (String) event.get("breachType");
            severity = (String) event.get("severity");
            String detectionSource = (String) event.get("detectionSource");
            String detectionMethod = (String) event.get("detectionMethod");
            LocalDateTime detectionTime = LocalDateTime.parse((String) event.get("detectionTime"));
            @SuppressWarnings("unchecked")
            List<String> affectedSystems = (List<String>) event.get("affectedSystems");
            @SuppressWarnings("unchecked")
            List<String> compromisedDataTypes = (List<String>) event.get("compromisedDataTypes");
            Integer estimatedRecordsAffected = (Integer) event.getOrDefault("estimatedRecordsAffected", 0);
            String attackVector = (String) event.getOrDefault("attackVector", "UNKNOWN");
            @SuppressWarnings("unchecked")
            List<String> suspiciousIPs = (List<String>) event.getOrDefault("suspiciousIPs", List.of());
            String threatActorType = (String) event.getOrDefault("threatActorType", "UNKNOWN");
            Boolean containmentRequired = (Boolean) event.getOrDefault("containmentRequired", true);
            Boolean notificationRequired = (Boolean) event.getOrDefault("notificationRequired", true);
            @SuppressWarnings("unchecked")
            Map<String, Object> forensicEvidence = (Map<String, Object>) event.getOrDefault("forensicEvidence", Map.of());
            
            log.info("Data breach alert - AlertId: {}, IncidentId: {}, Type: {}, Severity: {}, AffectedSystems: {}, Records: {}", 
                    alertId, incidentId, breachType, severity, affectedSystems.size(), estimatedRecordsAffected);
            
            // Check idempotency
            String eventKey = alertId + "_" + incidentId;
            if (isAlreadyProcessed(eventKey)) {
                log.warn("Data breach alert already processed, skipping: alertId={}, incidentId={}", 
                        alertId, incidentId);
                acknowledgment.acknowledge();
                return;
            }
            
            validateDataBreachAlert(alertId, incidentId, breachType, severity, affectedSystems, 
                    compromisedDataTypes, detectionTime);
            
            // Immediate containment actions
            executeImmediateContainment(alertId, incidentId, breachType, severity, 
                    affectedSystems, suspiciousIPs, attackVector, containmentRequired);
            
            // Create incident record
            createSecurityIncident(alertId, incidentId, breachType, severity, detectionSource, 
                    detectionMethod, detectionTime, affectedSystems, compromisedDataTypes, 
                    estimatedRecordsAffected, attackVector, threatActorType);
            
            // Initiate forensic investigation
            initiateForensicInvestigation(alertId, incidentId, breachType, affectedSystems, 
                    forensicEvidence, attackVector, suspiciousIPs);
            
            // Execute notification protocols
            if (notificationRequired) {
                executeNotificationProtocols(alertId, incidentId, breachType, severity, 
                        affectedSystems, compromisedDataTypes, estimatedRecordsAffected);
            }
            
            // Escalate based on severity
            escalateBasedOnSeverity(alertId, incidentId, breachType, severity, 
                    estimatedRecordsAffected, compromisedDataTypes);
            
            // Setup continuous monitoring
            setupContinuousMonitoring(alertId, incidentId, affectedSystems, attackVector, 
                    suspiciousIPs, threatActorType);
            
            // Update security posture
            updateSecurityPosture(alertId, incidentId, breachType, attackVector, 
                    affectedSystems, forensicEvidence);
            
            // Mark as processed
            markEventAsProcessed(eventKey);
            
            auditDataBreachAlert(alertId, incidentId, breachType, severity, affectedSystems, 
                    compromisedDataTypes, estimatedRecordsAffected, processingStartTime);
            
            eventProcessedCounter.increment();
            long processingTimeMs = java.time.Duration.between(processingStartTime, LocalDateTime.now()).toMillis();
            
            log.info("Data breach alert processed successfully - AlertId: {}, IncidentId: {}, Severity: {}, ProcessingTime: {}ms", 
                    alertId, incidentId, severity, processingTimeMs);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            eventFailedCounter.increment();
            log.error("CRITICAL: Data breach alert processing failed - AlertId: {}, IncidentId: {}, Type: {}, Severity: {}, Error: {}", 
                    alertId, incidentId, breachType, severity, e.getMessage(), e);
            
            if (alertId != null && incidentId != null) {
                handleProcessingFailure(alertId, incidentId, breachType, severity, e);
            }
            
            throw new RuntimeException("Data breach alert processing failed", e);
        } finally {
            sample.stop(processingTimer);
        }
    }
    
    private void validateDataBreachAlert(String alertId, String incidentId, String breachType, 
                                       String severity, List<String> affectedSystems, 
                                       List<String> compromisedDataTypes, LocalDateTime detectionTime) {
        if (alertId == null || alertId.trim().isEmpty()) {
            throw new IllegalArgumentException("Alert ID is required");
        }
        
        if (incidentId == null || incidentId.trim().isEmpty()) {
            throw new IllegalArgumentException("Incident ID is required");
        }
        
        if (breachType == null || breachType.trim().isEmpty()) {
            throw new IllegalArgumentException("Breach type is required");
        }
        
        List<String> validBreachTypes = List.of("DATA_EXFILTRATION", "UNAUTHORIZED_ACCESS", 
                "MALWARE_INFECTION", "INSIDER_THREAT", "PHISHING_ATTACK", "RANSOMWARE", 
                "SQL_INJECTION", "API_BREACH", "CLOUD_MISCONFIGURATION");
        if (!validBreachTypes.contains(breachType)) {
            throw new IllegalArgumentException("Invalid breach type: " + breachType);
        }
        
        if (severity == null || severity.trim().isEmpty()) {
            throw new IllegalArgumentException("Severity is required");
        }
        
        List<String> validSeverities = List.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
        if (!validSeverities.contains(severity)) {
            throw new IllegalArgumentException("Invalid severity: " + severity);
        }
        
        if (affectedSystems == null || affectedSystems.isEmpty()) {
            throw new IllegalArgumentException("Affected systems list cannot be empty");
        }
        
        if (compromisedDataTypes == null || compromisedDataTypes.isEmpty()) {
            throw new IllegalArgumentException("Compromised data types list cannot be empty");
        }
        
        if (detectionTime == null || detectionTime.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Invalid detection time");
        }
        
        log.debug("Data breach alert validation passed - AlertId: {}, IncidentId: {}", alertId, incidentId);
    }
    
    private void executeImmediateContainment(String alertId, String incidentId, String breachType, 
                                           String severity, List<String> affectedSystems, 
                                           List<String> suspiciousIPs, String attackVector, 
                                           Boolean containmentRequired) {
        try {
            if (!containmentRequired) {
                log.info("Containment not required for breach alert: {}", alertId);
                return;
            }
            
            log.info("Executing immediate containment - AlertId: {}, IncidentId: {}, Severity: {}", 
                    alertId, incidentId, severity);
            
            // Isolate affected systems
            for (String system : affectedSystems) {
                breachResponseService.isolateSystem(system, incidentId, severity);
            }
            
            // Block suspicious IPs
            if (suspiciousIPs != null && !suspiciousIPs.isEmpty()) {
                breachResponseService.blockSuspiciousIPs(suspiciousIPs, incidentId, alertId);
            }
            
            // Activate emergency security controls
            breachResponseService.activateEmergencySecurityControls(incidentId, breachType, 
                    severity, affectedSystems);
            
            // Disable potentially compromised accounts
            breachResponseService.disableCompromisedAccounts(incidentId, affectedSystems, 
                    breachType, severity);
            
            // Enable enhanced monitoring
            breachResponseService.enableEnhancedMonitoring(incidentId, affectedSystems, 
                    attackVector, suspiciousIPs);
            
            // Preserve forensic evidence
            breachResponseService.preserveForensicEvidence(incidentId, affectedSystems, 
                    breachType, alertId);
            
            log.info("Immediate containment executed successfully - AlertId: {}, IncidentId: {}", 
                    alertId, incidentId);
            
        } catch (Exception e) {
            log.error("Failed to execute immediate containment - AlertId: {}, IncidentId: {}", 
                    alertId, incidentId, e);
            throw new RuntimeException("Immediate containment failed", e);
        }
    }
    
    private void createSecurityIncident(String alertId, String incidentId, String breachType, 
                                      String severity, String detectionSource, String detectionMethod, 
                                      LocalDateTime detectionTime, List<String> affectedSystems, 
                                      List<String> compromisedDataTypes, Integer estimatedRecordsAffected, 
                                      String attackVector, String threatActorType) {
        try {
            incidentManagementService.createSecurityIncident(alertId, incidentId, breachType, 
                    severity, detectionSource, detectionMethod, detectionTime, affectedSystems, 
                    compromisedDataTypes, estimatedRecordsAffected, attackVector, threatActorType);
            
            log.info("Security incident created - AlertId: {}, IncidentId: {}, Type: {}, Severity: {}", 
                    alertId, incidentId, breachType, severity);
            
        } catch (Exception e) {
            log.error("Failed to create security incident - AlertId: {}, IncidentId: {}", 
                    alertId, incidentId, e);
            throw new RuntimeException("Security incident creation failed", e);
        }
    }
    
    private void initiateForensicInvestigation(String alertId, String incidentId, String breachType, 
                                             List<String> affectedSystems, Map<String, Object> forensicEvidence, 
                                             String attackVector, List<String> suspiciousIPs) {
        try {
            forensicInvestigationService.initiateInvestigation(alertId, incidentId, breachType, 
                    affectedSystems, forensicEvidence, attackVector, suspiciousIPs);
            
            log.info("Forensic investigation initiated - AlertId: {}, IncidentId: {}, Type: {}", 
                    alertId, incidentId, breachType);
            
        } catch (Exception e) {
            log.error("Failed to initiate forensic investigation - AlertId: {}, IncidentId: {}", 
                    alertId, incidentId, e);
            // Don't throw exception as forensics is not blocking
        }
    }
    
    private void executeNotificationProtocols(String alertId, String incidentId, String breachType, 
                                            String severity, List<String> affectedSystems, 
                                            List<String> compromisedDataTypes, Integer estimatedRecordsAffected) {
        try {
            // Notify security team
            securityNotificationService.notifySecurityTeam(alertId, incidentId, breachType, 
                    severity, affectedSystems, estimatedRecordsAffected);
            
            // Notify CISO
            if ("CRITICAL".equals(severity) || "HIGH".equals(severity)) {
                securityNotificationService.notifyCISO(alertId, incidentId, breachType, 
                        severity, affectedSystems, compromisedDataTypes, estimatedRecordsAffected);
            }
            
            // Notify executive team for critical breaches
            if ("CRITICAL".equals(severity) && estimatedRecordsAffected > 1000) {
                securityNotificationService.notifyExecutiveTeam(alertId, incidentId, breachType, 
                        severity, estimatedRecordsAffected);
            }
            
            // Notify legal team if PII is involved
            if (compromisedDataTypes.stream().anyMatch(type -> 
                    type.contains("PII") || type.contains("PERSONAL") || type.contains("HEALTH"))) {
                securityNotificationService.notifyLegalTeam(alertId, incidentId, breachType, 
                        compromisedDataTypes, estimatedRecordsAffected);
            }
            
            // Notify compliance team
            securityNotificationService.notifyComplianceTeam(alertId, incidentId, breachType, 
                    severity, compromisedDataTypes, estimatedRecordsAffected);
            
            log.info("Notification protocols executed - AlertId: {}, IncidentId: {}, Severity: {}", 
                    alertId, incidentId, severity);
            
        } catch (Exception e) {
            log.error("Failed to execute notification protocols - AlertId: {}, IncidentId: {}", 
                    alertId, incidentId, e);
            // Don't throw exception as notifications are not blocking
        }
    }
    
    private void escalateBasedOnSeverity(String alertId, String incidentId, String breachType, 
                                       String severity, Integer estimatedRecordsAffected, 
                                       List<String> compromisedDataTypes) {
        try {
            switch (severity) {
                case "CRITICAL" -> {
                    incidentManagementService.escalateToCriticalResponse(alertId, incidentId, 
                            breachType, estimatedRecordsAffected, compromisedDataTypes);
                    securityNotificationService.activateCrisisManagement(alertId, incidentId, 
                            breachType, estimatedRecordsAffected);
                }
                case "HIGH" -> {
                    incidentManagementService.escalateToHighPriorityResponse(alertId, incidentId, 
                            breachType, estimatedRecordsAffected, compromisedDataTypes);
                }
                case "MEDIUM" -> {
                    incidentManagementService.escalateToStandardResponse(alertId, incidentId, 
                            breachType, estimatedRecordsAffected);
                }
                case "LOW" -> {
                    incidentManagementService.escalateToLowPriorityResponse(alertId, incidentId, 
                            breachType);
                }
            }
            
            log.info("Escalation completed - AlertId: {}, IncidentId: {}, Severity: {}", 
                    alertId, incidentId, severity);
            
        } catch (Exception e) {
            log.error("Failed to escalate based on severity - AlertId: {}, IncidentId: {}, Severity: {}", 
                    alertId, incidentId, severity, e);
            // Don't throw exception as escalation failure shouldn't block processing
        }
    }
    
    private void setupContinuousMonitoring(String alertId, String incidentId, List<String> affectedSystems, 
                                         String attackVector, List<String> suspiciousIPs, 
                                         String threatActorType) {
        try {
            breachResponseService.setupContinuousMonitoring(alertId, incidentId, affectedSystems, 
                    attackVector, suspiciousIPs, threatActorType);
            
            log.info("Continuous monitoring setup completed - AlertId: {}, IncidentId: {}", 
                    alertId, incidentId);
            
        } catch (Exception e) {
            log.error("Failed to setup continuous monitoring - AlertId: {}, IncidentId: {}", 
                    alertId, incidentId, e);
            // Don't throw exception as monitoring setup failure shouldn't block processing
        }
    }
    
    private void updateSecurityPosture(String alertId, String incidentId, String breachType, 
                                     String attackVector, List<String> affectedSystems, 
                                     Map<String, Object> forensicEvidence) {
        try {
            breachResponseService.updateSecurityPosture(alertId, incidentId, breachType, 
                    attackVector, affectedSystems, forensicEvidence);
            
            log.info("Security posture updated - AlertId: {}, IncidentId: {}, Type: {}", 
                    alertId, incidentId, breachType);
            
        } catch (Exception e) {
            log.error("Failed to update security posture - AlertId: {}, IncidentId: {}", 
                    alertId, incidentId, e);
            // Don't throw exception as posture update failure shouldn't block processing
        }
    }
    
    private void auditDataBreachAlert(String alertId, String incidentId, String breachType, 
                                    String severity, List<String> affectedSystems, 
                                    List<String> compromisedDataTypes, Integer estimatedRecordsAffected, 
                                    LocalDateTime startTime) {
        try {
            long processingTimeMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            
            auditService.auditSecurityEvent(
                    "DATA_BREACH_ALERT_PROCESSED",
                    incidentId,
                    String.format("Data breach alert processed - Type: %s, Severity: %s, AffectedSystems: %d, Records: %d", 
                            breachType, severity, affectedSystems.size(), estimatedRecordsAffected),
                    Map.of(
                            "alertId", alertId,
                            "incidentId", incidentId,
                            "breachType", breachType,
                            "severity", severity,
                            "affectedSystemsCount", affectedSystems.size(),
                            "compromisedDataTypes", String.join(",", compromisedDataTypes),
                            "estimatedRecordsAffected", estimatedRecordsAffected,
                            "processingTimeMs", processingTimeMs
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to audit data breach alert - AlertId: {}, IncidentId: {}", 
                    alertId, incidentId, e);
        }
    }
    
    private void handleProcessingFailure(String alertId, String incidentId, String breachType, 
                                       String severity, Exception error) {
        try {
            incidentManagementService.recordProcessingFailure(alertId, incidentId, breachType, 
                    severity, error.getMessage());
            
            auditService.auditSecurityEvent(
                    "DATA_BREACH_ALERT_PROCESSING_FAILED",
                    incidentId,
                    "Failed to process data breach alert: " + error.getMessage(),
                    Map.of(
                            "alertId", alertId,
                            "incidentId", incidentId,
                            "breachType", breachType != null ? breachType : "UNKNOWN",
                            "severity", severity != null ? severity : "UNKNOWN",
                            "error", error.getClass().getSimpleName(),
                            "errorMessage", error.getMessage()
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to handle processing failure - AlertId: {}, IncidentId: {}", 
                    alertId, incidentId, e);
        }
    }
    
    public void handleCircuitBreakerFallback(String eventJson, String topic, int partition, 
                                           long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("CIRCUIT BREAKER: Data breach alert processing circuit breaker activated - Topic: {}, Error: {}", 
                topic, e.getMessage());
        
        // In a real implementation, you might want to:
        // 1. Store the event for later processing
        // 2. Send to a fallback queue
        // 3. Send critical alerts via alternative channels
        
        try {
            circuitBreakerService.handleFallback("data-breach-alerts", eventJson, e);
        } catch (Exception fallbackError) {
            log.error("Fallback handling failed for data breach alert", fallbackError);
        }
    }
    
    @DltHandler
    public void handleDlt(String eventJson, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                         @Header(value = "x-original-topic", required = false) String originalTopic,
                         @Header(value = "x-error-message", required = false) String errorMessage) {
        
        log.error("CRITICAL: Data breach alert sent to DLT - OriginalTopic: {}, Error: {}, Event: {}", 
                originalTopic, errorMessage, eventJson);
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            String alertId = (String) event.get("alertId");
            String incidentId = (String) event.get("incidentId");
            String breachType = (String) event.get("breachType");
            String severity = (String) event.get("severity");
            
            log.error("DLT: Data breach alert failed permanently - AlertId: {}, IncidentId: {}, Type: {}, Severity: {} - IMMEDIATE MANUAL INTERVENTION REQUIRED", 
                    alertId, incidentId, breachType, severity);
            
            // Critical: Send emergency notification for failed breach alerts
            securityNotificationService.sendEmergencyNotification(alertId, incidentId, breachType, 
                    severity, "DLT: " + errorMessage);
            
            incidentManagementService.markForEmergencyReview(alertId, incidentId, breachType, 
                    severity, "DLT: " + errorMessage);
            
        } catch (Exception e) {
            log.error("Failed to parse data breach alert DLT event: {}", eventJson, e);
        }
    }
    
    private boolean isAlreadyProcessed(String eventKey) {
        LocalDateTime processedTime = processedEvents.get(eventKey);
        if (processedTime != null) {
            // Check if processed within last 24 hours
            if (processedTime.isAfter(LocalDateTime.now().minusHours(24))) {
                return true;
            } else {
                // Remove expired entry
                processedEvents.remove(eventKey);
            }
        }
        return false;
    }
    
    private void markEventAsProcessed(String eventKey) {
        processedEvents.put(eventKey, LocalDateTime.now());
        
        // Clean up old entries periodically
        if (processedEvents.size() > 10000) {
            LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
            processedEvents.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
        }
    }
}