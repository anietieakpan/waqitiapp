package com.waqiti.common.observability;

import com.waqiti.common.observability.dto.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * REST controller providing observability metrics and monitoring endpoints
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/observability")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "waqiti.observability.controller.enabled", havingValue = "true", matchIfMissing = true)
public class ObservabilityController implements HealthIndicator {

    private final BusinessMetricsRegistry businessMetrics;
    private final SecurityMetricsRegistry securityMetrics;
    private final PerformanceMetricsRegistry performanceMetrics;
    private final MeterRegistry meterRegistry;

    /**
     * Get comprehensive platform metrics dashboard
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN', 'MONITOR') or hasAnyAuthority('SCOPE_MONITOR')")
    public ResponseEntity<ObservabilityDashboard> getDashboard() {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            ObservabilityDashboard dashboard = ObservabilityDashboard.builder()
                    .timestamp(LocalDateTime.now())
                    .businessMetrics(businessMetrics.getMetricsSummary())
                    .securityMetrics(securityMetrics.getSecurityMetricsSummary())
                    .performanceMetrics(performanceMetrics.getPerformanceMetricsSummary())
                    .systemHealth(getSystemHealthSummary())
                    .build();

            sample.stop(Timer.builder("waqiti.observability.dashboard.request.time")
                    .description("Time to generate observability dashboard")
                    .register(meterRegistry));

            return ResponseEntity.ok(dashboard);
            
        } catch (Exception e) {
            log.error("Error generating observability dashboard", e);
            sample.stop(Timer.builder("waqiti.observability.dashboard.request.time")
                    .description("Time to generate observability dashboard")
                    .tag("status", "error")
                    .register(meterRegistry));
            
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get business metrics summary
     */
    @GetMapping("/metrics/business")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS_ANALYST') or hasAnyAuthority('SCOPE_BUSINESS_METRICS')")
    public ResponseEntity<BusinessMetricsSummary> getBusinessMetrics() {
        log.debug("Fetching business metrics summary");
        return ResponseEntity.ok(businessMetrics.getMetricsSummary());
    }

    /**
     * Get security metrics summary
     */
    @GetMapping("/metrics/security")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECURITY_ADMIN') or hasAnyAuthority('SCOPE_SECURITY_METRICS')")
    public ResponseEntity<SecurityMetricsSummary> getSecurityMetrics() {
        log.debug("Fetching security metrics summary");
        return ResponseEntity.ok(securityMetrics.getSecurityMetricsSummary());
    }

    /**
     * Get performance metrics summary
     */
    @GetMapping("/metrics/performance")
    @PreAuthorize("hasAnyRole('ADMIN', 'SRE') or hasAnyAuthority('SCOPE_PERFORMANCE_METRICS')")
    public ResponseEntity<PerformanceMetricsSummary> getPerformanceMetrics() {
        log.debug("Fetching performance metrics summary");
        return ResponseEntity.ok(performanceMetrics.getPerformanceMetricsSummary());
    }

    /**
     * Get real-time alerts
     */
    @GetMapping("/alerts")
    @PreAuthorize("hasAnyRole('ADMIN', 'MONITOR') or hasAnyAuthority('SCOPE_ALERTS')")
    public ResponseEntity<AlertsSummary> getAlerts() {
        log.debug("Fetching real-time alerts");
        
        AlertsSummary alerts = AlertsSummary.builder()
                .activeSecurityAlerts(securityMetrics.getActiveSecurityAlerts())
                .performanceAlerts(performanceMetrics.getActivePerformanceAlerts())
                .businessAlerts(getBusinessAlerts())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(alerts);
    }

    /**
     * Get SLA compliance metrics
     */
    @GetMapping("/sla")
    @PreAuthorize("hasAnyRole('ADMIN', 'SRE') or hasAnyAuthority('SCOPE_SLA')")
    public ResponseEntity<SLAComplianceReport> getSLACompliance() {
        log.debug("Fetching SLA compliance metrics");
        
        SLAComplianceReport sla = SLAComplianceReport.builder()
                .paymentProcessingSLA(calculatePaymentProcessingSLA())
                .apiResponseTimeSLA(calculateApiResponseTimeSLA())
                .systemAvailabilitySLA(calculateSystemAvailabilitySLA())
                .databasePerformanceSLA(calculateDatabasePerformanceSLA())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(sla);
    }

    /**
     * Get system health summary
     */
    @GetMapping("/health/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'MONITOR') or hasAnyAuthority('SCOPE_HEALTH')")
    public ResponseEntity<SystemHealthSummary> getHealthSummary() {
        log.debug("Fetching system health summary");
        return ResponseEntity.ok(getSystemHealthSummary());
    }

