package com.waqiti.analytics.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Anomaly pattern model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyPattern {
    
    private String patternId;
    private String type;
    private String entityId;
    private String description;
    private String severity;
    private Instant detectedAt;
}