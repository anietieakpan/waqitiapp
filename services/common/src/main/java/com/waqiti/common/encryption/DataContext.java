package com.waqiti.common.encryption;

import lombok.Builder;
import lombok.Data;

/**
 * Context information for encryption/decryption operations
 */
@Data
@Builder
public class DataContext {
    
    private String userId;
    private String tenantId;
    private String requestId;
    private String operation;
    private String clientIp;
    private boolean auditRequired;
    private String complianceLevel;
    
    // Additional audit trail fields
    private String sessionId;
    private String sourceIp;
    private String userAgent;
    private String correlationId;
    private String businessContext;
    private String accessReason;
    
    // Explicit getters for compilation issues
    public String getUserId() { return userId; }
    public String getTenantId() { return tenantId; }
    
    /**
     * Create system context for background operations
     */
    public static DataContext systemContext() {
        return DataContext.builder()
            .userId("SYSTEM")
            .tenantId("DEFAULT")
            .operation("SYSTEM_OPERATION")
            .auditRequired(true)
            .build();
    }
    
    /**
     * Create user context for user operations
     */
    public static DataContext userContext(String userId, String tenantId) {
        return DataContext.builder()
            .userId(userId)
            .tenantId(tenantId)
            .auditRequired(true)
            .build();
    }
    
    /**
     * Create migration context for bulk operations
     */
    public static DataContext migrationContext(String operation) {
        return DataContext.builder()
            .userId("MIGRATION")
            .tenantId("MIGRATION")
            .operation(operation)
            .auditRequired(true)
            .build();
    }
}