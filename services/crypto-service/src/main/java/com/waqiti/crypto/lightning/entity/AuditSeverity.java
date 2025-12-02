package com.waqiti.crypto.lightning.entity;

/**
 * Audit event severity levels
 */
public enum AuditSeverity {
    /**
     * Low severity - routine operations, informational events
     */
    LOW,
    
    /**
     * Medium severity - business operations, financial transactions
     */
    MEDIUM,
    
    /**
     * High severity - security events, compliance violations, system errors
     */
    HIGH,
    
    /**
     * Critical severity - security breaches, system failures, data breaches
     */
    CRITICAL
}