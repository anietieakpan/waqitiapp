package com.waqiti.common.webhook;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Webhook record for tracking
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookRecord {
    private String webhookId;
    private String endpointUrl;
    private String eventType;
    private String idempotencyKey;
    private WebhookStatus status;
    private int retryCount;
    private Instant lastAttemptAt;
    private Instant nextRetryAt;
    private String lastError;
}