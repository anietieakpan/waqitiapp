package com.waqiti.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for configuration dependencies
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigDependenciesDto {
    private String configKey;
    private List<String> dependsOn;
    private List<String> requiredBy;
    private List<String> relatedConfigs;
}
