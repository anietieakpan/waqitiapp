package com.waqiti.webhook.dto;

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
public class WebhookTestResult {
    private boolean success;
    private String subscriptionId;
    private LocalDateTime timestamp;
    private Map<String, Object> payload;
    private String errorMessage;
    private Integer responseCode;
    private String responseBody;
    private Long responseTimeMs;
}
