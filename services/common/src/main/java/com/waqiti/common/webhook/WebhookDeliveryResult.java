package com.waqiti.common.webhook;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Webhook delivery result
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookDeliveryResult {
    private String webhookId;
    private boolean success;
    private int statusCode;
    private String responseBody;
    private String errorMessage;
    private int attemptNumber;
    private Instant deliveredAt;
    private boolean isDuplicate;
    private boolean retryable;
    
    public static WebhookDeliveryResult duplicate(String webhookId) {
        return WebhookDeliveryResult.builder()
            .webhookId(webhookId)
            .isDuplicate(true)
            .build();
    }
    
    public static WebhookDeliveryResult success(String webhookId, int statusCode, String responseBody) {
        return WebhookDeliveryResult.builder()
            .webhookId(webhookId)
            .success(true)
            .statusCode(statusCode)
            .responseBody(responseBody)
            .deliveredAt(Instant.now())
            .build();
    }
    
    public static WebhookDeliveryResult failure(String webhookId, String errorMessage) {
        return WebhookDeliveryResult.builder()
            .webhookId(webhookId)
            .success(false)
            .errorMessage(errorMessage)
            .build();
    }

    public static WebhookDeliveryResult failed(String webhookId, String errorMessage) {
        return failure(webhookId, errorMessage);
    }

    public static WebhookDeliveryResult retryable(String webhookId, String errorMessage) {
        return WebhookDeliveryResult.builder()
            .webhookId(webhookId)
            .success(false)
            .errorMessage(errorMessage)
            .build();
    }

    public boolean isSuccessful() {
        return success;
    }

    public boolean isRetryable() {
        return retryable || (statusCode >= 500 && statusCode < 600) || statusCode == 429;
    }
}