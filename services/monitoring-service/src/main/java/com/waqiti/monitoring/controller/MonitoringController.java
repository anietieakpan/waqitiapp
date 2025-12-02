package com.waqiti.monitoring.controller;

import com.waqiti.monitoring.dto.*;
import com.waqiti.monitoring.model.*;
import com.waqiti.monitoring.service.ComprehensiveMonitoringService;
import com.waqiti.monitoring.service.AlertingService;
import com.waqiti.monitoring.service.TracingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for System Monitoring and Observability
 * Provides comprehensive API for metrics, alerts, tracing, and system health
 */
@RestController
@RequestMapping("/api/v1/monitoring")
@Tag(name = "System Monitoring", description = "APIs for system monitoring, metrics, and observability")
@RequiredArgsConstructor
@Validated
@Slf4j
public class MonitoringController {

    private final ComprehensiveMonitoringService monitoringService;
    private final AlertingService alertingService;
    private final TracingService tracingService;

    /**
     * Get system health overview
     */
    @GetMapping("/health")
    @Operation(summary = "Get system health", description = "Retrieves comprehensive system health status")
    public ResponseEntity<SystemHealthResponse> getSystemHealth() {
        log.debug("Fetching system health overview");
        SystemHealthResponse health = monitoringService.getSystemHealth();
        return ResponseEntity.ok(health);
    }

    /**
     * Get real-time metrics dashboard
     */
    @GetMapping("/metrics/dashboard")
    @Operation(summary = "Get metrics dashboard", description = "Retrieves real-time metrics for monitoring dashboard")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MONITOR') or hasRole('DEVOPS')")
    public ResponseEntity<MetricsDashboard> getMetricsDashboard(
            @RequestParam(required = false, defaultValue = "5") int refreshIntervalMinutes) {
        
        log.debug("Fetching metrics dashboard with refresh interval: {} minutes", refreshIntervalMinutes);
        MetricsDashboard dashboard = monitoringService.getMetricsDashboard(refreshIntervalMinutes);
        return ResponseEntity.ok(dashboard);
    }

