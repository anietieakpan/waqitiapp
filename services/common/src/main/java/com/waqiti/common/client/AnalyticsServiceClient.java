package com.waqiti.common.client;

import com.waqiti.common.api.StandardApiResponse;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Analytics Service Client
 * 
 * Provides standardized communication with the Analytics Service
 */
@Component
@Slf4j
public class AnalyticsServiceClient extends ServiceClient {

    public AnalyticsServiceClient(RestTemplate restTemplate, 
                                @Value("${services.analytics-service.url}") String baseUrl) {
        super(restTemplate, baseUrl, "analytics-service");
    }

    /**
     * Get transaction analytics
     */
    public CompletableFuture<ServiceResponse<TransactionAnalyticsDTO>> getTransactionAnalytics(AnalyticsRequest request) {
        Map<String, Object> queryParams = Map.of(
            "fromDate", request.getFromDate().toString(),
            "toDate", request.getToDate().toString(),
            "period", request.getPeriod(),
            "groupBy", request.getGroupBy() != null ? String.join(",", request.getGroupBy()) : ""
        );
        return get("/api/v1/analytics/transactions", TransactionAnalyticsDTO.class, queryParams);
    }

    /**
     * Get user analytics
     */
    public CompletableFuture<ServiceResponse<UserAnalyticsDTO>> getUserAnalytics(UUID userId, AnalyticsRequest request) {
        Map<String, Object> queryParams = Map.of(
            "fromDate", request.getFromDate().toString(),
            "toDate", request.getToDate().toString(),
            "period", request.getPeriod()
        );
        return get("/api/v1/analytics/users/" + userId, UserAnalyticsDTO.class, queryParams);
    }

    /**
     * Get merchant analytics
     */
    public CompletableFuture<ServiceResponse<MerchantAnalyticsDTO>> getMerchantAnalytics(UUID merchantId, AnalyticsRequest request) {
        Map<String, Object> queryParams = Map.of(
            "fromDate", request.getFromDate().toString(),
            "toDate", request.getToDate().toString(),
            "period", request.getPeriod()
        );
        return get("/api/v1/analytics/merchants/" + merchantId, MerchantAnalyticsDTO.class, queryParams);
    }

    /**
     * Get real-time metrics
     */
    public CompletableFuture<ServiceResponse<RealTimeMetricsDTO>> getRealTimeMetrics() {
        return get("/api/v1/analytics/real-time", RealTimeMetricsDTO.class, null);
    }

    /**
     * Get dashboard metrics
     */
    public CompletableFuture<ServiceResponse<DashboardMetricsDTO>> getDashboardMetrics(DashboardRequest request) {
        Map<String, Object> queryParams = Map.of(
            "timeframe", request.getTimeframe(),
            "widgets", request.getWidgets() != null ? String.join(",", request.getWidgets()) : ""
        );
        return get("/api/v1/analytics/dashboard", DashboardMetricsDTO.class, queryParams);
    }

    /**
     * Get fraud analytics
     */
    public CompletableFuture<ServiceResponse<FraudAnalyticsDTO>> getFraudAnalytics(FraudAnalyticsRequest request) {
        Map<String, Object> queryParams = Map.of(
            "fromDate", request.getFromDate().toString(),
            "toDate", request.getToDate().toString(),
            "type", request.getType() != null ? request.getType() : ""
        );
        return get("/api/v1/analytics/fraud", FraudAnalyticsDTO.class, queryParams);
    }

    /**
     * Generate business intelligence report
     */
    public CompletableFuture<ServiceResponse<BIReportDTO>> generateBIReport(BIReportRequest request) {
        return post("/api/v1/analytics/reports/bi", request, BIReportDTO.class);
    }

    /**
     * Get report by ID
     */
    public CompletableFuture<ServiceResponse<BIReportDTO>> getReport(UUID reportId) {
        return get("/api/v1/analytics/reports/" + reportId, BIReportDTO.class, null);
    }

    /**
     * Get available reports
     */
    public CompletableFuture<ServiceResponse<List<BIReportSummaryDTO>>> getAvailableReports(ReportSearchRequest request) {
        Map<String, Object> queryParams = Map.of(
            "type", request.getType() != null ? request.getType() : "",
            "status", request.getStatus() != null ? request.getStatus() : "",
            "page", request.getPage(),
            "size", request.getSize()
        );
        return getList("/api/v1/analytics/reports", 
            new ParameterizedTypeReference<StandardApiResponse<List<BIReportSummaryDTO>>>() {}, 
            queryParams);
    }

