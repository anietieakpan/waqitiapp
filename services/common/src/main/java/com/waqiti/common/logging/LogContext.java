package com.waqiti.common.logging;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public class LogContext {
    
    @Data
    @Builder
    public static class PaymentLogContext {
        private String transactionId;
        private String userId;
        private BigDecimal amount;
        private String currency;
        private String paymentMethod;
        private String status;
        private Long processingTimeMs;
        private String errorCode;
        private String errorMessage;
        private Map<String, Object> metadata;
    }
    
    @Data
    @Builder
    public static class FraudLogContext {
        private String checkId;
        private String userId;
        private String transactionId;
        private String checkType;
        private Double riskScore;
        private Boolean fraudDetected;
        private String decision;
        private String reason;
        private Map<String, Object> indicators;
        private String fraudType;
        private String rulesTriggered;
        private String actionTaken;
        private String mlModelVersion;
    }
    
    @Data
    @Builder
    public static class AuthLogContext {
        private String eventType;
        private String userId;
        private String method;
        private String ipAddress;
        private String userAgent;
        private Boolean success;
        private String failureReason;
        private Map<String, Object> metadata;
        private String username;
        private String authMethod;
        private String authResult;
        private String clientIp;
        private Boolean biometricUsed;
        private String sessionId;
        
        public boolean isBiometricUsed() {
            return biometricUsed != null && biometricUsed;
        }
    }
    
    @Data
    @Builder
    public static class ApiLogContext {
        private String method;
        private String path;
        private Integer statusCode;
        private Long responseTimeMs;
        private String clientId;
        private String errorCode;
        private Map<String, Object> requestHeaders;
        private Map<String, Object> responseHeaders;
        private String endpoint;
        private String httpMethod;
        private Integer responseCode;
        private Long requestSize;
        private Long responseSize;
        private String clientIp;
        private String userAgent;
        private String userId;
    }
    
    @Data
    @Builder
    public static class SecurityLogContext {
        private String eventType;
        private String severity;
        private String userId;
        private String resource;
        private String action;
        private Boolean allowed;
        private String denialReason;
        private String threatIndicator;
        private Map<String, Object> context;
        private String clientIp;
        private String description;
        private String actionTaken;
    }
    
    @Data
    @Builder
    public static class AuditLogContext {
        private String entityType;
        private String entityId;
        private String action;
        private String userId;
        private Map<String, Object> previousValues;
        private Map<String, Object> newValues;
        private String reason;
        private Instant timestamp;
        private String resourceType;
        private String resourceId;
        private String userRole;
        private Map<String, Object> oldValues;
        private String result;
    }
    
    @Data
    @Builder
    public static class PerformanceLogContext {
        private String operation;
        private String component;
        private Long executionTimeMs;
        private Boolean success;
        private Integer itemsProcessed;
        private Map<String, Long> breakdownMs;
        private Map<String, Object> metrics;
        private Long durationMs;
        private Double cpuUsage;
        private Long memoryUsage;
        private Integer databaseQueries;
        private Integer externalApiCalls;
        private Integer cacheHits;
        private Integer cacheMisses;
    }
    
    @Data
    @Builder
    public static class BusinessLogContext {
        private String eventType;
        private String businessProcess;
        private String outcome;
        private Map<String, Object> businessMetrics;
        private Map<String, Object> context;
        private String userId;
        private BigDecimal revenueImpact;
        private String customerSegment;
        private String featureUsed;
        private String conversionStep;
        private Map<String, Object> metadata;
    }
    
    @Data
    @Builder
    public static class DatabaseLogContext {
        private String operation;
        private String operationType;
        private String table;
        private String tableName;
        private Long queryTimeMs;
        private Integer rowsAffected;
        private String query;
        private String sqlQuery;
        private Boolean success;
        private String errorMessage;
        private Integer connectionPoolSize;
        private boolean slowQuery;
        
        public String getOperationType() {
            return operationType != null ? operationType : operation;
        }
        
        public String getTableName() {
            return tableName != null ? tableName : table;
        }
        
        public String getSqlQuery() {
            return sqlQuery != null ? sqlQuery : query;
        }
        
        public boolean isSlowQuery() {
            return slowQuery;
        }
    }
}