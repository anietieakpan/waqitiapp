package com.waqiti.ml.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity to track ML model performance metrics
 */
@Entity
@Table(name = "model_performance_metrics")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModelPerformanceMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(name = "model_version", nullable = false, length = 50)
    private String modelVersion;

    @Column(name = "accuracy", precision = 10, scale = 6)
    private Double accuracy;

    @Column(name = "precision", precision = 10, scale = 6)
    private Double precision;

    @Column(name = "recall", precision = 10, scale = 6)
    private Double recall;

    @Column(name = "f1_score", precision = 10, scale = 6)
    private Double f1Score;

    @Column(name = "auc_roc", precision = 10, scale = 6)
    private Double aucRoc;

    @Column(name = "prediction_count")
    private Long predictionCount = 0L;

    @Column(name = "error_count")
    private Long errorCount = 0L;

    @Column(name = "average_inference_time_ms")
    private Double averageInferenceTimeMs;

    @Column(name = "drift_score", precision = 10, scale = 6)
    private Double driftScore;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    /**
     * Calculate error rate
     */
    public double getErrorRate() {
        if (predictionCount == null || predictionCount == 0) {
            return 0.0;
        }
        Long errors = errorCount != null ? errorCount : 0L;
        return (double) errors / predictionCount;
    }

    /**
     * Check if model performance is healthy
     */
    public boolean isHealthy() {
        return accuracy != null && accuracy > 0.8 && getErrorRate() < 0.1;
    }

    /**
     * Increment prediction count
     */
    public void incrementPredictionCount() {
        if (predictionCount == null) {
            predictionCount = 1L;
        } else {
            predictionCount++;
        }
    }

    /**
     * Increment error count
     */
    public void incrementErrorCount() {
        if (errorCount == null) {
            errorCount = 1L;
        } else {
            errorCount++;
        }
    }
}