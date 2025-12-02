package com.waqiti.security.controller;

import com.waqiti.security.dto.*;
import com.waqiti.security.service.TransactionSigningService;
import com.waqiti.security.service.HardwareKeyService;
import com.waqiti.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/security/signing")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Transaction Signing", description = "Transaction signing and verification endpoints")
@SecurityRequirement(name = "bearerAuth")
public class TransactionSigningController {

    private final TransactionSigningService signingService;
    private final HardwareKeyService hardwareKeyService;

    @PostMapping("/sign")
    @Operation(summary = "Sign a transaction", description = "Sign a transaction using specified method")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<TransactionSignatureDTO>> signTransaction(
            @Valid @RequestBody SignTransactionRequest request,
            Principal principal) {
        
        log.info("Signing transaction {} for user {}", request.getTransactionId(), principal.getName());
        
        // Set user ID from authenticated principal
        request.setUserId(principal.getName());
        
        TransactionSignatureDTO signature = signingService.signTransaction(request);
        
        return ResponseEntity.ok(
            ApiResponse.<TransactionSignatureDTO>builder()
                .success(true)
                .data(signature)
                .message("Transaction signed successfully")
                .build()
        );
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify transaction signature", description = "Verify a transaction signature")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<SignatureVerificationResult>> verifySignature(
            @RequestParam @NotBlank String transactionId,
            @RequestParam @NotBlank String signature) {
        
        log.info("Verifying signature for transaction {}", transactionId);
        
        SignatureVerificationResult result = signingService.verifySignature(transactionId, signature);
        
        return ResponseEntity.ok(
            ApiResponse.<SignatureVerificationResult>builder()
                .success(true)
                .data(result)
                .message(result.isValid() ? "Signature is valid" : "Signature is invalid")
                .build()
        );
    }

