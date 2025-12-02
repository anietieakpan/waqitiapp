package com.waqiti.user.controller;

import com.waqiti.common.api.ApiResponse;
import com.waqiti.common.ratelimit.RateLimit;
import com.waqiti.common.ratelimit.RateLimit.KeyType;
import com.waqiti.common.ratelimit.RateLimit.Priority;
import com.waqiti.user.dto.*;
import com.waqiti.user.service.AuthenticationService;
import com.waqiti.user.service.UserRegistrationService;
import com.waqiti.user.service.PasswordResetService;
import com.waqiti.user.service.MfaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * CRITICAL AUTHENTICATION ENDPOINTS CONTROLLER
 * 
 * Handles the most sensitive authentication operations with strict rate limiting
 * to prevent brute force attacks and ensure security:
 * 
 * - Login attempts: 5 requests/5 minutes per IP (brute force protection)
 * - User registration: 3 requests/hour per IP (spam prevention)
 * - Password reset: 3 requests/hour per email (abuse prevention)
 * - OTP verification: 5 requests/5 minutes per user (enumeration protection)
 * 
 * All limits are designed per industry security standards and regulatory requirements.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Critical Authentication", description = "High-security authentication operations with strict rate limiting")
public class CriticalAuthController {

    private final AuthenticationService authenticationService;
    private final UserRegistrationService registrationService;
    private final PasswordResetService passwordResetService;
    private final MfaService mfaService;

    /**
     * CRITICAL ENDPOINT: User Login
     * Rate Limited: 5 requests/5 minutes per IP (brute force protection)
     * Priority: EMERGENCY
     */
    @PostMapping("/login")
    @RateLimit(
        requests = 5,
        window = 5,
        unit = TimeUnit.MINUTES,
        keyType = KeyType.IP,
        priority = Priority.EMERGENCY,
        burstAllowed = false,
        blockDuration = 15,
        blockUnit = TimeUnit.MINUTES,
        alertThreshold = 0.6,
        description = "Login endpoint - maximum brute force protection",
        errorMessage = "Too many login attempts from this IP address. Please wait 15 minutes before trying again."
    )
    @Operation(
        summary = "User login authentication",
        description = "Authenticates user credentials. Strictly limited to 5 attempts per 5 minutes per IP to prevent brute force attacks."
    )
    public ResponseEntity<ApiResponse<AuthenticationResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            @RequestHeader("X-Request-ID") String requestId,
            @RequestHeader(value = "X-Device-ID", required = false) String deviceId,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        
        String clientIp = getClientIpAddress(httpRequest);
        
        log.warn("CRITICAL_LOGIN_ATTEMPT: IP {} attempting login for user {} - Device: {} - UserAgent: {}", 
                clientIp, request.getUsername(), deviceId, userAgent);
        
