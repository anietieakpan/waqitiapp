package com.waqiti.common.audit;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class AuditModels {
    
    @Data
    @Builder
    public static class ComplianceAuditRequest {
        private String complianceType;
        private Instant startDate;
        private Instant endDate;
        private String entityId;
        private List<String> auditScopes;
        private Map<String, Object> parameters;
    }
    
    @Data
    @Builder
    public static class AuditQuery {
        private String entityType;
        private String entityId;
        private String action;
        private String userId;
        private Instant startDate;
        private Instant endDate;
        private int page;
        private int size;
        private String sortBy;
        private String sortDirection;
    }
    
    @Data
    @Builder
    public static class ComplianceReport {
        private String reportId;
        private String complianceType;
        private Instant generatedAt;
        private String status;
        private int totalViolations;
        private int criticalViolations;
        private List<ComplianceViolation> violations;
        private Map<String, Object> summary;
    }
    
    @Data
    @Builder
    public static class ComplianceViolation {
        private String violationId;
        private String severity;
        private String description;
        private String entityId;
        private Instant occurredAt;
        private String remediation;
    }
    
    @Data
    @Builder
    public static class TransactionAuditRequest {
        private String transactionId;
        private String userId;
        private String accountId;
        private String fromAccount;
        private String toAccount;
        private java.math.BigDecimal amount;
        private boolean success;
        private String errorCode;
        private String errorMessage;
        private Instant startDate;
        private Instant endDate;
        private List<String> auditTypes;
        
        public String getFromAccount() {
            return fromAccount;
        }
        
        public String getToAccount() {
            return toAccount;
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public String getErrorCode() {
            return errorCode;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
    }
    
    @Data
    @Builder
    public static class SecurityAuditRequest {
        private String eventType;
        private String eventName;
        private String userId;
        private String ipAddress;
        private String userAgent;
        private String sessionId;
        private String resource;
        private String action;
        private boolean success;
        private String failureReason;
        private Instant timestamp;
        private Map<String, Object> metadata;
        private String correlationId;
        private String riskScore;
        private String riskLevel;
        private String threatType;
        private String description;
        private Map<String, Object> details;
        
        public String getEventName() {
            return eventName != null ? eventName : eventType;
        }
        
        public String getCorrelationId() {
            return correlationId;
        }
        
        public String getRiskScore() {
            return riskScore;
        }
        
        public String getRiskLevel() {
            return riskLevel != null ? riskLevel : "MEDIUM";
        }
        
        public String getThreatType() {
            return threatType;
        }
        
        public String getDescription() {
            return description;
        }
        
        public Map<String, Object> getDetails() {
            return details != null ? details : new java.util.HashMap<>();
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public Map<String, Object> getMetadata() {
            return metadata != null ? metadata : new java.util.HashMap<>();
        }
    }
    
    /**
     * Data operation audit for PII protection and compliance tracking
     */
    @Data
    @Builder
    public static class DataOperationAudit {
        private String eventId;
        private Instant timestamp;
        private String userId;
        private String username;
        private String operation;
        private String dataType;
        private Map<String, Object> metadata;
        private boolean success;
        private String errorMessage;
        private String ipAddress;
        private String sessionId;
        
        // Getters with null-safe defaults
        public String getEventId() {
            return eventId;
        }
        
        public Instant getTimestamp() {
            return timestamp != null ? timestamp : Instant.now();
        }
        
        public String getUserId() {
            return userId;
        }
        
        public String getUsername() {
            return username != null ? username : "system";
        }
        
        public String getOperation() {
            return operation;
        }
        
        public String getDataType() {
            return dataType;
        }
        
        public Map<String, Object> getMetadata() {
            return metadata != null ? metadata : new java.util.HashMap<>();
        }
        
        public boolean isSuccess() {
            return success;
        }
    }
}