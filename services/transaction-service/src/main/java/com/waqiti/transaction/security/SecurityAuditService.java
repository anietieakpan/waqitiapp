package com.waqiti.transaction.security;

import com.waqiti.common.audit.AuditEvent;
import com.waqiti.common.audit.AuditEventPublisher;
import com.waqiti.common.audit.AuditLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for security-related audit logging.
 *
 * This service provides comprehensive audit logging for security events,
 * access control decisions, and security violations. All events are logged
 * both locally and published to the central audit service for compliance.
 *
 * Key Features:
 * - Real-time security event logging
 * - Integration with central audit service
 * - Structured event metadata
 * - Compliance-ready audit trails
 * - Non-blocking (fire-and-forget) event publishing
 *
 * @author Waqiti Platform Team
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityAuditService {

    private final AuditEventPublisher auditEventPublisher;

    /**
     * Logs a successful access grant event.
     *
     * @param resourceType Type of resource accessed (e.g., "WALLET_ACCESS", "TRANSACTION_VIEW")
     * @param username User who was granted access
     * @param resourceId ID of the resource accessed
     */
    public void logAccessGranted(String resourceType, String username, String resourceId) {
        log.info("ACCESS_GRANTED: type={}, user={}, resource={}",
                 resourceType, username, resourceId);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("resourceType", resourceType);
        metadata.put("resourceId", resourceId);
        metadata.put("username", username);
        metadata.put("outcome", "GRANTED");
        metadata.put("timestamp", LocalDateTime.now());

        publishAuditEvent(
            "SECURITY_ACCESS_GRANTED",
            AuditLevel.MEDIUM,
            String.format("User %s granted access to %s: %s", username, resourceType, resourceId),
            metadata
        );
    }

    /**
     * Logs an access denial event.
     *
     * @param resourceType Type of resource accessed
     * @param username User who was denied access
     * @param resourceId ID of the resource
     * @param reason Reason for denial
     */
    public void logAccessDenied(String resourceType, String username, String resourceId, String reason) {
        log.warn("ACCESS_DENIED: type={}, user={}, resource={}, reason={}",
                 resourceType, username, resourceId, reason);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("resourceType", resourceType);
        metadata.put("resourceId", resourceId);
        metadata.put("username", username);
        metadata.put("outcome", "DENIED");
        metadata.put("reason", reason);
        metadata.put("timestamp", LocalDateTime.now());

        publishAuditEvent(
            "SECURITY_ACCESS_DENIED",
            AuditLevel.HIGH,
            String.format("User %s denied access to %s: %s (Reason: %s)",
                          username, resourceType, resourceId, reason),
            metadata
        );
    }

    /**
     * Logs a security violation attempt.
     *
     * This is for potential attacks or suspicious behavior.
     *
     * @param violationType Type of violation (e.g., "IDOR_ATTEMPT", "UNAUTHORIZED_ACCESS")
     * @param username User who attempted the violation
     * @param details Additional details about the violation
     */
    public void logSecurityViolation(String violationType, String username, Map<String, Object> details) {
        log.error("SECURITY_VIOLATION: type={}, user={}, details={}",
                  violationType, username, details);

        Map<String, Object> metadata = new HashMap<>(details != null ? details : Map.of());
        metadata.put("violationType", violationType);
        metadata.put("username", username);
        metadata.put("timestamp", LocalDateTime.now());
        metadata.put("severity", "CRITICAL");

        publishAuditEvent(
            "SECURITY_VIOLATION",
            AuditLevel.CRITICAL,
            String.format("Security violation detected: %s by user %s", violationType, username),
            metadata
        );

        // Additional alerting for critical violations
        if ("IDOR_ATTEMPT".equals(violationType) || "SQL_INJECTION_ATTEMPT".equals(violationType)) {
            // Could trigger PagerDuty/Slack alerts here
            log.error("CRITICAL SECURITY ALERT: {} by user {}", violationType, username);
        }
    }

    /**
     * Logs a security error (e.g., service unavailable, validation failure).
     *
     * @param errorType Type of error
     * @param username User context
     * @param resourceId Resource being accessed
     * @param throwable The exception that occurred
     */
    public void logSecurityError(String errorType, String username, String resourceId, Throwable throwable) {
        log.error("SECURITY_ERROR: type={}, user={}, resource={}",
                  errorType, username, resourceId, throwable);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("errorType", errorType);
        metadata.put("username", username);
        metadata.put("resourceId", resourceId);
        metadata.put("exceptionClass", throwable != null ? throwable.getClass().getName() : "UNKNOWN");
        metadata.put("exceptionMessage", throwable != null ? throwable.getMessage() : "UNKNOWN");
        metadata.put("timestamp", LocalDateTime.now());

        publishAuditEvent(
            "SECURITY_ERROR",
            AuditLevel.HIGH,
            String.format("Security error: %s for user %s accessing %s",
                          errorType, username, resourceId),
            metadata
        );
    }

    /**
     * Logs successful authentication.
     *
     * @param username User who authenticated
     * @param authMethod Authentication method used
     * @param ipAddress IP address of the user
     */
    public void logAuthenticationSuccess(String username, String authMethod, String ipAddress) {
        log.info("AUTHENTICATION_SUCCESS: user={}, method={}, ip={}",
                 username, authMethod, ipAddress);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("username", username);
        metadata.put("authMethod", authMethod);
        metadata.put("ipAddress", ipAddress);
        metadata.put("outcome", "SUCCESS");
        metadata.put("timestamp", LocalDateTime.now());

        publishAuditEvent(
            "AUTHENTICATION_SUCCESS",
            AuditLevel.MEDIUM,
            String.format("User %s authenticated successfully via %s from IP %s",
                          username, authMethod, ipAddress),
            metadata
        );
    }

    /**
     * Logs failed authentication attempt.
     *
     * @param username Username that failed authentication
     * @param authMethod Authentication method attempted
     * @param ipAddress IP address of the attempt
     * @param reason Reason for failure
     */
    public void logAuthenticationFailure(String username, String authMethod, String ipAddress, String reason) {
        log.warn("AUTHENTICATION_FAILURE: user={}, method={}, ip={}, reason={}",
                 username, authMethod, ipAddress, reason);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("username", username);
        metadata.put("authMethod", authMethod);
        metadata.put("ipAddress", ipAddress);
        metadata.put("outcome", "FAILURE");
        metadata.put("reason", reason);
        metadata.put("timestamp", LocalDateTime.now());

        publishAuditEvent(
            "AUTHENTICATION_FAILURE",
            AuditLevel.HIGH,
            String.format("Authentication failed for user %s via %s from IP %s (Reason: %s)",
                          username, authMethod, ipAddress, reason),
            metadata
        );
    }

    /**
     * Logs session creation.
     *
     * @param username User whose session was created
     * @param sessionId Session identifier
     * @param ipAddress IP address
     */
    public void logSessionCreated(String username, String sessionId, String ipAddress) {
        log.info("SESSION_CREATED: user={}, sessionId={}, ip={}",
                 username, sessionId, ipAddress);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("username", username);
        metadata.put("sessionId", sessionId);
        metadata.put("ipAddress", ipAddress);
        metadata.put("timestamp", LocalDateTime.now());

        publishAuditEvent(
            "SESSION_CREATED",
            AuditLevel.LOW,
            String.format("Session created for user %s", username),
            metadata
        );
    }

    /**
     * Logs privilege escalation attempt.
     *
     * @param username User attempting escalation
     * @param requestedRole Role being requested
     * @param currentRoles User's current roles
     */
    public void logPrivilegeEscalationAttempt(String username, String requestedRole, String currentRoles) {
        log.error("PRIVILEGE_ESCALATION_ATTEMPT: user={}, requested={}, current={}",
                  username, requestedRole, currentRoles);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("username", username);
        metadata.put("requestedRole", requestedRole);
        metadata.put("currentRoles", currentRoles);
        metadata.put("timestamp", LocalDateTime.now());

        publishAuditEvent(
            "PRIVILEGE_ESCALATION_ATTEMPT",
            AuditLevel.CRITICAL,
            String.format("User %s attempted privilege escalation to role %s", username, requestedRole),
            metadata
        );
    }

    /**
     * Publishes audit event to the central audit service.
     *
     * This is non-blocking to prevent impact on application performance.
     *
     * @param eventType Type of event
     * @param level Audit level (severity)
     * @param message Event message
     * @param metadata Event metadata
     */
    private void publishAuditEvent(String eventType, AuditLevel level, String message, Map<String, Object> metadata) {
        try {
            AuditEvent event = AuditEvent.builder()
                .eventType(eventType)
                .level(level)
                .message(message)
                .metadata(metadata)
                .serviceName("transaction-service")
                .timestamp(LocalDateTime.now())
                .build();

            // Non-blocking publish
            auditEventPublisher.publishAsync(event);

        } catch (Exception e) {
            // Don't let audit failures impact the main application flow
            log.error("Failed to publish audit event: eventType={}", eventType, e);
        }
    }
}
