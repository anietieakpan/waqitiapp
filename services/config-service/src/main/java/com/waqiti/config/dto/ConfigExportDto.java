package com.waqiti.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO for configuration export result
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigExportDto {
    private String format;
    private String content;
    private Map<String, String> configurations;
    private Integer totalConfigs;
}
