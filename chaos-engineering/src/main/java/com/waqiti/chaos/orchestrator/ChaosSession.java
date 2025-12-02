package com.waqiti.chaos.orchestrator;

import com.waqiti.chaos.core.ChaosResult;
import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ChaosSession {
    private String sessionId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private ChaosSessionConfig config;
    private int totalExperiments;
    private List<ChaosResult> results;
    private ChaosResult resilienceTestResult;
    private ValidationResult preValidation;
    private ValidationResult postValidation;
    private boolean aborted;
    private boolean degraded;
    private Throwable error;
    
    public Duration getDuration() {
        if (startTime != null && endTime != null) {
            return Duration.between(startTime, endTime);
        }
        return Duration.ZERO;
    }
    
    public int getSuccessfulExperiments() {
        if (results == null) return 0;
        return (int) results.stream().filter(ChaosResult::isSuccess).count();
    }
    
    public int getFailedExperiments() {
        if (results == null) return 0;
        return (int) results.stream().filter(r -> !r.isSuccess()).count();
    }
    
    public double getSuccessRate() {
        if (results == null || results.isEmpty()) return 0;
        return (double) getSuccessfulExperiments() / results.size();
    }
}