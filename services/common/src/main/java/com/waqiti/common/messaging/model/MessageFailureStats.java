package com.waqiti.common.messaging.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Statistics for message failures
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageFailureStats {
    
    private String messageType;
    @Builder.Default
    private int totalFailures = 0;
    @Builder.Default
    private Map<String, Integer> errorCounts = new ConcurrentHashMap<>();
    @Builder.Default
    private Map<String, String> lastErrorMessages = new ConcurrentHashMap<>();
    private LocalDateTime firstFailure;
    private LocalDateTime lastFailure;
    @Builder.Default
    private double averageFailureRate = 0.0;
    @Builder.Default
    private long totalProcessingTime = 0L;
    @Builder.Default
    private long failureCount = 0L;
    @Builder.Default
    private java.util.Set<String> failureTypes = new java.util.HashSet<>();
    private LocalDateTime lastFailureAt;
    
    /**
     * Additional getter methods
     */
    public long getFailureCount() { return failureCount; }
    public void incrementFailureCount() { this.failureCount++; }
    public void addFailureType(String type) { this.failureTypes.add(type); }
    public void setLastFailureAt(LocalDateTime time) { this.lastFailureAt = time; }
    
    /**
     * Record a failure occurrence
     */
    public void recordFailure(String errorType, String errorMessage, Map<String, Object> context) {
        totalFailures++;
        errorCounts.merge(errorType, 1, Integer::sum);
        lastErrorMessages.put(errorType, errorMessage);
        
        LocalDateTime now = LocalDateTime.now();
        if (firstFailure == null) {
            firstFailure = now;
        }
        lastFailure = now;
    }
    
    /**
     * Get failure rate as percentage
     */
    public double getFailureRate() {
        return totalFailures > 0 ? averageFailureRate : 0.0;
    }
    
    /**
     * Get most common error type
     */
    public String getMostCommonError() {
        return errorCounts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("UNKNOWN");
    }
}