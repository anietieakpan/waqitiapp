package com.waqiti.common.encryption;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Real-time migration progress tracking
 */
@Data
public class MigrationProgress {
    
    private String migrationId;
    private MigrationRequest request;
    private LocalDateTime startTime;
    private long totalRecords;
    private long processedRecords;
    private double progressPercent;
    private boolean completed;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private String currentBatch;
    private String errorMessage;
    private List<String> recentErrors = new ArrayList<>();
    
    public MigrationProgress(String migrationId, MigrationRequest request) {
        this.migrationId = migrationId;
        this.request = request;
        this.startTime = LocalDateTime.now();
        this.progressPercent = 0.0;
    }
    
    public boolean isCancelled() {
        return cancelled.get();
    }
    
    public void setCancelled(boolean cancelled) {
        this.cancelled.set(cancelled);
    }
    
    public void addError(String error) {
        recentErrors.add(LocalDateTime.now() + ": " + error);
        
        // Keep only last 10 errors
        if (recentErrors.size() > 10) {
            recentErrors.remove(0);
        }
    }
    
    public void setError(String errorMessage) {
        this.errorMessage = errorMessage;
        addError(errorMessage);
    }
    
    /**
     * Get estimated completion time
     */
    public LocalDateTime getEstimatedCompletion() {
        if (progressPercent <= 0 || processedRecords <= 0) {
            return null;
        }
        
        long elapsedMillis = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
        long estimatedTotalMillis = (long) (elapsedMillis / (progressPercent / 100.0));
        long remainingMillis = estimatedTotalMillis - elapsedMillis;
        
        return LocalDateTime.now().plusNanos(remainingMillis * 1_000_000);
    }
    
    /**
     * Get processing rate (records per second)
     */
    public double getProcessingRate() {
        if (processedRecords <= 0) {
            return 0.0;
        }
        
        long elapsedSeconds = java.time.Duration.between(startTime, LocalDateTime.now()).getSeconds();
        if (elapsedSeconds <= 0) {
            return 0.0;
        }
        
        return (double) processedRecords / elapsedSeconds;
    }
    
    /**
     * Get current status summary
     */
    public String getStatusSummary() {
        if (cancelled.get()) {
            return "CANCELLED";
        } else if (completed) {
            return "COMPLETED";
        } else if (errorMessage != null) {
            return "ERROR: " + errorMessage;
        } else {
            return String.format("RUNNING (%.2f%%)", progressPercent);
        }
    }
}