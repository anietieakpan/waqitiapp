package com.waqiti.security.consumer;

import com.waqiti.common.events.SecurityIncidentDetectedEvent;
import com.waqiti.security.service.IncidentResponseService;
import com.waqiti.security.service.ThreatAnalysisService;
import com.waqiti.security.service.ForensicsService;
import com.waqiti.security.service.ContainmentService;
import com.waqiti.security.service.NotificationService;
import com.waqiti.security.service.ComplianceService;
import com.waqiti.security.service.EvidenceCollectionService;
import com.waqiti.security.repository.ProcessedEventRepository;
import com.waqiti.security.repository.SecurityIncidentRepository;
import com.waqiti.security.model.ProcessedEvent;
import com.waqiti.security.model.SecurityIncident;
import com.waqiti.security.model.IncidentStatus;
import com.waqiti.security.model.ThreatLevel;
import com.waqiti.security.model.IncidentType;
import com.waqiti.security.model.ResponsePhase;
import com.waqiti.security.model.ContainmentAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.*;

/**
 * Consumer for SecurityIncidentDetectedEvent - Critical for cybersecurity and threat response
 * Handles incident analysis, containment, forensics, and regulatory compliance
 * ZERO TOLERANCE: All security incidents must be contained and investigated immediately
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SecurityIncidentDetectedEventConsumer {
    
    private final IncidentResponseService incidentResponseService;
    private final ThreatAnalysisService threatAnalysisService;
    private final ForensicsService forensicsService;
    private final ContainmentService containmentService;
    private final NotificationService notificationService;
    private final ComplianceService complianceService;
    private final EvidenceCollectionService evidenceCollectionService;
    private final ProcessedEventRepository processedEventRepository;
    private final SecurityIncidentRepository securityIncidentRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final Duration CRITICAL_RESPONSE_TIME = Duration.ofMinutes(15);
    private static final Duration HIGH_RESPONSE_TIME = Duration.ofMinutes(60);
    private static final Duration FORENSICS_PRESERVATION_HOURS = Duration.ofHours(72);
    
    private static final Map<String, ThreatLevel> THREAT_LEVEL_MAPPING = Map.of(
        "DATA_BREACH", ThreatLevel.CRITICAL,
        "MALWARE_DETECTED", ThreatLevel.HIGH,
        "UNAUTHORIZED_ACCESS", ThreatLevel.HIGH,
        "DDOS_ATTACK", ThreatLevel.HIGH,
        "PHISHING_ATTEMPT", ThreatLevel.MEDIUM,
        "SUSPICIOUS_ACTIVITY", ThreatLevel.MEDIUM,
        "POLICY_VIOLATION", ThreatLevel.LOW
    );
    
    @KafkaListener(
        topics = "security.incident.detected",
        groupId = "security-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE) // Highest isolation for security incidents
    public void handleSecurityIncidentDetected(SecurityIncidentDetectedEvent event) {
        log.error("SECURITY INCIDENT DETECTED: {} - Type: {} - Severity: {} - Source: {} - Affected Systems: {}", 
            event.getIncidentId(), event.getIncidentType(), event.getSeverity(),
            event.getSourceSystem(), event.getAffectedSystems().size());
        
        // IDEMPOTENCY CHECK - Prevent duplicate incident processing
        if (processedEventRepository.existsByEventId(event.getEventId())) {
            log.warn("Security incident already processed: {}", event.getEventId());
            return;
        }
        
        try {
            // Create security incident record
            SecurityIncident incident = createSecurityIncident(event);
            
            // STEP 1: Assess threat level and classify incident severity
            assessThreatLevelAndClassify(incident, event);
            
            // STEP 2: Execute immediate containment and isolation procedures
            executeImmediateContainmentAndIsolation(incident, event);
            
            // STEP 3: Perform comprehensive threat analysis and attribution
            performThreatAnalysisAndAttribution(incident, event);
            
            // STEP 4: Collect and preserve digital evidence and forensics
            collectAndPreserveDigitalEvidence(incident, event);
            
            // STEP 5: Analyze attack vectors and compromise indicators
            analyzeAttackVectorsAndIndicators(incident, event);
            
            // STEP 6: Execute incident response playbook procedures
            executeIncidentResponsePlaybook(incident, event);
            
            // STEP 7: Assess data breach and privacy impact scope
            assessDataBreachAndPrivacyImpact(incident, event);
            
            // STEP 8: Implement recovery and restoration procedures
            implementRecoveryAndRestoration(incident, event);
            
            // STEP 9: Generate regulatory notifications and breach reports
            generateRegulatoryNotificationsAndReports(incident, event);
            
            // STEP 10: Conduct post-incident analysis and lessons learned
            conductPostIncidentAnalysis(incident, event);
            
            // STEP 11: Send security alerts and stakeholder notifications
            sendSecurityAlertsAndNotifications(incident, event);
            
            // STEP 12: Record successful processing and schedule follow-ups
            ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventId(event.getEventId())
                .eventType("SecurityIncidentDetectedEvent")
                .processedAt(Instant.now())
                .incidentId(event.getIncidentId())
                .incidentType(event.getIncidentType())
                .severity(event.getSeverity())
                .sourceSystem(event.getSourceSystem())
                .affectedSystemsCount(event.getAffectedSystems().size())
                .threatLevel(incident.getThreatLevel())
                .incidentStatus(incident.getStatus())
                .containmentExecuted(incident.isContainmentExecuted())
                .evidenceCollected(incident.isEvidenceCollected())
                .regulatoryNotificationRequired(incident.isRegulatoryNotificationRequired())
                .build();
                
            processedEventRepository.save(processedEvent);
            
            log.error("SECURITY INCIDENT PROCESSED: {} - Status: {}, Threat Level: {}, Contained: {}, Evidence: {}, Regulatory: {}", 
                event.getIncidentId(), incident.getStatus(), incident.getThreatLevel(),
                incident.isContainmentExecuted(), incident.isEvidenceCollected(), incident.isRegulatoryNotificationRequired());
                
        } catch (Exception e) {
            log.error("CRITICAL: Failed to process security incident: {}", 
                event.getIncidentId(), e);
                
            // Create emergency intervention record
            createEmergencyInterventionRecord(event, e);
            
            throw new RuntimeException("Security incident processing failed", e);
        }
    }
    
    private SecurityIncident createSecurityIncident(SecurityIncidentDetectedEvent event) {
        SecurityIncident incident = SecurityIncident.builder()
            .id(event.getIncidentId())
            .incidentType(mapIncidentType(event.getIncidentType()))
            .severity(event.getSeverity())
            .sourceSystem(event.getSourceSystem())
            .affectedSystems(new ArrayList<>(event.getAffectedSystems()))
            .detectedAt(event.getDetectedAt())
            .detectionSource(event.getDetectionSource())
            .alertDetails(event.getAlertDetails())
            .initialEvidence(event.getInitialEvidence())
            .status(IncidentStatus.DETECTED)
            .responsePhase(ResponsePhase.DETECTION)
            .createdAt(LocalDateTime.now())
            .securityFlags(new ArrayList<>())
            .containmentActions(new ArrayList<>())
            .forensicsFindings(new ArrayList<>())
            .build();
        
        return securityIncidentRepository.save(incident);
    }
    
    private void assessThreatLevelAndClassify(SecurityIncident incident, SecurityIncidentDetectedEvent event) {
        // Determine threat level based on incident type
        ThreatLevel threatLevel = THREAT_LEVEL_MAPPING.getOrDefault(
            event.getIncidentType(),
            ThreatLevel.MEDIUM
        );
        
        // Adjust threat level based on affected systems
        if (event.getAffectedSystems().stream().anyMatch(system -> 
            system.contains("core-banking") || 
            system.contains("payment") || 
            system.contains("customer-data"))) {
            
            threatLevel = ThreatLevel.CRITICAL;
            incident.addSecurityFlag("CRITICAL_SYSTEM_AFFECTED");
        }
        
        // Escalate based on financial data exposure
        if (event.getAlertDetails() != null && 
            (event.getAlertDetails().contains("PII") || 
             event.getAlertDetails().contains("financial") ||
             event.getAlertDetails().contains("payment"))) {
            
            if (threatLevel.ordinal() < ThreatLevel.HIGH.ordinal()) {
                threatLevel = ThreatLevel.HIGH;
            }
            incident.addSecurityFlag("FINANCIAL_DATA_EXPOSURE_RISK");
        }
        
        incident.setThreatLevel(threatLevel);
        
        // Calculate response time requirements
        Duration requiredResponseTime = switch (threatLevel) {
            case CRITICAL -> CRITICAL_RESPONSE_TIME;
            case HIGH -> HIGH_RESPONSE_TIME;
            case MEDIUM -> Duration.ofHours(4);
            case LOW -> Duration.ofHours(24);
        };
        
        incident.setRequiredResponseTime(requiredResponseTime);
        incident.setResponseDeadline(LocalDateTime.now().plus(requiredResponseTime));
        
        // Classify incident category for reporting
        String incidentCategory = threatAnalysisService.classifyIncidentCategory(
            event.getIncidentType(),
            event.getAlertDetails(),
            event.getAffectedSystems()
        );
        
        incident.setIncidentCategory(incidentCategory);
        
        // Assess potential impact scope
        Map<String, Object> impactAssessment = threatAnalysisService.assessPotentialImpact(
            event.getAffectedSystems(),
            event.getIncidentType(),
            threatLevel
        );
        
        incident.setImpactAssessment(impactAssessment);
        
        int estimatedAffectedUsers = (Integer) impactAssessment.get("estimatedAffectedUsers");
        BigDecimal estimatedFinancialImpact = (BigDecimal) impactAssessment.get("estimatedFinancialImpact");
        
        incident.setEstimatedAffectedUsers(estimatedAffectedUsers);
        incident.setEstimatedFinancialImpact(estimatedFinancialImpact);
        
        // Check for breach notification requirements
        boolean breachNotificationRequired = complianceService.assessBreachNotificationRequirement(
            incidentCategory,
            estimatedAffectedUsers,
            threatLevel
        );
        
        incident.setRegulatoryNotificationRequired(breachNotificationRequired);
        
        if (breachNotificationRequired) {
            incident.addSecurityFlag("BREACH_NOTIFICATION_REQUIRED");
        }
        
        securityIncidentRepository.save(incident);
        
        log.error("Threat assessment completed for incident {}: Level: {}, Category: {}, Response time: {} minutes, Users: {}", 
            event.getIncidentId(), threatLevel, incidentCategory, requiredResponseTime.toMinutes(), estimatedAffectedUsers);
    }
    
    private void executeImmediateContainmentAndIsolation(SecurityIncident incident, SecurityIncidentDetectedEvent event) {
        List<ContainmentAction> containmentActions = new ArrayList<>();
        
        // Execute automatic containment based on threat level
        if (incident.getThreatLevel() == ThreatLevel.CRITICAL) {
            // Immediate system isolation
            for (String affectedSystem : event.getAffectedSystems()) {
                try {
                    String isolationId = containmentService.isolateSystem(
                        affectedSystem,
                        event.getIncidentId(),
                        "CRITICAL_THREAT_ISOLATION"
                    );
                    
                    containmentActions.add(ContainmentAction.builder()
                        .action("SYSTEM_ISOLATION")
                        .target(affectedSystem)
                        .actionId(isolationId)
                        .executedAt(LocalDateTime.now())
                        .success(true)
                        .build());
                        
                } catch (Exception e) {
                    containmentActions.add(ContainmentAction.builder()
                        .action("SYSTEM_ISOLATION_FAILED")
                        .target(affectedSystem)
                        .executedAt(LocalDateTime.now())
                        .success(false)
                        .errorMessage(e.getMessage())
                        .build());
                        
                    log.error("Failed to isolate system {} for incident {}: {}", 
                        affectedSystem, event.getIncidentId(), e.getMessage());
                }
            }
            
            // Activate incident response team
            String warRoomId = incidentResponseService.activateIncidentResponseTeam(
                event.getIncidentId(),
                incident.getThreatLevel(),
                event.getAffectedSystems()
            );
            
            incident.setWarRoomId(warRoomId);
        }
        
        // Block suspicious IP addresses if available
        if (event.getInitialEvidence() != null) {
            List<String> suspiciousIPs = threatAnalysisService.extractSuspiciousIPs(
                event.getInitialEvidence()
            );
            
            for (String ipAddress : suspiciousIPs) {
                try {
                    String blockId = containmentService.blockIPAddress(
                        ipAddress,
                        event.getIncidentId(),
                        Duration.ofHours(24)
                    );
                    
                    containmentActions.add(ContainmentAction.builder()
                        .action("IP_BLOCK")
                        .target(ipAddress)
                        .actionId(blockId)
                        .executedAt(LocalDateTime.now())
                        .success(true)
                        .build());
                        
                } catch (Exception e) {
                    log.error("Failed to block IP {} for incident {}: {}", 
                        ipAddress, event.getIncidentId(), e.getMessage());
                }
            }
        }
        
        // Disable compromised user accounts
        List<String> compromisedAccounts = threatAnalysisService.identifyCompromisedAccounts(
            event.getAlertDetails(),
            event.getAffectedSystems()
        );
        
        for (String accountId : compromisedAccounts) {
            try {
                String disableId = containmentService.disableUserAccount(
                    accountId,
                    event.getIncidentId(),
                    "SECURITY_INCIDENT_PRECAUTION"
                );
                
                containmentActions.add(ContainmentAction.builder()
                    .action("ACCOUNT_DISABLE")
                    .target(accountId)
                    .actionId(disableId)
                    .executedAt(LocalDateTime.now())
                    .success(true)
                    .build());
                    
            } catch (Exception e) {
                log.error("Failed to disable account {} for incident {}: {}", 
                    accountId, event.getIncidentId(), e.getMessage());
            }
        }
        
        // Execute malware containment if detected
        if ("MALWARE_DETECTED".equals(event.getIncidentType())) {
            List<String> malwareContainmentIds = containmentService.executeMalwareContainment(
                event.getAffectedSystems(),
                event.getIncidentId()
            );
            
            for (String containmentId : malwareContainmentIds) {
                containmentActions.add(ContainmentAction.builder()
                    .action("MALWARE_CONTAINMENT")
                    .target("SYSTEM_WIDE")
                    .actionId(containmentId)
                    .executedAt(LocalDateTime.now())
                    .success(true)
                    .build());
            }
        }
        
        incident.setContainmentActions(containmentActions);
        incident.setContainmentExecuted(true);
        incident.setContainmentExecutedAt(LocalDateTime.now());
        incident.setResponsePhase(ResponsePhase.CONTAINMENT);
        
        // Calculate containment effectiveness
        long successfulActions = containmentActions.stream()
            .mapToLong(action -> action.isSuccess() ? 1 : 0)
            .sum();
        
        double containmentEffectiveness = (double) successfulActions / containmentActions.size();
        incident.setContainmentEffectiveness(containmentEffectiveness);
        
        securityIncidentRepository.save(incident);
        
        log.error("Containment executed for incident {}: Actions: {}, Success rate: {}%, War room: {}", 
            event.getIncidentId(), containmentActions.size(), containmentEffectiveness * 100, 
            incident.getWarRoomId() != null);
    }
    
    private void performThreatAnalysisAndAttribution(SecurityIncident incident, SecurityIncidentDetectedEvent event) {
        // Analyze threat intelligence and attribution
        Map<String, Object> threatIntelligence = threatAnalysisService.analyzeThreatIntelligence(
            event.getIncidentType(),
            event.getInitialEvidence(),
            event.getAffectedSystems()
        );
        
        incident.setThreatIntelligence(threatIntelligence);
        
        // Identify attack patterns and TTPs (Tactics, Techniques, Procedures)
        List<String> attackPatterns = threatAnalysisService.identifyAttackPatterns(
            event.getAlertDetails(),
            event.getInitialEvidence()
        );
        
        incident.setAttackPatterns(attackPatterns);
        
        // Perform threat actor attribution
        Map<String, Object> attributionAnalysis = threatAnalysisService.performThreatAttribution(
            attackPatterns,
            event.getSourceSystem(),
            event.getDetectionSource()
        );
        
        incident.setAttributionAnalysis(attributionAnalysis);
        
        String suspectedThreatActor = (String) attributionAnalysis.get("suspectedActor");
        Double confidenceScore = (Double) attributionAnalysis.get("confidenceScore");
        
        incident.setSuspectedThreatActor(suspectedThreatActor);
        incident.setAttributionConfidence(confidenceScore);
        
        // Analyze attack timeline and progression
        Map<String, LocalDateTime> attackTimeline = threatAnalysisService.reconstructAttackTimeline(
            event.getDetectedAt(),
            event.getInitialEvidence(),
            event.getAffectedSystems()
        );
        
        incident.setAttackTimeline(attackTimeline);
        
        // Assess threat sophistication level
        String sophisticationLevel = threatAnalysisService.assessThreatSophistication(
            attackPatterns,
            attributionAnalysis,
            incident.getThreatLevel()
        );
        
        incident.setSophisticationLevel(sophisticationLevel);
        
        // Check for APT (Advanced Persistent Threat) indicators
        boolean aptIndicators = threatAnalysisService.checkAPTIndicators(
            attackPatterns,
            attackTimeline,
            suspectedThreatActor
        );
        
        if (aptIndicators) {
            incident.addSecurityFlag("APT_INDICATORS_DETECTED");
            incident.setSuspectedAPT(true);
        }
        
        // Analyze lateral movement patterns
        List<String> lateralMovementPaths = threatAnalysisService.analyzeLateralMovement(
            event.getAffectedSystems(),
            attackTimeline
        );
        
        incident.setLateralMovementPaths(lateralMovementPaths);
        
        if (!lateralMovementPaths.isEmpty()) {
            incident.addSecurityFlag("LATERAL_MOVEMENT_DETECTED");
        }
        
        securityIncidentRepository.save(incident);
        
        log.error("Threat analysis completed for incident {}: Actor: {}, Confidence: {}%, Sophistication: {}, APT: {}", 
            event.getIncidentId(), suspectedThreatActor, confidenceScore * 100, 
            sophisticationLevel, aptIndicators);
    }
    
    private void collectAndPreserveDigitalEvidence(SecurityIncident incident, SecurityIncidentDetectedEvent event) {
        List<String> evidenceCollectionIds = new ArrayList<>();
        
        // Collect system logs and artifacts
        for (String affectedSystem : event.getAffectedSystems()) {
            try {
                String logCollectionId = evidenceCollectionService.collectSystemLogs(
                    affectedSystem,
                    event.getDetectedAt().minusHours(24), // 24 hours before incident
                    LocalDateTime.now(),
                    event.getIncidentId()
                );
                
                evidenceCollectionIds.add(logCollectionId);
                
            } catch (Exception e) {
                log.error("Failed to collect logs from system {} for incident {}: {}", 
                    affectedSystem, event.getIncidentId(), e.getMessage());
            }
        }
        
        // Collect network traffic artifacts
        String networkEvidenceId = evidenceCollectionService.collectNetworkEvidence(
            event.getAffectedSystems(),
            event.getDetectedAt().minusHours(12),
            LocalDateTime.now(),
            event.getIncidentId()
        );
        
        if (networkEvidenceId != null) {
            evidenceCollectionIds.add(networkEvidenceId);
        }
        
        // Create memory dumps for critical systems
        if (incident.getThreatLevel() == ThreatLevel.CRITICAL) {
            for (String system : event.getAffectedSystems()) {
                if (system.contains("core-banking") || system.contains("payment")) {
                    try {
                        String memoryDumpId = evidenceCollectionService.createMemoryDump(
                            system,
                            event.getIncidentId()
                        );
                        
                        evidenceCollectionIds.add(memoryDumpId);
                        
                    } catch (Exception e) {
                        log.error("Failed to create memory dump for system {} in incident {}: {}", 
                            system, event.getIncidentId(), e.getMessage());
                    }
                }
            }
        }
        
        // Collect file system artifacts
        List<String> fileSystemArtifacts = evidenceCollectionService.collectFileSystemArtifacts(
            event.getAffectedSystems(),
            incident.getAttackPatterns(),
            event.getIncidentId()
        );
        
        evidenceCollectionIds.addAll(fileSystemArtifacts);
        
        // Create forensic disk images if warranted
        if (incident.getThreatLevel() == ThreatLevel.CRITICAL && 
            ("DATA_BREACH".equals(event.getIncidentType()) || 
             "MALWARE_DETECTED".equals(event.getIncidentType()))) {
            
            List<String> diskImageIds = forensicsService.createForensicDiskImages(
                event.getAffectedSystems(),
                event.getIncidentId()
            );
            
            evidenceCollectionIds.addAll(diskImageIds);
        }
        
        // Preserve evidence with chain of custody
        String chainOfCustodyId = evidenceCollectionService.establishChainOfCustody(
            evidenceCollectionIds,
            event.getIncidentId(),
            LocalDateTime.now(),
            FORENSICS_PRESERVATION_HOURS
        );
        
        incident.setEvidenceCollectionIds(evidenceCollectionIds);
        incident.setChainOfCustodyId(chainOfCustodyId);
        incident.setEvidenceCollected(true);
        incident.setEvidenceCollectedAt(LocalDateTime.now());
        
        // Calculate evidence preservation timeline
        LocalDateTime evidenceExpiryDate = LocalDateTime.now().plus(FORENSICS_PRESERVATION_HOURS);
        incident.setEvidenceExpiryDate(evidenceExpiryDate);
        
        // Create forensics preservation order
        String preservationOrderId = forensicsService.createPreservationOrder(
            evidenceCollectionIds,
            chainOfCustodyId,
            event.getIncidentId(),
            evidenceExpiryDate
        );
        
        incident.setPreservationOrderId(preservationOrderId);
        
        securityIncidentRepository.save(incident);
        
        log.error("Evidence collection completed for incident {}: Evidence items: {}, Chain of custody: {}, Expires: {}", 
            event.getIncidentId(), evidenceCollectionIds.size(), chainOfCustodyId, evidenceExpiryDate);
    }
    
    private void analyzeAttackVectorsAndIndicators(SecurityIncident incident, SecurityIncidentDetectedEvent event) {
        // Analyze attack vectors
        List<String> attackVectors = threatAnalysisService.identifyAttackVectors(
            event.getAlertDetails(),
            incident.getAttackPatterns(),
            incident.getLateralMovementPaths()
        );
        
        incident.setAttackVectors(attackVectors);
        
        // Extract and analyze Indicators of Compromise (IoCs)
        Map<String, List<String>> iocs = threatAnalysisService.extractIndicatorsOfCompromise(
            event.getInitialEvidence(),
            incident.getEvidenceCollectionIds()
        );
        
        incident.setIndicatorsOfCompromise(iocs);
        
        // Validate IoCs against threat intelligence feeds
        Map<String, Object> iocValidation = threatAnalysisService.validateIoCsAgainstThreatIntel(
            iocs,
            incident.getSuspectedThreatActor()
        );
        
        incident.setIocValidationResults(iocValidation);
        
        boolean knownThreatIoCs = (Boolean) iocValidation.get("knownThreatMatches");
        
        if (knownThreatIoCs) {
            incident.addSecurityFlag("KNOWN_THREAT_INDICATORS");
        }
        
        // Analyze vulnerability exploitation
        List<String> exploitedVulnerabilities = threatAnalysisService.identifyExploitedVulnerabilities(
            attackVectors,
            event.getAffectedSystems(),
            incident.getAttackPatterns()
        );
        
        incident.setExploitedVulnerabilities(exploitedVulnerabilities);
        
        // Check for zero-day exploitation
        boolean zerodayExploitation = threatAnalysisService.checkZeroDayExploitation(
            exploitedVulnerabilities,
            incident.getAttackPatterns()
        );
        
        if (zeroday") {
            incident.addSecurityFlag("ZERO_DAY_EXPLOITATION");
            incident.setZeroDayExploit(true);
        }
        
        // Analyze data exfiltration indicators
        Map<String, Object> exfiltrationAnalysis = threatAnalysisService.analyzeDataExfiltration(
            incident.getEvidenceCollectionIds(),
            attackVectors,
            event.getAffectedSystems()
        );
        
        incident.setExfiltrationAnalysis(exfiltrationAnalysis);
        
        boolean dataExfiltrated = (Boolean) exfiltrationAnalysis.get("dataExfiltrated");
        
        if (dataExfiltrated) {
            incident.addSecurityFlag("DATA_EXFILTRATION_DETECTED");
            incident.setDataExfiltrated(true);
            
            List<String> exfiltratedDataTypes = (List<String>) exfiltrationAnalysis.get("dataTypes");
            incident.setExfiltratedDataTypes(exfiltratedDataTypes);
        }
        
        // Create threat intelligence report
        String threatIntelReportId = threatAnalysisService.generateThreatIntelligenceReport(
            event.getIncidentId(),
            iocs,
            attackVectors,
            exploitedVulnerabilities,
            incident.getSuspectedThreatActor()
        );
        
        incident.setThreatIntelReportId(threatIntelReportId);
        
        securityIncidentRepository.save(incident);
        
        log.error("Attack vector analysis completed for incident {}: Vectors: {}, IoCs: {}, Vulnerabilities: {}, Zero-day: {}, Exfiltration: {}", 
            event.getIncidentId(), attackVectors.size(), iocs.size(), 
            exploitedVulnerabilities.size(), zeroday, dataExfiltrated);
    }
    
    private void executeIncidentResponsePlaybook(SecurityIncident incident, SecurityIncidentDetectedEvent event) {
        // Load appropriate incident response playbook
        String playbookId = incidentResponseService.selectIncidentResponsePlaybook(
            incident.getIncidentType(),
            incident.getThreatLevel(),
            incident.getIncidentCategory()
        );
        
        incident.setPlaybookId(playbookId);
        
        // Execute playbook procedures
        List<String> executedProcedures = incidentResponseService.executePlaybookProcedures(
            playbookId,
            event.getIncidentId(),
            incident.getThreatLevel(),
            event.getAffectedSystems()
        );
        
        incident.setExecutedProcedures(executedProcedures);
        
        // Update incident status based on playbook progress
        incident.setResponsePhase(ResponsePhase.INVESTIGATION);
        
        // Assign incident response team members
        Map<String, String> assignedTeam = incidentResponseService.assignResponseTeam(
            event.getIncidentId(),
            incident.getThreatLevel(),
            incident.getIncidentCategory()
        );
        
        incident.setAssignedTeam(assignedTeam);
        
        // Create incident timeline
        Map<String, LocalDateTime> incidentTimeline = incidentResponseService.createIncidentTimeline(
            event.getDetectedAt(),
            incident.getContainmentExecutedAt(),
            incident.getEvidenceCollectedAt(),
            LocalDateTime.now()
        );
        
        incident.setIncidentTimeline(incidentTimeline);
        
        // Schedule periodic status updates
        List<LocalDateTime> statusUpdateSchedule = incidentResponseService.scheduleStatusUpdates(
            event.getIncidentId(),
            incident.getThreatLevel(),
            Duration.ofHours(4) // Update every 4 hours for high severity
        );
        
        incident.setStatusUpdateSchedule(statusUpdateSchedule);
        
        securityIncidentRepository.save(incident);
        
        log.error("Incident response playbook executed for incident {}: Playbook: {}, Team assigned: {}, Procedures: {}", 
            event.getIncidentId(), playbookId, assignedTeam.size(), executedProcedures.size());
    }
    
    private void assessDataBreachAndPrivacyImpact(SecurityIncident incident, SecurityIncidentDetectedEvent event) {
        // Assess personal data impact
        Map<String, Object> privacyImpactAssessment = complianceService.assessPrivacyImpact(
            event.getAffectedSystems(),
            incident.getExfiltratedDataTypes(),
            incident.getEstimatedAffectedUsers()
        );
        
        incident.setPrivacyImpactAssessment(privacyImpactAssessment);
        
        boolean personalDataInvolved = (Boolean) privacyImpactAssessment.get("personalDataInvolved");
        
        if (personalDataInvolved) {
            incident.addSecurityFlag("PERSONAL_DATA_INVOLVED");
            
            // Assess GDPR implications
            boolean gdprApplicable = complianceService.assessGDPRApplicability(
                incident.getEstimatedAffectedUsers(),
                privacyImpactAssessment
            );
            
            if (gdprApplicable) {
                incident.addSecurityFlag("GDPR_BREACH_NOTIFICATION_REQUIRED");
                incident.setGdprNotificationRequired(true);
                
                // Schedule GDPR notification deadline (72 hours)
                LocalDateTime gdprDeadline = LocalDateTime.now().plusHours(72);
                incident.setGdprNotificationDeadline(gdprDeadline);
            }
            
            // Assess CCPA implications
            boolean ccpaApplicable = complianceService.assessCCPAApplicability(
                incident.getEstimatedAffectedUsers(),
                privacyImpactAssessment
            );
            
            if (ccpaApplicable) {
                incident.addSecurityFlag("CCPA_BREACH_NOTIFICATION_REQUIRED");
                incident.setCcpaNotificationRequired(true);
            }
        }
        
        // Assess financial data impact
        Map<String, Object> financialImpactAssessment = complianceService.assessFinancialDataImpact(
            incident.getExfiltratedDataTypes(),
            event.getAffectedSystems(),
            incident.getEstimatedAffectedUsers()
        );
        
        incident.setFinancialImpactAssessment(financialImpactAssessment);
        
        boolean financialDataExposed = (Boolean) financialImpactAssessment.get("financialDataExposed");
        
        if (financialDataExposed) {
            incident.addSecurityFlag("FINANCIAL_DATA_EXPOSED");
            
            // Assess PCI DSS implications
            boolean pciDssImpact = complianceService.assessPCIDSSImpact(
                incident.getExfiltratedDataTypes(),
                event.getAffectedSystems()
            );
            
            if (pciDssImpact) {
                incident.addSecurityFlag("PCI_DSS_INCIDENT_REPORTING_REQUIRED");
                incident.setPciDssNotificationRequired(true);
            }
        }
        
        // Calculate breach notification requirements
        Map<String, Boolean> breachNotificationRequirements = complianceService.calculateBreachNotificationRequirements(
            incident.getThreatLevel(),
            personalDataInvolved,
            financialDataExposed,
            incident.getEstimatedAffectedUsers()
        );
        
        incident.setBreachNotificationRequirements(breachNotificationRequirements);
        
        // Schedule regulatory notifications
        if (incident.isRegulatoryNotificationRequired()) {
            complianceService.scheduleRegulatoryNotifications(
                event.getIncidentId(),
                breachNotificationRequirements,
                incident.getGdprNotificationDeadline()
            );
        }
        
        securityIncidentRepository.save(incident);
        
        log.error("Privacy impact assessment completed for incident {}: Personal data: {}, Financial data: {}, GDPR: {}, PCI DSS: {}", 
            event.getIncidentId(), personalDataInvolved, financialDataExposed, 
            incident.isGdprNotificationRequired(), incident.isPciDssNotificationRequired());
    }
    
    private void implementRecoveryAndRestoration(SecurityIncident incident, SecurityIncidentDetectedEvent event) {
        // Create recovery plan
        String recoveryPlanId = incidentResponseService.createRecoveryPlan(
            event.getAffectedSystems(),
            incident.getContainmentActions(),
            incident.getThreatLevel()
        );
        
        incident.setRecoveryPlanId(recoveryPlanId);
        
        // Execute system restoration procedures
        List<String> restorationResults = new ArrayList<>();
        
        for (String affectedSystem : event.getAffectedSystems()) {
            try {
                String restorationId = incidentResponseService.restoreSystemFromBackup(
                    affectedSystem,
                    event.getIncidentId(),
                    incident.getAttackTimeline()
                );
                
                restorationResults.add("SUCCESS:" + affectedSystem + ":" + restorationId);
                
            } catch (Exception e) {
                restorationResults.add("FAILED:" + affectedSystem + ":" + e.getMessage());
                log.error("Failed to restore system {} for incident {}: {}", 
                    affectedSystem, event.getIncidentId(), e.getMessage());
            }
        }
        
        incident.setRestorationResults(restorationResults);
        
        // Implement security improvements and patches
        List<String> securityImprovements = incidentResponseService.implementSecurityImprovements(
            incident.getExploitedVulnerabilities(),
            incident.getAttackVectors(),
            event.getAffectedSystems()
        );
        
        incident.setSecurityImprovements(securityImprovements);
        
        // Validate system integrity post-restoration
        Map<String, Boolean> integrityValidation = incidentResponseService.validateSystemIntegrity(
            event.getAffectedSystems(),
            incident.getIndicatorsOfCompromise()
        );
        
        incident.setIntegrityValidation(integrityValidation);
        
        boolean allSystemsClean = integrityValidation.values().stream().allMatch(Boolean::booleanValue);
        
        if (allSystemsClean) {
            incident.setResponsePhase(ResponsePhase.RECOVERY);
            incident.setSystemsRestored(true);
        } else {
            incident.addSecurityFlag("SYSTEM_INTEGRITY_COMPROMISED");
        }
        
        // Implement enhanced monitoring
        String enhancedMonitoringId = incidentResponseService.implementEnhancedMonitoring(
            event.getAffectedSystems(),
            incident.getIndicatorsOfCompromise(),
            Duration.ofDays(30) // 30 days enhanced monitoring
        );
        
        incident.setEnhancedMonitoringId(enhancedMonitoringId);
        
        securityIncidentRepository.save(incident);
        
        log.error("Recovery procedures completed for incident {}: Systems restored: {}, Improvements: {}, Enhanced monitoring: {}", 
            event.getIncidentId(), allSystemsClean, securityImprovements.size(), 
            enhancedMonitoringId != null);
    }
    
    private void generateRegulatoryNotificationsAndReports(SecurityIncident incident, SecurityIncidentDetectedEvent event) {
        List<String> regulatoryNotificationIds = new ArrayList<>();
        
        // Generate GDPR breach notification
        if (incident.isGdprNotificationRequired()) {
            String gdprNotificationId = complianceService.generateGDPRBreachNotification(
                event.getIncidentId(),
                incident.getPrivacyImpactAssessment(),
                incident.getEstimatedAffectedUsers(),
                incident.getGdprNotificationDeadline()
            );
            
            regulatoryNotificationIds.add(gdprNotificationId);
        }
        
        // Generate PCI DSS incident report
        if (incident.isPciDssNotificationRequired()) {
            String pciNotificationId = complianceService.generatePCIDSSIncidentReport(
                event.getIncidentId(),
                incident.getFinancialImpactAssessment(),
                incident.getExploitedVulnerabilities()
            );
            
            regulatoryNotificationIds.add(pciNotificationId);
        }
        
        // Generate law enforcement notification if required
        if (incident.getThreatLevel() == ThreatLevel.CRITICAL && 
            incident.getEstimatedFinancialImpact().compareTo(new BigDecimal("100000")) > 0) {
            
            String lawEnforcementId = complianceService.generateLawEnforcementNotification(
                event.getIncidentId(),
                incident.getThreatIntelReportId(),
                incident.getAttributionAnalysis()
            );
            
            regulatoryNotificationIds.add(lawEnforcementId);
        }
        
        // Generate cyber insurance notification
        String insuranceNotificationId = complianceService.generateCyberInsuranceNotification(
            event.getIncidentId(),
            incident.getEstimatedFinancialImpact(),
            incident.getPrivacyImpactAssessment()
        );
        
        regulatoryNotificationIds.add(insuranceNotificationId);
        
        // Generate internal security report
        String securityReportId = complianceService.generateInternalSecurityReport(
            event.getIncidentId(),
            incident.getThreatIntelReportId(),
            incident.getForensicsFindings(),
            incident.getSecurityImprovements()
        );
        
        incident.setSecurityReportId(securityReportId);
        
        incident.setRegulatoryNotificationIds(regulatoryNotificationIds);
        
        securityIncidentRepository.save(incident);
        
        log.error("Regulatory notifications generated for incident {}: Notifications: {}, GDPR: {}, PCI DSS: {}", 
            event.getIncidentId(), regulatoryNotificationIds.size(), 
            incident.isGdprNotificationRequired(), incident.isPciDssNotificationRequired());
    }
    
    private void conductPostIncidentAnalysis(SecurityIncident incident, SecurityIncidentDetectedEvent event) {
        // Generate lessons learned report
        String lessonsLearnedId = incidentResponseService.generateLessonsLearnedReport(
            event.getIncidentId(),
            incident.getAttackVectors(),
            incident.getContainmentActions(),
            incident.getSecurityImprovements()
        );
        
        incident.setLessonsLearnedReportId(lessonsLearnedId);
        
        // Conduct root cause analysis
        Map<String, Object> rootCauseAnalysis = incidentResponseService.conductRootCauseAnalysis(
            incident.getExploitedVulnerabilities(),
            incident.getAttackVectors(),
            incident.getContainmentEffectiveness()
        );
        
        incident.setRootCauseAnalysis(rootCauseAnalysis);
        
        // Calculate incident metrics
        Duration responseTime = Duration.between(event.getDetectedAt(), incident.getContainmentExecutedAt());
        Duration recoveryTime = Duration.between(incident.getContainmentExecutedAt(), LocalDateTime.now());
        
        Map<String, Object> incidentMetrics = Map.of(
            "responseTime", responseTime.toMinutes(),
            "recoveryTime", recoveryTime.toMinutes(),
            "containmentEffectiveness", incident.getContainmentEffectiveness(),
            "estimatedCost", incident.getEstimatedFinancialImpact(),
            "affectedUsers", incident.getEstimatedAffectedUsers()
        );
        
        incident.setIncidentMetrics(incidentMetrics);
        
        // Update threat intelligence database
        threatAnalysisService.updateThreatIntelligenceDatabase(
            incident.getIndicatorsOfCompromise(),
            incident.getSuspectedThreatActor(),
            incident.getAttackPatterns()
        );
        
        // Schedule follow-up security assessments
        String followUpAssessmentId = incidentResponseService.scheduleFollowUpSecurityAssessment(
            event.getAffectedSystems(),
            incident.getExploitedVulnerabilities(),
            LocalDateTime.now().plusWeeks(2)
        );
        
        incident.setFollowUpAssessmentId(followUpAssessmentId);
        
        // Mark incident as closed if recovery is complete
        if (incident.isSystemsRestored() && 
            incident.getRegulatoryNotificationIds() != null &&
            !incident.getRegulatoryNotificationIds().isEmpty()) {
            
            incident.setStatus(IncidentStatus.CLOSED);
            incident.setResponsePhase(ResponsePhase.POST_INCIDENT);
            incident.setClosedAt(LocalDateTime.now());
        }
        
        securityIncidentRepository.save(incident);
        
        log.error("Post-incident analysis completed for incident {}: Response time: {} minutes, Recovery time: {} minutes, Status: {}", 
            event.getIncidentId(), responseTime.toMinutes(), recoveryTime.toMinutes(), incident.getStatus());
    }
    
    private void sendSecurityAlertsAndNotifications(SecurityIncident incident, SecurityIncidentDetectedEvent event) {
        // Send executive security briefing
        notificationService.sendExecutiveSecurityBriefing(
            event.getIncidentId(),
            incident.getThreatLevel(),
            incident.getEstimatedFinancialImpact(),
            incident.getEstimatedAffectedUsers(),
            incident.getStatus()
        );
        
        // Send security team notifications
        notificationService.sendSecurityTeamAlert(
            event.getIncidentId(),
            incident.getThreatLevel(),
            incident.getSuspectedThreatActor(),
            incident.getContainmentActions(),
            incident.getWarRoomId()
        );
        
        // Send customer impact notifications
        if (incident.getEstimatedAffectedUsers() > 0) {
            notificationService.sendCustomerSecurityNotification(
                event.getIncidentId(),
                incident.getEstimatedAffectedUsers(),
                incident.isGdprNotificationRequired(),
                incident.getPrivacyImpactAssessment()
            );
        }
        
        // Send regulatory notifications
        if (incident.isRegulatoryNotificationRequired()) {
            notificationService.sendRegulatorySecurityNotification(
                event.getIncidentId(),
                incident.getBreachNotificationRequirements(),
                incident.getRegulatoryNotificationIds()
            );
        }
        
        // Send threat intelligence sharing notifications
        if (incident.getSuspectedThreatActor() != null) {
            notificationService.sendThreatIntelligenceSharing(
                event.getIncidentId(),
                incident.getThreatIntelReportId(),
                incident.getIndicatorsOfCompromise(),
                incident.getAttackPatterns()
            );
        }
        
        // Send incident status updates to stakeholders
        notificationService.sendIncidentStatusUpdate(
            event.getIncidentId(),
            incident.getStatus(),
            incident.getResponsePhase(),
            incident.getIncidentTimeline()
        );
        
        log.error("Security notifications sent for incident {}: Executive briefing, Team alerts, Customer impact: {}, Regulatory: {}", 
            event.getIncidentId(), incident.getEstimatedAffectedUsers() > 0, 
            incident.isRegulatoryNotificationRequired());
    }
    
    private IncidentType mapIncidentType(String incidentTypeStr) {
        try {
            return IncidentType.valueOf(incidentTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return IncidentType.OTHER;
        }
    }
    
    private void createEmergencyInterventionRecord(SecurityIncidentDetectedEvent event, Exception exception) {
        manualInterventionService.createTask(
            "SECURITY_INCIDENT_PROCESSING_FAILED",
            String.format(
                "EMERGENCY: Failed to process critical security incident. " +
                "Incident ID: %s, Type: %s, Severity: %s, Affected Systems: %d. " +
                "IMMEDIATE MANUAL SECURITY RESPONSE REQUIRED. " +
                "Containment may not be executed. Evidence may be lost. " +
                "Regulatory notifications may be missed. " +
                "Exception: %s. CRITICAL MANUAL INTERVENTION REQUIRED.",
                event.getIncidentId(),
                event.getIncidentType(),
                event.getSeverity(),
                event.getAffectedSystems().size(),
                exception.getMessage()
            ),
            "EMERGENCY",
            event,
            exception
        );
    }
}