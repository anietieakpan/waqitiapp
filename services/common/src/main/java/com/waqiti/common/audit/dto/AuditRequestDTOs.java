package com.waqiti.common.audit.dto;

import com.waqiti.common.events.model.AuditEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * DTOs for audit logging requests
 */
public class AuditRequestDTOs {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionAuditRequest {
        private String transactionId;
        private String transactionType;
        private BigDecimal amount;
        private String currency;
        private String fromAccount;
        private String toAccount;
        private boolean success;
        private String errorCode;
        private String errorMessage;
        private Double riskScore;
        private Map<String, Object> metadata;
        private String userId;  // Added missing field
        private String type;    // Added missing field  
        private String status;  // Added missing field
        
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
        
        public Double getRiskScore() {
            return riskScore != null ? riskScore : 0.0;
        }
        
        public Map<String, Object> getMetadata() {
            return metadata != null ? metadata : new java.util.HashMap<>();
        }
        
        public String getUserId() {
            return userId;
        }
        
        public String getType() {
            return type != null ? type : transactionType;
        }
        
        public String getStatus() {
            return status != null ? status : (success ? "SUCCESS" : "FAILURE");
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataModificationAuditRequest {
        private String entityType;
        private String entityId;
        private String operation;
        private Map<String, Object> oldValues;
        private Map<String, Object> newValues;
        private boolean sensitiveData;
        private String dataClassification;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecurityAuditRequest {
        private String eventName;
        private String action;
        private boolean success;
        private String riskLevel;
        private String threatType;
        private String description;
        private Map<String, Object> details;
        
        public String getEventName() {
            return eventName;
        }
        
        public String getAction() {
            return action;
        }
        
        public boolean isSuccess() {
            return success;
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
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceAuditRequest {
        private String complianceType;
        private String action;
        private boolean compliant;
        private List<String> flags;
        private Map<String, Object> details;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditQuery {
        private String userId;
        private String eventType;
        private String eventCategory;
        private Instant startTime;
        private Instant endTime;
        private String resourceId;
        private String resourceType;
        private Boolean success;
        private String riskLevel;
        private Integer limit;
        private Integer offset;
        private String sortBy;
        private String sortOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceReport {
        private Instant startTime;
        private Instant endTime;
        private long totalEvents;
        private Map<String, Long> eventsByType;
        private List<AuditEvent> securityEvents;
        private List<AuditEvent> complianceViolations;
        private List<AuditEvent> highRiskEvents;
        private Map<String, Object> dataAccessSummary;
        private Map<String, Long> userActivitySummary;
        private List<String> recommendations;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserActivityReport {
        private String userId;
        private Instant startTime;
        private Instant endTime;
        private long totalEvents;
        private Map<String, Long> eventsByType;
        private List<AuditEvent> loginHistory;
        private List<AuditEvent> dataAccess;
        private List<AuditEvent> transactions;
        private List<AuditEvent> securityEvents;
        private Map<String, Object> riskProfile;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeoLocation {
        private String ipAddress;
        private String country;
        private String countryCode;
        private String region;
        private String city;
        private Double latitude;
        private Double longitude;
        private String timezone;
        private String isp;
        private String provider;  // Added provider field
        private double confidence;  // Added confidence field
    }
}

// Re-export individual classes for easier imports
class TransactionAuditRequest extends AuditRequestDTOs.TransactionAuditRequest {}
class DataModificationAuditRequest extends AuditRequestDTOs.DataModificationAuditRequest {}
class SecurityAuditRequest extends AuditRequestDTOs.SecurityAuditRequest {}
class ComplianceAuditRequest extends AuditRequestDTOs.ComplianceAuditRequest {}
class AuditQuery extends AuditRequestDTOs.AuditQuery {}
class ComplianceReport extends AuditRequestDTOs.ComplianceReport {}
class UserActivityReport extends AuditRequestDTOs.UserActivityReport {}
class GeoLocation extends AuditRequestDTOs.GeoLocation {}