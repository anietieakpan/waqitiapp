package com.waqiti.scaling.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "scaling_predictions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScalingPrediction {
    
    @Id
    @GeneratedValue
    private UUID id;
    
    @Column(name = "prediction_id", unique = true, nullable = false, length = 50)
    private String predictionId;
    
    @Column(name = "service_name", nullable = false, length = 100)
    private String serviceName;
    
    @Column(name = "namespace", length = 100)
    private String namespace;
    
    @Column(name = "prediction_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private PredictionType predictionType;
    
    @Column(name = "time_horizon_minutes", nullable = false)
    private Integer timeHorizonMinutes;
    
    @Column(name = "predicted_at", nullable = false)
    private LocalDateTime predictedAt;
    
    @Column(name = "valid_from", nullable = false)
    private LocalDateTime validFrom;
    
    @Column(name = "valid_until", nullable = false)
    private LocalDateTime validUntil;
    
    @Column(name = "model_version", length = 20)
    private String modelVersion;
    
    @Column(name = "model_accuracy")
    private Double modelAccuracy;
    
    @Column(name = "confidence_score", nullable = false)
    private Double confidenceScore;
    
    @Column(name = "prediction_status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private PredictionStatus status = PredictionStatus.ACTIVE;
    
    // Current state
    @Column(name = "current_instances", nullable = false)
    private Integer currentInstances;
    
    @Column(name = "current_cpu_utilization")
    private Double currentCpuUtilization;
    
    @Column(name = "current_memory_utilization")
    private Double currentMemoryUtilization;
    
    @Column(name = "current_request_rate")
    private Double currentRequestRate;
    
    @Column(name = "current_response_time")
    private Double currentResponseTime;
    
    // Predicted state
    @Column(name = "predicted_instances", nullable = false)
    private Integer predictedInstances;
    
    @Column(name = "predicted_cpu_utilization")
    private Double predictedCpuUtilization;
    
    @Column(name = "predicted_memory_utilization")
    private Double predictedMemoryUtilization;
    
    @Column(name = "predicted_request_rate")
    private Double predictedRequestRate;
    
    @Column(name = "predicted_response_time")
    private Double predictedResponseTime;
    
    @Column(name = "predicted_load_score")
    private Double predictedLoadScore;
    
    // Scaling recommendation
    @Column(name = "scaling_action", length = 20)
    @Enumerated(EnumType.STRING)
    private ScalingAction scalingAction;
    
    @Column(name = "recommended_instances")
    private Integer recommendedInstances;
    
    @Column(name = "scaling_magnitude")
    private Double scalingMagnitude;
    
    @Column(name = "scaling_urgency", length = 20)
    @Enumerated(EnumType.STRING)
    private ScalingUrgency scalingUrgency;
    
    @Column(name = "scaling_reason", columnDefinition = "TEXT")
    private String scalingReason;
    
    // Features used for prediction
    @Type(type = "jsonb")
    @Column(name = "input_features", columnDefinition = "jsonb")
    private Map<String, Object> inputFeatures;
    
    @Type(type = "jsonb")
    @Column(name = "temporal_features", columnDefinition = "jsonb")
    private Map<String, Object> temporalFeatures;
    
    @Type(type = "jsonb")
    @Column(name = "historical_patterns", columnDefinition = "jsonb")
    private Map<String, Object> historicalPatterns;
    
    // Prediction intervals and uncertainty
    @Type(type = "jsonb")
    @Column(name = "prediction_intervals", columnDefinition = "jsonb")
    private List<PredictionInterval> predictionIntervals;
    
    @Column(name = "uncertainty_score")
    private Double uncertaintyScore;
    
    @Column(name = "prediction_variance")
    private Double predictionVariance;
    
    // Cost impact analysis
    @Column(name = "current_cost_per_hour", precision = 19, scale = 4)
    private BigDecimal currentCostPerHour;
    
    @Column(name = "predicted_cost_per_hour", precision = 19, scale = 4)
    private BigDecimal predictedCostPerHour;
    
    @Column(name = "cost_impact", precision = 19, scale = 4)
    private BigDecimal costImpact;
    
    @Column(name = "cost_efficiency_score")
    private Double costEfficiencyScore;
    
    // Performance impact
    @Column(name = "performance_impact_score")
    private Double performanceImpactScore;
    
    @Column(name = "sla_compliance_probability")
    private Double slaComplianceProbability;
    
    @Column(name = "availability_impact")
    private Double availabilityImpact;
    
    // External factors
    @Type(type = "jsonb")
    @Column(name = "external_factors", columnDefinition = "jsonb")
    private Map<String, Object> externalFactors;
    
    @Column(name = "weather_impact")
    private Boolean weatherImpact;
    
    @Column(name = "business_event_impact")
    private Boolean businessEventImpact;
    
    @Column(name = "seasonal_factor")
    private Double seasonalFactor;
    
    // Validation and feedback
    @Column(name = "actual_instances")
    private Integer actualInstances;
    
    @Column(name = "actual_cpu_utilization")
    private Double actualCpuUtilization;
    
    @Column(name = "actual_memory_utilization")
    private Double actualMemoryUtilization;
    
    @Column(name = "prediction_accuracy")
    private Double predictionAccuracy;
    
    @Column(name = "mae_instances") // Mean Absolute Error
    private Double maeInstances;
    
    @Column(name = "mape_utilization") // Mean Absolute Percentage Error
    private Double mapeUtilization;
    
    @Column(name = "validated_at")
    private LocalDateTime validatedAt;
    
    // Metadata
    @Type(type = "jsonb")
    @Column(name = "model_metadata", columnDefinition = "jsonb")
    private Map<String, Object> modelMetadata;
    
    @Type(type = "jsonb")
    @Column(name = "prediction_metadata", columnDefinition = "jsonb")
    private Map<String, Object> predictionMetadata;
    
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
        predictedAt = LocalDateTime.now();
        
        if (predictionId == null) {
            predictionId = "PRED_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public enum PredictionType {
        LOAD_FORECAST,           // General load prediction
        TRAFFIC_SPIKE,           // Traffic spike prediction
        PERFORMANCE_DEGRADATION, // Performance issues prediction
        COST_OPTIMIZATION,       // Cost optimization opportunities
        CAPACITY_PLANNING,       // Long-term capacity needs
        ANOMALY_DETECTION,       // Anomalous behavior prediction
        MAINTENANCE_IMPACT,      // Maintenance window impact
        SEASONAL_ADJUSTMENT,     // Seasonal traffic patterns
        REAL_TIME_REACTIVE,      // Real-time reactive scaling
        PROACTIVE_SCALING        // Proactive scaling recommendation
    }
    
    public enum PredictionStatus {
        ACTIVE,      // Prediction is currently valid
        EXPIRED,     // Prediction has expired
        VALIDATED,   // Prediction has been validated with actual data
        SUPERSEDED,  // Replaced by a newer prediction
        CANCELLED,   // Prediction was cancelled
        FAILED       // Prediction generation failed
    }
    
    public enum ScalingAction {
        SCALE_UP,     // Increase resources
        SCALE_DOWN,   // Decrease resources
        MAINTAIN,     // Keep current resources
        OPTIMIZE,     // Optimize resource allocation
        INVESTIGATE   // Investigate before scaling
    }
    
    public enum ScalingUrgency {
        IMMEDIATE,    // Scale immediately
        HIGH,         // Scale within 5 minutes
        MEDIUM,       // Scale within 15 minutes
        LOW,          // Scale within 30 minutes
        PLANNED       // Schedule scaling action
    }
    
    // Nested classes for JSON storage
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PredictionInterval {
        private Integer timeOffsetMinutes;
        private Double predictedValue;
        private Double lowerBound;
        private Double upperBound;
        private Double confidence;
        private String metricName;
    }
    
    // Validation methods
    
    public boolean isValid() {
        return status == PredictionStatus.ACTIVE && 
               LocalDateTime.now().isBefore(validUntil) &&
               confidenceScore >= 0.5;
    }
    
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(validUntil);
    }
    
    public boolean isHighConfidence() {
        return confidenceScore >= 0.8;
    }
    
    public boolean requiresImmediateAction() {
        return scalingUrgency == ScalingUrgency.IMMEDIATE;
    }
    
    public boolean isPredictingScaleUp() {
        return scalingAction == ScalingAction.SCALE_UP;
    }
    
    public boolean isPredictingScaleDown() {
        return scalingAction == ScalingAction.SCALE_DOWN;
    }
    
    public boolean hasSignificantCostImpact() {
        return costImpact != null && costImpact.abs().compareTo(new BigDecimal("10.00")) > 0;
    }
    
    public boolean hasPerformanceRisk() {
        return slaComplianceProbability != null && slaComplianceProbability < 0.95;
    }
    
    public int getScalingDelta() {
        if (recommendedInstances != null && currentInstances != null) {
            return recommendedInstances - currentInstances;
        }
        return 0;
    }
    
    public double getScalingPercentage() {
        if (currentInstances != null && currentInstances > 0 && recommendedInstances != null) {
            return ((double) (recommendedInstances - currentInstances) / currentInstances) * 100.0;
        }
        return 0.0;
    }
    
    public void expire() {
        this.status = PredictionStatus.EXPIRED;
        this.updatedAt = LocalDateTime.now();
    }
    
    public void validate(Integer actualInstances, Double actualCpu, Double actualMemory) {
        this.actualInstances = actualInstances;
        this.actualCpuUtilization = actualCpu;
        this.actualMemoryUtilization = actualMemory;
        this.validatedAt = LocalDateTime.now();
        this.status = PredictionStatus.VALIDATED;
        
        // Calculate prediction accuracy
        if (this.predictedInstances != null && actualInstances != null) {
            this.maeInstances = Math.abs(this.predictedInstances - actualInstances) / (double) actualInstances;
        }
        
        if (this.predictedCpuUtilization != null && actualCpu != null) {
            this.mapeUtilization = Math.abs(this.predictedCpuUtilization - actualCpu) / actualCpu * 100.0;
        }
        
        // Overall prediction accuracy (simple average for now)
        double accuracy = 0.0;
        int components = 0;
        
        if (this.maeInstances != null) {
            accuracy += (1.0 - Math.min(this.maeInstances, 1.0));
            components++;
        }
        
        if (this.mapeUtilization != null) {
            accuracy += (1.0 - Math.min(this.mapeUtilization / 100.0, 1.0));
            components++;
        }
        
        if (components > 0) {
            this.predictionAccuracy = accuracy / components;
        }
    }
    
    public void supersede() {
        this.status = PredictionStatus.SUPERSEDED;
        this.updatedAt = LocalDateTime.now();
    }
    
    public void cancel(String reason) {
        this.status = PredictionStatus.CANCELLED;
        this.scalingReason = reason;
        this.updatedAt = LocalDateTime.now();
    }
    
    public boolean shouldTriggerScaling(Double currentCpuThreshold, Double currentMemoryThreshold) {
        // Check if predicted utilization exceeds thresholds
        if (predictedCpuUtilization != null && predictedCpuUtilization > currentCpuThreshold) {
            return true;
        }
        
        if (predictedMemoryUtilization != null && predictedMemoryUtilization > currentMemoryThreshold) {
            return true;
        }
        
        // Check if scaling urgency is high
        if (scalingUrgency == ScalingUrgency.IMMEDIATE || scalingUrgency == ScalingUrgency.HIGH) {
            return true;
        }
        
        return false;
    }
    
    public Map<String, Object> toScalingEvent() {
        Map<String, Object> event = new java.util.HashMap<>();
        event.put("predictionId", predictionId);
        event.put("serviceName", serviceName);
        event.put("namespace", namespace);
        event.put("scalingAction", scalingAction);
        event.put("currentInstances", currentInstances);
        event.put("recommendedInstances", recommendedInstances);
        event.put("scalingUrgency", scalingUrgency);
        event.put("confidenceScore", confidenceScore);
        event.put("scalingReason", scalingReason);
        event.put("predictedAt", predictedAt);
        event.put("validUntil", validUntil);
        return event;
    }
}