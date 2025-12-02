package com.waqiti.config.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating configuration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateConfigRequest {

    @NotBlank(message = "Configuration key is required")
    private String key;

    @NotBlank(message = "Configuration value is required")
    private String value;

    private String service;

    private String environment;

    private String description;

    private Boolean sensitive;

    private Boolean encrypted;

    private String dataType;

    private String defaultValue;
}
