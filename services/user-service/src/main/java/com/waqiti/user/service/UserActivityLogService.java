package com.waqiti.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for logging user activities and audit events
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserActivityLogService {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    /**
     * Log user activity
     */
    public void logActivity(String userId, String activityType, String description, Map<String, Object> metadata) {
        Map<String, Object> activityLog = new HashMap<>();
        activityLog.put("id", UUID.randomUUID().toString());
        activityLog.put("userId", userId);
        activityLog.put("activityType", activityType);
        activityLog.put("description", description);
        activityLog.put("metadata", metadata);
        activityLog.put("timestamp", LocalDateTime.now());
        activityLog.put("service", "user-service");
        
        // Send to audit service
        kafkaTemplate.send("user-activity-logs", activityLog);
        
        // Also send to audit trail topic for compliance
        kafkaTemplate.send("audit-trail", activityLog);
        
        log.debug("Activity logged for user {}: {}", userId, activityType);
    }
    
    /**
     * Log security event
     */
    public void logSecurityEvent(String userId, String eventType, String description, Map<String, Object> details) {
        Map<String, Object> securityLog = new HashMap<>();
        securityLog.put("id", UUID.randomUUID().toString());
        securityLog.put("userId", userId);
        securityLog.put("eventType", eventType);
        securityLog.put("description", description);
        securityLog.put("details", details);
        securityLog.put("timestamp", LocalDateTime.now());
        securityLog.put("service", "user-service");
        securityLog.put("severity", determineSeverity(eventType));
        
        // Send to security monitoring
        kafkaTemplate.send("security-events", securityLog);
        
        log.info("Security event logged for user {}: {}", userId, eventType);
    }
    
    /**
     * Log fraud-related activity
     */
    public void logFraudActivity(String userId, String fraudType, Map<String, Object> fraudDetails) {
        Map<String, Object> fraudLog = new HashMap<>();
        fraudLog.put("id", UUID.randomUUID().toString());
        fraudLog.put("userId", userId);
        fraudLog.put("fraudType", fraudType);
        fraudLog.put("details", fraudDetails);
        fraudLog.put("timestamp", LocalDateTime.now());
        fraudLog.put("service", "user-service");
        
        // Send to fraud monitoring
        kafkaTemplate.send("fraud-activity-logs", fraudLog);
        
        log.warn("Fraud activity logged for user {}: {}", userId, fraudType);
    }
    
    /**
     * Log account status change
     */
    public void logAccountStatusChange(String userId, String oldStatus, String newStatus, String reason) {
        Map<String, Object> statusLog = new HashMap<>();
        statusLog.put("id", UUID.randomUUID().toString());
        statusLog.put("userId", userId);
        statusLog.put("oldStatus", oldStatus);
        statusLog.put("newStatus", newStatus);
        statusLog.put("reason", reason);
        statusLog.put("timestamp", LocalDateTime.now());
        statusLog.put("service", "user-service");
        
        // Send to audit trail
        kafkaTemplate.send("account-status-changes", statusLog);
        
        log.info("Account status changed for user {}: {} -> {}", userId, oldStatus, newStatus);
    }
    
    /**
     * Determine severity based on event type
     */
    private String determineSeverity(String eventType) {
        if (eventType.contains("FRAUD") || eventType.contains("SUSPENSION") || eventType.contains("LOCK")) {
            return "CRITICAL";
        } else if (eventType.contains("WARNING") || eventType.contains("RESTRICTION")) {
            return "HIGH";
        } else if (eventType.contains("MONITOR")) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }
}