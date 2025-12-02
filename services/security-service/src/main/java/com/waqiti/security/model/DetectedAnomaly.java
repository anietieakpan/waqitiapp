package com.waqiti.security.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Detected Anomaly
 * Represents a single detected anomaly in authentication behavior
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetectedAnomaly {

    private String anomalyType;
    private AnomalySeverity severity;
    private Double confidence;
    private String description;
    private Map<String, Object> evidence;
    private Integer riskScore;
    private Instant detectedAt;
    private String detectionMethod;
    private String modelVersion;
}
