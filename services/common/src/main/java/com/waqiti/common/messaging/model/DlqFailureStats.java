package com.waqiti.common.messaging.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Statistics for DLQ failures by topic
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DlqFailureStats {
    
    private String topicName;
    private int failureCount;
    private Map<String, Integer> failureTypesCounts;
    private LocalDateTime firstFailureAt;
    private LocalDateTime lastFailureAt;
    private double failureRate;
    
    public void incrementFailureCount() {
        failureCount++;
    }
    
    public void addFailureType(String failureType) {
        failureTypesCounts.put(failureType, 
            failureTypesCounts.getOrDefault(failureType, 0) + 1);
    }
    
    public void setLastFailureAt(LocalDateTime lastFailureAt) {
        this.lastFailureAt = lastFailureAt;
    }
}