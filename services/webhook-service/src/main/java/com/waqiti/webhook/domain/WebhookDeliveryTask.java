package com.waqiti.webhook.domain;

import com.waqiti.webhook.entity.Webhook;
import com.waqiti.webhook.entity.WebhookDelivery;
import lombok.Getter;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Webhook delivery task for asynchronous processing
 * Manages the state and completion of webhook delivery attempts
 */
@Getter
public class WebhookDeliveryTask implements Comparable<WebhookDeliveryTask> {
    private final Webhook webhook;
    private final WebhookPayload payload;
    private final AtomicInteger attemptNumber;
    private final CompletableFuture<WebhookDelivery> future;
    private final long createdAt;
    
    public WebhookDeliveryTask(Webhook webhook, WebhookPayload payload, int initialAttempts) {
        this.webhook = webhook;
        this.payload = payload;
        this.attemptNumber = new AtomicInteger(initialAttempts);
        this.future = new CompletableFuture<>();
        this.createdAt = System.currentTimeMillis();
    }
    
    public void incrementAttempt() {
        attemptNumber.incrementAndGet();
    }
    
    public int getAttemptNumber() {
        return attemptNumber.get();
    }
    
    public void complete(WebhookDelivery delivery) {
        future.complete(delivery);
    }
    
    public void completeExceptionally(Throwable throwable) {
        future.completeExceptionally(throwable);
    }
    
    public boolean isDone() {
        return future.isDone();
    }
    
    public boolean isCompletedExceptionally() {
        return future.isCompletedExceptionally();
    }
    
    public long getAge() {
        return System.currentTimeMillis() - createdAt;
    }
    
    @Override
    public int compareTo(WebhookDeliveryTask other) {
        // Priority queue ordering: Critical > High > Normal > Low
        int priorityComparison = Integer.compare(
            this.payload.getPriority().getOrder(),
            other.payload.getPriority().getOrder()
        );
        
        if (priorityComparison != 0) {
            return priorityComparison;
        }
        
        // If same priority, older tasks first
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