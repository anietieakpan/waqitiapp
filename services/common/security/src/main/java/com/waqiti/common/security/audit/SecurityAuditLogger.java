package com.waqiti.common.security.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Security Audit Logger
 *
 * Logs security-relevant events for:
 * - Compliance (SOX, PCI DSS, GDPR)
 * - Forensic analysis
 * - Threat detection
 * - Security monitoring
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityAuditLogger {

    public void logSecurityEvent(String eventType, String userId, String resourceId, Map<String, Object> metadata) {
        log.info("SECURITY_AUDIT: event={}, user={}, resource={}, metadata={}, timestamp={}",
            eventType, userId, resourceId, metadata, Instant.now());

        // In production: send to SIEM, Splunk, ELK, or security analytics platform
    }

    public void logUnauthorizedAccess(String userId, String resourceType, String resourceId) {
        logSecurityEvent("UNAUTHORIZED_ACCESS", userId, resourceType + ":" + resourceId,
            Map.of("resourceType", resourceType, "resourceId", resourceId));
    }

    public void logAccessDenied(String userId, String resource, String action) {
        logSecurityEvent("ACCESS_DENIED", userId, resource,
            Map.of("action", action));
    }

    public void logSuccessfulAccess(String userId, String resource) {
        logSecurityEvent("ACCESS_GRANTED", userId, resource, Map.of());
    }

    public void logAuthenticationFailure(String username, String reason) {
        logSecurityEvent("AUTH_FAILURE", username, "authentication",
            Map.of("reason", reason));
    }

    public void logAuthenticationSuccess(String username) {
        logSecurityEvent("AUTH_SUCCESS", username, "authentication", Map.of());
    }

    public void logPrivilegeEscalation(String userId, String fromRole, String toRole) {
        logSecurityEvent("PRIVILEGE_ESCALATION", userId, "role_change",
            Map.of("fromRole", fromRole, "toRole", toRole));
    }
}
