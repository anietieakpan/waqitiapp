package com.waqiti.user.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Audit service for MFA security events.
 * Provides comprehensive logging and monitoring for MFA operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MfaAuditService {
    
    public void logMfaAttemptAllowed(String userId, String sourceIp, String attemptId, 
                                    boolean trustedDevice, long processingTime) {
        log.info("MFA_AUDIT: Attempt allowed - User: {}, IP: {}, AttemptId: {}, TrustedDevice: {}, ProcessingTime: {}ms",
                userId, sourceIp, attemptId, trustedDevice, processingTime);
    }
    
    public void logMfaSuccess(String userId, String sourceIp, String attemptId, String deviceFingerprint) {
        log.info("MFA_AUDIT: Success - User: {}, IP: {}, AttemptId: {}, Device: {}",
                userId, sourceIp, attemptId, deviceFingerprint != null ? "Present" : "None");
    }
    
    public void logMfaFailure(Object failure) {
        log.error("MFA_SECURITY: Failure recorded - Details: {}", failure);
    }
    
    public void logLockedAccountAttempt(String userId, String sourceIp, String reason) {
        log.error("MFA_SECURITY: Locked account attempt - User: {}, IP: {}, Reason: {}",
                userId, sourceIp, reason);
    }
    
    public void logBlockedIpAttempt(String sourceIp, String userId, String reason) {
        log.error("MFA_SECURITY: Blocked IP attempt - IP: {}, User: {}, Reason: {}",
                sourceIp, userId, reason);
    }
    
    public void logRateLimitExceeded(String userId, String sourceIp, String limitType) {
        log.warn("MFA_SECURITY: Rate limit exceeded - User: {}, IP: {}, Type: {}",
                userId, sourceIp, limitType);
    }
    
    public void logSuspiciousActivity(String userId, String sourceIp, String reason) {
        log.error("MFA_SECURITY: Suspicious activity - User: {}, IP: {}, Reason: {}",
                userId, sourceIp, reason);
    }
    
    public void logTemporaryLockout(String userId, String sourceIp, long failedCount) {
        log.error("MFA_SECURITY: Temporary lockout - User: {}, IP: {}, FailedAttempts: {}",
                userId, sourceIp, failedCount);
    }
    
    public void logExtendedLockout(String userId, String sourceIp, long failedCount) {
        log.error("MFA_SECURITY: Extended lockout - User: {}, IP: {}, FailedAttempts: {}",
                userId, sourceIp, failedCount);
    }
    
    public void logPermanentLockout(String userId, String sourceIp, long failedCount) {
        log.error("MFA_SECURITY: Permanent lockout - User: {}, IP: {}, FailedAttempts: {}",
                userId, sourceIp, failedCount);
    }
    
    public void logIpLockout(String sourceIp, long failureCount) {
        log.error("MFA_SECURITY: IP lockout - IP: {}, Failures: {}",
                sourceIp, failureCount);
    }
    
    public void logDeviceTrusted(String userId, String deviceFingerprint, String sourceIp) {
        log.info("MFA_AUDIT: Device trusted - User: {}, Device: {}, IP: {}",
                userId, deviceFingerprint, sourceIp);
    }
    
    public void logSecurityCheckError(String userId, String sourceIp, String error) {
        log.error("MFA_ERROR: Security check failed - User: {}, IP: {}, Error: {}",
                userId, sourceIp, error);
    }
}