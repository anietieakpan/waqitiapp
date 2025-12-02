package com.waqiti.scaling.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "metrics_collections")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetricsCollection {
    
    @Id
    @GeneratedValue
    private UUID id;
    
    @Column(name = "collection_id", unique = true, nullable = false, length = 50)
    private String collectionId;
    
    @Column(name = "service_name", nullable = false, length = 100)
    private String serviceName;
    
    @Column(name = "namespace", length = 100)
    private String namespace;
    
    @Column(name = "metric_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private MetricType metricType;
    
    @Column(name = "collection_source", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private CollectionSource collectionSource;
    
    @Column(name = "collection_interval_seconds", nullable = false)
    private Integer collectionIntervalSeconds;
    
    @Column(name = "aggregation_window_minutes", nullable = false)
    private Integer aggregationWindowMinutes;
    
    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;
    
    @Column(name = "time_range_start", nullable = false)
    private LocalDateTime timeRangeStart;
    
    @Column(name = "time_range_end", nullable = false)
    private LocalDateTime timeRangeEnd;
    
    // Infrastructure Metrics
    @Column(name = "cpu_utilization_avg")
    private Double cpuUtilizationAvg;
    
    @Column(name = "cpu_utilization_max")
    private Double cpuUtilizationMax;
    
    @Column(name = "cpu_utilization_p95")
    private Double cpuUtilizationP95;
    
    @Column(name = "memory_utilization_avg")
    private Double memoryUtilizationAvg;
    
    @Column(name = "memory_utilization_max")
    private Double memoryUtilizationMax;
    
    @Column(name = "memory_utilization_p95")
    private Double memoryUtilizationP95;
    
    @Column(name = "disk_io_utilization")
    private Double diskIoUtilization;
    
    @Column(name = "network_io_utilization")
    private Double networkIoUtilization;
    
    @Column(name = "pod_count")
    private Integer podCount;
    
    @Column(name = "running_pods")
    private Integer runningPods;
    
    @Column(name = "pending_pods")
    private Integer pendingPods;
    
    @Column(name = "failed_pods")
    private Integer failedPods;
    
    @Column(name = "node_count")
    private Integer nodeCount;
    
    @Column(name = "available_cpu_cores")
    private Double availableCpuCores;
    
    @Column(name = "available_memory_gb")
    private Double availableMemoryGb;
    
    // Application Metrics
    @Column(name = "request_rate_per_second")
    private Double requestRatePerSecond;
    
    @Column(name = "response_time_avg_ms")
    private Double responseTimeAvgMs;
    
    @Column(name = "response_time_p95_ms")
    private Double responseTimeP95Ms;
    
    @Column(name = "response_time_p99_ms")
    private Double responseTimeP99Ms;
    
    @Column(name = "error_rate_percentage")
    private Double errorRatePercentage;
    
    @Column(name = "active_connections")
    private Integer activeConnections;
    
    @Column(name = "queue_depth")
    private Integer queueDepth;
    
    @Column(name = "throughput_ops_per_second")
    private Double throughputOpsPerSecond;
    
    @Column(name = "concurrent_users")
    private Integer concurrentUsers;
    
    @Column(name = "session_count")
    private Integer sessionCount;
    
    // Business Metrics
    @Column(name = "transaction_volume")
    private Long transactionVolume;
    
    @Column(name = "transaction_value_total")
    private Double transactionValueTotal;
    
    @Column(name = "user_activity_score")
    private Double userActivityScore;
    
    @Column(name = "payment_processing_rate")
    private Double paymentProcessingRate;
    
    @Column(name = "api_call_volume")
    private Long apiCallVolume;
    
    @Column(name = "successful_transactions")
    private Long successfulTransactions;
    
    @Column(name = "failed_transactions")
    private Long failedTransactions;
    
    // Cost Metrics
    @Column(name = "cpu_cost_per_hour")
    private Double cpuCostPerHour;
    
    @Column(name = "memory_cost_per_hour")
    private Double memoryCostPerHour;
    
    @Column(name = "storage_cost_per_hour")
    private Double storageCostPerHour;
    
    @Column(name = "network_cost_per_hour")
    private Double networkCostPerHour;
    
    @Column(name = "total_cost_per_hour")
    private Double totalCostPerHour;
    
    @Column(name = "cost_per_transaction")
    private Double costPerTransaction;
    
    @Column(name = "cost_efficiency_score")
    private Double costEfficiencyScore;
    
    // Quality Metrics
    @Column(name = "availability_percentage")
    private Double availabilityPercentage;
    
    @Column(name = "reliability_score")
    private Double reliabilityScore;
    
    @Column(name = "performance_score")
    private Double performanceScore;
    
    @Column(name = "sla_compliance_score")
    private Double slaComplianceScore;
    
    @Column(name = "customer_satisfaction_score")
    private Double customerSatisfactionScore;
    
    // Detailed Metrics Data
    @Type(type = "jsonb")
    @Column(name = "detailed_metrics", columnDefinition = "jsonb")
    private Map<String, Object> detailedMetrics;
    
    @Type(type = "jsonb")
    @Column(name = "time_series_data", columnDefinition = "jsonb")
    private List<TimeSeriesPoint> timeSeriesData;
    
    @Type(type = "jsonb")
    @Column(name = "percentile_data", columnDefinition = "jsonb")
    private Map<String, Double> percentileData;
    
    @Type(type = "jsonb")
    @Column(name = "histogram_data", columnDefinition = "jsonb")
    private Map<String, Object> histogramData;
    
    // Anomaly Detection
    @Column(name = "anomaly_score")
    private Double anomalyScore;
    
    @Column(name = "anomaly_detected")
    private Boolean anomalyDetected = false;
    
    @Type(type = "jsonb")
    @Column(name = "anomaly_details", columnDefinition = "jsonb")
    private Map<String, Object> anomalyDetails;
    
    // Prediction Features
    @Type(type = "jsonb")
    @Column(name = "derived_features", columnDefinition = "jsonb")
    private Map<String, Object> derivedFeatures;
    
    @Type(type = "jsonb")
    @Column(name = "temporal_features", columnDefinition = "jsonb")
    private Map<String, Object> temporalFeatures;
    
    @Type(type = "jsonb")
    @Column(name = "statistical_features", columnDefinition = "jsonb")
    private Map<String, Object> statisticalFeatures;
    
    // Data Quality
    @Column(name = "data_quality_score")
    private Double dataQualityScore;
    
    @Column(name = "missing_data_percentage")
    private Double missingDataPercentage;
    
    @Column(name = "data_completeness")
    private Double dataCompleteness;
    
    @Column(name = "collection_errors")
    private Integer collectionErrors = 0;
    
    @Type(type = "jsonb")
    @Column(name = "collection_metadata", columnDefinition = "jsonb")
    private Map<String, Object> collectionMetadata;
    
    // External Context
    @Type(type = "jsonb")
    @Column(name = "external_factors", columnDefinition = "jsonb")
    private Map<String, Object> externalFactors;
    
    @Column(name = "weather_condition", length = 50)
    private String weatherCondition;
    
    @Column(name = "is_holiday")
    private Boolean isHoliday = false;
    
    @Column(name = "is_weekend")
    private Boolean isWeekend = false;
    
    @Column(name = "is_business_hours")
    private Boolean isBusinessHours = true;
    
    @Column(name = "promotional_event_active")
    private Boolean promotionalEventActive = false;
    
    @Column(name = "system_maintenance_window")
    private Boolean systemMaintenanceWindow = false;
    
    // Retention and Archival
    @Column(name = "retention_days")
    private Integer retentionDays = 90;
    
    @Column(name = "archived")
    private Boolean archived = false;
    
    @Column(name = "archived_at")
    private LocalDateTime archivedAt;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Version
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        collectedAt = LocalDateTime.now();
        
        if (collectionId == null) {
            collectionId = "MC_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        }
        
        // Set temporal context
        LocalDateTime now = LocalDateTime.now();
        this.isWeekend = now.getDayOfWeek().getValue() >= 6;
        this.isBusinessHours = now.getHour() >= 8 && now.getHour() <= 18;
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public enum MetricType {
        INFRASTRUCTURE,     // CPU, Memory, Network, Disk
        APPLICATION,        // Request rate, Response time, Errors
        BUSINESS,          // Transactions, Revenue, Users
        COST,              // Resource costs, Efficiency
        QUALITY,           // SLA, Availability, Performance
        SECURITY,          // Threats, Vulnerabilities
        CUSTOM             // Custom application metrics
    }
    
    public enum CollectionSource {
        PROMETHEUS,        // Prometheus metrics server
        KUBERNETES,        // Kubernetes API metrics
        APPLICATION,       // Application-specific metrics
        CLOUD_PROVIDER,    // AWS CloudWatch, GCP Monitoring, etc.
        DATABASE,          // Database performance metrics
        LOAD_BALANCER,     // Load balancer metrics
        CDN,              // CDN performance metrics
        EXTERNAL_API,      // External API response metrics
        CUSTOM_AGENT,      // Custom monitoring agents
        LOG_AGGREGATOR     // Metrics derived from logs
    }
    
    // Nested classes for JSON storage
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeSeriesPoint {
        private LocalDateTime timestamp;
        private String metricName;
        private Double value;
        private Map<String, String> labels;
    }
    
    // Business logic methods
    
    public boolean isHighUtilization() {
        return (cpuUtilizationAvg != null && cpuUtilizationAvg > 80) ||
               (memoryUtilizationAvg != null && memoryUtilizationAvg > 85);
    }
    
    public boolean isLowUtilization() {
        return (cpuUtilizationAvg != null && cpuUtilizationAvg < 30) &&
               (memoryUtilizationAvg != null && memoryUtilizationAvg < 40);
    }
    
    public boolean hasPerformanceIssues() {
        return (responseTimeP95Ms != null && responseTimeP95Ms > 2000) ||
               (errorRatePercentage != null && errorRatePercentage > 5);
    }
    
    public boolean isUnderProvisioned() {
        return isHighUtilization() && hasPerformanceIssues();
    }
    
    public boolean isOverProvisioned() {
        return isLowUtilization() && !hasPerformanceIssues();
    }
    
    public boolean hasHighTraffic() {
        return requestRatePerSecond != null && requestRatePerSecond > getBaselineTraffic() * 2;
    }
    
    public boolean hasAnomalousPattern() {
        return anomalyDetected != null && anomalyDetected;
    }
    
    public boolean requiresImmediateAttention() {
        return hasPerformanceIssues() || hasAnomalousPattern() || 
               (availabilityPercentage != null && availabilityPercentage < 99.0);
    }
    
    public Double getResourceEfficiencyScore() {
        if (cpuUtilizationAvg == null || memoryUtilizationAvg == null) {
            return null;
        }
        
        // Ideal utilization is around 70-80%
        double cpuEfficiency = Math.max(0, 1 - Math.abs(cpuUtilizationAvg - 75) / 75);
        double memoryEfficiency = Math.max(0, 1 - Math.abs(memoryUtilizationAvg - 75) / 75);
        
        return (cpuEfficiency + memoryEfficiency) / 2.0;
    }
    
    public Double getPerformanceScore() {
        if (performanceScore != null) {
            return performanceScore;
        }
        
        double score = 1.0;
        
        // Response time impact
        if (responseTimeP95Ms != null) {
            if (responseTimeP95Ms > 2000) score *= 0.5;
            else if (responseTimeP95Ms > 1000) score *= 0.8;
        }
        
        // Error rate impact
        if (errorRatePercentage != null) {
            if (errorRatePercentage > 5) score *= 0.3;
            else if (errorRatePercentage > 1) score *= 0.7;
        }
        
        // Availability impact
        if (availabilityPercentage != null) {
            score *= Math.min(1.0, availabilityPercentage / 99.0);
        }
        
        return score;
    }
    
    public Double getScalingRecommendationScore() {
        double score = 0.0;
        
        // High utilization suggests scale up
        if (cpuUtilizationAvg != null && cpuUtilizationAvg > 80) {
            score += 0.4;
        }
        if (memoryUtilizationAvg != null && memoryUtilizationAvg > 85) {
            score += 0.4;
        }
        
        // Performance issues suggest scale up
        if (hasPerformanceIssues()) {
            score += 0.3;
        }
        
        // Low utilization suggests scale down
        if (isLowUtilization() && !hasPerformanceIssues()) {
            score -= 0.5;
        }
        
        // Traffic patterns
        if (hasHighTraffic()) {
            score += 0.2;
        }
        
        return Math.max(-1.0, Math.min(1.0, score));
    }
    
    private double getBaselineTraffic() {
        // This would typically be calculated from historical data
        // For now, return a default baseline
        return 100.0; // requests per second
    }
    
    public void calculateDerivedMetrics() {
        Map<String, Object> derived = new java.util.HashMap<>();
        
        // Calculate resource efficiency
        Double resourceEfficiency = getResourceEfficiencyScore();
        if (resourceEfficiency != null) {
            derived.put("resource_efficiency", resourceEfficiency);
        }
        
        // Calculate performance score
        Double perfScore = getPerformanceScore();
        if (perfScore != null) {
            derived.put("calculated_performance_score", perfScore);
        }
        
        // Calculate scaling recommendation
        Double scalingScore = getScalingRecommendationScore();
        derived.put("scaling_recommendation_score", scalingScore);
        
        // Calculate cost efficiency
        if (totalCostPerHour != null && throughputOpsPerSecond != null && throughputOpsPerSecond > 0) {
            derived.put("cost_per_operation", totalCostPerHour / (throughputOpsPerSecond * 3600));
        }
        
        // Traffic patterns
        derived.put("is_high_traffic", hasHighTraffic());
        derived.put("is_under_provisioned", isUnderProvisioned());
        derived.put("is_over_provisioned", isOverProvisioned());
        
        this.derivedFeatures = derived;
    }
    
    public void calculateTemporalFeatures() {
        Map<String, Object> temporal = new java.util.HashMap<>();
        
        LocalDateTime now = collectedAt != null ? collectedAt : LocalDateTime.now();
        
        temporal.put("hour_of_day", now.getHour());
        temporal.put("day_of_week", now.getDayOfWeek().getValue());
        temporal.put("day_of_month", now.getDayOfMonth());
        temporal.put("month_of_year", now.getMonthValue());
        temporal.put("is_weekend", isWeekend);
        temporal.put("is_business_hours", isBusinessHours);
        temporal.put("is_holiday", isHoliday);
        temporal.put("is_month_end", now.getDayOfMonth() >= 28);
        temporal.put("is_quarter_end", now.getMonthValue() % 3 == 0 && now.getDayOfMonth() >= 28);
        
        this.temporalFeatures = temporal;
    }
    
    public void archive() {
        this.archived = true;
        this.archivedAt = LocalDateTime.now();
    }
    
    public boolean shouldArchive() {
        if (retentionDays == null) return false;
        
        LocalDateTime archiveThreshold = LocalDateTime.now().minusDays(retentionDays);
        return createdAt.isBefore(archiveThreshold);
    }
    
    public Map<String, Object> toMLFeatureVector() {
        Map<String, Object> features = new java.util.HashMap<>();
        
        // Include all numeric metrics
        if (cpuUtilizationAvg != null) features.put("cpu_util_avg", cpuUtilizationAvg);
        if (memoryUtilizationAvg != null) features.put("memory_util_avg", memoryUtilizationAvg);
        if (requestRatePerSecond != null) features.put("request_rate", requestRatePerSecond);
        if (responseTimeAvgMs != null) features.put("response_time_avg", responseTimeAvgMs);
        if (errorRatePercentage != null) features.put("error_rate", errorRatePercentage);
        if (podCount != null) features.put("pod_count", podCount);
        if (throughputOpsPerSecond != null) features.put("throughput", throughputOpsPerSecond);
        
        // Include temporal features
        if (temporalFeatures != null) {
            features.putAll(temporalFeatures);
        }
        
        // Include derived features
        if (derivedFeatures != null) {
            features.putAll(derivedFeatures);
        }
        
        return features;
    }
}