    /**
     * Predict fraud
     */
    public CompletableFuture<ServiceResponse<FraudPredictionDTO>> predictFraud(FraudPredictionRequest request) {
        return post("/api/v1/analytics/ml/fraud/predict", request, FraudPredictionDTO.class);
    }

    /**
     * Predict customer churn
     */
    public CompletableFuture<ServiceResponse<ChurnPredictionDTO>> predictChurn(UUID userId) {
        return get("/api/v1/analytics/ml/churn/" + userId, ChurnPredictionDTO.class, null);
    }

    /**
     * Get customer lifetime value prediction
     */
    public CompletableFuture<ServiceResponse<LTVPredictionDTO>> predictLTV(UUID userId) {
        return get("/api/v1/analytics/ml/ltv/" + userId, LTVPredictionDTO.class, null);
    }

    /**
     * Get transaction forecast
     */
    public CompletableFuture<ServiceResponse<TransactionForecastDTO>> getTransactionForecast(ForecastRequest request) {
        Map<String, Object> queryParams = Map.of(
            "days", request.getDays(),
            "type", request.getType()
        );
        return get("/api/v1/analytics/ml/forecast", TransactionForecastDTO.class, queryParams);
    }

    /**
     * Detect anomalies
     */
    public CompletableFuture<ServiceResponse<AnomalyDetectionResultDTO>> detectAnomalies(AnomalyDetectionRequest request) {
        return post("/api/v1/analytics/ml/anomalies", request, AnomalyDetectionResultDTO.class);
    }

    /**
     * Get customer segmentation
     */
    public CompletableFuture<ServiceResponse<CustomerSegmentationDTO>> getCustomerSegmentation(boolean refresh) {
        Map<String, Object> queryParams = Map.of("refresh", refresh);
        return get("/api/v1/analytics/ml/segmentation", CustomerSegmentationDTO.class, queryParams);
    }

    /**
     * Get recommendations for user
     */
    public CompletableFuture<ServiceResponse<RecommendationResultDTO>> getRecommendations(UUID userId, String context) {
        Map<String, Object> queryParams = Map.of("context", context);
        return get("/api/v1/analytics/ml/recommendations/" + userId, RecommendationResultDTO.class, queryParams);
    }

    /**
     * Get data quality metrics
     */
    public CompletableFuture<ServiceResponse<DataQualityMetricsDTO>> getDataQualityMetrics(DataQualityRequest request) {
        Map<String, Object> queryParams = Map.of(
            "dataSource", request.getDataSource(),
            "table", request.getTable() != null ? request.getTable() : "",
            "date", request.getDate().toString()
        );
        return get("/api/v1/analytics/data-quality", DataQualityMetricsDTO.class, queryParams);
    }

    /**
     * Track custom event
     */
    public CompletableFuture<ServiceResponse<Void>> trackEvent(EventTrackingRequest request) {
        return post("/api/v1/analytics/events/track", request, Void.class);
    }

    /**
     * Get model performance
     */
    public CompletableFuture<ServiceResponse<ModelPerformanceDTO>> getModelPerformance(String modelName) {
        return get("/api/v1/analytics/ml/models/" + modelName + "/performance", ModelPerformanceDTO.class, null);
    }

    /**
     * Get trending analysis
     */
    public CompletableFuture<ServiceResponse<TrendingAnalysisDTO>> getTrendingAnalysis() {
        return get("/api/v1/analytics/trending", TrendingAnalysisDTO.class, null);
    }

    /**
     * Export analytics data
     */
    public CompletableFuture<ServiceResponse<byte[]>> exportAnalytics(ExportRequest request) {
        return post("/api/v1/analytics/export", request, byte[].class);
    }

    @Override
    protected String getCurrentCorrelationId() {
        return org.slf4j.MDC.get("correlationId");
    }

    @Override
    protected String getCurrentAuthToken() {
        return org.springframework.security.core.context.SecurityContextHolder
            .getContext()
            .getAuthentication()
            .getCredentials()
            .toString();
    }

