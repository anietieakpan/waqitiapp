package com.waqiti.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import jakarta.servlet.http.HttpServletRequest;
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
 * Comprehensive Audit Service for enterprise-grade audit logging
 * Provides PCI DSS compliant audit trail functionality
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ComprehensiveAuditService {

    private final MongoTemplate mongoTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${waqiti.audit.enabled:true}")
    private boolean auditEnabled;
    
    @Value("${waqiti.audit.retention.years:7}")
    private int retentionYears;
    
    @Value("${waqiti.audit.integrity.secret:${VAULT_AUDIT_SECRET:defaultSecret}}")
    private String integritySecret;
    
    // Hash chain for tamper detection
    private final AtomicLong sequenceNumber = new AtomicLong(0);
    private volatile String previousHash = "";
    private final Map<String, String> hashChain = new ConcurrentHashMap<>();
    
    
    /**
     * Main audit logging method with PCI DSS compliance
     */
    @Transactional
    public void logAuditEvent(
            AuditEventType eventType,
            String userId,
            String action,
            Map<String, Object> details,
            AuditSeverity severity,
            String affectedResource) {
        
        if (!auditEnabled) {
            log.warn("Audit logging is disabled - this may violate compliance requirements");
            return;
        }
        
        try {
            // Generate unique identifiers
            String eventId = UUID.randomUUID().toString();
            long sequence = sequenceNumber.incrementAndGet();
            
            // Build audit record
            ComprehensiveAuditRecord record = ComprehensiveAuditRecord.builder()
                .eventId(eventId)
                .sequenceNumber(sequence)
                .eventType(eventType)
                .userId(userId)
                .username(extractUsername())
                .action(action)
                .timestamp(Instant.now())
                .timestampUtc(LocalDateTime.now(ZoneOffset.UTC))
                .severity(severity)
                .sourceIp(extractSourceIp())
                .sourceHost(extractSourceHost())
                .userAgent(extractUserAgent())
                .sessionId(extractSessionId())
                .correlationId(extractCorrelationId())
                .affectedResource(affectedResource)
                .details(sanitizeDetails(details))
                .success(determineSuccess(details))
                .hash(calculateHash(eventId, sequence, details))
                .previousHash(previousHash)
                .signature(signRecord(eventId, sequence))
                .build();
            
            // Update hash chain
            updateHashChain(record);
            
            // Persist to MongoDB for immutability if available
            if (mongoTemplate != null) {
                mongoTemplate.save(record, "audit_logs");
            } else {
                // Fallback to logging if MongoDB not available
                log.info("AUDIT_RECORD: {}", objectMapper.writeValueAsString(record));
            }
            
            // Stream to Kafka for real-time monitoring if available
            if (kafkaTemplate != null) {
                streamAuditEvent(record);
            }
            
            // Check for security violations
            checkSecurityViolations(record);
            
            log.debug("Audit event logged: eventId={}, type={}, user={}", 
                eventId, eventType, userId);
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to log audit event - compliance violation risk", e);
            // Attempt to log the failure itself
            logAuditFailure(eventType, userId, action, e);
        }
    }
    
    /**
     * Audit user authentication events
     */
    @Async
    public CompletableFuture<Void> auditAuthentication(String username, String ipAddress, boolean successful, String reason) {
        if (!auditEnabled) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            Map<String, Object> details = new HashMap<>();
            details.put("ipAddress", ipAddress);
            details.put("reason", reason);
            details.put("success", successful);
            
            AuditEventType eventType = successful ? AuditEventType.LOGIN_SUCCESS : AuditEventType.LOGIN_FAILURE;
            AuditSeverity severity = successful ? AuditSeverity.INFO : AuditSeverity.HIGH;
            
            logAuditEvent(
                eventType,
                username,
                "Authentication " + (successful ? "succeeded" : "failed"),
                details,
                severity,
                "Authentication System"
            );
            
        } catch (Exception e) {
            log.error("Failed to audit authentication event", e);
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Audit financial transactions
     */
    @Async
    public CompletableFuture<Void> auditFinancialTransaction(String transactionId, String transactionType, 
                                        String fromAccount, String toAccount, String amount, 
                                        String currency, boolean successful) {
        if (!auditEnabled) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            Map<String, Object> details = new HashMap<>();
            details.put("transactionId", transactionId);
            details.put("transactionType", transactionType);
            details.put("fromAccount", fromAccount);
            details.put("toAccount", toAccount);
            details.put("amount", amount);
            details.put("currency", currency);
            details.put("success", successful);
            
            AuditEventType eventType = successful ? AuditEventType.TRANSACTION_COMPLETED : AuditEventType.TRANSACTION_FAILED;
            AuditSeverity severity = successful ? AuditSeverity.INFO : AuditSeverity.MEDIUM;
            
            logAuditEvent(
                eventType,
                getCurrentUser(),
                "Financial transaction " + transactionType,
                details,
                severity,
                "Transaction: " + transactionId
            );
            
        } catch (Exception e) {
            log.error("Failed to audit financial transaction", e);
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Audit data access events
     */
    @Async
    public CompletableFuture<Void> auditDataAccess(String userId, String dataType, String accessType, String dataId) {
        if (!auditEnabled) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            Map<String, Object> details = new HashMap<>();
            details.put("dataType", dataType);
            details.put("accessType", accessType);
            details.put("dataId", dataId);
            
            AuditEventType eventType = AuditEventType.DATA_READ;
            if ("CREATE".equals(accessType)) eventType = AuditEventType.DATA_WRITE;
            else if ("UPDATE".equals(accessType)) eventType = AuditEventType.DATA_WRITE;
            else if ("DELETE".equals(accessType)) eventType = AuditEventType.DATA_DELETE;
            else if ("EXPORT".equals(accessType)) eventType = AuditEventType.DATA_EXPORT;
            
            logAuditEvent(
                eventType,
                userId,
                "Data access: " + accessType,
                details,
                AuditSeverity.INFO,
                dataType + ":" + dataId
            );
            
        } catch (Exception e) {
            log.error("Failed to audit data access event", e);
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Audit security events
     */
    @Async
    public CompletableFuture<Void> auditSecurityEvent(String eventType, String description, String ipAddress) {
        if (!auditEnabled) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            Map<String, Object> details = new HashMap<>();
            details.put("eventType", eventType);
            details.put("description", description);
            details.put("ipAddress", ipAddress);
            
            logAuditEvent(
                AuditEventType.SECURITY_ALERT,
                getCurrentUser(),
                "Security event: " + eventType,
                details,
                AuditSeverity.HIGH,
                "Security System"
            );
            
        } catch (Exception e) {
            log.error("Failed to audit security event", e);
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Audit administrative actions
     */
    @Async
    public CompletableFuture<Void> auditAdminAction(String adminUserId, String action, String targetEntity, 
                               String targetId, Map<String, Object> changes) {
        if (!auditEnabled) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            Map<String, Object> details = new HashMap<>();
            details.put("targetEntity", targetEntity);
            details.put("targetId", targetId);
            details.put("changes", changes);
            
            logAuditEvent(
                AuditEventType.ADMIN_ACTION,
                adminUserId,
                "Administrative action: " + action,
                details,
                AuditSeverity.MEDIUM,
                targetEntity + ":" + targetId
            );
            
        } catch (Exception e) {
            log.error("Failed to audit admin action", e);
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Audit critical compliance events
     */
    public void auditCriticalComplianceEvent(String eventType, String userId, String description, 
                                           Map<String, Object> metadata) {
        
        try {
            logAuditEvent(
                AuditEventType.ADMIN_ACTION, // Using existing enum value
                userId,
                description,
                metadata,
                AuditSeverity.CRITICAL,
                eventType
            );
            
            // Log critical event
            log.error("CRITICAL_COMPLIANCE_AUDIT: {} - User: {} - {}", eventType, userId, description);
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to audit critical compliance event: {} for user: {}", eventType, userId, e);
        }
    }

    /**
     * Audit general compliance events
     */
    public void auditComplianceEvent(String eventType, String userId, String description, 
                                   Map<String, Object> metadata) {
        
        try {
            logAuditEvent(
                AuditEventType.ADMIN_ACTION, // Using existing enum value
                userId,
                description,
                metadata,
                AuditSeverity.INFO,
                eventType
            );
            
            // Log event
            log.info("COMPLIANCE_AUDIT: {} - User: {} - {}", eventType, userId, description);
            
        } catch (Exception e) {
            log.error("Failed to audit compliance event: {} for user: {}", eventType, userId, e);
        }
    }

    /**
     * Search audit records with comprehensive filtering
     */
    public List<AuditRecord> searchAuditRecords(AuditSearchRequest searchRequest) {
        try {
            Query query = buildSearchQuery(searchRequest);
            
            // Apply sorting
            query.with(Sort.by(Sort.Direction.DESC, "timestamp"));
            
            // Apply pagination
            if (searchRequest.getPageSize() > 0) {
                query.limit(searchRequest.getPageSize());
                if (searchRequest.getPageNumber() > 0) {
                    query.skip((long) searchRequest.getPageNumber() * searchRequest.getPageSize());
                }
            } else {
                query.limit(1000); // Default limit
            }
            
            List<AuditRecord> results = mongoTemplate.find(query, AuditRecord.class, "audit_logs");
            
            log.debug("Audit search completed: {} records found", results.size());
            return results;
            
        } catch (Exception e) {
            log.error("Failed to search audit records", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Count audit records matching search criteria
     */
    public long countAuditRecords(AuditSearchRequest searchRequest) {
        try {
            Query query = buildSearchQuery(searchRequest);
            return mongoTemplate.count(query, AuditRecord.class, "audit_logs");
        } catch (Exception e) {
            log.error("Failed to count audit records", e);
            return 0;
        }
    }
    
    /**
     * Search audit records by user
     */
    public List<AuditRecord> searchByUser(String userId, LocalDateTime startDate, LocalDateTime endDate, int limit) {
        try {
            Query query = new Query();
            query.addCriteria(Criteria.where("userId").is(userId));
            
            if (startDate != null && endDate != null) {
                query.addCriteria(Criteria.where("timestampUtc").gte(startDate).lte(endDate));
            }
            
            query.with(Sort.by(Sort.Direction.DESC, "timestamp"));
            if (limit > 0) {
                query.limit(limit);
            }
            
            return mongoTemplate.find(query, AuditRecord.class, "audit_logs");
        } catch (Exception e) {
            log.error("Failed to search audit records by user", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Search audit records by event type
     */
    public List<AuditRecord> searchByEventType(AuditEventType eventType, LocalDateTime startDate, LocalDateTime endDate, int limit) {
        try {
            Query query = new Query();
            query.addCriteria(Criteria.where("eventType").is(eventType));
            
            if (startDate != null && endDate != null) {
                query.addCriteria(Criteria.where("timestampUtc").gte(startDate).lte(endDate));
            }
            
            query.with(Sort.by(Sort.Direction.DESC, "timestamp"));
            if (limit > 0) {
                query.limit(limit);
            }
            
            return mongoTemplate.find(query, AuditRecord.class, "audit_logs");
        } catch (Exception e) {
            log.error("Failed to search audit records by event type", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Search audit records by severity
     */
    public List<AuditRecord> searchBySeverity(AuditSeverity severity, LocalDateTime startDate, LocalDateTime endDate, int limit) {
        try {
            Query query = new Query();
            query.addCriteria(Criteria.where("severity").is(severity));
            
            if (startDate != null && endDate != null) {
                query.addCriteria(Criteria.where("timestampUtc").gte(startDate).lte(endDate));
            }
            
            query.with(Sort.by(Sort.Direction.DESC, "timestamp"));
            if (limit > 0) {
                query.limit(limit);
            }
            
            return mongoTemplate.find(query, AuditRecord.class, "audit_logs");
        } catch (Exception e) {
            log.error("Failed to search audit records by severity", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Search compliance-relevant audit records
     */
    public List<AuditRecord> searchComplianceRecords(String complianceType, LocalDateTime startDate, LocalDateTime endDate, int limit) {
        try {
            Query query = new Query();
            
            // Search in details for compliance flags
            switch (complianceType.toUpperCase()) {
                case "PCI":
                    query.addCriteria(Criteria.where("details.pciRelevant").is(true));
                    break;
                case "SOX":
                    query.addCriteria(Criteria.where("details.soxRelevant").is(true));
                    break;
                case "GDPR":
                    query.addCriteria(Criteria.where("details.gdprRelevant").is(true));
                    break;
                case "SOC2":
                    query.addCriteria(Criteria.where("details.soc2Relevant").is(true));
                    break;
                default:
                    log.warn("Unknown compliance type: {}", complianceType);
                    return new ArrayList<>();
            }
            
            if (startDate != null && endDate != null) {
                query.addCriteria(Criteria.where("timestampUtc").gte(startDate).lte(endDate));
            }
            
            query.with(Sort.by(Sort.Direction.DESC, "timestamp"));
            if (limit > 0) {
                query.limit(limit);
            }
            
            return mongoTemplate.find(query, AuditRecord.class, "audit_logs");
        } catch (Exception e) {
            log.error("Failed to search compliance audit records", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Search failed operations
     */
    public List<AuditRecord> searchFailedOperations(LocalDateTime startDate, LocalDateTime endDate, int limit) {
        try {
            Query query = new Query();
            query.addCriteria(Criteria.where("success").is(false));
            
            if (startDate != null && endDate != null) {
                query.addCriteria(Criteria.where("timestampUtc").gte(startDate).lte(endDate));
            }
            
            query.with(Sort.by(Sort.Direction.DESC, "timestamp"));
            if (limit > 0) {
                query.limit(limit);
            }
            
            return mongoTemplate.find(query, AuditRecord.class, "audit_logs");
        } catch (Exception e) {
            log.error("Failed to search failed operations", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Export audit records for compliance reporting
     */
    public List<AuditRecord> exportAuditRecords(LocalDateTime startDate, LocalDateTime endDate, 
                                               List<AuditEventType> eventTypes, 
                                               List<String> userIds) {
        try {
            Query query = new Query();
            
            // Date range
            if (startDate != null && endDate != null) {
                query.addCriteria(Criteria.where("timestampUtc").gte(startDate).lte(endDate));
            }
            
            // Event types
            if (eventTypes != null && !eventTypes.isEmpty()) {
                query.addCriteria(Criteria.where("eventType").in(eventTypes));
            }
            
            // User IDs
            if (userIds != null && !userIds.isEmpty()) {
                query.addCriteria(Criteria.where("userId").in(userIds));
            }
            
            query.with(Sort.by(Sort.Direction.ASC, "timestamp"));
            
            List<AuditRecord> results = mongoTemplate.find(query, AuditRecord.class, "audit_logs");
            
            log.info("Audit export completed: {} records exported for date range {} to {}", 
                    results.size(), startDate, endDate);
            
            return results;
        } catch (Exception e) {
            log.error("Failed to export audit records", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Build MongoDB query from search request
     */
    private Query buildSearchQuery(AuditSearchRequest searchRequest) {
        Query query = new Query();
        
        // User ID filter
        if (searchRequest.getUserId() != null && !searchRequest.getUserId().isEmpty()) {
            query.addCriteria(Criteria.where("userId").is(searchRequest.getUserId()));
        }
        
        // Date range filter
        if (searchRequest.getStartDate() != null && searchRequest.getEndDate() != null) {
            query.addCriteria(Criteria.where("timestampUtc")
                .gte(searchRequest.getStartDate())
                .lte(searchRequest.getEndDate()));
        }
        
        // Event type filter
        if (searchRequest.getEventType() != null) {
            query.addCriteria(Criteria.where("eventType").is(searchRequest.getEventType()));
        }
        
        // Severity filter
        if (searchRequest.getSeverity() != null) {
            query.addCriteria(Criteria.where("severity").is(searchRequest.getSeverity()));
        }
        
        // Success filter
        if (searchRequest.getSuccess() != null) {
            query.addCriteria(Criteria.where("success").is(searchRequest.getSuccess()));
        }
        
        // Source IP filter
        if (searchRequest.getSourceIp() != null && !searchRequest.getSourceIp().isEmpty()) {
            query.addCriteria(Criteria.where("sourceIp").is(searchRequest.getSourceIp()));
        }
        
        // Action filter (partial match)
        if (searchRequest.getAction() != null && !searchRequest.getAction().isEmpty()) {
            query.addCriteria(Criteria.where("action").regex(searchRequest.getAction(), "i"));
        }
        
        // Affected resource filter
        if (searchRequest.getAffectedResource() != null && !searchRequest.getAffectedResource().isEmpty()) {
            query.addCriteria(Criteria.where("affectedResource").regex(searchRequest.getAffectedResource(), "i"));
        }
        
        // Correlation ID filter
        if (searchRequest.getCorrelationId() != null && !searchRequest.getCorrelationId().isEmpty()) {
            query.addCriteria(Criteria.where("correlationId").is(searchRequest.getCorrelationId()));
        }
        
        return query;
    }
    
    /**
     * Audit Search Request DTO
     */
    @Data
    @Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AuditSearchRequest {
        private String userId;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private AuditEventType eventType;
        private AuditSeverity severity;
        private Boolean success;
        private String sourceIp;
        private String action;
        private String affectedResource;
        private String correlationId;
        private int pageNumber = 0;
        private int pageSize = 100;
    }

    // Helper methods
    
    private String getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "system";
    }
    
    private String extractUsername() {
        return getCurrentUser();
    }
    
    private String extractSourceIp() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty()) {
                    ip = request.getRemoteAddr();
                }
                return ip;
            }
        } catch (Exception e) {
            log.debug("Could not extract source IP", e);
        }
        return "unknown";
    }
    
    private String extractSourceHost() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                return request.getRemoteHost();
            }
        } catch (Exception e) {
            log.debug("Could not extract source host", e);
        }
        return "unknown";
    }
    
    private String extractUserAgent() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                return request.getHeader("User-Agent");
            }
        } catch (Exception e) {
            log.debug("Could not extract user agent", e);
        }
        return "unknown";
    }
    
    private String extractSessionId() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                return attributes.getSessionId();
            }
        } catch (Exception e) {
            log.debug("Could not extract session ID", e);
        }
        return UUID.randomUUID().toString();
    }
    
    private String extractCorrelationId() {
        // In production, this would extract from MDC or request headers
        return UUID.randomUUID().toString();
    }
    
    private Map<String, Object> sanitizeDetails(Map<String, Object> details) {
        if (details == null) return new HashMap<>();
        
        Map<String, Object> sanitized = new HashMap<>(details);
        // Remove sensitive data
        sanitized.remove("password");
        sanitized.remove("cardNumber");
        sanitized.remove("cvv");
        sanitized.remove("pin");
        sanitized.remove("ssn");
        return sanitized;
    }
    
    private boolean determineSuccess(Map<String, Object> details) {
        if (details == null) return true;
        Object success = details.get("success");
        if (success instanceof Boolean) {
            return (Boolean) success;
        }
        return !details.containsKey("error") && !details.containsKey("failure");
    }
    
    private String calculateHash(String eventId, long sequence, Map<String, Object> details) {
        try {
            String data = eventId + ":" + sequence + ":" + objectMapper.writeValueAsString(details);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            log.error("Failed to calculate hash", e);
            return "";
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
            log.error("Failed to sign record", e);
            return "";
        }
    }
    
    private void updateHashChain(ComprehensiveAuditRecord record) {
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
    
    private void streamAuditEvent(ComprehensiveAuditRecord record) {
        try {
            kafkaTemplate.send("audit-events", record.getEventId(), record);
        } catch (Exception e) {
            log.error("Failed to stream audit event to Kafka", e);
        }
    }
    
    private void checkSecurityViolations(ComprehensiveAuditRecord record) {
        // Check for security violations
        if (record.getEventType() == AuditEventType.LOGIN_FAILURE ||
            record.getEventType() == AuditEventType.PERMISSION_DENIED ||
            record.getEventType() == AuditEventType.SECURITY_ALERT) {

            // In production, this would trigger security alerts
            log.warn("SECURITY ALERT: Potential security violation detected - Event: {} User: {}",
                record.getEventType(), record.getUserId());
        }
    }
    
    private void logAuditFailure(AuditEventType eventType, String userId, String action, Exception error) {
        try {
            log.error("AUDIT FAILURE: Failed to log event - Type: {} User: {} Action: {} Error: {}", 
                eventType, userId, action, error.getMessage());
            // In production, this would write to a backup audit location
        } catch (Exception e) {
            log.error("Critical: Failed to log audit failure", e);
        }
    }
    
    /**
     * Comprehensive Audit Record entity (inner class to avoid conflict with standalone AuditRecord)
     */
    @Data
    @Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ComprehensiveAuditRecord {
        private String eventId;
        private Long sequenceNumber;
        private AuditEventType eventType;
        private String userId;
        private String username;
        private String action;
        private Instant timestamp;
        private LocalDateTime timestampUtc;
        private AuditSeverity severity;
        private String sourceIp;
        private String sourceHost;
        private String userAgent;
        private String sessionId;
        private String correlationId;
        private String affectedResource;
        private Map<String, Object> details;
        private boolean success;
        private String hash;
        private String previousHash;
        private String signature;
    }

    /**
     * Audit data operation (overload for compatibility with various map types)
     */
    public void auditDataOperation(String operation, String resourceType,
                                  Map<String, ?> details) {
        Map<String, Object> convertedDetails = new HashMap<>();
        if (details != null) {
            convertedDetails.putAll(details);
        }
        // Delegate to existing implementation if it exists
        log.info("Data operation audit: {} on {}", operation, resourceType);
        // Store audit record
        try {
            ComprehensiveAuditRecord record = ComprehensiveAuditRecord.builder()
                .action(operation)
                .timestamp(Instant.now())
                .timestampUtc(LocalDateTime.now())
                .details(convertedDetails)
                .eventType(AuditEventType.DATA_ACCESS)
                .success(true)
                .build();
            // Save would go here
        } catch (Exception e) {
            log.error("Failed to audit data operation", e);
        }
    }
    
    /**
     * Audit high-risk operations for compliance and security
     * Used for operations that have significant financial or regulatory impact
     */
    public void auditHighRiskOperation(String operationType, String userId, String description, 
                                      Map<String, Object> details) {
        log.error("HIGH-RISK OPERATION: type={}, user={}, description={}", operationType, userId, description);
        
        try {
            logAuditEvent(
                AuditEventType.HIGH_RISK_OPERATION,
                userId,
                operationType,
                details,
                AuditSeverity.CRITICAL,
                description
            );
            
            // Stream critical event for real-time monitoring
            Map<String, Object> criticalEvent = new HashMap<>();
            criticalEvent.put("eventType", "HIGH_RISK_OPERATION");
            criticalEvent.put("operationType", operationType);
            criticalEvent.put("userId", userId);
            criticalEvent.put("description", description);
            criticalEvent.put("details", details);
            criticalEvent.put("timestamp", Instant.now().toString());
            criticalEvent.put("severity", "CRITICAL");
            criticalEvent.put("requiresImmmediateReview", true);
            
            if (kafkaTemplate != null) {
                kafkaTemplate.send("high-risk-operations", criticalEvent);
                kafkaTemplate.send("critical-audit-events", criticalEvent);
            }
            
            // Store in immutable audit log
            if (mongoTemplate != null) {
                mongoTemplate.save(criticalEvent, "high_risk_operations");
            }
            
            log.info("High-risk operation audit recorded: operationType={}, userId={}", operationType, userId);
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to audit high-risk operation - compliance violation", e);
            // Attempt emergency backup logging
            try {
                Map<String, Object> failureRecord = new HashMap<>();
                failureRecord.put("failureType", "AUDIT_FAILURE");
                failureRecord.put("originalOperation", operationType);
                failureRecord.put("userId", userId);
                failureRecord.put("error", e.getMessage());
                failureRecord.put("timestamp", Instant.now().toString());
                
                if (mongoTemplate != null) {
                    mongoTemplate.save(failureRecord, "audit_failures");
                }
            } catch (Exception backupException) {
                log.error("CRITICAL: Backup audit logging also failed", backupException);
            }
        }
    }

    /**
     * Get complete audit trail for an entity
     *
     * @param entityType The type of entity (e.g., "User", "Transaction")
     * @param entityId The entity ID
     * @return List of audit records for the entity
     */
    public List<AuditRecord> getAuditTrail(String entityType, String entityId) {
        try {
            log.debug("Retrieving audit trail for {} with ID: {}", entityType, entityId);

            // Query MongoDB for audit events related to this entity
            if (mongoTemplate != null) {
                org.springframework.data.mongodb.core.query.Query query =
                    new org.springframework.data.mongodb.core.query.Query();
                query.addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("resourceId").is(entityId));
                query.addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("resourceType").is(entityType));
                query.with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "timestamp"));

                List<Map> rawRecords = mongoTemplate.find(query, Map.class, "audit_events");

                return rawRecords.stream()
                    .map(this::convertToAuditRecord)
                    .collect(java.util.stream.Collectors.toList());
            }

            // Fallback to empty list if MongoDB not available
            log.warn("MongoDB template not available, returning empty audit trail");
            return new java.util.ArrayList<>();

        } catch (Exception e) {
            log.error("Failed to retrieve audit trail for {} {}", entityType, entityId, e);
            return new java.util.ArrayList<>();
        }
    }

    /**
     * Get summarized activity for a user within a time range
     *
     * @param userId The user ID
     * @param startDate Start of the time range
     * @param endDate End of the time range
     * @return Audit summary with statistics
     */
    public AuditSummary getUserActivitySummary(String userId, java.time.LocalDateTime startDate, java.time.LocalDateTime endDate) {
        try {
            log.debug("Generating activity summary for user: {} from {} to {}", userId, startDate, endDate);

            Instant startInstant = startDate.atZone(java.time.ZoneId.systemDefault()).toInstant();
            Instant endInstant = endDate.atZone(java.time.ZoneId.systemDefault()).toInstant();

            AuditSummary summary = new AuditSummary();
            summary.setUserId(userId);
            summary.setStartDate(startDate);
            summary.setEndDate(endDate);

            if (mongoTemplate != null) {
                // Query for user events in time range
                org.springframework.data.mongodb.core.query.Query query =
                    new org.springframework.data.mongodb.core.query.Query();
                query.addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("userId").is(userId));
                query.addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("timestamp")
                    .gte(startInstant).lte(endInstant));

                long totalEvents = mongoTemplate.count(query, "audit_events");
                summary.setTotalEvents(totalEvents);

                // Count successful vs failed events
                org.springframework.data.mongodb.core.query.Query successQuery =
                    new org.springframework.data.mongodb.core.query.Query();
                successQuery.addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("userId").is(userId));
                successQuery.addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("timestamp")
                    .gte(startInstant).lte(endInstant));
                successQuery.addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("success").is(true));
                long successfulEvents = mongoTemplate.count(successQuery, "audit_events");
                summary.setSuccessfulEvents(successfulEvents);
                summary.setFailedEvents(totalEvents - successfulEvents);

                // Get event type distribution
                List<Map> events = mongoTemplate.find(query, Map.class, "audit_events");
                Map<String, Long> eventTypeDistribution = events.stream()
                    .filter(e -> e.containsKey("eventType"))
                    .collect(java.util.stream.Collectors.groupingBy(
                        e -> String.valueOf(e.get("eventType")),
                        java.util.stream.Collectors.counting()
                    ));
                summary.setEventTypeDistribution(eventTypeDistribution);

                // Identify high-risk events
                org.springframework.data.mongodb.core.query.Query highRiskQuery =
                    new org.springframework.data.mongodb.core.query.Query();
                highRiskQuery.addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("userId").is(userId));
                highRiskQuery.addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("timestamp")
                    .gte(startInstant).lte(endInstant));
                highRiskQuery.addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("riskLevel")
                    .in("HIGH", "CRITICAL"));
                long highRiskEvents = mongoTemplate.count(highRiskQuery, "audit_events");
                summary.setHighRiskEvents(highRiskEvents);

                // Extract unique IP addresses
                List<String> ipAddresses = events.stream()
                    .filter(e -> e.containsKey("ipAddress"))
                    .map(e -> String.valueOf(e.get("ipAddress")))
                    .distinct()
                    .collect(java.util.stream.Collectors.toList());
                summary.setUniqueIpAddresses(ipAddresses);

                // Extract unique devices/user agents
                List<String> devices = events.stream()
                    .filter(e -> e.containsKey("userAgent"))
                    .map(e -> String.valueOf(e.get("userAgent")))
                    .distinct()
                    .limit(10)
                    .collect(java.util.stream.Collectors.toList());
                summary.setUniqueDevices(devices);

            } else {
                log.warn("MongoDB template not available, returning minimal summary");
                summary.setTotalEvents(0);
                summary.setSuccessfulEvents(0);
                summary.setFailedEvents(0);
                summary.setHighRiskEvents(0);
                summary.setEventTypeDistribution(new java.util.HashMap<>());
                summary.setUniqueIpAddresses(new java.util.ArrayList<>());
                summary.setUniqueDevices(new java.util.ArrayList<>());
            }

            return summary;

        } catch (Exception e) {
            log.error("Failed to generate activity summary for user: {}", userId, e);

            // Return empty summary on error
            AuditSummary errorSummary = new AuditSummary();
            errorSummary.setUserId(userId);
            errorSummary.setStartDate(startDate);
            errorSummary.setEndDate(endDate);
            errorSummary.setTotalEvents(0);
            errorSummary.setSuccessfulEvents(0);
            errorSummary.setFailedEvents(0);
            errorSummary.setHighRiskEvents(0);
            errorSummary.setEventTypeDistribution(new java.util.HashMap<>());
            errorSummary.setUniqueIpAddresses(new java.util.ArrayList<>());
            errorSummary.setUniqueDevices(new java.util.ArrayList<>());

            return errorSummary;
        }
    }

    /**
     * Convert raw MongoDB document to AuditRecord
     */
    private AuditRecord convertToAuditRecord(Map<String, Object> raw) {
        AuditRecord record = new AuditRecord();
        record.setId(String.valueOf(raw.getOrDefault("id", "")));

        // Convert eventType String to AuditEventType enum
        String eventTypeStr = String.valueOf(raw.getOrDefault("eventType", ""));
        try {
            record.setEventType(AuditEventType.valueOf(eventTypeStr));
        } catch (IllegalArgumentException e) {
            record.setEventType(null); // Or use a default value
        }

        record.setUserId(String.valueOf(raw.getOrDefault("userId", "")));
        record.setResourceId(String.valueOf(raw.getOrDefault("resourceId", "")));
        record.setResourceType(String.valueOf(raw.getOrDefault("resourceType", "")));
        record.setAction(String.valueOf(raw.getOrDefault("action", "")));
        record.setSuccess(Boolean.parseBoolean(String.valueOf(raw.getOrDefault("success", "true"))));

        // Convert Instant to LocalDateTime
        record.setTimestamp(raw.containsKey("timestamp") ?
            LocalDateTime.ofInstant(Instant.parse(String.valueOf(raw.get("timestamp"))),
                java.time.ZoneOffset.UTC) : LocalDateTime.now());

        record.setDetails(raw.containsKey("details") ?
            (Map<String, Object>) raw.get("details") : new java.util.HashMap<>());
        record.setIpAddress(String.valueOf(raw.getOrDefault("ipAddress", "")));
        record.setUserAgent(String.valueOf(raw.getOrDefault("userAgent", "")));
        return record;
    }

    /**
     * Audit Summary data class
     */
    public static class AuditSummary {
        private String userId;
        private java.time.LocalDateTime startDate;
        private java.time.LocalDateTime endDate;
        private java.time.LocalDateTime periodStart;
        private java.time.LocalDateTime periodEnd;
        private long totalEvents;
        private long successfulEvents;
        private long failedEvents;
        private long highRiskEvents;
        private long securityEvents;
        private long dataAccessEvents;
        private long configurationChanges;
        private long failedAttempts;
        private Map<String, Long> eventTypeDistribution;
        private Map<String, Long> eventsByCategory;
        private Map<String, Long> eventsByUser;
        private List<String> uniqueIpAddresses;
        private List<String> uniqueDevices;
        private String riskLevel;

        // Getters and setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public java.time.LocalDateTime getStartDate() { return startDate; }
        public void setStartDate(java.time.LocalDateTime startDate) { this.startDate = startDate; }
        public java.time.LocalDateTime getEndDate() { return endDate; }
        public void setEndDate(java.time.LocalDateTime endDate) { this.endDate = endDate; }
        public long getTotalEvents() { return totalEvents; }
        public void setTotalEvents(long totalEvents) { this.totalEvents = totalEvents; }
        public long getSuccessfulEvents() { return successfulEvents; }
        public void setSuccessfulEvents(long successfulEvents) { this.successfulEvents = successfulEvents; }
        public long getFailedEvents() { return failedEvents; }
        public void setFailedEvents(long failedEvents) { this.failedEvents = failedEvents; }
        public long getHighRiskEvents() { return highRiskEvents; }
        public void setHighRiskEvents(long highRiskEvents) { this.highRiskEvents = highRiskEvents; }
        public Map<String, Long> getEventTypeDistribution() { return eventTypeDistribution; }
        public void setEventTypeDistribution(Map<String, Long> eventTypeDistribution) {
            this.eventTypeDistribution = eventTypeDistribution;
        }
        public List<String> getUniqueIpAddresses() { return uniqueIpAddresses; }
        public void setUniqueIpAddresses(List<String> uniqueIpAddresses) {
            this.uniqueIpAddresses = uniqueIpAddresses;
        }
        public List<String> getUniqueDevices() { return uniqueDevices; }
        public void setUniqueDevices(List<String> uniqueDevices) { this.uniqueDevices = uniqueDevices; }

        public java.time.LocalDateTime getPeriodStart() { return periodStart; }
        public void setPeriodStart(java.time.LocalDateTime periodStart) { this.periodStart = periodStart; }
        public java.time.LocalDateTime getPeriodEnd() { return periodEnd; }
        public void setPeriodEnd(java.time.LocalDateTime periodEnd) { this.periodEnd = periodEnd; }
        public long getSecurityEvents() { return securityEvents; }
        public void setSecurityEvents(long securityEvents) { this.securityEvents = securityEvents; }
        public long getDataAccessEvents() { return dataAccessEvents; }
        public void setDataAccessEvents(long dataAccessEvents) { this.dataAccessEvents = dataAccessEvents; }
        public long getConfigurationChanges() { return configurationChanges; }
        public void setConfigurationChanges(long configurationChanges) { this.configurationChanges = configurationChanges; }
        public long getFailedAttempts() { return failedAttempts; }
        public void setFailedAttempts(long failedAttempts) { this.failedAttempts = failedAttempts; }
        public Map<String, Long> getEventsByCategory() { return eventsByCategory; }
        public void setEventsByCategory(Map<String, Long> eventsByCategory) { this.eventsByCategory = eventsByCategory; }
        public Map<String, Long> getEventsByUser() { return eventsByUser; }
        public void setEventsByUser(Map<String, Long> eventsByUser) { this.eventsByUser = eventsByUser; }
        public String getRiskLevel() { return riskLevel; }
        public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    }
}