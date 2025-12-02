package com.waqiti.common.audit.service;

import java.util.Map;
import java.util.UUID;

/**
 * Interface for Audit Service
 * Provides audit logging capabilities for payment and security events
 */
public interface AuditService {
    
    /**
     * Audit a payment action
     * @param action The action being audited
     * @param userId The user performing the action
     * @param transactionId The transaction ID if applicable
     * @param details Additional details about the action
     */
    void auditPaymentAction(String action, String userId, String transactionId, Map<String, Object> details);
    
    /**
     * Audit a security event
     * @param event The security event type
     * @param userId The user involved
     * @param details Event details
     */
    void auditSecurityEvent(String event, String userId, Map<String, Object> details);
    
    /**
     * Audit data access
     * @param resource The resource accessed
     * @param userId The user accessing the resource
     * @param action The action performed
     */
    void auditDataAccess(String resource, String userId, String action);
    
    /**
     * Audit system event
     * @param event The system event
     * @param details Event details
     */
    void auditSystemEvent(String event, Map<String, Object> details);
    
    /**
     * Audit failed authentication attempt
     * @param username The username attempted
     * @param ipAddress The IP address of the attempt
     * @param reason The failure reason
     */
    void auditFailedAuthentication(String username, String ipAddress, String reason);
}