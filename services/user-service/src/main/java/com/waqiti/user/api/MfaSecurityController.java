package com.waqiti.user.api;

import com.waqiti.common.api.ApiResponse;
import com.waqiti.user.security.MfaSecurityService;
import com.waqiti.user.security.MfaSecurityService.MfaSecurityResult;
import com.waqiti.user.service.KeycloakAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Production-grade MFA Security Controller.
 * Handles MFA verification with comprehensive security controls.
 */
@RestController
@RequestMapping("/api/v1/auth/mfa")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "MFA Security", description = "Multi-factor authentication with security controls")
public class MfaSecurityController {
    
    private final MfaSecurityService mfaSecurityService;
    private final KeycloakAuthService keycloakAuthService;
    
    @PostMapping("/verify")
    @Operation(summary = "Verify MFA code with security validation", 
               description = "Verifies MFA code with rate limiting, lockout protection, and suspicious activity detection")
    public ResponseEntity<ApiResponse<MfaVerificationResponse>> verifyMfa(
            @NonNull @RequestBody MfaVerificationRequest request,
            @NonNull HttpServletRequest httpRequest) {
        
        String userId = getCurrentUserId();
        String sourceIp = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        String deviceFingerprint = request.getDeviceFingerprint();
        
        log.info("MFA verification attempt: userId={}, ip={}", userId, sourceIp);
        
        try {
            // Check if MFA attempt is allowed (rate limiting, lockouts, etc.)
            MfaSecurityResult securityResult = mfaSecurityService.checkMfaAttemptAllowed(
                    userId, sourceIp, userAgent, deviceFingerprint);
            
            if (!securityResult.isAllowed()) {
                log.warn("MFA attempt blocked for user {}: {}", userId, securityResult.getReason());
                
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(ApiResponse.error(
                                securityResult.getReason(),
                                "MFA_BLOCKED",
                                Map.of(
                                    "attemptId", securityResult.getAttemptId(),
                                    "retryAfter", securityResult.getRetryAfter() != null ? 
                                        securityResult.getRetryAfter().toSeconds() : 0
                                )
                        ));
            }
            
            // Attempt MFA verification
            boolean mfaValid = false;
            String failureReason = null;
            
            try {
                // Verify MFA code using Keycloak or other MFA provider
                mfaValid = verifyMfaCode(userId, request.getMfaCode(), request.getMfaType());
                
            } catch (Exception e) {
                failureReason = "MFA verification failed: " + e.getMessage();
                log.error("MFA verification error for user {}: {}", userId, e.getMessage(), e);
            }
            
            if (mfaValid) {
                // Record successful MFA
                mfaSecurityService.recordMfaSuccess(
                        userId, sourceIp, securityResult.getAttemptId(), 
                        deviceFingerprint, request.isTrustDevice());
                
                log.info("MFA verification successful for user: {}", userId);
                
                MfaVerificationResponse response = MfaVerificationResponse.builder()
                        .success(true)
                        .attemptId(securityResult.getAttemptId())
                        .trustedDevice(securityResult.isTrustedDevice())
                        .warning(securityResult.hasWarning() ? securityResult.getWarningMessage() : null)
                        .nextSteps(securityResult.hasWarning() ? 
                            Map.of("additionalVerificationRequired", true) : null)
                        .build();
                
                return ResponseEntity.ok(ApiResponse.success(response));
                
            } else {
                // Record MFA failure
                mfaSecurityService.recordMfaFailure(
                        userId, sourceIp, securityResult.getAttemptId(), 
                        failureReason != null ? failureReason : "Invalid MFA code", 
                        deviceFingerprint);
                
                log.warn("MFA verification failed for user: {}", userId);
                
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(
                                "Invalid MFA code. Please try again.",
                                "MFA_INVALID",
                                Map.of("attemptId", securityResult.getAttemptId())
                        ));
            }
            
        } catch (Exception e) {
            log.error("Unexpected error during MFA verification for user {}: {}", userId, e.getMessage(), e);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(
                            "MFA verification temporarily unavailable. Please try again.",
                            "MFA_SERVICE_ERROR"
                    ));
        }
    }
    
    @GetMapping("/status")
    @Operation(summary = "Get MFA security status", 
               description = "Returns current MFA security status including lockout information")
    public ResponseEntity<ApiResponse<MfaSecurityStatus>> getMfaSecurityStatus(
            @NonNull HttpServletRequest httpRequest) {
        
        String userId = getCurrentUserId();
        String sourceIp = getClientIpAddress(httpRequest);
        
        try {
            // Check current security status without incrementing counters
            MfaSecurityResult securityResult = mfaSecurityService.checkMfaAttemptAllowed(
                    userId, sourceIp, null, null);
            
            MfaSecurityStatus status = MfaSecurityStatus.builder()
                    .allowed(securityResult.isAllowed())
                    .reason(securityResult.getReason())
                    .retryAfterSeconds(securityResult.getRetryAfter() != null ? 
                        securityResult.getRetryAfter().toSeconds() : null)
                    .trustedDevice(securityResult.isTrustedDevice())
                    .hasWarning(securityResult.hasWarning())
                    .warningMessage(securityResult.getWarningMessage())
                    .timestamp(Instant.now())
                    .build();
            
            return ResponseEntity.ok(ApiResponse.success(status));
            
        } catch (Exception e) {
            log.error("Error getting MFA security status for user {}: {}", userId, e.getMessage(), e);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Unable to retrieve security status"));
        }
    }
    
    @PostMapping("/trust-device")
    @Operation(summary = "Trust current device for MFA", 
               description = "Marks current device as trusted to reduce MFA frequency")
    public ResponseEntity<ApiResponse<DeviceTrustResponse>> trustDevice(
            @NonNull @RequestBody TrustDeviceRequest request,
            @NonNull HttpServletRequest httpRequest) {
        
        String userId = getCurrentUserId();
        String sourceIp = getClientIpAddress(httpRequest);
        String deviceFingerprint = request.getDeviceFingerprint();
        
        log.info("Device trust request: userId={}, ip={}", userId, sourceIp);
        
        try {
            // Verify MFA before trusting device
            if (!verifyMfaCode(userId, request.getMfaCode(), request.getMfaType())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Invalid MFA code. Cannot trust device."));
            }
            
            // Record successful MFA with device trust
            mfaSecurityService.recordMfaSuccess(userId, sourceIp, 
                    java.util.UUID.randomUUID().toString(), deviceFingerprint, true);
            
            DeviceTrustResponse response = DeviceTrustResponse.builder()
                    .deviceTrusted(true)
                    .trustDuration(Duration.ofDays(30)) // Configurable
                    .deviceId(deviceFingerprint)
                    .trustedAt(Instant.now())
                    .build();
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("Error trusting device for user {}: {}", userId, e.getMessage(), e);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Unable to trust device. Please try again."));
        }
    }
    
    @DeleteMapping("/untrust-device")
    @Operation(summary = "Remove device from trusted list", 
               description = "Removes device from trusted list requiring MFA for future logins")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Void>> untrustDevice(
            @NonNull @RequestParam @NotBlank String deviceFingerprint) {
        
        String userId = getCurrentUserId();
        
        log.info("Device untrust request: userId={}, deviceFingerprint={}", userId, deviceFingerprint);
        
        try {
            // Remove device from Redis trusted devices cache
            String trustedDevicesKey = "trusted_devices:" + userId;
            
            // Use MfaSecurityService to handle device trust operations
            boolean deviceRemoved = mfaSecurityService.removeTrustedDevice(userId, deviceFingerprint);
            
            if (deviceRemoved) {
                // Log security event
                log.info("Device successfully removed from trusted list: userId={}, deviceFingerprint={}", 
                    userId, deviceFingerprint);
                
                // Invalidate any existing sessions on this device (security measure)
                invalidateDeviceSessions(userId, deviceFingerprint);
                
                // Send security notification to user
                sendDeviceUntrustNotification(userId, deviceFingerprint);
                
                return ResponseEntity.ok(ApiResponse.success(null, "Device removed from trusted list"));
            } else {
                log.warn("Device not found in trusted list: userId={}, deviceFingerprint={}", 
                    userId, deviceFingerprint);
                
                return ResponseEntity.ok(ApiResponse.success(null, "Device was not in trusted list"));
            }
            
        } catch (Exception e) {
            log.error("Error untrusting device for user {}: {}", userId, e.getMessage(), e);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Unable to remove device trust"));
        }
    }
    
    @GetMapping("/trusted-devices")
    @Operation(summary = "List trusted devices", 
               description = "Returns list of devices trusted for MFA")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<TrustedDevicesResponse>> getTrustedDevices() {
        
        String userId = getCurrentUserId();
        
        try {
            // Retrieve trusted devices from MfaSecurityService
            java.util.List<TrustedDevice> trustedDevices = mfaSecurityService.getTrustedDevices(userId);
            
            // Convert to response DTOs with security information
            java.util.List<TrustedDeviceResponse> deviceResponses = trustedDevices.stream()
                .map(device -> TrustedDeviceResponse.builder()
                    .deviceFingerprint(device.getDeviceFingerprint())
                    .deviceName(device.getDeviceName())
                    .deviceType(device.getDeviceType())
                    .operatingSystem(device.getOperatingSystem())
                    .browser(device.getBrowser())
                    .location(device.getLastLocation())
                    .trustedAt(device.getTrustedAt())
                    .lastUsedAt(device.getLastUsedAt())
                    .ipAddress(maskIpAddress(device.getLastIpAddress()))
                    .isCurrentDevice(isCurrentDevice(device.getDeviceFingerprint()))
                    .build())
                .collect(java.util.stream.Collectors.toList());
            
            // Sort by last used (most recent first)
            deviceResponses.sort((a, b) -> b.getLastUsedAt().compareTo(a.getLastUsedAt()));
            
            TrustedDevicesResponse response = TrustedDevicesResponse.builder()
                    .devices(deviceResponses)
                    .totalCount(deviceResponses.size())
                    .maxTrustedDevices(getMaxTrustedDevicesForUser(userId))
                    .build();
            
            log.debug("Retrieved {} trusted devices for user {}", deviceResponses.size(), userId);
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("Error retrieving trusted devices for user {}: {}", userId, e.getMessage(), e);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Unable to retrieve trusted devices"));
        }
    }
    
    @PostMapping("/admin/unlock-user")
    @Operation(summary = "Admin: Unlock user account", 
               description = "Administrative function to unlock permanently locked accounts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> unlockUser(
            @NonNull @RequestBody UnlockUserRequest request) {
        
        String adminUserId = getCurrentUserId();
        String targetUserId = request.getUserId();
        
        log.info("Admin unlock request: admin={}, target={}, reason={}", 
                adminUserId, targetUserId, request.getReason());
        
        try {
            // Validate admin permissions
            if (!hasAdminPermissions(adminUserId)) {
                log.warn("Unauthorized admin unlock attempt: admin={}, target={}", adminUserId, targetUserId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Insufficient permissions for admin operations"));
            }
            
            // Validate target user exists
            if (!userExists(targetUserId)) {
                log.warn("Admin unlock attempted for non-existent user: admin={}, target={}", adminUserId, targetUserId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Target user not found"));
            }
            
            // Remove user lockout from MfaSecurityService
            boolean unlockSuccessful = mfaSecurityService.unlockUser(targetUserId);
            
            if (unlockSuccessful) {
                // Audit the admin action
                auditAdminUnlockAction(adminUserId, targetUserId, request.getReason());
                
                // Send notification to the unlocked user
                sendAccountUnlockedNotification(targetUserId, adminUserId);
                
                // Log successful unlock
                log.info("User account unlocked successfully: admin={}, target={}, reason={}", 
                    adminUserId, targetUserId, request.getReason());
                
                return ResponseEntity.ok(ApiResponse.success(null, "User account unlocked successfully"));
            } else {
                log.warn("Failed to unlock user account: admin={}, target={}", adminUserId, targetUserId);
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("User account was not locked or unlock failed"));
            }
            
        } catch (Exception e) {
            log.error("Error unlocking user {} by admin {}: {}", 
                    targetUserId, adminUserId, e.getMessage(), e);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Unable to unlock user account"));
        }
    }
    
    // Private helper methods
    
    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        throw new SecurityException("No authenticated user found");
    }
    
    @Nullable
    private String getClientIpAddress(@NonNull HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    private boolean verifyMfaCode(@NonNull String userId, @NonNull String mfaCode, @NonNull MfaType mfaType) {
        // Integration with actual MFA verification service
        // This could be TOTP, SMS, hardware token, etc.
        try {
            switch (mfaType) {
                case TOTP:
                    return verifyTotpCode(userId, mfaCode);
                case SMS:
                    return verifySmsCode(userId, mfaCode);
                case EMAIL:
                    return verifyEmailCode(userId, mfaCode);
                case HARDWARE_TOKEN:
                    return verifyHardwareToken(userId, mfaCode);
                default:
                    return false;
            }
        } catch (Exception e) {
            log.error("MFA code verification failed for user {} with type {}: {}", 
                    userId, mfaType, e.getMessage(), e);
            return false;
        }
    }
    
    private boolean verifyTotpCode(@NonNull String userId, @NonNull String code) {
        try {
            // Get user's TOTP secret from secure storage
            String totpSecret = mfaSecurityService.getUserTotpSecret(userId);
            if (totpSecret == null) {
                log.warn("No TOTP secret found for user: {}", userId);
                return false;
            }
            
            // Validate code format
            if (!code.matches("\\d{6}")) {
                log.debug("Invalid TOTP code format for user: {}", userId);
                return false;
            }
            
            // Use TOTP algorithm to verify code
            long currentTimeSlot = System.currentTimeMillis() / 30000; // 30-second time slots
            
            // Check current time slot and previous/next for clock skew tolerance
            for (int i = -1; i <= 1; i++) {
                String expectedCode = generateTotpCode(totpSecret, currentTimeSlot + i);
                if (code.equals(expectedCode)) {
                    // Verify this code hasn't been used recently (replay attack prevention)
                    if (!mfaSecurityService.isCodeRecentlyUsed(userId, code)) {
                        mfaSecurityService.markCodeAsUsed(userId, code);
                        log.debug("TOTP code verified successfully for user: {}", userId);
                        return true;
                    } else {
                        log.warn("TOTP code replay attempt detected for user: {}", userId);
                        return false;
                    }
                }
            }
            
            log.debug("TOTP code verification failed for user: {}", userId);
            return false;
            
        } catch (Exception e) {
            log.error("Error verifying TOTP code for user {}: {}", userId, e.getMessage(), e);
            return false;
        }
    }
    
    private boolean verifySmsCode(@NonNull String userId, @NonNull String code) {
        try {
            // Validate code format
            if (!code.matches("\\d{6}")) {
                log.debug("Invalid SMS code format for user: {}", userId);
                return false;
            }
            
            // Get expected SMS code from cache (with expiration)
            String expectedCode = mfaSecurityService.getSmsCode(userId);
            if (expectedCode == null) {
                log.debug("No SMS code found or expired for user: {}", userId);
                return false;
            }
            
            // Verify code matches
            if (code.equals(expectedCode)) {
                // Remove used code from cache
                mfaSecurityService.removeSmsCode(userId);
                
                // Check rate limiting (prevent SMS bombing)
                if (mfaSecurityService.isSmsRateLimited(userId)) {
                    log.warn("SMS verification rate limited for user: {}", userId);
                    return false;
                }
                
                log.debug("SMS code verified successfully for user: {}", userId);
                return true;
            } else {
                log.debug("SMS code verification failed for user: {}", userId);
                return false;
            }
            
        } catch (Exception e) {
            log.error("Error verifying SMS code for user {}: {}", userId, e.getMessage(), e);
            return false;
        }
    }
    
    private boolean verifyEmailCode(@NonNull String userId, @NonNull String code) {
        try {
            // Validate code format
            if (!code.matches("\\d{6}")) {
                log.debug("Invalid email code format for user: {}", userId);
                return false;
            }
            
            // Get expected email code from cache (with expiration)
            String expectedCode = mfaSecurityService.getEmailCode(userId);
            if (expectedCode == null) {
                log.debug("No email code found or expired for user: {}", userId);
                return false;
            }
            
            // Verify code matches
            if (code.equals(expectedCode)) {
                // Remove used code from cache
                mfaSecurityService.removeEmailCode(userId);
                
                // Check rate limiting (prevent email bombing)
                if (mfaSecurityService.isEmailRateLimited(userId)) {
                    log.warn("Email verification rate limited for user: {}", userId);
                    return false;
                }
                
                log.debug("Email code verified successfully for user: {}", userId);
                return true;
            } else {
                log.debug("Email code verification failed for user: {}", userId);
                return false;
            }
            
        } catch (Exception e) {
            log.error("Error verifying email code for user {}: {}", userId, e.getMessage(), e);
            return false;
        }
    }
    
    private boolean verifyHardwareToken(String userId, String code) {
        try {
            // Validate code format (6-8 digits for hardware tokens)
            if (!code.matches("\\d{6,8}")) {
                log.debug("Invalid hardware token code format for user: {}", userId);
                return false;
            }
            
            // Get user's hardware token configuration
            HardwareTokenConfig tokenConfig = mfaSecurityService.getHardwareTokenConfig(userId);
            if (tokenConfig == null) {
                log.warn("No hardware token configured for user: {}", userId);
                return false;
            }
            
            // Verify against hardware token using appropriate algorithm
            boolean isValid = false;
            switch (tokenConfig.getTokenType()) {
                case "RSA_SECURID":
                    isValid = verifyRsaSecurIdToken(tokenConfig, code);
                    break;
                case "YUBIKEY":
                    isValid = verifyYubikeyToken(tokenConfig, code);
                    break;
                case "FIDO2":
                    isValid = verifyFido2Token(tokenConfig, code);
                    break;
                default:
                    log.warn("Unsupported hardware token type: {} for user: {}", 
                        tokenConfig.getTokenType(), userId);
                    return false;
            }
            
            if (isValid) {
                // Check for replay attacks
                if (!mfaSecurityService.isTokenCodeRecentlyUsed(userId, code)) {
                    mfaSecurityService.markTokenCodeAsUsed(userId, code);
                    log.debug("Hardware token verified successfully for user: {}", userId);
                    return true;
                } else {
                    log.warn("Hardware token replay attempt detected for user: {}", userId);
                    return false;
                }
            } else {
                log.debug("Hardware token verification failed for user: {}", userId);
                return false;
            }
            
        } catch (Exception e) {
            log.error("Error verifying hardware token for user {}: {}", userId, e.getMessage(), e);
            return false;
        }
    }
    
    // Helper methods for supporting functionality
    
    private void invalidateDeviceSessions(String userId, String deviceFingerprint) {
        try {
            mfaSecurityService.invalidateDeviceSessions(userId, deviceFingerprint);
            log.debug("Invalidated sessions for device: userId={}, device={}", userId, deviceFingerprint);
        } catch (Exception e) {
            log.warn("Failed to invalidate device sessions: userId={}, device={}", userId, deviceFingerprint, e);
        }
    }
    
    private void sendDeviceUntrustNotification(String userId, String deviceFingerprint) {
        try {
            mfaSecurityService.sendSecurityNotification(userId, "DEVICE_UNTRUSTED", 
                Map.of("deviceFingerprint", deviceFingerprint, "timestamp", Instant.now()));
        } catch (Exception e) {
            log.warn("Failed to send device untrust notification: userId={}", userId, e);
        }
    }
    
    private String maskIpAddress(String ipAddress) {
        if (ipAddress == null) return "N/A";
        
        // Mask IP for privacy (show only first two octets for IPv4)
        String[] parts = ipAddress.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".***.***.";
        }
        
        // For IPv6 or other formats, mask most of it
        if (ipAddress.length() > 8) {
            return ipAddress.substring(0, 4) + "****" + ipAddress.substring(ipAddress.length() - 4);
        }
        
        return "***";
    }
    
    private boolean isCurrentDevice(String deviceFingerprint) {
        try {
            // This would compare against current request's device fingerprint
            // For now, return false as it requires request context
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    
    private int getMaxTrustedDevicesForUser(String userId) {
        try {
            // Get user's plan/tier to determine max trusted devices
            return mfaSecurityService.getMaxTrustedDevicesForUser(userId);
        } catch (Exception e) {
            return 5; // Default maximum
        }
    }
    
    private boolean hasAdminPermissions(String userId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            return auth != null && auth.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
        } catch (Exception e) {
            log.warn("Error checking admin permissions for user: {}", userId, e);
            return false;
        }
    }
    
    private boolean userExists(String userId) {
        try {
            return mfaSecurityService.userExists(userId);
        } catch (Exception e) {
            log.warn("Error checking user existence: {}", userId, e);
            return false;
        }
    }
    
    private void auditAdminUnlockAction(String adminUserId, String targetUserId, String reason) {
        try {
            mfaSecurityService.logSecurityEvent("ADMIN_UNLOCK", adminUserId,
                Map.of("targetUser", targetUserId, "reason", reason, "timestamp", Instant.now()));
        } catch (Exception e) {
            log.error("Failed to audit admin unlock action: admin={}, target={}", adminUserId, targetUserId, e);
        }
    }
    
    private void sendAccountUnlockedNotification(String userId, String adminUserId) {
        try {
            mfaSecurityService.sendSecurityNotification(userId, "ACCOUNT_UNLOCKED",
                Map.of("unlockedBy", adminUserId, "timestamp", Instant.now()));
        } catch (Exception e) {
            log.warn("Failed to send account unlocked notification: userId={}", userId, e);
        }
    }
    
    private String generateTotpCode(String secret, long timeSlot) {
        try {
            // Implement TOTP algorithm (RFC 6238)
            byte[] secretBytes = java.util.Base64.getDecoder().decode(secret);
            byte[] timeBytes = java.nio.ByteBuffer.allocate(8).putLong(timeSlot).array();
            
            javax.crypto.Mac hmac = javax.crypto.Mac.getInstance("HmacSHA1");
            javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(secretBytes, "HmacSHA1");
            hmac.init(keySpec);
            
            byte[] hash = hmac.doFinal(timeBytes);
            int offset = hash[hash.length - 1] & 0x0F;
            
            int code = ((hash[offset] & 0x7F) << 24) |
                      ((hash[offset + 1] & 0xFF) << 16) |
                      ((hash[offset + 2] & 0xFF) << 8) |
                      (hash[offset + 3] & 0xFF);
            
            code = code % 1000000;
            return String.format("%06d", code);
            
        } catch (Exception e) {
            log.error("Error generating TOTP code", e);
            return "000000";
        }
    }
    
    private boolean verifyRsaSecurIdToken(HardwareTokenConfig config, String code) {
        try {
            // RSA SecurID token verification logic
            // This would integrate with RSA Authentication Manager
            return mfaSecurityService.verifyRsaSecurId(config.getTokenSerial(), code);
        } catch (Exception e) {
            log.error("Error verifying RSA SecurID token", e);
            return false;
        }
    }
    
    private boolean verifyYubikeyToken(HardwareTokenConfig config, String code) {
        try {
            // Yubikey OTP verification logic
            // This would integrate with Yubico validation service
            return mfaSecurityService.verifyYubikey(config.getTokenId(), code);
        } catch (Exception e) {
            log.error("Error verifying Yubikey token", e);
            return false;
        }
    }
    
    private boolean verifyFido2Token(HardwareTokenConfig config, String code) {
        try {
            // FIDO2/WebAuthn verification logic
            // This would verify the assertion response
            return mfaSecurityService.verifyFido2(config.getCredentialId(), code);
        } catch (Exception e) {
            log.error("Error verifying FIDO2 token", e);
            return false;
        }
    }
    
    // Request/Response DTOs
    
    public static class MfaVerificationRequest {
        @NotBlank
        @Size(min = 4, max = 8)
        private String mfaCode;
        
        private MfaType mfaType = MfaType.TOTP;
        private String deviceFingerprint;
        private boolean trustDevice = false;
        
        // Getters and setters
        public String getMfaCode() { return mfaCode; }
        public void setMfaCode(String mfaCode) { this.mfaCode = mfaCode; }
        public MfaType getMfaType() { return mfaType; }
        public void setMfaType(MfaType mfaType) { this.mfaType = mfaType; }
        public String getDeviceFingerprint() { return deviceFingerprint; }
        public void setDeviceFingerprint(String deviceFingerprint) { this.deviceFingerprint = deviceFingerprint; }
        public boolean isTrustDevice() { return trustDevice; }
        public void setTrustDevice(boolean trustDevice) { this.trustDevice = trustDevice; }
    }
    
    public static class MfaVerificationResponse {
        private boolean success;
        private String attemptId;
        private boolean trustedDevice;
        private String warning;
        private Map<String, Object> nextSteps;
        
        public static MfaVerificationResponseBuilder builder() {
            return new MfaVerificationResponseBuilder();
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getAttemptId() { return attemptId; }
        public boolean isTrustedDevice() { return trustedDevice; }
        public String getWarning() { return warning; }
        public Map<String, Object> getNextSteps() { return nextSteps; }
        
        public static class MfaVerificationResponseBuilder {
            private boolean success;
            private String attemptId;
            private boolean trustedDevice;
            private String warning;
            private Map<String, Object> nextSteps;
            
            public MfaVerificationResponseBuilder success(boolean success) {
                this.success = success;
                return this;
            }
            
            public MfaVerificationResponseBuilder attemptId(String attemptId) {
                this.attemptId = attemptId;
                return this;
            }
            
            public MfaVerificationResponseBuilder trustedDevice(boolean trustedDevice) {
                this.trustedDevice = trustedDevice;
                return this;
            }
            
            public MfaVerificationResponseBuilder warning(String warning) {
                this.warning = warning;
                return this;
            }
            
            public MfaVerificationResponseBuilder nextSteps(Map<String, Object> nextSteps) {
                this.nextSteps = nextSteps;
                return this;
            }
            
            public MfaVerificationResponse build() {
                MfaVerificationResponse response = new MfaVerificationResponse();
                response.success = this.success;
                response.attemptId = this.attemptId;
                response.trustedDevice = this.trustedDevice;
                response.warning = this.warning;
                response.nextSteps = this.nextSteps;
                return response;
            }
        }
    }
    
    public static class MfaSecurityStatus {
        private boolean allowed;
        private String reason;
        private Long retryAfterSeconds;
        private boolean trustedDevice;
        private boolean hasWarning;
        private String warningMessage;
        private Instant timestamp;
        
        public static MfaSecurityStatusBuilder builder() {
            return new MfaSecurityStatusBuilder();
        }
        
        // Getters
        public boolean isAllowed() { return allowed; }
        public String getReason() { return reason; }
        public Long getRetryAfterSeconds() { return retryAfterSeconds; }
        public boolean isTrustedDevice() { return trustedDevice; }
        public boolean isHasWarning() { return hasWarning; }
        public String getWarningMessage() { return warningMessage; }
        public Instant getTimestamp() { return timestamp; }
        
        public static class MfaSecurityStatusBuilder {
            private boolean allowed;
            private String reason;
            private Long retryAfterSeconds;
            private boolean trustedDevice;
            private boolean hasWarning;
            private String warningMessage;
            private Instant timestamp;
            
            public MfaSecurityStatusBuilder allowed(boolean allowed) {
                this.allowed = allowed;
                return this;
            }
            
            public MfaSecurityStatusBuilder reason(String reason) {
                this.reason = reason;
                return this;
            }
            
            public MfaSecurityStatusBuilder retryAfterSeconds(Long retryAfterSeconds) {
                this.retryAfterSeconds = retryAfterSeconds;
                return this;
            }
            
            public MfaSecurityStatusBuilder trustedDevice(boolean trustedDevice) {
                this.trustedDevice = trustedDevice;
                return this;
            }
            
            public MfaSecurityStatusBuilder hasWarning(boolean hasWarning) {
                this.hasWarning = hasWarning;
                return this;
            }
            
            public MfaSecurityStatusBuilder warningMessage(String warningMessage) {
                this.warningMessage = warningMessage;
                return this;
            }
            
            public MfaSecurityStatusBuilder timestamp(Instant timestamp) {
                this.timestamp = timestamp;
                return this;
            }
            
            public MfaSecurityStatus build() {
                MfaSecurityStatus status = new MfaSecurityStatus();
                status.allowed = this.allowed;
                status.reason = this.reason;
                status.retryAfterSeconds = this.retryAfterSeconds;
                status.trustedDevice = this.trustedDevice;
                status.hasWarning = this.hasWarning;
                status.warningMessage = this.warningMessage;
                status.timestamp = this.timestamp;
                return status;
            }
        }
    }
    
    public static class TrustDeviceRequest {
        @NotBlank
        private String mfaCode;
        private MfaType mfaType = MfaType.TOTP;
        @NotBlank
        private String deviceFingerprint;
        
        // Getters and setters
        public String getMfaCode() { return mfaCode; }
        public void setMfaCode(String mfaCode) { this.mfaCode = mfaCode; }
        public MfaType getMfaType() { return mfaType; }
        public void setMfaType(MfaType mfaType) { this.mfaType = mfaType; }
        public String getDeviceFingerprint() { return deviceFingerprint; }
        public void setDeviceFingerprint(String deviceFingerprint) { this.deviceFingerprint = deviceFingerprint; }
    }
    
    public static class DeviceTrustResponse {
        private boolean deviceTrusted;
        private Duration trustDuration;
        private String deviceId;
        private Instant trustedAt;
        
        public static DeviceTrustResponseBuilder builder() {
            return new DeviceTrustResponseBuilder();
        }
        
        // Getters
        public boolean isDeviceTrusted() { return deviceTrusted; }
        public Duration getTrustDuration() { return trustDuration; }
        public String getDeviceId() { return deviceId; }
        public Instant getTrustedAt() { return trustedAt; }
        
        public static class DeviceTrustResponseBuilder {
            private boolean deviceTrusted;
            private Duration trustDuration;
            private String deviceId;
            private Instant trustedAt;
            
            public DeviceTrustResponseBuilder deviceTrusted(boolean deviceTrusted) {
                this.deviceTrusted = deviceTrusted;
                return this;
            }
            
            public DeviceTrustResponseBuilder trustDuration(Duration trustDuration) {
                this.trustDuration = trustDuration;
                return this;
            }
            
            public DeviceTrustResponseBuilder deviceId(String deviceId) {
                this.deviceId = deviceId;
                return this;
            }
            
            public DeviceTrustResponseBuilder trustedAt(Instant trustedAt) {
                this.trustedAt = trustedAt;
                return this;
            }
            
            public DeviceTrustResponse build() {
                DeviceTrustResponse response = new DeviceTrustResponse();
                response.deviceTrusted = this.deviceTrusted;
                response.trustDuration = this.trustDuration;
                response.deviceId = this.deviceId;
                response.trustedAt = this.trustedAt;
                return response;
            }
        }
    }
    
    public static class TrustedDevicesResponse {
        private java.util.List<TrustedDevice> devices;
        
        public static TrustedDevicesResponseBuilder builder() {
            return new TrustedDevicesResponseBuilder();
        }
        
        public java.util.List<TrustedDevice> getDevices() { return devices; }
        
        public static class TrustedDevicesResponseBuilder {
            private java.util.List<TrustedDevice> devices;
            
            public TrustedDevicesResponseBuilder devices(java.util.List<TrustedDevice> devices) {
                this.devices = devices;
                return this;
            }
            
            public TrustedDevicesResponse build() {
                TrustedDevicesResponse response = new TrustedDevicesResponse();
                response.devices = this.devices;
                return response;
            }
        }
        
        public static class TrustedDevice {
            private String deviceId;
            private String deviceName;
            private Instant trustedAt;
            private String lastUsedFrom;
            
            // Getters and setters
            public String getDeviceId() { return deviceId; }
            public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
            public String getDeviceName() { return deviceName; }
            public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
            public Instant getTrustedAt() { return trustedAt; }
            public void setTrustedAt(Instant trustedAt) { this.trustedAt = trustedAt; }
            public String getLastUsedFrom() { return lastUsedFrom; }
            public void setLastUsedFrom(String lastUsedFrom) { this.lastUsedFrom = lastUsedFrom; }
        }
    }
    
    public static class UnlockUserRequest {
        @NotBlank
        private String userId;
        @NotBlank
        private String reason;
        
        // Getters and setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
    
    public enum MfaType {
        TOTP,      // Time-based One-Time Password (Google Authenticator, Authy, etc.)
        SMS,       // SMS-based code
        EMAIL,     // Email-based code
        HARDWARE_TOKEN  // Hardware token (YubiKey, etc.)
    }
}