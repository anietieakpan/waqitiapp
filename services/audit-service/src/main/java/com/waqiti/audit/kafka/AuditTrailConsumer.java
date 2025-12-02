package com.waqiti.audit.kafka;

import com.waqiti.audit.event.AuditEvent;
import com.waqiti.audit.service.AuditService;
import com.waqiti.audit.service.ComplianceAuditService;
import com.waqiti.audit.service.SecurityAuditService;
import com.waqiti.audit.service.ImmutableStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Production-grade Kafka consumer for audit trail events
 * Handles: audit-events, audit-trail, audit-health-check, security-audit-events,
 * compliance-audit-trail, immutable-audit-store, audit.alerts.stream, audit.events.stream,
 * audit.chain.updates, user-activity-logs, soc-events, ledger-events
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditTrailConsumer {

    private final AuditService auditService;
    private final ComplianceAuditService complianceAuditService;
    private final SecurityAuditService securityAuditService;
    private final ImmutableStoreService immutableStoreService;

    @KafkaListener(topics = {"audit-events", "audit-trail", "audit-health-check", "security-audit-events",
                             "compliance-audit-trail", "immutable-audit-store", "audit.alerts.stream", 
                             "audit.events.stream", "audit.chain.updates", "user-activity-logs", 
                             "soc-events", "ledger-events"}, 
                   groupId = "audit-trail-processor")
    @Transactional
    public void processAuditEvent(@Payload AuditEvent event,
                                @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                @Header(KafkaHeaders.OFFSET) long offset,
                                Acknowledgment acknowledgment) {
        try {
            log.info("Processing audit event: {} - Type: {} - Action: {} - User: {}", 
                    event.getAuditId(), event.getAuditType(), event.getAction(), event.getUserId());
            
            // Process based on topic
            switch (topic) {
                case "audit-events" -> handleAuditEvent(event);
                case "audit-trail" -> handleAuditTrail(event);
                case "audit-health-check" -> handleAuditHealthCheck(event);
                case "security-audit-events" -> handleSecurityAuditEvent(event);
                case "compliance-audit-trail" -> handleComplianceAuditTrail(event);
                case "immutable-audit-store" -> handleImmutableStore(event);
                case "audit.alerts.stream" -> handleAuditAlerts(event);
                case "audit.events.stream" -> handleAuditEventStream(event);
                case "audit.chain.updates" -> handleAuditChainUpdates(event);
                case "user-activity-logs" -> handleUserActivityLogs(event);
                case "soc-events" -> handleSocEvents(event);
                case "ledger-events" -> handleLedgerEvents(event);
            }
            
            // Store in immutable audit log
            storeImmutableRecord(event);
            
            // Update audit metrics
            updateAuditMetrics(event);
            
            // Acknowledge
            acknowledgment.acknowledge();
            
            log.info("Successfully processed audit event: {}", event.getAuditId());
            
        } catch (Exception e) {
            log.error("Failed to process audit event {}: {}", 
                    event.getAuditId(), e.getMessage(), e);
            
            // Critical audit failure - log to separate system
            auditService.logCriticalAuditFailure(event, e);
            
            throw new RuntimeException("Audit processing failed", e);
        }
    }

    private void handleAuditEvent(AuditEvent event) {
        // Process standard audit event
        String auditId = auditService.createAuditRecord(
            event.getEventType(),
            event.getAction(),
            event.getUserId(),
            event.getResourceId(),
            event.getAuditDetails()
        );
        
        // Classify audit event
        String classification = auditService.classifyAuditEvent(
            event.getAction(),
            event.getResourceType(),
            event.getRiskLevel()
        );
        
        // Store audit record
        auditService.storeAuditRecord(
            auditId,
            event,
            classification,
            LocalDateTime.now()
        );
        
        // Check compliance requirements
        if (auditService.requiresComplianceReview(classification)) {
            complianceAuditService.scheduleComplianceReview(
                auditId,
                event.getComplianceFramework(),
                event.getReviewPriority()
            );
        }
        
        // Generate audit fingerprint for integrity
        String fingerprint = generateAuditFingerprint(event);
        auditService.storeAuditFingerprint(auditId, fingerprint);
        
        // Check for suspicious patterns
        if (auditService.detectSuspiciousPattern(event)) {
            securityAuditService.flagSuspiciousActivity(
                event.getUserId(),
                event.getAction(),
                event.getPatternIndicators()
            );
        }
    }

    private void handleAuditTrail(AuditEvent event) {
        // Build comprehensive audit trail
        List<Map<String, Object>> auditChain = auditService.buildAuditChain(
            event.getResourceId(),
            event.getChainStartTime(),
            event.getChainEndTime()
        );
        
        // Validate audit trail integrity
        boolean isIntegrityValid = auditService.validateAuditTrailIntegrity(
            auditChain,
            event.getExpectedChecksum()
        );
        
        if (!isIntegrityValid) {
            log.error("Audit trail integrity violation detected for resource: {}", 
                    event.getResourceId());
            
            securityAuditService.handleIntegrityViolation(
                event.getResourceId(),
                auditChain,
                event.getIntegrityDetails()
            );
        }
        
        // Generate audit trail report
        String reportId = auditService.generateAuditTrailReport(
            event.getResourceId(),
            auditChain,
            event.getReportFormat()
        );
        
        // Archive audit trail
        auditService.archiveAuditTrail(
            reportId,
            auditChain,
            event.getRetentionPolicy()
        );
        
        // Update audit trail metrics
        auditService.updateAuditTrailMetrics(
            event.getResourceType(),
            auditChain.size(),
            event.getTrailDuration()
        );
    }

    private void handleAuditHealthCheck(AuditEvent event) {
        // Perform audit system health check
        Map<String, Object> healthStatus = auditService.performHealthCheck(
            event.getHealthCheckType(),
            event.getHealthCriteria()
        );
        
        // Check audit system components
        boolean isAuditStoreHealthy = auditService.checkAuditStoreHealth();
        boolean isIndexingHealthy = auditService.checkIndexingHealth();
        boolean isReplicationHealthy = auditService.checkReplicationHealth();
        
        // Store health check results
        auditService.storeHealthCheckResults(
            event.getHealthCheckId(),
            healthStatus,
            LocalDateTime.now()
        );
        
        // Alert on health issues
        if (!isAuditStoreHealthy || !isIndexingHealthy || !isReplicationHealthy) {
            auditService.sendHealthAlert(
                event.getHealthCheckId(),
                healthStatus,
                event.getAlertTargets()
            );
        }
        
        // Schedule remediation if needed
        if (event.isAutoRemediationEnabled()) {
            auditService.scheduleAutoRemediation(
                event.getHealthCheckId(),
                healthStatus,
                event.getRemediationActions()
            );
        }
    }

    private void handleSecurityAuditEvent(AuditEvent event) {
        // Process security-specific audit event
        String securityEventType = event.getSecurityEventType();
        
        switch (securityEventType) {
            case "ACCESS_VIOLATION" -> {
                securityAuditService.recordAccessViolation(
                    event.getUserId(),
                    event.getResourceId(),
                    event.getAttemptedAction(),
                    event.getViolationDetails()
                );
            }
            case "PRIVILEGE_ESCALATION" -> {
                securityAuditService.recordPrivilegeEscalation(
                    event.getUserId(),
                    event.getFromRole(),
                    event.getToRole(),
                    event.getEscalationMethod()
                );
            }
            case "DATA_BREACH_ATTEMPT" -> {
                securityAuditService.recordDataBreachAttempt(
                    event.getUserId(),
                    event.getDataCategory(),
                    event.getBreachVector(),
                    event.getPreventionMeasures()
                );
            }
            case "AUTHENTICATION_ANOMALY" -> {
                securityAuditService.recordAuthenticationAnomaly(
                    event.getUserId(),
                    event.getAuthenticationMethod(),
                    event.getAnomalyType(),
                    event.getRiskScore()
                );
            }
        }
        
        // Calculate security risk score
        Double riskScore = securityAuditService.calculateSecurityRisk(
            event.getSecurityEventType(),
            event.getSecurityContext(),
            event.getThreatIntelligence()
        );
        
        // Store security audit record
        securityAuditService.storeSecurityAuditRecord(
            event.getAuditId(),
            securityEventType,
            riskScore,
            event.getSecurityMitigation()
        );
        
        // Trigger security response if high risk
        if (riskScore > event.getHighRiskThreshold()) {
            securityAuditService.triggerSecurityResponse(
                event.getAuditId(),
                riskScore,
                event.getResponsePlan()
            );
        }
    }

    private void handleComplianceAuditTrail(AuditEvent event) {
        // Process compliance audit trail
        String complianceFramework = event.getComplianceFramework();
        
        // Map to compliance requirements
        List<String> requirements = complianceAuditService.mapToComplianceRequirements(
            event.getAction(),
            event.getResourceType(),
            complianceFramework
        );
        
        // Record compliance audit entry
        String complianceAuditId = complianceAuditService.recordComplianceAudit(
            event.getAuditId(),
            complianceFramework,
            requirements,
            event.getComplianceEvidence()
        );
        
        // Assess compliance status
        Map<String, Object> complianceAssessment = complianceAuditService.assessCompliance(
            complianceAuditId,
            requirements,
            event.getComplianceMetrics()
        );
        
        // Store compliance assessment
        complianceAuditService.storeComplianceAssessment(
            complianceAuditId,
            complianceAssessment,
            LocalDateTime.now()
        );
        
        // Generate compliance report if required
        if (event.isComplianceReportRequired()) {
            String reportId = complianceAuditService.generateComplianceReport(
                complianceFramework,
                event.getReportPeriod(),
                complianceAssessment
            );
            
            // Submit to regulators
            complianceAuditService.submitRegulatoryReport(
                reportId,
                event.getRegulatoryBodies(),
                event.getSubmissionDeadline()
            );
        }
    }

    private void handleImmutableStore(AuditEvent event) {
        // Store in immutable audit store
        String storeId = immutableStoreService.storeImmutableRecord(
            event.getAuditId(),
            event.getAuditData(),
            event.getIntegrityHash()
        );
        
        // Create blockchain entry if enabled
        if (event.isBlockchainEnabled()) {
            String blockHash = immutableStoreService.createBlockchainEntry(
                storeId,
                event.getAuditData(),
                event.getPreviousBlockHash()
            );
            
            // Update blockchain pointer
            immutableStoreService.updateBlockchainPointer(
                storeId,
                blockHash,
                event.getBlockchainNetwork()
            );
        }
        
        // Replicate to backup stores
        immutableStoreService.replicateToBackupStores(
            storeId,
            event.getAuditData(),
            event.getReplicationTargets()
        );
        
        // Generate immutability proof
        String proof = immutableStoreService.generateImmutabilityProof(
            storeId,
            event.getAuditData(),
            LocalDateTime.now()
        );
        
        // Store proof certificate
        immutableStoreService.storeProofCertificate(
            storeId,
            proof,
            event.getCertificateAuthority()
        );
    }

    private void handleAuditAlerts(AuditEvent event) {
        // Process audit alerts
        String alertType = event.getAlertType();
        
        // Evaluate alert conditions
        boolean shouldAlert = auditService.evaluateAlertConditions(
            alertType,
            event.getAlertCriteria(),
            event.getCurrentMetrics()
        );
        
        if (shouldAlert) {
            // Create audit alert
            String alertId = auditService.createAuditAlert(
                alertType,
                event.getSeverity(),
                event.getAlertMessage(),
                event.getAffectedResources()
            );
            
            // Send alert notifications
            auditService.sendAuditAlertNotifications(
                alertId,
                event.getNotificationTargets(),
                event.getEscalationRules()
            );
            
            // Log alert in audit trail
            auditService.logAlertInAuditTrail(
                alertId,
                event.getAlertContext(),
                LocalDateTime.now()
            );
        }
        
        // Update alert statistics
        auditService.updateAlertStatistics(
            alertType,
            shouldAlert,
            event.getResponseTime()
        );
    }

    private void handleAuditEventStream(AuditEvent event) {
        // Process audit event stream
        auditService.processEventStream(
            event.getStreamId(),
            event.getEventBatch(),
            event.getStreamingConfig()
        );
        
        // Apply stream processing rules
        auditService.applyStreamProcessingRules(
            event.getStreamId(),
            event.getProcessingRules(),
            event.getFilterCriteria()
        );
        
        // Update stream metrics
        auditService.updateStreamMetrics(
            event.getStreamId(),
            event.getEventCount(),
            event.getProcessingLatency()
        );
    }

    private void handleAuditChainUpdates(AuditEvent event) {
        // Handle audit chain updates
        String chainId = event.getChainId();
        
        // Validate chain integrity
        boolean isChainValid = auditService.validateAuditChain(
            chainId,
            event.getChainData(),
            event.getPreviousChainHash()
        );
        
        if (!isChainValid) {
            log.error("Audit chain integrity violation: {}", chainId);
            securityAuditService.handleChainIntegrityViolation(
                chainId,
                event.getChainData(),
                event.getIntegrityError()
            );
            return;
        }
        
        // Update audit chain
        auditService.updateAuditChain(
            chainId,
            event.getChainUpdate(),
            event.getUpdateSignature()
        );
        
        // Propagate chain update
        auditService.propagateChainUpdate(
            chainId,
            event.getChainUpdate(),
            event.getReplicationNodes()
        );
    }

    private void handleUserActivityLogs(AuditEvent event) {
        // Process user activity logs
        String userId = event.getUserId();
        
        // Record user activity
        auditService.recordUserActivity(
            userId,
            event.getActivityType(),
            event.getActivityDetails(),
            event.getSessionId(),
            event.getIpAddress(),
            event.getUserAgent()
        );
        
        // Analyze activity patterns
        Map<String, Object> activityAnalysis = auditService.analyzeUserActivityPatterns(
            userId,
            event.getActivityHistory(),
            event.getAnalysisWindow()
        );
        
        // Detect anomalous behavior
        if (auditService.detectAnomalousUserBehavior(userId, activityAnalysis)) {
            securityAuditService.flagAnomalousUserBehavior(
                userId,
                activityAnalysis,
                event.getBehaviorBaseline()
            );
        }
        
        // Update user activity metrics
        auditService.updateUserActivityMetrics(
            userId,
            event.getActivityType(),
            event.getActivityDuration()
        );
    }

    private void handleSocEvents(AuditEvent event) {
        // Process SOC (Security Operations Center) events
        String socEventType = event.getSocEventType();
        
        switch (socEventType) {
            case "INCIDENT_DETECTED" -> {
                securityAuditService.recordSecurityIncident(
                    event.getIncidentId(),
                    event.getIncidentType(),
                    event.getSeverity(),
                    event.getIncidentDetails()
                );
            }
            case "THREAT_INTELLIGENCE" -> {
                securityAuditService.processThreatIntelligence(
                    event.getThreatId(),
                    event.getThreatType(),
                    event.getThreatDetails(),
                    event.getThreatSources()
                );
            }
            case "VULNERABILITY_ASSESSMENT" -> {
                securityAuditService.recordVulnerabilityAssessment(
                    event.getAssetId(),
                    event.getVulnerabilities(),
                    event.getRiskRating(),
                    event.getRemediationPlan()
                );
            }
            case "SECURITY_MONITORING" -> {
                securityAuditService.updateSecurityMonitoring(
                    event.getMonitoringTarget(),
                    event.getMonitoringMetrics(),
                    event.getSecurityBaseline()
                );
            }
        }
        
        // Generate SOC report
        securityAuditService.generateSocReport(
            socEventType,
            event.getSocMetrics(),
            event.getReportingPeriod()
        );
    }

    private void handleLedgerEvents(AuditEvent event) {
        // Process ledger events
        String ledgerType = event.getLedgerType();
        
        // Create ledger entry
        String ledgerEntryId = auditService.createLedgerEntry(
            ledgerType,
            event.getTransactionId(),
            event.getLedgerData(),
            event.getEntryType()
        );
        
        // Validate ledger balance
        boolean isBalanceValid = auditService.validateLedgerBalance(
            ledgerType,
            event.getExpectedBalance(),
            event.getBalanceCheckpoint()
        );
        
        if (!isBalanceValid) {
            log.error("Ledger balance mismatch: {} - Expected: {}, Actual: {}", 
                    ledgerType, event.getExpectedBalance(), event.getActualBalance());
            
            auditService.handleLedgerDiscrepancy(
                ledgerEntryId,
                event.getExpectedBalance(),
                event.getActualBalance(),
                event.getDiscrepancyReason()
            );
        }
        
        // Update ledger hash chain
        auditService.updateLedgerHashChain(
            ledgerType,
            ledgerEntryId,
            event.getPreviousHash(),
            event.getCurrentHash()
        );
        
        // Perform ledger reconciliation
        if (event.isReconciliationRequired()) {
            auditService.performLedgerReconciliation(
                ledgerType,
                event.getReconciliationPeriod(),
                event.getReconciliationRules()
            );
        }
    }

    private void storeImmutableRecord(AuditEvent event) {
        // Create immutable audit record
        Map<String, Object> immutableRecord = auditService.createImmutableRecord(
            event.getAuditId(),
            event.getAuditData(),
            event.getTimestamp()
        );
        
        // Generate cryptographic hash
        String cryptoHash = generateCryptographicHash(immutableRecord);
        
        // Store in immutable store
        immutableStoreService.storeRecord(
            event.getAuditId(),
            immutableRecord,
            cryptoHash
        );
    }

    private String generateAuditFingerprint(AuditEvent event) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String data = String.format("%s|%s|%s|%s|%s",
                    event.getUserId(),
                    event.getAction(),
                    event.getResourceId(),
                    event.getTimestamp(),
                    event.getAuditDetails());
            
            byte[] hash = digest.digest(data.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("Failed to generate audit fingerprint: {}", e.getMessage());
            return "FINGERPRINT_ERROR_" + System.currentTimeMillis();
        }
    }

    private String generateCryptographicHash(Map<String, Object> record) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String recordString = record.toString();
            byte[] hash = digest.digest(recordString.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("Failed to generate cryptographic hash: {}", e.getMessage());
            return "HASH_ERROR_" + System.currentTimeMillis();
        }
    }

    private void updateAuditMetrics(AuditEvent event) {
        // Update audit processing metrics
        auditService.updateAuditMetrics(
            event.getAuditType(),
            event.getEventType(),
            event.getProcessingTime(),
            event.getDataSize()
        );
    }
}