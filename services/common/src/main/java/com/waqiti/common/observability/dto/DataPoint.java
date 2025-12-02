package com.waqiti.common.observability.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents a single data point in time series data
 * Used across observability DTOs for metrics, trends, and analytics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataPoint {
    
    private LocalDateTime timestamp;
    private double value;
    private String label;
    private String unit;
    private String category;
    
    /**
     * Create a simple data point with timestamp and value
     */
    public static DataPoint of(LocalDateTime timestamp, double value) {
        return DataPoint.builder()
            .timestamp(timestamp)
            .value(value)
            .build();
    }
    
    /**
     * Create a labeled data point
     */
    public static DataPoint of(LocalDateTime timestamp, double value, String label) {
        return DataPoint.builder()
            .timestamp(timestamp)
            .value(value)
            .label(label)
            .build();
    }
    
    /**
     * Create a data point with unit
     */
    public static DataPoint of(LocalDateTime timestamp, double value, String label, String unit) {
        return DataPoint.builder()
            .timestamp(timestamp)
            .value(value)
            .label(label)
            .unit(unit)
            .build();
    }
}