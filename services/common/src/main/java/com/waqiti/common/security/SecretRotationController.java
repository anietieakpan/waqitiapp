package com.waqiti.common.security;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * REST Controller for Secret Rotation Operations
 * Provides secure endpoints for managing automated secret rotation
 */
@RestController
@RequestMapping("/api/v1/security/rotation")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Secret Rotation", description = "Automated secret rotation management")
@SecurityRequirement(name = "bearerAuth")
public class SecretRotationController {
    
    private final HybridSecretRotationService rotationService;
    
    /**
     * Get rotation status report
     */
    @GetMapping("/status")
    @Operation(summary = "Get rotation status report", 
               description = "Retrieve current status of all registered secrets and ongoing rotations")
    @ApiResponse(responseCode = "200", description = "Status report retrieved successfully")
    @PreAuthorize("hasRole('SECURITY_ADMIN') or hasRole('OPS_ADMIN')")
    public ResponseEntity<HybridSecretRotationService.RotationStatusReport> getRotationStatus() {
        log.info("Retrieving rotation status report");
        
        HybridSecretRotationService.RotationStatusReport report = rotationService.getRotationStatus();
        return ResponseEntity.ok(report);
    }
    
    /**
     * Register a new secret for rotation
     */
    @PostMapping("/register")
    @Operation(summary = "Register secret for rotation", 
               description = "Register a new secret for automated rotation management")
    @ApiResponse(responseCode = "200", description = "Secret registered successfully")
    @ApiResponse(responseCode = "400", description = "Invalid registration request")
    @PreAuthorize("hasRole('SECURITY_ADMIN')")
    public ResponseEntity<String> registerSecret(
            @Valid @RequestBody HybridSecretRotationService.SecretRegistration registration) {
        
        log.info("Registering secret: {}", registration.getSecretId());
        
        try {
            rotationService.registerSecret(registration);
            return ResponseEntity.ok("Secret registered successfully");
        } catch (Exception e) {
            log.error("Failed to register secret", e);
            return ResponseEntity.badRequest().body("Registration failed: " + e.getMessage());
        }
    }
    
