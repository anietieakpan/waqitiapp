package com.waqiti.webhook.model;

import com.waqiti.webhook.entity.Webhook;
import com.waqiti.webhook.entity.WebhookDelivery;
import lombok.Data;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Webhook delivery task for processing
 */
@Data
public class WebhookDeliveryTask implements Comparable<WebhookDeliveryTask> {
    
    private final Webhook webhook;
    private final WebhookPayload payload;
    private final AtomicInteger attemptNumber;
    private final CompletableFuture<WebhookDelivery> future;
    private final long createdAt;
    
    public WebhookDeliveryTask(Webhook webhook, WebhookPayload payload, int initialAttempt) {
        this.webhook = webhook;
        this.payload = payload;
        this.attemptNumber = new AtomicInteger(initialAttempt);
        this.future = new CompletableFuture<>();
        this.createdAt = System.currentTimeMillis();
    }
    
    /**
     * Increment attempt number
     */
    public void incrementAttempt() {
        attemptNumber.incrementAndGet();
    }
    
    /**
     * Get current attempt number
     */
    public int getAttemptNumber() {
        return attemptNumber.get();
    }
    
    /**
     * Complete task with result
     */
    public void complete(WebhookDelivery delivery) {
        future.complete(delivery);
    }
    
    /**
     * Complete task with exception
     */
    public void completeExceptionally(Throwable throwable) {
        future.completeExceptionally(throwable);
    }
    
    /**
     * Check if task is completed
     */
    public boolean isCompleted() {
        return future.isDone();
    }
    
    /**
     * Get task age in milliseconds
     */
    public long getAgeMs() {
        return System.currentTimeMillis() - createdAt;
    }
    
    /**
     * Compare tasks for priority queue ordering
     * Higher priority tasks come first
     */
    @Override
    public int compareTo(WebhookDeliveryTask other) {
        // First compare by priority
        int priorityCompare = Integer.compare(
            webhook.getPriority().getPriorityValue(),
            other.webhook.getPriority().getPriorityValue()
        );
        
        if (priorityCompare != 0) {
            return priorityCompare;
        }
        
        // Then by creation time (older first)
        return Long.compare(this.createdAt, other.createdAt);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WebhookDeliveryTask that = (WebhookDeliveryTask) o;
        return webhook.getId().equals(that.webhook.getId());
    }
    
    @Override
    public int hashCode() {
        return webhook.getId().hashCode();
    }
}