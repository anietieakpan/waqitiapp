package com.waqiti.common.audit;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Audit log for data access operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataAccessAuditLog {
    
    private UUID id;
    private String userId;
    private String dataType;
    private String tableName;
    private String operation; // SELECT, INSERT, UPDATE, DELETE
    private String recordId;
    private String fieldName;
    private String oldValue;
    private String newValue;
    private String accessReason;
    private String sqlQuery;
    private long queryExecutionTimeMs;
    private int recordsAffected;
    private String ipAddress;
    private String sessionId;
    private String applicationName;
    private LocalDateTime timestamp;
    private Map<String, Object> metadata;
    
    /**
     * Data access operations
     */
    public enum DataOperation {
        SELECT,
        INSERT,
        UPDATE,
        DELETE,
        BULK_UPDATE,
        BULK_DELETE
    }
    
    /**
     * Create data access audit log
     */
    public static DataAccessAuditLog create(String userId, String tableName, 
                                          DataOperation operation, String recordId) {
        return DataAccessAuditLog.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tableName(tableName)
                .operation(operation.name())
                .recordId(recordId)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * Add query details
     */
    public DataAccessAuditLog withQuery(String sqlQuery, long executionTimeMs, int recordsAffected) {
        this.sqlQuery = sqlQuery;
        this.queryExecutionTimeMs = executionTimeMs;
        this.recordsAffected = recordsAffected;
        return this;
    }
    
    /**
     * Add field change details
     */
    public DataAccessAuditLog withFieldChange(String fieldName, String oldValue, String newValue) {
        this.fieldName = fieldName;
        this.oldValue = oldValue;
        this.newValue = newValue;
        return this;
    }
    
    /**
     * Add session details
     */
    public DataAccessAuditLog withSession(String sessionId, String ipAddress, String applicationName) {
        this.sessionId = sessionId;
        this.ipAddress = ipAddress;
        this.applicationName = applicationName;
        return this;
    }
    
    /**
     * Check if data access involves sensitive information
     */
    public boolean isSensitiveData() {
        return "users".equals(tableName) || 
               "payment_cards".equals(tableName) || 
               "transactions".equals(tableName) ||
               "user_profiles".equals(tableName) ||
               (fieldName != null && (fieldName.contains("password") || 
                                    fieldName.contains("ssn") || 
                                    fieldName.contains("card_number")));
    }
    
    /**
     * Get user who accessed the data
     */
    public String getAccessedBy() {
        return userId;
    }
    
    /**
     * Get purpose of data access
     */
    public String getPurpose() {
        return accessReason;
    }
    
    /**
     * Get entity type (derived from table name)
     */
    public String getEntityType() {
        return tableName != null ? tableName.toUpperCase() : "UNKNOWN";
    }
    
    /**
     * Get entity ID (alias for recordId)
     */
    public String getEntityId() {
        return recordId;
    }
    
    /**
     * Get fields accessed (returns field name or generic description)
     */
    public String getFieldsAccessed() {
        return fieldName != null ? fieldName : "ALL_FIELDS";
    }
    
    /**
     * Get reason for data access
     */
    public String getReason() {
        return accessReason != null ? accessReason : "BUSINESS_OPERATION";
    }
    
    /**
     * Get query execution time in milliseconds
     */
    public Long getQueryExecutionTimeMs() {
        return queryExecutionTimeMs;
    }
    
    /**
     * Get number of records affected
     */
    public Integer getRecordsAffected() {
        return recordsAffected;
    }
}