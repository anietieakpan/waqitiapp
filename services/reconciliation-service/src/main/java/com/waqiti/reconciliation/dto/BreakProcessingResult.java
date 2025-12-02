package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BreakProcessingResult {

    private int totalBreaks;
    
    private int resolvedBreaks;
    
    private int pendingBreaks;
    
    @Builder.Default
    private int breakCount = 0;
    
    private List<BreakProcessingSummary> processingSummaries;
    
    private Map<String, Integer> breaksByType;
    
    private Map<String, Integer> breaksBySeverity;
    
    private List<BreakResolutionAttempt> resolutionAttempts;
    
    @Builder.Default
    private LocalDateTime processingCompletedAt = LocalDateTime.now();
    
    private Long totalProcessingTimeMs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BreakProcessingSummary {
        private UUID breakId;
        private String breakType;
        private String processingStatus;
        private boolean resolved;
        private String resolutionMethod;
        private String failureReason;
        private LocalDateTime processedAt;
        private Long processingTimeMs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BreakResolutionAttempt {
        private UUID breakId;
        private String attemptType;
        private boolean successful;
        private String resolutionMethod;
        private String errorMessage;
        private LocalDateTime attemptedAt;
        private String attemptedBy;
    }

    public int getBreakCount() {
        return totalBreaks;
    }

    public double getResolutionRate() {
        if (totalBreaks == 0) return 0.0;
        return (double) resolvedBreaks / totalBreaks * 100.0;
    }

    public boolean hasUnresolvedBreaks() {
        return pendingBreaks > 0;
    }

    public boolean allBreaksResolved() {
        return pendingBreaks == 0 && resolvedBreaks == totalBreaks;
    }

    public int getFailedResolutions() {
        return totalBreaks - resolvedBreaks - pendingBreaks;
    }
}