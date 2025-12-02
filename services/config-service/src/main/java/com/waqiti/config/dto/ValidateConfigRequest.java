package com.waqiti.config.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for validating configuration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidateConfigRequest {

    @NotBlank(message = "Configuration key is required")
    private String key;

    @NotBlank(message = "Configuration value is required")
    private String value;

    private String dataType;
}
