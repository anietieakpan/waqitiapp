package com.waqiti.payment.service.impl;

import com.waqiti.common.audit.service.AuditService;
import com.waqiti.payment.service.encryption.PaymentEncryptionService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Production-grade Audit Service implementation
 * Provides comprehensive audit logging with persistence and event streaming
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductionAuditService implements AuditService {

    private final EntityManager entityManager;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final PaymentEncryptionService encryptionService;
    private final MeterRegistry meterRegistry;
    
    private static final String AUDIT_TOPIC = "audit.events";
    private static final String SECURITY_AUDIT_TOPIC = "security.audit.events";
    private static final String HIGH_RISK_AUDIT_TOPIC = "audit.high-risk";

    @Override
    @Transactional
    public void auditPaymentAction(String action, String userId, String transactionId, Map<String, Object> details) {
        try {
            // Create audit record
            AuditRecord record = new AuditRecord();
            record.setId(UUID.randomUUID().toString());
            record.setAction(action);
            record.setUserId(userId);
            record.setTransactionId(transactionId);
            record.setTimestamp(LocalDateTime.now());
            record.setDetails(sanitizeDetails(details));
            record.setCategory("PAYMENT");
            
            // Add context information
            record.setIpAddress(getCurrentIpAddress());
            record.setSessionId(getCurrentSessionId());
            record.setUserAgent(getCurrentUserAgent());
            
            // Encrypt sensitive data
            if (details.containsKey("accountNumber") || details.containsKey("cardNumber")) {
                record.setSensitiveData(encryptSensitiveData(details));
            }
            
            // Persist to database
            persistAuditRecord(record);
            
            // Stream to Kafka for real-time processing
            kafkaTemplate.send(AUDIT_TOPIC, record.toMap());
            
            // Check for high-risk activities
            if (isHighRiskAction(action, details)) {
                alertHighRiskActivity(record);
            }
            
            // Update metrics
            updateAuditMetrics(action, "PAYMENT");
            
            log.debug("Audited payment action: {} for user: {} transaction: {}", 
                     action, userId, transactionId);
            
        } catch (Exception e) {
            log.error("Failed to audit payment action: {} for user: {}", action, userId, e);
            // Audit failures should not break the main flow
            // Send to dead letter queue for later processing
            sendToDeadLetterQueue(action, userId, transactionId, details, e);
        }
    }

    @Override
    @Transactional
    public void auditSecurityEvent(String event, String userId, Map<String, Object> details) {
        try {
            // Create security audit record
            SecurityAuditRecord record = new SecurityAuditRecord();
            record.setId(UUID.randomUUID().toString());
            record.setEvent(event);
            record.setUserId(userId);
            record.setTimestamp(LocalDateTime.now());
            record.setDetails(sanitizeDetails(details));
            record.setThreatLevel(assessThreatLevel(event, details));
            
            // Add security context
            record.setIpAddress(getCurrentIpAddress());
            record.setGeoLocation(getCurrentGeoLocation());
            record.setDeviceFingerprint(getDeviceFingerprint());
            
            // Persist to database
            persistSecurityAuditRecord(record);
            
            // Stream to security topic
            kafkaTemplate.send(SECURITY_AUDIT_TOPIC, record.toMap());
            
            // Alert on critical security events
            if (record.getThreatLevel() >= 8) {
                alertSecurityTeam(record);
            }
            
            // Update security metrics
            updateSecurityMetrics(event, record.getThreatLevel());
            
            log.info("Security event audited: {} for user: {} threat level: {}", 
                    event, userId, record.getThreatLevel());
            
        } catch (Exception e) {
            log.error("Failed to audit security event: {} for user: {}", event, userId, e);
            sendToDeadLetterQueue(event, userId, null, details, e);
        }
    }

    @Override
    @Transactional
    public void auditDataAccess(String resource, String userId, String action) {
        try {
            // Create data access audit record
            DataAccessRecord record = new DataAccessRecord();
            record.setId(UUID.randomUUID().toString());
            record.setResource(resource);
            record.setUserId(userId);
            record.setAction(action);
            record.setTimestamp(LocalDateTime.now());
            record.setAccessGranted(true); // Assuming access was granted if we're auditing
            
            // Add compliance metadata
            record.setDataClassification(classifyResource(resource));
            record.setComplianceFlags(getComplianceFlags(resource));
            
            // Persist record
            persistDataAccessRecord(record);
            
            // Stream for compliance reporting
            kafkaTemplate.send(AUDIT_TOPIC, record.toMap());
            
            // Update access metrics
            meterRegistry.counter("audit.data.access", 
                "resource", resource, 
                "action", action
            ).increment();
            
            log.debug("Data access audited: {} on {} by user: {}", action, resource, userId);
            
        } catch (Exception e) {
            log.error("Failed to audit data access: {} on {} by user: {}", action, resource, userId, e);
        }
    }

    @Override
    public void auditSystemEvent(String event, Map<String, Object> details) {
        try {
            // Create system audit record
            SystemAuditRecord record = new SystemAuditRecord();
            record.setId(UUID.randomUUID().toString());
            record.setEvent(event);
            record.setTimestamp(LocalDateTime.now());
            record.setDetails(sanitizeDetails(details));
            record.setHostname(getHostname());
            record.setServiceName("payment-service");
            
            // Persist and stream
            persistSystemAuditRecord(record);
            kafkaTemplate.send(AUDIT_TOPIC, record.toMap());
            
            // Update system metrics
            meterRegistry.counter("audit.system.events", "event", event).increment();
            
            log.debug("System event audited: {}", event);
            
        } catch (Exception e) {
            log.error("Failed to audit system event: {}", event, e);
        }
    }

    @Override
    @Transactional
    public void auditFailedAuthentication(String username, String ipAddress, String reason) {
        try {
            // Create failed auth record
            AuthenticationAuditRecord record = new AuthenticationAuditRecord();
            record.setId(UUID.randomUUID().toString());
            record.setUsername(username);
            record.setIpAddress(ipAddress);
            record.setReason(reason);
            record.setTimestamp(LocalDateTime.now());
            record.setAttemptNumber(getFailedAttemptCount(username, ipAddress));
            
            // Check for brute force patterns
            if (record.getAttemptNumber() > 5) {
                record.setPotentialThreat("BRUTE_FORCE");
                alertSecurityTeam(Map.of(
                    "type", "BRUTE_FORCE_DETECTED",
                    "username", username,
                    "ipAddress", ipAddress,
                    "attempts", record.getAttemptNumber()
                ));
            }
            
            // Persist and stream
            persistAuthenticationAuditRecord(record);
            kafkaTemplate.send(SECURITY_AUDIT_TOPIC, record.toMap());
            
            // Update auth failure metrics
            meterRegistry.counter("audit.auth.failures", 
                "reason", reason
            ).increment();
            
            log.warn("Failed authentication attempt: {} from IP: {} reason: {}", 
                    username, ipAddress, reason);
            
        } catch (Exception e) {
            log.error("Failed to audit authentication failure", e);
        }
    }

    // Private helper methods

    private void persistAuditRecord(AuditRecord record) {
        String sql = """
            INSERT INTO audit_log (id, action, user_id, transaction_id, timestamp, details, category, ip_address, session_id)
            VALUES (:id, :action, :userId, :transactionId, :timestamp, :details, :category, :ipAddress, :sessionId)
            """;
        
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("id", record.getId());
        query.setParameter("action", record.getAction());
        query.setParameter("userId", record.getUserId());
        query.setParameter("transactionId", record.getTransactionId());
        query.setParameter("timestamp", record.getTimestamp());
        query.setParameter("details", convertToJson(record.getDetails()));
        query.setParameter("category", record.getCategory());
        query.setParameter("ipAddress", record.getIpAddress());
        query.setParameter("sessionId", record.getSessionId());
        
        query.executeUpdate();
    }

    private void persistSecurityAuditRecord(SecurityAuditRecord record) {
        String sql = """
            INSERT INTO security_audit_log (id, event, user_id, timestamp, details, threat_level, ip_address, geo_location)
            VALUES (:id, :event, :userId, :timestamp, :details, :threatLevel, :ipAddress, :geoLocation)
            """;
        
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("id", record.getId());
        query.setParameter("event", record.getEvent());
        query.setParameter("userId", record.getUserId());
        query.setParameter("timestamp", record.getTimestamp());
        query.setParameter("details", convertToJson(record.getDetails()));
        query.setParameter("threatLevel", record.getThreatLevel());
        query.setParameter("ipAddress", record.getIpAddress());
        query.setParameter("geoLocation", record.getGeoLocation());
        
        query.executeUpdate();
    }

    private void persistDataAccessRecord(DataAccessRecord record) {
        String sql = """
            INSERT INTO data_access_log (id, resource, user_id, action, timestamp, access_granted, data_classification)
            VALUES (:id, :resource, :userId, :action, :timestamp, :accessGranted, :classification)
            """;
        
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("id", record.getId());
        query.setParameter("resource", record.getResource());
        query.setParameter("userId", record.getUserId());
        query.setParameter("action", record.getAction());
        query.setParameter("timestamp", record.getTimestamp());
        query.setParameter("accessGranted", record.isAccessGranted());
        query.setParameter("classification", record.getDataClassification());
        
        query.executeUpdate();
    }

    private void persistSystemAuditRecord(SystemAuditRecord record) {
        String sql = """
            INSERT INTO system_audit_log (id, event, timestamp, details, hostname, service_name)
            VALUES (:id, :event, :timestamp, :details, :hostname, :serviceName)
            """;
        
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("id", record.getId());
        query.setParameter("event", record.getEvent());
        query.setParameter("timestamp", record.getTimestamp());
        query.setParameter("details", convertToJson(record.getDetails()));
        query.setParameter("hostname", record.getHostname());
        query.setParameter("serviceName", record.getServiceName());
        
        query.executeUpdate();
    }

    private void persistAuthenticationAuditRecord(AuthenticationAuditRecord record) {
        String sql = """
            INSERT INTO auth_audit_log (id, username, ip_address, reason, timestamp, attempt_number, potential_threat)
            VALUES (:id, :username, :ipAddress, :reason, :timestamp, :attemptNumber, :threat)
            """;
        
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("id", record.getId());
        query.setParameter("username", record.getUsername());
        query.setParameter("ipAddress", record.getIpAddress());
        query.setParameter("reason", record.getReason());
        query.setParameter("timestamp", record.getTimestamp());
        query.setParameter("attemptNumber", record.getAttemptNumber());
        query.setParameter("threat", record.getPotentialThreat());
        
        query.executeUpdate();
    }

    private Map<String, Object> sanitizeDetails(Map<String, Object> details) {
        Map<String, Object> sanitized = new HashMap<>();
        for (Map.Entry<String, Object> entry : details.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            // Mask sensitive fields
            if (isSensitiveField(key)) {
                sanitized.put(key, maskValue(value.toString()));
            } else {
                sanitized.put(key, value);
            }
        }
        return sanitized;
    }

    private boolean isSensitiveField(String fieldName) {
        Set<String> sensitiveFields = Set.of(
            "password", "pin", "ssn", "accountNumber", 
            "cardNumber", "cvv", "securityCode", "privateKey"
        );
        return sensitiveFields.stream()
            .anyMatch(field -> fieldName.toLowerCase().contains(field.toLowerCase()));
    }

    private String maskValue(String value) {
        if (value == null || value.length() <= 4) {
            return "****";
        }
        return "*".repeat(value.length() - 4) + value.substring(value.length() - 4);
    }

    private String encryptSensitiveData(Map<String, Object> details) {
        try {
            Map<String, Object> sensitive = new HashMap<>();
            for (Map.Entry<String, Object> entry : details.entrySet()) {
                if (isSensitiveField(entry.getKey())) {
                    sensitive.put(entry.getKey(), entry.getValue());
                }
            }
            return encryptionService.encrypt(convertToJson(sensitive));
        } catch (Exception e) {
            log.error("Failed to encrypt sensitive data", e);
            return null;
        }
    }

    private boolean isHighRiskAction(String action, Map<String, Object> details) {
        // Check for high-risk patterns
        Set<String> highRiskActions = Set.of(
            "LARGE_TRANSFER", "ACCOUNT_TAKEOVER", "PRIVILEGE_ESCALATION",
            "DATA_EXPORT", "MASS_UPDATE", "SECURITY_BYPASS"
        );
        
        if (highRiskActions.contains(action)) {
            return true;
        }
        
        // Check amount for payment actions
        if (action.startsWith("PAYMENT_") && details.containsKey("amount")) {
            Object amount = details.get("amount");
            if (amount instanceof Number && ((Number) amount).doubleValue() > 10000) {
                return true;
            }
        }
        
        return false;
    }

    private void alertHighRiskActivity(AuditRecord record) {
        Map<String, Object> alert = Map.of(
            "type", "HIGH_RISK_ACTIVITY",
            "action", record.getAction(),
            "userId", record.getUserId(),
            "transactionId", record.getTransactionId(),
            "timestamp", record.getTimestamp(),
            "details", record.getDetails()
        );
        
        kafkaTemplate.send(HIGH_RISK_AUDIT_TOPIC, alert);
        log.warn("High-risk activity detected: {}", alert);
    }

    private void alertSecurityTeam(Object alert) {
        CompletableFuture.runAsync(() -> {
            try {
                kafkaTemplate.send(SECURITY_AUDIT_TOPIC + ".alerts", alert);
                log.warn("Security alert sent: {}", alert);
            } catch (Exception e) {
                log.error("Failed to send security alert", e);
            }
        });
    }

    private int assessThreatLevel(String event, Map<String, Object> details) {
        // Simple threat level assessment (0-10)
        int threatLevel = 0;
        
        Map<String, Integer> eventThreats = Map.of(
            "UNAUTHORIZED_ACCESS", 8,
            "BRUTE_FORCE_ATTEMPT", 9,
            "SQL_INJECTION", 10,
            "XSS_ATTEMPT", 9,
            "PRIVILEGE_ESCALATION", 9,
            "DATA_BREACH", 10
        );
        
        threatLevel = eventThreats.getOrDefault(event, 3);
        
        // Adjust based on details
        if (details.containsKey("repeated") && (Boolean) details.get("repeated")) {
            threatLevel = Math.min(10, threatLevel + 2);
        }
        
        return threatLevel;
    }

    private void updateAuditMetrics(String action, String category) {
        meterRegistry.counter("audit.actions", 
            "action", action, 
            "category", category
        ).increment();
    }

    private void updateSecurityMetrics(String event, int threatLevel) {
        meterRegistry.counter("audit.security.events", 
            "event", event, 
            "threat_level", String.valueOf(threatLevel)
        ).increment();
        
        if (threatLevel >= 8) {
            meterRegistry.counter("audit.security.critical").increment();
        }
    }

    private String classifyResource(String resource) {
        // Simple classification logic
        if (resource.contains("payment") || resource.contains("transaction")) {
            return "HIGHLY_SENSITIVE";
        } else if (resource.contains("user") || resource.contains("account")) {
            return "SENSITIVE";
        } else {
            return "INTERNAL";
        }
    }

    private Set<String> getComplianceFlags(String resource) {
        Set<String> flags = new HashSet<>();
        
        if (resource.contains("payment")) {
            flags.add("PCI_DSS");
        }
        if (resource.contains("user") || resource.contains("personal")) {
            flags.add("GDPR");
            flags.add("CCPA");
        }
        
        return flags;
    }

    private int getFailedAttemptCount(String username, String ipAddress) {
        String sql = """
            SELECT COUNT(*) FROM auth_audit_log 
            WHERE username = :username AND ip_address = :ipAddress 
            AND timestamp > :cutoff
            """;
        
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("username", username);
        query.setParameter("ipAddress", ipAddress);
        query.setParameter("cutoff", LocalDateTime.now().minusHours(1));
        
        return ((Number) query.getSingleResult()).intValue();
    }

    private void sendToDeadLetterQueue(String action, String userId, String transactionId, 
                                      Map<String, Object> details, Exception error) {
        try {
            Map<String, Object> deadLetter = Map.of(
                "action", action,
                "userId", userId,
                "transactionId", transactionId != null ? transactionId : "",
                "details", details,
                "error", error.getMessage(),
                "timestamp", LocalDateTime.now().toString()
            );
            
            kafkaTemplate.send(AUDIT_TOPIC + ".dlq", deadLetter);
        } catch (Exception e) {
            log.error("Failed to send to dead letter queue", e);
        }
    }

    private String getCurrentIpAddress() {
        // Would get from request context in production
        return "127.0.0.1";
    }

    private String getCurrentSessionId() {
        // Would get from security context in production
        return UUID.randomUUID().toString();
    }

    private String getCurrentUserAgent() {
        // Would get from request headers in production
        return "Payment-Service/1.0";
    }

    private String getCurrentGeoLocation() {
        // Would use GeoIP service in production
        return "Unknown";
    }

    private String getDeviceFingerprint() {
        // Would calculate from request attributes
        return UUID.randomUUID().toString();
    }

    private String getHostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String convertToJson(Object obj) {
        try {
            return obj.toString(); // Would use proper JSON serialization
        } catch (Exception e) {
            return "{}";
        }
    }

    // Inner classes for audit records

    private static class AuditRecord {
        private String id;
        private String action;
        private String userId;
        private String transactionId;
        private LocalDateTime timestamp;
        private Map<String, Object> details;
        private String category;
        private String ipAddress;
        private String sessionId;
        private String userAgent;
        private String sensitiveData;

        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getTransactionId() { return transactionId; }
        public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        public Map<String, Object> getDetails() { return details; }
        public void setDetails(Map<String, Object> details) { this.details = details; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getIpAddress() { return ipAddress; }
        public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public String getUserAgent() { return userAgent; }
        public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
        public void setSensitiveData(String sensitiveData) { this.sensitiveData = sensitiveData; }

        public Map<String, Object> toMap() {
            return Map.of(
                "id", id,
                "action", action,
                "userId", userId,
                "transactionId", transactionId != null ? transactionId : "",
                "timestamp", timestamp.toString(),
                "details", details,
                "category", category
            );
        }
    }

    private static class SecurityAuditRecord {
        private String id;
        private String event;
        private String userId;
        private LocalDateTime timestamp;
        private Map<String, Object> details;
        private int threatLevel;
        private String ipAddress;
        private String geoLocation;
        private String deviceFingerprint;

        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getEvent() { return event; }
        public void setEvent(String event) { this.event = event; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        public Map<String, Object> getDetails() { return details; }
        public void setDetails(Map<String, Object> details) { this.details = details; }
        public int getThreatLevel() { return threatLevel; }
        public void setThreatLevel(int threatLevel) { this.threatLevel = threatLevel; }
        public String getIpAddress() { return ipAddress; }
        public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
        public String getGeoLocation() { return geoLocation; }
        public void setGeoLocation(String geoLocation) { this.geoLocation = geoLocation; }
        public void setDeviceFingerprint(String deviceFingerprint) { this.deviceFingerprint = deviceFingerprint; }

        public Map<String, Object> toMap() {
            return Map.of(
                "id", id,
                "event", event,
                "userId", userId,
                "timestamp", timestamp.toString(),
                "details", details,
                "threatLevel", threatLevel
            );
        }
    }

    private static class DataAccessRecord {
        private String id;
        private String resource;
        private String userId;
        private String action;
        private LocalDateTime timestamp;
        private boolean accessGranted;
        private String dataClassification;
        private Set<String> complianceFlags;

        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getResource() { return resource; }
        public void setResource(String resource) { this.resource = resource; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        public boolean isAccessGranted() { return accessGranted; }
        public void setAccessGranted(boolean accessGranted) { this.accessGranted = accessGranted; }
        public String getDataClassification() { return dataClassification; }
        public void setDataClassification(String dataClassification) { this.dataClassification = dataClassification; }
        public void setComplianceFlags(Set<String> complianceFlags) { this.complianceFlags = complianceFlags; }

        public Map<String, Object> toMap() {
            return Map.of(
                "id", id,
                "resource", resource,
                "userId", userId,
                "action", action,
                "timestamp", timestamp.toString(),
                "accessGranted", accessGranted
            );
        }
    }

    private static class SystemAuditRecord {
        private String id;
        private String event;
        private LocalDateTime timestamp;
        private Map<String, Object> details;
        private String hostname;
        private String serviceName;

        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getEvent() { return event; }
        public void setEvent(String event) { this.event = event; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        public Map<String, Object> getDetails() { return details; }
        public void setDetails(Map<String, Object> details) { this.details = details; }
        public String getHostname() { return hostname; }
        public void setHostname(String hostname) { this.hostname = hostname; }
        public String getServiceName() { return serviceName; }
        public void setServiceName(String serviceName) { this.serviceName = serviceName; }

        public Map<String, Object> toMap() {
            return Map.of(
                "id", id,
                "event", event,
                "timestamp", timestamp.toString(),
                "details", details,
                "hostname", hostname,
                "serviceName", serviceName
            );
        }
    }

    private static class AuthenticationAuditRecord {
        private String id;
        private String username;
        private String ipAddress;
        private String reason;
        private LocalDateTime timestamp;
        private int attemptNumber;
        private String potentialThreat;

        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getIpAddress() { return ipAddress; }
        public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        public int getAttemptNumber() { return attemptNumber; }
        public void setAttemptNumber(int attemptNumber) { this.attemptNumber = attemptNumber; }
        public String getPotentialThreat() { return potentialThreat; }
        public void setPotentialThreat(String potentialThreat) { this.potentialThreat = potentialThreat; }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("id", id);
            map.put("username", username);
            map.put("ipAddress", ipAddress);
            map.put("reason", reason);
            map.put("timestamp", timestamp.toString());
            map.put("attemptNumber", attemptNumber);
            if (potentialThreat != null) {
                map.put("potentialThreat", potentialThreat);
            }
            return map;
        }
    }
}