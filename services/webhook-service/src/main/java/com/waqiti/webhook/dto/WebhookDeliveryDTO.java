package com.waqiti.webhook.dto;

import com.waqiti.webhook.model.WebhookStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookDeliveryDTO {
    private String id;
    private String subscriptionId;
    private String eventType;
    private Map<String, Object> payload;
    private WebhookStatus status;
    private LocalDateTime deliveredAt;
    private Integer responseCode;
    private String responseBody;
    private Integer attempts;
    private Long responseTimeMs;
}
