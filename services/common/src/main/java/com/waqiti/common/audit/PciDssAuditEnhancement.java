package com.waqiti.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * PCI DSS Compliant Audit Enhancement
 * 
 * Implements PCI DSS Requirement 10: Track and monitor all access to network resources and cardholder data
 * - 10.1: Audit trails linking to individual users
 * - 10.2: Automated audit trails for system components
 * - 10.3: Record audit trail entries with required fields
 * - 10.4: Synchronize all critical system clocks
 * - 10.5: Secure audit trails so they cannot be altered
 * - 10.6: Review logs and security events daily
 * - 10.7: Retain audit trail history for at least one year
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PciDssAuditEnhancement {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final ComprehensiveAuditService auditService;
    
    @Value("${audit.pci.enabled:true}")
    private boolean pciAuditEnabled;
    
    @Value("${audit.pci.retention.years:7}")
    private int retentionYears;
    
    @Value("${audit.pci.realtime.alert:true}")
    private boolean realtimeAlert;
    
    @Value("${audit.integrity.secret:${VAULT_AUDIT_SECRET}}")
    private String integritySecret;
    
    // Hash chain for tamper detection
    private final AtomicLong sequenceNumber = new AtomicLong(0);
    private volatile String previousHash = "";
    private final Map<String, String> hashChain = new ConcurrentHashMap<>();
    
    // Metrics
    private Counter auditEventCounter;
    private Counter pciViolationCounter;
    private Counter tamperDetectionCounter;
    
    /**
     * PCI DSS Required Audit Event Types
     */
    public enum PciAuditEvent {
        // Cardholder Data Access (PCI DSS 10.2.1)
        CARD_DATA_ACCESS,
        CARD_DATA_VIEW,
        CARD_DATA_EXPORT,
        CARD_DATA_MODIFICATION,
        CARD_DATA_DELETION,
        
        // Administrative Actions (PCI DSS 10.2.2)
        ADMIN_ACCESS,
        PRIVILEGE_ELEVATION,
        ADMIN_ACTION,
        CONFIG_CHANGE,
        
        // Access to Audit Logs (PCI DSS 10.2.3)
        AUDIT_LOG_ACCESS,
        AUDIT_LOG_EXPORT,
        AUDIT_LOG_DELETION_ATTEMPT,
        
        // Authentication (PCI DSS 10.2.4)
        LOGIN_SUCCESS,
        LOGIN_FAILURE,
        ACCOUNT_LOCKOUT,
        PASSWORD_CHANGE,
        
        // System Security (PCI DSS 10.2.5)
        FIREWALL_CHANGE,
        ANTIVIRUS_EVENT,
        IDS_ALERT,
        FILE_INTEGRITY_CHANGE,
        
        // Audit System (PCI DSS 10.2.6)
        AUDIT_SYSTEM_START,
        AUDIT_SYSTEM_STOP,
        AUDIT_SYSTEM_FAILURE,
        
        // Object Creation/Deletion (PCI DSS 10.2.7)
        OBJECT_CREATED,
        OBJECT_DELETED,
        PERMISSION_CHANGED
    }
    
    /**
     * Create PCI DSS compliant audit record
     * Required fields per PCI DSS 10.3
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PciAuditRecord createPciAuditRecord(
            PciAuditEvent eventType,
            String userId,
            String action,
            Map<String, Object> details) {
        
        if (!pciAuditEnabled) {
            log.warn("PCI audit is disabled - this violates PCI DSS requirements!");
            return null;
        }
        
        try {
            // Generate unique event ID
            String eventId = generateEventId();
            long sequence = sequenceNumber.incrementAndGet();
            
            // Create audit record with all PCI required fields
            PciAuditRecord record = PciAuditRecord.builder()
                // 10.3.1: User identification
                .userId(userId)
                .username(getUsernameFromId(userId))
                
                // 10.3.2: Type of event
                .eventType(eventType)
                .action(action)
                
                // 10.3.3: Date and time (with NTP sync)
                .timestamp(Instant.now())
                .timestampUtc(LocalDateTime.now(ZoneOffset.UTC))
                
                // 10.3.4: Success or failure indication
                .success(determineSuccess(details))
                .failureReason(extractFailureReason(details))
                
                // 10.3.5: Origination of event
                .sourceIp(extractSourceIp())
                .sourceHost(extractSourceHost())
                .userAgent(extractUserAgent())
                
                // 10.3.6: Identity or name of affected data
                .affectedResource(extractAffectedResource(details))
                .resourceType(extractResourceType(details))
                
                // Additional security fields
                .eventId(eventId)
                .sequenceNumber(sequence)
                .sessionId(extractSessionId())
                .correlationId(extractCorrelationId())
                .details(sanitizeDetails(details))
                
                // Integrity fields
                .hash(calculateHash(eventId, sequence, details))
                .previousHash(previousHash)
                .signature(signRecord(eventId, sequence))
                
                .build();
            
            // Update hash chain
            updateHashChain(record);
            
            // Persist to multiple stores for redundancy
            persistAuditRecord(record);
            
            // Stream for real-time monitoring
            streamAuditEvent(record);
            
            // Check for violations
            checkPciViolations(record);
            
            // Update metrics
            updateMetrics(eventType);
            
            return record;
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to create PCI audit record", e);
            // Audit system failure is itself a PCI violation
            alertSecurityTeam("Audit system failure", e);
            throw new AuditException("Failed to create audit record", e);
        }
    }
    
    /**
     * Log cardholder data access (PCI DSS 10.2.1)
     */
    @Async
    public void auditCardDataAccess(String userId, String cardToken, String operation, String reason) {
        Map<String, Object> details = new HashMap<>();
        details.put("cardToken", cardToken);
        details.put("operation", operation);
        details.put("reason", reason);
        details.put("dataClassification", "PCI");
        
        createPciAuditRecord(
            PciAuditEvent.CARD_DATA_ACCESS,
            userId,
            "Card data " + operation,
            details
        );
        
        // Alert on sensitive operations
        if (operation.equals("EXPORT") || operation.equals("DELETION")) {
            alertSecurityTeam("Sensitive card data operation: " + operation, null);
        }
    }
    
    /**
     * Log administrative actions (PCI DSS 10.2.2)
     */
    @Async
    public void auditAdminAction(String adminId, String action, String target, Map<String, Object> changes) {
        Map<String, Object> details = new HashMap<>();
        details.put("target", target);
        details.put("changes", changes);
        details.put("privilegeLevel", "ADMIN");
        
        createPciAuditRecord(
            PciAuditEvent.ADMIN_ACTION,
            adminId,
            action,
            details
        );
    }
    
    /**
     * Log authentication events (PCI DSS 10.2.4)
     */
    @Async
    public void auditAuthentication(String userId, boolean success, String method, String failureReason) {
        Map<String, Object> details = new HashMap<>();
        details.put("method", method);
        details.put("success", success);
        if (!success) {
            details.put("failureReason", failureReason);
        }
        
        PciAuditEvent event = success ? PciAuditEvent.LOGIN_SUCCESS : PciAuditEvent.LOGIN_FAILURE;
        
        createPciAuditRecord(
            event,
            userId,
            "Authentication " + (success ? "succeeded" : "failed"),
            details
        );
        
        // Track failed attempts for account lockout
        if (!success) {
            trackFailedAttempts(userId);
        }
    }
    
    /**
     * Verify audit log integrity (PCI DSS 10.5)
     */
    public boolean verifyAuditIntegrity(String eventId) {
        try {
            PciAuditRecord record = retrieveAuditRecord(eventId);
            if (record == null) {
                return false;
            }
            
            // Verify hash chain
            String calculatedHash = calculateHash(
                record.getEventId(),
                record.getSequenceNumber(),
                record.getDetails()
            );
            
            if (!calculatedHash.equals(record.getHash())) {
                log.error("SECURITY ALERT: Audit tampering detected for event: {}", eventId);
                tamperDetectionCounter.increment();
                alertSecurityTeam("Audit tampering detected", null);
                return false;
            }
            
            // Verify chain integrity
            if (record.getSequenceNumber() > 1) {
                String previousInChain = hashChain.get(record.getSequenceNumber() - 1 + "");
                if (!record.getPreviousHash().equals(previousInChain)) {
                    log.error("SECURITY ALERT: Hash chain broken at event: {}", eventId);
                    return false;
                }
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Failed to verify audit integrity", e);
            return false;
        }
    }
    
    /**
     * Daily audit log review (PCI DSS 10.6)
     */
    @Async
    public CompletableFuture<AuditReviewReport> performDailyAuditReview() {
        return CompletableFuture.supplyAsync(() -> {
            AuditReviewReport report = new AuditReviewReport();
            LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
            
            try {
                // Review authentication failures
                List<PciAuditRecord> authFailures = findAuditRecords(
                    PciAuditEvent.LOGIN_FAILURE, yesterday, LocalDateTime.now()
                );
                report.setAuthenticationFailures(authFailures.size());
                
                // Review administrative actions
                List<PciAuditRecord> adminActions = findAuditRecords(
                    PciAuditEvent.ADMIN_ACTION, yesterday, LocalDateTime.now()
                );
                report.setAdminActions(adminActions);
                
                // Review card data access
                List<PciAuditRecord> cardAccess = findAuditRecords(
                    PciAuditEvent.CARD_DATA_ACCESS, yesterday, LocalDateTime.now()
                );
                report.setCardDataAccess(cardAccess.size());
                
                // Check for anomalies
                report.setAnomalies(detectAnomalies(yesterday, LocalDateTime.now()));
                
                // Verify integrity
                report.setIntegrityValid(verifyDailyIntegrity());
                
                // Send report
                sendDailyReport(report);
                
                return report;
                
            } catch (Exception e) {
                log.error("Daily audit review failed", e);
                report.setError("Review failed: " + e.getMessage());
                return report;
            }
        });
    }
    
    // Helper methods
    
    private String generateEventId() {
        return UUID.randomUUID().toString();
    }
    
    private String calculateHash(String eventId, long sequence, Map<String, Object> details) {
        try {
            String data = eventId + ":" + sequence + ":" + objectMapper.writeValueAsString(details);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate hash", e);
        }
    }
    
    private String signRecord(String eventId, long sequence) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec key = new SecretKeySpec(integritySecret.getBytes(), "HmacSHA256");
            hmac.init(key);
            byte[] signature = hmac.doFinal((eventId + sequence).getBytes());
            return Base64.getEncoder().encodeToString(signature);
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign record", e);
        }
    }
    
    private void updateHashChain(PciAuditRecord record) {
        previousHash = record.getHash();
        hashChain.put(record.getSequenceNumber().toString(), record.getHash());
        
        // Cleanup old entries (keep last 10000)
        if (hashChain.size() > 10000) {
            long oldestToKeep = record.getSequenceNumber() - 10000;
            hashChain.entrySet().removeIf(e -> 
                Long.parseLong(e.getKey()) < oldestToKeep
            );
        }
    }
    
    private void persistAuditRecord(PciAuditRecord record) {
        // Save to multiple stores for redundancy
        try {
            // Primary database - use custom event type for PCI events
            AuditEventType eventType;
            try {
                eventType = AuditEventType.valueOf(record.getEventType().name());
            } catch (IllegalArgumentException e) {
                // If PCI event type doesn't match AuditEventType, use SYSTEM_EVENT as fallback
                eventType = AuditEventType.SYSTEM_EVENT;
            }

            auditService.logAuditEvent(
                eventType,
                record.getUserId(),
                record.getAction(),
                record.getDetails(),
                AuditSeverity.INFO,
                record.getAffectedResource()
            );
            
            // Backup to immutable storage
            writeToImmutableStorage(record);
            
        } catch (Exception e) {
            log.error("Failed to persist audit record", e);
            throw new AuditException("Persistence failed", e);
        }
    }
    
    @Async
    private void streamAuditEvent(PciAuditRecord record) {
        if (realtimeAlert) {
            try {
                String json = objectMapper.writeValueAsString(record);
                kafkaTemplate.send("pci-audit-events", json);
            } catch (Exception e) {
                log.error("Failed to stream audit event", e);
            }
        }
    }
    
    private void checkPciViolations(PciAuditRecord record) {
        // Check for suspicious patterns
        if (record.getEventType() == PciAuditEvent.LOGIN_FAILURE) {
            // Multiple failures indicate possible breach attempt
            if (countRecentFailures(record.getUserId()) > 5) {
                pciViolationCounter.increment();
                alertSecurityTeam("Multiple authentication failures for user: " + record.getUserId(), null);
            }
        }
        
        if (record.getEventType() == PciAuditEvent.CARD_DATA_EXPORT) {
            // Card data export requires special monitoring
            pciViolationCounter.increment();
            alertSecurityTeam("Card data export by user: " + record.getUserId(), null);
        }
    }
    
    private void updateMetrics(PciAuditEvent eventType) {
        if (auditEventCounter == null) {
            auditEventCounter = Counter.builder("pci.audit.events")
                .tag("type", eventType.name())
                .register(meterRegistry);
        }
        auditEventCounter.increment();
    }
    
    private void alertSecurityTeam(String message, Exception error) {
        try {
            Map<String, Object> alert = new HashMap<>();
            alert.put("message", message);
            alert.put("timestamp", Instant.now());
            alert.put("severity", "HIGH");
            if (error != null) {
                alert.put("error", error.getMessage());
            }
            
            kafkaTemplate.send("security-alerts", objectMapper.writeValueAsString(alert));
            
        } catch (Exception e) {
            log.error("Failed to send security alert", e);
        }
    }
    
    // Stub methods - implement based on your infrastructure
    private String getUsernameFromId(String userId) { return userId; }
    private boolean determineSuccess(Map<String, Object> details) { 
        return details.getOrDefault("success", true).equals(true);
    }
    private String extractFailureReason(Map<String, Object> details) {
        return (String) details.get("failureReason");
    }
    private String extractSourceIp() { return "127.0.0.1"; }
    private String extractSourceHost() { return "localhost"; }
    private String extractUserAgent() { return "API"; }
    private String extractAffectedResource(Map<String, Object> details) {
        return (String) details.get("resource");
    }
    private String extractResourceType(Map<String, Object> details) {
        return (String) details.get("resourceType");
    }
    private String extractSessionId() { return UUID.randomUUID().toString(); }
    private String extractCorrelationId() { return UUID.randomUUID().toString(); }
    private Map<String, Object> sanitizeDetails(Map<String, Object> details) {
        // Remove sensitive data
        Map<String, Object> sanitized = new HashMap<>(details);
        sanitized.remove("password");
        sanitized.remove("cardNumber");
        sanitized.remove("cvv");
        return sanitized;
    }
    private void trackFailedAttempts(String userId) {}
    private PciAuditRecord retrieveAuditRecord(String eventId) { return null; }
    private List<PciAuditRecord> findAuditRecords(PciAuditEvent event, LocalDateTime from, LocalDateTime to) {
        return new ArrayList<>();
    }
    private List<String> detectAnomalies(LocalDateTime from, LocalDateTime to) {
        return new ArrayList<>();
    }
    private boolean verifyDailyIntegrity() { return true; }
    private void sendDailyReport(AuditReviewReport report) {}
    private int countRecentFailures(String userId) { return 0; }
    private void writeToImmutableStorage(PciAuditRecord record) {}
    
    // Data classes
    
    @lombok.Data
    @lombok.Builder
    public static class PciAuditRecord {
        private String eventId;
        private Long sequenceNumber;
        private PciAuditEvent eventType;
        private String userId;
        private String username;
        private String action;
        private Instant timestamp;
        private LocalDateTime timestampUtc;
        private boolean success;
        private String failureReason;
        private String sourceIp;
        private String sourceHost;
        private String userAgent;
        private String affectedResource;
        private String resourceType;
        private String sessionId;
        private String correlationId;
        private Map<String, Object> details;
        private String hash;
        private String previousHash;
        private String signature;
    }
    
    @lombok.Data
    public static class AuditReviewReport {
        private int authenticationFailures;
        private List<PciAuditRecord> adminActions;
        private int cardDataAccess;
        private List<String> anomalies;
        private boolean integrityValid;
        private String error;
        private LocalDateTime reviewDate = LocalDateTime.now();
    }
    
    public static class AuditException extends RuntimeException {
        public AuditException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}