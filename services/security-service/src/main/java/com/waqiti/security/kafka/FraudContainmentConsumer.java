package com.waqiti.security.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.security.service.IncidentResponseService;
import com.waqiti.security.service.NotificationService;
import com.waqiti.security.service.SecurityMetricsService;
import com.waqiti.security.domain.SecurityIncident;
import com.waqiti.security.domain.ContainmentAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * CRITICAL: Consumes fraud containment execution events
 * 
 * This consumer processes notifications when fraud containment actions
 * have been executed by the fraud detection system. It coordinates
 * follow-up security measures and incident response.
 * 
 * Events processed:
 * - fraud-containment-executed: When fraud containment actions are completed
 * 
 * Security Impact: CRITICAL - Fraud response coordination
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FraudContainmentConsumer {

    private final IncidentResponseService incidentResponseService;
    private final NotificationService notificationService;
    private final SecurityMetricsService securityMetricsService;
    private final ObjectMapper objectMapper;

    /**
     * Process fraud containment execution notifications
     */
    @KafkaListener(topics = "fraud-containment-executed", groupId = "security-fraud-containment-group")
    public void handleFraudContainmentExecuted(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        
        try {
            log.info("SECURITY: Processing fraud containment execution - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
            
            Map<String, Object> containmentEvent = objectMapper.readValue(message, Map.class);
            
            String userId = (String) containmentEvent.get("userId");
            String alertId = (String) containmentEvent.get("alertId");
            Integer containmentActions = (Integer) containmentEvent.get("containmentActions");
            
            if (userId == null || alertId == null) {
                log.error("SECURITY: Invalid fraud containment event - missing userId or alertId");
                return;
            }
            
            // Create security incident for tracking
            SecurityIncident incident = createSecurityIncident(userId, alertId, containmentActions);
            
            // Initiate post-containment security measures
            initiatePostContainmentMeasures(incident, containmentEvent);
            
            // Update security metrics
            updateSecurityMetrics(incident);
            
            // Send notifications to security team
            sendSecurityNotifications(incident);
            
            log.info("SECURITY: Successfully processed fraud containment for user: {} alert: {}", 
                userId, alertId);
            
        } catch (Exception e) {
            log.error("SECURITY: Failed to process fraud containment event", e);
            // Re-throw to trigger Kafka retry mechanism
            throw new RuntimeException("Failed to process fraud containment", e);
        }
    }
    
    /**
     * Create security incident record for fraud containment
     */
    private SecurityIncident createSecurityIncident(String userId, String alertId, Integer containmentActions) {
        SecurityIncident incident = SecurityIncident.builder()
            .incidentId(UUID.randomUUID().toString())
            .userId(userId)
            .alertId(alertId)
            .incidentType("FRAUD_CONTAINMENT_EXECUTED")
            .severity("HIGH")
            .status("ACTIVE")
            .description("Fraud containment actions executed for user")
            .containmentActions(containmentActions != null ? containmentActions : 0)
            .detectedAt(LocalDateTime.now())
            .assignedTo("SECURITY_OPERATIONS_CENTER")
            .escalationLevel(2)
            .requiresInvestigation(true)
            .autoResolved(false)
            .build();
            
        return incidentResponseService.createIncident(incident);
    }
    
    /**
     * Initiate post-containment security measures
     */
    private void initiatePostContainmentMeasures(SecurityIncident incident, Map<String, Object> containmentEvent) {
        try {
            // 1. Enhanced monitoring for the user
            incidentResponseService.enableEnhancedMonitoring(incident.getUserId(), 
                java.time.Duration.ofDays(30));
            
            // 2. Security review flag
            incidentResponseService.flagForSecurityReview(incident.getUserId(), 
                "Fraud containment executed");
            
            // 3. Additional authentication requirements
            incidentResponseService.requireAdditionalAuthentication(incident.getUserId(),
                java.time.Duration.ofDays(7));
            
            // 4. Transaction monitoring
            incidentResponseService.enableTransactionMonitoring(incident.getUserId(),
                "ENHANCED", java.time.Duration.ofDays(14));
            
            log.info("SECURITY: Post-containment measures initiated for incident: {}", 
                incident.getIncidentId());
            
        } catch (Exception e) {
            log.error("SECURITY: Failed to initiate post-containment measures for incident: {}", 
                incident.getIncidentId(), e);
        }
    }
    
    /**
     * Update security metrics
     */
    private void updateSecurityMetrics(SecurityIncident incident) {
        try {
            securityMetricsService.incrementFraudContainmentCount();
            securityMetricsService.recordIncidentResponseTime(incident);
            securityMetricsService.updateThreatLevel("ELEVATED");
            
        } catch (Exception e) {
            log.error("SECURITY: Failed to update security metrics", e);
        }
    }
    
    /**
     * Send notifications to security team
     */
    private void sendSecurityNotifications(SecurityIncident incident) {
        try {
            // Immediate notification to security operations center
            notificationService.sendSecurityAlert(
                "SECURITY_OPERATIONS_CENTER",
                "Fraud Containment Executed",
                String.format("Fraud containment actions have been executed for user %s. " +
                    "Incident ID: %s. Please review for follow-up actions.",
                    incident.getUserId(), incident.getIncidentId()),
                "HIGH"
            );
            
            // Notification to compliance team
            notificationService.sendComplianceAlert(
                "COMPLIANCE_TEAM",
                "Fraud Event Containment",
                String.format("Fraud containment executed for user %s. " +
                    "Review for regulatory reporting requirements. Incident: %s",
                    incident.getUserId(), incident.getIncidentId())
            );
            
            // Dashboard update
            notificationService.updateSecurityDashboard("FRAUD_CONTAINMENT", incident);
            
        } catch (Exception e) {
            log.error("SECURITY: Failed to send security notifications", e);
        }
    }
}