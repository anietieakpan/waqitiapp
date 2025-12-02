package com.waqiti.audit.service;

import com.waqiti.audit.domain.AuditLog;
import com.waqiti.audit.domain.AuditLogEntry;
import com.waqiti.audit.repository.AuditLogRepository;
import com.waqiti.audit.security.AuditLogEncryptionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Audit Log Service
 * Handles audit log operations with encryption and tamper-evident hash chains
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final AuditLogEncryptionService encryptionService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void recordSecurityAudit(UUID auditId, UUID userId, String auditEventType, String action,
                                   String resourceType, String resourceId, String performedBy,
                                   String ipAddress, String userAgent, String sessionId,
                                   LocalDateTime eventTimestamp, String severity,
                                   Map<String, Object> auditData) {
        log.info("Recording security audit - AuditId: {}, Type: {}, Action: {}", auditId, auditEventType, action);

        // Create AuditLogEntry for encryption
        AuditLogEntry entry = buildAuditLogEntry(auditId, userId, auditEventType, resourceType, resourceId,
                action, eventTimestamp, buildSecurityMetadata(performedBy, ipAddress, userAgent, sessionId, severity, auditData));

        // Encrypt and chain
        AuditLogEntry encryptedEntry = encryptionService.encryptAndChain(entry);

        // Convert to AuditLog for persistence
        AuditLog auditLog = convertToAuditLog(encryptedEntry, "SECURITY", severity, ipAddress, userAgent, sessionId);
        auditLogRepository.save(auditLog);
    }

    @Transactional
    public void recordComplianceAudit(UUID auditId, UUID userId, String auditEventType, String action,
                                     String resourceType, String resourceId, String performedBy,
                                     LocalDateTime eventTimestamp, String severity,
                                     Map<String, Object> auditData) {
        log.info("Recording compliance audit - AuditId: {}, Type: {}, Action: {}", auditId, auditEventType, action);

        AuditLogEntry entry = buildAuditLogEntry(auditId, userId, auditEventType, resourceType, resourceId,
                action, eventTimestamp, buildComplianceMetadata(performedBy, severity, auditData));

        AuditLogEntry encryptedEntry = encryptionService.encryptAndChain(entry);
        AuditLog auditLog = convertToAuditLog(encryptedEntry, "COMPLIANCE", severity, null, null, null);
        auditLogRepository.save(auditLog);
    }

    @Transactional
    public void recordFinancialAudit(UUID auditId, UUID userId, String auditEventType, String action,
                                    String resourceType, String resourceId, String performedBy,
                                    LocalDateTime eventTimestamp, Map<String, Object> auditData) {
        log.info("Recording financial audit - AuditId: {}, Type: {}, Action: {}", auditId, auditEventType, action);

        AuditLogEntry entry = buildAuditLogEntry(auditId, userId, auditEventType, resourceType, resourceId,
                action, eventTimestamp, buildFinancialMetadata(performedBy, auditData));

        AuditLogEntry encryptedEntry = encryptionService.encryptAndChain(entry);
        AuditLog auditLog = convertToAuditLog(encryptedEntry, "FINANCIAL", "HIGH", null, null, null);
        auditLog.setInvolvesFinancialData(true);
        auditLogRepository.save(auditLog);
    }

    @Transactional
    public void recordDataAccessAudit(UUID auditId, UUID userId, String auditEventType, String action,
                                     String resourceType, String resourceId, String performedBy,
                                     String ipAddress, LocalDateTime eventTimestamp, Boolean isSensitive,
                                     Map<String, Object> auditData) {
        log.info("Recording data access audit - AuditId: {}, Type: {}, Sensitive: {}", auditId, auditEventType, isSensitive);

        AuditLogEntry entry = buildAuditLogEntry(auditId, userId, auditEventType, resourceType, resourceId,
                action, eventTimestamp, buildDataAccessMetadata(performedBy, ipAddress, isSensitive, auditData));

        AuditLogEntry encryptedEntry = encryptionService.encryptAndChain(entry);
        AuditLog auditLog = convertToAuditLog(encryptedEntry, "DATA_ACCESS", isSensitive ? "HIGH" : "MEDIUM",
                ipAddress, null, null);
        auditLog.setIsSensitive(isSensitive);
        auditLogRepository.save(auditLog);
    }

    @Transactional
    public void recordSystemAudit(UUID auditId, String auditEventType, String action, String resourceType,
                                 String resourceId, String performedBy, LocalDateTime eventTimestamp,
                                 Map<String, Object> auditData) {
        log.info("Recording system audit - AuditId: {}, Type: {}, Action: {}", auditId, auditEventType, action);

        AuditLogEntry entry = buildAuditLogEntry(auditId, null, auditEventType, resourceType, resourceId,
                action, eventTimestamp, buildSystemMetadata(performedBy, auditData));

        AuditLogEntry encryptedEntry = encryptionService.encryptAndChain(entry);
        AuditLog auditLog = convertToAuditLog(encryptedEntry, "SYSTEM", "MEDIUM", null, null, null);
        auditLogRepository.save(auditLog);
    }

    @Transactional
    public void recordUserActivityAudit(UUID auditId, UUID userId, String auditEventType, String action,
                                       String resourceType, String ipAddress, String userAgent, String sessionId,
                                       LocalDateTime eventTimestamp, Map<String, Object> auditData) {
        log.info("Recording user activity audit - AuditId: {}, UserId: {}, Type: {}", auditId, userId, auditEventType);

        AuditLogEntry entry = buildAuditLogEntry(auditId, userId, auditEventType, resourceType, null,
                action, eventTimestamp, buildUserActivityMetadata(ipAddress, userAgent, sessionId, auditData));

        AuditLogEntry encryptedEntry = encryptionService.encryptAndChain(entry);
        AuditLog auditLog = convertToAuditLog(encryptedEntry, "USER_ACTIVITY", "LOW",
                ipAddress, userAgent, sessionId);
        auditLogRepository.save(auditLog);
    }

    @Transactional
    public void recordConfigurationAudit(UUID auditId, UUID userId, String auditEventType, String action,
                                        String resourceType, String resourceId, String performedBy,
                                        LocalDateTime eventTimestamp, Map<String, Object> beforeState,
                                        Map<String, Object> afterState, Map<String, Object> auditData) {
        log.info("Recording configuration audit - AuditId: {}, Type: {}, Action: {}", auditId, auditEventType, action);

        AuditLogEntry entry = buildAuditLogEntry(auditId, userId, auditEventType, resourceType, resourceId,
                action, eventTimestamp, buildConfigurationMetadata(performedBy, beforeState, afterState, auditData));

        AuditLogEntry encryptedEntry = encryptionService.encryptAndChain(entry);
        AuditLog auditLog = convertToAuditLog(encryptedEntry, "CONFIGURATION_CHANGE", "MEDIUM", null, null, null);

        try {
            if (beforeState != null) {
                auditLog.setBeforeState(objectMapper.writeValueAsString(beforeState));
            }
            if (afterState != null) {
                auditLog.setAfterState(objectMapper.writeValueAsString(afterState));
            }
        } catch (Exception e) {
            log.error("Failed to serialize state data", e);
        }

        auditLogRepository.save(auditLog);
    }

    @Transactional
    public void recordGenericAudit(UUID auditId, UUID userId, String auditEventType, String auditCategory) {
        log.info("Recording generic audit - AuditId: {}, Type: {}, Category: {}", auditId, auditEventType, auditCategory);

        AuditLogEntry entry = buildAuditLogEntry(auditId, userId, auditEventType, auditCategory, null,
                "GENERIC", LocalDateTime.now(), Map.of());

        AuditLogEntry encryptedEntry = encryptionService.encryptAndChain(entry);
        AuditLog auditLog = convertToAuditLog(encryptedEntry, auditCategory, "LOW", null, null, null);
        auditLogRepository.save(auditLog);
    }

    @Transactional
    public void recordSensitiveAudit(UUID auditId, UUID userId, String auditEventType, String resourceType,
                                    String resourceId, String performedBy, LocalDateTime eventTimestamp) {
        log.info("Recording sensitive audit - AuditId: {}, Type: {}", auditId, auditEventType);

        AuditLogEntry entry = buildAuditLogEntry(auditId, userId, auditEventType, resourceType, resourceId,
                "SENSITIVE_ACCESS", eventTimestamp, Map.of("performedBy", performedBy, "sensitive", true));

        AuditLogEntry encryptedEntry = encryptionService.encryptAndChain(entry);
        AuditLog auditLog = convertToAuditLog(encryptedEntry, "DATA_ACCESS", "HIGH", null, null, null);
        auditLog.setIsSensitive(true);
        auditLog.setInvolvesPII(true);
        auditLogRepository.save(auditLog);
    }

    public void updateAuditMetrics(String auditCategory, String auditEventType, String severity) {
        log.debug("Updating audit metrics - Category: {}, Type: {}, Severity: {}",
                auditCategory, auditEventType, severity);
        // Metrics update logic
    }

    @Transactional
    public void handleAuditFailure(UUID auditId, UUID userId, String auditEventType, String errorMessage) {
        log.error("Handling audit failure - AuditId: {}, Type: {}, Error: {}", auditId, auditEventType, errorMessage);

        AuditLog auditLog = AuditLog.builder()
                .auditId(UUID.randomUUID())
                .eventType("AUDIT_FAILURE")
                .entityType("AUDIT_LOG")
                .entityId(auditId.toString())
                .userId(userId != null ? userId.toString() : null)
                .action("FAILURE")
                .timestamp(LocalDateTime.now())
                .errorMessage(errorMessage)
                .status("FAILED")
                .build();

        auditLogRepository.save(auditLog);
    }

    @Transactional
    public void markForManualReview(UUID auditId, UUID userId, String auditEventType, String reason) {
        log.warn("Marking audit for manual review - AuditId: {}, Reason: {}", auditId, reason);

        AuditLog auditLog = AuditLog.builder()
                .auditId(UUID.randomUUID())
                .eventType("MANUAL_REVIEW_REQUIRED")
                .entityType("AUDIT_LOG")
                .entityId(auditId != null ? auditId.toString() : null)
                .userId(userId != null ? userId.toString() : null)
                .action("REVIEW")
                .timestamp(LocalDateTime.now())
                .businessContext(reason)
                .status("PENDING_REVIEW")
                .build();

        auditLogRepository.save(auditLog);
    }

    @Transactional
    public void setArchived(UUID auditId, boolean archived) {
        auditLogRepository.findById(auditId).ifPresent(entry -> {
            entry.setIsArchived(archived);
            auditLogRepository.save(entry);
        });
    }

    @Transactional
    public void setArchivedAt(UUID auditId, LocalDateTime archivedAt) {
        auditLogRepository.findById(auditId).ifPresent(entry -> {
            entry.setArchivedAt(archivedAt);
            auditLogRepository.save(entry);
        });
    }

    // Helper methods
    private AuditLogEntry buildAuditLogEntry(UUID auditId, UUID userId, String eventType, String entityType,
                                            String entityId, String action, LocalDateTime timestamp,
                                            Map<String, Object> metadata) {
        return AuditLogEntry.builder()
                .id(auditId)
                .eventType(eventType)
                .entityType(entityType)
                .entityId(entityId)
                .userId(userId != null ? userId.toString() : null)
                .action(action)
                .timestamp(timestamp)
                .metadata(metadata)
                .build();
    }

    private AuditLog convertToAuditLog(AuditLogEntry entry, String category, String severity,
                                      String ipAddress, String userAgent, String sessionId) {
        return AuditLog.builder()
                .auditId(entry.getId())
                .eventType(entry.getEventType())
                .entityType(entry.getEntityType())
                .entityId(entry.getEntityId())
                .userId(entry.getUserId())
                .action(entry.getAction())
                .timestamp(entry.getTimestamp())
                .eventCategory(category)
                .severity(severity)
                .sourceIpAddress(ipAddress)
                .userAgent(userAgent)
                .sessionId(sessionId)
                .integrityHash(entry.getCurrentHash())
                .previousEventHash(entry.getPreviousHash())
                .build();
    }

    private Map<String, Object> buildSecurityMetadata(String performedBy, String ipAddress,
                                                     String userAgent, String sessionId,
                                                     String severity, Map<String, Object> auditData) {
        Map<String, Object> metadata = new java.util.HashMap<>(auditData != null ? auditData : Map.of());
        metadata.put("performedBy", performedBy);
        metadata.put("ipAddress", ipAddress);
        metadata.put("userAgent", userAgent);
        metadata.put("sessionId", sessionId);
        metadata.put("severity", severity);
        return metadata;
    }

    private Map<String, Object> buildComplianceMetadata(String performedBy, String severity,
                                                       Map<String, Object> auditData) {
        Map<String, Object> metadata = new java.util.HashMap<>(auditData != null ? auditData : Map.of());
        metadata.put("performedBy", performedBy);
        metadata.put("severity", severity);
        return metadata;
    }

    private Map<String, Object> buildFinancialMetadata(String performedBy, Map<String, Object> auditData) {
        Map<String, Object> metadata = new java.util.HashMap<>(auditData != null ? auditData : Map.of());
        metadata.put("performedBy", performedBy);
        return metadata;
    }

    private Map<String, Object> buildDataAccessMetadata(String performedBy, String ipAddress,
                                                       Boolean isSensitive, Map<String, Object> auditData) {
        Map<String, Object> metadata = new java.util.HashMap<>(auditData != null ? auditData : Map.of());
        metadata.put("performedBy", performedBy);
        metadata.put("ipAddress", ipAddress);
        metadata.put("sensitive", isSensitive);
        return metadata;
    }

    private Map<String, Object> buildSystemMetadata(String performedBy, Map<String, Object> auditData) {
        Map<String, Object> metadata = new java.util.HashMap<>(auditData != null ? auditData : Map.of());
        metadata.put("performedBy", performedBy);
        return metadata;
    }

    private Map<String, Object> buildUserActivityMetadata(String ipAddress, String userAgent,
                                                         String sessionId, Map<String, Object> auditData) {
        Map<String, Object> metadata = new java.util.HashMap<>(auditData != null ? auditData : Map.of());
        metadata.put("ipAddress", ipAddress);
        metadata.put("userAgent", userAgent);
        metadata.put("sessionId", sessionId);
        return metadata;
    }

    private Map<String, Object> buildConfigurationMetadata(String performedBy, Map<String, Object> beforeState,
                                                          Map<String, Object> afterState,
                                                          Map<String, Object> auditData) {
        Map<String, Object> metadata = new java.util.HashMap<>(auditData != null ? auditData : Map.of());
        metadata.put("performedBy", performedBy);
        metadata.put("beforeState", beforeState);
        metadata.put("afterState", afterState);
        return metadata;
    }
}
