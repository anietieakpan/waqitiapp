// File: src/main/java/com/waqiti/user/api/UserController.java
package com.waqiti.user.api;

import com.waqiti.common.ratelimit.RateLimited;
import com.waqiti.user.domain.VerificationType;
import com.waqiti.user.dto.*;
import com.waqiti.user.service.UserService;
import com.waqiti.user.service.KeycloakAuthService;
import com.waqiti.user.security.AccountSettingsMfaService;
import com.waqiti.user.security.AccountSettingsMfaService.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = false)
public class UserController {
    private final UserService userService;
    private final KeycloakAuthService keycloakAuthService;
    private final AccountSettingsMfaService accountSettingsMfaService;
    private final com.waqiti.user.security.OwnershipValidator ownershipValidator;

    @PostMapping("/register")
    @RateLimited(keyType = RateLimited.KeyType.IP, capacity = 5, refillTokens = 5, refillPeriodMinutes = 60)
    public ResponseEntity<UserResponse> registerUser(@Valid @RequestBody UserRegistrationRequest request) {
        log.info("User registration request received for: {}", request.getUsername());
        return new ResponseEntity<>(userService.registerUser(request), HttpStatus.CREATED);
    }

    @GetMapping("/verify/{token}")
    @RateLimited(keyType = RateLimited.KeyType.IP, capacity = 10, refillTokens = 10, refillPeriodMinutes = 15)
    public ResponseEntity<String> verifyAccount(@PathVariable String token) {
        log.info("Email verification request received");
        boolean verified = userService.verifyToken(token, VerificationType.EMAIL);
        return ResponseEntity.ok(verified ? "Account verified successfully" : "Verification failed");
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN') or hasAnyAuthority('SCOPE_OPENID', 'SCOPE_PROFILE')")
    public ResponseEntity<UserResponse> getCurrentUser() {
        log.info("Current user request received");
        
        try {
            UUID userId = keycloakAuthService.getCurrentUserId();
            log.debug("Getting user details for authenticated user ID: {}", userId);
            UserResponse user = userService.getUserById(userId);
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            log.error("Error getting current user: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CLIENT_ADMIN') or @userSecurity.canAccessUser(#userId)")
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID userId) {
        log.info("User request received for ID: {}", userId);

        // SECURITY FIX (CRITICAL-002): Defense-in-depth ownership validation
        // Layer 1: @PreAuthorize (above)
        // Layer 2: Explicit runtime validation (below) - protects if Layer 1 fails
        ownershipValidator.validateUserOwnership(userId, "getUserById");

        return ResponseEntity.ok(userService.getUserById(userId));
    }

    @PutMapping("/profile")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN') or hasAnyAuthority('SCOPE_PROFILE')")
    public ResponseEntity<UserResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        log.info("Profile update request received");
        
        try {
            UUID userId = keycloakAuthService.getCurrentUserId();
            return ResponseEntity.ok(userService.updateProfile(userId, request));
        } catch (Exception e) {
            log.error("Error updating profile: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/password/change")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN') or hasAnyAuthority('SCOPE_PROFILE')")
    public ResponseEntity<String> changePassword(
            @Valid @RequestBody PasswordChangeRequest request) {
        log.info("Password change request received");
        
        // Note: Password changes should be handled through Keycloak Account Management Console
        // This endpoint is deprecated but kept for backward compatibility
        log.warn("Password change through API is deprecated. Use Keycloak Account Management.");
        
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                .body("Password changes are now handled through Keycloak Account Management Console");
    }

    @PostMapping("/password/reset/request")
    @RateLimited(keyType = RateLimited.KeyType.IP, capacity = 3, refillTokens = 3, refillPeriodMinutes = 15)
    public ResponseEntity<String> requestPasswordReset(@Valid @RequestBody PasswordResetInitiationRequest request) {
        log.info("Password reset request received for email: {}", request.getEmail());
        boolean initiated = userService.initiatePasswordReset(request);
        return ResponseEntity.ok(initiated ?
                "Password reset instructions sent to your email" :
                "Failed to initiate password reset");
    }

    @PostMapping("/password/reset")
    @RateLimited(keyType = RateLimited.KeyType.IP, capacity = 5, refillTokens = 5, refillPeriodMinutes = 15)
    public ResponseEntity<String> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        log.info("Password reset with token request received");
        boolean reset = userService.resetPassword(request);
        return ResponseEntity.ok(reset ? "Password reset successfully" : "Password reset failed");
    }

    // Account Settings 2FA Endpoints
    
    @PostMapping("/settings/mfa/assess-requirements")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 20, refillTokens = 20, refillPeriodMinutes = 5)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN') or hasAnyAuthority('SCOPE_PROFILE')")
    public ResponseEntity<AccountSettingsMfaRequirement> assessSettingsRequirements(
            @Valid @RequestBody SettingsChangeContext context) {
        log.info("Assessing MFA requirements for settings change: {}", context.getChangeType());
        
        try {
            UUID userId = keycloakAuthService.getCurrentUserId();
            AccountSettingsMfaRequirement requirement = accountSettingsMfaService
                .determineSettingsMfaRequirement(userId.toString(), context);
            
            if (requirement.isBlocked()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(requirement);
            }
            
            return ResponseEntity.ok(requirement);
        } catch (Exception e) {
            log.error("Error assessing settings MFA requirements: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
    
    @PostMapping("/settings/mfa/initiate")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 10, refillTokens = 10, refillPeriodMinutes = 15)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN') or hasAnyAuthority('SCOPE_PROFILE')")
    public ResponseEntity<AccountSettingsMfaChallenge> initiateSettingsMfa(
            @RequestParam String changeId,
            @Valid @RequestBody AccountSettingsMfaRequirement requirement) {
        log.info("Initiating settings MFA for change: {}", changeId);
        
        try {
            UUID userId = keycloakAuthService.getCurrentUserId();
            AccountSettingsMfaChallenge challenge = accountSettingsMfaService
                .generateSettingsMfaChallenge(userId.toString(), changeId, requirement);
            
            return ResponseEntity.ok(challenge);
        } catch (Exception e) {
            log.error("Error initiating settings MFA: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/settings/mfa/verify")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 5, refillTokens = 5, refillPeriodMinutes = 15)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN') or hasAnyAuthority('SCOPE_PROFILE')")
    public ResponseEntity<AccountSettingsMfaVerificationResult> verifySettingsMfa(
            @RequestParam String challengeId,
            @Valid @RequestBody Map<MfaMethod, String> mfaResponses,
            @RequestParam(required = false) Map<String, Object> additionalData) {
        log.info("Verifying settings MFA for challenge: {}", challengeId);
        
        AccountSettingsMfaVerificationResult result = accountSettingsMfaService
            .verifySettingsMfa(challengeId, mfaResponses, 
                additionalData != null ? additionalData : Map.of());
        
        if (result.isAccountLocked()) {
            return ResponseEntity.status(HttpStatus.LOCKED).body(result);
        }
        
        if (!result.isSuccess()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
        }
        
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/settings/mfa/validate-session")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 50, refillTokens = 50, refillPeriodMinutes = 1)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN') or hasAnyAuthority('SCOPE_PROFILE')")
    public ResponseEntity<Map<String, Object>> validateSettingsSession(
            @RequestParam String sessionToken,
            @RequestParam String changeType) {
        log.debug("Validating settings session for change type: {}", changeType);
        
        try {
            UUID userId = keycloakAuthService.getCurrentUserId();
            boolean valid = accountSettingsMfaService
                .validateSettingsSession(sessionToken, userId.toString(), changeType);
            
            return ResponseEntity.ok(Map.of(
                "valid", valid,
                "message", valid ? "Session is valid" : "Session is invalid or expired"
            ));
        } catch (Exception e) {
            log.error("Error validating settings session: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
    
    @PostMapping("/settings/mfa/invalidate-session")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN') or hasAnyAuthority('SCOPE_PROFILE')")
    public ResponseEntity<Map<String, String>> invalidateSettingsSession(
            @RequestParam String sessionToken) {
        log.info("Invalidating settings session");
        
        accountSettingsMfaService.invalidateSettingsSession(sessionToken);
        return ResponseEntity.ok(Map.of("message", "Session invalidated successfully"));
    }
    
    // Enhanced Profile Update with 2FA
    
    @PutMapping("/profile/secure")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 5, refillTokens = 5, refillPeriodMinutes = 30)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN') or hasAnyAuthority('SCOPE_PROFILE')")
    public ResponseEntity<UserResponse> updateProfileSecure(
            @Valid @RequestBody UpdateProfileRequest request,
            @RequestParam String sessionToken) {
        log.info("Secure profile update request received");
        
        try {
            UUID userId = keycloakAuthService.getCurrentUserId();
            
            // Validate 2FA session for profile changes
            if (!accountSettingsMfaService.validateSettingsSession(sessionToken, 
                    userId.toString(), "PROFILE_INFORMATION")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            
            UserResponse updatedUser = userService.updateProfile(userId, request);
            
            // Invalidate session after successful update
            accountSettingsMfaService.invalidateSettingsSession(sessionToken);
            
            return ResponseEntity.ok(updatedUser);
        } catch (Exception e) {
            log.error("Error updating profile securely: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
    
    @PostMapping("/settings/email/change-request")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 3, refillTokens = 3, refillPeriodMinutes = 60)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN') or hasAnyAuthority('SCOPE_PROFILE')")
    public ResponseEntity<Map<String, String>> requestEmailChange(
            @RequestParam String newEmail,
            @RequestParam String sessionToken) {
        log.info("Email change request received");
        
        try {
            UUID userId = keycloakAuthService.getCurrentUserId();
            
            // Validate 2FA session for email change
            if (!accountSettingsMfaService.validateSettingsSession(sessionToken, 
                    userId.toString(), "EMAIL_CHANGE")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            
            // Process email change request
            boolean initiated = userService.initiateEmailChange(userId, newEmail);
            
            // Invalidate session after successful initiation
            accountSettingsMfaService.invalidateSettingsSession(sessionToken);
            
            return ResponseEntity.ok(Map.of(
                "message", initiated ? "Email change initiated - check your new email for verification" 
                    : "Failed to initiate email change"
            ));
        } catch (Exception e) {
            log.error("Error requesting email change: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
    
    @PostMapping("/settings/phone/change-request")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 3, refillTokens = 3, refillPeriodMinutes = 60)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN') or hasAnyAuthority('SCOPE_PROFILE')")
    public ResponseEntity<Map<String, String>> requestPhoneChange(
            @RequestParam String newPhone,
            @RequestParam String sessionToken) {
        log.info("Phone change request received");
        
        try {
            UUID userId = keycloakAuthService.getCurrentUserId();
            
            // Validate 2FA session for phone change
            if (!accountSettingsMfaService.validateSettingsSession(sessionToken, 
                    userId.toString(), "PHONE_CHANGE")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            
            // Process phone change request
            boolean initiated = userService.initiatePhoneChange(userId, newPhone);
            
            // Invalidate session after successful initiation
            accountSettingsMfaService.invalidateSettingsSession(sessionToken);
            
            return ResponseEntity.ok(Map.of(
                "message", initiated ? "Phone change initiated - check your new phone for verification" 
                    : "Failed to initiate phone change"
            ));
        } catch (Exception e) {
            log.error("Error requesting phone change: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
    
    @PutMapping("/settings/2fa/configure")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 5, refillTokens = 5, refillPeriodMinutes = 30)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN') or hasAnyAuthority('SCOPE_PROFILE')")
    public ResponseEntity<Map<String, String>> configure2FA(
            @Valid @RequestBody Configure2FARequest request,
            @RequestParam String sessionToken) {
        log.info("2FA configuration request received");
        
        try {
            UUID userId = keycloakAuthService.getCurrentUserId();
            
            // Validate 2FA session for 2FA settings change
            if (!accountSettingsMfaService.validateSettingsSession(sessionToken, 
                    userId.toString(), "TWO_FACTOR_SETTINGS")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            
            // Process 2FA configuration
            boolean configured = userService.configure2FA(userId, request);
            
            // Invalidate session after successful configuration
            accountSettingsMfaService.invalidateSettingsSession(sessionToken);
            
            return ResponseEntity.ok(Map.of(
                "message", configured ? "2FA configuration updated successfully" 
                    : "Failed to update 2FA configuration"
            ));
        } catch (Exception e) {
            log.error("Error configuring 2FA: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
    
    @PutMapping("/settings/notifications")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 10, refillTokens = 10, refillPeriodMinutes = 5)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN') or hasAnyAuthority('SCOPE_PROFILE')")
    public ResponseEntity<Map<String, String>> updateNotificationSettings(
            @Valid @RequestBody NotificationSettingsRequest request,
            @RequestParam(required = false) String sessionToken) {
        log.info("Notification settings update request received");
        
        try {
            UUID userId = keycloakAuthService.getCurrentUserId();
            
            // For low-risk changes like notification preferences, session validation is optional
            if (sessionToken != null && !accountSettingsMfaService.validateSettingsSession(sessionToken, 
                    userId.toString(), "NOTIFICATION_PREFERENCES")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            
            // Process notification settings update
            boolean updated = userService.updateNotificationSettings(userId, request);
            
            // Invalidate session if provided
            if (sessionToken != null) {
                accountSettingsMfaService.invalidateSettingsSession(sessionToken);
            }
            
            return ResponseEntity.ok(Map.of(
                "message", updated ? "Notification settings updated successfully" 
                    : "Failed to update notification settings"
            ));
        } catch (Exception e) {
            log.error("Error updating notification settings: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PutMapping("/settings/privacy")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 10, refillTokens = 10, refillPeriodMinutes = 5)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN') or hasAnyAuthority('SCOPE_PROFILE')")
    public ResponseEntity<Map<String, String>> updatePrivacySettings(
            @Valid @RequestBody PrivacySettingsRequest request,
            @RequestParam(required = false) String sessionToken) {
        log.info("Privacy settings update request received");
        
        try {
            UUID userId = keycloakAuthService.getCurrentUserId();
            
            // For medium-risk changes, session validation may be required
            if (sessionToken != null && !accountSettingsMfaService.validateSettingsSession(sessionToken, 
                    userId.toString(), "PRIVACY_SETTINGS")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            
            // Process privacy settings update
            boolean updated = userService.updatePrivacySettings(userId, request);
            
            // Invalidate session if provided
            if (sessionToken != null) {
                accountSettingsMfaService.invalidateSettingsSession(sessionToken);
            }
            
            return ResponseEntity.ok(Map.of(
                "message", updated ? "Privacy settings updated successfully" 
                    : "Failed to update privacy settings"
            ));
        } catch (Exception e) {
            log.error("Error updating privacy settings: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}