package com.waqiti.common.observability.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents error trend analysis data for observability
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorTrend {
    private String errorType;
    private String trendDirection; // increasing, decreasing, stable
    private double trendSlope;
    private List<DataPoint> dataPoints;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String analysis;
    private String recommendation;
}
