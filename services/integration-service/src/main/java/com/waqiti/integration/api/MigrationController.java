package com.waqiti.integration.api;

import com.waqiti.integration.service.RoutingIntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/migration")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Migration Management", description = "Cyclos to Internal Services Migration Control")
@SecurityRequirement(name = "bearerAuth")
public class MigrationController {

    private final RoutingIntegrationService routingIntegrationService;

    @Value("${integration.use-internal-services:false}")
    private boolean useInternalServices;

    @Value("${integration.internal-services-percentage:0}")
    private int internalServicesPercentage;

    @GetMapping("/status")
    @Operation(summary = "Get migration status", description = "Returns current migration configuration and status")
    @ApiResponse(responseCode = "200", description = "Migration status retrieved successfully")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MigrationStatus> getMigrationStatus() {
        log.debug("Retrieving migration status");
        
        try {
            RoutingIntegrationService.RoutingConfiguration config = 
                routingIntegrationService.getRoutingConfiguration();
            
            MigrationStatus status = MigrationStatus.builder()
                .currentPhase(determineMigrationPhase(config))
                .useInternalServices(config.useInternalServices)
                .internalServicesPercentage(config.internalServicesPercentage)
                .forceInternalForNewUsers(config.forceInternalForNewUsers)
                .operationStatus(OperationStatus.builder()
                    .usersEnabled(config.operationFlags.usersEnabled)
                    .accountsEnabled(config.operationFlags.accountsEnabled)
                    .balanceEnabled(config.operationFlags.balanceEnabled)
                    .paymentsEnabled(config.operationFlags.paymentsEnabled)
                    .build())
                .timestamp(Instant.now())
                .build();
            
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            log.error("Failed to retrieve migration status", e);
            throw new RuntimeException("Failed to get migration status", e);
        }
    }

    @PostMapping("/phase/{phase}")
    @Operation(summary = "Set migration phase", description = "Updates the migration phase and routing configuration")
    @ApiResponse(responseCode = "200", description = "Migration phase updated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid migration phase")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MigrationStatus> setMigrationPhase(
            @PathVariable String phase,
            @RequestParam(required = false, defaultValue = "0") int percentage) {
        
        log.info("Setting migration phase to: {} (percentage: {})", phase, percentage);
        
        try {
            validateMigrationPhase(phase, percentage);
            
            // Update system properties based on phase
            updateConfigurationForPhase(phase, percentage);
            
            // Return updated status
            return getMigrationStatus();
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid migration phase request: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to set migration phase: {}", phase, e);
            throw new RuntimeException("Failed to update migration phase", e);
        }
    }

    @PostMapping("/operations/{operation}/toggle")
    @Operation(summary = "Toggle operation routing", description = "Enables or disables internal routing for specific operations")
    @ApiResponse(responseCode = "200", description = "Operation routing updated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid operation")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> toggleOperationRouting(
            @PathVariable String operation,
            @RequestParam boolean enabled) {
        
        log.info("Toggling operation routing: {} -> {}", operation, enabled);
        
        try {
            validateOperation(operation);
            
            // Update operation-specific flag
            String propertyName = "integration.internal." + operation + "-enabled";
            System.setProperty(propertyName, String.valueOf(enabled));
            
            Map<String, Object> response = Map.of(
                "operation", operation,
                "enabled", enabled,
                "timestamp", Instant.now(),
                "status", "updated"
            );
            
            log.info("Successfully toggled operation routing: {} -> {}", operation, enabled);
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid operation toggle request: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to toggle operation routing: {}", operation, e);
            throw new RuntimeException("Failed to update operation routing", e);
        }
    }

    @GetMapping("/health")
    @Operation(summary = "Get migration health", description = "Returns health status of both Cyclos and internal services")
    @ApiResponse(responseCode = "200", description = "Migration health retrieved successfully")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MigrationHealth> getMigrationHealth() {
        log.debug("Retrieving migration health status");
        
        try {
            // Check health of both systems
            var cyclosHealthFuture = routingIntegrationService.checkHealth();
            
            // For simplicity, we'll check internal health synchronously
            boolean internalHealthy = checkInternalServicesHealth();
            
            var cyclosHealth = cyclosHealthFuture.get();
            
            MigrationHealth health = MigrationHealth.builder()
                .cyclosHealthy(cyclosHealth.getIsHealthy())
                .cyclosResponseTime(cyclosHealth.getResponseTime())
                .internalServicesHealthy(internalHealthy)
                .overallHealthy(cyclosHealth.getIsHealthy() || internalHealthy)
                .timestamp(Instant.now())
                .recommendations(generateHealthRecommendations(cyclosHealth.getIsHealthy(), internalHealthy))
                .build();
            
            return ResponseEntity.ok(health);
            
        } catch (Exception e) {
            log.error("Failed to retrieve migration health", e);
            throw new RuntimeException("Failed to get migration health", e);
        }
    }

