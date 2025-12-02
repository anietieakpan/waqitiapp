package com.waqiti.reconciliation.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks the status and progress of a reconciliation operation
 */
@Data
public class ReconciliationStatus {

    private final String reconciliationId;
    private final LocalDateTime startTime;
    private LocalDateTime endTime;
    private boolean completed;
    private boolean successful;
    private String error;
    private int totalTransactions;
    private final AtomicInteger processedTransactions;
    private final AtomicInteger matchedTransactions;
    private final AtomicInteger discrepancyTransactions;
    private final AtomicInteger pendingTransactions;
    private String currentPhase;
    private double progressPercentage;

    public ReconciliationStatus(String reconciliationId) {
        this.reconciliationId = reconciliationId;
        this.startTime = LocalDateTime.now();
        this.completed = false;
        this.successful = false;
        this.processedTransactions = new AtomicInteger(0);
        this.matchedTransactions = new AtomicInteger(0);
        this.discrepancyTransactions = new AtomicInteger(0);
        this.pendingTransactions = new AtomicInteger(0);
        this.currentPhase = "INITIALIZING";
        this.progressPercentage = 0.0;
    }

    // Progress tracking methods
    public void setTotalTransactions(int total) {
        this.totalTransactions = total;
        updateProgress();
    }

    public void incrementProcessed() {
        this.processedTransactions.incrementAndGet();
        updateProgress();
    }

    public void incrementMatched() {
        this.matchedTransactions.incrementAndGet();
    }

    public void incrementDiscrepancy() {
        this.discrepancyTransactions.incrementAndGet();
    }

    public void incrementPending() {
        this.pendingTransactions.incrementAndGet();
    }

    public void setCurrentPhase(String phase) {
        this.currentPhase = phase;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
        if (completed) {
            this.endTime = LocalDateTime.now();
            this.successful = (error == null);
            this.progressPercentage = 100.0;
        }
    }

    public void setError(String error) {
        this.error = error;
        this.successful = false;
    }

    private void updateProgress() {
        if (totalTransactions > 0) {
            this.progressPercentage = (processedTransactions.get() * 100.0) / totalTransactions;
        }
    }

    // Utility methods
    public long getDurationMillis() {
        LocalDateTime end = endTime != null ? endTime : LocalDateTime.now();
        return java.time.Duration.between(startTime, end).toMillis();
    }

    public long getDurationSeconds() {
        return getDurationMillis() / 1000;
    }

    public boolean isRunning() {
        return !completed;
    }

    public boolean hasError() {
        return error != null;
    }

    public double getMatchingRate() {
        int processed = processedTransactions.get();
        if (processed == 0) {
            return 0.0;
        }
        return (matchedTransactions.get() * 100.0) / processed;
    }

    public double getDiscrepancyRate() {
        int processed = processedTransactions.get();
        if (processed == 0) {
            return 0.0;
        }
        return (discrepancyTransactions.get() * 100.0) / processed;
    }

    public String getStatusSummary() {
        if (!completed) {
            return String.format("Running - %s (%.1f%% complete)", currentPhase, progressPercentage);
        }
        
        if (successful) {
            return String.format("Completed successfully in %d seconds", getDurationSeconds());
        } else {
            return String.format("Failed after %d seconds - %s", getDurationSeconds(), error);
        }
    }

    public String getDetailedStatus() {
        return String.format(
            "Reconciliation %s - %s | Total: %d, Processed: %d, Matched: %d, Discrepancies: %d, Pending: %d | %.1f%% complete",
            reconciliationId,
            getStatusSummary(),
            totalTransactions,
            processedTransactions.get(),
            matchedTransactions.get(),
            discrepancyTransactions.get(),
            pendingTransactions.get(),
            progressPercentage
        );
    }
}