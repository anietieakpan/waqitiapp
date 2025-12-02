package com.waqiti.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for exporting configurations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportConfigRequest {
    private String service;
    private String environment;
    private String format; // JSON, YAML, PROPERTIES
    private Boolean includeSecrets;
}