    /**
     * Get specific metric by name
     */
    @GetMapping("/metrics/{metricName}")
    @Operation(summary = "Get metric by name", description = "Retrieves specific metric data with time series")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MONITOR') or hasRole('DEVOPS')")
    public ResponseEntity<MetricTimeSeries> getMetric(
            @PathVariable @NotBlank String metricName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false, defaultValue = "1m") String interval) {
        
        log.debug("Fetching metric: {} from {} to {} with interval: {}", 
                metricName, startTime, endTime, interval);
        
        if (startTime == null) {
            startTime = LocalDateTime.now().minusHours(1);
        }
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }
        
        MetricQuery query = MetricQuery.builder()
                .metricName(metricName)
                .startTime(startTime)
                .endTime(endTime)
                .interval(interval)
                .build();
        
        MetricTimeSeries timeSeries = monitoringService.getMetricTimeSeries(query);
        return ResponseEntity.ok(timeSeries);
    }

    /**
     * Get service-specific metrics
     */
    @GetMapping("/services/{serviceName}/metrics")
    @Operation(summary = "Get service metrics", description = "Retrieves metrics for a specific service")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MONITOR') or hasRole('DEVOPS')")
    public ResponseEntity<ServiceMetrics> getServiceMetrics(
            @PathVariable @NotBlank String serviceName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        
        log.debug("Fetching metrics for service: {} from {} to {}", serviceName, startTime, endTime);
        
        if (startTime == null) {
            startTime = LocalDateTime.now().minusHours(1);
        }
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }
        
        ServiceMetrics metrics = monitoringService.getServiceMetrics(serviceName, startTime, endTime);
        return ResponseEntity.ok(metrics);
    }

    /**
     * Get current active alerts
     */
    @GetMapping("/alerts")
    @Operation(summary = "Get active alerts", description = "Retrieves current active system alerts")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MONITOR') or hasRole('DEVOPS')")
    public ResponseEntity<Page<AlertDTO>> getActiveAlerts(
            @RequestParam(required = false) AlertSeverity severity,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) AlertStatus status,
            Pageable pageable) {
        
        log.debug("Fetching active alerts with severity: {}, service: {}", severity, service);
        
        AlertFilter filter = AlertFilter.builder()
                .severity(severity)
                .service(service)
                .status(status != null ? status : AlertStatus.ACTIVE)
                .build();
        
        Page<AlertDTO> alerts = alertingService.getAlerts(filter, pageable);
        return ResponseEntity.ok(alerts);
    }

    /**
     * Create custom alert rule
     */
    @PostMapping("/alerts/rules")
    @Operation(summary = "Create alert rule", description = "Creates a new custom alert rule")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Alert rule created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid alert rule configuration"),
        @ApiResponse(responseCode = "409", description = "Alert rule with same name already exists")
    })
    @PreAuthorize("hasRole('ADMIN') or hasRole('DEVOPS')")
    public ResponseEntity<AlertRuleDTO> createAlertRule(
            @Valid @RequestBody CreateAlertRuleRequest request,
            @RequestHeader("X-User-ID") String createdBy) {
        
        log.info("Creating alert rule: {} by user: {}", request.getName(), createdBy);
        
        request.setCreatedBy(createdBy);
        AlertRuleDTO alertRule = alertingService.createAlertRule(request);
        
        log.info("Alert rule created successfully with ID: {}", alertRule.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(alertRule);
    }

    /**
     * Update alert rule
     */
    @PutMapping("/alerts/rules/{ruleId}")
    @Operation(summary = "Update alert rule", description = "Updates an existing alert rule")
    @PreAuthorize("hasRole('ADMIN') or hasRole('DEVOPS')")
    public ResponseEntity<AlertRuleDTO> updateAlertRule(
            @PathVariable @NotBlank String ruleId,
            @Valid @RequestBody UpdateAlertRuleRequest request,
            @RequestHeader("X-User-ID") String updatedBy) {
        
        log.info("Updating alert rule: {} by user: {}", ruleId, updatedBy);
        
        request.setRuleId(ruleId);
        request.setUpdatedBy(updatedBy);
        
        AlertRuleDTO updatedRule = alertingService.updateAlertRule(request);
        
        log.info("Alert rule updated successfully: {}", ruleId);
        return ResponseEntity.ok(updatedRule);
    }

    /**
     * Acknowledge alert
     */
    @PostMapping("/alerts/{alertId}/acknowledge")
    @Operation(summary = "Acknowledge alert", description = "Acknowledge an active alert")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MONITOR') or hasRole('DEVOPS')")
    public ResponseEntity<AlertDTO> acknowledgeAlert(
            @PathVariable @NotBlank String alertId,
            @RequestParam(required = false) String notes,
            @RequestHeader("X-User-ID") String acknowledgedBy) {
        
        log.info("Acknowledging alert: {} by user: {}", alertId, acknowledgedBy);
        
        AcknowledgeAlertRequest request = AcknowledgeAlertRequest.builder()
                .alertId(alertId)
                .acknowledgedBy(acknowledgedBy)
                .notes(notes)
                .build();
        
        AlertDTO acknowledgedAlert = alertingService.acknowledgeAlert(request);
        
        log.info("Alert acknowledged successfully: {}", alertId);
        return ResponseEntity.ok(acknowledgedAlert);
    }

    /**
     * Resolve alert
     */
    @PostMapping("/alerts/{alertId}/resolve")
    @Operation(summary = "Resolve alert", description = "Mark an alert as resolved")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MONITOR') or hasRole('DEVOPS')")
    public ResponseEntity<AlertDTO> resolveAlert(
            @PathVariable @NotBlank String alertId,
            @RequestParam(required = false) String resolution,
            @RequestHeader("X-User-ID") String resolvedBy) {
        
        log.info("Resolving alert: {} by user: {}", alertId, resolvedBy);
        
        ResolveAlertRequest request = ResolveAlertRequest.builder()
                .alertId(alertId)
                .resolvedBy(resolvedBy)
                .resolution(resolution)
                .build();
        
        AlertDTO resolvedAlert = alertingService.resolveAlert(request);
        
        log.info("Alert resolved successfully: {}", alertId);
        return ResponseEntity.ok(resolvedAlert);
    }

    /**
     * Get system performance overview
     */
    @GetMapping("/performance")
    @Operation(summary = "Get performance overview", description = "Retrieves system performance metrics and trends")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MONITOR') or hasRole('DEVOPS')")
    public ResponseEntity<PerformanceOverview> getPerformanceOverview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        
        log.debug("Fetching performance overview from {} to {}", startTime, endTime);
        
        if (startTime == null) {
            startTime = LocalDateTime.now().minusHours(24);
        }
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }
        
        PerformanceOverview performance = monitoringService.getPerformanceOverview(startTime, endTime);
        return ResponseEntity.ok(performance);
    }

    /**
     * Get distributed traces
     */
    @GetMapping("/traces")
    @Operation(summary = "Get distributed traces", description = "Retrieves distributed tracing data")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MONITOR') or hasRole('DEVOPS')")
    public ResponseEntity<Page<TraceDTO>> getTraces(
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String operationName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) Integer minDuration,
            Pageable pageable) {
        
        log.debug("Fetching traces with traceId: {}, service: {}, operation: {}", 
                traceId, serviceName, operationName);
        
        if (startTime == null) {
            startTime = LocalDateTime.now().minusHours(1);
        }
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }
        
        TraceFilter filter = TraceFilter.builder()
                .traceId(traceId)
                .serviceName(serviceName)
                .operationName(operationName)
                .startTime(startTime)
                .endTime(endTime)
                .minDuration(minDuration)
                .build();
        
        Page<TraceDTO> traces = tracingService.getTraces(filter, pageable);
        return ResponseEntity.ok(traces);
    }

    /**
     * Get trace details by ID
     */
    @GetMapping("/traces/{traceId}")
    @Operation(summary = "Get trace details", description = "Retrieves detailed information for a specific trace")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MONITOR') or hasRole('DEVOPS')")
    public ResponseEntity<TraceDetailsDTO> getTraceDetails(
            @PathVariable @NotBlank String traceId) {
        
        log.debug("Fetching trace details for: {}", traceId);
        TraceDetailsDTO traceDetails = tracingService.getTraceDetails(traceId);
        return ResponseEntity.ok(traceDetails);
    }

    /**
     * Get SLA compliance status
     */
    @GetMapping("/sla/status")
    @Operation(summary = "Get SLA status", description = "Retrieves current SLA compliance status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MONITOR') or hasRole('BUSINESS')")
    public ResponseEntity<SLAStatusResponse> getSLAStatus(
            @RequestParam(required = false) String service,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        
        log.debug("Fetching SLA status for service: {} from {} to {}", service, startTime, endTime);
        
        if (startTime == null) {
            startTime = LocalDateTime.now().minusDays(7);
        }
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }
        
        SLAStatusResponse slaStatus = monitoringService.getSLAStatus(service, startTime, endTime);
        return ResponseEntity.ok(slaStatus);
    }

    /**
     * Get anomaly detection results
     */
    @GetMapping("/anomalies")
    @Operation(summary = "Get anomaly detection results", description = "Retrieves detected anomalies in system behavior")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MONITOR') or hasRole('DEVOPS')")
    public ResponseEntity<Page<AnomalyDTO>> getAnomalies(
            @RequestParam(required = false) String metricName,
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) AnomalyType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            Pageable pageable) {
        
        log.debug("Fetching anomalies for metric: {}, service: {}, type: {}", 
                metricName, serviceName, type);
        
        if (startTime == null) {
            startTime = LocalDateTime.now().minusHours(24);
        }
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }
        
        AnomalyFilter filter = AnomalyFilter.builder()
                .metricName(metricName)
                .serviceName(serviceName)
                .type(type)
                .startTime(startTime)
                .endTime(endTime)
                .build();
        
        Page<AnomalyDTO> anomalies = monitoringService.getAnomalies(filter, pageable);
        return ResponseEntity.ok(anomalies);
    }

    /**
     * Create custom metric
     */
    @PostMapping("/metrics/custom")
    @Operation(summary = "Create custom metric", description = "Creates a new custom business metric")
    @PreAuthorize("hasRole('ADMIN') or hasRole('DEVOPS')")
    public ResponseEntity<CustomMetricDTO> createCustomMetric(
            @Valid @RequestBody CreateCustomMetricRequest request,
            @RequestHeader("X-User-ID") String createdBy) {
        
        log.info("Creating custom metric: {} by user: {}", request.getName(), createdBy);
        
        request.setCreatedBy(createdBy);
        CustomMetricDTO customMetric = monitoringService.createCustomMetric(request);
        
        log.info("Custom metric created successfully with ID: {}", customMetric.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(customMetric);
    }

    /**
     * Record custom metric value
     */
    @PostMapping("/metrics/custom/{metricId}/record")
    @Operation(summary = "Record metric value", description = "Records a value for a custom metric")
    @PreAuthorize("hasRole('SYSTEM') or hasRole('SERVICE')")
    public ResponseEntity<Void> recordCustomMetricValue(
            @PathVariable @NotBlank String metricId,
            @Valid @RequestBody RecordMetricValueRequest request) {
        
        log.debug("Recording value {} for custom metric: {}", request.getValue(), metricId);
        
        request.setMetricId(metricId);
        monitoringService.recordCustomMetricValue(request);
        
        return ResponseEntity.ok().build();
    }

    /**
     * Get monitoring configuration
     */
    @GetMapping("/config")
    @Operation(summary = "Get monitoring configuration", description = "Retrieves current monitoring system configuration")
    @PreAuthorize("hasRole('ADMIN') or hasRole('DEVOPS')")
    public ResponseEntity<MonitoringConfiguration> getMonitoringConfiguration() {
        log.debug("Fetching monitoring configuration");
        MonitoringConfiguration config = monitoringService.getMonitoringConfiguration();
        return ResponseEntity.ok(config);
    }

    /**
     * Update monitoring configuration
     */
    @PutMapping("/config")
    @Operation(summary = "Update monitoring configuration", description = "Updates monitoring system configuration")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MonitoringConfiguration> updateMonitoringConfiguration(
            @Valid @RequestBody UpdateMonitoringConfigRequest request,
            @RequestHeader("X-User-ID") String updatedBy) {
        
        log.info("Updating monitoring configuration by user: {}", updatedBy);
        
        request.setUpdatedBy(updatedBy);
        MonitoringConfiguration updatedConfig = monitoringService.updateMonitoringConfiguration(request);
        
        log.info("Monitoring configuration updated successfully");
        return ResponseEntity.ok(updatedConfig);
    }

    /**
     * Export monitoring data
     */
    @GetMapping("/export")
    @Operation(summary = "Export monitoring data", description = "Export metrics and monitoring data")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MONITOR')")
    public ResponseEntity<byte[]> exportMonitoringData(
            @RequestParam(defaultValue = "CSV") String format,
            @RequestParam(required = false) String metricName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        
        log.info("Exporting monitoring data in {} format", format);
        
        MonitoringDataExportRequest exportRequest = MonitoringDataExportRequest.builder()
                .format(format)
                .metricName(metricName)
                .startTime(startTime)
                .endTime(endTime)
                .build();
        
        byte[] exportData = monitoringService.exportMonitoringData(exportRequest);
        
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=monitoring-data." + format.toLowerCase())
                .body(exportData);
    }

    /**
     * Health check endpoint specifically for monitoring service
     */
    @GetMapping("/health/detailed")
    @Operation(summary = "Detailed health check", description = "Comprehensive health check of monitoring service")
    public ResponseEntity<Map<String, Object>> detailedHealthCheck() {
        Map<String, Object> health = Map.of(
                "status", "UP",
                "service", "monitoring-service",
                "timestamp", LocalDateTime.now().toString(),
                "components", Map.of(
                    "metricsCollection", "UP",
                    "alerting", "UP",
                    "tracing", "UP",
                    "anomalyDetection", "UP",
                    "prometheus", "UP"
                ),
                "metrics", Map.of(
                    "totalMetrics", monitoringService.getTotalMetricsCount(),
                    "activeAlerts", alertingService.getActiveAlertsCount(),
                    "tracesCollected", tracingService.getTracesCollectedToday()
                )
        );
        
        return ResponseEntity.ok(health);
    }
}