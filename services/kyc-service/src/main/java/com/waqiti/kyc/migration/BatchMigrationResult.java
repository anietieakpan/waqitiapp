package com.waqiti.kyc.migration;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class BatchMigrationResult {
    
    private int totalUsers;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private List<MigrationResult> results = new ArrayList<>();
    
    public void addResult(MigrationResult result) {
        results.add(result);
    }
    
    public int getSuccessCount() {
        return (int) results.stream().filter(MigrationResult::isSuccessful).count();
    }
    
    public int getFailedCount() {
        return (int) results.stream().filter(MigrationResult::isFailed).count();
    }
    
    public int getSkippedCount() {
        return (int) results.stream().filter(MigrationResult::isSkipped).count();
    }
    
    public int getProcessedCount() {
        return results.size();
    }
    
    public double getSuccessRate() {
        if (results.isEmpty()) return 0.0;
        return (double) getSuccessCount() / results.size() * 100.0;
    }
    
    public long getTotalDurationMillis() {
        if (startTime != null && endTime != null) {
            return java.time.Duration.between(startTime, endTime).toMillis();
        }
        return 0;
    }
    
    public int getTotalMigratedVerifications() {
        return results.stream().mapToInt(MigrationResult::getMigratedVerifications).sum();
    }
    
    public int getTotalMigratedDocuments() {
        return results.stream().mapToInt(MigrationResult::getMigratedDocuments).sum();
    }
    
    public List<MigrationResult> getFailedResults() {
        return results.stream().filter(MigrationResult::isFailed).toList();
    }
}