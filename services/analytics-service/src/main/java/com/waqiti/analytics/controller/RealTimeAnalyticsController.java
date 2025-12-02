package com.waqiti.analytics.controller;

import com.waqiti.analytics.engine.RealTimeAnalyticsEngine;
import com.waqiti.analytics.model.*;
import com.waqiti.analytics.processor.UserAnalyticsProcessor;
import com.waqiti.analytics.processor.MerchantAnalyticsProcessor;
import com.waqiti.analytics.processor.FraudAnalyticsProcessor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Enterprise-grade REST controller for real-time analytics
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Validated
@Tag(name = "Real-Time Analytics", description = "Real-time analytics and monitoring endpoints")
@PreAuthorize("hasRole('ANALYTICS_USER')")
public class RealTimeAnalyticsController {

    private final RealTimeAnalyticsEngine analyticsEngine;
    private final UserAnalyticsProcessor userAnalyticsProcessor;
    private final MerchantAnalyticsProcessor merchantAnalyticsProcessor;
    private final FraudAnalyticsProcessor fraudAnalyticsProcessor;

    /**
     * Get real-time transaction statistics
     */
    @GetMapping("/transactions/stats")
    @Operation(
        summary = "Get real-time transaction statistics",
        description = "Retrieve current real-time transaction statistics including volume, amount, and success rates"
    )
    @ApiResponse(responseCode = "200", description = "Successfully retrieved transaction statistics")
    public ResponseEntity<LiveTransactionStatsResponse> getLiveTransactionStats() {
        try {
            TransactionStats stats = analyticsEngine.getRealTimeTransactionStats();
            
            LiveTransactionStatsResponse response = LiveTransactionStatsResponse.builder()
                    .totalTransactions(stats.getTotalTransactions())
                    .totalVolume(stats.getTotalVolume())
                    .averageAmount(stats.getAverageAmount())
                    .successfulTransactions(stats.getSuccessfulTransactions())
                    .failedTransactions(stats.getFailedTransactions())
                    .timestamp(stats.getTimestamp())
                    .period(stats.getPeriod())
                    .build();
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving live transaction stats", e);
            throw new AnalyticsException("Failed to retrieve live transaction statistics", e);
        }
    }

    /**
     * Get transaction metrics for specific time period
     */
    @GetMapping("/transactions/metrics")
    @Operation(
        summary = "Get transaction metrics for time period",
        description = "Retrieve aggregated transaction metrics for a specific time period"
    )
    @ApiResponse(responseCode = "200", description = "Successfully retrieved transaction metrics")
    public ResponseEntity<TransactionMetricsResponse> getTransactionMetrics(
            @Parameter(description = "Time period (hourly, daily, weekly, monthly)")
            @RequestParam(defaultValue = "daily") String period,
            @Parameter(description = "Number of periods to retrieve")
            @RequestParam(defaultValue = "7") @Min(1) @Max(30) int count) {
        
        try {
            TransactionMetrics metrics = analyticsEngine.getTransactionMetrics(period);
            
            TransactionMetricsResponse response = TransactionMetricsResponse.builder()
                    .period(metrics.getPeriod())
                    .transactionCount(metrics.getTransactionCount())
                    .totalVolume(metrics.getTotalVolume())
                    .averageAmount(metrics.getAverageAmount())
                    .maxAmount(metrics.getMaxAmount())
                    .minAmount(metrics.getMinAmount())
                    .successfulTransactions(metrics.getSuccessfulTransactions())
                    .failedTransactions(metrics.getFailedTransactions())
                    .successRate(metrics.getSuccessRate())
                    .lastUpdated(metrics.getLastUpdated())
                    .build();
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving transaction metrics for period: {}", period, e);
            throw new AnalyticsException("Failed to retrieve transaction metrics", e);
        }
    }

