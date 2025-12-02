package com.waqiti.webhook.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Result DTO for webhook endpoint health check
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookHealthCheckResult {

    private String subscriptionId;
    private boolean isHealthy;
    private Integer responseCode;
    private Long responseTimeMs;
    private String errorMessage;
    private LocalDateTime checkedAt;
}
