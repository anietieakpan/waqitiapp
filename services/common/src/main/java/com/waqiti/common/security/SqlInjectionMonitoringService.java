package com.waqiti.common.security;

import com.waqiti.common.audit.AuditService;
import com.waqiti.common.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SQL Injection Monitoring and Response Service
 * 
 * Provides real-time monitoring, alerting, and automated response
 * to SQL injection attempts with:
 * - Attack pattern tracking
 * - Rate limiting and blocking
 * - Executive alerting for critical threats
 * - Automated incident response
 * - Forensic logging
 * 
 * @author Waqiti Security Team
 * @version 2.0.0
 * @since 2024-01-16
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqlInjectionMonitoringService {
    
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SqlInjectionValidator sqlInjectionValidator;
    
    // Track attack attempts per IP
    private final Map<String, AtomicInteger> ipAttackCounts = new ConcurrentHashMap<>();
    
    // Track attack attempts per user
    private final Map<String, AtomicInteger> userAttackCounts = new ConcurrentHashMap<>();
    
    // Blocked IPs
    private final Map<String, LocalDateTime> blockedIPs = new ConcurrentHashMap<>();
    
    // Blocked users
    private final Map<String, LocalDateTime> blockedUsers = new ConcurrentHashMap<>();
    
    // Configuration
    private static final int MAX_ATTEMPTS_PER_IP = 5;
    private static final int MAX_ATTEMPTS_PER_USER = 3;
    private static final int BLOCK_DURATION_HOURS = 24;
    
    /**
     * Monitor and validate input for SQL injection
     * 
     * @param input User input
     * @param fieldName Field name
     * @param userId User ID
     * @param ipAddress IP address
     * @param endpoint API endpoint
     * @return true if input is safe
     */
    public boolean monitorAndValidate(String input, String fieldName, String userId, 
                                     String ipAddress, String endpoint) {
        
        // Check if IP or user is blocked
        if (isBlocked(ipAddress, userId)) {
            log.error("BLOCKED_ACCESS: Blocked entity attempted access - IP: {}, User: {}", 
                ipAddress, userId);
            recordBlockedAttempt(ipAddress, userId, endpoint);
            return false;
        }
        
        // Validate input
        boolean isSafe = sqlInjectionValidator.isInputSafe(input, fieldName);
        
        if (!isSafe) {
            // SQL injection detected
            handleSqlInjectionAttempt(input, fieldName, userId, ipAddress, endpoint);
            return false;
        }
        
        return true;
    }
    
    /**
     * Handle detected SQL injection attempt
     */
    @Async
    protected void handleSqlInjectionAttempt(String maliciousInput, String fieldName, 
                                           String userId, String ipAddress, String endpoint) {
        
        String incidentId = UUID.randomUUID().toString();
        
        log.error("SQL_INJECTION_ATTEMPT: Incident {} - User: {}, IP: {}, Endpoint: {}, Field: {}", 
            incidentId, userId, ipAddress, endpoint, fieldName);
        
        // Track attack attempts
        int ipAttempts = incrementAttackCount(ipAttackCounts, ipAddress);
        int userAttempts = userId != null ? incrementAttackCount(userAttackCounts, userId) : 0;
        
        // Create security incident
        SecurityIncident incident = createSecurityIncident(
            incidentId, maliciousInput, fieldName, userId, ipAddress, endpoint, 
            ipAttempts, userAttempts
        );
        
        // Audit the incident
        auditSecurityIncident(incident);
        
        // Determine response level
        ResponseLevel responseLevel = determineResponseLevel(ipAttempts, userAttempts);
        
        // Execute response actions
        executeResponseActions(incident, responseLevel);
        
        // Send alerts
        sendSecurityAlerts(incident, responseLevel);
        
        // Block if threshold exceeded
        if (shouldBlock(ipAttempts, userAttempts)) {
            blockAttacker(ipAddress, userId);
        }
    }
    
    /**
     * Determine response level based on attack severity
     */
    private ResponseLevel determineResponseLevel(int ipAttempts, int userAttempts) {
        if (ipAttempts > 10 || userAttempts > 5) {
            return ResponseLevel.CRITICAL;
        } else if (ipAttempts > 5 || userAttempts > 3) {
            return ResponseLevel.HIGH;
        } else if (ipAttempts > 2 || userAttempts > 1) {
            return ResponseLevel.MEDIUM;
        } else {
            return ResponseLevel.LOW;
        }
    }
    
    /**
     * Execute automated response actions
     */
    private void executeResponseActions(SecurityIncident incident, ResponseLevel level) {
        switch (level) {
            case CRITICAL:
                // Immediate blocking and escalation
                blockAttacker(incident.getIpAddress(), incident.getUserId());
                triggerEmergencyResponse(incident);
                notifySecurityTeam(incident);
                initiateForensicCapture(incident);
                break;
                
            case HIGH:
                // Block and alert
                blockAttacker(incident.getIpAddress(), incident.getUserId());
                notifySecurityTeam(incident);
                break;
                
            case MEDIUM:
                // Temporary rate limiting
                applyRateLimiting(incident.getIpAddress(), incident.getUserId());
                break;
                
            case LOW:
                // Log and monitor
                log.warn("SQL_INJECTION_MONITORING: Low-level attempt logged - Incident: {}", 
                    incident.getIncidentId());
                break;
        }
    }
    
    /**
     * Send security alerts based on response level
     */
    private void sendSecurityAlerts(SecurityIncident incident, ResponseLevel level) {
        try {
            // Send to security monitoring topic
            kafkaTemplate.send(KafkaTopics.SECURITY_INCIDENTS, incident.getIncidentId(), incident);
            
            if (level == ResponseLevel.CRITICAL || level == ResponseLevel.HIGH) {
                // Send critical alert
                Map<String, Object> alert = Map.of(
                    "alertType", "SQL_INJECTION_ATTACK",
                    "incidentId", incident.getIncidentId(),
                    "severity", level.toString(),
                    "userId", incident.getUserId() != null ? incident.getUserId() : "unknown",
                    "ipAddress", incident.getIpAddress(),
                    "endpoint", incident.getEndpoint(),
                    "attackCount", incident.getIpAttackCount(),
                    "timestamp", LocalDateTime.now()
                );
                
                kafkaTemplate.send(KafkaTopics.CRITICAL_SECURITY_ALERTS, 
                    "SQL_INJECTION_" + level, alert);
                
                // For critical incidents, also send executive alert
                if (level == ResponseLevel.CRITICAL) {
                    kafkaTemplate.send(KafkaTopics.EXECUTIVE_ALERTS, 
                        "CRITICAL_SQL_INJECTION", alert);
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to send security alerts for incident: {}", incident.getIncidentId(), e);
        }
    }
    
    /**
     * Block attacker IP and/or user
     */
    private void blockAttacker(String ipAddress, String userId) {
        LocalDateTime blockUntil = LocalDateTime.now().plusHours(BLOCK_DURATION_HOURS);
        
        if (ipAddress != null) {
            blockedIPs.put(ipAddress, blockUntil);
            log.error("SECURITY_ACTION: Blocked IP {} until {}", ipAddress, blockUntil);
        }
        
        if (userId != null) {
            blockedUsers.put(userId, blockUntil);
            log.error("SECURITY_ACTION: Blocked User {} until {}", userId, blockUntil);
            
            // Send event to disable user account
            kafkaTemplate.send(KafkaTopics.USER_SECURITY_ACTIONS, userId, 
                Map.of(
                    "action", "SUSPEND_ACCOUNT",
                    "reason", "SQL_INJECTION_ATTEMPTS",
                    "until", blockUntil
                ));
        }
    }
    
    /**
     * Check if IP or user is blocked
     */
    private boolean isBlocked(String ipAddress, String userId) {
        // Check IP block
        if (ipAddress != null && blockedIPs.containsKey(ipAddress)) {
            LocalDateTime blockUntil = blockedIPs.get(ipAddress);
            if (LocalDateTime.now().isBefore(blockUntil)) {
                return true;
            } else {
                // Block expired, remove it
                blockedIPs.remove(ipAddress);
            }
        }
        
        // Check user block
        if (userId != null && blockedUsers.containsKey(userId)) {
            LocalDateTime blockUntil = blockedUsers.get(userId);
            if (LocalDateTime.now().isBefore(blockUntil)) {
                return true;
            } else {
                // Block expired, remove it
                blockedUsers.remove(userId);
            }
        }
        
        return false;
    }
    
    /**
     * Increment attack count for tracking
     */
    private int incrementAttackCount(Map<String, AtomicInteger> countMap, String key) {
        if (key == null) {
            return 0;
        }
        
        return countMap.computeIfAbsent(key, k -> new AtomicInteger(0))
                      .incrementAndGet();
    }
    
    /**
     * Determine if blocking threshold exceeded
     */
    private boolean shouldBlock(int ipAttempts, int userAttempts) {
        return ipAttempts >= MAX_ATTEMPTS_PER_IP || userAttempts >= MAX_ATTEMPTS_PER_USER;
    }
    
    /**
     * Create security incident object
     */
    private SecurityIncident createSecurityIncident(String incidentId, String maliciousInput, 
                                                  String fieldName, String userId, 
                                                  String ipAddress, String endpoint, 
                                                  int ipAttempts, int userAttempts) {
        return SecurityIncident.builder()
            .incidentId(incidentId)
            .incidentType("SQL_INJECTION")
            .maliciousInput(truncateForSecurity(maliciousInput))
            .fieldName(fieldName)
            .userId(userId)
            .ipAddress(ipAddress)
            .endpoint(endpoint)
            .ipAttackCount(ipAttempts)
            .userAttackCount(userAttempts)
            .timestamp(LocalDateTime.now())
            .blocked(shouldBlock(ipAttempts, userAttempts))
            .build();
    }
    
    /**
     * Audit security incident
     */
    private void auditSecurityIncident(SecurityIncident incident) {
        try {
            auditService.auditSecurityEvent(
                "SQL_INJECTION_ATTEMPT",
                incident.getUserId() != null ? incident.getUserId() : "anonymous",
                String.format("SQL injection attempt detected - Incident: %s", incident.getIncidentId()),
                Map.of(
                    "incidentId", incident.getIncidentId(),
                    "fieldName", incident.getFieldName(),
                    "endpoint", incident.getEndpoint(),
                    "ipAddress", incident.getIpAddress(),
                    "attackCount", incident.getIpAttackCount(),
                    "blocked", incident.isBlocked()
                )
            );
        } catch (Exception e) {
            log.error("Failed to audit security incident: {}", incident.getIncidentId(), e);
        }
    }
    
    /**
     * Record blocked access attempt
     */
    private void recordBlockedAttempt(String ipAddress, String userId, String endpoint) {
        try {
            auditService.auditSecurityEvent(
                "BLOCKED_ACCESS_ATTEMPT",
                userId != null ? userId : "anonymous",
                "Blocked entity attempted access",
                Map.of(
                    "ipAddress", ipAddress != null ? ipAddress : "unknown",
                    "endpoint", endpoint,
                    "timestamp", LocalDateTime.now()
                )
            );
        } catch (Exception e) {
            log.error("Failed to record blocked attempt", e);
        }
    }
    
    /**
     * Trigger emergency response for critical incidents
     */
    private void triggerEmergencyResponse(SecurityIncident incident) {
        log.error("EMERGENCY_RESPONSE: Triggering emergency response for incident: {}", 
            incident.getIncidentId());
        
        // Additional emergency actions
        kafkaTemplate.send(KafkaTopics.EMERGENCY_RESPONSE, incident.getIncidentId(), 
            Map.of(
                "action", "LOCKDOWN_MODE",
                "incidentId", incident.getIncidentId(),
                "triggerTime", LocalDateTime.now()
            ));
    }
    
    /**
     * Notify security team
     */
    private void notifySecurityTeam(SecurityIncident incident) {
        // Send notification to security team
        kafkaTemplate.send(KafkaTopics.SECURITY_TEAM_ALERTS, incident.getIncidentId(), incident);
    }
    
    /**
     * Apply rate limiting to suspicious traffic
     */
    private void applyRateLimiting(String ipAddress, String userId) {
        // Send rate limiting event
        kafkaTemplate.send(KafkaTopics.RATE_LIMITING_EVENTS, 
            ipAddress != null ? ipAddress : userId,
            Map.of(
                "action", "APPLY_RATE_LIMIT",
                "target", ipAddress != null ? ipAddress : userId,
                "limitType", "SECURITY_THROTTLE",
                "duration", "PT1H" // 1 hour
            ));
    }
    
    /**
     * Initiate forensic capture for investigation
     */
    private void initiateForensicCapture(SecurityIncident incident) {
        // Trigger forensic data capture
        kafkaTemplate.send(KafkaTopics.FORENSIC_CAPTURE, incident.getIncidentId(),
            Map.of(
                "incidentId", incident.getIncidentId(),
                "captureType", "SQL_INJECTION_FORENSICS",
                "priority", "CRITICAL"
            ));
    }
    
    /**
     * Truncate malicious input for safe storage
     */
    private String truncateForSecurity(String input) {
        if (input == null) {
            return null;
        }
        
        // Remove dangerous content and truncate
        String safe = input.replaceAll("[^a-zA-Z0-9\\s]", "");
        return safe.length() > 50 ? safe.substring(0, 50) + "..." : safe;
    }
    
    /**
     * Response level enumeration
     */
    public enum ResponseLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    /**
     * Security incident data class
     */
    @lombok.Data
    @lombok.Builder
    public static class SecurityIncident {
        private String incidentId;
        private String incidentType;
        private String maliciousInput;
        private String fieldName;
        private String userId;
        private String ipAddress;
        private String endpoint;
        private int ipAttackCount;
        private int userAttackCount;
        private LocalDateTime timestamp;
        private boolean blocked;
    }
}