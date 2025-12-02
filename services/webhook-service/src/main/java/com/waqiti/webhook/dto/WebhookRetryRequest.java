package com.waqiti.webhook.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for manually retrying a failed webhook
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookRetryRequest {

    @NotBlank(message = "Webhook ID is required")
    private String webhookId;

    private boolean forceRetry;
    private String reason;
}
