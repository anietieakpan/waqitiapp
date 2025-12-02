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
public class WebhookStatisticsRequest {
    private String userId;
    private String subscriptionId;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
}
