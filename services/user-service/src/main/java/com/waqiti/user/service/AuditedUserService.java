package com.waqiti.user.service;

import com.waqiti.common.audit.annotation.AuditLogged;
import com.waqiti.common.audit.domain.AuditLog.EventCategory;
import com.waqiti.common.audit.domain.AuditLog.Severity;
import com.waqiti.user.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Audited User Service with comprehensive audit logging
 * 
 * This service wraps user management and authentication operations with 
 * comprehensive audit logging for compliance with SOX, PCI DSS, GDPR, and SOC 2 requirements.
 * 
 * AUDIT COVERAGE:
 * - User authentication and authorization
 * - Account creation and management
 * - Password changes and resets
 * - Multi-factor authentication
 * - Role and permission changes
 * - Profile updates and data access
 * - Account lockouts and security events
 * - Privacy and consent management
 * 
 * COMPLIANCE MAPPING:
 * - PCI DSS: User authentication and access control
 * - SOX: User authorization and privilege management
 * - GDPR: Personal data processing and consent
 * - SOC 2: Identity and access management
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-15
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditedUserService {
    
    private final UserService userService;
    
    /**
     * User login with comprehensive audit logging
     */
    @AuditLogged(
        eventType = "USER_LOGIN_ATTEMPT",
        category = EventCategory.SECURITY,
        severity = Severity.MEDIUM,
        description = "User login attempt for username: #{username}",
        entityType = "Authentication",
        entityIdExpression = "#username",
        pciRelevant = true,
        soc2Relevant = true,
        riskScore = 30,
        metadata = {
            "username: #username",
            "loginMethod: #loginMethod",
            "deviceInfo: #deviceInfo",
            "ipAddress: #ipAddress",
            "loginResult: #result.successful",
            "failureReason: #result.failureReason"
        },
        captureParameters = true,
        captureReturnValue = true,
        excludeFields = {"password", "pin"},
        auditSuccessOnly = false,
        sendToSiem = true
    )
    public LoginResponse authenticateUser(String username, String password, String loginMethod, 
                                        String deviceInfo, String ipAddress) {
        log.info("AUDIT: User login attempt - Username: {} Method: {} IP: {}", username, loginMethod, ipAddress);
        
        return userService.authenticateUser(username, password, loginMethod, deviceInfo, ipAddress);
    }
    
    /**
     * User logout with audit logging
     */
    @AuditLogged(
        eventType = "USER_LOGOUT",
        category = EventCategory.SECURITY,
        severity = Severity.INFO,
        description = "User logout for user #{userId}",
        entityType = "Authentication",
        entityIdExpression = "#userId",
        soc2Relevant = true,
        metadata = {
            "userId: #userId",
            "sessionId: #sessionId",
            "logoutType: #logoutType",
            "sessionDuration: #sessionDuration"
        },
        captureParameters = true,
        sendToSiem = false
    )
    public void logoutUser(UUID userId, String sessionId, String logoutType, long sessionDuration) {
        log.info("AUDIT: User logout - User: {} Session: {} Type: {}", userId, sessionId, logoutType);
        
        userService.logoutUser(userId, sessionId, logoutType, sessionDuration);
    }
    
    /**
     * Failed login attempt with audit logging
     */
    @AuditLogged(
        eventType = "LOGIN_FAILED",
        category = EventCategory.SECURITY,
        severity = Severity.HIGH,
        description = "Failed login attempt for username: #{username} - reason: #{failureReason}",
        entityType = "AuthenticationFailure",
        entityIdExpression = "#username",
        pciRelevant = true,
        soc2Relevant = true,
        requiresNotification = true,
        riskScore = 60,
        metadata = {
            "username: #username",
            "failureReason: #failureReason",
            "attemptCount: #attemptCount",
            "ipAddress: #ipAddress",
            "userAgent: #userAgent",
            "lockoutTriggered: #lockoutTriggered"
        },
        captureParameters = true,
        excludeFields = {"password"},
        sendToSiem = true
    )
    public void recordFailedLogin(String username, String failureReason, int attemptCount, 
                                 String ipAddress, String userAgent, boolean lockoutTriggered) {
        log.warn("AUDIT: Failed login - Username: {} Reason: {} Attempts: {} IP: {}", 
                username, failureReason, attemptCount, ipAddress);
        
        userService.recordFailedLogin(username, failureReason, attemptCount, ipAddress, userAgent, lockoutTriggered);
    }
    
    /**
     * Create user account with audit logging
     */
    @AuditLogged(
        eventType = "USER_ACCOUNT_CREATED",
        category = EventCategory.ADMIN,
        severity = Severity.MEDIUM,
        description = "User account created for #{request.email}",
        entityType = "User",
        entityIdExpression = "#result.userId",
        gdprRelevant = true,
        soc2Relevant = true,
        metadata = {
            "email: #request.email",
            "firstName: #request.firstName",
            "lastName: #request.lastName",
            "registrationMethod: #request.registrationMethod",
            "kycRequired: #request.kycRequired",
            "userId: #result.userId"
        },
        captureParameters = true,
        captureReturnValue = true,
        excludeFields = {"password", "ssn", "identificationNumber"},
        sendToSiem = false
    )
    @Transactional
    public UserRegistrationResponse createUserAccount(UserRegistrationRequest request) {
        log.info("AUDIT: Creating user account for email: {}", request.getEmail());
        
        return userService.createUserAccount(request);
    }
    
    /**
     * Update user profile with audit logging
     */
    @AuditLogged(
        eventType = "USER_PROFILE_UPDATED",
        category = EventCategory.DATA_ACCESS,
        severity = Severity.MEDIUM,
        description = "User profile updated for user #{userId}",
        entityType = "UserProfile",
        entityIdExpression = "#userId",
        gdprRelevant = true,
        metadata = {
            "userId: #userId",
            "updatedFields: #updatedFields",
            "adminUpdate: #adminUpdate",
            "consentUpdated: #consentUpdated"
        },
        captureParameters = true,
        excludeFields = {"ssn", "identificationNumber", "personalData"},
        sendToSiem = false
    )
    @Transactional
    public UserProfileResponse updateUserProfile(UUID userId, UserProfileUpdateRequest request, 
                                                String[] updatedFields, boolean adminUpdate, boolean consentUpdated) {
        log.info("AUDIT: Updating user profile for user: {} fields: {}", userId, String.join(",", updatedFields));
        
        return userService.updateUserProfile(userId, request, updatedFields, adminUpdate, consentUpdated);
    }
    
    /**
     * Change password with audit logging
     */
    @AuditLogged(
        eventType = "PASSWORD_CHANGED",
        category = EventCategory.SECURITY,
        severity = Severity.MEDIUM,
        description = "Password changed for user #{userId}",
        entityType = "PasswordChange",
        entityIdExpression = "#userId",
        pciRelevant = true,
        soc2Relevant = true,
        metadata = {
            "userId: #userId",
            "changeMethod: #changeMethod",
            "adminReset: #adminReset",
            "strengthScore: #strengthScore",
            "previousChangeDate: #previousChangeDate"
        },
        captureParameters = true,
        excludeFields = {"oldPassword", "newPassword"},
        sendToSiem = true
    )
    @Transactional
    public void changePassword(UUID userId, String oldPassword, String newPassword, String changeMethod, 
                              boolean adminReset, int strengthScore, String previousChangeDate) {
        log.info("AUDIT: Password changed for user: {} method: {} admin: {}", userId, changeMethod, adminReset);
        
        userService.changePassword(userId, oldPassword, newPassword, changeMethod, adminReset, strengthScore, previousChangeDate);
    }
    
    /**
     * Reset password with audit logging
     */
    @AuditLogged(
        eventType = "PASSWORD_RESET_REQUESTED",
        category = EventCategory.SECURITY,
        severity = Severity.HIGH,
        description = "Password reset requested for #{identifier}",
        entityType = "PasswordReset",
        entityIdExpression = "#identifier",
        pciRelevant = true,
        soc2Relevant = true,
        requiresNotification = true,
        riskScore = 50,
        metadata = {
            "identifier: #identifier",
            "resetMethod: #resetMethod",
            "verificationMethod: #verificationMethod",
            "ipAddress: #ipAddress",
            "tokenGenerated: #result.tokenGenerated"
        },
        captureParameters = true,
        captureReturnValue = true,
        sendToSiem = true
    )
    public PasswordResetResponse requestPasswordReset(String identifier, String resetMethod, 
                                                     String verificationMethod, String ipAddress) {
        log.warn("AUDIT: Password reset requested for: {} method: {} IP: {}", identifier, resetMethod, ipAddress);
        
        return userService.requestPasswordReset(identifier, resetMethod, verificationMethod, ipAddress);
    }
    
    /**
     * Enable MFA with audit logging
     */
    @AuditLogged(
        eventType = "MFA_ENABLED",
        category = EventCategory.SECURITY,
        severity = Severity.MEDIUM,
        description = "Multi-factor authentication enabled for user #{userId}",
        entityType = "MFAConfiguration",
        entityIdExpression = "#userId",
        pciRelevant = true,
        soc2Relevant = true,
        metadata = {
            "userId: #userId",
            "mfaMethod: #mfaMethod",
            "backupCodes: #backupCodesGenerated",
            "enrollmentMethod: #enrollmentMethod"
        },
        captureParameters = true,
        captureReturnValue = true,
        excludeFields = {"secretKey", "backupCodes"},
        sendToSiem = true
    )
    @Transactional
    public MFAEnrollmentResponse enableMFA(UUID userId, String mfaMethod, String enrollmentMethod, 
                                          boolean backupCodesGenerated) {
        log.info("AUDIT: Enabling MFA for user: {} method: {}", userId, mfaMethod);
        
        return userService.enableMFA(userId, mfaMethod, enrollmentMethod, backupCodesGenerated);
    }
    
    /**
     * Verify MFA with audit logging
     */
    @AuditLogged(
        eventType = "MFA_VERIFICATION",
        category = EventCategory.SECURITY,
        severity = Severity.MEDIUM,
        description = "MFA verification for user #{userId} - result: #{result.successful}",
        entityType = "MFAVerification",
        entityIdExpression = "#userId",
        pciRelevant = true,
        soc2Relevant = true,
        riskScore = 20,
        metadata = {
            "userId: #userId",
            "mfaMethod: #mfaMethod",
            "verificationResult: #result.successful",
            "failureReason: #result.failureReason",
            "attemptCount: #attemptCount"
        },
        captureParameters = true,
        captureReturnValue = true,
        excludeFields = {"verificationCode", "token"},
        auditSuccessOnly = false,
        sendToSiem = true
    )
    public MFAVerificationResponse verifyMFA(UUID userId, String verificationCode, String mfaMethod, int attemptCount) {
        log.info("AUDIT: MFA verification for user: {} method: {} attempt: {}", userId, mfaMethod, attemptCount);
        
        return userService.verifyMFA(userId, verificationCode, mfaMethod, attemptCount);
    }
    
    /**
     * Assign role with audit logging
     */
    @AuditLogged(
        eventType = "ROLE_ASSIGNED",
        category = EventCategory.ADMIN,
        severity = Severity.HIGH,
        description = "Role #{role} assigned to user #{userId}",
        entityType = "RoleAssignment",
        entityIdExpression = "#userId",
        soxRelevant = true,
        soc2Relevant = true,
        requiresNotification = true,
        metadata = {
            "userId: #userId",
            "role: #role",
            "assignedBy: #assignedBy",
            "reason: #reason",
            "effectiveDate: #effectiveDate",
            "expirationDate: #expirationDate"
        },
        captureParameters = true,
        sendToSiem = true
    )
    @Transactional
    public void assignRole(UUID userId, String role, UUID assignedBy, String reason, 
                          String effectiveDate, String expirationDate) {
        log.info("AUDIT: Assigning role: {} to user: {} by: {} reason: {}", role, userId, assignedBy, reason);
        
        userService.assignRole(userId, role, assignedBy, reason, effectiveDate, expirationDate);
    }
    
    /**
     * Revoke role with audit logging
     */
    @AuditLogged(
        eventType = "ROLE_REVOKED",
        category = EventCategory.ADMIN,
        severity = Severity.HIGH,
        description = "Role #{role} revoked from user #{userId}",
        entityType = "RoleRevocation",
        entityIdExpression = "#userId",
        soxRelevant = true,
        soc2Relevant = true,
        requiresNotification = true,
        metadata = {
            "userId: #userId",
            "role: #role",
            "revokedBy: #revokedBy",
            "reason: #reason",
            "revocationDate: #revocationDate"
        },
        captureParameters = true,
        sendToSiem = true
    )
    @Transactional
    public void revokeRole(UUID userId, String role, UUID revokedBy, String reason, String revocationDate) {
        log.info("AUDIT: Revoking role: {} from user: {} by: {} reason: {}", role, userId, revokedBy, reason);
        
        userService.revokeRole(userId, role, revokedBy, reason, revocationDate);
    }
    
    /**
     * Lock user account with audit logging
     */
    @AuditLogged(
        eventType = "ACCOUNT_LOCKED",
        category = EventCategory.SECURITY,
        severity = Severity.CRITICAL,
        description = "User account locked for user #{userId} - reason: #{reason}",
        entityType = "AccountLock",
        entityIdExpression = "#userId",
        pciRelevant = true,
        soc2Relevant = true,
        requiresNotification = true,
        investigationRequired = true,
        riskScore = 80,
        metadata = {
            "userId: #userId",
            "reason: #reason",
            "lockType: #lockType",
            "lockedBy: #lockedBy",
            "automaticLock: #automaticLock",
            "unlockDate: #unlockDate"
        },
        captureParameters = true,
        sendToSiem = true
    )
    @Transactional
    public void lockUserAccount(UUID userId, String reason, String lockType, UUID lockedBy, 
                               boolean automaticLock, String unlockDate) {
        log.warn("AUDIT: Locking user account: {} reason: {} type: {} by: {}", userId, reason, lockType, lockedBy);
        
        userService.lockUserAccount(userId, reason, lockType, lockedBy, automaticLock, unlockDate);
    }
    
    /**
     * Unlock user account with audit logging
     */
    @AuditLogged(
        eventType = "ACCOUNT_UNLOCKED",
        category = EventCategory.SECURITY,
        severity = Severity.HIGH,
        description = "User account unlocked for user #{userId}",
        entityType = "AccountUnlock",
        entityIdExpression = "#userId",
        pciRelevant = true,
        soc2Relevant = true,
        requiresNotification = true,
        metadata = {
            "userId: #userId",
            "unlockedBy: #unlockedBy",
            "reason: #reason",
            "approvalRequired: #approvalRequired",
            "verificationPerformed: #verificationPerformed"
        },
        captureParameters = true,
        sendToSiem = true
    )
    @Transactional
    public void unlockUserAccount(UUID userId, UUID unlockedBy, String reason, 
                                 boolean approvalRequired, boolean verificationPerformed) {
        log.info("AUDIT: Unlocking user account: {} by: {} reason: {}", userId, unlockedBy, reason);
        
        userService.unlockUserAccount(userId, unlockedBy, reason, approvalRequired, verificationPerformed);
    }
    
    /**
     * Access PII data with audit logging
     */
    @AuditLogged(
        eventType = "PII_DATA_ACCESSED",
        category = EventCategory.DATA_ACCESS,
        severity = Severity.HIGH,
        description = "PII data accessed for user #{userId}",
        entityType = "PIIAccess",
        entityIdExpression = "#userId",
        gdprRelevant = true,
        requiresNotification = true,
        metadata = {
            "userId: #userId",
            "dataFields: #dataFields",
            "accessReason: #accessReason",
            "legalBasis: #legalBasis",
            "consentVerified: #consentVerified"
        },
        captureParameters = true,
        excludeFields = {"ssn", "identificationNumber", "personalData"},
        sendToSiem = true
    )
    public PIIDataResponse accessPIIData(UUID userId, String[] dataFields, String accessReason, 
                                        String legalBasis, boolean consentVerified) {
        log.info("AUDIT: Accessing PII data for user: {} fields: {} reason: {}", 
                userId, String.join(",", dataFields), accessReason);
        
        return userService.accessPIIData(userId, dataFields, accessReason, legalBasis, consentVerified);
    }
    
    /**
     * Update consent preferences with audit logging
     */
    @AuditLogged(
        eventType = "CONSENT_UPDATED",
        category = EventCategory.DATA_ACCESS,
        severity = Severity.MEDIUM,
        description = "Consent preferences updated for user #{userId}",
        entityType = "ConsentUpdate",
        entityIdExpression = "#userId",
        gdprRelevant = true,
        metadata = {
            "userId: #userId",
            "consentType: #consentType",
            "consentGiven: #consentGiven",
            "previousConsent: #previousConsent",
            "legalBasis: #legalBasis",
            "consentDate: #consentDate"
        },
        captureParameters = true,
        sendToSiem = false
    )
    @Transactional
    public void updateConsentPreferences(UUID userId, String consentType, boolean consentGiven, 
                                        boolean previousConsent, String legalBasis, String consentDate) {
        log.info("AUDIT: Updating consent for user: {} type: {} given: {}", userId, consentType, consentGiven);
        
        userService.updateConsentPreferences(userId, consentType, consentGiven, previousConsent, legalBasis, consentDate);
    }
    
    /**
     * Delete user data with audit logging (GDPR Right to be Forgotten)
     */
    @AuditLogged(
        eventType = "USER_DATA_DELETED",
        category = EventCategory.DATA_ACCESS,
        severity = Severity.CRITICAL,
        description = "User data deleted for user #{userId} - GDPR Right to be Forgotten",
        entityType = "DataDeletion",
        entityIdExpression = "#userId",
        gdprRelevant = true,
        requiresNotification = true,
        metadata = {
            "userId: #userId",
            "deletionReason: #deletionReason",
            "dataCategories: #dataCategories",
            "retentionOverride: #retentionOverride",
            "approvedBy: #approvedBy",
            "deletionDate: #deletionDate"
        },
        captureParameters = true,
        sendToSiem = true
    )
    @Transactional
    public UserDataDeletionResponse deleteUserData(UUID userId, String deletionReason, String[] dataCategories, 
                                                   boolean retentionOverride, UUID approvedBy, String deletionDate) {
        log.warn("AUDIT: Deleting user data for user: {} reason: {} categories: {}", 
                userId, deletionReason, String.join(",", dataCategories));
        
        return userService.deleteUserData(userId, deletionReason, dataCategories, retentionOverride, approvedBy, deletionDate);
    }
    
    /**
     * Suspicious user activity detection
     */
    @AuditLogged(
        eventType = "SUSPICIOUS_USER_ACTIVITY",
        category = EventCategory.FRAUD,
        severity = Severity.CRITICAL,
        description = "Suspicious user activity detected for user #{userId} - type: #{activityType}",
        entityType = "SuspiciousUserActivity",
        entityIdExpression = "#userId",
        pciRelevant = true,
        soc2Relevant = true,
        requiresNotification = true,
        investigationRequired = true,
        riskScore = 90,
        metadata = {
            "userId: #userId",
            "activityType: #activityType",
            "confidence: #confidence",
            "indicators: #indicators",
            "immediateAction: #immediateAction",
            "riskLevel: #riskLevel"
        },
        captureParameters = true,
        sendToSiem = true
    )
    public void flagSuspiciousUserActivity(UUID userId, String activityType, double confidence, 
                                          String indicators, String immediateAction, String riskLevel) {
        log.error("AUDIT: SUSPICIOUS USER ACTIVITY - User: {} Type: {} Confidence: {} Risk: {}", 
                 userId, activityType, confidence, riskLevel);
        
        userService.flagSuspiciousUserActivity(userId, activityType, confidence, indicators, immediateAction, riskLevel);
    }
}