        try {
            // Check for suspicious login patterns
            if (authenticationService.isSuspiciousLoginAttempt(clientIp, request.getUsername())) {
                log.error("SUSPICIOUS_LOGIN_BLOCKED: IP {} blocked for suspicious activity - User: {}", 
                        clientIp, request.getUsername());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Login blocked due to suspicious activity. Contact support if this is legitimate."));
            }
            
            AuthenticationResponse response = authenticationService.authenticate(
                request, clientIp, deviceId, userAgent, requestId);
            
            if (response.isSuccess()) {
                log.info("CRITICAL_LOGIN_SUCCESS: User {} successfully authenticated from IP {}", 
                        request.getUsername(), clientIp);
            } else {
                log.warn("CRITICAL_LOGIN_FAILED: Authentication failed for user {} from IP {} - Reason: {}", 
                        request.getUsername(), clientIp, response.getErrorMessage());
            }
            
            return ResponseEntity.ok(ApiResponse.success(response, "Authentication completed"));
            
        } catch (Exception e) {
            log.error("CRITICAL_LOGIN_ERROR: Authentication error for user {} from IP {} - Error: {}", 
                    request.getUsername(), clientIp, e.getMessage(), e);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Authentication service temporarily unavailable. Please try again later."));
        }
    }

    /**
     * CRITICAL ENDPOINT: User Registration
     * Rate Limited: 3 requests/hour per IP (spam prevention)
     * Priority: HIGH
     */
    @PostMapping("/register")
    @RateLimit(
        requests = 3,
        window = 1,
        unit = TimeUnit.HOURS,
        keyType = KeyType.IP,
        priority = Priority.HIGH,
        burstAllowed = false,
        blockDuration = 2,
        blockUnit = TimeUnit.HOURS,
        alertThreshold = 0.5,
        description = "User registration endpoint - spam protection",
        errorMessage = "Registration limit exceeded. Maximum 3 registrations per hour allowed from this IP address."
    )
    @Operation(
        summary = "Register new user account",
        description = "Creates a new user account. Limited to 3 registrations per hour per IP to prevent spam and automated attacks."
    )
    public ResponseEntity<ApiResponse<RegistrationResponse>> register(
            @Valid @RequestBody RegistrationRequest request,
            HttpServletRequest httpRequest,
            @RequestHeader("X-Request-ID") String requestId,
            @RequestHeader(value = "X-Device-ID", required = false) String deviceId) {
        
        String clientIp = getClientIpAddress(httpRequest);
        
        log.info("CRITICAL_REGISTRATION_ATTEMPT: IP {} attempting registration for email {} - Device: {}", 
                clientIp, request.getEmail(), deviceId);
        
        try {
            // Check for registration abuse
            if (registrationService.isRegistrationAbuse(clientIp, request.getEmail())) {
                log.error("REGISTRATION_ABUSE_BLOCKED: IP {} blocked for registration abuse - Email: {}", 
                        clientIp, request.getEmail());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Registration blocked. Multiple accounts detected from this source."));
            }
            
            RegistrationResponse response = registrationService.registerUser(request, clientIp, deviceId, requestId);
            
            log.info("CRITICAL_REGISTRATION_SUCCESS: User {} registered successfully from IP {}", 
                    request.getEmail(), clientIp);
            
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "User registration completed successfully"));
            
        } catch (Exception e) {
            log.error("CRITICAL_REGISTRATION_ERROR: Registration error for email {} from IP {} - Error: {}", 
                    request.getEmail(), clientIp, e.getMessage(), e);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Registration service temporarily unavailable. Please try again later."));
        }
    }

    /**
     * CRITICAL ENDPOINT: Password Reset Request
     * Rate Limited: 3 requests/hour per email (abuse prevention)
     * Priority: HIGH
     */
    @PostMapping("/password-reset")
    @RateLimit(
        requests = 3,
        window = 1,
        unit = TimeUnit.HOURS,
        keyType = KeyType.CUSTOM,
        customKey = "#request.email",
        priority = Priority.HIGH,
        burstAllowed = false,
        blockDuration = 1,
        blockUnit = TimeUnit.HOURS,
        alertThreshold = 0.6,
        description = "Password reset endpoint - abuse protection",
        errorMessage = "Password reset limit exceeded. Maximum 3 reset requests per hour allowed per email address."
    )
    @Operation(
        summary = "Request password reset",
        description = "Initiates password reset process. Limited to 3 requests per hour per email to prevent abuse."
    )
    public ResponseEntity<ApiResponse<PasswordResetResponse>> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request,
            HttpServletRequest httpRequest,
            @RequestHeader("X-Request-ID") String requestId) {
        
        String clientIp = getClientIpAddress(httpRequest);
        
        log.warn("CRITICAL_PASSWORD_RESET: IP {} requesting reset for email {}", clientIp, request.getEmail());
        
        try {
            // Check for password reset abuse
            if (passwordResetService.isResetAbuse(request.getEmail(), clientIp)) {
                log.error("PASSWORD_RESET_ABUSE: IP {} blocked for reset abuse - Email: {}", 
                        clientIp, request.getEmail());
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.error("Too many password reset requests. Please wait before requesting again."));
            }
            
            PasswordResetResponse response = passwordResetService.initiateReset(request, clientIp, requestId);
            
            log.warn("CRITICAL_PASSWORD_RESET_INITIATED: Reset initiated for email {} from IP {}", 
                    request.getEmail(), clientIp);
            
            return ResponseEntity.ok(ApiResponse.success(response, "Password reset request processed"));
            
        } catch (Exception e) {
            log.error("CRITICAL_PASSWORD_RESET_ERROR: Reset error for email {} from IP {} - Error: {}", 
                    request.getEmail(), clientIp, e.getMessage(), e);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Password reset service temporarily unavailable. Please try again later."));
        }
    }

    /**
     * CRITICAL ENDPOINT: OTP Verification
     * Rate Limited: 5 requests/5 minutes per user (enumeration protection)
     * Priority: CRITICAL
     */
    @PostMapping("/verify-otp")
    @RateLimit(
        requests = 5,
        window = 5,
        unit = TimeUnit.MINUTES,
        keyType = KeyType.USER,
        priority = Priority.CRITICAL,
        burstAllowed = false,
        blockDuration = 30,
        blockUnit = TimeUnit.MINUTES,
        alertThreshold = 0.6,
        description = "OTP verification endpoint - enumeration protection",
        errorMessage = "Too many OTP verification attempts. Account temporarily locked for 30 minutes for security."
    )
    @Operation(
        summary = "Verify OTP code",
        description = "Verifies one-time password code. Limited to 5 attempts per 5 minutes per user to prevent code enumeration."
    )
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<OtpVerificationResponse>> verifyOtp(
            @Valid @RequestBody OtpVerificationRequest request,
            HttpServletRequest httpRequest,
            @RequestHeader("X-Request-ID") String requestId,
            @RequestHeader(value = "X-User-ID", required = false) String userId) {
        
        String clientIp = getClientIpAddress(httpRequest);
        
        log.warn("CRITICAL_OTP_VERIFICATION: IP {} verifying OTP for user {} - Type: {}", 
                clientIp, userId, request.getOtpType());
        
        try {
            // Check for OTP enumeration attempts
            if (mfaService.isOtpEnumerationAttempt(userId, clientIp)) {
                log.error("OTP_ENUMERATION_BLOCKED: IP {} blocked for enumeration - User: {}", clientIp, userId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Account temporarily locked due to suspicious OTP verification attempts."));
            }
            
            OtpVerificationResponse response = mfaService.verifyOtp(request, userId, clientIp, requestId);
            
            if (response.isSuccess()) {
                log.info("CRITICAL_OTP_SUCCESS: OTP verified for user {} from IP {}", userId, clientIp);
            } else {
                log.warn("CRITICAL_OTP_FAILED: OTP verification failed for user {} from IP {} - Attempts: {}", 
                        userId, clientIp, response.getAttemptsRemaining());
            }
            
            return ResponseEntity.ok(ApiResponse.success(response, "OTP verification completed"));
            
        } catch (Exception e) {
            log.error("CRITICAL_OTP_ERROR: OTP verification error for user {} from IP {} - Error: {}", 
                    userId, clientIp, e.getMessage(), e);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("OTP verification service temporarily unavailable. Please try again later."));
        }
    }

    /**
     * CRITICAL ENDPOINT: Account Unlock Request
     * Rate Limited: 2 requests/hour per IP
     * Priority: HIGH
     */
    @PostMapping("/unlock-account")
    @RateLimit(
        requests = 2,
        window = 1,
        unit = TimeUnit.HOURS,
        keyType = KeyType.IP,
        priority = Priority.HIGH,
        burstAllowed = false,
        blockDuration = 2,
        blockUnit = TimeUnit.HOURS,
        description = "Account unlock request endpoint"
    )
    @Operation(
        summary = "Request account unlock",
        description = "Requests unlock for a locked account. Limited to 2 requests per hour per IP."
    )
    public ResponseEntity<ApiResponse<AccountUnlockResponse>> requestAccountUnlock(
            @Valid @RequestBody AccountUnlockRequest request,
            HttpServletRequest httpRequest,
            @RequestHeader("X-Request-ID") String requestId) {
        
        String clientIp = getClientIpAddress(httpRequest);
        
        log.warn("CRITICAL_UNLOCK_REQUEST: IP {} requesting unlock for email {}", clientIp, request.getEmail());
        
        try {
            AccountUnlockResponse response = authenticationService.requestAccountUnlock(request, clientIp, requestId);
            
            log.warn("CRITICAL_UNLOCK_PROCESSED: Unlock request processed for email {} from IP {}", 
                    request.getEmail(), clientIp);
            
            return ResponseEntity.ok(ApiResponse.success(response, "Account unlock request processed"));
            
        } catch (Exception e) {
            log.error("CRITICAL_UNLOCK_ERROR: Unlock error for email {} from IP {} - Error: {}", 
                    request.getEmail(), clientIp, e.getMessage(), e);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Account unlock service temporarily unavailable."));
        }
    }

    /**
     * Utility endpoint for monitoring authentication rate limits
     */
    @GetMapping("/rate-limit/status")
    @RateLimit(
        requests = 20,
        window = 1,
        unit = TimeUnit.MINUTES,
        keyType = KeyType.IP,
        priority = Priority.LOW,
        description = "Authentication rate limit status check"
    )
    @Operation(summary = "Check authentication rate limit status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAuthRateLimitStatus(
            HttpServletRequest httpRequest) {
        
        String clientIp = getClientIpAddress(httpRequest);
        
        Map<String, Object> status = Map.of(
            "clientIp", clientIp,
            "service", "user-service",
            "endpoint", "authentication",
            "timestamp", System.currentTimeMillis(),
            "message", "Authentication rate limit status check completed"
        );
        
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    /**
     * Extract real client IP address considering proxy headers
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }
        
        String xClusterClientIp = request.getHeader("X-Cluster-Client-IP");
        if (xClusterClientIp != null && !xClusterClientIp.isEmpty() && !"unknown".equalsIgnoreCase(xClusterClientIp)) {
            return xClusterClientIp;
        }
        
        return request.getRemoteAddr();
    }
}