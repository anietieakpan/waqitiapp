package com.waqiti.security.exception;

import org.springframework.security.core.AuthenticationException;

/**
 * Exception thrown when authentication bypass attempts are detected
 * 
 * CRITICAL SECURITY: This exception is thrown to prevent authentication
 * bypass vulnerabilities and unauthorized access attempts.
 * 
 * This exception indicates serious security violations including:
 * - Attempts to access resources without proper authentication
 * - Horizontal privilege escalation (accessing other users' data)
 * - Vertical privilege escalation (gaining elevated permissions)
 * - Session hijacking or fixation attempts
 * - Parameter manipulation to bypass security checks
 * - Missing or invalid authorization context
 * 
 * SECURITY IMPLICATIONS:
 * - All instances should be logged for security monitoring
 * - May indicate active attack attempts
 * - Could signal compromised accounts or sessions
 * - Requires immediate security team attention
 * - Should trigger automated security responses
 * 
 * RESPONSE ACTIONS:
 * - Log security violation with full context
 * - Invalidate suspicious sessions
 * - Alert security monitoring systems
 * - Consider temporary account lockout
 * - Review access patterns for anomalies
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-15
 */
public class AuthenticationBypassException extends AuthenticationException {
    
    private final String violationType;
    private final String userId;
    private final String resourceId;
    private final long timestamp;

    public AuthenticationBypassException(String message) {
        super(message);
        this.violationType = "GENERIC_BYPASS_ATTEMPT";
        this.userId = null;
        this.resourceId = null;
        this.timestamp = System.currentTimeMillis();
    }

    public AuthenticationBypassException(String message, Throwable cause) {
        super(message, cause);
        this.violationType = "GENERIC_BYPASS_ATTEMPT";
        this.userId = null;
        this.resourceId = null;
        this.timestamp = System.currentTimeMillis();
    }

    public AuthenticationBypassException(String message, String violationType, String userId) {
        super(message);
        this.violationType = violationType;
        this.userId = userId;
        this.resourceId = null;
        this.timestamp = System.currentTimeMillis();
    }

    public AuthenticationBypassException(String message, String violationType, String userId, String resourceId) {
        super(message);
        this.violationType = violationType;
        this.userId = userId;
        this.resourceId = resourceId;
        this.timestamp = System.currentTimeMillis();
    }

    public String getViolationType() {
        return violationType;
    }

    public String getUserId() {
        return userId;
    }

    public String getResourceId() {
        return resourceId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return String.format("AuthenticationBypassException{violationType='%s', userId='%s', resourceId='%s', timestamp=%d, message='%s'}", 
            violationType, userId, resourceId, timestamp, getMessage());
    }
}