package com.waqiti.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO for configuration metrics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigMetricsDto {
    private Long totalConfigurations;
    private Long activeConfigurations;
    private Long sensitiveConfigurations;
    private Map<String, Long> configsByService;
    private Map<String, Long> configsByEnvironment;
    private Long totalFeatureFlags;
    private Long enabledFeatureFlags;
}
