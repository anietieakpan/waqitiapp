package com.waqiti.webhook.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO representing circuit breaker status for webhook endpoint
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookCircuitBreakerStatus {

    private String endpointUrl;
    private String state; // CLOSED, OPEN, HALF_OPEN
    private int failureCount;
    private int successCount;
    private double failureRate;
    private LocalDateTime lastFailureTime;
    private LocalDateTime nextRetryTime;
}
