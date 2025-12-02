package com.waqiti.ledger.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.ledger.domain.AuditLogEntry;
import com.waqiti.ledger.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Production-grade Audit Log Service
 * 
 * Provides comprehensive audit trail functionality for ledger operations
 * with cryptographic integrity, tamper detection, and compliance features.
 * 
 * Features:
 * - Immutable audit trail with hash chain verification
 * - Cryptographic HMAC for tamper detection
 * - Automatic context capture (user, IP, session, etc.)
 * - Structured change tracking with before/after states
 * - Archive management for compliance retention
 * - Performance-optimized batch operations
 * - Distributed correlation ID support
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    
    @Value("${ledger.audit.hmac.secret}")
    private String hmacSecret;
    
    @Value("${ledger.audit.chain.enabled:true}")
    private boolean hashChainEnabled;
    
    @Value("${ledger.audit.source:ledger-service}")
    private String defaultSource;
    
    private final SecureRandom secureRandom = new SecureRandom();
    
    /**
     * Create audit log entry for entity creation
     */
    @Transactional
    public void auditCreate(String entityType, String entityId, Object newState, String description) {
        log.debug("Creating audit log for {} creation: {}", entityType, entityId);
        
        try {
            AuditLogEntry entry = createBaseAuditEntry(entityType, entityId, "CREATE", description)
                .newState(serializeState(newState))
                .successful(true)
                .build();
            
            finalizeAndSaveEntry(entry);
            
        } catch (Exception e) {
            log.error("Failed to create audit log for {} creation: {}", entityType, entityId, e);
            saveFailureEntry(entityType, entityId, "CREATE", description, e);
        }
    }
    
    /**
     * Create audit log entry for entity update
     */
    @Transactional
    public void auditUpdate(String entityType, String entityId, Object previousState, 
                          Object newState, String description) {
        log.debug("Creating audit log for {} update: {}", entityType, entityId);
        
        try {
            Map<String, Object> changes = calculateChanges(previousState, newState);
            
            AuditLogEntry entry = createBaseAuditEntry(entityType, entityId, "UPDATE", description)
                .previousState(serializeState(previousState))
                .newState(serializeState(newState))
                .changes(serializeState(changes))
                .successful(true)
                .build();
            
            finalizeAndSaveEntry(entry);
            
        } catch (Exception e) {
            log.error("Failed to create audit log for {} update: {}", entityType, entityId, e);
            saveFailureEntry(entityType, entityId, "UPDATE", description, e);
        }
    }
    
    /**
     * Create audit log entry for entity deletion (archival)
     */
    @Transactional
    public void auditDelete(String entityType, String entityId, Object previousState, String description) {
        log.debug("Creating audit log for {} deletion: {}", entityType, entityId);
        
        try {
            AuditLogEntry entry = createBaseAuditEntry(entityType, entityId, "DELETE", description)
                .previousState(serializeState(previousState))
                .successful(true)
                .build();
            
            finalizeAndSaveEntry(entry);
            
        } catch (Exception e) {
            log.error("Failed to create audit log for {} deletion: {}", entityType, entityId, e);
            saveFailureEntry(entityType, entityId, "DELETE", description, e);
        }
    }
    
    /**
     * Create audit log entry for business operations (approve, reject, etc.)
     */
    @Transactional
    public void auditOperation(String entityType, String entityId, String action, 
                             Object context, String description) {
        log.debug("Creating audit log for {} {} operation: {}", entityType, action, entityId);
        
        try {
            AuditLogEntry entry = createBaseAuditEntry(entityType, entityId, action, description)
                .newState(serializeState(context))
                .successful(true)
                .build();
            
            finalizeAndSaveEntry(entry);
            
        } catch (Exception e) {
            log.error("Failed to create audit log for {} {} operation: {}", entityType, action, entityId, e);
            saveFailureEntry(entityType, entityId, action, description, e);
        }
    }
    
    /**
     * Archive audit log entry (compliance-safe deletion)
     */
    @Transactional
    public void archiveAuditLog(UUID auditId, String reason, String archivedBy) {
        log.info("Archiving audit log: {} by {} - Reason: {}", auditId, archivedBy, reason);
        
        try {
            Optional<AuditLogEntry> entryOpt = auditLogRepository.findById(auditId);
            if (entryOpt.isPresent()) {
                AuditLogEntry entry = entryOpt.get();
                
                if (entry.isArchived()) {
                    log.warn("Audit log already archived: {}", auditId);
                    return;
                }
                
                entry.setArchived(true);
                entry.setArchivedAt(LocalDateTime.now());
                entry.setArchivedReason(reason);
                entry.setArchivedBy(archivedBy);
                
                auditLogRepository.save(entry);
                
                log.info("Audit log archived successfully: {}", auditId);
                
                // Create audit log for the archival action itself
                auditOperation("AUDIT_LOG", auditId.toString(), "ARCHIVE", 
                    Map.of("reason", reason, "archivedBy", archivedBy),
                    "Audit log archived: " + reason);
                    
            } else {
                log.warn("Attempted to archive non-existent audit log: {}", auditId);
            }
            
        } catch (Exception e) {
            log.error("Failed to archive audit log: {}", auditId, e);
            throw new RuntimeException("Audit log archival failed", e);
        }
    }
    
    /**
     * Bulk archive audit logs by date range
     */
    @Transactional
    public int bulkArchiveByDateRange(LocalDateTime cutoffDate, String reason, String archivedBy) {
        log.info("Bulk archiving audit logs before {} by {} - Reason: {}", cutoffDate, archivedBy, reason);
        
        try {
            auditLogRepository.archiveByDateRange(cutoffDate, LocalDateTime.now(), reason, archivedBy);
            
            // Count archived entries for reporting
            List<AuditLogEntry> archivedEntries = auditLogRepository.findByTimestampBeforeAndArchivedTrue(cutoffDate);
            int archivedCount = archivedEntries.size();
            
            log.info("Bulk archived {} audit logs before {}", archivedCount, cutoffDate);
            
            // Create audit log for the bulk archival operation
            auditOperation("AUDIT_LOG", "BULK_ARCHIVE", "BULK_ARCHIVE", 
                Map.of("cutoffDate", cutoffDate, "archivedCount", archivedCount, 
                       "reason", reason, "archivedBy", archivedBy),
                String.format("Bulk archived %d audit logs", archivedCount));
            
            return archivedCount;
            
        } catch (Exception e) {
            log.error("Failed to bulk archive audit logs before {}", cutoffDate, e);
            throw new RuntimeException("Bulk audit log archival failed", e);
        }
    }
    
    /**
     * Verify audit trail integrity using hash chain
     */
    public boolean verifyIntegrity(LocalDateTime startTime, LocalDateTime endTime) {
        log.info("Verifying audit trail integrity from {} to {}", startTime, endTime);
        
        try {
            List<AuditLogEntry> entries = auditLogRepository
                .findByTimestampBetweenOrderByTimestamp(startTime, endTime);
                
            if (entries.isEmpty()) {
                log.info("No audit entries found in specified range");
                return true;
            }
            
            boolean isValid = true;
            String expectedPreviousHash = null;
            
            for (int i = 0; i < entries.size(); i++) {
                AuditLogEntry entry = entries.get(i);
                
                // Verify HMAC
                String expectedHmac = calculateHmac(entry);
                if (!expectedHmac.equals(entry.getHmac())) {
                    log.error("HMAC verification failed for entry: {} - Expected: {}, Found: {}", 
                             entry.getId(), expectedHmac, entry.getHmac());
                    isValid = false;
                }
                
                // Verify hash chain (except for first entry)
                if (i > 0) {
                    if (!expectedPreviousHash.equals(entry.getPreviousHash())) {
                        log.error("Hash chain broken at entry: {} - Expected previous: {}, Found: {}", 
                                 entry.getId(), expectedPreviousHash, entry.getPreviousHash());
                        isValid = false;
                    }
                }
                
                expectedPreviousHash = entry.getHash();
            }
            
            log.info("Audit trail integrity verification completed - Valid: {}", isValid);
            return isValid;
            
        } catch (Exception e) {
            log.error("Failed to verify audit trail integrity", e);
            return false;
        }
    }
    
    /**
     * Get audit trail for specific entity
     */
    public List<AuditLogEntry> getEntityAuditTrail(String entityType, String entityId) {
        log.debug("Retrieving audit trail for {} {}", entityType, entityId);
        
        try {
            return auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc(entityType, entityId);
        } catch (Exception e) {
            log.error("Failed to retrieve audit trail for {} {}", entityType, entityId, e);
            return Collections.emptyList();
        }
    }
    
    // Private helper methods
    
    private AuditLogEntry.AuditLogEntryBuilder createBaseAuditEntry(String entityType, String entityId, 
                                                                   String action, String description) {
        AuditLogEntry.AuditLogEntryBuilder builder = AuditLogEntry.builder()
            .id(UUID.randomUUID())
            .entityType(entityType)
            .entityId(entityId)
            .action(action)
            .description(description)
            .source(defaultSource)
            .timestamp(LocalDateTime.now());
            
        // Capture security context
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            builder.performedBy(auth.getName())
                   .performedByRole(auth.getAuthorities().toString());
        } else {
            builder.performedBy("SYSTEM")
                   .performedByRole("SYSTEM");
        }
        
        // Capture HTTP context if available
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                builder.ipAddress(getClientIpAddress(request))
                       .userAgent(request.getHeader("User-Agent"))
                       .sessionId(request.getSession(false) != null ? request.getSession().getId() : null);
            }
        } catch (Exception e) {
            log.debug("Failed to capture HTTP context for audit log", e);
        }
        
        // Generate correlation ID
        builder.correlationId(UUID.randomUUID().toString());
        
        return builder;
    }
    
    private void finalizeAndSaveEntry(AuditLogEntry entry) throws Exception {
        // Set hash chain
        if (hashChainEnabled) {
            String previousHash = getPreviousEntryHash();
            entry.setPreviousHash(previousHash);
            
            String entryHash = calculateHash(entry);
            entry.setHash(entryHash);
        } else {
            entry.setPreviousHash("DISABLED");
            entry.setHash(UUID.randomUUID().toString());
        }
        
        // Set HMAC for tamper detection
        String hmac = calculateHmac(entry);
        entry.setHmac(hmac);
        
        // Save entry
        auditLogRepository.save(entry);
        
        log.debug("Audit log entry saved successfully: {}", entry.getId());
    }
    
    private void saveFailureEntry(String entityType, String entityId, String action, 
                                String description, Exception error) {
        try {
            AuditLogEntry entry = createBaseAuditEntry(entityType, entityId, action, description)
                .successful(false)
                .errorMessage(error.getMessage())
                .build();
                
            finalizeAndSaveEntry(entry);
            
        } catch (Exception e) {
            log.error("Failed to save failure audit log - this is critical!", e);
        }
    }
    
    private String serializeState(Object state) {
        if (state == null) {
            return "{}"; // Return empty JSON instead of null for audit compliance
        }
        
        try {
            return objectMapper.writeValueAsString(state);
        } catch (Exception e) {
            log.error("Failed to serialize state for audit log", e);
            return "SERIALIZATION_ERROR: " + e.getMessage();
        }
    }
    
    private Map<String, Object> calculateChanges(Object previousState, Object newState) {
        Map<String, Object> changes = new HashMap<>();
        
        try {
            if (previousState == null && newState == null) {
                return changes;
            }
            
            if (previousState == null) {
                changes.put("type", "CREATION");
                changes.put("new", newState);
                return changes;
            }
            
            if (newState == null) {
                changes.put("type", "DELETION");
                changes.put("previous", previousState);
                return changes;
            }
            
            // Simple change detection - in production, use more sophisticated diff
            changes.put("type", "MODIFICATION");
            changes.put("previous", previousState);
            changes.put("new", newState);
            changes.put("modified_at", LocalDateTime.now());
            
        } catch (Exception e) {
            log.error("Failed to calculate changes for audit log", e);
            changes.put("error", "CHANGE_CALCULATION_ERROR: " + e.getMessage());
        }
        
        return changes;
    }
    
    private String getPreviousEntryHash() {
        try {
            Optional<AuditLogEntry> lastEntry = auditLogRepository.findTopByOrderByTimestampDesc();
            return lastEntry.map(AuditLogEntry::getHash).orElse("GENESIS");
        } catch (Exception e) {
            log.error("Failed to get previous entry hash", e);
            return "ERROR";
        }
    }
    
    private String calculateHash(AuditLogEntry entry) throws Exception {
        String hashInput = String.format("%s|%s|%s|%s|%s|%s|%s", 
            entry.getEntityType(),
            entry.getEntityId(),
            entry.getAction(),
            entry.getPerformedBy(),
            entry.getTimestamp(),
            entry.getPreviousHash(),
            entry.getNewState() != null ? entry.getNewState() : "");
            
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(hashInput.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hashBytes);
    }
    
    private String calculateHmac(AuditLogEntry entry) throws Exception {
        String hmacInput = String.format("%s|%s|%s|%s|%s", 
            entry.getId(),
            entry.getEntityType(),
            entry.getEntityId(),
            entry.getAction(),
            entry.getHash());
            
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        
        byte[] hmacBytes = mac.doFinal(hmacInput.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hmacBytes);
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        String[] headerNames = {
            "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", 
            "WL-Proxy-Client-IP", "HTTP_X_FORWARDED_FOR", "HTTP_X_FORWARDED", 
            "HTTP_X_CLUSTER_CLIENT_IP", "HTTP_CLIENT_IP", "HTTP_FORWARDED_FOR", "HTTP_FORWARDED"
        };
        
        for (String headerName : headerNames) {
            String ip = request.getHeader(headerName);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // Handle comma-separated IPs (take the first one)
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        }
        
        return request.getRemoteAddr();
    }
}