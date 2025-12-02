package com.waqiti.common.messaging.analysis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Represents a detected failure pattern in message processing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailurePattern {
    
    private String patternId;
    private String description;
    private FailureSeverity severity;
    private String affectedTopic;
    private String failureType;
    private int occurrenceCount;
    private LocalDateTime firstOccurrence;
    private LocalDateTime lastOccurrence;
    private List<String> affectedMessageIds;
    private Map<String, Object> metadata;
    private double confidence;
    private String recommendedAction;
    
    /**
     * Check if this pattern is critical
     */
    public boolean isCritical() {
        return severity == FailureSeverity.CRITICAL;
    }
    
    /**
     * Check if pattern is recent
     */
    public boolean isRecent(int hoursThreshold) {
        return lastOccurrence != null && 
               lastOccurrence.isAfter(LocalDateTime.now().minusHours(hoursThreshold));
    }
}