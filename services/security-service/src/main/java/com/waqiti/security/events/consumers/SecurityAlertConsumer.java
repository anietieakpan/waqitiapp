package com.waqiti.security.events.consumers;

import com.waqiti.common.audit.AuditLogger;
import com.waqiti.common.events.security.SecurityAlertEvent;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.security.domain.SecurityIncident;
import com.waqiti.security.domain.IncidentSeverity;
import com.waqiti.security.domain.IncidentStatus;
import com.waqiti.security.domain.ThreatIndicator;
import com.waqiti.security.repository.SecurityIncidentRepository;
import com.waqiti.security.service.ThreatDetectionService;
import com.waqiti.security.service.IncidentResponseService;
import com.waqiti.security.service.SecurityOrchestrationService;
import com.waqiti.security.service.ForensicsService;
import com.waqiti.security.service.SecurityNotificationService;
import com.waqiti.common.exceptions.SecurityException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Production-grade consumer for security alert events.
 * Handles comprehensive security monitoring including:
 * - Intrusion detection and prevention
 * - Account takeover (ATO) prevention
 * - Suspicious activity monitoring
 * - DDoS attack detection
 * - Data breach detection
 * - Automated incident response
 * 
 * Critical for platform security and threat mitigation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityAlertConsumer {

    private final SecurityIncidentRepository incidentRepository;
    private final ThreatDetectionService threatDetectionService;
    private final IncidentResponseService incidentResponseService;
    private final SecurityOrchestrationService orchestrationService;
    private final ForensicsService forensicsService;
    private final SecurityNotificationService notificationService;
    private final AuditLogger auditLogger;
    private final MetricsService metricsService;

    private static final int CRITICAL_THREAT_SCORE_THRESHOLD = 80;
    private static final int HIGH_THREAT_SCORE_THRESHOLD = 60;

    @KafkaListener(
        topics = "security-alerts",
        groupId = "security-service-alert-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        include = {SecurityException.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void handleSecurityAlert(
            @Payload SecurityAlertEvent alertEvent,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "correlation-id", required = false) String correlationId,
            @Header(value = "alert-priority", required = false) String priority,
            Acknowledgment acknowledgment) {

        String eventId = alertEvent.getEventId() != null ? 
            alertEvent.getEventId() : UUID.randomUUID().toString();

        try {
            log.warn("Processing security alert: {} type: {} severity: {} for entity: {}", 
                    eventId, alertEvent.getAlertType(), alertEvent.getSeverity(), alertEvent.getAffectedEntity());

            // Metrics tracking
            metricsService.incrementCounter("security.alert.processing.started",
                Map.of(
                    "alert_type", alertEvent.getAlertType(),
                    "severity", alertEvent.getSeverity()
                ));

            // Idempotency check
            if (isAlertAlreadyProcessed(alertEvent.getAffectedEntity(), eventId)) {
                log.info("Security alert {} already processed for entity {}", eventId, alertEvent.getAffectedEntity());
                acknowledgment.acknowledge();
                return;
            }

            // Create security incident record
            SecurityIncident incident = createIncidentRecord(alertEvent, eventId, correlationId);

            // Perform threat analysis
            performThreatAnalysis(incident, alertEvent);

            // Execute parallel security checks
            List<CompletableFuture<SecurityCheckResult>> securityTasks = 
                createSecurityCheckTasks(incident, alertEvent);

            // Wait for all security checks to complete
            CompletableFuture<Void> allChecks = CompletableFuture.allOf(
                securityTasks.toArray(new CompletableFuture[0])
            );

            allChecks.join();

            // Aggregate security check results
            List<SecurityCheckResult> results = securityTasks.stream()
                .map(future -> {
                    try {
                        return future.get();
                    } catch (Exception e) {
                        log.error("Security check failed: {}", e.getMessage());
                        return SecurityCheckResult.failure(e.getMessage());
                    }
                })
                .collect(Collectors.toList());

            // Analyze results and calculate threat score
            analyzeThreatIndicators(incident, results);

            // Determine incident response actions
            determineResponseActions(incident, alertEvent);

            // Execute automated response if critical
            if (incident.getSeverity() == IncidentSeverity.CRITICAL) {
                executeAutomatedResponse(incident, alertEvent);
            }

            // Update incident status
            updateIncidentStatus(incident);

            // Save incident record
            SecurityIncident savedIncident = incidentRepository.save(incident);

            // Collect forensic evidence
            collectForensicEvidence(savedIncident, alertEvent);

            // Send security notifications
            sendSecurityNotifications(savedIncident, alertEvent);

            // Update security metrics
            updateSecurityMetrics(savedIncident, alertEvent);

            // Create comprehensive audit trail
            createSecurityAuditLog(savedIncident, alertEvent, correlationId);

            // Success metrics
            metricsService.incrementCounter("security.alert.processing.success",
                Map.of(
                    "severity", savedIncident.getSeverity().toString(),
                    "status", savedIncident.getStatus().toString(),
                    "threat_score", String.valueOf(savedIncident.getThreatScore())
                ));

            log.warn("Successfully processed security alert: {} with threat score: {} status: {}", 
                    savedIncident.getId(), savedIncident.getThreatScore(), savedIncident.getStatus());

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error processing security alert event {}: {}", eventId, e.getMessage(), e);
            metricsService.incrementCounter("security.alert.processing.error");
            
            // Critical audit log for security failures
            auditLogger.logCriticalAlert("SECURITY_ALERT_PROCESSING_ERROR",
                "Critical security alert processing failure - platform at risk",
                Map.of(
                    "affectedEntity", alertEvent.getAffectedEntity(),
                    "alertType", alertEvent.getAlertType(),
                    "severity", alertEvent.getSeverity(),
                    "eventId", eventId,
                    "error", e.getMessage(),
                    "correlationId", correlationId != null ? correlationId : "N/A"
                ));
            
            throw new SecurityException("Failed to process security alert: " + e.getMessage(), e);
        }
    }

    @KafkaListener(
        topics = "security-alerts-critical",
        groupId = "security-service-critical-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleCriticalSecurityAlert(
            @Payload SecurityAlertEvent alertEvent,
            @Header(value = "correlation-id", required = false) String correlationId,
            Acknowledgment acknowledgment) {

        try {
            log.error("CRITICAL SECURITY ALERT: Processing immediate threat: {} for entity: {}", 
                    alertEvent.getAlertType(), alertEvent.getAffectedEntity());

            // Immediate containment actions
            SecurityIncident incident = performImmediateContainment(alertEvent, correlationId);

            // Execute emergency response
            executeEmergencyResponse(incident, alertEvent);

            // Notify security team immediately
            notificationService.sendCriticalSecurityAlert(incident);

            // Initiate war room if needed
            if (incident.getThreatScore() >= 90) {
                initiateSecurityWarRoom(incident);
            }

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process critical security alert: {}", e.getMessage(), e);
            acknowledgment.acknowledge(); // Acknowledge to prevent blocking critical queue
        }
    }

    private boolean isAlertAlreadyProcessed(String affectedEntity, String eventId) {
        return incidentRepository.existsByAffectedEntityAndEventId(affectedEntity, eventId);
    }

    private SecurityIncident createIncidentRecord(SecurityAlertEvent event, String eventId, String correlationId) {
        return SecurityIncident.builder()
            .id(UUID.randomUUID().toString())
            .eventId(eventId)
            .alertType(event.getAlertType())
            .severity(IncidentSeverity.valueOf(event.getSeverity().toUpperCase()))
            .affectedEntity(event.getAffectedEntity())
            .affectedEntityType(event.getEntityType())
            .sourceIp(event.getSourceIp())
            .sourceLocation(event.getSourceLocation())
            .userAgent(event.getUserAgent())
            .attackVector(event.getAttackVector())
            .description(event.getDescription())
            .detectionMethod(event.getDetectionMethod())
            .status(IncidentStatus.DETECTED)
            .correlationId(correlationId)
            .detectedAt(LocalDateTime.now())
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    private void performThreatAnalysis(SecurityIncident incident, SecurityAlertEvent event) {
        try {
            log.info("Performing threat analysis for incident: {}", incident.getId());

            // Analyze threat patterns
            var threatAnalysis = threatDetectionService.analyzeThreat(
                event.getAlertType(),
                event.getAttackVector(),
                event.getIndicators()
            );

            incident.setThreatCategory(threatAnalysis.getCategory());
            incident.setThreatActorProfile(threatAnalysis.getActorProfile());
            incident.setAttackPattern(threatAnalysis.getAttackPattern());
            incident.setMitreAttackTechniques(threatAnalysis.getMitreTechniques());

            // Check if part of larger campaign
            if (threatAnalysis.isPartOfCampaign()) {
                incident.setCampaignId(threatAnalysis.getCampaignId());
                incident.setCampaignName(threatAnalysis.getCampaignName());
            }

            // Determine blast radius
            incident.setBlastRadius(threatAnalysis.getBlastRadius());
            incident.setAffectedSystems(threatAnalysis.getAffectedSystems());

            log.info("Threat analysis completed: Category: {} Pattern: {}", 
                    threatAnalysis.getCategory(), threatAnalysis.getAttackPattern());

        } catch (Exception e) {
            log.error("Error in threat analysis: {}", e.getMessage());
            incident.setThreatAnalysisError(e.getMessage());
        }
    }

    private List<CompletableFuture<SecurityCheckResult>> createSecurityCheckTasks(
            SecurityIncident incident, SecurityAlertEvent event) {
        
        List<CompletableFuture<SecurityCheckResult>> tasks = new ArrayList<>();

        // IP reputation check
        tasks.add(CompletableFuture.supplyAsync(() -> 
            performIpReputationCheck(incident, event)));

        // User behavior analysis
        tasks.add(CompletableFuture.supplyAsync(() -> 
            performUserBehaviorAnalysis(incident, event)));

        // System integrity check
        tasks.add(CompletableFuture.supplyAsync(() -> 
            performSystemIntegrityCheck(incident, event)));

        // Data exfiltration check
        tasks.add(CompletableFuture.supplyAsync(() -> 
            performDataExfiltrationCheck(incident, event)));

        // Malware detection
        tasks.add(CompletableFuture.supplyAsync(() -> 
            performMalwareDetection(incident, event)));

        return tasks;
    }

    private SecurityCheckResult performIpReputationCheck(SecurityIncident incident, SecurityAlertEvent event) {
        try {
            log.info("Performing IP reputation check for: {}", event.getSourceIp());

            var ipReputation = threatDetectionService.checkIpReputation(event.getSourceIp());

            incident.setIpReputation(ipReputation.getReputationScore());
            incident.setIpThreatLevel(ipReputation.getThreatLevel());
            incident.setKnownMalicious(ipReputation.isKnownMalicious());

            // Check if IP is from TOR, VPN, or proxy
            incident.setFromTor(ipReputation.isTorExit());
            incident.setFromVpn(ipReputation.isVpn());
            incident.setFromProxy(ipReputation.isProxy());

            // Geolocation anomaly check
            if (ipReputation.isGeolocationAnomaly()) {
                incident.addThreatIndicator(ThreatIndicator.GEOLOCATION_ANOMALY);
            }

            return SecurityCheckResult.success(
                "IP_REPUTATION",
                !ipReputation.isKnownMalicious(),
                ipReputation.getThreatScore()
            );

        } catch (Exception e) {
            log.error("Error in IP reputation check: {}", e.getMessage());
            return SecurityCheckResult.failure("IP_REPUTATION", e.getMessage());
        }
    }

    private SecurityCheckResult performUserBehaviorAnalysis(SecurityIncident incident, SecurityAlertEvent event) {
        try {
            log.info("Performing user behavior analysis for entity: {}", event.getAffectedEntity());

            var behaviorAnalysis = threatDetectionService.analyzeUserBehavior(
                event.getAffectedEntity(),
                event.getActivityPattern(),
                event.getTimestamp()
            );

            incident.setBehaviorAnomalyScore(behaviorAnalysis.getAnomalyScore());
            incident.setDeviationFromBaseline(behaviorAnalysis.getDeviationPercentage());

            // Check for impossible travel
            if (behaviorAnalysis.hasImpossibleTravel()) {
                incident.addThreatIndicator(ThreatIndicator.IMPOSSIBLE_TRAVEL);
                incident.setImpossibleTravelDetected(true);
            }

            // Check for credential stuffing patterns
            if (behaviorAnalysis.hasCredentialStuffingPattern()) {
                incident.addThreatIndicator(ThreatIndicator.CREDENTIAL_STUFFING);
            }

            // Check for account takeover indicators
            if (behaviorAnalysis.hasAtoIndicators()) {
                incident.addThreatIndicator(ThreatIndicator.ACCOUNT_TAKEOVER);
                incident.setAccountTakeoverRisk(behaviorAnalysis.getAtoRiskScore());
            }

            return SecurityCheckResult.success(
                "USER_BEHAVIOR",
                behaviorAnalysis.getAnomalyScore() < 70,
                behaviorAnalysis.getAnomalyScore()
            );

        } catch (Exception e) {
            log.error("Error in user behavior analysis: {}", e.getMessage());
            return SecurityCheckResult.failure("USER_BEHAVIOR", e.getMessage());
        }
    }

    private SecurityCheckResult performSystemIntegrityCheck(SecurityIncident incident, SecurityAlertEvent event) {
        try {
            log.info("Performing system integrity check");

            var integrityCheck = orchestrationService.checkSystemIntegrity(
                event.getAffectedSystems()
            );

            incident.setIntegrityCompromised(integrityCheck.hasCompromise());
            incident.setCompromisedComponents(integrityCheck.getCompromisedComponents());

            // Check for privilege escalation
            if (integrityCheck.hasPrivilegeEscalation()) {
                incident.addThreatIndicator(ThreatIndicator.PRIVILEGE_ESCALATION);
                incident.setPrivilegeEscalationDetected(true);
            }

            // Check for rootkit presence
            if (integrityCheck.hasRootkitIndicators()) {
                incident.addThreatIndicator(ThreatIndicator.ROOTKIT_DETECTED);
            }

            return SecurityCheckResult.success(
                "SYSTEM_INTEGRITY",
                !integrityCheck.hasCompromise(),
                integrityCheck.getIntegrityScore()
            );

        } catch (Exception e) {
            log.error("Error in system integrity check: {}", e.getMessage());
            return SecurityCheckResult.failure("SYSTEM_INTEGRITY", e.getMessage());
        }
    }

    private SecurityCheckResult performDataExfiltrationCheck(SecurityIncident incident, SecurityAlertEvent event) {
        try {
            log.info("Performing data exfiltration check");

            var exfiltrationCheck = threatDetectionService.checkDataExfiltration(
                event.getAffectedEntity(),
                event.getDataTransferPattern()
            );

            incident.setDataExfiltrationDetected(exfiltrationCheck.isDetected());
            incident.setExfiltratedDataVolume(exfiltrationCheck.getDataVolume());
            incident.setExfiltrationDestination(exfiltrationCheck.getDestination());

            if (exfiltrationCheck.isDetected()) {
                incident.addThreatIndicator(ThreatIndicator.DATA_EXFILTRATION);
                incident.setSensitiveDataCompromised(exfiltrationCheck.hasSensitiveData());
            }

            return SecurityCheckResult.success(
                "DATA_EXFILTRATION",
                !exfiltrationCheck.isDetected(),
                exfiltrationCheck.getRiskScore()
            );

        } catch (Exception e) {
            log.error("Error in data exfiltration check: {}", e.getMessage());
            return SecurityCheckResult.failure("DATA_EXFILTRATION", e.getMessage());
        }
    }

    private SecurityCheckResult performMalwareDetection(SecurityIncident incident, SecurityAlertEvent event) {
        try {
            log.info("Performing malware detection");

            var malwareCheck = threatDetectionService.detectMalware(
                event.getPayload(),
                event.getFileHashes()
            );

            incident.setMalwareDetected(malwareCheck.isDetected());
            incident.setMalwareFamily(malwareCheck.getMalwareFamily());
            incident.setMalwareVariant(malwareCheck.getVariant());

            if (malwareCheck.isDetected()) {
                incident.addThreatIndicator(ThreatIndicator.MALWARE_DETECTED);
                incident.setMalwareIocs(malwareCheck.getIocs());
            }

            // Check for ransomware
            if (malwareCheck.isRansomware()) {
                incident.addThreatIndicator(ThreatIndicator.RANSOMWARE_DETECTED);
                incident.setRansomwareDetected(true);
            }

            return SecurityCheckResult.success(
                "MALWARE_DETECTION",
                !malwareCheck.isDetected(),
                malwareCheck.getThreatScore()
            );

        } catch (Exception e) {
            log.error("Error in malware detection: {}", e.getMessage());
            return SecurityCheckResult.failure("MALWARE_DETECTION", e.getMessage());
        }
    }

    private void analyzeThreatIndicators(SecurityIncident incident, List<SecurityCheckResult> results) {
        // Calculate composite threat score
        double totalScore = 0.0;
        int weightSum = 0;

        for (SecurityCheckResult result : results) {
            if (result.getThreatScore() != null) {
                totalScore += result.getThreatScore();
                weightSum++;
            }
        }

        double averageScore = weightSum > 0 ? totalScore / weightSum : 0.0;

        // Apply threat indicator multipliers
        if (incident.hasIndicator(ThreatIndicator.RANSOMWARE_DETECTED)) {
            averageScore *= 1.5;
        }
        if (incident.hasIndicator(ThreatIndicator.DATA_EXFILTRATION)) {
            averageScore *= 1.3;
        }
        if (incident.hasIndicator(ThreatIndicator.ACCOUNT_TAKEOVER)) {
            averageScore *= 1.2;
        }

        // Normalize to 0-100 scale
        incident.setThreatScore(Math.min(100, (int) averageScore));

        // Update severity based on threat score
        if (incident.getThreatScore() >= CRITICAL_THREAT_SCORE_THRESHOLD) {
            incident.setSeverity(IncidentSeverity.CRITICAL);
        } else if (incident.getThreatScore() >= HIGH_THREAT_SCORE_THRESHOLD) {
            incident.setSeverity(IncidentSeverity.HIGH);
        }
    }

    private void determineResponseActions(SecurityIncident incident, SecurityAlertEvent event) {
        List<String> responseActions = new ArrayList<>();

        // Critical severity responses
        if (incident.getSeverity() == IncidentSeverity.CRITICAL) {
            responseActions.add("IMMEDIATE_CONTAINMENT");
            responseActions.add("ISOLATE_AFFECTED_SYSTEMS");
            responseActions.add("BLOCK_IP_ADDRESS");
            responseActions.add("SUSPEND_USER_ACCOUNT");
            responseActions.add("INITIATE_INCIDENT_RESPONSE_TEAM");
        }

        // Specific threat responses
        if (incident.hasIndicator(ThreatIndicator.RANSOMWARE_DETECTED)) {
            responseActions.add("ISOLATE_NETWORK_SEGMENT");
            responseActions.add("BACKUP_CRITICAL_DATA");
            responseActions.add("DISABLE_RDP_ACCESS");
        }

        if (incident.hasIndicator(ThreatIndicator.DATA_EXFILTRATION)) {
            responseActions.add("BLOCK_OUTBOUND_TRAFFIC");
            responseActions.add("REVOKE_DATA_ACCESS");
            responseActions.add("NOTIFY_DATA_PROTECTION_OFFICER");
        }

        if (incident.hasIndicator(ThreatIndicator.ACCOUNT_TAKEOVER)) {
            responseActions.add("FORCE_PASSWORD_RESET");
            responseActions.add("REVOKE_ALL_SESSIONS");
            responseActions.add("ENABLE_MFA");
        }

        incident.setResponseActions(responseActions);
        incident.setResponsePlan(incidentResponseService.generateResponsePlan(incident));
    }

    private void executeAutomatedResponse(SecurityIncident incident, SecurityAlertEvent event) {
        try {
            log.warn("Executing automated response for critical incident: {}", incident.getId());

            for (String action : incident.getResponseActions()) {
                boolean executed = incidentResponseService.executeAction(action, incident);
                if (executed) {
                    incident.addExecutedAction(action, LocalDateTime.now());
                }
            }

            // Update incident status
            incident.setStatus(IncidentStatus.CONTAINED);
            incident.setContainedAt(LocalDateTime.now());

        } catch (Exception e) {
            log.error("Error executing automated response: {}", e.getMessage());
            incident.setResponseError(e.getMessage());
        }
    }

    private void updateIncidentStatus(SecurityIncident incident) {
        if (incident.getExecutedActions() != null && !incident.getExecutedActions().isEmpty()) {
            incident.setStatus(IncidentStatus.RESPONDED);
        } else if (incident.getThreatScore() >= CRITICAL_THREAT_SCORE_THRESHOLD) {
            incident.setStatus(IncidentStatus.ESCALATED);
        } else {
            incident.setStatus(IncidentStatus.INVESTIGATING);
        }

        incident.setLastUpdatedAt(LocalDateTime.now());
        incident.setProcessingTimeMs(
            ChronoUnit.MILLIS.between(incident.getDetectedAt(), LocalDateTime.now())
        );
    }

    private void collectForensicEvidence(SecurityIncident incident, SecurityAlertEvent event) {
        try {
            log.info("Collecting forensic evidence for incident: {}", incident.getId());

            var forensicData = forensicsService.collectEvidence(
                incident.getId(),
                incident.getAffectedEntity(),
                incident.getAffectedSystems()
            );

            incident.setForensicDataCollected(true);
            incident.setForensicDataLocation(forensicData.getStorageLocation());
            incident.setForensicDataHash(forensicData.getHash());
            incident.setChainOfCustody(forensicData.getChainOfCustody());

        } catch (Exception e) {
            log.error("Error collecting forensic evidence: {}", e.getMessage());
        }
    }

    private void sendSecurityNotifications(SecurityIncident incident, SecurityAlertEvent event) {
        try {
            // Standard security notification
            notificationService.sendSecurityIncidentNotification(incident);

            // Critical incident escalation
            if (incident.getSeverity() == IncidentSeverity.CRITICAL) {
                notificationService.sendCriticalIncidentEscalation(incident);
                notificationService.notifySecurityLeadership(incident);
            }

            // Ransomware specific notifications
            if (incident.hasIndicator(ThreatIndicator.RANSOMWARE_DETECTED)) {
                notificationService.sendRansomwareAlert(incident);
            }

            // Data breach notifications
            if (incident.hasIndicator(ThreatIndicator.DATA_EXFILTRATION)) {
                notificationService.sendDataBreachNotification(incident);
            }

        } catch (Exception e) {
            log.error("Failed to send security notifications: {}", e.getMessage());
        }
    }

    private void updateSecurityMetrics(SecurityIncident incident, SecurityAlertEvent event) {
        try {
            // Record incident metrics
            metricsService.incrementCounter("security.incident.created",
                Map.of(
                    "severity", incident.getSeverity().toString(),
                    "alert_type", incident.getAlertType(),
                    "status", incident.getStatus().toString()
                ));

            // Record threat score
            metricsService.recordGauge("security.threat_score", incident.getThreatScore(),
                Map.of("severity", incident.getSeverity().toString()));

            // Record response time
            metricsService.recordTimer("security.response_time_ms", incident.getProcessingTimeMs(),
                Map.of("severity", incident.getSeverity().toString()));

            // Update security posture metrics
            metricsService.incrementCounter("security.threat_indicators",
                Map.of("count", String.valueOf(incident.getThreatIndicators().size())));

        } catch (Exception e) {
            log.error("Failed to update security metrics: {}", e.getMessage());
        }
    }

    private void createSecurityAuditLog(SecurityIncident incident, SecurityAlertEvent event, String correlationId) {
        auditLogger.logSecurityEvent(
            "SECURITY_INCIDENT_PROCESSED",
            incident.getAffectedEntity(),
            incident.getId(),
            incident.getAlertType(),
            incident.getThreatScore(),
            "security_processor",
            incident.getStatus() != IncidentStatus.ESCALATED,
            Map.of(
                "incidentId", incident.getId(),
                "alertType", incident.getAlertType(),
                "severity", incident.getSeverity().toString(),
                "status", incident.getStatus().toString(),
                "threatScore", String.valueOf(incident.getThreatScore()),
                "affectedEntity", incident.getAffectedEntity(),
                "sourceIp", incident.getSourceIp() != null ? incident.getSourceIp() : "N/A",
                "threatIndicators", incident.getThreatIndicators().stream()
                    .map(ThreatIndicator::name)
                    .collect(Collectors.joining(",")),
                "responseActions", String.join(",", incident.getResponseActions()),
                "processingTimeMs", String.valueOf(incident.getProcessingTimeMs()),
                "correlationId", correlationId != null ? correlationId : "N/A",
                "eventId", event.getEventId()
            )
        );
    }

    private SecurityIncident performImmediateContainment(SecurityAlertEvent event, String correlationId) {
        SecurityIncident incident = createIncidentRecord(event, UUID.randomUUID().toString(), correlationId);
        incident.setSeverity(IncidentSeverity.CRITICAL);
        incident.setThreatScore(100);
        
        // Immediate containment actions
        incidentResponseService.blockIpAddress(event.getSourceIp());
        incidentResponseService.suspendUserAccount(event.getAffectedEntity());
        incidentResponseService.isolateSystem(event.getAffectedSystems());
        
        incident.setStatus(IncidentStatus.CONTAINED);
        incident.setContainedAt(LocalDateTime.now());
        
        return incidentRepository.save(incident);
    }

    private void executeEmergencyResponse(SecurityIncident incident, SecurityAlertEvent event) {
        log.error("Executing emergency response for critical incident: {}", incident.getId());
        
        // Kill all active sessions
        orchestrationService.killAllSessions(incident.getAffectedEntity());
        
        // Block all network traffic
        orchestrationService.blockAllTraffic(incident.getAffectedSystems());
        
        // Initiate system snapshot for forensics
        forensicsService.createEmergencySnapshot(incident.getId());
    }

    private void initiateSecurityWarRoom(SecurityIncident incident) {
        log.error("INITIATING SECURITY WAR ROOM for incident: {}", incident.getId());
        notificationService.initiateWarRoom(incident);
    }

    /**
     * Internal class for security check results
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class SecurityCheckResult {
        private String checkType;
        private boolean passed;
        private Double threatScore;
        private String error;

        public static SecurityCheckResult success(String type, boolean passed, double score) {
            return new SecurityCheckResult(type, passed, score, null);
        }

        public static SecurityCheckResult failure(String type, String error) {
            return new SecurityCheckResult(type, false, 100.0, error);
        }

        public static SecurityCheckResult failure(String error) {
            return new SecurityCheckResult(null, false, 100.0, error);
        }
    }
}