    @PostMapping("/keys/generate")
    @Operation(summary = "Generate signing key", description = "Generate a new signing key pair")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<SigningKeyDTO>> generateSigningKey(
            @RequestParam SigningMethod method,
            Principal principal) {
        
        log.info("Generating {} signing key for user {}", method, principal.getName());
        
        SigningKeyDTO key = signingService.generateSigningKey(principal.getName(), method);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse.<SigningKeyDTO>builder()
                .success(true)
                .data(key)
                .message("Signing key generated successfully")
                .build()
        );
    }

    @GetMapping("/keys")
    @Operation(summary = "List signing keys", description = "Get user's signing keys")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<List<SigningKeyDTO>>> getUserSigningKeys(Principal principal) {
        log.info("Fetching signing keys for user {}", principal.getName());
        
        List<SigningKeyDTO> keys = signingService.getUserSigningKeys(principal.getName());
        
        return ResponseEntity.ok(
            ApiResponse.<List<SigningKeyDTO>>builder()
                .success(true)
                .data(keys)
                .message("Retrieved " + keys.size() + " signing keys")
                .build()
        );
    }

    @DeleteMapping("/keys/{keyId}")
    @Operation(summary = "Revoke signing key", description = "Revoke a signing key")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Void>> revokeSigningKey(
            @PathVariable String keyId,
            @RequestParam String reason,
            Principal principal) {
        
        log.info("Revoking signing key {} for user {}", keyId, principal.getName());
        
        signingService.revokeSigningKey(principal.getName(), keyId, reason);
        
        return ResponseEntity.ok(
            ApiResponse.<Void>builder()
                .success(true)
                .message("Signing key revoked successfully")
                .build()
        );
    }

    // Hardware Key Endpoints

    @GetMapping("/hardware/devices")
    @Operation(summary = "List available hardware devices", description = "Get list of connected hardware devices")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<List<HardwareDeviceInfo>>> listHardwareDevices() {
        log.info("Listing available hardware devices");
        
        List<HardwareDeviceInfo> devices = hardwareKeyService.listAvailableDevices();
        
        return ResponseEntity.ok(
            ApiResponse.<List<HardwareDeviceInfo>>builder()
                .success(true)
                .data(devices)
                .message("Found " + devices.size() + " hardware devices")
                .build()
        );
    }

    @PostMapping("/hardware/challenge")
    @Operation(summary = "Generate device challenge", description = "Generate challenge for hardware device registration")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<DeviceChallengeDTO>> generateDeviceChallenge(Principal principal) {
        log.info("Generating device challenge for user {}", principal.getName());
        
        DeviceChallengeDTO challenge = hardwareKeyService.generateChallenge(principal.getName());
        
        return ResponseEntity.ok(
            ApiResponse.<DeviceChallengeDTO>builder()
                .success(true)
                .data(challenge)
                .message("Device challenge generated")
                .build()
        );
    }

    @PostMapping("/hardware/register")
    @Operation(summary = "Register hardware key", description = "Register a hardware security key")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<HardwareKeyDTO>> registerHardwareKey(
            @Valid @RequestBody RegisterHardwareKeyRequest request,
            Principal principal) {
        
        log.info("Registering hardware key for user {}", principal.getName());
        
        request.setUserId(principal.getName());
        HardwareKeyDTO hardwareKey = signingService.registerHardwareKey(request);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse.<HardwareKeyDTO>builder()
                .success(true)
                .data(hardwareKey)
                .message("Hardware key registered successfully")
                .build()
        );
    }

    @PostMapping("/hardware/{deviceId}/test")
    @Operation(summary = "Test hardware device", description = "Test hardware device functionality")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<HardwareTestResult>> testHardwareDevice(
            @PathVariable String deviceId,
            @RequestParam(required = false) String pin) {
        
        log.info("Testing hardware device {}", deviceId);
        
        HardwareTestResult result = hardwareKeyService.testDevice(deviceId, pin);
        
        return ResponseEntity.ok(
            ApiResponse.<HardwareTestResult>builder()
                .success(true)
                .data(result)
                .message(result.isSuccess() ? "Device test successful" : "Device test failed")
                .build()
        );
    }

    @PostMapping("/hardware/{deviceId}/attestation")
    @Operation(summary = "Perform device attestation", description = "Verify hardware device authenticity")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<DeviceAttestationResult>> performAttestation(
            @PathVariable String deviceId) {
        
        log.info("Performing attestation for device {}", deviceId);
        
        DeviceAttestationResult result = hardwareKeyService.performAttestation(deviceId);
        
        return ResponseEntity.ok(
            ApiResponse.<DeviceAttestationResult>builder()
                .success(true)
                .data(result)
                .message(result.isValid() ? "Device attestation valid" : "Device attestation failed")
                .build()
        );
    }

    @PutMapping("/hardware/{deviceId}/secure-element")
    @Operation(summary = "Enable secure element", description = "Enable secure element features on hardware device")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<SecureElementStatus>> enableSecureElement(
            @PathVariable String deviceId,
            @Valid @RequestBody SecureElementConfig config) {
        
        log.info("Enabling secure element for device {}", deviceId);
        
        SecureElementStatus status = hardwareKeyService.enableSecureElement(deviceId, config);
        
        return ResponseEntity.ok(
            ApiResponse.<SecureElementStatus>builder()
                .success(true)
                .data(status)
                .message("Secure element enabled successfully")
                .build()
        );
    }

    // Multi-signature Endpoints

    @PostMapping("/multisig/configure")
    @Operation(summary = "Configure multi-signature", description = "Configure multi-signature requirements")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<MultiSignatureConfigDTO>> configureMultiSignature(
            @Valid @RequestBody ConfigureMultiSignatureRequest request,
            Principal principal) {
        
        log.info("Configuring multi-signature for user {}", principal.getName());
        
        request.setUserId(principal.getName());
        MultiSignatureConfigDTO config = signingService.configureMultiSignature(request);
        
        return ResponseEntity.ok(
            ApiResponse.<MultiSignatureConfigDTO>builder()
                .success(true)
                .data(config)
                .message("Multi-signature configured successfully")
                .build()
        );
    }

    @PostMapping("/multisig/{transactionId}/sign")
    @Operation(summary = "Add signature to multi-sig transaction", description = "Add a signature to a multi-signature transaction")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<MultiSignatureStatus>> addMultiSignature(
            @PathVariable String transactionId,
            @Valid @RequestBody AddMultiSignatureRequest request,
            Principal principal) {
        
        log.info("Adding signature to multi-sig transaction {} by user {}", transactionId, principal.getName());
        
        request.setSignerId(principal.getName());
        MultiSignatureStatus status = signingService.addMultiSignature(transactionId, request);
        
        return ResponseEntity.ok(
            ApiResponse.<MultiSignatureStatus>builder()
                .success(true)
                .data(status)
                .message("Signature added successfully")
                .build()
        );
    }

    @GetMapping("/multisig/{transactionId}/status")
    @Operation(summary = "Get multi-signature status", description = "Get status of multi-signature transaction")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<MultiSignatureStatus>> getMultiSignatureStatus(
            @PathVariable String transactionId) {
        
        log.info("Fetching multi-signature status for transaction {}", transactionId);
        
        MultiSignatureStatus status = signingService.getMultiSignatureStatus(transactionId);
        
        return ResponseEntity.ok(
            ApiResponse.<MultiSignatureStatus>builder()
                .success(true)
                .data(status)
                .message("Multi-signature status retrieved")
                .build()
        );
    }

    // Security Settings

    @GetMapping("/settings")
    @Operation(summary = "Get signing settings", description = "Get user's transaction signing settings")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<SigningSettingsDTO>> getSigningSettings(Principal principal) {
        log.info("Fetching signing settings for user {}", principal.getName());
        
        SigningSettingsDTO settings = signingService.getUserSigningSettings(principal.getName());
        
        return ResponseEntity.ok(
            ApiResponse.<SigningSettingsDTO>builder()
                .success(true)
                .data(settings)
                .message("Signing settings retrieved")
                .build()
        );
    }

    @PutMapping("/settings")
    @Operation(summary = "Update signing settings", description = "Update user's transaction signing settings")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<SigningSettingsDTO>> updateSigningSettings(
            @Valid @RequestBody UpdateSigningSettingsRequest request,
            Principal principal) {
        
        log.info("Updating signing settings for user {}", principal.getName());
        
        SigningSettingsDTO settings = signingService.updateUserSigningSettings(principal.getName(), request);
        
        return ResponseEntity.ok(
            ApiResponse.<SigningSettingsDTO>builder()
                .success(true)
                .data(settings)
                .message("Signing settings updated successfully")
                .build()
        );
    }
}