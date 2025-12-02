package com.waqiti.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for searching configurations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigSearchRequest {
    private String key;
    private String service;
    private String environment;
    private Boolean active;
    private Boolean sensitive;
}
