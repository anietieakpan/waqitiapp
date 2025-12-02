package com.waqiti.nft.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Audit Logger for Blockchain Key Management
 * 
 * Logs all key access events to Kafka for:
 * - Security monitoring
 * - Compliance auditing
 * - Forensic analysis
 * - Anomaly detection
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLogger {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final String AUDIT_TOPIC = "security.blockchain-key-access";
    
    /**
     * Log key access event
     * 
     * @param keyIdentifier Key being accessed
     * @param operation Operation being performed
     * @param status Status of operation (ATTEMPTED, SUCCESS, FAILED, etc.)
     */
    public void logKeyAccess(String keyIdentifier, String operation, String status) {
        try {
            Map<String, Object> auditEvent = new HashMap<>();
            auditEvent.put("timestamp", LocalDateTime.now().toString());
            auditEvent.put("eventType", "BLOCKCHAIN_KEY_ACCESS");
            auditEvent.put("keyIdentifier", keyIdentifier);
            auditEvent.put("operation", operation);
            auditEvent.put("status", status);
            auditEvent.put("service", "nft-service");
            auditEvent.put("component", "BlockchainKeyManager");
            
            // Add contextual information
            auditEvent.put("threadId", Thread.currentThread().getId());
            auditEvent.put("threadName", Thread.currentThread().getName());
            
            // Publish to Kafka
            kafkaTemplate.send(AUDIT_TOPIC, keyIdentifier, auditEvent);
            
            // Also log locally for immediate visibility
            log.info("KEY_ACCESS_AUDIT: keyId={}, operation={}, status={}", 
                keyIdentifier, operation, status);
            
        } catch (Exception e) {
            // Audit logging failure should not break key operations
            log.error("Failed to publish audit log event: keyId={}, operation={}, status={}", 
                keyIdentifier, operation, status, e);
        }
    }
    
    /**
     * Log security violation
     */
    public void logSecurityViolation(String keyIdentifier, String violation, String details) {
        try {
            Map<String, Object> auditEvent = new HashMap<>();
            auditEvent.put("timestamp", LocalDateTime.now().toString());
            auditEvent.put("eventType", "SECURITY_VIOLATION");
            auditEvent.put("keyIdentifier", keyIdentifier);
            auditEvent.put("violation", violation);
            auditEvent.put("details", details);
            auditEvent.put("service", "nft-service");
            auditEvent.put("severity", "CRITICAL");
            
            kafkaTemplate.send(AUDIT_TOPIC, keyIdentifier, auditEvent);
            
            log.error("SECURITY_VIOLATION: keyId={}, violation={}, details={}", 
                keyIdentifier, violation, details);
            
        } catch (Exception e) {
            log.error("Failed to publish security violation event", e);
        }
    }
}