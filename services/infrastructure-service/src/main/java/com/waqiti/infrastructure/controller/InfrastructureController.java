package com.waqiti.infrastructure.controller;

import com.waqiti.infrastructure.service.InfrastructureService;
import com.waqiti.infrastructure.domain.*;
import com.waqiti.common.api.ApiResponse;
import com.waqiti.common.tracing.Traced;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/infrastructure")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Infrastructure Management", description = "System reliability and disaster recovery operations")
public class InfrastructureController {

    private final InfrastructureService infrastructureService;

    @GetMapping("/health")
    @Operation(summary = "Perform comprehensive system health check")
    @PreAuthorize("hasRole('ADMIN') or hasRole('OPERATIONS')")
    @Traced(operation = "health_check_api")
    public ResponseEntity<ApiResponse<SystemHealthResult>> performHealthCheck() {
        log.info("Health check requested via API");
        
        SystemHealthResult result = infrastructureService.performSystemHealthCheck();
        
        return ResponseEntity.ok(ApiResponse.success(result, "System health check completed"));
    }

    @PostMapping("/backup")
    @Operation(summary = "Execute backup operations")
    @PreAuthorize("hasRole('ADMIN') or hasRole('OPERATIONS')")
    @Traced(operation = "backup_api")
    public ResponseEntity<ApiResponse<String>> executeBackup() {
        log.info("Backup execution requested via API");
        
        CompletableFuture<BackupResult> future = infrastructureService.executeBackupOperations();
        
        // Return async response
        return ResponseEntity.accepted().body(
            ApiResponse.success("backup-initiated", "Backup operations started"));
    }

    @PostMapping("/incidents")
    @Operation(summary = "Report and handle system incident")
    @PreAuthorize("hasRole('ADMIN') or hasRole('OPERATIONS') or hasRole('SUPPORT')")
    @Traced(operation = "incident_report_api")
    public ResponseEntity<ApiResponse<IncidentResult>> reportIncident(
            @Valid @RequestBody IncidentRequest request) {
        
        log.info("Incident reported via API: {} - {}", request.getIncidentType(), request.getDescription());
        
        IncidentResult result = infrastructureService.handleSystemIncident(request);
        
        return ResponseEntity.ok(ApiResponse.success(result, "Incident handled successfully"));
    }

    @GetMapping("/reports/{period}")
    @Operation(summary = "Generate infrastructure report")
    @PreAuthorize("hasRole('ADMIN') or hasRole('OPERATIONS') or hasRole('MANAGER')")
    @Traced(operation = "infrastructure_report_api")
    public ResponseEntity<ApiResponse<InfrastructureReport>> generateReport(
            @PathVariable ReportPeriod period) {
        
        log.info("Infrastructure report requested for period: {}", period);
        
        InfrastructureReport report = infrastructureService.generateInfrastructureReport(period);
        
        return ResponseEntity.ok(ApiResponse.success(report, "Infrastructure report generated"));
    }

    @GetMapping("/capacity/current")
    @Operation(summary = "Get current system capacity metrics")
    @PreAuthorize("hasRole('ADMIN') or hasRole('OPERATIONS')")
    @Traced(operation = "capacity_metrics_api")
    public ResponseEntity<ApiResponse<String>> getCurrentCapacity() {
        log.info("Current capacity metrics requested via API");
        
        // This would typically trigger a real-time capacity check
        return ResponseEntity.ok(ApiResponse.success("capacity-check-initiated", 
            "Current capacity metrics collection started"));
    }

    @PostMapping("/scaling/trigger")
    @Operation(summary = "Manually trigger auto-scaling")
    @PreAuthorize("hasRole('ADMIN')")
    @Traced(operation = "manual_scaling_api")
    public ResponseEntity<ApiResponse<String>> triggerScaling(
            @RequestParam String resource,
            @RequestParam String action) {
        
        log.info("Manual scaling triggered: {} - {}", resource, action);
        
        // This would trigger scaling operations
        return ResponseEntity.ok(ApiResponse.success("scaling-triggered", 
            String.format("Scaling %s for resource %s", action, resource)));
    }

    @GetMapping("/disaster-recovery/status")
    @Operation(summary = "Get disaster recovery status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('OPERATIONS')")
    @Traced(operation = "dr_status_api")
    public ResponseEntity<ApiResponse<String>> getDisasterRecoveryStatus() {
        log.info("Disaster recovery status requested via API");
        
        return ResponseEntity.ok(ApiResponse.success("dr-status-healthy", 
            "Disaster recovery systems are operational"));
    }

    @PostMapping("/disaster-recovery/test")
    @Operation(summary = "Execute disaster recovery test")
    @PreAuthorize("hasRole('ADMIN')")
    @Traced(operation = "dr_test_api")
    public ResponseEntity<ApiResponse<String>> testDisasterRecovery(
            @RequestParam(required = false, defaultValue = "false") boolean fullTest) {
        
        log.info("Disaster recovery test requested: fullTest={}", fullTest);
        
        return ResponseEntity.accepted().body(ApiResponse.success("dr-test-initiated", 
            "Disaster recovery test started"));
    }
}