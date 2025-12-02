package com.waqiti.config.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for bulk configuration update
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkConfigUpdate {

    @NotBlank(message = "Configuration key is required")
    private String key;

    @NotBlank(message = "Configuration value is required")
    private String value;

    private String description;
}
