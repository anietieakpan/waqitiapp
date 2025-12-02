package com.waqiti.discovery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Registry Statistics DTO
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistryStatisticsDto {
    private Long totalRegistrations;
    private Long totalDeregistrations;
    private Long totalHealthChecks;
    private Long failedHealthChecks;
    private Double healthCheckSuccessRate;
    private Map<String, Integer> servicesByType;
    private Map<String, Integer> servicesByEnvironment;
}
