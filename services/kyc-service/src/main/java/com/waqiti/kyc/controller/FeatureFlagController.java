package com.waqiti.kyc.controller;

import com.waqiti.kyc.compatibility.CompatibilityHealthStatus;
import com.waqiti.kyc.compatibility.KYCCompatibilityService;
import com.waqiti.kyc.config.FeatureFlagConfiguration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/kyc/feature-flags")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "KYC Feature Flags", description = "Manage KYC service feature flags")
@SecurityRequirement(name = "bearerAuth")
public class FeatureFlagController {

    private final FeatureFlagConfiguration featureFlags;
    private final KYCCompatibilityService compatibilityService;

    @Operation(summary = "Get all feature flags")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Feature flags retrieved successfully")
    })
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SYSTEM')")
    public ResponseEntity<Map<String, Object>> getAllFeatureFlags() {
        log.info("Fetching all feature flags");
        
        Map<String, Object> response = new HashMap<>();
        response.put("flags", featureFlags.getFlags());
        response.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get specific feature flag")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Feature flag retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Feature flag not found")
    })
    @GetMapping("/{flagName}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SYSTEM')")
    public ResponseEntity<Map<String, Object>> getFeatureFlag(@PathVariable String flagName) {
        log.info("Fetching feature flag: {}", flagName);
        
        FeatureFlagConfiguration.FeatureFlag flag = featureFlags.getFlags().get(flagName);
        if (flag == null) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("name", flagName);
        response.put("enabled", flag.isEnabled());
        response.put("percentage", flag.getPercentage());
        response.put("description", flag.getDescription());
        response.put("metadata", flag.getMetadata());
        response.put("effectiveStatus", featureFlags.isEnabled(flagName));
        
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update feature flag")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Feature flag updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @PutMapping("/{flagName}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> updateFeatureFlag(
            @PathVariable String flagName,
            @RequestBody FeatureFlagUpdateRequest request) {
        
        log.info("Updating feature flag: {} to enabled: {}, percentage: {}", 
                flagName, request.isEnabled(), request.getPercentage());
        
        // Validate flag name
        if (!isValidFeatureFlag(flagName)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid feature flag: " + flagName));
        }
        
        // Update the flag
        if (request.getPercentage() != null) {
            featureFlags.setFeaturePercentage(flagName, request.getPercentage());
        } else {
            featureFlags.setFeature(flagName, request.isEnabled());
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("name", flagName);
        response.put("enabled", request.isEnabled());
        response.put("percentage", request.getPercentage());
        response.put("updatedAt", LocalDateTime.now());
        response.put("updatedBy", "ADMIN"); // Would get from security context
        
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get migration rollout status")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Rollout status retrieved successfully")
    })
    @GetMapping("/rollout-status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SYSTEM')")
    public ResponseEntity<Map<String, Object>> getRolloutStatus() {
        log.info("Fetching KYC migration rollout status");
        
        Map<String, Object> status = new HashMap<>();
        
        // Feature flag states
        status.put("useNewService", featureFlags.isEnabled("USE_NEW_KYC_SERVICE"));
        status.put("dualWriteMode", featureFlags.isEnabled("DUAL_WRITE_MODE"));
        status.put("shadowMode", featureFlags.isEnabled("SHADOW_MODE"));
        status.put("autoMigration", featureFlags.isEnabled("AUTO_MIGRATION"));
        status.put("migrationEnabled", featureFlags.isEnabled("ENABLE_KYC_MIGRATION"));
        
        // Get percentage if in gradual rollout
        FeatureFlagConfiguration.FeatureFlag newServiceFlag = 
                featureFlags.getFlags().get("USE_NEW_KYC_SERVICE");
        if (newServiceFlag != null) {
            status.put("rolloutPercentage", newServiceFlag.getPercentage());
        }
        
        // Current phase
        status.put("currentPhase", determineCurrentPhase());
        status.put("recommendedNextAction", getRecommendedNextAction());
        
        return ResponseEntity.ok(status);
    }

    @Operation(summary = "Get compatibility health status")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Health status retrieved successfully")
    })
    @GetMapping("/health")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SYSTEM')")
    public ResponseEntity<CompatibilityHealthStatus> getCompatibilityHealth() {
        log.info("Fetching compatibility health status");
        
        CompatibilityHealthStatus healthStatus = compatibilityService.getHealthStatus();
        
        return ResponseEntity.ok(healthStatus);
    }

    @Operation(summary = "Execute migration phase transition")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Phase transition executed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid phase transition")
    })
    @PostMapping("/phase-transition")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> executePhaseTransition(
            @RequestBody PhaseTransitionRequest request) {
        
        log.info("Executing phase transition to: {}", request.getTargetPhase());
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            switch (request.getTargetPhase()) {
                case "SHADOW_MODE" -> enableShadowMode();
                case "DUAL_WRITE" -> enableDualWriteMode();
                case "GRADUAL_ROLLOUT" -> startGradualRollout(request.getPercentage());
                case "FULL_MIGRATION" -> enableFullMigration();
                case "ROLLBACK" -> executeRollback();
                default -> {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Invalid target phase: " + request.getTargetPhase()));
                }
            }
            
            result.put("status", "SUCCESS");
            result.put("targetPhase", request.getTargetPhase());
            result.put("timestamp", LocalDateTime.now());
            
        } catch (Exception e) {
            log.error("Phase transition failed: {}", e.getMessage());
            result.put("status", "FAILED");
            result.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }

    private boolean isValidFeatureFlag(String flagName) {
        return flagName.equals("USE_NEW_KYC_SERVICE") ||
               flagName.equals("ENABLE_KYC_MIGRATION") ||
               flagName.equals("DUAL_WRITE_MODE") ||
               flagName.equals("SHADOW_MODE") ||
               flagName.equals("AUTO_MIGRATION");
    }

    private String determineCurrentPhase() {
        if (!featureFlags.isEnabled("SHADOW_MODE") && !featureFlags.isEnabled("USE_NEW_KYC_SERVICE")) {
            return "LEGACY_ONLY";
        } else if (featureFlags.isEnabled("SHADOW_MODE") && !featureFlags.isEnabled("USE_NEW_KYC_SERVICE")) {
            return "SHADOW_MODE";
        } else if (featureFlags.isEnabled("DUAL_WRITE_MODE")) {
            return "DUAL_WRITE";
        } else if (featureFlags.isEnabled("USE_NEW_KYC_SERVICE")) {
            FeatureFlagConfiguration.FeatureFlag flag = featureFlags.getFlags().get("USE_NEW_KYC_SERVICE");
            if (flag != null && flag.getPercentage() > 0 && flag.getPercentage() < 100) {
                return "GRADUAL_ROLLOUT_" + flag.getPercentage() + "%";
            }
            return "FULL_MIGRATION";
        }
        return "UNKNOWN";
    }

    private String getRecommendedNextAction() {
        String currentPhase = determineCurrentPhase();
        
        return switch (currentPhase) {
            case "LEGACY_ONLY" -> "Enable shadow mode to start comparing results";
            case "SHADOW_MODE" -> "Review shadow mode metrics, then enable dual write mode";
            case "DUAL_WRITE" -> "Start gradual rollout at 5-10%";
            case "FULL_MIGRATION" -> "Monitor metrics and prepare for legacy system decommission";
            default -> {
                if (currentPhase.startsWith("GRADUAL_ROLLOUT_")) {
                    yield "Increase rollout percentage or proceed to full migration";
                }
                yield "Review current state";
            }
        };
    }

    private void enableShadowMode() {
        featureFlags.setFeature("SHADOW_MODE", true);
        featureFlags.setFeature("USE_NEW_KYC_SERVICE", false);
        featureFlags.setFeature("DUAL_WRITE_MODE", false);
    }

    private void enableDualWriteMode() {
        featureFlags.setFeature("DUAL_WRITE_MODE", true);
        featureFlags.setFeature("SHADOW_MODE", true);
    }

    private void startGradualRollout(Integer percentage) {
        int rolloutPercentage = percentage != null ? percentage : 10;
        featureFlags.setFeature("USE_NEW_KYC_SERVICE", true);
        featureFlags.setFeaturePercentage("USE_NEW_KYC_SERVICE", rolloutPercentage);
        featureFlags.setFeature("DUAL_WRITE_MODE", true);
    }

    private void enableFullMigration() {
        featureFlags.setFeature("USE_NEW_KYC_SERVICE", true);
        featureFlags.setFeaturePercentage("USE_NEW_KYC_SERVICE", 100);
        featureFlags.setFeature("DUAL_WRITE_MODE", false);
        featureFlags.setFeature("SHADOW_MODE", false);
    }

    private void executeRollback() {
        featureFlags.setFeature("USE_NEW_KYC_SERVICE", false);
        featureFlags.setFeaturePercentage("USE_NEW_KYC_SERVICE", 0);
        featureFlags.setFeature("DUAL_WRITE_MODE", false);
        featureFlags.setFeature("SHADOW_MODE", false);
    }

    @Data
    static class FeatureFlagUpdateRequest {
        private boolean enabled;
        private Integer percentage;
        private String description;
        private Map<String, String> metadata;
    }

    @Data
    static class PhaseTransitionRequest {
        private String targetPhase;
        private Integer percentage;
        private Map<String, String> parameters;
    }
}