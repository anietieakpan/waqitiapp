package com.waqiti.webhook.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO representing a platform event that triggers webhooks
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformEvent {

    private String eventId;
    private String eventType;
    private String tenantId;
    private String userId;
    private Map<String, Object> payload;
    private LocalDateTime timestamp;
    private String source;
    private String version;
}
