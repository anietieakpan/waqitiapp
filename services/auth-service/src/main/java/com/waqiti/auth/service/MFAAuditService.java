package com.waqiti.auth.service;

import com.waqiti.auth.domain.MFASecret;
import com.waqiti.common.audit.AuditLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MFAAuditService {

    private final AuditLogger auditLogger;

    public void logMFASetup(UUID userId, MFASecret.MFAMethod method) {
        Map<String, Object> details = new HashMap<>();
        details.put("userId", userId);
        details.put("mfaMethod", method);
        details.put("action", "MFA_SETUP_INITIATED");
        auditLogger.logSecurityEvent("MFA_SETUP", userId.toString(), details);
        log.info("MFA setup initiated for user: {} with method: {}", userId, method);
    }

    public void logMFASetupFailure(UUID userId, String reason) {
        Map<String, Object> details = new HashMap<>();
        details.put("userId", userId);
        details.put("reason", reason);
        details.put("action", "MFA_SETUP_FAILED");
        auditLogger.logSecurityEvent("MFA_SETUP_FAILURE", userId.toString(), details);
        log.warn("MFA setup failed for user: {} - Reason: {}", userId, reason);
    }

    public void logMFAEnabled(UUID userId, MFASecret.MFAMethod method) {
        Map<String, Object> details = new HashMap<>();
        details.put("userId", userId);
        details.put("mfaMethod", method);
        details.put("action", "MFA_ENABLED");
        auditLogger.logSecurityEvent("MFA_ENABLED", userId.toString(), details);
        log.info("MFA enabled for user: {} with method: {}", userId, method);
    }

    public void logMFASuccess(UUID userId, MFASecret.MFAMethod method) {
        Map<String, Object> details = new HashMap<>();
        details.put("userId", userId);
        details.put("mfaMethod", method);
        details.put("action", "MFA_VERIFICATION_SUCCESS");
        auditLogger.logSecurityEvent("MFA_SUCCESS", userId.toString(), details);
    }

    public void logMFAFailure(UUID userId, MFASecret.MFAMethod method, String reason) {
        Map<String, Object> details = new HashMap<>();
        details.put("userId", userId);
        details.put("mfaMethod", method);
        details.put("reason", reason);
        details.put("action", "MFA_VERIFICATION_FAILED");
        auditLogger.logSecurityEvent("MFA_FAILURE", userId.toString(), details);
        log.warn("MFA verification failed for user: {} - Reason: {}", userId, reason);
    }

    public void logMFALockout(UUID userId) {
        Map<String, Object> details = new HashMap<>();
        details.put("userId", userId);
        details.put("action", "MFA_ACCOUNT_LOCKED");
        auditLogger.logSecurityEvent("MFA_LOCKOUT", userId.toString(), details);
        log.error("MFA account locked for user: {} due to excessive failed attempts", userId);
    }

    public void logMFAExpired(UUID userId) {
        Map<String, Object> details = new HashMap<>();
        details.put("userId", userId);
        details.put("action", "MFA_SECRET_EXPIRED");
        auditLogger.logSecurityEvent("MFA_EXPIRED", userId.toString(), details);
        log.warn("MFA secret expired for user: {}", userId);
    }

    public void logMFAError(UUID userId, String errorMessage) {
        Map<String, Object> details = new HashMap<>();
        details.put("userId", userId);
        details.put("error", errorMessage);
        details.put("action", "MFA_ERROR");
        auditLogger.logSecurityEvent("MFA_ERROR", userId.toString(), details);
        log.error("MFA error for user: {} - Error: {}", userId, errorMessage);
    }

    public void logBackupCodesLow(UUID userId, Integer remaining) {
        Map<String, Object> details = new HashMap<>();
        details.put("userId", userId);
        details.put("backupCodesRemaining", remaining);
        details.put("action", "BACKUP_CODES_LOW");
        auditLogger.logSecurityEvent("BACKUP_CODES_LOW", userId.toString(), details);
        log.warn("User {} has only {} backup codes remaining", userId, remaining);
    }

    public void logMFARotation(UUID userId) {
        Map<String, Object> details = new HashMap<>();
        details.put("userId", userId);
        details.put("action", "MFA_SECRET_ROTATED");
        auditLogger.logSecurityEvent("MFA_ROTATION", userId.toString(), details);
        log.info("MFA secret rotated for user: {}", userId);
    }

    public void logMFADisabled(UUID userId) {
        Map<String, Object> details = new HashMap<>();
        details.put("userId", userId);
        details.put("action", "MFA_DISABLED");
        auditLogger.logSecurityEvent("MFA_DISABLED", userId.toString(), details);
        log.warn("MFA disabled for user: {}", userId);
    }
}
