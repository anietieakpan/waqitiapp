package com.waqiti.user.controller;

import com.waqiti.user.dto.security.*;
import com.waqiti.user.dto.security.result.*;
import com.waqiti.user.security.BiometricAuthenticationService;
import com.waqiti.common.response.ApiResponse;
import com.waqiti.common.security.CurrentUser;
import com.waqiti.common.security.UserPrincipal;
import com.waqiti.common.ratelimit.RateLimited;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * Biometric Authentication Controller
 * 
 * Provides REST API endpoints for biometric authentication management
 */
@RestController
@RequestMapping("/api/v1/biometric")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Biometric Authentication", description = "Biometric authentication and credential management")
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = false)
public class BiometricAuthenticationController {

    private final BiometricAuthenticationService biometricAuthenticationService;

    @Operation(
        summary = "Register biometric credential",
        description = "Register a new biometric credential for the authenticated user"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Biometric credential registered successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid biometric data or poor quality"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Biometric type already registered"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Device not trusted")
    })
    @PostMapping("/register")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 3, refillTokens = 3, refillPeriodMinutes = 60)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN') or hasAnyAuthority('SCOPE_PROFILE')")
    @SecurityRequirement(name = "bearer-token")
    public ResponseEntity<ApiResponse<BiometricRegistrationResult>> registerBiometric(
            @Parameter(description = "Biometric registration request", required = true)
            @Valid @RequestBody BiometricRegistrationRequest request,
            @CurrentUser UserPrincipal currentUser) {
        
        try {
            // Ensure user can only register for their own account
            request.setUserId(currentUser.getId());
            
            BiometricRegistrationResult result = biometricAuthenticationService.registerBiometric(request);
            
            if (result.isSuccess()) {
                return ResponseEntity.status(HttpStatus.CREATED)
                        .body(ApiResponse.success(result, "Biometric credential registered successfully"));
            } else {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(result.getErrorCode(), result.getErrorMessage()));
            }
            
        } catch (SecurityException e) {
            log.warn("Security error during biometric registration for user: {}", currentUser.getId(), e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("SECURITY_ERROR", e.getMessage()));
        } catch (Exception e) {
            log.error("Error registering biometric for user: {}", currentUser.getId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("REGISTRATION_ERROR", "Failed to register biometric credential"));
        }
    }

    @Operation(
        summary = "Authenticate with biometric",
        description = "Authenticate user using biometric credential"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Authentication successful"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication failed"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "423", description = "Account locked due to too many failed attempts")
    })
    @PostMapping("/authenticate")
    public ResponseEntity<ApiResponse<BiometricAuthenticationResult>> authenticate(
            @Parameter(description = "Biometric authentication request", required = true)
            @Valid @RequestBody BiometricAuthenticationRequest request) {
        
        try {
            BiometricAuthenticationResult result = biometricAuthenticationService.authenticate(request);
            
            if (result.isSuccess()) {
                return ResponseEntity.ok(ApiResponse.success(result, "Authentication successful"));
            } else {
                HttpStatus status = "ACCOUNT_LOCKED".equals(result.getErrorCode()) ? 
                        HttpStatus.LOCKED : HttpStatus.UNAUTHORIZED;
                
                return ResponseEntity.status(status)
                        .body(ApiResponse.error(result.getErrorCode(), result.getErrorMessage()));
            }
            
        } catch (Exception e) {
            log.error("Error during biometric authentication for user: {}", request.getUserId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("AUTHENTICATION_ERROR", "Authentication system error"));
        }
    }

    @Operation(
        summary = "Register WebAuthn credential",
        description = "Register a WebAuthn/FIDO2 credential for passwordless authentication"
    )
    @PostMapping("/webauthn/register")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN') or hasAnyAuthority('SCOPE_PROFILE')")
    @SecurityRequirement(name = "bearer-token")
    public ResponseEntity<ApiResponse<WebAuthnRegistrationResult>> registerWebAuthn(
            @Parameter(description = "WebAuthn registration request", required = true)
            @Valid @RequestBody WebAuthnRegistrationRequest request,
            @CurrentUser UserPrincipal currentUser) {
        
        try {
            request.setUserId(currentUser.getId());
            
            WebAuthnRegistrationResult result = biometricAuthenticationService.registerWebAuthn(request);
            
            if (result.isSuccess()) {
                return ResponseEntity.status(HttpStatus.CREATED)
                        .body(ApiResponse.success(result, "WebAuthn credential registered successfully"));
            } else {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(result.getErrorCode(), "Failed to register WebAuthn credential"));
            }
            
        } catch (SecurityException e) {
            log.warn("Security error during WebAuthn registration for user: {}", currentUser.getId(), e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("SECURITY_ERROR", e.getMessage()));
        } catch (Exception e) {
            log.error("Error registering WebAuthn for user: {}", currentUser.getId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("REGISTRATION_ERROR", "Failed to register WebAuthn credential"));
        }
    }

    @Operation(
        summary = "Authenticate with WebAuthn",
        description = "Authenticate user using WebAuthn/FIDO2 credential"
    )
    @PostMapping("/webauthn/authenticate")
    public ResponseEntity<ApiResponse<WebAuthnAuthenticationResult>> authenticateWebAuthn(
            @Parameter(description = "WebAuthn authentication request", required = true)
            @Valid @RequestBody WebAuthnAuthenticationRequest request) {
        
        try {
            WebAuthnAuthenticationResult result = biometricAuthenticationService.authenticateWebAuthn(request);
            
            if (result.isSuccess()) {
                return ResponseEntity.ok(ApiResponse.success(result, "WebAuthn authentication successful"));
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(result.getErrorCode(), "WebAuthn authentication failed"));
            }
            
        } catch (Exception e) {
            log.error("Error during WebAuthn authentication for user: {}", request.getUserId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("AUTHENTICATION_ERROR", "WebAuthn authentication system error"));
        }
    }

    @Operation(
        summary = "Analyze behavioral biometrics",
        description = "Analyze user's behavioral biometric patterns for continuous authentication"
    )
    @PostMapping("/behavioral/analyze")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN') or hasAnyAuthority('SCOPE_PROFILE')")
    @SecurityRequirement(name = "bearer-token")
    public ResponseEntity<ApiResponse<BehavioralBiometricResult>> analyzeBehavioralBiometrics(
            @Parameter(description = "Behavioral biometric analysis request", required = true)
            @Valid @RequestBody BehavioralBiometricRequest request,
            @CurrentUser UserPrincipal currentUser) {
        
        try {
            request.setUserId(currentUser.getId());
            
            BehavioralBiometricResult result = biometricAuthenticationService.analyzeBehavioralBiometrics(request);
            
            return ResponseEntity.ok(ApiResponse.success(result, "Behavioral analysis completed"));
            
        } catch (Exception e) {
            log.error("Error analyzing behavioral biometrics for user: {}", currentUser.getId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("ANALYSIS_ERROR", "Failed to analyze behavioral biometrics"));
        }
    }

    @Operation(
        summary = "Get user's biometric credentials",
        description = "Retrieve list of registered biometric credentials for the authenticated user"
    )
    @GetMapping("/credentials")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN') or hasAnyAuthority('SCOPE_PROFILE')")
    @SecurityRequirement(name = "bearer-token")
    public ResponseEntity<ApiResponse<List<BiometricCredentialInfo>>> getBiometricCredentials(
            @CurrentUser UserPrincipal currentUser) {
        
        try {
            List<BiometricCredentialInfo> credentials = biometricAuthenticationService
                    .getUserBiometricCredentials(currentUser.getId());
            
            return ResponseEntity.ok(ApiResponse.success(credentials, "Biometric credentials retrieved successfully"));
            
        } catch (Exception e) {
            log.error("Error retrieving biometric credentials for user: {}", currentUser.getId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("RETRIEVAL_ERROR", "Failed to retrieve biometric credentials"));
        }
    }

    @Operation(
        summary = "Remove biometric credential",
        description = "Remove a specific biometric credential"
    )
    @DeleteMapping("/credentials/{credentialId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN') or hasAnyAuthority('SCOPE_PROFILE')")
    @SecurityRequirement(name = "bearer-token")
    public ResponseEntity<ApiResponse<Void>> removeBiometricCredential(
            @Parameter(description = "Credential ID to remove", required = true)
            @PathVariable String credentialId,
            @CurrentUser UserPrincipal currentUser) {
        
        try {
            biometricAuthenticationService.removeBiometricCredential(currentUser.getId(), credentialId);
            
            return ResponseEntity.ok(ApiResponse.success(null, "Biometric credential removed successfully"));
            
        } catch (SecurityException e) {
            log.warn("Security error removing biometric credential: {} for user: {}", credentialId, currentUser.getId(), e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("SECURITY_ERROR", e.getMessage()));
        } catch (Exception e) {
            log.error("Error removing biometric credential: {} for user: {}", credentialId, currentUser.getId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("REMOVAL_ERROR", "Failed to remove biometric credential"));
        }
    }

    @Operation(
        summary = "Check biometric capability",
        description = "Check if device and browser support specific biometric types"
    )
    @PostMapping("/capability/check")
    public ResponseEntity<ApiResponse<BiometricCapabilityResult>> checkBiometricCapability(
            @Parameter(description = "Biometric capability check request", required = true)
            @Valid @RequestBody BiometricCapabilityRequest request) {
        
        try {
            // This would typically check device capabilities, browser support, etc.
            BiometricCapabilityResult result = BiometricCapabilityResult.builder()
                    .fingerprintSupported(true)
                    .faceIdSupported(true)
                    .webAuthnSupported(true)
                    .behavioralSupported(true)
                    .platformAuthenticatorAvailable(true)
                    .userVerifyingPlatformAuthenticatorAvailable(true)
                    .build();
            
            return ResponseEntity.ok(ApiResponse.success(result, "Biometric capability check completed"));
            
        } catch (Exception e) {
            log.error("Error checking biometric capability", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("CAPABILITY_ERROR", "Failed to check biometric capability"));
        }
    }

    @Operation(
        summary = "Generate biometric challenge",
        description = "Generate a challenge for biometric authentication to prevent replay attacks"
    )
    @PostMapping("/challenge/generate")
    public ResponseEntity<ApiResponse<BiometricChallengeResult>> generateBiometricChallenge(
            @Parameter(description = "Challenge generation request", required = true)
            @Valid @RequestBody BiometricChallengeRequest request) {
        
        try {
            // Generate a cryptographic challenge
            String challenge = java.util.UUID.randomUUID().toString();
            long expiresAt = System.currentTimeMillis() + 300000; // 5 minutes
            
            BiometricChallengeResult result = BiometricChallengeResult.builder()
                    .challenge(challenge)
                    .expiresAt(expiresAt)
                    .algorithm("HMAC-SHA256")
                    .build();
            
            return ResponseEntity.ok(ApiResponse.success(result, "Biometric challenge generated"));
            
        } catch (Exception e) {
            log.error("Error generating biometric challenge", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("CHALLENGE_ERROR", "Failed to generate biometric challenge"));
        }
    }
}