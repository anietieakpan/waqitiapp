package com.waqiti.ledger.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.ledger.domain.AuditLogEntry;
import com.waqiti.ledger.dto.LedgerAuditTrailResponse;
import com.waqiti.ledger.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Audit Trail Service with Immutable Logging
 * 
 * Provides comprehensive audit logging with:
 * - Cryptographic integrity verification
 * - Blockchain-style hash chaining
 * - Tamper detection
 * - Regulatory compliance (SOX, PCI-DSS, GDPR)
 * - Async write-through to multiple stores
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditTrailService {
    
    private final AuditLogRepository auditLogRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    // Secret for HMAC integrity verification (should be in Vault)
    private static final String INTEGRITY_SECRET = System.getenv("AUDIT_INTEGRITY_SECRET");
    private static final String AUDIT_TOPIC = "ledger-audit-trail";
    
    /**
     * Log an audit event with full integrity protection
     * Uses SEPARATE transaction to ensure audit logs even if main transaction fails
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLogEntry logAuditEvent(AuditEvent event) {
        try {
            // Calculate hash chain
            String previousHash = getLastEntryHash();

            // Create audit log entry with builder (immutable object)
            AuditLogEntry.AuditLogEntryBuilder builder = createAuditLogEntryBuilder(event);
            builder.previousHash(previousHash);

            // Build temporary entry for hash calculation
            AuditLogEntry tempEntry = builder.build();

            // Calculate entry hash with integrity
            String entryHash = calculateEntryHash(tempEntry);

            // Calculate HMAC for tamper detection
            String hmac = calculateHMAC(tempEntry, entryHash);

            // Create final immutable entry with all hashes
            AuditLogEntry entry = createAuditLogEntryBuilder(event)
                .previousHash(previousHash)
                .hash(entryHash)
                .hmac(hmac)
                .build();

            // Save to database (immutable - no updates allowed)
            entry = auditLogRepository.save(entry);

            // Async write to Kafka for external audit systems
            publishToKafka(entry);

            // Async write to blockchain or external immutable store
            writeToImmutableStore(entry);

            log.debug("Audit event logged: {} for entity {} by {}",
                event.getAction(), event.getEntityId(), event.getPerformedBy());

            return entry;

        } catch (Exception e) {
            log.error("Failed to log audit event: {}", event, e);
            // Still try to log to fallback system
            logToFallbackSystem(event, e);
            throw new AuditLogException("Failed to create audit log", e);
        }
    }
    
    /**
     * Create audit log entry builder from event
     */
    private AuditLogEntry.AuditLogEntryBuilder createAuditLogEntryBuilder(AuditEvent event) {
        HttpServletRequest request = getCurrentRequest();

        return AuditLogEntry.builder()
            .id(UUID.randomUUID())
            .timestamp(LocalDateTime.now())
            .entityType(event.getEntityType())
            .entityId(event.getEntityId())
            .action(event.getAction())
            .performedBy(event.getPerformedBy())
            .performedByRole(event.getPerformedByRole())
            .ipAddress(getClientIpAddress(request))
            .userAgent(request != null ? request.getHeader("User-Agent") : null)
            .sessionId(request != null ? request.getSession().getId() : null)
            .correlationId(event.getCorrelationId())
            .previousState(serializeState(event.getPreviousState()))
            .newState(serializeState(event.getNewState()))
            .changes(calculateChanges(event.getPreviousState(), event.getNewState()))
            .description(event.getDescription())
            .source(event.getSource())
            .successful(event.isSuccessful())
            .errorMessage(event.getErrorMessage())
            .metadata(event.getMetadata());
    }
    
    /**
     * Calculate cryptographic hash of entry for chain integrity
     */
    private String calculateEntryHash(AuditLogEntry entry) {
        try {
            // Create deterministic string representation
            StringBuilder data = new StringBuilder();
            data.append(entry.getId());
            data.append(entry.getTimestamp());
            data.append(entry.getEntityType());
            data.append(entry.getEntityId());
            data.append(entry.getAction());
            data.append(entry.getPerformedBy());
            data.append(entry.getPreviousHash());
            data.append(entry.getPreviousState());
            data.append(entry.getNewState());
            
            // Calculate SHA-256 hash
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.toString().getBytes(StandardCharsets.UTF_8));
            
            return Base64.getEncoder().encodeToString(hash);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate entry hash", e);
        }
    }
    
    /**
     * Calculate HMAC for tamper detection
     *
     * CRITICAL SECURITY FIX: Removed hardcoded "default-secret" fallback
     * Application MUST be configured with proper INTEGRITY_SECRET
     */
    private String calculateHMAC(AuditLogEntry entry, String hash) {
        try {
            // SECURITY FIX: No fallback to hardcoded secret
            if (INTEGRITY_SECRET == null || INTEGRITY_SECRET.trim().isEmpty()) {
                throw new IllegalStateException(
                    "CRITICAL SECURITY ERROR: INTEGRITY_SECRET is not configured. " +
                    "Set environment variable AUDIT_INTEGRITY_SECRET or configure via Vault. " +
                    "Audit trail integrity cannot be guaranteed without proper secret configuration."
                );
            }

            String secret = INTEGRITY_SECRET;

            // Create HMAC data
            String data = entry.getId() + hash + entry.getTimestamp();

            // Calculate HMAC-SHA256
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);

            byte[] hmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(hmac);

        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate HMAC", e);
        }
    }
    
    /**
     * Get hash of last entry for chain continuity
     */
    private String getLastEntryHash() {
        return auditLogRepository.findTopByOrderByTimestampDesc()
            .map(AuditLogEntry::getHash)
            .orElse("GENESIS");
    }
    
    /**
     * Verify integrity of audit trail
     */
    @Transactional(readOnly = true)
    public VerificationResult verifyAuditTrailIntegrity(LocalDateTime startTime, LocalDateTime endTime) {
        List<AuditLogEntry> entries = auditLogRepository.findByTimestampBetweenOrderByTimestamp(
            startTime, endTime);
        
        VerificationResult result = new VerificationResult();
        result.setTotalEntries(entries.size());
        result.setStartTime(startTime);
        result.setEndTime(endTime);
        
        String expectedPreviousHash = null;
        List<String> violations = new ArrayList<>();
        
        for (AuditLogEntry entry : entries) {
            // Verify hash chain
            if (expectedPreviousHash != null && !entry.getPreviousHash().equals(expectedPreviousHash)) {
                violations.add(String.format("Chain broken at entry %s: expected previous hash %s, found %s",
                    entry.getId(), expectedPreviousHash, entry.getPreviousHash()));
            }
            
            // Verify entry hash
            String calculatedHash = calculateEntryHash(entry);
            if (!calculatedHash.equals(entry.getHash())) {
                violations.add(String.format("Hash mismatch for entry %s: expected %s, calculated %s",
                    entry.getId(), entry.getHash(), calculatedHash));
            }
            
            // Verify HMAC
            String calculatedHmac = calculateHMAC(entry, entry.getHash());
            if (!calculatedHmac.equals(entry.getHmac())) {
                violations.add(String.format("HMAC verification failed for entry %s", entry.getId()));
            }
            
            expectedPreviousHash = entry.getHash();
        }
        
        result.setValid(violations.isEmpty());
        result.setViolations(violations);
        result.setVerifiedAt(LocalDateTime.now());
        
        // Log verification result
        if (!result.isValid()) {
            log.error("Audit trail integrity verification failed: {} violations found", violations.size());
            alertSecurityTeam("Audit trail integrity violation detected", violations);
        }
        
        return result;
    }
    
    /**
     * Query audit trail with filters
     */
    @Transactional(readOnly = true)
    public List<LedgerAuditTrailResponse> queryAuditTrail(AuditQuery query) {
        List<AuditLogEntry> entries = auditLogRepository.findByQuery(
            query.getEntityType(),
            query.getEntityId(),
            query.getAction(),
            query.getPerformedBy(),
            query.getStartTime(),
            query.getEndTime()
        );
        
        return entries.stream()
            .map(this::mapToResponse)
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Publish to Kafka for external audit systems
     */
    private void publishToKafka(AuditLogEntry entry) {
        CompletableFuture.runAsync(() -> {
            try {
                kafkaTemplate.send(AUDIT_TOPIC, entry.getId().toString(), entry);
                log.debug("Audit event published to Kafka: {}", entry.getId());
            } catch (Exception e) {
                log.error("Failed to publish audit event to Kafka", e);
            }
        });
    }
    
    /**
     * Write to immutable external store (blockchain, WORM storage)
     */
    private void writeToImmutableStore(AuditLogEntry entry) {
        CompletableFuture.runAsync(() -> {
            try {
                // Integration with blockchain or WORM storage
                // This could be IPFS, Hyperledger, AWS Glacier Vault Lock, etc.
                Map<String, Object> immutableRecord = Map.of(
                    "id", entry.getId(),
                    "hash", entry.getHash(),
                    "hmac", entry.getHmac(),
                    "timestamp", entry.getTimestamp(),
                    "data", Base64.getEncoder().encodeToString(
                        objectMapper.writeValueAsBytes(entry))
                );
                
                // Send to immutable store topic for processing
                kafkaTemplate.send("immutable-audit-store", immutableRecord);
                
            } catch (Exception e) {
                log.error("Failed to write to immutable store", e);
            }
        });
    }
    
    /**
     * Fallback logging system for critical failures
     */
    private void logToFallbackSystem(AuditEvent event, Exception error) {
        try {
            // Write to separate file or database
            log.error("AUDIT_FALLBACK: {}", objectMapper.writeValueAsString(event));
            
            // Send critical alert
            Map<String, Object> alert = Map.of(
                "type", "AUDIT_LOG_FAILURE",
                "event", event,
                "error", error.getMessage(),
                "timestamp", System.currentTimeMillis()
            );
            
            kafkaTemplate.send("critical-alerts", alert);
            
        } catch (Exception e) {
            // Last resort - write to system log
            log.error("CRITICAL: Audit log failure - complete system failure: {}", event, e);
        }
    }
    
    /**
     * Alert security team of integrity violations
     */
    private void alertSecurityTeam(String message, List<String> violations) {
        try {
            Map<String, Object> alert = Map.of(
                "alertType", "AUDIT_INTEGRITY_VIOLATION",
                "message", message,
                "violations", violations,
                "service", "ledger-service",
                "timestamp", LocalDateTime.now(),
                "severity", "CRITICAL"
            );
            
            kafkaTemplate.send("security-alerts", alert);
            
        } catch (Exception e) {
            log.error("Failed to send security alert", e);
        }
    }
    
    /**
     * Get current HTTP request
     */
    private HttpServletRequest getCurrentRequest() {
        try {
            ServletRequestAttributes attrs = 
                (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return attrs.getRequest();
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Extract client IP address
     */
    private String getClientIpAddress(HttpServletRequest request) {
        if (request == null) return null;
        
        String[] headers = {
            "X-Forwarded-For",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "X-Real-IP"
        };
        
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // Handle comma-separated IPs
                return ip.split(",")[0].trim();
            }
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * Serialize state for storage
     */
    private String serializeState(Object state) {
        if (state == null) return null;
        try {
            return objectMapper.writeValueAsString(state);
        } catch (Exception e) {
            return state.toString();
        }
    }
    
    /**
     * Calculate changes between states
     */
    private String calculateChanges(Object previousState, Object newState) {
        if (previousState == null || newState == null) {
            return null;
        }
        
        try {
            Map<String, Object> changes = new HashMap<>();
            
            // Convert to maps for comparison
            Map<String, Object> prevMap = objectMapper.convertValue(previousState, Map.class);
            Map<String, Object> newMap = objectMapper.convertValue(newState, Map.class);
            
            // Find changed fields
            for (Map.Entry<String, Object> entry : newMap.entrySet()) {
                Object prevValue = prevMap.get(entry.getKey());
                Object newValue = entry.getValue();
                
                if (!Objects.equals(prevValue, newValue)) {
                    changes.put(entry.getKey(), Map.of(
                        "previous", prevValue,
                        "new", newValue
                    ));
                }
            }
            
            return objectMapper.writeValueAsString(changes);
            
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Map entity to response DTO
     */
    private LedgerAuditTrailResponse mapToResponse(AuditLogEntry entry) {
        return LedgerAuditTrailResponse.builder()
            .auditId(entry.getId())
            .entityType(entry.getEntityType())
            .entityId(entry.getEntityId())
            .action(entry.getAction())
            .performedBy(entry.getPerformedBy())
            .performedAt(entry.getTimestamp())
            .ipAddress(entry.getIpAddress())
            .userAgent(entry.getUserAgent())
            .previousState(deserializeState(entry.getPreviousState()))
            .newState(deserializeState(entry.getNewState()))
            .changes(deserializeState(entry.getChanges()))
            .description(entry.getDescription())
            .source(entry.getSource())
            .successful(entry.isSuccessful())
            .errorMessage(entry.getErrorMessage())
            .build();
    }
    
    /**
     * Deserialize state from storage
     */
    private Map<String, Object> deserializeState(String state) {
        if (state == null) return null;
        try {
            return objectMapper.readValue(state, Map.class);
        } catch (Exception e) {
            return Map.of("raw", state);
        }
    }
    
    /**
     * Audit event data
     */
    @lombok.Data
    @lombok.Builder
    public static class AuditEvent {
        private String entityType;
        private String entityId;
        private String action;
        private String performedBy;
        private String performedByRole;
        private String correlationId;
        private Object previousState;
        private Object newState;
        private String description;
        private String source;
        private boolean successful;
        private String errorMessage;
        private Map<String, Object> metadata;
    }
    
    /**
     * Audit query parameters
     */
    @lombok.Data
    @lombok.Builder
    public static class AuditQuery {
        private String entityType;
        private String entityId;
        private String action;
        private String performedBy;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
    }
    
    /**
     * Verification result
     */
    @lombok.Data
    public static class VerificationResult {
        private int totalEntries;
        private boolean valid;
        private List<String> violations;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private LocalDateTime verifiedAt;
    }
    
    /**
     * Audit log exception
     */
    public static class AuditLogException extends RuntimeException {
        public AuditLogException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}