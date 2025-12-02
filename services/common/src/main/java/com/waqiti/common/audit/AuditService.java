package com.waqiti.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.FinancialAuditLog.TransactionType;
import com.waqiti.common.audit.FinancialAuditLog.TransactionStatus;
import com.waqiti.common.events.model.AuditEvent;
import com.waqiti.common.kafka.dlq.DlqMessageMetadata;
import com.waqiti.common.kafka.dlq.RecoveryResult;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.common.security.SecurityContext;
import com.waqiti.common.service.GeoLocationService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Comprehensive audit logging service for financial transactions
 * Ensures compliance with regulatory requirements for transaction logging
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final MongoTemplate mongoTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SecurityContext securityContext;
    private final ObjectMapper objectMapper;
    private final GeoLocationService geoLocationService;
    private final MetricsService metricsService;
    private final MeterRegistry meterRegistry;
    
    /**
     * Log financial transaction with comprehensive details
     */
    public void logFinancialTransaction(FinancialAuditLog auditLog) {
        try {
            // Set audit metadata
            auditLog.setId(UUID.randomUUID());
            auditLog.setTimestamp(LocalDateTime.now());
            
            // Persist to database for compliance
            AuditLogEntity entity = mapToEntity(auditLog);
            mongoTemplate.save(entity, "audit_logs");
            
            // Publish to Kafka for real-time monitoring
            publishToKafka("financial-audit-logs", auditLog.getId().toString(), convertToAuditEvent(auditLog));
            
            // Log to application logs
            log.info("Financial audit log: {} - Type: {} - Amount: {} {} - User: {} - Status: {}",
                    auditLog.getTransactionId(),
                    auditLog.getTransactionType(),
                    auditLog.getAmount(),
                    auditLog.getCurrency(),
                    auditLog.getUserId(),
                    auditLog.getStatus());
            
        } catch (Exception e) {
            log.error("Failed to log financial transaction audit", e);
            // Never fail the transaction due to audit logging failure
            // but alert monitoring system
            alertAuditFailure(auditLog, e);
        }
    }
    
    /**
     * Log payment transaction
     */
    public void logPaymentTransaction(String transactionId, String userId, String recipientId,
                                     BigDecimal amount, String currency, TransactionType type,
                                     TransactionStatus status, Map<String, Object> metadata) {
        FinancialAuditLog auditLog = FinancialAuditLog.builder()
                .transactionId(transactionId)
                .userId(userId)
                .recipientId(recipientId)
                .amount(amount)
                .currency(currency)
                .transactionType(type)
                .status(status)
                .metadata(metadata)
                .ipAddress(extractIpAddress())
                .userAgent(extractUserAgent())
                .sessionId(extractSessionId())
                .build();
        
        logFinancialTransaction(auditLog);
    }
    
    /**
     * Log security event from SecurityAuditRequest
     */
    public void logSecurityEvent(AuditModels.SecurityAuditRequest request) {
        SecurityAuditLog securityLog = SecurityAuditLog.builder()
            .eventType(request.getEventType())
            .userId(request.getUserId())
            .ipAddress(request.getIpAddress())
            .userAgent(request.getUserAgent())
            .sessionId(request.getSessionId())
            .resource(request.getResource())
            .action(request.getAction())
            .success(request.isSuccess())
            .failureReason(request.getFailureReason())
            .severity(SecurityAuditLog.SecuritySeverity.valueOf(request.getRiskLevel()))
            .metadata(request.getMetadata())
            .build();
        logSecurityEvent(securityLog);
    }
    
    /**
     * Log security event
     */
    public void logSecurityEvent(SecurityAuditLog securityLog) {
        try {
            securityLog.setId(UUID.randomUUID());
            securityLog.setTimestamp(LocalDateTime.now());
            
            // Persist security event
            SecurityAuditEntity entity = mapToSecurityEntity(securityLog);
            mongoTemplate.save(entity, "security_audit_logs");
            
            // Publish for real-time security monitoring
            publishToKafka("security-audit-logs", securityLog.getId().toString(), convertToAuditEvent(securityLog));
            
            // Log based on severity
            switch (securityLog.getSeverity()) {
                case CRITICAL:
                    log.error("CRITICAL SECURITY EVENT: {} - User: {} - IP: {}",
                            securityLog.getEventType(), securityLog.getUserId(), securityLog.getIpAddress());
                    break;
                case HIGH:
                    log.warn("HIGH SECURITY EVENT: {} - User: {} - IP: {}",
                            securityLog.getEventType(), securityLog.getUserId(), securityLog.getIpAddress());
                    break;
                default:
                    log.info("Security event: {} - User: {}", securityLog.getEventType(), securityLog.getUserId());
            }
            
        } catch (Exception e) {
            log.error("Failed to log security event", e);
        }
    }
    
    /**
     * Log API access
     */
    public void logApiAccess(ApiAuditLog apiLog) {
        try {
            apiLog.setId(UUID.randomUUID());
            apiLog.setTimestamp(LocalDateTime.now());
            
            // Calculate response time
            Long startTime = apiLog.getStartTime();
            Long endTime = apiLog.getEndTime();
            if (startTime != null && endTime != null) {
                long responseTime = endTime - startTime;
                apiLog.setResponseTimeMs(responseTime);
            }
            
            // Log slow APIs
            Long responseTimeMs = apiLog.getResponseTimeMs();
            if (responseTimeMs != null && responseTimeMs > 1000) {
                log.warn("Slow API detected: {} - {}ms - User: {}",
                        apiLog.getEndpoint(), apiLog.getResponseTimeMs(), apiLog.getUserId());
            }
            
            // Async persist to avoid impacting API performance
            publishToKafka("api-audit-logs", apiLog.getId().toString(), convertToAuditEvent(apiLog));
            
        } catch (Exception e) {
            log.debug("Failed to log API access", e);
        }
    }
    
    /**
     * Log data access for GDPR compliance
     */
    public void logDataAccess(DataAccessAuditLog dataLog) {
        try {
            dataLog.setId(UUID.randomUUID());
            dataLog.setTimestamp(LocalDateTime.now());
            
            // Log sensitive data access
            if (dataLog.isSensitiveData()) {
                log.info("Sensitive data accessed: {} - User: {} - Purpose: {}",
                        dataLog.getDataType(), dataLog.getAccessedBy(), dataLog.getPurpose());
            }
            
            // Persist for compliance
            DataAccessEntity entity = mapToDataAccessEntity(dataLog);
            mongoTemplate.save(entity, "data_access_logs");
            
            // Publish for monitoring
            publishToKafka("data-access-logs", dataLog.getId().toString(), convertToAuditEvent(dataLog));
            
        } catch (Exception e) {
            log.error("Failed to log data access", e);
        }
    }
    
    /**
     * Log compliance event
     */
    public void logComplianceEvent(ComplianceAuditLog complianceLog) {
        try {
            complianceLog.setId(UUID.randomUUID());
            complianceLog.setTimestamp(LocalDateTime.now());
            
            // Always persist compliance events
            ComplianceEntity entity = mapToComplianceEntity(complianceLog);
            mongoTemplate.save(entity, "compliance_logs");
            
            // Alert on compliance violations
            if (complianceLog.isViolation()) {
                log.error("COMPLIANCE VIOLATION: {} - Regulation: {} - User: {}",
                        complianceLog.getEventType(),
                        complianceLog.getRegulation(),
                        complianceLog.getUserId());
                
                // Send immediate alert
                alertComplianceViolation(complianceLog);
            }
            
            // Publish for reporting
            publishToKafka("compliance-audit-logs", complianceLog.getId().toString(), convertToAuditEvent(complianceLog));
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to log compliance event", e);
            // Compliance logging must not fail silently
            throw new AuditException("Failed to log compliance event", e);
        }
    }
    
    /**
     * Log compliance event with simple parameters
     */
    public void logComplianceEvent(String eventType, String resourceId, Map<String, Object> eventData) {
        log.info("Logging compliance event: type={}, resourceId={}", eventType, resourceId);

        try {
            ComplianceAuditLog complianceLog = new ComplianceAuditLog();
            complianceLog.setEventType(eventType);
            complianceLog.setResourceId(resourceId);
            complianceLog.setEventData(eventData);
            complianceLog.setUserId((String) eventData.getOrDefault("userId", "SYSTEM"));

            logComplianceEvent(complianceLog);
        } catch (Exception e) {
            log.error("Failed to log compliance event: {}", eventType, e);
        }
    }

    /**
     * Search audit logs
     */
    public Page<AuditLogEntity> searchAuditLogs(AuditSearchCriteria criteria, Pageable pageable) {
        Query query = buildSearchQuery(criteria);
        long total = mongoTemplate.count(query, AuditLogEntity.class, "audit_logs");
        query.with(pageable);
        List<AuditLogEntity> results = mongoTemplate.find(query, AuditLogEntity.class, "audit_logs");
        return new PageImpl<>(results, pageable, total);
    }
    
    /**
     * Export audit logs for regulatory reporting
     */
    public byte[] exportAuditLogs(LocalDateTime startDate, LocalDateTime endDate, AuditType type) {
        Query query = new Query(Criteria.where("timestamp").gte(startDate).lte(endDate).and("type").is(type));
        List<AuditLogEntity> logs = mongoTemplate.find(query, AuditLogEntity.class, "audit_logs");
        return generateAuditReport(logs);
    }
    
    /**
     * Audit financial events (for ledger and accounting operations)
     */
    public void auditFinancialEvent(String eventType, String userId, String message, Map<String, Object> eventData) {
        try {
            FinancialAuditLog auditLog = FinancialAuditLog.builder()
                    .transactionId(UUID.randomUUID().toString())
                    .userId(userId != null ? userId : "SYSTEM")
                    .transactionType(TransactionType.ACCOUNTING_EVENT)
                    .status(TransactionStatus.COMPLETED)
                    .metadata(eventData != null ? new HashMap<>(eventData) : new HashMap<>())
                    .ipAddress(extractIpAddress())
                    .userAgent(extractUserAgent())
                    .sessionId(extractSessionId())
                    .build();

            auditLog.getMetadata().put("eventType", eventType);
            auditLog.getMetadata().put("message", message);

            logFinancialTransaction(auditLog);
        } catch (Exception e) {
            log.error("Failed to audit financial event: {}", eventType, e);
        }
    }

    /**
     * Audit security events (with userId)
     */
    public void auditSecurityEvent(String eventType, String userId, String description, Map<String, ?> metadata) {
        try {
            FinancialAuditLog auditLog = FinancialAuditLog.builder()
                    .transactionId(UUID.randomUUID().toString())
                    .userId(userId)
                    .transactionType(TransactionType.SECURITY_EVENT)
                    .status(TransactionStatus.COMPLETED)
                    .metadata(new HashMap<>(metadata))
                    .ipAddress(extractIpAddress())
                    .userAgent(extractUserAgent())
                    .sessionId(extractSessionId())
                    .build();

            mongoTemplate.save(auditLog, "security_audit_logs");
            publishToKafka("security-audit-logs", auditLog.getTransactionId(), convertToAuditEvent(auditLog));

            log.info("Security event audited: {} - UserId: {}", eventType, userId);
        } catch (Exception e) {
            log.error("Failed to audit security event", e);
        }
    }

    /**
     * Audit security events
     */
    public void auditSecurityEvent(String eventType, String description, Map<String, ?> metadata) {
        try {
            FinancialAuditLog auditLog = FinancialAuditLog.builder()
                    .transactionId(UUID.randomUUID().toString())
                    .userId(extractUserId())
                    .transactionType(TransactionType.SECURITY_EVENT)
                    .status(TransactionStatus.COMPLETED)
                    .metadata(new HashMap<>(metadata))
                    .ipAddress(extractIpAddress())
                    .userAgent(extractUserAgent())
                    .sessionId(extractSessionId())
                    .build();

            auditLog.getMetadata().put("eventType", eventType);
            auditLog.getMetadata().put("description", description);

            logFinancialTransaction(auditLog);
        } catch (Exception e) {
            log.error("Failed to audit security event: {}", eventType, e);
        }
    }
    
    /**
     * Audit compliance events
     */
    public void auditComplianceEvent(com.waqiti.common.events.compliance.ComplianceAuditEvent event) {
        try {
            FinancialAuditLog auditLog = FinancialAuditLog.builder()
                    .transactionId(event.getTransactionId() != null ? event.getTransactionId() : UUID.randomUUID().toString())
                    .userId(event.getUserId() != null ? event.getUserId() : extractUserId())
                    .transactionType(TransactionType.SECURITY_EVENT)
                    .status(TransactionStatus.COMPLETED)
                    .metadata(new HashMap<>())
                    .ipAddress(event.getIpAddress() != null ? event.getIpAddress() : extractIpAddress())
                    .userAgent(event.getUserAgent() != null ? event.getUserAgent() : extractUserAgent())
                    .sessionId(extractSessionId())
                    .build();
            
            // Add compliance-specific metadata
            auditLog.getMetadata().put("eventType", event.getEventType());
            auditLog.getMetadata().put("entityName", event.getEntityName());
            auditLog.getMetadata().put("riskLevel", event.getRiskLevel() != null ? event.getRiskLevel().toString() : "UNKNOWN");
            auditLog.getMetadata().put("requiresInvestigation", event.isRequiresInvestigation());
            auditLog.getMetadata().put("requiresImmediateAction", event.isRequiresImmediateAction());
            
            if (event.getScreeningResult() != null) {
                auditLog.getMetadata().put("isMatch", event.isMatch());
                auditLog.getMetadata().put("confidenceScore", event.getConfidenceScore());
            }
            
            if (event.getProvidersUsed() != null) {
                auditLog.getMetadata().put("providersUsed", event.getProvidersUsed());
            }
            
            if (event.getOverrideReason() != null) {
                auditLog.getMetadata().put("overrideReason", event.getOverrideReason());
                auditLog.getMetadata().put("authorizedBy", event.getAuthorizedBy());
            }
            
            if (event.getErrorMessage() != null) {
                auditLog.getMetadata().put("errorMessage", event.getErrorMessage());
                auditLog.getMetadata().put("errorCode", event.getErrorCode());
            }
            
            logFinancialTransaction(auditLog);
        } catch (Exception e) {
            log.error("Failed to audit compliance event: {}", event.getEventType(), e);
        }
    }
    
    /**
     * Get audit trail for a specific transaction
     */
    public List<AuditLogEntity> getTransactionAuditTrail(String transactionId) {
        Query query = new Query(Criteria.where("transactionId").is(transactionId));
        return mongoTemplate.find(query, AuditLogEntity.class, "audit_logs");
    }
    
    // Private helper methods
    
    /**
     * Convert audit log types to AuditEvent
     */
    public com.waqiti.common.events.model.AuditEvent convertToAuditEvent(FinancialAuditLog auditLog) {
        return com.waqiti.common.events.model.AuditEvent.builder()
                .eventId(auditLog.getId() != null ? auditLog.getId().toString() : UUID.randomUUID().toString())
                .eventType("FINANCIAL_TRANSACTION")
                .timestamp(auditLog.getTimestamp() != null ? 
                          auditLog.getTimestamp().atZone(java.time.ZoneId.systemDefault()).toInstant() : 
                          java.time.Instant.now())
                .userId(auditLog.getUserId())
                .action(auditLog.getTransactionType() != null ? auditLog.getTransactionType().name() : "UNKNOWN")
                .resource(auditLog.getTransactionId())
                .outcome(auditLog.getStatus() != null ? auditLog.getStatus().name() : "UNKNOWN")
                .ipAddress(auditLog.getIpAddress())
                .userAgent(auditLog.getUserAgent())
                .details(auditLog.getMetadata())
                .complianceCategory("FINANCIAL")
                .retainIndefinitely(true)
                .build();
    }
    
    public com.waqiti.common.events.model.AuditEvent convertToAuditEvent(SecurityAuditLog auditLog) {
        return com.waqiti.common.events.model.AuditEvent.builder()
                .eventId(auditLog.getId() != null ? auditLog.getId().toString() : UUID.randomUUID().toString())
                .eventType("SECURITY_EVENT")
                .timestamp(auditLog.getTimestamp() != null ? 
                          auditLog.getTimestamp().atZone(java.time.ZoneId.systemDefault()).toInstant() : 
                          java.time.Instant.now())
                .userId(auditLog.getUserId())
                .action(auditLog.getEventType())
                .resource(auditLog.getResource())
                .outcome(auditLog.getOutcome() != null ? auditLog.getOutcome().name() : "UNKNOWN")
                .ipAddress(auditLog.getIpAddress())
                .userAgent(auditLog.getUserAgent())
                .details(auditLog.getDetails())
                .complianceCategory("SECURITY")
                .retainIndefinitely(true)
                .build();
    }
    
    public com.waqiti.common.events.model.AuditEvent convertToAuditEvent(ApiAuditLog auditLog) {
        Map<String, Object> details = Map.of(
            "endpoint", auditLog.getApiEndpoint(),
            "method", auditLog.getHttpMethod(),
            "statusCode", auditLog.getStatusCode(),
            "responseTimeMs", auditLog.getResponseTimeMs() != null ? auditLog.getResponseTimeMs() : 0,
            "requestPath", auditLog.getRequestPath() != null ? auditLog.getRequestPath() : ""
        );
        
        return com.waqiti.common.events.model.AuditEvent.builder()
                .eventId(auditLog.getId() != null ? auditLog.getId().toString() : UUID.randomUUID().toString())
                .eventType("API_ACCESS")
                .timestamp(auditLog.getTimestamp() != null ? 
                          auditLog.getTimestamp().atZone(java.time.ZoneId.systemDefault()).toInstant() : 
                          java.time.Instant.now())
                .userId(auditLog.getUserId())
                .action(auditLog.getHttpMethod())
                .resource(auditLog.getApiEndpoint())
                .outcome(String.valueOf(auditLog.getStatusCode()))
                .ipAddress(auditLog.getIpAddress())
                .userAgent(auditLog.getUserAgent())
                .details(details)
                .complianceCategory("API_ACCESS")
                .retainIndefinitely(false)
                .build();
    }
    
    public com.waqiti.common.events.model.AuditEvent convertToAuditEvent(DataAccessAuditLog auditLog) {
        Map<String, Object> details = Map.of(
            "tableName", auditLog.getTableName() != null ? auditLog.getTableName() : "",
            "operation", auditLog.getOperation() != null ? auditLog.getOperation() : "",
            "recordId", auditLog.getRecordId() != null ? auditLog.getRecordId() : "",
            "fieldName", auditLog.getFieldName() != null ? auditLog.getFieldName() : "",
            "sensitiveData", auditLog.isSensitiveData()
        );
        
        return com.waqiti.common.events.model.AuditEvent.builder()
                .eventId(auditLog.getId() != null ? auditLog.getId().toString() : UUID.randomUUID().toString())
                .eventType("DATA_ACCESS")
                .timestamp(auditLog.getTimestamp() != null ? 
                          auditLog.getTimestamp().atZone(java.time.ZoneId.systemDefault()).toInstant() : 
                          java.time.Instant.now())
                .userId(auditLog.getUserId())
                .action(auditLog.getOperation())
                .resource(auditLog.getTableName())
                .outcome("SUCCESS")
                .ipAddress(auditLog.getIpAddress())
                .userAgent("N/A")
                .details(details)
                .complianceCategory("DATA_ACCESS")
                .retainIndefinitely(auditLog.isSensitiveData())
                .build();
    }
    
    public com.waqiti.common.events.model.AuditEvent convertToAuditEvent(ComplianceAuditLog auditLog) {
        Map<String, Object> details = Map.of(
            "complianceFramework", auditLog.getComplianceFramework() != null ? auditLog.getComplianceFramework() : "",
            "regulationRule", auditLog.getRegulationRule() != null ? auditLog.getRegulationRule() : "",
            "description", auditLog.getDescription() != null ? auditLog.getDescription() : "",
            "level", auditLog.getLevel() != null ? auditLog.getLevel().name() : "INFO",
            "status", auditLog.getStatus() != null ? auditLog.getStatus() : "PENDING_REVIEW",
            "isViolation", auditLog.isViolation()
        );
        
        return com.waqiti.common.events.model.AuditEvent.builder()
                .eventId(auditLog.getId() != null ? auditLog.getId().toString() : UUID.randomUUID().toString())
                .eventType("COMPLIANCE_EVENT")
                .timestamp(auditLog.getTimestamp() != null ? 
                          auditLog.getTimestamp().atZone(java.time.ZoneId.systemDefault()).toInstant() : 
                          java.time.Instant.now())
                .userId(auditLog.getUserId())
                .action(auditLog.getEventType())
                .resource(auditLog.getComplianceFramework())
                .outcome(auditLog.isViolation() ? "VIOLATION" : "COMPLIANT")
                .ipAddress("N/A")
                .userAgent("N/A")
                .details(details)
                .complianceCategory(auditLog.getComplianceFramework())
                .retainIndefinitely(true)
                .build();
    }
    
    /**
     * Convert SecuritySeverity string to enum
     */
    private SecurityAuditLog.SecuritySeverity convertToSecuritySeverity(String severity) {
        if (severity == null) return SecurityAuditLog.SecuritySeverity.MEDIUM;
        try {
            return SecurityAuditLog.SecuritySeverity.valueOf(severity.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid security severity: {}, defaulting to MEDIUM", severity);
            return SecurityAuditLog.SecuritySeverity.MEDIUM;
        }
    }
    
    /**
     * Wrapper method to publish audit events as generic objects
     */
    private void publishToKafka(String topic, String key, Object event) {
        try {
            kafkaTemplate.send(topic, key, event);
        } catch (Exception e) {
            log.error("Failed to publish to Kafka topic {}: {}", topic, e.getMessage());
        }
    }
    
    private AuditLogEntity mapToEntity(FinancialAuditLog auditLog) {
        return AuditLogEntity.builder()
                .id(auditLog.getId())
                .transactionId(auditLog.getTransactionId())
                .userId(auditLog.getUserId())
                .type(AuditType.FINANCIAL)
                .action(auditLog.getTransactionType() != null ? auditLog.getTransactionType().name() : "UNKNOWN")
                .status(auditLog.getStatus() != null ? auditLog.getStatus().name() : "UNKNOWN")
                .amount(auditLog.getAmount())
                .currency(auditLog.getCurrency())
                .metadataJson(auditLog.getMetadata() != null ? objectMapper.valueToTree(auditLog.getMetadata()) : null)
                .ipAddress(auditLog.getIpAddress())
                .userAgent(auditLog.getUserAgent())
                .timestamp(auditLog.getTimestamp())
                .build();
    }
    
    private SecurityAuditEntity mapToSecurityEntity(SecurityAuditLog securityLog) {
        return SecurityAuditEntity.builder()
                .id(securityLog.getId())
                .eventType(securityLog.getEventType())
                .userId(securityLog.getUserId())
                .category(securityLog.getCategory())
                .severity(securityLog.getSeverity())
                .description(securityLog.getDescription())
                .ipAddress(securityLog.getIpAddress())
                .userAgent(securityLog.getUserAgent())
                .sessionId(securityLog.getSessionId())
                .resource(securityLog.getResource())
                .action(securityLog.getAction())
                .outcome(securityLog.getOutcome())
                .threatLevel(securityLog.getThreatLevel())
                .sourceService(securityLog.getSourceService())
                .correlationId(securityLog.getCorrelationId())
                .detectionMethod(securityLog.getDetectionMethod())
                .mitigationAction(securityLog.getMitigationAction())
                .requiresAlert(securityLog.getRequiresAlert())
                .timestamp(securityLog.getTimestamp())
                .build();
    }
    
    private DataAccessEntity mapToDataAccessEntity(DataAccessAuditLog dataLog) {
        return DataAccessEntity.builder()
                .id(dataLog.getId())
                .userId(dataLog.getUserId())
                .dataType(dataLog.getDataType())
                .tableName(dataLog.getTableName())
                .operation(dataLog.getOperation())
                .recordId(dataLog.getRecordId())
                .fieldName(dataLog.getFieldName())
                .oldValue(dataLog.getOldValue())
                .newValue(dataLog.getNewValue())
                .accessReason(dataLog.getAccessReason())
                .sqlQuery(dataLog.getSqlQuery())
                .queryExecutionTimeMs(dataLog.getQueryExecutionTimeMs())
                .recordsAffected(dataLog.getRecordsAffected())
                .ipAddress(dataLog.getIpAddress())
                .sessionId(dataLog.getSessionId())
                .applicationName(dataLog.getApplicationName())
                .timestamp(dataLog.getTimestamp())
                .build();
    }
    
    private ComplianceEntity mapToComplianceEntity(ComplianceAuditLog complianceLog) {
        // Convert string status to enum
        ComplianceAuditLog.ComplianceStatus statusEnum = null;
        if (complianceLog.getStatus() != null) {
            try {
                statusEnum = ComplianceAuditLog.ComplianceStatus.valueOf(complianceLog.getStatus());
            } catch (IllegalArgumentException e) {
                statusEnum = ComplianceAuditLog.ComplianceStatus.PENDING_REVIEW;
            }
        }
        
        return ComplianceEntity.builder()
                .id(complianceLog.getId())
                .eventType(complianceLog.getEventType())
                .complianceFramework(complianceLog.getComplianceFramework())
                .regulationRule(complianceLog.getRegulationRule())
                .description(complianceLog.getDescription())
                .level(complianceLog.getLevel())
                .status(statusEnum)
                .violationDetails(complianceLog.getViolationDetails())
                .remediationAction(complianceLog.getRemediationAction())
                .approverUserId(complianceLog.getApproverUserId())
                .documentReference(complianceLog.getDocumentReference())
                .userId(complianceLog.getUserId())
                .timestamp(complianceLog.getTimestamp())
                .dueDate(complianceLog.getDueDate())
                .build();
    }
    
    private void alertAuditFailure(FinancialAuditLog auditLog, Exception e) {
        // Send alert to monitoring system
        Map<String, Object> alert = Map.of(
                "type", "AUDIT_FAILURE",
                "severity", "HIGH",
                "transactionId", auditLog.getTransactionId(),
                "error", e.getMessage(),
                "timestamp", LocalDateTime.now()
        );
        
        publishToKafka("system-alerts", "AUDIT_FAILURE", alert);
    }
    
    private void alertComplianceViolation(ComplianceAuditLog complianceLog) {
        // Send immediate alert for compliance violations
        Map<String, Object> alert = Map.of(
                "type", "COMPLIANCE_VIOLATION",
                "severity", "CRITICAL",
                "violation", complianceLog.getEventType(),
                "regulation", complianceLog.getRegulation(),
                "userId", complianceLog.getUserId(),
                "timestamp", LocalDateTime.now()
        );
        
        publishToKafka("compliance-alerts", "COMPLIANCE_VIOLATION", alert);
    }
    
    private Query buildSearchQuery(AuditSearchCriteria criteria) {
        Query query = new Query();
        
        if (criteria.getUserId() != null) {
            query.addCriteria(Criteria.where("userId").is(criteria.getUserId()));
        }
        
        if (criteria.getTransactionId() != null) {
            query.addCriteria(Criteria.where("transactionId").is(criteria.getTransactionId()));
        }
        
        if (criteria.getStartDate() != null && criteria.getEndDate() != null) {
            query.addCriteria(Criteria.where("timestamp").gte(criteria.getStartDate()).lte(criteria.getEndDate()));
        }
        
        if (criteria.getType() != null) {
            query.addCriteria(Criteria.where("type").is(criteria.getType()));
        }
        
        return query;
    }
    
    private String extractIpAddress() {
        // Extract from request context
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            return attributes.getRequest().getRemoteAddr();
        }
        return "unknown";
    }
    
    private String extractUserAgent() {
        // Extract from request context
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            return attributes.getRequest().getHeader("User-Agent");
        }
        return "unknown";
    }
    
    private String extractSessionId() {
        // Extract from security context
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            return attributes.getRequest().getSession(false) != null ? 
                   attributes.getRequest().getSession(false).getId() : "no-session";
        }
        return "no-session";
    }
    
    private String extractUserId() {
        // Extract from security context
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() && 
                !"anonymousUser".equals(authentication.getName())) {
                return authentication.getName();
            }
        } catch (Exception e) {
            log.debug("Unable to extract user ID from security context: {}", e.getMessage());
        }
        return "anonymous";
    }
    
    private byte[] generateAuditReport(List<AuditLogEntity> logs) {
        // Generate CSV or PDF report for regulatory submission
        // Implementation would use reporting library
        return new byte[0];
    }
    
    /**
     * Log security violation
     */
    public void logSecurityViolation(jakarta.servlet.http.HttpServletRequest request, String description) {
        SecurityAuditLog securityLog = SecurityAuditLog.builder()
                .id(UUID.randomUUID())
                .userId(extractUserIdFromRequest(request))
                .eventType("SECURITY_VIOLATION")
                .category(SecurityAuditLog.SecurityEventCategory.POLICY_VIOLATION)
                .severity(SecurityAuditLog.SecuritySeverity.HIGH)
                .description(description)
                .ipAddress(request.getRemoteAddr())
                .userAgent(request.getHeader("User-Agent"))
                .sessionId(request.getSession(false) != null ? request.getSession(false).getId() : null)
                .timestamp(LocalDateTime.now())
                .requiresAlert(true)
                .build();
        
        logSecurityEvent(securityLog);
    }
    
    /**
     * Log authentication failure
     */
    public void logAuthenticationFailure(jakarta.servlet.http.HttpServletRequest request) {
        SecurityAuditLog securityLog = SecurityAuditLog.createAuthEvent(
                extractUserIdFromRequest(request),
                "LOGIN",
                SecurityAuditLog.SecurityOutcome.FAILURE,
                request.getRemoteAddr()
        );
        logSecurityEvent(securityLog);
    }
    
    /**
     * Log payment failure
     */
    public void logPaymentFailure(jakarta.servlet.http.HttpServletRequest request, String reason) {
        FinancialAuditLog.TransactionType transactionType = FinancialAuditLog.TransactionType.PAYMENT;
        FinancialAuditLog.TransactionStatus status = FinancialAuditLog.TransactionStatus.FAILED;
        
        FinancialAuditLog auditLog = FinancialAuditLog.builder()
                .id(UUID.randomUUID())
                .transactionId(UUID.randomUUID().toString())
                .userId(extractUserIdFromRequest(request))
                .transactionType(transactionType)
                .status(status)
                .amount(java.math.BigDecimal.ZERO)
                .currency("USD")
                .metadata(Map.of("failure_reason", reason))
                .ipAddress(request.getRemoteAddr())
                .timestamp(LocalDateTime.now())
                .build();
                
        logFinancialTransaction(auditLog);
    }
    
    /**
     * Log payment error
     */
    public void logPaymentError(jakarta.servlet.http.HttpServletRequest request, String error) {
        logPaymentFailure(request, error);
    }
    
    /**
     * Log system error
     */
    public void logSystemError(String system, Exception exception) {
        SecurityAuditLog securityLog = SecurityAuditLog.builder()
                .id(UUID.randomUUID())
                .userId("SYSTEM")
                .eventType("SYSTEM_ERROR")
                .category(SecurityAuditLog.SecurityEventCategory.SYSTEM_COMPROMISE)
                .severity(SecurityAuditLog.SecuritySeverity.HIGH)
                .description("System error in " + system + ": " + exception.getMessage())
                .sourceService(system)
                .timestamp(LocalDateTime.now())
                .requiresAlert(true)
                .build();
                
        logSecurityEvent(securityLog);
    }
    
    /**
     * Log API call
     */
    public void logApiCall(String endpoint, String method, int statusCode, Long responseTimeMs, Map<String, Object> metadata) {
        ApiAuditLog apiLog = ApiAuditLog.builder()
                .id(UUID.randomUUID())
                .userId(extractCurrentUserId())
                .apiEndpoint(endpoint)
                .httpMethod(method)
                .statusCode(statusCode)
                .responseTimeMs(responseTimeMs)
                .metadata(metadata)
                .timestamp(LocalDateTime.now())
                .build();
                
        logApiAccess(apiLog);
    }
    
    /**
     * Save audit event
     */
    public void saveAuditEvent(AuditEvent auditEvent) {
        try {
            AuditLogEntity entity = AuditLogEntity.builder()
                    .id(UUID.fromString(auditEvent.getEventId()))
                    .userId(auditEvent.getUserId())
                    .type(AuditType.OTHER)
                    .action(auditEvent.getAction())
                    .entityType(auditEvent.getEntityType())
                    .entityId(auditEvent.getEntityId())
                    .ipAddress(auditEvent.getIpAddress())
                    .timestamp(auditEvent.getTimestamp().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime())
                    .build();
                    
            mongoTemplate.save(entity, "audit_logs");
        } catch (Exception e) {
            log.error("Failed to save audit event", e);
        }
    }
    
    /**
     * Log transaction
     */
    public void logTransaction(com.waqiti.common.audit.dto.AuditRequestDTOs.TransactionAuditRequest request) {
        FinancialAuditLog auditLog = FinancialAuditLog.builder()
                .id(UUID.randomUUID())
                .transactionId(request.getTransactionId())
                .userId(request.getUserId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .transactionType(FinancialAuditLog.TransactionType.valueOf(request.getType().toUpperCase()))
                .status(FinancialAuditLog.TransactionStatus.valueOf(request.getStatus().toUpperCase()))
                .metadata(request.getMetadata())
                .timestamp(LocalDateTime.now())
                .build();
                
        logFinancialTransaction(auditLog);
    }
    
    private String extractUserIdFromRequest(jakarta.servlet.http.HttpServletRequest request) {
        // Extract user ID from security context or JWT token
        try {
            return org.springframework.security.core.context.SecurityContextHolder.getContext()
                    .getAuthentication().getName();
        } catch (Exception e) {
            return "anonymous";
        }
    }
    
    private String extractCurrentUserId() {
        return extractUserIdFromRequest(null);
    }
    
    /**
     * Audit Kafka events - comprehensive Kafka operation logging
     */
    public void auditKafkaEvent(String eventType, String groupId, String description, Map<String, ?> metadata) {
        try {
            FinancialAuditLog auditLog = FinancialAuditLog.builder()
                    .transactionId(UUID.randomUUID().toString())
                    .userId(extractUserId())
                    .transactionType(TransactionType.SECURITY_EVENT)
                    .status(TransactionStatus.COMPLETED)
                    .metadata(new HashMap<>(metadata))
                    .ipAddress(extractIpAddress())
                    .userAgent(extractUserAgent())
                    .sessionId(extractSessionId())
                    .build();
            
            auditLog.getMetadata().put("eventType", eventType);
            auditLog.getMetadata().put("description", description);
            auditLog.getMetadata().put("consumerGroup", groupId);
            auditLog.getMetadata().put("component", "KAFKA");
            
            logFinancialTransaction(auditLog);
        } catch (Exception e) {
            log.error("Failed to audit Kafka event: {}", eventType, e);
        }
    }
    
    /**
     * Audit fraud detection events - comprehensive fraud operation logging
     */
    public void auditFraudDetection(com.waqiti.common.fraud.ComprehensiveFraudDetectionService.FraudDetectionEvent event) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            if (event.getMetadata() != null) {
                metadata.putAll(event.getMetadata());
            }
            metadata.put("eventType", event.getEventType());
            metadata.put("detectionMethod", event.getDetectionMethod());
            metadata.put("riskScore", event.getRiskScore());
            metadata.put("description", event.getDescription());
            metadata.put("blocked", event.isBlocked());
            
            if (event.getRoutingNumber() != null) {
                metadata.put("routingNumber", event.getRoutingNumber());
            }
            if (event.getFraudReason() != null) {
                metadata.put("fraudReason", event.getFraudReason());
            }
            if (event.getFraudIndicators() != null) {
                metadata.put("fraudIndicatorCount", event.getFraudIndicators().size());
            }
            if (event.isRequiresManualReview()) {
                metadata.put("requiresManualReview", true);
            }
            
            FinancialAuditLog auditLog = FinancialAuditLog.builder()
                    .transactionId(event.getTransactionId() != null ? event.getTransactionId() : UUID.randomUUID().toString())
                    .userId(event.getUserId() != null ? event.getUserId() : extractUserId())
                    .transactionType(TransactionType.FRAUD_CHECK)
                    .status(event.isBlocked() ? TransactionStatus.BLOCKED : TransactionStatus.COMPLETED)
                    .metadata(metadata)
                    .ipAddress(extractIpAddress())
                    .userAgent(extractUserAgent())
                    .sessionId(extractSessionId())
                    .build();
            
            logFinancialTransaction(auditLog);
        } catch (Exception e) {
            log.error("Failed to audit fraud detection event: {}", event.getEventType(), e);
        }
    }
    
    /**
     * Audit key generation
     */
    public void auditKeyGeneration(String keyId, String keyType) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("keyId", keyId);
        metadata.put("keyType", keyType);
        metadata.put("timestamp", LocalDateTime.now());
        
        auditSecurityEvent("KEY_GENERATION", "Encryption key generated: " + keyId, metadata);
    }
    
    /**
     * Audit key rotation
     */
    public void auditKeyRotation(String keyId, String reason) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("keyId", keyId);
        metadata.put("reason", reason);
        metadata.put("timestamp", LocalDateTime.now());
        
        auditSecurityEvent("KEY_ROTATION", "Encryption key rotated: " + keyId, metadata);
    }
    
    /**
     * Audit key retrieval
     */
    public void auditKeyRetrieval(String keyId, String purpose) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("keyId", keyId);
        metadata.put("purpose", purpose);
        metadata.put("timestamp", LocalDateTime.now());
        
        auditSecurityEvent("KEY_RETRIEVAL", "Encryption key retrieved: " + keyId, metadata);
    }
    
    /**
     * Audit encryption operation
     */
    public void auditEncryptionOperation(com.waqiti.common.encryption.model.EncryptionContext context, 
                                        com.waqiti.common.encryption.model.EncryptionMetadata metadata,
                                        boolean success) {
        Map<String, Object> auditMetadata = new HashMap<>();
        auditMetadata.put("contextId", context.getContextId());
        auditMetadata.put("encryptionType", context.getEncryptionType());
        auditMetadata.put("success", success);
        auditMetadata.put("algorithm", metadata.getAlgorithm());
        auditMetadata.put("timestamp", LocalDateTime.now());
        
        auditSecurityEvent("ENCRYPTION_OPERATION", "Data encryption performed", auditMetadata);
    }
    
    /**
     * Audit decryption operation
     */
    public void auditDecryptionOperation(com.waqiti.common.encryption.model.EncryptionContext context,
                                        com.waqiti.common.encryption.model.EncryptionMetadata metadata,
                                        boolean success) {
        Map<String, Object> auditMetadata = new HashMap<>();
        auditMetadata.put("contextId", context.getContextId());
        auditMetadata.put("encryptionType", context.getEncryptionType());
        auditMetadata.put("success", success);
        auditMetadata.put("algorithm", metadata.getAlgorithm());
        auditMetadata.put("timestamp", LocalDateTime.now());
        
        auditSecurityEvent("DECRYPTION_OPERATION", "Data decryption performed", auditMetadata);
    }

    /**
     * Log DLQ event for dead letter queue processing
     */
    public void logDlqEvent(String eventType, String topic, Map<String, Object> eventData) {
        try {
            Map<String, Object> auditData = new HashMap<>(eventData);
            auditData.put("eventType", eventType);
            auditData.put("topic", topic);
            auditData.put("timestamp", LocalDateTime.now());
            auditData.put("source", "DLQ-Consumer");

            // Log to MongoDB
            AuditLogEntity entity = new AuditLogEntity();
            entity.setId(UUID.randomUUID());
            entity.setTimestamp(LocalDateTime.now());
            entity.setEventType(eventType);
            entity.setDescription("DLQ event: " + topic);
            // Convert Map<String, Object> to Map<String, String>
            Map<String, String> metadataStrings = auditData.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue() != null ? e.getValue().toString() : ""
                ));
            entity.setMetadata(metadataStrings);
            mongoTemplate.save(entity, "dlq_audit_logs");

            // Publish to Kafka
            publishToKafka("dlq-audit-events", entity.getId().toString(), auditData);

            log.info("DLQ audit event logged: {} - Topic: {}", eventType, topic);

        } catch (Exception e) {
            log.error("Failed to log DLQ event: {} - {}", eventType, topic, e);
        }
    }

    /**
     * Audit payment event
     */
    public void auditPaymentEvent(String eventType, String userId, String description, Map<String, Object> metadata) {
        try {
            Map<String, Object> auditData = new HashMap<>(metadata);
            auditData.put("eventType", eventType);
            auditData.put("userId", userId);
            auditData.put("description", description);
            auditData.put("timestamp", LocalDateTime.now());
            auditData.put("source", "PaymentService");

            // Log to MongoDB
            AuditLogEntity entity = new AuditLogEntity();
            entity.setId(UUID.randomUUID());
            entity.setTimestamp(LocalDateTime.now());
            entity.setEventType(eventType);
            entity.setDescription(description);
            // Convert Map<String, Object> to Map<String, String>
            Map<String, String> metadataStrings = auditData.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue() != null ? e.getValue().toString() : ""
                ));
            entity.setMetadata(metadataStrings);
            mongoTemplate.save(entity, "payment_audit_logs");

            // Publish to Kafka
            publishToKafka("payment-audit-events", entity.getId().toString(), auditData);

            log.info("Payment audit event logged: {} - User: {}", eventType, userId);

        } catch (Exception e) {
            log.error("Failed to log payment event: {} - User: {}", eventType, userId, e);
        }
    }

    /**
     * Log audit event from AuditLogEntry
     */
    public void logAuditEvent(com.waqiti.common.audit.AuditLogger.AuditLogEntry entry) {
        try {
            log.debug("Logging audit event: {} - Operation: {}", entry.getEventType(), entry.getOperation());

            // Create financial audit log if it's a financial event
            if (entry.getAmount() != null && entry.getAmount().compareTo(BigDecimal.ZERO) > 0 && entry.getCurrency() != null) {
                FinancialAuditLog financialLog = FinancialAuditLog.builder()
                    .userId(entry.getUserId())
                    .transactionType(TransactionType.valueOf(entry.getEventType().toUpperCase().replace("-", "_")))
                    .amount(entry.getAmount())
                    .currency(entry.getCurrency())
                    .status(entry.getStatus() != null ? TransactionStatus.valueOf(entry.getStatus().toUpperCase()) : TransactionStatus.PENDING)
                    .description(entry.getDescription())
                    .metadata(entry.getAdditionalData())
                    .build();
                logFinancialTransaction(financialLog);
            }

            // Create security audit log if it's a security event
            if (entry.getSeverity() != null || entry.getSessionId() != null) {
                SecurityAuditLog securityLog = SecurityAuditLog.builder()
                    .userId(entry.getUserId())
                    .eventType(entry.getEventType())
                    .description(entry.getDescription())
                    .ipAddress(entry.getSourceIpAddress())
                    .userAgent(entry.getUserAgent())
                    .sessionId(entry.getSessionId())
                    .success(entry.isSuccess())
                    .metadata(entry.getAdditionalData())
                    .build();
                logSecurityEvent(securityLog);
            }

        } catch (Exception e) {
            log.error("Failed to log audit event: {}", entry.getEventType(), e);
        }
    }

    /**
     * Log general audit event with correlation ID
     */
    public void logAuditEvent(String eventType, String correlationId, Map<String, Object> eventData) {
        try {
            log.debug("Logging audit event: type={}, correlationId={}", eventType, correlationId);

            // Create audit log entity
            AuditLogEntity entity = new AuditLogEntity();
            entity.setId(UUID.randomUUID());
            entity.setTimestamp(LocalDateTime.now());
            entity.setEventType(eventType);
            entity.setDescription("Audit event: " + eventType);
            entity.setCorrelationId(correlationId);

            // Convert Map<String, Object> to Map<String, String>
            if (eventData != null) {
                Map<String, String> metadataStrings = eventData.entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue() != null ? e.getValue().toString() : ""
                    ));
                entity.setMetadata(metadataStrings);
            }

            mongoTemplate.save(entity, "audit_logs");

            // Publish to Kafka for real-time monitoring
            publishToKafka("audit-events", entity.getId().toString(), eventData);

            log.info("Audit event logged: {} - CorrelationId: {}", eventType, correlationId);

        } catch (Exception e) {
            log.error("Failed to log audit event: {} - CorrelationId: {}", eventType, correlationId, e);
        }
    }

    /**
     * Log admin access
     */
    public void logAdminAccess(String userId, String resourceType, String resourceId) {
        try {
            Map<String, Object> metadata = Map.of(
                "userId", userId,
                "resourceType", resourceType,
                "resourceId", resourceId,
                "accessType", "ADMIN",
                "timestamp", LocalDateTime.now()
            );

            AuditLogEntity entity = new AuditLogEntity();
            entity.setId(UUID.randomUUID());
            entity.setUserId(userId);
            entity.setEventType("ADMIN_ACCESS");
            entity.setDescription("Admin accessed resource: " + resourceType);
            entity.setTimestamp(LocalDateTime.now());
            Map<String, String> metadataStrings = metadata.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue() != null ? e.getValue().toString() : ""
                ));
            entity.setMetadata(metadataStrings);
            mongoTemplate.save(entity, "audit_logs");

            publishToKafka("access-audit-logs", entity.getId().toString(), metadata);
            log.info("Admin access logged: userId={}, resource={}/{}", userId, resourceType, resourceId);
        } catch (Exception e) {
            log.error("Failed to log admin access", e);
        }
    }

    /**
     * Log delegated access
     */
    public void logDelegatedAccess(String userId, String resourceType, String resourceId) {
        try {
            Map<String, Object> metadata = Map.of(
                "userId", userId,
                "resourceType", resourceType,
                "resourceId", resourceId,
                "accessType", "DELEGATED",
                "timestamp", LocalDateTime.now()
            );

            AuditLogEntity entity = new AuditLogEntity();
            entity.setId(UUID.randomUUID());
            entity.setUserId(userId);
            entity.setEventType("DELEGATED_ACCESS");
            entity.setDescription("Delegated access to resource: " + resourceType);
            entity.setTimestamp(LocalDateTime.now());
            Map<String, String> metadataStrings = metadata.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue() != null ? e.getValue().toString() : ""
                ));
            entity.setMetadata(metadataStrings);
            mongoTemplate.save(entity, "audit_logs");

            publishToKafka("access-audit-logs", entity.getId().toString(), metadata);
            log.info("Delegated access logged: userId={}, resource={}/{}", userId, resourceType, resourceId);
        } catch (Exception e) {
            log.error("Failed to log delegated access", e);
        }
    }

    /**
     * Log unauthorized access
     */
    public void logUnauthorizedAccess(String userId, String resourceType, String resourceId) {
        try {
            Map<String, Object> metadata = Map.of(
                "userId", userId,
                "resourceType", resourceType,
                "resourceId", resourceId,
                "accessType", "UNAUTHORIZED",
                "severity", "HIGH",
                "timestamp", LocalDateTime.now()
            );

            AuditLogEntity entity = new AuditLogEntity();
            entity.setId(UUID.randomUUID());
            entity.setUserId(userId);
            entity.setEventType("UNAUTHORIZED_ACCESS");
            entity.setDescription("Unauthorized access attempt to resource: " + resourceType);
            entity.setTimestamp(LocalDateTime.now());
            Map<String, String> metadataStrings = metadata.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue() != null ? e.getValue().toString() : ""
                ));
            entity.setMetadata(metadataStrings);
            mongoTemplate.save(entity, "audit_logs");

            publishToKafka("security-audit-logs", entity.getId().toString(), metadata);
            log.warn("UNAUTHORIZED ACCESS: userId={}, resource={}/{}", userId, resourceType, resourceId);
        } catch (Exception e) {
            log.error("Failed to log unauthorized access", e);
        }
    }

    /**
     * Log transaction view
     */
    public void logTransactionView(String userId, String transactionId) {
        try {
            Map<String, Object> metadata = Map.of(
                "userId", userId,
                "transactionId", transactionId,
                "action", "VIEW",
                "timestamp", LocalDateTime.now()
            );

            AuditLogEntity entity = new AuditLogEntity();
            entity.setId(UUID.randomUUID());
            entity.setUserId(userId);
            entity.setEventType("TRANSACTION_VIEW");
            entity.setDescription("User viewed transaction: " + transactionId);
            entity.setTimestamp(LocalDateTime.now());
            Map<String, String> metadataStrings = metadata.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue() != null ? e.getValue().toString() : ""
                ));
            entity.setMetadata(metadataStrings);
            mongoTemplate.save(entity, "audit_logs");

            publishToKafka("transaction-audit-logs", entity.getId().toString(), metadata);
        } catch (Exception e) {
            log.error("Failed to log transaction view", e);
        }
    }

    /**
     * Log bulk unauthorized access
     */
    public void logBulkUnauthorizedAccess(String userId, String resourceType, int unauthorizedCount) {
        try {
            Map<String, Object> metadata = Map.of(
                "userId", userId,
                "resourceType", resourceType,
                "unauthorizedCount", unauthorizedCount,
                "severity", "CRITICAL",
                "timestamp", LocalDateTime.now()
            );

            AuditLogEntity entity = new AuditLogEntity();
            entity.setId(UUID.randomUUID());
            entity.setUserId(userId);
            entity.setEventType("BULK_UNAUTHORIZED_ACCESS");
            entity.setDescription("Bulk unauthorized access attempt: " + unauthorizedCount + " resources");
            entity.setTimestamp(LocalDateTime.now());
            Map<String, String> metadataStrings = metadata.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue() != null ? e.getValue().toString() : ""
                ));
            entity.setMetadata(metadataStrings);
            mongoTemplate.save(entity, "audit_logs");

            publishToKafka("security-audit-logs", entity.getId().toString(), metadata);
            log.error("CRITICAL: Bulk unauthorized access: userId={}, count={}", userId, unauthorizedCount);
        } catch (Exception e) {
            log.error("Failed to log bulk unauthorized access", e);
        }
    }

    /**
     * Log access grant
     */
    public void logAccessGrant(String grantorId, String granteeId, String resourceType, String resourceId, int durationMinutes) {
        try {
            Map<String, Object> metadata = Map.of(
                "grantorId", grantorId,
                "granteeId", granteeId,
                "resourceType", resourceType,
                "resourceId", resourceId,
                "durationMinutes", durationMinutes,
                "timestamp", LocalDateTime.now()
            );

            AuditLogEntity entity = new AuditLogEntity();
            entity.setId(UUID.randomUUID());
            entity.setUserId(grantorId);
            entity.setEventType("ACCESS_GRANT");
            entity.setDescription("Access granted to " + granteeId + " for resource: " + resourceType);
            entity.setTimestamp(LocalDateTime.now());
            Map<String, String> metadataStrings = metadata.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue() != null ? e.getValue().toString() : ""
                ));
            entity.setMetadata(metadataStrings);
            mongoTemplate.save(entity, "audit_logs");

            publishToKafka("access-audit-logs", entity.getId().toString(), metadata);
            log.info("Access granted: grantor={}, grantee={}, resource={}/{}", grantorId, granteeId, resourceType, resourceId);
        } catch (Exception e) {
            log.error("Failed to log access grant", e);
        }
    }

    /**
     * Log access revoke
     */
    public void logAccessRevoke(String revokerId, String userId, String resourceType, String resourceId) {
        try {
            Map<String, Object> metadata = Map.of(
                "revokerId", revokerId,
                "userId", userId,
                "resourceType", resourceType,
                "resourceId", resourceId,
                "timestamp", LocalDateTime.now()
            );

            AuditLogEntity entity = new AuditLogEntity();
            entity.setId(UUID.randomUUID());
            entity.setUserId(revokerId);
            entity.setEventType("ACCESS_REVOKE");
            entity.setDescription("Access revoked for " + userId + " from resource: " + resourceType);
            entity.setTimestamp(LocalDateTime.now());
            Map<String, String> metadataStrings = metadata.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue() != null ? e.getValue().toString() : ""
                ));
            entity.setMetadata(metadataStrings);
            mongoTemplate.save(entity, "audit_logs");

            publishToKafka("access-audit-logs", entity.getId().toString(), metadata);
            log.info("Access revoked: revoker={}, user={}, resource={}/{}", revokerId, userId, resourceType, resourceId);
        } catch (Exception e) {
            log.error("Failed to log access revoke", e);
        }
    }

    /**
     * Log compliance report generation
     */
    public void logComplianceReportGeneration(com.waqiti.common.compliance.ComplianceReport report) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("reportId", report.getReportId());
            metadata.put("reportType", report.getReportType() != null ? report.getReportType().toString() : "UNKNOWN");
            metadata.put("reportPeriod", report.getReportPeriod());
            metadata.put("generatedBy", report.getGeneratedBy());
            metadata.put("status", report.getStatus() != null ? report.getStatus().toString() : "UNKNOWN");
            metadata.put("timestamp", LocalDateTime.now());

            AuditLogEntity entity = new AuditLogEntity();
            entity.setId(UUID.randomUUID());
            entity.setUserId(report.getGeneratedBy());
            entity.setEventType("COMPLIANCE_REPORT_GENERATED");
            entity.setDescription("Compliance report generated: " + report.getReportType() + " - " + report.getReportId());
            entity.setTimestamp(LocalDateTime.now());
            Map<String, String> metadataStrings = metadata.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue() != null ? e.getValue().toString() : ""
                ));
            entity.setMetadata(metadataStrings);
            mongoTemplate.save(entity, "audit_logs");

            publishToKafka("compliance-audit-logs", entity.getId().toString(), metadata);
            log.info("Compliance report generated: type={}, id={}, generatedBy={}", report.getReportType(), report.getReportId(), report.getGeneratedBy());
        } catch (Exception e) {
            log.error("Failed to log compliance report generation", e);
        }
    }

    /**
     * Log compliance report submission
     */
    public void logComplianceReportSubmission(String reportId,
                                             com.waqiti.common.compliance.ComplianceSubmissionRequest request,
                                             com.waqiti.common.compliance.ComplianceSubmissionResult result) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("reportId", reportId);
            metadata.put("regulatoryAuthority", request != null ? request.getRegulatoryAuthority() : "UNKNOWN");
            metadata.put("submissionMethod", request != null ? request.getSubmissionMethod() : "UNKNOWN");
            metadata.put("success", result != null && result.isSuccessful());
            metadata.put("confirmationNumber", result != null ? result.getConfirmationNumber() : null);
            metadata.put("timestamp", LocalDateTime.now());

            AuditLogEntity entity = new AuditLogEntity();
            entity.setId(UUID.randomUUID());
            entity.setUserId(request != null ? request.getSubmittedBy() : "SYSTEM");
            entity.setEventType("COMPLIANCE_REPORT_SUBMITTED");
            entity.setDescription("Compliance report submitted: " + reportId + " to " +
                (request != null ? request.getRegulatoryAuthority() : "UNKNOWN"));
            entity.setTimestamp(LocalDateTime.now());
            Map<String, String> metadataStrings = metadata.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue() != null ? e.getValue().toString() : ""
                ));
            entity.setMetadata(metadataStrings);
            mongoTemplate.save(entity, "audit_logs");

            publishToKafka("compliance-audit-logs", entity.getId().toString(), metadata);
            log.info("Compliance report submitted: reportId={}, authority={}, success={}",
                reportId, request != null ? request.getRegulatoryAuthority() : "UNKNOWN",
                result != null && result.isSuccessful());
        } catch (Exception e) {
            log.error("Failed to log compliance report submission", e);
        }
    }

    // ========== Methods for BalanceReconciliationEventConsumer ==========

    public void logReconciliationAttempt(UUID walletId, java.math.BigDecimal expectedBalance,
                                        String currency, String correlationId, java.time.LocalDateTime timestamp) {
        log.info("Logging reconciliation attempt: walletId={}, expected={} {}, correlationId={}",
            walletId, expectedBalance, currency, correlationId);
    }

    public void logReconciliationSuccess(UUID walletId, java.math.BigDecimal actualBalance,
                                        java.math.BigDecimal expectedBalance, java.math.BigDecimal discrepancy,
                                        String correlationId, java.time.LocalDateTime timestamp) {
        log.info("Logging reconciliation success: walletId={}, actual={}, expected={}, discrepancy={}, correlationId={}",
            walletId, actualBalance, expectedBalance, discrepancy, correlationId);
    }

    public void logReconciliationAutoCorrection(UUID walletId, java.math.BigDecimal discrepancy,
                                               UUID correctionEntryId, String correctionReason,
                                               String correlationId, java.time.LocalDateTime timestamp) {
        log.info("Logging reconciliation auto-correction: walletId={}, discrepancy={}, correctionId={}, reason={}, correlationId={}",
            walletId, discrepancy, correctionEntryId, correctionReason, correlationId);
    }

    public void logReconciliationDiscrepancy(UUID walletId, java.math.BigDecimal actualBalance,
                                            java.math.BigDecimal expectedBalance, java.math.BigDecimal discrepancy,
                                            String correctionReason, String correlationId, java.time.LocalDateTime timestamp) {
        log.warn("Logging reconciliation discrepancy: walletId={}, actual={}, expected={}, discrepancy={}, reason={}, correlationId={}",
            walletId, actualBalance, expectedBalance, discrepancy, correctionReason, correlationId);
    }

    public void logReconciliationError(UUID walletId, java.math.BigDecimal expectedBalance,
                                      String correlationId, String errorMessage, java.time.LocalDateTime timestamp) {
        log.error("Logging reconciliation error: walletId={}, expected={}, correlationId={}, error={}",
            walletId, expectedBalance, correlationId, errorMessage);
    }

    public void logManualIntervention(UUID walletId, UUID reconciliationId, String operatorId,
                                     String interventionType, java.time.LocalDateTime timestamp) {
        log.warn("Logging manual intervention: walletId={}, reconciliationId={}, operatorId={}, type={}",
            walletId, reconciliationId, operatorId, interventionType);
    }

    /**
     * Log account deactivation event processing failure
     */
    public void logAccountDeactivationEventProcessingFailure(String eventId, String reason, LocalDateTime timestamp) {
        log.error("AUDIT: Account deactivation event processing failed - eventId={}, reason={}, timestamp={}",
            eventId, reason, timestamp);

        Map<String, Object> details = new HashMap<>();
        details.put("eventId", eventId);
        details.put("failureReason", reason);
        details.put("timestamp", timestamp);
        details.put("eventType", "ACCOUNT_DEACTIVATION_FAILURE");

        createAuditLog("ACCOUNT_DEACTIVATION_EVENT_FAILURE", details);
    }

    /**
     * Log wallet temporary deactivation
     */
    public void logWalletIsTemporarilyDeactivated(UUID walletId, String reason, LocalDateTime timestamp) {
        log.warn("AUDIT: Wallet temporarily deactivated - walletId={}, reason={}, timestamp={}",
            walletId, reason, timestamp);

        Map<String, Object> details = new HashMap<>();
        details.put("walletId", walletId);
        details.put("deactivationReason", reason);
        details.put("timestamp", timestamp);
        details.put("deactivationType", "TEMPORARY");
        details.put("status", "FROZEN");

        createAuditLog("WALLET_TEMPORARY_DEACTIVATION", details);
    }

    /**
     * Log wallet permanent deactivation
     */
    public void logWalletIsPermanentlyDeactivated(UUID walletId, String reason, LocalDateTime timestamp) {
        log.warn("AUDIT: Wallet permanently deactivated - walletId={}, reason={}, timestamp={}",
            walletId, reason, timestamp);

        Map<String, Object> details = new HashMap<>();
        details.put("walletId", walletId);
        details.put("deactivationReason", reason);
        details.put("timestamp", timestamp);
        details.put("deactivationType", "PERMANENT");
        details.put("status", "CLOSED");

        createAuditLog("WALLET_PERMANENT_DEACTIVATION", details);
    }

    /**
     * Log wallet suspension for security review
     */
    public void logWalletSuspended(UUID walletId, String reason, LocalDateTime timestamp) {
        log.warn("AUDIT: Wallet suspended for security review - walletId={}, reason={}, timestamp={}",
            walletId, reason, timestamp);

        Map<String, Object> details = new HashMap<>();
        details.put("walletId", walletId);
        details.put("suspensionReason", reason);
        details.put("timestamp", timestamp);
        details.put("status", "SUSPENDED");
        details.put("requiresReview", true);

        createAuditLog("WALLET_SUSPENSION", details);
    }

    /**
     * Log compliance-driven wallet closure
     */
    public void logComplianceWalletClosure(UUID walletId, String reason, LocalDateTime timestamp) {
        log.warn("AUDIT: Wallet closed for compliance reasons - walletId={}, reason={}, timestamp={}",
            walletId, reason, timestamp);

        Map<String, Object> details = new HashMap<>();
        details.put("walletId", walletId);
        details.put("closureReason", reason);
        details.put("timestamp", timestamp);
        details.put("closureType", "COMPLIANCE");
        details.put("regulatoryReportRequired", true);

        createAuditLog("COMPLIANCE_WALLET_CLOSURE", details);
    }

    /**
     * Log refund processing fallback activation
     */
    public void logRefundProcessingFallback(UUID walletId, String reason, LocalDateTime timestamp) {
        log.error("AUDIT: Refund processing fallback activated - walletId={}, reason={}, timestamp={}",
            walletId, reason, timestamp);

        Map<String, Object> details = new HashMap<>();
        details.put("walletId", walletId);
        details.put("fallbackReason", reason);
        details.put("timestamp", timestamp);
        details.put("requiresManualReview", true);

        createAuditLog("REFUND_PROCESSING_FALLBACK", details);
    }

    /**
     * Log account deactivation event with lifecycle tracking
     */
    public void logAccountDeactivationEventLt(String eventId, LocalDateTime timestamp) {
        log.info("AUDIT: Account deactivation event lifecycle tracking - eventId={}, timestamp={}",
            eventId, timestamp);

        Map<String, Object> details = new HashMap<>();
        details.put("eventId", eventId);
        details.put("timestamp", timestamp);
        details.put("eventType", "ACCOUNT_DEACTIVATION");
        details.put("lifecycleStage", "PROCESSING");

        createAuditLog("ACCOUNT_DEACTIVATION_EVENT_LIFECYCLE", details);
    }

    /**
     * Helper method to create standardized audit logs
     * Production-grade implementation with proper type conversion and JSON handling
     */
    private void createAuditLog(String eventType, Map<String, Object> details) {
        try {
            AuditLogEntity auditLog = new AuditLogEntity();
            auditLog.setId(UUID.randomUUID());
            auditLog.setEventType(eventType);

            // Convert Map<String, Object> to Map<String, String> for simple metadata
            // Complex objects go to metadataJson field
            if (details != null && !details.isEmpty()) {
                Map<String, String> simpleMetadata = new java.util.HashMap<>();
                for (Map.Entry<String, Object> entry : details.entrySet()) {
                    if (entry.getValue() != null) {
                        // Store simple types as string metadata
                        if (entry.getValue() instanceof String ||
                            entry.getValue() instanceof Number ||
                            entry.getValue() instanceof Boolean ||
                            entry.getValue() instanceof java.time.temporal.Temporal) {
                            simpleMetadata.put(entry.getKey(), String.valueOf(entry.getValue()));
                        }
                    }
                }
                auditLog.setMetadata(simpleMetadata);

                // Store full object graph as JSON for complex analysis
                try {
                    auditLog.setMetadataJson(objectMapper.valueToTree(details));
                } catch (Exception e) {
                    log.warn("Failed to serialize metadata to JSON: {}", e.getMessage());
                }
            }

            auditLog.setTimestamp(LocalDateTime.now());

            // Handle userId - convert UUID to String if necessary
            UUID currentUserId = getCurrentUserId();
            auditLog.setUserId(currentUserId != null ? currentUserId.toString() : null);

            auditLog.setIpAddress(getCurrentIpAddress());

            mongoTemplate.save(auditLog, "audit_logs");

            // Publish to Kafka for real-time monitoring
            publishToKafka("audit-events", auditLog.getId().toString(), details);

        } catch (Exception e) {
            log.error("Failed to create audit log for event type: {}", eventType, e);
        }
    }

    /**
     * Get current user ID from security context
     */
    private UUID getCurrentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof String) {
                return UUID.fromString((String) auth.getPrincipal());
            }
        } catch (Exception e) {
            log.debug("Could not retrieve current user ID", e);
        }
        return null;
    }

    /**
     * Get current IP address from request
     */
    private String getCurrentIpAddress() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            log.debug("Could not retrieve current IP address", e);
        }
        return "unknown";
    }

    /**
     * Generic event logging method for flexible audit trail
     */
    public void logEvent(String eventType, String userId, String description, Map<String, Object> details) {
        log.info("[AUDIT] Event: type={}, userId={}, description={}", eventType, userId, description);

        try {
            Map<String, Object> metadata = new HashMap<>(details != null ? details : Map.of());
            metadata.put("eventType", eventType);
            metadata.put("description", description);
            metadata.put("timestamp", LocalDateTime.now());

            auditSecurityEvent(eventType, description, metadata);
            incrementAuditCounter("audit_events_total", "type", eventType);
        } catch (Exception e) {
            log.error("[AUDIT] Failed to log event: type={}, userId={}", eventType, userId, e);
        }
    }

    /**
     * Production-grade helper to increment audit counters with proper metrics tagging
     * Provides standardized metrics collection across all audit operations
     *
     * @param counterName Base counter name (e.g., "audit_events_total")
     * @param tagKey Tag key for categorization (e.g., "type", "service", "severity")
     * @param tagValue Tag value (e.g., "PAYMENT_CREATED", "wallet-service", "HIGH")
     */
    private void incrementAuditCounter(String counterName, String tagKey, String tagValue) {
        try {
            if (metricsService != null && counterName != null && tagKey != null && tagValue != null) {
                Map<String, String> tags = Map.of(
                    tagKey, tagValue,
                    "service", "audit-service"
                );
                metricsService.incrementCounter(counterName, tags);
            }
        } catch (Exception e) {
            log.warn("Failed to increment audit counter: {}", counterName, e);
            // Don't fail audit logging due to metrics issues
        }
    }

    /**
     * Log wallet-specific events
     */
    public void auditWalletEvent(String eventType, String userId, String description, Map<String, Object> metadata) {
        logEvent("WALLET_" + eventType, userId, description, metadata);
    }

    /**
     * Log payment-specific event
     */
    public void logPaymentEvent(String eventType, String transactionId, Map<String, Object> eventData) {
        log.info("[AUDIT] Payment event: type={}, transactionId={}", eventType, transactionId);

        try {
            Map<String, Object> metadata = new HashMap<>(eventData != null ? eventData : Map.of());
            metadata.put("transactionId", transactionId);
            metadata.put("timestamp", LocalDateTime.now());

            auditSecurityEvent("PAYMENT_" + eventType, "Payment event: " + eventType, metadata);
            incrementAuditCounter("payment_audit_events_total", "type", eventType);
        } catch (Exception e) {
            log.error("[AUDIT] Failed to log payment event: type={}, transactionId={}", eventType, transactionId, e);
        }
    }

    /**
     * Audit financial event - for ledger service accounting events
     */
    public void auditFinancialEvent(String eventType, UUID eventId, String accountingPeriod,
                                   Map<String, Object> eventData) {
        try {
            log.info("[AUDIT] Financial event: type={}, eventId={}, period={}",
                    eventType, eventId, accountingPeriod);

            Map<String, Object> metadata = new HashMap<>(eventData != null ? eventData : Map.of());
            metadata.put("eventId", eventId != null ? eventId.toString() : "");
            metadata.put("accountingPeriod", accountingPeriod);
            metadata.put("timestamp", LocalDateTime.now());

            FinancialAuditLog auditLog = FinancialAuditLog.builder()
                    .transactionId(eventId != null ? eventId.toString() : UUID.randomUUID().toString())
                    .userId(extractUserId())
                    .transactionType(TransactionType.ACCOUNTING_EVENT)
                    .status(TransactionStatus.COMPLETED)
                    .metadata(metadata)
                    .ipAddress(extractIpAddress())
                    .userAgent(extractUserAgent())
                    .sessionId(extractSessionId())
                    .build();

            logFinancialTransaction(auditLog);
            incrementAuditCounter("financial_audit_events_total", "type", eventType);

        } catch (Exception e) {
            log.error("[AUDIT] Failed to log financial event: type={}, eventId={}",
                    eventType, eventId, e);
        }
    }

    /**
     * Log notification event for alerts and correlations
     */
    public void logNotificationEvent(String eventType, String alertId, Map<String, Object> eventData) {
        try {
            log.info("[AUDIT] Notification event: type={}, alertId={}", eventType, alertId);

            Map<String, Object> metadata = new HashMap<>(eventData != null ? eventData : Map.of());
            metadata.put("alertId", alertId);
            metadata.put("timestamp", LocalDateTime.now());

            // Store in audit log
            incrementAuditCounter("notification_events_total", "type", eventType);

        } catch (Exception e) {
            log.error("[AUDIT] Failed to log notification event: type={}, alertId={}",
                    eventType, alertId, e);
        }
    }

    /**
     * Get meter registry for metrics
     */
    public MeterRegistry getMeterRegistry() {
        return this.meterRegistry;
    }

    public void logDlqPermanentFailure(DlqMessageMetadata metadata) {

//        TODO - properly implement with business logic, etc. added by aniix october, 28th 2025
    }

    public void logDlqRecovery(DlqMessageMetadata metadata, RecoveryResult result) {
//        TODO - properly implement with business logic, etc. added by aniix october, 28th 2025
    }
}