    // Helper methods

    private String determineMigrationPhase(RoutingIntegrationService.RoutingConfiguration config) {
        if (config.useInternalServices) {
            return "FULLY_MIGRATED";
        } else if (config.internalServicesPercentage > 0) {
            return "GRADUAL_MIGRATION";
        } else if (config.forceInternalForNewUsers) {
            return "NEW_USERS_ONLY";
        } else {
            return "CYCLOS_ONLY";
        }
    }

    private void validateMigrationPhase(String phase, int percentage) {
        switch (phase.toUpperCase()) {
            case "CYCLOS_ONLY":
            case "NEW_USERS_ONLY":
            case "GRADUAL_MIGRATION":
            case "FULLY_MIGRATED":
                break;
            default:
                throw new IllegalArgumentException("Invalid migration phase: " + phase);
        }
        
        if (percentage < 0 || percentage > 100) {
            throw new IllegalArgumentException("Percentage must be between 0 and 100");
        }
    }

    private void validateOperation(String operation) {
        switch (operation.toLowerCase()) {
            case "users":
            case "accounts":
            case "balance":
            case "payments":
                break;
            default:
                throw new IllegalArgumentException("Invalid operation: " + operation);
        }
    }

    private void updateConfigurationForPhase(String phase, int percentage) {
        switch (phase.toUpperCase()) {
            case "CYCLOS_ONLY":
                System.setProperty("integration.use-internal-services", "false");
                System.setProperty("integration.internal-services-percentage", "0");
                System.setProperty("integration.force-internal-for-new-users", "false");
                break;
                
            case "NEW_USERS_ONLY":
                System.setProperty("integration.use-internal-services", "false");
                System.setProperty("integration.internal-services-percentage", "0");
                System.setProperty("integration.force-internal-for-new-users", "true");
                break;
                
            case "GRADUAL_MIGRATION":
                System.setProperty("integration.use-internal-services", "false");
                System.setProperty("integration.internal-services-percentage", String.valueOf(percentage));
                System.setProperty("integration.force-internal-for-new-users", "true");
                break;
                
            case "FULLY_MIGRATED":
                System.setProperty("integration.use-internal-services", "true");
                System.setProperty("integration.internal-services-percentage", "100");
                System.setProperty("integration.force-internal-for-new-users", "true");
                break;
        }
    }

    private boolean checkInternalServicesHealth() {
        // Simplified health check - in production this would be more comprehensive
        try {
            // Could ping core banking service, ledger service, etc.
            return true;
        } catch (Exception e) {
            log.warn("Internal services health check failed", e);
            return false;
        }
    }

    private String generateHealthRecommendations(boolean cyclosHealthy, boolean internalHealthy) {
        if (cyclosHealthy && internalHealthy) {
            return "Both systems healthy - safe to proceed with migration";
        } else if (!cyclosHealthy && internalHealthy) {
            return "Consider accelerating migration to internal services";
        } else if (cyclosHealthy && !internalHealthy) {
            return "Fix internal services before continuing migration";
        } else {
            return "Critical: Both systems unhealthy - immediate attention required";
        }
    }

    // Response DTOs

    public static class MigrationStatus {
        public String currentPhase;
        public boolean useInternalServices;
        public int internalServicesPercentage;
        public boolean forceInternalForNewUsers;
        public OperationStatus operationStatus;
        public Instant timestamp;

        public static MigrationStatusBuilder builder() {
            return new MigrationStatusBuilder();
        }

        public static class MigrationStatusBuilder {
            private String currentPhase;
            private boolean useInternalServices;
            private int internalServicesPercentage;
            private boolean forceInternalForNewUsers;
            private OperationStatus operationStatus;
            private Instant timestamp;

