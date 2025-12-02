package com.waqiti.common.observability.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Error metric for performance analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorMetric {
    private String errorType;
    private String errorMessage;
    private long count;
    private double percentage;
    private LocalDateTime lastOccurrence;
    private List<String> affectedEndpoints;
}