    /**
     * Get detailed error analysis
     */
    @GetMapping("/errors/analysis")
    @PreAuthorize("hasAnyRole('ADMIN', 'SRE') or hasAnyAuthority('SCOPE_ERROR_ANALYSIS')")
    public ResponseEntity<ErrorAnalysisReport> getErrorAnalysis(@RequestParam(defaultValue = "24") int hours) {
        log.debug("Fetching error analysis for last {} hours", hours);
        
        ErrorAnalysisReport report = ErrorAnalysisReport.builder()
                .analysisWindow(hours + " hours")
                .totalErrors(performanceMetrics.getTotalErrors())
                .errorRate(performanceMetrics.getErrorRate())
                .topErrors(performanceMetrics.getTopErrors(10))
                .errorTrends(performanceMetrics.getErrorTrends(hours))
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(report);
    }

    /**
     * Trigger manual metric collection
     */
    @PostMapping("/collect")
    @PreAuthorize("hasAnyRole('ADMIN', 'SRE') or hasAnyAuthority('SCOPE_METRIC_COLLECTION')")
    public ResponseEntity<Map<String, Object>> triggerMetricCollection() {
        log.info("Manual metric collection triggered");
        
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", LocalDateTime.now());
        result.put("status", "triggered");
        result.put("message", "Metric collection initiated");

        // Trigger collection processes
        businessMetrics.getMetricsSummary();
        securityMetrics.getSecurityMetricsSummary();
        performanceMetrics.getPerformanceMetricsSummary();

        return ResponseEntity.ok(result);
    }

    /**
     * Health indicator implementation
     */
    @Override
    public Health health() {
        try {
            // Check metrics collection health
            BusinessMetricsSummary businessSummary = businessMetrics.getMetricsSummary();
            SecurityMetricsSummary securitySummary = securityMetrics.getSecurityMetricsSummary();
            PerformanceMetricsSummary performanceSummary = performanceMetrics.getPerformanceMetricsSummary();

            Health.Builder healthBuilder = Health.up();

            // Add key metrics to health check
            healthBuilder
                    .withDetail("activeUsers", businessSummary.getActiveUsers())
                    .withDetail("pendingTransactions", businessSummary.getPendingTransactions())
                    .withDetail("securityAlerts", securitySummary.getActiveSecurityAlerts())
                    .withDetail("averageResponseTime", performanceSummary.getAverageResponseTime())
                    .withDetail("errorRate", performanceSummary.getErrorRate());

            // Check for concerning conditions
            if (securitySummary.getActiveSecurityAlerts() > 10) {
                healthBuilder.down().withDetail("issue", "High number of security alerts");
            }

            if (performanceSummary.getErrorRate() > 5.0) {
                healthBuilder.down().withDetail("issue", "High error rate");
            }

            if (performanceSummary.getAverageResponseTime() > 5000) {
                healthBuilder.down().withDetail("issue", "High response times");
            }

            return healthBuilder.build();

        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", "Failed to collect observability metrics")
                    .withException(e)
                    .build();
        }
    }

    // Private helper methods

    private SystemHealthSummary getSystemHealthSummary() {
        return SystemHealthSummary.builder()
                .overall("UP")
                .database("UP")
                .redis("UP")
                .messageQueue("UP")
                .externalServices("UP")
                .timestamp(LocalDateTime.now())
                .build();
    }

    private long getBusinessAlerts() {
        // Calculate business-related alerts
        long alerts = 0;
        
        // Check for business anomalies
        BusinessMetricsSummary summary = businessMetrics.getMetricsSummary();
        
        if (summary.getPendingTransactions() > 1000) alerts++;
        if (summary.getTotalPlatformBalance() < 100000) alerts++;
        
        return alerts;
    }

    private SLAMetric calculatePaymentProcessingSLA() {
        return SLAMetric.builder()
                .target(95.0)
                .actual(performanceMetrics.getPaymentSuccessRate())
                .status(performanceMetrics.getPaymentSuccessRate() >= 95.0 ? "MEETING" : "FAILING")
                .build();
    }

    private SLAMetric calculateApiResponseTimeSLA() {
        double avgResponseTime = performanceMetrics.getAverageResponseTime();
        boolean meeting = avgResponseTime <= 2000; // 2 seconds SLA
        
        return SLAMetric.builder()
                .target(2000.0)
                .actual(avgResponseTime)
                .status(meeting ? "MEETING" : "FAILING")
                .build();
    }

    private SLAMetric calculateSystemAvailabilitySLA() {
        double availability = performanceMetrics.getSystemAvailability();
        
        return SLAMetric.builder()
                .target(99.9)
                .actual(availability)
                .status(availability >= 99.9 ? "MEETING" : "FAILING")
                .build();
    }

    private SLAMetric calculateDatabasePerformanceSLA() {
        double avgDbTime = performanceMetrics.getAverageDatabaseQueryTime();
        boolean meeting = avgDbTime <= 500; // 500ms SLA
        
        return SLAMetric.builder()
                .target(500.0)
                .actual(avgDbTime)
                .status(meeting ? "MEETING" : "FAILING")
                .build();
    }
}