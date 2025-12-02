package com.waqiti.notification.model;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Model for tracking email queue state per category
 */
@Data
@Slf4j
public class EmailQueueState {
    
    private final String category;
    private final AtomicLong queuedCount = new AtomicLong(0);
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong deliveredCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);
    private final AtomicLong transactionalCount = new AtomicLong(0);
    private final AtomicLong marketingCount = new AtomicLong(0);
    private final AtomicLong alertCount = new AtomicLong(0);
    private final AtomicLong batchCount = new AtomicLong(0);
    private final AtomicLong totalBatchEmails = new AtomicLong(0);
    
    private LocalDateTime createdAt;
    private LocalDateTime lastUpdated;
    private double avgProcessingTime = 0.0;
    private double deliveryRate = 1.0;
    private String status = "ACTIVE";
    
    public EmailQueueState(String category) {
        this.category = category;
        this.createdAt = LocalDateTime.now();
        this.lastUpdated = LocalDateTime.now();
    }
    
    public void incrementQueued() {
        queuedCount.incrementAndGet();
        updateTimestamp();
        log.debug("Email queued for category: {}. Total queued: {}", category, queuedCount.get());
    }
    
    public void incrementProcessed() {
        processedCount.incrementAndGet();
        updateTimestamp();
    }
    
    public void incrementDelivered() {
        deliveredCount.incrementAndGet();
        updateDeliveryRate();
        updateTimestamp();
    }
    
    public void incrementFailed() {
        failedCount.incrementAndGet();
        updateDeliveryRate();
        updateTimestamp();
    }
    
    public void incrementTransactional() {
        transactionalCount.incrementAndGet();
        incrementQueued();
    }
    
    public void incrementMarketing() {
        marketingCount.incrementAndGet();
        incrementQueued();
    }
    
    public void incrementAlert() {
        alertCount.incrementAndGet();
        incrementQueued();
    }
    
    public void addBatch(String batchId, int emailCount) {
        batchCount.incrementAndGet();
        totalBatchEmails.addAndGet(emailCount);
        updateTimestamp();
        log.debug("Batch {} added for category: {}. {} emails in batch", batchId, category, emailCount);
    }
    
    public void update(Consumer<EmailQueueState> updater) {
        try {
            updater.accept(this);
            updateTimestamp();
        } catch (Exception e) {
            log.error("Error updating queue state for category {}: {}", category, e.getMessage());
        }
    }
    
    public long getTotalEmailsSent() {
        return deliveredCount.get() + failedCount.get();
    }
    
    public long getPendingEmails() {
        return Math.max(0, queuedCount.get() - processedCount.get());
    }
    
    public double getSuccessRate() {
        long totalSent = getTotalEmailsSent();
        if (totalSent == 0) return 1.0;
        return (double) deliveredCount.get() / totalSent;
    }
    
    public double getFailureRate() {
        long totalSent = getTotalEmailsSent();
        if (totalSent == 0) return 0.0;
        return (double) failedCount.get() / totalSent;
    }
    
    public boolean isHealthy() {
        return getSuccessRate() > 0.95 && "ACTIVE".equals(status);
    }
    
    public String getHealthStatus() {
        double successRate = getSuccessRate();
        if (successRate > 0.98) return "EXCELLENT";
        if (successRate > 0.95) return "GOOD";
        if (successRate > 0.90) return "WARNING";
        return "CRITICAL";
    }
    
    public EmailQueueMetrics getMetrics() {
        return EmailQueueMetrics.builder()
            .category(category)
            .queuedCount(queuedCount.get())
            .processedCount(processedCount.get())
            .deliveredCount(deliveredCount.get())
            .failedCount(failedCount.get())
            .pendingCount(getPendingEmails())
            .successRate(getSuccessRate())
            .failureRate(getFailureRate())
            .deliveryRate(deliveryRate)
            .avgProcessingTime(avgProcessingTime)
            .status(status)
            .healthStatus(getHealthStatus())
            .transactionalCount(transactionalCount.get())
            .marketingCount(marketingCount.get())
            .alertCount(alertCount.get())
            .batchCount(batchCount.get())
            .totalBatchEmails(totalBatchEmails.get())
            .createdAt(createdAt)
            .lastUpdated(lastUpdated)
            .build();
    }
    
    public void reset() {
        queuedCount.set(0);
        processedCount.set(0);
        deliveredCount.set(0);
        failedCount.set(0);
        transactionalCount.set(0);
        marketingCount.set(0);
        alertCount.set(0);
        batchCount.set(0);
        totalBatchEmails.set(0);
        avgProcessingTime = 0.0;
        deliveryRate = 1.0;
        status = "ACTIVE";
        updateTimestamp();
        log.info("Queue state reset for category: {}", category);
    }
    
    private void updateTimestamp() {
        lastUpdated = LocalDateTime.now();
    }
    
    private void updateDeliveryRate() {
        long totalSent = getTotalEmailsSent();
        if (totalSent > 0) {
            deliveryRate = (double) deliveredCount.get() / totalSent;
        }
    }
    
    // Builder pattern for metrics
    @lombok.Builder
    @lombok.Data
    public static class EmailQueueMetrics {
        private String category;
        private long queuedCount;
        private long processedCount;
        private long deliveredCount;
        private long failedCount;
        private long pendingCount;
        private double successRate;
        private double failureRate;
        private double deliveryRate;
        private double avgProcessingTime;
        private String status;
        private String healthStatus;
        private long transactionalCount;
        private long marketingCount;
        private long alertCount;
        private long batchCount;
        private long totalBatchEmails;
        private LocalDateTime createdAt;
        private LocalDateTime lastUpdated;
    }
}