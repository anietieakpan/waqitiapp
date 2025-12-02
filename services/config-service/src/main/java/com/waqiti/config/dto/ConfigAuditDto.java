package com.waqiti.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for configuration audit record
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigAuditDto {
    private UUID id;
    private String configKey;
    private String action;
    private String oldValue;
    private String newValue;
    private String userId;
    private String userName;
    private Instant timestamp;
    private String ipAddress;
    private String reason;
    private Boolean success;
    private String errorMessage;
}
