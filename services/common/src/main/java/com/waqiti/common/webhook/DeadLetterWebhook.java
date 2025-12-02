package com.waqiti.common.webhook;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Dead letter webhook entry
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeadLetterWebhook {
    private String id;
    private String webhookId;
    private WebhookEvent event;
    private Instant failedAt;
    private String reason;
    private String failureReason;
    private int totalAttempts;
    private boolean manualRetryRequested;

    public String getId() {
        return id != null ? id : webhookId;
    }
}