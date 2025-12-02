package com.waqiti.security.api;

import com.waqiti.security.service.EnhancedMultiFactorAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/security/mfa")
@RequiredArgsConstructor
@Slf4j
public class MultiFactorAuthController {

    private final EnhancedMultiFactorAuthService mfaService;

    @PostMapping("/setup")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<MfaSetupResponse> setupMfa(@RequestBody @Valid MfaSetupRequest request) {
        log.info("Setting up MFA for user: {}, method: {}", request.getUserId(), request.getMethod());
        
        MfaSetupResponse response = mfaService.setupMfa(
            request.getUserId(),
            request.getMethod(),
            request.getPhoneNumber(),
            request.getEmail()
        );
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/enable")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, Object>> enableMfa(@RequestBody @Valid MfaEnableRequest request) {
        log.info("Enabling MFA for user: {}", request.getUserId());
        
        boolean success = mfaService.enableMfa(
            request.getUserId(),
            request.getVerificationCode(),
            request.getBackupCodes()
        );
        
        if (success) {
            return ResponseEntity.ok(Map.of(
                "status", "enabled",
                "message", "Multi-factor authentication has been enabled",
                "timestamp", System.currentTimeMillis()
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "failed",
                "message", "Failed to enable MFA. Please verify your code and try again."
            ));
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<MfaVerificationResponse> verifyMfa(@RequestBody @Valid MfaVerificationRequest request) {
        log.info("Verifying MFA for user: {}", request.getUserId());
        
        MfaVerificationResponse response = mfaService.verifyMfa(
            request.getUserId(),
            request.getCode(),
            request.getMethod()
        );
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/send-code")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, Object>> sendCode(@RequestBody @Valid SendCodeRequest request) {
        log.info("Sending MFA code to user: {}, method: {}", request.getUserId(), request.getMethod());
        
        String codeId = mfaService.sendCode(
            request.getUserId(),
            request.getMethod()
        );
        
        return ResponseEntity.ok(Map.of(
            "codeId", codeId,
            "method", request.getMethod(),
            "message", "Verification code sent successfully",
            "expiresIn", 300 // 5 minutes
        ));
    }

    @PostMapping("/backup-codes/generate")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<BackupCodesResponse> generateBackupCodes(@RequestBody @Valid GenerateBackupCodesRequest request) {
        log.info("Generating backup codes for user: {}", request.getUserId());
        
        List<String> backupCodes = mfaService.generateBackupCodes(
            request.getUserId(),
            request.getCount()
        );
        
        return ResponseEntity.ok(BackupCodesResponse.builder()
            .backupCodes(backupCodes)
            .generatedAt(System.currentTimeMillis())
            .warning("Store these codes in a safe place. Each code can only be used once.")
            .build());
    }

    @PostMapping("/backup-codes/verify")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, Object>> verifyBackupCode(@RequestBody @Valid VerifyBackupCodeRequest request) {
        log.info("Verifying backup code for user: {}", request.getUserId());
        
        boolean valid = mfaService.verifyBackupCode(
            request.getUserId(),
            request.getBackupCode()
        );
        
        if (valid) {
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Backup code verified and consumed"
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "failed",
                "message", "Invalid or already used backup code"
            ));
        }
    }

    @PostMapping("/disable")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, Object>> disableMfa(@RequestBody @Valid DisableMfaRequest request) {
        log.info("Disabling MFA for user: {}", request.getUserId());
        
        boolean success = mfaService.disableMfa(
            request.getUserId(),
            request.getPassword(),
            request.getVerificationCode()
        );
        
        if (success) {
            return ResponseEntity.ok(Map.of(
                "status", "disabled",
                "message", "Multi-factor authentication has been disabled"
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "failed",
                "message", "Failed to disable MFA. Please verify your credentials."
            ));
        }
    }

