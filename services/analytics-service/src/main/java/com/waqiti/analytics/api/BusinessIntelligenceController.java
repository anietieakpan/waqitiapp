package com.waqiti.analytics.api;

import com.waqiti.analytics.service.DataAggregationService;
import com.waqiti.analytics.service.MachineLearningAnalyticsService;
import com.waqiti.analytics.service.RealTimeAnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/analytics/business")
@RequiredArgsConstructor
@Slf4j
public class BusinessIntelligenceController {

    private final DataAggregationService dataAggregationService;
    private final MachineLearningAnalyticsService mlAnalyticsService;
    private final RealTimeAnalyticsService realTimeService;

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN', 'EXECUTIVE')")
    public ResponseEntity<Map<String, Object>> getExecutiveDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Getting executive dashboard from {} to {}", startDate, endDate);
        
        Map<String, Object> dashboard = dataAggregationService.getExecutiveDashboard(startDate, endDate);
        return ResponseEntity.ok(dashboard);
    }

    @GetMapping("/kpis")
    @PreAuthorize("hasAnyRole('ADMIN', 'EXECUTIVE', 'ANALYST')")
    public ResponseEntity<Map<String, Object>> getKPIs(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) List<String> metrics) {
        log.info("Getting KPIs from {} to {} for metrics: {}", startDate, endDate, metrics);
        
        Map<String, Object> kpis = dataAggregationService.calculateKPIs(startDate, endDate, metrics);
        return ResponseEntity.ok(kpis);
    }

    @GetMapping("/performance")
    @PreAuthorize("hasAnyRole('ADMIN', 'EXECUTIVE')")
    public ResponseEntity<Map<String, Object>> getPerformanceMetrics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "monthly") String period) {
        log.info("Getting performance metrics from {} to {} by {}", startDate, endDate, period);
        
        Map<String, Object> performance = dataAggregationService.analyzePerformanceMetrics(startDate, endDate, period);
        return ResponseEntity.ok(performance);
    }

    @GetMapping("/growth-analysis")
    @PreAuthorize("hasAnyRole('ADMIN', 'EXECUTIVE')")
    public ResponseEntity<Map<String, Object>> getGrowthAnalysis(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "monthly") String period) {
        log.info("Getting growth analysis from {} to {} by {}", startDate, endDate, period);
        
        Map<String, Object> growthAnalysis = mlAnalyticsService.analyzeGrowthTrends(startDate, endDate, period);
        return ResponseEntity.ok(growthAnalysis);
    }

    @GetMapping("/market-analysis")
    @PreAuthorize("hasAnyRole('ADMIN', 'EXECUTIVE')")
    public ResponseEntity<Map<String, Object>> getMarketAnalysis(
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String segment,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Getting market analysis for region: {}, segment: {} from {} to {}", region, segment, startDate, endDate);
        
        Map<String, Object> marketAnalysis = dataAggregationService.analyzeMarketData(region, segment, startDate, endDate);
        return ResponseEntity.ok(marketAnalysis);
    }

    @GetMapping("/competitive-analysis")
    @PreAuthorize("hasRole('EXECUTIVE')")
    public ResponseEntity<Map<String, Object>> getCompetitiveAnalysis(
            @RequestParam(required = false) List<String> competitors,
            @RequestParam(required = false) List<String> metrics) {
        log.info("Getting competitive analysis for competitors: {} and metrics: {}", competitors, metrics);
        
        Map<String, Object> competitiveAnalysis = mlAnalyticsService.analyzeCompetitivePosition(competitors, metrics);
        return ResponseEntity.ok(competitiveAnalysis);
    }

    @GetMapping("/forecasting")
    @PreAuthorize("hasAnyRole('ADMIN', 'EXECUTIVE')")
    public ResponseEntity<Map<String, Object>> getBusinessForecasting(
            @RequestParam String metric,
            @RequestParam(defaultValue = "30") int forecastDays,
            @RequestParam(defaultValue = "95") int confidenceLevel) {
        log.info("Getting business forecasting for metric: {} for {} days with {}% confidence", 
            metric, forecastDays, confidenceLevel);
        
        Map<String, Object> forecast = mlAnalyticsService.generateBusinessForecast(metric, forecastDays, confidenceLevel);
        return ResponseEntity.ok(forecast);
    }

    @GetMapping("/risk-assessment")
    @PreAuthorize("hasAnyRole('ADMIN', 'EXECUTIVE', 'RISK_MANAGER')")
    public ResponseEntity<Map<String, Object>> getBusinessRiskAssessment(
            @RequestParam(required = false) List<String> riskCategories,
            @RequestParam(defaultValue = "30") int assessmentPeriod) {
        log.info("Getting business risk assessment for categories: {} over {} days", riskCategories, assessmentPeriod);
        
        Map<String, Object> riskAssessment = mlAnalyticsService.assessBusinessRisks(riskCategories, assessmentPeriod);
        return ResponseEntity.ok(riskAssessment);
    }

    @GetMapping("/financial-health")
    @PreAuthorize("hasAnyRole('ADMIN', 'EXECUTIVE', 'CFO')")
    public ResponseEntity<Map<String, Object>> getFinancialHealthScore(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        log.info("Getting financial health score as of: {}", asOfDate);
        
        Map<String, Object> financialHealth = dataAggregationService.calculateFinancialHealth(asOfDate);
        return ResponseEntity.ok(financialHealth);
    }

    @GetMapping("/operational-efficiency")
    @PreAuthorize("hasAnyRole('ADMIN', 'EXECUTIVE', 'OPERATIONS')")
    public ResponseEntity<Map<String, Object>> getOperationalEfficiency(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) List<String> processes) {
        log.info("Getting operational efficiency from {} to {} for processes: {}", startDate, endDate, processes);
        
        Map<String, Object> efficiency = dataAggregationService.analyzeOperationalEfficiency(startDate, endDate, processes);
        return ResponseEntity.ok(efficiency);
    }

    @PostMapping("/custom-report")
    @PreAuthorize("hasAnyRole('ADMIN', 'EXECUTIVE', 'ANALYST')")
    public ResponseEntity<Map<String, Object>> generateCustomReport(@RequestBody @Valid CustomReportRequest request) {
        log.info("Generating custom report: {}", request.getReportName());
        
        String reportId = dataAggregationService.generateCustomReport(
            request.getReportName(),
            request.getMetrics(),
            request.getDimensions(),
            request.getFilters(),
            request.getStartDate(),
            request.getEndDate()
        );
        
        return ResponseEntity.ok(Map.of(
            "reportId", reportId,
            "reportName", request.getReportName(),
            "status", "generating",
            "estimatedCompletion", LocalDateTime.now().plusMinutes(10)
        ));
    }

    @GetMapping("/reports/{reportId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EXECUTIVE', 'ANALYST')")
    public ResponseEntity<Map<String, Object>> getCustomReport(@PathVariable String reportId) {
        log.info("Getting custom report: {}", reportId);
        
        Map<String, Object> report = dataAggregationService.getCustomReport(reportId);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/reports")
    @PreAuthorize("hasAnyRole('ADMIN', 'EXECUTIVE', 'ANALYST')")
    public ResponseEntity<List<Map<String, Object>>> getAvailableReports(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String createdBy) {
        log.info("Getting available reports for category: {}, created by: {}", category, createdBy);
        
        List<Map<String, Object>> reports = dataAggregationService.getAvailableReports(category, createdBy);
        return ResponseEntity.ok(reports);
    }

    @GetMapping("/alerts")
    @PreAuthorize("hasAnyRole('ADMIN', 'EXECUTIVE')")
    public ResponseEntity<List<Map<String, Object>>> getBusinessAlerts(
            @RequestParam(defaultValue = "active") String status,
            @RequestParam(required = false) String severity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("Getting business alerts with status: {}, severity: {}", status, severity);
        
        List<Map<String, Object>> alerts = realTimeService.getBusinessAlerts(status, severity, page, size);
        return ResponseEntity.ok(alerts);
    }

    @PostMapping("/alerts/configure")
    @PreAuthorize("hasAnyRole('ADMIN', 'EXECUTIVE')")
    public ResponseEntity<Map<String, Object>> configureAlert(@RequestBody @Valid AlertConfigRequest request) {
        log.info("Configuring business alert: {}", request.getAlertName());
        
        String alertId = realTimeService.configureBusinessAlert(
            request.getAlertName(),
            request.getMetric(),
            request.getCondition(),
            request.getThreshold(),
            request.getRecipients()
        );
        
        return ResponseEntity.ok(Map.of(
            "alertId", alertId,
            "alertName", request.getAlertName(),
            "status", "configured"
        ));
    }

    @GetMapping("/benchmarks")
    @PreAuthorize("hasAnyRole('ADMIN', 'EXECUTIVE')")
    public ResponseEntity<Map<String, Object>> getIndustryBenchmarks(
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) String companySize,
            @RequestParam(required = false) List<String> metrics) {
        log.info("Getting industry benchmarks for industry: {}, size: {}, metrics: {}", 
            industry, companySize, metrics);
        
        Map<String, Object> benchmarks = mlAnalyticsService.getIndustryBenchmarks(industry, companySize, metrics);
        return ResponseEntity.ok(benchmarks);
    }

    @GetMapping("/scenario-analysis")
    @PreAuthorize("hasAnyRole('ADMIN', 'EXECUTIVE')")
    public ResponseEntity<Map<String, Object>> getScenarioAnalysis(@RequestBody @Valid ScenarioRequest request) {
        log.info("Running scenario analysis: {}", request.getScenarioName());
        
        Map<String, Object> scenarioResults = mlAnalyticsService.runScenarioAnalysis(
            request.getScenarioName(),
            request.getVariables(),
            request.getAssumptions()
        );
        
        return ResponseEntity.ok(scenarioResults);
    }
}

