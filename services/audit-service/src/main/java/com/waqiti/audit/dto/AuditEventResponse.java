package com.waqiti.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Audit Event Response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEventResponse {
    private UUID eventId;
    private String eventType;
    private String entityType;
    private String entityId;
    private String userId;
    private String action;
    private String description;
    private String severity;
    private Map<String, Object> metadata;
    private LocalDateTime timestamp;
    private String ipAddress;
    private String userAgent;
}
