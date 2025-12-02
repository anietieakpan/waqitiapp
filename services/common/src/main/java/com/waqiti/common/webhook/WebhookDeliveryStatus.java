package com.waqiti.common.webhook;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

/**
 * Webhook delivery status tracking
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookDeliveryStatus {
    
    private String webhookId;
    private String endpointUrl;
    private WebhookEvent.WebhookStatus status;
    private int attemptCount;
    private int maxAttempts;
    private Instant lastAttemptAt;
    private Instant nextAttemptAt;
    private Duration totalDuration;
    private String lastErrorMessage;
    private Integer lastResponseCode;
    private boolean isSuccess;
    private boolean canRetry;
    private long estimatedNextRetryMs;
    
    /**
     * Create status from webhook event
     */
    public static WebhookDeliveryStatus fromWebhookEvent(WebhookEvent event) {
        return WebhookDeliveryStatus.builder()
            .webhookId(event.getId().toString())
            .endpointUrl(event.getEndpointUrl())
            .status(event.getStatus())
            .attemptCount(event.getRetryCount())
            .maxAttempts(event.getMaxRetryAttempts())
            .lastAttemptAt(event.getLastAttemptAt())
            .nextAttemptAt(event.getNextAttemptAt())
            .lastErrorMessage(event.getLastErrorMessage())
            .lastResponseCode(event.getLastResponseStatus())
            .isSuccess(event.getStatus() == WebhookEvent.WebhookStatus.DELIVERED)
            .canRetry(event.canRetry())
            .totalDuration(event.getDeliveredAt() != null && event.getCreatedAt() != null ?
                Duration.between(event.getCreatedAt(), event.getDeliveredAt()) : null)
            .estimatedNextRetryMs(event.getNextAttemptAt() != null ?
                Duration.between(Instant.now(), event.getNextAttemptAt()).toMillis() : 0)
            .build();
    }
    
    /**
     * Check if delivery is in progress
     */
    public boolean isInProgress() {
        return status == WebhookEvent.WebhookStatus.PROCESSING || 
               status == WebhookEvent.WebhookStatus.PENDING;
    }
    
    /**
     * Check if delivery is complete (success or final failure)
     */
    public boolean isComplete() {
        return status == WebhookEvent.WebhookStatus.DELIVERED ||
               status == WebhookEvent.WebhookStatus.FAILED ||
               status == WebhookEvent.WebhookStatus.EXPIRED ||
               status == WebhookEvent.WebhookStatus.CANCELLED ||
               status == WebhookEvent.WebhookStatus.DEAD_LETTER;
    }
    
    /**
     * Get success rate (0.0 to 1.0)
     */
    public double getSuccessRate() {
        if (attemptCount == 0) return 0.0;
        return isSuccess ? 1.0 : 0.0;
    }
    
    /**
     * Get progress percentage (0-100)
     */
    public int getProgressPercentage() {
        if (isComplete()) return 100;
        if (maxAttempts == 0) return 0;
        return (int) ((double) attemptCount / maxAttempts * 100);
    }
}