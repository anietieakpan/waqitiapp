package com.waqiti.common.observability.dto;

import com.waqiti.common.enums.ViolationSeverity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Error information for observability reporting
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorInfo {
    private String errorType;
    private String errorMessage;
    private long occurrenceCount;
    private ViolationSeverity severity;
    private LocalDateTime firstSeen;
    private LocalDateTime lastSeen;
    private List<String> affectedEndpoints;
    private Map<String, String> metadata;
    private boolean isResolved;
    private String resolution;
}