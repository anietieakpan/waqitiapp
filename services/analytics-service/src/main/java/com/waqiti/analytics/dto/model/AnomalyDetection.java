package com.waqiti.analytics.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Anomaly detection model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyDetection {
    private LocalDateTime detectedAt;
    private String anomalyType;
    private BigDecimal severity;
    private String description;
    private BigDecimal expectedValue;
    private BigDecimal actualValue;
}