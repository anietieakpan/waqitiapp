package com.waqiti.audit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Create Audit Event Request DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAuditEventRequest {
    @NotBlank
    private String eventType;

    @NotBlank
    private String entityType;

    private String entityId;

    @NotBlank
    private String action;

    private String userId;
    private String description;
    private String severity;
    private Map<String, Object> metadata;
    private String ipAddress;
    private String userAgent;
}
