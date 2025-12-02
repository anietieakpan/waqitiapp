package com.waqiti.common.audit;

/**
 * Enumeration of audit event severity levels
 */
public enum AuditSeverity {
    /**
     * Critical severity - Immediate attention required
     * Examples: Security breaches, critical system failures, data corruption
     */
    CRITICAL,
    
    /**
     * High severity - Significant issue requiring prompt attention
     * Examples: Failed authentication attempts, fraud detection, compliance violations
     */
    HIGH,
    
    /**
     * Medium severity - Important but not urgent
     * Examples: Configuration changes, permission changes, failed transactions
     */
    MEDIUM,
    
    /**
     * Low severity - Informational events
     * Examples: Successful logins, routine operations, regular data access
     */
    LOW,
    
    /**
     * Info severity - General information
     * Examples: System status, health checks, routine reports
     */
    INFO,
    
    /**
     * Debug severity - Detailed diagnostic information
     * Used primarily in development and troubleshooting
     */
    DEBUG
}