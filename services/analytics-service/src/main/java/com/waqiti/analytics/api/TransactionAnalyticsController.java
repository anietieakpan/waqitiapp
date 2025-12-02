package com.waqiti.analytics.api;

import com.waqiti.analytics.domain.TransactionAnalytics;
import com.waqiti.analytics.domain.TransactionMetrics;
import com.waqiti.analytics.service.DataAggregationService;
import com.waqiti.analytics.service.MachineLearningAnalyticsService;
import com.waqiti.analytics.service.MetricsCollectionService;
import com.waqiti.analytics.service.RealTimeAnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/analytics/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionAnalyticsController {

    private final DataAggregationService dataAggregationService;
    private final MachineLearningAnalyticsService mlAnalyticsService;
    private final MetricsCollectionService metricsService;
    private final RealTimeAnalyticsService realTimeService;

    @GetMapping("/metrics")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<TransactionMetrics> getTransactionMetrics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String userId) {
        log.info("Getting transaction metrics from {} to {} for currency: {}, user: {}", 
            startDate, endDate, currency, userId);
        
        TransactionMetrics metrics = metricsService.getTransactionMetrics(startDate, endDate, currency, userId);
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/analytics")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<TransactionAnalytics> getTransactionAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "daily") String granularity) {
        log.info("Getting transaction analytics from {} to {} with granularity: {}", 
            startDate, endDate, granularity);
        
        TransactionAnalytics analytics = dataAggregationService.getTransactionAnalytics(startDate, endDate, granularity);
        return ResponseEntity.ok(analytics);
    }

    @GetMapping("/trends")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<Map<String, Object>> getTransactionTrends(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) String metric,
            @RequestParam(required = false) String groupBy) {
        log.info("Getting transaction trends for {} days, metric: {}, groupBy: {}", days, metric, groupBy);
        
        Map<String, Object> trends = mlAnalyticsService.analyzeTransactionTrends(days, metric, groupBy);
        return ResponseEntity.ok(trends);
    }

    @GetMapping("/patterns")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<Map<String, Object>> getTransactionPatterns(
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "behavioral") String patternType,
            @RequestParam(defaultValue = "30") int days) {
        log.info("Analyzing transaction patterns for user: {}, type: {}, days: {}", userId, patternType, days);
        
        Map<String, Object> patterns = mlAnalyticsService.analyzeTransactionPatterns(userId, patternType, days);
        return ResponseEntity.ok(patterns);
    }

    @GetMapping("/predictions")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<Map<String, Object>> getTransactionPredictions(
            @RequestParam(required = false) String metric,
            @RequestParam(defaultValue = "7") int forecastDays) {
        log.info("Getting transaction predictions for metric: {}, forecast days: {}", metric, forecastDays);
        
        Map<String, Object> predictions = mlAnalyticsService.predictTransactionVolume(metric, forecastDays);
        return ResponseEntity.ok(predictions);
    }

    @GetMapping("/real-time")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<Map<String, Object>> getRealTimeMetrics() {
        log.info("Getting real-time transaction metrics");
        
        Map<String, Object> realTimeMetrics = realTimeService.getRealTimeMetrics();
        return ResponseEntity.ok(realTimeMetrics);
    }

    @GetMapping("/cohort-analysis")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<Map<String, Object>> getCohortAnalysis(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "monthly") String period) {
        log.info("Getting cohort analysis from {} to {} with period: {}", startDate, endDate, period);
        
        Map<String, Object> cohortAnalysis = mlAnalyticsService.performCohortAnalysis(startDate, endDate, period);
        return ResponseEntity.ok(cohortAnalysis);
    }

    @GetMapping("/funnel-analysis")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<Map<String, Object>> getFunnelAnalysis(
            @RequestParam List<String> events,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Getting funnel analysis for events: {} from {} to {}", events, startDate, endDate);
        
        Map<String, Object> funnelAnalysis = mlAnalyticsService.analyzeFunnel(events, startDate, endDate);
        return ResponseEntity.ok(funnelAnalysis);
    }

    @PostMapping("/custom-query")
    @PreAuthorize("hasRole('ANALYST')")
    public ResponseEntity<Map<String, Object>> executeCustomQuery(@RequestBody @Valid CustomQueryRequest request) {
        log.info("Executing custom analytics query: {}", request.getQueryName());
        
        Map<String, Object> results = dataAggregationService.executeCustomQuery(
            request.getQuery(),
            request.getParameters(),
            request.getOutputFormat()
        );
        
        return ResponseEntity.ok(results);
    }

    @GetMapping("/segmentation")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<Map<String, Object>> getUserSegmentation(
            @RequestParam(required = false) String segmentationType,
            @RequestParam(defaultValue = "30") int days) {
        log.info("Getting user segmentation for type: {} over {} days", segmentationType, days);
        
        Map<String, Object> segmentation = mlAnalyticsService.performUserSegmentation(segmentationType, days);
        return ResponseEntity.ok(segmentation);
    }

    @GetMapping("/revenue-analysis")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<Map<String, Object>> getRevenueAnalysis(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String breakdown) {
        log.info("Getting revenue analysis from {} to {} with breakdown: {}", startDate, endDate, breakdown);
        
        Map<String, Object> revenueAnalysis = dataAggregationService.analyzeRevenue(startDate, endDate, breakdown);
        return ResponseEntity.ok(revenueAnalysis);
    }

    @GetMapping("/geographical")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<Map<String, Object>> getGeographicalAnalysis(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "country") String level) {
        log.info("Getting geographical analysis from {} to {} at level: {}", startDate, endDate, level);
        
        Map<String, Object> geoAnalysis = dataAggregationService.analyzeGeographicalDistribution(startDate, endDate, level);
        return ResponseEntity.ok(geoAnalysis);
    }

    @PostMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<Map<String, Object>> exportAnalytics(@RequestBody @Valid ExportRequest request) {
        log.info("Exporting analytics data: format={}, type={}", request.getFormat(), request.getAnalyticsType());
        
        String exportId = dataAggregationService.exportAnalyticsData(
            request.getAnalyticsType(),
            request.getParameters(),
            request.getFormat()
        );
        
        return ResponseEntity.ok(Map.of(
            "exportId", exportId,
            "status", "processing",
            "format", request.getFormat(),
            "estimatedCompletion", LocalDateTime.now().plusMinutes(5)
        ));
    }

    @GetMapping("/export/{exportId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<Map<String, Object>> getExportStatus(@PathVariable String exportId) {
        log.info("Checking export status: {}", exportId);
        
        Map<String, Object> status = dataAggregationService.getExportStatus(exportId);
        return ResponseEntity.ok(status);
    }

    @GetMapping("/anomalies")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<Map<String, Object>> getAnomalyDetection(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(required = false) String metric,
            @RequestParam(defaultValue = "0.05") double threshold) {
        log.info("Detecting anomalies for {} days, metric: {}, threshold: {}", days, metric, threshold);
        
        Map<String, Object> anomalies = mlAnalyticsService.detectAnomalies(days, metric, threshold);
        return ResponseEntity.ok(anomalies);
    }
}

