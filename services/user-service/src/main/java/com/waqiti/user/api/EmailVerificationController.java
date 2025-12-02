package com.waqiti.user.api;

import com.waqiti.user.service.EmailVerificationService;
import com.waqiti.user.service.EmailVerificationService.EmailVerificationResult;
import com.waqiti.user.service.EmailVerificationService.EmailVerificationStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users/email-verification")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Email Verification", description = "Email verification management endpoints")
public class EmailVerificationController {
    
    private final EmailVerificationService emailVerificationService;
    
    @PostMapping("/send")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Send email verification", description = "Send verification email to user")
    public ResponseEntity<EmailVerificationResult> sendVerificationEmail(Authentication authentication) {
        String userId = authentication.getName();
        EmailVerificationResult result = emailVerificationService.sendVerificationEmail(userId);
        
        if (result.isSuccess()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }
    
    @GetMapping("/verify")
    @Operation(summary = "Verify email with token", description = "Verify email using token from link")
    public ResponseEntity<EmailVerificationResult> verifyEmailWithToken(
            @RequestParam("token") @NotBlank String token) {
        
        log.info("Email verification attempt with token");
        EmailVerificationResult result = emailVerificationService.verifyEmailWithToken(token);
        
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/verify-code")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Verify email with code", description = "Verify email using numeric code")
    public ResponseEntity<EmailVerificationResult> verifyEmailWithCode(
            Authentication authentication,
            @Valid @RequestBody VerifyCodeRequest request) {
        
        String userId = authentication.getName();
        log.info("Email verification attempt with code for user: {}", userId);
        
        EmailVerificationResult result = emailVerificationService.verifyEmailWithCode(
                userId, request.getCode());
        
        if (result.isSuccess()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }
    
    @PostMapping("/resend")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Resend verification email", description = "Resend verification email to user")
    public ResponseEntity<Map<String, Object>> resendVerificationEmail(Authentication authentication) {
        String userId = authentication.getName();
        log.info("Resending verification email for user: {}", userId);
        
        boolean sent = emailVerificationService.resendVerificationEmail(userId);
        
        if (sent) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Verification email sent successfully"
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to send verification email"
            ));
        }
    }
    
    @GetMapping("/status")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get verification status", description = "Get email verification status for user")
    public ResponseEntity<EmailVerificationStatus> getVerificationStatus(Authentication authentication) {
        String userId = authentication.getName();
        EmailVerificationStatus status = emailVerificationService.getVerificationStatus(userId);
        
        return ResponseEntity.ok(status);
    }
    
    @GetMapping("/check/{userId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SYSTEM')")
    @Operation(summary = "Check if email verified", description = "Check if user's email is verified (admin only)")
    public ResponseEntity<Map<String, Boolean>> isEmailVerified(@PathVariable String userId) {
        boolean verified = emailVerificationService.isEmailVerified(userId);
        
        return ResponseEntity.ok(Map.of("verified", verified));
    }
    
    @lombok.Data
    public static class VerifyCodeRequest {
        @NotBlank(message = "Verification code is required")
        @Pattern(regexp = "^[0-9]{6,8}$", message = "Verification code must be 6-8 digits")
        private String code;
    }
}