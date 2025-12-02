package com.waqiti.analytics.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Domain model for anomaly detection results
 */
@Document(collection = "anomaly_detection_results")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyDetectionResult {
    
    @Id
    private String id;
    
    private String resultId;
    
    private String detectionId;
    
    private String resultType;
    
    private String userId;
    
    private String status;
    
    private Double confidence;
    
    private LocalDateTime processedAt;
    
    private String correlationId;
    
    private Map<String, Object> resultData;
    
    private Map<String, Object> metadata;
}