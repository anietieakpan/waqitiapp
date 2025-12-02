package com.waqiti.compliance.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ml_detection_results")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MLDetectionResult {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "account_id", nullable = false)
    private UUID accountId;
    
    @Column(name = "pattern_type", nullable = false, length = 100)
    private String patternType;
    
    @Column(name = "confidence_score", nullable = false)
    private Double confidenceScore;
    
    @Column(name = "associated_amount", precision = 19, scale = 2)
    private BigDecimal associatedAmount;
    
    @Column(name = "model_name", length = 100)
    private String modelName;
    
    @Column(name = "model_version", length = 50)
    private String modelVersion;
    
    @Column(name = "anomaly_description", columnDefinition = "TEXT")
    private String anomalyDescription;
    
    @ElementCollection
    @CollectionTable(name = "ml_detection_metadata", 
                      joinColumns = @JoinColumn(name = "detection_id"))
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value")
    private Map<String, String> metadata = new HashMap<>();
    
    @Column(name = "is_confirmed")
    private Boolean isConfirmed;
    
    @Column(name = "is_false_positive")
    private Boolean isFalsePositive;
    
    @Column(name = "reviewed_by")
    private String reviewedBy;
    
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
    
    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;
    
    @CreationTimestamp
    @Column(name = "detected_at", nullable = false, updatable = false)
    private LocalDateTime detectedAt;
    
    @Column(name = "reported_at")
    private LocalDateTime reportedAt;
    
    @Column(name = "alert_generated")
    private Boolean alertGenerated;
    
    @Column(name = "alert_id")
    private UUID alertId;
}