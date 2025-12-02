package com.waqiti.config.controller;

import com.waqiti.config.dto.*;
import com.waqiti.config.service.ConfigurationService;
import com.waqiti.common.security.SecurityContext;
import com.waqiti.common.validation.ValidUUID;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * REST API controller for centralized configuration management
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
@Validated
@Tag(name = "Configuration", description = "Centralized configuration and feature flag management")
@RateLimiter(name = "configApi")
public class ConfigurationController {

    private final ConfigurationService configurationService;
    private final SecurityContext securityContext;

    @Operation(
        summary = "Get configuration value",
        description = "Get configuration value by key"
    )
    @ApiResponse(responseCode = "200", description = "Configuration value retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Configuration not found")
    @GetMapping("/{key}")
    @PreAuthorize("hasRole('SERVICE') or hasRole('ADMIN')")
    public ResponseEntity<ConfigValue> getConfig(
            @Parameter(description = "Configuration key") @PathVariable String key,
            @RequestParam(required = false) String defaultValue) {
        log.debug("Getting configuration for key: {}", key);
        
        ConfigValue config = configurationService.getConfig(key, defaultValue);
        return ResponseEntity.ok(config);
    }

    @Operation(
        summary = "Create configuration",
        description = "Create a new configuration entry"
    )
    @ApiResponse(responseCode = "201", description = "Configuration created successfully")
    @ApiResponse(responseCode = "409", description = "Configuration already exists")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ConfigValue> createConfig(
            @Valid @RequestBody CreateConfigRequest request) {
        String userId = securityContext.getCurrentUserId();
        log.info("Creating configuration {} by user {}", request.getKey(), userId);
        
        ConfigValue config = configurationService.createConfig(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(config);
    }

    @Operation(
        summary = "Update configuration",
        description = "Update an existing configuration value"
    )
    @ApiResponse(responseCode = "200", description = "Configuration updated successfully")
    @ApiResponse(responseCode = "404", description = "Configuration not found")
    @PutMapping("/{key}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ConfigValue> updateConfig(
            @Parameter(description = "Configuration key") @PathVariable String key,
            @Valid @RequestBody UpdateConfigRequest request) {
        String userId = securityContext.getCurrentUserId();
        log.info("Updating configuration {} by user {}", key, userId);
        
        ConfigValue config = configurationService.updateConfig(key, request);
        return ResponseEntity.ok(config);
    }

    @Operation(
        summary = "Delete configuration",
        description = "Delete a configuration entry"
    )
    @ApiResponse(responseCode = "204", description = "Configuration deleted successfully")
    @ApiResponse(responseCode = "404", description = "Configuration not found")
    @DeleteMapping("/{key}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteConfig(
            @Parameter(description = "Configuration key") @PathVariable String key) {
        String userId = securityContext.getCurrentUserId();
        log.warn("Deleting configuration {} by user {}", key, userId);
        
        configurationService.deleteConfig(key);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Search configurations",
        description = "Search configurations with filters"
    )
    @ApiResponse(responseCode = "200", description = "Configurations retrieved successfully")
    @PostMapping("/search")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<ConfigurationDto>> searchConfigurations(
            @Valid @RequestBody ConfigSearchRequest request,
            @PageableDefault(size = 50) Pageable pageable) {
        log.debug("Searching configurations with filters: {}", request);
        
        Page<ConfigurationDto> configs = configurationService.searchConfigurations(request, pageable);
        return ResponseEntity.ok(configs);
    }

    @Operation(
        summary = "Get service configuration",
        description = "Get all configurations for a specific service"
    )
    @ApiResponse(responseCode = "200", description = "Service configuration retrieved successfully")
    @GetMapping("/service/{serviceName}")
    @PreAuthorize("hasRole('SERVICE') or hasRole('ADMIN')")
    public ResponseEntity<ServiceConfigDto> getServiceConfig(
            @Parameter(description = "Service name") @PathVariable String serviceName) {
        log.debug("Getting configuration for service: {}", serviceName);
        
        ServiceConfigDto config = configurationService.getServiceConfig(serviceName);
        return ResponseEntity.ok(config);
    }

    @Operation(
        summary = "Bulk update configurations",
        description = "Update multiple configurations in a single request"
    )
    @ApiResponse(responseCode = "200", description = "Bulk update completed")
    @PostMapping("/bulk-update")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BulkUpdateResultDto> bulkUpdateConfigs(
            @Valid @RequestBody List<BulkConfigUpdate> updates) {
        String userId = securityContext.getCurrentUserId();
        log.info("Bulk updating {} configurations by user {}", updates.size(), userId);
        
        BulkUpdateResultDto result = configurationService.bulkUpdateConfigs(updates);
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Export configurations",
        description = "Export configurations in various formats"
    )
    @ApiResponse(responseCode = "200", description = "Configurations exported successfully")
    @PostMapping("/export")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ConfigExportDto> exportConfigurations(
            @Valid @RequestBody ExportConfigRequest request) {
        String userId = securityContext.getCurrentUserId();
        log.info("Exporting configurations by user {}", userId);
        
        ConfigExportDto export = configurationService.exportConfigurations(request);
        return ResponseEntity.ok(export);
    }

    @Operation(
        summary = "Import configurations",
        description = "Import configurations from external source"
    )
    @ApiResponse(responseCode = "200", description = "Import completed")
    @PostMapping("/import")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ConfigImportResultDto> importConfigurations(
            @Valid @RequestBody ConfigImportRequest request) {
        String userId = securityContext.getCurrentUserId();
        log.info("Importing {} configurations by user {}", request.getConfigurations().size(), userId);
        
        ConfigImportResultDto result = configurationService.importConfigurations(request);
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Refresh all configurations",
        description = "Trigger configuration refresh for all services"
    )
    @ApiResponse(responseCode = "204", description = "Refresh triggered successfully")
    @PostMapping("/refresh")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> refreshConfigurations() {
        String userId = securityContext.getCurrentUserId();
        log.info("Refreshing all configurations by user {}", userId);
        
        configurationService.refreshAllConfigurations();
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Get configuration history",
        description = "Get audit history for a configuration key"
    )
    @ApiResponse(responseCode = "200", description = "History retrieved successfully")
    @GetMapping("/{key}/history")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ConfigAuditDto>> getConfigHistory(
            @Parameter(description = "Configuration key") @PathVariable String key) {
        log.debug("Getting history for configuration: {}", key);
        
        List<ConfigAuditDto> history = configurationService.getConfigHistory(key);
        return ResponseEntity.ok(history);
    }

    // Feature Flag Endpoints

    @Operation(
        summary = "Check feature flag",
        description = "Check if a feature flag is enabled"
    )
    @ApiResponse(responseCode = "200", description = "Feature flag status retrieved")
    @GetMapping("/feature-flags/{flagName}")
    @PreAuthorize("hasRole('SERVICE') or hasRole('USER')")
    public ResponseEntity<FeatureFlagStatusDto> checkFeatureFlag(
            @Parameter(description = "Feature flag name") @PathVariable String flagName,
            @RequestParam(required = false) String userId,
            @RequestBody(required = false) Map<String, Object> context) {
        boolean enabled = configurationService.isFeatureEnabled(flagName, userId, context != null ? context : Map.of());
        
        FeatureFlagStatusDto status = FeatureFlagStatusDto.builder()
            .flagName(flagName)
            .enabled(enabled)
            .userId(userId)
            .build();
        
        return ResponseEntity.ok(status);
    }

    @Operation(
        summary = "Create feature flag",
        description = "Create a new feature flag"
    )
    @ApiResponse(responseCode = "201", description = "Feature flag created successfully")
    @PostMapping("/feature-flags")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FeatureFlagDto> createFeatureFlag(
            @Valid @RequestBody CreateFeatureFlagRequest request) {
        String userId = securityContext.getCurrentUserId();
        log.info("Creating feature flag {} by user {}", request.getName(), userId);
        
        FeatureFlagDto flag = configurationService.createFeatureFlag(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(flag);
    }

    @Operation(
        summary = "Update feature flag",
        description = "Update an existing feature flag"
    )
    @ApiResponse(responseCode = "200", description = "Feature flag updated successfully")
    @PutMapping("/feature-flags/{flagName}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FeatureFlagDto> updateFeatureFlag(
            @Parameter(description = "Feature flag name") @PathVariable String flagName,
            @Valid @RequestBody UpdateFeatureFlagRequest request) {
        String userId = securityContext.getCurrentUserId();
        log.info("Updating feature flag {} by user {}", flagName, userId);
        
        FeatureFlagDto flag = configurationService.updateFeatureFlag(flagName, request);
        return ResponseEntity.ok(flag);
    }

    @Operation(
        summary = "Get all feature flags",
        description = "Get list of all feature flags"
    )
    @ApiResponse(responseCode = "200", description = "Feature flags retrieved successfully")
    @GetMapping("/feature-flags")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<FeatureFlagDto>> getAllFeatureFlags() {
        log.debug("Getting all feature flags");
        
        List<FeatureFlagDto> flags = configurationService.getAllFeatureFlags();
        return ResponseEntity.ok(flags);
    }

    @Operation(
        summary = "Delete feature flag",
        description = "Delete a feature flag"
    )
    @ApiResponse(responseCode = "204", description = "Feature flag deleted successfully")
    @DeleteMapping("/feature-flags/{flagName}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteFeatureFlag(
            @Parameter(description = "Feature flag name") @PathVariable String flagName) {
        String userId = securityContext.getCurrentUserId();
        log.warn("Deleting feature flag {} by user {}", flagName, userId);
        
        configurationService.deleteFeatureFlag(flagName);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Get configuration metrics",
        description = "Get metrics about configuration usage and performance"
    )
    @ApiResponse(responseCode = "200", description = "Metrics retrieved successfully")
    @GetMapping("/metrics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ConfigMetricsDto> getConfigMetrics() {
        log.debug("Getting configuration metrics");
        
        ConfigMetricsDto metrics = configurationService.getConfigMetrics();
        return ResponseEntity.ok(metrics);
    }

    @Operation(
        summary = "Validate configuration",
        description = "Validate configuration value without saving"
    )
    @ApiResponse(responseCode = "200", description = "Validation result")
    @PostMapping("/validate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ValidationResultDto> validateConfig(
            @Valid @RequestBody ValidateConfigRequest request) {
        ValidationResultDto result = configurationService.validateConfig(request);
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Get configuration dependencies",
        description = "Get dependencies for a configuration key"
    )
    @ApiResponse(responseCode = "200", description = "Dependencies retrieved successfully")
    @GetMapping("/{key}/dependencies")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ConfigDependenciesDto> getConfigDependencies(
            @Parameter(description = "Configuration key") @PathVariable String key) {
        log.debug("Getting dependencies for configuration: {}", key);
        
        ConfigDependenciesDto dependencies = configurationService.getConfigDependencies(key);
        return ResponseEntity.ok(dependencies);
    }
}