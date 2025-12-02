package com.waqiti.security.events;

import com.waqiti.common.events.SecurityIncidentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Security Incident Event Producer
 * 
 * CRITICAL IMPLEMENTATION: Publishes security incident events
 * Connects to SecurityIncidentDetectedEventConsumer
 * 
 * This producer is essential for:
 * - Security breach detection and response
 * - Incident management and escalation
 * - Security team alerting
 * - Compliance and audit requirements
 * - Root cause analysis and forensics
 * 
 * Incident Types:
 * - DATA_BREACH: Unauthorized data access
 * - ACCOUNT_COMPROMISE: Account takeover detected
 * - UNAUTHORIZED_ACCESS: Illegal system access
 * - MALICIOUS_ACTIVITY: Detected attack attempts
 * - SECURITY_POLICY_VIOLATION: Policy breaches
 * - INSIDER_THREAT: Internal security threats
 * - RANSOMWARE: Ransomware detection
 * - DDoS_ATTACK: Distributed denial of service
 * 
 * @author Waqiti Security Team
 * @version 2.0 - Production Implementation
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SecurityIncidentEventProducer {

    private final KafkaTemplate<String, SecurityIncidentEvent> kafkaTemplate;
    
    private static final String TOPIC = "security-incidents";

    /**
     * Publish data breach incident
     */
    public CompletableFuture<SendResult<String, SecurityIncidentEvent>> publishDataBreach(
            String incidentId,
            String affectedUserId,
            String breachType,
            List<String> affectedData,
            String detectionSource,
            Map<String, Object> breachDetails,
            String correlationId) {
        
        log.error("Publishing data breach incident: incidentId={}, user={}, type={}",
            incidentId, affectedUserId, breachType);
        
        SecurityIncidentEvent event = SecurityIncidentEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .incidentId(incidentId)
            .incidentType("DATA_BREACH")
            .severity("CRITICAL")
            .affectedUserId(affectedUserId)
            .affectedEntities(affectedData)
            .detectionSource(detectionSource)
            .description(String.format("Data breach detected: %s - Affected data: %s", 
                breachType, String.join(", ", affectedData)))
            .requiresImmediateAction(true)
            .requiresNotification(true)
            .metadata(breachDetails)
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        return sendEvent(event);
    }

    /**
     * Publish account compromise incident
     */
    public CompletableFuture<SendResult<String, SecurityIncidentEvent>> publishAccountCompromise(
            String userId,
            String compromiseIndicators,
            Double confidence,
            Map<String, Object> evidenceDetails,
            String correlationId) {
        
        log.error("Publishing account compromise: userId={}, indicators={}, confidence={}",
            userId, compromiseIndicators, confidence);
        
        String incidentId = generateIncidentId("ACC_COMP");
        
        SecurityIncidentEvent event = SecurityIncidentEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .incidentId(incidentId)
            .incidentType("ACCOUNT_COMPROMISE")
            .severity(confidence >= 0.9 ? "CRITICAL" : "HIGH")
            .affectedUserId(userId)
            .detectionSource("FRAUD_DETECTION_ML")
            .description(String.format("Account compromise detected - Indicators: %s (Confidence: %.2f)", 
                compromiseIndicators, confidence))
            .requiresImmediateAction(confidence >= 0.85)
            .requiresNotification(true)
            .metadata(evidenceDetails)
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        return sendEvent(event);
    }

    /**
     * Publish unauthorized access incident
     */
    public CompletableFuture<SendResult<String, SecurityIncidentEvent>> publishUnauthorizedAccess(
            String userId,
            String resourceAccessed,
            String ipAddress,
            String userAgent,
            Map<String, Object> accessDetails,
            String correlationId) {
        
        log.warn("Publishing unauthorized access: userId={}, resource={}, ip={}",
            userId, resourceAccessed, ipAddress);
        
        String incidentId = generateIncidentId("UNAUTH_ACCESS");
        
        SecurityIncidentEvent event = SecurityIncidentEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .incidentId(incidentId)
            .incidentType("UNAUTHORIZED_ACCESS")
            .severity("HIGH")
            .affectedUserId(userId)
            .affectedEntities(List.of(resourceAccessed))
            .detectionSource("ACCESS_CONTROL_SYSTEM")
            .description(String.format("Unauthorized access attempt to %s from IP %s", 
                resourceAccessed, ipAddress))
            .requiresImmediateAction(true)
            .requiresNotification(true)
            .metadata(Map.of(
                "ipAddress", ipAddress,
                "userAgent", userAgent,
                "resource", resourceAccessed,
                "accessDetails", accessDetails
            ))
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        return sendEvent(event);
    }

    /**
     * Publish malicious activity incident
     */
    public CompletableFuture<SendResult<String, SecurityIncidentEvent>> publishMaliciousActivity(
            String activityType,
            String sourceIp,
            String attackVector,
            List<String> targetedSystems,
            Map<String, Object> attackDetails,
            String correlationId) {
        
        log.error("Publishing malicious activity: type={}, sourceIp={}, vector={}",
            activityType, sourceIp, attackVector);
        
        String incidentId = generateIncidentId("MALICIOUS");
        
        SecurityIncidentEvent event = SecurityIncidentEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .incidentId(incidentId)
            .incidentType("MALICIOUS_ACTIVITY")
            .severity("CRITICAL")
            .affectedEntities(targetedSystems)
            .detectionSource("IDS_IPS_SYSTEM")
            .description(String.format("Malicious activity detected: %s via %s from %s", 
                activityType, attackVector, sourceIp))
            .requiresImmediateAction(true)
            .requiresNotification(true)
            .metadata(Map.of(
                "activityType", activityType,
                "sourceIp", sourceIp,
                "attackVector", attackVector,
                "attackDetails", attackDetails
            ))
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        return sendEvent(event);
    }

    /**
     * Publish security policy violation
     */
    public CompletableFuture<SendResult<String, SecurityIncidentEvent>> publishPolicyViolation(
            String userId,
            String violationType,
            String policyViolated,
            String violationDetails,
            String correlationId) {
        
        log.warn("Publishing policy violation: userId={}, type={}, policy={}",
            userId, violationType, policyViolated);
        
        String incidentId = generateIncidentId("POLICY_VIOL");
        
        SecurityIncidentEvent event = SecurityIncidentEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .incidentId(incidentId)
            .incidentType("SECURITY_POLICY_VIOLATION")
            .severity("MEDIUM")
            .affectedUserId(userId)
            .detectionSource("POLICY_ENFORCEMENT_ENGINE")
            .description(String.format("Security policy violation: %s violated %s - %s", 
                userId, policyViolated, violationDetails))
            .requiresImmediateAction(false)
            .requiresNotification(true)
            .metadata(Map.of(
                "violationType", violationType,
                "policyViolated", policyViolated,
                "violationDetails", violationDetails
            ))
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        return sendEvent(event);
    }

    /**
     * Publish insider threat incident
     */
    public CompletableFuture<SendResult<String, SecurityIncidentEvent>> publishInsiderThreat(
            String employeeId,
            String threatIndicators,
            Double riskScore,
            List<String> suspiciousActivities,
            Map<String, Object> evidenceDetails,
            String correlationId) {
        
        log.error("Publishing insider threat: employeeId={}, riskScore={}, indicators={}",
            employeeId, riskScore, threatIndicators);
        
        String incidentId = generateIncidentId("INSIDER");
        
        SecurityIncidentEvent event = SecurityIncidentEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .incidentId(incidentId)
            .incidentType("INSIDER_THREAT")
            .severity("CRITICAL")
            .affectedUserId(employeeId)
            .affectedEntities(suspiciousActivities)
            .detectionSource("INSIDER_THREAT_DETECTION")
            .description(String.format("Insider threat detected for employee %s - Risk Score: %.2f - Indicators: %s", 
                employeeId, riskScore, threatIndicators))
            .requiresImmediateAction(riskScore >= 0.80)
            .requiresNotification(true)
            .metadata(evidenceDetails)
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        return sendEvent(event);
    }

    /**
     * Publish ransomware detection
     */
    public CompletableFuture<SendResult<String, SecurityIncidentEvent>> publishRansomwareDetection(
            String affectedSystem,
            String ransomwareVariant,
            List<String> affectedFiles,
            String detectionMethod,
            String correlationId) {
        
        log.error("CRITICAL: Publishing ransomware detection: system={}, variant={}, files={}",
            affectedSystem, ransomwareVariant, affectedFiles.size());
        
        String incidentId = generateIncidentId("RANSOMWARE");
        
        SecurityIncidentEvent event = SecurityIncidentEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .incidentId(incidentId)
            .incidentType("RANSOMWARE")
            .severity("CRITICAL")
            .affectedEntities(List.of(affectedSystem))
            .detectionSource(detectionMethod)
            .description(String.format("RANSOMWARE DETECTED: %s on %s - %d files affected", 
                ransomwareVariant, affectedSystem, affectedFiles.size()))
            .requiresImmediateAction(true)
            .requiresNotification(true)
            .metadata(Map.of(
                "ransomwareVariant", ransomwareVariant,
                "affectedSystem", affectedSystem,
                "affectedFilesCount", affectedFiles.size(),
                "detectionMethod", detectionMethod
            ))
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        return sendEvent(event);
    }

    /**
     * Publish DDoS attack incident
     */
    public CompletableFuture<SendResult<String, SecurityIncidentEvent>> publishDDoSAttack(
            String targetService,
            String attackType,
            Integer requestRate,
            List<String> sourceIPs,
            Map<String, Object> attackMetrics,
            String correlationId) {
        
        log.error("Publishing DDoS attack: target={}, type={}, rate={}/s, sources={}",
            targetService, attackType, requestRate, sourceIPs.size());
        
        String incidentId = generateIncidentId("DDOS");
        
        SecurityIncidentEvent event = SecurityIncidentEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .incidentId(incidentId)
            .incidentType("DDoS_ATTACK")
            .severity("CRITICAL")
            .affectedEntities(List.of(targetService))
            .detectionSource("DDOS_PROTECTION_SYSTEM")
            .description(String.format("DDoS attack detected: %s against %s - %d req/s from %d sources", 
                attackType, targetService, requestRate, sourceIPs.size()))
            .requiresImmediateAction(true)
            .requiresNotification(true)
            .metadata(Map.of(
                "attackType", attackType,
                "targetService", targetService,
                "requestRate", requestRate,
                "sourceCount", sourceIPs.size(),
                "attackMetrics", attackMetrics
            ))
            .correlationId(correlationId)
            .timestamp(Instant.now())
            .build();
        
        return sendEvent(event);
    }

    /**
     * Generate unique incident ID
     */
    private String generateIncidentId(String prefix) {
        return String.format("INC-%s-%s", prefix, UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }

    /**
     * Send event to Kafka with error handling
     */
    private CompletableFuture<SendResult<String, SecurityIncidentEvent>> sendEvent(SecurityIncidentEvent event) {
        try {
            CompletableFuture<SendResult<String, SecurityIncidentEvent>> future = 
                kafkaTemplate.send(TOPIC, event.getIncidentId(), event);
            
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.debug("Security incident event published: eventId={}, incidentId={}, type={}",
                        event.getEventId(), event.getIncidentId(), event.getIncidentType());
                } else {
                    log.error("CRITICAL: Failed to publish security incident: eventId={}, incidentId={}, error={}",
                        event.getEventId(), event.getIncidentId(), ex.getMessage(), ex);
                }
            });
            
            return future;
            
        } catch (Exception e) {
            log.error("CRITICAL: Error sending security incident event: eventId={}, error={}", 
                event.getEventId(), e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Get event statistics
     */
    public Map<String, Object> getEventStatistics() {
        return Map.of(
            "topic", TOPIC,
            "incidentTypes", List.of(
                "DATA_BREACH",
                "ACCOUNT_COMPROMISE",
                "UNAUTHORIZED_ACCESS",
                "MALICIOUS_ACTIVITY",
                "SECURITY_POLICY_VIOLATION",
                "INSIDER_THREAT",
                "RANSOMWARE",
                "DDoS_ATTACK"
            ),
            "producerActive", true
        );
    }
}