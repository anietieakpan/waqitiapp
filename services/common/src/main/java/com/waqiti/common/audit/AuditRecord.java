package com.waqiti.common.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Immutable audit record for compliance logging
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditRecord implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    // Core audit fields
    private String auditId;
    private AuditEventType eventType;
    private AuditSeverity severity;
    private LocalDateTime timestamp;
    
    // User context
    private String userId;
    private String username;
    private String sessionId;
    private String ipAddress;
    private String userAgent;
    
    // Entity information
    private String entityType;
    private String entityId;
    
    // Financial data (if applicable)
    private BigDecimal amount;
    private String currency;
    
    // Additional details
    private Map<String, Object> details;
    
    // Integrity and traceability
    private String integrityHash;
    private String previousHash;
    private String requestId;
    private String serviceName;
    
    // Compliance metadata
    private String complianceType;
    private String complianceResult;
    private LocalDateTime expiresAt; // For GDPR compliance
    
    // Performance tracking
    private Long executionTimeMs;
    private String performanceMetrics;

    // Additional fields for compatibility
    private String id;
    private String resourceId;
    private String resourceType;
    private String action;
    private boolean success;
    private Integer riskScore;
}