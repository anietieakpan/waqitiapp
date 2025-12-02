package com.waqiti.webhook.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookStatistics {
    private long totalSubscriptions;
    private long activeSubscriptions;
    private long totalDeliveries;
    private long successfulDeliveries;
    private long failedDeliveries;
    private double successRate;
    private Double averageResponseTimeMs;
    private Double p95ResponseTimeMs;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
}