    // DTOs
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TransactionAnalyticsDTO {
        private LocalDateTime fromDate;
        private LocalDateTime toDate;
        private String period;
        private long totalTransactions;
        private BigDecimal totalAmount;
        private BigDecimal averageAmount;
        private BigDecimal successRate;
        private Map<String, Long> transactionsByStatus;
        private Map<String, BigDecimal> amountByMethod;
        private Map<String, Long> transactionsByCountry;
        private List<TimeSeriesDataPoint> timeSeries;
        private Map<String, Object> additionalMetrics;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class UserAnalyticsDTO {
        private UUID userId;
        private LocalDateTime fromDate;
        private LocalDateTime toDate;
        private long transactionCount;
        private BigDecimal totalSpent;
        private BigDecimal totalReceived;
        private BigDecimal averageTransactionAmount;
        private int loginCount;
        private int daysActive;
        private BigDecimal engagementScore;
        private String customerSegment;
        private Map<String, Object> behaviorMetrics;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class MerchantAnalyticsDTO {
        private UUID merchantId;
        private LocalDateTime fromDate;
        private LocalDateTime toDate;
        private long transactionCount;
        private BigDecimal transactionVolume;
        private BigDecimal averageTransactionAmount;
        private int uniqueCustomers;
        private BigDecimal successRate;
        private BigDecimal grossRevenue;
        private BigDecimal netRevenue;
        private Map<String, Object> performanceMetrics;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RealTimeMetricsDTO {
        private LocalDateTime timestamp;
        private int windowSizeMinutes;
        private long totalTransactions;
        private BigDecimal totalAmount;
        private double transactionsPerSecond;
        private BigDecimal successRate;
        private BigDecimal errorRate;
        private long averageResponseTime;
        private List<CountryMetric> topCountries;
        private Map<String, Long> channelDistribution;
        private Map<String, Object> riskMetrics;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CountryMetric {
        private String country;
        private long transactionCount;
        private BigDecimal totalAmount;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DashboardMetricsDTO {
        private String timeframe;
        private LocalDateTime lastUpdated;
        private Map<String, Object> widgets;
        private List<AlertSummary> alerts;
        private Map<String, Object> kpis;
        private List<TrendIndicator> trends;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AlertSummary {
        private String type;
        private String severity;
        private int count;
        private LocalDateTime lastOccurred;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TrendIndicator {
        private String metric;
        private String direction;
        private BigDecimal change;
        private String period;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FraudAnalyticsDTO {
        private LocalDateTime fromDate;
        private LocalDateTime toDate;
        private long totalFraudCases;
        private BigDecimal totalFraudAmount;
        private BigDecimal fraudRate;
        private Map<String, Long> fraudByType;
        private Map<String, BigDecimal> fraudAmountByType;
        private List<FraudTrendPoint> trends;
        private Map<String, Object> patterns;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FraudTrendPoint {
        private LocalDate date;
        private long fraudCount;
        private BigDecimal fraudAmount;
        private BigDecimal fraudRate;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class BIReportDTO {
        private UUID id;
        private String reportId;
        private String name;
        private String type;
        private String status;
        private LocalDateTime fromDate;
        private LocalDateTime toDate;
        private String executiveSummary;
        private Map<String, Object> keyMetrics;
        private Map<String, Object> detailedAnalysis;
        private List<String> recommendations;
        private LocalDateTime generatedAt;
        private String generatedBy;
        private byte[] reportData;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class BIReportSummaryDTO {
        private UUID id;
        private String reportId;
        private String name;
        private String type;
        private String status;
        private LocalDateTime fromDate;
        private LocalDateTime toDate;
        private LocalDateTime generatedAt;
        private String generatedBy;
        private int downloadCount;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FraudPredictionDTO {
        private UUID transactionId;
        private BigDecimal fraudScore;
        private boolean isFraud;
        private BigDecimal confidence;
        private List<String> riskFactors;
        private List<String> explanations;
        private String modelVersion;
        private LocalDateTime timestamp;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ChurnPredictionDTO {
        private UUID userId;
        private BigDecimal churnProbability;
        private String riskLevel;
        private int daysToChurn;
        private List<String> keyIndicators;
        private List<String> retentionActions;
        private String modelVersion;
        private LocalDateTime timestamp;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class LTVPredictionDTO {
        private UUID userId;
        private BigDecimal predictedLTV;
        private BigDecimal confidenceLower;
        private BigDecimal confidenceUpper;
        private String customerSegment;
        private List<String> keyFactors;
        private int predictionHorizonDays;
        private String modelVersion;
        private LocalDateTime timestamp;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TransactionForecastDTO {
        private List<ForecastPoint> forecastPoints;
        private String modelVersion;
        private Map<String, BigDecimal> accuracy;
        private LocalDateTime generatedAt;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ForecastPoint {
        private LocalDate date;
        private long predictedVolume;
        private BigDecimal predictedAmount;
        private long volumeConfidenceLower;
        private long volumeConfidenceUpper;
        private BigDecimal amountConfidenceLower;
        private BigDecimal amountConfidenceUpper;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AnomalyDetectionResultDTO {
        private UUID userId;
        private List<AnomalyResult> anomalies;
        private BigDecimal overallAnomalyScore;
        private LocalDateTime analysisDate;
        private int lookbackDays;
        private String modelVersion;
        private String message;
        private LocalDateTime timestamp;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AnomalyResult {
        private UUID transactionId;
        private BigDecimal anomalyScore;
        private String anomalyType;
        private String explanation;
        private LocalDateTime timestamp;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CustomerSegmentationDTO {
        private List<CustomerSegment> segments;
        private int totalCustomers;
        private String modelVersion;
        private LocalDateTime analysisDate;
        private LocalDateTime timestamp;
        private String message;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CustomerSegment {
        private String segmentId;
        private String segmentName;
        private String description;
        private int customerCount;
        private BigDecimal averageLTV;
        private Map<String, Object> characteristics;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RecommendationResultDTO {
        private UUID userId;
        private List<Recommendation> recommendations;
        private String context;
        private String algorithm;
        private BigDecimal confidence;
        private String modelVersion;
        private LocalDateTime timestamp;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class Recommendation {
        private String type;
        private String title;
        private String description;
        private BigDecimal score;
        private Map<String, Object> parameters;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DataQualityMetricsDTO {
        private String dataSource;
        private String tableName;
        private LocalDate metricDate;
        private long totalRecords;
        private BigDecimal completenessRate;
        private BigDecimal accuracyRate;
        private BigDecimal consistencyScore;
        private BigDecimal validityScore;
        private List<DataQualityIssue> issues;
        private Map<String, Object> profile;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DataQualityIssue {
        private String column;
        private String issueType;
        private String severity;
        private long count;
        private String description;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ModelPerformanceDTO {
        private String modelName;
        private String modelVersion;
        private String modelType;
        private BigDecimal accuracy;
        private BigDecimal precision;
        private BigDecimal recall;
        private BigDecimal f1Score;
        private LocalDateTime lastTraining;
        private LocalDateTime lastPrediction;
        private long predictionCount;
        private boolean driftDetected;
        private Map<String, Object> metrics;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TrendingAnalysisDTO {
        private LocalDateTime timestamp;
        private BigDecimal hourlyGrowthRate;
        private String trendDirection;
        private boolean volumeSpike;
        private List<String> emergingPatterns;
        private List<String> anomalies;
        private Map<String, Object> predictions;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TimeSeriesDataPoint {
        private LocalDateTime timestamp;
        private BigDecimal value;
        private Map<String, Object> metadata;
    }

    // Request DTOs
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AnalyticsRequest {
        private LocalDateTime fromDate;
        private LocalDateTime toDate;
        private String period; // HOURLY, DAILY, WEEKLY, MONTHLY
        private List<String> groupBy;
        private Map<String, Object> filters;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DashboardRequest {
        private String timeframe; // 1H, 24H, 7D, 30D
        private List<String> widgets;
        private Map<String, Object> filters;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FraudAnalyticsRequest {
        private LocalDateTime fromDate;
        private LocalDateTime toDate;
        private String type;
        private List<String> groupBy;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class BIReportRequest {
        private String reportType;
        private String period;
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private Map<String, Object> parameters;
        private String format; // PDF, EXCEL, JSON
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ReportSearchRequest {
        private String type;
        private String status;
        private LocalDateTime fromDate;
        private LocalDateTime toDate;
        @Builder.Default
        private int page = 0;
        @Builder.Default
        private int size = 20;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FraudPredictionRequest {
        private UUID transactionId;
        private UUID userId;
        private BigDecimal amount;
        private String currency;
        private String deviceId;
        private String ipAddress;
        private Map<String, Object> features;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ForecastRequest {
        private int days;
        private String type; // VOLUME, AMOUNT, BOTH
        private Map<String, Object> parameters;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AnomalyDetectionRequest {
        private UUID userId;
        private int lookbackDays;
        private String analysisType;
        private Map<String, Object> parameters;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DataQualityRequest {
        private String dataSource;
        private String table;
        private LocalDate date;
        private List<String> columns;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class EventTrackingRequest {
        private String eventType;
        private UUID userId;
        private Map<String, Object> properties;
        private LocalDateTime timestamp;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ExportRequest {
        private String dataType;
        private LocalDateTime fromDate;
        private LocalDateTime toDate;
        private String format; // CSV, EXCEL, JSON
        private Map<String, Object> filters;
    }
}