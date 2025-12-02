package com.waqiti.wallet.kafka;

import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.wallet.event.WalletAuditLogEvent;
import com.waqiti.wallet.service.AuditService;
import com.waqiti.wallet.service.ComplianceService;
import com.waqiti.wallet.service.SecurityService;
import com.waqiti.wallet.service.ReportingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Production-grade Kafka consumer for wallet audit logs
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletAuditLogConsumer {

    private final AuditService auditService;
    private final ComplianceService complianceService;
    private final SecurityService securityService;
    private final ReportingService reportingService;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "wallet-audit-logs", groupId = "audit-processor")
    public void processAuditLog(@Payload WalletAuditLogEvent event,
                              @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                              @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                              @Header(KafkaHeaders.OFFSET) long offset,
                              Acknowledgment acknowledgment) {
        try {
            log.info("Processing audit log: {} type: {} severity: {} wallet: {}", 
                    event.getAuditId(), event.getEventType(), 
                    event.getSeverity(), event.getWalletId());
            
            // Validate event
            validateAuditEvent(event);
            
            // Process based on event type
            switch (event.getEventType()) {
                case "ACCESS_CONTROL" -> processAccessControlEvent(event);
                case "TRANSACTION_AUDIT" -> processTransactionAudit(event);
                case "BALANCE_CHANGE" -> processBalanceChangeAudit(event);
                case "LIMIT_CHANGE" -> processLimitChangeAudit(event);
                case "SECURITY_INCIDENT" -> processSecurityIncident(event);
                case "COMPLIANCE_EVENT" -> processComplianceEvent(event);
                case "CONFIGURATION_CHANGE" -> processConfigurationChange(event);
                case "USER_ACTION" -> processUserAction(event);
                case "SYSTEM_EVENT" -> processSystemEvent(event);
                case "FRAUD_DETECTION" -> processFraudDetection(event);
                default -> processGenericAudit(event);
            }
            
            // Store audit log
            storeAuditLog(event);
            
            // Check for anomalies
            checkForAnomalies(event);
            
            // Generate alerts if needed
            generateAlerts(event);
            
            // Update compliance records
            updateComplianceRecords(event);
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
            log.debug("Successfully processed audit log: {}", event.getAuditId());
            
        } catch (Exception e) {
            log.error("Failed to process audit log {}: topic={}, partition={}, offset={}, error={}",
                    event.getAuditId(), topic, partition, offset, e.getMessage(), e);

            dlqHandler.handleFailedMessage(event, topic, partition, offset, e)
                .thenAccept(result -> log.info("Wallet audit log sent to DLQ: auditId={}, offset={}, destination={}, category={}",
                        event.getAuditId(), offset, result.getDestinationTopic(), result.getFailureCategory()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ handling failed for wallet audit log - MESSAGE MAY BE LOST! " +
                            "auditId={}, topic={}, partition={}, offset={}, error={}",
                            event.getAuditId(), topic, partition, offset, dlqError.getMessage(), dlqError);
                    return null;
                });

            throw new RuntimeException("Audit log processing failed", e);
        }
    }

    private void validateAuditEvent(WalletAuditLogEvent event) {
        if (event.getAuditId() == null || event.getAuditId().trim().isEmpty()) {
            throw new IllegalArgumentException("Audit ID is required");
        }
        
        if (event.getEventType() == null || event.getEventType().trim().isEmpty()) {
            throw new IllegalArgumentException("Event type is required");
        }
        
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Timestamp is required");
        }
    }

    private void processAccessControlEvent(WalletAuditLogEvent event) {
        // Log access attempt
        auditService.logAccessAttempt(
            event.getWalletId(),
            event.getUserId(),
            event.getAccessType(),
            event.getResource(),
            event.isSuccessful(),
            event.getIpAddress(),
            event.getDeviceId()
        );
        
        // Check for suspicious access patterns
        if (securityService.isSuspiciousAccess(
                event.getUserId(),
                event.getIpAddress(),
                event.getAccessPattern())) {
            
            securityService.flagSuspiciousAccess(
                event.getWalletId(),
                event.getUserId(),
                event.getAccessDetails()
            );
        }
        
        // Track failed access attempts
        if (!event.isSuccessful()) {
            int failedAttempts = auditService.incrementFailedAccess(
                event.getUserId(),
                event.getResource()
            );
            
            if (failedAttempts > 5) {
                securityService.blockAccess(
                    event.getUserId(),
                    event.getResource(),
                    "EXCESSIVE_FAILED_ATTEMPTS"
                );
            }
        }
    }

    private void processTransactionAudit(WalletAuditLogEvent event) {
        // Record transaction audit
        auditService.recordTransactionAudit(
            event.getTransactionId(),
            event.getWalletId(),
            event.getTransactionType(),
            event.getAmount(),
            event.getCurrency(),
            event.getStatus(),
            event.getMetadata()
        );
        
        // Check transaction patterns
        if (event.isAnomalousTransaction()) {
            complianceService.flagAnomalousTransaction(
                event.getTransactionId(),
                event.getAnomalyReasons(),
                event.getRiskScore()
            );
        }
        
        // Track high-value transactions
        if (event.getAmount() != null && 
            event.getAmount().compareTo(event.getHighValueThreshold()) > 0) {
            
            complianceService.reportHighValueTransaction(
                event.getTransactionId(),
                event.getWalletId(),
                event.getAmount(),
                event.getCounterparty()
            );
        }
    }

    private void processBalanceChangeAudit(WalletAuditLogEvent event) {
        // Record balance change
        auditService.recordBalanceChange(
            event.getWalletId(),
            event.getPreviousBalance(),
            event.getNewBalance(),
            event.getChangeAmount(),
            event.getChangeReason(),
            event.getAuthorizedBy()
        );
        
        // Check for unauthorized changes
        if (!event.isAuthorized()) {
            securityService.reportUnauthorizedBalanceChange(
                event.getWalletId(),
                event.getChangeAmount(),
                event.getUnauthorizedReason()
            );
        }
        
        // Verify balance integrity
        boolean integrityValid = auditService.verifyBalanceIntegrity(
            event.getWalletId(),
            event.getNewBalance(),
            event.getCalculatedBalance()
        );
        
        if (!integrityValid) {
            auditService.reportBalanceDiscrepancy(
                event.getWalletId(),
                event.getNewBalance(),
                event.getCalculatedBalance(),
                event.getDiscrepancyAmount()
            );
        }
    }

    private void processLimitChangeAudit(WalletAuditLogEvent event) {
        // Record limit change
        auditService.recordLimitChange(
            event.getWalletId(),
            event.getLimitType(),
            event.getPreviousLimit(),
            event.getNewLimit(),
            event.getChangeReason(),
            event.getApprovedBy()
        );
        
        // Check for policy violations
        if (complianceService.violatesLimitPolicy(
                event.getWalletId(),
                event.getLimitType(),
                event.getNewLimit())) {
            
            complianceService.reportPolicyViolation(
                event.getWalletId(),
                "LIMIT_POLICY_VIOLATION",
                event.getLimitType(),
                event.getNewLimit()
            );
        }
    }

    private void processSecurityIncident(WalletAuditLogEvent event) {
        // Log security incident
        String incidentId = securityService.logSecurityIncident(
            event.getWalletId(),
            event.getIncidentType(),
            event.getSeverity(),
            event.getIncidentDetails(),
            event.getAffectedResources()
        );
        
        // Initiate incident response
        securityService.initiateIncidentResponse(
            incidentId,
            event.getIncidentType(),
            event.getSeverity()
        );
        
        // Apply security measures
        if ("CRITICAL".equals(event.getSeverity()) || "HIGH".equals(event.getSeverity())) {
            securityService.applyEmergencyMeasures(
                event.getWalletId(),
                event.getSecurityMeasures()
            );
        }
        
        // Notify security team
        securityService.notifySecurityTeam(
            incidentId,
            event.getIncidentType(),
            event.getSeverity(),
            event.getWalletId()
        );
    }

    private void processComplianceEvent(WalletAuditLogEvent event) {
        // Record compliance event
        complianceService.recordComplianceEvent(
            event.getWalletId(),
            event.getComplianceType(),
            event.getRegulation(),
            event.getComplianceStatus(),
            event.getComplianceDetails()
        );
        
        // Check regulatory requirements
        if (event.isRegulatoryReportRequired()) {
            complianceService.generateRegulatoryReport(
                event.getWalletId(),
                event.getReportType(),
                event.getRegulator(),
                event.getReportingDeadline()
            );
        }
        
        // Update compliance score
        complianceService.updateComplianceScore(
            event.getWalletId(),
            event.getComplianceScore(),
            event.getComplianceFactors()
        );
    }

    private void processConfigurationChange(WalletAuditLogEvent event) {
        // Record configuration change
        auditService.recordConfigurationChange(
            event.getWalletId(),
            event.getConfigurationType(),
            event.getPreviousValue(),
            event.getNewValue(),
            event.getChangedBy(),
            event.getChangeReason()
        );
        
        // Validate configuration
        if (!auditService.isValidConfiguration(
                event.getConfigurationType(),
                event.getNewValue())) {
            
            auditService.reportInvalidConfiguration(
                event.getWalletId(),
                event.getConfigurationType(),
                event.getNewValue(),
                event.getValidationErrors()
            );
        }
        
        // Track critical changes
        if (event.isCriticalConfiguration()) {
            auditService.trackCriticalConfigChange(
                event.getWalletId(),
                event.getConfigurationType(),
                event.getImpactAssessment()
            );
        }
    }

    private void processUserAction(WalletAuditLogEvent event) {
        // Log user action
        auditService.logUserAction(
            event.getUserId(),
            event.getWalletId(),
            event.getActionType(),
            event.getActionDetails(),
            event.getActionResult(),
            event.getTimestamp()
        );
        
        // Track user behavior
        auditService.updateUserBehaviorProfile(
            event.getUserId(),
            event.getActionType(),
            event.getBehaviorMetrics()
        );
        
        // Check for policy violations
        if (auditService.violatesUserPolicy(
                event.getUserId(),
                event.getActionType())) {
            
            auditService.reportUserPolicyViolation(
                event.getUserId(),
                event.getActionType(),
                event.getPolicyViolated()
            );
        }
    }

    private void processSystemEvent(WalletAuditLogEvent event) {
        // Log system event
        auditService.logSystemEvent(
            event.getSystemComponent(),
            event.getEventCategory(),
            event.getEventDetails(),
            event.getSystemMetrics()
        );
        
        // Track system performance
        if (event.getPerformanceMetrics() != null) {
            auditService.updateSystemPerformance(
                event.getSystemComponent(),
                event.getPerformanceMetrics()
            );
        }
        
        // Check for system anomalies
        if (auditService.detectSystemAnomaly(
                event.getSystemComponent(),
                event.getSystemMetrics())) {
            
            auditService.reportSystemAnomaly(
                event.getSystemComponent(),
                event.getAnomalyType(),
                event.getAnomalyDetails()
            );
        }
    }

    private void processFraudDetection(WalletAuditLogEvent event) {
        // Log fraud detection event
        String fraudCaseId = securityService.logFraudDetection(
            event.getWalletId(),
            event.getFraudType(),
            event.getFraudScore(),
            event.getFraudIndicators()
        );
        
        // Initiate fraud investigation
        if (event.getFraudScore() > 70) {
            securityService.initiateFraudInvestigation(
                fraudCaseId,
                event.getWalletId(),
                event.getFraudType()
            );
        }
        
        // Apply fraud prevention measures
        securityService.applyFraudPreventionMeasures(
            event.getWalletId(),
            event.getFraudType(),
            event.getPreventionMeasures()
        );
        
        // Report to fraud team
        if (event.getFraudScore() > 85) {
            securityService.escalateToFraudTeam(
                fraudCaseId,
                event.getWalletId(),
                event.getFraudDetails()
            );
        }
    }

    private void processGenericAudit(WalletAuditLogEvent event) {
        // Log generic audit event
        auditService.logGenericEvent(
            event.getAuditId(),
            event.getEventType(),
            event.getWalletId(),
            event.getEventDetails(),
            event.getTimestamp()
        );
    }

    private void storeAuditLog(WalletAuditLogEvent event) {
        // Store in audit database
        auditService.storeAuditLog(
            event.getAuditId(),
            event.getEventType(),
            event.getWalletId(),
            event.getUserId(),
            event.getEventDetails(),
            event.getMetadata(),
            event.getTimestamp()
        );
        
        // Archive if required
        if (event.isArchiveRequired()) {
            auditService.archiveAuditLog(
                event.getAuditId(),
                event.getArchiveRetentionDays()
            );
        }
        
        // Store in compliance repository if needed
        if (event.isComplianceRelevant()) {
            complianceService.storeComplianceAudit(
                event.getAuditId(),
                event.getComplianceType(),
                event.getRetentionPeriod()
            );
        }
    }

    private void checkForAnomalies(WalletAuditLogEvent event) {
        // Analyze for anomalies
        Map<String, Object> anomalies = auditService.detectAnomalies(
            event.getWalletId(),
            event.getEventType(),
            event.getEventPattern(),
            event.getHistoricalData()
        );
        
        if (!anomalies.isEmpty()) {
            // Report anomalies
            auditService.reportAnomalies(
                event.getWalletId(),
                event.getEventType(),
                anomalies
            );
            
            // Trigger investigation if critical
            if (event.getSeverity() != null && 
                ("CRITICAL".equals(event.getSeverity()) || "HIGH".equals(event.getSeverity()))) {
                
                auditService.triggerAnomalyInvestigation(
                    event.getWalletId(),
                    anomalies
                );
            }
        }
    }

    private void generateAlerts(WalletAuditLogEvent event) {
        // Determine if alerts needed
        if (shouldGenerateAlert(event)) {
            // Generate alert
            String alertId = auditService.generateAlert(
                event.getAlertType(),
                event.getSeverity(),
                event.getWalletId(),
                event.getAlertMessage()
            );
            
            // Send notifications
            auditService.sendAlertNotifications(
                alertId,
                event.getAlertRecipients(),
                event.getAlertChannels()
            );
            
            // Schedule follow-up
            if (event.isFollowUpRequired()) {
                auditService.scheduleAlertFollowUp(
                    alertId,
                    event.getFollowUpTime()
                );
            }
        }
    }

    private boolean shouldGenerateAlert(WalletAuditLogEvent event) {
        return event.isAlertRequired() ||
               "CRITICAL".equals(event.getSeverity()) ||
               "SECURITY_INCIDENT".equals(event.getEventType()) ||
               "FRAUD_DETECTION".equals(event.getEventType()) ||
               (event.getRiskScore() != null && event.getRiskScore() > 80);
    }

    private void updateComplianceRecords(WalletAuditLogEvent event) {
        // Update compliance tracking
        complianceService.updateAuditTrail(
            event.getWalletId(),
            event.getEventType(),
            event.getComplianceImpact(),
            event.getTimestamp()
        );
        
        // Generate compliance reports if needed
        if (event.isComplianceReportRequired()) {
            reportingService.generateComplianceReport(
                event.getWalletId(),
                event.getReportPeriod(),
                event.getReportType()
            );
        }
    }
}