    /**
     * Get user analytics
     */
    @GetMapping("/users/{userId}/analytics")
    @Operation(
        summary = "Get user analytics",
        description = "Retrieve comprehensive analytics for a specific user"
    )
    @ApiResponse(responseCode = "200", description = "Successfully retrieved user analytics")
    @PreAuthorize("hasRole('USER_ANALYTICS') and (#userId == authentication.name or hasRole('ADMIN'))")
    public ResponseEntity<UserAnalyticsResponse> getUserAnalytics(
            @Parameter(description = "User ID", required = true)
            @PathVariable @NotBlank String userId) {
        
        try {
            UserAnalytics analytics = userAnalyticsProcessor.getUserAnalytics(userId);
            
            UserAnalyticsResponse response = UserAnalyticsResponse.builder()
                    .userId(analytics.getUserId())
                    .totalTransactions(analytics.getTotalTransactions())
                    .totalSpent(analytics.getTotalSpent())
                    .averageTransactionAmount(analytics.getAverageTransactionAmount())
                    .maxTransactionAmount(analytics.getMaxTransactionAmount())
                    .totalSessions(analytics.getTotalSessions())
                    .firstTransactionDate(analytics.getFirstTransactionDate())
                    .lastTransactionDate(analytics.getLastTransactionDate())
                    .lastActiveDate(analytics.getLastActiveDate())
                    .dailyVelocity(analytics.getDailyVelocity())
                    .weeklyVelocity(analytics.getWeeklyVelocity())
                    .preferredPaymentMethods(analytics.getPreferredPaymentMethods())
                    .merchantPreferences(analytics.getMerchantPreferences())
                    .peakSpendingHour(analytics.getPeakSpendingHour())
                    .loyaltyScore(analytics.getLoyaltyScore())
                    .segment(userAnalyticsProcessor.getUserSegment(userId))
                    .build();
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving user analytics for user: {}", userId, e);
            throw new AnalyticsException("Failed to retrieve user analytics", e);
        }
    }

    /**
     * Get merchant analytics
     */
    @GetMapping("/merchants/{merchantId}/analytics")
    @Operation(
        summary = "Get merchant analytics",
        description = "Retrieve comprehensive analytics for a specific merchant"
    )
    @ApiResponse(responseCode = "200", description = "Successfully retrieved merchant analytics")
    @PreAuthorize("hasRole('MERCHANT_ANALYTICS')")
    public ResponseEntity<MerchantAnalyticsResponse> getMerchantAnalytics(
            @Parameter(description = "Merchant ID", required = true)
            @PathVariable @NotBlank String merchantId) {
        
        try {
            MerchantAnalytics analytics = merchantAnalyticsProcessor.getMerchantAnalytics(merchantId);
            
            MerchantAnalyticsResponse response = MerchantAnalyticsResponse.builder()
                    .merchantId(analytics.getMerchantId())
                    .merchantName(analytics.getMerchantName())
                    .totalTransactions(analytics.getTotalTransactions())
                    .totalRevenue(analytics.getTotalRevenue())
                    .averageTransactionAmount(analytics.getAverageTransactionAmount())
                    .maxTransactionAmount(analytics.getMaxTransactionAmount())
                    .uniqueCustomers(analytics.getUniqueCustomers())
                    .averageTransactionsPerCustomer(analytics.getAverageTransactionsPerCustomer())
                    .customerLifetimeValue(analytics.getCustomerLifetimeValue())
                    .repeatCustomerRate(analytics.getRepeatCustomerRate())
                    .successRate(analytics.getSuccessRate())
                    .revenueGrowthRate(analytics.getRevenueGrowthRate())
                    .churnRate(analytics.getChurnRate())
                    .paymentMethodBreakdown(analytics.getPaymentMethodBreakdown())
                    .currencyBreakdown(analytics.getCurrencyBreakdown())
                    .peakHour(analytics.getPeakHour())
                    .fraudRate(analytics.getFraudRate())
                    .disputeRate(analytics.getDisputeRate())
                    .riskLevel(analytics.getRiskLevel())
                    .build();
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving merchant analytics for merchant: {}", merchantId, e);
            throw new AnalyticsException("Failed to retrieve merchant analytics", e);
        }
    }

    /**
     * Get trending patterns
     */
    @GetMapping("/trends")
    @Operation(
        summary = "Get trending patterns",
        description = "Retrieve current trending patterns in transaction data"
    )
    @ApiResponse(responseCode = "200", description = "Successfully retrieved trending patterns")
    public ResponseEntity<List<TrendingPattern>> getTrendingPatterns(
            @Parameter(description = "Limit number of results")
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        
        try {
            List<TrendingPattern> patterns = analyticsEngine.getTrendingPatterns();
            
            return ResponseEntity.ok(patterns.stream()
                    .limit(limit)
                    .toList());
        } catch (Exception e) {
            log.error("Error retrieving trending patterns", e);
            throw new AnalyticsException("Failed to retrieve trending patterns", e);
        }
    }

