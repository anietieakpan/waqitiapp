package com.waqiti.audit.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.audit.service.AuditLogService;
import com.waqiti.audit.service.ComplianceAuditService;
import com.waqiti.audit.service.SecurityAuditService;
import com.waqiti.audit.service.AuditRetentionService;
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

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLogEventConsumer {
    
    private final AuditLogService auditLogService;
    private final ComplianceAuditService complianceAuditService;
    private final SecurityAuditService securityAuditService;
    private final AuditRetentionService auditRetentionService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"audit-log-events", "audit-logs", "security-audit-events", "compliance-audit-events"},
        groupId = "audit-service-log-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional
    public void handleAuditLogEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("AUDIT LOG: Processing audit event - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
        
        LocalDateTime processingStartTime = LocalDateTime.now();
        UUID auditId = null;
        UUID userId = null;
        String auditEventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            auditId = UUID.fromString((String) event.get("auditId"));
            userId = event.containsKey("userId") ? UUID.fromString((String) event.get("userId")) : null;
            auditEventType = (String) event.get("auditEventType");
            String auditCategory = (String) event.get("auditCategory");
            String action = (String) event.get("action");
            String resourceType = (String) event.get("resourceType");
            String resourceId = (String) event.get("resourceId");
            String performedBy = (String) event.get("performedBy");
            String ipAddress = (String) event.get("ipAddress");
            String userAgent = (String) event.get("userAgent");
            String sessionId = (String) event.get("sessionId");
            LocalDateTime eventTimestamp = LocalDateTime.parse((String) event.get("timestamp"));
            String severity = (String) event.get("severity");
            Boolean isSensitive = (Boolean) event.getOrDefault("isSensitive", false);
            Boolean requiresRetention = (Boolean) event.getOrDefault("requiresRetention", true);
            @SuppressWarnings("unchecked")
            Map<String, Object> auditData = (Map<String, Object>) event.getOrDefault("auditData", Map.of());
            @SuppressWarnings("unchecked")
            Map<String, Object> beforeState = (Map<String, Object>) event.get("beforeState");
            @SuppressWarnings("unchecked")
            Map<String, Object> afterState = (Map<String, Object>) event.get("afterState");
            
            log.info("Audit log event - AuditId: {}, UserId: {}, Type: {}, Category: {}, Action: {}, Resource: {} ({}), Severity: {}", 
                    auditId, userId, auditEventType, auditCategory, action, resourceType, resourceId, severity);
            
            validateAuditEvent(auditId, auditEventType, auditCategory, action, resourceType, eventTimestamp);
            
            processAuditByCategory(auditId, userId, auditEventType, auditCategory, action, resourceType, 
                    resourceId, performedBy, ipAddress, userAgent, sessionId, eventTimestamp, 
                    severity, isSensitive, auditData, beforeState, afterState);
            
            if ("SECURITY".equals(auditCategory)) {
                handleSecurityAudit(auditId, userId, auditEventType, action, resourceType, resourceId, 
                        performedBy, ipAddress, eventTimestamp, auditData);
            }
            
            if ("COMPLIANCE".equals(auditCategory)) {
                handleComplianceAudit(auditId, userId, auditEventType, action, resourceType, resourceId, 
                        performedBy, eventTimestamp, auditData);
            }
            
            if ("FINANCIAL".equals(auditCategory)) {
                handleFinancialAudit(auditId, userId, auditEventType, action, resourceType, resourceId, 
                        performedBy, eventTimestamp, auditData);
            }
            
            if (isSensitive) {
                handleSensitiveAudit(auditId, userId, auditEventType, resourceType, resourceId, 
                        performedBy, eventTimestamp);
            }
            
            if (requiresRetention) {
                scheduleAuditRetention(auditId, auditCategory, eventTimestamp);
            }
            
            updateAuditMetrics(auditCategory, auditEventType, severity);
            
            long processingTimeMs = java.time.Duration.between(processingStartTime, LocalDateTime.now()).toMillis();
            
            log.info("Audit log event processed - AuditId: {}, Type: {}, ProcessingTime: {}ms", 
                    auditId, auditEventType, processingTimeMs);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("CRITICAL: Audit log processing failed - AuditId: {}, UserId: {}, Type: {}, Error: {}", 
                    auditId, userId, auditEventType, e.getMessage(), e);
            
            if (auditId != null) {
                handleAuditFailure(auditId, userId, auditEventType, e);
            }
            
            throw new RuntimeException("Audit log processing failed", e);
        }
    }
    
    private void validateAuditEvent(UUID auditId, String auditEventType, String auditCategory,
                                   String action, String resourceType, LocalDateTime eventTimestamp) {
        if (auditId == null) {
            throw new IllegalArgumentException("Audit ID is required");
        }
        
        if (auditEventType == null || auditEventType.trim().isEmpty()) {
            throw new IllegalArgumentException("Audit event type is required");
        }
        
        if (auditCategory == null || auditCategory.trim().isEmpty()) {
            throw new IllegalArgumentException("Audit category is required");
        }
        
        if (action == null || action.trim().isEmpty()) {
            throw new IllegalArgumentException("Action is required");
        }
        
        if (resourceType == null || resourceType.trim().isEmpty()) {
            throw new IllegalArgumentException("Resource type is required");
        }
        
        if (eventTimestamp == null) {
            throw new IllegalArgumentException("Event timestamp is required");
        }
        
        log.debug("Audit event validation passed - AuditId: {}", auditId);
    }
    
    private void processAuditByCategory(UUID auditId, UUID userId, String auditEventType,
                                       String auditCategory, String action, String resourceType,
                                       String resourceId, String performedBy, String ipAddress,
                                       String userAgent, String sessionId, LocalDateTime eventTimestamp,
                                       String severity, Boolean isSensitive, Map<String, Object> auditData,
                                       Map<String, Object> beforeState, Map<String, Object> afterState) {
        try {
            switch (auditCategory) {
                case "SECURITY" -> auditLogService.recordSecurityAudit(auditId, userId, auditEventType, 
                        action, resourceType, resourceId, performedBy, ipAddress, userAgent, sessionId, 
                        eventTimestamp, severity, auditData);
                
                case "COMPLIANCE" -> auditLogService.recordComplianceAudit(auditId, userId, auditEventType, 
                        action, resourceType, resourceId, performedBy, eventTimestamp, severity, auditData);
                
                case "FINANCIAL" -> auditLogService.recordFinancialAudit(auditId, userId, auditEventType, 
                        action, resourceType, resourceId, performedBy, eventTimestamp, auditData);
                
                case "DATA_ACCESS" -> auditLogService.recordDataAccessAudit(auditId, userId, auditEventType, 
                        action, resourceType, resourceId, performedBy, ipAddress, eventTimestamp, 
                        isSensitive, auditData);
                
                case "SYSTEM" -> auditLogService.recordSystemAudit(auditId, auditEventType, action, 
                        resourceType, resourceId, performedBy, eventTimestamp, auditData);
                
                case "USER_ACTIVITY" -> auditLogService.recordUserActivityAudit(auditId, userId, 
                        auditEventType, action, resourceType, ipAddress, userAgent, sessionId, 
                        eventTimestamp, auditData);
                
                case "CONFIGURATION_CHANGE" -> auditLogService.recordConfigurationAudit(auditId, userId, 
                        auditEventType, action, resourceType, resourceId, performedBy, eventTimestamp, 
                        beforeState, afterState, auditData);
                
                default -> {
                    log.warn("Unknown audit category: {}", auditCategory);
                    auditLogService.recordGenericAudit(auditId, userId, auditEventType, auditCategory);
                }
            }
            
            log.debug("Audit category processing completed - AuditId: {}, Category: {}", 
                    auditId, auditCategory);
            
        } catch (Exception e) {
            log.error("Failed to process audit by category - AuditId: {}, Category: {}", 
                    auditId, auditCategory, e);
            throw new RuntimeException("Audit category processing failed", e);
        }
    }
    
    private void handleSecurityAudit(UUID auditId, UUID userId, String auditEventType, String action,
                                    String resourceType, String resourceId, String performedBy,
                                    String ipAddress, LocalDateTime eventTimestamp,
                                    Map<String, Object> auditData) {
        try {
            log.info("Processing SECURITY audit - AuditId: {}, Action: {}, Resource: {} ({}), IP: {}", 
                    auditId, action, resourceType, resourceId, ipAddress);
            
            securityAuditService.processSecurityAudit(auditId, userId, auditEventType, action, 
                    resourceType, resourceId, performedBy, ipAddress, eventTimestamp, auditData);
            
            if (isSecurityThreat(action, auditData)) {
                securityAuditService.flagSecurityThreat(auditId, userId, action, ipAddress, auditData);
            }
            
        } catch (Exception e) {
            log.error("Failed to handle security audit - AuditId: {}", auditId, e);
        }
    }
    
    private void handleComplianceAudit(UUID auditId, UUID userId, String auditEventType, String action,
                                      String resourceType, String resourceId, String performedBy,
                                      LocalDateTime eventTimestamp, Map<String, Object> auditData) {
        try {
            log.info("Processing COMPLIANCE audit - AuditId: {}, Action: {}, Resource: {} ({})", 
                    auditId, action, resourceType, resourceId);
            
            complianceAuditService.processComplianceAudit(auditId, userId, auditEventType, action, 
                    resourceType, resourceId, performedBy, eventTimestamp, auditData);
            
            if (requiresRegulatoryReporting(auditEventType, auditData)) {
                complianceAuditService.scheduleRegulatoryReport(auditId, userId, auditEventType, 
                        eventTimestamp, auditData);
            }
            
        } catch (Exception e) {
            log.error("Failed to handle compliance audit - AuditId: {}", auditId, e);
        }
    }
    
    private void handleFinancialAudit(UUID auditId, UUID userId, String auditEventType, String action,
                                     String resourceType, String resourceId, String performedBy,
                                     LocalDateTime eventTimestamp, Map<String, Object> auditData) {
        try {
            log.info("Processing FINANCIAL audit - AuditId: {}, Action: {}, Resource: {} ({})", 
                    auditId, action, resourceType, resourceId);
            
            auditLogService.recordFinancialAudit(auditId, userId, auditEventType, action, resourceType, 
                    resourceId, performedBy, eventTimestamp, auditData);
            
        } catch (Exception e) {
            log.error("Failed to handle financial audit - AuditId: {}", auditId, e);
        }
    }
    
    private void handleSensitiveAudit(UUID auditId, UUID userId, String auditEventType,
                                     String resourceType, String resourceId, String performedBy,
                                     LocalDateTime eventTimestamp) {
        try {
            log.info("Processing SENSITIVE audit - AuditId: {}, Type: {}, Resource: {} ({})", 
                    auditId, auditEventType, resourceType, resourceId);
            
            auditLogService.recordSensitiveAudit(auditId, userId, auditEventType, resourceType, 
                    resourceId, performedBy, eventTimestamp);
            
            securityAuditService.trackSensitiveDataAccess(auditId, userId, resourceType, resourceId, 
                    performedBy, eventTimestamp);
            
        } catch (Exception e) {
            log.error("Failed to handle sensitive audit - AuditId: {}", auditId, e);
        }
    }
    
    private void scheduleAuditRetention(UUID auditId, String auditCategory, LocalDateTime eventTimestamp) {
        try {
            auditRetentionService.scheduleRetention(auditId, auditCategory, eventTimestamp);
            
            log.debug("Audit retention scheduled - AuditId: {}, Category: {}", auditId, auditCategory);
            
        } catch (Exception e) {
            log.error("Failed to schedule audit retention - AuditId: {}", auditId, e);
        }
    }
    
    private void updateAuditMetrics(String auditCategory, String auditEventType, String severity) {
        try {
            auditLogService.updateAuditMetrics(auditCategory, auditEventType, severity);
        } catch (Exception e) {
            log.error("Failed to update audit metrics - Category: {}, Type: {}", 
                    auditCategory, auditEventType, e);
        }
    }
    
    private void handleAuditFailure(UUID auditId, UUID userId, String auditEventType, Exception error) {
        try {
            auditLogService.handleAuditFailure(auditId, userId, auditEventType, error.getMessage());
            
            log.error("Audit log processing failed - AuditId: {}, UserId: {}, Type: {} - Error: {}", 
                    auditId, userId, auditEventType, error.getMessage());
            
        } catch (Exception e) {
            log.error("Failed to handle audit failure - AuditId: {}", auditId, e);
        }
    }
    
    private boolean isSecurityThreat(String action, Map<String, Object> auditData) {
        return action != null && (action.contains("UNAUTHORIZED") || action.contains("FAILED_LOGIN") || 
                action.contains("SUSPICIOUS") || action.contains("BREACH"));
    }
    
    private boolean requiresRegulatoryReporting(String auditEventType, Map<String, Object> auditData) {
        return auditEventType != null && (auditEventType.contains("TRANSACTION_LARGE") || 
                auditEventType.contains("COMPLIANCE_VIOLATION") || auditEventType.contains("REGULATORY"));
    }
    
    @KafkaListener(
        topics = {"audit-log-events.DLQ", "audit-logs.DLQ", "security-audit-events.DLQ", "compliance-audit-events.DLQ"},
        groupId = "audit-service-log-dlq-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleDlq(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "x-original-topic", required = false) String originalTopic,
            @Header(value = "x-error-message", required = false) String errorMessage,
            @Header(value = "x-error-class", required = false) String errorClass,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp) {
        
        log.error("CRITICAL: Audit log event sent to DLQ - OriginalTopic: {}, Error: {}, ErrorClass: {}, Event: {}", 
                originalTopic, errorMessage, errorClass, eventJson);
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            UUID auditId = event.containsKey("auditId") ? 
                    UUID.fromString((String) event.get("auditId")) : null;
            UUID userId = event.containsKey("userId") ? 
                    UUID.fromString((String) event.get("userId")) : null;
            String auditEventType = (String) event.get("auditEventType");
            
            log.error("DLQ: Audit log failed permanently - AuditId: {}, UserId: {}, Type: {} - CRITICAL DATA LOSS", 
                    auditId, userId, auditEventType);
            
            if (auditId != null) {
                auditLogService.markForManualReview(auditId, userId, auditEventType, 
                        "DLQ: " + errorMessage);
            }
            
        } catch (Exception e) {
            log.error("Failed to parse audit log DLQ event: {}", eventJson, e);
        }
    }
}