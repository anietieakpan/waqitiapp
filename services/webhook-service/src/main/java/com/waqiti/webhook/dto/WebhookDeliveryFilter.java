package com.waqiti.webhook.dto;

import com.waqiti.webhook.model.WebhookStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookDeliveryFilter {
    private String subscriptionId;
    private String eventType;
    private WebhookStatus status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer minResponseCode;
    private Integer maxResponseCode;
}