    /**
     * Get anomaly detection results
     */
    @GetMapping("/anomalies")
    @Operation(
        summary = "Get anomaly detection results",
        description = "Retrieve current anomalies detected in the system"
    )
    @ApiResponse(responseCode = "200", description = "Successfully retrieved anomalies")
    @PreAuthorize("hasRole('FRAUD_ANALYST')")
    public ResponseEntity<List<AnomalyDetectionResult>> getAnomalies(
            @Parameter(description = "Severity filter (LOW, MEDIUM, HIGH, CRITICAL)")
            @RequestParam(required = false) String severity,
            @Parameter(description = "Limit number of results")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        
        try {
            List<AnomalyDetectionResult> anomalies = fraudAnalyticsProcessor.detectAnomalies();
            
            // Filter by severity if provided
            if (severity != null) {
                anomalies = anomalies.stream()
                        .filter(anomaly -> severity.equals(anomaly.getSeverity()))
                        .toList();
            }
            
            return ResponseEntity.ok(anomalies.stream()
                    .limit(limit)
                    .toList());
        } catch (Exception e) {
            log.error("Error retrieving anomalies", e);
            throw new AnalyticsException("Failed to retrieve anomalies", e);
        }
    }

    /**
     * Get top merchants by revenue
     */
    @GetMapping("/merchants/top-by-revenue")
    @Operation(
        summary = "Get top merchants by revenue",
        description = "Retrieve merchants ranked by total revenue"
    )
    @ApiResponse(responseCode = "200", description = "Successfully retrieved top merchants")
    @PreAuthorize("hasRole('MERCHANT_ANALYTICS')")
    public ResponseEntity<List<MerchantRankingResult>> getTopMerchantsByRevenue(
            @Parameter(description = "Number of top merchants to retrieve")
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        
        try {
            List<MerchantRankingResult> topMerchants = merchantAnalyticsProcessor.getTopMerchantsByRevenue(limit);
            return ResponseEntity.ok(topMerchants);
        } catch (Exception e) {
            log.error("Error retrieving top merchants by revenue", e);
            throw new AnalyticsException("Failed to retrieve top merchants", e);
        }
    }

    /**
     * Get merchant performance comparison
     */
    @GetMapping("/merchants/{merchantId}/performance-comparison")
    @Operation(
        summary = "Get merchant performance comparison",
        description = "Compare merchant performance against industry averages"
    )
    @ApiResponse(responseCode = "200", description = "Successfully retrieved performance comparison")
    @PreAuthorize("hasRole('MERCHANT_ANALYTICS')")
    public ResponseEntity<MerchantPerformanceComparison> getMerchantPerformanceComparison(
            @Parameter(description = "Merchant ID", required = true)
            @PathVariable @NotBlank String merchantId) {
        
        try {
            MerchantPerformanceComparison comparison = merchantAnalyticsProcessor.getMerchantPerformanceComparison(merchantId);
            return ResponseEntity.ok(comparison);
        } catch (Exception e) {
            log.error("Error retrieving merchant performance comparison for merchant: {}", merchantId, e);
            throw new AnalyticsException("Failed to retrieve performance comparison", e);
        }
    }

    /**
     * Get system health metrics
     */
    @GetMapping("/system/health")
    @Operation(
        summary = "Get system health metrics",
        description = "Retrieve overall system health and performance metrics"
    )
    @ApiResponse(responseCode = "200", description = "Successfully retrieved system health")
    @PreAuthorize("hasRole('SYSTEM_MONITOR')")
    public ResponseEntity<SystemHealthResponse> getSystemHealth() {
        try {
            TransactionStats currentStats = analyticsEngine.getRealTimeTransactionStats();
            List<AnomalyDetectionResult> criticalAnomalies = fraudAnalyticsProcessor.detectAnomalies().stream()
                    .filter(anomaly -> "CRITICAL".equals(anomaly.getSeverity()))
                    .toList();
            
            SystemHealthResponse health = SystemHealthResponse.builder()
                    .status(criticalAnomalies.isEmpty() ? "HEALTHY" : "DEGRADED")
                    .totalTransactions(currentStats.getTotalTransactions())
                    .totalVolume(currentStats.getTotalVolume())
                    .criticalAnomalies(criticalAnomalies.size())
                    .lastUpdated(Instant.now())
                    .build();
            
            return ResponseEntity.ok(health);
        } catch (Exception e) {
            log.error("Error retrieving system health", e);
            throw new AnalyticsException("Failed to retrieve system health", e);
        }
    }