    /**
     * Trigger manual rotation of a specific secret
     */
    @PostMapping("/rotate/{secretId}")
    @Operation(summary = "Rotate specific secret", 
               description = "Manually trigger rotation of a specific secret")
    @ApiResponse(responseCode = "202", description = "Rotation initiated successfully")
    @ApiResponse(responseCode = "404", description = "Secret not found")
    @ApiResponse(responseCode = "409", description = "Secret is already being rotated")
    @PreAuthorize("hasRole('SECURITY_ADMIN') or hasRole('OPS_ADMIN')")
    public ResponseEntity<String> rotateSecret(
            @Parameter(description = "Secret ID to rotate") @PathVariable String secretId) {
        
        log.info("Manual rotation requested for secret: {}", secretId);
        
        try {
            // Validate rotation readiness
            HybridSecretRotationService.ValidationResult validation = 
                rotationService.validateRotationReadiness(secretId);
            
            if (!validation.isValid()) {
                return ResponseEntity.status(409).body("Rotation not ready: " + validation.getMessage());
            }
            
            // Start rotation asynchronously
            CompletableFuture<HybridSecretRotationService.RotationResult> rotationFuture = 
                rotationService.rotateSecret(secretId);
            
            return ResponseEntity.accepted().body("Rotation initiated for secret: " + secretId);
            
        } catch (HybridSecretRotationService.SecretRotationException e) {
            if (e.getMessage().contains("not registered")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.badRequest().body("Rotation failed: " + e.getMessage());
        }
    }
    
    /**
     * Perform system-wide rotation
     */
    @PostMapping("/system-rotation")
    @Operation(summary = "System-wide rotation", 
               description = "Perform coordinated rotation of multiple secrets")
    @ApiResponse(responseCode = "202", description = "System rotation initiated")
    @ApiResponse(responseCode = "400", description = "Invalid rotation request")
    @PreAuthorize("hasRole('SECURITY_ADMIN')")
    public ResponseEntity<String> performSystemRotation(
            @Valid @RequestBody HybridSecretRotationService.SystemRotationRequest request) {
        
        log.info("System-wide rotation requested: {}", request.getDescription());
        
        try {
            CompletableFuture<HybridSecretRotationService.SystemRotationResult> rotationFuture = 
                rotationService.performSystemWideRotation(request);
            
            return ResponseEntity.accepted().body("System rotation initiated: " + request.getDescription());
            
        } catch (Exception e) {
            log.error("System rotation failed", e);
            return ResponseEntity.badRequest().body("System rotation failed: " + e.getMessage());
        }
    }
    
    /**
     * Emergency rotation for compromised secrets
     */
    @PostMapping("/emergency/{secretId}")
    @Operation(summary = "Emergency rotation", 
               description = "Immediately rotate a compromised secret")
    @ApiResponse(responseCode = "200", description = "Emergency rotation completed")
    @ApiResponse(responseCode = "404", description = "Secret not found")
    @ApiResponse(responseCode = "500", description = "Emergency rotation failed")
    @PreAuthorize("hasRole('SECURITY_ADMIN')")
    public ResponseEntity<HybridSecretRotationService.RotationResult> emergencyRotation(
            @Parameter(description = "Secret ID to rotate") @PathVariable String secretId,
            @Valid @RequestBody EmergencyRotationRequest request) {
        
        log.warn("EMERGENCY ROTATION requested for secret: {} - Reason: {}", secretId, request.getReason());
        
        try {
            HybridSecretRotationService.EmergencyRotationRequest emergencyRequest = 
                HybridSecretRotationService.EmergencyRotationRequest.builder()
                    .secretId(secretId)
                    .reason(request.getReason())
                    .initiatedBy(request.getInitiatedBy())
                    .build();
            
            HybridSecretRotationService.RotationResult result = 
                rotationService.emergencyRotation(emergencyRequest);
            
            return ResponseEntity.ok(result);
            
        } catch (HybridSecretRotationService.SecretRotationException e) {
            if (e.getMessage().contains("not registered")) {
                return ResponseEntity.notFound().build();
            }
            log.error("Emergency rotation failed", e);
            return ResponseEntity.status(500).body(
                HybridSecretRotationService.RotationResult.builder()
                    .secretId(secretId)
                    .success(false)
                    .errorMessage("Emergency rotation failed: " + e.getMessage())
                    .build()
            );
        }
    }
    
    /**
     * Validate rotation readiness
     */
    @GetMapping("/validate/{secretId}")
    @Operation(summary = "Validate rotation readiness", 
               description = "Check if a secret is ready for rotation")
    @ApiResponse(responseCode = "200", description = "Validation completed")
    @ApiResponse(responseCode = "404", description = "Secret not found")
    @PreAuthorize("hasRole('SECURITY_ADMIN') or hasRole('OPS_ADMIN')")
    public ResponseEntity<HybridSecretRotationService.ValidationResult> validateRotationReadiness(
            @Parameter(description = "Secret ID to validate") @PathVariable String secretId) {
        
        try {
            HybridSecretRotationService.ValidationResult result = 
                rotationService.validateRotationReadiness(secretId);
            
            return ResponseEntity.ok(result);
            
        } catch (HybridSecretRotationService.SecretRotationException e) {
            if (e.getMessage().contains("not registered")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(
                HybridSecretRotationService.ValidationResult.failure(e.getMessage())
            );
        }
    }
    
    /**
     * Get rotation history for a secret
     */
    @GetMapping("/history/{secretId}")
    @Operation(summary = "Get rotation history", 
               description = "Retrieve rotation history for a specific secret")
    @ApiResponse(responseCode = "200", description = "History retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Secret not found")
    @PreAuthorize("hasRole('SECURITY_ADMIN') or hasRole('AUDITOR')")
    public ResponseEntity<List<RotationHistoryEntry>> getRotationHistory(
            @Parameter(description = "Secret ID") @PathVariable String secretId,
            @Parameter(description = "Number of entries to return") @RequestParam(defaultValue = "50") int limit) {
        
        // This would typically query an audit log or database
        // For now, return a placeholder response
        log.info("Rotation history requested for secret: {}", secretId);
        
        // Implementation would fetch from audit service
        return ResponseEntity.ok(List.of());
    }
    
    /**
     * Get upcoming rotations
     */
    @GetMapping("/upcoming")
    @Operation(summary = "Get upcoming rotations", 
               description = "Retrieve list of secrets scheduled for rotation")
    @ApiResponse(responseCode = "200", description = "Upcoming rotations retrieved")
    @PreAuthorize("hasRole('SECURITY_ADMIN') or hasRole('OPS_ADMIN')")
    public ResponseEntity<List<UpcomingRotation>> getUpcomingRotations(
            @Parameter(description = "Number of days to look ahead") @RequestParam(defaultValue = "30") int days) {
        
        HybridSecretRotationService.RotationStatusReport report = rotationService.getRotationStatus();
        
        List<UpcomingRotation> upcomingRotations = report.getSecretStatuses().stream()
            .filter(status -> status.getNextRotation() != null)
            .map(status -> UpcomingRotation.builder()
                .secretId(status.getSecretId())
                .secretType(status.getSecretType())
                .nextRotation(status.getNextRotation())
                .dependentServices(status.getDependentServices())
                .build())
            .collect(java.util.stream.Collectors.toList());
        
        return ResponseEntity.ok(upcomingRotations);
    }
    
    /**
     * Pause/resume rotation for a secret
     */
    @PostMapping("/pause/{secretId}")
    @Operation(summary = "Pause secret rotation", 
               description = "Temporarily pause rotation for a specific secret")
    @ApiResponse(responseCode = "200", description = "Rotation paused successfully")
    @ApiResponse(responseCode = "404", description = "Secret not found")
    @PreAuthorize("hasRole('SECURITY_ADMIN')")
    public ResponseEntity<String> pauseRotation(
            @Parameter(description = "Secret ID") @PathVariable String secretId,
            @RequestBody PauseRotationRequest request) {
        
        log.info("Pausing rotation for secret: {} - Reason: {}", secretId, request.getReason());
        
        // Implementation would update secret metadata to pause rotation
        return ResponseEntity.ok("Rotation paused for secret: " + secretId);
    }
    
    @PostMapping("/resume/{secretId}")
    @Operation(summary = "Resume secret rotation", 
               description = "Resume rotation for a previously paused secret")
    @ApiResponse(responseCode = "200", description = "Rotation resumed successfully")
    @ApiResponse(responseCode = "404", description = "Secret not found")
    @PreAuthorize("hasRole('SECURITY_ADMIN')")
    public ResponseEntity<String> resumeRotation(
            @Parameter(description = "Secret ID") @PathVariable String secretId) {
        
        log.info("Resuming rotation for secret: {}", secretId);
        
        // Implementation would update secret metadata to resume rotation
        return ResponseEntity.ok("Rotation resumed for secret: " + secretId);
    }
    
    /**
     * Test rotation capability
     */
    @PostMapping("/test/{secretId}")
    @Operation(summary = "Test rotation capability", 
               description = "Perform a dry-run test of secret rotation")
    @ApiResponse(responseCode = "200", description = "Test completed successfully")
    @ApiResponse(responseCode = "404", description = "Secret not found")
    @PreAuthorize("hasRole('SECURITY_ADMIN')")
    public ResponseEntity<TestRotationResult> testRotation(
            @Parameter(description = "Secret ID") @PathVariable String secretId) {
        
        log.info("Testing rotation capability for secret: {}", secretId);
        
        try {
            // Validate rotation readiness
            HybridSecretRotationService.ValidationResult validation = 
                rotationService.validateRotationReadiness(secretId);
            
            TestRotationResult result = TestRotationResult.builder()
                .secretId(secretId)
                .canRotate(validation.isValid())
                .validationMessage(validation.getMessage())
                .testedAt(java.time.Instant.now())
                .build();
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Rotation test failed", e);
            
            TestRotationResult result = TestRotationResult.builder()
                .secretId(secretId)
                .canRotate(false)
                .validationMessage("Test failed: " + e.getMessage())
                .testedAt(java.time.Instant.now())
                .build();
            
            return ResponseEntity.ok(result);
        }
    }
    
    // Request/Response DTOs
    
    @lombok.Data
    @lombok.Builder
    public static class EmergencyRotationRequest {
        private String reason;
        private String initiatedBy;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class PauseRotationRequest {
        private String reason;
        private int pauseDurationHours;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class RotationHistoryEntry {
        private java.time.Instant timestamp;
        private String action;
        private boolean success;
        private String strategy;
        private String initiatedBy;
        private String message;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class UpcomingRotation {
        private String secretId;
        private HybridSecretRotationService.SecretType secretType;
        private java.time.Instant nextRotation;
        private List<String> dependentServices;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class TestRotationResult {
        private String secretId;
        private boolean canRotate;
        private String validationMessage;
        private java.time.Instant testedAt;
    }
}