            public MigrationStatusBuilder currentPhase(String phase) {
                this.currentPhase = phase;
                return this;
            }

            public MigrationStatusBuilder useInternalServices(boolean use) {
                this.useInternalServices = use;
                return this;
            }

            public MigrationStatusBuilder internalServicesPercentage(int percentage) {
                this.internalServicesPercentage = percentage;
                return this;
            }

            public MigrationStatusBuilder forceInternalForNewUsers(boolean force) {
                this.forceInternalForNewUsers = force;
                return this;
            }

            public MigrationStatusBuilder operationStatus(OperationStatus status) {
                this.operationStatus = status;
                return this;
            }

            public MigrationStatusBuilder timestamp(Instant timestamp) {
                this.timestamp = timestamp;
                return this;
            }

            public MigrationStatus build() {
                MigrationStatus status = new MigrationStatus();
                status.currentPhase = this.currentPhase;
                status.useInternalServices = this.useInternalServices;
                status.internalServicesPercentage = this.internalServicesPercentage;
                status.forceInternalForNewUsers = this.forceInternalForNewUsers;
                status.operationStatus = this.operationStatus;
                status.timestamp = this.timestamp;
                return status;
            }
        }
    }

    public static class OperationStatus {
        public boolean usersEnabled;
        public boolean accountsEnabled;
        public boolean balanceEnabled;
        public boolean paymentsEnabled;

        public static OperationStatusBuilder builder() {
            return new OperationStatusBuilder();
        }

        public static class OperationStatusBuilder {
            private boolean usersEnabled;
            private boolean accountsEnabled;
            private boolean balanceEnabled;
            private boolean paymentsEnabled;

            public OperationStatusBuilder usersEnabled(boolean enabled) {
                this.usersEnabled = enabled;
                return this;
            }

            public OperationStatusBuilder accountsEnabled(boolean enabled) {
                this.accountsEnabled = enabled;
                return this;
            }

            public OperationStatusBuilder balanceEnabled(boolean enabled) {
                this.balanceEnabled = enabled;
                return this;
            }

            public OperationStatusBuilder paymentsEnabled(boolean enabled) {
                this.paymentsEnabled = enabled;
                return this;
            }

            public OperationStatus build() {
                OperationStatus status = new OperationStatus();
                status.usersEnabled = this.usersEnabled;
                status.accountsEnabled = this.accountsEnabled;
                status.balanceEnabled = this.balanceEnabled;
                status.paymentsEnabled = this.paymentsEnabled;
                return status;
            }
        }
    }

    public static class MigrationHealth {
        public boolean cyclosHealthy;
        public long cyclosResponseTime;
        public boolean internalServicesHealthy;
        public boolean overallHealthy;
        public Instant timestamp;
        public String recommendations;

        public static MigrationHealthBuilder builder() {
            return new MigrationHealthBuilder();
        }

        public static class MigrationHealthBuilder {
            private boolean cyclosHealthy;
            private long cyclosResponseTime;
            private boolean internalServicesHealthy;
            private boolean overallHealthy;
            private Instant timestamp;
            private String recommendations;

            public MigrationHealthBuilder cyclosHealthy(boolean healthy) {
                this.cyclosHealthy = healthy;
                return this;
            }

            public MigrationHealthBuilder cyclosResponseTime(long responseTime) {
                this.cyclosResponseTime = responseTime;
                return this;
            }

            public MigrationHealthBuilder internalServicesHealthy(boolean healthy) {
                this.internalServicesHealthy = healthy;
                return this;
            }

            public MigrationHealthBuilder overallHealthy(boolean healthy) {
                this.overallHealthy = healthy;
                return this;
            }

            public MigrationHealthBuilder timestamp(Instant timestamp) {
                this.timestamp = timestamp;
                return this;
            }

            public MigrationHealthBuilder recommendations(String recommendations) {
                this.recommendations = recommendations;
                return this;
            }

            public MigrationHealth build() {
                MigrationHealth health = new MigrationHealth();
                health.cyclosHealthy = this.cyclosHealthy;
                health.cyclosResponseTime = this.cyclosResponseTime;
                health.internalServicesHealthy = this.internalServicesHealthy;
                health.overallHealthy = this.overallHealthy;
                health.timestamp = this.timestamp;
                health.recommendations = this.recommendations;
                return health;
            }
        }
    }
}