package com.waqiti.compliance.events.consumers;

import com.waqiti.common.audit.AuditLogger;
import com.waqiti.common.events.compliance.ComplianceAuditEvent;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.compliance.domain.ComplianceAuditRecord;
import com.waqiti.compliance.domain.ComplianceRisk;
import com.waqiti.compliance.domain.RegulatoryAction;
import com.waqiti.compliance.repository.ComplianceAuditRepository;
import com.waqiti.compliance.service.RegulatoryReportingService;
import com.waqiti.compliance.service.ComplianceAnalyticsService;
import com.waqiti.compliance.service.ComplianceNotificationService;
import com.waqiti.common.exceptions.ComplianceProcessingException;
import com.waqiti.common.security.encryption.ComplianceDataEncryption;

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
import java.util.Map;
import java.util.UUID;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/**
 * Production-grade consumer for compliance audit trail events.
 * Maintains immutable compliance audit records with:
 * - Tamper-proof audit chain
 * - Regulatory compliance tracking
 * - Real-time risk assessment
 * - Automated regulatory reporting
 * - Cryptographic integrity verification
 * 
 * Critical for regulatory compliance and legal protection.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplianceAuditTrailConsumer {

    private final ComplianceAuditRepository auditRepository;
    private final RegulatoryReportingService reportingService;
    private final ComplianceAnalyticsService analyticsService;
    private final ComplianceNotificationService notificationService;
    private final AuditLogger auditLogger;
    private final MetricsService metricsService;
    private final ComplianceDataEncryption dataEncryption;

    @KafkaListener(
        topics = "compliance-audit-trail",
        groupId = "compliance-service-audit-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 30000),
        include = {ComplianceProcessingException.class}
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public void handleComplianceAuditEvent(
            @Payload ComplianceAuditEvent auditEvent,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "correlation-id", required = false) String correlationId,
            @Header(value = "regulatory-jurisdiction", required = false) String jurisdiction,
            Acknowledgment acknowledgment) {

        String eventId = auditEvent.getEventId() != null ? 
            auditEvent.getEventId() : UUID.randomUUID().toString();

        try {
            log.info("Processing compliance audit event: {} for entity: {} with action: {}", 
                    eventId, auditEvent.getEntityId(), auditEvent.getAction());

            // Metrics tracking
            metricsService.incrementCounter("compliance.audit.processing.started",
                Map.of(
                    "action", auditEvent.getAction(),
                    "risk_level", auditEvent.getRiskLevel() != null ? auditEvent.getRiskLevel() : "unknown"
                ));

            // Idempotency check with cryptographic verification
            if (isAuditEventProcessed(eventId, auditEvent)) {
                log.info("Audit event {} already processed with matching hash", eventId);
                acknowledgment.acknowledge();
                return;
            }

            // Create immutable audit record
            ComplianceAuditRecord auditRecord = createAuditRecord(auditEvent, eventId, correlationId, jurisdiction);

            // Validate audit chain integrity
            validateAuditChainIntegrity(auditRecord);

            // Encrypt sensitive data
            encryptSensitiveData(auditRecord);

            // Save audit record with chain verification
            ComplianceAuditRecord savedRecord = auditRepository.save(auditRecord);

            // Assess compliance risk
            assessComplianceRisk(savedRecord, auditEvent);

            // Generate regulatory reports if required
            generateRegulatoryReports(savedRecord, auditEvent);

            // Send compliance notifications
            sendComplianceNotifications(savedRecord, auditEvent);

            // Update compliance analytics
            updateComplianceAnalytics(savedRecord, auditEvent);

            // Create system audit log
            createSystemAuditLog(savedRecord, auditEvent, correlationId);

            // Success metrics
            metricsService.incrementCounter("compliance.audit.processing.success",
                Map.of(
                    "action", auditEvent.getAction(),
                    "jurisdiction", jurisdiction != null ? jurisdiction : "default"
                ));

            log.info("Successfully processed compliance audit record: {} for entity: {}", 
                    savedRecord.getId(), auditEvent.getEntityId());

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error processing compliance audit event {}: {}", eventId, e.getMessage(), e);
            metricsService.incrementCounter("compliance.audit.processing.error",
                Map.of("error_type", e.getClass().getSimpleName()));
            
            // Create error audit log
            auditLogger.logError("COMPLIANCE_AUDIT_PROCESSING_ERROR", 
                "system", eventId, e.getMessage(),
                Map.of(
                    "entityId", auditEvent.getEntityId(),
                    "action", auditEvent.getAction(),
                    "correlationId", correlationId
                ));
            
            throw new ComplianceProcessingException("Failed to process compliance audit: " + e.getMessage(), e);
        }
    }

    /**
     * Dead letter queue handler for failed compliance audits
     * CRITICAL: Compliance audit failures require immediate manual intervention
     */
    @KafkaListener(
        topics = "compliance-audit-trail-dlt",
        groupId = "compliance-service-audit-dlt-processor"
    )
    @Transactional
    public void handleComplianceAuditDLT(
            @Payload ComplianceAuditEvent auditEvent,
            @Header(value = "correlation-id", required = false) String correlationId,
            Acknowledgment acknowledgment) {
        
        log.error("CRITICAL: Compliance audit event sent to DLT: {} for entity: {}", 
                auditEvent.getEventId(), auditEvent.getEntityId());

        try {
            // Log critical compliance failure
            auditLogger.logCriticalAlert("COMPLIANCE_AUDIT_DLT",
                "Critical compliance audit processing failure",
                Map.of(
                    "entityId", auditEvent.getEntityId(),
                    "action", auditEvent.getAction(),
                    "riskLevel", auditEvent.getRiskLevel(),
                    "correlationId", correlationId,
                    "regulatoryImplications", "HIGH_RISK"
                ));

            // Notify compliance team immediately
            notificationService.sendCriticalComplianceAlert(
                "COMPLIANCE AUDIT DLT FAILURE",
                String.format("Critical compliance audit failed for entity %s, action %s", 
                    auditEvent.getEntityId(), auditEvent.getAction()),
                auditEvent
            );

            // Create manual review record
            createManualReviewRecord(auditEvent, correlationId);

            // Metrics for DLT events
            metricsService.incrementCounter("compliance.audit.dlt.received",
                Map.of("action", auditEvent.getAction()));

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to handle compliance audit DLT: {}", e.getMessage(), e);
            acknowledgment.acknowledge(); // Acknowledge to prevent infinite loop
        }
    }

    private boolean isAuditEventProcessed(String eventId, ComplianceAuditEvent event) {
        // Check by event ID and cryptographic hash to ensure data integrity
        String eventHash = calculateEventHash(event);
        return auditRepository.existsByEventIdAndEventHash(eventId, eventHash);
    }

    private ComplianceAuditRecord createAuditRecord(ComplianceAuditEvent event, String eventId, 
                                                   String correlationId, String jurisdiction) {
        // Get previous audit record for chain verification
        ComplianceAuditRecord previousRecord = auditRepository.findLatestByEntityId(event.getEntityId());
        
        return ComplianceAuditRecord.builder()
            .id(UUID.randomUUID().toString())
            .eventId(eventId)
            .entityId(event.getEntityId())
            .entityType(event.getEntityType())
            .action(event.getAction())
            .actionDescription(event.getActionDescription())
            .userId(event.getUserId())
            .userRole(event.getUserRole())
            .timestamp(event.getTimestamp() != null ? event.getTimestamp() : LocalDateTime.now())
            .riskLevel(event.getRiskLevel())
            .complianceFlags(event.getComplianceFlags())
            .regulatoryReferences(event.getRegulatoryReferences())
            .jurisdiction(jurisdiction != null ? jurisdiction : "DEFAULT")
            .beforeState(event.getBeforeState())
            .afterState(event.getAfterState())
            .metadata(event.getMetadata())
            .correlationId(correlationId)
            .ipAddress(event.getIpAddress())
            .userAgent(event.getUserAgent())
            .sessionId(event.getSessionId())
            .previousRecordHash(previousRecord != null ? previousRecord.getRecordHash() : null)
            .chainSequence(previousRecord != null ? previousRecord.getChainSequence() + 1 : 1L)
            .eventHash(calculateEventHash(event))
            .recordHash(null) // Will be calculated after all fields are set
            .isEncrypted(false) // Will be set to true after encryption
            .retentionPeriod(calculateRetentionPeriod(event))
            .requiresRegulatorReporting(requiresRegulatoryReporting(event))
            .processingStatus("PROCESSED")
            .createdAt(LocalDateTime.now())
            .build();
    }

    private void validateAuditChainIntegrity(ComplianceAuditRecord record) {
        if (record.getPreviousRecordHash() != null) {
            // Verify the previous record exists and hash matches
            ComplianceAuditRecord previousRecord = auditRepository.findByRecordHash(record.getPreviousRecordHash());
            if (previousRecord == null) {
                throw new ComplianceProcessingException("Audit chain integrity violation: previous record not found");
            }
            
            // Verify sequence continuity
            if (record.getChainSequence() != previousRecord.getChainSequence() + 1) {
                throw new ComplianceProcessingException("Audit chain integrity violation: sequence break");
            }
        }
    }

    private void encryptSensitiveData(ComplianceAuditRecord record) {
        try {
            // Encrypt sensitive fields
            if (record.getBeforeState() != null) {
                record.setBeforeState(dataEncryption.encryptComplianceData(record.getBeforeState()));
            }
            
            if (record.getAfterState() != null) {
                record.setAfterState(dataEncryption.encryptComplianceData(record.getAfterState()));
            }
            
            if (record.getMetadata() != null) {
                record.setMetadata(dataEncryption.encryptMetadata(record.getMetadata()));
            }
            
            record.setIsEncrypted(true);
            
            // Calculate final record hash after encryption
            record.setRecordHash(calculateRecordHash(record));
            
        } catch (Exception e) {
            throw new ComplianceProcessingException("Failed to encrypt sensitive audit data", e);
        }
    }

    private void assessComplianceRisk(ComplianceAuditRecord record, ComplianceAuditEvent event) {
        try {
            ComplianceRisk riskAssessment = analyticsService.assessComplianceRisk(record, event);
            
            // Update record with risk assessment
            record.setRiskScore(riskAssessment.getRiskScore());
            record.setRiskFactors(riskAssessment.getRiskFactors());
            
            // High-risk events trigger immediate alerts
            if (riskAssessment.getRiskScore() > 0.8) {
                notificationService.sendHighRiskAlert(record, riskAssessment);
                
                metricsService.incrementCounter("compliance.high_risk_event",
                    Map.of(
                        "action", event.getAction(),
                        "entity_type", event.getEntityType()
                    ));
            }
            
        } catch (Exception e) {
            log.error("Risk assessment failed for audit record {}: {}", record.getId(), e.getMessage());
            // Don't fail the entire process, but flag for manual review
            record.setRequiresManualReview(true);
        }
    }

    private void generateRegulatoryReports(ComplianceAuditRecord record, ComplianceAuditEvent event) {
        if (!record.getRequiresRegulatorReporting()) {
            return;
        }
        
        try {
            // Generate required regulatory reports
            reportingService.generateComplianceReport(record, event);
            
            // Special handling for high-value transactions
            if (isHighValueTransaction(event)) {
                reportingService.generateSuspiciousActivityReport(record, event);
            }
            
            // AML reporting for financial transactions
            if (isFinancialTransaction(event)) {
                reportingService.generateAMLReport(record, event);
            }
            
        } catch (Exception e) {
            log.error("Regulatory reporting failed for audit record {}: {}", record.getId(), e.getMessage());
            // Flag for manual regulatory review
            record.setRequiresManualReview(true);
            notificationService.sendRegulatoryReportingError(record, e);
        }
    }

    private void sendComplianceNotifications(ComplianceAuditRecord record, ComplianceAuditEvent event) {
        try {
            // Notify compliance team for high-risk events
            if (record.getRiskScore() != null && record.getRiskScore() > 0.7) {
                notificationService.sendComplianceTeamAlert(record, event);
            }
            
            // Notify legal team for regulatory actions
            if (isRegulatoryAction(event)) {
                notificationService.sendLegalTeamNotification(record, event);
            }
            
            // Notify audit team for audit-related events
            if (isAuditEvent(event)) {
                notificationService.sendAuditTeamNotification(record, event);
            }
            
        } catch (Exception e) {
            log.error("Compliance notifications failed for record {}: {}", record.getId(), e.getMessage());
            // Don't fail processing for notification issues
        }
    }

    private void updateComplianceAnalytics(ComplianceAuditRecord record, ComplianceAuditEvent event) {
        try {
            analyticsService.recordComplianceEvent(record, event);
            
            // Update compliance metrics
            metricsService.recordTimer("compliance.audit.processing_time",
                System.currentTimeMillis(),
                Map.of(
                    "action", event.getAction(),
                    "entity_type", event.getEntityType(),
                    "risk_level", event.getRiskLevel()
                ));
            
            // Update risk metrics
            if (record.getRiskScore() != null) {
                metricsService.recordGauge("compliance.risk_score", record.getRiskScore(),
                    Map.of("entity_type", event.getEntityType()));
            }
            
        } catch (Exception e) {
            log.error("Analytics update failed for record {}: {}", record.getId(), e.getMessage());
        }
    }

    private void createSystemAuditLog(ComplianceAuditRecord record, ComplianceAuditEvent event, String correlationId) {
        auditLogger.logComplianceEvent(
            "COMPLIANCE_AUDIT_RECORDED",
            event.getUserId(),
            record.getId(),
            event.getAction(),
            record.getRiskScore() != null ? record.getRiskScore().doubleValue() : 0.0,
            true,
            Map.of(
                "entityId", event.getEntityId(),
                "entityType", event.getEntityType(),
                "riskLevel", event.getRiskLevel(),
                "jurisdiction", record.getJurisdiction(),
                "chainSequence", record.getChainSequence().toString(),
                "correlationId", correlationId != null ? correlationId : "N/A",
                "requiresReporting", record.getRequiresRegulatorReporting().toString()
            )
        );
    }

    private void createManualReviewRecord(ComplianceAuditEvent event, String correlationId) {
        // Create a record for manual review when automatic processing fails
        try {
            ComplianceAuditRecord manualRecord = ComplianceAuditRecord.builder()
                .id(UUID.randomUUID().toString())
                .eventId(event.getEventId())
                .entityId(event.getEntityId())
                .action(event.getAction())
                .processingStatus("MANUAL_REVIEW_REQUIRED")
                .requiresManualReview(true)
                .correlationId(correlationId)
                .createdAt(LocalDateTime.now())
                .build();
            
            auditRepository.save(manualRecord);
            
        } catch (Exception e) {
            log.error("Failed to create manual review record: {}", e.getMessage(), e);
        }
    }

    private String calculateEventHash(ComplianceAuditEvent event) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String dataToHash = event.getEntityId() + event.getAction() + 
                              (event.getTimestamp() != null ? event.getTimestamp().toString() : "") +
                              (event.getBeforeState() != null ? event.getBeforeState() : "") +
                              (event.getAfterState() != null ? event.getAfterState() : "");
            
            byte[] hashBytes = digest.digest(dataToHash.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
            
        } catch (Exception e) {
            throw new ComplianceProcessingException("Failed to calculate event hash", e);
        }
    }

    private String calculateRecordHash(ComplianceAuditRecord record) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String dataToHash = record.getId() + record.getEntityId() + record.getAction() +
                              record.getTimestamp().toString() + record.getChainSequence().toString() +
                              (record.getPreviousRecordHash() != null ? record.getPreviousRecordHash() : "");
            
            byte[] hashBytes = digest.digest(dataToHash.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
            
        } catch (Exception e) {
            throw new ComplianceProcessingException("Failed to calculate record hash", e);
        }
    }

    private Integer calculateRetentionPeriod(ComplianceAuditEvent event) {
        // Retention periods based on regulatory requirements
        return switch (event.getAction().toUpperCase()) {
            case "FINANCIAL_TRANSACTION" -> 2555; // 7 years
            case "AML_SCREENING" -> 1825; // 5 years
            case "KYC_VERIFICATION" -> 1825; // 5 years
            case "REGULATORY_FILING" -> 3650; // 10 years
            default -> 1095; // 3 years default
        };
    }

    private boolean requiresRegulatoryReporting(ComplianceAuditEvent event) {
        return event.getRiskLevel() != null && 
               (event.getRiskLevel().equals("HIGH") || event.getRiskLevel().equals("CRITICAL")) ||
               isHighValueTransaction(event) ||
               isRegulatoryAction(event);
    }

    private boolean isHighValueTransaction(ComplianceAuditEvent event) {
        return event.getMetadata() != null && 
               event.getMetadata().containsKey("amount") &&
               Double.parseDouble(event.getMetadata().get("amount").toString()) > 10000.0;
    }

    private boolean isFinancialTransaction(ComplianceAuditEvent event) {
        return event.getAction().contains("TRANSACTION") || 
               event.getAction().contains("PAYMENT") ||
               event.getAction().contains("TRANSFER");
    }

    private boolean isRegulatoryAction(ComplianceAuditEvent event) {
        return event.getAction().contains("REGULATORY") || 
               event.getAction().contains("COMPLIANCE") ||
               event.getAction().contains("AML") ||
               event.getAction().contains("KYC");
    }

    private boolean isAuditEvent(ComplianceAuditEvent event) {
        return event.getAction().contains("AUDIT") || 
               event.getAction().contains("REVIEW");
    }
}