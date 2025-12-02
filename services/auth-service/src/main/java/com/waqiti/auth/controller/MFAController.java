package com.waqiti.auth.controller;

import com.waqiti.auth.dto.MFASetupResponse;
import com.waqiti.auth.dto.TOTPVerificationRequest;
import com.waqiti.auth.service.TOTPService;
import com.waqiti.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth/mfa")
@RequiredArgsConstructor
@Tag(name = "MFA", description = "Multi-Factor Authentication API")
@SecurityRequirement(name = "bearer-jwt")
public class MFAController {

    private final TOTPService totpService;

    @PostMapping("/setup")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Setup TOTP MFA", description = "Generate TOTP secret and QR code for user")
    public ResponseEntity<ApiResponse<MFASetupResponse>> setupMFA(
            Authentication authentication,
            @RequestParam String email) {

        UUID userId = UUID.fromString(authentication.getName());
        String issuer = "Waqiti";

        MFASetupResponse response = totpService.generateSecret(userId, issuer, email);
        MFASetupResponse enriched = MFASetupResponse.withInstructions(response);

        return ResponseEntity.ok(ApiResponse.success(enriched));
    }

    @PostMapping("/verify")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Verify TOTP code", description = "Verify 6-digit TOTP code")
    public ResponseEntity<ApiResponse<Boolean>> verifyMFA(
            @Valid @RequestBody TOTPVerificationRequest request) {

        boolean isValid = totpService.verifyCode(request);

        if (isValid && request.getTrustDevice() != null && request.getTrustDevice()) {
            // Logic to trust device would go here
        }

        return ResponseEntity.ok(ApiResponse.success(isValid));
    }

    @PostMapping("/verify-backup")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Verify backup code", description = "Verify single-use backup code")
    public ResponseEntity<ApiResponse<Boolean>> verifyBackupCode(
            Authentication authentication,
            @RequestParam String backupCode) {

        UUID userId = UUID.fromString(authentication.getName());
        boolean isValid = totpService.verifyBackupCode(userId, backupCode);

        return ResponseEntity.ok(ApiResponse.success(isValid));
    }

    @PostMapping("/rotate")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Rotate MFA secret", description = "Generate new TOTP secret (security best practice)")
    public ResponseEntity<ApiResponse<MFASetupResponse>> rotateMFA(
            Authentication authentication,
            @RequestParam String email) {

        UUID userId = UUID.fromString(authentication.getName());
        MFASetupResponse response = totpService.rotateSecret(userId, "Waqiti", email);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/disable")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Disable MFA", description = "Disable MFA for user account")
    public ResponseEntity<ApiResponse<Void>> disableMFA(Authentication authentication) {

        UUID userId = UUID.fromString(authentication.getName());
        totpService.disableMFA(userId);

        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
