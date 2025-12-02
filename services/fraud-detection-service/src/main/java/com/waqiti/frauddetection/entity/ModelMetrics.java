package com.waqiti.frauddetection.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/**
 * Model Metrics Entity
 * Tracks ML model performance metrics
 */
@Entity
@Table(name = "model_metrics", indexes = {
    @Index(name = "idx_metrics_model", columnList = "modelName"),
    @Index(name = "idx_metrics_recorded", columnList = "recordedAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelMetrics {
    
    @Id
    @Column(name = "metric_id", nullable = false, length = 36)
    private String metricId;
    
    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;
    
    @Column(name = "model_version", length = 20)
    private String modelVersion;
    
    @Column(name = "accuracy", precision = 5, scale = 4)
    private Double accuracy; // 0.0000 to 1.0000
    
    @Column(name = "precision_score", precision = 5, scale = 4)
    private Double precision; // 0.0000 to 1.0000
    
    @Column(name = "recall", precision = 5, scale = 4)
    private Double recall; // 0.0000 to 1.0000
    
    @Column(name = "f1_score", precision = 5, scale = 4)
    private Double f1Score; // 0.0000 to 1.0000
    
    @Column(name = "auc_roc", precision = 5, scale = 4)
    private Double aucRoc; // Area Under ROC Curve
    
    @Column(name = "true_positives")
    private Long truePositives;
    
    @Column(name = "false_positives")
    private Long falsePositives;
    
    @Column(name = "true_negatives")
    private Long trueNegatives;
    
    @Column(name = "false_negatives")
    private Long falseNegatives;
    
    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;
    
    @Column(name = "evaluation_dataset", length = 100)
    private String evaluationDataset;
    
    @Column(name = "notes", length = 500)
    private String notes;
}
