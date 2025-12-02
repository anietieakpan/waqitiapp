package com.waqiti.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating configuration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateConfigRequest {
    private String value;
    private String description;
    private Boolean active;
    private String dataType;
    private String defaultValue;
}