    /**
     * Get analytics dashboard data
     */
    @GetMapping("/dashboard")
    @Operation(
        summary = "Get analytics dashboard data",
        description = "Retrieve comprehensive dashboard data for analytics UI"
    )
    @ApiResponse(responseCode = "200", description = "Successfully retrieved dashboard data")
    public ResponseEntity<AnalyticsDashboardResponse> getDashboardData(
            @Parameter(description = "Time range in hours")
            @RequestParam(defaultValue = "24") @Min(1) @Max(168) int timeRangeHours) {
        
        try {
            Instant since = Instant.now().minus(timeRangeHours, ChronoUnit.HOURS);
            
            TransactionStats stats = analyticsEngine.getRealTimeTransactionStats();
            List<TrendingPattern> trends = analyticsEngine.getTrendingPatterns().stream().limit(5).toList();
            List<AnomalyDetectionResult> recentAnomalies = fraudAnalyticsProcessor.detectAnomalies().stream()
                    .filter(anomaly -> anomaly.getDetectedAt().isAfter(since))
                    .limit(10)
                    .toList();
            
            AnalyticsDashboardResponse dashboard = AnalyticsDashboardResponse.builder()
                    .transactionStats(stats)
                    .trendingPatterns(trends)
                    .recentAnomalies(recentAnomalies)
                    .timeRange(timeRangeHours)
                    .generatedAt(Instant.now())
                    .build();
            
            return ResponseEntity.ok(dashboard);
        } catch (Exception e) {
            log.error("Error retrieving dashboard data", e);
            throw new AnalyticsException("Failed to retrieve dashboard data", e);
        }
    }

    /**
     * Export analytics data
     */
    @GetMapping("/export")
    @Operation(
        summary = "Export analytics data",
        description = "Export analytics data in specified format"
    )
    @ApiResponse(responseCode = "200", description = "Successfully exported analytics data")
    @PreAuthorize("hasRole('ANALYTICS_EXPORT')")
    public ResponseEntity<String> exportAnalyticsData(
            @Parameter(description = "Export format (CSV, JSON, XLSX)")
            @RequestParam(defaultValue = "JSON") String format,
            @Parameter(description = "Data type to export")
            @RequestParam @NotBlank String dataType,
            @Parameter(description = "Start date (ISO format)")
            @RequestParam Instant startDate,
            @Parameter(description = "End date (ISO format)")
            @RequestParam Instant endDate) {
        
        try {
            // Implementation would generate export data
            String exportData = generateExportData(format, dataType, startDate, endDate);
            
            return ResponseEntity.ok()
                    .header("Content-Disposition", 
                            "attachment; filename=analytics-export." + format.toLowerCase())
                    .body(exportData);
        } catch (Exception e) {
            log.error("Error exporting analytics data", e);
            throw new AnalyticsException("Failed to export analytics data", e);
        }
    }

    /**
     * Search analytics data
     */
    @PostMapping("/search")
    @Operation(
        summary = "Search analytics data",
        description = "Search through analytics data with filters and criteria"
    )
    @ApiResponse(responseCode = "200", description = "Successfully searched analytics data")
    public ResponseEntity<AnalyticsSearchResponse> searchAnalytics(
            @Parameter(description = "Search criteria", required = true)
            @RequestBody @Validated AnalyticsSearchRequest request) {
        
        try {
            // Implementation would perform search based on criteria
            AnalyticsSearchResponse response = performAnalyticsSearch(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error searching analytics data", e);
            throw new AnalyticsException("Failed to search analytics data", e);
        }
    }

    /**
     * Helper methods
     */
    private String generateExportData(String format, String dataType, Instant startDate, Instant endDate) {
        // Implementation would generate actual export data
        return "{ \"message\": \"Export data would be generated here\" }";
    }

    private AnalyticsSearchResponse performAnalyticsSearch(AnalyticsSearchRequest request) {
        // Implementation would perform actual search
        return AnalyticsSearchResponse.builder()
                .totalResults(0L)
                .results(List.of())
                .build();
    }
}