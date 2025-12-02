package com.waqiti.config.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request DTO for importing configurations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigImportRequest {

    @NotEmpty(message = "Configurations cannot be empty")
    private Map<String, String> configurations;

    private String service;
    private String environment;
    private Boolean overwrite;
}
