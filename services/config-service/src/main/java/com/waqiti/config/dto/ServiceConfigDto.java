package com.waqiti.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * DTO for service-specific configuration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceConfigDto {
    private String serviceName;
    private String environment;
    private Map<String, String> configurations;
    private Instant lastModified;
}
