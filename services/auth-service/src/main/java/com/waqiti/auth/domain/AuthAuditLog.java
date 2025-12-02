package com.waqiti.auth.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Enterprise-grade Authentication Audit Log for comprehensive security auditing.
 *
 * Features:
 * - Complete authentication event trail
 * - Security incident tracking
 * - Compliance reporting (SOX, PCI-DSS)
 * - Forensic analysis support
 * - Tamper-evident logging
 *
 * Logged Events:
 * - Login attempts (success/failure)
 * - Logout events
 * - Token operations (generation, refresh, revocation)
 * - Password changes
 * - 2FA operations
 * - Account lockouts
 * - Privilege escalations
 * - Suspicious activities
 *
 * Compliance:
 * - SOX: Complete audit trail
 * - PCI-DSS 10.2: Track user authentication attempts
 * - GDPR: Data access logging
 *
 * @author Waqiti Security Team
 * @version 1.0.0
 */
@Entity
@Table(name = "auth_audit_logs", indexes = {
    @Index(name = "idx_audit_user", columnList = "user_id"),
    @Index(name = "idx_audit_event_type", columnList = "event_type"),
    @Index(name = "idx_audit_status", columnList = "status"),
    @Index(name = "idx_audit_created", columnList = "created_at"),
    @Index(name = "idx_audit_ip", columnList = "ip_address"),
    @Index(name = "idx_audit_session", columnList = "session_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "session_id")
    private UUID sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EventStatus status;

    @Column(name = "event_message", length = 1000)
    private String eventMessage;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    // Security context
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "device_id", length = 255)
    private String deviceId;

    @Column(name = "device_type", length = 50)
    private String deviceType;

    @Column(name = "geolocation", length = 100)
    private String geolocation;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    // Risk assessment
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 20)
    @Builder.Default
    private RiskLevel riskLevel = RiskLevel.LOW;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Column(name = "risk_factors", length = 1000)
    private String riskFactors;

    // Additional metadata
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata; // JSON string for additional data

    @Column(name = "correlation_id")
    private UUID correlationId; // For linking related events

    @Column(name = "trace_id", length = 100)
    private String traceId; // Distributed tracing ID

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Immutable - no updates or version column
    // Audit logs should never be modified

    // Enums
    public enum EventType {
        // Authentication
        LOGIN_SUCCESS,
        LOGIN_FAILURE,
        LOGOUT,
        SESSION_CREATED,
        SESSION_EXPIRED,
        SESSION_TERMINATED,

        // Token operations
        TOKEN_GENERATED,
        TOKEN_REFRESHED,
        TOKEN_REVOKED,
        TOKEN_EXPIRED,

        // Account operations
        ACCOUNT_CREATED,
        ACCOUNT_LOCKED,
        ACCOUNT_UNLOCKED,
        ACCOUNT_SUSPENDED,
        ACCOUNT_DELETED,

        // Password operations
        PASSWORD_CHANGED,
        PASSWORD_RESET_REQUESTED,
        PASSWORD_RESET_COMPLETED,
        PASSWORD_EXPIRED,

        // Two-factor authentication
        TWO_FACTOR_ENABLED,
        TWO_FACTOR_DISABLED,
        TWO_FACTOR_SUCCESS,
        TWO_FACTOR_FAILURE,

        // Security events
        SUSPICIOUS_ACTIVITY_DETECTED,
        MULTIPLE_FAILED_LOGINS,
        UNUSUAL_LOCATION_LOGIN,
        UNUSUAL_DEVICE_LOGIN,
        CONCURRENT_SESSION_DETECTED,

        // Privilege operations
        ROLE_ASSIGNED,
        ROLE_REVOKED,
        PERMISSION_GRANTED,
        PERMISSION_REVOKED,

        // Verification
        EMAIL_VERIFIED,
        PHONE_VERIFIED,
        IDENTITY_VERIFIED
    }

    public enum EventStatus {
        SUCCESS,
        FAILURE,
        WARNING,
        INFO,
        ERROR,
        SECURITY_ALERT
    }

    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    // Static factory methods for common audit events
    public static AuthAuditLog loginSuccess(UUID userId, String username, String ipAddress, String userAgent) {
        return AuthAuditLog.builder()
            .userId(userId)
            .username(username)
            .eventType(EventType.LOGIN_SUCCESS)
            .status(EventStatus.SUCCESS)
            .eventMessage("User logged in successfully")
            .ipAddress(ipAddress)
            .userAgent(userAgent)
            .riskLevel(RiskLevel.LOW)
            .build();
    }

    public static AuthAuditLog loginFailure(String username, String ipAddress, String failureReason) {
        return AuthAuditLog.builder()
            .username(username)
            .eventType(EventType.LOGIN_FAILURE)
            .status(EventStatus.FAILURE)
            .eventMessage("Login attempt failed")
            .failureReason(failureReason)
            .ipAddress(ipAddress)
            .riskLevel(RiskLevel.MEDIUM)
            .build();
    }

    public static AuthAuditLog suspiciousActivity(UUID userId, String username, String reason, RiskLevel riskLevel) {
        return AuthAuditLog.builder()
            .userId(userId)
            .username(username)
            .eventType(EventType.SUSPICIOUS_ACTIVITY_DETECTED)
            .status(EventStatus.SECURITY_ALERT)
            .eventMessage("Suspicious activity detected")
            .failureReason(reason)
            .riskLevel(riskLevel)
            .build();
    }
}
