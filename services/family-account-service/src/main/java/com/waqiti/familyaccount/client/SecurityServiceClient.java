package com.waqiti.familyaccount.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * Security Service Feign Client
 *
 * Feign client for interacting with the security-service microservice.
 * Provides security operations including:
 * - Security event logging
 * - Device validation
 * - Suspicious activity detection
 * - Fraud prevention
 * - Audit trail management
 *
 * Circuit Breaker Configuration:
 * - Name: security-service
 * - Failure Rate Threshold: 50%
 * - Sliding Window Size: 10 requests
 * - Wait Duration in Open State: 10 seconds
 *
 * Retry Configuration:
 * - Max Attempts: 2
 * - Wait Duration: 500ms
 *
 * Timeout Configuration:
 * - Connect Timeout: 3 seconds
 * - Read Timeout: 3 seconds
 *
 * @author Waqiti Family Account Team
 * @version 1.0.0
 * @since 2025-11-19
 */
@FeignClient(
    name = "security-service",
    url = "${feign.client.config.security-service.url}",
    configuration = SecurityServiceClientConfig.class
)
public interface SecurityServiceClient {

    /**
     * Log security event
     *
     * Logs a security-related event for audit and compliance purposes.
     * Events are stored in a tamper-proof audit log with full traceability.
     *
     * Event types include:
     * - FAMILY_ACCOUNT_CREATED
     * - FAMILY_MEMBER_ADDED
     * - FAMILY_MEMBER_REMOVED
     * - FAMILY_MEMBER_SUSPENDED
     * - TRANSACTION_AUTHORIZED
     * - TRANSACTION_DECLINED
     * - SPENDING_LIMIT_EXCEEDED
     * - SPENDING_RULE_VIOLATION
     * - PARENTAL_CONTROL_MODIFIED
     * - WALLET_FROZEN
     * - SUSPICIOUS_ACTIVITY_DETECTED
     *
     * @param eventType The type of security event
     * @param userId The user ID associated with the event
     * @param familyId The family account ID (if applicable)
     * @param details Additional event details as key-value pairs
     * @throws feign.FeignException if the service call fails (logged, not critical)
     */
    @PostMapping("/api/v1/security/events/log")
    void logSecurityEvent(
        @RequestParam("eventType") String eventType,
        @RequestParam("userId") String userId,
        @RequestParam(value = "familyId", required = false) String familyId,
        @RequestBody Map<String, Object> details
    );

    /**
     * Validate device for transaction
     *
     * Validates that a device is registered and trusted for the user.
     * Used in transaction authorization to ensure the transaction originates
     * from a known device.
     *
     * Validation checks:
     * - Device is registered to the user
     * - Device is not reported as stolen/lost
     * - Device has not been flagged for suspicious activity
     * - Device certificate/fingerprint is valid
     * - Device geolocation is consistent with user's patterns (if enabled)
     *
     * @param userId The user ID owning the device
     * @param deviceId The unique device identifier
     * @return true if device is valid and trusted, false otherwise
     * @throws feign.FeignException if the service call fails
     */
    @GetMapping("/api/v1/security/devices/validate")
    Boolean validateDevice(
        @RequestParam("userId") String userId,
        @RequestParam("deviceId") String deviceId
    );

    /**
     * Check for suspicious activity
     *
     * Analyzes user activity patterns to detect potentially fraudulent behavior.
     * Uses machine learning models and rule-based detection.
     *
     * Suspicious indicators include:
     * - Unusual transaction patterns (amount, frequency, timing)
     * - Transactions from unusual locations
     * - Multiple declined transactions
     * - Rapid spending spikes
     * - Transactions immediately after adding new member
     * - Access from multiple devices in short timeframe
     * - Velocity checks (too many transactions too quickly)
     *
     * @param userId The user ID to analyze
     * @param activityType The type of activity (TRANSACTION, LOGIN, ACCOUNT_CHANGE, etc.)
     * @param context Additional context for analysis (location, device, amount, etc.)
     * @return true if activity appears suspicious, false if normal
     * @throws feign.FeignException if the service call fails
     */
    @PostMapping("/api/v1/security/fraud/check-suspicious-activity")
    Boolean isSuspiciousActivity(
        @RequestParam("userId") String userId,
        @RequestParam("activityType") String activityType,
        @RequestBody Map<String, Object> context
    );

