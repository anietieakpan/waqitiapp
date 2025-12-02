package com.waqiti.common.encryption;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Migration execution result
 */
@Data
public class MigrationResult {
    
    private String migrationId;
    private MigrationStatus status;
    private long totalRecords;
    private long processedCount;
    private int errorCount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String errorMessage;
    private String verificationDetails;
    private List<String> errors = new ArrayList<>();
    
    public MigrationResult(String migrationId) {
        this.migrationId = migrationId;
        this.status = MigrationStatus.RUNNING;
        this.startTime = LocalDateTime.now();
    }
    
    public static MigrationResult failed(String migrationId, String errorMessage) {
        MigrationResult result = new MigrationResult(migrationId);
        result.setStatus(MigrationStatus.FAILED);
        result.setErrorMessage(errorMessage);
        result.setEndTime(LocalDateTime.now());
        return result;
    }
    
    public void addError(String error) {
        errors.add(error);
        errorCount++;
    }
    
    public boolean isSuccessful() {
        return status == MigrationStatus.COMPLETED;
    }
    
    public long getDurationMinutes() {
        if (startTime != null && endTime != null) {
            return java.time.Duration.between(startTime, endTime).toMinutes();
        }
        return 0;
    }
    
    /**
     * Get human-readable summary
     */
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append(String.format("Migration %s: %s\n", migrationId, status));
        summary.append(String.format("Records processed: %d/%d\n", processedCount, totalRecords));
        
        if (errorCount > 0) {
            summary.append(String.format("Errors encountered: %d\n", errorCount));
        }
        
        if (getDurationMinutes() > 0) {
            summary.append(String.format("Duration: %d minutes\n", getDurationMinutes()));
        }
        
        if (verificationDetails != null) {
            summary.append(String.format("Verification: %s\n", verificationDetails));
        }
        
        return summary.toString();
    }
}

enum MigrationStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    COMPLETED_WITH_ERRORS,
    FAILED,
    CANCELLED
}