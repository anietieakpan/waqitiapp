package com.waqiti.security.kafka;

import com.waqiti.security.event.CriticalAlertEvent;
import com.waqiti.security.service.AlertManagementService;
import com.waqiti.security.service.IncidentResponseService;
import com.waqiti.security.service.EscalationService;
import com.waqiti.security.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * Production-grade Kafka consumer for critical system alerts
 * Handles: critical-alerts, critical-system-alerts, critical-security-alerts, alerts-emergency
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CriticalAlertsConsumer {

    private final AlertManagementService alertService;
    private final IncidentResponseService incidentService;
    private final EscalationService escalationService;
    private final NotificationService notificationService;

    @KafkaListener(topics = {"critical-alerts", "critical-system-alerts", 
                             "critical-security-alerts", "alerts-emergency"}, 
                   groupId = "critical-alert-processor")
    public void processCriticalAlert(@Payload CriticalAlertEvent event,
                                   @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                   @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                   @Header(KafkaHeaders.OFFSET) long offset,
                                   Acknowledgment acknowledgment) {
        try {
            log.error("CRITICAL ALERT: {} - Type: {} - Severity: {} - System: {}", 
                    event.getAlertId(), event.getAlertType(), event.getSeverity(), event.getAffectedSystem());
            
            // Immediate incident creation
            String incidentId = incidentService.createCriticalIncident(
                event.getAlertType(),
                event.getSeverity(),
                event.getAffectedSystem(),
                event.getDescription(),
                event.getImpact()
            );
            
            // Parallel execution of critical actions
            CompletableFuture<Void> notificationFuture = CompletableFuture.runAsync(() -> 
                sendEmergencyNotifications(event, incidentId)
            );
            
            CompletableFuture<Void> mitigationFuture = CompletableFuture.runAsync(() -> 
                applyEmergencyMitigation(event)
            );
            
            CompletableFuture<Void> escalationFuture = CompletableFuture.runAsync(() -> 
                triggerEscalationChain(event, incidentId)
            );
            
            // Wait for all critical actions
            try {
                CompletableFuture.allOf(notificationFuture, mitigationFuture, escalationFuture)
                    .get(1, java.util.concurrent.TimeUnit.MINUTES);
            } catch (java.util.concurrent.TimeoutException e) {
                log.error("Critical alert actions timed out after 1 minute. Alert ID: {}, Type: {}",
                    event.getAlertId(), event.getAlertType(), e);
                List.of(notificationFuture, mitigationFuture, escalationFuture).forEach(f -> f.cancel(true));
                throw new RuntimeException("Critical alert actions timed out", e);
            } catch (java.util.concurrent.ExecutionException e) {
                log.error("Critical alert actions execution failed. Alert ID: {}, Type: {}",
                    event.getAlertId(), event.getAlertType(), e.getCause());
                throw new RuntimeException("Critical alert actions failed: " + e.getCause().getMessage(), e.getCause());
            } catch (java.util.concurrent.InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Critical alert actions interrupted. Alert ID: {}, Type: {}",
                    event.getAlertId(), event.getAlertType(), e);
                throw new RuntimeException("Critical alert actions interrupted", e);
            }

            // Log to audit trail
            alertService.logCriticalAlert(
                event.getAlertId(),
                event.getAlertType(),
                event.getSeverity(),
                event.getAffectedSystem(),
                event.getTimestamp(),
                incidentId
            );
            
            // Update monitoring dashboards
            alertService.updateCriticalAlertDashboard(
                event.getAlertType(),
                event.getSeverity(),
                event.getAffectedSystem(),
                event.getMetrics()
            );
            
            // Acknowledge
            acknowledgment.acknowledge();
            
            log.info("Critical alert processed - Incident: {} created", incidentId);
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to process critical alert {}: {}", 
                    event.getAlertId(), e.getMessage(), e);
            // Never throw - always acknowledge critical alerts to prevent reprocessing
            acknowledgment.acknowledge();
            // Send failure notification
            notificationService.sendProcessingFailureAlert(event.getAlertId(), e.getMessage());
        }
    }

    private void sendEmergencyNotifications(CriticalAlertEvent event, String incidentId) {
        // Send to all channels simultaneously
        notificationService.sendPagerDutyAlert(event, incidentId);
        notificationService.sendSlackCriticalAlert(event, incidentId);
        notificationService.sendSmsToOncallTeam(event, incidentId);
        notificationService.sendEmailToManagement(event, incidentId);
        
        // Call on-call engineer if severity is CRITICAL
        if ("CRITICAL".equals(event.getSeverity())) {
            notificationService.initiatePhoneCall(event.getOncallPhone(), event.getDescription());
        }
    }

    private void applyEmergencyMitigation(CriticalAlertEvent event) {
        switch (event.getAlertType()) {
            case "SECURITY_BREACH" -> {
                incidentService.activateSecurityLockdown(event.getAffectedSystem());
                incidentService.revokeAllAccess(event.getCompromisedResources());
            }
            case "SYSTEM_FAILURE" -> {
                incidentService.activateDisasterRecovery(event.getAffectedSystem());
                incidentService.switchToBackupSystems(event.getFailedComponents());
            }
            case "DATA_BREACH" -> {
                incidentService.isolateAffectedData(event.getCompromisedData());
                incidentService.activateDataBreachProtocol(event.getDataClassification());
            }
            case "PAYMENT_SYSTEM_DOWN" -> {
                incidentService.activatePaymentFailover(event.getPaymentProvider());
                incidentService.enableManualProcessing();
            }
            case "FRAUD_SPIKE" -> {
                incidentService.activateFraudLockdown();
                incidentService.blockHighRiskTransactions(event.getRiskThreshold());
            }
        }
    }

    private void triggerEscalationChain(CriticalAlertEvent event, String incidentId) {
        // Immediate escalation based on severity
        switch (event.getSeverity()) {
            case "CRITICAL" -> {
                escalationService.notifyCTO(incidentId, event);
                escalationService.notifyCEO(incidentId, event);
                escalationService.notifyBoard(incidentId, event);
                escalationService.activateWarRoom(incidentId);
            }
            case "HIGH" -> {
                escalationService.notifyVPEngineering(incidentId, event);
                escalationService.notifySecurityTeam(incidentId, event);
                escalationService.scheduleEmergencyMeeting(incidentId);
            }
            case "MEDIUM" -> {
                escalationService.notifyTeamLead(incidentId, event);
                escalationService.notifyOnCallTeam(incidentId, event);
            }
        }
        
        // Set up escalation timers
        escalationService.scheduleAutoEscalation(
            incidentId,
            event.getEscalationTimeout(),
            event.getEscalationLevel()
        );
    }
}