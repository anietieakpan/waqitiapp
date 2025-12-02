package com.waqiti.discovery.controller;

import com.waqiti.discovery.dto.*;
import com.waqiti.discovery.service.ServiceRegistryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * REST API controller for service registry and discovery
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/registry")
@RequiredArgsConstructor
@Validated
@Tag(name = "Service Registry", description = "Service discovery and registry management")
public class ServiceRegistryController {

    private final ServiceRegistryService serviceRegistryService;

    @Operation(
        summary = "Get all registered services",
        description = "Get list of all services registered in the discovery server"
    )
    @ApiResponse(responseCode = "200", description = "Services retrieved successfully")
    @GetMapping("/services")
    @PreAuthorize("hasRole('SERVICE') or hasRole('ADMIN')")
    public ResponseEntity<List<ServiceInfoDto>> getAllServices() {
        log.debug("Getting all registered services");
        
        List<ServiceInfoDto> services = serviceRegistryService.getAllServices();
        return ResponseEntity.ok(services);
    }

    @Operation(
        summary = "Get service details",
        description = "Get detailed information about a specific service"
    )
    @ApiResponse(responseCode = "200", description = "Service details retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Service not found")
    @GetMapping("/services/{serviceName}")
    @PreAuthorize("hasRole('SERVICE') or hasRole('ADMIN')")
    public ResponseEntity<ServiceDetailsDto> getServiceDetails(
            @Parameter(description = "Service name") @PathVariable String serviceName) {
        log.debug("Getting details for service: {}", serviceName);
        
        ServiceDetailsDto details = serviceRegistryService.getServiceDetails(serviceName);
        return ResponseEntity.ok(details);
    }

    @Operation(
        summary = "Get service instances",
        description = "Get available instances of a service with load balancing"
    )
    @ApiResponse(responseCode = "200", description = "Service instances retrieved successfully")
    @GetMapping("/services/{serviceName}/instances")
    @PreAuthorize("hasRole('SERVICE')")
    public ResponseEntity<List<ServiceInstanceDto>> getServiceInstances(
            @Parameter(description = "Service name") @PathVariable String serviceName,
            @RequestParam(defaultValue = "ROUND_ROBIN") LoadBalancerStrategy strategy) {
        log.debug("Getting instances for service: {} with strategy: {}", serviceName, strategy);
        
        List<ServiceInstanceDto> instances = serviceRegistryService.getServiceInstances(serviceName, strategy);
        return ResponseEntity.ok(instances);
    }

