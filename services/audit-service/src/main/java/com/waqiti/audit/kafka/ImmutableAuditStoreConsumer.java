package com.waqiti.audit.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.audit.service.ImmutableAuditService;
import com.waqiti.audit.service.AuditIntegrityService;
import com.waqiti.audit.service.ComplianceAuditService;
import com.waqiti.audit.service.AuditAlertService;
import com.waqiti.audit.domain.ImmutableAuditRecord;
import com.waqiti.audit.domain.AuditIntegrityCheck;
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
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/**
 * CRITICAL AUDIT: Consumes immutable audit store events
 * 
 * This consumer processes audit records that must be stored in an immutable
 * format for regulatory compliance and forensic analysis. These records
 * provide tamper-proof audit trails for critical system events.
 * 
 * Events processed:
 * - immutable-audit-store: Critical audit events requiring immutable storage
 * 
 * Compliance Impact: CRITICAL - Regulatory audit trail integrity
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ImmutableAuditStoreConsumer {

    private final ImmutableAuditService immutableAuditService;
    private final AuditIntegrityService auditIntegrityService;
    private final ComplianceAuditService complianceAuditService;
    private final AuditAlertService auditAlertService;
    private final ObjectMapper objectMapper;

    /**
     * Process immutable audit store events
     */
    @KafkaListener(topics = "immutable-audit-store", groupId = "audit-immutable-store-group")
    public void handleImmutableAuditStore(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        
        try {
            log.info("AUDIT: Processing immutable audit store - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
            
            Map<String, Object> auditEvent = objectMapper.readValue(message, Map.class);
            
            if (auditEvent == null || auditEvent.isEmpty()) {
                log.error("AUDIT: Invalid immutable audit event - empty or null");
                createAuditAlert("INVALID_AUDIT_EVENT", "Empty or null audit event received", "HIGH");
                return;
            }
            
            // Create immutable audit record
            ImmutableAuditRecord auditRecord = createImmutableRecord(auditEvent, partition, offset);
            
            // Validate audit record integrity
            if (!validateAuditRecord(auditRecord)) {
                log.error("AUDIT: Audit record failed integrity validation");
                createAuditAlert("AUDIT_INTEGRITY_FAILURE", 
                    "Audit record failed integrity validation", "CRITICAL");
                return;
            }
            
            // Store in immutable storage
            storeImmutableRecord(auditRecord);
            
            // Create integrity chain
            createIntegrityChain(auditRecord);
            
            // Update compliance audit trail
            updateComplianceAuditTrail(auditRecord);
            
            // Perform audit integrity checks
            performIntegrityChecks(auditRecord);
            
            // Update audit metrics
            updateAuditMetrics(auditRecord);
            
            log.info("AUDIT: Successfully stored immutable audit record: {}", auditRecord.getRecordId());
            
        } catch (Exception e) {
            log.error("AUDIT: CRITICAL - Failed to process immutable audit store", e);
            
            // Create critical audit alert
            createAuditAlert("IMMUTABLE_STORE_FAILURE", 
                "Critical failure in immutable audit storage: " + e.getMessage(), "CRITICAL");
            
            // Re-throw to trigger Kafka retry
            throw new RuntimeException("AUDIT FAILURE: Immutable store processing failed", e);
        }
    }
    
    /**
     * Create immutable audit record
     */
    private ImmutableAuditRecord createImmutableRecord(Map<String, Object> auditEvent, 
                                                      int partition, long offset) {
        try {
            // Extract audit event details
            String eventType = (String) auditEvent.get("eventType");
            String userId = (String) auditEvent.get("userId");
            String entityId = (String) auditEvent.get("entityId");
            String action = (String) auditEvent.get("action");
            LocalDateTime timestamp = auditEvent.containsKey("timestamp") ? 
                LocalDateTime.parse((String) auditEvent.get("timestamp")) : LocalDateTime.now();
            
            // Create hash of the audit event for integrity
            String eventHash = calculateEventHash(auditEvent);
            
            // Get previous record hash for chaining
            String previousHash = immutableAuditService.getLastRecordHash();
            
            ImmutableAuditRecord record = ImmutableAuditRecord.builder()
                .recordId(UUID.randomUUID().toString())
                .eventType(eventType)
                .userId(userId)
                .entityId(entityId)
                .action(action)
                .timestamp(timestamp)
                .eventData(auditEvent)
                .eventHash(eventHash)
                .previousHash(previousHash)
                .kafkaPartition(partition)
                .kafkaOffset(offset)
                .source("KAFKA_IMMUTABLE_STORE")
                .integrity("VERIFIED")
                .retentionPeriod(calculateRetentionPeriod(eventType))
                .complianceLevel(determineComplianceLevel(eventType))
                .encrypted(shouldEncrypt(eventType))
                .build();
            
            // Calculate record hash including chain
            String recordHash = calculateRecordHash(record);
            record.setRecordHash(recordHash);
            
            return record;
            
        } catch (Exception e) {
            log.error("AUDIT: Failed to create immutable audit record", e);
            throw new RuntimeException("Failed to create audit record", e);
        }
    }
    
    /**
     * Validate audit record integrity
     */
    private boolean validateAuditRecord(ImmutableAuditRecord record) {
        try {
            // Validate required fields
            if (record.getEventType() == null || record.getEventHash() == null || 
                record.getTimestamp() == null) {
                log.error("AUDIT: Missing required fields in audit record");
                return false;
            }
            
            // Validate hash integrity
            String recalculatedHash = calculateEventHash(record.getEventData());
            if (!recalculatedHash.equals(record.getEventHash())) {
                log.error("AUDIT: Event hash mismatch - possible tampering detected");
                return false;
            }
            
            // Validate timestamp (not in future, not too old)
            LocalDateTime now = LocalDateTime.now();
            if (record.getTimestamp().isAfter(now.plusMinutes(5))) {
                log.error("AUDIT: Audit timestamp is in the future");
                return false;
            }
            
            if (record.getTimestamp().isBefore(now.minusDays(30))) {
                log.warn("AUDIT: Audit timestamp is very old - possible replay attack");
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("AUDIT: Error validating audit record", e);
            return false;
        }
    }
    
    /**
     * Store record in immutable storage
     */
    private void storeImmutableRecord(ImmutableAuditRecord record) {
        try {
            // Store in primary immutable storage
            immutableAuditService.storeRecord(record);
            
            // Create backup in secondary storage
            immutableAuditService.createBackup(record);
            
            // Store in compliance archive if required
            if (isComplianceRequired(record.getEventType())) {
                complianceAuditService.archiveRecord(record);
            }
            
            log.debug("AUDIT: Immutable record stored successfully: {}", record.getRecordId());
            
        } catch (Exception e) {
            log.error("AUDIT: Failed to store immutable record", e);
            throw e;
        }
    }
    
    /**
     * Create integrity chain linking records
     */
    private void createIntegrityChain(ImmutableAuditRecord record) {
        try {
            auditIntegrityService.addToChain(record);
            
            // Verify chain integrity
            boolean chainValid = auditIntegrityService.verifyChainIntegrity();
            if (!chainValid) {
                log.error("AUDIT: CRITICAL - Audit chain integrity compromised");
                createAuditAlert("CHAIN_INTEGRITY_FAILURE", 
                    "Audit chain integrity verification failed", "CRITICAL");
            }
            
        } catch (Exception e) {
            log.error("AUDIT: Failed to create integrity chain", e);
            throw e;
        }
    }
    
    /**
     * Update compliance audit trail
     */
    private void updateComplianceAuditTrail(ImmutableAuditRecord record) {
        try {
            complianceAuditService.updateTrail(record);
            
            // Check if compliance reporting is triggered
            if (triggersComplianceReporting(record)) {
                complianceAuditService.triggerComplianceReport(record);
            }
            
        } catch (Exception e) {
            log.error("AUDIT: Failed to update compliance audit trail", e);
        }
    }
    
    /**
     * Perform integrity checks
     */
    private void performIntegrityChecks(ImmutableAuditRecord record) {
        try {
            // Schedule integrity verification
            AuditIntegrityCheck integrityCheck = AuditIntegrityCheck.builder()
                .checkId(UUID.randomUUID().toString())
                .recordId(record.getRecordId())
                .checkType("HASH_VERIFICATION")
                .scheduledAt(LocalDateTime.now().plusMinutes(5)) // Verify after 5 minutes
                .status("SCHEDULED")
                .build();
            
            auditIntegrityService.scheduleIntegrityCheck(integrityCheck);
            
        } catch (Exception e) {
            log.error("AUDIT: Failed to schedule integrity checks", e);
        }
    }
    
    /**
     * Update audit metrics
     */
    private void updateAuditMetrics(ImmutableAuditRecord record) {
        try {
            immutableAuditService.updateMetrics(record);
            
        } catch (Exception e) {
            log.error("AUDIT: Failed to update audit metrics", e);
        }
    }
    
    /**
     * Calculate hash of audit event
     */
    private String calculateEventHash(Map<String, Object> eventData) {
        try {
            String eventString = objectMapper.writeValueAsString(eventData);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(eventString.getBytes(StandardCharsets.UTF_8));
            
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
            log.error("AUDIT: Failed to calculate event hash", e);
            throw new RuntimeException("Hash calculation failed", e);
        }
    }
    
    /**
     * Calculate hash of complete record for chaining
     */
    private String calculateRecordHash(ImmutableAuditRecord record) {
        try {
            String recordData = String.format("%s|%s|%s|%s|%s", 
                record.getRecordId(), record.getEventHash(), record.getPreviousHash(),
                record.getTimestamp(), record.getEventType());
                
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(recordData.getBytes(StandardCharsets.UTF_8));
            
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
            log.error("AUDIT: Failed to calculate record hash", e);
            throw new RuntimeException("Record hash calculation failed", e);
        }
    }
    
    /**
     * Calculate retention period based on event type
     */
    private String calculateRetentionPeriod(String eventType) {
        return switch (eventType) {
            case "FINANCIAL_TRANSACTION" -> "7_YEARS";
            case "USER_AUTHENTICATION" -> "3_YEARS";
            case "SYSTEM_CONFIGURATION" -> "5_YEARS";
            case "SECURITY_EVENT" -> "10_YEARS";
            default -> "5_YEARS";
        };
    }
    
    /**
     * Determine compliance level
     */
    private String determineComplianceLevel(String eventType) {
        return switch (eventType) {
            case "FINANCIAL_TRANSACTION", "FRAUD_DETECTION" -> "SOX_PCI_DSS";
            case "USER_DATA_ACCESS" -> "GDPR_CCPA";
            case "SECURITY_EVENT" -> "SOC2_ISO27001";
            default -> "STANDARD";
        };
    }
    
    /**
     * Check if event should be encrypted
     */
    private boolean shouldEncrypt(String eventType) {
        return eventType.contains("FINANCIAL") || 
               eventType.contains("PERSONAL") || 
               eventType.contains("SECURITY");
    }
    
    /**
     * Check if compliance reporting is required
     */
    private boolean isComplianceRequired(String eventType) {
        return eventType.contains("FINANCIAL") || 
               eventType.contains("FRAUD") || 
               eventType.contains("REGULATORY");
    }
    
    /**
     * Check if event triggers compliance reporting
     */
    private boolean triggersComplianceReporting(ImmutableAuditRecord record) {
        return record.getComplianceLevel().contains("SOX") || 
               record.getEventType().contains("CRITICAL");
    }
    
    /**
     * Create audit alert
     */
    private void createAuditAlert(String alertType, String message, String severity) {
        try {
            auditAlertService.createAlert(alertType, message, severity);
        } catch (Exception e) {
            log.error("AUDIT: Failed to create audit alert", e);
        }
    }
}