package com.waqiti.discovery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Registry Health DTO
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistryHealthDto {
    private String status;
    private Integer totalServices;
    private Integer healthyServices;
    private Integer totalInstances;
    private Integer healthyInstances;
    private Double healthPercentage;
    private Long uptime;
}