    @GetMapping("/status/{userId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<MfaStatusResponse> getMfaStatus(@PathVariable String userId) {
        log.info("Getting MFA status for user: {}", userId);
        
        MfaStatusResponse status = mfaService.getMfaStatus(userId);
        return ResponseEntity.ok(status);
    }

    @PostMapping("/trust-device")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, Object>> trustDevice(@RequestBody @Valid TrustDeviceRequest request) {
        log.info("Trusting device for user: {}", request.getUserId());
        
        String trustToken = mfaService.trustDevice(
            request.getUserId(),
            request.getDeviceId(),
            request.getDeviceName(),
            request.getTrustDuration()
        );
        
        return ResponseEntity.ok(Map.of(
            "trustToken", trustToken,
            "deviceId", request.getDeviceId(),
            "expiresIn", request.getTrustDuration()
        ));
    }

    @GetMapping("/trusted-devices/{userId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<TrustedDevice>> getTrustedDevices(@PathVariable String userId) {
        log.info("Getting trusted devices for user: {}", userId);
        
        List<TrustedDevice> devices = mfaService.getTrustedDevices(userId);
        return ResponseEntity.ok(devices);
    }

    @DeleteMapping("/trusted-devices/{userId}/{deviceId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, Object>> revokeTrustedDevice(
            @PathVariable String userId,
            @PathVariable String deviceId) {
        log.info("Revoking trusted device: {} for user: {}", deviceId, userId);
        
        mfaService.revokeTrustedDevice(userId, deviceId);
        
        return ResponseEntity.ok(Map.of(
            "status", "revoked",
            "deviceId", deviceId,
            "message", "Device trust has been revoked"
        ));
    }
}

@lombok.Data
@lombok.Builder
class MfaSetupRequest {
    private String userId;
    private String method; // TOTP, SMS, EMAIL, PUSH
    private String phoneNumber;
    private String email;
}

@lombok.Data
@lombok.Builder
class MfaSetupResponse {
    private String setupId;
    private String method;
    private String qrCode; // For TOTP
    private String secret; // For TOTP
    private String phoneNumber; // For SMS
    private String email; // For EMAIL
    private List<String> backupCodes;
}

@lombok.Data
@lombok.Builder
class MfaEnableRequest {
    private String userId;
    private String verificationCode;
    private boolean backupCodes;
}

@lombok.Data
@lombok.Builder
class MfaVerificationRequest {
    private String userId;
    private String code;
    private String method;
}

@lombok.Data
@lombok.Builder
class MfaVerificationResponse {
    private boolean verified;
    private String sessionToken;
    private long expiresIn;
    private boolean requiresAdditionalAuth;
}

@lombok.Data
@lombok.Builder
class SendCodeRequest {
    private String userId;
    private String method; // SMS, EMAIL
}

@lombok.Data
@lombok.Builder
class GenerateBackupCodesRequest {
    private String userId;
    private int count = 10;
}

@lombok.Data
@lombok.Builder
class BackupCodesResponse {
    private List<String> backupCodes;
    private long generatedAt;
    private String warning;
}

@lombok.Data
@lombok.Builder
class VerifyBackupCodeRequest {
    private String userId;
    private String backupCode;
}

@lombok.Data
@lombok.Builder
class DisableMfaRequest {
    private String userId;
    private String password;
    private String verificationCode;
}

@lombok.Data
@lombok.Builder
class MfaStatusResponse {
    private String userId;
    private boolean enabled;
    private List<String> enabledMethods;
    private String primaryMethod;
    private int backupCodesRemaining;
    private List<TrustedDevice> trustedDevices;
}

@lombok.Data
@lombok.Builder
class TrustDeviceRequest {
    private String userId;
    private String deviceId;
    private String deviceName;
    private long trustDuration; // in seconds
}

@lombok.Data
@lombok.Builder
class TrustedDevice {
    private String deviceId;
    private String deviceName;
    private String deviceType;
    private String lastUsed;
    private String trustedSince;
    private String expiresAt;
}