    /**
     * Register new device for user
     *
     * Registers a new device for a user after successful authentication.
     * Used when a family member sets up the app on a new device.
     *
     * @param userId The user ID
     * @param deviceId The unique device identifier
     * @param deviceInfo Device information (model, OS version, etc.)
     * @return true if device registered successfully
     * @throws feign.FeignException if the service call fails
     */
    @PostMapping("/api/v1/security/devices/register")
    Boolean registerDevice(
        @RequestParam("userId") String userId,
        @RequestParam("deviceId") String deviceId,
        @RequestBody Map<String, Object> deviceInfo
    );

    /**
     * Report device as lost or stolen
     *
     * Marks a device as lost/stolen and revokes its access.
     * All active sessions on the device are terminated.
     *
     * @param userId The user ID
     * @param deviceId The device identifier to report
     * @param reason Reason for reporting (LOST, STOLEN, COMPROMISED)
     * @return true if device was successfully reported
     * @throws feign.FeignException if the service call fails
     */
    @PostMapping("/api/v1/security/devices/report-lost")
    Boolean reportDeviceAsLost(
        @RequestParam("userId") String userId,
        @RequestParam("deviceId") String deviceId,
        @RequestParam("reason") String reason
    );

    /**
     * Get user's registered devices
     *
     * Retrieves list of all devices registered to a user.
     * Used in account settings for device management.
     *
     * @param userId The user ID
     * @return List of registered devices with details
     * @throws feign.FeignException if the service call fails
     */
    @GetMapping("/api/v1/security/devices/list")
    Map<String, Object> getUserDevices(@RequestParam("userId") String userId);

    /**
     * Verify biometric authentication
     *
     * Verifies biometric authentication (fingerprint, face ID) for
     * sensitive operations like transaction approval.
     *
     * @param userId The user ID
     * @param biometricToken The biometric verification token
     * @return true if biometric authentication successful
     * @throws feign.FeignException if the service call fails
     */
    @PostMapping("/api/v1/security/biometric/verify")
    Boolean verifyBiometric(
        @RequestParam("userId") String userId,
        @RequestParam("biometricToken") String biometricToken
    );

    /**
     * Check if IP address is blacklisted
     *
     * Checks if an IP address is on the security blacklist.
     *
     * @param ipAddress The IP address to check
     * @return true if IP is blacklisted, false otherwise
     * @throws feign.FeignException if the service call fails
     */
    @GetMapping("/api/v1/security/ip/is-blacklisted")
    Boolean isIpBlacklisted(@RequestParam("ipAddress") String ipAddress);

    /**
     * Log failed authentication attempt
     *
     * Logs a failed authentication attempt for security monitoring.
     * Triggers account lockout after multiple failures.
     *
     * @param userId The user ID
     * @param reason Failure reason (INVALID_PASSWORD, ACCOUNT_LOCKED, etc.)
     * @param ipAddress Source IP address
     * @param deviceId Device identifier
     * @throws feign.FeignException if the service call fails
     */
    @PostMapping("/api/v1/security/auth/log-failure")
    void logAuthenticationFailure(
        @RequestParam("userId") String userId,
        @RequestParam("reason") String reason,
        @RequestParam("ipAddress") String ipAddress,
        @RequestParam(value = "deviceId", required = false) String deviceId
    );

    /**
     * Get security risk score for user
     *
     * Calculates a security risk score (0-100) for a user based on:
     * - Recent suspicious activities
     * - Failed authentication attempts
     * - Unusual transaction patterns
     * - Account age and verification status
     * - Device security posture
     *
     * @param userId The user ID
     * @return Risk score (0 = low risk, 100 = high risk)
     * @throws feign.FeignException if the service call fails
     */
    @GetMapping("/api/v1/security/risk/score")
    Integer getUserRiskScore(@RequestParam("userId") String userId);

    /**
     * Enable two-factor authentication
     *
     * Enables 2FA for a user account (SMS, TOTP, or email-based).
     *
     * @param userId The user ID
     * @param method The 2FA method (SMS, TOTP, EMAIL)
     * @return true if 2FA enabled successfully
     * @throws feign.FeignException if the service call fails
     */
    @PostMapping("/api/v1/security/2fa/enable")
    Boolean enableTwoFactorAuth(
        @RequestParam("userId") String userId,
        @RequestParam("method") String method
    );

    /**
     * Verify two-factor authentication code
     *
     * Verifies a 2FA code for sensitive operations.
     *
     * @param userId The user ID
     * @param code The 2FA code
     * @return true if code is valid
     * @throws feign.FeignException if the service call fails
     */
    @PostMapping("/api/v1/security/2fa/verify")
    Boolean verifyTwoFactorCode(
        @RequestParam("userId") String userId,
        @RequestParam("code") String code
    );
}
