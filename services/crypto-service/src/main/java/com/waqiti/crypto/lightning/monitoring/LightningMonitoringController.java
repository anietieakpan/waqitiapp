package com.waqiti.crypto.lightning.monitoring;

import com.waqiti.crypto.lightning.monitoring.LightningMonitoringService.PerformanceSnapshot;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Lightning Network Monitoring Controller
 * Provides REST endpoints for monitoring metrics, alerts, and system health
 * Supports both operational monitoring and administrative oversight
 */
@RestController
@RequestMapping("/api/v1/lightning/monitoring")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Lightning Monitoring", 
     description = "Lightning Network monitoring, metrics, and alerting")
@SecurityRequirement(name = "bearerAuth")
public class LightningMonitoringController {

    private final LightningMonitoringService monitoringService;
    private final LightningAlertService alertService;

    // ============ METRICS ENDPOINTS ============

    @GetMapping("/metrics")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get current Lightning Network metrics", 
               description = "Returns current system metrics including balances, channels, and performance data")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Metrics retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        @ApiResponse(responseCode = "500", description = "Failed to retrieve metrics")
    })
    public ResponseEntity<MetricsResponse> getCurrentMetrics() {
        log.debug("Admin/Operator requested Lightning Network metrics");
        
        try {
            Map<String, Object> metrics = monitoringService.getCurrentMetrics();
            
            return ResponseEntity.ok(
                MetricsResponse.builder()
                    .timestamp(Instant.now())
                    .metrics(metrics)
                    .status("SUCCESS")
                    .build()
            );
            
        } catch (Exception e) {
            log.error("Failed to retrieve Lightning Network metrics", e);
            return ResponseEntity.internalServerError().body(
                MetricsResponse.builder()
                    .timestamp(Instant.now())
                    .status("ERROR")
                    .error("Failed to retrieve metrics: " + e.getMessage())
                    .build()
            );
        }
    }

    @GetMapping("/metrics/performance")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get performance history", 
               description = "Returns historical performance metrics for trend analysis")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Performance history retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        @ApiResponse(responseCode = "500", description = "Failed to retrieve performance history")
    })
    public ResponseEntity<PerformanceHistoryResponse> getPerformanceHistory(
            @RequestParam(defaultValue = "24") 
            @Parameter(description = "Number of hours of history to return") 
            int hours) {
        
        log.debug("Admin/Operator requested Lightning Network performance history");
        
        try {
            List<PerformanceSnapshot> allSnapshots = monitoringService.getPerformanceHistory();
            
            // Filter snapshots based on requested time range
            Instant cutoff = Instant.now().minus(Duration.ofHours(hours));
            List<PerformanceSnapshot> filteredSnapshots = allSnapshots.stream()
                .filter(snapshot -> snapshot.getTimestamp().isAfter(cutoff))
                .collect(Collectors.toList());
            
            return ResponseEntity.ok(
                PerformanceHistoryResponse.builder()
                    .timestamp(Instant.now())
                    .hoursRequested(hours)
                    .snapshotCount(filteredSnapshots.size())
                    .snapshots(filteredSnapshots)
                    .status("SUCCESS")
                    .build()
            );
            
        } catch (Exception e) {
            log.error("Failed to retrieve Lightning Network performance history", e);
            return ResponseEntity.internalServerError().body(
                PerformanceHistoryResponse.builder()
                    .timestamp(Instant.now())
                    .hoursRequested(hours)
                    .snapshotCount(0)
                    .status("ERROR")
                    .error("Failed to retrieve performance history: " + e.getMessage())
                    .build()
            );
        }
    }

    @GetMapping("/metrics/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'USER')")
    @Operation(summary = "Get Lightning Network summary metrics", 
               description = "Returns high-level system summary suitable for dashboards")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Summary metrics retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        @ApiResponse(responseCode = "500", description = "Failed to retrieve summary")
    })
    public ResponseEntity<SummaryMetricsResponse> getSummaryMetrics() {
        log.debug("User requested Lightning Network summary metrics");
        
        try {
            return ResponseEntity.ok(
                SummaryMetricsResponse.builder()
                    .timestamp(Instant.now())
                    .nodeBalance(monitoringService.getNodeBalance())
                    .channelCount((int) monitoringService.getChannelCount())
                    .activeChannelCount((int) monitoringService.getActiveChannelCount())
                    .totalCapacity(monitoringService.getTotalCapacity())
                    .localBalance(monitoringService.getLocalBalance())
                    .remoteBalance(monitoringService.getRemoteBalance())
                    .peerCount((int) monitoringService.getPeerCount())
                    .syncProgress(monitoringService.getSyncProgress())
                    .status("SUCCESS")
                    .build()
            );
            
        } catch (Exception e) {
            log.error("Failed to retrieve Lightning Network summary metrics", e);
            return ResponseEntity.internalServerError().body(
                SummaryMetricsResponse.builder()
                    .timestamp(Instant.now())
                    .status("ERROR")
                    .error("Failed to retrieve summary: " + e.getMessage())
                    .build()
            );
        }
    }

    // ============ HEALTH ENDPOINTS ============

    @GetMapping("/health")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'USER')")
    @Operation(summary = "Get Lightning Network health status", 
               description = "Returns detailed health information about the Lightning Network")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Health status retrieved successfully"),
        @ApiResponse(responseCode = "500", description = "Health check failed")
    })
    public ResponseEntity<HealthResponse> getHealth() {
        log.debug("User requested Lightning Network health status");
        
        try {
            Health health = monitoringService.health();
            
            return ResponseEntity.ok(
                HealthResponse.builder()
                    .timestamp(Instant.now())
                    .status(health.getStatus().getCode())
                    .details(health.getDetails())
                    .build()
            );
            
        } catch (Exception e) {
            log.error("Failed to retrieve Lightning Network health", e);
            return ResponseEntity.internalServerError().body(
                HealthResponse.builder()
                    .timestamp(Instant.now())
                    .status("ERROR")
                    .error("Failed to retrieve health status: " + e.getMessage())
                    .build()
            );
        }
    }

    @GetMapping("/health/detailed")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get detailed health diagnostics", 
               description = "Returns comprehensive health diagnostics for troubleshooting")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Detailed health retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        @ApiResponse(responseCode = "500", description = "Health diagnostics failed")
    })
    public ResponseEntity<DetailedHealthResponse> getDetailedHealth() {
        log.debug("Admin/Operator requested detailed Lightning Network health");
        
        try {
            Health health = monitoringService.health();
            Map<String, Object> metrics = monitoringService.getCurrentMetrics();
            
            return ResponseEntity.ok(
                DetailedHealthResponse.builder()
                    .timestamp(Instant.now())
                    .overallStatus(health.getStatus().getCode())
                    .healthDetails(health.getDetails())
                    .currentMetrics(metrics)
                    .recommendations(generateHealthRecommendations(health, metrics))
                    .build()
            );
            
        } catch (Exception e) {
            log.error("Failed to retrieve detailed Lightning Network health", e);
            return ResponseEntity.internalServerError().body(
                DetailedHealthResponse.builder()
                    .timestamp(Instant.now())
                    .overallStatus("ERROR")
                    .error("Failed to retrieve detailed health: " + e.getMessage())
                    .build()
            );
        }
    }

    // ============ ALERT ENDPOINTS ============

    @GetMapping("/alerts")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get alert system status", 
               description = "Returns information about the alert system configuration and statistics")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Alert status retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        @ApiResponse(responseCode = "500", description = "Failed to retrieve alert status")
    })
    public ResponseEntity<AlertStatusResponse> getAlertStatus() {
        log.debug("Admin/Operator requested alert system status");
        
        try {
            Map<String, Object> alertStats = alertService.getAlertStatistics();
            List<String> notificationChannels = alertService.getEnabledNotificationChannels();
            
            return ResponseEntity.ok(
                AlertStatusResponse.builder()
                    .timestamp(Instant.now())
                    .alertStatistics(alertStats)
                    .enabledChannels(notificationChannels)
                    .status("SUCCESS")
                    .build()
            );
            
        } catch (Exception e) {
            log.error("Failed to retrieve alert system status", e);
            return ResponseEntity.internalServerError().body(
                AlertStatusResponse.builder()
                    .timestamp(Instant.now())
                    .status("ERROR")
                    .error("Failed to retrieve alert status: " + e.getMessage())
                    .build()
            );
        }
    }

    @PostMapping("/alerts/test")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Send test alert", 
               description = "Sends a test alert through all configured notification channels")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Test alert sent successfully"),
        @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        @ApiResponse(responseCode = "500", description = "Failed to send test alert")
    })
    public ResponseEntity<TestAlertResponse> sendTestAlert(
            @RequestParam(defaultValue = "INFO") 
            @Parameter(description = "Alert severity for test") 
            String severity) {
        
        log.info("Admin requested test alert with severity: {}", severity);
        
        try {
            LightningAlertService.AlertSeverity alertSeverity;
            try {
                alertSeverity = LightningAlertService.AlertSeverity.valueOf(severity.toUpperCase());
            } catch (IllegalArgumentException e) {
                alertSeverity = LightningAlertService.AlertSeverity.INFO;
            }
            
            alertService.sendAlert(
                alertSeverity,
                "TEST_ALERT",
                "This is a test alert from the Lightning Network monitoring system",
                Map.of(
                    "test_time", Instant.now().toString(),
                    "triggered_by", "admin_test",
                    "severity_requested", severity
                )
            );
            
            return ResponseEntity.ok(
                TestAlertResponse.builder()
                    .timestamp(Instant.now())
                    .severity(alertSeverity.toString())
                    .message("Test alert sent successfully")
                    .status("SUCCESS")
                    .build()
            );
            
        } catch (Exception e) {
            log.error("Failed to send test alert", e);
            return ResponseEntity.internalServerError().body(
                TestAlertResponse.builder()
                    .timestamp(Instant.now())
                    .severity(severity)
                    .status("ERROR")
                    .error("Failed to send test alert: " + e.getMessage())
                    .build()
            );
        }
    }

    // ============ UTILITY METHODS ============

    private List<String> generateHealthRecommendations(Health health, Map<String, Object> metrics) {
        List<String> recommendations = new java.util.ArrayList<>();
        
        try {
            String status = health.getStatus().getCode();
            
            if ("DOWN".equals(status) || "CRITICAL".equals(status)) {
                recommendations.add("Critical system issues detected - immediate attention required");
                recommendations.add("Check Lightning Network service logs for errors");
                recommendations.add("Verify Bitcoin node connectivity and synchronization");
                recommendations.add("Consider executing disaster recovery procedures if issues persist");
            }
            
            if ("DEGRADED".equals(status)) {
                recommendations.add("System performance is degraded - investigate performance issues");
                recommendations.add("Check channel balance distribution and consider rebalancing");
                recommendations.add("Monitor payment failure rates and routing performance");
            }
            
            // Specific recommendations based on metrics
            Object channelCount = metrics.get("channel_count");
            if (channelCount instanceof Number && ((Number) channelCount).intValue() < 3) {
                recommendations.add("Consider opening additional channels for better routing redundancy");
            }
            
            Object syncStatus = metrics.get("sync_status");
            if (!"SYNCED".equals(syncStatus)) {
                recommendations.add("Node synchronization is incomplete - wait for full sync or investigate sync issues");
            }
            
            Object paymentSuccessRate = metrics.get("payment_success_rate");
            if (paymentSuccessRate instanceof Number && ((Number) paymentSuccessRate).intValue() < 95) {
                recommendations.add("Payment success rate is low - check channel liquidity and routing paths");
            }
            
            if (recommendations.isEmpty()) {
                recommendations.add("System is operating normally - no immediate action required");
                recommendations.add("Continue regular monitoring and maintenance procedures");
            }
            
        } catch (Exception e) {
            log.debug("Failed to generate health recommendations", e);
            recommendations.add("Unable to generate specific recommendations - check system manually");
        }
        
        return recommendations;
    }

    // ============ RESPONSE CLASSES ============

    @lombok.Builder
    @lombok.Getter
    public static class MetricsResponse {
        private final Instant timestamp;
        private final Map<String, Object> metrics;
        private final String status;
        private final String error;
    }

    @lombok.Builder
    @lombok.Getter
    public static class PerformanceHistoryResponse {
        private final Instant timestamp;
        private final int hoursRequested;
        private final int snapshotCount;
        private final List<PerformanceSnapshot> snapshots;
        private final String status;
        private final String error;
    }

    @lombok.Builder
    @lombok.Getter
    public static class SummaryMetricsResponse {
        private final Instant timestamp;
        private final double nodeBalance;
        private final int channelCount;
        private final int activeChannelCount;
        private final double totalCapacity;
        private final double localBalance;
        private final double remoteBalance;
        private final int peerCount;
        private final double syncProgress;
        private final String status;
        private final String error;
    }

    @lombok.Builder
    @lombok.Getter
    public static class HealthResponse {
        private final Instant timestamp;
        private final String status;
        private final Map<String, Object> details;
        private final String error;
    }

    @lombok.Builder
    @lombok.Getter
    public static class DetailedHealthResponse {
        private final Instant timestamp;
        private final String overallStatus;
        private final Map<String, Object> healthDetails;
        private final Map<String, Object> currentMetrics;
        private final List<String> recommendations;
        private final String error;
    }

    @lombok.Builder
    @lombok.Getter
    public static class AlertStatusResponse {
        private final Instant timestamp;
        private final Map<String, Object> alertStatistics;
        private final List<String> enabledChannels;
        private final String status;
        private final String error;
    }

    @lombok.Builder
    @lombok.Getter
    public static class TestAlertResponse {
        private final Instant timestamp;
        private final String severity;
        private final String message;
        private final String status;
        private final String error;
    }
}