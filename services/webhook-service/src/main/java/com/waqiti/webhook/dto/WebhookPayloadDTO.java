package com.waqiti.webhook.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO representing the webhook payload sent to customer endpoints
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookPayloadDTO {

    private String eventId;
    private String eventType;
    private Map<String, Object> data;
    private LocalDateTime timestamp;
    private String signature;
}
