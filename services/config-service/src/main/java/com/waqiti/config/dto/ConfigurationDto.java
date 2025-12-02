package com.waqiti.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for full configuration details
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigurationDto {
    private UUID id;
    private String key;
    private String value;
    private String service;
    private String environment;
    private String description;
    private Boolean active;
    private Boolean sensitive;
    private Boolean encrypted;
    private String dataType;
    private String defaultValue;
    private Instant createdAt;
    private Instant lastModified;
    private String createdBy;
    private String modifiedBy;
    private Long version;
}
