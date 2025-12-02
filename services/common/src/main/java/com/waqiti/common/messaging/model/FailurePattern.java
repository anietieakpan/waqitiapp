package com.waqiti.common.messaging.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Represents a detected failure pattern in messaging
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailurePattern {
    
    private String patternId;
    private String patternType;
    private String description;
    private double severity;
    private int occurrenceCount;
    private LocalDateTime firstOccurrence;
    private LocalDateTime lastOccurrence;
    private List<String> affectedTopics;
    private Map<String, Object> patternData;
    private boolean isResolved;
    private String resolution;
    
    /**
     * Pattern types enumeration
     */
    public enum PatternType {
        RECURRING_FAILURE,
        CASCADING_FAILURE,
        RESOURCE_EXHAUSTION,
        CONFIGURATION_ERROR,
        NETWORK_PARTITION,
        TIMEOUT_PATTERN,
        CIRCUIT_BREAKER_OPEN,
        DEADLOCK_PATTERN
    }
    
    /**
     * Check if pattern is critical
     */
    public boolean isCritical() {
        return severity >= 0.8;
    }
    
    /**
     * Check if pattern is recurring
     */
    public boolean isRecurring() {
        return occurrenceCount > 1;
    }
}