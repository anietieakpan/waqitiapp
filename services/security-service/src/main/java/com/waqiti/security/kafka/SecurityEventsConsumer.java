package com.waqiti.security.kafka;

import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.security.model.*;
import com.waqiti.security.repository.SecurityEventRepository;
import com.waqiti.security.service.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Production-grade Kafka consumer for security events
 * Handles security incident detection, threat assessment, and automated response
 * 
 * Critical for: System security, threat detection, incident response
 * SLA: Must process security events within 10 seconds for rapid threat response
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SecurityEventsConsumer {

    private final SecurityEventRepository eventRepository;
    private final ThreatDetectionService threatDetectionService;
    private final IncidentResponseService incidentResponseService;
    private final SecurityAnalysisService analysisService;
    private final ThreatIntelligenceService threatIntelService;
    private final SecurityControlService controlService;
    private final ForensicsService forensicsService;
    private final NotificationService notificationService;
    private final WorkflowService workflowService;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final ScheduledExecutorService scheduledExecutor;

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long SLA_THRESHOLD_MS = 10000; // 10 seconds
    private static final Set<String> CRITICAL_EVENT_TYPES = Set.of(
        "SECURITY_BREACH", "DATA_EXFILTRATION", "SYSTEM_COMPROMISE", "MALWARE_DETECTION",
        "UNAUTHORIZED_ACCESS", "PRIVILEGE_ESCALATION", "INSIDER_THREAT", "DDoS_ATTACK"
    );
    
    @KafkaListener(
        topics = {"security-events"},
        groupId = "security-events-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    @CircuitBreaker(name = "security-events-processor", fallbackMethod = "handleSecurityEventFailure")
    @Retry(name = "security-events-processor")
    public void processSecurityEvent(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String eventId = event.getEventId();
        log.info("Processing security event: {} from topic: {} partition: {} offset: {}", 
                eventId, topic, partition, offset);

        long startTime = System.currentTimeMillis();
        
        try {
            Map<String, Object> payload = event.getPayload();
            SecurityEvent securityEvent = extractSecurityEvent(payload);
            
            // Validate security event
            validateSecurityEvent(securityEvent);
            
            // Check for duplicate event
            if (isDuplicateEvent(securityEvent)) {
                log.warn("Duplicate security event detected: {}, skipping", securityEvent.getEventId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Enrich event with additional context
            SecurityEvent enrichedEvent = enrichSecurityEvent(securityEvent);
            
            // Perform threat assessment
            ThreatAssessment threatAssessment = performThreatAssessment(enrichedEvent);
            
            // Process event based on threat level
            SecurityProcessingResult result = processSecurityEvent(enrichedEvent, threatAssessment);
            
            // Execute immediate security controls if required
            if (threatAssessment.requiresImmediateAction()) {
                executeImmediateSecurityControls(enrichedEvent, threatAssessment, result);
            }
            
            // Perform threat correlation
            if (threatAssessment.enablesCorrelation()) {
                performThreatCorrelation(enrichedEvent, threatAssessment, result);
            }
            
            // Collect forensic evidence
            if (threatAssessment.requiresForensics()) {
                collectForensicEvidence(enrichedEvent, result);
            }
            
            // Update threat intelligence
            updateThreatIntelligence(enrichedEvent, threatAssessment);
            
            // Trigger automated workflows
            if (threatAssessment.hasAutomatedWorkflows()) {
                triggerSecurityWorkflows(enrichedEvent, threatAssessment);
            }
            
            // Send security notifications
            sendSecurityNotifications(enrichedEvent, threatAssessment, result);
            
            // Update security monitoring systems
            updateSecurityMonitoring(enrichedEvent, result);
            
            // Audit security event processing
            auditSecurityEventProcessing(enrichedEvent, result, event);
            
            // Record security metrics
            recordSecurityMetrics(enrichedEvent, result, startTime);
            
            // Acknowledge message
            acknowledgment.acknowledge();
            
            log.info("Successfully processed security event: {} type: {} threat: {} in {}ms", 
                    enrichedEvent.getEventId(), enrichedEvent.getEventType(), 
                    threatAssessment.getThreatLevel(), System.currentTimeMillis() - startTime);
            
        } catch (ValidationException e) {
            log.error("Validation failed for security event: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();
            
        } catch (CriticalSecurityException e) {
            log.error("Critical security processing failed: {}", eventId, e);
            handleCriticalSecurityError(event, e);
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process security event: {}", eventId, e);
            handleProcessingError(event, e, acknowledgment);
        }
    }

    private SecurityEvent extractSecurityEvent(Map<String, Object> payload) {
        return SecurityEvent.builder()
            .eventId(extractString(payload, "eventId", UUID.randomUUID().toString()))
            .eventType(SecurityEventType.fromString(extractString(payload, "eventType", null)))
            .severity(SecuritySeverity.fromString(extractString(payload, "severity", "MEDIUM")))
            .status(SecurityStatus.OPEN)
            .sourceIp(extractString(payload, "sourceIp", null))
            .targetIp(extractString(payload, "targetIp", null))
            .sourcePort(extractInteger(payload, "sourcePort", null))
            .targetPort(extractInteger(payload, "targetPort", null))
            .protocol(extractString(payload, "protocol", null))
            .userId(extractString(payload, "userId", null))
            .sessionId(extractString(payload, "sessionId", null))
            .userAgent(extractString(payload, "userAgent", null))
            .deviceId(extractString(payload, "deviceId", null))
            .accountId(extractString(payload, "accountId", null))
            .applicationId(extractString(payload, "applicationId", null))
            .resourceId(extractString(payload, "resourceId", null))
            .action(extractString(payload, "action", null))
            .outcome(extractString(payload, "outcome", null))
            .description(extractString(payload, "description", null))
            .riskScore(extractInteger(payload, "riskScore", 0))
            .country(extractString(payload, "country", null))
            .region(extractString(payload, "region", null))
            .city(extractString(payload, "city", null))
            .sourceSystem(extractString(payload, "sourceSystem", "UNKNOWN"))
            .detectionMethod(extractString(payload, "detectionMethod", null))
            .alertRuleId(extractString(payload, "alertRuleId", null))
            .eventData(extractMap(payload, "eventData"))
            .indicators(extractStringList(payload, "indicators"))
            .mitreTechniques(extractStringList(payload, "mitreTechniques"))
            .metadata(extractMap(payload, "metadata"))
            .detectedAt(extractInstant(payload, "detectedAt"))
            .createdAt(Instant.now())
            .build();
    }

    private void validateSecurityEvent(SecurityEvent event) {
        if (event.getEventType() == null) {
            throw new ValidationException("Event type is required");
        }
        
        if (event.getSeverity() == null) {
            throw new ValidationException("Severity is required");
        }
        
        if (event.getSourceIp() == null || event.getSourceIp().isEmpty()) {
            throw new ValidationException("Source IP is required");
        }
        
        // Validate critical event requirements
        if (CRITICAL_EVENT_TYPES.contains(event.getEventType().toString())) {
            if (event.getDescription() == null || event.getDescription().trim().isEmpty()) {
                throw new ValidationException("Description required for critical security event");
            }
            
            if (event.getSeverity().ordinal() < SecuritySeverity.HIGH.ordinal()) {
                log.warn("Critical event type {} should have HIGH or CRITICAL severity", 
                        event.getEventType());
            }
        }
        
        // Validate IP addresses
        if (!isValidIpAddress(event.getSourceIp())) {
            throw new ValidationException("Invalid source IP address: " + event.getSourceIp());
        }
        
        if (event.getTargetIp() != null && !isValidIpAddress(event.getTargetIp())) {
            throw new ValidationException("Invalid target IP address: " + event.getTargetIp());
        }
        
        // Validate ports
        if (event.getSourcePort() != null && 
            (event.getSourcePort() < 1 || event.getSourcePort() > 65535)) {
            throw new ValidationException("Invalid source port: " + event.getSourcePort());
        }
        
        if (event.getTargetPort() != null && 
            (event.getTargetPort() < 1 || event.getTargetPort() > 65535)) {
            throw new ValidationException("Invalid target port: " + event.getTargetPort());
        }
    }

    private boolean isValidIpAddress(String ip) {
        if (ip == null) return false;
        
        // Simple IPv4 validation
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;
        
        try {
            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) return false;
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isDuplicateEvent(SecurityEvent event) {
        // Check for exact duplicate by event ID
        if (eventRepository.existsByEventIdAndCreatedAtAfter(
                event.getEventId(), 
                Instant.now().minus(10, ChronoUnit.MINUTES))) {
            return true;
        }
        
        // Check for similar event (same source IP, type, within time window)
        return eventRepository.existsSimilarEvent(
            event.getSourceIp(),
            event.getEventType(),
            event.getTargetIp(),
            Instant.now().minus(5, ChronoUnit.MINUTES)
        );
    }

    private SecurityEvent enrichSecurityEvent(SecurityEvent event) {
        // Enrich with IP geolocation data
        if (event.getSourceIp() != null) {
            GeoLocationData geoData = geoLocationService.getLocationData(event.getSourceIp());
            if (geoData != null) {
                event.setCountry(geoData.getCountry());
                event.setRegion(geoData.getRegion());
                event.setCity(geoData.getCity());
                event.setLatitude(geoData.getLatitude());
                event.setLongitude(geoData.getLongitude());
                event.setIsp(geoData.getIsp());
                event.setOrganization(geoData.getOrganization());
            }
        }
        
        // Enrich with threat intelligence data
        ThreatIntelligenceData threatIntel = threatIntelService.getThreatIntelligence(event.getSourceIp());
        if (threatIntel != null) {
            event.setKnownThreatActor(threatIntel.isKnownThreatActor());
            event.setMaliciousIp(threatIntel.isMaliciousIp());
            event.setThreatTags(threatIntel.getThreatTags());
            event.setReputationScore(threatIntel.getReputationScore());
        }
        
        // Enrich with user context if available
        if (event.getUserId() != null) {
            UserSecurityProfile userProfile = userSecurityService.getUserSecurityProfile(event.getUserId());
            if (userProfile != null) {
                event.setUserRiskLevel(userProfile.getRiskLevel());
                event.setUserPrivilegeLevel(userProfile.getPrivilegeLevel());
                event.setLastLoginLocation(userProfile.getLastLoginLocation());
                event.setTypicalLoginTimes(userProfile.getTypicalLoginTimes());
            }
        }
        
        // Enrich with device context
        if (event.getDeviceId() != null) {
            DeviceProfile deviceProfile = deviceService.getDeviceProfile(event.getDeviceId());
            if (deviceProfile != null) {
                event.setDeviceType(deviceProfile.getDeviceType());
                event.setOperatingSystem(deviceProfile.getOperatingSystem());
                event.setDeviceRiskScore(deviceProfile.getRiskScore());
                event.setKnownDevice(deviceProfile.isKnownDevice());
            }
        }
        
        // Enrich with historical event context
        SecurityEventHistory history = eventRepository.getEventHistory(
            event.getSourceIp(),
            event.getEventType(),
            Instant.now().minus(24, ChronoUnit.HOURS)
        );
        
        event.setHistoricalEventCount(history.getTotalEvents());
        event.setRecentEventCount(history.getRecentEvents(1)); // Last hour
        event.setPreviousEventTime(history.getLastEventTime());
        
        return event;
    }

    private ThreatAssessment performThreatAssessment(SecurityEvent event) {
        ThreatAssessment assessment = new ThreatAssessment();
        assessment.setEventId(event.getEventId());
        assessment.setAssessmentTime(Instant.now());
        
        // Calculate base threat score
        int threatScore = calculateThreatScore(event);
        assessment.setThreatScore(threatScore);
        
        // Determine threat level
        String threatLevel = determineThreatLevel(threatScore, event);
        assessment.setThreatLevel(threatLevel);
        
        // Assess threat characteristics
        assessment.setRequiresImmediateAction(requiresImmediateAction(event, threatLevel));
        assessment.setEnablesCorrelation(enablesCorrelation(event));
        assessment.setRequiresForensics(requiresForensics(event, threatLevel));
        assessment.setHasAutomatedWorkflows(hasAutomatedWorkflows(event.getEventType()));
        
        // Threat classification
        ThreatClassification classification = classifyThreat(event);
        assessment.setThreatClassification(classification);
        
        // Confidence assessment
        double confidence = calculateConfidence(event, threatScore);
        assessment.setConfidence(confidence);
        
        // Attack phase identification
        AttackPhase attackPhase = identifyAttackPhase(event);
        assessment.setAttackPhase(attackPhase);
        
        // Generate threat indicators
        List<ThreatIndicator> indicators = generateThreatIndicators(event);
        assessment.setThreatIndicators(indicators);
        
        return assessment;
    }

    private int calculateThreatScore(SecurityEvent event) {
        int baseScore = 30; // Base threat score
        
        // Event type factor
        switch (event.getEventType()) {
            case SECURITY_BREACH:
            case DATA_EXFILTRATION:
            case SYSTEM_COMPROMISE:
                baseScore += 40;
                break;
            case MALWARE_DETECTION:
            case UNAUTHORIZED_ACCESS:
                baseScore += 35;
                break;
            case PRIVILEGE_ESCALATION:
            case INSIDER_THREAT:
                baseScore += 30;
                break;
            case FAILED_LOGIN:
            case SUSPICIOUS_ACTIVITY:
                baseScore += 20;
                break;
            case BRUTE_FORCE_ATTACK:
            case DDoS_ATTACK:
                baseScore += 25;
                break;
        }
        
        // Severity factor
        switch (event.getSeverity()) {
            case CRITICAL:
                baseScore += 25;
                break;
            case HIGH:
                baseScore += 20;
                break;
            case MEDIUM:
                baseScore += 10;
                break;
            case LOW:
                baseScore += 5;
                break;
        }
        
        // Threat intelligence factor
        if (event.isMaliciousIp()) {
            baseScore += 20;
        }
        if (event.isKnownThreatActor()) {
            baseScore += 25;
        }
        if (event.getReputationScore() != null && event.getReputationScore() < 30) {
            baseScore += 15;
        }
        
        // Geographic risk factor
        if (isHighRiskCountry(event.getCountry())) {
            baseScore += 15;
        }
        
        // User context factor
        if ("HIGH".equals(event.getUserRiskLevel())) {
            baseScore += 15;
        }
        if ("ADMIN".equals(event.getUserPrivilegeLevel())) {
            baseScore += 10;
        }
        
        // Historical events factor
        if (event.getHistoricalEventCount() > 10) {
            baseScore += 15;
        } else if (event.getHistoricalEventCount() > 5) {
            baseScore += 10;
        }
        
        // Recent events burst factor
        if (event.getRecentEventCount() > 10) {
            baseScore += 20;
        } else if (event.getRecentEventCount() > 5) {
            baseScore += 10;
        }
        
        // Time-based factor (off-hours activity)
        if (isOffHoursActivity(event.getDetectedAt(), event.getCountry())) {
            baseScore += 10;
        }
        
        // Device context factor
        if (event.getDeviceRiskScore() != null && event.getDeviceRiskScore() > 70) {
            baseScore += 15;
        }
        if (!event.isKnownDevice()) {
            baseScore += 10;
        }
        
        return Math.min(baseScore, 100);
    }

    private String determineThreatLevel(int threatScore, SecurityEvent event) {
        // Critical threat conditions
        if (CRITICAL_EVENT_TYPES.contains(event.getEventType().toString()) ||
            threatScore >= 85 ||
            event.getSeverity() == SecuritySeverity.CRITICAL ||
            event.isKnownThreatActor()) {
            return "CRITICAL";
        }
        
        // High threat conditions
        if (threatScore >= 70 ||
            event.getSeverity() == SecuritySeverity.HIGH ||
            event.isMaliciousIp() ||
            event.getRecentEventCount() > 10) {
            return "HIGH";
        }
        
        // Medium threat conditions
        if (threatScore >= 50 ||
            event.getSeverity() == SecuritySeverity.MEDIUM ||
            event.getHistoricalEventCount() > 5) {
            return "MEDIUM";
        }
        
        return "LOW";
    }

    private boolean requiresImmediateAction(SecurityEvent event, String threatLevel) {
        return "CRITICAL".equals(threatLevel) ||
               CRITICAL_EVENT_TYPES.contains(event.getEventType().toString()) ||
               event.isKnownThreatActor() ||
               event.getRecentEventCount() > 20;
    }

    private boolean enablesCorrelation(SecurityEvent event) {
        return event.getHistoricalEventCount() > 0 ||
               event.isMaliciousIp() ||
               event.isKnownThreatActor() ||
               Arrays.asList(SecurityEventType.BRUTE_FORCE_ATTACK, SecurityEventType.DDoS_ATTACK,
                           SecurityEventType.UNAUTHORIZED_ACCESS).contains(event.getEventType());
    }

    private boolean requiresForensics(SecurityEvent event, String threatLevel) {
        return "CRITICAL".equals(threatLevel) ||
               Arrays.asList(SecurityEventType.SECURITY_BREACH, SecurityEventType.DATA_EXFILTRATION,
                           SecurityEventType.SYSTEM_COMPROMISE, SecurityEventType.MALWARE_DETECTION)
               .contains(event.getEventType());
    }

    private boolean hasAutomatedWorkflows(SecurityEventType eventType) {
        return Arrays.asList(SecurityEventType.MALWARE_DETECTION, SecurityEventType.BRUTE_FORCE_ATTACK,
                           SecurityEventType.DDoS_ATTACK, SecurityEventType.UNAUTHORIZED_ACCESS,
                           SecurityEventType.SUSPICIOUS_ACTIVITY).contains(eventType);
    }

    private ThreatClassification classifyThreat(SecurityEvent event) {
        ThreatClassification classification = new ThreatClassification();
        
        // Primary classification
        if (event.getEventType() == SecurityEventType.MALWARE_DETECTION) {
            classification.setPrimaryCategory("MALWARE");
            classification.setSubCategory("DETECTION");
        } else if (event.getEventType() == SecurityEventType.BRUTE_FORCE_ATTACK) {
            classification.setPrimaryCategory("INTRUSION");
            classification.setSubCategory("BRUTE_FORCE");
        } else if (event.getEventType() == SecurityEventType.DDoS_ATTACK) {
            classification.setPrimaryCategory("AVAILABILITY");
            classification.setSubCategory("DDoS");
        } else if (event.getEventType() == SecurityEventType.DATA_EXFILTRATION) {
            classification.setPrimaryCategory("DATA_BREACH");
            classification.setSubCategory("EXFILTRATION");
        } else {
            classification.setPrimaryCategory("GENERAL");
            classification.setSubCategory("SECURITY_INCIDENT");
        }
        
        // Threat actor classification
        if (event.isKnownThreatActor()) {
            classification.setThreatActorType("KNOWN_APT");
        } else if (event.isMaliciousIp()) {
            classification.setThreatActorType("KNOWN_MALICIOUS");
        } else if (isHighRiskCountry(event.getCountry())) {
            classification.setThreatActorType("HIGH_RISK_GEOGRAPHIC");
        } else {
            classification.setThreatActorType("UNKNOWN");
        }
        
        return classification;
    }

    private double calculateConfidence(SecurityEvent event, int threatScore) {
        double confidence = 0.5; // Base confidence
        
        // High confidence indicators
        if (event.isKnownThreatActor()) confidence += 0.3;
        if (event.isMaliciousIp()) confidence += 0.2;
        if (event.getReputationScore() != null && event.getReputationScore() < 20) confidence += 0.2;
        if (CRITICAL_EVENT_TYPES.contains(event.getEventType().toString())) confidence += 0.1;
        
        // Medium confidence indicators
        if (event.getHistoricalEventCount() > 10) confidence += 0.1;
        if (event.getRecentEventCount() > 5) confidence += 0.1;
        if (isHighRiskCountry(event.getCountry())) confidence += 0.05;
        
        return Math.min(confidence, 1.0);
    }

    private AttackPhase identifyAttackPhase(SecurityEvent event) {
        // Map events to MITRE ATT&CK framework phases
        switch (event.getEventType()) {
            case RECONNAISSANCE:
                return AttackPhase.RECONNAISSANCE;
            case INITIAL_ACCESS:
            case BRUTE_FORCE_ATTACK:
                return AttackPhase.INITIAL_ACCESS;
            case PRIVILEGE_ESCALATION:
                return AttackPhase.PRIVILEGE_ESCALATION;
            case PERSISTENCE:
                return AttackPhase.PERSISTENCE;
            case DEFENSE_EVASION:
                return AttackPhase.DEFENSE_EVASION;
            case CREDENTIAL_ACCESS:
                return AttackPhase.CREDENTIAL_ACCESS;
            case DISCOVERY:
                return AttackPhase.DISCOVERY;
            case LATERAL_MOVEMENT:
                return AttackPhase.LATERAL_MOVEMENT;
            case COLLECTION:
                return AttackPhase.COLLECTION;
            case DATA_EXFILTRATION:
                return AttackPhase.EXFILTRATION;
            case SYSTEM_COMPROMISE:
            case MALWARE_DETECTION:
                return AttackPhase.IMPACT;
            default:
                return AttackPhase.UNKNOWN;
        }
    }

    private List<ThreatIndicator> generateThreatIndicators(SecurityEvent event) {
        List<ThreatIndicator> indicators = new ArrayList<>();
        
        // IP-based indicators
        if (event.getSourceIp() != null) {
            indicators.add(ThreatIndicator.builder()
                .type("IP")
                .value(event.getSourceIp())
                .confidence(event.isMaliciousIp() ? 0.9 : 0.6)
                .description("Source IP address")
                .build());
        }
        
        // User-based indicators
        if (event.getUserId() != null && "HIGH".equals(event.getUserRiskLevel())) {
            indicators.add(ThreatIndicator.builder()
                .type("USER")
                .value(event.getUserId())
                .confidence(0.7)
                .description("High-risk user account")
                .build());
        }
        
        // Device-based indicators
        if (event.getDeviceId() != null && !event.isKnownDevice()) {
            indicators.add(ThreatIndicator.builder()
                .type("DEVICE")
                .value(event.getDeviceId())
                .confidence(0.6)
                .description("Unknown device")
                .build());
        }
        
        // Behavioral indicators
        if (event.getRecentEventCount() > 10) {
            indicators.add(ThreatIndicator.builder()
                .type("BEHAVIOR")
                .value("HIGH_FREQUENCY_EVENTS")
                .confidence(0.8)
                .description("Unusual event frequency")
                .build());
        }
        
        return indicators;
    }

    private boolean isHighRiskCountry(String country) {
        Set<String> highRiskCountries = Set.of(
            "CN", "RU", "IR", "KP", "SY", "IQ", "AF", "LY", "SO", "SD"
        );
        return highRiskCountries.contains(country);
    }

    private boolean isOffHoursActivity(Instant detectedAt, String country) {
        if (detectedAt == null) return false;
        
        // Simple off-hours check (outside 8 AM - 6 PM local time)
        // This is a simplified implementation
        int hour = detectedAt.atZone(java.time.ZoneId.of("UTC")).getHour();
        return hour < 8 || hour > 18;
    }

    private SecurityProcessingResult processSecurityEvent(SecurityEvent event, ThreatAssessment assessment) {
        SecurityProcessingResult result = new SecurityProcessingResult();
        result.setEventId(event.getEventId());
        result.setThreatAssessment(assessment);
        result.setProcessingStartTime(Instant.now());
        
        try {
            // Save security event
            SecurityEvent savedEvent = eventRepository.save(event);
            result.setSavedEvent(savedEvent);
            
            // Process based on threat level
            switch (assessment.getThreatLevel()) {
                case "CRITICAL":
                    result = processCriticalThreat(event, assessment);
                    break;
                    
                case "HIGH":
                    result = processHighThreat(event, assessment);
                    break;
                    
                case "MEDIUM":
                    result = processMediumThreat(event, assessment);
                    break;
                    
                case "LOW":
                    result = processLowThreat(event, assessment);
                    break;
            }
            
            result.setStatus(ProcessingStatus.COMPLETED);
            
        } catch (Exception e) {
            log.error("Failed to process security event: {}", event.getEventId(), e);
            result.setStatus(ProcessingStatus.FAILED);
            result.setErrorMessage(e.getMessage());
            throw new SecurityProcessingException("Security processing failed", e);
        }
        
        result.setProcessingEndTime(Instant.now());
        result.setProcessingTimeMs(
            ChronoUnit.MILLIS.between(result.getProcessingStartTime(), result.getProcessingEndTime())
        );
        
        return result;
    }

    private SecurityProcessingResult processCriticalThreat(SecurityEvent event, ThreatAssessment assessment) {
        SecurityProcessingResult result = new SecurityProcessingResult();
        
        // Create critical security incident
        SecurityIncident incident = incidentResponseService.createCriticalIncident(
            event,
            assessment,
            "CRITICAL_THREAT_DETECTED"
        );
        result.setSecurityIncident(incident);
        
        // Immediate containment actions
        List<String> containmentActions = new ArrayList<>();
        
        // Block source IP immediately
        if (event.getSourceIp() != null) {
            controlService.blockIpAddress(event.getSourceIp(), "CRITICAL_THREAT", event.getEventId());
            containmentActions.add("SOURCE_IP_BLOCKED");
        }
        
        // Disable user account if involved
        if (event.getUserId() != null) {
            controlService.disableUserAccount(event.getUserId(), "SECURITY_INCIDENT", event.getEventId());
            containmentActions.add("USER_ACCOUNT_DISABLED");
        }
        
        // Isolate affected systems
        if (event.getTargetIp() != null) {
            controlService.isolateSystem(event.getTargetIp(), "CRITICAL_THREAT", event.getEventId());
            containmentActions.add("SYSTEM_ISOLATED");
        }
        
        result.setContainmentActions(containmentActions);
        
        // Trigger emergency response team
        incidentResponseService.activateEmergencyResponse(incident);
        
        // Create executive alert
        alertingService.createExecutiveAlert(
            "CRITICAL_SECURITY_THREAT",
            event,
            assessment
        );
        
        return result;
    }

    private SecurityProcessingResult processHighThreat(SecurityEvent event, ThreatAssessment assessment) {
        SecurityProcessingResult result = new SecurityProcessingResult();
        
        // Create security incident
        SecurityIncident incident = incidentResponseService.createIncident(
            event,
            assessment,
            "HIGH_THREAT_DETECTED"
        );
        result.setSecurityIncident(incident);
        
        // Enhanced monitoring
        List<String> monitoringActions = new ArrayList<>();
        
        // Enhanced monitoring for source IP
        if (event.getSourceIp() != null) {
            monitoringService.enableEnhancedMonitoring(
                "IP", 
                event.getSourceIp(), 
                "HIGH_THREAT_MONITORING"
            );
            monitoringActions.add("ENHANCED_IP_MONITORING");
        }
        
        // User activity monitoring
        if (event.getUserId() != null) {
            monitoringService.enableUserActivityMonitoring(
                event.getUserId(), 
                "HIGH_THREAT_USER_MONITORING"
            );
            monitoringActions.add("USER_ACTIVITY_MONITORING");
        }
        
        result.setMonitoringActions(monitoringActions);
        
        // Assign to security analyst
        String analystId = assignmentService.getSeniorSecurityAnalyst();
        result.setAssignedAnalyst(analystId);
        
        return result;
    }

    private SecurityProcessingResult processMediumThreat(SecurityEvent event, ThreatAssessment assessment) {
        SecurityProcessingResult result = new SecurityProcessingResult();
        
        // Create standard security case
        SecurityCase securityCase = caseManagementService.createSecurityCase(
            event,
            assessment,
            "SECURITY_TEAM"
        );
        result.setSecurityCase(securityCase);
        
        // Standard monitoring
        if (event.getSourceIp() != null) {
            monitoringService.addToWatchlist(
                "IP",
                event.getSourceIp(),
                "MEDIUM_THREAT_WATCHLIST"
            );
        }
        
        // Assign to available analyst
        String analystId = assignmentService.getAvailableSecurityAnalyst();
        result.setAssignedAnalyst(analystId);
        
        return result;
    }

    private SecurityProcessingResult processLowThreat(SecurityEvent event, ThreatAssessment assessment) {
        SecurityProcessingResult result = new SecurityProcessingResult();
        
        // Log for future analysis
        analysisService.logForTrendAnalysis(event, assessment);
        
        // Update threat intelligence with low-confidence data
        threatIntelService.updateLowConfidenceIntelligence(event);
        
        return result;
    }

    private void executeImmediateSecurityControls(SecurityEvent event, ThreatAssessment assessment, 
                                                 SecurityProcessingResult result) {
        List<String> immediateControls = new ArrayList<>();
        
        // Rate limiting for brute force attacks
        if (event.getEventType() == SecurityEventType.BRUTE_FORCE_ATTACK) {
            controlService.applyRateLimiting(
                event.getSourceIp(),
                "BRUTE_FORCE_PROTECTION"
            );
            immediateControls.add("RATE_LIMITING_APPLIED");
        }
        
        // DDoS protection
        if (event.getEventType() == SecurityEventType.DDoS_ATTACK) {
            controlService.activateDDoSProtection(
                event.getTargetIp(),
                event.getSourceIp()
            );
            immediateControls.add("DDoS_PROTECTION_ACTIVATED");
        }
        
        // Malware containment
        if (event.getEventType() == SecurityEventType.MALWARE_DETECTION) {
            controlService.quarantineAffectedSystems(
                event.getTargetIp(),
                event.getEventId()
            );
            immediateControls.add("SYSTEMS_QUARANTINED");
        }
        
        result.setImmediateControls(immediateControls);
    }

    private void performThreatCorrelation(SecurityEvent event, ThreatAssessment assessment, 
                                        SecurityProcessingResult result) {
        // Correlate with recent events from same source
        List<SecurityEvent> correlatedEvents = threatDetectionService.correlateThreatEvents(
            event.getSourceIp(),
            event.getEventType(),
            Instant.now().minus(24, ChronoUnit.HOURS)
        );
        
        if (!correlatedEvents.isEmpty()) {
            ThreatCorrelationResult correlationResult = ThreatCorrelationResult.builder()
                .primaryEvent(event)
                .correlatedEvents(correlatedEvents)
                .correlationStrength(calculateCorrelationStrength(event, correlatedEvents))
                .correlationRules(getTriggeredCorrelationRules(event, correlatedEvents))
                .build();
            
            result.setThreatCorrelation(correlationResult);
        }
        
        // Update attack campaign tracking
        threatDetectionService.updateAttackCampaignTracking(event, correlatedEvents);
    }

    private double calculateCorrelationStrength(SecurityEvent event, List<SecurityEvent> correlatedEvents) {
        if (correlatedEvents.isEmpty()) return 0.0;
        
        double strength = 0.3; // Base correlation
        
        // Same event type correlation
        long sameTypeEvents = correlatedEvents.stream()
            .filter(e -> e.getEventType() == event.getEventType())
            .count();
        
        if (sameTypeEvents > 0) {
            strength += Math.min(0.4, sameTypeEvents * 0.1);
        }
        
        // Time-based correlation (events in quick succession)
        long recentEvents = correlatedEvents.stream()
            .filter(e -> ChronoUnit.HOURS.between(e.getDetectedAt(), event.getDetectedAt()) < 1)
            .count();
        
        if (recentEvents > 0) {
            strength += Math.min(0.3, recentEvents * 0.1);
        }
        
        return Math.min(strength, 1.0);
    }

    private List<String> getTriggeredCorrelationRules(SecurityEvent event, List<SecurityEvent> correlatedEvents) {
        List<String> rules = new ArrayList<>();
        
        if (correlatedEvents.size() > 5) {
            rules.add("HIGH_FREQUENCY_EVENTS_SAME_SOURCE");
        }
        
        if (correlatedEvents.stream().anyMatch(e -> e.getEventType() == SecurityEventType.BRUTE_FORCE_ATTACK)) {
            rules.add("BRUTE_FORCE_CAMPAIGN_DETECTED");
        }
        
        if (correlatedEvents.stream().map(SecurityEvent::getTargetIp).distinct().count() > 3) {
            rules.add("MULTI_TARGET_ATTACK_PATTERN");
        }
        
        return rules;
    }

    private void collectForensicEvidence(SecurityEvent event, SecurityProcessingResult result) {
        ForensicEvidenceCollection evidence = forensicsService.collectEvidence(
            event.getEventId(),
            event.getSourceIp(),
            event.getTargetIp(),
            event.getDetectedAt()
        );
        
        result.setForensicEvidence(evidence);
        
        // Preserve system state
        if (event.getTargetIp() != null) {
            forensicsService.preserveSystemState(
                event.getTargetIp(),
                event.getEventId()
            );
        }
        
        // Collect network traffic
        forensicsService.collectNetworkTraffic(
            event.getSourceIp(),
            event.getTargetIp(),
            event.getDetectedAt().minus(1, ChronoUnit.HOURS),
            event.getDetectedAt().plus(1, ChronoUnit.HOURS)
        );
    }

    private void updateThreatIntelligence(SecurityEvent event, ThreatAssessment assessment) {
        // Update IP reputation data
        if (event.getSourceIp() != null && assessment.getThreatScore() > 70) {
            threatIntelService.updateIpReputation(
                event.getSourceIp(),
                assessment.getThreatScore(),
                event.getEventType().toString()
            );
        }
        
        // Add to threat indicators
        for (ThreatIndicator indicator : assessment.getThreatIndicators()) {
            threatIntelService.addThreatIndicator(indicator);
        }
        
        // Update attack patterns
        threatIntelService.updateAttackPatterns(
            event.getEventType(),
            event.getMitreTechniques(),
            assessment.getAttackPhase()
        );
    }

    private void triggerSecurityWorkflows(SecurityEvent event, ThreatAssessment assessment) {
        List<String> workflows = getSecurityWorkflows(
            event.getEventType(), 
            assessment.getThreatLevel()
        );
        
        for (String workflowType : workflows) {
            CompletableFuture.runAsync(() -> {
                try {
                    workflowService.triggerWorkflow(workflowType, event, assessment);
                } catch (Exception e) {
                    log.error("Failed to trigger security workflow {} for event {}", 
                             workflowType, event.getEventId(), e);
                }
            });
        }
    }

    private List<String> getSecurityWorkflows(SecurityEventType eventType, String threatLevel) {
        Map<String, List<String>> workflowMapping = Map.of(
            "CRITICAL", Arrays.asList("CRITICAL_INCIDENT_RESPONSE", "EXECUTIVE_NOTIFICATION", "EMERGENCY_CONTAINMENT"),
            "HIGH", Arrays.asList("HIGH_THREAT_INVESTIGATION", "ENHANCED_MONITORING", "THREAT_HUNTING"),
            "MEDIUM", Arrays.asList("STANDARD_INVESTIGATION", "MONITORING_UPDATE"),
            "LOW", Arrays.asList("LOG_ANALYSIS", "TREND_TRACKING")
        );
        
        return workflowMapping.getOrDefault(threatLevel, Arrays.asList("STANDARD_INVESTIGATION"));
    }

    private void sendSecurityNotifications(SecurityEvent event, ThreatAssessment assessment, 
                                         SecurityProcessingResult result) {
        
        Map<String, Object> notificationData = Map.of(
            "eventId", event.getEventId(),
            "eventType", event.getEventType().toString(),
            "severity", event.getSeverity().toString(),
            "threatLevel", assessment.getThreatLevel(),
            "threatScore", assessment.getThreatScore(),
            "sourceIp", event.getSourceIp(),
            "targetIp", event.getTargetIp() != null ? event.getTargetIp() : "N/A",
            "confidence", assessment.getConfidence()
        );
        
        // Critical threat notifications
        if ("CRITICAL".equals(assessment.getThreatLevel())) {
            CompletableFuture.runAsync(() -> {
                notificationService.sendCriticalSecurityAlert(notificationData);
                notificationService.sendExecutiveAlert("CRITICAL_SECURITY_THREAT", notificationData);
                notificationService.sendSOCAlert("CRITICAL_THREAT", notificationData);
            });
        }
        
        // High threat notifications
        if ("HIGH".equals(assessment.getThreatLevel())) {
            CompletableFuture.runAsync(() -> {
                notificationService.sendHighThreatAlert(notificationData);
                notificationService.sendSOCAlert("HIGH_THREAT", notificationData);
            });
        }
        
        // Team notifications
        CompletableFuture.runAsync(() -> {
            notificationService.sendTeamNotification(
                "SECURITY_TEAM",
                "SECURITY_EVENT_DETECTED",
                notificationData
            );
        });
        
        // Slack alerts for immediate attention
        if (assessment.requiresImmediateAction()) {
            CompletableFuture.runAsync(() -> {
                notificationService.sendSlackAlert(
                    "security-alerts",
                    "IMMEDIATE_SECURITY_ACTION_REQUIRED",
                    notificationData
                );
            });
        }
    }

    private void updateSecurityMonitoring(SecurityEvent event, SecurityProcessingResult result) {
        // Update security dashboard
        dashboardService.updateSecurityDashboard(event, result);
        
        // Update SIEM systems
        siemService.updateSecurityEvent(event, result);
        
        // Update threat hunting platforms
        threatHuntingService.updateThreatData(event, result);
        
        // Update compliance monitoring
        complianceMonitoringService.updateSecurityCompliance(event, result);
    }

    private void auditSecurityEventProcessing(SecurityEvent event, SecurityProcessingResult result, 
                                            GenericKafkaEvent originalEvent) {
        auditService.auditSecurityEvent(
            event.getEventId(),
            event.getEventType().toString(),
            event.getSourceIp(),
            event.getTargetIp(),
            result.getThreatAssessment().getThreatLevel(),
            result.getStatus().toString(),
            originalEvent.getEventId()
        );
    }

    private void recordSecurityMetrics(SecurityEvent event, SecurityProcessingResult result, 
                                     long startTime) {
        long processingTime = System.currentTimeMillis() - startTime;
        
        metricsService.recordSecurityMetrics(
            event.getEventType().toString(),
            event.getSeverity().toString(),
            result.getThreatAssessment().getThreatLevel(),
            result.getThreatAssessment().getThreatScore(),
            processingTime,
            processingTime <= SLA_THRESHOLD_MS,
            result.getStatus().toString()
        );
        
        // Record threat intelligence metrics
        metricsService.recordThreatIntelligenceMetrics(
            event.isKnownThreatActor(),
            event.isMaliciousIp(),
            event.getReputationScore()
        );
        
        // Record response effectiveness
        if (result.getContainmentActions() != null) {
            metricsService.recordIncidentResponseMetrics(
                result.getContainmentActions().size(),
                result.getThreatAssessment().getThreatLevel()
            );
        }
    }

    // Error handling methods
    private void handleValidationError(GenericKafkaEvent event, ValidationException e) {
        auditService.logValidationError(event.getEventId(), e.getMessage());
        kafkaTemplate.send("security-events-validation-errors", event);
    }

    private void handleCriticalSecurityError(GenericKafkaEvent event, CriticalSecurityException e) {
        emergencyAlertService.createEmergencyAlert(
            "CRITICAL_SECURITY_PROCESSING_FAILED",
            event.getPayload(),
            e.getMessage()
        );
        
        kafkaTemplate.send("security-events-critical-failures", event);
    }

    private void handleProcessingError(GenericKafkaEvent event, Exception e, Acknowledgment acknowledgment) {
        String eventId = event.getEventId();
        Integer retryCount = event.getMetadataValue("retryCount", Integer.class, 0);
        
        if (retryCount < MAX_RETRY_ATTEMPTS) {
            long retryDelay = (long) Math.pow(2, retryCount) * 1000;
            
            log.warn("Retrying security event {} after {}ms (attempt {})", 
                    eventId, retryDelay, retryCount + 1);
            
            event.setMetadataValue("retryCount", retryCount + 1);
            event.setMetadataValue("lastError", e.getMessage());
            
            scheduledExecutor.schedule(() -> {
                kafkaTemplate.send("security-events-retry", event);
            }, retryDelay, TimeUnit.MILLISECONDS);
            
            acknowledgment.acknowledge();
        } else {
            log.error("Max retries exceeded for security event {}, sending to DLQ", eventId);
            sendToDLQ(event, e);
            acknowledgment.acknowledge();
        }
    }

    private void sendToDLQ(GenericKafkaEvent event, Exception e) {
        event.setMetadataValue("dlqReason", e.getMessage());
        event.setMetadataValue("dlqTimestamp", Instant.now());
        event.setMetadataValue("originalTopic", "security-events");
        
        kafkaTemplate.send("security-events.DLQ", event);
        
        alertingService.createDLQAlert(
            "security-events",
            event.getEventId(),
            e.getMessage()
        );
    }

    // Fallback method for circuit breaker
    public void handleSecurityEventFailure(GenericKafkaEvent event, String topic, int partition,
                                         long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("Circuit breaker activated for security event processing: {}", e.getMessage());
        
        failedEventRepository.save(
            FailedEvent.builder()
                .eventId(event.getEventId())
                .topic(topic)
                .payload(event)
                .errorMessage(e.getMessage())
                .createdAt(Instant.now())
                .build()
        );
        
        alertingService.sendCriticalAlert(
            "Security Events Circuit Breaker Open",
            "Security event processing is failing. Threat detection compromised."
        );
        
        acknowledgment.acknowledge();
    }

    // Helper extraction methods
    private String extractString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private Integer extractInteger(Map<String, Object> map, String key, Integer defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        return Integer.parseInt(value.toString());
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
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new HashMap<>();
    }

    // Custom exceptions
    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }

    public static class CriticalSecurityException extends RuntimeException {
        public CriticalSecurityException(String message) {
            super(message);
        }
    }

    public static class SecurityProcessingException extends RuntimeException {
        public SecurityProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}