    @Operation(
        summary = "Register service dependencies",
        description = "Register dependencies for a service"
    )
    @ApiResponse(responseCode = "204", description = "Dependencies registered successfully")
    @PostMapping("/services/{serviceName}/dependencies")
    @PreAuthorize("hasRole('SERVICE')")
    public ResponseEntity<Void> registerServiceDependencies(
            @Parameter(description = "Service name") @PathVariable String serviceName,
            @Valid @RequestBody RegisterDependenciesRequest request) {
        log.info("Registering dependencies for service: {}", serviceName);
        
        serviceRegistryService.registerServiceDependencies(serviceName, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Get dependency graph",
        description = "Get the complete service dependency graph"
    )
    @ApiResponse(responseCode = "200", description = "Dependency graph retrieved successfully")
    @GetMapping("/dependency-graph")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ServiceDependencyGraphDto> getServiceDependencyGraph() {
        log.debug("Getting service dependency graph");
        
        ServiceDependencyGraphDto graph = serviceRegistryService.getServiceDependencyGraph();
        return ResponseEntity.ok(graph);
    }

    @Operation(
        summary = "Perform health check",
        description = "Perform health check on a specific service"
    )
    @ApiResponse(responseCode = "200", description = "Health check completed")
    @PostMapping("/services/{serviceName}/health-check")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ServiceHealthCheckResultDto> performHealthCheck(
            @Parameter(description = "Service name") @PathVariable String serviceName) {
        log.info("Performing health check for service: {}", serviceName);
        
        ServiceHealthCheckResultDto result = serviceRegistryService.performHealthCheck(serviceName);
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Get service metrics",
        description = "Get metrics for a specific service"
    )
    @ApiResponse(responseCode = "200", description = "Metrics retrieved successfully")
    @GetMapping("/services/{serviceName}/metrics")
    @PreAuthorize("hasRole('SERVICE') or hasRole('ADMIN')")
    public ResponseEntity<ServiceMetricsDto> getServiceMetrics(
            @Parameter(description = "Service name") @PathVariable String serviceName,
            @RequestParam(defaultValue = "LAST_HOUR") MetricsTimeframe timeframe) {
        log.debug("Getting metrics for service: {}, timeframe: {}", serviceName, timeframe);
        
        ServiceMetricsDto metrics = serviceRegistryService.getServiceMetrics(serviceName, timeframe);
        return ResponseEntity.ok(metrics);
    }

    @Operation(
        summary = "Toggle service status",
        description = "Enable or disable a service"
    )
    @ApiResponse(responseCode = "204", description = "Service status updated successfully")
    @PutMapping("/services/{serviceName}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> toggleServiceStatus(
            @Parameter(description = "Service name") @PathVariable String serviceName,
            @RequestParam boolean enable) {
        log.warn("User toggling service {} to {}", serviceName, enable ? "enabled" : "disabled");

        serviceRegistryService.toggleServiceStatus(serviceName, enable);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Get service configuration",
        description = "Get configuration for a specific service"
    )
    @ApiResponse(responseCode = "200", description = "Configuration retrieved successfully")
    @GetMapping("/services/{serviceName}/configuration")
    @PreAuthorize("hasRole('SERVICE') or hasRole('ADMIN')")
    public ResponseEntity<ServiceConfigurationDto> getServiceConfiguration(
            @Parameter(description = "Service name") @PathVariable String serviceName) {
        log.debug("Getting configuration for service: {}", serviceName);
        
        ServiceConfigurationDto config = serviceRegistryService.getServiceConfiguration(serviceName);
        return ResponseEntity.ok(config);
    }

    @Operation(
        summary = "Update service configuration",
        description = "Update configuration for a specific service"
    )
    @ApiResponse(responseCode = "200", description = "Configuration updated successfully")
    @PutMapping("/services/{serviceName}/configuration")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ServiceConfigurationDto> updateServiceConfiguration(
            @Parameter(description = "Service name") @PathVariable String serviceName,
            @Valid @RequestBody UpdateServiceConfigRequest request) {
        log.info("Updating configuration for service {}", serviceName);

        ServiceConfigurationDto config = serviceRegistryService.updateServiceConfiguration(serviceName, request);
        return ResponseEntity.ok(config);
    }

    @Operation(
        summary = "Get registry health",
        description = "Get health status of the registry server"
    )
    @ApiResponse(responseCode = "200", description = "Registry health retrieved successfully")
    @GetMapping("/health")
    public ResponseEntity<RegistryHealthDto> getRegistryHealth() {
        log.debug("Getting registry health");
        
        RegistryHealthDto health = serviceRegistryService.getRegistryHealth();
        return ResponseEntity.ok(health);
    }

    @Operation(
        summary = "Get registry statistics",
        description = "Get statistics about the service registry"
    )
    @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully")
    @GetMapping("/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RegistryStatisticsDto> getRegistryStatistics() {
        log.debug("Getting registry statistics");
        
        RegistryStatisticsDto stats = serviceRegistryService.getRegistryStatistics();
        return ResponseEntity.ok(stats);
    }

    @Operation(
        summary = "Search services",
        description = "Search for services based on criteria"
    )
    @ApiResponse(responseCode = "200", description = "Search results retrieved successfully")
    @PostMapping("/services/search")
    @PreAuthorize("hasRole('SERVICE') or hasRole('ADMIN')")
    public ResponseEntity<List<ServiceInfoDto>> searchServices(
            @Valid @RequestBody ServiceSearchRequest request) {
        log.debug("Searching services with criteria: {}", request);
        
        List<ServiceInfoDto> results = serviceRegistryService.searchServices(request);
        return ResponseEntity.ok(results);
    }

    @Operation(
        summary = "Get service topology",
        description = "Get visual representation of service topology"
    )
    @ApiResponse(responseCode = "200", description = "Topology retrieved successfully")
    @GetMapping("/topology")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ServiceTopologyDto> getServiceTopology() {
        log.debug("Getting service topology");
        
        ServiceTopologyDto topology = serviceRegistryService.getServiceTopology();
        return ResponseEntity.ok(topology);
    }

    @Operation(
        summary = "Deregister service",
        description = "Force deregister a service from the registry"
    )
    @ApiResponse(responseCode = "204", description = "Service deregistered successfully")
    @DeleteMapping("/services/{serviceName}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deregisterService(
            @Parameter(description = "Service name") @PathVariable String serviceName) {
        log.warn("Deregistering service {}", serviceName);

        serviceRegistryService.deregisterService(serviceName);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Get service alerts",
        description = "Get active alerts for services"
    )
    @ApiResponse(responseCode = "200", description = "Alerts retrieved successfully")
    @GetMapping("/alerts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ServiceAlertDto>> getServiceAlerts() {
        log.debug("Getting service alerts");
        
        List<ServiceAlertDto> alerts = serviceRegistryService.getServiceAlerts();
        return ResponseEntity.ok(alerts);
    }

    @Operation(
        summary = "Acknowledge alert",
        description = "Acknowledge a service alert"
    )
    @ApiResponse(responseCode = "204", description = "Alert acknowledged successfully")
    @PutMapping("/alerts/{alertId}/acknowledge")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> acknowledgeAlert(
            @Parameter(description = "Alert ID") @PathVariable String alertId) {
        log.info("Acknowledging alert {}", alertId);

        serviceRegistryService.acknowledgeAlert(alertId, "system");
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Export registry data",
        description = "Export registry data for backup or analysis"
    )
    @ApiResponse(responseCode = "200", description = "Data exported successfully")
    @GetMapping("/export")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RegistryExportDto> exportRegistryData(
            @RequestParam(defaultValue = "JSON") ExportFormat format) {
        log.info("Exporting registry data in {} format", format);

        RegistryExportDto export = serviceRegistryService.exportRegistryData(format);
        return ResponseEntity.ok(export);
    }
}